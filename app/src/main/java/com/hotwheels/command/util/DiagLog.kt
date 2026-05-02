package com.hotwheels.command.util

import android.content.Context
import android.content.SharedPreferences
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.StateFlow
import kotlinx.coroutines.flow.asStateFlow
import java.text.SimpleDateFormat
import java.util.Date
import java.util.Locale

object DiagLog {
    private const val PREFS = "hw_diag_log"
    private const val KEY = "entries"
    private const val MAX_ENTRIES = 120
    private val timeFmt = SimpleDateFormat("HH:mm:ss.SSS", Locale.US)

    @Volatile private var prefs: SharedPreferences? = null
    private val _entries = MutableStateFlow<List<String>>(emptyList())
    val entries: StateFlow<List<String>> = _entries.asStateFlow()

    fun init(context: Context) {
        val p = context.applicationContext.getSharedPreferences(PREFS, Context.MODE_PRIVATE)
        prefs = p
        val raw = p.getString(KEY, "") ?: ""
        _entries.value = if (raw.isEmpty()) emptyList() else raw.split("\n")
    }

    @Synchronized
    fun log(tag: String, msg: String) {
        android.util.Log.d("HW_DIAG", "$tag $msg")
        val ts = timeFmt.format(Date())
        val entry = "$ts $tag $msg"
        val updated = (_entries.value + entry).takeLast(MAX_ENTRIES)
        _entries.value = updated
        prefs?.edit()?.putString(KEY, updated.joinToString("\n"))?.commit()
    }

    @Synchronized
    fun clear() {
        _entries.value = emptyList()
        prefs?.edit()?.remove(KEY)?.commit()
    }
}
