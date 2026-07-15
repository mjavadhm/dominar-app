package com.dominar.ride.phone

import android.annotation.SuppressLint
import android.content.BroadcastReceiver
import android.content.Context
import android.content.Intent
import android.content.IntentFilter
import android.media.AudioManager
import android.net.Uri
import android.os.BatteryManager
import android.provider.ContactsContract
import android.telecom.TelecomManager
import android.telephony.TelephonyManager
import android.util.Log
import com.dominar.ride.ble.BleConnectionManager
import com.dominar.ride.protocol.DominarProtocol
import kotlinx.coroutines.CoroutineScope
import kotlinx.coroutines.Job
import kotlinx.coroutines.delay
import kotlinx.coroutines.isActive
import kotlinx.coroutines.launch
import java.util.UUID

/**
 * Watches phone call state, sends PhoneStatus / MissedCall packets to the
 * cluster, and executes Control commands (accept/reject/volume) coming back
 * from the cluster.
 */
@SuppressLint("MissingPermission")
@Suppress("DEPRECATION")
class CallMonitor(
    private val context: Context,
    private val ble: BleConnectionManager,
    private val scope: CoroutineScope
) {
    companion object {
        private const val TAG = "CallMonitor"
        private const val HEARTBEAT_INTERVAL_MS = 3_000L
        private val PHONE_UUID = UUID.fromString(DominarProtocol.UUID_PHONE)
        private val MISSED_UUID = UUID.fromString(DominarProtocol.UUID_MISSED)
    }

    private var heartbeat = 0
    private var callState = DominarProtocol.CallState.NO_CALL
    private var callerName = ""
    private var lastIncomingNumber = ""
    private var wasRinging = false
    private var heartbeatJob: Job? = null
    private var registered = false

    // Control-packet counters from the cluster (increment = new command)
    private var lastAccept = -1
    private var lastReject = -1

    private val receiver = object : BroadcastReceiver() {
        override fun onReceive(ctx: Context, intent: Intent) {
            if (intent.action != TelephonyManager.ACTION_PHONE_STATE_CHANGED) return
            val state = intent.getStringExtra(TelephonyManager.EXTRA_STATE) ?: return
            val number = intent.getStringExtra(TelephonyManager.EXTRA_INCOMING_NUMBER)
            onPhoneState(state, number)
        }
    }

    fun start() {
        if (!registered) {
            context.registerReceiver(
                receiver, IntentFilter(TelephonyManager.ACTION_PHONE_STATE_CHANGED)
            )
            registered = true
        }
        heartbeatJob?.cancel()
        heartbeatJob = scope.launch {
            while (isActive) {
                sendPhoneStatus()
                delay(HEARTBEAT_INTERVAL_MS)
            }
        }
    }

    fun stop() {
        heartbeatJob?.cancel()
        if (registered) {
            runCatching { context.unregisterReceiver(receiver) }
            registered = false
        }
    }

    // ---------- Call state ----------

    private fun onPhoneState(state: String, number: String?) {
        when (state) {
            TelephonyManager.EXTRA_STATE_RINGING -> {
                wasRinging = true
                if (!number.isNullOrBlank()) lastIncomingNumber = number
                callerName = lookupContactName(lastIncomingNumber)
                callState = DominarProtocol.CallState.INCOMING
                sendPhoneStatus()
            }
            TelephonyManager.EXTRA_STATE_OFFHOOK -> {
                wasRinging = false
                callState = DominarProtocol.CallState.ACTIVE
                sendPhoneStatus()
            }
            TelephonyManager.EXTRA_STATE_IDLE -> {
                if (wasRinging) sendMissedCall()
                wasRinging = false
                callState = DominarProtocol.CallState.NO_CALL
                callerName = ""
                lastIncomingNumber = ""
                sendPhoneStatus()
            }
        }
    }

    private fun lookupContactName(number: String): String {
        if (number.isBlank()) return number
        return try {
            val uri = Uri.withAppendedPath(
                ContactsContract.PhoneLookup.CONTENT_FILTER_URI, Uri.encode(number)
            )
            context.contentResolver.query(
                uri, arrayOf(ContactsContract.PhoneLookup.DISPLAY_NAME), null, null, null
            )?.use { c -> if (c.moveToFirst()) c.getString(0) else number } ?: number
        } catch (e: Exception) {
            Log.w(TAG, "Contact lookup failed", e)
            number
        }
    }

    // ---------- Packets out ----------

    fun sendPhoneStatus() {
        heartbeat = (heartbeat + 1) and 0xFF
        val packet = DominarProtocol.buildPhoneStatusPacket(
            batteryLevel = batteryLevel(),
            signalStrength = 4,
            volume = volumeLevel(),
            callState = callState,
            callerName = callerName,
            isActiveCall = callState == DominarProtocol.CallState.ACTIVE,
            headsetConnected = false,
            heartbeat = heartbeat
        )
        ble.send(PHONE_UUID, packet)
    }

    private fun sendMissedCall() {
        val packet = DominarProtocol.buildMissedCallPacket(
            name = callerName.ifBlank { lastIncomingNumber },
            number = lastIncomingNumber
        )
        ble.send(MISSED_UUID, packet)
    }

    private fun batteryLevel(): Int {
        val bm = context.getSystemService(Context.BATTERY_SERVICE) as BatteryManager
        val percent = bm.getIntProperty(BatteryManager.BATTERY_PROPERTY_CAPACITY)
        return (percent / 25).coerceIn(0, 4)
    }

    private fun volumeLevel(): Int {
        val am = context.getSystemService(Context.AUDIO_SERVICE) as AudioManager
        val max = am.getStreamMaxVolume(AudioManager.STREAM_MUSIC).coerceAtLeast(1)
        return (am.getStreamVolume(AudioManager.STREAM_MUSIC) * 15) / max
    }

    // ---------- Control packets in (from cluster) ----------

    fun handleControl(control: DominarProtocol.ControlState) {
        // First packet after connect: sync counters without acting
        if (lastAccept == -1) {
            lastAccept = control.callAcceptCounter
            lastReject = control.callRejectCounter
            return
        }
        if (control.callAcceptCounter != lastAccept) {
            lastAccept = control.callAcceptCounter
            acceptCall()
        }
        if (control.callRejectCounter != lastReject) {
            lastReject = control.callRejectCounter
            rejectCall()
        }
        applyVolume(control.volume)
    }

    private fun acceptCall() {
        try {
            val tm = context.getSystemService(Context.TELECOM_SERVICE) as TelecomManager
            tm.acceptRingingCall()
            Log.d(TAG, "Call accepted from cluster")
        } catch (e: Exception) {
            Log.e(TAG, "acceptRingingCall failed", e)
        }
    }

    private fun rejectCall() {
        try {
            val tm = context.getSystemService(Context.TELECOM_SERVICE) as TelecomManager
            tm.endCall()
            Log.d(TAG, "Call rejected from cluster")
        } catch (e: Exception) {
            Log.e(TAG, "endCall failed", e)
        }
    }

    private fun applyVolume(clusterVolume: Int) {
        val am = context.getSystemService(Context.AUDIO_SERVICE) as AudioManager
        val max = am.getStreamMaxVolume(AudioManager.STREAM_MUSIC)
        val target = (clusterVolume * max) / 15
        if (target != am.getStreamVolume(AudioManager.STREAM_MUSIC)) {
            am.setStreamVolume(AudioManager.STREAM_MUSIC, target, 0)
        }
    }
}
