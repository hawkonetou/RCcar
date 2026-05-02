package com.hotwheels.command

import android.bluetooth.BluetoothAdapter
import android.bluetooth.BluetoothManager
import android.content.ComponentName
import android.content.Context
import android.content.Intent
import android.content.ServiceConnection
import android.os.Bundle
import android.os.IBinder
import android.view.WindowManager
import androidx.activity.ComponentActivity
import androidx.activity.compose.setContent
import androidx.activity.result.contract.ActivityResultContracts
import androidx.compose.runtime.LaunchedEffect
import androidx.compose.runtime.collectAsState
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.setValue
import com.hotwheels.command.bluetooth.BatteryState
import com.hotwheels.command.bluetooth.BluetoothCarService
import com.hotwheels.command.bluetooth.ConnectionState
import com.hotwheels.command.data.LastDeviceStore
import com.hotwheels.command.ui.drive.DriveScreen
import com.hotwheels.command.ui.drive.DriveViewModel
import com.hotwheels.command.ui.select.DeviceSelectionScreen
import com.hotwheels.command.ui.select.DeviceSelectionViewModel
import com.hotwheels.command.ui.theme.HotWheelsTheme
import com.hotwheels.command.ui.theme.ThemeMode
import com.hotwheels.command.ui.theme.ThemeStore
import com.hotwheels.command.util.DiagLog
import androidx.core.view.WindowCompat
import com.hotwheels.command.util.PermissionUtils
import kotlinx.coroutines.flow.MutableStateFlow

class DriveActivity : ComponentActivity() {

    // Compose-reactive: when the service binds, the UI recomposes and starts collecting service.state.
    private var service: BluetoothCarService? by mutableStateOf(null)
    private var bound = false
    private val fallbackState = MutableStateFlow<ConnectionState>(ConnectionState.Idle)
    private val fallbackBattery = MutableStateFlow<BatteryState?>(null)
    private val fallbackLink = MutableStateFlow(Long.MAX_VALUE)

    private val connection = object : ServiceConnection {
        override fun onServiceConnected(name: ComponentName?, binder: IBinder?) {
            DiagLog.log("ACT", "onServiceConnected name=$name binder=${binder != null}")
            val b = binder as? BluetoothCarService.LocalBinder
            if (b == null) {
                DiagLog.log("ACT", "onServiceConnected: binder cast FAILED")
                return
            }
            service = b.service
            bound = true
            tryAutoConnect()
        }
        override fun onServiceDisconnected(name: ComponentName?) {
            DiagLog.log("ACT", "onServiceDisconnected name=$name")
            bound = false
            service = null
        }
    }

    private val requestPermissions =
        registerForActivityResult(ActivityResultContracts.RequestMultiplePermissions()) { result ->
            val granted = result.all { it.value }
            DiagLog.log("ACT", "permissions result: ${result.entries.joinToString { "${it.key.substringAfterLast('.')}=${it.value}" }}")
            if (granted) {
                bindToService()
            } else {
                DiagLog.log("ACT", "permissions denied — service not bound")
            }
        }

    private val requestEnableBt =
        registerForActivityResult(ActivityResultContracts.StartActivityForResult()) { /* no-op */ }

    override fun onCreate(savedInstanceState: Bundle?) {
        super.onCreate(savedInstanceState)
        DiagLog.log("ACT", "onCreate")
        window.addFlags(WindowManager.LayoutParams.FLAG_KEEP_SCREEN_ON)

        val missing = PermissionUtils.missing(this)
        DiagLog.log("ACT", "permissions missing=${missing.map { it.substringAfterLast('.') }}")
        if (missing.isNotEmpty()) {
            DiagLog.log("ACT", "requesting permissions")
            requestPermissions.launch(PermissionUtils.required)
        } else {
            bindToService()
        }

        val store = LastDeviceStore(applicationContext)

        setContent {
            val themeMode by ThemeStore.mode.collectAsState()
            // Adapt status/nav bars to current theme so the system chrome blends in.
            val isLight = themeMode == ThemeMode.Light
            val controller = remember(themeMode) { WindowCompat.getInsetsController(window, window.decorView) }
            controller.isAppearanceLightStatusBars = isLight
            controller.isAppearanceLightNavigationBars = isLight
            window.statusBarColor = if (isLight) 0xFFF4F7FA.toInt() else 0xFF050709.toInt()
            window.navigationBarColor = if (isLight) 0xFFF4F7FA.toInt() else 0xFF050709.toInt()

            HotWheelsTheme(mode = themeMode) {
                val state by (service?.state ?: fallbackState).collectAsState()
                val battery by (service?.battery ?: fallbackBattery).collectAsState()
                val linkFresh by (service?.linkFreshMs ?: fallbackLink).collectAsState()

                val driveVm = remember {
                    DriveViewModel(
                        sendValue = { v -> service?.setTargetValue(v) },
                        sendSteering = { v -> service?.setSteeringValue(v) },
                        reportThrottleStat = { v ->
                            com.hotwheels.command.data.SessionStatsStore.reportThrottle(applicationContext, v)
                        }
                    )
                }
                val selectVm = remember { DeviceSelectionViewModel(applicationContext) }

                LaunchedEffect(state) {
                    if (state is ConnectionState.Connected) {
                        val s = state as ConnectionState.Connected
                        store.saveLastDevice(s.deviceAddress, s.deviceName)
                    }
                }

                when (val s = state) {
                    is ConnectionState.Idle ->
                        DeviceSelectionScreen(
                            viewModel = selectVm,
                            lastError = null,
                            onDeviceSelected = { device ->
                                DiagLog.log("ACT", "tap device='${device.name}' mac=${device.address} service=${if (service == null) "null" else "ok"}")
                                service?.connect(device.name, device.address)
                            }
                        )
                    is ConnectionState.Failed ->
                        DeviceSelectionScreen(
                            viewModel = selectVm,
                            lastError = "${s.deviceName}: ${s.reason}",
                            onDeviceSelected = { device ->
                                DiagLog.log("ACT", "tap device='${device.name}' mac=${device.address} service=${if (service == null) "null" else "ok"}")
                                service?.connect(device.name, device.address)
                            }
                        )
                    else -> DriveScreen(
                        state = state,
                        viewModel = driveVm,
                        battery = battery,
                        linkFreshMs = linkFresh,
                        onBatteryBypass = { v -> service?.setBatteryBypass(v) }
                    )
                }
            }
        }
    }

    private fun bindToService() {
        DiagLog.log("ACT", "bindToService start")
        val intent = Intent(this, BluetoothCarService::class.java)
        try {
            startForegroundService(intent)
            DiagLog.log("ACT", "startForegroundService OK")
        } catch (e: Throwable) {
            DiagLog.log("ACT", "startForegroundService FAILED ${e::class.java.simpleName}: ${e.message}")
        }
        val bindResult = bindService(intent, connection, Context.BIND_AUTO_CREATE)
        DiagLog.log("ACT", "bindService returned=$bindResult")
    }

    private fun tryAutoConnect() {
        val store = LastDeviceStore(applicationContext)
        val (mac, name) = store.getLastDevice() ?: return
        val mgr = getSystemService(BLUETOOTH_SERVICE) as? BluetoothManager
        val adapter: BluetoothAdapter? = mgr?.adapter
        if (adapter == null || !adapter.isEnabled) {
            requestEnableBt.launch(Intent(BluetoothAdapter.ACTION_REQUEST_ENABLE))
            return
        }
        service?.connect(name, mac)
    }

    override fun onStop() {
        super.onStop()
        // Safety: cut motor when app is no longer visible (notifications don't trigger onStop)
        service?.setTargetValue(0)
    }

    override fun onDestroy() {
        if (bound) {
            unbindService(connection)
            bound = false
        }
        if (isFinishing) {
            stopService(Intent(this, BluetoothCarService::class.java))
        }
        super.onDestroy()
    }
}
