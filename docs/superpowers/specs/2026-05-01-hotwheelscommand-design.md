# HotWheelsCommand — Design

**Date** : 2026-05-01
**Statut** : Validé en brainstorming, en attente de relecture utilisateur avant plan d'implémentation
**Cible** : APK Android pour piloter une voiture RC à base d'ESP32-WROOM-32D via Bluetooth Classic SPP

---

## 1. Objectif

Application Android native (Kotlin + Jetpack Compose) permettant de piloter une voiture RC en envoyant des valeurs `-100..100` via Bluetooth Classic à un ESP32-WROOM-32D nommé `HotWheels_V1`. La voiture doit être **réactive** (latence perçue < 10 ms entre l'action utilisateur et la réception côté firmware).

Le firmware ESP32 existant n'est **pas modifié**. Il lit la valeur via `SerialBT.readStringUntil('\n').toInt()`, applique un PWM proportionnel (positif = avant, négatif = arrière, 0 = arrêt).

### Particularité fonctionnelle

L'app envoie **l'inverse** de la valeur affichée par le slider : si l'utilisateur place le slider à `+56`, l'app envoie `"-56\n"` à la voiture. Cette inversion est volontaire et fait partie du cahier des charges.

---

## 2. Décisions structurantes (validées en brainstorming)

| Décision | Choix retenu | Raison |
|----------|--------------|--------|
| Type de Bluetooth | **Classic SPP** | Latence prévisible < 10 ms (BLE limité à ~15 ms minimum sur Android). Compatible firmware existant. |
| Stack Android | **Kotlin natif + Jetpack Compose** | Contrôle thread maximal, latence prévisible, écosystème BT natif. |
| `minSdk` / `targetSdk` | **31 / 34** | Téléphone cible = Poco X6 Pro (Android 14). Permissions modernes uniquement. |
| Protocole de trame | **ASCII `<valeur>\n`** | Compatible firmware existant sans modification. |
| Délimiteur | **`\n`** | Confirmé par lecture du firmware (`SerialBT.readStringUntil('\n')`). |
| Spring-back du slider | **Retour immédiat à 0** au relâchement | Sécurité : comportement gâchette. |
| Connexion initiale | **Auto-connect au dernier appareil + fallback liste** | UX fluide, gestion multi-voitures future possible. |
| Reconnexion en cours d'usage | **Auto en arrière-plan, max 5 s, indicateur visuel** | Latence minimale en cas de micro-coupure. |
| Comportement arrière-plan | **Foreground service maintient la connexion ; envoi `0\n` à `onStop` ; rien à `onPause`** | Sécurité quand l'utilisateur quitte vraiment l'app, continuité quand il s'agit d'une notification. |
| Stratégie d'envoi | **Sur changement + throttle min 2 ms + heartbeat 50 ms** | Latence garantie, ~10× moins de messages qu'un polling 1 ms. |
| Style UI | **Cyberpunk sombre + bleu électrique pur** | Lisibilité maximale en pilotage. |
| Orientation | **Paysage forcé** | Cahier des charges. |
| Position du slider | **À droite, vertical** | Cahier des charges. |

---

## 3. Architecture

### 3.1 Vue d'ensemble

```
┌─────────────────────────────────────────────────────────────┐
│  APP HotWheelsCommand                                        │
│                                                              │
│  ┌──────────────────┐         ┌──────────────────────────┐  │
│  │  UI (Compose)    │         │  BluetoothCarService     │  │
│  │                  │ bind    │  (Foreground Service)    │  │
│  │  • DriveScreen   │◄───────►│                          │  │
│  │  • DeviceSelect  │  Flow   │  • BluetoothSocket       │  │
│  │                  │         │  • SenderThread          │  │
│  │  ViewModel       │         │  • ConnectionStateFlow   │  │
│  └──────────────────┘         └────────────┬─────────────┘  │
│                                             │                │
└─────────────────────────────────────────────┼────────────────┘
                                              │ BT Classic SPP
                                              ▼
                                    ┌──────────────────┐
                                    │  ESP32-WROOM-32D │
                                    │  "HotWheels_V1"  │
                                    └──────────────────┘
```

### 3.2 Composants

**UI layer (Jetpack Compose)** — deux écrans :

- `DeviceSelectionScreen` : liste des appareils Bluetooth déjà appairés. Affichée uniquement quand l'auto-connect échoue ou au tout premier lancement.
- `DriveScreen` : écran de pilotage plein paysage. Slider vertical à droite, affichage numérique central, indicateur de connexion en haut à gauche.

**ViewModel layer**

- `DriveViewModel` : détient l'état du slider, observe le `ConnectionState` exposé par le service, transmet les valeurs au service. Aucune logique Bluetooth.
- `DeviceSelectionViewModel` : récupère la liste des appareils appairés via `BluetoothAdapter.getBondedDevices()`.

**Service / domaine Bluetooth**

- `BluetoothCarService` : foreground service Android. Détient le `BluetoothSocket`, le `SenderThread`, expose un `StateFlow<ConnectionState>` à l'UI. Survit aux notifications du téléphone.
- `CarConnection` : classe interne au service qui encapsule le socket, le thread d'envoi, le throttle et le heartbeat. Pas de dépendance Android au-delà du `BluetoothSocket` (testable en JVM avec mock).
- `ConnectionState` : `sealed class` modélisant la machine d'états (`Idle`, `Connecting`, `Connected`, `Reconnecting`, `Failed`).

**Persistance**

- `LastDeviceStore` : wrapper sur `SharedPreferences` qui mémorise le MAC de la dernière voiture connectée pour l'auto-connect.

### 3.3 Communication UI ↔ Service

Liaison standard Android (`bindService` + `IBinder`) qui expose :

- `connectionState: StateFlow<ConnectionState>`
- `setTargetValue(value: Int)`
- `connect(macAddress: String)`
- `disconnect()`

---

## 4. Flux de données

### 4.1 Flux de connexion au lancement

```
[Lancement app]
      │
      ▼
[Vérifier permissions BLUETOOTH_CONNECT]
      │
      ▼
[Lire SharedPreferences : dernier MAC connu ?]
      │
      ├── Oui ──► [Auto-connect] ──► OK ──► DriveScreen
      │                │
      │              Échec
      │                │
      └── Non ──►──────┴──► DeviceSelectionScreen
                              │
                              ▼
                    [User tape sur HotWheels_V1]
                              │
                              ▼
                  [Connexion + sauvegarde MAC]
                              │
                              ▼
                         DriveScreen
```

### 4.2 Flux d'envoi pendant pilotage

```
[User glisse slider à valeur V]
   │
   ▼
[Compose recompose] → ViewModel.onSliderValueChange(V)
   │
   ▼
[ViewModel] → service.setTargetValue(-V)        ◄── inversion ici
   │
   ▼
[Service] currentTargetValue = -V (volatile / atomic)
   │
[SenderThread (boucle dédiée)]
   loop {
     val now = monotonicTime()
     val changed = currentTargetValue != lastSent
     val heartbeatDue = now - lastSentTime >= 50ms
     if ((changed || heartbeatDue) && now - lastSentTime >= 2ms) {
         outputStream.write("$currentTargetValue\n".toByteArray(US_ASCII))
         outputStream.flush()
         lastSent = currentTargetValue
         lastSentTime = now
     }
     LockSupport.parkNanos(500_000)   // 0,5 ms
   }
```

### 4.3 Spring-back

L'event de relâchement Compose (`pointerInput` `up` ou `onDragEnd`) → `DriveViewModel.onSliderReleased()` → `setTargetValue(0)` immédiat. L'envoi `0\n` est garanti sous 2 ms par le SenderThread. La position visuelle du slider snap à 0 sans transition.

### 4.4 Cycle de vie activity → moteur

| Évènement Android | Action |
|-------------------|--------|
| `onCreate` | bind au service, démarrer auto-connect si MAC connu |
| `onStart` (premier passage après `onCreate`) | rien de spécifique (binding maintenu) |
| `onResume` | UI prête, slider actif |
| `onPause` (notification) | **on continue d'envoyer normalement** |
| `onStop` (Home, switcher, lock) | `service.setTargetValue(0)` immédiat ; socket maintenu vivant par le service |
| `onStart` (retour dans l'app après onStop) | reprise immédiate (socket toujours ouvert), aucun re-pairing nécessaire |
| `onDestroy` (back depuis DriveScreen, fin volontaire) | `stopService` → `0\n` final → `socket.close()` → notification disparaît |

---

## 5. UI

### 5.1 Layout `DriveScreen` (paysage)

```
┌──────────────────────────────────────────────────────────────────┐
│ ●  CONNECTÉ — HotWheels_V1                            ⚙ menu    │
│                                                                  │
│                                                          ┌─┐    │
│                                                          │▓│    │
│                                                          │▓│    │ +100
│                                                          │▓│    │
│  ┌───────────────────────────┐                           │▓│    │
│  │                           │                           │▓│    │
│  │         -56               │                           │▓│    │
│  │  (gros chiffres néon)     │                           │█│    │ ← thumb
│  │                           │                           │░│    │
│  │  envoyé : -56             │                           │░│    │
│  │  débit : 47 msg/s         │                           │░│    │
│  │                           │                           │░│    │ -100
│  └───────────────────────────┘                           └─┘    │
│                                                                  │
│  pilotage HotWheels                              v1.0.0          │
└──────────────────────────────────────────────────────────────────┘
```

**Composants** :

- **Top bar** (≈50 dp) : indicateur de connexion (point lumineux 12 dp + texte d'état) à gauche, bouton menu (3 points) à droite.
- **Slider vertical** (à droite, ~80 % hauteur, ~80 dp largeur) : track sombre avec bordure néon, fill en gradient depuis le centre, thumb glowing, ticks à `+100/+50/0/-50/-100`. Zone tactile élargie à ~120 dp pour faciliter la prise. Snap à 0 au relâchement.
- **Zone d'affichage centrale** : valeur du slider en très grand (≈96 sp, monospace, glow), sous-titre `envoyé : <valeur_inversée>` (16 sp, gris cyan), compteur `débit : N msg/s` (utile pour debug).
- **Bottom bar** (≈24 dp) : titre + version, discret.

### 5.2 Palette (DesignTokens)

| Token | Valeur | Usage |
|-------|--------|-------|
| `bg.primary` | `#0A0E1A` | Fond global |
| `bg.surface` | `#10162B` | Cartes, top bar |
| `accent.electric` | `#00E5FF` | Néon principal |
| `accent.glow` | `#0091FF` | Gradient/glow secondaire |
| `state.connected` | `#00FF94` | Vert électrique |
| `state.connecting` | `#FFB800` | Jaune néon |
| `state.error` | `#FF2E5C` | Rouge néon |
| `text.primary` | `#E8F4FF` | Texte standard |
| `text.muted` | `#5A7390` | Texte secondaire |
| `font.display` | Orbitron / Rajdhani | Chiffres géants |
| `font.body` | JetBrains Mono / Roboto Mono | Tout le reste |

### 5.3 Effets visuels

- Glow sur le thumb du slider et sur les chiffres : ombres bleues douces (`Modifier.shadow` + RenderEffect.blur sur API 31+).
- Pulse subtile (1,5 s) du point d'état lorsque `Connected`.
- Scanlines très discrètes en arrière-plan (≈5 % opacité) — décor cyberpunk sans nuire à la lisibilité.
- **Aucune animation sur le slider lui-même** (réactivité prime sur effet visuel).

### 5.4 `DeviceSelectionScreen`

Liste verticale style "terminal" :

```
> APPAREILS APPAIRÉS
─────────────────────────
  HotWheels_V1
  ▸ 24:6F:28:AB:CD:EF
─────────────────────────
  [Autre appareil]
─────────────────────────

[ ACTUALISER ]   [ RÉGLAGES BT ]
```

Bouton "RÉGLAGES BT" lance `Settings.ACTION_BLUETOOTH_SETTINGS` pour permettre l'appairage initial via les paramètres système.

---

## 6. Permissions et manifest

### 6.1 Manifest

```xml
<uses-permission android:name="android.permission.BLUETOOTH_CONNECT" />
<uses-permission android:name="android.permission.FOREGROUND_SERVICE" />
<uses-permission android:name="android.permission.FOREGROUND_SERVICE_CONNECTED_DEVICE" />
<uses-permission android:name="android.permission.WAKE_LOCK" />

<uses-feature android:name="android.hardware.bluetooth" android:required="true" />

<activity
    android:name=".DriveActivity"
    android:exported="true"
    android:screenOrientation="landscape"
    android:configChanges="orientation|screenSize|keyboardHidden"
    android:keepScreenOn="true"
    android:theme="@style/Theme.HotWheels">
    <intent-filter>
        <action android:name="android.intent.action.MAIN" />
        <category android:name="android.intent.category.LAUNCHER" />
    </intent-filter>
</activity>

<service
    android:name=".bluetooth.BluetoothCarService"
    android:exported="false"
    android:foregroundServiceType="connectedDevice" />
```

**Notes** :

- Pas de `BLUETOOTH_SCAN` ni `ACCESS_FINE_LOCATION` : on n'effectue **pas de scan**, uniquement la liste des appareils appairés (autorisée avec `BLUETOOTH_CONNECT` seul depuis API 31).
- `foregroundServiceType="connectedDevice"` est obligatoire sur Android 14+ pour les services maintenant une connexion à un périphérique externe.

### 6.2 Demande de permission au runtime

`ActivityResultContracts.RequestPermission` au premier lancement. Si refus définitif → écran d'erreur avec lien vers les paramètres app.

### 6.3 Notification du foreground service

```
┌─────────────────────────────────────────┐
│ ⚡ HotWheelsCommand                      │
│ Connecté à HotWheels_V1                 │
│ [ COUPER LE MOTEUR ] [ DÉCONNECTER ]    │
└─────────────────────────────────────────┘
```

- Channel `bluetooth_car_connection`, importance `LOW` (silencieuse).
- Persistante, non swipable tant que le service vit.
- Action 1 "Couper le moteur" → envoie `0\n` immédiatement (sécurité accessible sans rouvrir l'app).
- Action 2 "Déconnecter" → arrête le service, ferme le socket.

---

## 7. Gestion d'erreurs et machine d'états

### 7.1 Machine d'états (StateFlow exposé par le service)

```
Idle ──► Connecting ──► Connected ──► Reconnecting ──► Connected
                │            │              │
                ▼            ▼              ▼
              Failed       Idle          Failed
                │
                ▼
            Connecting (retry user)
```

États :

- `Idle` : pas de cible, service inactif ou en attente.
- `Connecting` : `socket.connect()` en cours.
- `Connected` : envoi actif, slider opérationnel.
- `Reconnecting` : perte détectée, retry en cours (jusqu'à 3 essais sur 5 s).
- `Failed` : retry abandonné, l'utilisateur doit valider une reprise.

### 7.2 Détection de déconnexion

- **Source primaire** : `IOException` sur `outputStream.write()` du SenderThread → bascule `Reconnecting`.
- **Source secondaire** : `BroadcastReceiver` sur `BluetoothDevice.ACTION_ACL_DISCONNECTED` filtré sur le MAC cible → bascule immédiate (plus rapide qu'attendre l'IOException).

Le firmware ne renvoie rien : pas de keepalive applicatif possible. Le heartbeat de 50 ms garantit néanmoins une détection sub-100 ms.

### 7.3 Stratégie de reconnexion

```kotlin
attempt = 1
while (attempt <= 3 && totalElapsed < 5_000ms) {
    delay(min(200ms * attempt, 1000ms))   // backoff doux
    try {
        socket = device.createRfcommSocketToServiceRecord(SPP_UUID)
        socket.connect()
        // succès → state = Connected
    } catch (IOException) {
        attempt++
    }
}
// abandon → state = Failed
```

Pendant `Reconnecting` : slider désactivé visuellement, aucun envoi tenté.

### 7.4 Mapping états → UI

| État | Indicateur | Slider | Texte |
|------|-----------|--------|-------|
| `Idle` | ⚪ blanc | désactivé | `EN ATTENTE` |
| `Connecting` | 🟡 pulse | désactivé | `CONNEXION…` |
| `Connected` | 🟢 pulse lent | actif | `CONNECTÉ — <nom>` |
| `Reconnecting` | 🟡 pulse rapide | désactivé + overlay | `RECONNEXION… (n/3)` |
| `Failed` | 🔴 statique | désactivé + dialog | `DÉCONNECTÉ — RÉESSAYER ?` |

### 7.5 Cas d'erreur

| Erreur | Comportement |
|--------|--------------|
| BT désactivé sur le téléphone au lancement | Dialog "Activer le Bluetooth" → `ACTION_REQUEST_ENABLE` |
| Permission BT refusée | Écran d'erreur + lien paramètres app |
| Aucun appareil appairé | Liste vide + bouton "Ouvrir réglages BT" |
| Voiture éteinte au moment de l'auto-connect | Échec rapide → fallback `DeviceSelectionScreen` |
| Déconnexion soudaine pendant pilotage | `Reconnecting` (5 s max), slider verrouillé, valeur visuelle remise à 0 |
| App tuée par l'OS | Notification du service présente → l'utilisateur peut "Déconnecter" depuis la notif |
| `outputStream.write` bloque > 50 ms | Log et continuation, pas de file d'attente |
| Aucun changement depuis 50 ms | Heartbeat → renvoi de la dernière valeur |

### 7.6 Sécurité (envoi `0\n` garanti)

- `onStop` de l'activity → `setTargetValue(0)` immédiat (envoi sous 2 ms).
- Action notification "Couper le moteur" → idem, accessible app fermée.
- Crash / fermeture du service → `try/finally` du SenderThread écrit `0\n` avant `socket.close()`.

---

## 8. Structure du projet

```
hotwheelscommand/
├── build.gradle.kts
├── settings.gradle.kts
├── gradle.properties
├── app/
│   ├── build.gradle.kts
│   ├── proguard-rules.pro
│   └── src/
│       ├── main/
│       │   ├── AndroidManifest.xml
│       │   ├── java/com/hotwheels/command/
│       │   │   ├── HotWheelsApp.kt
│       │   │   ├── DriveActivity.kt
│       │   │   ├── ui/
│       │   │   │   ├── theme/
│       │   │   │   │   ├── Color.kt
│       │   │   │   │   ├── Type.kt
│       │   │   │   │   └── Theme.kt
│       │   │   │   ├── components/
│       │   │   │   │   ├── NeonVerticalSlider.kt
│       │   │   │   │   ├── ConnectionIndicator.kt
│       │   │   │   │   ├── ScanlineBackground.kt
│       │   │   │   │   └── GlowText.kt
│       │   │   │   ├── drive/
│       │   │   │   │   ├── DriveScreen.kt
│       │   │   │   │   └── DriveViewModel.kt
│       │   │   │   └── select/
│       │   │   │       ├── DeviceSelectionScreen.kt
│       │   │   │       └── DeviceSelectionViewModel.kt
│       │   │   ├── bluetooth/
│       │   │   │   ├── BluetoothCarService.kt
│       │   │   │   ├── CarConnection.kt
│       │   │   │   ├── ConnectionState.kt
│       │   │   │   └── SppConstants.kt
│       │   │   ├── data/
│       │   │   │   └── LastDeviceStore.kt
│       │   │   └── util/
│       │   │       └── PermissionUtils.kt
│       │   └── res/
│       │       ├── values/
│       │       │   ├── colors.xml
│       │       │   ├── strings.xml
│       │       │   └── themes.xml
│       │       ├── font/
│       │       └── drawable/
│       │           ├── ic_launcher_foreground.xml
│       │           └── ic_notification.xml
│       ├── test/
│       │   └── java/com/hotwheels/command/
│       │       ├── bluetooth/
│       │       │   └── CarConnectionTest.kt
│       │       └── ui/drive/
│       │           └── DriveViewModelTest.kt
│       └── androidTest/
│           └── java/com/hotwheels/command/
│               └── ui/SliderInteractionTest.kt
└── docs/
    └── superpowers/
        └── specs/
            └── 2026-05-01-hotwheelscommand-design.md
```

### Dépendances clés (Gradle)

```kotlin
android {
    namespace = "com.hotwheels.command"
    compileSdk = 34
    defaultConfig {
        applicationId = "com.hotwheels.command"
        minSdk = 31
        targetSdk = 34
        versionCode = 1
        versionName = "1.0.0"
    }
    buildFeatures { compose = true }
}

dependencies {
    implementation(platform("androidx.compose:compose-bom:2024.10.00"))
    implementation("androidx.compose.ui:ui")
    implementation("androidx.compose.material3:material3")
    implementation("androidx.compose.ui:ui-tooling-preview")
    implementation("androidx.activity:activity-compose:1.9.3")
    implementation("androidx.lifecycle:lifecycle-viewmodel-compose:2.8.7")
    implementation("androidx.lifecycle:lifecycle-runtime-compose:2.8.7")
    implementation("org.jetbrains.kotlinx:kotlinx-coroutines-android:1.9.0")

    testImplementation("junit:junit:4.13.2")
    testImplementation("org.jetbrains.kotlinx:kotlinx-coroutines-test:1.9.0")
    testImplementation("io.mockk:mockk:1.13.13")
}
```

Pas de Hilt/Koin : projet trop petit, instanciation manuelle via `viewModelFactory`.

---

## 9. Stratégie de tests

### 9.1 Tests unitaires (JVM, TDD)

| Test | Vérifie |
|------|---------|
| `DriveViewModelTest.inverseValue` | Slider à 56 → service reçoit `-56` |
| `DriveViewModelTest.springBackOnRelease` | `onSliderReleased` → `setTargetValue(0)` immédiat |
| `DriveViewModelTest.boundsClamping` | Slider clampé à [-100, 100] |
| `CarConnectionTest.protocolFormat` | Écrit exactement `"-56\n"` (ASCII US + newline) |
| `CarConnectionTest.throttleMin2ms` | Deux changements rapides → 2ème écriture attend ≥ 2 ms |
| `CarConnectionTest.heartbeat50ms` | Aucun changement → renvoi à 50 ms |
| `CarConnectionTest.errorMovesToReconnecting` | `IOException` → state `Reconnecting` |
| `CarConnectionTest.zeroOnClose` | `close()` écrit `"0\n"` avant fermeture du socket |

### 9.2 Test instrumenté (optionnel pour MVP)

- Slider geste tactile via `ComposeTestRule` → vérifier que ViewModel reçoit la valeur attendue.

### 9.3 Test manuel (validation finale)

Procédure dans `docs/test-procedure.md` :

1. Appairer l'ESP32 dans les réglages Android (PIN par défaut si exigé).
2. Lancer l'app, sélectionner `HotWheels_V1`.
3. Glisser le slider, vérifier sur le moniteur série Arduino (115 200 baud) que les valeurs reçues sont **bien l'inverse** du slider.
4. Vérifier la réactivité visuelle (le moteur réagit instantanément au geste).
5. Sortir de l'app via Home → vérifier que le moteur s'arrête immédiatement (`Commande: 0` côté série).
6. Revenir dans l'app → vérifier reprise sans reconnexion visible.
7. Tirer le tiroir de notifications → vérifier que le pilotage continue.
8. Couper la voiture pendant pilotage → vérifier passage en `Reconnecting` puis `Failed`.
9. Rallumer la voiture → vérifier reconnexion automatique au retour utilisateur.

---

## 10. Outillage et distribution

- **Build APK debug** : `./gradlew :app:assembleDebug` → `app/build/outputs/apk/debug/app-debug.apk`
- **Build APK release signé** : keystore généré localement (procédure documentée séparément), `./gradlew :app:assembleRelease`
- **Install direct** : `./gradlew :app:installDebug` (USB debug ou Wi-Fi ADB)
- **Distribution** : sideload de l'APK sur le Poco X6 Pro, pas de Play Store.
- **Pas de CI** pour le MVP.

---

## 11. Hors scope (MVP)

- Profil multi-voitures avec sélecteur dans le menu (architecture compatible mais UI non implémentée).
- Téléchargement OTA du firmware ESP32.
- Réglages avancés (sensibilité du slider, courbe d'accélération).
- Mode jumelage (pairing) in-app — délégué aux paramètres Android.
- Logs / historique de pilotage.
- Mode démo sans BT.
- Support Android < 12.
- Support iOS (impossible avec SPP sans MFi).

---

## 12. Risques et points de vigilance

| Risque | Mitigation |
|--------|------------|
| `String.readStringUntil` côté firmware alloue dynamiquement à chaque message | Throttle minimum 2 ms = pic théorique ≤ 500 msg/s ; en pratique le slider change rarement plus de ~50 fois/s, et le heartbeat ajoute 20 msg/s en idle. Charge typique réelle : ~50–70 msg/s. Acceptable pour MVP, à mesurer. |
| `outputStream.write` bloquant si le buffer Bluetooth saturé | Mesuré pendant tests manuels ; si > 50 ms régulièrement, envisager un `flush()` plus fréquent ou un buffer dédié. |
| Auto-connect peut prendre 1-3 s sur certains téléphones | UX : afficher l'écran de pilotage en mode `Connecting` pendant ce temps, ne pas bloquer sur un splash. |
| Notification persistante peut être perçue comme intrusive | Silencieuse (channel `LOW`), avec actions utiles ("Couper le moteur"). |
| Spring-back instantané peut surprendre si geste imprécis | Acté comme intentionnel pour la sécurité. À ré-évaluer après essais réels. |
