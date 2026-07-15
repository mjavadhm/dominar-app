package com.dominar.ride.ble

import android.annotation.SuppressLint
import android.bluetooth.BluetoothDevice
import android.bluetooth.BluetoothGatt
import android.bluetooth.BluetoothGattCallback
import android.bluetooth.BluetoothGattCharacteristic
import android.bluetooth.BluetoothGattDescriptor
import android.bluetooth.BluetoothManager
import android.bluetooth.BluetoothProfile
import android.bluetooth.BluetoothStatusCodes
import android.bluetooth.le.BluetoothLeScanner
import android.bluetooth.le.ScanCallback
import android.bluetooth.le.ScanResult
import android.bluetooth.le.ScanSettings
import android.content.Context
import android.os.Build
import android.util.Log
import com.dominar.ride.protocol.DominarProtocol
import kotlinx.coroutines.CompletableDeferred
import kotlinx.coroutines.CoroutineScope
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.Job
import kotlinx.coroutines.channels.Channel
import kotlinx.coroutines.delay
import kotlinx.coroutines.flow.MutableSharedFlow
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.SharedFlow
import kotlinx.coroutines.flow.StateFlow
import kotlinx.coroutines.flow.asSharedFlow
import kotlinx.coroutines.flow.asStateFlow
import kotlinx.coroutines.launch
import kotlinx.coroutines.withTimeoutOrNull
import java.text.SimpleDateFormat
import java.util.Date
import java.util.Locale
import java.util.UUID

/**
 * Production BLE connection manager for the Dominar cluster.
 * - Serialized write queue (one in-flight write at a time)
 * - Auto-reconnect with exponential backoff
 * - Control-packet notifications (call accept/reject, volume)
 */
@SuppressLint("MissingPermission")
class BleConnectionManager(
    private val context: Context,
    private val scope: CoroutineScope
) {
    companion object {
        private const val TAG = "BleConnectionManager"
        private const val SCAN_TIMEOUT_MS = 15_000L
        private const val WRITE_TIMEOUT_MS = 5_000L
        private const val BASE_BACKOFF_MS = 1_000L
        private const val MAX_BACKOFF_MS = 30_000L
        private val CCCD_UUID: UUID =
            UUID.fromString("00002902-0000-1000-8000-00805f9b34fb")
        private val CONTROLS_UUID: UUID =
            UUID.fromString(DominarProtocol.UUID_CONTROLS)
    }

    sealed class ConnectionState {
        data object Disconnected : ConnectionState()
        data object Scanning : ConnectionState()
        data object Connecting : ConnectionState()
        data object Connected : ConnectionState()
        data class Reconnecting(val attempt: Int) : ConnectionState()
        data class Error(val message: String) : ConnectionState()
    }

    private val _connectionState =
        MutableStateFlow<ConnectionState>(ConnectionState.Disconnected)
    val connectionState: StateFlow<ConnectionState> = _connectionState.asStateFlow()

    private val _foundDevices =
        MutableStateFlow<List<Pair<BluetoothDevice, String>>>(emptyList())
    val foundDevices: StateFlow<List<Pair<BluetoothDevice, String>>> =
        _foundDevices.asStateFlow()

    private val _controlPackets =
        MutableSharedFlow<DominarProtocol.ControlState>(extraBufferCapacity = 16)
    val controlPackets: SharedFlow<DominarProtocol.ControlState> =
        _controlPackets.asSharedFlow()

    private val _logs = MutableStateFlow<List<String>>(emptyList())
    val logs: StateFlow<List<String>> = _logs.asStateFlow()

    private var gatt: BluetoothGatt? = null
    private var scanner: BluetoothLeScanner? = null
    private var lastDevice: BluetoothDevice? = null
    private var userInitiatedDisconnect = false
    private var reconnectJob: Job? = null
    private var scanTimeoutJob: Job? = null

    // ---------- Write queue ----------

    private class WriteRequest(val charUuid: UUID, val data: ByteArray)

    private val writeQueue = Channel<WriteRequest>(capacity = 64)
    @Volatile private var writeCompletion: CompletableDeferred<Boolean>? = null

    init {
        scope.launch(Dispatchers.IO) {
            for (req in writeQueue) {
                val g = gatt ?: continue
                if (_connectionState.value !is ConnectionState.Connected) continue
                val char = findCharacteristic(g, req.charUuid) ?: run {
                    log("ERROR: Characteristic ${req.charUuid} not found")
                    null
                } ?: continue

                val completion = CompletableDeferred<Boolean>()
                writeCompletion = completion
                if (!doWrite(g, char, req.data)) {
                    log("❌ Write failed to start → ${short(req.charUuid)}")
                    writeCompletion = null
                    continue
                }
                val ok = withTimeoutOrNull(WRITE_TIMEOUT_MS) { completion.await() }
                writeCompletion = null
                when (ok) {
                    true -> log("✅ Write OK (${req.data.size}B) → ${short(req.charUuid)}")
                    false -> log("❌ Write FAILED → ${short(req.charUuid)}")
                    null -> log("⏱ Write TIMEOUT → ${short(req.charUuid)}")
                }
            }
        }
    }

    /** Queue a packet for writing. Returns false if not connected / queue full. */
    fun send(charUuid: UUID, data: ByteArray): Boolean {
        if (_connectionState.value !is ConnectionState.Connected) {
            log("Dropped ${data.size}B → ${short(charUuid)} (not connected)")
            return false
        }
        return writeQueue.trySend(WriteRequest(charUuid, data)).isSuccess
    }

    // ---------- Scanning ----------

    fun startScan() {
        val adapter =
            (context.getSystemService(Context.BLUETOOTH_SERVICE) as BluetoothManager).adapter
        if (adapter == null || !adapter.isEnabled) {
            _connectionState.value = ConnectionState.Error("Bluetooth is not enabled")
            return
        }
        scanner = adapter.bluetoothLeScanner ?: run {
            _connectionState.value = ConnectionState.Error("BLE scanner not available")
            return
        }

        _foundDevices.value = emptyList()
        _connectionState.value = ConnectionState.Scanning
        log("Scanning...")

        val settings = ScanSettings.Builder()
            .setScanMode(ScanSettings.SCAN_MODE_LOW_LATENCY)
            .build()
        scanner?.startScan(null, settings, scanCallback)

        scanTimeoutJob?.cancel()
        scanTimeoutJob = scope.launch {
            delay(SCAN_TIMEOUT_MS)
            stopScan()
        }
    }

    fun stopScan() {
        scanTimeoutJob?.cancel()
        runCatching { scanner?.stopScan(scanCallback) }
        if (_connectionState.value is ConnectionState.Scanning) {
            _connectionState.value = ConnectionState.Disconnected
            log("Scan stopped — ${_foundDevices.value.size} device(s) found")
        }
    }

    private val scanCallback = object : ScanCallback() {
        override fun onScanResult(callbackType: Int, result: ScanResult) {
            val device = result.device
            val name = device.name ?: return
            if (_foundDevices.value.none { it.first.address == device.address }) {
                _foundDevices.value = _foundDevices.value + (device to name)
                log("Found: $name (${device.address})")
            }
        }

        override fun onScanFailed(errorCode: Int) {
            _connectionState.value = ConnectionState.Error("Scan failed: $errorCode")
        }
    }

    // ---------- Connection ----------

    fun connect(device: BluetoothDevice) {
        stopScan()
        userInitiatedDisconnect = false
        lastDevice = device
        _connectionState.value = ConnectionState.Connecting
        log("Connecting to ${device.name ?: device.address}...")
        gatt = device.connectGatt(context, false, gattCallback, BluetoothDevice.TRANSPORT_LE)
    }

    fun disconnect() {
        userInitiatedDisconnect = true
        reconnectJob?.cancel()
        gatt?.disconnect()
        gatt?.close()
        gatt = null
        _connectionState.value = ConnectionState.Disconnected
        log("Disconnected (by user)")
    }

    private fun scheduleReconnect() {
        val device = lastDevice ?: return
        reconnectJob?.cancel()
        reconnectJob = scope.launch {
            var attempt = 0
            while (_connectionState.value !is ConnectionState.Connected &&
                !userInitiatedDisconnect
            ) {
                attempt++
                val backoff =
                    (BASE_BACKOFF_MS * (1L shl (attempt - 1).coerceAtMost(5)))
                        .coerceAtMost(MAX_BACKOFF_MS)
                _connectionState.value = ConnectionState.Reconnecting(attempt)
                log("Reconnect attempt $attempt in ${backoff}ms...")
                delay(backoff)
                if (userInitiatedDisconnect) break
                gatt?.close()
                gatt = device.connectGatt(
                    context, false, gattCallback, BluetoothDevice.TRANSPORT_LE
                )
                // Wait for the connection attempt to resolve before next retry
                delay(10_000)
            }
        }
    }

    private val gattCallback = object : BluetoothGattCallback() {
        override fun onConnectionStateChange(g: BluetoothGatt, status: Int, newState: Int) {
            when (newState) {
                BluetoothProfile.STATE_CONNECTED -> {
                    log("Connected — discovering services...")
                    g.discoverServices()
                }
                BluetoothProfile.STATE_DISCONNECTED -> {
                    writeCompletion?.complete(false)
                    if (userInitiatedDisconnect) {
                        _connectionState.value = ConnectionState.Disconnected
                    } else {
                        log("Connection lost (status=$status)")
                        scheduleReconnect()
                    }
                }
            }
        }

        override fun onServicesDiscovered(g: BluetoothGatt, status: Int) {
            if (status == BluetoothGatt.GATT_SUCCESS) {
                reconnectJob?.cancel()
                _connectionState.value = ConnectionState.Connected
                log("Services discovered: ${g.services.size}")
                enableControlNotifications(g)
            } else {
                _connectionState.value = ConnectionState.Error("Service discovery failed: $status")
            }
        }

        override fun onCharacteristicWrite(
            g: BluetoothGatt,
            characteristic: BluetoothGattCharacteristic,
            status: Int
        ) {
            writeCompletion?.complete(status == BluetoothGatt.GATT_SUCCESS)
        }

        @Deprecated("Deprecated in API 33, still needed for older devices")
        override fun onCharacteristicChanged(
            g: BluetoothGatt,
            characteristic: BluetoothGattCharacteristic
        ) {
            @Suppress("DEPRECATION")
            handleNotification(characteristic.uuid, characteristic.value ?: return)
        }

        override fun onCharacteristicChanged(
            g: BluetoothGatt,
            characteristic: BluetoothGattCharacteristic,
            value: ByteArray
        ) {
            handleNotification(characteristic.uuid, value)
        }
    }

    private fun handleNotification(uuid: UUID, value: ByteArray) {
        if (uuid == CONTROLS_UUID) {
            DominarProtocol.parseControlPacket(value)?.let {
                log("Control packet: $it")
                _controlPackets.tryEmit(it)
            }
        }
    }

    private fun enableControlNotifications(g: BluetoothGatt) {
        val char = findCharacteristic(g, CONTROLS_UUID) ?: run {
            log("Controls characteristic not found — skip notifications")
            return
        }
        g.setCharacteristicNotification(char, true)
        val cccd = char.getDescriptor(CCCD_UUID) ?: return
        if (Build.VERSION.SDK_INT >= 33) {
            g.writeDescriptor(cccd, BluetoothGattDescriptor.ENABLE_NOTIFICATION_VALUE)
        } else {
            @Suppress("DEPRECATION")
            cccd.value = BluetoothGattDescriptor.ENABLE_NOTIFICATION_VALUE
            @Suppress("DEPRECATION")
            g.writeDescriptor(cccd)
        }
        log("Control notifications enabled")
    }

    // ---------- Helpers ----------

    private fun doWrite(
        g: BluetoothGatt,
        char: BluetoothGattCharacteristic,
        data: ByteArray
    ): Boolean {
        return if (Build.VERSION.SDK_INT >= 33) {
            g.writeCharacteristic(
                char, data, BluetoothGattCharacteristic.WRITE_TYPE_DEFAULT
            ) == BluetoothStatusCodes.SUCCESS
        } else {
            @Suppress("DEPRECATION")
            char.value = data
            char.writeType = BluetoothGattCharacteristic.WRITE_TYPE_DEFAULT
            @Suppress("DEPRECATION")
            g.writeCharacteristic(char)
        }
    }

    private fun findCharacteristic(
        g: BluetoothGatt,
        uuid: UUID
    ): BluetoothGattCharacteristic? {
        for (service in g.services) {
            service.getCharacteristic(uuid)?.let { return it }
        }
        return null
    }

    private fun short(uuid: UUID) = uuid.toString().substring(0, 4)

    private fun log(msg: String) {
        Log.d(TAG, msg)
        val ts = SimpleDateFormat("HH:mm:ss", Locale.US).format(Date())
        _logs.value = (_logs.value + "[$ts] $msg").takeLast(200)
    }
}
