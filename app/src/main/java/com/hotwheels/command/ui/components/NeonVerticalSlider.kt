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
import androidx.compose.ui.input.pointer.pointerInput
import androidx.compose.ui.unit.dp
import com.hotwheels.command.ui.theme.AccentElectric
import com.hotwheels.command.ui.theme.AccentGlow
import com.hotwheels.command.ui.theme.BgSurface
import kotlin.math.roundToInt

@Composable
fun NeonVerticalSlider(
    value: Int,
    enabled: Boolean,
    onValueChange: (Int) -> Unit,
    onRelease: () -> Unit,
    modifier: Modifier = Modifier
) {
    var heightPx by remember { mutableFloatStateOf(1f) }
    val widthDp = 80.dp

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
            val trackWidth = 6.dp.toPx()
            drawRoundRect(
                color = BgSurface,
                topLeft = Offset(centerX - trackWidth / 2f, 0f),
                size = Size(trackWidth, size.height),
                cornerRadius = CornerRadius(trackWidth / 2f)
            )
            val midY = size.height / 2f
            val ratio = value / 100f
            val fillTop = if (ratio >= 0) midY - ratio * midY else midY
            val fillBottom = if (ratio < 0) midY + (-ratio) * midY else midY
            drawRoundRect(
                brush = Brush.verticalGradient(
                    colors = listOf(AccentElectric, AccentGlow),
                    startY = 0f, endY = size.height
                ),
                topLeft = Offset(centerX - trackWidth / 2f, fillTop),
                size = Size(trackWidth, fillBottom - fillTop),
                cornerRadius = CornerRadius(trackWidth / 2f)
            )
            val thumbY = midY - ratio * midY
            val thumbR = 16.dp.toPx()
            drawCircle(
                color = AccentElectric.copy(alpha = 0.3f),
                radius = thumbR * 1.6f,
                center = Offset(centerX, thumbY)
            )
            drawCircle(
                color = AccentElectric,
                radius = thumbR,
                center = Offset(centerX, thumbY)
            )
        }
    }
}
