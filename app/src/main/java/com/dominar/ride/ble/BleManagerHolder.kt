package com.dominar.ride.ble

import android.content.Context
import kotlinx.coroutines.CoroutineScope
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.SupervisorJob

/**
 * Process-wide shared BleConnectionManager so the Service, ViewModels
 * and future features all talk to the SAME connection.
 */
object BleManagerHolder {
    private val appScope = CoroutineScope(SupervisorJob() + Dispatchers.Default)
    @Volatile private var instance: BleConnectionManager? = null

    fun get(context: Context): BleConnectionManager {
        return instance ?: synchronized(this) {
            instance ?: BleConnectionManager(context.applicationContext, appScope)
                .also { instance = it }
        }
    }
}
