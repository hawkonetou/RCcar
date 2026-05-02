package com.hotwheels.command.ui.drive

import androidx.lifecycle.ViewModel
import com.hotwheels.command.bluetooth.SppConstants
import com.hotwheels.command.data.ThrottleLimitStore

class DriveViewModel(
    private val sendValue: (Int) -> Unit,
    private val sendSteering: (Int) -> Unit = {}
) : ViewModel() {

    private var sliderValue: Int = 0
    private var steeringValue: Int = 0

    fun onSliderValueChange(newValue: Int) {
        val clamped = newValue.coerceIn(SppConstants.MIN_VALUE, SppConstants.MAX_VALUE)
        if (clamped == sliderValue) return
        sliderValue = clamped
        // Limiteur global : 50 / 75 / 100 % du throttle max.
        val limit = ThrottleLimitStore.limit.value
        val limited = (clamped * limit / 100).coerceIn(SppConstants.MIN_VALUE, SppConstants.MAX_VALUE)
        sendValue(-limited)
    }

    fun onSliderReleased() {
        sliderValue = 0
        sendValue(0)
    }

    fun onSteeringChange(newValue: Int) {
        val clamped = newValue.coerceIn(SppConstants.MIN_VALUE, SppConstants.MAX_VALUE)
        if (clamped == steeringValue) return
        steeringValue = clamped
        sendSteering(clamped)
    }

    fun onSteeringReleased() {
        steeringValue = 0
        sendSteering(0)
    }

    fun currentSlider(): Int = sliderValue
}
