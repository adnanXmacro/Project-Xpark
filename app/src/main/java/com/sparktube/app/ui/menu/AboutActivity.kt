package com.sparktube.app.ui.menu

import android.content.Context
import android.content.Intent
import android.os.Bundle
import android.util.TypedValue
import android.view.Gravity
import android.widget.LinearLayout
import android.widget.TextView
import androidx.appcompat.app.AppCompatActivity
import com.sparktube.app.BuildConfig
import com.sparktube.app.R
import com.sparktube.app.databinding.ActivityAboutBinding
import com.sparktube.app.util.AppPrefs
import com.sparktube.app.util.Formatters
import com.sparktube.app.util.HelpLine
import com.sparktube.app.util.Themes

class AboutActivity : AppCompatActivity() {

    private lateinit var binding: ActivityAboutBinding

    override fun onCreate(savedInstanceState: Bundle?) {
        Themes.apply(this)
        super.onCreate(savedInstanceState)
        binding = ActivityAboutBinding.inflate(layoutInflater)
        setContentView(binding.root)

        binding.backButton.setOnClickListener { finish() }
        binding.version.text = getString(R.string.version_fmt, BuildConfig.VERSION_NAME)
        binding.rowHelpLine.setOnClickListener { HelpLine.openEmail(this) }

        bindCredits()
    }

    override fun onResume() {
        super.onResume()
        if (Themes.recreateIfNeeded(this)) return
        bindCredits()
    }

    private fun bindCredits() {
        val credits = binding.credits
        credits.removeAllViews()
        credits.addView(creditRow(getString(R.string.about_developed_by), getString(R.string.about_developed_by_value)))
        credits.addView(creditRow(getString(R.string.about_publisher), getString(R.string.about_publisher_value)))
        credits.addView(creditRow(getString(R.string.about_built_on), getString(R.string.about_built_on_value)))
        credits.addView(
            creditRow(
                getString(R.string.about_downloaded),
                Formatters.formatFileSize(AppPrefs.totalBytesDownloaded)
            )
        )
    }

    private fun creditRow(label: String, value: String): LinearLayout {
        val pad = dp(12)
        return LinearLayout(this).apply {
            orientation = LinearLayout.HORIZONTAL
            gravity = Gravity.CENTER_VERTICAL
            setPadding(0, pad, 0, pad)
            addView(
                TextView(this@AboutActivity).apply {
                    text = label
                    textSize = 14f
                    setTextColor(getColor(R.color.on_surface_variant))
                    layoutParams = LinearLayout.LayoutParams(0, LinearLayout.LayoutParams.WRAP_CONTENT, 1f)
                }
            )
            addView(
                TextView(this@AboutActivity).apply {
                    text = value
                    textSize = 14f
                    setTextColor(getColor(R.color.on_surface))
                }
            )
        }
    }

    private fun dp(value: Int): Int = TypedValue.applyDimension(
        TypedValue.COMPLEX_UNIT_DIP, value.toFloat(), resources.displayMetrics
    ).toInt()

    companion object {
        fun start(context: Context) {
            context.startActivity(Intent(context, AboutActivity::class.java))
        }
    }
}
