package com.hotwheels.command.ui.theme

import androidx.compose.foundation.isSystemInDarkTheme
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.darkColorScheme
import androidx.compose.runtime.Composable

private val HotWheelsColorScheme = darkColorScheme(
    primary = AccentElectric,
    onPrimary = BgPrimary,
    secondary = AccentGlow,
    background = BgPrimary,
    onBackground = TextPrimary,
    surface = BgSurface,
    onSurface = TextPrimary,
    error = StateError,
    onError = TextPrimary
)

@Composable
fun HotWheelsTheme(
    @Suppress("UNUSED_PARAMETER") darkTheme: Boolean = isSystemInDarkTheme(),
    content: @Composable () -> Unit
) {
    MaterialTheme(
        colorScheme = HotWheelsColorScheme,
        typography = HotWheelsTypography,
        content = content
    )
}
