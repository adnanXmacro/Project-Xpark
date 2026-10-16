package com.sparktube.app.ui.music

import android.content.Context
import com.sparktube.app.R
import com.sparktube.app.util.AppPrefs

/**
 * Shared loop control for the music screens: the currently selected mode as a
 * label (settings row) plus the loop bottom sheet. All choices are persisted
 * in [AppPrefs] and pushed to the player through [LoopSheet], so nothing here
 * touches the queue itself.
 */
object MusicLoopUi {

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
     * Opens the loop settings sheet. [onDismissed] runs once it closes so the
     * caller can refresh its own UI (button tint / settings label).
     */
    fun showPicker(context: Context, onDismissed: () -> Unit) {
        LoopSheet(context).apply {
            setOnDismissListener { onDismissed() }
            show()
        }
    }
}
