package ru.tafinceva.health

import android.app.Application
import android.app.NotificationChannel
import android.app.NotificationManager

class HealthApplication : Application() {

    override fun onCreate() {
        super.onCreate()
        createNotificationChannel()
    }

    private fun createNotificationChannel() {
        val manager = getSystemService(NOTIFICATION_SERVICE) as NotificationManager
        if (manager.getNotificationChannel(SyncWorker.NOTIF_CHANNEL_ID) == null) {
            val channel = NotificationChannel(
                SyncWorker.NOTIF_CHANNEL_ID,
                "Health Sync",
                NotificationManager.IMPORTANCE_LOW
            ).apply {
                description = "Background health data synchronisation"
            }
            manager.createNotificationChannel(channel)
        }
    }
}
