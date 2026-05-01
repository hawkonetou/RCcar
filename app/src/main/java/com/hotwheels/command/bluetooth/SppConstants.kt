package com.hotwheels.command.bluetooth

import java.util.UUID

object SppConstants {
    val SPP_UUID: UUID = UUID.fromString("00001101-0000-1000-8000-00805F9B34FB")

    const val THROTTLE_MIN_MS: Long = 2L
    const val HEARTBEAT_MS: Long = 50L
    const val PARK_NANOS: Long = 500_000L

    const val RECONNECT_MAX_ATTEMPTS: Int = 3
    const val RECONNECT_TOTAL_TIMEOUT_MS: Long = 5_000L
    const val RECONNECT_BACKOFF_BASE_MS: Long = 200L
    const val RECONNECT_BACKOFF_MAX_MS: Long = 1_000L

    const val MIN_VALUE: Int = -100
    const val MAX_VALUE: Int = 100
}
