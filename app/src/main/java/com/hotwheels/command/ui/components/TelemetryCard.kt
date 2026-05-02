package com.hotwheels.command.ui.components

import androidx.compose.foundation.background
import androidx.compose.foundation.border
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.fillMaxHeight
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.size
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import com.hotwheels.command.ui.theme.LocalPalette
import com.hotwheels.command.ui.theme.MonoFamily

@Composable
fun TelemetryCard(
    label: String,
    modifier: Modifier = Modifier,
    accent: Boolean = false,
    content: @Composable () -> Unit
) {
    val palette = LocalPalette.current
    Column(
        modifier = modifier
            .fillMaxSize()
            .border(1.dp, if (accent) palette.accentDim35 else palette.accentDim20)
            .background(palette.panel)
            .padding(horizontal = 10.dp, vertical = 8.dp)
    ) {
        Text(
            text = label,
            color = palette.textMuted,
            fontFamily = MonoFamily,
            fontSize = 11.sp,
            letterSpacing = 2.sp,
            fontWeight = FontWeight.Medium
        )
        Spacer(Modifier.height(4.dp))
        content()
    }
}

@Composable
fun DirectionTriangles(motorValue: Int, modifier: Modifier = Modifier) {
    val palette = LocalPalette.current
    val absVal = kotlin.math.abs(motorValue).coerceIn(0, 100)
    val activeCount = ((absVal + 19) / 20).coerceAtMost(5)
    val pointRight = motorValue >= 0
    Row(modifier = modifier, verticalAlignment = Alignment.CenterVertically) {
        repeat(5) { i ->
            val isActive = i < activeCount
            TriangleArrow(
                pointRight = pointRight,
                color = if (isActive) palette.accent else palette.accentDim20
            )
            if (i < 4) Spacer(Modifier.size(2.dp))
        }
    }
}

@Composable
private fun TriangleArrow(pointRight: Boolean, color: Color) {
    androidx.compose.foundation.Canvas(modifier = Modifier.size(width = 12.dp, height = 12.dp)) {
        val path = androidx.compose.ui.graphics.Path().apply {
            if (pointRight) {
                moveTo(0f, 0f); lineTo(size.width, size.height / 2f); lineTo(0f, size.height); close()
            } else {
                moveTo(size.width, 0f); lineTo(0f, size.height / 2f); lineTo(size.width, size.height); close()
            }
        }
        drawPath(path, color = color)
    }
}
