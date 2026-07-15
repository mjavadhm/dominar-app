package com.dominar.ride.ui.screens

import androidx.compose.foundation.Image
import androidx.compose.foundation.background
import androidx.compose.foundation.border
import androidx.compose.foundation.clickable
import androidx.compose.foundation.layout.*
import androidx.compose.foundation.rememberScrollState
import androidx.compose.foundation.shape.CircleShape
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.foundation.verticalScroll
import androidx.compose.material3.*
import androidx.compose.runtime.*
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clip
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.layout.ContentScale
import androidx.compose.ui.res.painterResource
import androidx.compose.ui.text.font.FontFamily
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.text.style.TextOverflow
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import com.dominar.ride.R
import com.dominar.ride.ble.BleConnectionManager.ConnectionState
import com.dominar.ride.ui.AppState
import com.dominar.ride.ui.theme.*

private val StatusError = Color(0xFFEF5350)
private val StatusIdle = Color(0xFF8A93A5)

private fun statusOf(state: ConnectionState): Pair<String, Color> = when (state) {
    is ConnectionState.Connected -> "Connected" to StatusGood
    is ConnectionState.Connecting -> "Connecting…" to StatusWarning
    is ConnectionState.Reconnecting -> "Reconnecting (${state.attempt})…" to StatusWarning
    is ConnectionState.Scanning -> "Scanning…" to StatusWarning
    is ConnectionState.Error -> "Error" to StatusError
    else -> "Disconnected" to StatusIdle
}

@Composable
fun HomeScreen(
    app: AppState,
    onStartRide: () -> Unit,
    onOpenBleTest: () -> Unit = {}
) {
    val state by app.connectionState.collectAsState()
    val logs by app.logs.collectAsState()
    var showPicker by remember { mutableStateOf(false) }

    if (showPicker) {
        DevicePickerSheet(app = app, onDismiss = { showPicker = false })
    }

    Column(
        modifier = Modifier
            .fillMaxSize()
            .background(MaterialTheme.colorScheme.background)
            .statusBarsPadding()
            .verticalScroll(rememberScrollState())
            .padding(horizontal = 16.dp)
            .padding(bottom = 24.dp)
    ) {
        HomeHeader(state = state, onOpenBleTest = onOpenBleTest)
        VehicleCard(app = app, state = state)
        Spacer(Modifier.height(16.dp))
        ConnectionCard(app = app, state = state, onPickDevice = { showPicker = true })
        Spacer(Modifier.height(16.dp))
        StartRideButton(onStartRide)
        Spacer(Modifier.height(16.dp))
        ActivityCard(logs = logs)
    }
}

@Composable
private fun HomeHeader(state: ConnectionState, onOpenBleTest: () -> Unit) {
    val (_, statusColor) = statusOf(state)
    Row(
        modifier = Modifier
            .fillMaxWidth()
            .padding(vertical = 16.dp),
        horizontalArrangement = Arrangement.SpaceBetween,
        verticalAlignment = Alignment.CenterVertically
    ) {
        Row(
            verticalAlignment = Alignment.CenterVertically,
            horizontalArrangement = Arrangement.spacedBy(8.dp)
        ) {
            Box(
                modifier = Modifier
                    .size(10.dp)
                    .clip(CircleShape)
                    .background(statusColor)
            )
            Text(
                text = "DOMINAR",
                style = MaterialTheme.typography.titleMedium,
                fontWeight = FontWeight.Black,
                letterSpacing = 3.sp,
                color = MaterialTheme.colorScheme.onBackground
            )
        }
        // Developer tools (BLE test) entry
        Box(
            modifier = Modifier
                .size(42.dp)
                .clip(CircleShape)
                .background(MaterialTheme.colorScheme.surface)
                .clickable { onOpenBleTest() },
            contentAlignment = Alignment.Center
        ) {
            Text("📡", fontSize = 18.sp)
        }
    }
}

@Composable
private fun VehicleCard(app: AppState, state: ConnectionState) {
    val (statusText, statusColor) = statusOf(state)
    Column(
        modifier = Modifier
            .fillMaxWidth()
            .clip(RoundedCornerShape(20.dp))
            .background(MaterialTheme.colorScheme.surface)
            .padding(20.dp)
    ) {
        Row(
            modifier = Modifier.fillMaxWidth(),
            horizontalArrangement = Arrangement.SpaceBetween,
            verticalAlignment = Alignment.Top
        ) {
            Column {
                Text(
                    text = app.savedDeviceName ?: "Dominar",
                    style = MaterialTheme.typography.titleLarge,
                    fontWeight = FontWeight.Bold,
                    color = MaterialTheme.colorScheme.onSurface,
                    maxLines = 1,
                    overflow = TextOverflow.Ellipsis
                )
                Text(
                    text = app.savedDeviceAddress ?: "No cluster paired",
                    style = MaterialTheme.typography.bodySmall,
                    color = TextSubtleDark
                )
            }
            Row(
                verticalAlignment = Alignment.CenterVertically,
                horizontalArrangement = Arrangement.spacedBy(5.dp),
                modifier = Modifier
                    .clip(RoundedCornerShape(50))
                    .background(statusColor.copy(alpha = 0.12f))
                    .padding(horizontal = 10.dp, vertical = 5.dp)
            ) {
                Box(
                    modifier = Modifier
                        .size(7.dp)
                        .clip(CircleShape)
                        .background(statusColor)
                )
                Text(
                    text = statusText,
                    style = MaterialTheme.typography.labelSmall,
                    color = statusColor,
                    fontWeight = FontWeight.SemiBold
                )
            }
        }

        Image(
            painter = painterResource(id = R.drawable.ic_dominar),
            contentDescription = "Dominar",
            modifier = Modifier
                .fillMaxWidth()
                .height(180.dp),
            contentScale = ContentScale.Fit
        )
    }
}

@Composable
private fun ConnectionCard(
    app: AppState,
    state: ConnectionState,
    onPickDevice: () -> Unit
) {
    Column(
        modifier = Modifier
            .fillMaxWidth()
            .clip(RoundedCornerShape(20.dp))
            .background(MaterialTheme.colorScheme.surface)
            .padding(16.dp),
        verticalArrangement = Arrangement.spacedBy(12.dp)
    ) {
        if (app.savedDeviceAddress == null) {
            Text(
                text = "Pair the app with your bike's cluster to get started.",
                style = MaterialTheme.typography.bodyMedium,
                color = TextSubtleDark
            )
            Button(
                onClick = onPickDevice,
                modifier = Modifier
                    .fillMaxWidth()
                    .height(52.dp),
                shape = RoundedCornerShape(14.dp),
                colors = ButtonDefaults.buttonColors(containerColor = PrimaryBlue)
            ) {
                Text("Find my cluster", fontWeight = FontWeight.Bold)
            }
        } else {
            val busy = state is ConnectionState.Connecting ||
                state is ConnectionState.Reconnecting ||
                state is ConnectionState.Scanning

            when {
                state is ConnectionState.Connected -> OutlinedButton(
                    onClick = { app.disconnect() },
                    modifier = Modifier
                        .fillMaxWidth()
                        .height(52.dp),
                    shape = RoundedCornerShape(14.dp)
                ) {
                    Text("Disconnect", fontWeight = FontWeight.Bold)
                }
                busy -> Button(
                    onClick = {},
                    enabled = false,
                    modifier = Modifier
                        .fillMaxWidth()
                        .height(52.dp),
                    shape = RoundedCornerShape(14.dp)
                ) {
                    CircularProgressIndicator(
                        modifier = Modifier.size(18.dp),
                        strokeWidth = 2.dp
                    )
                    Spacer(Modifier.width(10.dp))
                    Text(statusOf(state).first)
                }
                else -> Button(
                    onClick = { app.connectSaved() },
                    modifier = Modifier
                        .fillMaxWidth()
                        .height(52.dp),
                    shape = RoundedCornerShape(14.dp),
                    colors = ButtonDefaults.buttonColors(containerColor = PrimaryBlue)
                ) {
                    Text("Connect", fontWeight = FontWeight.Bold)
                }
            }

            Row(
                modifier = Modifier.fillMaxWidth(),
                horizontalArrangement = Arrangement.SpaceBetween,
                verticalAlignment = Alignment.CenterVertically
            ) {
                Text(
                    text = "Auto-connect",
                    style = MaterialTheme.typography.bodyMedium,
                    color = MaterialTheme.colorScheme.onSurface
                )
                Switch(
                    checked = app.autoConnect,
                    onCheckedChange = { app.setAutoConnect(it) }
                )
            }

            Row(horizontalArrangement = Arrangement.spacedBy(8.dp)) {
                TextButton(onClick = onPickDevice) { Text("Change device") }
                TextButton(onClick = { app.forgetDevice() }) {
                    Text("Forget", color = StatusError)
                }
            }
        }
    }
}

@Composable
private fun StartRideButton(onStartRide: () -> Unit) {
    Button(
        onClick = onStartRide,
        modifier = Modifier
            .fillMaxWidth()
            .height(56.dp),
        shape = RoundedCornerShape(14.dp),
        colors = ButtonDefaults.buttonColors(containerColor = PrimaryBlue)
    ) {
        Text("🏍", fontSize = 18.sp)
        Spacer(Modifier.width(8.dp))
        Text("Start Ride", fontSize = 17.sp, fontWeight = FontWeight.Bold)
    }
}

@Composable
private fun ActivityCard(logs: List<String>) {
    Column(
        modifier = Modifier
            .fillMaxWidth()
            .clip(RoundedCornerShape(20.dp))
            .background(MaterialTheme.colorScheme.surface)
            .border(1.dp, BorderDark, RoundedCornerShape(20.dp))
            .padding(16.dp)
    ) {
        Text(
            text = "LIVE ACTIVITY",
            style = MaterialTheme.typography.labelSmall,
            color = TextSubtleDark,
            letterSpacing = 1.2.sp
        )
        Spacer(Modifier.height(8.dp))
        if (logs.isEmpty()) {
            Text(
                text = "No activity yet — connect to your cluster.",
                style = MaterialTheme.typography.bodySmall,
                color = TextSubtleDark
            )
        } else {
            logs.takeLast(6).forEach { line ->
                Text(
                    text = line,
                    fontSize = 11.sp,
                    fontFamily = FontFamily.Monospace,
                    color = MaterialTheme.colorScheme.onSurface.copy(alpha = 0.75f),
                    maxLines = 1,
                    overflow = TextOverflow.Ellipsis
                )
            }
        }
    }
}

@OptIn(ExperimentalMaterial3Api::class)
@Composable
private fun DevicePickerSheet(app: AppState, onDismiss: () -> Unit) {
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
                    Text("🏍", fontSize = 20.sp)
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
