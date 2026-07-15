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

    // ========== PACKET BUILDERS ==========

    /**
     * Build Navigation packet (48 bytes).
     * Based on protocol Section 1.
     */
    fun buildNavPacket(
        isAm: Boolean = true,
        isMeters: Boolean = false,
        maneuverCode: Int = 0x47,  // STRAIGHT
        distToTurnInt: Int = 0,
        distToTurnFrac: Int = 0,
        etaHours: Int = 0,
        etaMinutes: Int = 0,
        distLeftInt: Int = 0,
        distLeftFrac: Int = 0,
        roundaboutExit: Int = 0,
        instructionText: String = ""
    ): ByteArray {
        val packet = ByteArray(48)

        // Byte 0: Flags
        //   Bit 0 = 1 (always), Bit 4 = distUnit (1=meters), Bit 7 = PM flag
        var flags = 0x01
        if (!isAm) flags = flags or 0x80
        if (isMeters) flags = flags or 0x10
        packet[0] = flags.toByte()

        // Byte 1: Maneuver code
        packet[1] = maneuverCode.toByte()

        // Bytes 2-3: Distance to turn fractional (LSB, MSB)
        val frac = distToTurnFrac * 100
        packet[2] = (frac and 0xFF).toByte()
        packet[3] = ((frac shr 8) and 0xFF).toByte()

        // Bytes 4-5: Distance to turn integer (LSB, MSB)
        packet[4] = (distToTurnInt and 0xFF).toByte()
        packet[5] = ((distToTurnInt shr 8) and 0xFF).toByte()

        // Byte 6: ETA Minutes
        packet[6] = etaMinutes.toByte()

        // Byte 7: ETA Hours + Roundabout Exit
        packet[7] = ((roundaboutExit shl 4) or (etaHours and 0x0F)).toByte()

        // Bytes 8-11: Distance left
        val distLeftFracVal = distLeftFrac * 100
        packet[8] = (distLeftFracVal and 0xFF).toByte()
        packet[9] = ((distLeftFracVal shr 8) and 0xFF).toByte()
        packet[10] = (distLeftInt and 0xFF).toByte()
        packet[11] = ((distLeftInt shr 8) and 0xFF).toByte()

        // Byte 12-13: Flags2 + Reserved
        packet[12] = 0x00
        packet[13] = 0x00

        // Byte 14: Text length (max 31)
        val text = instructionText.take(31)
        packet[14] = text.length.toByte()

        // Bytes 15-46: Text (32 bytes padded)
        val textBytes = text.toByteArray(Charsets.US_ASCII)
        textBytes.copyInto(packet, destinationOffset = 15)

        // Byte 47: Checksum = sum of bytes 0-46 masked with 0xFF
        var checksum = 0
        for (i in 0 until 47) {
            checksum += packet[i].toInt() and 0xFF
        }
        packet[47] = (checksum and 0xFF).toByte()

        return packet
    }

    /**
     * Build Navigation Start packet.
     */
    fun buildNavStartPacket(): ByteArray {
        val packet = ByteArray(48)
        packet[0] = 0x01
        // maneuver = 0 (start), rest zeroes
        var checksum = 0
        for (i in 0 until 47) checksum += packet[i].toInt() and 0xFF
        packet[47] = (checksum and 0xFF).toByte()
        return packet
    }

    /**
     * Build Navigation Stop packet.
     */
    fun buildNavStopPacket(): ByteArray {
        val packet = ByteArray(48)
        // all zeroes = stop
        return packet
    }

    /**
     * Build Phone Status packet (55 bytes).
     * Based on protocol Section 2.
     */
    fun buildPhoneStatusPacket(
        volume: Int = 7,       // 0-15
        headset: Int = 0,      // 0 or 1
        callState: Int = 0,    // 0=NO_CALL, 1=INCOMING, 2=OUTGOING, 3=ACTIVE, 4=END
        batteryLevel: Int = 4, // 0-5
        signalStrength: Int = 3, // 0-4
        isActiveCall: Boolean = false,
        callerName: String = "",
        heartbeat: Int = 0
    ): ByteArray {
        val packet = ByteArray(55)

        // Byte 0: (headset << 4) | volume | 0xC0
        packet[0] = (0xC0 or (headset shl 4) or (volume and 0x0F)).toByte()

        // Byte 1: callState | (batteryLevel << 3)
        packet[1] = ((callState and 0x07) or ((batteryLevel and 0x1F) shl 3)).toByte()

        // Byte 2: Signal strength
        packet[2] = signalStrength.toByte()

        // Byte 3: Is active call
        packet[3] = if (isActiveCall) 1 else 0

        // Byte 4: Is NOT active
        packet[4] = if (isActiveCall) 0 else 1

        // Bytes 5-19: Reserved / padding

        // Byte 20: Name length (max 30)
        val name = callerName.take(30)
        packet[20] = name.length.toByte()

        // Bytes 21-50: Caller name
        val nameBytes = name.toByteArray(Charsets.UTF_8)
        nameBytes.copyInto(packet, destinationOffset = 21)

        // Byte 53: Heartbeat
        packet[53] = (heartbeat and 0xFF).toByte()

        return packet
    }

    /**
     * Build SMS/Alert packet (40 bytes).
     * Based on protocol Section 3.
     */
    fun buildAlertPacket(
        alertType: Int = 1,    // 1=SMS, 2=WhatsApp
        content: String = ""
    ): ByteArray {
        val packet = ByteArray(40)

        // Byte 0: Alert type
        packet[0] = alertType.toByte()

        // Byte 1: Content length (max 32)
        val text = content.take(32)
        packet[1] = text.length.toByte()

        // Bytes 2-33: Content
        val textBytes = text.toByteArray(Charsets.UTF_8)
        textBytes.copyInto(packet, destinationOffset = 2)

        return packet
    }

    /**
     * Build Missed Call packet (74 bytes).
     * Based on protocol Section 2 - Missed Call.
     */
    fun buildMissedCallPacket(
        name: String = "",
        number: String = ""
    ): ByteArray {
        val packet = ByteArray(74)

        // Byte 0: ID (always 1)
        packet[0] = 1

        // Byte 1: Name length (max 32)
        val nameStr = name.take(32)
        packet[1] = nameStr.length.toByte()

        // Bytes 2-33: Name
        nameStr.toByteArray(Charsets.UTF_8).copyInto(packet, destinationOffset = 2)

        // Byte 34: Number length (max 18)
        val numStr = number.take(18)
        packet[34] = numStr.length.toByte()

        // Bytes 35-52: Number
        numStr.toByteArray(Charsets.UTF_8).copyInto(packet, destinationOffset = 35)

        return packet
    }
}
