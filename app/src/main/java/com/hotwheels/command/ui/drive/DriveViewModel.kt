package com.hotwheels.command.ui.drive

import androidx.lifecycle.ViewModel
import com.hotwheels.command.bluetooth.SppConstants
import com.hotwheels.command.data.ThrottleLimitStore
import com.hotwheels.command.data.TuningStore
import kotlinx.coroutines.CoroutineScope
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.SupervisorJob
import kotlinx.coroutines.cancel
import kotlinx.coroutines.delay
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.StateFlow
import kotlinx.coroutines.launch
import kotlinx.coroutines.Job

class DriveViewModel(
    private val sendValue: (Int) -> Unit,
    private val sendSteering: (Int) -> Unit = {},
    private val reportThrottleStat: (Int) -> Unit = {}
) : ViewModel() {

    private val scope = CoroutineScope(SupervisorJob() + Dispatchers.Default)

    private var sliderValue: Int = 0
    private var steeringValue: Int = 0
    private var brakeJob: Job? = null

    private val _cruise = MutableStateFlow(false)
    val cruise: StateFlow<Boolean> = _cruise
    private var cruiseValue: Int = 0

    fun onSliderValueChange(newValue: Int) {
        val clamped = newValue.coerceIn(SppConstants.MIN_VALUE, SppConstants.MAX_VALUE)
        if (clamped == sliderValue) return
        sliderValue = clamped
        // Activer cruise = on memorise la valeur courante au moment ou il s'allume,
        // mais s'il est deja actif, l'utilisateur reprend la main et on remplace.
        if (_cruise.value) {
            cruiseValue = clamped
        }
        emitThrottle(clamped)
    }

    fun onSliderReleased() {
        if (_cruise.value) {
            // Cruise actif : on garde la valeur memorisee.
            return
        }
        val previous = sliderValue
        sliderValue = 0
        if (TuningStore.brakeOnRelease.value && previous != 0) {
            // Pulse de frein actif : un coup contraire ~150 ms puis 0.
            brakeJob?.cancel()
            brakeJob = scope.launch {
                val pulse = if (previous > 0) -25 else 25
                emitThrottle(pulse)
                delay(150L)
                emitThrottle(0)
            }
        } else {
            emitThrottle(0)
        }
    }

    fun toggleCruise() {
        val now = !_cruise.value
        _cruise.value = now
        if (now) {
            // Verrouille la valeur courante.
            cruiseValue = sliderValue
        } else {
            // Relache : retour a 0.
            sliderValue = 0
            emitThrottle(0)
        }
    }

    fun onSteeringChange(newValue: Int) {
        val clamped = newValue.coerceIn(SppConstants.MIN_VALUE, SppConstants.MAX_VALUE)
        if (clamped == steeringValue) return
        steeringValue = clamped
        emitSteering(clamped)
    }

    fun onSteeringReleased() {
        steeringValue = 0
        emitSteering(0)
    }

    private fun emitThrottle(rawValue: Int) {
        // Pipeline : expo -> limiteur -> inversion.
        val expoed = TuningStore.applyExpo(rawValue, TuningStore.expoThrottle.value)
        val limit = ThrottleLimitStore.limit.value
        val limited = (expoed * limit / 100).coerceIn(SppConstants.MIN_VALUE, SppConstants.MAX_VALUE)
        val signed = if (TuningStore.invertThrottle.value) limited else -limited
        sendValue(signed)
        reportThrottleStat(limited)
    }

    private fun emitSteering(rawValue: Int) {
        // Pipeline : expo -> trim -> inversion.
        val expoed = TuningStore.applyExpo(rawValue, TuningStore.expoSteering.value)
        val trimmed = (expoed + TuningStore.trimSteering.value)
            .coerceIn(SppConstants.MIN_VALUE, SppConstants.MAX_VALUE)
        val signed = if (TuningStore.invertSteering.value) -trimmed else trimmed
        sendSteering(signed)
    }

    /** Re-emission cyclique de la valeur de cruise pour eviter le watchdog firmware. */
    fun cruiseTick() {
        if (_cruise.value) emitThrottle(cruiseValue)
    }

    fun currentSlider(): Int = sliderValue

    override fun onCleared() {
        super.onCleared()
        scope.cancel()
    }
}
