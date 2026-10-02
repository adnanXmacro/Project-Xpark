package com.sparktube.app.update

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
import android.os.SystemClock
import android.widget.Toast
import androidx.core.app.NotificationCompat
import androidx.core.content.ContextCompat
import com.sparktube.app.BuildConfig
import com.sparktube.app.R
import com.sparktube.app.ui.MainActivity
import com.sparktube.app.util.UpdateChecker
import okhttp3.OkHttpClient
import okhttp3.Request
import java.io.File
import java.io.FileOutputStream
import java.io.IOException
import java.util.concurrent.TimeUnit
import java.util.concurrent.atomic.AtomicBoolean

class UpdateDownloadService : Service() {

    private val cancelled = AtomicBoolean(false)
    private var worker: Thread? = null
    @Volatile private var call: okhttp3.Call? = null

    override fun onBind(intent: Intent?): IBinder? = null

    override fun onStartCommand(intent: Intent?, flags: Int, startId: Int): Int {
        when (intent?.action) {
            ACTION_CANCEL -> {
                cancelled.set(true)
                call?.cancel()
                finishCleanup(null)
                return START_NOT_STICKY
            }
            ACTION_START -> {
                val url = intent.getStringExtra(EXTRA_URL)
                val tag = intent.getStringExtra(EXTRA_TAG).orEmpty()
                if (url.isNullOrBlank()) {
                    stopSelf()
                    return START_NOT_STICKY
                }
                if (running && worker?.isAlive == true) {
                    return START_NOT_STICKY
                }
                running = true
                ensureChannel()
                startFg(progressNotification(tag.removePrefix("v"), 0, indeterminate = true))
                cancelled.set(false)
                worker = Thread({ download(url, tag) }, "update-apk").also { it.start() }
            }
            else -> stopSelf()
        }
        return START_NOT_STICKY
    }

    override fun onDestroy() {
        cancelled.set(true)
        call?.cancel()
        running = false
        super.onDestroy()
    }

    private fun download(url: String, tag: String) {
        val version = tag.removePrefix("v")
        val dir = UpdateInstaller.updatesDir()
        val dest = File(dir, "OpenTube-$version.apk")
        val part = File(dir, "OpenTube-$version.apk.part")
        try {
            if (part.exists()) part.delete()
            val request = Request.Builder()
                .url(url)
                .header("Accept", "application/octet-stream")
                .header("User-Agent", "OpenTube/${BuildConfig.VERSION_NAME}")
                .get()
                .build()
            val nextCall = client.newCall(request)
            call = nextCall
            nextCall.execute().use { resp ->
                if (!resp.isSuccessful) throw IOException("HTTP ${resp.code}")
                val body = resp.body ?: throw IOException("empty")
                val total = body.contentLength()
                body.byteStream().use { input ->
                    FileOutputStream(part).use { output ->
                        val buf = ByteArray(64 * 1024)
                        var read = 0L
                        var lastNotify = 0L
                        while (true) {
                            if (cancelled.get()) return@use
                            val n = input.read(buf)
                            if (n < 0) break
                            output.write(buf, 0, n)
                            read += n
                            val now = SystemClock.elapsedRealtime()
                            if (now - lastNotify >= 400L) {
                                lastNotify = now
                                val percent = if (total > 0) ((read * 100) / total).toInt().coerceIn(0, 99) else 0
                                notifyProgress(version, percent, indeterminate = total <= 0)
                            }
                        }
                    }
                }
            }
            if (cancelled.get()) {
                part.delete()
                finishCleanup(null)
                return
            }
            if (dest.exists()) dest.delete()
            if (!part.renameTo(dest)) {
                part.copyTo(dest, overwrite = true)
                part.delete()
            }
            notifyManager.notify(NOTIF_ID, readyNotification(version))
            running = false
            stopForeground(STOP_FOREGROUND_DETACH)
            UpdateInstaller.onReady(dest)
            if (UpdateInstaller.isInForeground) {
                notifyManager.cancel(NOTIF_ID)
            }
            stopSelf()
        } catch (e: Exception) {
            part.delete()
            if (cancelled.get()) {
                finishCleanup(null)
            } else {
                notifyManager.notify(NOTIF_ID, failedNotification())
                running = false
                stopForeground(STOP_FOREGROUND_DETACH)
                stopSelf()
            }
        }
    }

    private fun finishCleanup(file: File?) {
        file?.let { UpdateInstaller.clearPending(it) }
        running = false
        stopForeground(STOP_FOREGROUND_REMOVE)
        notifyManager.cancel(NOTIF_ID)
        stopSelf()
    }

    private fun notifyProgress(version: String, percent: Int, indeterminate: Boolean) {
        notifyManager.notify(NOTIF_ID, progressNotification(version, percent, indeterminate))
    }

    private fun startFg(notification: Notification) {
        if (Build.VERSION.SDK_INT >= 34) {
            startForeground(
                NOTIF_ID,
                notification,
                ServiceInfo.FOREGROUND_SERVICE_TYPE_DATA_SYNC
            )
        } else {
            startForeground(NOTIF_ID, notification)
        }
    }

    private fun progressNotification(version: String, percent: Int, indeterminate: Boolean): Notification {
        val text = if (indeterminate) {
            getString(R.string.update_downloading, version)
        } else {
            getString(R.string.update_download_progress, version, percent)
        }
        return NotificationCompat.Builder(this, CHANNEL_ID)
            .setSmallIcon(android.R.drawable.stat_sys_download)
            .setContentTitle(getString(R.string.update_available_title))
            .setContentText(text)
            .setOngoing(true)
            .setOnlyAlertOnce(true)
            .setProgress(100, percent, indeterminate)
            .addAction(
                0,
                getString(R.string.update_download_cancel),
                servicePending(ACTION_CANCEL, 2)
            )
            .build()
    }

    private fun readyNotification(version: String): Notification {
        val open = Intent(this, MainActivity::class.java)
            .addFlags(Intent.FLAG_ACTIVITY_NEW_TASK or Intent.FLAG_ACTIVITY_SINGLE_TOP)
            .putExtra(MainActivity.EXTRA_INSTALL_UPDATE, true)
        val tap = PendingIntent.getActivity(
            this,
            3,
            open,
            PendingIntent.FLAG_IMMUTABLE or PendingIntent.FLAG_UPDATE_CURRENT
        )
        return NotificationCompat.Builder(this, CHANNEL_ID)
            .setSmallIcon(android.R.drawable.stat_sys_download_done)
            .setContentTitle(getString(R.string.update_ready_to_install, version))
            .setContentText(getString(R.string.update_tap_to_install))
            .setAutoCancel(true)
            .setContentIntent(tap)
            .build()
    }

    private fun failedNotification(): Notification =
        NotificationCompat.Builder(this, CHANNEL_ID)
            .setSmallIcon(android.R.drawable.stat_notify_error)
            .setContentTitle(getString(R.string.update_download_failed))
            .setAutoCancel(true)
            .build()

    private fun servicePending(action: String, requestCode: Int): PendingIntent {
        val intent = Intent(this, UpdateDownloadService::class.java).setAction(action)
        return PendingIntent.getService(
            this,
            requestCode,
            intent,
            PendingIntent.FLAG_IMMUTABLE or PendingIntent.FLAG_UPDATE_CURRENT
        )
    }

    private fun ensureChannel() {
        if (Build.VERSION.SDK_INT < Build.VERSION_CODES.O) return
        val nm = notifyManager
        if (nm.getNotificationChannel(CHANNEL_ID) != null) return
        nm.createNotificationChannel(
            NotificationChannel(
                CHANNEL_ID,
                getString(R.string.update_notification_channel),
                NotificationManager.IMPORTANCE_LOW
            )
        )
    }

    private val notifyManager: NotificationManager
        get() = getSystemService(NotificationManager::class.java)

    companion object {
        const val ACTION_START = "com.sparktube.app.update.START"
        const val ACTION_CANCEL = "com.sparktube.app.update.CANCEL"
        const val EXTRA_URL = "url"
        const val EXTRA_TAG = "tag"
        const val CHANNEL_ID = "app_updates"
        const val NOTIF_ID = 4201

        @Volatile
        var running = false
            private set

        private val client: OkHttpClient = OkHttpClient.Builder()
            .connectTimeout(30, TimeUnit.SECONDS)
            .readTimeout(120, TimeUnit.SECONDS)
            .followRedirects(true)
            .followSslRedirects(true)
            .build()

        fun start(context: Context, release: UpdateChecker.Release) {
            val url = release.apkUrl
            if (url.isNullOrBlank()) {
                runCatching {
                    context.startActivity(
                        Intent(Intent.ACTION_VIEW, android.net.Uri.parse(release.htmlUrl))
                    )
                }
                return
            }
            if (running) {
                Toast.makeText(context, R.string.update_download_already, Toast.LENGTH_SHORT).show()
                return
            }
            val intent = Intent(context, UpdateDownloadService::class.java)
                .setAction(ACTION_START)
                .putExtra(EXTRA_URL, url)
                .putExtra(EXTRA_TAG, release.tag)
            running = true
            try {
                ContextCompat.startForegroundService(context, intent)
            } catch (e: Exception) {
                running = false
                Toast.makeText(context, R.string.update_download_failed, Toast.LENGTH_SHORT).show()
            }
        }
    }
}
