package com.hotwheels.command.ui.components

import androidx.compose.foundation.layout.Box
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.ui.Modifier
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.text.TextStyle

@Composable
fun GlowText(
    text: String,
    style: TextStyle,
    color: Color,
    glow: Color,
    modifier: Modifier = Modifier
) {
    Box(modifier = modifier) {
        Text(text = text, style = style.copy(color = glow.copy(alpha = 0.5f)))
        Text(text = text, style = style.copy(color = color))
    }
}
