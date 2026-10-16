package com.sparktube.app.ui.music

import android.content.Context
import android.graphics.drawable.GradientDrawable
import android.util.TypedValue
import android.view.Gravity
import android.view.View
import android.view.ViewGroup
import android.widget.FrameLayout
import android.widget.HorizontalScrollView
import android.widget.LinearLayout
import android.widget.TextView
import androidx.core.view.isVisible
import com.google.android.material.bottomsheet.BottomSheetBehavior
import com.google.android.material.bottomsheet.BottomSheetDialog
import com.sparktube.app.R
import com.sparktube.app.databinding.SheetLoopBinding
import com.sparktube.app.playback.PlaybackCenter
import com.sparktube.app.util.AppPrefs
import com.sparktube.app.util.Themes

/**
 * Loop settings bottom sheet: Off / Infinite / Repeat N times / Timer. The
 * count and timer options expand inline value chips. Every change is saved to
 * [AppPrefs] and pushed live to the player via [PlaybackCenter.setMusicLoop].
 */
class LoopSheet(context: Context) : BottomSheetDialog(context) {

    private val binding = SheetLoopBinding.inflate(layoutInflater)

    init {
        setContentView(binding.root)
        behavior.skipCollapsed = true
        setOnShowListener { behavior.state = BottomSheetBehavior.STATE_EXPANDED }
        render()
    }

    /** Rebuilds the whole body so the active mode and inline chips stay in sync. */
    private fun render() {
        val c = binding.optionsContainer
        c.removeAllViews()

        c.addView(modeRow(R.string.off, AppPrefs.LOOP_OFF))
        c.addView(modeRow(R.string.loop_infinite, AppPrefs.LOOP_INFINITE))

        c.addView(modeRow(R.string.loop_count_option, AppPrefs.LOOP_COUNT))
        c.addView(
            chipRow(
                choices = COUNT_CHOICES,
                format = R.string.loop_count_value_fmt,
                selectedValue = AppPrefs.musicLoopCount,
                visible = AppPrefs.musicLoopMode == AppPrefs.LOOP_COUNT
            ) {
                AppPrefs.musicLoopCount = it
                select(AppPrefs.LOOP_COUNT)
            }
        )

        c.addView(modeRow(R.string.loop_timer_option, AppPrefs.LOOP_TIMER))
        c.addView(
            chipRow(
                choices = TIMER_CHOICES,
                format = R.string.loop_minutes_fmt,
                selectedValue = AppPrefs.musicLoopMinutes,
                visible = AppPrefs.musicLoopMode == AppPrefs.LOOP_TIMER
            ) {
                AppPrefs.musicLoopMinutes = it
                select(AppPrefs.LOOP_TIMER)
            }
        )
    }

    /** A tappable mode row: radio dot + label, highlighted when active. */
    private fun modeRow(labelRes: Int, mode: String): LinearLayout {
        val active = AppPrefs.musicLoopMode == mode
        return LinearLayout(context).apply {
            orientation = LinearLayout.HORIZONTAL
            gravity = Gravity.CENTER_VERTICAL
            setPadding(dp(20), dp(14), dp(20), dp(14))
            isClickable = true
            isFocusable = true
            background = selectableItemBackground()
            layoutParams = matchWidth()
            setOnClickListener { select(mode) }

            addView(radioDot(active))
            addView(
                TextView(context).apply {
                    text = context.getString(labelRes)
                    textSize = 15f
                    setTextColor(context.getColor(R.color.on_surface))
                    layoutParams = LinearLayout.LayoutParams(
                        0, ViewGroup.LayoutParams.WRAP_CONTENT, 1f
                    ).also { it.marginStart = dp(14) }
                }
            )
        }
    }

    /** Inline chips for the count / timer values, shown only for their mode. */
    private fun chipRow(
        choices: List<Int>,
        format: Int,
        selectedValue: Int,
        visible: Boolean,
        onPick: (Int) -> Unit
    ): HorizontalScrollView {
        val scroll = HorizontalScrollView(context).apply {
            isHorizontalScrollBarEnabled = false
            isVisible = visible
        }
        val row = LinearLayout(context).apply {
            orientation = LinearLayout.HORIZONTAL
            setPadding(dp(54), 0, dp(20), dp(10))
        }
        choices.forEach { value ->
            row.addView(
                TextView(context).apply {
                    text = context.getString(format, value)
                    textSize = 13f
                    setTextColor(context.getColor(R.color.on_surface))
                    gravity = Gravity.CENTER
                    setPadding(dp(14), dp(8), dp(14), dp(8))
                    background = context.getDrawable(R.drawable.bg_chip)
                    isClickable = true
                    isFocusable = true
                    isSelected = value == selectedValue
                    layoutParams = LinearLayout.LayoutParams(
                        ViewGroup.LayoutParams.WRAP_CONTENT, ViewGroup.LayoutParams.WRAP_CONTENT
                    ).also { it.marginEnd = dp(8) }
                    setOnClickListener { onPick(value) }
                }
            )
        }
        scroll.addView(row)
        return scroll
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

    /** Applies [mode], persists it, re-renders and updates the player. */
    private fun select(mode: String) {
        AppPrefs.musicLoopMode = mode
        PlaybackCenter.setMusicLoop()
        render()
    }

    private fun selectableItemBackground() = android.util.TypedValue().let { tv ->
        context.theme.resolveAttribute(android.R.attr.selectableItemBackground, tv, true)
        if (tv.resourceId != 0) context.getDrawable(tv.resourceId) else null
    }

    private fun matchWidth() = LinearLayout.LayoutParams(
        ViewGroup.LayoutParams.MATCH_PARENT, ViewGroup.LayoutParams.WRAP_CONTENT
    )

    private fun dp(value: Int): Int = TypedValue.applyDimension(
        TypedValue.COMPLEX_UNIT_DIP, value.toFloat(), context.resources.displayMetrics
    ).toInt()

    private companion object {
        val COUNT_CHOICES = listOf(2, 3, 5, 10)
        val TIMER_CHOICES = listOf(5, 10, 15, 30, 45, 60)
    }
}
