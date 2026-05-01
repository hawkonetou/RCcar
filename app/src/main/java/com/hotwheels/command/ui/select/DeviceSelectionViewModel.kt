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
