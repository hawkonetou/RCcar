package com.hotwheels.command.ui.select

import android.content.Intent
import android.provider.Settings
import androidx.compose.foundation.background
import androidx.compose.foundation.border
import androidx.compose.foundation.clickable
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.lazy.LazyColumn
import androidx.compose.foundation.lazy.items
import androidx.compose.material3.Button
import androidx.compose.material3.ButtonDefaults
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.runtime.LaunchedEffect
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.setValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.platform.LocalContext
import androidx.compose.ui.text.font.FontFamily
import androidx.compose.ui.text.font.FontStyle
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import androidx.lifecycle.compose.collectAsStateWithLifecycle
import com.hotwheels.command.ui.components.DiagButton
import com.hotwheels.command.ui.components.DiagSheet
import com.hotwheels.command.ui.components.ScanlineBackground
import com.hotwheels.command.ui.theme.AccentElectric
import com.hotwheels.command.ui.theme.AccentMagenta
import com.hotwheels.command.ui.theme.BgPrimary
import com.hotwheels.command.ui.theme.BgSurface
import com.hotwheels.command.ui.theme.CyanDim35
import com.hotwheels.command.ui.theme.CyanDim50
import com.hotwheels.command.ui.theme.MonoFamily
import com.hotwheels.command.ui.theme.TextMuted
import com.hotwheels.command.ui.theme.TextPrimary

@Composable
fun DeviceSelectionScreen(
    viewModel: DeviceSelectionViewModel,
    lastError: String? = null,
    onDeviceSelected: (PairedDevice) -> Unit
) {
    val devices by viewModel.devices.collectAsStateWithLifecycle()
    val context = LocalContext.current
    var diagOpen by remember { mutableStateOf(false) }

    LaunchedEffect(Unit) { viewModel.refresh() }

    Box(modifier = Modifier.fillMaxSize().background(BgPrimary)) {
        ScanlineBackground()
        Column(modifier = Modifier.fillMaxSize().padding(16.dp)) {

            // Header
            Row(
                modifier = Modifier.fillMaxWidth(),
                horizontalArrangement = Arrangement.SpaceBetween,
                verticalAlignment = Alignment.CenterVertically
            ) {
                Column {
                    Text(
                        text = "▸ APPAREILS APPAIRÉS",
                        color = AccentElectric,
                        fontFamily = MonoFamily,
                        fontSize = 14.sp,
                        fontWeight = FontWeight.SemiBold,
                        letterSpacing = 2.sp
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
                DiagButton(onClick = { diagOpen = true })
            }
            Spacer(Modifier.height(12.dp))

            if (lastError != null) {
                Box(
                    modifier = Modifier
                        .fillMaxWidth()
                        .background(Color(0x33FF1744))
                        .border(1.dp, Color(0xFFFF1744))
                        .padding(8.dp)
                ) {
                    Text(
                        text = "▸ ÉCHEC : $lastError",
                        color = Color(0xFFFF8A95),
                        fontFamily = MonoFamily,
                        fontSize = 11.sp
                    )
                }
                Spacer(Modifier.height(8.dp))
            }

            LazyColumn(modifier = Modifier.weight(1f)) {
                items(devices) { device ->
                    Row(
                        modifier = Modifier
                            .fillMaxWidth()
                            .border(1.dp, CyanDim35)
                            .background(BgSurface)
                            .clickable { onDeviceSelected(device) }
                            .padding(14.dp)
                    ) {
                        Column {
                            Text(
                                text = device.name,
                                color = TextPrimary,
                                fontFamily = MonoFamily,
                                fontWeight = FontWeight.SemiBold,
                                fontSize = 14.sp,
                                letterSpacing = 1.sp
                            )
                            Spacer(Modifier.height(2.dp))
                            Text(
                                text = "▸ ${device.address}",
                                color = CyanDim50,
                                fontFamily = MonoFamily,
                                fontSize = 10.sp,
                                letterSpacing = 1.sp
                            )
                        }
                    }
                    Spacer(Modifier.height(6.dp))
                }
            }

            Spacer(Modifier.height(12.dp))
            Row(
                horizontalArrangement = Arrangement.spacedBy(8.dp),
                verticalAlignment = Alignment.CenterVertically
            ) {
                Button(
                    onClick = { viewModel.refresh() },
                    colors = ButtonDefaults.buttonColors(containerColor = BgSurface, contentColor = AccentElectric)
                ) { Text("ACTUALISER", fontSize = 10.sp, fontFamily = MonoFamily, letterSpacing = 2.sp) }
                Button(
                    onClick = { context.startActivity(Intent(Settings.ACTION_BLUETOOTH_SETTINGS)) },
                    colors = ButtonDefaults.buttonColors(containerColor = BgSurface, contentColor = AccentElectric)
                ) { Text("RÉGLAGES BT", fontSize = 10.sp, fontFamily = MonoFamily, letterSpacing = 2.sp) }
            }
        }

        if (diagOpen) {
            DiagSheet(onDismiss = { diagOpen = false })
        }
    }
}
