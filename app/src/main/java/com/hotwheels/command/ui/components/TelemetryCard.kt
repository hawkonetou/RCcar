package com.hotwheels.command.ui.components

import androidx.compose.foundation.background
import androidx.compose.foundation.border
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.size
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.text.font.FontFamily
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import com.hotwheels.command.ui.theme.AccentElectric
import com.hotwheels.command.ui.theme.BgPanel
import com.hotwheels.command.ui.theme.CyanDim20
import com.hotwheels.command.ui.theme.CyanDim35
import com.hotwheels.command.ui.theme.CyanDim50
import com.hotwheels.command.ui.theme.DotoFamily
import com.hotwheels.command.ui.theme.MonoFamily

@Composable
fun TelemetryCard(
    label: String,
    modifier: Modifier = Modifier,
    accent: Boolean = false,
    content: @Composable () -> Unit
) {
    Column(
        modifier = modifier
            .fillMaxWidth()
            .border(1.dp, if (accent) CyanDim35 else CyanDim20)
            .background(BgPanel)
            .padding(horizontal = 8.dp, vertical = 6.dp)
    ) {
        Text(
            text = label,
            color = CyanDim50,
            fontFamily = MonoFamily,
            fontSize = 9.sp,
            letterSpacing = 2.sp,
            fontWeight = FontWeight.Medium
        )
        Spacer(Modifier.height(4.dp))
        content()
    }
}

@Composable
fun DirectionTriangles(motorValue: Int, modifier: Modifier = Modifier) {
    val absVal = kotlin.math.abs(motorValue).coerceIn(0, 100)
    val activeCount = ((absVal + 19) / 20).coerceAtMost(5)
    val pointRight = motorValue >= 0
    Row(modifier = modifier, verticalAlignment = Alignment.CenterVertically) {
        repeat(5) { i ->
            val isActive = i < activeCount
            TriangleArrow(
                pointRight = pointRight,
                color = if (isActive) AccentElectric else CyanDim20
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
