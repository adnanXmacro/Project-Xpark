package com.sparktube.app.util

import android.app.Activity
import android.app.NotificationChannel
import android.app.NotificationManager
import android.app.PendingIntent
import android.content.Context
import android.content.Intent
import android.os.Build
import androidx.core.app.NotificationCompat
import androidx.core.app.NotificationManagerCompat
import androidx.core.app.TaskStackBuilder
import com.sparktube.app.R
import com.sparktube.app.ui.MainActivity
import com.sparktube.app.ui.menu.SettingsPageActivity
import com.sparktube.app.ui.update.UpdatePrompt

/**
 * One-shot tip after this update: a dialog on first MainActivity open, plus
 * a notification that deep-links into Video settings.
 */
object OpenLinksHint {

    private const val CHANNEL_ID = "open_links_tips"
    private const val NOTIF_ID = 4301
    private const val NOTIF_COLOR = 0xFFF03E3E.toInt()

    fun maybeShow(activity: Activity) {
        if (AppPrefs.openYoutubeLinksHintShown) return
        AppPrefs.openYoutubeLinksHintShown = true
        UpdatePrompt.showNotice(
            activity,
            activity.getString(R.string.open_links_hint_title),
            activity.getString(R.string.open_links_hint_message)
        )
        postNotification(activity)
    }

    /** Re-posts the tip after the user grants notification permission. */
    fun retryNotification(context: Context) {
        if (!AppPrefs.openYoutubeLinksHintShown) return
        postNotification(context)
    }

    private fun postNotification(context: Context) {
        ensureChannel(context)
        val tap = TaskStackBuilder.create(context)
            .addNextIntent(Intent(context, MainActivity::class.java))
            .addNextIntent(
                Intent(context, SettingsPageActivity::class.java)
                    .putExtra(SettingsPageActivity.EXTRA_TYPE, SettingsPageActivity.TYPE_VIDEO)
            )
            .getPendingIntent(
                11,
                PendingIntent.FLAG_IMMUTABLE or PendingIntent.FLAG_UPDATE_CURRENT
            )
        val notification = NotificationCompat.Builder(context, CHANNEL_ID)
            .setSmallIcon(R.drawable.ic_notif_update)
            .setColor(NOTIF_COLOR)
            .setContentTitle(context.getString(R.string.open_links_notif_title))
            .setContentText(context.getString(R.string.open_links_notif_text))
            .setStyle(
                NotificationCompat.BigTextStyle()
                    .bigText(context.getString(R.string.open_links_notif_text))
            )
            .setAutoCancel(true)
            .setContentIntent(tap)
            .build()
        runCatching {
            NotificationManagerCompat.from(context).notify(NOTIF_ID, notification)
        }
    }

    private fun ensureChannel(context: Context) {
        if (Build.VERSION.SDK_INT < Build.VERSION_CODES.O) return
        val nm = context.getSystemService(NotificationManager::class.java) ?: return
        if (nm.getNotificationChannel(CHANNEL_ID) != null) return
        nm.createNotificationChannel(
            NotificationChannel(
                CHANNEL_ID,
                context.getString(R.string.open_links_notif_channel),
                NotificationManager.IMPORTANCE_DEFAULT
            )
        )
    }
}
