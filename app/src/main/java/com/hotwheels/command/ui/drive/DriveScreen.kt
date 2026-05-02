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
import androidx.compose.ui.text.font.FontStyle
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import androidx.lifecycle.compose.collectAsStateWithLifecycle
import com.hotwheels.command.bluetooth.BatteryState
import com.hotwheels.command.bluetooth.ConnectionState
import com.hotwheels.command.data.SteeringEnabledStore
import com.hotwheels.command.ui.components.BatteryBadge
import com.hotwheels.command.ui.components.BigThrottleNumber
import com.hotwheels.command.ui.components.ConnectingRing
import com.hotwheels.command.ui.components.CornerBrackets
import com.hotwheels.command.ui.components.DiagButton
import com.hotwheels.command.ui.components.DiagSheet
import com.hotwheels.command.ui.components.DirectionTriangles
import com.hotwheels.command.ui.components.LedDot
import com.hotwheels.command.ui.components.LedState
import com.hotwheels.command.ui.components.LinkQualityIndicator
import com.hotwheels.command.ui.components.NeonHorizontalSlider
import com.hotwheels.command.ui.components.NeonVerticalSlider
import com.hotwheels.command.ui.components.ScanlineBackground
import com.hotwheels.command.ui.components.TelemetryCard
import com.hotwheels.command.ui.components.ThemeToggleButton
import com.hotwheels.command.ui.theme.DotoFamily
import com.hotwheels.command.ui.theme.LocalPalette
import com.hotwheels.command.ui.theme.MonoFamily
import kotlin.math.abs

@Composable
fun DriveScreen(
    state: ConnectionState,
    viewModel: DriveViewModel,
    battery: BatteryState? = null,
    linkFreshMs: Long = Long.MAX_VALUE
) {
    val palette = LocalPalette.current
    val enabled = state is ConnectionState.Connected
    var sliderValue by remember { mutableIntStateOf(0) }
    var steeringValue by remember { mutableIntStateOf(0) }
    var diagOpen by remember { mutableStateOf(false) }
    val steeringEnabled by SteeringEnabledStore.enabled.collectAsStateWithLifecycle()

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

    Box(modifier = Modifier.fillMaxSize().background(palette.bg)) {
        ScanlineBackground()
        Column(modifier = Modifier.fillMaxSize().padding(12.dp)) {

            TopBar(
                ledState = ledState,
                deviceName = deviceName,
                deviceMac = deviceMac,
                battery = battery,
                linkFreshMs = linkFreshMs,
                onDiag = { diagOpen = true }
            )

            Spacer(Modifier.height(8.dp))

            if (state is ConnectionState.Connecting || state is ConnectionState.Reconnecting) {
                ConnectingPanel(modifier = Modifier.weight(1f))
            } else {
                ConnectedHud(
                    sliderValue = sliderValue,
                    steeringValue = steeringValue,
                    steeringEnabled = steeringEnabled,
                    enabled = enabled,
                    onSliderChange = {
                        sliderValue = it
                        viewModel.onSliderValueChange(it)
                    },
                    onSliderRelease = {
                        sliderValue = 0
                        viewModel.onSliderReleased()
                    },
                    onSteeringChange = {
                        steeringValue = it
                        viewModel.onSteeringChange(it)
                    },
                    onSteeringRelease = {
                        steeringValue = 0
                        viewModel.onSteeringReleased()
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
    linkFreshMs: Long,
    onDiag: () -> Unit
) {
    val palette = LocalPalette.current
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
                color = palette.accent,
                fontFamily = MonoFamily,
                fontWeight = FontWeight.SemiBold,
                fontSize = 14.sp,
                letterSpacing = 1.5.sp
            )
            Text(
                text = "▸ $deviceMac",
                color = palette.textMuted,
                fontFamily = MonoFamily,
                fontSize = 12.sp,
                letterSpacing = 1.sp
            )
            Text(
                text = "concept by Tom LEBRETON",
                color = palette.magenta,
                fontFamily = MonoFamily,
                fontStyle = FontStyle.Italic,
                fontSize = 11.sp,
                letterSpacing = 1.5.sp
            )
        }
        Row(
            verticalAlignment = Alignment.CenterVertically,
            horizontalArrangement = Arrangement.spacedBy(8.dp)
        ) {
            LinkQualityIndicator(freshMs = linkFreshMs)
            ThemeToggleButton()
            DiagButton(onClick = onDiag)
            BatteryBadge(battery = battery)
        }
    }
}

@Composable
private fun ConnectedHud(
    sliderValue: Int,
    steeringValue: Int,
    steeringEnabled: Boolean,
    enabled: Boolean,
    onSliderChange: (Int) -> Unit,
    onSliderRelease: () -> Unit,
    onSteeringChange: (Int) -> Unit,
    onSteeringRelease: () -> Unit,
    modifier: Modifier = Modifier
) {
    val palette = LocalPalette.current
    Row(
        modifier = modifier.fillMaxWidth(),
        verticalAlignment = Alignment.CenterVertically
    ) {
        if (steeringEnabled) {
            // Layout direction : slider horizontal au-dessus, 3 cartes en ligne en bas.
            SteeringLeftZone(
                sliderValue = sliderValue,
                steeringValue = steeringValue,
                enabled = enabled,
                onSteeringChange = onSteeringChange,
                onSteeringRelease = onSteeringRelease,
                modifier = Modifier.weight(1.1f).fillMaxHeight()
            )
        } else {
            Column(
                modifier = Modifier.width(160.dp),
                verticalArrangement = Arrangement.spacedBy(6.dp)
            ) {
                TelemetryCard(label = "DIRECTION", accent = true) {
                    DirectionTriangles(motorValue = sliderValue, modifier = Modifier.padding(top = 2.dp))
                    Spacer(Modifier.height(3.dp))
                    Text(
                        text = if (sliderValue > 0) "▶ AVANT"
                        else if (sliderValue < 0) "◀ ARRIÈRE"
                        else "■ ARRÊT",
                        color = palette.accent,
                        fontFamily = MonoFamily,
                        fontSize = 13.sp,
                        fontWeight = FontWeight.SemiBold,
                        letterSpacing = 1.sp
                    )
                }
                TelemetryCard(label = "PWM SORTIE") {
                    val pwm = (abs(sliderValue) * 255 / 100)
                    Row(verticalAlignment = Alignment.Bottom) {
                        Text(
                            text = pwm.toString(),
                            color = palette.accent,
                            fontFamily = DotoFamily,
                            fontWeight = FontWeight.Black,
                            fontSize = 26.sp
                        )
                        Spacer(Modifier.width(4.dp))
                        Text(
                            text = "/ 255",
                            color = palette.textSubtle,
                            fontFamily = MonoFamily,
                            fontSize = 12.sp
                        )
                    }
                }
                TelemetryCard(label = "SIGNAL") {
                    Text(
                        text = if (enabled) "▣ ACTIF" else "▣ INACTIF",
                        color = palette.accent,
                        fontFamily = MonoFamily,
                        fontSize = 13.sp,
                        fontWeight = FontWeight.SemiBold,
                        letterSpacing = 1.sp
                    )
                }
            }
        }

        Spacer(Modifier.width(12.dp))

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
                    color = palette.textMuted,
                    fontFamily = MonoFamily,
                    fontSize = 11.sp,
                    letterSpacing = 3.sp
                )
                Spacer(Modifier.height(2.dp))
                BigThrottleNumber(value = sliderValue)
                Spacer(Modifier.height(2.dp))
                Text(
                    text = "▸ ENVOI : ${-sliderValue} ◂",
                    color = palette.accent,
                    fontFamily = MonoFamily,
                    fontSize = 12.sp,
                    letterSpacing = 2.5.sp
                )
            }
        }

        Spacer(Modifier.width(12.dp))

        Row(
            verticalAlignment = Alignment.CenterVertically
        ) {
            Column(
                modifier = Modifier.fillMaxHeight().padding(vertical = 6.dp),
                verticalArrangement = Arrangement.SpaceBetween,
                horizontalAlignment = Alignment.End
            ) {
                listOf("+100", "+050", "+000", "-050", "-100").forEachIndexed { i, label ->
                    Text(
                        text = label,
                        color = if (i == 2) palette.accent else palette.textMuted,
                        fontFamily = MonoFamily,
                        fontSize = 11.sp,
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
private fun SteeringLeftZone(
    sliderValue: Int,
    steeringValue: Int,
    enabled: Boolean,
    onSteeringChange: (Int) -> Unit,
    onSteeringRelease: () -> Unit,
    modifier: Modifier = Modifier
) {
    val palette = LocalPalette.current
    Column(
        modifier = modifier,
        verticalArrangement = Arrangement.spacedBy(8.dp)
    ) {
        // Bandeau slider direction.
        Column(
            modifier = Modifier.fillMaxWidth().weight(1f),
            verticalArrangement = Arrangement.Center,
            horizontalAlignment = Alignment.CenterHorizontally
        ) {
            Row(
                modifier = Modifier.fillMaxWidth(),
                horizontalArrangement = Arrangement.SpaceBetween,
                verticalAlignment = Alignment.CenterVertically
            ) {
                Text(
                    text = "▸ DIRECTION",
                    color = palette.textMuted,
                    fontFamily = MonoFamily,
                    fontSize = 10.sp,
                    letterSpacing = 2.5.sp
                )
                Text(
                    text = when {
                        steeringValue > 0 -> "▶ DROITE ${steeringValue}"
                        steeringValue < 0 -> "◀ GAUCHE ${-steeringValue}"
                        else -> "■ CENTRE"
                    },
                    color = palette.accent,
                    fontFamily = MonoFamily,
                    fontSize = 11.sp,
                    fontWeight = FontWeight.SemiBold,
                    letterSpacing = 1.5.sp
                )
            }
            Spacer(Modifier.height(4.dp))
            NeonHorizontalSlider(
                value = if (enabled) steeringValue else 0,
                enabled = enabled,
                onValueChange = onSteeringChange,
                onRelease = onSteeringRelease
            )
            Spacer(Modifier.height(4.dp))
            Row(
                modifier = Modifier.fillMaxWidth(),
                horizontalArrangement = Arrangement.SpaceBetween
            ) {
                Text("-100", color = palette.textMuted, fontFamily = MonoFamily, fontSize = 9.sp, letterSpacing = 1.sp)
                Text("0", color = palette.accent, fontFamily = MonoFamily, fontSize = 9.sp, letterSpacing = 1.sp)
                Text("+100", color = palette.textMuted, fontFamily = MonoFamily, fontSize = 9.sp, letterSpacing = 1.sp)
            }
        }

        // 3 cartes telemetrie compactes en ligne.
        Row(
            modifier = Modifier.fillMaxWidth(),
            horizontalArrangement = Arrangement.spacedBy(6.dp)
        ) {
            Box(modifier = Modifier.weight(1f)) {
                TelemetryCard(label = "SIGNAL") {
                    Text(
                        text = if (enabled) "▣ ACTIF" else "▣ INACTIF",
                        color = palette.accent,
                        fontFamily = MonoFamily,
                        fontSize = 12.sp,
                        fontWeight = FontWeight.SemiBold,
                        letterSpacing = 1.sp
                    )
                }
            }
            Box(modifier = Modifier.weight(1f)) {
                TelemetryCard(label = "PWM SORTIE") {
                    val pwm = (abs(sliderValue) * 255 / 100)
                    Row(verticalAlignment = Alignment.Bottom) {
                        Text(
                            text = pwm.toString(),
                            color = palette.accent,
                            fontFamily = DotoFamily,
                            fontWeight = FontWeight.Black,
                            fontSize = 22.sp
                        )
                        Spacer(Modifier.width(3.dp))
                        Text(
                            text = "/ 255",
                            color = palette.textSubtle,
                            fontFamily = MonoFamily,
                            fontSize = 11.sp
                        )
                    }
                }
            }
            Box(modifier = Modifier.weight(1.1f)) {
                TelemetryCard(label = "DIRECTION", accent = true) {
                    DirectionTriangles(motorValue = sliderValue, modifier = Modifier.padding(top = 2.dp))
                    Spacer(Modifier.height(2.dp))
                    Text(
                        text = if (sliderValue > 0) "▶ AVANT"
                        else if (sliderValue < 0) "◀ ARRIÈRE"
                        else "■ ARRÊT",
                        color = palette.accent,
                        fontFamily = MonoFamily,
                        fontSize = 12.sp,
                        fontWeight = FontWeight.SemiBold,
                        letterSpacing = 1.sp
                    )
                }
            }
        }
    }
}

@Composable
private fun ConnectingPanel(modifier: Modifier = Modifier) {
    val palette = LocalPalette.current
    Box(modifier = modifier.fillMaxWidth(), contentAlignment = Alignment.Center) {
        Box(modifier = Modifier.fillMaxSize().padding(horizontal = 80.dp, vertical = 40.dp)) {
            CornerBrackets()
        }
        Column(horizontalAlignment = Alignment.CenterHorizontally, verticalArrangement = Arrangement.spacedBy(8.dp)) {
            ConnectingRing()
            Text(
                text = "ÉTABLISSEMENT DU LIEN",
                color = palette.accent,
                fontFamily = MonoFamily,
                fontWeight = FontWeight.SemiBold,
                fontSize = 15.sp,
                letterSpacing = 4.sp
            )
            Text(
                text = "▸ SPP RFCOMM · UUID 00001101-… ◂",
                color = palette.textMuted,
                fontFamily = MonoFamily,
                fontSize = 11.sp,
                letterSpacing = 2.sp
            )
        }
    }
}

@Composable
private fun BottomBar(active: Boolean) {
    val palette = LocalPalette.current
    Row(
        modifier = Modifier.fillMaxWidth(),
        horizontalArrangement = Arrangement.SpaceBetween,
        verticalAlignment = Alignment.CenterVertically
    ) {
        Row(verticalAlignment = Alignment.CenterVertically, horizontalArrangement = Arrangement.spacedBy(10.dp)) {
            Text(
                text = "▣ MOTEUR",
                color = palette.textMuted,
                fontFamily = MonoFamily,
                fontSize = 11.sp,
                letterSpacing = 2.sp
            )
            Text(
                text = if (active) "ACTIF" else "STAND BY",
                color = palette.accent,
                fontFamily = MonoFamily,
                fontSize = 11.sp,
                fontWeight = FontWeight.SemiBold,
                letterSpacing = 2.sp
            )
        }
        Row(verticalAlignment = Alignment.CenterVertically, horizontalArrangement = Arrangement.spacedBy(8.dp)) {
            Text(
                text = "▶ PILOTAGE",
                color = palette.textMuted,
                fontFamily = MonoFamily,
                fontSize = 11.sp,
                letterSpacing = 2.sp
            )
            Text(
                text = "v0.5.0",
                color = palette.textMuted,
                fontFamily = MonoFamily,
                fontSize = 11.sp
            )
        }
    }
}
