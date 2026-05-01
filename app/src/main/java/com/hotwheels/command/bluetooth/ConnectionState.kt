package com.hotwheels.command.bluetooth

sealed class ConnectionState {
    data object Idle : ConnectionState()
    data class Connecting(val deviceName: String, val deviceAddress: String) : ConnectionState()
    data class Connected(val deviceName: String, val deviceAddress: String) : ConnectionState()
    data class Reconnecting(
        val deviceName: String,
        val deviceAddress: String,
        val attempt: Int,
        val maxAttempts: Int
    ) : ConnectionState()
    data class Failed(val deviceName: String, val deviceAddress: String, val reason: String) : ConnectionState()
}
