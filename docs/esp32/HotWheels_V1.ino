// HotWheels_V1 - firmware ESP32
//
// Cablage batterie (1S Li-ion 600 mAh, 3.0 V vide -> 4.2 V plein) :
//
//      Vbat (+) ----[ R1 = 100 kOhm ]----+----[ R2 = 100 kOhm ]---- GND
//                                        |
//                                       GPIO 34 (ADC1_CH6, input-only)
//
//   - Le pont diviseur 1:1 ramene 4.2 V (max) a 2.1 V au pin -> safe pour l'ADC ESP32 (3.3 V max).
//   - GPIO 34 est input-only et ideal pour l'ADC. Les broches moteur (25/26/27) sont libres pour le L293D.
//   - Si tu utilises une batterie differente, ajuste VBAT_FULL_CV / VBAT_EMPTY_CV ci-dessous.

#include "BluetoothSerial.h"

BluetoothSerial SerialBT;

// --- Moteur ---
const int pinIN1 = 26;
const int pinIN2 = 27;
const int pinEN  = 25;

// --- Batterie ---
const int   BAT_PIN          = 34;        // ADC1_CH6
const int   R1_OHMS          = 100000;    // top : Vbat -> ADC pin
const int   R2_OHMS          = 100000;    // bottom : ADC pin -> GND
const float VREF_V           = 3.3f;      // ESP32 ref tension
const int   ADC_MAX          = 4095;      // 12-bit
const int   VBAT_FULL_CV     = 420;       // 4.20 V en centivolts
const int   VBAT_EMPTY_CV    = 300;       // 3.00 V en centivolts
const unsigned long BAT_INTERVAL_MS = 1000;

unsigned long lastBatSend = 0;

int readVbatCentivolts() {
  long sum = 0;
  for (int i = 0; i < 16; i++) sum += analogRead(BAT_PIN);
  float vadc = (float)sum / 16.0f * VREF_V / (float)ADC_MAX;
  float vbat = vadc * (float)(R1_OHMS + R2_OHMS) / (float)R2_OHMS;
  return (int)(vbat * 100.0f + 0.5f);
}

int percentFromCentivolts(int cv) {
  if (cv >= VBAT_FULL_CV) return 100;
  if (cv <= VBAT_EMPTY_CV) return 0;
  return (cv - VBAT_EMPTY_CV) * 100 / (VBAT_FULL_CV - VBAT_EMPTY_CV);
}

void sendBatteryIfDue() {
  unsigned long now = millis();
  if (now - lastBatSend < BAT_INTERVAL_MS) return;
  lastBatSend = now;
  if (!SerialBT.hasClient()) return;          // pas de client connecte -> rien a envoyer
  int cv = readVbatCentivolts();
  int pct = percentFromCentivolts(cv);
  SerialBT.print("BAT:");
  SerialBT.print(cv);
  SerialBT.print(",");
  SerialBT.print(pct);
  SerialBT.print("\n");
}

void setup() {
  Serial.begin(115200);
  SerialBT.begin("HotWheels_V1");
  Serial.println("Le Bluetooth est pret ! Connecte ton telephone.");

  pinMode(pinIN1, OUTPUT);
  pinMode(pinIN2, OUTPUT);
  pinMode(pinEN, OUTPUT);

  digitalWrite(pinIN1, LOW);
  digitalWrite(pinIN2, LOW);
  analogWrite(pinEN, 0);

  // ADC config (defauts : 12-bit, attenuation 11dB -> ~0..3.3 V).
  analogReadResolution(12);
}

void loop() {
  if (SerialBT.available()) {
    String command = SerialBT.readStringUntil('\n');
    command.trim();

    if (command.length() > 0) {
      int motorValue = command.toInt();
      motorValue = constrain(motorValue, -100, 100);
      int pwmValue = map(abs(motorValue), 0, 100, 0, 255);

      if (motorValue > 0) {
        digitalWrite(pinIN1, HIGH);
        digitalWrite(pinIN2, LOW);
        analogWrite(pinEN, pwmValue);
      } else if (motorValue < 0) {
        digitalWrite(pinIN1, LOW);
        digitalWrite(pinIN2, HIGH);
        analogWrite(pinEN, pwmValue);
      } else {
        digitalWrite(pinIN1, LOW);
        digitalWrite(pinIN2, LOW);
        analogWrite(pinEN, 0);
      }

      Serial.print("Commande: ");
      Serial.print(motorValue);
      Serial.print("% -> Signal PWM: ");
      Serial.println(pwmValue);
    }
  }

  sendBatteryIfDue();
}
