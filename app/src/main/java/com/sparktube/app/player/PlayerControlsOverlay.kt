package com.sparktube.app.player

import android.annotation.SuppressLint
import android.content.Context
import android.graphics.PorterDuff
import android.graphics.Typeface
import android.os.Handler
import android.os.Looper
import android.util.TypedValue
import android.view.Gravity
import android.view.View
import android.view.ViewGroup
import android.widget.ImageView
import android.widget.LinearLayout
import android.widget.SeekBar
import android.widget.TextView
import androidx.core.content.ContextCompat
import androidx.core.view.isVisible
import com.sparktube.app.R
import com.sparktube.app.databinding.ViewPlayerOverlayBinding
import com.sparktube.app.util.AppPrefs
import com.sparktube.app.util.Formatters
import com.sparktube.app.util.Themes

/**
 * Draws and drives the custom video overlay. Everything here is data-driven:
 * the button row is rebuilt from [PlayerControlRegistry] using the user's saved
 * order, so adding/removing/reordering controls needs no changes in this class.
 *
 * The overlay owns only presentation and interaction timing; every real action
 * is delegated back to the activity through [Callbacks].
 */
class PlayerControlsOverlay(
    private val context: Context,
    private val binding: ViewPlayerOverlayBinding,
    private val callbacks: Callbacks
) {

    interface Callbacks {
        fun onTogglePlayPause()
        fun onSeekBy(deltaMs: Long)
        fun onPrevious()
        fun onNext()
        fun onSeekTo(positionMs: Long)
        fun onSpeed()
        fun onQuality()
        fun onMore()
        fun onToggleFullscreen()
        fun onToggleLoop()
        fun onToggleMute()
        fun onPip()
        /** Called on any interaction so the activity can keep the screen awake etc. */
        fun onInteraction()

        /** Live playhead position, used by the progress row while visible. */
        fun positionMs(): Long
        /** Live media duration (0 when unknown / live). */
        fun durationMs(): Long
    }

    private val handler = Handler(Looper.getMainLooper())

    /** Whether the overlay is currently shown. */
    var isVisible: Boolean = false
        private set

    /** True while the user is dragging the seek bar; blocks auto-hide. */
    private var scrubbing = false

    private val autoHide = Runnable { if (playState.isPlaying) hide() }

    private val ticker = object : Runnable {
        override fun run() {
            if (!isVisible) return
            updateProgress()
            handler.postDelayed(this, TICK_MS)
        }
    }

    private var playState: PlayerControlState = PlayerControlState()

    /** Whether the progress/timestamps control is part of the current layout. */
    private var progressEnabled = false

    /** Whether the centre play/pause control is part of the current layout. */
    private var playControlEnabled = false

    init {
        binding.topScrim.isVisible = false
        binding.bottomScrim.isVisible = false
        binding.bottomPanel.isVisible = false
        binding.centerPlayPause.isVisible = false

        binding.centerPlayPause.setOnClickListener {
            callbacks.onTogglePlayPause()
            callbacks.onInteraction()
            scheduleHide()
        }

        // The control bar swallows taps so touching empty space next to the
        // buttons does not fall through to the video (which would toggle the
        // overlay) nor start a seek gesture.
        binding.bottomPanel.setOnClickListener { scheduleHide() }

        binding.seekBar.setOnSeekBarChangeListener(object : SeekBar.OnSeekBarChangeListener {
            override fun onProgressChanged(bar: SeekBar, progress: Int, fromUser: Boolean) {
                if (!fromUser) return
                val duration = callbacks.durationMs().takeIf { it > 0 } ?: playState.durationMs
                binding.timeCurrent.text = Formatters.formatDuration(
                    (progress / 1000f * duration / 1000).toLong()
                )
            }

            override fun onStartTrackingTouch(bar: SeekBar) {
                scrubbing = true
                handler.removeCallbacks(autoHide)
            }

            override fun onStopTrackingTouch(bar: SeekBar) {
                scrubbing = false
                callbacks.onSeekTo((bar.progress / 1000f * playState.durationMs).toLong())
                scheduleHide()
            }
        })
    }

    /**
     * Rebuilds the visible control row from prefs + current state. Safe to call
     * on every playback state change; it is cheap and idempotent.
     */
    fun render(state: PlayerControlState) {
        playState = state

        binding.centerPlayPause.setImageResource(
            if (state.isPlaying) R.drawable.ic_pause else R.drawable.ic_play_arrow
        )

        val visibleIds = PlayerControlRegistry.sanitizeVisible(AppPrefs.playerControls)
        binding.controlsRow.removeAllViews()
        binding.rightCluster.removeAllViews()

        var hasProgress = false
        visibleIds.forEach { id ->
            val control = PlayerControlRegistry.byId[id] ?: return@forEach
            if (!control.available(state)) return@forEach
            if (id == PlayerControlRegistry.PLAY_PAUSE) {
                binding.centerPlayPause.isVisible = true
                return@forEach
            }
            if (id == PlayerControlRegistry.PROGRESS) {
                hasProgress = true
                return@forEach
            }
            // Speed and fullscreen are pinned on the right, YouTube-style;
            // they are added after the left-hand row so order stays Speed then Fullscreen.
            if (id == PlayerControlRegistry.SPEED || id == PlayerControlRegistry.FULLSCREEN) {
                return@forEach
            }
            binding.controlsRow.addView(controlView(control, state))
        }
        if (PlayerControlRegistry.SPEED in visibleIds) {
            PlayerControlRegistry.byId[PlayerControlRegistry.SPEED]
                ?.takeIf { it.available(state) }
                ?.let { binding.rightCluster.addView(controlView(it, state)) }
        }
        if (PlayerControlRegistry.FULLSCREEN in visibleIds) {
            PlayerControlRegistry.byId[PlayerControlRegistry.FULLSCREEN]
                ?.takeIf { it.available(state) }
                ?.let { binding.rightCluster.addView(controlView(it, state)) }
        }

        progressEnabled = hasProgress
        val hasLeft = binding.controlsRow.childCount > 0
        val hasRight = binding.rightCluster.childCount > 0
        binding.controlsRow.isVisible = hasLeft
        binding.controlsScroll.isVisible = hasLeft
        binding.rightCluster.isVisible = hasRight
        binding.progressRow.isVisible = hasProgress && state.durationMs > 0
        val playControl = PlayerControlRegistry.byId[PlayerControlRegistry.PLAY_PAUSE]
        playControlEnabled = visibleIds.contains(PlayerControlRegistry.PLAY_PAUSE) &&
            (playControl?.available(state) == true)
        val showPlay = playControlEnabled
        // While buffering the spinner takes the centre spot instead of the
        // play button; and nothing appears at all until the overlay is shown.
        binding.centerPlayPause.isVisible = isVisible && showPlay && !state.isBuffering
        binding.overlayBuffering.isVisible = state.isBuffering
        updateProgress()
    }

    /** Called by the state poller so the overlay follows playback without a rebuild. */
    fun updateState(state: PlayerControlState) {
        playState = state
        binding.centerPlayPause.setImageResource(
            if (state.isPlaying) R.drawable.ic_pause else R.drawable.ic_play_arrow
        )
        binding.overlayBuffering.isVisible = state.isBuffering
        binding.progressRow.isVisible = progressEnabled && state.durationMs > 0
        binding.timeDuration.text = Formatters.formatDuration(state.durationMs / 1000)
        if (isVisible) {
            binding.centerPlayPause.isVisible = !state.isBuffering && playControlEnabled
        }
        updateProgress()
    }

    private fun updateProgress() {
        val duration = callbacks.durationMs().takeIf { it > 0 } ?: playState.durationMs
        if (duration <= 0 || scrubbing) return
        val position = callbacks.positionMs().coerceIn(0, duration)
        binding.seekBar.progress = (position * 1000 / duration).toInt()
        binding.timeCurrent.text = Formatters.formatDuration(position / 1000)
        binding.timeDuration.text = Formatters.formatDuration(duration / 1000)
    }

    /** One button: icon plus an optional short label (speed "1x", quality "720p"). */
    @SuppressLint("ClickableViewAccessibility")
    private fun controlView(control: PlayerControl, state: PlayerControlState): View {
        val enabled = control.hasData(state)
        val label = labelFor(control.id, state)

        val row = LinearLayout(context).apply {
            orientation = LinearLayout.HORIZONTAL
            gravity = Gravity.CENTER_VERTICAL
            setPadding(dp(10), dp(6), dp(10), dp(6))
            isEnabled = enabled
            alpha = if (enabled) 1f else DISABLED_ALPHA
            layoutParams = LinearLayout.LayoutParams(
                ViewGroup.LayoutParams.WRAP_CONTENT, ViewGroup.LayoutParams.WRAP_CONTENT
            ).also { it.marginEnd = dp(2) }
            isClickable = true
            isFocusable = true
            contentDescription = context.getString(control.labelRes)
            setOnClickListener {
                if (!enabled) return@setOnClickListener
                dispatch(control.id)
                callbacks.onInteraction()
                scheduleHide()
            }
        }

        val icon = ImageView(context).apply {
            setImageResource(iconFor(control.id, state))
            layoutParams = LinearLayout.LayoutParams(dp(24), dp(24))
            importantForAccessibility = View.IMPORTANT_FOR_ACCESSIBILITY_NO
        }
        val tint = tintFor(control.id, state)
        icon.setColorFilter(tint, PorterDuff.Mode.SRC_IN)
        row.addView(icon)

        if (!label.isNullOrBlank()) {
            row.addView(
                TextView(context).apply {
                    text = label
                    setTextColor(tint)
                    setTextSize(TypedValue.COMPLEX_UNIT_SP, 12f)
                    setTypeface(typeface, Typeface.BOLD)
                    layoutParams = LinearLayout.LayoutParams(
                        ViewGroup.LayoutParams.WRAP_CONTENT, ViewGroup.LayoutParams.WRAP_CONTENT
                    ).also { it.marginStart = dp(4) }
                }
            )
        }
        return row
    }

    private fun dispatch(id: String) {
        when (id) {
            PlayerControlRegistry.PLAY_PAUSE -> callbacks.onTogglePlayPause()
            PlayerControlRegistry.PREVIOUS -> callbacks.onPrevious()
            PlayerControlRegistry.NEXT -> callbacks.onNext()
            PlayerControlRegistry.REWIND -> callbacks.onSeekBy(-SEEK_STEP_MS)
            PlayerControlRegistry.FORWARD -> callbacks.onSeekBy(SEEK_STEP_MS)
            PlayerControlRegistry.SPEED -> callbacks.onSpeed()
            PlayerControlRegistry.QUALITY -> callbacks.onQuality()
            PlayerControlRegistry.MORE -> callbacks.onMore()
            PlayerControlRegistry.LOOP -> callbacks.onToggleLoop()
            PlayerControlRegistry.MUTE -> callbacks.onToggleMute()
            PlayerControlRegistry.PIP -> callbacks.onPip()
            PlayerControlRegistry.FULLSCREEN -> callbacks.onToggleFullscreen()
        }
    }

    private fun iconFor(id: String, state: PlayerControlState): Int = when (id) {
        PlayerControlRegistry.MUTE ->
            if (state.isMuted) R.drawable.ic_volume_off else R.drawable.ic_volume_up
        PlayerControlRegistry.LOOP ->
            if (state.loopEnabled) R.drawable.ic_repeat_one else R.drawable.ic_repeat
        PlayerControlRegistry.FULLSCREEN ->
            if (state.isFullscreen) R.drawable.ic_fullscreen_exit else R.drawable.ic_fullscreen
        else -> PlayerControlRegistry.byId[id]?.iconRes ?: R.drawable.ic_settings
    }

    private fun tintFor(id: String, state: PlayerControlState): Int = when {
        id == PlayerControlRegistry.LOOP && state.loopEnabled -> accent()
        id == PlayerControlRegistry.MUTE && state.isMuted -> accent()
        else -> ContextCompat.getColor(context, R.color.white)
    }

    /** Short text drawn next to the icon for the two "value" controls. */
    private fun labelFor(id: String, state: PlayerControlState): String? = when (id) {
        PlayerControlRegistry.SPEED -> formatSpeed(state.speed)
        PlayerControlRegistry.QUALITY -> state.qualityLabel
        else -> null
    }

    fun show() {
        if (isVisible) return
        isVisible = true
        listOf(binding.topScrim, binding.bottomScrim, binding.bottomPanel).forEach {
            it.isVisible = true
            it.animate().cancel()
            it.alpha = 0f
            it.animate().alpha(1f).setDuration(FADE_MS).start()
        }
        val hasPlay = PlayerControlRegistry.sanitizeVisible(AppPrefs.playerControls)
            .contains(PlayerControlRegistry.PLAY_PAUSE)
        binding.centerPlayPause.isVisible = hasPlay && !playState.isBuffering
        binding.centerPlayPause.animate().cancel()
        binding.centerPlayPause.alpha = 0f
        binding.centerPlayPause.animate().alpha(1f).setDuration(FADE_MS).start()
        binding.overlayBuffering.isVisible = playState.isBuffering
        handler.removeCallbacks(ticker)
        handler.post(ticker)
        scheduleHide()
        // The legacy corner buttons follow the custom overlay's visibility.
        onVisibilityChanged?.invoke(true)
    }

    fun hide() {
        if (!isVisible) return
        isVisible = false
        handler.removeCallbacks(autoHide)
        handler.removeCallbacks(ticker)
        listOf(binding.topScrim, binding.bottomScrim, binding.bottomPanel, binding.centerPlayPause)
            .forEach {
                it.animate().cancel()
                it.animate().alpha(0f).setDuration(FADE_MS)
                    // Guard against a show() that started while this fade was
                    // still running: only hide if we are still meant to be hidden.
                    .withEndAction { if (!isVisible) it.isVisible = false }
                    .start()
            }
        onVisibilityChanged?.invoke(false)
    }

    fun toggle() {
        if (isVisible) hide() else show()
    }

    /** Resets the inactivity timer; called on every touch inside the overlay. */
    fun scheduleHide() {
        handler.removeCallbacks(autoHide)
        if (!isVisible || scrubbing || !playState.isPlaying) return
        handler.postDelayed(autoHide, AppPrefs.playerControlsTimeout.toLong())
    }

    fun release() {
        handler.removeCallbacksAndMessages(null)
    }

    /** Notified with true when the overlay appears and false when it fades out. */
    var onVisibilityChanged: ((Boolean) -> Unit)? = null

    private fun accent(): Int = try {
        Themes.accentColor(context)
    } catch (e: Exception) {
        ContextCompat.getColor(context, R.color.white)
    }

    private fun formatSpeed(speed: Float): String =
        if (speed == 1f) "1x" else "${trimFloat(speed)}x"

    private fun trimFloat(value: Float): String =
        if (value % 1f == 0f) value.toInt().toString()
        else value.toString().trimEnd('0').trimEnd('.')

    private fun dp(value: Int): Int = TypedValue.applyDimension(
        TypedValue.COMPLEX_UNIT_DIP, value.toFloat(), context.resources.displayMetrics
    ).toInt()

    private companion object {
        const val TICK_MS = 500L
        const val FADE_MS = 180L
        const val SEEK_STEP_MS = 10_000L
        const val DISABLED_ALPHA = 0.38f
    }
}
