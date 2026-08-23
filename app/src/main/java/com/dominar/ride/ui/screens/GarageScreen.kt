package com.dominar.ride.ui.screens

import android.content.Intent
import android.net.Uri
import android.widget.Toast
import androidx.compose.foundation.background
import androidx.compose.foundation.clickable
import androidx.compose.foundation.layout.*
import androidx.compose.foundation.rememberScrollState
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.foundation.text.KeyboardOptions
import androidx.compose.foundation.verticalScroll
import androidx.compose.material3.*
import androidx.compose.runtime.*
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clip
import androidx.compose.ui.platform.LocalContext
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.text.input.KeyboardType
import androidx.compose.ui.text.style.TextOverflow
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import androidx.hilt.navigation.compose.hiltViewModel
import com.dominar.ride.data.db.DocumentEntity
import com.dominar.ride.data.db.FuelLogEntity
import com.dominar.ride.data.db.ParkingEntity
import com.dominar.ride.data.db.ServiceLogEntity
import com.dominar.ride.garage.ServiceStatus
import com.dominar.ride.garage.serviceTypeOf
import com.dominar.ride.ui.FuelStats
import com.dominar.ride.ui.GarageViewModel
import com.dominar.ride.ui.theme.BorderDark
import com.dominar.ride.ui.theme.PrimaryBlue
import com.dominar.ride.ui.theme.StatusDanger
import com.dominar.ride.ui.theme.StatusGood
import com.dominar.ride.ui.theme.StatusWarning
import com.dominar.ride.ui.theme.TextSubtleDark
import java.util.Locale

// ---------- Shared helpers (also used by HomeScreen) ----------

private val persianLocale = android.icu.util.ULocale("fa_IR@calendar=persian")

/** Formats a timestamp as a Shamsi (Jalali) date, e.g. 1405/06/01. */
internal fun formatDate(timestamp: Long): String {
    val cal = android.icu.util.Calendar.getInstance(persianLocale)
    cal.timeInMillis = timestamp
    return String.format(
        Locale.US,
        "%04d/%02d/%02d",
        cal.get(android.icu.util.Calendar.YEAR),
        cal.get(android.icu.util.Calendar.MONTH) + 1,
        cal.get(android.icu.util.Calendar.DAY_OF_MONTH)
    )
}

internal fun formatKm(km: Int): String = String.format(Locale.US, "%,d km", km)

internal fun timeAgo(timestamp: Long): String {
    val mins = ((System.currentTimeMillis() - timestamp) / 60_000L).coerceAtLeast(0)
    return when {
        mins < 1 -> "just now"
        mins < 60 -> "$mins min ago"
        mins < 48 * 60 -> "${mins / 60} h ago"
        else -> "${mins / (60 * 24)} days ago"
    }
}

internal fun dueLabelOf(status: ServiceStatus): String? {
    status.lastLog ?: return null
    if ((status.progress ?: 0f) >= 1f) return "Overdue"
    val kmLeft = status.kmLeft
    val daysLeft = status.daysLeft
    val kmFraction =
        if (kmLeft != null && status.intervalKm != null && status.intervalKm > 0)
            kmLeft.toDouble() / status.intervalKm
        else null
    val dayFraction =
        if (daysLeft != null && status.intervalMonths != null && status.intervalMonths > 0)
            daysLeft.toDouble() / (status.intervalMonths * 30.0)
        else null
    return when {
        kmLeft != null && kmFraction != null && (dayFraction == null || kmFraction <= dayFraction) ->
            "in ${formatKm(kmLeft)}"
        daysLeft != null -> "in $daysLeft days"
        else -> null
    }
}

internal fun dueColorOf(progress: Float?) = when {
    progress == null -> TextSubtleDark
    progress >= 1f -> StatusDanger
    progress >= 0.8f -> StatusWarning
    else -> StatusGood
}

// ---------- Screen ----------

@Composable
fun GarageScreen(vm: GarageViewModel = hiltViewModel()) {
    val context = LocalContext.current
    val statuses by vm.serviceStatuses.collectAsState()
    val serviceLogs by vm.serviceLogs.collectAsState()
    val fuelLogs by vm.fuelLogs.collectAsState()
    val fuelStats by vm.fuelStats.collectAsState()
    val documents by vm.documents.collectAsState()
    val parking by vm.parking.collectAsState()

    var serviceTargetKey by remember { mutableStateOf<String?>(null) }
    var showFuelSheet by remember { mutableStateOf(false) }
    var documentTarget by remember { mutableStateOf<String?>(null) }

    val bestOdometer = remember(serviceLogs, fuelLogs) {
        maxOf(
            serviceLogs.maxOfOrNull { it.odometerKm } ?: 0,
            fuelLogs.maxOfOrNull { it.odometerKm } ?: 0
        ).takeIf { it > 0 }
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
        Text(
            text = "Garage",
            style = MaterialTheme.typography.headlineSmall,
            fontWeight = FontWeight.Bold,
            modifier = Modifier.padding(vertical = 16.dp)
        )

        SectionHeader("PARKING")
        ParkingCard(
            parking = parking,
            onSaveHere = {
                vm.saveParkingHere { ok ->
                    if (!ok) Toast.makeText(context, "Couldn't get location", Toast.LENGTH_SHORT).show()
                }
            },
            onNavigate = { p ->
                runCatching {
                    context.startActivity(
                        Intent(
                            Intent.ACTION_VIEW,
                            Uri.parse("geo:${p.lat},${p.lng}?q=${p.lat},${p.lng}(Parked bike)")
                        )
                    )
                }
            },
            onClear = { vm.clearParking() }
        )

        Spacer(Modifier.height(20.dp))

        SectionHeader("SERVICES")
        statuses.forEach { status ->
            ServiceStatusRow(status = status, onClick = { serviceTargetKey = status.type.key })
            Spacer(Modifier.height(8.dp))
        }
        Text(
            text = "Tap a service to log it, set its reminder or see its history.",
            style = MaterialTheme.typography.labelSmall,
            color = TextSubtleDark
        )

        Spacer(Modifier.height(20.dp))

        SectionHeader("FUEL")
        FuelStatsCard(stats = fuelStats)
        Spacer(Modifier.height(8.dp))
        AddButton("\uFF0B Add a fill-up") { showFuelSheet = true }
        if (fuelLogs.isNotEmpty()) {
            Spacer(Modifier.height(14.dp))
            fuelLogs.take(5).forEach { log ->
                FuelLogRow(log = log, onDelete = { vm.deleteFuelLog(log) })
                Spacer(Modifier.height(6.dp))
            }
        }

        Spacer(Modifier.height(20.dp))

        SectionHeader("DOCUMENTS")
        DocumentRow(
            emoji = "\uD83D\uDEE1\uFE0F",
            label = "Insurance (3rd party)",
            document = documents["INSURANCE"],
            onClick = { documentTarget = "INSURANCE" }
        )
        Spacer(Modifier.height(8.dp))
        DocumentRow(
            emoji = "\uD83D\uDCCB",
            label = "Technical inspection",
            document = documents["INSPECTION"],
            onClick = { documentTarget = "INSPECTION" }
        )
    }

    // ---------- Sheets ----------

    serviceTargetKey?.let { key ->
        val status = statuses.firstOrNull { it.type.key == key }
        if (status != null) {
            ServiceSheet(
                status = status,
                history = serviceLogs.filter { it.type == key },
                defaultOdometer = bestOdometer,
                onLog = { odo, ts, note -> vm.addServiceLog(key, odo, ts, note) },
                onSaveInterval = { km, months -> vm.setServiceInterval(key, km, months) },
                onDeleteLog = { vm.deleteServiceLog(it) },
                onDismiss = { serviceTargetKey = null }
            )
        }
    }
    if (showFuelSheet) {
        FuelSheet(
            defaultOdometer = bestOdometer,
            onSave = { liters, cost, odo, full ->
                vm.addFuelLog(liters, cost, odo, full)
                showFuelSheet = false
            },
            onDismiss = { showFuelSheet = false }
        )
    }
    documentTarget?.let { type ->
        DocumentSheet(
            label = if (type == "INSURANCE") "Insurance (3rd party)" else "Technical inspection",
            initial = documents[type]?.expiryTimestamp,
            onSave = { ts ->
                vm.setDocumentExpiry(type, ts)
                documentTarget = null
            },
            onDismiss = { documentTarget = null }
        )
    }
}

// ---------- Components ----------

@Composable
private fun SectionHeader(text: String) {
    Text(
        text = text,
        style = MaterialTheme.typography.labelSmall,
        color = TextSubtleDark,
        letterSpacing = 1.2.sp,
        modifier = Modifier.padding(bottom = 8.dp)
    )
}

@Composable
private fun AddButton(text: String, onClick: () -> Unit) {
    Row(
        modifier = Modifier
            .fillMaxWidth()
            .clip(RoundedCornerShape(12.dp))
            .background(PrimaryBlue.copy(alpha = 0.10f))
            .clickable { onClick() }
            .padding(vertical = 12.dp),
        horizontalArrangement = Arrangement.Center
    ) {
        Text(
            text = text,
            color = PrimaryBlue,
            fontWeight = FontWeight.SemiBold,
            style = MaterialTheme.typography.bodyMedium
        )
    }
}

@Composable
private fun ParkingCard(
    parking: ParkingEntity?,
    onSaveHere: () -> Unit,
    onNavigate: (ParkingEntity) -> Unit,
    onClear: () -> Unit
) {
    Column(
        modifier = Modifier
            .fillMaxWidth()
            .clip(RoundedCornerShape(16.dp))
            .background(MaterialTheme.colorScheme.surface)
            .padding(16.dp)
    ) {
        if (parking == null) {
            Text(
                text = "\uD83C\uDD7F\uFE0F  No parking location saved",
                style = MaterialTheme.typography.bodyLarge,
                fontWeight = FontWeight.SemiBold
            )
            Text(
                text = "Saved automatically when the bike disconnects, or save it manually.",
                style = MaterialTheme.typography.bodySmall,
                color = TextSubtleDark
            )
            Spacer(Modifier.height(10.dp))
            Button(
                onClick = onSaveHere,
                shape = RoundedCornerShape(10.dp),
                colors = ButtonDefaults.buttonColors(containerColor = PrimaryBlue)
            ) { Text("Save current location") }
        } else {
            Text(
                text = "\uD83C\uDD7F\uFE0F  Parked ${timeAgo(parking.timestamp)}",
                style = MaterialTheme.typography.bodyLarge,
                fontWeight = FontWeight.SemiBold
            )
            Text(
                text = String.format(
                    Locale.US, "%.5f, %.5f \u00B7 %s",
                    parking.lat, parking.lng,
                    if (parking.auto) "auto-saved" else "saved manually"
                ),
                style = MaterialTheme.typography.bodySmall,
                color = TextSubtleDark
            )
            Spacer(Modifier.height(10.dp))
            Row(
                horizontalArrangement = Arrangement.spacedBy(8.dp),
                verticalAlignment = Alignment.CenterVertically
            ) {
                Button(
                    onClick = { onNavigate(parking) },
                    shape = RoundedCornerShape(10.dp),
                    colors = ButtonDefaults.buttonColors(containerColor = PrimaryBlue)
                ) { Text("Navigate") }
                TextButton(onClick = onSaveHere) { Text("Update") }
                TextButton(onClick = onClear) { Text("Clear", color = StatusDanger) }
            }
        }
    }
}

@Composable
private fun ServiceStatusRow(status: ServiceStatus, onClick: () -> Unit) {
    Column(
        modifier = Modifier
            .fillMaxWidth()
            .clip(RoundedCornerShape(14.dp))
            .background(MaterialTheme.colorScheme.surface)
            .clickable { onClick() }
            .padding(14.dp)
    ) {
        Row(
            modifier = Modifier.fillMaxWidth(),
            horizontalArrangement = Arrangement.SpaceBetween,
            verticalAlignment = Alignment.CenterVertically
        ) {
            Text(
                text = "${status.type.emoji}  ${status.type.label}",
                style = MaterialTheme.typography.bodyLarge,
                fontWeight = FontWeight.SemiBold
            )
            Text(
                text = dueLabelOf(status) ?: "\u2014",
                style = MaterialTheme.typography.labelMedium,
                color = dueColorOf(status.progress),
                fontWeight = FontWeight.SemiBold
            )
        }
        Spacer(Modifier.height(4.dp))
        if (status.lastLog != null) {
            Text(
                text = "Last: ${formatKm(status.lastLog.odometerKm)} \u00B7 ${formatDate(status.lastLog.timestamp)}",
                style = MaterialTheme.typography.labelSmall,
                color = TextSubtleDark
            )
            status.progress?.let { p ->
                Spacer(Modifier.height(8.dp))
                LinearProgressIndicator(
                    progress = { p },
                    modifier = Modifier
                        .fillMaxWidth()
                        .height(6.dp)
                        .clip(RoundedCornerShape(3.dp)),
                    color = dueColorOf(p),
                    trackColor = BorderDark
                )
            }
        } else {
            Text(
                text = "Not logged yet \u2014 tap to log or set a reminder",
                style = MaterialTheme.typography.labelSmall,
                color = TextSubtleDark
            )
        }
    }
}

@Composable
private fun ServiceLogRow(log: ServiceLogEntity, onDelete: () -> Unit) {
    val type = serviceTypeOf(log.type)
    Row(
        modifier = Modifier
            .fillMaxWidth()
            .clip(RoundedCornerShape(12.dp))
            .background(MaterialTheme.colorScheme.surface)
            .padding(horizontal = 12.dp, vertical = 6.dp),
        verticalAlignment = Alignment.CenterVertically
    ) {
        Text(type.emoji, fontSize = 18.sp)
        Spacer(Modifier.width(10.dp))
        Column(Modifier.weight(1f)) {
            Text(
                text = type.label + (log.note?.takeIf { it.isNotBlank() }?.let { " \u2014 $it" } ?: ""),
                style = MaterialTheme.typography.bodyMedium,
                maxLines = 1,
                overflow = TextOverflow.Ellipsis
            )
            Text(
                text = "${formatKm(log.odometerKm)} \u00B7 ${formatDate(log.timestamp)}",
                style = MaterialTheme.typography.labelSmall,
                color = TextSubtleDark
            )
        }
        IconButton(onClick = onDelete) { Text("\uD83D\uDDD1", fontSize = 14.sp) }
    }
}

@Composable
private fun FuelStatsCard(stats: FuelStats) {
    Row(
        modifier = Modifier
            .fillMaxWidth()
            .clip(RoundedCornerShape(14.dp))
            .background(MaterialTheme.colorScheme.surface)
            .padding(14.dp),
        horizontalArrangement = Arrangement.SpaceBetween
    ) {
        StatItem(
            label = "Consumption",
            value = stats.avgLitersPer100Km?.let {
                String.format(Locale.US, "%.1f L/100km", it)
            } ?: "\u2014"
        )
        StatItem(
            label = "Last 30 days",
            value = if (stats.costLast30Days > 0)
                String.format(Locale.US, "%,d T", stats.costLast30Days)
            else "\u2014"
        )
        StatItem(
            label = "Odometer",
            value = stats.lastOdometerKm?.let { formatKm(it) } ?: "\u2014"
        )
    }
}

@Composable
private fun StatItem(label: String, value: String) {
    Column(horizontalAlignment = Alignment.CenterHorizontally) {
        Text(value, style = MaterialTheme.typography.bodyMedium, fontWeight = FontWeight.Bold)
        Text(label, style = MaterialTheme.typography.labelSmall, color = TextSubtleDark)
    }
}

@Composable
private fun FuelLogRow(log: FuelLogEntity, onDelete: () -> Unit) {
    Row(
        modifier = Modifier
            .fillMaxWidth()
            .clip(RoundedCornerShape(12.dp))
            .background(MaterialTheme.colorScheme.surface)
            .padding(horizontal = 12.dp, vertical = 6.dp),
        verticalAlignment = Alignment.CenterVertically
    ) {
        Text("\u26FD", fontSize = 18.sp)
        Spacer(Modifier.width(10.dp))
        Column(Modifier.weight(1f)) {
            Text(
                text = String.format(Locale.US, "%.1f L \u00B7 %,d T", log.liters, log.totalCost) +
                    if (log.fullTank) " \u00B7 full" else "",
                style = MaterialTheme.typography.bodyMedium
            )
            Text(
                text = "${formatKm(log.odometerKm)} \u00B7 ${formatDate(log.timestamp)}",
                style = MaterialTheme.typography.labelSmall,
                color = TextSubtleDark
            )
        }
        IconButton(onClick = onDelete) { Text("\uD83D\uDDD1", fontSize = 14.sp) }
    }
}

@Composable
private fun DocumentRow(
    emoji: String,
    label: String,
    document: DocumentEntity?,
    onClick: () -> Unit
) {
    val daysLeft = document?.let {
        ((it.expiryTimestamp - System.currentTimeMillis()) / (24L * 60 * 60 * 1000)).toInt()
    }
    Row(
        modifier = Modifier
            .fillMaxWidth()
            .clip(RoundedCornerShape(14.dp))
            .background(MaterialTheme.colorScheme.surface)
            .clickable { onClick() }
            .padding(14.dp),
        verticalAlignment = Alignment.CenterVertically,
        horizontalArrangement = Arrangement.spacedBy(12.dp)
    ) {
        Text(emoji, fontSize = 20.sp)
        Column(Modifier.weight(1f)) {
            Text(label, style = MaterialTheme.typography.bodyMedium, fontWeight = FontWeight.SemiBold)
            Text(
                text = document?.let { "Expires ${formatDate(it.expiryTimestamp)}" }
                    ?: "Tap to set expiry date",
                style = MaterialTheme.typography.labelSmall,
                color = TextSubtleDark
            )
        }
        if (daysLeft != null) {
            val (txt, color) = when {
                daysLeft < 0 -> "Expired" to StatusDanger
                daysLeft <= 7 -> "$daysLeft days" to StatusDanger
                daysLeft <= 30 -> "$daysLeft days" to StatusWarning
                else -> "$daysLeft days" to StatusGood
            }
            Text(
                text = txt,
                style = MaterialTheme.typography.labelMedium,
                color = color,
                fontWeight = FontWeight.SemiBold
            )
        }
    }
}

// ---------- Apple-style bottom sheets ----------

/** Apple-style sheet top bar: Cancel on the left, bold centered title. */
@Composable
private fun SheetHeader(title: String, onCancel: () -> Unit) {
    Box(Modifier.fillMaxWidth()) {
        TextButton(
            onClick = onCancel,
            modifier = Modifier.align(Alignment.CenterStart)
        ) { Text("Cancel", color = PrimaryBlue) }
        Text(
            text = title,
            modifier = Modifier.align(Alignment.Center),
            style = MaterialTheme.typography.titleMedium,
            fontWeight = FontWeight.Bold
        )
    }
}

@Composable
private fun SheetSectionLabel(text: String) {
    Text(
        text = text,
        style = MaterialTheme.typography.labelSmall,
        color = TextSubtleDark,
        letterSpacing = 1.2.sp,
        modifier = Modifier.padding(top = 18.dp, bottom = 8.dp)
    )
}

@Composable
private fun SheetPrimaryButton(text: String, enabled: Boolean = true, onClick: () -> Unit) {
    Button(
        onClick = onClick,
        enabled = enabled,
        modifier = Modifier
            .fillMaxWidth()
            .height(52.dp),
        shape = RoundedCornerShape(14.dp),
        colors = ButtonDefaults.buttonColors(containerColor = PrimaryBlue)
    ) { Text(text, fontWeight = FontWeight.Bold) }
}

@OptIn(ExperimentalMaterial3Api::class)
@Composable
private fun ServiceSheet(
    status: ServiceStatus,
    history: List<ServiceLogEntity>,
    defaultOdometer: Int?,
    onLog: (odometerKm: Int, timestamp: Long, note: String?) -> Unit,
    onSaveInterval: (Int?, Int?) -> Unit,
    onDeleteLog: (ServiceLogEntity) -> Unit,
    onDismiss: () -> Unit
) {
    val sheetState = rememberModalBottomSheetState(skipPartiallyExpanded = true)
    var odometer by remember { mutableStateOf(defaultOdometer?.toString() ?: "") }
    var timestamp by remember { mutableStateOf(System.currentTimeMillis()) }
    var note by remember { mutableStateOf("") }
    var km by remember { mutableStateOf(status.intervalKm?.toString() ?: "") }
    var months by remember { mutableStateOf(status.intervalMonths?.toString() ?: "") }

    ModalBottomSheet(
        onDismissRequest = onDismiss,
        sheetState = sheetState,
        shape = RoundedCornerShape(topStart = 28.dp, topEnd = 28.dp),
        containerColor = MaterialTheme.colorScheme.background
    ) {
        Column(
            modifier = Modifier
                .fillMaxWidth()
                .verticalScroll(rememberScrollState())
                .padding(horizontal = 20.dp)
                .padding(bottom = 28.dp)
                .navigationBarsPadding()
                .imePadding()
        ) {
            SheetHeader("${status.type.emoji}  ${status.type.label}", onCancel = onDismiss)

            // Current status
            if (status.lastLog != null) {
                Column(
                    modifier = Modifier
                        .fillMaxWidth()
                        .clip(RoundedCornerShape(14.dp))
                        .background(MaterialTheme.colorScheme.surface)
                        .padding(14.dp)
                ) {
                    Row(
                        modifier = Modifier.fillMaxWidth(),
                        horizontalArrangement = Arrangement.SpaceBetween
                    ) {
                        Text(
                            text = "Last: ${formatKm(status.lastLog.odometerKm)} \u00B7 ${formatDate(status.lastLog.timestamp)}",
                            style = MaterialTheme.typography.labelSmall,
                            color = TextSubtleDark
                        )
                        Text(
                            text = dueLabelOf(status) ?: "\u2014",
                            style = MaterialTheme.typography.labelMedium,
                            color = dueColorOf(status.progress),
                            fontWeight = FontWeight.SemiBold
                        )
                    }
                    status.progress?.let { p ->
                        Spacer(Modifier.height(8.dp))
                        LinearProgressIndicator(
                            progress = { p },
                            modifier = Modifier
                                .fillMaxWidth()
                                .height(6.dp)
                                .clip(RoundedCornerShape(3.dp)),
                            color = dueColorOf(p),
                            trackColor = BorderDark
                        )
                    }
                }
            }

            SheetSectionLabel("LOG IT NOW")
            Column(verticalArrangement = Arrangement.spacedBy(10.dp)) {
                OutlinedTextField(
                    value = odometer,
                    onValueChange = { odometer = it.filter { c -> c.isDigit() } },
                    label = { Text("Odometer (km)") },
                    supportingText = if (defaultOdometer != null) {
                        { Text("Pre-filled from your last entry \u2014 adjust if needed.") }
                    } else null,
                    keyboardOptions = KeyboardOptions(keyboardType = KeyboardType.Number),
                    singleLine = true,
                    modifier = Modifier.fillMaxWidth()
                )
                DateField(label = "Date", timestamp = timestamp, onChange = { timestamp = it })
                OutlinedTextField(
                    value = note,
                    onValueChange = { note = it },
                    label = { Text("Note (optional)") },
                    modifier = Modifier.fillMaxWidth()
                )
            }

            SheetSectionLabel("REMIND ME EVERY\u2026")
            Row(horizontalArrangement = Arrangement.spacedBy(10.dp)) {
                OutlinedTextField(
                    value = km,
                    onValueChange = { km = it.filter { c -> c.isDigit() } },
                    label = { Text("Kilometers") },
                    keyboardOptions = KeyboardOptions(keyboardType = KeyboardType.Number),
                    singleLine = true,
                    modifier = Modifier.weight(1f)
                )
                OutlinedTextField(
                    value = months,
                    onValueChange = { months = it.filter { c -> c.isDigit() } },
                    label = { Text("Months") },
                    keyboardOptions = KeyboardOptions(keyboardType = KeyboardType.Number),
                    singleLine = true,
                    modifier = Modifier.weight(1f)
                )
            }
            Text(
                text = "Leave both empty to turn the reminder off.",
                style = MaterialTheme.typography.labelSmall,
                color = TextSubtleDark,
                modifier = Modifier.padding(top = 4.dp)
            )

            Spacer(Modifier.height(16.dp))
            val odoValue = odometer.toIntOrNull()
            SheetPrimaryButton(
                text = if (odoValue != null) "Log ${status.type.label}" else "Save reminder only"
            ) {
                onSaveInterval(km.toIntOrNull(), months.toIntOrNull())
                if (odoValue != null) onLog(odoValue, timestamp, note.takeIf { it.isNotBlank() })
                onDismiss()
            }

            if (history.isNotEmpty()) {
                SheetSectionLabel("HISTORY")
                history.take(5).forEach { log ->
                    Row(
                        modifier = Modifier
                            .fillMaxWidth()
                            .clip(RoundedCornerShape(12.dp))
                            .background(MaterialTheme.colorScheme.surface)
                            .padding(horizontal = 12.dp, vertical = 4.dp),
                        verticalAlignment = Alignment.CenterVertically
                    ) {
                        Column(Modifier.weight(1f)) {
                            Text(
                                text = "${formatKm(log.odometerKm)} \u00B7 ${formatDate(log.timestamp)}",
                                style = MaterialTheme.typography.bodySmall
                            )
                            log.note?.takeIf { it.isNotBlank() }?.let {
                                Text(
                                    text = it,
                                    style = MaterialTheme.typography.labelSmall,
                                    color = TextSubtleDark,
                                    maxLines = 1,
                                    overflow = TextOverflow.Ellipsis
                                )
                            }
                        }
                        IconButton(onClick = { onDeleteLog(log) }) {
                            Text("\uD83D\uDDD1", fontSize = 13.sp)
                        }
                    }
                    Spacer(Modifier.height(6.dp))
                }
            }
        }
    }
}

@OptIn(ExperimentalMaterial3Api::class)
@Composable
private fun FuelSheet(
    defaultOdometer: Int?,
    onSave: (liters: Double, totalCost: Long, odometerKm: Int, fullTank: Boolean) -> Unit,
    onDismiss: () -> Unit
) {
    val sheetState = rememberModalBottomSheetState(skipPartiallyExpanded = true)
    var liters by remember { mutableStateOf("") }
    var cost by remember { mutableStateOf("") }
    var odometer by remember { mutableStateOf(defaultOdometer?.toString() ?: "") }
    var fullTank by remember { mutableStateOf(true) }

    ModalBottomSheet(
        onDismissRequest = onDismiss,
        sheetState = sheetState,
        shape = RoundedCornerShape(topStart = 28.dp, topEnd = 28.dp),
        containerColor = MaterialTheme.colorScheme.background
    ) {
        Column(
            modifier = Modifier
                .fillMaxWidth()
                .verticalScroll(rememberScrollState())
                .padding(horizontal = 20.dp)
                .padding(bottom = 28.dp)
                .navigationBarsPadding()
                .imePadding()
        ) {
            SheetHeader("\u26FD  Fill-up", onCancel = onDismiss)

            Column(verticalArrangement = Arrangement.spacedBy(10.dp)) {
                OutlinedTextField(
                    value = liters,
                    onValueChange = { liters = it.filter { c -> c.isDigit() || c == '.' } },
                    label = { Text("Liters") },
                    keyboardOptions = KeyboardOptions(keyboardType = KeyboardType.Decimal),
                    singleLine = true,
                    modifier = Modifier.fillMaxWidth()
                )
                OutlinedTextField(
                    value = cost,
                    onValueChange = { cost = it.filter { c -> c.isDigit() } },
                    label = { Text("Total cost (Toman)") },
                    keyboardOptions = KeyboardOptions(keyboardType = KeyboardType.Number),
                    singleLine = true,
                    modifier = Modifier.fillMaxWidth()
                )
                OutlinedTextField(
                    value = odometer,
                    onValueChange = { odometer = it.filter { c -> c.isDigit() } },
                    label = { Text("Odometer (km)") },
                    supportingText = if (defaultOdometer != null) {
                        { Text("Pre-filled from your last entry.") }
                    } else null,
                    keyboardOptions = KeyboardOptions(keyboardType = KeyboardType.Number),
                    singleLine = true,
                    modifier = Modifier.fillMaxWidth()
                )
                val litersD = liters.toDoubleOrNull()
                val costL = cost.toLongOrNull()
                if (litersD != null && litersD > 0 && costL != null && costL > 0) {
                    Text(
                        text = String.format(
                            Locale.US, "\u2248 %,d T per liter", (costL / litersD).toLong()
                        ),
                        style = MaterialTheme.typography.labelSmall,
                        color = TextSubtleDark
                    )
                }
                Row(
                    modifier = Modifier.fillMaxWidth(),
                    horizontalArrangement = Arrangement.SpaceBetween,
                    verticalAlignment = Alignment.CenterVertically
                ) {
                    Column {
                        Text("Full tank", style = MaterialTheme.typography.bodyMedium)
                        Text(
                            text = "Needed for consumption stats.",
                            style = MaterialTheme.typography.labelSmall,
                            color = TextSubtleDark
                        )
                    }
                    Switch(checked = fullTank, onCheckedChange = { fullTank = it })
                }
            }

            Spacer(Modifier.height(16.dp))
            SheetPrimaryButton(
                text = "Save fill-up",
                enabled = liters.toDoubleOrNull() != null && odometer.toIntOrNull() != null
            ) {
                onSave(liters.toDouble(), cost.toLongOrNull() ?: 0L, odometer.toInt(), fullTank)
            }
        }
    }
}

@OptIn(ExperimentalMaterial3Api::class)
@Composable
private fun DocumentSheet(
    label: String,
    initial: Long?,
    onSave: (Long) -> Unit,
    onDismiss: () -> Unit
) {
    val sheetState = rememberModalBottomSheetState(skipPartiallyExpanded = true)
    var timestamp by remember {
        mutableStateOf(initial ?: (System.currentTimeMillis() + 365L * 24 * 60 * 60 * 1000))
    }

    ModalBottomSheet(
        onDismissRequest = onDismiss,
        sheetState = sheetState,
        shape = RoundedCornerShape(topStart = 28.dp, topEnd = 28.dp),
        containerColor = MaterialTheme.colorScheme.background
    ) {
        Column(
            modifier = Modifier
                .fillMaxWidth()
                .padding(horizontal = 20.dp)
                .padding(bottom = 28.dp)
                .navigationBarsPadding()
        ) {
            SheetHeader(label, onCancel = onDismiss)

            DateField(label = "Expiry date", timestamp = timestamp, onChange = { timestamp = it })
            val daysLeft =
                ((timestamp - System.currentTimeMillis()) / (24L * 60 * 60 * 1000)).toInt()
            Text(
                text = when {
                    daysLeft < 0 -> "Already expired."
                    daysLeft == 0 -> "Expires today."
                    else -> "Expires in $daysLeft days."
                },
                style = MaterialTheme.typography.labelSmall,
                color = when {
                    daysLeft <= 7 -> StatusDanger
                    daysLeft <= 30 -> StatusWarning
                    else -> TextSubtleDark
                },
                modifier = Modifier.padding(top = 6.dp)
            )

            Spacer(Modifier.height(16.dp))
            SheetPrimaryButton("Save") { onSave(timestamp) }
        }
    }
}

// ---------- Shared date field ----------

@OptIn(ExperimentalMaterial3Api::class)
@Composable
private fun DateField(label: String, timestamp: Long, onChange: (Long) -> Unit) {
    var open by remember { mutableStateOf(false) }
    Box(Modifier.fillMaxWidth()) {
        OutlinedTextField(
            value = formatDate(timestamp),
            onValueChange = {},
            readOnly = true,
            label = { Text(label) },
            modifier = Modifier.fillMaxWidth()
        )
        Box(
            Modifier
                .matchParentSize()
                .clickable { open = true }
        )
    }
    if (open) {
        val state = rememberDatePickerState(initialSelectedDateMillis = timestamp)
        DatePickerDialog(
            onDismissRequest = { open = false },
            confirmButton = {
                TextButton(onClick = {
                    state.selectedDateMillis?.let(onChange)
                    open = false
                }) { Text("OK") }
            },
            dismissButton = {
                TextButton(onClick = { open = false }) { Text("Cancel") }
            }
        ) { DatePicker(state = state) }
    }
}
