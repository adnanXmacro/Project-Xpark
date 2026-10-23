package com.sparktube.app.ui.player

import android.content.ComponentName
import android.content.Context
import android.content.Intent
import android.content.pm.PackageManager
import android.os.Bundle
import android.widget.Toast
import androidx.appcompat.app.AppCompatActivity
import com.sparktube.app.R
import com.sparktube.app.util.AppPrefs
import com.sparktube.app.util.YoutubeLinks

/**
 * Thin trampoline for ACTION_VIEW / ACTION_SEND YouTube video URLs.
 *
 * The intent-filters stay registered so Android can list Open Tube, but
 * playback only starts when [AppPrefs.openYoutubeLinks] is on. Otherwise
 * we bounce the tap to the next matching app so we never steal links.
 */
class YoutubeLinkActivity : AppCompatActivity() {

    override fun onCreate(savedInstanceState: Bundle?) {
        super.onCreate(savedInstanceState)
        handle(intent)
    }

    override fun onNewIntent(intent: Intent) {
        super.onNewIntent(intent)
        setIntent(intent)
        handle(intent)
    }

    private fun handle(intent: Intent?) {
        val url = extractUrl(intent)
        if (url == null) {
            if (intent?.action != Intent.ACTION_SEND) {
                Toast.makeText(this, R.string.error_not_youtube_video, Toast.LENGTH_SHORT).show()
            }
            finish()
            return
        }
        if (!AppPrefs.openYoutubeLinks) {
            handOff(intent)
            finish()
            return
        }
        PlayerActivity.startFromUrl(this, url)
        finish()
    }

    private fun extractUrl(intent: Intent?): String? {
        if (intent == null) return null
        val fromView = YoutubeLinks.videoUrlFrom(intent.data?.toString())
        if (fromView != null) return fromView
        if (intent.action == Intent.ACTION_SEND) {
            return YoutubeLinks.firstVideoUrlIn(
                intent.getStringExtra(Intent.EXTRA_TEXT)
                    ?: intent.getStringExtra(Intent.EXTRA_SUBJECT)
            )
        }
        return null
    }

    /**
     * Toggle is off: re-send the original VIEW intent without Open Tube in
     * the candidate set so the tap still opens YouTube / the browser.
     */
    private fun handOff(original: Intent?) {
        val data = original?.data ?: return
        if (original.action != Intent.ACTION_VIEW) return
        val next = Intent(Intent.ACTION_VIEW, data).apply {
            addCategory(Intent.CATEGORY_BROWSABLE)
            flags = Intent.FLAG_ACTIVITY_NEW_TASK
            setPackage(null)
        }
        val ours = packageName
        val chosen = packageManager.queryIntentActivities(next, 0)
            .map { it.activityInfo.packageName }
            .firstOrNull { it != ours }
        if (chosen != null) {
            next.setPackage(chosen)
            runCatching { startActivity(next) }
        }
    }

    companion object {
        fun syncEnabled(context: Context) {
            val state = if (AppPrefs.openYoutubeLinks) {
                PackageManager.COMPONENT_ENABLED_STATE_ENABLED
            } else {
                PackageManager.COMPONENT_ENABLED_STATE_DISABLED
            }
            context.packageManager.setComponentEnabledSetting(
                ComponentName(context, YoutubeLinkActivity::class.java),
                state,
                PackageManager.DONT_KILL_APP
            )
        }
    }
}
