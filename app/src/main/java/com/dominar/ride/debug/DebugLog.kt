package com.dominar.ride.debug

import android.content.Context
import android.util.Log
import java.text.SimpleDateFormat
import java.util.Date
import java.util.Locale
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.StateFlow

/**
 * In-app debug logger: an in-memory ring buffer of request/response entries
 * that the Settings > Debug section renders and exports.
 *
 * - The on/off state persists in its own SharedPreferences file, so removing
 *   the feature leaves no trace in the main app prefs.
 * - [log] is a cheap no-op while disabled and safe to call from any thread.
 *
 * To remove the debug feature entirely: delete the `debug` package, the
 * `DebugLog.init(this)` line in MainApplication, the DEBUG section in
 * SettingsScreen, and the one-line `DebugLog.log(...)` call sites in
 * NeshanApi and TrafficOverlay.
 */
data class DebugEntry(
    val timeMs: Long,
    val tag: String,
    val title: String,
    val detail: String,
)

object DebugLog {

    private const val PREFS = "dominar_debug"
    private const val KEY_ENABLED = "enabled"
    private const val MAX_ENTRIES = 300

    @Volatile
    var enabled: Boolean = false
        private set

    private val _entries = MutableStateFlow<List<DebugEntry>>(emptyList())
    val entries: StateFlow<List<DebugEntry>> = _entries

    /** Loads the persisted on/off state. Called once from MainApplication. */
    fun init(context: Context) {
        enabled = context.getSharedPreferences(PREFS, Context.MODE_PRIVATE)
            .getBoolean(KEY_ENABLED, false)
    }

    fun setEnabled(context: Context, value: Boolean) {
        enabled = value
        context.getSharedPreferences(PREFS, Context.MODE_PRIVATE)
            .edit().putBoolean(KEY_ENABLED, value).apply()
    }

    /**
     * Records an entry (no-op while disabled). [title] is the one-line
     * summary; [detail] carries the full payload (URL, raw response body).
     */
    fun log(tag: String, title: String, detail: String = "") {
        if (!enabled) return
        Log.d(tag, if (detail.isBlank()) title else title + "\n" + detail)
        synchronized(this) {
            _entries.value =
                (_entries.value + DebugEntry(System.currentTimeMillis(), tag, title, detail))
                    .takeLast(MAX_ENTRIES)
        }
    }

    fun clear() {
        _entries.value = emptyList()
    }

    /** Full plain-text export of everything currently in the buffer. */
    fun exportText(): String {
        val fmt = SimpleDateFormat("yyyy-MM-dd HH:mm:ss.SSS", Locale.US)
        val all = _entries.value
        return buildString {
            appendLine("Dominar debug log - exported " + fmt.format(Date()))
            appendLine("Entries: " + all.size)
            appendLine()
            all.forEach { e ->
                appendLine("[" + fmt.format(Date(e.timeMs)) + "] [" + e.tag + "] " + e.title)
                if (e.detail.isNotBlank()) appendLine(e.detail)
                appendLine("------------------------------------------------------------")
            }
        }
    }
}
