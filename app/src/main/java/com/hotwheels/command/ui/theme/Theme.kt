package com.hotwheels.command.ui.theme

import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.darkColorScheme
import androidx.compose.material3.lightColorScheme
import androidx.compose.runtime.Composable
import androidx.compose.runtime.CompositionLocalProvider

@Composable
fun HotWheelsTheme(
    mode: ThemeMode = ThemeMode.Dark,
    content: @Composable () -> Unit
) {
    val palette = if (mode == ThemeMode.Light) LightPalette else DarkPalette
    val scheme = if (palette.isDark) {
        darkColorScheme(
            primary = palette.accent,
            onPrimary = palette.bg,
            secondary = palette.accentGlow,
            background = palette.bg,
            onBackground = palette.textPrimary,
            surface = palette.surface,
            onSurface = palette.textPrimary,
            error = palette.stateError,
            onError = palette.textPrimary
        )
    } else {
        lightColorScheme(
            primary = palette.accent,
            onPrimary = palette.bg,
            secondary = palette.accentGlow,
            background = palette.bg,
            onBackground = palette.textPrimary,
            surface = palette.surface,
            onSurface = palette.textPrimary,
            error = palette.stateError,
            onError = palette.bg
        )
    }
    CompositionLocalProvider(LocalPalette provides palette) {
        MaterialTheme(
            colorScheme = scheme,
            typography = HotWheelsTypography,
            content = content
        )
    }
}
