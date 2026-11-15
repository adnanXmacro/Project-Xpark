package com.sparktube.app.download

import android.app.Notification
import android.app.NotificationChannel
import android.app.NotificationManager
import android.app.PendingIntent
import android.app.Service
import android.content.Context
import android.content.Intent
import android.content.pm.ServiceInfo
import android.os.Build
import android.os.IBinder
import android.util.Log
import androidx.core.app.NotificationCompat
import androidx.core.content.ContextCompat
import com.sparktube.app.R
import com.sparktube.app.data.DownloadRecord
import com.sparktube.app.ui.MainActivity
import com.sparktube.app.util.Formatters
import kotlinx.coroutines.CoroutineScope
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.Job
import kotlinx.coroutines.SupervisorJob
import kotlinx.coroutines.cancel
import kotlinx.coroutines.delay
import kotlinx.coroutines.isActive
import kotlinx.coroutines.launch

/**
 * Foreground service that keeps downloads alive when the app is backgrounded
 * or swiped away from recent tasks, and surfaces live progress + completion
 * notifications in the system notification shade.
 */
class DownloadService : Service() {

    private val serviceScope = CoroutineScope(SupervisorJob() + Dispatchers.Main)
    private var progressJob: Job? = null

    override fun onBind(intent: Intent?): IBinder? = null

    override fun onStartCommand(intent: Intent?, flags: Int, startId: Int): Int {
        ensureChannels()

        when (intent?.action) {
            ACTION_CANCEL_ALL -> {
                DownloadCenter.cancelActive(this)
                stopForegroundAndFinish()
                return START_NOT_STICKY
            }
            ACTION_CANCEL_ID -> {
                val videoId = intent.getStringExtra(EXTRA_VIDEO_ID)
                if (!videoId.isNullOrBlank()) {
                    DownloadCenter.cancelByVideoId(this, videoId)
                }
            }
        }

        if (!isRunning) {
            isRunning = true
            startInForeground()
        }
        startProgressLoop()

        return START_STICKY
    }

    private fun startInForeground() {
        val active = DownloadCenter.activeRecords(this)
        val progress = DownloadCenter.totalActiveProgress(this)
        val notif = buildProgressNotification(active, progress)
        if (Build.VERSION.SDK_INT >= 34) {
            startForeground(
                NOTIF_ID_PROGRESS,
                notif,
                ServiceInfo.FOREGROUND_SERVICE_TYPE_DATA_SYNC
            )
        } else {
            startForeground(NOTIF_ID_PROGRESS, notif)
        }
    }

    private fun startProgressLoop() {
        progressJob?.cancel()
        progressJob = serviceScope.launch {
            while (isActive) {
                DownloadCenter.refreshStatuses(this@DownloadService)
                val active = DownloadCenter.activeRecords(this@DownloadService)
                if (active.isEmpty()) {
                    stopForegroundAndFinish()
                    break
                }
                val progress = DownloadCenter.totalActiveProgress(this@DownloadService)
                val notif = buildProgressNotification(active, progress)
                notifyManager.notify(NOTIF_ID_PROGRESS, notif)
                delay(500L)
            }
        }
    }

    private fun stopForegroundAndFinish() {
        isRunning = false
        progressJob?.cancel()
        stopForeground(STOP_FOREGROUND_REMOVE)
        notifyManager.cancel(NOTIF_ID_PROGRESS)
        stopSelf()
    }

    override fun onTaskRemoved(rootIntent: Intent?) {
        // Keeps downloading in the background if there are still active downloads
        if (!DownloadCenter.hasAnyActiveDownload(this)) {
            stopForegroundAndFinish()
        }
    }

    override fun onDestroy() {
        isRunning = false
        serviceScope.cancel()
        super.onDestroy()
    }

    private fun buildProgressNotification(
        active: List<DownloadRecord>,
        progress: DownloadCenter.ProgressInfo
    ): Notification {
        val title = when {
            active.isEmpty() -> getString(R.string.download_status_running)
            active.size == 1 -> active[0].title
            else -> getString(R.string.download_multiple_fmt, active[0].title, active.size - 1)
        }

        val text = when {
            progress.percent >= 0 && progress.totalBytes > 0L -> {
                val done = Formatters.formatFileSize(progress.downloadedBytes)
                val total = Formatters.formatFileSize(progress.totalBytes)
                getString(R.string.download_progress_fmt, progress.percent, done, total)
            }
            progress.percent >= 0 -> "${progress.percent}%"
            else -> getString(R.string.download_status_running)
        }

        val openIntent = Intent(this, MainActivity::class.java).apply {
            action = Intent.ACTION_MAIN
            flags = Intent.FLAG_ACTIVITY_SINGLE_TOP or Intent.FLAG_ACTIVITY_CLEAR_TOP
            putExtra(MainActivity.EXTRA_OPEN_DOWNLOADS, true)
        }
        val openPending = PendingIntent.getActivity(
            this,
            101,
            openIntent,
            PendingIntent.FLAG_UPDATE_CURRENT or PendingIntent.FLAG_IMMUTABLE
        )

        val cancelIntent = Intent(this, DownloadService::class.java).apply {
            action = ACTION_CANCEL_ALL
        }
        val cancelPending = PendingIntent.getService(
            this,
            102,
            cancelIntent,
            PendingIntent.FLAG_UPDATE_CURRENT or PendingIntent.FLAG_IMMUTABLE
        )

        val isIndeterminate = progress.percent < 0
        val maxProgress = 100
        val currentProgress = if (isIndeterminate) 0 else progress.percent

        return NotificationCompat.Builder(this, CHANNEL_PROGRESS)
            .setSmallIcon(R.drawable.ic_download)
            .setColor(ContextCompat.getColor(this, R.color.spark_red))
            .setContentTitle(title)
            .setContentText(text)
            .setOngoing(true)
            .setOnlyAlertOnce(true)
            .setProgress(maxProgress, currentProgress, isIndeterminate)
            .setContentIntent(openPending)
            .addAction(
                0,
                getString(R.string.download_notif_cancel),
                cancelPending
            )
            .build()
    }

    private fun ensureChannels() {
        if (Build.VERSION.SDK_INT < Build.VERSION_CODES.O) return
        val nm = notifyManager
        if (nm.getNotificationChannel(CHANNEL_PROGRESS) == null) {
            nm.createNotificationChannel(
                NotificationChannel(
                    CHANNEL_PROGRESS,
                    getString(R.string.download_notif_channel_progress),
                    NotificationManager.IMPORTANCE_LOW
                )
            )
        }
        if (nm.getNotificationChannel(CHANNEL_COMPLETE) == null) {
            nm.createNotificationChannel(
                NotificationChannel(
                    CHANNEL_COMPLETE,
                    getString(R.string.download_notif_channel_complete),
                    NotificationManager.IMPORTANCE_DEFAULT
                )
            )
        }
    }

    private val notifyManager: NotificationManager
        get() = getSystemService(NotificationManager::class.java)

    companion object {
        const val CHANNEL_PROGRESS = "downloads_progress"
        const val CHANNEL_COMPLETE = "downloads_complete"
        const val NOTIF_ID_PROGRESS = 5101

        const val ACTION_START = "com.sparktube.app.download.START"
        const val ACTION_CANCEL_ALL = "com.sparktube.app.download.CANCEL_ALL"
        const val ACTION_CANCEL_ID = "com.sparktube.app.download.CANCEL_ID"
        const val EXTRA_VIDEO_ID = "extra_video_id"

        @Volatile
        var isRunning = false
            private set

        fun start(context: Context) {
            val intent = Intent(context, DownloadService::class.java).setAction(ACTION_START)
            try {
                ContextCompat.startForegroundService(context, intent)
            } catch (e: Exception) {
                Log.w("DownloadService", "Could not start DownloadService", e)
            }
        }

        fun notifyDownloadCompleted(context: Context, record: DownloadRecord) {
            val nm = context.getSystemService(NotificationManager::class.java) ?: return
            ensureStaticChannels(context, nm)

            val openIntent = Intent(context, MainActivity::class.java).apply {
                action = Intent.ACTION_MAIN
                flags = Intent.FLAG_ACTIVITY_SINGLE_TOP or Intent.FLAG_ACTIVITY_CLEAR_TOP
                putExtra(MainActivity.EXTRA_OPEN_DOWNLOADS, true)
            }
            val openPending = PendingIntent.getActivity(
                context,
                (record.videoId.hashCode() and 0xFFFF) + 200,
                openIntent,
                PendingIntent.FLAG_UPDATE_CURRENT or PendingIntent.FLAG_IMMUTABLE
            )

            val notif = NotificationCompat.Builder(context, CHANNEL_COMPLETE)
                .setSmallIcon(R.drawable.ic_check)
                .setColor(ContextCompat.getColor(context, R.color.music_green))
                .setContentTitle(context.getString(R.string.download_notif_complete_title))
                .setContentText(record.title)
                .setSubText("${record.type} • ${record.quality}")
                .setAutoCancel(true)
                .setContentIntent(openPending)
                .build()

            nm.notify(record.videoId.hashCode(), notif)
        }

        fun notifyDownloadFailed(context: Context, record: DownloadRecord) {
            val nm = context.getSystemService(NotificationManager::class.java) ?: return
            ensureStaticChannels(context, nm)

            val notif = NotificationCompat.Builder(context, CHANNEL_COMPLETE)
                .setSmallIcon(android.R.drawable.stat_notify_error)
                .setColor(ContextCompat.getColor(context, R.color.spark_red))
                .setContentTitle(context.getString(R.string.download_notif_failed_title))
                .setContentText(record.title)
                .setAutoCancel(true)
                .build()

            nm.notify(record.videoId.hashCode(), notif)
        }

        private fun ensureStaticChannels(context: Context, nm: NotificationManager) {
            if (Build.VERSION.SDK_INT < Build.VERSION_CODES.O) return
            if (nm.getNotificationChannel(CHANNEL_COMPLETE) == null) {
                nm.createNotificationChannel(
                    NotificationChannel(
                        CHANNEL_COMPLETE,
                        context.getString(R.string.download_notif_channel_complete),
                        NotificationManager.IMPORTANCE_DEFAULT
                    )
                )
            }
        }
    }
}
