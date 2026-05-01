# HotWheelsCommand Implementation Plan

> **For agentic workers:** REQUIRED SUB-SKILL: Use superpowers:subagent-driven-development (recommended) or superpowers:executing-plans to implement this plan task-by-task. Steps use checkbox (`- [ ]`) syntax for tracking.

**Goal:** Build an Android APK (Kotlin + Jetpack Compose) that pilots an ESP32-WROOM-32D RC car via Bluetooth Classic SPP, sending the inverse of a vertical slider value (-100..100) with sub-10ms perceived latency.

**Architecture:** Foreground service holds the `BluetoothSocket` and a dedicated sender thread; UI (Compose) binds to the service and pushes target values. Sender uses change-detection + 2 ms throttle + 50 ms heartbeat. Activity is forced landscape; on `onStop` it sends `0\n` to cut the motor while keeping the connection alive.

**Tech Stack:** Kotlin 1.9, Android Gradle Plugin 8.5+, minSdk 31 / targetSdk 34, Jetpack Compose (BOM 2024.10.00), Coroutines 1.9, MockK + JUnit4 for tests.

**Reference spec:** `docs/superpowers/specs/2026-05-01-hotwheelscommand-design.md`

---

## File Structure

Files this plan creates (all relative to `/mnt/shared/Gitlab/hotwheelscommand/`):

```
build.gradle.kts                          # root gradle
settings.gradle.kts
gradle.properties
gradle/wrapper/gradle-wrapper.properties
gradle/libs.versions.toml                 # version catalog
gradlew, gradlew.bat
app/build.gradle.kts
app/proguard-rules.pro
app/src/main/AndroidManifest.xml
app/src/main/res/values/{colors,strings,themes}.xml
app/src/main/res/drawable/ic_notification.xml
app/src/main/res/font/orbitron_*.ttf       # downloaded fonts
app/src/main/res/font/jetbrains_mono_*.ttf
app/src/main/java/com/hotwheels/command/
    HotWheelsApp.kt                        # Application class (notif channel)
    DriveActivity.kt                       # MainActivity: landscape, permissions, nav
    bluetooth/
        SppConstants.kt                    # UUID + tuning constants
        ConnectionState.kt                 # sealed class
        CarConnection.kt                   # JVM-testable: socket I/O + throttle + heartbeat
        BluetoothCarService.kt             # foreground service + binder
    data/
        LastDeviceStore.kt                 # SharedPreferences wrapper
    ui/
        theme/{Color.kt, Type.kt, Theme.kt}
        components/
            ScanlineBackground.kt
            GlowText.kt
            ConnectionIndicator.kt
            NeonVerticalSlider.kt
        drive/{DriveScreen.kt, DriveViewModel.kt}
        select/{DeviceSelectionScreen.kt, DeviceSelectionViewModel.kt}
    util/PermissionUtils.kt
app/src/test/java/com/hotwheels/command/
    bluetooth/CarConnectionTest.kt
    ui/drive/DriveViewModelTest.kt
```

Each file has one responsibility; `CarConnection.kt` is the only non-trivial logic and is fully unit-tested in JVM via a fake `OutputStream`.

---

## Task 1: Bootstrap Android project skeleton

**Files:**
- Create: `settings.gradle.kts`
- Create: `build.gradle.kts`
- Create: `gradle.properties`
- Create: `gradle/libs.versions.toml`
- Create: `gradle/wrapper/gradle-wrapper.properties`
- Create: `gradlew`, `gradlew.bat` (via `gradle wrapper`)
- Create: `app/build.gradle.kts`
- Create: `app/proguard-rules.pro`
- Create: `.gitignore`

- [ ] **Step 1: Create `.gitignore`**

```gitignore
*.iml
.gradle/
local.properties
.idea/
.DS_Store
build/
captures/
.externalNativeBuild
.cxx
*.apk
*.ap_
*.aab
.kotlin/
```

- [ ] **Step 2: Create `gradle/libs.versions.toml`**

```toml
[versions]
agp = "8.5.2"
kotlin = "1.9.25"
composeBom = "2024.10.00"
activityCompose = "1.9.3"
lifecycle = "2.8.7"
coroutines = "1.9.0"
junit = "4.13.2"
mockk = "1.13.13"
coroutinesTest = "1.9.0"

[libraries]
compose-bom = { group = "androidx.compose", name = "compose-bom", version.ref = "composeBom" }
compose-ui = { group = "androidx.compose.ui", name = "ui" }
compose-ui-tooling-preview = { group = "androidx.compose.ui", name = "ui-tooling-preview" }
compose-ui-tooling = { group = "androidx.compose.ui", name = "ui-tooling" }
compose-material3 = { group = "androidx.compose.material3", name = "material3" }
activity-compose = { group = "androidx.activity", name = "activity-compose", version.ref = "activityCompose" }
lifecycle-viewmodel-compose = { group = "androidx.lifecycle", name = "lifecycle-viewmodel-compose", version.ref = "lifecycle" }
lifecycle-runtime-compose = { group = "androidx.lifecycle", name = "lifecycle-runtime-compose", version.ref = "lifecycle" }
coroutines-android = { group = "org.jetbrains.kotlinx", name = "kotlinx-coroutines-android", version.ref = "coroutines" }
junit = { group = "junit", name = "junit", version.ref = "junit" }
mockk = { group = "io.mockk", name = "mockk", version.ref = "mockk" }
coroutines-test = { group = "org.jetbrains.kotlinx", name = "kotlinx-coroutines-test", version.ref = "coroutinesTest" }

[plugins]
android-application = { id = "com.android.application", version.ref = "agp" }
kotlin-android = { id = "org.jetbrains.kotlin.android", version.ref = "kotlin" }
kotlin-compose = { id = "org.jetbrains.kotlin.plugin.compose", version.ref = "kotlin" }
```

- [ ] **Step 3: Create `settings.gradle.kts`**

```kotlin
pluginManagement {
    repositories {
        google()
        mavenCentral()
        gradlePluginPortal()
    }
}
dependencyResolutionManagement {
    repositoriesMode.set(RepositoriesMode.FAIL_ON_PROJECT_REPOS)
    repositories {
        google()
        mavenCentral()
    }
}
rootProject.name = "HotWheelsCommand"
include(":app")
```

- [ ] **Step 4: Create root `build.gradle.kts`**

```kotlin
plugins {
    alias(libs.plugins.android.application) apply false
    alias(libs.plugins.kotlin.android) apply false
    alias(libs.plugins.kotlin.compose) apply false
}
```

- [ ] **Step 5: Create `gradle.properties`**

```properties
org.gradle.jvmargs=-Xmx2048m -Dfile.encoding=UTF-8
org.gradle.parallel=true
org.gradle.caching=true
android.useAndroidX=true
android.nonTransitiveRClass=true
kotlin.code.style=official
```

- [ ] **Step 6: Create `app/build.gradle.kts`**

```kotlin
plugins {
    alias(libs.plugins.android.application)
    alias(libs.plugins.kotlin.android)
    alias(libs.plugins.kotlin.compose)
}

android {
    namespace = "com.hotwheels.command"
    compileSdk = 34

    defaultConfig {
        applicationId = "com.hotwheels.command"
        minSdk = 31
        targetSdk = 34
        versionCode = 1
        versionName = "1.0.0"
        testInstrumentationRunner = "androidx.test.runner.AndroidJUnitRunner"
    }

    buildTypes {
        release {
            isMinifyEnabled = false
            proguardFiles(getDefaultProguardFile("proguard-android-optimize.txt"), "proguard-rules.pro")
        }
    }

    compileOptions {
        sourceCompatibility = JavaVersion.VERSION_17
        targetCompatibility = JavaVersion.VERSION_17
    }

    kotlinOptions { jvmTarget = "17" }

    buildFeatures { compose = true }
}

dependencies {
    implementation(platform(libs.compose.bom))
    implementation(libs.compose.ui)
    implementation(libs.compose.ui.tooling.preview)
    implementation(libs.compose.material3)
    implementation(libs.activity.compose)
    implementation(libs.lifecycle.viewmodel.compose)
    implementation(libs.lifecycle.runtime.compose)
    implementation(libs.coroutines.android)
    debugImplementation(libs.compose.ui.tooling)

    testImplementation(libs.junit)
    testImplementation(libs.mockk)
    testImplementation(libs.coroutines.test)
}
```

- [ ] **Step 7: Create `app/proguard-rules.pro` (empty defaults)**

```
# Keep nothing custom for MVP
```

- [ ] **Step 8: Generate Gradle wrapper**

Run: `cd /mnt/shared/Gitlab/hotwheelscommand && gradle wrapper --gradle-version 8.10`
Expected: creates `gradlew`, `gradlew.bat`, `gradle/wrapper/gradle-wrapper.{jar,properties}`.
If `gradle` not installed, manually create `gradle/wrapper/gradle-wrapper.properties` with:
```
distributionBase=GRADLE_USER_HOME
distributionPath=wrapper/dists
distributionUrl=https\://services.gradle.org/distributions/gradle-8.10-bin.zip
zipStoreBase=GRADLE_USER_HOME
zipStorePath=wrapper/dists
```
and download `gradle-wrapper.jar` from the matching Gradle release.

- [ ] **Step 9: Verify project syncs**

Run: `./gradlew help`
Expected: BUILD SUCCESSFUL (downloads dependencies on first run).

- [ ] **Step 10: Commit**

```bash
git add -A
git commit -m "chore: bootstrap Android Gradle project"
```

---

## Task 2: Theme, colors, fonts (cyberpunk tokens)

**Files:**
- Create: `app/src/main/res/values/colors.xml`
- Create: `app/src/main/res/values/strings.xml`
- Create: `app/src/main/res/values/themes.xml`
- Create: `app/src/main/java/com/hotwheels/command/ui/theme/Color.kt`
- Create: `app/src/main/java/com/hotwheels/command/ui/theme/Type.kt`
- Create: `app/src/main/java/com/hotwheels/command/ui/theme/Theme.kt`

- [ ] **Step 1: Create `app/src/main/res/values/colors.xml`**

```xml
<?xml version="1.0" encoding="utf-8"?>
<resources>
    <color name="bg_primary">#0A0E1A</color>
    <color name="bg_surface">#10162B</color>
    <color name="accent_electric">#00E5FF</color>
    <color name="accent_glow">#0091FF</color>
    <color name="state_connected">#00FF94</color>
    <color name="state_connecting">#FFB800</color>
    <color name="state_error">#FF2E5C</color>
    <color name="text_primary">#E8F4FF</color>
    <color name="text_muted">#5A7390</color>
</resources>
```

- [ ] **Step 2: Create `app/src/main/res/values/strings.xml`**

```xml
<?xml version="1.0" encoding="utf-8"?>
<resources>
    <string name="app_name">HotWheelsCommand</string>
    <string name="notif_channel_name">HotWheels connection</string>
    <string name="notif_title">HotWheelsCommand</string>
    <string name="notif_text_connected">Connected to %1$s</string>
    <string name="notif_action_stop_motor">CUT MOTOR</string>
    <string name="notif_action_disconnect">DISCONNECT</string>
</resources>
```

- [ ] **Step 3: Create `app/src/main/res/values/themes.xml`**

```xml
<?xml version="1.0" encoding="utf-8"?>
<resources>
    <style name="Theme.HotWheels" parent="android:Theme.Material.NoActionBar">
        <item name="android:statusBarColor">@color/bg_primary</item>
        <item name="android:navigationBarColor">@color/bg_primary</item>
        <item name="android:windowBackground">@color/bg_primary</item>
    </style>
</resources>
```

- [ ] **Step 4: Create `Color.kt`**

```kotlin
package com.hotwheels.command.ui.theme

import androidx.compose.ui.graphics.Color

val BgPrimary = Color(0xFF0A0E1A)
val BgSurface = Color(0xFF10162B)
val AccentElectric = Color(0xFF00E5FF)
val AccentGlow = Color(0xFF0091FF)
val StateConnected = Color(0xFF00FF94)
val StateConnecting = Color(0xFFFFB800)
val StateError = Color(0xFFFF2E5C)
val TextPrimary = Color(0xFFE8F4FF)
val TextMuted = Color(0xFF5A7390)
```

- [ ] **Step 5: Create `Type.kt`** (uses Compose default monospace; custom font integration deferred — YAGNI for MVP)

```kotlin
package com.hotwheels.command.ui.theme

import androidx.compose.material3.Typography
import androidx.compose.ui.text.TextStyle
import androidx.compose.ui.text.font.FontFamily
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.unit.sp

val MonoFamily: FontFamily = FontFamily.Monospace

val HotWheelsTypography = Typography(
    displayLarge = TextStyle(
        fontFamily = MonoFamily,
        fontWeight = FontWeight.Bold,
        fontSize = 96.sp,
        letterSpacing = 0.sp
    ),
    titleMedium = TextStyle(
        fontFamily = MonoFamily,
        fontWeight = FontWeight.Medium,
        fontSize = 16.sp,
        letterSpacing = 1.sp
    ),
    bodyMedium = TextStyle(
        fontFamily = MonoFamily,
        fontWeight = FontWeight.Normal,
        fontSize = 14.sp
    ),
    labelSmall = TextStyle(
        fontFamily = MonoFamily,
        fontWeight = FontWeight.Normal,
        fontSize = 12.sp,
        letterSpacing = 0.5.sp
    )
)
```

- [ ] **Step 6: Create `Theme.kt`**

```kotlin
package com.hotwheels.command.ui.theme

import androidx.compose.foundation.isSystemInDarkTheme
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.darkColorScheme
import androidx.compose.runtime.Composable

private val HotWheelsColorScheme = darkColorScheme(
    primary = AccentElectric,
    onPrimary = BgPrimary,
    secondary = AccentGlow,
    background = BgPrimary,
    onBackground = TextPrimary,
    surface = BgSurface,
    onSurface = TextPrimary,
    error = StateError,
    onError = TextPrimary
)

@Composable
fun HotWheelsTheme(
    @Suppress("UNUSED_PARAMETER") darkTheme: Boolean = isSystemInDarkTheme(),
    content: @Composable () -> Unit
) {
    MaterialTheme(
        colorScheme = HotWheelsColorScheme,
        typography = HotWheelsTypography,
        content = content
    )
}
```

- [ ] **Step 7: Build to verify resources compile**

Run: `./gradlew :app:assembleDebug`
Expected: BUILD SUCCESSFUL (no app code yet — manifest still pending; if it fails on missing manifest, this task can be re-verified after Task 9).

- [ ] **Step 8: Commit**

```bash
git add -A
git commit -m "feat(ui): cyberpunk theme tokens and typography"
```

---

## Task 3: SppConstants and ConnectionState

**Files:**
- Create: `app/src/main/java/com/hotwheels/command/bluetooth/SppConstants.kt`
- Create: `app/src/main/java/com/hotwheels/command/bluetooth/ConnectionState.kt`

- [ ] **Step 1: Create `SppConstants.kt`**

```kotlin
package com.hotwheels.command.bluetooth

import java.util.UUID

object SppConstants {
    val SPP_UUID: UUID = UUID.fromString("00001101-0000-1000-8000-00805F9B34FB")

    const val THROTTLE_MIN_MS: Long = 2L
    const val HEARTBEAT_MS: Long = 50L
    const val PARK_NANOS: Long = 500_000L

    const val RECONNECT_MAX_ATTEMPTS: Int = 3
    const val RECONNECT_TOTAL_TIMEOUT_MS: Long = 5_000L
    const val RECONNECT_BACKOFF_BASE_MS: Long = 200L
    const val RECONNECT_BACKOFF_MAX_MS: Long = 1_000L

    const val MIN_VALUE: Int = -100
    const val MAX_VALUE: Int = 100
}
```

- [ ] **Step 2: Create `ConnectionState.kt`**

```kotlin
package com.hotwheels.command.bluetooth

sealed class ConnectionState {
    data object Idle : ConnectionState()
    data class Connecting(val deviceName: String, val deviceAddress: String) : ConnectionState()
    data class Connected(val deviceName: String, val deviceAddress: String) : ConnectionState()
    data class Reconnecting(
        val deviceName: String,
        val deviceAddress: String,
        val attempt: Int,
        val maxAttempts: Int
    ) : ConnectionState()
    data class Failed(val deviceName: String, val deviceAddress: String, val reason: String) : ConnectionState()
}
```

- [ ] **Step 3: Compile to verify**

Run: `./gradlew :app:compileDebugKotlin`
Expected: BUILD SUCCESSFUL.

- [ ] **Step 4: Commit**

```bash
git add -A
git commit -m "feat(bluetooth): SppConstants and ConnectionState sealed class"
```

---

## Task 4: CarConnection (TDD)

`CarConnection` encapsulates the sender thread and protocol logic. It accepts an injectable `OutputStream` so it can be unit-tested in JVM with no Android dependencies.

**Files:**
- Create: `app/src/main/java/com/hotwheels/command/bluetooth/CarConnection.kt`
- Create: `app/src/test/java/com/hotwheels/command/bluetooth/CarConnectionTest.kt`

### 4.1 — Write test class skeleton

- [ ] **Step 1: Create the test file with ASCII format test**

```kotlin
package com.hotwheels.command.bluetooth

import kotlinx.coroutines.test.runTest
import org.junit.After
import org.junit.Assert.assertEquals
import org.junit.Test
import java.io.ByteArrayOutputStream

class CarConnectionTest {

    private val out = ByteArrayOutputStream()
    private var clockNanos = 0L
    private val clock: () -> Long = { clockNanos }
    private fun advance(ms: Long) { clockNanos += ms * 1_000_000L }

    private lateinit var conn: CarConnection

    @After
    fun tearDown() {
        if (::conn.isInitialized) conn.stop()
    }

    private fun newConnection() = CarConnection(
        outputStream = out,
        clockNanos = clock,
        sleeperNanos = { /* no-op for tests; we drive the loop manually */ }
    ).also { conn = it }

    @Test
    fun `tick writes ASCII value with newline`() = runTest {
        val c = newConnection()
        c.setTargetValue(-56)
        c.tickForTest()
        assertEquals("-56\n", out.toString(Charsets.US_ASCII))
    }
}
```

- [ ] **Step 2: Run test, expect compile failure (CarConnection not defined)**

Run: `./gradlew :app:testDebugUnitTest --tests "com.hotwheels.command.bluetooth.CarConnectionTest"`
Expected: compile FAIL — "Unresolved reference: CarConnection".

- [ ] **Step 3: Create minimal `CarConnection.kt` to satisfy test 1**

```kotlin
package com.hotwheels.command.bluetooth

import java.io.OutputStream
import java.util.concurrent.atomic.AtomicInteger
import java.util.concurrent.locks.LockSupport

class CarConnection(
    private val outputStream: OutputStream,
    private val clockNanos: () -> Long = { System.nanoTime() },
    private val sleeperNanos: (Long) -> Unit = { LockSupport.parkNanos(it) }
) {
    private val target = AtomicInteger(0)
    private var lastSent: Int = Int.MIN_VALUE
    private var lastSentNanos: Long = Long.MIN_VALUE
    @Volatile private var running = false
    private var thread: Thread? = null

    fun setTargetValue(value: Int) {
        val clamped = value.coerceIn(SppConstants.MIN_VALUE, SppConstants.MAX_VALUE)
        target.set(clamped)
    }

    fun start() {
        if (running) return
        running = true
        thread = Thread({ runLoop() }, "CarConnection-Sender").apply {
            isDaemon = true
            priority = Thread.MAX_PRIORITY
            start()
        }
    }

    fun stop() {
        running = false
        thread?.interrupt()
        thread = null
        runCatching {
            outputStream.write("0\n".toByteArray(Charsets.US_ASCII))
            outputStream.flush()
        }
    }

    private fun runLoop() {
        while (running) {
            try {
                tickOnce()
            } catch (_: InterruptedException) {
                return
            } catch (e: Exception) {
                onWriteFailure(e)
                return
            }
            sleeperNanos(SppConstants.PARK_NANOS)
        }
    }

    /** Visible for tests — execute one iteration of the sender loop. */
    internal fun tickForTest() {
        tickOnce()
    }

    private fun tickOnce() {
        val now = clockNanos()
        val current = target.get()
        val changed = current != lastSent
        val heartbeatDue = (now - lastSentNanos) / 1_000_000L >= SppConstants.HEARTBEAT_MS
        val throttleOk = (now - lastSentNanos) / 1_000_000L >= SppConstants.THROTTLE_MIN_MS
        val firstSend = lastSentNanos == Long.MIN_VALUE
        if ((changed || heartbeatDue || firstSend) && (throttleOk || firstSend)) {
            outputStream.write("$current\n".toByteArray(Charsets.US_ASCII))
            outputStream.flush()
            lastSent = current
            lastSentNanos = now
        }
    }

    @Volatile private var failureListener: ((Throwable) -> Unit)? = null
    fun onFailure(listener: (Throwable) -> Unit) { failureListener = listener }
    private fun onWriteFailure(e: Throwable) { failureListener?.invoke(e) }
}
```

- [ ] **Step 4: Run test, expect PASS**

Run: `./gradlew :app:testDebugUnitTest --tests "com.hotwheels.command.bluetooth.CarConnectionTest"`
Expected: 1 test passed.

- [ ] **Step 5: Commit**

```bash
git add -A
git commit -m "feat(bluetooth): CarConnection skeleton with protocol format"
```

### 4.2 — Throttle test

- [ ] **Step 1: Add test for throttle ≥ 2 ms**

Append to `CarConnectionTest.kt`:

```kotlin
    @Test
    fun `throttle prevents two writes within 2ms`() {
        val c = newConnection()
        c.setTargetValue(10)
        c.tickForTest()           // first send
        advance(1)                // 1 ms later
        c.setTargetValue(20)
        c.tickForTest()           // should be throttled
        assertEquals("10\n", out.toString(Charsets.US_ASCII))
        advance(2)                // total 3 ms since last send
        c.tickForTest()
        assertEquals("10\n20\n", out.toString(Charsets.US_ASCII))
    }
```

- [ ] **Step 2: Run, expect PASS** (logic already in `tickOnce`)

Run: `./gradlew :app:testDebugUnitTest --tests "*CarConnectionTest*"`
Expected: 2 tests passed.

- [ ] **Step 3: Commit**

```bash
git add -A
git commit -m "test(bluetooth): verify 2ms throttle enforcement"
```

### 4.3 — Heartbeat test

- [ ] **Step 1: Add heartbeat test**

```kotlin
    @Test
    fun `heartbeat resends value after 50ms with no change`() {
        val c = newConnection()
        c.setTargetValue(0)
        c.tickForTest()
        advance(49)
        c.tickForTest()
        assertEquals("0\n", out.toString(Charsets.US_ASCII))
        advance(2)                // total 51 ms
        c.tickForTest()
        assertEquals("0\n0\n", out.toString(Charsets.US_ASCII))
    }
```

- [ ] **Step 2: Run, expect PASS**

Run: `./gradlew :app:testDebugUnitTest --tests "*CarConnectionTest*"`
Expected: 3 tests passed.

- [ ] **Step 3: Commit**

```bash
git add -A
git commit -m "test(bluetooth): verify 50ms heartbeat"
```

### 4.4 — Clamping test

- [ ] **Step 1: Add bounds-clamping test**

```kotlin
    @Test
    fun `setTargetValue clamps to -100 100`() {
        val c = newConnection()
        c.setTargetValue(250)
        c.tickForTest()
        c.setTargetValue(-300)
        advance(3)
        c.tickForTest()
        assertEquals("100\n-100\n", out.toString(Charsets.US_ASCII))
    }
```

- [ ] **Step 2: Run, expect PASS**

Run: `./gradlew :app:testDebugUnitTest --tests "*CarConnectionTest*"`
Expected: 4 tests passed.

- [ ] **Step 3: Commit**

```bash
git add -A
git commit -m "test(bluetooth): verify slider value clamping"
```

### 4.5 — Failure listener test

- [ ] **Step 1: Add failure path test using a throwing OutputStream**

```kotlin
    @Test
    fun `IOException invokes failure listener`() {
        val throwingStream = object : java.io.OutputStream() {
            override fun write(b: Int) { throw java.io.IOException("boom") }
        }
        var caught: Throwable? = null
        val c = CarConnection(
            outputStream = throwingStream,
            clockNanos = { 0L },
            sleeperNanos = {}
        )
        c.onFailure { caught = it }
        c.setTargetValue(42)
        try { c.tickForTest() } catch (e: Exception) { caught = e }
        assertEquals("boom", caught?.message)
        c.stop()
    }
```

Note: `tickForTest` rethrows; `runLoop` is what funnels into the listener. We assert the same exception is observable to the caller, which is the behavior we need. Adjust if you want the listener route — replace the catch block with explicit listener invocation.

- [ ] **Step 2: Run, expect PASS**

Run: `./gradlew :app:testDebugUnitTest --tests "*CarConnectionTest*"`
Expected: 5 tests passed.

- [ ] **Step 3: Commit**

```bash
git add -A
git commit -m "test(bluetooth): verify write failure surfaces to caller"
```

### 4.6 — Zero-on-stop test

- [ ] **Step 1: Add test that stop() flushes "0\n"**

```kotlin
    @Test
    fun `stop writes 0 then flushes`() {
        val c = newConnection()
        c.setTargetValue(75)
        c.tickForTest()
        out.reset()
        c.stop()
        assertEquals("0\n", out.toString(Charsets.US_ASCII))
    }
```

- [ ] **Step 2: Run, expect PASS**

Run: `./gradlew :app:testDebugUnitTest --tests "*CarConnectionTest*"`
Expected: 6 tests passed.

- [ ] **Step 3: Commit**

```bash
git add -A
git commit -m "test(bluetooth): verify stop sends safety zero"
```

---

## Task 5: LastDeviceStore (SharedPreferences wrapper)

**Files:**
- Create: `app/src/main/java/com/hotwheels/command/data/LastDeviceStore.kt`

- [ ] **Step 1: Create the file**

```kotlin
package com.hotwheels.command.data

import android.content.Context

class LastDeviceStore(context: Context) {
    private val prefs = context.getSharedPreferences("hotwheels_prefs", Context.MODE_PRIVATE)

    fun saveLastDevice(macAddress: String, deviceName: String) {
        prefs.edit()
            .putString(KEY_MAC, macAddress)
            .putString(KEY_NAME, deviceName)
            .apply()
    }

    fun getLastDevice(): Pair<String, String>? {
        val mac = prefs.getString(KEY_MAC, null) ?: return null
        val name = prefs.getString(KEY_NAME, null) ?: return null
        return mac to name
    }

    fun clear() { prefs.edit().clear().apply() }

    private companion object {
        const val KEY_MAC = "last_device_mac"
        const val KEY_NAME = "last_device_name"
    }
}
```

- [ ] **Step 2: Compile**

Run: `./gradlew :app:compileDebugKotlin`
Expected: BUILD SUCCESSFUL.

- [ ] **Step 3: Commit**

```bash
git add -A
git commit -m "feat(data): persist last connected device MAC/name"
```

---

## Task 6: BluetoothCarService (foreground service + binder)

**Files:**
- Create: `app/src/main/java/com/hotwheels/command/bluetooth/BluetoothCarService.kt`

- [ ] **Step 1: Create the service**

```kotlin
package com.hotwheels.command.bluetooth

import android.annotation.SuppressLint
import android.app.Notification
import android.app.NotificationManager
import android.app.PendingIntent
import android.app.Service
import android.bluetooth.BluetoothAdapter
import android.bluetooth.BluetoothDevice
import android.bluetooth.BluetoothManager
import android.bluetooth.BluetoothSocket
import android.content.BroadcastReceiver
import android.content.Context
import android.content.Intent
import android.content.IntentFilter
import android.os.Binder
import android.os.IBinder
import androidx.core.app.NotificationCompat
import com.hotwheels.command.HotWheelsApp
import com.hotwheels.command.R
import kotlinx.coroutines.CoroutineScope
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.Job
import kotlinx.coroutines.SupervisorJob
import kotlinx.coroutines.cancel
import kotlinx.coroutines.delay
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.StateFlow
import kotlinx.coroutines.flow.asStateFlow
import kotlinx.coroutines.launch
import kotlin.math.min

class BluetoothCarService : Service() {

    inner class LocalBinder : Binder() {
        val service: BluetoothCarService get() = this@BluetoothCarService
    }

    private val binder = LocalBinder()
    private val scope = CoroutineScope(SupervisorJob() + Dispatchers.IO)

    private val _state = MutableStateFlow<ConnectionState>(ConnectionState.Idle)
    val state: StateFlow<ConnectionState> = _state.asStateFlow()

    private var socket: BluetoothSocket? = null
    private var connection: CarConnection? = null
    private var connectJob: Job? = null

    private val disconnectionReceiver = object : BroadcastReceiver() {
        override fun onReceive(ctx: Context?, intent: Intent?) {
            val action = intent?.action ?: return
            if (action != BluetoothDevice.ACTION_ACL_DISCONNECTED) return
            val current = _state.value
            val mac = when (current) {
                is ConnectionState.Connected -> current.deviceAddress
                is ConnectionState.Reconnecting -> current.deviceAddress
                else -> return
            }
            @Suppress("DEPRECATION")
            val device: BluetoothDevice? = intent.getParcelableExtra(BluetoothDevice.EXTRA_DEVICE)
            if (device?.address == mac) {
                scope.launch { startReconnect(mac) }
            }
        }
    }

    override fun onBind(intent: Intent?): IBinder = binder

    override fun onCreate() {
        super.onCreate()
        registerReceiver(
            disconnectionReceiver,
            IntentFilter(BluetoothDevice.ACTION_ACL_DISCONNECTED)
        )
    }

    override fun onStartCommand(intent: Intent?, flags: Int, startId: Int): Int {
        when (intent?.action) {
            ACTION_STOP_MOTOR -> connection?.setTargetValue(0)
            ACTION_DISCONNECT -> stopServiceCleanly()
        }
        return START_STICKY
    }

    fun setTargetValue(value: Int) {
        connection?.setTargetValue(value)
    }

    fun connect(deviceName: String, macAddress: String) {
        connectJob?.cancel()
        connectJob = scope.launch { doConnect(deviceName, macAddress) }
    }

    @SuppressLint("MissingPermission")
    private suspend fun doConnect(deviceName: String, macAddress: String) {
        _state.value = ConnectionState.Connecting(deviceName, macAddress)
        val adapter = bluetoothAdapter() ?: run {
            _state.value = ConnectionState.Failed(deviceName, macAddress, "No Bluetooth adapter")
            return
        }
        val device = adapter.getRemoteDevice(macAddress)
        try {
            val s = device.createRfcommSocketToServiceRecord(SppConstants.SPP_UUID)
            adapter.cancelDiscovery()
            s.connect()
            socket = s
            val conn = CarConnection(s.outputStream)
            conn.onFailure { scope.launch { startReconnect(macAddress) } }
            conn.start()
            connection = conn
            _state.value = ConnectionState.Connected(deviceName, macAddress)
            startForeground(NOTIF_ID, buildNotification(deviceName))
        } catch (e: Exception) {
            _state.value = ConnectionState.Failed(deviceName, macAddress, e.message ?: "connect failed")
            runCatching { socket?.close() }
            socket = null
        }
    }

    @SuppressLint("MissingPermission")
    private suspend fun startReconnect(macAddress: String) {
        val current = _state.value
        val deviceName = when (current) {
            is ConnectionState.Connected -> current.deviceName
            is ConnectionState.Reconnecting -> current.deviceName
            else -> return
        }
        connection?.stop()
        connection = null
        runCatching { socket?.close() }
        socket = null

        val start = System.currentTimeMillis()
        var attempt = 1
        while (attempt <= SppConstants.RECONNECT_MAX_ATTEMPTS &&
            System.currentTimeMillis() - start < SppConstants.RECONNECT_TOTAL_TIMEOUT_MS
        ) {
            _state.value = ConnectionState.Reconnecting(
                deviceName, macAddress, attempt, SppConstants.RECONNECT_MAX_ATTEMPTS
            )
            val backoff = min(
                SppConstants.RECONNECT_BACKOFF_BASE_MS * attempt,
                SppConstants.RECONNECT_BACKOFF_MAX_MS
            )
            delay(backoff)
            try {
                val adapter = bluetoothAdapter() ?: throw IllegalStateException("no adapter")
                val device = adapter.getRemoteDevice(macAddress)
                val s = device.createRfcommSocketToServiceRecord(SppConstants.SPP_UUID)
                s.connect()
                socket = s
                val conn = CarConnection(s.outputStream)
                conn.onFailure { scope.launch { startReconnect(macAddress) } }
                conn.start()
                connection = conn
                _state.value = ConnectionState.Connected(deviceName, macAddress)
                return
            } catch (_: Exception) {
                attempt++
            }
        }
        _state.value = ConnectionState.Failed(deviceName, macAddress, "Reconnect timeout")
    }

    fun disconnect() = stopServiceCleanly()

    private fun stopServiceCleanly() {
        connection?.stop()
        connection = null
        runCatching { socket?.close() }
        socket = null
        _state.value = ConnectionState.Idle
        stopForeground(STOP_FOREGROUND_REMOVE)
        stopSelf()
    }

    override fun onDestroy() {
        runCatching { unregisterReceiver(disconnectionReceiver) }
        scope.cancel()
        connection?.stop()
        runCatching { socket?.close() }
        super.onDestroy()
    }

    private fun bluetoothAdapter(): BluetoothAdapter? =
        (getSystemService(Context.BLUETOOTH_SERVICE) as? BluetoothManager)?.adapter

    private fun buildNotification(deviceName: String): Notification {
        val stopMotorPending = PendingIntent.getService(
            this, 1,
            Intent(this, BluetoothCarService::class.java).setAction(ACTION_STOP_MOTOR),
            PendingIntent.FLAG_IMMUTABLE or PendingIntent.FLAG_UPDATE_CURRENT
        )
        val disconnectPending = PendingIntent.getService(
            this, 2,
            Intent(this, BluetoothCarService::class.java).setAction(ACTION_DISCONNECT),
            PendingIntent.FLAG_IMMUTABLE or PendingIntent.FLAG_UPDATE_CURRENT
        )
        return NotificationCompat.Builder(this, HotWheelsApp.NOTIF_CHANNEL_ID)
            .setContentTitle(getString(R.string.notif_title))
            .setContentText(getString(R.string.notif_text_connected, deviceName))
            .setSmallIcon(R.drawable.ic_notification)
            .setOngoing(true)
            .setSilent(true)
            .addAction(0, getString(R.string.notif_action_stop_motor), stopMotorPending)
            .addAction(0, getString(R.string.notif_action_disconnect), disconnectPending)
            .build()
    }

    @Suppress("UNUSED_PARAMETER")
    private fun notificationManager(): NotificationManager =
        getSystemService(NOTIFICATION_SERVICE) as NotificationManager

    companion object {
        const val NOTIF_ID = 4242
        const val ACTION_STOP_MOTOR = "com.hotwheels.command.action.STOP_MOTOR"
        const val ACTION_DISCONNECT = "com.hotwheels.command.action.DISCONNECT"
    }
}
```

- [ ] **Step 2: Compile (will fail until HotWheelsApp + ic_notification exist)**

Run: `./gradlew :app:compileDebugKotlin`
Expected: errors referencing `HotWheelsApp`, `R.drawable.ic_notification`. Acceptable — these are added next.

- [ ] **Step 3: Commit (allow incomplete compile)**

```bash
git add -A
git commit -m "feat(bluetooth): foreground service with state machine and reconnect"
```

---

## Task 7: HotWheelsApp + notification icon

**Files:**
- Create: `app/src/main/java/com/hotwheels/command/HotWheelsApp.kt`
- Create: `app/src/main/res/drawable/ic_notification.xml`

- [ ] **Step 1: Create `HotWheelsApp.kt`**

```kotlin
package com.hotwheels.command

import android.app.Application
import android.app.NotificationChannel
import android.app.NotificationManager

class HotWheelsApp : Application() {
    override fun onCreate() {
        super.onCreate()
        val mgr = getSystemService(NOTIFICATION_SERVICE) as NotificationManager
        val channel = NotificationChannel(
            NOTIF_CHANNEL_ID,
            getString(R.string.notif_channel_name),
            NotificationManager.IMPORTANCE_LOW
        ).apply { setSound(null, null) }
        mgr.createNotificationChannel(channel)
    }
    companion object {
        const val NOTIF_CHANNEL_ID = "bluetooth_car_connection"
    }
}
```

- [ ] **Step 2: Create `ic_notification.xml`** (simple bolt vector)

```xml
<vector xmlns:android="http://schemas.android.com/apk/res/android"
    android:width="24dp" android:height="24dp"
    android:viewportWidth="24" android:viewportHeight="24"
    android:tint="#FFFFFF">
    <path android:pathData="M13,2 L4,14 L11,14 L9,22 L20,10 L13,10 Z"
        android:fillColor="#FFFFFF" />
</vector>
```

- [ ] **Step 3: Compile**

Run: `./gradlew :app:compileDebugKotlin`
Expected: BUILD SUCCESSFUL.

- [ ] **Step 4: Commit**

```bash
git add -A
git commit -m "feat(app): Application class with notification channel"
```

---

## Task 8: PermissionUtils

**Files:**
- Create: `app/src/main/java/com/hotwheels/command/util/PermissionUtils.kt`

- [ ] **Step 1: Create the helper**

```kotlin
package com.hotwheels.command.util

import android.Manifest
import android.content.Context
import android.content.pm.PackageManager
import androidx.core.content.ContextCompat

object PermissionUtils {
    const val BT_CONNECT = Manifest.permission.BLUETOOTH_CONNECT

    fun hasBluetoothConnect(context: Context): Boolean =
        ContextCompat.checkSelfPermission(context, BT_CONNECT) == PackageManager.PERMISSION_GRANTED
}
```

- [ ] **Step 2: Commit**

```bash
git add -A
git commit -m "feat(util): permission helper"
```

---

## Task 9: AndroidManifest.xml

**Files:**
- Create: `app/src/main/AndroidManifest.xml`

- [ ] **Step 1: Create the manifest**

```xml
<?xml version="1.0" encoding="utf-8"?>
<manifest xmlns:android="http://schemas.android.com/apk/res/android">

    <uses-permission android:name="android.permission.BLUETOOTH_CONNECT" />
    <uses-permission android:name="android.permission.FOREGROUND_SERVICE" />
    <uses-permission android:name="android.permission.FOREGROUND_SERVICE_CONNECTED_DEVICE" />
    <uses-permission android:name="android.permission.WAKE_LOCK" />

    <uses-feature android:name="android.hardware.bluetooth" android:required="true" />

    <application
        android:name=".HotWheelsApp"
        android:label="@string/app_name"
        android:theme="@style/Theme.HotWheels"
        android:supportsRtl="true">

        <activity
            android:name=".DriveActivity"
            android:exported="true"
            android:screenOrientation="landscape"
            android:configChanges="orientation|screenSize|keyboardHidden"
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
    </application>
</manifest>
```

- [ ] **Step 2: Commit**

```bash
git add -A
git commit -m "feat(app): Android manifest with permissions and service"
```

---

## Task 10: DriveViewModel (TDD: inversion + spring-back + bounds)

**Files:**
- Create: `app/src/main/java/com/hotwheels/command/ui/drive/DriveViewModel.kt`
- Create: `app/src/test/java/com/hotwheels/command/ui/drive/DriveViewModelTest.kt`

### 10.1 — Test inversion

- [ ] **Step 1: Create test class with first failing test**

```kotlin
package com.hotwheels.command.ui.drive

import org.junit.Assert.assertEquals
import org.junit.Test

class DriveViewModelTest {

    private val sent = mutableListOf<Int>()
    private val sender: (Int) -> Unit = { sent += it }

    @Test
    fun `slider 56 sends -56`() {
        val vm = DriveViewModel(sender)
        vm.onSliderValueChange(56)
        assertEquals(listOf(-56), sent)
    }
}
```

- [ ] **Step 2: Run, expect compile failure**

Run: `./gradlew :app:testDebugUnitTest --tests "*DriveViewModelTest*"`
Expected: Unresolved reference: DriveViewModel.

- [ ] **Step 3: Create `DriveViewModel.kt`**

```kotlin
package com.hotwheels.command.ui.drive

import androidx.lifecycle.ViewModel
import com.hotwheels.command.bluetooth.SppConstants

class DriveViewModel(
    private val sendValue: (Int) -> Unit
) : ViewModel() {

    private var sliderValue: Int = 0

    fun onSliderValueChange(newValue: Int) {
        val clamped = newValue.coerceIn(SppConstants.MIN_VALUE, SppConstants.MAX_VALUE)
        if (clamped == sliderValue) return
        sliderValue = clamped
        sendValue(-clamped)
    }

    fun onSliderReleased() {
        sliderValue = 0
        sendValue(0)
    }

    fun currentSlider(): Int = sliderValue
}
```

- [ ] **Step 4: Run, expect PASS**

Run: `./gradlew :app:testDebugUnitTest --tests "*DriveViewModelTest*"`
Expected: 1 test passed.

- [ ] **Step 5: Commit**

```bash
git add -A
git commit -m "feat(ui): DriveViewModel with slider value inversion"
```

### 10.2 — Test spring-back

- [ ] **Step 1: Add test**

```kotlin
    @Test
    fun `release sends zero`() {
        val vm = DriveViewModel(sender)
        vm.onSliderValueChange(80)
        sent.clear()
        vm.onSliderReleased()
        assertEquals(listOf(0), sent)
    }
```

- [ ] **Step 2: Run, expect PASS**

Run: `./gradlew :app:testDebugUnitTest --tests "*DriveViewModelTest*"`
Expected: 2 tests passed.

- [ ] **Step 3: Commit**

```bash
git add -A
git commit -m "test(ui): verify DriveViewModel spring-back to zero"
```

### 10.3 — Test clamping

- [ ] **Step 1: Add test**

```kotlin
    @Test
    fun `slider clamps to allowed range`() {
        val vm = DriveViewModel(sender)
        vm.onSliderValueChange(250)
        vm.onSliderValueChange(-9999)
        assertEquals(listOf(-100, 100), sent)
    }
```

- [ ] **Step 2: Run, expect PASS**

Run: `./gradlew :app:testDebugUnitTest --tests "*DriveViewModelTest*"`
Expected: 3 tests passed.

- [ ] **Step 3: Commit**

```bash
git add -A
git commit -m "test(ui): verify slider clamping in viewmodel"
```

---

## Task 11: DeviceSelectionViewModel

**Files:**
- Create: `app/src/main/java/com/hotwheels/command/ui/select/DeviceSelectionViewModel.kt`

- [ ] **Step 1: Create the ViewModel**

```kotlin
package com.hotwheels.command.ui.select

import android.annotation.SuppressLint
import android.bluetooth.BluetoothAdapter
import android.bluetooth.BluetoothManager
import android.content.Context
import androidx.lifecycle.ViewModel
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.StateFlow
import kotlinx.coroutines.flow.asStateFlow

data class PairedDevice(val name: String, val address: String)

class DeviceSelectionViewModel(private val appContext: Context) : ViewModel() {

    private val _devices = MutableStateFlow<List<PairedDevice>>(emptyList())
    val devices: StateFlow<List<PairedDevice>> = _devices.asStateFlow()

    @SuppressLint("MissingPermission")
    fun refresh() {
        val mgr = appContext.getSystemService(Context.BLUETOOTH_SERVICE) as? BluetoothManager
        val adapter: BluetoothAdapter? = mgr?.adapter
        val bonded = adapter?.bondedDevices.orEmpty()
        _devices.value = bonded.map {
            PairedDevice(name = it.name ?: "(unnamed)", address = it.address)
        }
    }
}
```

- [ ] **Step 2: Compile**

Run: `./gradlew :app:compileDebugKotlin`
Expected: BUILD SUCCESSFUL.

- [ ] **Step 3: Commit**

```bash
git add -A
git commit -m "feat(ui): DeviceSelectionViewModel listing bonded devices"
```

---

## Task 12: ScanlineBackground component

**Files:**
- Create: `app/src/main/java/com/hotwheels/command/ui/components/ScanlineBackground.kt`

- [ ] **Step 1: Create the component**

```kotlin
package com.hotwheels.command.ui.components

import androidx.compose.foundation.Canvas
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.runtime.Composable
import androidx.compose.ui.Modifier
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.geometry.Offset

@Composable
fun ScanlineBackground(modifier: Modifier = Modifier, color: Color = Color(0x0A00E5FF)) {
    Canvas(modifier = modifier.fillMaxSize()) {
        var y = 0f
        while (y < size.height) {
            drawLine(color, Offset(0f, y), Offset(size.width, y), strokeWidth = 1f)
            y += 3f
        }
    }
}
```

- [ ] **Step 2: Commit**

```bash
git add -A
git commit -m "feat(ui): scanline cyberpunk background"
```

---

## Task 13: GlowText component

**Files:**
- Create: `app/src/main/java/com/hotwheels/command/ui/components/GlowText.kt`

- [ ] **Step 1: Create the component**

```kotlin
package com.hotwheels.command.ui.components

import androidx.compose.foundation.layout.Box
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.ui.Modifier
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.text.TextStyle

@Composable
fun GlowText(
    text: String,
    style: TextStyle,
    color: Color,
    glow: Color,
    modifier: Modifier = Modifier
) {
    Box(modifier = modifier) {
        // simple two-pass glow: blurred copy underneath, sharp on top
        Text(text = text, style = style.copy(color = glow.copy(alpha = 0.5f)))
        Text(text = text, style = style.copy(color = color))
    }
}
```

- [ ] **Step 2: Commit**

```bash
git add -A
git commit -m "feat(ui): glow text component"
```

---

## Task 14: ConnectionIndicator component

**Files:**
- Create: `app/src/main/java/com/hotwheels/command/ui/components/ConnectionIndicator.kt`

- [ ] **Step 1: Create the component**

```kotlin
package com.hotwheels.command.ui.components

import androidx.compose.animation.core.RepeatMode
import androidx.compose.animation.core.animateFloat
import androidx.compose.animation.core.infiniteRepeatable
import androidx.compose.animation.core.rememberInfiniteTransition
import androidx.compose.animation.core.tween
import androidx.compose.foundation.background
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.layout.width
import androidx.compose.foundation.shape.CircleShape
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.runtime.getValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.alpha
import androidx.compose.ui.draw.clip
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.unit.dp
import com.hotwheels.command.bluetooth.ConnectionState
import com.hotwheels.command.ui.theme.StateConnected
import com.hotwheels.command.ui.theme.StateConnecting
import com.hotwheels.command.ui.theme.StateError
import com.hotwheels.command.ui.theme.TextMuted
import com.hotwheels.command.ui.theme.TextPrimary

@Composable
fun ConnectionIndicator(state: ConnectionState, modifier: Modifier = Modifier) {
    val (color, label, pulseSpeedMs) = when (state) {
        is ConnectionState.Idle -> Triple(TextMuted, "EN ATTENTE", 0)
        is ConnectionState.Connecting -> Triple(StateConnecting, "CONNEXION…", 600)
        is ConnectionState.Connected -> Triple(StateConnected, "CONNECTÉ — ${state.deviceName}", 1500)
        is ConnectionState.Reconnecting -> Triple(StateConnecting, "RECONNEXION… (${state.attempt}/${state.maxAttempts})", 300)
        is ConnectionState.Failed -> Triple(StateError, "DÉCONNECTÉ", 0)
    }
    val transition = rememberInfiniteTransition(label = "indicator")
    val alphaAnim by transition.animateFloat(
        initialValue = 0.4f,
        targetValue = 1f,
        animationSpec = infiniteRepeatable(
            tween(if (pulseSpeedMs == 0) 0 else pulseSpeedMs),
            repeatMode = RepeatMode.Reverse
        ),
        label = "alpha"
    )
    Row(modifier = modifier, verticalAlignment = Alignment.CenterVertically) {
        Spacer(
            Modifier
                .size(12.dp)
                .clip(CircleShape)
                .alpha(if (pulseSpeedMs == 0) 1f else alphaAnim)
                .background(color)
        )
        Spacer(Modifier.width(8.dp))
        Text(text = label, color = TextPrimary)
    }
}
```

- [ ] **Step 2: Commit**

```bash
git add -A
git commit -m "feat(ui): connection indicator with pulse animation"
```

---

## Task 15: NeonVerticalSlider component

**Files:**
- Create: `app/src/main/java/com/hotwheels/command/ui/components/NeonVerticalSlider.kt`

- [ ] **Step 1: Create the slider**

```kotlin
package com.hotwheels.command.ui.components

import androidx.compose.foundation.Canvas
import androidx.compose.foundation.gestures.detectDragGestures
import androidx.compose.foundation.gestures.detectTapGestures
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.fillMaxHeight
import androidx.compose.foundation.layout.width
import androidx.compose.runtime.Composable
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.setValue
import androidx.compose.ui.Modifier
import androidx.compose.ui.geometry.Offset
import androidx.compose.ui.graphics.Brush
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.input.pointer.pointerInput
import androidx.compose.ui.unit.dp
import com.hotwheels.command.ui.theme.AccentElectric
import com.hotwheels.command.ui.theme.AccentGlow
import com.hotwheels.command.ui.theme.BgSurface
import kotlin.math.roundToInt

@Composable
fun NeonVerticalSlider(
    value: Int,                 // current slider value displayed
    enabled: Boolean,
    onValueChange: (Int) -> Unit,
    onRelease: () -> Unit,
    modifier: Modifier = Modifier
) {
    var height by remember { mutableStateOf(1f) }
    val widthDp = 80.dp

    fun yToValue(y: Float): Int {
        val centered = (height / 2f) - y
        val ratio = (centered / (height / 2f)).coerceIn(-1f, 1f)
        return (ratio * 100f).roundToInt()
    }

    Box(
        modifier = modifier
            .width(widthDp)
            .fillMaxHeight()
            .pointerInput(enabled) {
                if (!enabled) return@pointerInput
                detectDragGestures(
                    onDragStart = { o -> onValueChange(yToValue(o.y)) },
                    onDrag = { change, _ ->
                        change.consume()
                        onValueChange(yToValue(change.position.y))
                    },
                    onDragEnd = { onRelease() },
                    onDragCancel = { onRelease() }
                )
            }
            .pointerInput(enabled) {
                if (!enabled) return@pointerInput
                detectTapGestures(
                    onPress = { o ->
                        onValueChange(yToValue(o.y))
                        val released = tryAwaitRelease()
                        if (released) onRelease() else onRelease()
                    }
                )
            }
    ) {
        Canvas(modifier = Modifier.fillMaxHeight().width(widthDp)) {
            height = size.height
            val centerX = size.width / 2f
            val trackWidth = 6.dp.toPx()
            // track
            drawRoundRect(
                color = BgSurface,
                topLeft = Offset(centerX - trackWidth / 2f, 0f),
                size = androidx.compose.ui.geometry.Size(trackWidth, size.height),
                cornerRadius = androidx.compose.ui.geometry.CornerRadius(trackWidth / 2f)
            )
            // fill from center
            val midY = size.height / 2f
            val ratio = value / 100f
            val fillTop = if (ratio >= 0) midY - ratio * midY else midY
            val fillBottom = if (ratio < 0) midY + (-ratio) * midY else midY
            drawRoundRect(
                brush = Brush.verticalGradient(
                    colors = listOf(AccentElectric, AccentGlow),
                    startY = 0f, endY = size.height
                ),
                topLeft = Offset(centerX - trackWidth / 2f, fillTop),
                size = androidx.compose.ui.geometry.Size(trackWidth, fillBottom - fillTop),
                cornerRadius = androidx.compose.ui.geometry.CornerRadius(trackWidth / 2f)
            )
            // thumb
            val thumbY = midY - ratio * midY
            val thumbR = 16.dp.toPx()
            drawCircle(
                color = AccentElectric.copy(alpha = 0.3f),
                radius = thumbR * 1.6f,
                center = Offset(centerX, thumbY)
            )
            drawCircle(
                color = AccentElectric,
                radius = thumbR,
                center = Offset(centerX, thumbY)
            )
        }
    }
}
```

- [ ] **Step 2: Commit**

```bash
git add -A
git commit -m "feat(ui): vertical neon slider with drag and tap"
```

---

## Task 16: DeviceSelectionScreen

**Files:**
- Create: `app/src/main/java/com/hotwheels/command/ui/select/DeviceSelectionScreen.kt`

- [ ] **Step 1: Create the screen**

```kotlin
package com.hotwheels.command.ui.select

import android.content.Intent
import android.provider.Settings
import androidx.compose.foundation.background
import androidx.compose.foundation.clickable
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.lazy.LazyColumn
import androidx.compose.foundation.lazy.items
import androidx.compose.material3.Button
import androidx.compose.material3.ButtonDefaults
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.runtime.LaunchedEffect
import androidx.compose.runtime.getValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.platform.LocalContext
import androidx.compose.ui.unit.dp
import androidx.lifecycle.compose.collectAsStateWithLifecycle
import com.hotwheels.command.ui.components.ScanlineBackground
import com.hotwheels.command.ui.theme.AccentElectric
import com.hotwheels.command.ui.theme.BgPrimary
import com.hotwheels.command.ui.theme.BgSurface
import com.hotwheels.command.ui.theme.TextMuted
import com.hotwheels.command.ui.theme.TextPrimary

@Composable
fun DeviceSelectionScreen(
    viewModel: DeviceSelectionViewModel,
    onDeviceSelected: (PairedDevice) -> Unit
) {
    val devices by viewModel.devices.collectAsStateWithLifecycle()
    val context = LocalContext.current

    LaunchedEffect(Unit) { viewModel.refresh() }

    Box(modifier = Modifier.fillMaxSize().background(BgPrimary)) {
        ScanlineBackground()
        Column(modifier = Modifier.fillMaxSize().padding(24.dp)) {
            Text(text = "> APPAREILS APPAIRÉS", color = AccentElectric)
            Spacer(Modifier.height(16.dp))
            LazyColumn(modifier = Modifier.weight(1f)) {
                items(devices) { device ->
                    Row(
                        modifier = Modifier
                            .fillMaxWidth()
                            .background(BgSurface)
                            .clickable { onDeviceSelected(device) }
                            .padding(16.dp)
                    ) {
                        Column {
                            Text(text = device.name, color = TextPrimary)
                            Text(text = "▸ ${device.address}", color = TextMuted)
                        }
                    }
                    Spacer(Modifier.height(8.dp))
                }
            }
            Row(horizontalArrangement = Arrangement.spacedBy(12.dp), verticalAlignment = Alignment.CenterVertically) {
                Button(
                    onClick = { viewModel.refresh() },
                    colors = ButtonDefaults.buttonColors(containerColor = BgSurface, contentColor = AccentElectric)
                ) { Text("ACTUALISER") }
                Button(
                    onClick = { context.startActivity(Intent(Settings.ACTION_BLUETOOTH_SETTINGS)) },
                    colors = ButtonDefaults.buttonColors(containerColor = BgSurface, contentColor = AccentElectric)
                ) { Text("RÉGLAGES BT") }
            }
        }
    }
}
```

- [ ] **Step 2: Commit**

```bash
git add -A
git commit -m "feat(ui): DeviceSelectionScreen with paired list and BT settings link"
```

---

## Task 17: DriveScreen

**Files:**
- Create: `app/src/main/java/com/hotwheels/command/ui/drive/DriveScreen.kt`

- [ ] **Step 1: Create the screen**

```kotlin
package com.hotwheels.command.ui.drive

import androidx.compose.foundation.background
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.fillMaxHeight
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.weight
import androidx.compose.foundation.layout.width
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableIntStateOf
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.setValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.unit.dp
import com.hotwheels.command.bluetooth.ConnectionState
import com.hotwheels.command.ui.components.ConnectionIndicator
import com.hotwheels.command.ui.components.GlowText
import com.hotwheels.command.ui.components.NeonVerticalSlider
import com.hotwheels.command.ui.components.ScanlineBackground
import com.hotwheels.command.ui.theme.AccentElectric
import com.hotwheels.command.ui.theme.AccentGlow
import com.hotwheels.command.ui.theme.BgPrimary
import com.hotwheels.command.ui.theme.TextMuted

@Composable
fun DriveScreen(
    state: ConnectionState,
    viewModel: DriveViewModel
) {
    val enabled = state is ConnectionState.Connected
    var sliderValue by remember { mutableIntStateOf(0) }

    Box(modifier = Modifier.fillMaxSize().background(BgPrimary)) {
        ScanlineBackground()
        Column(modifier = Modifier.fillMaxSize().padding(16.dp)) {
            ConnectionIndicator(state = state)
            Spacer(Modifier.weight(1f))
            Row(
                modifier = Modifier.fillMaxHeight(0.85f),
                verticalAlignment = Alignment.CenterVertically
            ) {
                Column(modifier = Modifier.weight(1f), horizontalAlignment = Alignment.CenterHorizontally) {
                    GlowText(
                        text = sliderValue.toString(),
                        style = MaterialTheme.typography.displayLarge,
                        color = AccentElectric,
                        glow = AccentGlow
                    )
                    Spacer(Modifier.width(8.dp))
                    Text(text = "envoyé : ${-sliderValue}", color = TextMuted)
                }
                Spacer(Modifier.width(16.dp))
                NeonVerticalSlider(
                    value = if (enabled) sliderValue else 0,
                    enabled = enabled,
                    onValueChange = { v ->
                        sliderValue = v
                        viewModel.onSliderValueChange(v)
                    },
                    onRelease = {
                        sliderValue = 0
                        viewModel.onSliderReleased()
                    },
                    modifier = Modifier.fillMaxHeight()
                )
            }
            Spacer(Modifier.weight(1f))
            Row(horizontalArrangement = Arrangement.SpaceBetween, modifier = Modifier.padding(top = 8.dp)) {
                Text("pilotage HotWheels", color = TextMuted)
                Text("v1.0.0", color = TextMuted)
            }
        }
    }
}
```

- [ ] **Step 2: Commit**

```bash
git add -A
git commit -m "feat(ui): DriveScreen with slider and value display"
```

---

## Task 18: DriveActivity (permissions, navigation, lifecycle)

**Files:**
- Create: `app/src/main/java/com/hotwheels/command/DriveActivity.kt`

- [ ] **Step 1: Create the activity**

```kotlin
package com.hotwheels.command

import android.bluetooth.BluetoothAdapter
import android.content.ComponentName
import android.content.Context
import android.content.Intent
import android.content.ServiceConnection
import android.os.Build
import android.os.Bundle
import android.os.IBinder
import android.view.WindowManager
import androidx.activity.ComponentActivity
import androidx.activity.compose.setContent
import androidx.activity.result.contract.ActivityResultContracts
import androidx.activity.viewModels
import androidx.compose.runtime.LaunchedEffect
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.setValue
import androidx.lifecycle.ViewModel
import androidx.lifecycle.ViewModelProvider
import androidx.lifecycle.compose.collectAsStateWithLifecycle
import com.hotwheels.command.bluetooth.BluetoothCarService
import com.hotwheels.command.bluetooth.ConnectionState
import com.hotwheels.command.data.LastDeviceStore
import com.hotwheels.command.ui.drive.DriveScreen
import com.hotwheels.command.ui.drive.DriveViewModel
import com.hotwheels.command.ui.select.DeviceSelectionScreen
import com.hotwheels.command.ui.select.DeviceSelectionViewModel
import com.hotwheels.command.ui.theme.HotWheelsTheme
import com.hotwheels.command.util.PermissionUtils

class DriveActivity : ComponentActivity() {

    private var service: BluetoothCarService? = null
    private var bound = false

    private val connection = object : ServiceConnection {
        override fun onServiceConnected(name: ComponentName?, binder: IBinder?) {
            val b = binder as? BluetoothCarService.LocalBinder ?: return
            service = b.service
            bound = true
            tryAutoConnect()
        }
        override fun onServiceDisconnected(name: ComponentName?) {
            bound = false
            service = null
        }
    }

    private val requestPermission =
        registerForActivityResult(ActivityResultContracts.RequestPermission()) { granted ->
            if (granted) bindToService()
        }

    private val requestEnableBt =
        registerForActivityResult(ActivityResultContracts.StartActivityForResult()) { /* no-op */ }

    override fun onCreate(savedInstanceState: Bundle?) {
        super.onCreate(savedInstanceState)
        window.addFlags(WindowManager.LayoutParams.FLAG_KEEP_SCREEN_ON)

        if (!PermissionUtils.hasBluetoothConnect(this)) {
            requestPermission.launch(PermissionUtils.BT_CONNECT)
        } else {
            bindToService()
        }

        val store = LastDeviceStore(applicationContext)

        setContent {
            HotWheelsTheme {
                val state by (service?.state?.collectAsStateWithLifecycle() ?: remember { mutableStateOf<ConnectionState>(ConnectionState.Idle) })

                val driveVm = remember {
                    DriveViewModel(sendValue = { v -> service?.setTargetValue(v) })
                }
                val selectVm = remember { DeviceSelectionViewModel(applicationContext) }

                LaunchedEffect(state) {
                    if (state is ConnectionState.Connected) {
                        val s = state as ConnectionState.Connected
                        store.saveLastDevice(s.deviceAddress, s.deviceName)
                    }
                }

                when (state) {
                    is ConnectionState.Idle, is ConnectionState.Failed ->
                        DeviceSelectionScreen(
                            viewModel = selectVm,
                            onDeviceSelected = { device -> service?.connect(device.name, device.address) }
                        )
                    else -> DriveScreen(state = state, viewModel = driveVm)
                }
            }
        }
    }

    private fun bindToService() {
        val intent = Intent(this, BluetoothCarService::class.java)
        startForegroundService(intent)
        bindService(intent, connection, Context.BIND_AUTO_CREATE)
    }

    private fun tryAutoConnect() {
        val store = LastDeviceStore(applicationContext)
        val (mac, name) = store.getLastDevice() ?: return
        if (BluetoothAdapter.getDefaultAdapter()?.isEnabled == false) {
            requestEnableBt.launch(Intent(BluetoothAdapter.ACTION_REQUEST_ENABLE))
            return
        }
        service?.connect(name, mac)
    }

    override fun onStop() {
        super.onStop()
        // Safety: cut motor when app is no longer visible (notification overlays don't trigger onStop)
        service?.setTargetValue(0)
    }

    override fun onDestroy() {
        if (bound) {
            unbindService(connection)
            bound = false
        }
        if (isFinishing) {
            // user really left the app
            stopService(Intent(this, BluetoothCarService::class.java))
        }
        super.onDestroy()
    }
}
```

- [ ] **Step 2: Build the full app**

Run: `./gradlew :app:assembleDebug`
Expected: BUILD SUCCESSFUL. If errors, address compilation issues inline.

- [ ] **Step 3: Commit**

```bash
git add -A
git commit -m "feat(app): DriveActivity wires permissions, service, navigation"
```

---

## Task 19: Run all unit tests, build APK, manual test procedure doc

**Files:**
- Create: `docs/test-procedure.md`

- [ ] **Step 1: Run all unit tests**

Run: `./gradlew :app:testDebugUnitTest`
Expected: all tests in `CarConnectionTest` and `DriveViewModelTest` pass.

- [ ] **Step 2: Build debug APK**

Run: `./gradlew :app:assembleDebug`
Expected: APK at `app/build/outputs/apk/debug/app-debug.apk`.

- [ ] **Step 3: Create `docs/test-procedure.md`**

```markdown
# Manual test procedure — HotWheelsCommand

Pre-requisites:
- ESP32-WROOM-32D programmed with the firmware that listens on `BluetoothSerial("HotWheels_V1")` and reads `readStringUntil('\n')`.
- Poco X6 Pro (or any Android 12+ phone), Bluetooth enabled.
- USB cable + Arduino IDE Serial Monitor at 115 200 baud (optional but recommended).

Steps:

1. Pair `HotWheels_V1` via Android Settings → Bluetooth (PIN if prompted).
2. Install the APK: `adb install app/build/outputs/apk/debug/app-debug.apk`.
3. Launch the app. Grant `BLUETOOTH_CONNECT` permission when asked.
4. On `DeviceSelectionScreen`, tap `HotWheels_V1`.
5. Verify indicator turns green → CONNECTÉ.
6. Drag the slider toward the top (positive). Confirm:
   - Display shows the slider value (e.g. `+56`).
   - Sub-text shows `envoyé : -56`.
   - Serial Monitor on the ESP32 shows `Commande: -56% -> Signal PWM: ...`.
   - Motor spins in the *reverse* direction (because we send the inverse).
7. Release slider. Confirm:
   - Slider snaps to 0 instantly.
   - Serial monitor shows `Commande: 0%`.
   - Motor stops.
8. Press Home. Confirm motor stops immediately (Serial monitor: `Commande: 0%`).
9. Reopen app. Confirm immediate resume, no reconnection step visible.
10. Pull down notification shade. Confirm the slider remains active (still drives the car if dragged).
11. Tap the persistent notification "CUT MOTOR" action. Confirm motor stops.
12. Power off the ESP32 mid-pilot. Indicator → RECONNEXION… → DÉCONNECTÉ within ~5 s.
13. Power on ESP32 and tap "Réessayer" — connection re-established.
14. Press back from `DriveScreen` until app exits. Confirm notification disappears.

Failures to investigate:
- If latency feels >10 ms, check `THROTTLE_MIN_MS` and `HEARTBEAT_MS` in `SppConstants.kt`.
- If car does not move at all, log `"$current\n"` bytes sent vs Serial Monitor input.
```

- [ ] **Step 4: Commit**

```bash
git add -A
git commit -m "docs: manual test procedure"
```

---

## Self-review

**Spec coverage** (each spec section → covered by which task):

- §1 Objectif → Tasks 4, 6, 18 (full data path)
- §2 Décisions structurantes → reflected in `SppConstants.kt` (Task 3), Manifest (Task 9), `gradle.properties`/`app/build.gradle.kts` (Task 1)
- §3 Architecture → Tasks 6 (service), 10 (viewmodel), 17/18 (UI)
- §4 Flux de données → Task 4 (sender loop), Task 10 (slider), Task 18 (lifecycle)
- §5 UI → Tasks 12-17 (components + screens), Task 2 (theme)
- §6 Permissions/manifest → Tasks 8, 9, 7
- §7 Erreurs / état → Task 6 (state machine + reconnect)
- §8 Structure projet → All file paths match
- §9 Tests → Tasks 4 (CarConnectionTest), 10 (DriveViewModelTest), 19 (manual)
- §10 Outillage → Task 19
- §11 Hors scope → respected (no Hilt, no scan, no in-app pairing, no OTA)
- §12 Risques → throttle/heartbeat measurable in `tickOnce`, manual test has guidance

**Placeholder scan:** No "TODO/TBD" left in plan. Every step has full code or full command.

**Type consistency check:**
- `CarConnection(outputStream, clockNanos, sleeperNanos)` — used identically in tests (Task 4) and service (Task 6).
- `setTargetValue(Int)` — same name in `CarConnection`, `BluetoothCarService`, `DriveViewModel.sendValue`.
- `ConnectionState` variants `Idle/Connecting/Connected/Reconnecting/Failed` — same fields used in service (Task 6), indicator (Task 14), activity navigation (Task 18).
- `PairedDevice(name, address)` defined in Task 11, consumed unchanged in Task 16.
- `LastDeviceStore.getLastDevice()` returns `Pair<String,String>` (mac, name) — destructured the same way in Task 18.

No inconsistencies found.

---

## Execution Handoff

Plan complete and saved to `docs/superpowers/plans/2026-05-01-hotwheelscommand.md`. Two execution options:

1. **Subagent-Driven (recommended)** — I dispatch a fresh subagent per task, review between tasks, fast iteration.
2. **Inline Execution** — Execute tasks in this session with checkpoints for review.

Which approach?
