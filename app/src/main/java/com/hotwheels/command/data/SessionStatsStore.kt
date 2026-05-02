package com.hotwheels.command.data

import android.content.Context
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.StateFlow

/**
 * Statistiques d'usage cumulees, persistees :
 * - motorTimeMs : temps total moteur actif (throttle != 0)
 * - distanceArb : integrale du throttle * dt (unite arbitraire, proportionnelle a la distance)
 * - lastSessionStart : timestamp epoch du debut de session courante (volatile)
 */
object SessionStatsStore {
    private const val PREF = "hwc_stats"
    private const val KEY_MOTOR_MS = "motor_time_ms"
    private const val KEY_DIST = "distance_arb"

    private val _motorTimeMs = MutableStateFlow(0L)
    val motorTimeMs: StateFlow<Long> = _motorTimeMs

    private val _distanceArb = MutableStateFlow(0L)
    val distanceArb: StateFlow<Long> = _distanceArb

    private var lastTickMs: Long = 0L
    private var lastThrottle: Int = 0

    fun init(context: Context) {
        val sp = context.applicationContext.getSharedPreferences(PREF, Context.MODE_PRIVATE)
        _motorTimeMs.value = sp.getLong(KEY_MOTOR_MS, 0L)
        _distanceArb.value = sp.getLong(KEY_DIST, 0L)
    }

    /** Appele a chaque envoi de throttle. La valeur est dans [-100, 100]. */
    fun reportThrottle(context: Context, throttle: Int) {
        val now = System.currentTimeMillis()
        val dt = if (lastTickMs == 0L) 0L else (now - lastTickMs).coerceAtMost(500L)
        if (dt > 0 && lastThrottle != 0) {
            _motorTimeMs.value = _motorTimeMs.value + dt
            // Distance arbitraire : integrale de |throttle| * dt / 1000 (unite : %.s).
            _distanceArb.value = _distanceArb.value + (kotlin.math.abs(lastThrottle) * dt / 1000L)
            // Persist toutes les 5 secondes pour limiter les ecritures.
            if (_motorTimeMs.value % 5000L < 100L) {
                persist(context)
            }
        }
        lastThrottle = throttle
        lastTickMs = now
    }

    fun reset(context: Context) {
        _motorTimeMs.value = 0L
        _distanceArb.value = 0L
        persist(context)
    }

    fun persist(context: Context) {
        context.applicationContext.getSharedPreferences(PREF, Context.MODE_PRIVATE)
            .edit()
            .putLong(KEY_MOTOR_MS, _motorTimeMs.value)
            .putLong(KEY_DIST, _distanceArb.value)
            .apply()
    }
}
