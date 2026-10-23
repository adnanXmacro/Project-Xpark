package com.sparktube.app.ui.player

import android.content.Intent
import android.os.Bundle
import android.widget.Toast
import androidx.appcompat.app.AppCompatActivity
import com.sparktube.app.R
import com.sparktube.app.util.YoutubeLinks

/**
 * Trampoline for ACTION_VIEW / ACTION_SEND YouTube video URLs.
 *
 * Android (not this app) decides whether we receive the tap: the user must
 * enable Open Tube's supported links in system settings, and typically turn
 * the same off for the official YouTube app. See Morphe troubleshooting #33.
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
}
