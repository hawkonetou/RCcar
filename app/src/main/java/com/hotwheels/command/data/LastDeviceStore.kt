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
