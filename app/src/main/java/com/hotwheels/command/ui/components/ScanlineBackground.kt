package com.hotwheels.command.ui.components

import androidx.compose.animation.core.LinearEasing
import androidx.compose.animation.core.animateFloat
import androidx.compose.animation.core.infiniteRepeatable
import androidx.compose.animation.core.rememberInfiniteTransition
import androidx.compose.animation.core.tween
import androidx.compose.foundation.Canvas
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.runtime.Composable
import androidx.compose.runtime.getValue
import androidx.compose.ui.Modifier
import androidx.compose.ui.geometry.Offset
import androidx.compose.ui.graphics.Brush
import androidx.compose.ui.graphics.Color
import com.hotwheels.command.ui.theme.AccentElectric
import com.hotwheels.command.ui.theme.AccentMagenta
import com.hotwheels.command.ui.theme.GridLine

@Composable
fun ScanlineBackground(modifier: Modifier = Modifier, color: Color = Color(0x0A00E5FF)) {
    val transition = rememberInfiniteTransition(label = "scan")
    val offset by transition.animateFloat(
        initialValue = 0f,
        targetValue = 100f,
        animationSpec = infiniteRepeatable(
            animation = tween(9000, easing = LinearEasing)
        ),
        label = "scanOffset"
    )

    Canvas(modifier = modifier.fillMaxSize()) {
        val w = size.width
        val h = size.height

        // Subtle radial gradients (cyan top, magenta bottom)
        drawRect(
            brush = Brush.radialGradient(
                colors = listOf(AccentElectric.copy(alpha = 0.05f), Color.Transparent),
                center = Offset(w / 2f, 0f),
                radius = h
            ),
            size = size
        )
        drawRect(
            brush = Brush.radialGradient(
                colors = listOf(AccentMagenta.copy(alpha = 0.025f), Color.Transparent),
                center = Offset(w / 2f, h),
                radius = h
            ),
            size = size
        )

        // Blueprint grid 28px
        val grid = 28f
        var x = 0f
        while (x < w) {
            drawLine(GridLine, Offset(x, 0f), Offset(x, h), strokeWidth = 1f)
            x += grid
        }
        var y = 0f
        while (y < h) {
            drawLine(GridLine, Offset(0f, y), Offset(w, y), strokeWidth = 1f)
            y += grid
        }

        // Animated scanlines (3px periodicity)
        val period = 3f
        val baseY = -((offset) % period)
        var sy = baseY
        while (sy < h) {
            drawLine(color, Offset(0f, sy), Offset(w, sy), strokeWidth = 1f)
            sy += period
        }

        // Vignette (radial dark corners)
        drawRect(
            brush = Brush.radialGradient(
                colors = listOf(Color.Transparent, Color.Black.copy(alpha = 0.55f)),
                center = Offset(w / 2f, h / 2f),
                radius = kotlin.math.max(w, h) / 1.4f
            ),
            size = size
        )
    }
}
