package com.hotwheels.command.bluetooth

import android.annotation.SuppressLint
import android.app.Notification
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
import androidx.core.content.ContextCompat
import com.hotwheels.command.HotWheelsApp
import com.hotwheels.command.R
import com.hotwheels.command.util.DiagLog
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

    private val _battery = MutableStateFlow<BatteryState?>(null)
    val battery: StateFlow<BatteryState?> = _battery.asStateFlow()
    private var batteryForwarder: Job? = null

    private val _linkFreshMs = MutableStateFlow(Long.MAX_VALUE)
    val linkFreshMs: StateFlow<Long> = _linkFreshMs.asStateFlow()
    private var linkForwarder: Job? = null

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
        DiagLog.log("SVC", "onCreate")
        try {
            startForeground(NOTIF_ID, buildNotification(getString(R.string.notif_state_idle)))
            DiagLog.log("SVC", "startForeground OK")
        } catch (e: Throwable) {
            DiagLog.log("SVC", "startForeground FAILED ${e::class.java.simpleName}: ${e.message}")
            throw e
        }
        ContextCompat.registerReceiver(
            this,
            disconnectionReceiver,
            IntentFilter(BluetoothDevice.ACTION_ACL_DISCONNECTED),
            ContextCompat.RECEIVER_NOT_EXPORTED
        )
        DiagLog.log("SVC", "onCreate done")
    }

    override fun onStartCommand(intent: Intent?, flags: Int, startId: Int): Int {
        when (intent?.action) {
            ACTION_STOP_MOTOR -> {
                connection?.setTargetValue(0)
                connection?.setSteeringValue(0)
            }
            ACTION_DISCONNECT -> stopServiceCleanly()
        }
        return START_STICKY
    }

    private fun updateNotification(text: String) {
        val mgr = getSystemService(NOTIFICATION_SERVICE) as android.app.NotificationManager
        mgr.notify(NOTIF_ID, buildNotification(text))
    }

    fun setTargetValue(value: Int) {
        connection?.setTargetValue(value)
    }

    fun setSteeringValue(value: Int) {
        connection?.setSteeringValue(value)
    }

    fun connect(deviceName: String, macAddress: String) {
        DiagLog.log("SVC", "connect() called name='$deviceName' mac=$macAddress")
        connectJob?.cancel()
        connectJob = scope.launch { doConnect(deviceName, macAddress) }
    }

    @SuppressLint("MissingPermission")
    private suspend fun doConnect(deviceName: String, macAddress: String) {
        DiagLog.log("SVC", "doConnect enter")
        _state.value = ConnectionState.Connecting(deviceName, macAddress)
        DiagLog.log("SVC", "state=Connecting")
        updateNotification(getString(R.string.notif_state_connecting, deviceName))
        val adapter = bluetoothAdapter() ?: run {
            DiagLog.log("SVC", "no Bluetooth adapter")
            _state.value = ConnectionState.Failed(deviceName, macAddress, "No Bluetooth adapter")
            updateNotification(getString(R.string.notif_state_failed))
            return
        }
        DiagLog.log("SVC", "adapter ok enabled=${adapter.isEnabled}")
        val device = adapter.getRemoteDevice(macAddress)
        DiagLog.log("SVC", "getRemoteDevice ok bond=${device.bondState}")
        try {
            DiagLog.log("SVC", "createRfcommSocketToServiceRecord uuid=${SppConstants.SPP_UUID}")
            val s = device.createRfcommSocketToServiceRecord(SppConstants.SPP_UUID)
            DiagLog.log("SVC", "socket created, calling cancelDiscovery")
            adapter.cancelDiscovery()
            DiagLog.log("SVC", "cancelDiscovery ok, calling socket.connect (BLOCKING)")
            s.connect()
            DiagLog.log("SVC", "socket.connect OK")
            socket = s
            val conn = CarConnection(s.outputStream, s.inputStream)
            conn.onFailure { scope.launch { startReconnect(macAddress) } }
            conn.start()
            connection = conn
            batteryForwarder?.cancel()
            batteryForwarder = scope.launch { conn.battery.collect { _battery.value = it } }
            linkForwarder?.cancel()
            linkForwarder = scope.launch { conn.linkFreshMs.collect { _linkFreshMs.value = it } }
            _state.value = ConnectionState.Connected(deviceName, macAddress)
            DiagLog.log("SVC", "state=Connected")
            updateNotification(getString(R.string.notif_text_connected, deviceName))
        } catch (e: Exception) {
            val stack = e.stackTraceToString().lines().take(8).joinToString(" | ")
            DiagLog.log("SVC", "doConnect EXC ${e::class.java.simpleName}: ${e.message} :: $stack")
            _state.value = ConnectionState.Failed(
                deviceName,
                macAddress,
                "${e::class.java.simpleName}: ${e.message ?: "connect failed"}"
            )
            updateNotification(getString(R.string.notif_state_failed))
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
            // Backoff exponentiel borne : 1s, 2s, 4s, 8s, 16s, 30s, 30s, ...
            val expo = SppConstants.RECONNECT_BACKOFF_BASE_MS shl (attempt - 1).coerceAtMost(20)
            val backoff = min(expo, SppConstants.RECONNECT_BACKOFF_MAX_MS)
            DiagLog.log("SVC", "reconnect attempt=$attempt backoff=${backoff}ms")
            delay(backoff)
            try {
                val adapter = bluetoothAdapter() ?: throw IllegalStateException("no adapter")
                val device = adapter.getRemoteDevice(macAddress)
                val s = device.createRfcommSocketToServiceRecord(SppConstants.SPP_UUID)
                s.connect()
                socket = s
                val conn = CarConnection(s.outputStream, s.inputStream)
                conn.onFailure { scope.launch { startReconnect(macAddress) } }
                conn.start()
                connection = conn
                batteryForwarder?.cancel()
                batteryForwarder = scope.launch { conn.battery.collect { _battery.value = it } }
                linkForwarder?.cancel()
                linkForwarder = scope.launch { conn.linkFreshMs.collect { _linkFreshMs.value = it } }
                _state.value = ConnectionState.Connected(deviceName, macAddress)
                return
            } catch (_: Exception) {
                attempt++
            }
        }
        DiagLog.log("SVC", "reconnect ABANDON after attempt=$attempt elapsed=${System.currentTimeMillis() - start}ms")
        _state.value = ConnectionState.Failed(deviceName, macAddress, "Reconnect timeout (5 min)")
    }

    fun disconnect() = stopServiceCleanly()

    private fun stopServiceCleanly() {
        batteryForwarder?.cancel()
        batteryForwarder = null
        _battery.value = null
        linkForwarder?.cancel()
        linkForwarder = null
        _linkFreshMs.value = Long.MAX_VALUE
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

    companion object {
        const val NOTIF_ID = 4242
        const val ACTION_STOP_MOTOR = "com.hotwheels.command.action.STOP_MOTOR"
        const val ACTION_DISCONNECT = "com.hotwheels.command.action.DISCONNECT"
    }
}
