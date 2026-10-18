package com.sparktube.app.player

import androidx.annotation.DrawableRes
import androidx.annotation.StringRes

/**
 * A snapshot of everything a control may need to decide whether it can be
 * shown right now (availability) and which icon it should draw. Rebuilt from
 * [com.sparktube.app.playback.PlaybackCenter] whenever playback state changes,
 * so the overlay never has to reach into the engine itself.
 */
data class PlayerControlState(
    val isVideo: Boolean = true,
    val isPlaying: Boolean = false,
    val isBuffering: Boolean = false,
    val isMuted: Boolean = false,
    val canSeek: Boolean = false,
    val hasPrevious: Boolean = false,
    val hasNext: Boolean = false,
    val hasQualityOptions: Boolean = false,
    val loopEnabled: Boolean = false,
    val isFullscreen: Boolean = false,
    val pipSupported: Boolean = false,
    val positionMs: Long = 0L,
    val durationMs: Long = 0L,
    val speed: Float = 1f,
    val qualityLabel: String? = null
)

/**
 * One modular, user-arrangeable player control. The registry
 * ([PlayerControlRegistry]) is the single source of truth: the overlay and the
 * customization screen both render straight from it, so adding a control never
 * means editing UI code in two places.
 *
 * [id] is the stable key persisted in preferences — never rename it without a
 * migration, or users lose their layout. [availability] is re-evaluated live;
 * a control that reports false appears under "Available" but cannot be added
 * to the player (e.g. Picture-in-picture on an unsupported device).
 */
data class PlayerControl(
    val id: String,
    @StringRes val labelRes: Int,
    @DrawableRes val iconRes: Int,
    val defaultVisible: Boolean = false,
    /** Lower comes first in the default layout. */
    val defaultOrder: Int = 0,
    /** Whether this control can be *rendered* at all right now. */
    val available: (PlayerControlState) -> Boolean = { true },
    /**
     * Optional precondition for the control to be *usable* even though it can
     * be rendered; a control whose [available] is true but whose [hasData] is
     * false is shown disabled rather than hidden (e.g. Next with no queue).
     */
    val hasData: (PlayerControlState) -> Boolean = { true },
    /** Why the control is unavailable, for the customization screen. */
    @StringRes val unavailableReasonRes: Int? = null
)
