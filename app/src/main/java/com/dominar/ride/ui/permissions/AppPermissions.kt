package com.dominar.ride.ui.permissions

import android.Manifest
import android.content.Context
import android.content.Intent
import android.content.pm.PackageManager
import android.net.Uri
import android.os.Build
import android.os.PowerManager
import android.provider.Settings
import androidx.core.app.NotificationManagerCompat
import androidx.core.content.ContextCompat

/** Special (non-runtime) access toggles that live in Android settings. */
enum class SpecialAccess { NOTIFICATION_LISTENER, BATTERY_OPTIMIZATION }

/**
 * One user-facing permission "step": what we ask for, why, and whether the
 * app is broken without it. Optional steps are never requested automatically.
 */
data class PermissionStep(
    val key: String,
    val emoji: String,
    val title: String,
    val description: String,
    val permissions: List<String> = emptyList(),
    val required: Boolean = false,
    /** When true, the step counts as granted if ANY of [permissions] is granted. */
    val anyOf: Boolean = false,
    val special: SpecialAccess? = null
)

/** Every permission the app can use, in the order shown during onboarding. */
fun permissionSteps(): List<PermissionStep> = buildList {
    if (Build.VERSION.SDK_INT >= 33) {
        add(
            PermissionStep(
                key = "notifications",
                emoji = "\uD83D\uDD14",
                title = "Notifications",
                description = "Shows the cluster connection status and reminds you " +
                    "about due services and expiring documents.",
                permissions = listOf(Manifest.permission.POST_NOTIFICATIONS),
                required = true
            )
        )
    }
    if (Build.VERSION.SDK_INT >= 31) {
        add(
            PermissionStep(
                key = "bluetooth",
                emoji = "\uD83D\uDCE1",
                title = "Nearby devices",
                description = "Finds your bike's cluster and keeps the Bluetooth " +
                    "connection alive while you ride.",
                permissions = listOf(
                    Manifest.permission.BLUETOOTH_SCAN,
                    Manifest.permission.BLUETOOTH_CONNECT
                ),
                required = true
            )
        )
    }
    add(
        PermissionStep(
            key = "location",
            emoji = "\uD83D\uDCCD",
            title = "Location",
            description = "Powers the map and navigation, GPS speed, 0-100 timing " +
                "and your saved parking spot. Never shared anywhere.",
            permissions = listOf(
                Manifest.permission.ACCESS_FINE_LOCATION,
                Manifest.permission.ACCESS_COARSE_LOCATION
            ),
            required = true,
            anyOf = true
        )
    )
    add(
        PermissionStep(
            key = "calls",
            emoji = "\uD83D\uDCDE",
            title = "Calls",
            description = "Shows who's calling on the cluster display so you don't " +
                "reach for your phone. Skip it if you don't want call mirroring.",
            permissions = listOf(
                Manifest.permission.READ_PHONE_STATE,
                Manifest.permission.READ_CALL_LOG,
                Manifest.permission.READ_CONTACTS,
                Manifest.permission.ANSWER_PHONE_CALLS
            )
        )
    )
    add(
        PermissionStep(
            key = "listener",
            emoji = "\uD83D\uDCAC",
            title = "Notification access",
            description = "Mirrors app notifications on the cluster. Opens an " +
                "Android settings page where you flip one switch.",
            special = SpecialAccess.NOTIFICATION_LISTENER
        )
    )
    add(
        PermissionStep(
            key = "battery",
            emoji = "\uD83D\uDD0B",
            title = "Run in background",
            description = "Exempts the app from battery optimization so the cluster " +
                "stays connected with the screen off.",
            special = SpecialAccess.BATTERY_OPTIMIZATION
        )
    )
}

fun isGranted(context: Context, permission: String): Boolean =
    ContextCompat.checkSelfPermission(context, permission) ==
        PackageManager.PERMISSION_GRANTED

fun isStepGranted(context: Context, step: PermissionStep): Boolean = when (step.special) {
    SpecialAccess.NOTIFICATION_LISTENER ->
        NotificationManagerCompat.getEnabledListenerPackages(context)
            .contains(context.packageName)
    SpecialAccess.BATTERY_OPTIMIZATION ->
        (context.getSystemService(Context.POWER_SERVICE) as PowerManager)
            .isIgnoringBatteryOptimizations(context.packageName)
    null ->
        if (step.anyOf) step.permissions.any { isGranted(context, it) }
        else step.permissions.all { isGranted(context, it) }
}

/** Intent that opens the Android settings page for a [SpecialAccess] toggle. */
fun specialAccessIntent(context: Context, special: SpecialAccess): Intent = when (special) {
    SpecialAccess.NOTIFICATION_LISTENER ->
        Intent(Settings.ACTION_NOTIFICATION_LISTENER_SETTINGS)
    SpecialAccess.BATTERY_OPTIMIZATION ->
        Intent(
            Settings.ACTION_REQUEST_IGNORE_BATTERY_OPTIMIZATIONS,
            Uri.parse("package:${context.packageName}")
        )
}

/** Deep-links to this app's page in Android settings (for "Don't ask again"). */
fun openAppSettings(context: Context) {
    runCatching {
        context.startActivity(
            Intent(
                Settings.ACTION_APPLICATION_DETAILS_SETTINGS,
                Uri.parse("package:${context.packageName}")
            )
        )
    }
}
