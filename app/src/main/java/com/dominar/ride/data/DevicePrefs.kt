package com.dominar.ride.data

import android.content.Context

/** Persists the last connected cluster and lightweight UI preferences. */
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

    // Ride HUD toggles (overlays on the Ride screen).
    var hudShowSpeed: Boolean
        get() = prefs.getBoolean("hud_show_speed", true)
        set(value) = prefs.edit().putBoolean("hud_show_speed", value).apply()

    var hudShowLeanAngle: Boolean
        get() = prefs.getBoolean("hud_show_lean_angle", true)
        set(value) = prefs.edit().putBoolean("hud_show_lean_angle", value).apply()

    var hudShowNextTurn: Boolean
        get() = prefs.getBoolean("hud_show_next_turn", true)
        set(value) = prefs.edit().putBoolean("hud_show_next_turn", value).apply()

    /** Clears only the paired-device data; HUD preferences are kept. */
    fun clear() = prefs.edit()
        .remove("last_device_address")
        .remove("last_device_name")
        .remove("auto_connect")
        .apply()
}
