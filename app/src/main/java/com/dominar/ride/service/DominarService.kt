package com.dominar.ride.service

import android.annotation.SuppressLint
import android.app.Notification
import android.app.NotificationChannel
import android.app.NotificationManager
import android.app.PendingIntent
import android.app.Service
import android.bluetooth.BluetoothAdapter
import android.bluetooth.BluetoothManager
import android.content.Context
import android.content.Intent
import android.content.pm.PackageManager
import android.content.pm.ServiceInfo
import android.os.Build
import android.os.IBinder
import androidx.core.app.NotificationCompat
import com.dominar.ride.MainActivity
import com.dominar.ride.R
import com.dominar.ride.ble.BleConnectionManager
import com.dominar.ride.ble.BleManagerHolder
import com.dominar.ride.data.DevicePrefs
import com.dominar.ride.data.db.ParkingDao
import com.dominar.ride.data.db.ParkingEntity
import com.google.android.gms.location.FusedLocationProviderClient
import com.google.android.gms.location.LocationServices
import com.google.android.gms.location.Priority
import com.google.android.gms.tasks.CancellationTokenSource
import dagger.hilt.android.AndroidEntryPoint
import kotlinx.coroutines.CoroutineScope
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.Job
import kotlinx.coroutines.SupervisorJob
import kotlinx.coroutines.cancel
import kotlinx.coroutines.launch
import com.dominar.ride.phone.CallMonitor
import javax.inject.Inject

/**
 * Foreground service that keeps the BLE connection to the cluster alive
 * while the app is in background / closed.
 *
 * The persistent notification has a "Stop & disconnect" action so the rider
 * can fully shut the service down (and save battery) without opening the app.
 *
 * It also saves the parking location automatically when the cluster
 * disconnects (bike turned off / out of range) and when the rider taps
 * "Stop & disconnect" while connected (BLE range means the bike is nearby).
 */
@AndroidEntryPoint
class DominarService : Service() {

    companion object {
        private const val CHANNEL_ID = "dominar_connection"
        private const val NOTIFICATION_ID = 1
        const val ACTION_START = "com.dominar.ride.action.START"
        const val ACTION_STOP = "com.dominar.ride.action.STOP"

        // lastLocation older than this is considered stale for parking.
        private const val PARKING_MAX_LOCATION_AGE_MS = 2 * 60_000L

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

    @Inject
    lateinit var parkingDao: ParkingDao

    private val serviceScope = CoroutineScope(SupervisorJob() + Dispatchers.Default)
    private var stateJob: Job? = null
    private var callMonitor: CallMonitor? = null
    private var lastState: BleConnectionManager.ConnectionState? = null

    override fun onBind(intent: Intent?): IBinder? = null

    override fun onCreate() {
        super.onCreate()
        createChannel()
    }

    override fun onStartCommand(intent: Intent?, flags: Int, startId: Int): Int {
        if (intent?.action == ACTION_STOP) {
            stateJob?.cancel()
            val manager = BleManagerHolder.get(this)
            if (manager.connectionState.value is BleConnectionManager.ConnectionState.Connected) {
                // Rider is next to the bike (BLE range) — remember where it is.
                saveParkingOnDisconnect()
            }
            manager.disconnect()
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

    // ---------- Parking auto-save ----------

    private fun hasLocationPermission(): Boolean =
        checkSelfPermission(android.Manifest.permission.ACCESS_FINE_LOCATION) ==
            PackageManager.PERMISSION_GRANTED ||
            checkSelfPermission(android.Manifest.permission.ACCESS_COARSE_LOCATION) ==
            PackageManager.PERMISSION_GRANTED

    @SuppressLint("MissingPermission")
    private fun saveParkingOnDisconnect() {
        if (!hasLocationPermission()) return
        val fused = LocationServices.getFusedLocationProviderClient(this)
        runCatching {
            fused.lastLocation
                .addOnSuccessListener { location ->
                    if (location != null &&
                        System.currentTimeMillis() - location.time <= PARKING_MAX_LOCATION_AGE_MS
                    ) {
                        persistParking(location.latitude, location.longitude)
                    } else {
                        // lastLocation is null or stale — ask for a fresh fix.
                        requestFreshLocation(fused)
                    }
                }
                .addOnFailureListener { requestFreshLocation(fused) }
        }
    }

    @SuppressLint("MissingPermission")
    private fun requestFreshLocation(fused: FusedLocationProviderClient) {
        if (!hasLocationPermission()) return
        runCatching {
            fused.getCurrentLocation(
                Priority.PRIORITY_BALANCED_POWER_ACCURACY,
                CancellationTokenSource().token
            ).addOnSuccessListener { location ->
                if (location != null) persistParking(location.latitude, location.longitude)
            }
        }
    }

    private fun persistParking(lat: Double, lng: Double) {
        // Independent scope: the write must survive even if the service is
        // being torn down right after a manual "Stop & disconnect".
        CoroutineScope(SupervisorJob() + Dispatchers.IO).launch {
            parkingDao.upsert(
                ParkingEntity(
                    id = 0,
                    lat = lat,
                    lng = lng,
                    timestamp = System.currentTimeMillis(),
                    auto = true
                )
            )
        }
    }

    // ---------- Notification ----------

    private fun observeConnectionState() {
        stateJob?.cancel()
        stateJob = serviceScope.launch {
            BleManagerHolder.get(this@DominarService).connectionState.collect { state ->
                val previous = lastState
                lastState = state
                if (previous is BleConnectionManager.ConnectionState.Connected &&
                    state !is BleConnectionManager.ConnectionState.Connected
                ) {
                    saveParkingOnDisconnect()
                }
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

    private fun buildNotification(text: String): Notification {
        val stopPendingIntent = PendingIntent.getService(
            this, 1,
            Intent(this, DominarService::class.java).setAction(ACTION_STOP),
            PendingIntent.FLAG_UPDATE_CURRENT or PendingIntent.FLAG_IMMUTABLE
        )
        return NotificationCompat.Builder(this, CHANNEL_ID)
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
            .addAction(0, "Stop & disconnect", stopPendingIntent)
            .build()
    }

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
