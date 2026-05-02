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
import com.hotwheels.command.ui.theme.LocalPalette

@Composable
fun ScanlineBackground(modifier: Modifier = Modifier) {
    val palette = LocalPalette.current
    val transition = rememberInfiniteTransition(label = "scan")
    val offset by transition.animateFloat(
        initialValue = 0f,
        targetValue = 100f,
        animationSpec = infiniteRepeatable(
            animation = tween(9000, easing = LinearEasing)
        ),
        label = "scanOffset"
    )

    val cyanRadial = if (palette.isDark) palette.accent.copy(alpha = 0.05f) else palette.accent.copy(alpha = 0.04f)
    val magentaRadial = if (palette.isDark) palette.magenta.copy(alpha = 0.025f) else palette.magenta.copy(alpha = 0.02f)
    val vignetteEnd = if (palette.isDark) Color.Black.copy(alpha = 0.55f) else Color.Black.copy(alpha = 0.06f)

    Canvas(modifier = modifier.fillMaxSize()) {
        val w = size.width
        val h = size.height

        drawRect(
            brush = Brush.radialGradient(
                colors = listOf(cyanRadial, Color.Transparent),
                center = Offset(w / 2f, 0f),
                radius = h
            ),
            size = size
        )
        drawRect(
            brush = Brush.radialGradient(
                colors = listOf(magentaRadial, Color.Transparent),
                center = Offset(w / 2f, h),
                radius = h
            ),
            size = size
        )

        val grid = 28f
        var x = 0f
        while (x < w) {
            drawLine(palette.gridLine, Offset(x, 0f), Offset(x, h), strokeWidth = 1f)
            x += grid
        }
        var y = 0f
        while (y < h) {
            drawLine(palette.gridLine, Offset(0f, y), Offset(w, y), strokeWidth = 1f)
            y += grid
        }

        val period = 3f
        val baseY = -((offset) % period)
        var sy = baseY
        while (sy < h) {
            drawLine(palette.scanline, Offset(0f, sy), Offset(w, sy), strokeWidth = 1f)
            sy += period
        }

        drawRect(
            brush = Brush.radialGradient(
                colors = listOf(Color.Transparent, vignetteEnd),
                center = Offset(w / 2f, h / 2f),
                radius = kotlin.math.max(w, h) / 1.4f
            ),
            size = size
        )
    }
}
