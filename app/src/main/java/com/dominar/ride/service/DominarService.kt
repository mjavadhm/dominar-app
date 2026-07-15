package com.dominar.ride.service

import android.app.NotificationChannel
import android.app.NotificationManager
import android.app.PendingIntent
import android.app.Service
import android.bluetooth.BluetoothAdapter
import android.bluetooth.BluetoothManager
import android.content.Context
import android.content.Intent
import android.content.pm.ServiceInfo
import android.os.Build
import android.os.IBinder
import androidx.core.app.NotificationCompat
import com.dominar.ride.MainActivity
import com.dominar.ride.R
import com.dominar.ride.ble.BleConnectionManager
import com.dominar.ride.ble.BleManagerHolder
import com.dominar.ride.data.DevicePrefs
import kotlinx.coroutines.CoroutineScope
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.Job
import kotlinx.coroutines.SupervisorJob
import kotlinx.coroutines.cancel
import kotlinx.coroutines.launch
import com.dominar.ride.phone.CallMonitor

/**
 * Foreground service that keeps the BLE connection to the cluster alive
 * while the app is in background / closed.
 */
class DominarService : Service() {

    companion object {
        private const val CHANNEL_ID = "dominar_connection"
        private const val NOTIFICATION_ID = 1
        const val ACTION_START = "com.dominar.ride.action.START"
        const val ACTION_STOP = "com.dominar.ride.action.STOP"

        fun start(context: Context) {
            val intent = Intent(context, DominarService::class.java).setAction(ACTION_START)
            if (Build.VERSION.SDK_INT >= 26) context.startForegroundService(intent)
            else context.startService(intent)
        }

        fun stop(context: Context) {
            context.startService(
                Intent(context, DominarService::class.java).setAction(ACTION_STOP)
            )
        }
    }

    private val serviceScope = CoroutineScope(SupervisorJob() + Dispatchers.Default)
    private var stateJob: Job? = null
    private var callMonitor: CallMonitor? = null

    override fun onBind(intent: Intent?): IBinder? = null

    override fun onCreate() {
        super.onCreate()
        createChannel()
    }

    override fun onStartCommand(intent: Intent?, flags: Int, startId: Int): Int {
        if (intent?.action == ACTION_STOP) {
            BleManagerHolder.get(this).disconnect()
            stopForeground(STOP_FOREGROUND_REMOVE)
            stopSelf()
            return START_NOT_STICKY
        }

        startAsForeground("Starting...")
        observeConnectionState()
        
        if (callMonitor == null) {
            val monitor = CallMonitor(this, BleManagerHolder.get(this), serviceScope)
            monitor.start()
            callMonitor = monitor
            serviceScope.launch {
                BleManagerHolder.get(this@DominarService).controlPackets.collect {
                    callMonitor?.handleControl(it)
                }
            }
        }

        autoConnectIfPossible()
        return START_STICKY
    }

    override fun onDestroy() {
        callMonitor?.stop()
        serviceScope.cancel()
        super.onDestroy()
    }

    // ---------- Auto-connect ----------

    private fun autoConnectIfPossible() {
        val prefs = DevicePrefs(this)
        val address = prefs.lastDeviceAddress ?: return
        if (!prefs.autoConnect) return
        if (!BluetoothAdapter.checkBluetoothAddress(address)) return

        val manager = BleManagerHolder.get(this)
        val state = manager.connectionState.value
        if (state is BleConnectionManager.ConnectionState.Connected ||
            state is BleConnectionManager.ConnectionState.Connecting
        ) return

        val adapter =
            (getSystemService(Context.BLUETOOTH_SERVICE) as BluetoothManager).adapter ?: return
        if (!adapter.isEnabled) return

        runCatching { adapter.getRemoteDevice(address) }
            .getOrNull()
            ?.let { manager.connect(it) }
    }

    // ---------- Notification ----------

    private fun observeConnectionState() {
        stateJob?.cancel()
        stateJob = serviceScope.launch {
            BleManagerHolder.get(this@DominarService).connectionState.collect { state ->
                val text = when (state) {
                    is BleConnectionManager.ConnectionState.Connected -> "Connected to cluster ✓"
                    is BleConnectionManager.ConnectionState.Connecting -> "Connecting..."
                    is BleConnectionManager.ConnectionState.Reconnecting ->
                        "Reconnecting (attempt ${state.attempt})..."
                    is BleConnectionManager.ConnectionState.Scanning -> "Scanning..."
                    is BleConnectionManager.ConnectionState.Error -> "Error: ${state.message}"
                    else -> "Disconnected"
                }
                notify(text)
            }
        }
    }

    private fun createChannel() {
        if (Build.VERSION.SDK_INT >= 26) {
            val channel = NotificationChannel(
                CHANNEL_ID, "Cluster connection", NotificationManager.IMPORTANCE_LOW
            ).apply { setShowBadge(false) }
            getSystemService(NotificationManager::class.java).createNotificationChannel(channel)
        }
    }

    private fun buildNotification(text: String) =
        NotificationCompat.Builder(this, CHANNEL_ID)
            .setSmallIcon(R.drawable.ic_launcher_foreground)
            .setContentTitle("Dominar")
            .setContentText(text)
            .setOngoing(true)
            .setOnlyAlertOnce(true)
            .setContentIntent(
                PendingIntent.getActivity(
                    this, 0,
                    Intent(this, MainActivity::class.java),
                    PendingIntent.FLAG_UPDATE_CURRENT or PendingIntent.FLAG_IMMUTABLE
                )
            )
            .build()

    private fun startAsForeground(text: String) {
        val notification = buildNotification(text)
        if (Build.VERSION.SDK_INT >= 29) {
            startForeground(
                NOTIFICATION_ID, notification,
                ServiceInfo.FOREGROUND_SERVICE_TYPE_CONNECTED_DEVICE
            )
        } else {
            startForeground(NOTIFICATION_ID, notification)
        }
    }

    private fun notify(text: String) {
        getSystemService(NotificationManager::class.java)
            .notify(NOTIFICATION_ID, buildNotification(text))
    }
}
