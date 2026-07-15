package com.dominar.ride.service

import android.content.BroadcastReceiver
import android.content.Context
import android.content.Intent
import com.dominar.ride.data.DevicePrefs

/** Restarts the connection service after phone reboot. */
class BootReceiver : BroadcastReceiver() {
    override fun onReceive(context: Context, intent: Intent) {
        if (intent.action == Intent.ACTION_BOOT_COMPLETED) {
            val prefs = DevicePrefs(context)
            if (prefs.autoConnect && prefs.lastDeviceAddress != null) {
                DominarService.start(context)
            }
        }
    }
}
