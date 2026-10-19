package com.sparktube.app.ui.player

import android.content.Context
import android.graphics.Typeface
import android.graphics.drawable.GradientDrawable
import android.util.TypedValue
import android.view.Gravity
import android.view.View
import android.view.ViewGroup
import android.widget.ImageView
import android.widget.LinearLayout
import android.widget.TextView
import android.widget.Toast
import androidx.core.view.isVisible
import com.google.android.material.bottomsheet.BottomSheetDialog
import com.sparktube.app.R
import com.sparktube.app.download.DownloadCenter
import com.sparktube.app.playback.AudioTrackGroup
import com.sparktube.app.playback.QueueEntry
import com.sparktube.app.playback.StreamCatalog
import com.sparktube.app.playback.effectiveHeight
import com.sparktube.app.util.Themes
import org.schabi.newpipe.extractor.stream.VideoStream

/**
 * Download picker with three collapsible groups, video first:
 * Download video (video + audio), Download video only, Download audio only.
 * Music playback only shows the audio group. Already-downloaded options are
 * marked and cannot be started again.
 */
class DownloadSheet(
    context: Context,
    private val entry: QueueEntry,
    private val catalog: StreamCatalog,
    private val audioOnly: Boolean = false
) : BottomSheetDialog(context) {

    private fun dp(value: Int): Int = TypedValue.applyDimension(
        TypedValue.COMPLEX_UNIT_DIP, value.toFloat(), context.resources.displayMetrics
    ).toInt()

    /** Whether this exact type + quality was already downloaded successfully. */
    private fun downloaded(type: String, quality: String): Boolean =
        DownloadCenter.recordsFor(context, entry.url).any {
            it.type == type && it.quality == quality && it.status == DownloadCenter.STATUS_DONE
        }

    override fun show() {
        val scroll = android.widget.ScrollView(context)
        val root = LinearLayout(context).apply {
            orientation = LinearLayout.VERTICAL
            setPadding(dp(20), dp(16), dp(20), dp(28))
        }

        val title = TextView(context).apply {
            text = context.getString(R.string.action_download)
            textSize = 18f
            setTextColor(context.getColor(R.color.on_surface))
            setTypeface(null, Typeface.BOLD)
        }
        root.addView(title)
        val songTitle = TextView(context).apply {
            text = entry.title
            textSize = 13f
            maxLines = 1
            setTextColor(context.getColor(R.color.on_surface_variant))
        }
        root.addView(songTitle)

        val musicOnly = audioOnly || !catalog.hasVideo
        val audioTrack = catalog.audioTracks.firstOrNull()

        if (!musicOnly) {
            val avBody = LinearLayout(context).apply { orientation = LinearLayout.VERTICAL }
            fillAvOptions(avBody, audioTrack)
            if (avBody.childCount > 0) {
                root.addView(
                    collapsibleGroup(
                        context.getString(R.string.download_video_dropdown),
                        avBody,
                        expanded = true
                    )
                )
            }

            val videoBody = LinearLayout(context).apply { orientation = LinearLayout.VERTICAL }
            fillVideoOnlyOptions(videoBody)
            if (videoBody.childCount > 0) {
                root.addView(
                    collapsibleGroup(
                        context.getString(R.string.download_video_only_dropdown),
                        videoBody,
                        expanded = false
                    )
                )
            }
        }

        if (audioTrack != null) {
            val audioBody = LinearLayout(context).apply { orientation = LinearLayout.VERTICAL }
            fillAudioOptions(audioBody, audioTrack)
            if (audioBody.childCount > 0) {
                root.addView(
                    collapsibleGroup(
                        context.getString(R.string.download_audio_dropdown),
                        audioBody,
                        expanded = musicOnly
                    )
                )
            }
        }

        scroll.addView(root)
        setContentView(scroll)
        super.show()
    }

    /** Video + audio pair (or muxed fallback when there are no adaptive streams). */
    private fun fillAvOptions(body: LinearLayout, audioTrack: AudioTrackGroup?) {
        if (catalog.videoOnly.isNotEmpty() && audioTrack?.best != null) {
            body.addView(
                TextView(context).apply {
                    text = context.getString(R.string.download_pair_note)
                    textSize = 12f
                    maxLines = 2
                    setTextColor(context.getColor(R.color.on_surface_variant))
                    layoutParams = marginParams(top = 4)
                }
            )
            catalog.videoOnly.forEach { v ->
                val quality = "${v.effectiveHeight()}p"
                val alreadyDone = downloaded(DownloadCenter.TYPE_AV, quality)
                body.addView(qualityRow(quality, alreadyDone) {
                    startAv(v, audioTrack, quality)
                })
            }
            return
        }
        catalog.muxed.forEach { v ->
            val quality = "${v.effectiveHeight()}p"
            val alreadyDone = downloaded(DownloadCenter.TYPE_VIDEO, quality)
            body.addView(qualityRow("$quality (muxed)", alreadyDone) {
                startVideo(v, quality)
            })
        }
    }

    private fun fillVideoOnlyOptions(body: LinearLayout) {
        catalog.videoOnly.forEach { v ->
            val quality = "${v.effectiveHeight()}p"
            val alreadyDone = downloaded(DownloadCenter.TYPE_VIDEO, quality)
            body.addView(qualityRow("$quality (no sound)", alreadyDone) {
                startVideo(v, quality)
            })
        }
    }

    private fun fillAudioOptions(body: LinearLayout, audioTrack: AudioTrackGroup) {
        audioTrack.streams
            .filter { it.isUrl }
            .sortedByDescending { it.averageBitrate }
            .forEach { stream ->
                val kbps = if (stream.averageBitrate > 0) {
                    "${stream.averageBitrate} kbps"
                } else {
                    "Audio"
                }
                val alreadyDone = downloaded(DownloadCenter.TYPE_AUDIO, kbps)
                body.addView(qualityRow(kbps + suffix(audioTrack), alreadyDone) {
                    if (alreadyDone) {
                        Toast.makeText(context, R.string.already_downloaded, Toast.LENGTH_SHORT).show()
                    } else {
                        DownloadCenter.startSingle(
                            context,
                            entry.url,
                            entry.title,
                            entry.uploader,
                            entry.thumbnailUrl,
                            DownloadCenter.TYPE_AUDIO,
                            kbps,
                            stream.content,
                            "m4a"
                        )
                        toastStarted()
                    }
                    dismiss()
                })
            }
    }

    private fun startAv(v: VideoStream, audioTrack: AudioTrackGroup, quality: String) {
        if (downloaded(DownloadCenter.TYPE_AV, quality)) {
            Toast.makeText(context, R.string.already_downloaded, Toast.LENGTH_SHORT).show()
        } else {
            DownloadCenter.startPair(
                context,
                entry.url,
                entry.title,
                entry.uploader,
                entry.thumbnailUrl,
                quality,
                v.content,
                audioTrack.best!!.content
            )
            toastStarted()
        }
        dismiss()
    }

    private fun startVideo(v: VideoStream, quality: String) {
        if (downloaded(DownloadCenter.TYPE_VIDEO, quality)) {
            Toast.makeText(context, R.string.already_downloaded, Toast.LENGTH_SHORT).show()
        } else {
            DownloadCenter.startSingle(
                context,
                entry.url,
                entry.title,
                entry.uploader,
                entry.thumbnailUrl,
                DownloadCenter.TYPE_VIDEO,
                quality,
                v.content,
                "mp4"
            )
            toastStarted()
        }
        dismiss()
    }

    /**
     * A tappable header that expands / collapses [body]. [expanded] is the
     * starting state (video group starts open, the others start closed).
     */
    private fun collapsibleGroup(
        title: String,
        body: LinearLayout,
        expanded: Boolean
    ): LinearLayout {
        body.isVisible = expanded
        val chevron = ImageView(context).apply {
            setImageResource(R.drawable.ic_chevron_up)
            setColorFilter(context.getColor(R.color.on_surface_variant))
            rotation = if (expanded) 180f else 0f
            layoutParams = LinearLayout.LayoutParams(dp(22), dp(22))
            importantForAccessibility = View.IMPORTANT_FOR_ACCESSIBILITY_NO
        }
        val header = LinearLayout(context).apply {
            orientation = LinearLayout.HORIZONTAL
            gravity = Gravity.CENTER_VERTICAL
            setPadding(dp(14), dp(14), dp(14), dp(14))
            isClickable = true
            isFocusable = true
            background = GradientDrawable().apply {
                cornerRadius = dp(12).toFloat()
                setColor(Themes.elevatedColor(context))
            }
            layoutParams = marginParams(top = 12)
            addView(
                TextView(context).apply {
                    text = title
                    textSize = 15f
                    setTypeface(null, Typeface.BOLD)
                    setTextColor(context.getColor(R.color.on_surface))
                    layoutParams = LinearLayout.LayoutParams(
                        0, ViewGroup.LayoutParams.WRAP_CONTENT, 1f
                    )
                }
            )
            addView(chevron)
            setOnClickListener {
                val open = !body.isVisible
                body.isVisible = open
                chevron.animate().rotation(if (open) 180f else 0f).setDuration(160).start()
            }
        }
        return LinearLayout(context).apply {
            orientation = LinearLayout.VERTICAL
            addView(header)
            addView(body)
        }
    }

    private fun qualityRow(label: String, done: Boolean, onClick: () -> Unit): TextView =
        TextView(context).apply {
            text = if (done) "$label • ${context.getString(R.string.download_option_done)}" else label
            textSize = 15f
            setPadding(dp(14), dp(12), dp(14), dp(12))
            setTextColor(
                if (done) Themes.onSurfaceVariantColor(context)
                else Themes.onSurfaceColor(context)
            )
            background = GradientDrawable().apply {
                cornerRadius = dp(10).toFloat()
                setColor(Themes.elevatedColor(context))
            }
            layoutParams = marginParams(top = 8)
            setOnClickListener { onClick() }
        }

    private fun marginParams(top: Int = 0): LinearLayout.LayoutParams =
        LinearLayout.LayoutParams(
            ViewGroup.LayoutParams.MATCH_PARENT, ViewGroup.LayoutParams.WRAP_CONTENT
        ).also { it.topMargin = dp(top) }

    private fun suffix(track: AudioTrackGroup): String {
        return if (track.label.isNotBlank() && track.label != "Default") {
            " • ${track.label}"
        } else {
            ""
        }
    }

    private fun toastStarted() {
        Toast.makeText(context, R.string.download_started, Toast.LENGTH_SHORT).show()
    }
}
