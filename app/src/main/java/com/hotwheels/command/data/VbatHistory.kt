package com.hotwheels.command.data

import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.StateFlow

/**
 * Buffer circulaire 60 dernieres secondes de tension batterie (centivolts).
 * Echantillonnage : 1 point par seconde environ. Volatile (pas de persistance,
 * c'est un outil de diag instantane).
 */
object VbatHistory {
    const val CAPACITY = 60

    private val buffer = ArrayDeque<Int>()
    private var lastSampleMs = 0L
    private val _samples = MutableStateFlow<List<Int>>(emptyList())
    val samples: StateFlow<List<Int>> = _samples

    fun reportCv(cv: Int) {
        val now = System.currentTimeMillis()
        if (now - lastSampleMs < 900) return
        lastSampleMs = now
        if (buffer.size >= CAPACITY) buffer.removeFirst()
        buffer.addLast(cv)
        _samples.value = buffer.toList()
    }

    fun clear() {
        buffer.clear()
        _samples.value = emptyList()
    }
}
