package com.sparktube.app.ui.update

import android.app.Activity
import android.app.Dialog
import android.graphics.Color
import android.graphics.Typeface
import android.graphics.drawable.ColorDrawable
import android.util.TypedValue
import android.view.LayoutInflater
import android.view.ViewGroup
import android.widget.LinearLayout
import android.widget.TextView
import com.sparktube.app.R
import com.sparktube.app.databinding.DialogNoticeBinding
import com.sparktube.app.databinding.DialogUpdateBinding
import com.sparktube.app.util.Changelog
import com.sparktube.app.util.ChangelogSection
import com.sparktube.app.util.UpdateChecker

/**
 * The two custom-styled dialogs used by the update flow — a rounded card
 * listing "what's new" and a small notice card. Replaces the plain system
 * AlertDialogs so the look matches the rest of Open Tube.
 */
object UpdatePrompt {

    /** Shows the "update available" card with the cleaned changelog. */
    fun show(activity: Activity, release: UpdateChecker.Release, onDownload: () -> Unit) {
        val binding = DialogUpdateBinding.inflate(LayoutInflater.from(activity))
        val dialog = Dialog(activity).apply { setContentView(binding.root) }

        val version = release.tag.removePrefix("v").removePrefix("V")
        binding.versionText.text = activity.getString(R.string.update_version_fmt, version)

        val sections = Changelog.parse(release.body)
            .ifEmpty { Changelog.fallback(activity, version) }
        renderSections(activity, binding.changelog, sections)

        binding.downloadButton.setOnClickListener {
            dialog.dismiss()
            onDownload()
        }
        binding.laterButton.setOnClickListener { dialog.dismiss() }

        sizeWindow(activity, dialog)
        dialog.show()
        clampChangelog(activity, binding)
    }

    /** Small confirmation card (used after the user taps "Download update"). */
    fun showNotice(
        activity: Activity,
        title: String,
        message: String,
        iconRes: Int = R.drawable.ic_update
    ) {
        val binding = DialogNoticeBinding.inflate(LayoutInflater.from(activity))
        val dialog = Dialog(activity).apply { setContentView(binding.root) }
        binding.noticeIcon.setImageResource(iconRes)
        binding.noticeTitle.text = title
        binding.noticeMessage.text = message
        binding.noticeButton.setOnClickListener { dialog.dismiss() }
        sizeWindow(activity, dialog)
        dialog.show()
    }

    // ----- Internals -----

    private fun renderSections(
        activity: Activity,
        parent: LinearLayout,
        sections: List<ChangelogSection>
    ) {
        parent.removeAllViews()
        sections.forEachIndexed { index, section ->
            section.title?.takeIf { it.isNotBlank() }?.let { title ->
                parent.addView(
                    sectionHeader(activity, title, topMargin = if (index == 0) 0 else dp(activity, 14))
                )
            }
            section.items.forEach { item -> parent.addView(bulletRow(activity, item)) }
        }
    }

    private fun sectionHeader(activity: Activity, title: String, topMargin: Int): TextView =
        TextView(activity).apply {
            text = title
            setTextColor(activity.getColor(R.color.on_surface))
            setTypeface(null, Typeface.BOLD)
            textSize = 13.5f
            letterSpacing = 0.02f
            layoutParams = LinearLayout.LayoutParams(
                ViewGroup.LayoutParams.MATCH_PARENT, ViewGroup.LayoutParams.WRAP_CONTENT
            ).also { it.topMargin = topMargin }
        }

    private fun bulletRow(activity: Activity, text: String): LinearLayout =
        LinearLayout(activity).apply {
            orientation = LinearLayout.HORIZONTAL
            layoutParams = LinearLayout.LayoutParams(
                ViewGroup.LayoutParams.MATCH_PARENT, ViewGroup.LayoutParams.WRAP_CONTENT
            ).also { it.topMargin = dp(activity, 7) }

            addView(
                TextView(activity).apply {
                    this.text = "•"
                    setTextColor(activity.getColor(R.color.on_surface_variant))
                    textSize = 14f
                    layoutParams = LinearLayout.LayoutParams(
                        ViewGroup.LayoutParams.WRAP_CONTENT, ViewGroup.LayoutParams.WRAP_CONTENT
                    ).also { it.marginEnd = dp(activity, 8) }
                }
            )
            addView(
                TextView(activity).apply {
                    this.text = text
                    setTextColor(activity.getColor(R.color.on_surface_variant))
                    textSize = 14f
                    setLineSpacing(dp(activity, 2).toFloat(), 1f)
                    layoutParams = LinearLayout.LayoutParams(
                        0, ViewGroup.LayoutParams.WRAP_CONTENT, 1f
                    )
                }
            )
        }

    private fun clampChangelog(activity: Activity, binding: DialogUpdateBinding) {
        binding.changelogScroll.post {
            val max = (activity.resources.displayMetrics.heightPixels * 0.42f).toInt()
            if (binding.changelog.height > max) {
                val lp = binding.changelogScroll.layoutParams
                lp.height = max
                binding.changelogScroll.layoutParams = lp
            }
        }
    }

    private fun sizeWindow(activity: Activity, dialog: Dialog) {
        dialog.window?.apply {
            setBackgroundDrawable(ColorDrawable(Color.TRANSPARENT))
            setLayout(
                (activity.resources.displayMetrics.widthPixels * 0.9f).toInt(),
                ViewGroup.LayoutParams.WRAP_CONTENT
            )
        }
    }

    private fun dp(activity: Activity, value: Int): Int = TypedValue.applyDimension(
        TypedValue.COMPLEX_UNIT_DIP, value.toFloat(), activity.resources.displayMetrics
    ).toInt()
}
