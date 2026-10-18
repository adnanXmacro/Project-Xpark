package com.sparktube.app.player

import android.os.Build
import com.sparktube.app.R

/**
 * The catalogue of modular player controls. This is the data-driven heart of
 * the custom player: every control is declared once here and rendered by both
 * the overlay and the customization screen.
 *
 * Captions/subtitles are intentionally absent: this app's playback pipeline
 * does not surface text tracks, so rather than ship a decorative control that
 * cannot work, the feature is omitted entirely.
 */
object PlayerControlRegistry {

    const val PLAY_PAUSE = "play_pause"
    const val PREVIOUS = "previous"
    const val NEXT = "next"
    const val REWIND = "rewind"
    const val FORWARD = "forward"
    const val PROGRESS = "progress"
    const val SPEED = "speed"
    const val QUALITY = "quality"
    const val LOOP = "loop"
    const val MUTE = "mute"
    const val PIP = "pip"
    const val FULLSCREEN = "fullscreen"
    const val MORE = "more"

    val all: List<PlayerControl> = listOf(
        PlayerControl(
            id = PLAY_PAUSE,
            labelRes = R.string.control_play_pause,
            iconRes = R.drawable.ic_play_arrow,
            defaultVisible = true,
            defaultOrder = 0
        ),
        PlayerControl(
            id = PREVIOUS,
            labelRes = R.string.control_previous,
            iconRes = R.drawable.ic_skip_previous,
            defaultVisible = true,
            defaultOrder = 1,
            hasData = { it.hasPrevious }
        ),
        PlayerControl(
            id = NEXT,
            labelRes = R.string.control_next,
            iconRes = R.drawable.ic_skip_next,
            defaultVisible = true,
            defaultOrder = 2,
            hasData = { it.hasNext }
        ),
        PlayerControl(
            id = REWIND,
            labelRes = R.string.control_rewind,
            iconRes = R.drawable.ic_seek_back,
            defaultVisible = true,
            defaultOrder = 3,
            hasData = { it.canSeek }
        ),
        PlayerControl(
            id = FORWARD,
            labelRes = R.string.control_forward,
            iconRes = R.drawable.ic_seek_forward,
            defaultVisible = true,
            defaultOrder = 4,
            hasData = { it.canSeek }
        ),
        PlayerControl(
            id = PROGRESS,
            labelRes = R.string.control_progress,
            iconRes = R.drawable.ic_timeline,
            defaultVisible = true,
            defaultOrder = 5,
            hasData = { it.canSeek }
        ),
        PlayerControl(
            id = SPEED,
            labelRes = R.string.control_speed,
            iconRes = R.drawable.ic_speed,
            defaultVisible = true,
            defaultOrder = 6
        ),
        PlayerControl(
            id = QUALITY,
            labelRes = R.string.control_quality,
            iconRes = R.drawable.ic_tune,
            defaultVisible = true,
            defaultOrder = 7,
            hasData = { it.hasQualityOptions }
        ),
        PlayerControl(
            id = FULLSCREEN,
            labelRes = R.string.control_fullscreen,
            iconRes = R.drawable.ic_fullscreen,
            defaultVisible = true,
            defaultOrder = 8
        ),
        PlayerControl(
            id = MORE,
            labelRes = R.string.control_more,
            iconRes = R.drawable.ic_more_vert,
            defaultVisible = true,
            defaultOrder = 9
        ),
        PlayerControl(
            id = LOOP,
            labelRes = R.string.control_loop,
            iconRes = R.drawable.ic_repeat,
            defaultVisible = false,
            defaultOrder = 10,
            available = { it.isVideo }
        ),
        PlayerControl(
            id = MUTE,
            labelRes = R.string.control_mute,
            iconRes = R.drawable.ic_volume_up,
            defaultVisible = false,
            defaultOrder = 11
        ),
        PlayerControl(
            id = PIP,
            labelRes = R.string.control_pip,
            iconRes = R.drawable.ic_pip,
            defaultVisible = false,
            defaultOrder = 12,
            available = { it.pipSupported && it.isVideo },
            unavailableReasonRes = R.string.control_unavailable_pip
        )
    )

    /** Look-up by stable id; unknown ids (from an old preference) are ignored. */
    val byId: Map<String, PlayerControl> = all.associateBy { it.id }

    /** Ids shown in a fresh install, in order. */
    val defaultVisibleIds: List<String> =
        all.filter { it.defaultVisible }.sortedBy { it.defaultOrder }.map { it.id }

    /** Whether PiP is even possible on this device. */
    fun pipSupported(): Boolean = Build.VERSION.SDK_INT >= Build.VERSION_CODES.O

    /**
     * Cleans a persisted *visible* id list: unknown ids (from an old version)
     * are dropped and duplicates are removed while keeping first-seen order.
     * Controls absent from the list are simply hidden, so this never force-shows
     * anything the user removed.
     */
    fun sanitizeVisible(saved: List<String>): List<String> {
        val seen = LinkedHashSet<String>()
        saved.forEach { if (byId.containsKey(it)) seen.add(it) }
        return seen.toList()
    }

    /** Controls the user can still add: the ones not currently visible. */
    fun availableFor(visible: List<String>): List<PlayerControl> {
        val visibleSet = visible.toSet()
        return all.filterNot { it.id in visibleSet }.sortedBy { it.defaultOrder }
    }
}
