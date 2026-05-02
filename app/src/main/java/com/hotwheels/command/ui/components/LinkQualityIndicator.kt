package com.hotwheels.command.ui.components

import androidx.compose.foundation.Canvas
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.layout.width
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.geometry.Offset
import androidx.compose.ui.geometry.Size
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import com.hotwheels.command.bluetooth.SppConstants
import com.hotwheels.command.ui.theme.LocalPalette
import com.hotwheels.command.ui.theme.MonoFamily

enum class LinkQuality { Good, Fair, Poor, None }

fun classifyLink(freshMs: Long): LinkQuality = when {
    freshMs == Long.MAX_VALUE -> LinkQuality.None
    freshMs <= SppConstants.LINK_GOOD_MS -> LinkQuality.Good
    freshMs <= SppConstants.LINK_FAIR_MS -> LinkQuality.Fair
    else -> LinkQuality.Poor
}

@Composable
fun LinkQualityIndicator(freshMs: Long, modifier: Modifier = Modifier) {
    val palette = LocalPalette.current
    val q = classifyLink(freshMs)
    val (color, label, activeBars) = when (q) {
        LinkQuality.Good -> Triple(palette.stateConnected, "OK", 3)
        LinkQuality.Fair -> Triple(palette.stateConnecting, "FAIR", 2)
        LinkQuality.Poor -> Triple(palette.stateError, "POOR", 1)
        LinkQuality.None -> Triple(palette.textSubtle, "—", 0)
    }
    Row(modifier = modifier, verticalAlignment = Alignment.CenterVertically) {
        SignalBars(activeBars = activeBars, activeColor = color, dimColor = palette.accentDim20)
        Spacer(Modifier.width(6.dp))
        Text(
            text = label,
            color = color,
            fontFamily = MonoFamily,
            fontWeight = FontWeight.SemiBold,
            fontSize = 11.sp,
            letterSpacing = 1.sp
        )
    }
}

@Composable
private fun SignalBars(activeBars: Int, activeColor: Color, dimColor: Color) {
    Canvas(modifier = Modifier.size(width = 18.dp, height = 14.dp)) {
        val barWidth = 4f
        val gap = 2f
        val baseY = size.height
        for (i in 0 until 3) {
            val barHeight = size.height * (i + 1) / 3f
            val x = i * (barWidth + gap)
            val color = if (i < activeBars) activeColor else dimColor
            drawRect(
                color = color,
                topLeft = Offset(x, baseY - barHeight),
                size = Size(barWidth, barHeight)
            )
        }
    }
}
