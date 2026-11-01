package com.sparktube.app.ui.menu

import android.content.Context
import android.content.Intent
import android.content.pm.PackageManager
import android.graphics.Typeface
import android.net.Uri
import android.os.Build
import android.provider.Settings
import android.util.TypedValue
import android.view.Gravity
import android.widget.LinearLayout
import android.widget.TextView
import android.widget.Toast
import androidx.appcompat.app.AlertDialog
import com.sparktube.app.R
import com.sparktube.app.util.Themes

/**
 * Step-by-step guide to clear YouTube as the default link handler and enable
 * Open Tube. Never changes another app's defaults itself.
 */
object OpenYoutubeLinksWizard {

    private const val YOUTUBE_PKG = "com.google.android.youtube"
    private const val YT_CREATE_PKG = "com.google.android.apps.youtube.creator"
    private const val SAMPLE_URL = "https://youtu.be/"

    fun show(context: Context) {
        showStep(context, 1)
    }

    private fun showStep(context: Context, step: Int) {
        val dialog = AlertDialog.Builder(context).create()
        val root = LinearLayout(context).apply {
            orientation = LinearLayout.VERTICAL
            setPadding(dp(context, 24), dp(context, 20), dp(context, 24), dp(context, 8))
        }
        root.addView(
            TextView(context).apply {
                text = context.getString(R.string.links_wizard_title)
                textSize = 18f
                setTypeface(null, Typeface.BOLD)
                setTextColor(Themes.onSurfaceColor(context))
            }
        )
        root.addView(
            TextView(context).apply {
                text = context.getString(R.string.links_wizard_step_fmt, step)
                textSize = 12f
                setPadding(0, dp(context, 4), 0, 0)
                setTextColor(Themes.onSurfaceVariantColor(context))
            }
        )
        val (title, body) = when (step) {
            1 -> R.string.links_wizard_step1_title to R.string.links_wizard_step1_body
            2 -> R.string.links_wizard_step2_title to R.string.links_wizard_step2_body
            else -> R.string.links_wizard_step3_title to R.string.links_wizard_step3_body
        }
        root.addView(
            TextView(context).apply {
                text = context.getString(title)
                textSize = 16f
                setTypeface(null, Typeface.BOLD)
                setPadding(0, dp(context, 14), 0, 0)
                setTextColor(Themes.onSurfaceColor(context))
            }
        )
        root.addView(
            TextView(context).apply {
                text = context.getString(body)
                textSize = 14f
                setPadding(0, dp(context, 8), 0, dp(context, 12))
                setTextColor(Themes.onSurfaceColor(context))
            }
        )
        dialog.setView(root)
        dialog.setTitle(null)

        when (step) {
            1 -> {
                val youtubeInstalled = isInstalled(context, YOUTUBE_PKG)
                dialog.setButton(AlertDialog.BUTTON_POSITIVE, context.getString(R.string.links_wizard_open_youtube)) { _, _ ->
                    openPackageSettings(context, YOUTUBE_PKG)
                    if (isInstalled(context, YT_CREATE_PKG)) {
                        openPackageSettings(context, YT_CREATE_PKG)
                    }
                    showStep(context, 1)
                }
                dialog.setButton(
                    AlertDialog.BUTTON_NEUTRAL,
                    context.getString(if (youtubeInstalled) R.string.links_wizard_next else R.string.links_wizard_skip)
                ) { _, _ -> showStep(context, 2) }
                dialog.setButton(AlertDialog.BUTTON_NEGATIVE, context.getString(android.R.string.cancel)) { _, _ -> }
            }
            2 -> {
                dialog.setButton(AlertDialog.BUTTON_POSITIVE, context.getString(R.string.links_wizard_open_ours)) { _, _ ->
                    openOurLinkSettings(context)
                    showStep(context, 2)
                }
                dialog.setButton(AlertDialog.BUTTON_NEUTRAL, context.getString(R.string.links_wizard_next)) { _, _ ->
                    showStep(context, 3)
                }
                dialog.setButton(AlertDialog.BUTTON_NEGATIVE, context.getString(R.string.links_wizard_back)) { _, _ ->
                    showStep(context, 1)
                }
            }
            else -> {
                dialog.setButton(AlertDialog.BUTTON_POSITIVE, context.getString(R.string.links_wizard_try_link)) { _, _ ->
                    runCatching {
                        context.startActivity(Intent(Intent.ACTION_VIEW, Uri.parse(SAMPLE_URL)))
                    }
                }
                dialog.setButton(AlertDialog.BUTTON_NEUTRAL, context.getString(R.string.links_wizard_done)) { _, _ -> }
                dialog.setButton(AlertDialog.BUTTON_NEGATIVE, context.getString(R.string.links_wizard_back)) { _, _ ->
                    showStep(context, 2)
                }
            }
        }
        dialog.show()
        styleButtons(dialog)
    }

    private fun styleButtons(dialog: AlertDialog) {
        val accent = Themes.accentColor(dialog.context)
        dialog.getButton(AlertDialog.BUTTON_POSITIVE)?.setTextColor(accent)
        dialog.getButton(AlertDialog.BUTTON_NEUTRAL)?.setTextColor(accent)
        dialog.getButton(AlertDialog.BUTTON_NEGATIVE)?.setTextColor(Themes.onSurfaceVariantColor(dialog.context))
    }

    private fun isInstalled(context: Context, pkg: String): Boolean =
        runCatching {
            context.packageManager.getPackageInfo(pkg, 0)
            true
        }.getOrDefault(false)

    private fun openOurLinkSettings(context: Context) {
        val pkg = Uri.parse("package:${context.packageName}")
        val candidates = buildList {
            if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.S) {
                add(Intent(Settings.ACTION_APP_OPEN_BY_DEFAULT_SETTINGS, pkg))
            }
            add(Intent(Settings.ACTION_APPLICATION_DETAILS_SETTINGS, pkg))
        }
        val opened = candidates.any { intent ->
            runCatching { context.startActivity(intent) }.isSuccess
        }
        if (!opened) {
            Toast.makeText(context, R.string.settings_open_youtube_links_failed, Toast.LENGTH_SHORT).show()
        }
    }

    private fun openPackageSettings(context: Context, pkg: String) {
        val uri = Uri.parse("package:$pkg")
        val opened = runCatching {
            context.startActivity(Intent(Settings.ACTION_APPLICATION_DETAILS_SETTINGS, uri))
        }.isSuccess
        if (!opened) {
            Toast.makeText(context, R.string.settings_open_youtube_links_failed, Toast.LENGTH_SHORT).show()
        }
    }

    private fun dp(context: Context, value: Int): Int = TypedValue.applyDimension(
        TypedValue.COMPLEX_UNIT_DIP, value.toFloat(), context.resources.displayMetrics
    ).toInt()
}
