package de.schaefer.sniffle

import android.app.Application
import android.app.NotificationChannel
import android.app.NotificationManager
import de.schaefer.sniffle.data.AppDatabase

class App : Application() {

    val database: AppDatabase by lazy { AppDatabase.create(this) }

    override fun onCreate() {
        super.onCreate()
        createNotificationChannels()
    }

    private fun createNotificationChannels() {
        val nm = getSystemService(NotificationManager::class.java)

        nm.createNotificationChannel(
            NotificationChannel(
                CHANNEL_DEVICES,
                getString(R.string.notification_channel_devices),
                NotificationManager.IMPORTANCE_DEFAULT
            ).apply {
                description = "Benachrichtigungen bei neuen Geräte-Funden"
            }
        )

        nm.createNotificationChannel(
            NotificationChannel(
                CHANNEL_SUMMARY,
                getString(R.string.notification_channel_summary),
                NotificationManager.IMPORTANCE_LOW
            ).apply {
                description = "Stille Zusammenfassung nach Hintergrund-Scans"
            }
        )

        nm.createNotificationChannel(
            NotificationChannel(
                CHANNEL_SERVICE,
                getString(R.string.notification_channel_service),
                NotificationManager.IMPORTANCE_MIN
            ).apply {
                description = "Anzeige während Hintergrund-Scan läuft"
            }
        )
    }

    companion object {
        const val CHANNEL_DEVICES = "devices"
        const val CHANNEL_SUMMARY = "summary"
        const val CHANNEL_SERVICE = "service"
    }
}
