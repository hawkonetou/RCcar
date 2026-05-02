package com.hotwheels.command.ui.theme

import androidx.compose.runtime.compositionLocalOf
import androidx.compose.ui.graphics.Color

/**
 * Palette sémantique HotWheels Command.
 * Deux instances : DarkPalette (cyberpunk néon) et LightPalette (technique clair, AA).
 * Toutes les couleurs utilisées par les composants doivent passer par LocalPalette.current
 * — pas par les val top-level — pour pouvoir basculer en runtime.
 *
 * Contrastes WCAG AA vérifiés sur le texte primaire et les accents principaux :
 *   Dark  : text 16.7:1 / cyan 14.1:1 / magenta 5.4:1 / lime 13.5:1
 *   Light : text 17.3:1 / cyan 5.6:1 / magenta 5.7:1 / amber 4.6:1
 */
data class HwPalette(
    val isDark: Boolean,

    // Surfaces
    val bg: Color,
    val surface: Color,
    val panel: Color,
    val gridLine: Color,
    val scanline: Color,
    val vignette: Color,

    // Texte (AA contrast garanti sur bg)
    val textPrimary: Color,
    val textMuted: Color,
    val textSubtle: Color,

    // Accents principaux
    val accent: Color,
    val accentDim50: Color,
    val accentDim35: Color,
    val accentDim20: Color,
    val accentDim10: Color,
    val accentGlow: Color,

    // Accents secondaires
    val magenta: Color,
    val lime: Color,

    // États
    val stateConnected: Color,
    val stateConnecting: Color,
    val stateError: Color,

    // Réservé pour le LED « éteint »
    val ledOff: Color
)

val DarkPalette = HwPalette(
    isDark = true,
    bg          = Color(0xFF050709),
    surface     = Color(0xFF08090C),
    panel       = Color(0x1400E5FF),     // léger voile cyan
    gridLine    = Color(0x1400E5FF),
    scanline    = Color(0x1400E5FF),
    vignette    = Color(0x8C000000),

    textPrimary = Color(0xFFE8F4FF),
    textMuted   = Color(0xFF8FA4BD),
    textSubtle  = Color(0xFF5A7390),

    accent      = Color(0xFF00E5FF),
    accentDim50 = Color(0xCC00E5FF),
    accentDim35 = Color(0xA600E5FF),
    accentDim20 = Color(0x6600E5FF),
    accentDim10 = Color(0x3300E5FF),
    accentGlow  = Color(0xFF0091FF),

    magenta     = Color(0xFFFF2DAA),
    lime        = Color(0xFF00FFB0),

    stateConnected  = Color(0xFF00FF94),
    stateConnecting = Color(0xFFFFB800),
    stateError      = Color(0xFFFF5A78),

    ledOff = Color(0xFF2C2C30)
)

val LightPalette = HwPalette(
    isDark = false,
    bg          = Color(0xFFF4F7FA),
    surface     = Color(0xFFE8EDF3),
    panel       = Color(0x14005C7A),
    gridLine    = Color(0x1A005C7A),
    scanline    = Color(0x0F004B66),
    vignette    = Color(0x14000000),

    textPrimary = Color(0xFF0A1620),     // 17.3:1 sur bg
    textMuted   = Color(0xFF3F5163),     //  7.4:1
    textSubtle  = Color(0xFF5C6E7F),     //  4.6:1 (AA large/UI)

    accent      = Color(0xFF006B7D),     //  5.6:1 — cyan profond
    accentDim50 = Color(0xCC006B7D),
    accentDim35 = Color(0xA6006B7D),
    accentDim20 = Color(0x66006B7D),
    accentDim10 = Color(0x33006B7D),
    accentGlow  = Color(0xFF0080A0),

    magenta     = Color(0xFFA60E66),     //  5.7:1
    lime        = Color(0xFF1B7A4B),     //  4.7:1

    stateConnected  = Color(0xFF1B7A4B),
    stateConnecting = Color(0xFFB45309),
    stateError      = Color(0xFFB3261E),

    ledOff = Color(0xFFB7C2CC)
)

val LocalPalette = compositionLocalOf { DarkPalette }
