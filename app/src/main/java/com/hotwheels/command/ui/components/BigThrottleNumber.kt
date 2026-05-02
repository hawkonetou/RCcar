package com.hotwheels.command.ui.components

import androidx.compose.animation.core.RepeatMode
import androidx.compose.animation.core.animateFloat
import androidx.compose.animation.core.infiniteRepeatable
import androidx.compose.animation.core.rememberInfiniteTransition
import androidx.compose.animation.core.tween
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.offset
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.runtime.getValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.graphics.Shadow
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.unit.dp
import com.hotwheels.command.ui.theme.AccentElectric
import com.hotwheels.command.ui.theme.AccentMagenta
import com.hotwheels.command.ui.theme.DotoFamily

@Composable
fun BigThrottleNumber(value: Int, modifier: Modifier = Modifier) {
    val transition = rememberInfiniteTransition(label = "num")
    val glow by transition.animateFloat(
        initialValue = 0.85f,
        targetValue = 1f,
        animationSpec = infiniteRepeatable(
            animation = tween(2600),
            repeatMode = RepeatMode.Reverse
        ),
        label = "numGlow"
    )

    Box(
        modifier = modifier.fillMaxWidth(),
        contentAlignment = Alignment.Center
    ) {
        Text(
            text = value.toString(),
            color = AccentMagenta.copy(alpha = 0.55f),
            style = MaterialTheme.typography.displayLarge,
            fontFamily = DotoFamily,
            fontWeight = FontWeight.Black,
            modifier = Modifier.offset(x = (-2).dp)
        )
        Text(
            text = value.toString(),
            color = AccentElectric.copy(alpha = 0.55f),
            style = MaterialTheme.typography.displayLarge,
            fontFamily = DotoFamily,
            fontWeight = FontWeight.Black,
            modifier = Modifier.offset(x = 2.dp)
        )
        Text(
            text = value.toString(),
            color = AccentElectric,
            style = MaterialTheme.typography.displayLarge.copy(
                shadow = Shadow(
                    color = AccentElectric.copy(alpha = glow * 0.85f),
                    blurRadius = 32f * glow
                )
            ),
            fontFamily = DotoFamily,
            fontWeight = FontWeight.Black
        )
    }
}
