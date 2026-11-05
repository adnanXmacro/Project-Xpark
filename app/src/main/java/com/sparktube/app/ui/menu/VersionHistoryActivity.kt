package com.sparktube.app.ui.menu

import android.content.Context
import android.content.Intent
import android.graphics.Typeface
import android.os.Bundle
import android.util.TypedValue
import android.view.ViewGroup
import android.widget.LinearLayout
import android.widget.TextView
import androidx.appcompat.app.AppCompatActivity
import com.sparktube.app.R
import com.sparktube.app.databinding.ActivitySettingsPageBinding
import com.sparktube.app.ui.update.UpdatePrompt
import com.sparktube.app.util.Changelog
import com.sparktube.app.util.Themes

class VersionHistoryActivity : AppCompatActivity() {

    private lateinit var binding: ActivitySettingsPageBinding

    override fun onCreate(savedInstanceState: Bundle?) {
        Themes.apply(this)
        super.onCreate(savedInstanceState)
        binding = ActivitySettingsPageBinding.inflate(layoutInflater)
        setContentView(binding.root)

        binding.title.setText(R.string.menu_version_history)
        binding.backButton.setOnClickListener { finish() }
        bindVersions()
    }

    override fun onResume() {
        super.onResume()
        if (Themes.recreateIfNeeded(this)) return
    }

    private fun bindVersions() {
        val parent = binding.content
        parent.removeAllViews()
        Changelog.knownVersions().forEachIndexed { index, version ->
            parent.addView(versionHeading(version, first = index == 0))
            val block = LinearLayout(this).apply {
                orientation = LinearLayout.VERTICAL
                layoutParams = LinearLayout.LayoutParams(
                    ViewGroup.LayoutParams.MATCH_PARENT,
                    ViewGroup.LayoutParams.WRAP_CONTENT
                )
            }
            UpdatePrompt.renderInto(this, block, Changelog.fallback(this, version))
            parent.addView(block)
        }
    }

    private fun versionHeading(version: String, first: Boolean): TextView =
        TextView(this).apply {
            text = getString(R.string.version_history_heading_fmt, version)
            setTextColor(getColor(R.color.on_surface))
            setTypeface(null, Typeface.BOLD)
            textSize = 16f
            layoutParams = LinearLayout.LayoutParams(
                ViewGroup.LayoutParams.MATCH_PARENT,
                ViewGroup.LayoutParams.WRAP_CONTENT
            ).also { it.topMargin = dp(if (first) 4 else 28) }
        }

    private fun dp(value: Int): Int = TypedValue.applyDimension(
        TypedValue.COMPLEX_UNIT_DIP, value.toFloat(), resources.displayMetrics
    ).toInt()

    companion object {
        fun start(context: Context) {
            context.startActivity(Intent(context, VersionHistoryActivity::class.java))
        }
    }
}
