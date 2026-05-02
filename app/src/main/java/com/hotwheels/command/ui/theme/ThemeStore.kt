package com.hotwheels.command.ui.theme

import android.content.Context
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.StateFlow

enum class ThemeMode { Dark, Light }

/**
 * Persistance + diffusion du mode de thème via SharedPreferences.
 * Initialise une seule fois côté Application (HotWheelsApp.onCreate).
 */
object ThemeStore {
    private const val PREF = "hwc_theme"
    private const val KEY = "mode"

    private val _mode = MutableStateFlow(ThemeMode.Dark)
    val mode: StateFlow<ThemeMode> = _mode

    fun init(context: Context) {
        val prefs = context.applicationContext.getSharedPreferences(PREF, Context.MODE_PRIVATE)
        _mode.value = when (prefs.getString(KEY, "dark")) {
            "light" -> ThemeMode.Light
            else -> ThemeMode.Dark
        }
    }

    fun set(context: Context, mode: ThemeMode) {
        _mode.value = mode
        context.applicationContext
            .getSharedPreferences(PREF, Context.MODE_PRIVATE)
            .edit()
            .putString(KEY, if (mode == ThemeMode.Light) "light" else "dark")
            .apply()
    }

    fun toggle(context: Context) {
        set(context, if (_mode.value == ThemeMode.Dark) ThemeMode.Light else ThemeMode.Dark)
    }
}
