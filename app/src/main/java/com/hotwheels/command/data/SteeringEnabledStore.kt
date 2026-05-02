package com.hotwheels.command.data

import android.content.Context
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.StateFlow

/**
 * Persistance de l'option "commande de direction" (M2).
 * Quand actif : un slider horizontal apparait sur DriveScreen et envoie
 * la consigne au moteur de direction via CarConnection.setSteeringValue().
 */
object SteeringEnabledStore {
    private const val PREF = "hwc_steering"
    private const val KEY = "enabled"

    private val _enabled = MutableStateFlow(false)
    val enabled: StateFlow<Boolean> = _enabled

    fun init(context: Context) {
        _enabled.value = context.applicationContext
            .getSharedPreferences(PREF, Context.MODE_PRIVATE)
            .getBoolean(KEY, false)
    }

    fun set(context: Context, value: Boolean) {
        _enabled.value = value
        context.applicationContext
            .getSharedPreferences(PREF, Context.MODE_PRIVATE)
            .edit()
            .putBoolean(KEY, value)
            .apply()
    }
}
