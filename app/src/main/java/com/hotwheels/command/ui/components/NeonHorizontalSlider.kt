package com.hotwheels.command.ui.components

import androidx.compose.foundation.Canvas
import androidx.compose.foundation.gestures.detectDragGestures
import androidx.compose.foundation.gestures.detectTapGestures
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.height
import androidx.compose.runtime.Composable
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableFloatStateOf
import androidx.compose.runtime.mutableIntStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.setValue
import androidx.compose.ui.Modifier
import androidx.compose.ui.geometry.CornerRadius
import androidx.compose.ui.geometry.Offset
import androidx.compose.ui.geometry.Size
import androidx.compose.ui.graphics.Brush
import androidx.compose.ui.graphics.drawscope.Stroke
import androidx.compose.ui.hapticfeedback.HapticFeedbackType
import androidx.compose.ui.input.pointer.pointerInput
import androidx.compose.ui.platform.LocalHapticFeedback
import androidx.compose.ui.unit.dp
import com.hotwheels.command.ui.theme.LocalPalette
import kotlin.math.roundToInt

/**
 * Slider horizontal de direction. Va de -100 (gauche) à +100 (droite).
 * Auto-recentre à 0 quand relâché. Haptique au passage par 0 et aux extrêmes.
 */
@Composable
fun NeonHorizontalSlider(
    value: Int,
    enabled: Boolean,
    onValueChange: (Int) -> Unit,
    onRelease: () -> Unit,
    modifier: Modifier = Modifier
) {
    val palette = LocalPalette.current
    val haptics = LocalHapticFeedback.current
    var widthPx by remember { mutableFloatStateOf(1f) }
    val heightDp = 64.dp

    var lastReported by remember { mutableIntStateOf(0) }

    fun xToValue(x: Float): Int {
        val centered = x - (widthPx / 2f)
        val ratio = (centered / (widthPx / 2f)).coerceIn(-1f, 1f)
        return (ratio * 100f).roundToInt()
    }

    fun reportValue(v: Int) {
        val crossedZero = (v == 0 && lastReported != 0) || (lastReported == 0 && v != 0)
        val hitExtreme = (v == 100 && lastReported != 100) || (v == -100 && lastReported != -100)
        if (crossedZero) haptics.performHapticFeedback(HapticFeedbackType.TextHandleMove)
        if (hitExtreme) haptics.performHapticFeedback(HapticFeedbackType.LongPress)
        lastReported = v
        onValueChange(v)
    }

    Box(
        modifier = modifier
            .fillMaxWidth()
            .height(heightDp)
            .pointerInput(enabled) {
                if (!enabled) return@pointerInput
                detectDragGestures(
                    onDragStart = { o -> reportValue(xToValue(o.x)) },
                    onDrag = { change, _ ->
                        change.consume()
                        reportValue(xToValue(change.position.x))
                    },
                    onDragEnd = { lastReported = 0; onRelease() },
                    onDragCancel = { lastReported = 0; onRelease() }
                )
            }
            .pointerInput(enabled) {
                if (!enabled) return@pointerInput
                detectTapGestures(
                    onPress = { o ->
                        reportValue(xToValue(o.x))
                        tryAwaitRelease()
                        lastReported = 0
                        onRelease()
                    }
                )
            }
    ) {
        Canvas(modifier = Modifier.fillMaxWidth().height(heightDp)) {
            widthPx = size.width
            val centerY = size.height / 2f
            val trackHeight = 22.dp.toPx()
            val trackTop = centerY - trackHeight / 2f

            // Piste — dégradé latéral magenta ↔ cyan ↔ magenta pour suggérer la symétrie.
            drawRoundRect(
                brush = Brush.horizontalGradient(
                    colors = listOf(
                        palette.magenta.copy(alpha = if (palette.isDark) 0.10f else 0.18f),
                        palette.accent.copy(alpha = 0.04f),
                        palette.accent.copy(alpha = if (palette.isDark) 0.10f else 0.18f)
                    )
                ),
                topLeft = Offset(0f, trackTop),
                size = Size(size.width, trackHeight),
                cornerRadius = CornerRadius(12f)
            )
            drawRoundRect(
                color = palette.accentDim35,
                topLeft = Offset(0f, trackTop),
                size = Size(size.width, trackHeight),
                cornerRadius = CornerRadius(12f),
                style = Stroke(width = 1.5f)
            )

            val midX = size.width / 2f
            val ratio = value / 100f

            if (ratio > 0) {
                val fillRight = midX + ratio * midX
                drawRoundRect(
                    brush = Brush.horizontalGradient(
                        colors = listOf(palette.accent, palette.accentGlow),
                        startX = midX, endX = fillRight
                    ),
                    topLeft = Offset(midX, trackTop + 2f),
                    size = Size(fillRight - midX, trackHeight - 4f),
                    cornerRadius = CornerRadius(8f)
                )
            } else if (ratio < 0) {
                val fillLeft = midX - (-ratio) * midX
                drawRoundRect(
                    brush = Brush.horizontalGradient(
                        colors = listOf(palette.magenta, palette.accent.copy(alpha = 0.6f)),
                        startX = fillLeft, endX = midX
                    ),
                    topLeft = Offset(fillLeft, trackTop + 2f),
                    size = Size(midX - fillLeft, trackHeight - 4f),
                    cornerRadius = CornerRadius(8f)
                )
            }

            // Repère central vertical.
            drawLine(
                color = palette.accent.copy(alpha = 0.6f),
                start = Offset(midX, trackTop - 4f),
                end = Offset(midX, trackTop + trackHeight + 4f),
                strokeWidth = 1.5f
            )

            // Graduations.
            for (i in 1..9) {
                val x = (size.width * i / 10f)
                if (kotlin.math.abs(x - midX) < 1f) continue
                val isMajor = (i == 1 || i == 5 || i == 9)
                drawLine(
                    color = if (isMajor) palette.accentDim50 else palette.accentDim20,
                    start = Offset(x, trackTop - 2f),
                    end = Offset(x, trackTop + trackHeight + 2f),
                    strokeWidth = 1f
                )
            }
            drawLine(palette.accentDim50, Offset(1f, trackTop - 4f), Offset(1f, trackTop + trackHeight + 4f), 1.5f)
            drawLine(palette.accentDim50, Offset(size.width - 1f, trackTop - 4f), Offset(size.width - 1f, trackTop + trackHeight + 4f), 1.5f)

            // Curseur.
            val thumbX = midX + ratio * midX
            drawRect(
                color = palette.accent.copy(alpha = 0.25f),
                topLeft = Offset(thumbX - 6f, trackTop - 14f),
                size = Size(12f, trackHeight + 28f)
            )
            drawRect(
                color = palette.accent,
                topLeft = Offset(thumbX - 1.5f, trackTop - 12f),
                size = Size(3f, trackHeight + 24f)
            )
            // Triangles repères haut/bas.
            val arrowSize = 8f
            val pathUp = androidx.compose.ui.graphics.Path().apply {
                moveTo(thumbX, trackTop - 18f)
                lineTo(thumbX - arrowSize, trackTop - 18f + arrowSize)
                lineTo(thumbX + arrowSize, trackTop - 18f + arrowSize)
                close()
            }
            drawPath(pathUp, color = palette.accent)
            val pathDown = androidx.compose.ui.graphics.Path().apply {
                moveTo(thumbX, trackTop + trackHeight + 18f)
                lineTo(thumbX - arrowSize, trackTop + trackHeight + 18f - arrowSize)
                lineTo(thumbX + arrowSize, trackTop + trackHeight + 18f - arrowSize)
                close()
            }
            drawPath(pathDown, color = palette.accent)
        }
    }
}
