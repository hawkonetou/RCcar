package com.hotwheels.command.ui.components

import androidx.compose.foundation.Canvas
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.runtime.Composable
import androidx.compose.ui.Modifier
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.geometry.Offset

@Composable
fun ScanlineBackground(modifier: Modifier = Modifier, color: Color = Color(0x0A00E5FF)) {
    Canvas(modifier = modifier.fillMaxSize()) {
        var y = 0f
        while (y < size.height) {
            drawLine(color, Offset(0f, y), Offset(size.width, y), strokeWidth = 1f)
            y += 3f
        }
    }
}
