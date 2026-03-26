package ru.tafinceva.health

import android.app.NotificationChannel
import android.app.NotificationManager
import android.app.Service
import android.content.Intent
import android.os.IBinder
import androidx.core.app.NotificationCompat

/**
 * Declared in AndroidManifest with foregroundServiceType="dataSync".
 * WorkManager binds to this service internally when SyncWorker calls setForeground().
 * No additional logic is required here; WorkManager handles the lifecycle.
 */
class SyncForegroundService : Service() {

    override fun onBind(intent: Intent?): IBinder? = null

    override fun onCreate() {
        super.onCreate()
        createChannel()
        val notification = NotificationCompat.Builder(this, SyncWorker.NOTIF_CHANNEL_ID)
            .setContentTitle("Health Extractor")
            .setContentText("Синхронизация…")
            .setSmallIcon(android.R.drawable.ic_popup_sync)
            .build()
        startForeground(SyncWorker.NOTIF_ID, notification)
    }

    private fun createChannel() {
        val manager = getSystemService(NOTIFICATION_SERVICE) as NotificationManager
        if (manager.getNotificationChannel(SyncWorker.NOTIF_CHANNEL_ID) == null) {
            manager.createNotificationChannel(
                NotificationChannel(
                    SyncWorker.NOTIF_CHANNEL_ID,
                    "Health Sync",
                    NotificationManager.IMPORTANCE_LOW
                )
            )
        }
    }
}
