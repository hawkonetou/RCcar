package com.hotwheels.command.ui.theme

import androidx.compose.material3.Typography
import androidx.compose.ui.text.TextStyle
import androidx.compose.ui.text.font.Font
import androidx.compose.ui.text.font.FontFamily
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.unit.sp
import com.hotwheels.command.R

val DotoFamily: FontFamily = FontFamily(
    Font(R.font.doto, weight = FontWeight.Black),
    Font(R.font.doto, weight = FontWeight.ExtraBold),
    Font(R.font.doto, weight = FontWeight.Bold),
    Font(R.font.doto, weight = FontWeight.Normal)
)

val MonoFamily: FontFamily = FontFamily(
    Font(R.font.jetbrains_mono, weight = FontWeight.Light),
    Font(R.font.jetbrains_mono, weight = FontWeight.Normal),
    Font(R.font.jetbrains_mono, weight = FontWeight.Medium),
    Font(R.font.jetbrains_mono, weight = FontWeight.SemiBold),
    Font(R.font.jetbrains_mono, weight = FontWeight.Bold)
)

/**
 * Échelle typographique HotWheels Command — bumpée vs v0.3.0 pour répondre aux
 * critères de lisibilité WCAG. Tailles UI minimales : 12sp pour les libellés, 14sp
 * pour le corps. Les graduations/timestamps tolèrent 11sp (large text + AA cyan).
 */
val HotWheelsTypography = Typography(
    displayLarge = TextStyle(
        fontFamily = DotoFamily,
        fontWeight = FontWeight.Black,
        fontSize = 160.sp,
        letterSpacing = 0.sp
    ),
    displayMedium = TextStyle(
        fontFamily = DotoFamily,
        fontWeight = FontWeight.Black,
        fontSize = 32.sp,
        letterSpacing = 2.sp
    ),
    titleMedium = TextStyle(
        fontFamily = MonoFamily,
        fontWeight = FontWeight.SemiBold,
        fontSize = 16.sp,
        letterSpacing = 1.5.sp
    ),
    bodyMedium = TextStyle(
        fontFamily = MonoFamily,
        fontWeight = FontWeight.Normal,
        fontSize = 14.sp
    ),
    labelMedium = TextStyle(
        fontFamily = MonoFamily,
        fontWeight = FontWeight.Medium,
        fontSize = 12.sp,
        letterSpacing = 1.5.sp
    ),
    labelSmall = TextStyle(
        fontFamily = MonoFamily,
        fontWeight = FontWeight.Normal,
        fontSize = 11.sp,
        letterSpacing = 1.5.sp
    )
)
