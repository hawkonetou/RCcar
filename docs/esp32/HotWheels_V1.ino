// =============================================================================
//  HotWheels_V1 — firmware ESP32  v0.4
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
#define BAT_PIN          32        // GPIO 32 (ADC1_CH4) - modifier en 34 si besoin
#define R1_OHMS          100000
#define R2_OHMS          100000
#define VBAT_FULL_CV     420
#define VBAT_EMPTY_CV    300

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
esp_adc_cal_characteristics_t adcCal;

// ----------------------------- LUT Li-ion -------------------------------------
struct LiIonPoint { int cv; int pct; };
const LiIonPoint LIION_LUT[] = {
  {420, 100}, {400,  85}, {385,  70}, {375,  55}, {370,  45},
  {365,  35}, {360,  25}, {350,  15}, {340,   8}, {320,   3}, {300,   0}
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

  if (emaCentivolts < 0) {
    emaCentivolts = (float)newCv;
  } else {
    emaCentivolts = EMA_ALPHA * (float)newCv + (1.0f - EMA_ALPHA) * emaCentivolts;
  }
  return (int)(emaCentivolts + 0.5f);
}

void sendBatteryIfDue() {
  unsigned long now = millis();
  if (now - lastBatSend < BAT_INTERVAL_MS) return;
  lastBatSend = now;
  if (!SerialBT.hasClient()) return;
  int cv = readVbatCentivolts();
  int pct = percentFromCentivolts(cv);
  // Trame enrichie : BAT:cv,pct,raw,pinMv,vbatMv
  // - cv      : centivolts apres EMA (legacy, ce que l'app affichait)
  // - pct     : pourcentage (LUT)
  // - raw     : ADC brut moyen (0..4095) — diagnostic
  // - pinMv   : tension au pin (mV) apres calibration eFuse — diagnostic
  // - vbatMv  : Vbat reconstruite (mV) avant EMA — diagnostic
  // L'app accepte aussi l'ancien format BAT:cv,pct (retro-compat).
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
  SerialBT.print("\n");
}

// ----------------------------- MOTEUR -----------------------------------------

void applyMotor(int channel, int motorValue) {
  motorValue = constrain(motorValue, -100, 100);
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
  Serial.println("[v0.4] BT pret. Connecte ton telephone.");

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
