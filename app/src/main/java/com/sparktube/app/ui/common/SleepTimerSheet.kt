package com.sparktube.app.ui.common

import android.content.Context
import android.graphics.drawable.GradientDrawable
import android.os.Handler
import android.os.Looper
import android.text.InputType
import android.util.TypedValue
import android.view.Gravity
import android.view.View
import android.view.ViewGroup
import android.widget.EditText
import android.widget.FrameLayout
import android.widget.LinearLayout
import android.widget.TextView
import androidx.appcompat.app.AlertDialog
import androidx.core.view.isVisible
import com.google.android.material.bottomsheet.BottomSheetBehavior
import com.google.android.material.bottomsheet.BottomSheetDialog
import com.sparktube.app.R
import com.sparktube.app.playback.PlaybackCenter
import com.sparktube.app.util.Themes

class SleepTimerSheet(context: Context) : BottomSheetDialog(context) {

    private val handler = Handler(Looper.getMainLooper())
    private val tick = object : Runnable {
        override fun run() {
            if (PlaybackCenter.isSleepTimerActive) render()
            handler.postDelayed(this, 1000)
        }
    }

    private val options = LinearLayout(context).apply {
        orientation = LinearLayout.VERTICAL
        layoutParams = LinearLayout.LayoutParams(
            ViewGroup.LayoutParams.MATCH_PARENT, ViewGroup.LayoutParams.WRAP_CONTENT
        )
    }

    init {
        val root = LinearLayout(context).apply {
            orientation = LinearLayout.VERTICAL
            setPadding(0, 0, 0, dp(16))
        }
        root.addView(
            View(context).apply {
                layoutParams = LinearLayout.LayoutParams(dp(36), dp(4)).also {
                    it.gravity = Gravity.CENTER_HORIZONTAL
                    it.topMargin = dp(10)
                }
                background = context.getDrawable(R.drawable.bg_sheet_handle)
            }
        )
        root.addView(
            TextView(context).apply {
                text = context.getString(R.string.sleep_timer)
                textSize = 18f
                setTypeface(null, android.graphics.Typeface.BOLD)
                setTextColor(context.getColor(R.color.on_surface))
                setPadding(dp(20), dp(14), dp(20), 0)
            }
        )
        root.addView(
            TextView(context).apply {
                text = context.getString(R.string.sleep_timer_subtitle)
                textSize = 13f
                setTextColor(context.getColor(R.color.on_surface_variant))
                setPadding(dp(20), dp(2), dp(20), dp(8))
            }
        )
        root.addView(options)
        setContentView(root)
        behavior.skipCollapsed = true
        setOnShowListener {
            behavior.state = BottomSheetBehavior.STATE_EXPANDED
            handler.removeCallbacks(tick)
            handler.post(tick)
        }
        setOnDismissListener { handler.removeCallbacks(tick) }
        render()
    }

    private fun render() {
        options.removeAllViews()
        val remaining = PlaybackCenter.sleepTimerRemainingMs
        val activeMinutes = if (PlaybackCenter.isSleepTimerActive) {
            ((remaining + 59_999L) / 60_000L).toInt().coerceAtLeast(1)
        } else {
            0
        }
        options.addView(modeRow(context.getString(R.string.sleep_timer_off), activeMinutes == 0) {
            PlaybackCenter.clearSleepTimer()
            dismiss()
        })
        PRESETS.forEach { minutes ->
            options.addView(
                modeRow(context.getString(R.string.loop_minutes_fmt, minutes), activeMinutes == minutes) {
                    PlaybackCenter.setSleepTimer(minutes)
                    dismiss()
                }
            )
        }
        val customActive = PlaybackCenter.isSleepTimerActive && activeMinutes !in PRESETS
        val customLabel = if (customActive) {
            context.getString(R.string.loop_minutes_fmt, activeMinutes)
        } else {
            context.getString(R.string.sleep_timer_custom)
        }
        options.addView(modeRow(customLabel, customActive) { askCustom() })
        if (PlaybackCenter.isSleepTimerActive) {
            options.addView(
                TextView(context).apply {
                    text = remainingLabel(remaining)
                    textSize = 13f
                    setTextColor(Themes.accentColor(context))
                    setPadding(dp(20), dp(8), dp(20), dp(4))
                }
            )
        }
    }

    private fun remainingLabel(ms: Long): String {
        val minutes = (ms / 60_000L).toInt()
        return if (minutes >= 1) {
            context.getString(R.string.sleep_timer_remaining_fmt, minutes)
        } else {
            context.getString(R.string.sleep_timer_remaining_sec_fmt, (ms / 1000L).toInt().coerceAtLeast(1))
        }
    }

    private fun modeRow(label: String, active: Boolean, onClick: () -> Unit): LinearLayout =
        LinearLayout(context).apply {
            orientation = LinearLayout.HORIZONTAL
            gravity = Gravity.CENTER_VERTICAL
            setPadding(dp(20), dp(14), dp(20), dp(14))
            isClickable = true
            isFocusable = true
            background = selectableItemBackground()
            addView(radioDot(active))
            addView(
                TextView(context).apply {
                    text = label
                    textSize = 15f
                    setTextColor(context.getColor(R.color.on_surface))
                    layoutParams = LinearLayout.LayoutParams(
                        0, ViewGroup.LayoutParams.WRAP_CONTENT, 1f
                    ).also { it.marginStart = dp(14) }
                }
            )
            setOnClickListener { onClick() }
        }

    private fun radioDot(active: Boolean): FrameLayout =
        FrameLayout(context).apply {
            layoutParams = LinearLayout.LayoutParams(dp(20), dp(20))
            background = GradientDrawable().apply {
                shape = GradientDrawable.OVAL
                if (active) {
                    setColor(Themes.accentColor(context))
                } else {
                    setColor(android.graphics.Color.TRANSPARENT)
                    setStroke(dp(2), context.getColor(R.color.outline))
                }
            }
            isVisible = true
            if (active) {
                addView(
                    View(context).apply {
                        background = GradientDrawable().apply {
                            shape = GradientDrawable.OVAL
                            setColor(context.getColor(R.color.white))
                        }
                        layoutParams = FrameLayout.LayoutParams(dp(8), dp(8), Gravity.CENTER)
                    }
                )
            }
        }

    private fun askCustom() {
        val input = EditText(context).apply {
            inputType = InputType.TYPE_CLASS_NUMBER
            hint = context.getString(R.string.sleep_timer_custom_hint)
            setSingleLine()
        }
        val container = FrameLayout(context).apply {
            setPadding(dp(24), dp(8), dp(24), 0)
            addView(input)
        }
        AlertDialog.Builder(context)
            .setTitle(R.string.sleep_timer_custom_title)
            .setView(container)
            .setPositiveButton(android.R.string.ok) { _, _ ->
                val minutes = input.text.toString().toIntOrNull() ?: 0
                if (minutes >= 1) {
                    PlaybackCenter.setSleepTimer(minutes)
                    dismiss()
                }
            }
            .setNegativeButton(android.R.string.cancel, null)
            .show()
    }

    private fun selectableItemBackground() = TypedValue().let { tv ->
        context.theme.resolveAttribute(android.R.attr.selectableItemBackground, tv, true)
        if (tv.resourceId != 0) context.getDrawable(tv.resourceId) else null
    }

    private fun dp(value: Int): Int = TypedValue.applyDimension(
        TypedValue.COMPLEX_UNIT_DIP, value.toFloat(), context.resources.displayMetrics
    ).toInt()

    companion object {
        private val PRESETS = listOf(15, 30, 45, 60)
    }
}
