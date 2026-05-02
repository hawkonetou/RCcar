package com.hotwheels.command.ui.components

import androidx.compose.animation.core.RepeatMode
import androidx.compose.animation.core.animateFloat
import androidx.compose.animation.core.infiniteRepeatable
import androidx.compose.animation.core.rememberInfiniteTransition
import androidx.compose.animation.core.tween
import androidx.compose.foundation.Canvas
import androidx.compose.foundation.layout.size
import androidx.compose.runtime.Composable
import androidx.compose.runtime.getValue
import androidx.compose.ui.Modifier
import androidx.compose.ui.geometry.Offset
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.unit.dp
import com.hotwheels.command.ui.theme.LocalPalette

enum class LedState { On, Pending, Off }

@Composable
fun LedDot(state: LedState, modifier: Modifier = Modifier) {
    val palette = LocalPalette.current
    val transition = rememberInfiniteTransition(label = "led")
    val (color, durationMs) = when (state) {
        LedState.On      -> palette.stateConnected to 1600
        LedState.Pending -> palette.stateConnecting to 600
        LedState.Off     -> palette.ledOff to 0
    }
    val alpha by transition.animateFloat(
        initialValue = 1f,
        targetValue = if (state == LedState.Off) 1f else if (state == LedState.Pending) 0.2f else 0.45f,
        animationSpec = infiniteRepeatable(
            animation = tween(durationMs.coerceAtLeast(100)),
            repeatMode = RepeatMode.Reverse
        ),
        label = "ledAlpha"
    )

    Canvas(modifier = modifier.size(12.dp)) {
        val r = size.minDimension / 2f
        val center = Offset(size.width / 2f, size.height / 2f)
        if (state != LedState.Off) {
            drawCircle(color = color.copy(alpha = 0.25f * alpha), radius = r * 2.4f, center = center)
            drawCircle(color = color.copy(alpha = 0.5f * alpha), radius = r * 1.6f, center = center)
        }
        drawCircle(color = color, radius = r, center = center)
        drawCircle(color = Color.White.copy(alpha = 0.6f), radius = r * 0.4f, center = center.copy(y = center.y - r * 0.25f))
    }
}
