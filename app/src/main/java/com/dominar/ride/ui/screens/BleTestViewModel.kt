package com.dominar.ride.ui.screens

import android.bluetooth.BluetoothDevice
import androidx.lifecycle.ViewModel
import com.dominar.ride.data.BleTestManager
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.StateFlow
import kotlinx.coroutines.flow.asStateFlow

class BleTestViewModel : ViewModel() {
    private var bleManager: BleTestManager? = null

    val connectionState get() = bleManager?.connectionState ?: MutableStateFlow(BleTestManager.ConnectionState.Disconnected)
    val logs get() = bleManager?.logs ?: MutableStateFlow(emptyList())
    val foundDevices get() = bleManager?.foundDevices ?: MutableStateFlow(emptyList())

    // Nav form state
    val navManeuver = MutableStateFlow(0x47) // STRAIGHT
    val navDistance = MutableStateFlow("250")
    val navDistFrac = MutableStateFlow("0")
    val navDistLeftInt = MutableStateFlow("12")
    val navDistLeftFrac = MutableStateFlow("50")
    val navIsMeters = MutableStateFlow(true)
    val navText = MutableStateFlow("Main Street")
    val navEtaH = MutableStateFlow("10")
    val navEtaM = MutableStateFlow("30")

    // Alert form state
    val alertType = MutableStateFlow(1) // 1=SMS, 2=WhatsApp
    val alertContent = MutableStateFlow("Hello from test!")

    // Phone status form state
    val phoneBattery = MutableStateFlow(4)
    val phoneSignal = MutableStateFlow(3)
    val phoneVolume = MutableStateFlow(7)
    val phoneCallState = MutableStateFlow(0)
    val phoneCallerName = MutableStateFlow("")

    fun initManager(manager: BleTestManager) {
        bleManager = manager
    }

    fun startScan() = bleManager?.startScan()
    fun stopScan() = bleManager?.stopScan()
    fun connectToDevice(device: BluetoothDevice) = bleManager?.connectToDevice(device)
    fun disconnect() = bleManager?.disconnect()

    fun sendNavPacket() {
        bleManager?.let { mgr ->
            val maneuverEnum = com.dominar.ride.protocol.DominarProtocol.Maneuver.entries.find { it.code == navManeuver.value } ?: com.dominar.ride.protocol.DominarProtocol.Maneuver.STRAIGHT
            val distToTurn = (navDistance.value.toIntOrNull() ?: 0) + (navDistFrac.value.toIntOrNull() ?: 0) / 100.0
            val distLeft = (navDistLeftInt.value.toIntOrNull() ?: 0) + (navDistLeftFrac.value.toIntOrNull() ?: 0) / 100.0
            
            val packet = com.dominar.ride.protocol.DominarProtocol.buildNavigationPacket(
                isPm = false,
                distanceUnitMeters = navIsMeters.value,
                maneuver = maneuverEnum,
                distanceToTurn = distToTurn,
                etaHour = navEtaH.value.toIntOrNull() ?: 0,
                etaMinute = navEtaM.value.toIntOrNull() ?: 0,
                distanceLeft = distLeft,
                instructionText = navText.value
            )
            mgr.writeToCharacteristic(BleTestManager.UUID_NAV, packet)
        }
    }

    fun sendNavStart() {
        bleManager?.let { it.writeToCharacteristic(BleTestManager.UUID_NAV, com.dominar.ride.protocol.DominarProtocol.buildNavStartPacket()) }
    }

    fun sendNavStop() {
        bleManager?.let { it.writeToCharacteristic(BleTestManager.UUID_NAV, com.dominar.ride.protocol.DominarProtocol.buildNavStopPacket()) }
    }

    fun sendAlert() {
        bleManager?.let { mgr ->
            val type = if (alertType.value == 1) com.dominar.ride.protocol.DominarProtocol.AlertType.SMS else com.dominar.ride.protocol.DominarProtocol.AlertType.WHATSAPP
            val packet = com.dominar.ride.protocol.DominarProtocol.buildAlertPacket(
                type = type,
                content = alertContent.value
            )
            mgr.writeToCharacteristic(BleTestManager.UUID_ALERT, packet)
        }
    }

    fun sendPhoneStatus() {
        bleManager?.let { mgr ->
            val callStateEnum = com.dominar.ride.protocol.DominarProtocol.CallState.entries.find { it.code == phoneCallState.value } ?: com.dominar.ride.protocol.DominarProtocol.CallState.NO_CALL
            val packet = com.dominar.ride.protocol.DominarProtocol.buildPhoneStatusPacket(
                batteryLevel = phoneBattery.value,
                signalStrength = phoneSignal.value,
                volume = phoneVolume.value,
                callState = callStateEnum,
                callerName = phoneCallerName.value,
                isActiveCall = phoneCallState.value == 3,
                headsetConnected = false,
                heartbeat = 0
            )
            mgr.writeToCharacteristic(BleTestManager.UUID_PHONE, packet)
        }
    }
}
