package com.dominar.ride.ui

import android.annotation.SuppressLint
import android.bluetooth.BluetoothDevice
import android.content.Context
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.setValue
import com.dominar.ride.ble.BleConnectionManager
import com.dominar.ride.ble.BleManagerHolder
import com.dominar.ride.data.DevicePrefs
import com.dominar.ride.service.DominarService

/**
 * Single source of truth for the UI layer. Bridges Compose screens to the
 * shared BleConnectionManager, DevicePrefs and DominarService.
 */
@SuppressLint("MissingPermission")
class AppState(context: Context) {
    private val appContext = context.applicationContext
    private val prefs = DevicePrefs(appContext)
    private val ble: BleConnectionManager = BleManagerHolder.get(appContext)

    val connectionState = ble.connectionState
    val foundDevices = ble.foundDevices
    val logs = ble.logs

    var savedDeviceName by mutableStateOf(prefs.lastDeviceName)
        private set
    var savedDeviceAddress by mutableStateOf(prefs.lastDeviceAddress)
        private set
    private var _autoConnect by mutableStateOf(prefs.autoConnect)
    val autoConnect: Boolean get() = _autoConnect

    fun startScan() = ble.startScan()
    fun stopScan() = ble.stopScan()

    fun connectTo(device: BluetoothDevice) {
        prefs.lastDeviceAddress = device.address
        prefs.lastDeviceName = device.name ?: device.address
        savedDeviceAddress = prefs.lastDeviceAddress
        savedDeviceName = prefs.lastDeviceName
        DominarService.start(appContext)
        ble.connect(device)
    }

    /** Starts the foreground service which auto-connects to the saved MAC. */
    fun connectSaved() = DominarService.start(appContext)

    fun disconnect() = DominarService.stop(appContext)

    fun setAutoConnect(enabled: Boolean) {
        prefs.autoConnect = enabled
        _autoConnect = enabled
    }

    fun forgetDevice() {
        DominarService.stop(appContext)
        prefs.clear()
        savedDeviceAddress = null
        savedDeviceName = null
        _autoConnect = true
    }
}
