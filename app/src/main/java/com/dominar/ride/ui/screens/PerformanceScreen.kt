package com.dominar.ride.ui.screens

import android.widget.Toast
import androidx.compose.foundation.Canvas
import androidx.compose.foundation.background
import androidx.compose.foundation.clickable
import androidx.compose.foundation.layout.*
import androidx.compose.foundation.rememberScrollState
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.foundation.verticalScroll
import androidx.compose.material3.*
import androidx.compose.runtime.*
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clip
import androidx.compose.ui.geometry.Offset
import androidx.compose.ui.geometry.Size
import androidx.compose.ui.graphics.StrokeCap
import androidx.compose.ui.graphics.drawscope.Stroke
import androidx.compose.ui.platform.LocalContext
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.text.style.TextAlign
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import androidx.hilt.navigation.compose.hiltViewModel
import com.dominar.ride.data.db.PerformanceRunEntity
import com.dominar.ride.ui.PerformanceViewModel
import com.dominar.ride.ui.TimerState
import com.dominar.ride.ui.theme.*
import java.util.Locale
import kotlin.math.abs
import kotlin.math.cos
import kotlin.math.sin

@Composable
fun PerformanceScreen(vm: PerformanceViewModel = hiltViewModel()) {
    val context = LocalContext.current
    val timerState by vm.timerState.collectAsState()
    val speedKmh by vm.speedKmh.collectAsState()
    val elapsedMs by vm.elapsedMs.collectAsState()
    val runs by vm.runs.collectAsState()
    val bestRun by vm.bestRun.collectAsState()
    val leanDeg by vm.leanDeg.collectAsState()
    val maxLeft by vm.maxLeanLeft.collectAsState()
    val maxRight by vm.maxLeanRight.collectAsState()

    // Lean sensor only runs while this tab is visible.
    DisposableEffect(Unit) {
        vm.startLeanSensor()
        onDispose { vm.stopLeanSensor() }
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
            text = "Performance",
            style = MaterialTheme.typography.headlineSmall,
            fontWeight = FontWeight.Bold,
            modifier = Modifier.padding(vertical = 16.dp)
        )

        SectionLabel("0\u2013100 KM/H")
        TimerCard(
            state = timerState,
            speedKmh = speedKmh,
            elapsedMs = elapsedMs,
            onStart = {
                if (!vm.startTimer()) {
                    Toast.makeText(
                        context,
                        "Location permission is required for the timer",
                        Toast.LENGTH_LONG
                    ).show()
                }
            },
            onCancel = { vm.cancelTimer() }
        )

        if (runs.isNotEmpty()) {
            Spacer(Modifier.height(16.dp))
            SectionLabel("BEST RUNS")
            Spacer(Modifier.height(4.dp))
            val sorted = runs.sortedBy { it.timeTo100Ms ?: Long.MAX_VALUE }.take(5)
            sorted.forEachIndexed { index, run ->
                RunRow(
                    run = run,
                    isBest = run.id == bestRun?.id,
                    onDelete = { vm.deleteRun(run) }
                )
                if (index != sorted.lastIndex) Spacer(Modifier.height(6.dp))
            }
        }

        Spacer(Modifier.height(16.dp))
        SectionLabel("LEAN ANGLE")
        LeanCard(
            available = vm.leanAvailable,
            leanDeg = leanDeg,
            maxLeft = maxLeft,
            maxRight = maxRight,
            onCalibrate = { vm.calibrateLean() },
            onResetRecords = { vm.resetLeanRecords() }
        )
    }
}

// ---------- 0-100 ----------

@Composable
private fun TimerCard(
    state: TimerState,
    speedKmh: Float,
    elapsedMs: Long,
    onStart: () -> Unit,
    onCancel: () -> Unit
) {
    Column(
        modifier = Modifier
            .fillMaxWidth()
            .clip(RoundedCornerShape(16.dp))
            .background(MaterialTheme.colorScheme.surface)
            .padding(16.dp),
        horizontalAlignment = Alignment.CenterHorizontally
    ) {
        Text(
            text = formatSeconds(elapsedMs),
            fontSize = 56.sp,
            fontWeight = FontWeight.Black,
            color = when (state) {
                is TimerState.Running -> PrimaryBlue
                is TimerState.Finished ->
                    if (state.timeTo100Ms != null) StatusGood else StatusWarning
                else -> MaterialTheme.colorScheme.onSurface
            }
        )
        Text(
            text = "seconds",
            style = MaterialTheme.typography.labelSmall,
            color = TextSubtleDark
        )
        Spacer(Modifier.height(8.dp))
        val (statusText, statusColor) = when (state) {
            is TimerState.Idle ->
                "Hit Start, come to a full stop, then launch." to TextSubtleDark
            is TimerState.WaitingForStop ->
                "Waiting for GPS \u00B7 come to a full stop\u2026" to StatusWarning
            is TimerState.Ready ->
                "READY \u2014 launch whenever you want!" to StatusGood
            is TimerState.Running -> "GO!" to PrimaryBlue
            is TimerState.Finished ->
                if (state.timeTo100Ms != null) "Run complete \uD83C\uDFC1" to StatusGood
                else "Run stopped early \u2014 partial result saved" to StatusWarning
        }
        Text(
            text = statusText,
            style = MaterialTheme.typography.bodySmall,
            color = statusColor,
            textAlign = TextAlign.Center
        )

        if (state is TimerState.Finished) {
            Spacer(Modifier.height(10.dp))
            Row(horizontalArrangement = Arrangement.spacedBy(18.dp)) {
                ResultStat("0\u201360", state.timeTo60Ms)
                ResultStat("0\u2013100", state.timeTo100Ms)
            }
        }

        if (state is TimerState.WaitingForStop || state is TimerState.Ready ||
            state is TimerState.Running
        ) {
            Spacer(Modifier.height(6.dp))
            Text(
                text = String.format(Locale.US, "%.0f km/h", speedKmh),
                style = MaterialTheme.typography.titleMedium,
                fontWeight = FontWeight.Bold,
                color = MaterialTheme.colorScheme.onSurface
            )
        }

        Spacer(Modifier.height(12.dp))
        when (state) {
            is TimerState.Idle, is TimerState.Finished -> Button(
                onClick = onStart,
                modifier = Modifier.fillMaxWidth().height(48.dp),
                shape = RoundedCornerShape(12.dp),
                colors = ButtonDefaults.buttonColors(containerColor = PrimaryBlue)
            ) {
                Text("\uD83D\uDEA6 Start 0\u2013100", fontWeight = FontWeight.Bold)
            }
            else -> OutlinedButton(
                onClick = onCancel,
                modifier = Modifier.fillMaxWidth().height(48.dp),
                shape = RoundedCornerShape(12.dp)
            ) {
                Text("Cancel")
            }
        }
    }
}

@Composable
private fun ResultStat(label: String, timeMs: Long?) {
    Column(horizontalAlignment = Alignment.CenterHorizontally) {
        Text(
            text = timeMs?.let { formatSeconds(it) + " s" } ?: "\u2014",
            style = MaterialTheme.typography.titleMedium,
            fontWeight = FontWeight.Bold,
            color = MaterialTheme.colorScheme.onSurface
        )
        Text(
            text = label,
            style = MaterialTheme.typography.labelSmall,
            color = TextSubtleDark
        )
    }
}

@Composable
private fun RunRow(run: PerformanceRunEntity, isBest: Boolean, onDelete: () -> Unit) {
    Row(
        modifier = Modifier
            .fillMaxWidth()
            .clip(RoundedCornerShape(12.dp))
            .background(MaterialTheme.colorScheme.surface)
            .padding(horizontal = 12.dp, vertical = 10.dp),
        verticalAlignment = Alignment.CenterVertically,
        horizontalArrangement = Arrangement.spacedBy(10.dp)
    ) {
        Text(if (isBest) "\uD83C\uDFC6" else "\uD83D\uDEA6", fontSize = 18.sp)
        Column(Modifier.weight(1f)) {
            Row(horizontalArrangement = Arrangement.spacedBy(12.dp)) {
                Text(
                    text = "0\u2013100: " + (run.timeTo100Ms?.let { formatSeconds(it) + " s" } ?: "\u2014"),
                    style = MaterialTheme.typography.bodyMedium,
                    fontWeight = FontWeight.SemiBold,
                    color = if (isBest) StatusGood else MaterialTheme.colorScheme.onSurface
                )
                Text(
                    text = "0\u201360: " + (run.timeTo60Ms?.let { formatSeconds(it) + " s" } ?: "\u2014"),
                    style = MaterialTheme.typography.bodyMedium,
                    color = TextSubtleDark
                )
            }
            Text(
                text = formatDate(run.timestamp),
                style = MaterialTheme.typography.labelSmall,
                color = TextSubtleDark
            )
        }
        Text(
            text = "\uD83D\uDDD1",
            fontSize = 16.sp,
            modifier = Modifier
                .clip(RoundedCornerShape(8.dp))
                .clickable { onDelete() }
                .padding(6.dp)
        )
    }
}

// ---------- Lean angle ----------

@Composable
private fun LeanCard(
    available: Boolean,
    leanDeg: Float,
    maxLeft: Float,
    maxRight: Float,
    onCalibrate: () -> Unit,
    onResetRecords: () -> Unit
) {
    Column(
        modifier = Modifier
            .fillMaxWidth()
            .clip(RoundedCornerShape(16.dp))
            .background(MaterialTheme.colorScheme.surface)
            .padding(16.dp),
        horizontalAlignment = Alignment.CenterHorizontally
    ) {
        if (!available) {
            Text(
                text = "This phone has no orientation sensor \u2014 lean angle isn't available.",
                style = MaterialTheme.typography.bodySmall,
                color = TextSubtleDark,
                textAlign = TextAlign.Center
            )
            return@Column
        }

        LeanGauge(leanDeg = leanDeg)
        Spacer(Modifier.height(4.dp))
        Text(
            text = String.format(Locale.US, "%.0f\u00B0 %s", abs(leanDeg),
                when {
                    leanDeg < -1f -> "LEFT"
                    leanDeg > 1f -> "RIGHT"
                    else -> ""
                }
            ).trim(),
            fontSize = 34.sp,
            fontWeight = FontWeight.Black,
            color = MaterialTheme.colorScheme.onSurface
        )
        Spacer(Modifier.height(8.dp))
        Row(horizontalArrangement = Arrangement.spacedBy(18.dp)) {
            Column(horizontalAlignment = Alignment.CenterHorizontally) {
                Text(
                    text = String.format(Locale.US, "%.0f\u00B0", maxLeft),
                    style = MaterialTheme.typography.titleMedium,
                    fontWeight = FontWeight.Bold,
                    color = MaterialTheme.colorScheme.onSurface
                )
                Text("\u2B05\uFE0F max left", style = MaterialTheme.typography.labelSmall, color = TextSubtleDark)
            }
            Column(horizontalAlignment = Alignment.CenterHorizontally) {
                Text(
                    text = String.format(Locale.US, "%.0f\u00B0", maxRight),
                    style = MaterialTheme.typography.titleMedium,
                    fontWeight = FontWeight.Bold,
                    color = MaterialTheme.colorScheme.onSurface
                )
                Text("max right \u27A1\uFE0F", style = MaterialTheme.typography.labelSmall, color = TextSubtleDark)
            }
        }
        Spacer(Modifier.height(12.dp))
        Row(
            modifier = Modifier.fillMaxWidth(),
            horizontalArrangement = Arrangement.spacedBy(10.dp)
        ) {
            Button(
                onClick = onCalibrate,
                modifier = Modifier.weight(1f).height(44.dp),
                shape = RoundedCornerShape(12.dp),
                colors = ButtonDefaults.buttonColors(containerColor = PrimaryBlue)
            ) {
                Text("\uD83C\uDFAF Set zero", fontWeight = FontWeight.SemiBold)
            }
            OutlinedButton(
                onClick = onResetRecords,
                modifier = Modifier.weight(1f).height(44.dp),
                shape = RoundedCornerShape(12.dp)
            ) {
                Text("Reset records")
            }
        }
        Spacer(Modifier.height(6.dp))
        Text(
            text = "Mount the phone on its holder, keep the bike upright and tap Set zero.",
            style = MaterialTheme.typography.labelSmall,
            color = TextSubtleDark,
            textAlign = TextAlign.Center
        )
    }
}

@Composable
private fun LeanGauge(leanDeg: Float) {
    val clamped = leanDeg.coerceIn(-60f, 60f)
    Canvas(
        modifier = Modifier
            .fillMaxWidth()
            .height(110.dp)
    ) {
        val radius = size.height - 14f
        val cx = size.width / 2f
        val cy = size.height
        drawArc(
            color = BorderDark,
            startAngle = 180f,
            sweepAngle = 180f,
            useCenter = false,
            style = Stroke(width = 12f, cap = StrokeCap.Round),
            topLeft = Offset(cx - radius, cy - radius),
            size = Size(radius * 2f, radius * 2f)
        )
        // Needle. 0 deg lean = straight up; positive lean tilts right.
        val theta = Math.toRadians((90f - clamped).toDouble())
        val needleLength = radius - 10f
        drawLine(
            color = PrimaryBlue,
            start = Offset(cx, cy),
            end = Offset(
                cx + needleLength * cos(theta).toFloat(),
                cy - needleLength * sin(theta).toFloat()
            ),
            strokeWidth = 8f,
            cap = StrokeCap.Round
        )
    }
}

// ---------- Shared ----------

@Composable
private fun SectionLabel(text: String) {
    Text(
        text = text,
        style = MaterialTheme.typography.labelSmall,
        color = TextSubtleDark,
        letterSpacing = 1.2.sp,
        modifier = Modifier.padding(vertical = 6.dp)
    )
}

private fun formatSeconds(ms: Long): String =
    String.format(Locale.US, "%.2f", ms / 1000.0)
