package com.hotwheels.command.ui.drive

import androidx.compose.foundation.background
import androidx.compose.foundation.border
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.fillMaxHeight
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.width
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableIntStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.setValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.text.font.FontFamily
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import com.hotwheels.command.bluetooth.BatteryState
import com.hotwheels.command.bluetooth.ConnectionState
import com.hotwheels.command.ui.components.ConnectionIndicator
import com.hotwheels.command.ui.components.GlowText
import com.hotwheels.command.ui.components.NeonVerticalSlider
import com.hotwheels.command.ui.components.ScanlineBackground
import com.hotwheels.command.ui.theme.AccentElectric
import com.hotwheels.command.ui.theme.AccentGlow
import com.hotwheels.command.ui.theme.BgPrimary
import com.hotwheels.command.ui.theme.TextMuted

@Composable
fun DriveScreen(
    state: ConnectionState,
    viewModel: DriveViewModel,
    battery: BatteryState? = null
) {
    val enabled = state is ConnectionState.Connected
    var sliderValue by remember { mutableIntStateOf(0) }

    Box(modifier = Modifier.fillMaxSize().background(BgPrimary)) {
        ScanlineBackground()
        Column(modifier = Modifier.fillMaxSize().padding(16.dp)) {
            Row(
                modifier = Modifier.fillMaxHeight(0.0f).padding(bottom = 4.dp),
                horizontalArrangement = Arrangement.SpaceBetween,
                verticalAlignment = Alignment.CenterVertically
            ) {
                ConnectionIndicator(state = state)
                BatteryBadge(battery = battery)
            }
            Spacer(Modifier.weight(1f))
            Row(
                modifier = Modifier.fillMaxHeight(0.85f),
                verticalAlignment = Alignment.CenterVertically
            ) {
                Column(
                    modifier = Modifier.weight(1f),
                    horizontalAlignment = Alignment.CenterHorizontally
                ) {
                    GlowText(
                        text = sliderValue.toString(),
                        style = MaterialTheme.typography.displayLarge,
                        color = AccentElectric,
                        glow = AccentGlow
                    )
                    Spacer(Modifier.width(8.dp))
                    Text(text = "envoyé : ${-sliderValue}", color = TextMuted)
                }
                Spacer(Modifier.width(16.dp))
                NeonVerticalSlider(
                    value = if (enabled) sliderValue else 0,
                    enabled = enabled,
                    onValueChange = { v ->
                        sliderValue = v
                        viewModel.onSliderValueChange(v)
                    },
                    onRelease = {
                        sliderValue = 0
                        viewModel.onSliderReleased()
                    },
                    modifier = Modifier.fillMaxHeight()
                )
            }
            Spacer(Modifier.weight(1f))
            Row(
                horizontalArrangement = Arrangement.SpaceBetween,
                modifier = Modifier.padding(top = 8.dp)
            ) {
                Text("pilotage HotWheels", color = TextMuted)
                Text("v1.0.0", color = TextMuted)
            }
        }
    }
}

@Composable
private fun BatteryBadge(battery: BatteryState?) {
    val (text, color) = when {
        battery == null -> "🔋 -- %" to TextMuted
        battery.percent > 50 -> "🔋 ${battery.percent}% (${"%.2f".format(battery.volts)} V)" to Color(0xFF00E676)
        battery.percent > 20 -> "🔋 ${battery.percent}% (${"%.2f".format(battery.volts)} V)" to Color(0xFFFFC400)
        else -> "🪫 ${battery.percent}% (${"%.2f".format(battery.volts)} V)" to Color(0xFFFF1744)
    }
    Box(
        modifier = Modifier
            .background(Color(0xFF111111))
            .border(1.dp, color)
            .padding(horizontal = 8.dp, vertical = 4.dp)
    ) {
        Text(text = text, color = color, fontFamily = FontFamily.Monospace, fontSize = 12.sp)
    }
}
