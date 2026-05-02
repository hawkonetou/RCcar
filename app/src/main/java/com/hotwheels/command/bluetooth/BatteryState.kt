package com.hotwheels.command.bluetooth

data class BatteryState(
    val centivolts: Int,
    val percent: Int
) {
    val volts: Float get() = centivolts / 100f
}
