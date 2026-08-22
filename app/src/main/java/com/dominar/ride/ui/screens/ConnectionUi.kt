package com.dominar.ride.ui.screens

import androidx.compose.foundation.background
import androidx.compose.foundation.clickable
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.material3.CircularProgressIndicator
import androidx.compose.material3.ExperimentalMaterial3Api
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.ModalBottomSheet
import androidx.compose.material3.Text
import androidx.compose.material3.TextButton
import androidx.compose.runtime.Composable
import androidx.compose.runtime.LaunchedEffect
import androidx.compose.runtime.collectAsState
import androidx.compose.runtime.getValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clip
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import com.dominar.ride.ble.BleConnectionManager.ConnectionState
import com.dominar.ride.ui.AppState
import com.dominar.ride.ui.theme.StatusGood
import com.dominar.ride.ui.theme.StatusWarning
import com.dominar.ride.ui.theme.TextSubtleDark

internal val ConnError = Color(0xFFEF5350)
internal val ConnIdle = Color(0xFF8A93A5)

internal fun connectionStatusOf(state: ConnectionState): Pair<String, Color> = when (state) {
    is ConnectionState.Connected -> "Connected" to StatusGood
    is ConnectionState.Connecting -> "Connecting…" to StatusWarning
    is ConnectionState.Reconnecting -> "Reconnecting (${state.attempt})…" to StatusWarning
    is ConnectionState.Scanning -> "Scanning…" to StatusWarning
    is ConnectionState.Error -> "Error" to ConnError
    else -> "Disconnected" to ConnIdle
}

@OptIn(ExperimentalMaterial3Api::class)
@Composable
internal fun DevicePickerSheet(app: AppState, onDismiss: () -> Unit) {
    val devices by app.foundDevices.collectAsState()
    val state by app.connectionState.collectAsState()

    LaunchedEffect(Unit) { app.startScan() }

    ModalBottomSheet(onDismissRequest = { app.stopScan(); onDismiss() }) {
        Column(
            modifier = Modifier
                .fillMaxWidth()
                .padding(horizontal = 20.dp)
                .padding(bottom = 32.dp),
            verticalArrangement = Arrangement.spacedBy(12.dp)
        ) {
            Row(
                modifier = Modifier.fillMaxWidth(),
                horizontalArrangement = Arrangement.SpaceBetween,
                verticalAlignment = Alignment.CenterVertically
            ) {
                Text(
                    text = "Select your cluster",
                    style = MaterialTheme.typography.titleMedium,
                    fontWeight = FontWeight.Bold
                )
                if (state is ConnectionState.Scanning) {
                    CircularProgressIndicator(
                        modifier = Modifier.size(18.dp),
                        strokeWidth = 2.dp
                    )
                } else {
                    TextButton(onClick = { app.startScan() }) { Text("Rescan") }
                }
            }

            if (devices.isEmpty()) {
                Text(
                    text = "Turn the bike's ignition on and keep the phone close.",
                    style = MaterialTheme.typography.bodySmall,
                    color = TextSubtleDark
                )
            }

            devices.forEach { (device, deviceName) ->
                Row(
                    modifier = Modifier
                        .fillMaxWidth()
                        .clip(RoundedCornerShape(14.dp))
                        .background(MaterialTheme.colorScheme.surface)
                        .clickable {
                            app.stopScan()
                            app.connectTo(device)
                            onDismiss()
                        }
                        .padding(14.dp),
                    verticalAlignment = Alignment.CenterVertically,
                    horizontalArrangement = Arrangement.spacedBy(12.dp)
                ) {
                    Text("\uD83C\uDFCD", fontSize = 20.sp)
                    Column {
                        Text(
                            text = deviceName ?: "Unknown device",
                            style = MaterialTheme.typography.bodyMedium,
                            fontWeight = FontWeight.SemiBold
                        )
                        Text(
                            text = device.address,
                            style = MaterialTheme.typography.labelSmall,
                            color = TextSubtleDark
                        )
                    }
                }
            }
        }
    }
}
