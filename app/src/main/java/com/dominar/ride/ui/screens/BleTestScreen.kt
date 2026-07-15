package com.dominar.ride.ui.screens

import android.Manifest
import android.content.pm.PackageManager
import android.os.Build
import androidx.activity.compose.rememberLauncherForActivityResult
import androidx.activity.result.contract.ActivityResultContracts
import androidx.compose.foundation.*
import androidx.compose.foundation.layout.*
import androidx.compose.foundation.lazy.LazyColumn
import androidx.compose.foundation.lazy.items
import androidx.compose.foundation.lazy.rememberLazyListState
import androidx.compose.foundation.shape.CircleShape
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.material3.*
import androidx.compose.runtime.*
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clip
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.platform.LocalContext
import androidx.compose.ui.text.font.FontFamily
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import androidx.core.content.ContextCompat
import com.dominar.ride.ble.BleConnectionManager
import com.dominar.ride.ui.theme.*

@Composable
fun BleTestScreen(
    viewModel: BleTestViewModel,
    onBack: () -> Unit
) {
    val context = LocalContext.current
    val connectionState by viewModel.connectionState.collectAsState()
    val logs by viewModel.logs.collectAsState()
    val foundDevices by viewModel.foundDevices.collectAsState()
    val scrollState = rememberScrollState()

    // Permission launcher
    var permissionsGranted by remember { mutableStateOf(false) }
    val permLauncher = rememberLauncherForActivityResult(
        ActivityResultContracts.RequestMultiplePermissions()
    ) { results ->
        permissionsGranted = results.values.all { it }
    }

    LaunchedEffect(Unit) {
        val perms = if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.S) {
            arrayOf(Manifest.permission.BLUETOOTH_SCAN, Manifest.permission.BLUETOOTH_CONNECT)
        } else {
            arrayOf(Manifest.permission.ACCESS_FINE_LOCATION)
        }
        val allGranted = perms.all {
            ContextCompat.checkSelfPermission(context, it) == PackageManager.PERMISSION_GRANTED
        }
        if (allGranted) permissionsGranted = true
        else permLauncher.launch(perms)
    }

    // Init manager
    LaunchedEffect(Unit) {
        viewModel.initManager(context)
    }

    // Selected tab
    var selectedTab by remember { mutableIntStateOf(0) }
    val tabs = listOf("Connect", "Navigation", "Alert", "Phone")

    Box(
        modifier = Modifier
            .fillMaxSize()
            .background(MaterialTheme.colorScheme.background)
    ) {
        Column(
            modifier = Modifier
                .fillMaxSize()
                .statusBarsPadding()
        ) {
            // Header
            Row(
                modifier = Modifier
                    .fillMaxWidth()
                    .padding(horizontal = 16.dp, vertical = 12.dp),
                verticalAlignment = Alignment.CenterVertically
            ) {
                Box(
                    modifier = Modifier
                        .size(38.dp)
                        .clip(CircleShape)
                        .background(MaterialTheme.colorScheme.surface)
                        .clickable { onBack() },
                    contentAlignment = Alignment.Center
                ) {
                    Text("←", fontSize = 18.sp, color = MaterialTheme.colorScheme.onSurface)
                }
                Spacer(modifier = Modifier.width(12.dp))
                Text(
                    text = "BLE Protocol Tester",
                    style = MaterialTheme.typography.titleMedium,
                    fontWeight = FontWeight.Bold,
                    color = MaterialTheme.colorScheme.onBackground
                )
                Spacer(modifier = Modifier.weight(1f))
                // Status dot
                val dotColor = when (connectionState) {
                    is BleConnectionManager.ConnectionState.Connected -> StatusGood
                    is BleConnectionManager.ConnectionState.Connecting,
                    is BleConnectionManager.ConnectionState.Reconnecting,
                    is BleConnectionManager.ConnectionState.Scanning -> StatusWarning
                    is BleConnectionManager.ConnectionState.Error -> StatusDanger
                    else -> TextSubtleDark
                }
                Box(
                    modifier = Modifier
                        .size(10.dp)
                        .clip(CircleShape)
                        .background(dotColor)
                )
            }

            // Tabs
            ScrollableTabRow(
                selectedTabIndex = selectedTab,
                containerColor = Color.Transparent,
                contentColor = PrimaryBlue,
                edgePadding = 16.dp,
                divider = {}
            ) {
                tabs.forEachIndexed { index, title ->
                    Tab(
                        selected = selectedTab == index,
                        onClick = { selectedTab = index },
                        text = {
                            Text(
                                title,
                                fontWeight = if (selectedTab == index) FontWeight.Bold else FontWeight.Normal,
                                fontSize = 13.sp
                            )
                        }
                    )
                }
            }

            Spacer(modifier = Modifier.height(8.dp))

            // Content
            Column(
                modifier = Modifier
                    .weight(1f)
                    .verticalScroll(scrollState)
                    .padding(horizontal = 16.dp)
            ) {
                when (selectedTab) {
                    0 -> ConnectionTab(viewModel, connectionState, foundDevices, permissionsGranted)
                    1 -> NavigationTab(viewModel, connectionState)
                    2 -> AlertTab(viewModel, connectionState)
                    3 -> PhoneTab(viewModel, connectionState)
                }

                Spacer(modifier = Modifier.height(16.dp))
            }

            // Log panel
            LogPanel(logs)
        }
    }
}

// ==================== CONNECTION TAB ====================

@Composable
private fun ConnectionTab(
    vm: BleTestViewModel,
    state: BleConnectionManager.ConnectionState,
    devices: List<Pair<android.bluetooth.BluetoothDevice, String>>,
    permsGranted: Boolean
) {
    val statusText = when (state) {
        is BleConnectionManager.ConnectionState.Disconnected -> "Disconnected"
        is BleConnectionManager.ConnectionState.Scanning -> "Scanning..."
        is BleConnectionManager.ConnectionState.Connecting -> "Connecting..."
        is BleConnectionManager.ConnectionState.Reconnecting -> "Connecting... (Retry ${state.attempt})"
        is BleConnectionManager.ConnectionState.Connected -> "Connected ✓"
        is BleConnectionManager.ConnectionState.Error -> "Error: ${state.message}"
    }

    SectionCard("Connection Status") {
        Text(statusText, color = MaterialTheme.colorScheme.onSurface, fontWeight = FontWeight.Medium)
        Spacer(modifier = Modifier.height(12.dp))

        Row(horizontalArrangement = Arrangement.spacedBy(8.dp)) {
            if (state is BleConnectionManager.ConnectionState.Connected) {
                TestButton("Disconnect", StatusDanger) { vm.disconnect() }
            } else {
                TestButton(
                    if (state is BleConnectionManager.ConnectionState.Scanning) "Scanning..." else "Scan",
                    PrimaryBlue,
                    enabled = permsGranted && state !is BleConnectionManager.ConnectionState.Scanning
                ) { vm.startScan() }
            }
        }
    }

    if (devices.isNotEmpty()) {
        Spacer(modifier = Modifier.height(12.dp))
        SectionCard("Found Devices") {
            devices.forEach { (device, name) ->
                Row(
                    modifier = Modifier
                        .fillMaxWidth()
                        .clip(RoundedCornerShape(10.dp))
                        .background(MaterialTheme.colorScheme.background)
                        .clickable { vm.connectToDevice(device) }
                        .padding(12.dp),
                    horizontalArrangement = Arrangement.SpaceBetween,
                    verticalAlignment = Alignment.CenterVertically
                ) {
                    Column {
                        Text(name, fontWeight = FontWeight.Bold, color = MaterialTheme.colorScheme.onSurface, fontSize = 14.sp)
                        @Suppress("MissingPermission")
                        Text(device.address, color = TextSubtleDark, fontSize = 11.sp)
                    }
                    Text("Connect →", color = PrimaryBlue, fontSize = 13.sp, fontWeight = FontWeight.Medium)
                }
                Spacer(modifier = Modifier.height(6.dp))
            }
        }
    }
}

// ==================== NAVIGATION TAB ====================

@Composable
private fun NavigationTab(vm: BleTestViewModel, state: BleConnectionManager.ConnectionState) {
    val isConnected = state is BleConnectionManager.ConnectionState.Connected
    val maneuver by vm.navManeuver.collectAsState()
    val distance by vm.navDistance.collectAsState()
    val distFrac by vm.navDistFrac.collectAsState()
    val distLeftInt by vm.navDistLeftInt.collectAsState()
    val distLeftFrac by vm.navDistLeftFrac.collectAsState()
    val isMeters by vm.navIsMeters.collectAsState()
    val text by vm.navText.collectAsState()
    val etaH by vm.navEtaH.collectAsState()
    val etaM by vm.navEtaM.collectAsState()

    val maneuvers = listOf(
        0x47 to "↑ STRAIGHT",
        0x49 to "← TURN LEFT",
        0x4A to "→ TURN RIGHT",
        0x43 to "↰ SLIGHT LEFT",
        0x44 to "↱ SLIGHT RIGHT",
        0x45 to "⤹ SHARP LEFT",
        0x46 to "⤸ SHARP RIGHT",
        0x4F to "↩ U-TURN LEFT",
        0x50 to "↪ U-TURN RIGHT",
        0x4E to "⟳ ROUNDABOUT LEFT",
        0x55 to "⟲ ROUNDABOUT RIGHT",
        0x48 to "🏁 DESTINATION"
    )

    SectionCard("Navigation Controls") {
        Row(horizontalArrangement = Arrangement.spacedBy(8.dp)) {
            TestButton("Start Nav", StatusGood, enabled = isConnected) { vm.sendNavStart() }
            TestButton("Stop Nav", StatusDanger, enabled = isConnected) { vm.sendNavStop() }
        }
    }

    Spacer(modifier = Modifier.height(12.dp))

    SectionCard("Turn-by-Turn Form") {
        // Maneuver selector
        Text("Maneuver", color = TextSubtleDark, fontSize = 12.sp)
        Spacer(modifier = Modifier.height(4.dp))
        var expanded by remember { mutableStateOf(false) }
        Box {
            OutlinedButton(
                onClick = { expanded = true },
                modifier = Modifier.fillMaxWidth(),
                shape = RoundedCornerShape(10.dp),
                border = BorderStroke(1.dp, BorderDark)
            ) {
                Text(
                    maneuvers.firstOrNull { it.first == maneuver }?.second ?: "Unknown",
                    color = MaterialTheme.colorScheme.onSurface
                )
            }
            DropdownMenu(expanded = expanded, onDismissRequest = { expanded = false }) {
                maneuvers.forEach { (code, label) ->
                    DropdownMenuItem(
                        text = { Text(label) },
                        onClick = {
                            vm.navManeuver.value = code
                            expanded = false
                        }
                    )
                }
            }
        }

        Spacer(modifier = Modifier.height(10.dp))

        // Unit
        Row(horizontalArrangement = Arrangement.spacedBy(8.dp)) {
            FilterChip(
                selected = isMeters,
                onClick = { vm.navIsMeters.value = true },
                label = { Text("Meters") },
                shape = RoundedCornerShape(10.dp)
            )
            FilterChip(
                selected = !isMeters,
                onClick = { vm.navIsMeters.value = false },
                label = { Text("Kilometers") },
                shape = RoundedCornerShape(10.dp)
            )
        }

        Spacer(modifier = Modifier.height(10.dp))

        // Distance to turn
        Text("Distance to Turn", color = MaterialTheme.colorScheme.onSurface, fontSize = 13.sp, fontWeight = FontWeight.SemiBold)
        Spacer(modifier = Modifier.height(6.dp))
        Row(horizontalArrangement = Arrangement.spacedBy(8.dp)) {
            Column(modifier = Modifier.weight(1f)) {
                FormField("Integer", distance) { vm.navDistance.value = it }
            }
            Column(modifier = Modifier.weight(1f)) {
                FormField("Decimal", distFrac) { vm.navDistFrac.value = it }
            }
        }

        Spacer(modifier = Modifier.height(10.dp))

        // Total Distance Left
        Text("Total Distance Left (Always km)", color = MaterialTheme.colorScheme.onSurface, fontSize = 13.sp, fontWeight = FontWeight.SemiBold)
        Spacer(modifier = Modifier.height(6.dp))
        Row(horizontalArrangement = Arrangement.spacedBy(8.dp)) {
            Column(modifier = Modifier.weight(1f)) {
                FormField("Integer", distLeftInt) { vm.navDistLeftInt.value = it }
            }
            Column(modifier = Modifier.weight(1f)) {
                FormField("Decimal", distLeftFrac) { vm.navDistLeftFrac.value = it }
            }
        }

        Spacer(modifier = Modifier.height(10.dp))

        // ETA
        Row(horizontalArrangement = Arrangement.spacedBy(8.dp)) {
            Column(modifier = Modifier.weight(1f)) {
                FormField("ETA Hours", etaH) { vm.navEtaH.value = it }
            }
            Column(modifier = Modifier.weight(1f)) {
                FormField("ETA Minutes", etaM) { vm.navEtaM.value = it }
            }
        }

        Spacer(modifier = Modifier.height(10.dp))

        // Road name
        FormField("Road Name / Instruction", text) { vm.navText.value = it }

        Spacer(modifier = Modifier.height(14.dp))

        TestButton("Send Navigation →", PrimaryBlue, enabled = isConnected, fullWidth = true) {
            vm.sendNavPacket()
        }
    }
}

// ==================== ALERT TAB ====================

@Composable
private fun AlertTab(vm: BleTestViewModel, state: BleConnectionManager.ConnectionState) {
    val isConnected = state is BleConnectionManager.ConnectionState.Connected
    val alertType by vm.alertType.collectAsState()
    val content by vm.alertContent.collectAsState()

    SectionCard("SMS / WhatsApp Alert") {
        Text("Alert Type", color = TextSubtleDark, fontSize = 12.sp)
        Spacer(modifier = Modifier.height(6.dp))
        Row(horizontalArrangement = Arrangement.spacedBy(8.dp)) {
            FilterChip(
                selected = alertType == 1,
                onClick = { vm.alertType.value = 1 },
                label = { Text("📱 SMS") },
                shape = RoundedCornerShape(10.dp)
            )
            FilterChip(
                selected = alertType == 2,
                onClick = { vm.alertType.value = 2 },
                label = { Text("💬 WhatsApp") },
                shape = RoundedCornerShape(10.dp)
            )
        }

        Spacer(modifier = Modifier.height(10.dp))

        FormField("Message / Sender Name", content) { vm.alertContent.value = it }

        Spacer(modifier = Modifier.height(4.dp))
        Text("Max 32 characters", color = TextSubtleDark, fontSize = 11.sp)

        Spacer(modifier = Modifier.height(14.dp))

        TestButton("Send Alert →", PrimaryBlue, enabled = isConnected, fullWidth = true) {
            vm.sendAlert()
        }
    }
}

// ==================== PHONE TAB ====================

@Composable
private fun PhoneTab(vm: BleTestViewModel, state: BleConnectionManager.ConnectionState) {
    val isConnected = state is BleConnectionManager.ConnectionState.Connected
    val battery by vm.phoneBattery.collectAsState()
    val signal by vm.phoneSignal.collectAsState()
    val volume by vm.phoneVolume.collectAsState()
    val callState by vm.phoneCallState.collectAsState()
    val callerName by vm.phoneCallerName.collectAsState()

    val callStates = listOf("NO_CALL", "INCOMING", "OUTGOING", "ACTIVE", "END_CALL")

    SectionCard("Phone Status") {
        // Battery slider
        Text("Battery Level: $battery", color = TextSubtleDark, fontSize = 12.sp)
        Slider(
            value = battery.toFloat(),
            onValueChange = { vm.phoneBattery.value = it.toInt() },
            valueRange = 0f..5f,
            steps = 4,
            colors = SliderDefaults.colors(thumbColor = PrimaryBlue, activeTrackColor = PrimaryBlue)
        )

        // Signal slider
        Text("Signal Strength: $signal", color = TextSubtleDark, fontSize = 12.sp)
        Slider(
            value = signal.toFloat(),
            onValueChange = { vm.phoneSignal.value = it.toInt() },
            valueRange = 0f..4f,
            steps = 3,
            colors = SliderDefaults.colors(thumbColor = PrimaryBlue, activeTrackColor = PrimaryBlue)
        )

        // Volume slider
        Text("Volume: $volume", color = TextSubtleDark, fontSize = 12.sp)
        Slider(
            value = volume.toFloat(),
            onValueChange = { vm.phoneVolume.value = it.toInt() },
            valueRange = 0f..15f,
            steps = 14,
            colors = SliderDefaults.colors(thumbColor = PrimaryBlue, activeTrackColor = PrimaryBlue)
        )

        Spacer(modifier = Modifier.height(8.dp))

        TestButton("Send Phone Status →", PrimaryBlue, enabled = isConnected, fullWidth = true) {
            vm.sendPhoneStatus()
        }
    }

    Spacer(modifier = Modifier.height(12.dp))

    SectionCard("Call State") {
        Text("State: ${callStates.getOrElse(callState) { "?" }}", color = TextSubtleDark, fontSize = 12.sp)
        Spacer(modifier = Modifier.height(6.dp))
        Row(
            modifier = Modifier.horizontalScroll(rememberScrollState()),
            horizontalArrangement = Arrangement.spacedBy(6.dp)
        ) {
            callStates.forEachIndexed { idx, label ->
                FilterChip(
                    selected = callState == idx,
                    onClick = { vm.phoneCallState.value = idx },
                    label = { Text(label, fontSize = 11.sp) },
                    shape = RoundedCornerShape(8.dp)
                )
            }
        }

        Spacer(modifier = Modifier.height(10.dp))

        FormField("Caller Name", callerName) { vm.phoneCallerName.value = it }

        Spacer(modifier = Modifier.height(14.dp))

        TestButton("Send Call State →", PrimaryBlue, enabled = isConnected, fullWidth = true) {
            vm.sendPhoneStatus()
        }
    }
}

// ==================== SHARED COMPONENTS ====================

@Composable
private fun SectionCard(title: String, content: @Composable ColumnScope.() -> Unit) {
    Column(
        modifier = Modifier
            .fillMaxWidth()
            .clip(RoundedCornerShape(16.dp))
            .background(MaterialTheme.colorScheme.surface)
            .border(1.dp, BorderDark, RoundedCornerShape(16.dp))
            .padding(16.dp)
    ) {
        Text(
            title,
            style = MaterialTheme.typography.titleSmall,
            fontWeight = FontWeight.Bold,
            color = MaterialTheme.colorScheme.onSurface
        )
        Spacer(modifier = Modifier.height(12.dp))
        content()
    }
}

@Composable
private fun FormField(label: String, value: String, onValueChange: (String) -> Unit) {
    Text(label, color = TextSubtleDark, fontSize = 12.sp)
    Spacer(modifier = Modifier.height(4.dp))
    OutlinedTextField(
        value = value,
        onValueChange = onValueChange,
        modifier = Modifier.fillMaxWidth(),
        singleLine = true,
        shape = RoundedCornerShape(10.dp),
        colors = OutlinedTextFieldDefaults.colors(
            focusedBorderColor = PrimaryBlue,
            unfocusedBorderColor = BorderDark,
            cursorColor = PrimaryBlue,
            focusedTextColor = MaterialTheme.colorScheme.onSurface,
            unfocusedTextColor = MaterialTheme.colorScheme.onSurface
        ),
        textStyle = LocalTextStyle.current.copy(fontSize = 14.sp)
    )
}

@Composable
private fun TestButton(
    label: String,
    color: Color,
    enabled: Boolean = true,
    fullWidth: Boolean = false,
    onClick: () -> Unit
) {
    Button(
        onClick = onClick,
        enabled = enabled,
        modifier = if (fullWidth) Modifier.fillMaxWidth().height(46.dp) else Modifier.height(40.dp),
        shape = RoundedCornerShape(10.dp),
        colors = ButtonDefaults.buttonColors(
            containerColor = color,
            disabledContainerColor = color.copy(alpha = 0.3f)
        )
    ) {
        Text(label, fontSize = 13.sp, fontWeight = FontWeight.SemiBold)
    }
}

@Composable
private fun LogPanel(logs: List<String>) {
    val listState = rememberLazyListState()

    LaunchedEffect(logs.size) {
        if (logs.isNotEmpty()) listState.animateScrollToItem(logs.size - 1)
    }

    Column(
        modifier = Modifier
            .fillMaxWidth()
            .height(160.dp)
            .background(Color(0xFF0D0D0D))
            .padding(horizontal = 12.dp, vertical = 8.dp)
    ) {
        Text("Console", color = TextSubtleDark, fontSize = 11.sp, fontWeight = FontWeight.Bold)
        Spacer(modifier = Modifier.height(4.dp))
        LazyColumn(state = listState, modifier = Modifier.fillMaxSize()) {
            items(logs) { log ->
                val logColor = when {
                    log.contains("ERROR") || log.contains("❌") -> StatusDanger
                    log.contains("✅") -> StatusGood
                    log.contains("Writing") -> StatusWarning
                    else -> TextSubtleDark
                }
                Text(
                    log,
                    color = logColor,
                    fontSize = 11.sp,
                    fontFamily = FontFamily.Monospace,
                    lineHeight = 15.sp
                )
            }
        }
    }
}
