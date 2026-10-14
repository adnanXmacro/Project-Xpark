package com.sparktube.app.ui.music

import android.content.Context
import androidx.appcompat.app.AlertDialog
import androidx.appcompat.app.AppCompatActivity
import com.sparktube.app.R
import com.sparktube.app.playback.PlaybackCenter
import com.sparktube.app.util.AppPrefs

/**
 * Shared loop control for the music screens: the currently selected mode as a
 * label (settings row) and the picker dialog (now-playing button + settings).
 * All choices are persisted in [AppPrefs] and pushed to the player through
 * [PlaybackCenter.setMusicLoop], so nothing here touches the queue itself.
 */
object MusicLoopUi {

    private val COUNT_CHOICES = listOf(2, 3, 5, 10)
    private val TIMER_CHOICES = listOf(5, 10, 15, 30, 45, 60)

    /** Human-readable current setting, e.g. "Off", "Infinite", "Repeat 3 times". */
    fun label(context: Context): String = when (AppPrefs.musicLoopMode) {
        AppPrefs.LOOP_INFINITE -> context.getString(R.string.loop_infinite)
        AppPrefs.LOOP_COUNT -> context.getString(R.string.loop_count_fmt, AppPrefs.musicLoopCount)
        AppPrefs.LOOP_TIMER -> context.getString(
            R.string.loop_timer_fmt, AppPrefs.musicLoopMinutes
        )
        else -> context.getString(R.string.off)
    }

    /** True while any looping (infinite / count / timer) is engaged. */
    fun isActive(): Boolean = AppPrefs.musicLoopMode != AppPrefs.LOOP_OFF

    /**
     * Shows the mode picker. Picking "repeat N times" / "timer" drills into a
     * second dialog to choose the value. [onApplied] runs after the preference
     * changed so the caller can refresh its UI.
     */
    fun showPicker(activity: AppCompatActivity, onApplied: () -> Unit) {
        val modes = listOf(
            AppPrefs.LOOP_OFF,
            AppPrefs.LOOP_INFINITE,
            AppPrefs.LOOP_COUNT,
            AppPrefs.LOOP_TIMER
        )
        val labels = arrayOf(
            activity.getString(R.string.off),
            activity.getString(R.string.loop_infinite),
            activity.getString(R.string.loop_count_fmt, AppPrefs.musicLoopCount),
            activity.getString(R.string.loop_timer_fmt, AppPrefs.musicLoopMinutes)
        )
        val current = modes.indexOf(AppPrefs.musicLoopMode)
        AlertDialog.Builder(activity)
            .setTitle(R.string.settings_loop_mode)
            .setSingleChoiceItems(labels, if (current >= 0) current else 0) { dialog, which ->
                dialog.dismiss()
                when (modes[which]) {
                    AppPrefs.LOOP_COUNT -> showValuePicker(
                        activity,
                        title = R.string.loop_count_title,
                        choices = COUNT_CHOICES,
                        currentValue = AppPrefs.musicLoopCount,
                        format = R.string.loop_count_value_fmt,
                        onApplied = onApplied
                    ) { value ->
                        AppPrefs.musicLoopCount = value
                        AppPrefs.musicLoopMode = AppPrefs.LOOP_COUNT
                    }

                    AppPrefs.LOOP_TIMER -> showValuePicker(
                        activity,
                        title = R.string.loop_timer_title,
                        choices = TIMER_CHOICES,
                        currentValue = AppPrefs.musicLoopMinutes,
                        format = R.string.loop_minutes_fmt,
                        onApplied = onApplied
                    ) { value ->
                        AppPrefs.musicLoopMinutes = value
                        AppPrefs.musicLoopMode = AppPrefs.LOOP_TIMER
                    }

                    else -> {
                        AppPrefs.musicLoopMode = modes[which]
                        PlaybackCenter.setMusicLoop()
                        onApplied()
                    }
                }
            }
            .setNegativeButton(android.R.string.cancel, null)
            .show()
    }

    private fun showValuePicker(
        activity: AppCompatActivity,
        title: Int,
        choices: List<Int>,
        currentValue: Int,
        format: Int,
        onApplied: () -> Unit,
        apply: (Int) -> Unit
    ) {
        val labels = choices.map { activity.getString(format, it) }.toTypedArray()
        val current = choices.indexOf(currentValue)
        AlertDialog.Builder(activity)
            .setTitle(title)
            .setSingleChoiceItems(labels, if (current >= 0) current else 0) { dialog, which ->
                apply(choices[which])
                PlaybackCenter.setMusicLoop()
                onApplied()
                dialog.dismiss()
            }
            .setNegativeButton(android.R.string.cancel, null)
            .show()
    }
}
