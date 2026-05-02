package com.hotwheels.command.ui.components

import androidx.compose.foundation.Canvas
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.runtime.Composable
import androidx.compose.ui.Modifier
import androidx.compose.ui.geometry.Offset
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.unit.Dp
import androidx.compose.ui.unit.dp
import com.hotwheels.command.ui.theme.LocalPalette

@Composable
fun CornerBrackets(
    modifier: Modifier = Modifier,
    size: Dp = 22.dp,
    strokeWidth: Dp = 2.dp,
    color: Color? = null,
    alpha: Float = 0.55f
) {
    val resolved = color ?: LocalPalette.current.accent
    Canvas(modifier = modifier.fillMaxSize()) {
        val s = size.toPx()
        val sw = strokeWidth.toPx()
        val w = this.size.width
        val h = this.size.height
        val c = resolved.copy(alpha = alpha)
        drawLine(c, Offset(0f, 0f), Offset(s, 0f), strokeWidth = sw)
        drawLine(c, Offset(0f, 0f), Offset(0f, s), strokeWidth = sw)
        drawLine(c, Offset(w - s, 0f), Offset(w, 0f), strokeWidth = sw)
        drawLine(c, Offset(w, 0f), Offset(w, s), strokeWidth = sw)
        drawLine(c, Offset(0f, h - s), Offset(0f, h), strokeWidth = sw)
        drawLine(c, Offset(0f, h), Offset(s, h), strokeWidth = sw)
        drawLine(c, Offset(w - s, h), Offset(w, h), strokeWidth = sw)
        drawLine(c, Offset(w, h - s), Offset(w, h), strokeWidth = sw)
    }
}
