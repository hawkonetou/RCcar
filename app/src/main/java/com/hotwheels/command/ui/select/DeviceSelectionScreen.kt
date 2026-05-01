package com.hotwheels.command.ui.select

import android.content.Intent
import android.provider.Settings
import androidx.compose.foundation.background
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
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.platform.LocalContext
import androidx.compose.ui.unit.dp
import androidx.lifecycle.compose.collectAsStateWithLifecycle
import com.hotwheels.command.ui.components.ScanlineBackground
import com.hotwheels.command.ui.theme.AccentElectric
import com.hotwheels.command.ui.theme.BgPrimary
import com.hotwheels.command.ui.theme.BgSurface
import com.hotwheels.command.ui.theme.TextMuted
import com.hotwheels.command.ui.theme.TextPrimary

@Composable
fun DeviceSelectionScreen(
    viewModel: DeviceSelectionViewModel,
    onDeviceSelected: (PairedDevice) -> Unit
) {
    val devices by viewModel.devices.collectAsStateWithLifecycle()
    val context = LocalContext.current

    LaunchedEffect(Unit) { viewModel.refresh() }

    Box(modifier = Modifier.fillMaxSize().background(BgPrimary)) {
        ScanlineBackground()
        Column(modifier = Modifier.fillMaxSize().padding(24.dp)) {
            Text(text = "> APPAREILS APPAIRÉS", color = AccentElectric)
            Spacer(Modifier.height(16.dp))
            LazyColumn(modifier = Modifier.weight(1f)) {
                items(devices) { device ->
                    Row(
                        modifier = Modifier
                            .fillMaxWidth()
                            .background(BgSurface)
                            .clickable { onDeviceSelected(device) }
                            .padding(16.dp)
                    ) {
                        Column {
                            Text(text = device.name, color = TextPrimary)
                            Text(text = "▸ ${device.address}", color = TextMuted)
                        }
                    }
                    Spacer(Modifier.height(8.dp))
                }
            }
            Row(
                horizontalArrangement = Arrangement.spacedBy(12.dp),
                verticalAlignment = Alignment.CenterVertically
            ) {
                Button(
                    onClick = { viewModel.refresh() },
                    colors = ButtonDefaults.buttonColors(containerColor = BgSurface, contentColor = AccentElectric)
                ) { Text("ACTUALISER") }
                Button(
                    onClick = { context.startActivity(Intent(Settings.ACTION_BLUETOOTH_SETTINGS)) },
                    colors = ButtonDefaults.buttonColors(containerColor = BgSurface, contentColor = AccentElectric)
                ) { Text("RÉGLAGES BT") }
            }
        }
    }
}
