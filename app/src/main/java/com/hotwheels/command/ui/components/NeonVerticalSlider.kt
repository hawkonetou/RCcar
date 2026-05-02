package com.hotwheels.command.ui.components

import androidx.compose.foundation.Canvas
import androidx.compose.foundation.gestures.detectDragGestures
import androidx.compose.foundation.gestures.detectTapGestures
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.fillMaxHeight
import androidx.compose.foundation.layout.width
import androidx.compose.runtime.Composable
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableFloatStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.setValue
import androidx.compose.ui.Modifier
import androidx.compose.ui.geometry.CornerRadius
import androidx.compose.ui.geometry.Offset
import androidx.compose.ui.geometry.Size
import androidx.compose.ui.graphics.Brush
import androidx.compose.ui.graphics.drawscope.Stroke
import androidx.compose.ui.input.pointer.pointerInput
import androidx.compose.ui.unit.dp
import com.hotwheels.command.ui.theme.LocalPalette
import kotlin.math.roundToInt

@Composable
fun NeonVerticalSlider(
    value: Int,
    enabled: Boolean,
    onValueChange: (Int) -> Unit,
    onRelease: () -> Unit,
    modifier: Modifier = Modifier
) {
    val palette = LocalPalette.current
    var heightPx by remember { mutableFloatStateOf(1f) }
    val widthDp = 88.dp

    fun yToValue(y: Float): Int {
        val centered = (heightPx / 2f) - y
        val ratio = (centered / (heightPx / 2f)).coerceIn(-1f, 1f)
        return (ratio * 100f).roundToInt()
    }

    Box(
        modifier = modifier
            .width(widthDp)
            .fillMaxHeight()
            .pointerInput(enabled) {
                if (!enabled) return@pointerInput
                detectDragGestures(
                    onDragStart = { o -> onValueChange(yToValue(o.y)) },
                    onDrag = { change, _ ->
                        change.consume()
                        onValueChange(yToValue(change.position.y))
                    },
                    onDragEnd = { onRelease() },
                    onDragCancel = { onRelease() }
                )
            }
            .pointerInput(enabled) {
                if (!enabled) return@pointerInput
                detectTapGestures(
                    onPress = { o ->
                        onValueChange(yToValue(o.y))
                        tryAwaitRelease()
                        onRelease()
                    }
                )
            }
    ) {
        Canvas(modifier = Modifier.fillMaxHeight().width(widthDp)) {
            heightPx = size.height
            val centerX = size.width / 2f
            val trackWidth = 22.dp.toPx()
            val trackLeft = centerX - trackWidth / 2f

            drawRoundRect(
                brush = Brush.verticalGradient(
                    colors = listOf(
                        palette.accent.copy(alpha = if (palette.isDark) 0.10f else 0.18f),
                        palette.accent.copy(alpha = 0.04f),
                        palette.magenta.copy(alpha = if (palette.isDark) 0.10f else 0.18f)
                    )
                ),
                topLeft = Offset(trackLeft, 0f),
                size = Size(trackWidth, size.height),
                cornerRadius = CornerRadius(12f)
            )
            drawRoundRect(
                color = palette.accentDim35,
                topLeft = Offset(trackLeft, 0f),
                size = Size(trackWidth, size.height),
                cornerRadius = CornerRadius(12f),
                style = Stroke(width = 1.5f)
            )

            val midY = size.height / 2f
            val ratio = value / 100f

            if (ratio > 0) {
                val fillTop = midY - ratio * midY
                drawRoundRect(
                    brush = Brush.verticalGradient(
                        colors = listOf(palette.accent, palette.accentGlow),
                        startY = fillTop, endY = midY
                    ),
                    topLeft = Offset(trackLeft + 2f, fillTop),
                    size = Size(trackWidth - 4f, midY - fillTop),
                    cornerRadius = CornerRadius(8f)
                )
            } else if (ratio < 0) {
                val fillBottom = midY + (-ratio) * midY
                drawRoundRect(
                    brush = Brush.verticalGradient(
                        colors = listOf(palette.accent.copy(alpha = 0.6f), palette.magenta),
                        startY = midY, endY = fillBottom
                    ),
                    topLeft = Offset(trackLeft + 2f, midY),
                    size = Size(trackWidth - 4f, fillBottom - midY),
                    cornerRadius = CornerRadius(8f)
                )
            }

            drawLine(
                color = palette.accent.copy(alpha = 0.6f),
                start = Offset(trackLeft - 4f, midY),
                end = Offset(trackLeft + trackWidth + 4f, midY),
                strokeWidth = 1.5f
            )

            for (i in 1..9) {
                val y = (size.height * i / 10f)
                if (kotlin.math.abs(y - midY) < 1f) continue
                val isMajor = (i == 1 || i == 5 || i == 9)
                drawLine(
                    color = if (isMajor) palette.accentDim50 else palette.accentDim20,
                    start = Offset(trackLeft - 2f, y),
                    end = Offset(trackLeft + trackWidth + 2f, y),
                    strokeWidth = 1f
                )
            }
            drawLine(palette.accentDim50, Offset(trackLeft - 4f, 1f), Offset(trackLeft + trackWidth + 4f, 1f), 1.5f)
            drawLine(palette.accentDim50, Offset(trackLeft - 4f, size.height - 1f), Offset(trackLeft + trackWidth + 4f, size.height - 1f), 1.5f)

            val thumbY = midY - ratio * midY
            drawRect(
                color = palette.accent.copy(alpha = 0.25f),
                topLeft = Offset(trackLeft - 14f, thumbY - 6f),
                size = Size(trackWidth + 28f, 12f)
            )
            drawRect(
                color = palette.accent,
                topLeft = Offset(trackLeft - 12f, thumbY - 1.5f),
                size = Size(trackWidth + 24f, 3f)
            )
            val arrowSize = 8f
            val pathLeft = androidx.compose.ui.graphics.Path().apply {
                moveTo(trackLeft - 18f, thumbY)
                lineTo(trackLeft - 18f + arrowSize, thumbY - arrowSize)
                lineTo(trackLeft - 18f + arrowSize, thumbY + arrowSize)
                close()
            }
            drawPath(pathLeft, color = palette.accent)
            val pathRight = androidx.compose.ui.graphics.Path().apply {
                moveTo(trackLeft + trackWidth + 18f, thumbY)
                lineTo(trackLeft + trackWidth + 18f - arrowSize, thumbY - arrowSize)
                lineTo(trackLeft + trackWidth + 18f - arrowSize, thumbY + arrowSize)
                close()
            }
            drawPath(pathRight, color = palette.accent)
        }
    }
}
