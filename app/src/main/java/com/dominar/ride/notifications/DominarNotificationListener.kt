package com.dominar.ride.notifications

import android.app.Notification
import android.provider.Telephony
import android.service.notification.NotificationListenerService
import android.service.notification.StatusBarNotification
import com.dominar.ride.ble.BleManagerHolder
import com.dominar.ride.protocol.DominarProtocol
import java.util.UUID

/**
 * Mirrors ALL incoming notifications to the cluster.
 * SMS and WhatsApp get their dedicated cluster icon; every other app is
 * shown as "AppName: title" (Alert text is ASCII, max 32 bytes).
 */
class DominarNotificationListener : NotificationListenerService() {

    private val recentKeys = LinkedHashMap<String, Long>()

    override fun onNotificationPosted(sbn: StatusBarNotification) {
        if (sbn.packageName == packageName) return
        if (sbn.isOngoing) return
        val n = sbn.notification ?: return
        if (n.flags and Notification.FLAG_GROUP_SUMMARY != 0) return

        val extras = n.extras
        val title = extras.getCharSequence(Notification.EXTRA_TITLE)?.toString().orEmpty()
        val text = extras.getCharSequence(Notification.EXTRA_TEXT)?.toString().orEmpty()
        if (title.isBlank() && text.isBlank()) return

        // Debounce duplicates (grouped/updated notifications repost quickly)
        val key = "${sbn.packageName}|$title|$text"
        val now = System.currentTimeMillis()
        recentKeys.entries.removeAll { now - it.value > 5_000 }
        if (recentKeys.containsKey(key)) return
        recentKeys[key] = now

        val appLabel = try {
            packageManager.getApplicationLabel(
                packageManager.getApplicationInfo(sbn.packageName, 0)
            ).toString()
        } catch (e: Exception) {
            sbn.packageName.substringAfterLast('.')
        }

        val defaultSmsApp = Telephony.Sms.getDefaultSmsPackage(this)
        val (type, content) = when (sbn.packageName) {
            "com.whatsapp", "com.whatsapp.w4b" ->
                DominarProtocol.AlertType.WHATSAPP to "$title: $text"
            defaultSmsApp ->
                DominarProtocol.AlertType.SMS to "$title: $text"
            else ->
                DominarProtocol.AlertType.SMS to "$appLabel: $title"
        }

        BleManagerHolder.get(this).send(
            UUID.fromString(DominarProtocol.UUID_ALERT),
            DominarProtocol.buildAlertPacket(type = type, content = content)
        )
    }
}
