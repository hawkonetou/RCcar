package com.hotwheels.command.ui.components

import androidx.compose.animation.core.RepeatMode
import androidx.compose.animation.core.animateFloat
import androidx.compose.animation.core.infiniteRepeatable
import androidx.compose.animation.core.rememberInfiniteTransition
import androidx.compose.animation.core.tween
import androidx.compose.foundation.Canvas
import androidx.compose.foundation.background
import androidx.compose.foundation.border
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.fillMaxHeight
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.layout.width
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.runtime.getValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clip
import androidx.compose.ui.geometry.Offset
import androidx.compose.ui.graphics.Brush
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import com.hotwheels.command.bluetooth.BatteryState
import com.hotwheels.command.ui.theme.LocalPalette
import com.hotwheels.command.ui.theme.MonoFamily

@Composable
fun BatteryBadge(battery: BatteryState?, modifier: Modifier = Modifier) {
    val palette = LocalPalette.current
    val implausible = battery != null && !battery.plausible
    val (fillColor, accentColor) = when {
        battery == null -> Brush.horizontalGradient(listOf(palette.textSubtle, palette.textSubtle)) to palette.textSubtle
        implausible -> Brush.horizontalGradient(listOf(palette.textSubtle, palette.textSubtle)) to palette.magenta
        battery.percent <= 20 -> Brush.horizontalGradient(listOf(palette.stateConnecting, palette.stateError)) to palette.stateError
        battery.percent <= 50 -> Brush.horizontalGradient(listOf(palette.accent, palette.stateConnecting)) to palette.stateConnecting
        else -> Brush.horizontalGradient(listOf(palette.accent, palette.lime)) to palette.accent
    }

    val transition = rememberInfiniteTransition(label = "bat")
    val pulseDuration = if (battery != null && !implausible && battery.percent <= 20) 900 else 3200
    val brightness by transition.animateFloat(
        initialValue = 1f,
        targetValue = 1.25f,
        animationSpec = infiniteRepeatable(
            animation = tween(pulseDuration),
            repeatMode = RepeatMode.Reverse
        ),
        label = "batPulse"
    )

    Row(
        verticalAlignment = Alignment.CenterVertically,
        horizontalArrangement = Arrangement.spacedBy(8.dp),
        modifier = modifier
    ) {
        Box(modifier = Modifier.size(width = 64.dp, height = 26.dp)) {
            Box(
                modifier = Modifier
                    .fillMaxHeight()
                    .width(58.dp)
                    .border(1.5.dp, accentColor, RoundedCornerShape(3.dp))
                    .padding(2.dp)
            ) {
                val pct = if (implausible) 0 else (battery?.percent ?: 0).coerceIn(0, 100)
                if (pct > 0) {
                    Box(
                        modifier = Modifier
                            .fillMaxHeight()
                            .clip(RoundedCornerShape(1.dp))
                            .background(fillColor)
                            .width((54.dp.value * pct / 100f).dp)
                    ) {
                        Canvas(modifier = Modifier.fillMaxHeight().width((54.dp.value * pct / 100f).dp)) {
                            val seg = 4f
                            var x = 0f
                            while (x < size.width) {
                                drawRect(
                                    color = Color.Black.copy(alpha = 0.18f),
                                    topLeft = Offset(x + seg, 0f),
                                    size = androidx.compose.ui.geometry.Size(1f, size.height)
                                )
                                x += seg + 1f
                            }
                        }
                    }
                }
            }
            Box(
                modifier = Modifier
                    .align(Alignment.CenterEnd)
                    .height(12.dp)
                    .width(5.dp)
                    .background(accentColor, RoundedCornerShape(topEnd = 2.dp, bottomEnd = 2.dp))
            )
        }
        Column(verticalArrangement = Arrangement.spacedBy(0.dp)) {
            Text(
                text = when {
                    battery == null -> "--%"
                    implausible -> "--"
                    else -> "${battery.percent}%"
                },
                color = accentColor,
                fontFamily = MonoFamily,
                fontWeight = FontWeight.SemiBold,
                fontSize = 14.sp,
                letterSpacing = 1.sp
            )
            if (battery != null) {
                Text(
                    text = if (implausible) "TÉLÉ. ANORM." else "%.2f V".format(battery.volts),
                    color = if (implausible) palette.magenta else palette.textMuted,
                    fontFamily = MonoFamily,
                    fontWeight = FontWeight.Normal,
                    fontSize = 11.sp,
                    letterSpacing = 1.sp
                )
            }
        }
    }
    @Suppress("UNUSED_EXPRESSION") brightness
}
