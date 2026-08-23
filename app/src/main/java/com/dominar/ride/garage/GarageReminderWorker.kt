package com.dominar.ride.garage

import android.app.NotificationChannel
import android.app.NotificationManager
import android.content.Context
import android.content.pm.PackageManager
import android.os.Build
import androidx.core.app.NotificationCompat
import androidx.core.content.ContextCompat
import androidx.work.CoroutineWorker
import androidx.work.WorkerParameters
import com.dominar.ride.R
import com.dominar.ride.data.db.DocumentDao
import com.dominar.ride.data.db.FuelLogDao
import com.dominar.ride.data.db.ServiceIntervalDao
import com.dominar.ride.data.db.ServiceLogDao
import dagger.hilt.EntryPoint
import dagger.hilt.InstallIn
import dagger.hilt.android.EntryPointAccessors
import dagger.hilt.components.SingletonComponent

/**
 * Runs once a day: checks service intervals and document expiry dates and
 * posts reminder notifications when something is (almost) due.
 */
class GarageReminderWorker(
    appContext: Context,
    params: WorkerParameters
) : CoroutineWorker(appContext, params) {

    @EntryPoint
    @InstallIn(SingletonComponent::class)
    interface GarageEntryPoint {
        fun serviceLogDao(): ServiceLogDao
        fun serviceIntervalDao(): ServiceIntervalDao
        fun fuelLogDao(): FuelLogDao
        fun documentDao(): DocumentDao
    }

    override suspend fun doWork(): Result {
        val entryPoint =
            EntryPointAccessors.fromApplication(applicationContext, GarageEntryPoint::class.java)
        ensureChannel()
        val now = System.currentTimeMillis()
        val dayMs = 24L * 60 * 60 * 1000

        // ---- Documents (30 / 7 / 1 / 0 days, then weekly after expiry) ----
        entryPoint.documentDao().listAll().forEach { doc ->
            val daysLeft = ((doc.expiryTimestamp - now) / dayMs).toInt()
            val label = if (doc.type == "INSURANCE") "Insurance" else "Technical inspection"
            val id = if (doc.type == "INSURANCE") 3000 else 3001
            when {
                daysLeft < 0 && (-daysLeft) % 7 == 0 ->
                    notify(id, "$label expired", "$label expired ${-daysLeft} days ago — renew it!")
                daysLeft == 0 ->
                    notify(id, "$label expiring", "$label expires today!")
                daysLeft in intArrayOf(1, 7, 30) ->
                    notify(id, "$label expiring", "$label expires in $daysLeft days")
            }
        }

        // ---- Services ----
        val logs = entryPoint.serviceLogDao().listAll()
        val intervals = entryPoint.serviceIntervalDao().listAll().associateBy { it.type }
        val fuel = entryPoint.fuelLogDao().listAll()
        val currentOdo = maxOf(
            logs.maxOfOrNull { it.odometerKm } ?: 0,
            fuel.maxOfOrNull { it.odometerKm } ?: 0
        )
        SERVICE_TYPES.forEachIndexed { index, type ->
            val last = logs.filter { it.type == type.key }.maxByOrNull { it.timestamp }
                ?: return@forEachIndexed
            val status = computeServiceStatus(type, last, intervals[type.key], currentOdo, now)
            val kmLeft = status.kmLeft ?: Int.MAX_VALUE
            val daysLeft = status.daysLeft ?: Int.MAX_VALUE
            val overdue = (status.progress ?: 0f) >= 1f
            if (overdue || kmLeft <= 200 || daysLeft <= 7) {
                val detail = when {
                    overdue -> "overdue"
                    kmLeft <= 200 -> "due in $kmLeft km"
                    else -> "due in $daysLeft days"
                }
                notify(2000 + index, "Service reminder", "${type.label} is $detail")
            }
        }
        return Result.success()
    }

    private fun ensureChannel() {
        if (Build.VERSION.SDK_INT >= 26) {
            val channel = NotificationChannel(
                CHANNEL_ID, "Garage reminders", NotificationManager.IMPORTANCE_DEFAULT
            )
            applicationContext.getSystemService(NotificationManager::class.java)
                .createNotificationChannel(channel)
        }
    }

    private fun notify(id: Int, title: String, text: String) {
        if (Build.VERSION.SDK_INT >= 33 &&
            ContextCompat.checkSelfPermission(
                applicationContext, android.Manifest.permission.POST_NOTIFICATIONS
            ) != PackageManager.PERMISSION_GRANTED
        ) return
        val notification = NotificationCompat.Builder(applicationContext, CHANNEL_ID)
            .setSmallIcon(R.drawable.ic_launcher_foreground)
            .setContentTitle(title)
            .setContentText(text)
            .setAutoCancel(true)
            .build()
        applicationContext.getSystemService(NotificationManager::class.java)
            .notify(id, notification)
    }

    companion object {
        private const val CHANNEL_ID = "garage_reminders"
    }
}
