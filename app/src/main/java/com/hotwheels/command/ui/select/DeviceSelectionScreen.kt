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
import androidx.compose.foundation.lazy.rememberLazyListState
import androidx.compose.material3.Button
import androidx.compose.material3.ButtonDefaults
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.runtime.LaunchedEffect
import androidx.compose.runtime.getValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.platform.LocalContext
import androidx.compose.ui.text.font.FontFamily
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import androidx.lifecycle.compose.collectAsStateWithLifecycle
import com.hotwheels.command.ui.components.ScanlineBackground
import com.hotwheels.command.ui.theme.AccentElectric
import com.hotwheels.command.ui.theme.BgPrimary
import com.hotwheels.command.ui.theme.BgSurface
import com.hotwheels.command.ui.theme.TextMuted
import com.hotwheels.command.ui.theme.TextPrimary
import com.hotwheels.command.util.DiagLog

@Composable
fun DeviceSelectionScreen(
    viewModel: DeviceSelectionViewModel,
    lastError: String? = null,
    onDeviceSelected: (PairedDevice) -> Unit
) {
    val devices by viewModel.devices.collectAsStateWithLifecycle()
    val diagEntries by DiagLog.entries.collectAsStateWithLifecycle()
    val context = LocalContext.current

    LaunchedEffect(Unit) { viewModel.refresh() }

    val diagListState = rememberLazyListState()
    LaunchedEffect(diagEntries.size) {
        if (diagEntries.isNotEmpty()) {
            diagListState.scrollToItem(diagEntries.size - 1)
        }
    }

    Box(modifier = Modifier.fillMaxSize().background(BgPrimary)) {
        ScanlineBackground()
        Column(modifier = Modifier.fillMaxSize().padding(16.dp)) {
            Text(text = "> APPAREILS APPAIRÉS", color = AccentElectric)
            Spacer(Modifier.height(8.dp))

            if (lastError != null) {
                Box(
                    modifier = Modifier
                        .fillMaxWidth()
                        .background(Color(0x33FF1744))
                        .border(1.dp, Color(0xFFFF1744))
                        .padding(8.dp)
                ) {
                    Text(
                        text = "> ÉCHEC: $lastError",
                        color = Color(0xFFFF8A95),
                        fontFamily = FontFamily.Monospace,
                        fontSize = 12.sp
                    )
                }
                Spacer(Modifier.height(8.dp))
            }

            LazyColumn(modifier = Modifier.weight(1f)) {
                items(devices) { device ->
                    Row(
                        modifier = Modifier
                            .fillMaxWidth()
                            .background(BgSurface)
                            .clickable { onDeviceSelected(device) }
                            .padding(12.dp)
                    ) {
                        Column {
                            Text(text = device.name, color = TextPrimary)
                            Text(text = "▸ ${device.address}", color = TextMuted, fontSize = 11.sp)
                        }
                    }
                    Spacer(Modifier.height(4.dp))
                }
            }

            Spacer(Modifier.height(8.dp))
            Text(text = "> JOURNAL DIAG (${diagEntries.size})", color = AccentElectric)
            Spacer(Modifier.height(4.dp))
            Box(
                modifier = Modifier
                    .fillMaxWidth()
                    .weight(1f)
                    .background(Color(0xFF0A0A0A))
                    .border(1.dp, AccentElectric)
                    .padding(4.dp)
            ) {
                LazyColumn(state = diagListState, modifier = Modifier.fillMaxSize()) {
                    items(diagEntries) { entry ->
                        Text(
                            text = entry,
                            color = TextPrimary,
                            fontFamily = FontFamily.Monospace,
                            fontSize = 9.sp
                        )
                    }
                }
            }

            Spacer(Modifier.height(8.dp))
            Row(
                horizontalArrangement = Arrangement.spacedBy(8.dp),
                verticalAlignment = Alignment.CenterVertically
            ) {
                Button(
                    onClick = { viewModel.refresh() },
                    colors = ButtonDefaults.buttonColors(containerColor = BgSurface, contentColor = AccentElectric)
                ) { Text("ACTUALISER", fontSize = 11.sp) }
                Button(
                    onClick = { context.startActivity(Intent(Settings.ACTION_BLUETOOTH_SETTINGS)) },
                    colors = ButtonDefaults.buttonColors(containerColor = BgSurface, contentColor = AccentElectric)
                ) { Text("RÉGLAGES BT", fontSize = 11.sp) }
                Button(
                    onClick = { DiagLog.clear() },
                    colors = ButtonDefaults.buttonColors(containerColor = BgSurface, contentColor = AccentElectric)
                ) { Text("EFFACER LOG", fontSize = 11.sp) }
            }
        }
    }
}
