package com.hotwheels.command.data

import android.content.Context
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.StateFlow

/**
 * Reglages de pilotage persistes :
 * - trimSteering : decalage permanent applique au M2 (-20..+20).
 * - expoThrottle : intensite de la courbe expo sur throttle (0..100). 0 = lineaire.
 * - expoSteering : idem pour direction (0..100).
 * - invertThrottle / invertSteering : inversion du sens.
 * - brakeOnRelease : pulse de frein actif au relachement du throttle.
 */
object TuningStore {
    private const val PREF = "hwc_tuning"
    private const val KEY_TRIM_S = "trim_steering"
    private const val KEY_EXPO_T = "expo_throttle"
    private const val KEY_EXPO_S = "expo_steering"
    private const val KEY_INV_T = "invert_throttle"
    private const val KEY_INV_S = "invert_steering"
    private const val KEY_BRAKE = "brake_on_release"

    private val _trimSteering = MutableStateFlow(0)
    val trimSteering: StateFlow<Int> = _trimSteering

    private val _expoThrottle = MutableStateFlow(0)
    val expoThrottle: StateFlow<Int> = _expoThrottle

    private val _expoSteering = MutableStateFlow(0)
    val expoSteering: StateFlow<Int> = _expoSteering

    private val _invertThrottle = MutableStateFlow(false)
    val invertThrottle: StateFlow<Boolean> = _invertThrottle

    private val _invertSteering = MutableStateFlow(false)
    val invertSteering: StateFlow<Boolean> = _invertSteering

    private val _brakeOnRelease = MutableStateFlow(false)
    val brakeOnRelease: StateFlow<Boolean> = _brakeOnRelease

    fun init(context: Context) {
        val sp = context.applicationContext.getSharedPreferences(PREF, Context.MODE_PRIVATE)
        _trimSteering.value = sp.getInt(KEY_TRIM_S, 0).coerceIn(-20, 20)
        _expoThrottle.value = sp.getInt(KEY_EXPO_T, 0).coerceIn(0, 100)
        _expoSteering.value = sp.getInt(KEY_EXPO_S, 0).coerceIn(0, 100)
        _invertThrottle.value = sp.getBoolean(KEY_INV_T, false)
        _invertSteering.value = sp.getBoolean(KEY_INV_S, false)
        _brakeOnRelease.value = sp.getBoolean(KEY_BRAKE, false)
    }

    fun setTrimSteering(context: Context, value: Int) {
        val v = value.coerceIn(-20, 20); _trimSteering.value = v
        context.applicationContext.getSharedPreferences(PREF, Context.MODE_PRIVATE)
            .edit().putInt(KEY_TRIM_S, v).apply()
    }
    fun setExpoThrottle(context: Context, value: Int) {
        val v = value.coerceIn(0, 100); _expoThrottle.value = v
        context.applicationContext.getSharedPreferences(PREF, Context.MODE_PRIVATE)
            .edit().putInt(KEY_EXPO_T, v).apply()
    }
    fun setExpoSteering(context: Context, value: Int) {
        val v = value.coerceIn(0, 100); _expoSteering.value = v
        context.applicationContext.getSharedPreferences(PREF, Context.MODE_PRIVATE)
            .edit().putInt(KEY_EXPO_S, v).apply()
    }
    fun setInvertThrottle(context: Context, value: Boolean) {
        _invertThrottle.value = value
        context.applicationContext.getSharedPreferences(PREF, Context.MODE_PRIVATE)
            .edit().putBoolean(KEY_INV_T, value).apply()
    }
    fun setInvertSteering(context: Context, value: Boolean) {
        _invertSteering.value = value
        context.applicationContext.getSharedPreferences(PREF, Context.MODE_PRIVATE)
            .edit().putBoolean(KEY_INV_S, value).apply()
    }
    fun setBrakeOnRelease(context: Context, value: Boolean) {
        _brakeOnRelease.value = value
        context.applicationContext.getSharedPreferences(PREF, Context.MODE_PRIVATE)
            .edit().putBoolean(KEY_BRAKE, value).apply()
    }

    /**
     * Applique la courbe expo. Pour expo=0 retourne v inchange (lineaire pur).
     * Pour expo=100 retourne sign(v) * v² / 100 (cubique adoucie a basse vitesse).
     * Mix lineaire entre les deux.
     */
    fun applyExpo(value: Int, expoPct: Int): Int {
        if (expoPct <= 0) return value
        val sign = if (value < 0) -1 else 1
        val abs = kotlin.math.abs(value).toFloat()
        val curved = (abs * abs) / 100f
        val mix = expoPct / 100f
        val out = (1f - mix) * abs + mix * curved
        return sign * out.toInt().coerceIn(0, 100)
    }
}
