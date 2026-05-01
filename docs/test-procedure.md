# Manual test procedure — HotWheelsCommand

## Pré-requis

- ESP32-WROOM-32D programmé avec le firmware lisant `BluetoothSerial("HotWheels_V1")` et `readStringUntil('\n')`.
- Téléphone Android 12+ (cible : Poco X6 Pro), Bluetooth activé.
- Câble USB + Arduino Serial Monitor à 115 200 baud (recommandé pour debug).
- L'APK debug : `app/build/outputs/apk/debug/app-debug.apk`.

## Construire l'APK

```bash
export JAVA_HOME=/home/franck/dev-tools/jdk-17.0.13+11
export ANDROID_HOME=/home/franck/Android/Sdk
export PATH=$JAVA_HOME/bin:$ANDROID_HOME/platform-tools:$PATH
./gradlew :app:assembleDebug
```

## Installer

USB debugging + ADB :
```bash
adb install -r app/build/outputs/apk/debug/app-debug.apk
```

Ou copier le fichier APK sur le téléphone et l'installer manuellement (autoriser "Sources inconnues" pour l'app source).

## Procédure de test

1. **Appairer la voiture** via Réglages Android → Bluetooth → `HotWheels_V1` (PIN si demandé).
2. **Lancer HotWheelsCommand**. Accorder la permission `BLUETOOTH_CONNECT` quand demandée.
3. Sur l'écran de sélection (`DeviceSelectionScreen`), taper sur `HotWheels_V1`.
4. Vérifier que l'indicateur passe au vert → `CONNECTÉ — HotWheels_V1`.
5. **Tirer le slider vers le haut** (positif). Vérifier :
   - L'affichage central indique la valeur du slider (ex: `+56`).
   - Le sous-titre indique `envoyé : -56`.
   - Le moniteur série ESP32 affiche `Commande: -56% -> Signal PWM: ...`.
   - Le moteur tourne dans le sens **inverse** (l'app envoie l'inverse, c'est volontaire).
6. **Relâcher le slider**. Vérifier :
   - Le slider snap à 0 instantanément.
   - Le moniteur série affiche `Commande: 0%`.
   - Le moteur s'arrête.
7. **Bouton Home**. Vérifier :
   - Le moteur s'arrête immédiatement (`Commande: 0%`).
   - La notification persistante "HotWheelsCommand — Connected to HotWheels_V1" est visible.
8. **Rouvrir l'app**. Vérifier reprise immédiate, pas d'étape de reconnexion.
9. **Tirer le tiroir des notifications** pendant le pilotage. Vérifier que le slider reste actif (pilotage continue).
10. **Action notification "CUT MOTOR"**. Vérifier que le moteur s'arrête.
11. **Couper l'ESP32 en plein pilotage**. Indicateur → `RECONNEXION… (n/3)` → `DÉCONNECTÉ` après ~5 s.
12. **Rallumer l'ESP32**. Possibilité de reconnecter via l'écran de sélection.
13. **Bouton Back depuis `DriveScreen`** plusieurs fois → l'app se ferme. Vérifier que la notification disparaît.

## Diagnostic

- **Latence > 10 ms perçue** : ajuster `THROTTLE_MIN_MS` ou `HEARTBEAT_MS` dans `app/src/main/java/com/hotwheels/command/bluetooth/SppConstants.kt`.
- **Voiture ne réagit pas** : logger les bytes envoyés (`outputStream.write(...)`) et comparer avec ce que voit le moniteur série.
- **Connection failed immédiate** : vérifier que la voiture est appairée dans les réglages Android, allumée et à portée.
- **Permission refusée** : Réglages → Apps → HotWheelsCommand → Permissions → Bluetooth.
