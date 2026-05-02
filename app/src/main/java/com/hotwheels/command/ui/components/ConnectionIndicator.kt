package com.hotwheels.command.ui.components

import androidx.compose.animation.core.RepeatMode
import androidx.compose.animation.core.animateFloat
import androidx.compose.animation.core.infiniteRepeatable
import androidx.compose.animation.core.rememberInfiniteTransition
import androidx.compose.animation.core.tween
import androidx.compose.foundation.background
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.layout.width
import androidx.compose.foundation.shape.CircleShape
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.runtime.getValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.alpha
import androidx.compose.ui.draw.clip
import androidx.compose.ui.unit.dp
import com.hotwheels.command.bluetooth.ConnectionState
import com.hotwheels.command.ui.theme.LocalPalette

@Composable
fun ConnectionIndicator(state: ConnectionState, modifier: Modifier = Modifier) {
    val palette = LocalPalette.current
    val (color, label, pulseSpeedMs) = when (state) {
        is ConnectionState.Idle -> Triple(palette.textSubtle, "EN ATTENTE", 0)
        is ConnectionState.Connecting -> Triple(palette.stateConnecting, "CONNEXION…", 600)
        is ConnectionState.Connected -> Triple(palette.stateConnected, "CONNECTÉ — ${state.deviceName}", 1500)
        is ConnectionState.Reconnecting -> Triple(palette.stateConnecting, "RECONNEXION… (${state.attempt}/${state.maxAttempts})", 300)
        is ConnectionState.Failed -> Triple(palette.stateError, "DÉCONNECTÉ", 0)
    }
    val transition = rememberInfiniteTransition(label = "indicator")
    val alphaAnim by transition.animateFloat(
        initialValue = 0.4f,
        targetValue = 1f,
        animationSpec = infiniteRepeatable(
            tween(if (pulseSpeedMs == 0) 1 else pulseSpeedMs),
            repeatMode = RepeatMode.Reverse
        ),
        label = "alpha"
    )
    Row(modifier = modifier, verticalAlignment = Alignment.CenterVertically) {
        Spacer(
            Modifier
                .size(12.dp)
                .clip(CircleShape)
                .alpha(if (pulseSpeedMs == 0) 1f else alphaAnim)
                .background(color)
        )
        Spacer(Modifier.width(8.dp))
        Text(text = label, color = palette.textPrimary)
    }
}
