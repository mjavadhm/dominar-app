package com.dominar.ride.data

import android.content.Context

/** Persists the last connected cluster so we can auto-reconnect after app/phone restart. */
class DevicePrefs(context: Context) {
    private val prefs =
        context.applicationContext.getSharedPreferences("dominar_prefs", Context.MODE_PRIVATE)

    var lastDeviceAddress: String?
        get() = prefs.getString("last_device_address", null)
        set(value) = prefs.edit().putString("last_device_address", value).apply()

    var lastDeviceName: String?
        get() = prefs.getString("last_device_name", null)
        set(value) = prefs.edit().putString("last_device_name", value).apply()

    var autoConnect: Boolean
        get() = prefs.getBoolean("auto_connect", true)
        set(value) = prefs.edit().putBoolean("auto_connect", value).apply()

    fun clear() = prefs.edit().clear().apply()
}
