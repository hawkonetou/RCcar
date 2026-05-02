package com.hotwheels.command.data

import android.content.Context
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.StateFlow

/**
 * Persistance du limiteur de gaz : 50 / 75 / 100 (% du throttle max).
 * Initialiser une seule fois cote Application (HotWheelsApp.onCreate).
 */
object ThrottleLimitStore {
    private const val PREF = "hwc_throttle"
    private const val KEY = "limit"
    val ALLOWED = listOf(50, 75, 100)

    private val _limit = MutableStateFlow(100)
    val limit: StateFlow<Int> = _limit

    fun init(context: Context) {
        val v = context.applicationContext
            .getSharedPreferences(PREF, Context.MODE_PRIVATE)
            .getInt(KEY, 100)
        _limit.value = v.coerceIn(1, 100)
    }

    fun set(context: Context, value: Int) {
        val clamped = value.coerceIn(1, 100)
        _limit.value = clamped
        context.applicationContext
            .getSharedPreferences(PREF, Context.MODE_PRIVATE)
            .edit()
            .putInt(KEY, clamped)
            .apply()
    }
}
