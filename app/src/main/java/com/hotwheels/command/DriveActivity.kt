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
import com.hotwheels.command.bluetooth.BluetoothCarService
import com.hotwheels.command.bluetooth.ConnectionState
import com.hotwheels.command.data.LastDeviceStore
import com.hotwheels.command.ui.drive.DriveScreen
import com.hotwheels.command.ui.drive.DriveViewModel
import com.hotwheels.command.ui.select.DeviceSelectionScreen
import com.hotwheels.command.ui.select.DeviceSelectionViewModel
import com.hotwheels.command.ui.theme.HotWheelsTheme
import com.hotwheels.command.util.PermissionUtils
import kotlinx.coroutines.flow.MutableStateFlow

class DriveActivity : ComponentActivity() {

    private var service: BluetoothCarService? = null
    private var bound = false
    private val fallbackState = MutableStateFlow<ConnectionState>(ConnectionState.Idle)

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
                val state by (service?.state ?: fallbackState).collectAsState()

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
