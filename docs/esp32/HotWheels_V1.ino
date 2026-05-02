// =============================================================================
//  HotWheels_V1 — firmware ESP32  v0.6
// =============================================================================
//
//  Cablage batterie (1S Li-ion, 3.0 V vide -> 4.2 V plein) :
//
//      Vbat (+) ----[ R1 = 100 kOhm ]----+----[ R2 = 100 kOhm ]---- GND
//                                        |
//                                       BAT_PIN  (ADC1, input-only)
//
//  Le pont diviseur 1:1 ramene 4.2 V (max) a 2.1 V au pin (safe pour l'ADC ESP32).
//  Adapte BAT_PIN ci-dessous selon ton cablage : 32 ou 34 sont tous deux ADC1.
//
//  Bouton "ARRET D'URGENCE" (optionnel) :
//
//      ESTOP_PIN ----[ Bouton momentane ]---- GND
//
//  Interne pull-up active : pin flottant = OK, bouton appuye = coupure moteur immediate.
//
// =============================================================================
//  Nouveautes v0.3
// =============================================================================
//   - Lecture batterie : moyennage 32 echantillons + EMA + courbe Li-ion non lineaire
//   - Calibration ADC eFuse via esp_adc_cal (precision +/-20 mV au lieu de +/-150 mV)
//   - Watchdog moteur : si pas de trame depuis WATCHDOG_MS, on coupe le PWM
//   - Bouton arret d'urgence GPIO (interrupt) pour cut moteur instantane
//   - Protocole extensible : accepte "VAL", "M1:VAL", "M2:VAL" pour pilotage multi-moteur
//   - Commande PING -> reponse PONG (mesure latence cote Android)
//   - GPIO batterie configurable en une ligne
// =============================================================================

#include "BluetoothSerial.h"
#include "esp_adc_cal.h"

BluetoothSerial SerialBT;

// ----------------------------- CONFIG -----------------------------------------

// Moteur principal
const int pinIN1 = 26;
const int pinIN2 = 27;
const int pinEN  = 25;

// (optionnel) Moteur secondaire — laisser -1 si non cable
const int pinIN3 = -1;
const int pinIN4 = -1;
const int pinEN2 = -1;

// Batterie
// GPIO32 (ADC1_CH4) sur ce board. GPIO34 (ADC1_CH6) est aussi valide si besoin.
// Note : si l'ADC retourne raw=0 alors que le multimetre lit la bonne tension au
// pin, c'est presque toujours un faux contact ou une soudure froide sur le fil
// du diviseur — verifier en bougeant la connexion plutot que de changer de pin.
#define BAT_PIN          32        // GPIO 32 (ADC1_CH4)
#define R1_OHMS          100000
#define R2_OHMS          100000
#define VBAT_FULL_CV     420
#define VBAT_EMPTY_CV    320       // 3.20V = 0% (cutoff securite Li-ion + marge)
#define VBAT_CUTOFF_CV   320       // En dessous, le firmware refuse d'appliquer le throttle

// Sag detection : si la tension instantanee chute brutalement vs moyenne lissee,
// on reduit le PWM pour eviter de griller le L298 sur blocage mecanique.
#define SAG_THRESHOLD_CV  20       // 200 mV de chute = surintensite probable
#define SAG_REDUCTION_PCT 20       // -20% PWM tant que sag actif

// Buzzer + LED RGB de statut (optionnels). Mettre a -1 pour desactiver.
#define BUZZER_PIN       -1        // pin piezo (PWM/TONE) — ex: 13
#define LED_R_PIN        -1        // ex: 12
#define LED_G_PIN        -1        // ex: 14
#define LED_B_PIN        -1        // ex: 27 (attention conflit pinIN2)

// Bouton arret d'urgence — laisser -1 pour desactiver
#define ESTOP_PIN        -1

// Temps
#define BAT_INTERVAL_MS  1000
#define WATCHDOG_MS      1500
#define LOOP_DELAY_MS    2

// Lissage
#define ADC_SAMPLES      32
#define EMA_ALPHA        0.20f

// ----------------------------- ETAT -------------------------------------------

unsigned long lastBatSend     = 0;
unsigned long lastCmdRecvd    = 0;
volatile bool emergencyStop   = false;
float emaCentivolts           = -1.0f;
float lastInstantCv           = -1.0f;   // dernier cv brut (avant EMA), pour detection sag
int   sagPenaltyPct           = 0;       // 0..SAG_REDUCTION_PCT : penalite courante
unsigned long sagUntilMs      = 0;       // jusqu'a quand maintenir la penalite
esp_adc_cal_characteristics_t adcCal;

// ----------------------------- LUT Li-ion -------------------------------------
struct LiIonPoint { int cv; int pct; };
// LUT remappee : 0% = 3.20V (cutoff securite). Permet de garder une marge avant
// la limite chimique 3.00V. La carte refuse d'appliquer le throttle a 0%.
const LiIonPoint LIION_LUT[] = {
  {420, 100}, {400,  85}, {385,  70}, {375,  55}, {370,  45},
  {365,  35}, {360,  25}, {350,  15}, {340,   8}, {325,   2}, {320,   0}
};
const int LIION_LUT_SIZE = sizeof(LIION_LUT) / sizeof(LIION_LUT[0]);

int percentFromCentivolts(int cv) {
  if (cv >= LIION_LUT[0].cv) return 100;
  if (cv <= LIION_LUT[LIION_LUT_SIZE - 1].cv) return 0;
  for (int i = 0; i < LIION_LUT_SIZE - 1; i++) {
    int hi = LIION_LUT[i].cv;
    int lo = LIION_LUT[i + 1].cv;
    if (cv <= hi && cv >= lo) {
      int pctHi = LIION_LUT[i].pct;
      int pctLo = LIION_LUT[i + 1].pct;
      return pctLo + (cv - lo) * (pctHi - pctLo) / (hi - lo);
    }
  }
  return 0;
}

// ----------------------------- ADC --------------------------------------------

// Variables globales pour le diagnostic ADC (exposees dans la trame BAT enrichie).
int    g_lastRaw = 0;        // derniere lecture brute (0..4095)
uint32_t g_lastPinMv = 0;    // tension au pin (mV) apres calibration eFuse
uint32_t g_lastVbatMv = 0;   // tension Vbat reconstruite (mV) apres pont diviseur

int readVbatCentivolts() {
  uint32_t sumMv = 0;
  uint32_t sumRaw = 0;
  for (int i = 0; i < ADC_SAMPLES; i++) {
    int raw = analogRead(BAT_PIN);
    sumRaw += raw;
    uint32_t mv = esp_adc_cal_raw_to_voltage(raw, &adcCal);
    sumMv += mv;
  }
  uint32_t avgMv = sumMv / ADC_SAMPLES;
  g_lastRaw = (int)(sumRaw / ADC_SAMPLES);
  g_lastPinMv = avgMv;
  uint32_t vbatMv = avgMv * (R1_OHMS + R2_OHMS) / R2_OHMS;
  g_lastVbatMv = vbatMv;
  int newCv = (int)((vbatMv + 5) / 10);

  // Detection sag : si chute > seuil entre l'EMA et l'instant, on active la penalite.
  if (emaCentivolts > 0 && (emaCentivolts - newCv) > SAG_THRESHOLD_CV) {
    sagPenaltyPct = SAG_REDUCTION_PCT;
    sagUntilMs = millis() + 500;
  }
  lastInstantCv = (float)newCv;

  if (emaCentivolts < 0) {
    emaCentivolts = (float)newCv;
  } else {
    emaCentivolts = EMA_ALPHA * (float)newCv + (1.0f - EMA_ALPHA) * emaCentivolts;
  }
  return (int)(emaCentivolts + 0.5f);
}

// Externe pour applyMotor / loop principal.
bool isUnderCutoff() {
  return emaCentivolts > 0 && emaCentivolts < (float)VBAT_CUTOFF_CV;
}

int currentSagPenalty() {
  if (millis() > sagUntilMs) sagPenaltyPct = 0;
  return sagPenaltyPct;
}

// Lecture temperature interne ESP32 (en Celsius). Note: precision +/- 5C.
extern "C" uint8_t temprature_sens_read();
int readTempCelsius() {
  uint8_t raw = temprature_sens_read();
  // Le capteur retourne F : C = (F - 32) * 5 / 9
  int tF = (int)raw;
  return (tF - 32) * 5 / 9;
}

void sendBatteryIfDue() {
  unsigned long now = millis();
  if (now - lastBatSend < BAT_INTERVAL_MS) return;
  lastBatSend = now;
  if (!SerialBT.hasClient()) return;
  int cv = readVbatCentivolts();
  int pct = percentFromCentivolts(cv);
  // Trame enrichie v0.6 : BAT:cv,pct,raw,pinMv,vbatMv,tempC,sag
  // - cv      : centivolts apres EMA (legacy)
  // - pct     : pourcentage (LUT, 0% = 3.20V cutoff)
  // - raw     : ADC brut moyen (0..4095)
  // - pinMv   : tension au pin (mV) apres calibration eFuse
  // - vbatMv  : Vbat reconstruite (mV) avant EMA
  // - tempC   : temperature interne ESP32 (C) — precision +/- 5C
  // - sag     : penalite sag courante (0..20)
  // L'app accepte aussi 5 champs (firmware v0.4) ou 2 champs (legacy v0.3).
  SerialBT.print("BAT:");
  SerialBT.print(cv);
  SerialBT.print(",");
  SerialBT.print(pct);
  SerialBT.print(",");
  SerialBT.print(g_lastRaw);
  SerialBT.print(",");
  SerialBT.print(g_lastPinMv);
  SerialBT.print(",");
  SerialBT.print(g_lastVbatMv);
  SerialBT.print(",");
  SerialBT.print(readTempCelsius());
  SerialBT.print(",");
  SerialBT.print(currentSagPenalty());
  SerialBT.print("\n");
}

// ----------------------------- MOTEUR -----------------------------------------

// Flag bypass batterie : commande BYPASS_BAT:1 / BYPASS_BAT:0 cote app
volatile bool batteryBypass = false;

void applyMotor(int channel, int motorValue) {
  motorValue = constrain(motorValue, -100, 100);

  // Coupure profonde batterie (sauf si l'utilisateur a active le bypass).
  if (motorValue != 0 && isUnderCutoff() && !batteryBypass) {
    motorValue = 0;
  }

  // Penalite sag : reduit le PWM en cas de chute brutale (blocage mecanique).
  int penalty = currentSagPenalty();
  if (penalty > 0 && motorValue != 0) {
    motorValue = motorValue * (100 - penalty) / 100;
  }

  int pwmValue = map(abs(motorValue), 0, 100, 0, 255);

  int in1, in2, en;
  if (channel == 1) {
    in1 = pinIN1; in2 = pinIN2; en = pinEN;
  } else if (channel == 2 && pinIN3 >= 0) {
    in1 = pinIN3; in2 = pinIN4; en = pinEN2;
  } else {
    return;
  }

  if (motorValue > 0) {
    digitalWrite(in1, HIGH);
    digitalWrite(in2, LOW);
    analogWrite(en, pwmValue);
  } else if (motorValue < 0) {
    digitalWrite(in1, LOW);
    digitalWrite(in2, HIGH);
    analogWrite(en, pwmValue);
  } else {
    digitalWrite(in1, LOW);
    digitalWrite(in2, LOW);
    analogWrite(en, 0);
  }
}

void cutAllMotors() {
  digitalWrite(pinIN1, LOW);
  digitalWrite(pinIN2, LOW);
  analogWrite(pinEN, 0);
  if (pinIN3 >= 0) {
    digitalWrite(pinIN3, LOW);
    digitalWrite(pinIN4, LOW);
    analogWrite(pinEN2, 0);
  }
}

void IRAM_ATTR onEmergencyStopFalling() {
  emergencyStop = true;
}

// ----------------------------- PROTOCOLE --------------------------------------

void handleCommand(String cmd) {
  cmd.trim();
  if (cmd.length() == 0) return;

  if (cmd == "PING") {
    SerialBT.print("PONG\n");
    return;
  }

  // Bypass batterie : BYPASS_BAT:1 ou BYPASS_BAT:0
  if (cmd.startsWith("BYPASS_BAT:")) {
    batteryBypass = (cmd.substring(11).toInt() != 0);
    SerialBT.print("BYPASS_BAT:");
    SerialBT.print(batteryBypass ? 1 : 0);
    SerialBT.print("\n");
    return;
  }

  int channel = 1;
  String payload = cmd;
  if (cmd.startsWith("M1:")) { channel = 1; payload = cmd.substring(3); }
  else if (cmd.startsWith("M2:")) { channel = 2; payload = cmd.substring(3); }

  int motorValue = payload.toInt();
  applyMotor(channel, motorValue);
  lastCmdRecvd = millis();

  Serial.print("[ch ");
  Serial.print(channel);
  Serial.print("] ");
  Serial.print(motorValue);
  Serial.println("%");
}

// ----------------------------- SETUP / LOOP -----------------------------------

void setup() {
  Serial.begin(115200);
  SerialBT.begin("HotWheels_V1");
  Serial.println("[v0.6] BT pret. Connecte ton telephone.");

  pinMode(pinIN1, OUTPUT);
  pinMode(pinIN2, OUTPUT);
  pinMode(pinEN, OUTPUT);
  if (pinIN3 >= 0) {
    pinMode(pinIN3, OUTPUT);
    pinMode(pinIN4, OUTPUT);
    pinMode(pinEN2, OUTPUT);
  }
  cutAllMotors();

  analogReadResolution(12);
  analogSetAttenuation(ADC_11db);
  esp_adc_cal_characterize(ADC_UNIT_1, ADC_ATTEN_DB_11, ADC_WIDTH_BIT_12, 1100, &adcCal);

  if (ESTOP_PIN >= 0) {
    pinMode(ESTOP_PIN, INPUT_PULLUP);
    attachInterrupt(digitalPinToInterrupt(ESTOP_PIN), onEmergencyStopFalling, FALLING);
  }

  lastCmdRecvd = millis();
}

void loop() {
  if (emergencyStop) {
    cutAllMotors();
    Serial.println("[ESTOP] coupure d'urgence !");
    emergencyStop = false;
    delay(500);
  }

  if (SerialBT.available()) {
    String command = SerialBT.readStringUntil('\n');
    handleCommand(command);
  }

  if (millis() - lastCmdRecvd > WATCHDOG_MS) {
    cutAllMotors();
  }

  sendBatteryIfDue();

  delay(LOOP_DELAY_MS);
}
