package com.hotwheels.command.ui.components

import androidx.compose.animation.core.LinearEasing
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
import androidx.compose.ui.geometry.Size
import androidx.compose.ui.graphics.drawscope.Stroke
import androidx.compose.ui.unit.Dp
import androidx.compose.ui.unit.dp
import com.hotwheels.command.ui.theme.LocalPalette

@Composable
fun ConnectingRing(modifier: Modifier = Modifier, ringSize: Dp = 96.dp) {
    val palette = LocalPalette.current
    val transition = rememberInfiniteTransition(label = "ring")
    val angle1 by transition.animateFloat(
        initialValue = 0f,
        targetValue = 360f,
        animationSpec = infiniteRepeatable(
            animation = tween(1400, easing = LinearEasing)
        ),
        label = "ring1"
    )
    val angle2 by transition.animateFloat(
        initialValue = 0f,
        targetValue = -360f,
        animationSpec = infiniteRepeatable(
            animation = tween(2200, easing = LinearEasing)
        ),
        label = "ring2"
    )

    Canvas(modifier = modifier.size(ringSize)) {
        val stroke = 2.dp.toPx()
        val r = (size.minDimension - stroke) / 2f
        val center = Offset(size.width / 2f, size.height / 2f)
        val arcSize = Size(r * 2f, r * 2f)
        val topLeft = Offset(center.x - r, center.y - r)

        drawCircle(color = palette.accentDim20, radius = r, center = center, style = Stroke(width = stroke))

        drawArc(
            color = palette.accent,
            startAngle = angle1,
            sweepAngle = 70f,
            useCenter = false,
            topLeft = topLeft,
            size = arcSize,
            style = Stroke(width = stroke + 1f)
        )
        drawArc(
            color = palette.magenta.copy(alpha = 0.65f),
            startAngle = angle2,
            sweepAngle = 50f,
            useCenter = false,
            topLeft = topLeft,
            size = arcSize,
            style = Stroke(width = stroke)
        )
    }
}
