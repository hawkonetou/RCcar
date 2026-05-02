package com.hotwheels.command.ui.drive

import androidx.compose.foundation.background
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.fillMaxHeight
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.width
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableIntStateOf
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.setValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.text.font.FontFamily
import androidx.compose.ui.text.font.FontStyle
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import com.hotwheels.command.bluetooth.BatteryState
import com.hotwheels.command.bluetooth.ConnectionState
import com.hotwheels.command.ui.components.BatteryBadge
import com.hotwheels.command.ui.components.BigThrottleNumber
import com.hotwheels.command.ui.components.ConnectingRing
import com.hotwheels.command.ui.components.CornerBrackets
import com.hotwheels.command.ui.components.DiagButton
import com.hotwheels.command.ui.components.DiagSheet
import com.hotwheels.command.ui.components.DirectionTriangles
import com.hotwheels.command.ui.components.LedDot
import com.hotwheels.command.ui.components.LedState
import com.hotwheels.command.ui.components.NeonVerticalSlider
import com.hotwheels.command.ui.components.ScanlineBackground
import com.hotwheels.command.ui.components.TelemetryCard
import com.hotwheels.command.ui.theme.AccentElectric
import com.hotwheels.command.ui.theme.AccentMagenta
import com.hotwheels.command.ui.theme.BgPrimary
import com.hotwheels.command.ui.theme.CyanDim20
import com.hotwheels.command.ui.theme.CyanDim35
import com.hotwheels.command.ui.theme.CyanDim50
import com.hotwheels.command.ui.theme.DotoFamily
import com.hotwheels.command.ui.theme.MonoFamily
import com.hotwheels.command.ui.theme.TextPrimary
import kotlin.math.abs

@Composable
fun DriveScreen(
    state: ConnectionState,
    viewModel: DriveViewModel,
    battery: BatteryState? = null
) {
    val enabled = state is ConnectionState.Connected
    var sliderValue by remember { mutableIntStateOf(0) }
    var diagOpen by remember { mutableStateOf(false) }

    val ledState = when (state) {
        is ConnectionState.Connected -> LedState.On
        is ConnectionState.Connecting, is ConnectionState.Reconnecting -> LedState.Pending
        else -> LedState.Off
    }
    val deviceName = when (state) {
        is ConnectionState.Connected -> state.deviceName
        is ConnectionState.Connecting -> state.deviceName
        is ConnectionState.Reconnecting -> state.deviceName
        is ConnectionState.Failed -> state.deviceName
        else -> "DEVICE"
    }
    val deviceMac = when (state) {
        is ConnectionState.Connected -> state.deviceAddress
        is ConnectionState.Connecting -> state.deviceAddress
        is ConnectionState.Reconnecting -> state.deviceAddress
        is ConnectionState.Failed -> state.deviceAddress
        else -> "--:--:--:--:--:--"
    }

    Box(modifier = Modifier.fillMaxSize().background(BgPrimary)) {
        ScanlineBackground()
        Column(modifier = Modifier.fillMaxSize().padding(12.dp)) {

            TopBar(
                ledState = ledState,
                deviceName = deviceName,
                deviceMac = deviceMac,
                battery = battery,
                onDiag = { diagOpen = true }
            )

            Spacer(Modifier.height(8.dp))

            if (state is ConnectionState.Connecting || state is ConnectionState.Reconnecting) {
                ConnectingPanel(modifier = Modifier.weight(1f))
            } else {
                ConnectedHud(
                    sliderValue = sliderValue,
                    enabled = enabled,
                    onSliderChange = {
                        sliderValue = it
                        viewModel.onSliderValueChange(it)
                    },
                    onSliderRelease = {
                        sliderValue = 0
                        viewModel.onSliderReleased()
                    },
                    modifier = Modifier.weight(1f)
                )
            }

            Spacer(Modifier.height(4.dp))
            BottomBar(active = enabled)
        }

        if (diagOpen) {
            DiagSheet(onDismiss = { diagOpen = false })
        }
    }
}

@Composable
private fun TopBar(
    ledState: LedState,
    deviceName: String,
    deviceMac: String,
    battery: BatteryState?,
    onDiag: () -> Unit
) {
    Row(
        modifier = Modifier.fillMaxWidth(),
        verticalAlignment = Alignment.CenterVertically,
        horizontalArrangement = Arrangement.SpaceBetween
    ) {
        Row(
            verticalAlignment = Alignment.CenterVertically,
            horizontalArrangement = Arrangement.spacedBy(10.dp)
        ) {
            LedDot(state = ledState)
            Text(
                text = deviceName,
                color = AccentElectric,
                fontFamily = MonoFamily,
                fontWeight = FontWeight.SemiBold,
                fontSize = 12.sp,
                letterSpacing = 1.5.sp
            )
            Text(
                text = "▸ $deviceMac",
                color = CyanDim50,
                fontFamily = MonoFamily,
                fontSize = 10.sp,
                letterSpacing = 1.sp
            )
            Text(
                text = "concept by Tom LEBRETON",
                color = AccentMagenta.copy(alpha = 0.7f),
                fontFamily = MonoFamily,
                fontStyle = FontStyle.Italic,
                fontSize = 9.sp,
                letterSpacing = 1.5.sp
            )
        }
        Row(
            verticalAlignment = Alignment.CenterVertically,
            horizontalArrangement = Arrangement.spacedBy(10.dp)
        ) {
            DiagButton(onClick = onDiag)
            BatteryBadge(battery = battery)
        }
    }
}

@Composable
private fun ConnectedHud(
    sliderValue: Int,
    enabled: Boolean,
    onSliderChange: (Int) -> Unit,
    onSliderRelease: () -> Unit,
    modifier: Modifier = Modifier
) {
    Row(
        modifier = modifier.fillMaxWidth(),
        verticalAlignment = Alignment.CenterVertically
    ) {
        // LEFT — telemetry cards
        Column(
            modifier = Modifier.width(150.dp),
            verticalArrangement = Arrangement.spacedBy(6.dp)
        ) {
            TelemetryCard(label = "DIRECTION", accent = true) {
                DirectionTriangles(motorValue = sliderValue, modifier = Modifier.padding(top = 2.dp))
                Spacer(Modifier.height(3.dp))
                Text(
                    text = if (sliderValue > 0) "▶ AVANT"
                    else if (sliderValue < 0) "◀ ARRIÈRE"
                    else "■ ARRÊT",
                    color = AccentElectric,
                    fontFamily = MonoFamily,
                    fontSize = 11.sp,
                    fontWeight = FontWeight.SemiBold,
                    letterSpacing = 1.sp
                )
            }
            TelemetryCard(label = "PWM SORTIE") {
                val pwm = (abs(sliderValue) * 255 / 100)
                Row(verticalAlignment = Alignment.Bottom) {
                    Text(
                        text = pwm.toString(),
                        color = AccentElectric,
                        fontFamily = DotoFamily,
                        fontWeight = FontWeight.Black,
                        fontSize = 22.sp
                    )
                    Spacer(Modifier.width(4.dp))
                    Text(
                        text = "/ 255",
                        color = CyanDim50.copy(alpha = 0.55f),
                        fontFamily = MonoFamily,
                        fontSize = 10.sp
                    )
                }
            }
            TelemetryCard(label = "SIGNAL") {
                Text(
                    text = if (enabled) "▣ ACTIF" else "▣ INACTIF",
                    color = AccentElectric,
                    fontFamily = MonoFamily,
                    fontSize = 11.sp,
                    fontWeight = FontWeight.SemiBold,
                    letterSpacing = 1.sp
                )
            }
        }

        Spacer(Modifier.width(12.dp))

        // CENTER — big number with brackets
        Box(
            modifier = Modifier.weight(1f).fillMaxHeight(),
            contentAlignment = Alignment.Center
        ) {
            Box(modifier = Modifier.fillMaxSize().padding(horizontal = 24.dp, vertical = 12.dp)) {
                CornerBrackets()
            }
            Column(horizontalAlignment = Alignment.CenterHorizontally) {
                Text(
                    text = "▸ THROTTLE % ◂",
                    color = CyanDim50,
                    fontFamily = MonoFamily,
                    fontSize = 9.sp,
                    letterSpacing = 3.sp
                )
                Spacer(Modifier.height(2.dp))
                BigThrottleNumber(value = sliderValue)
                Spacer(Modifier.height(2.dp))
                Text(
                    text = "▸ ENVOI : ${-sliderValue} ◂",
                    color = AccentElectric.copy(alpha = 0.65f),
                    fontFamily = MonoFamily,
                    fontSize = 10.sp,
                    letterSpacing = 2.5.sp
                )
            }
        }

        Spacer(Modifier.width(12.dp))

        // RIGHT — vertical slider with graduations
        Row(
            verticalAlignment = Alignment.CenterVertically
        ) {
            // graduations labels
            Column(
                modifier = Modifier.fillMaxHeight().padding(vertical = 6.dp),
                verticalArrangement = Arrangement.SpaceBetween,
                horizontalAlignment = Alignment.End
            ) {
                listOf("+100", "+050", "+000", "-050", "-100").forEachIndexed { i, label ->
                    Text(
                        text = label,
                        color = if (i == 2) AccentElectric else CyanDim50,
                        fontFamily = MonoFamily,
                        fontSize = 8.sp,
                        letterSpacing = 1.sp
                    )
                }
            }
            Spacer(Modifier.width(4.dp))
            NeonVerticalSlider(
                value = if (enabled) sliderValue else 0,
                enabled = enabled,
                onValueChange = onSliderChange,
                onRelease = onSliderRelease
            )
        }
    }
}

@Composable
private fun ConnectingPanel(modifier: Modifier = Modifier) {
    Box(modifier = modifier.fillMaxWidth(), contentAlignment = Alignment.Center) {
        Box(modifier = Modifier.fillMaxSize().padding(horizontal = 80.dp, vertical = 40.dp)) {
            CornerBrackets()
        }
        Column(horizontalAlignment = Alignment.CenterHorizontally, verticalArrangement = Arrangement.spacedBy(8.dp)) {
            ConnectingRing()
            Text(
                text = "ÉTABLISSEMENT DU LIEN",
                color = AccentElectric,
                fontFamily = MonoFamily,
                fontWeight = FontWeight.Medium,
                fontSize = 13.sp,
                letterSpacing = 4.sp
            )
            Text(
                text = "▸ SPP RFCOMM · UUID 00001101-… ◂",
                color = CyanDim50,
                fontFamily = MonoFamily,
                fontSize = 9.sp,
                letterSpacing = 2.sp
            )
        }
    }
}

@Composable
private fun BottomBar(active: Boolean) {
    Row(
        modifier = Modifier.fillMaxWidth(),
        horizontalArrangement = Arrangement.SpaceBetween,
        verticalAlignment = Alignment.CenterVertically
    ) {
        Row(verticalAlignment = Alignment.CenterVertically, horizontalArrangement = Arrangement.spacedBy(10.dp)) {
            Text(
                text = "▣ MOTEUR",
                color = CyanDim50,
                fontFamily = MonoFamily,
                fontSize = 9.sp,
                letterSpacing = 2.sp
            )
            Text(
                text = if (active) "ACTIF" else "STAND BY",
                color = AccentElectric,
                fontFamily = MonoFamily,
                fontSize = 9.sp,
                fontWeight = FontWeight.SemiBold,
                letterSpacing = 2.sp
            )
        }
        Row(verticalAlignment = Alignment.CenterVertically, horizontalArrangement = Arrangement.spacedBy(8.dp)) {
            Text(
                text = "▶ PILOTAGE",
                color = CyanDim50,
                fontFamily = MonoFamily,
                fontSize = 9.sp,
                letterSpacing = 2.sp
            )
            Text(
                text = "v0.3.0",
                color = CyanDim50,
                fontFamily = MonoFamily,
                fontSize = 9.sp
            )
        }
    }
}
