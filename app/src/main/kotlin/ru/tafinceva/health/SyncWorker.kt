package ru.tafinceva.health

import android.app.NotificationChannel
import android.app.NotificationManager
import android.content.Context
import android.content.pm.ServiceInfo
import android.os.Build
import androidx.core.app.NotificationCompat
import androidx.work.CoroutineWorker
import androidx.work.ForegroundInfo
import androidx.work.WorkerParameters
import java.time.LocalDate

/**
 * WorkManager CoroutineWorker that runs as a foreground service (dataSync type).
 *
 * Schedule: every 6 hours via [SyncScheduler].
 * What it does:
 *   1. Refresh Huawei access token if needed.
 *   2. Fetch today's steps, heart rate and sleep.
 *   3. Upload JSON to Google Drive.
 *   4. Record last-sync timestamp in TokenManager.
 */
class SyncWorker(
    appContext: Context,
    params: WorkerParameters
) : CoroutineWorker(appContext, params) {

    companion object {
        const val WORK_NAME        = "huawei_health_sync"
        const val NOTIF_CHANNEL_ID = "health_sync_channel"
        const val NOTIF_ID         = 1001
    }

    private val tokenManager      = TokenManager(appContext)
    private val huaweiRepository  = HuaweiRepository(tokenManager)
    private val driveRepository   = GoogleDriveRepository(tokenManager)

    override suspend fun doWork(): Result {
        // Promote to foreground service immediately
        setForeground(buildForegroundInfo("Синхронизация данных здоровья…"))

        return try {
            if (!tokenManager.isConfigured) {
                return Result.failure()
            }

            // 1. Fetch
            val healthData = huaweiRepository.fetchHealthData(LocalDate.now())

            // 2. Upload
            driveRepository.uploadHealthData(healthData)

            // 3. Record success
            tokenManager.lastSyncTime = System.currentTimeMillis()

            Result.success()
        } catch (e: Exception) {
            e.printStackTrace()
            // Retry later; WorkManager will back off automatically
            Result.retry()
        }
    }

    // ── Notification / ForegroundInfo ─────────────────────────

    private fun buildForegroundInfo(message: String): ForegroundInfo {
        ensureNotificationChannel()

        val notification = NotificationCompat.Builder(applicationContext, NOTIF_CHANNEL_ID)
            .setContentTitle("Health Extractor")
            .setContentText(message)
            .setSmallIcon(android.R.drawable.ic_popup_sync)
            .setOngoing(true)
            .build()

        return if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.Q) {
            ForegroundInfo(NOTIF_ID, notification, ServiceInfo.FOREGROUND_SERVICE_TYPE_DATA_SYNC)
        } else {
            ForegroundInfo(NOTIF_ID, notification)
        }
    }

    private fun ensureNotificationChannel() {
        val manager = applicationContext
            .getSystemService(Context.NOTIFICATION_SERVICE) as NotificationManager
        if (manager.getNotificationChannel(NOTIF_CHANNEL_ID) == null) {
            val channel = NotificationChannel(
                NOTIF_CHANNEL_ID,
                "Health Sync",
                NotificationManager.IMPORTANCE_LOW
            ).apply { description = "Background health data synchronisation" }
            manager.createNotificationChannel(channel)
        }
    }
}
