package com.dominar.ride.debug

import android.content.ClipData
import android.content.ClipboardManager
import android.content.Context
import android.content.Intent
import androidx.compose.foundation.background
import androidx.compose.foundation.clickable
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.Switch
import androidx.compose.material3.Text
import androidx.compose.material3.TextButton
import androidx.compose.runtime.Composable
import androidx.compose.runtime.collectAsState
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.setValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clip
import androidx.compose.ui.platform.LocalContext
import androidx.compose.ui.text.font.FontFamily
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.text.style.TextOverflow
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import java.text.SimpleDateFormat
import java.util.Date
import java.util.Locale

/**
 * Self-contained debug UI shown at the bottom of the Settings screen:
 * an on/off switch plus the logged entries with export/copy/clear actions.
 * Tap an entry to expand its full raw payload.
 */
@Composable
fun DebugSection() {
    val context = LocalContext.current
    var enabled by remember { mutableStateOf(DebugLog.enabled) }
    val entries by DebugLog.entries.collectAsState()
    var expandedKey by remember { mutableStateOf<Long?>(null) }

    Column(
        modifier = Modifier
            .fillMaxWidth()
            .clip(RoundedCornerShape(20.dp))
            .background(MaterialTheme.colorScheme.surface)
            .padding(16.dp),
        verticalArrangement = Arrangement.spacedBy(10.dp)
    ) {
        Row(
            modifier = Modifier.fillMaxWidth(),
            horizontalArrangement = Arrangement.SpaceBetween,
            verticalAlignment = Alignment.CenterVertically
        ) {
            Column(Modifier.weight(1f)) {
                Text(
                    text = "Debug mode",
                    style = MaterialTheme.typography.bodyLarge,
                    fontWeight = FontWeight.SemiBold
                )
                Text(
                    text = "Log every Neshan request with its raw response.",
                    style = MaterialTheme.typography.labelSmall,
                    color = MaterialTheme.colorScheme.onSurface.copy(alpha = 0.6f)
                )
            }
            Switch(
                checked = enabled,
                onCheckedChange = {
                    enabled = it
                    DebugLog.setEnabled(context, it)
                }
            )
        }

        if (!enabled) return@Column

        Row(horizontalArrangement = Arrangement.spacedBy(4.dp)) {
            TextButton(onClick = { shareLog(context) }) { Text("Export") }
            TextButton(onClick = { copyLog(context) }) { Text("Copy") }
            TextButton(onClick = { DebugLog.clear() }) { Text("Clear") }
        }

        if (entries.isEmpty()) {
            Text(
                text = "Nothing logged yet \u2014 open the Ride tab or run a search.",
                style = MaterialTheme.typography.bodySmall,
                color = MaterialTheme.colorScheme.onSurface.copy(alpha = 0.6f)
            )
            return@Column
        }

        val timeFmt = remember { SimpleDateFormat("HH:mm:ss", Locale.US) }
        entries.asReversed().take(50).forEach { entry ->
            val expanded = expandedKey == entry.timeMs
            Column(
                modifier = Modifier
                    .fillMaxWidth()
                    .clip(RoundedCornerShape(10.dp))
                    .background(MaterialTheme.colorScheme.background)
                    .clickable { expandedKey = if (expanded) null else entry.timeMs }
                    .padding(horizontal = 10.dp, vertical = 8.dp)
            ) {
                Text(
                    text = timeFmt.format(Date(entry.timeMs)) + "  [" + entry.tag + "]",
                    fontSize = 10.sp,
                    fontFamily = FontFamily.Monospace,
                    color = MaterialTheme.colorScheme.onSurface.copy(alpha = 0.55f)
                )
                Text(
                    text = entry.title,
                    fontSize = 12.sp,
                    fontFamily = FontFamily.Monospace,
                    color = MaterialTheme.colorScheme.onSurface,
                    maxLines = if (expanded) Int.MAX_VALUE else 2,
                    overflow = TextOverflow.Ellipsis
                )
                if (expanded && entry.detail.isNotBlank()) {
                    Text(
                        text = entry.detail,
                        fontSize = 11.sp,
                        fontFamily = FontFamily.Monospace,
                        color = MaterialTheme.colorScheme.onSurface.copy(alpha = 0.8f),
                        modifier = Modifier.padding(top = 6.dp)
                    )
                }
            }
        }
        if (entries.size > 50) {
            Text(
                text = "Showing the 50 newest of " + entries.size +
                    " entries \u2014 use Export to see everything.",
                style = MaterialTheme.typography.labelSmall,
                color = MaterialTheme.colorScheme.onSurface.copy(alpha = 0.6f)
            )
        }
    }
}

private fun shareLog(context: Context) {
    val intent = Intent(Intent.ACTION_SEND).apply {
        type = "text/plain"
        putExtra(Intent.EXTRA_SUBJECT, "Dominar debug log")
        putExtra(Intent.EXTRA_TEXT, DebugLog.exportText())
    }
    context.startActivity(Intent.createChooser(intent, "Export debug log"))
}

private fun copyLog(context: Context) {
    val clipboard = context.getSystemService(Context.CLIPBOARD_SERVICE) as ClipboardManager
    clipboard.setPrimaryClip(ClipData.newPlainText("Dominar debug log", DebugLog.exportText()))
}
