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
import androidx.compose.foundation.layout.Spacer
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
import androidx.compose.ui.text.font.FontFamily
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.text.style.TextAlign
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import com.hotwheels.command.bluetooth.BatteryState
import com.hotwheels.command.ui.theme.AccentElectric
import com.hotwheels.command.ui.theme.AccentLime
import com.hotwheels.command.ui.theme.MonoFamily
import com.hotwheels.command.ui.theme.StateConnecting
import com.hotwheels.command.ui.theme.StateError
import com.hotwheels.command.ui.theme.TextMuted

@Composable
fun BatteryBadge(battery: BatteryState?, modifier: Modifier = Modifier) {
    val (fillColor, accentColor) = when {
        battery == null -> Brush.horizontalGradient(listOf(TextMuted, TextMuted)) to TextMuted
        battery.percent <= 20 -> Brush.horizontalGradient(listOf(StateConnecting, StateError)) to StateError
        battery.percent <= 50 -> Brush.horizontalGradient(listOf(AccentElectric, StateConnecting)) to StateConnecting
        else -> Brush.horizontalGradient(listOf(AccentElectric, AccentLime)) to AccentElectric
    }

    val transition = rememberInfiniteTransition(label = "bat")
    val pulseDuration = if (battery != null && battery.percent <= 20) 900 else 3200
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
        horizontalArrangement = Arrangement.spacedBy(6.dp),
        modifier = modifier
    ) {
        Box(modifier = Modifier.size(width = 56.dp, height = 22.dp)) {
            Box(
                modifier = Modifier
                    .fillMaxHeight()
                    .width(50.dp)
                    .border(1.5.dp, accentColor, RoundedCornerShape(3.dp))
                    .padding(2.dp)
            ) {
                val pct = (battery?.percent ?: 0).coerceIn(0, 100)
                if (pct > 0) {
                    Box(
                        modifier = Modifier
                            .fillMaxHeight()
                            .clip(RoundedCornerShape(1.dp))
                            .background(fillColor)
                            .width((46.dp.value * pct / 100f).dp)
                    ) {
                        Canvas(modifier = Modifier.fillMaxHeight().width((46.dp.value * pct / 100f).dp)) {
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
            // battery cap
            Box(
                modifier = Modifier
                    .align(Alignment.CenterEnd)
                    .height(10.dp)
                    .width(4.dp)
                    .background(accentColor, RoundedCornerShape(topEnd = 2.dp, bottomEnd = 2.dp))
            )
        }
        Column(verticalArrangement = Arrangement.spacedBy(0.dp)) {
            Text(
                text = battery?.let { "${it.percent}%" } ?: "--%",
                color = accentColor,
                fontFamily = MonoFamily,
                fontWeight = FontWeight.SemiBold,
                fontSize = 13.sp,
                letterSpacing = 1.sp
            )
            if (battery != null) {
                Text(
                    text = "%.2f V".format(battery.volts),
                    color = accentColor.copy(alpha = 0.5f),
                    fontFamily = MonoFamily,
                    fontWeight = FontWeight.Light,
                    fontSize = 9.sp,
                    letterSpacing = 1.sp
                )
            }
        }
    }
    @Suppress("UNUSED_EXPRESSION") brightness // keep animation alive
}
