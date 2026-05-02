package com.hotwheels.command.ui.components

import androidx.compose.animation.AnimatedVisibility
import androidx.compose.animation.core.RepeatMode
import androidx.compose.animation.core.animateFloat
import androidx.compose.animation.core.infiniteRepeatable
import androidx.compose.animation.core.rememberInfiniteTransition
import androidx.compose.animation.core.tween
import androidx.compose.animation.expandVertically
import androidx.compose.animation.shrinkVertically
import androidx.compose.foundation.background
import androidx.compose.foundation.border
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.width
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.runtime.LaunchedEffect
import androidx.compose.runtime.getValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.hapticfeedback.HapticFeedbackType
import androidx.compose.ui.platform.LocalHapticFeedback
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import com.hotwheels.command.ui.theme.LocalPalette
import com.hotwheels.command.ui.theme.MonoFamily

/**
 * Banniere d'alerte quand le lien BT est silencieux > seuil.
 * Vibration repetee toutes les 1.5s tant que la condition est vraie.
 */
@Composable
fun LinkLostBanner(linkFreshMs: Long, thresholdMs: Long = 3_000L) {
    val palette = LocalPalette.current
    val haptics = LocalHapticFeedback.current
    val visible = linkFreshMs > thresholdMs

    LaunchedEffect(visible) {
        if (visible) {
            while (true) {
                haptics.performHapticFeedback(HapticFeedbackType.LongPress)
                kotlinx.coroutines.delay(1500L)
            }
        }
    }

    val transition = rememberInfiniteTransition(label = "linkLost")
    val pulse by transition.animateFloat(
        initialValue = 0.6f,
        targetValue = 1f,
        animationSpec = infiniteRepeatable(animation = tween(700), repeatMode = RepeatMode.Reverse),
        label = "linkLostPulse"
    )

    AnimatedVisibility(
        visible = visible,
        enter = expandVertically(),
        exit = shrinkVertically()
    ) {
        Row(
            modifier = Modifier
                .fillMaxWidth()
                .background(palette.stateError.copy(alpha = 0.18f * pulse))
                .border(1.5.dp, palette.stateError.copy(alpha = pulse))
                .padding(horizontal = 12.dp, vertical = 6.dp),
            verticalAlignment = Alignment.CenterVertically,
            horizontalArrangement = Arrangement.Center
        ) {
            Text(
                text = "⚠ LIEN SILENCIEUX",
                color = palette.stateError,
                fontFamily = MonoFamily,
                fontWeight = FontWeight.Bold,
                fontSize = 13.sp,
                letterSpacing = 3.sp
            )
            Spacer(Modifier.width(12.dp))
            Text(
                text = "MOTEUR FORCÉ À 0",
                color = palette.stateError.copy(alpha = 0.9f),
                fontFamily = MonoFamily,
                fontSize = 11.sp,
                letterSpacing = 2.sp
            )
        }
    }
}
