package com.dominar.ride.ui.screens

import androidx.activity.compose.BackHandler
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
import androidx.compose.ui.text.font.FontFamily
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.text.style.TextOverflow
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import com.dominar.ride.ble.BleConnectionManager.ConnectionState
import com.dominar.ride.debug.DebugSection
import com.dominar.ride.ui.AppState
import com.dominar.ride.ui.theme.BorderDark
import com.dominar.ride.ui.theme.PrimaryBlue
import com.dominar.ride.ui.theme.TextSubtleDark

@Composable
fun SettingsScreen(
    app: AppState,
    onBack: () -> Unit,
    onOpenPermissions: () -> Unit = {}
) {
    BackHandler(onBack = onBack)

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
        Row(
            modifier = Modifier
                .fillMaxWidth()
                .padding(vertical = 8.dp),
            verticalAlignment = Alignment.CenterVertically,
            horizontalArrangement = Arrangement.spacedBy(4.dp)
        ) {
            IconButton(onClick = onBack) { Text("\u2190", fontSize = 22.sp) }
            Text(
                text = "Settings",
                style = MaterialTheme.typography.titleLarge,
                fontWeight = FontWeight.Bold
            )
        }

        SectionLabel("CLUSTER CONNECTION")
        ConnectionSection(app = app, state = state, onPickDevice = { showPicker = true })

        SectionLabel("RIDE HUD")
        HudSection(app)

        SectionLabel("PERMISSIONS")
        PermissionsRow(onClick = onOpenPermissions)

        SectionLabel("LIVE ACTIVITY")
        ActivityLog(logs)

        SectionLabel("DEBUG")
        DebugSection()
    }
}

@Composable
private fun SectionLabel(text: String) {
    Text(
        text = text,
        style = MaterialTheme.typography.labelSmall,
        color = TextSubtleDark,
        letterSpacing = 1.2.sp,
        modifier = Modifier.padding(top = 20.dp, bottom = 8.dp)
    )
}

@Composable
private fun PermissionsRow(onClick: () -> Unit) {
    Row(
        modifier = Modifier
            .fillMaxWidth()
            .clip(RoundedCornerShape(20.dp))
            .background(MaterialTheme.colorScheme.surface)
            .clickable(onClick = onClick)
            .padding(16.dp),
        verticalAlignment = Alignment.CenterVertically
    ) {
        Column(Modifier.weight(1f)) {
            Text(
                text = "Permission & access guide",
                style = MaterialTheme.typography.bodyLarge,
                fontWeight = FontWeight.SemiBold
            )
            Text(
                text = "See why each permission is needed and grant anything you skipped.",
                style = MaterialTheme.typography.labelSmall,
                color = TextSubtleDark
            )
        }
        Text("\u203A", fontSize = 22.sp, color = TextSubtleDark)
    }
}

@Composable
private fun ConnectionSection(
    app: AppState,
    state: ConnectionState,
    onPickDevice: () -> Unit
) {
    val (statusText, statusColor) = connectionStatusOf(state)
    Column(
        modifier = Modifier
            .fillMaxWidth()
            .clip(RoundedCornerShape(20.dp))
            .background(MaterialTheme.colorScheme.surface)
            .padding(16.dp),
        verticalArrangement = Arrangement.spacedBy(12.dp)
    ) {
        Row(
            modifier = Modifier.fillMaxWidth(),
            horizontalArrangement = Arrangement.SpaceBetween,
            verticalAlignment = Alignment.CenterVertically
        ) {
            Column(Modifier.weight(1f)) {
                Text(
                    text = app.savedDeviceName ?: "No cluster paired",
                    style = MaterialTheme.typography.bodyLarge,
                    fontWeight = FontWeight.SemiBold,
                    maxLines = 1,
                    overflow = TextOverflow.Ellipsis
                )
                Text(
                    text = app.savedDeviceAddress
                        ?: "Pair the app with your bike's cluster.",
                    style = MaterialTheme.typography.labelSmall,
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

        if (app.savedDeviceAddress == null) {
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
                    Text(statusText)
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
                    Text("Forget", color = ConnError)
                }
            }
        }
    }
}

@Composable
private fun HudSection(app: AppState) {
    Column(
        modifier = Modifier
            .fillMaxWidth()
            .clip(RoundedCornerShape(20.dp))
            .background(MaterialTheme.colorScheme.surface)
            .padding(horizontal = 16.dp, vertical = 8.dp)
    ) {
        SwitchRow("Speedometer", app.hudShowSpeed) { app.setHudShowSpeed(it) }
        SwitchRow("Lean angle gauge", app.hudShowLeanAngle) { app.setHudShowLeanAngle(it) }
        SwitchRow("Next-turn banner", app.hudShowNextTurn) { app.setHudShowNextTurn(it) }
        Text(
            text = "These overlays appear on the Ride screen while a route is active. " +
                "Turn-by-turn directions are also sent to the cluster when connected.",
            style = MaterialTheme.typography.labelSmall,
            color = TextSubtleDark,
            modifier = Modifier.padding(bottom = 8.dp)
        )
    }
}

@Composable
private fun SwitchRow(
    label: String,
    checked: Boolean,
    onCheckedChange: (Boolean) -> Unit
) {
    Row(
        modifier = Modifier.fillMaxWidth(),
        horizontalArrangement = Arrangement.SpaceBetween,
        verticalAlignment = Alignment.CenterVertically
    ) {
        Text(
            text = label,
            style = MaterialTheme.typography.bodyMedium,
            color = MaterialTheme.colorScheme.onSurface
        )
        Switch(checked = checked, onCheckedChange = onCheckedChange)
    }
}

@Composable
private fun ActivityLog(logs: List<String>) {
    Column(
        modifier = Modifier
            .fillMaxWidth()
            .clip(RoundedCornerShape(20.dp))
            .background(MaterialTheme.colorScheme.surface)
            .border(1.dp, BorderDark, RoundedCornerShape(20.dp))
            .padding(16.dp)
    ) {
        if (logs.isEmpty()) {
            Text(
                text = "No activity yet \u2014 connect to your cluster.",
                style = MaterialTheme.typography.bodySmall,
                color = TextSubtleDark
            )
        } else {
            logs.takeLast(10).forEach { line ->
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
