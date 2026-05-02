package com.hotwheels.command.bluetooth

import java.util.UUID

object SppConstants {
    val SPP_UUID: UUID = UUID.fromString("00001101-0000-1000-8000-00805F9B34FB")

    const val THROTTLE_MIN_MS: Long = 2L
    const val HEARTBEAT_MS: Long = 50L
    const val PARK_NANOS: Long = 500_000L

    // Reconnexion : backoff exponentiel borne (1s, 2s, 4s, 8s, 16s, 30s, 30s, ...).
    const val RECONNECT_BACKOFF_BASE_MS: Long = 1_000L
    const val RECONNECT_BACKOFF_MAX_MS: Long = 30_000L
    const val RECONNECT_TOTAL_TIMEOUT_MS: Long = 5L * 60L * 1_000L   // 5 min
    const val RECONNECT_MAX_ATTEMPTS: Int = 12

    const val MIN_VALUE: Int = -100
    const val MAX_VALUE: Int = 100

    // Link quality : seuils de "fraicheur" sur la derniere trame BAT/PONG recue (ms).
    const val LINK_GOOD_MS: Long = 2_000L
    const val LINK_FAIR_MS: Long = 5_000L
    // Au-dela de LINK_FAIR_MS la qualite est "poor".

    // PING toutes les X ms quand connecte (calcul latence)
    const val PING_INTERVAL_MS: Long = 1_500L
}
