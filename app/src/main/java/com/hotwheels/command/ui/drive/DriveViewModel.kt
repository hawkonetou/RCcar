package com.hotwheels.command.ui.drive

import androidx.lifecycle.ViewModel
import com.hotwheels.command.bluetooth.SppConstants

class DriveViewModel(
    private val sendValue: (Int) -> Unit
) : ViewModel() {

    private var sliderValue: Int = 0

    fun onSliderValueChange(newValue: Int) {
        val clamped = newValue.coerceIn(SppConstants.MIN_VALUE, SppConstants.MAX_VALUE)
        if (clamped == sliderValue) return
        sliderValue = clamped
        sendValue(-clamped)
    }

    fun onSliderReleased() {
        sliderValue = 0
        sendValue(0)
    }

    fun currentSlider(): Int = sliderValue
}
