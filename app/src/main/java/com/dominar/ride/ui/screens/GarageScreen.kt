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
import com.dominar.ride.garage.SERVICE_TYPES
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
import java.text.SimpleDateFormat
import java.util.Date
import java.util.Locale

// ---------- Shared helpers (also used by HomeScreen) ----------

private val garageDateFormat = SimpleDateFormat("yyyy/MM/dd", Locale.US)

internal fun formatDate(timestamp: Long): String = garageDateFormat.format(Date(timestamp))

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

    var showAddService by remember { mutableStateOf(false) }
    var showAddFuel by remember { mutableStateOf(false) }
    var intervalTarget by remember { mutableStateOf<ServiceStatus?>(null) }
    var documentTarget by remember { mutableStateOf<String?>(null) }

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
            ServiceStatusRow(status = status, onEditInterval = { intervalTarget = status })
            Spacer(Modifier.height(8.dp))
        }
        AddButton("\uFF0B Log a service") { showAddService = true }

        if (serviceLogs.isNotEmpty()) {
            Spacer(Modifier.height(14.dp))
            SectionHeader("RECENT SERVICES")
            serviceLogs.take(5).forEach { log ->
                ServiceLogRow(log = log, onDelete = { vm.deleteServiceLog(log) })
                Spacer(Modifier.height(6.dp))
            }
        }

        Spacer(Modifier.height(20.dp))

        SectionHeader("FUEL")
        FuelStatsCard(stats = fuelStats)
        Spacer(Modifier.height(8.dp))
        AddButton("\uFF0B Add a fill-up") { showAddFuel = true }
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

    if (showAddService) {
        AddServiceDialog(
            onDismiss = { showAddService = false },
            onSave = { type, odo, ts, note ->
                vm.addServiceLog(type, odo, ts, note)
                showAddService = false
            }
        )
    }
    if (showAddFuel) {
        AddFuelDialog(
            onDismiss = { showAddFuel = false },
            onSave = { liters, cost, odo, full ->
                vm.addFuelLog(liters, cost, odo, full)
                showAddFuel = false
            }
        )
    }
    intervalTarget?.let { target ->
        IntervalDialog(
            status = target,
            onSave = { km, months ->
                vm.setServiceInterval(target.type.key, km, months)
                intervalTarget = null
            },
            onDismiss = { intervalTarget = null }
        )
    }
    documentTarget?.let { type ->
        DocumentDateDialog(
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
private fun ServiceStatusRow(status: ServiceStatus, onEditInterval: () -> Unit) {
    Column(
        modifier = Modifier
            .fillMaxWidth()
            .clip(RoundedCornerShape(14.dp))
            .background(MaterialTheme.colorScheme.surface)
            .clickable { onEditInterval() }
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
                text = "Not logged yet \u2014 tap to set interval",
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

// ---------- Dialogs ----------

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

@Composable
private fun AddServiceDialog(
    onDismiss: () -> Unit,
    onSave: (type: String, odometerKm: Int, timestamp: Long, note: String?) -> Unit
) {
    var type by remember { mutableStateOf(SERVICE_TYPES.first()) }
    var odometer by remember { mutableStateOf("") }
    var timestamp by remember { mutableStateOf(System.currentTimeMillis()) }
    var note by remember { mutableStateOf("") }
    var typeMenuOpen by remember { mutableStateOf(false) }

    AlertDialog(
        onDismissRequest = onDismiss,
        title = { Text("Log a service") },
        text = {
            Column(verticalArrangement = Arrangement.spacedBy(10.dp)) {
                Box(Modifier.fillMaxWidth()) {
                    OutlinedTextField(
                        value = "${type.emoji}  ${type.label}",
                        onValueChange = {},
                        readOnly = true,
                        label = { Text("Type") },
                        trailingIcon = { Text("\u25BE") },
                        modifier = Modifier.fillMaxWidth()
                    )
                    Box(
                        Modifier
                            .matchParentSize()
                            .clickable { typeMenuOpen = true }
                    )
                    DropdownMenu(
                        expanded = typeMenuOpen,
                        onDismissRequest = { typeMenuOpen = false }
                    ) {
                        SERVICE_TYPES.forEach { t ->
                            DropdownMenuItem(
                                text = { Text("${t.emoji}  ${t.label}") },
                                onClick = {
                                    type = t
                                    typeMenuOpen = false
                                }
                            )
                        }
                    }
                }
                OutlinedTextField(
                    value = odometer,
                    onValueChange = { odometer = it.filter { c -> c.isDigit() } },
                    label = { Text("Odometer (km)") },
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
        },
        confirmButton = {
            TextButton(
                enabled = odometer.toIntOrNull() != null,
                onClick = { onSave(type.key, odometer.toInt(), timestamp, note) }
            ) { Text("Save") }
        },
        dismissButton = { TextButton(onClick = onDismiss) { Text("Cancel") } }
    )
}

@Composable
private fun AddFuelDialog(
    onDismiss: () -> Unit,
    onSave: (liters: Double, totalCost: Long, odometerKm: Int, fullTank: Boolean) -> Unit
) {
    var liters by remember { mutableStateOf("") }
    var cost by remember { mutableStateOf("") }
    var odometer by remember { mutableStateOf("") }
    var fullTank by remember { mutableStateOf(true) }

    AlertDialog(
        onDismissRequest = onDismiss,
        title = { Text("Add a fill-up") },
        text = {
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
                    keyboardOptions = KeyboardOptions(keyboardType = KeyboardType.Number),
                    singleLine = true,
                    modifier = Modifier.fillMaxWidth()
                )
                Row(
                    modifier = Modifier.fillMaxWidth(),
                    horizontalArrangement = Arrangement.SpaceBetween,
                    verticalAlignment = Alignment.CenterVertically
                ) {
                    Text("Full tank", style = MaterialTheme.typography.bodyMedium)
                    Switch(checked = fullTank, onCheckedChange = { fullTank = it })
                }
            }
        },
        confirmButton = {
            TextButton(
                enabled = liters.toDoubleOrNull() != null && odometer.toIntOrNull() != null,
                onClick = {
                    onSave(liters.toDouble(), cost.toLongOrNull() ?: 0L, odometer.toInt(), fullTank)
                }
            ) { Text("Save") }
        },
        dismissButton = { TextButton(onClick = onDismiss) { Text("Cancel") } }
    )
}

@Composable
private fun IntervalDialog(
    status: ServiceStatus,
    onSave: (Int?, Int?) -> Unit,
    onDismiss: () -> Unit
) {
    var km by remember { mutableStateOf(status.intervalKm?.toString() ?: "") }
    var months by remember { mutableStateOf(status.intervalMonths?.toString() ?: "") }

    AlertDialog(
        onDismissRequest = onDismiss,
        title = { Text("${status.type.label} interval") },
        text = {
            Column(verticalArrangement = Arrangement.spacedBy(10.dp)) {
                Text(
                    text = "Remind me every\u2026 (leave empty to disable)",
                    style = MaterialTheme.typography.bodySmall,
                    color = TextSubtleDark
                )
                OutlinedTextField(
                    value = km,
                    onValueChange = { km = it.filter { c -> c.isDigit() } },
                    label = { Text("Kilometers") },
                    keyboardOptions = KeyboardOptions(keyboardType = KeyboardType.Number),
                    singleLine = true,
                    modifier = Modifier.fillMaxWidth()
                )
                OutlinedTextField(
                    value = months,
                    onValueChange = { months = it.filter { c -> c.isDigit() } },
                    label = { Text("Months") },
                    keyboardOptions = KeyboardOptions(keyboardType = KeyboardType.Number),
                    singleLine = true,
                    modifier = Modifier.fillMaxWidth()
                )
            }
        },
        confirmButton = {
            TextButton(onClick = { onSave(km.toIntOrNull(), months.toIntOrNull()) }) { Text("Save") }
        },
        dismissButton = { TextButton(onClick = onDismiss) { Text("Cancel") } }
    )
}

@OptIn(ExperimentalMaterial3Api::class)
@Composable
private fun DocumentDateDialog(
    initial: Long?,
    onSave: (Long) -> Unit,
    onDismiss: () -> Unit
) {
    val state = rememberDatePickerState(
        initialSelectedDateMillis = initial ?: System.currentTimeMillis()
    )
    DatePickerDialog(
        onDismissRequest = onDismiss,
        confirmButton = {
            TextButton(onClick = { state.selectedDateMillis?.let(onSave) }) { Text("Save") }
        },
        dismissButton = { TextButton(onClick = onDismiss) { Text("Cancel") } }
    ) { DatePicker(state = state) }
}
