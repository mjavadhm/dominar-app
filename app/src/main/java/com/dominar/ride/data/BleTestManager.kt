package com.dominar.ride.data

import android.annotation.SuppressLint
import android.bluetooth.*
import android.bluetooth.le.*
import android.content.Context
import android.os.ParcelUuid
import android.util.Log
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.StateFlow
import kotlinx.coroutines.flow.asStateFlow
import java.util.UUID

/**
 * BLE Test Manager for communicating with the Bajaj Ride cluster.
 *
 * Characteristic UUIDs from protocol docs:
 * - Navigation (TBT):   0110676e-6972-6565-6e69-676e4543544f
 * - Phone Status:        0210676e-6972-6565-6e69-676e4543544f
 * - Missed Call:         0310676e-6972-6565-6e69-676e4543544f
 * - SMS/WhatsApp Alert:  0410676e-6972-6565-6e69-676e4543544f
 * - Controls:            0a10676e-6972-6565-6e69-676e4543544f
 * - Media Info:          0610676e-6972-6565-6e69-676e4543544f
 */
class BleTestManager(private val context: Context) {

    companion object {
        private const val TAG = "BleTestManager"

        val UUID_NAV       = UUID.fromString("0110676e-6972-6565-6e69-676e4543544f")
        val UUID_PHONE     = UUID.fromString("0210676e-6972-6565-6e69-676e4543544f")
        val UUID_MISSED    = UUID.fromString("0310676e-6972-6565-6e69-676e4543544f")
        val UUID_ALERT     = UUID.fromString("0410676e-6972-6565-6e69-676e4543544f")
        val UUID_CONTROLS  = UUID.fromString("0a10676e-6972-6565-6e69-676e4543544f")
        val UUID_MEDIA     = UUID.fromString("0610676e-6972-6565-6e69-676e4543544f")
    }

    // --- State ---
    sealed class ConnectionState {
        data object Disconnected : ConnectionState()
        data object Scanning : ConnectionState()
        data class DeviceFound(val device: BluetoothDevice, val name: String) : ConnectionState()
        data object Connecting : ConnectionState()
        data object Connected : ConnectionState()
        data class Error(val message: String) : ConnectionState()
    }

    private val _connectionState = MutableStateFlow<ConnectionState>(ConnectionState.Disconnected)
    val connectionState: StateFlow<ConnectionState> = _connectionState.asStateFlow()

    private val _logs = MutableStateFlow<List<String>>(emptyList())
    val logs: StateFlow<List<String>> = _logs.asStateFlow()

    private var bluetoothGatt: BluetoothGatt? = null
    private var scanner: BluetoothLeScanner? = null
    private var discoveredDevices = mutableListOf<BluetoothDevice>()

    private val _foundDevices = MutableStateFlow<List<Pair<BluetoothDevice, String>>>(emptyList())
    val foundDevices: StateFlow<List<Pair<BluetoothDevice, String>>> = _foundDevices.asStateFlow()

    private fun log(msg: String) {
        Log.d(TAG, msg)
        _logs.value = _logs.value + "[${java.text.SimpleDateFormat("HH:mm:ss", java.util.Locale.US).format(java.util.Date())}] $msg"
    }

    // ========== SCANNING ==========

    @SuppressLint("MissingPermission")
    fun startScan() {
        val bluetoothManager = context.getSystemService(Context.BLUETOOTH_SERVICE) as BluetoothManager
        val adapter = bluetoothManager.adapter
        if (adapter == null || !adapter.isEnabled) {
            _connectionState.value = ConnectionState.Error("Bluetooth is not enabled")
            log("ERROR: Bluetooth not enabled")
            return
        }

        scanner = adapter.bluetoothLeScanner
        if (scanner == null) {
            _connectionState.value = ConnectionState.Error("BLE Scanner not available")
            log("ERROR: BLE Scanner not available")
            return
        }

        discoveredDevices.clear()
        _foundDevices.value = emptyList()
        _connectionState.value = ConnectionState.Scanning
        log("Scanning for BLE devices...")

        val settings = ScanSettings.Builder()
            .setScanMode(ScanSettings.SCAN_MODE_LOW_LATENCY)
            .build()

        scanner?.startScan(null, settings, scanCallback)

        // Auto-stop after 15 seconds
        android.os.Handler(android.os.Looper.getMainLooper()).postDelayed({
            stopScan()
        }, 15000)
    }

    @SuppressLint("MissingPermission")
    fun stopScan() {
        scanner?.stopScan(scanCallback)
        if (_connectionState.value is ConnectionState.Scanning) {
            if (_foundDevices.value.isEmpty()) {
                _connectionState.value = ConnectionState.Disconnected
                log("Scan complete - no devices found")
            } else {
                log("Scan complete - ${_foundDevices.value.size} device(s) found")
            }
        }
    }

    private val scanCallback = object : ScanCallback() {
        @SuppressLint("MissingPermission")
        override fun onScanResult(callbackType: Int, result: ScanResult) {
            val device = result.device
            val name = device.name ?: return  // Skip unnamed devices
            if (discoveredDevices.none { it.address == device.address }) {
                discoveredDevices.add(device)
                _foundDevices.value = _foundDevices.value + Pair(device, name)
                log("Found: $name (${device.address})")
            }
        }

        override fun onScanFailed(errorCode: Int) {
            _connectionState.value = ConnectionState.Error("Scan failed: $errorCode")
            log("ERROR: Scan failed with code $errorCode")
        }
    }

    // ========== CONNECTING ==========

    @SuppressLint("MissingPermission")
    fun connectToDevice(device: BluetoothDevice) {
        stopScan()
        _connectionState.value = ConnectionState.Connecting
        log("Connecting to ${device.name ?: device.address}...")

        bluetoothGatt = device.connectGatt(context, false, gattCallback, BluetoothDevice.TRANSPORT_LE)
    }

    @SuppressLint("MissingPermission")
    fun disconnect() {
        bluetoothGatt?.disconnect()
        bluetoothGatt?.close()
        bluetoothGatt = null
        _connectionState.value = ConnectionState.Disconnected
        log("Disconnected")
    }

    private val gattCallback = object : BluetoothGattCallback() {
        @SuppressLint("MissingPermission")
        override fun onConnectionStateChange(gatt: BluetoothGatt, status: Int, newState: Int) {
            when (newState) {
                BluetoothProfile.STATE_CONNECTED -> {
                    log("Connected! Discovering services...")
                    gatt.discoverServices()
                }
                BluetoothProfile.STATE_DISCONNECTED -> {
                    _connectionState.value = ConnectionState.Disconnected
                    log("Disconnected from device")
                    gatt.close()
                }
            }
        }

        override fun onServicesDiscovered(gatt: BluetoothGatt, status: Int) {
            if (status == BluetoothGatt.GATT_SUCCESS) {
                _connectionState.value = ConnectionState.Connected
                val services = gatt.services
                log("Services discovered: ${services.size} service(s)")
                services.forEach { service ->
                    log("  Service: ${service.uuid}")
                    service.characteristics.forEach { char ->
                        log("    Char: ${char.uuid} props=${char.properties}")
                    }
                }
            } else {
                _connectionState.value = ConnectionState.Error("Service discovery failed: $status")
                log("ERROR: Service discovery failed with status $status")
            }
        }

        override fun onCharacteristicWrite(
            gatt: BluetoothGatt,
            characteristic: BluetoothGattCharacteristic,
            status: Int
        ) {
            if (status == BluetoothGatt.GATT_SUCCESS) {
                log("✅ Write OK → ${characteristic.uuid}")
            } else {
                log("❌ Write FAILED → ${characteristic.uuid} status=$status")
            }
        }
    }

    // ========== WRITING ==========

    @SuppressLint("MissingPermission")
    fun writeToCharacteristic(charUuid: UUID, data: ByteArray): Boolean {
        val gatt = bluetoothGatt ?: run {
            log("ERROR: Not connected")
            return false
        }

        // Search all services for this characteristic
        var targetChar: BluetoothGattCharacteristic? = null
        for (service in gatt.services) {
            val char = service.getCharacteristic(charUuid)
            if (char != null) {
                targetChar = char
                break
            }
        }

        if (targetChar == null) {
            log("ERROR: Characteristic $charUuid not found")
            return false
        }

        targetChar.value = data
        targetChar.writeType = BluetoothGattCharacteristic.WRITE_TYPE_DEFAULT
        val result = gatt.writeCharacteristic(targetChar)
        log("Writing ${data.size} bytes → ${charUuid.toString().substring(0, 4)}... → ${if (result) "queued" else "FAILED"}")
        log("  Hex: ${data.joinToString(" ") { "%02X".format(it) }}")
        return result
    }

}
