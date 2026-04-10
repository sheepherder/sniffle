package de.schaefer.sniffle.background

import android.app.PendingIntent
import android.content.Context
import android.content.Intent
import androidx.core.app.NotificationCompat
import androidx.core.app.NotificationManagerCompat
import de.schaefer.sniffle.App
import de.schaefer.sniffle.MainActivity
import de.schaefer.sniffle.R
import de.schaefer.sniffle.data.DeviceEntity
import de.schaefer.sniffle.data.Section

object NotificationHelper {

    private fun openAppIntent(context: Context, requestCode: Int, mac: String? = null): PendingIntent {
        val intent = Intent(context, MainActivity::class.java).apply {
            flags = Intent.FLAG_ACTIVITY_NEW_TASK or Intent.FLAG_ACTIVITY_SINGLE_TOP
            mac?.let { putExtra(MainActivity.EXTRA_NAVIGATE_TO_MAC, it) }
        }
        return PendingIntent.getActivity(
            context, requestCode, intent,
            PendingIntent.FLAG_UPDATE_CURRENT or PendingIntent.FLAG_IMMUTABLE
        )
    }

    fun notifyDevice(context: Context, device: DeviceEntity, values: String?) {
        val section = device.section
        if (section == Section.TRANSIENT) return

        val title = when (section) {
            Section.SENSOR -> "Neuer Sensor: ${device.displayName}"
            Section.DEVICE -> "${device.displayName} regelmäßig in der Nähe"
            Section.MYSTERY -> "Unbekanntes Gerät ${device.mac} regelmäßig in der Nähe"
            Section.TRANSIENT -> error("unreachable")
        }

        val body = when (section) {
            Section.SENSOR -> values ?: "${device.brand ?: ""} ${device.model ?: ""}".trim()
            Section.DEVICE -> buildList {
                device.brand?.let { add(it) }
                device.company?.let { if (it != device.brand) add(it) }
                device.appearance?.let { add(it) }
            }.joinToString(" • ").ifEmpty { device.mac }
            Section.MYSTERY -> "Seit 3+ Tagen gesehen, keine Identität"
            Section.TRANSIENT -> error("unreachable")
        }

        val notificationId = device.mac.hashCode() and Int.MAX_VALUE
        val notification = NotificationCompat.Builder(context, App.CHANNEL_DEVICES)
            .setSmallIcon(android.R.drawable.stat_sys_data_bluetooth)
            .setContentTitle(title)
            .setContentText(body)
            .setPriority(NotificationCompat.PRIORITY_DEFAULT)
            .setAutoCancel(true)
            .setContentIntent(openAppIntent(context, notificationId, device.mac))
            .build()

        try {
            NotificationManagerCompat.from(context).notify(notificationId, notification)
        } catch (_: SecurityException) {
            // POST_NOTIFICATIONS not granted
        }
    }

    fun notifyScanSummary(context: Context, summary: String) {
        val notification = NotificationCompat.Builder(context, App.CHANNEL_SUMMARY)
            .setSmallIcon(android.R.drawable.stat_sys_data_bluetooth)
            .setContentTitle("Scan abgeschlossen")
            .setContentText(summary)
            .setPriority(NotificationCompat.PRIORITY_LOW)
            .setAutoCancel(true)
            .setContentIntent(openAppIntent(context, 998))
            .build()

        try {
            NotificationManagerCompat.from(context).notify(999, notification)
        } catch (_: SecurityException) {}
    }

    fun serviceNotification(context: Context): android.app.Notification {
        return NotificationCompat.Builder(context, App.CHANNEL_SERVICE)
            .setSmallIcon(android.R.drawable.stat_sys_data_bluetooth)
            .setContentTitle("Sniffle")
            .setContentText("Scanne nach Geräten…")
            .setPriority(NotificationCompat.PRIORITY_MIN)
            .setOngoing(true)
            .setContentIntent(openAppIntent(context, 997))
            .build()
    }
}
