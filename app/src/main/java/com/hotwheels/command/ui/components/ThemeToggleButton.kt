package com.hotwheels.command.ui.components

import androidx.compose.foundation.background
import androidx.compose.foundation.border
import androidx.compose.foundation.clickable
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.runtime.getValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.platform.LocalContext
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import androidx.lifecycle.compose.collectAsStateWithLifecycle
import com.hotwheels.command.ui.theme.LocalPalette
import com.hotwheels.command.ui.theme.MonoFamily
import com.hotwheels.command.ui.theme.ThemeMode
import com.hotwheels.command.ui.theme.ThemeStore

@Composable
fun ThemeToggleButton(modifier: Modifier = Modifier) {
    val palette = LocalPalette.current
    val context = LocalContext.current
    val mode by ThemeStore.mode.collectAsStateWithLifecycle()
    val glyph = if (mode == ThemeMode.Dark) "☀" else "☾"
    Box(
        contentAlignment = Alignment.Center,
        modifier = modifier
            .size(32.dp)
            .border(1.dp, palette.accentDim35, RoundedCornerShape(6.dp))
            .background(palette.panel, RoundedCornerShape(6.dp))
            .clickable { ThemeStore.toggle(context) }
    ) {
        Text(
            text = glyph,
            color = palette.accent,
            fontFamily = MonoFamily,
            fontSize = 16.sp
        )
    }
}
