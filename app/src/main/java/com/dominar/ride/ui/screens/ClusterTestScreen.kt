package com.dominar.ride.ui.screens

import androidx.compose.foundation.layout.*
import androidx.compose.foundation.rememberScrollState
import androidx.compose.foundation.verticalScroll
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.material3.*
import androidx.compose.runtime.*
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.platform.LocalContext
import androidx.compose.ui.text.font.FontFamily
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import com.dominar.ride.ble.BleConnectionManager.ConnectionState
import com.dominar.ride.ble.BleManagerHolder
import com.dominar.ride.protocol.DominarProtocol
import com.dominar.ride.protocol.DominarProtocol.Maneuver
import kotlinx.coroutines.delay
import kotlinx.coroutines.launch
import java.util.UUID

/**
 * Manual packet test console. Each button sends one crafted packet with a
 * known value so the cluster's rendering can be verified on the real bike.
 */
@Composable
fun ClusterTestScreen(onBack: () -> Unit) {
    val context = LocalContext.current
    val ble = remember { BleManagerHolder.get(context) }
    val state by ble.connectionState.collectAsState()
    val scope = rememberCoroutineScope()
    var lastSent by remember { mutableStateOf("Nothing sent yet") }
    var maneuverIndex by remember { mutableStateOf(0) }
    val maneuvers = Maneuver.values()

    fun send(uuid: String, packet: ByteArray, label: String) {
        ble.send(UUID.fromString(uuid), packet)
        lastSent = label + "\n" + packet.joinToString(" ") { String.format("%02X", it) }
    }

    fun nav(
        maneuver: Maneuver = Maneuver.STRAIGHT,
        dist: Double = 250.0,
        meters: Boolean = true,
        isPm: Boolean = false,
        etaH: Int = 10,
        etaM: Int = 30,
        left: Double = 5.0,
        exit: Int = 0,
        text: String = ""
    ) = DominarProtocol.buildNavigationPacket(
        isPm = isPm, distanceUnitMeters = meters, maneuver = maneuver,
        distanceToTurn = dist, etaHour = etaH, etaMinute = etaM,
        distanceLeft = left, roundaboutExit = exit, instructionText = text
    )

    Column(
        modifier = Modifier
            .fillMaxSize()
            .statusBarsPadding()
            .verticalScroll(rememberScrollState())
            .padding(16.dp),
        verticalArrangement = Arrangement.spacedBy(6.dp)
    ) {
        Row(
            modifier = Modifier.fillMaxWidth(),
            verticalAlignment = Alignment.CenterVertically
        ) {
            TextButton(onClick = onBack) { Text("← Back") }
            Spacer(Modifier.weight(1f))
            Text(
                text = if (state is ConnectionState.Connected) "Connected ✓" else "NOT CONNECTED",
                color = if (state is ConnectionState.Connected)
                    MaterialTheme.colorScheme.primary
                else MaterialTheme.colorScheme.error,
                fontWeight = FontWeight.Bold,
                fontSize = 13.sp
            )
        }

        // Last sent packet (hex) — for reporting back
        Text(
            text = lastSent,
            fontSize = 10.sp,
            fontFamily = FontFamily.Monospace,
            color = MaterialTheme.colorScheme.onSurface.copy(alpha = 0.7f)
        )

        SectionHeader("NAVIGATION — lifecycle")
        TestButton("▶ Nav Start") {
            send(DominarProtocol.UUID_NAV, DominarProtocol.buildNavStartPacket(), "NavStart")
        }
        TestButton("Maneuver: ${maneuvers[maneuverIndex].name} — send & next") {
            send(
                DominarProtocol.UUID_NAV,
                nav(maneuver = maneuvers[maneuverIndex]),
                "Maneuver ${maneuvers[maneuverIndex].name}"
            )
            maneuverIndex = (maneuverIndex + 1) % maneuvers.size
        }
        TestButton("🏁 Destination reached") {
            send(
                DominarProtocol.UUID_NAV,
                DominarProtocol.buildDestinationReachedPacket(),
                "DestinationReached"
            )
        }
        TestButton("⏹ Nav Stop") {
            send(DominarProtocol.UUID_NAV, DominarProtocol.buildNavStopPacket(), "NavStop")
        }

        SectionHeader("NAVIGATION — distance encoding")
        TestButton("Distance 250, unit = meters") {
            send(DominarProtocol.UUID_NAV, nav(dist = 250.0, meters = true), "Dist 250 m")
        }
        TestButton("Distance 0.25, unit = km") {
            send(DominarProtocol.UUID_NAV, nav(dist = 0.25, meters = false), "Dist 0.25 km")
        }
        TestButton("Distance 1.2, unit = km") {
            send(DominarProtocol.UUID_NAV, nav(dist = 1.2, meters = false), "Dist 1.2 km")
        }
        TestButton("Distance 12.5, unit = km, left 99.9") {
            send(
                DominarProtocol.UUID_NAV,
                nav(dist = 12.5, meters = false, left = 99.9),
                "Dist 12.5 km / left 99.9"
            )
        }

        SectionHeader("NAVIGATION — ETA / roundabout / text")
        TestButton("ETA 1:45 PM") {
            send(
                DominarProtocol.UUID_NAV,
                nav(isPm = true, etaH = 1, etaM = 45),
                "ETA 1:45 PM"
            )
        }
        TestButton("ETA 10:05 AM") {
            send(
                DominarProtocol.UUID_NAV,
                nav(isPm = false, etaH = 10, etaM = 5),
                "ETA 10:05 AM"
            )
        }
        TestButton("Roundabout RIGHT, exit 3") {
            send(
                DominarProtocol.UUID_NAV,
                nav(maneuver = Maneuver.ROUNDABOUT_RIGHT, exit = 3),
                "Roundabout exit 3"
            )
        }
        TestButton("Street name: AZADI ST") {
            send(
                DominarProtocol.UUID_NAV,
                nav(text = "AZADI ST"),
                "Text AZADI ST"
            )
        }

        SectionHeader("MISSED CALL")
        TestButton("Missed call: Ali / 09123456789") {
            send(
                DominarProtocol.UUID_MISSED,
                DominarProtocol.buildMissedCallPacket("Ali", "09123456789"),
                "MissedCall Ali"
            )
        }

        SectionHeader("ALERT")
        TestButton("Alert SMS") {
            send(
                DominarProtocol.UUID_ALERT,
                DominarProtocol.buildAlertPacket(DominarProtocol.AlertType.SMS, "TEST SMS"),
                "Alert SMS"
            )
        }
        TestButton("Alert WhatsApp") {
            send(
                DominarProtocol.UUID_ALERT,
                DominarProtocol.buildAlertPacket(DominarProtocol.AlertType.WHATSAPP, "TEST WA"),
                "Alert WhatsApp"
            )
        }

        SectionHeader("PHONE (10s burst — service heartbeat may interleave)")
        TestButton("Incoming call: TESTCALLER (repeats 10×1s)") {
            scope.launch {
                repeat(10) { i ->
                    send(
                        DominarProtocol.UUID_PHONE,
                        DominarProtocol.buildPhoneStatusPacket(
                            volume = 8,
                            headsetConnected = false,
                            callState = DominarProtocol.CallState.INCOMING,
                            batteryLevel = 4,
                            signalStrength = 4,
                            isActiveCall = false,
                            callerName = "TESTCALLER",
                            heartbeat = 200 + i
                        ),
                        "PhoneStatus INCOMING #$i"
                    )
                    delay(1000)
                }
            }
        }
    }
}

@Composable
private fun SectionHeader(title: String) {
    Text(
        text = title,
        style = MaterialTheme.typography.labelSmall,
        color = MaterialTheme.colorScheme.primary,
        letterSpacing = 1.2.sp,
        modifier = Modifier.padding(top = 10.dp)
    )
}

@Composable
private fun TestButton(label: String, onClick: () -> Unit) {
    OutlinedButton(
        onClick = onClick,
        modifier = Modifier.fillMaxWidth(),
        shape = RoundedCornerShape(10.dp),
        contentPadding = PaddingValues(horizontal = 14.dp, vertical = 10.dp)
    ) {
        Text(label, fontSize = 13.sp)
    }
}
