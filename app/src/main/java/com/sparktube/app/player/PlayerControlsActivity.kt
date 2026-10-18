package com.sparktube.app.player

import android.graphics.Typeface
import android.graphics.drawable.GradientDrawable
import android.os.Bundle
import android.text.TextUtils
import android.util.TypedValue
import android.view.Gravity
import android.view.View
import android.view.ViewGroup
import android.widget.ImageButton
import android.widget.ImageView
import android.widget.LinearLayout
import android.widget.TextView
import androidx.appcompat.app.AlertDialog
import androidx.appcompat.app.AppCompatActivity
import com.sparktube.app.R
import com.sparktube.app.databinding.ActivityPlayerControlsBinding
import com.sparktube.app.util.AppPrefs
import com.sparktube.app.util.Themes

/**
 * Dedicated customization screen for the video player. Two live sections:
 * the controls currently in the player (reorder / remove) and the controls
 * that can be added. Every change is persisted through [AppPrefs] and the
 * player overlay rebuilds from the same source, so the next time the overlay
 * is shown it already reflects the new layout.
 *
 * Reordering uses explicit move up/down buttons (rather than drag-and-drop) so
 * it works with screen readers and on small screens.
 */
class PlayerControlsActivity : AppCompatActivity() {

    private lateinit var binding: ActivityPlayerControlsBinding

    /** Working copy so the UI never mutates the saved layout mid-render. */
    private var visible: MutableList<String> = mutableListOf()

    override fun onCreate(savedInstanceState: Bundle?) {
        Themes.apply(this)
        super.onCreate(savedInstanceState)
        binding = ActivityPlayerControlsBinding.inflate(layoutInflater)
        setContentView(binding.root)

        binding.backButton.setOnClickListener { finish() }
        visible = PlayerControlRegistry.sanitizeVisible(AppPrefs.playerControls).toMutableList()
        render()
    }

    private fun render() {
        val content = binding.content
        content.removeAllViews()

        content.addView(sectionHeader(getString(R.string.player_controls_visible)))
        content.addView(sectionHint(getString(R.string.player_controls_visible_hint)))
        if (visible.isEmpty()) {
            content.addView(emptyState(getString(R.string.player_controls_empty_visible)))
        } else {
            visible.forEachIndexed { index, id ->
                val control = PlayerControlRegistry.byId[id] ?: return@forEachIndexed
                content.addView(visibleRow(control, index))
            }
        }

        content.addView(sectionHeader(getString(R.string.player_controls_available)))
        content.addView(sectionHint(getString(R.string.player_controls_available_hint)))
        val available = PlayerControlRegistry.availableFor(visible)
        if (available.isEmpty()) {
            content.addView(emptyState(getString(R.string.player_controls_empty_available)))
        } else {
            available.forEach { control ->
                content.addView(availableRow(control))
            }
        }

        content.addView(sectionHeader(getString(R.string.player_controls_speed_section)))
        content.addView(speedPresetCard())
        content.addView(menuRow(getString(R.string.player_controls_default_speed), speedLabel(AppPrefs.playerDefaultSpeed)) {
            showDefaultSpeedPicker()
        })
        content.addView(
            menuRow(
                getString(R.string.player_controls_timeout),
                getString(R.string.player_controls_timeout_fmt, AppPrefs.playerControlsTimeout / 1000)
            ) { showTimeoutPicker() }
        )

        content.addView(
            actionButton(getString(R.string.player_controls_restore)) {
                visible = PlayerControlRegistry.defaultVisibleIds.toMutableList()
                persistLayout()
                AppPrefs.playerSpeedPresets = AppPrefs.DEFAULT_SPEED_PRESETS
                AppPrefs.playerDefaultSpeed = 1f
                AppPrefs.playerControlsTimeout = 4000
                render()
            }
        )
    }

    // ----- Visible controls -----

    private fun visibleRow(control: PlayerControl, index: Int): LinearLayout {
        val row = controlCard(control)
        val actions = LinearLayout(this).apply {
            orientation = LinearLayout.HORIZONTAL
            gravity = Gravity.CENTER_VERTICAL
        }

        actions.addView(
            iconAction(R.drawable.ic_chevron_up, R.string.player_controls_move_up) {
                if (index > 0) {
                    visible.add(index - 1, visible.removeAt(index))
                    persistLayout()
                    render()
                }
            }.apply { alpha = if (index == 0) 0.3f else 1f }
        )
        actions.addView(
            iconAction(R.drawable.ic_chevron_up, R.string.player_controls_move_down) {
                if (index < visible.size - 1) {
                    visible.add(index + 1, visible.removeAt(index))
                    persistLayout()
                    render()
                }
            }.apply {
                rotation = 180f
                alpha = if (index == visible.size - 1) 0.3f else 1f
            }
        )
        actions.addView(
            iconAction(R.drawable.ic_close, R.string.player_controls_remove) {
                visible.removeAt(index)
                persistLayout()
                render()
            }
        )
        row.addView(actions)
        return row
    }

    private fun availableRow(control: PlayerControl): LinearLayout {
        val available = control.available(PlayerControlState())
        val row = controlCard(control, available)
        if (!available) {
            val reason = control.unavailableReasonRes
                ?.let { getString(R.string.player_controls_unavailable_reason, getString(it)) }
                ?: getString(R.string.player_controls_unavailable)
            row.addView(
                TextView(this).apply {
                    text = reason
                    textSize = 11f
                    setTextColor(getColor(R.color.on_surface_variant))
                    maxLines = 1
                    layoutParams = LinearLayout.LayoutParams(
                        0, ViewGroup.LayoutParams.WRAP_CONTENT, 1f
                    )
                }
            )
            return row
        }
        row.addView(
            iconAction(R.drawable.ic_add, R.string.player_controls_add) {
                if (!visible.contains(control.id)) {
                    // New controls land at the end so the existing order is
                    // never disturbed; the user can then move it up.
                    visible.add(control.id)
                    persistLayout()
                    render()
                }
            }
        )
        return row
    }

    // ----- Speed presets -----

    /** Toggle chips for the speed values the speed control will offer. */
    private fun speedPresetCard(): LinearLayout {
        val selected = AppPrefs.playerSpeedPresets
        val card = LinearLayout(this).apply {
            orientation = LinearLayout.VERTICAL
            setPadding(dp(14), dp(12), dp(14), dp(12))
            background = cardBackground()
            layoutParams = LinearLayout.LayoutParams(
                ViewGroup.LayoutParams.MATCH_PARENT, ViewGroup.LayoutParams.WRAP_CONTENT
            ).also { it.topMargin = dp(8) }
        }
        card.addView(
            TextView(this).apply {
                text = getString(R.string.player_controls_speed_presets)
                textSize = 13f
                setTextColor(getColor(R.color.on_surface_variant))
            }
        )
        val chips = LinearLayout(this).apply {
            orientation = LinearLayout.HORIZONTAL
        }
        PRESET_CHOICES.forEach { speed ->
            val on = selected.contains(speed)
            chips.addView(
                TextView(this).apply {
                    text = speedLabel(speed)
                    textSize = 13f
                    gravity = Gravity.CENTER
                    setPadding(dp(12), dp(8), dp(12), dp(8))
                    setTextColor(if (on) getColor(R.color.white) else getColor(R.color.on_surface))
                    background = GradientDrawable().apply {
                        cornerRadius = dp(20).toFloat()
                        setColor(
                            if (on) Themes.accentColor(this@PlayerControlsActivity)
                            else Themes.elevatedColor(this@PlayerControlsActivity)
                        )
                    }
                    isClickable = true
                    isFocusable = true
                    layoutParams = LinearLayout.LayoutParams(
                        ViewGroup.LayoutParams.WRAP_CONTENT, ViewGroup.LayoutParams.WRAP_CONTENT
                    ).also { it.marginEnd = dp(6) }
                    setOnClickListener { togglePreset(speed) }
                }
            )
        }
        card.addView(
            android.widget.HorizontalScrollView(this).apply {
                isHorizontalScrollBarEnabled = false
                layoutParams = LinearLayout.LayoutParams(
                    ViewGroup.LayoutParams.MATCH_PARENT, ViewGroup.LayoutParams.WRAP_CONTENT
                ).also { it.topMargin = dp(8) }
                addView(chips)
            }
        )
        return card
    }

    private fun togglePreset(speed: Float) {
        val current = AppPrefs.playerSpeedPresets.toMutableList()
        if (current.contains(speed)) {
            // Never allow the list to become empty: the speed menu would have
            // nothing to offer.
            if (current.size <= 1) return
            current.remove(speed)
        } else {
            current.add(speed)
        }
        AppPrefs.playerSpeedPresets = current.sorted()
        render()
    }

    private fun showDefaultSpeedPicker() {
        val presets = AppPrefs.playerSpeedPresets
        val labels = presets.map { speedLabel(it) }.toTypedArray()
        val current = presets.indexOfFirst { it == AppPrefs.playerDefaultSpeed }
        AlertDialog.Builder(this)
            .setTitle(R.string.player_controls_default_speed)
            .setSingleChoiceItems(labels, if (current >= 0) current else -1) { dialog, which ->
                AppPrefs.playerDefaultSpeed = presets[which]
                dialog.dismiss()
                render()
            }
            .setNegativeButton(android.R.string.cancel, null)
            .show()
    }

    private fun showTimeoutPicker() {
        val choices = AppPrefs.CONTROLS_TIMEOUT_CHOICES
        val labels = choices
            .map { getString(R.string.player_controls_timeout_fmt, it / 1000) }
            .toTypedArray()
        val current = choices.indexOf(AppPrefs.playerControlsTimeout)
        AlertDialog.Builder(this)
            .setTitle(R.string.player_controls_timeout)
            .setSingleChoiceItems(labels, if (current >= 0) current else 2) { dialog, which ->
                AppPrefs.playerControlsTimeout = choices[which]
                dialog.dismiss()
                render()
            }
            .setNegativeButton(android.R.string.cancel, null)
            .show()
    }

    private fun speedLabel(speed: Float): String =
        if (speed == 1f) getString(R.string.player_controls_speed_normal)
        else getString(R.string.player_controls_speed_preset_fmt, trimFloat(speed))

    private fun trimFloat(value: Float): String =
        if (value % 1f == 0f) value.toInt().toString()
        else value.toString().trimEnd('0').trimEnd('.')

    // ----- Persistence -----

    private fun persistLayout() {
        // De-duplicate defensively before saving: the same control can never
        // appear twice, whatever path added it.
        AppPrefs.playerControls = visible.distinct()
    }

    // ----- Row builders -----

    private fun controlCard(control: PlayerControl, enabled: Boolean = true): LinearLayout =
        LinearLayout(this).apply {
            orientation = LinearLayout.HORIZONTAL
            gravity = Gravity.CENTER_VERTICAL
            setPadding(dp(14), dp(10), dp(10), dp(10))
            background = cardBackground()
            layoutParams = LinearLayout.LayoutParams(
                ViewGroup.LayoutParams.MATCH_PARENT, ViewGroup.LayoutParams.WRAP_CONTENT
            ).also { it.topMargin = dp(8) }
            alpha = if (enabled) 1f else 0.55f

            addView(
                ImageView(this@PlayerControlsActivity).apply {
                    setImageResource(control.iconRes)
                    setColorFilter(getColor(R.color.on_surface))
                    layoutParams = LinearLayout.LayoutParams(dp(24), dp(24))
                    importantForAccessibility = View.IMPORTANT_FOR_ACCESSIBILITY_NO
                }
            )
            addView(
                TextView(this@PlayerControlsActivity).apply {
                    text = getString(control.labelRes)
                    textSize = 15f
                    maxLines = 1
                    ellipsize = TextUtils.TruncateAt.END
                    setTextColor(getColor(R.color.on_surface))
                    setTypeface(null, Typeface.BOLD)
                    layoutParams = LinearLayout.LayoutParams(
                        0, ViewGroup.LayoutParams.WRAP_CONTENT, 1f
                    ).also { it.marginStart = dp(12) }
                }
            )
        }

    private fun iconAction(icon: Int, contentDesc: Int, onClick: () -> Unit): ImageButton =
        ImageButton(this).apply {
            setImageResource(icon)
            setColorFilter(getColor(R.color.on_surface_variant))
            background = selectableBackground()
            contentDescription = getString(contentDesc)
            setPadding(dp(8), dp(8), dp(8), dp(8))
            layoutParams = LinearLayout.LayoutParams(dp(36), dp(36)).also { it.marginStart = dp(2) }
            setOnClickListener { onClick() }
        }

    private fun sectionHeader(text: String): TextView =
        TextView(this).apply {
            this.text = text
            textSize = 17f
            setTypeface(null, Typeface.BOLD)
            setTextColor(getColor(R.color.on_surface))
            layoutParams = LinearLayout.LayoutParams(
                ViewGroup.LayoutParams.MATCH_PARENT, ViewGroup.LayoutParams.WRAP_CONTENT
            ).also { it.topMargin = dp(18) }
        }

    private fun sectionHint(text: String): TextView =
        TextView(this).apply {
            this.text = text
            textSize = 12f
            setTextColor(getColor(R.color.on_surface_variant))
            layoutParams = LinearLayout.LayoutParams(
                ViewGroup.LayoutParams.MATCH_PARENT, ViewGroup.LayoutParams.WRAP_CONTENT
            ).also { it.topMargin = dp(2) }
        }

    private fun emptyState(text: String): TextView =
        TextView(this).apply {
            this.text = text
            textSize = 13f
            setTextColor(getColor(R.color.on_surface_variant))
            setPadding(dp(14), dp(14), dp(14), dp(14))
            background = cardBackground()
            layoutParams = LinearLayout.LayoutParams(
                ViewGroup.LayoutParams.MATCH_PARENT, ViewGroup.LayoutParams.WRAP_CONTENT
            ).also { it.topMargin = dp(8) }
        }

    private fun menuRow(mainLabel: String, valueLabel: String, onClick: () -> Unit): LinearLayout =
        LinearLayout(this).apply {
            orientation = LinearLayout.HORIZONTAL
            gravity = Gravity.CENTER_VERTICAL
            setPadding(dp(16), dp(15), dp(16), dp(15))
            isClickable = true
            isFocusable = true
            background = cardBackground()
            layoutParams = LinearLayout.LayoutParams(
                ViewGroup.LayoutParams.MATCH_PARENT, ViewGroup.LayoutParams.WRAP_CONTENT
            ).also { it.topMargin = dp(10) }
            setOnClickListener { onClick() }

            addView(
                TextView(this@PlayerControlsActivity).apply {
                    text = mainLabel
                    textSize = 15f
                    setTextColor(getColor(R.color.on_surface))
                    setTypeface(null, Typeface.BOLD)
                    layoutParams = LinearLayout.LayoutParams(
                        0, ViewGroup.LayoutParams.WRAP_CONTENT, 1f
                    )
                }
            )
            addView(
                TextView(this@PlayerControlsActivity).apply {
                    text = valueLabel
                    textSize = 13f
                    setTextColor(getColor(R.color.on_surface_variant))
                }
            )
        }

    private fun actionButton(label: String, onClick: () -> Unit): TextView =
        TextView(this).apply {
            text = label
            textSize = 14f
            gravity = Gravity.CENTER
            setTypeface(null, Typeface.BOLD)
            setTextColor(getColor(R.color.white))
            setPadding(dp(16), dp(14), dp(16), dp(14))
            background = GradientDrawable().apply {
                cornerRadius = dp(12).toFloat()
                setColor(Themes.accentColor(this@PlayerControlsActivity))
            }
            isClickable = true
            isFocusable = true
            layoutParams = LinearLayout.LayoutParams(
                ViewGroup.LayoutParams.MATCH_PARENT, ViewGroup.LayoutParams.WRAP_CONTENT
            ).also { it.topMargin = dp(20) }
            setOnClickListener { onClick() }
        }

    private fun cardBackground(): GradientDrawable = GradientDrawable().apply {
        cornerRadius = dp(12).toFloat()
        setColor(Themes.elevatedColor(this@PlayerControlsActivity))
    }

    private fun selectableBackground(): android.graphics.drawable.Drawable? {
        val tv = TypedValue()
        theme.resolveAttribute(android.R.attr.selectableItemBackgroundBorderless, tv, true)
        return if (tv.resourceId != 0) getDrawable(tv.resourceId) else null
    }

    private fun dp(value: Int): Int = TypedValue.applyDimension(
        TypedValue.COMPLEX_UNIT_DIP, value.toFloat(), resources.displayMetrics
    ).toInt()

    private companion object {
        val PRESET_CHOICES = listOf(0.25f, 0.5f, 0.75f, 1f, 1.25f, 1.5f, 1.75f, 2f, 2.5f, 3f)
    }
}
