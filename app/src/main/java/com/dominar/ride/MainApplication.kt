package com.dominar.ride

import android.app.Application
import androidx.work.ExistingPeriodicWorkPolicy
import androidx.work.PeriodicWorkRequestBuilder
import androidx.work.WorkManager
import com.dominar.ride.debug.DebugLog
import com.dominar.ride.garage.GarageReminderWorker
import dagger.hilt.android.HiltAndroidApp
import java.util.concurrent.TimeUnit

@HiltAndroidApp
class MainApplication : Application() {

    override fun onCreate() {
        super.onCreate()
        DebugLog.init(this)
        scheduleGarageReminders()
    }

    private fun scheduleGarageReminders() {
        WorkManager.getInstance(this).enqueueUniquePeriodicWork(
            "garage_reminders",
            ExistingPeriodicWorkPolicy.KEEP,
            PeriodicWorkRequestBuilder<GarageReminderWorker>(1, TimeUnit.DAYS).build()
        )
    }
}
