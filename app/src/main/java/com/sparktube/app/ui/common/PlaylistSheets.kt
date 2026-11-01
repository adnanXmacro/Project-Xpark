package com.sparktube.app.ui.common

import android.content.Context
import android.graphics.Typeface
import android.graphics.drawable.GradientDrawable
import android.util.TypedValue
import android.view.Gravity
import android.view.ViewGroup
import android.widget.EditText
import android.widget.FrameLayout
import android.widget.LinearLayout
import android.widget.TextView
import android.widget.Toast
import androidx.appcompat.app.AlertDialog
import com.google.android.material.bottomsheet.BottomSheetDialog
import com.sparktube.app.R
import com.sparktube.app.data.LocalStore
import com.sparktube.app.data.VideoEntry
import com.sparktube.app.util.Themes

object PlaylistSheets {

    fun showAdd(context: Context, entry: VideoEntry, music: Boolean) {
        val sheet = BottomSheetDialog(context)
        val root = sheetRoot(context)
        root.addView(sheetTitle(context, context.getString(R.string.playlist_sheet_title)))
        root.addView(
            sheetRow(context, "+ " + context.getString(R.string.new_playlist)) {
                sheet.dismiss()
                showNewDialog(context, entry, music)
            }
        )
        val playlists = if (music) LocalStore.musicPlaylists(context) else LocalStore.playlists(context)
        if (playlists.isEmpty()) {
            root.addView(
                hintRow(
                    context,
                    context.getString(
                        if (music) R.string.playlist_sheet_empty_music else R.string.playlist_sheet_empty
                    )
                )
            )
        } else {
            playlists.forEach { playlist ->
                val added = playlist.items.any { it.url == entry.url }
                root.addView(
                    sheetMenuRow(
                        context,
                        playlist.name,
                        if (added) context.getString(R.string.playlist_added) else ""
                    ) {
                        if (added) {
                            if (music) {
                                LocalStore.removeFromMusicPlaylist(context, playlist.id, entry.url)
                            } else {
                                LocalStore.removeFromPlaylist(context, playlist.id, entry.url)
                            }
                            Toast.makeText(
                                context,
                                context.getString(R.string.removed_from_playlist_fmt, playlist.name),
                                Toast.LENGTH_SHORT
                            ).show()
                        } else {
                            val ok = if (music) {
                                LocalStore.addToMusicPlaylist(context, playlist.id, entry)
                            } else {
                                LocalStore.addToPlaylist(context, playlist.id, entry)
                            }
                            if (ok) {
                                Toast.makeText(
                                    context,
                                    context.getString(R.string.saved_to_playlist_fmt, playlist.name),
                                    Toast.LENGTH_SHORT
                                ).show()
                            }
                        }
                        sheet.dismiss()
                    }
                )
            }
        }
        sheet.setContentView(root)
        sheet.show()
    }

    fun showNewDialog(
        context: Context,
        entry: VideoEntry? = null,
        music: Boolean,
        onCreated: (() -> Unit)? = null
    ) {
        val input = EditText(context).apply {
            hint = context.getString(R.string.new_playlist_hint)
            setSingleLine()
        }
        val container = FrameLayout(context).apply {
            setPadding(dp(context, 24), dp(context, 8), dp(context, 24), 0)
            addView(input)
        }
        AlertDialog.Builder(context)
            .setTitle(R.string.new_playlist)
            .setView(container)
            .setPositiveButton(R.string.create_playlist) { _, _ ->
                val name = input.text.toString().trim()
                when {
                    name.isEmpty() -> Toast.makeText(
                        context, R.string.playlist_name_empty, Toast.LENGTH_SHORT
                    ).show()
                    else -> {
                        val created = if (music) {
                            LocalStore.createMusicPlaylist(context, name)
                        } else {
                            LocalStore.createPlaylist(context, name)
                        }
                        if (created == null) {
                            Toast.makeText(context, R.string.playlist_name_exists, Toast.LENGTH_SHORT).show()
                        } else {
                            if (entry != null) {
                                if (music) {
                                    LocalStore.addToMusicPlaylist(context, created.id, entry)
                                } else {
                                    LocalStore.addToPlaylist(context, created.id, entry)
                                }
                                Toast.makeText(
                                    context,
                                    context.getString(R.string.saved_to_playlist_fmt, name),
                                    Toast.LENGTH_SHORT
                                ).show()
                            }
                            onCreated?.invoke()
                        }
                    }
                }
            }
            .setNegativeButton(android.R.string.cancel, null)
            .show()
    }

    fun showRenameDialog(context: Context, id: Long, currentName: String, music: Boolean, onRenamed: () -> Unit) {
        val input = EditText(context).apply {
            setText(currentName)
            hint = context.getString(R.string.new_playlist_hint)
            setSingleLine()
            setSelection(currentName.length)
        }
        val container = FrameLayout(context).apply {
            setPadding(dp(context, 24), dp(context, 8), dp(context, 24), 0)
            addView(input)
        }
        AlertDialog.Builder(context)
            .setTitle(R.string.rename_playlist)
            .setView(container)
            .setPositiveButton(android.R.string.ok) { _, _ ->
                val name = input.text.toString().trim()
                when {
                    name.isEmpty() -> Toast.makeText(
                        context, R.string.playlist_name_empty, Toast.LENGTH_SHORT
                    ).show()
                    else -> {
                        val ok = if (music) {
                            LocalStore.renameMusicPlaylist(context, id, name)
                        } else {
                            LocalStore.renamePlaylist(context, id, name)
                        }
                        if (ok) {
                            Toast.makeText(context, R.string.playlist_renamed, Toast.LENGTH_SHORT).show()
                            onRenamed()
                        } else {
                            Toast.makeText(context, R.string.playlist_name_exists, Toast.LENGTH_SHORT).show()
                        }
                    }
                }
            }
            .setNegativeButton(android.R.string.cancel, null)
            .show()
    }

    private fun sheetRoot(context: Context): LinearLayout = LinearLayout(context).apply {
        orientation = LinearLayout.VERTICAL
        setPadding(dp(context, 20), dp(context, 16), dp(context, 20), dp(context, 28))
    }

    private fun sheetTitle(context: Context, text: String): TextView = TextView(context).apply {
        this.text = text
        textSize = 18f
        setTextColor(Themes.onSurfaceColor(context))
        setTypeface(null, Typeface.BOLD)
        setPadding(0, 0, 0, dp(context, 8))
    }

    private fun hintRow(context: Context, text: String): TextView = TextView(context).apply {
        this.text = text
        textSize = 13f
        setPadding(dp(context, 4), dp(context, 10), dp(context, 4), dp(context, 6))
        setTextColor(context.getColor(R.color.on_surface_variant))
    }

    private fun sheetRow(context: Context, label: String, onClick: () -> Unit): LinearLayout =
        sheetMenuRow(context, label, "", onClick)

    private fun sheetMenuRow(
        context: Context,
        mainLabel: String,
        valueLabel: String,
        onClick: () -> Unit
    ): LinearLayout = LinearLayout(context).apply {
        orientation = LinearLayout.HORIZONTAL
        gravity = Gravity.CENTER_VERTICAL
        setPadding(dp(context, 14), dp(context, 14), dp(context, 14), dp(context, 14))
        background = GradientDrawable().apply {
            cornerRadius = dp(context, 12).toFloat()
            setColor(Themes.elevatedColor(context))
        }
        layoutParams = LinearLayout.LayoutParams(
            ViewGroup.LayoutParams.MATCH_PARENT, ViewGroup.LayoutParams.WRAP_CONTENT
        ).also { it.topMargin = dp(context, 8) }
        isClickable = true
        isFocusable = true
        addView(
            TextView(context).apply {
                text = mainLabel
                textSize = 15f
                setTextColor(Themes.onSurfaceColor(context))
                layoutParams = LinearLayout.LayoutParams(0, ViewGroup.LayoutParams.WRAP_CONTENT, 1f)
            }
        )
        if (valueLabel.isNotBlank()) {
            addView(
                TextView(context).apply {
                    text = valueLabel
                    textSize = 13f
                    setTextColor(Themes.accentColor(context))
                }
            )
        }
        setOnClickListener { onClick() }
    }

    private fun dp(context: Context, value: Int): Int = TypedValue.applyDimension(
        TypedValue.COMPLEX_UNIT_DIP, value.toFloat(), context.resources.displayMetrics
    ).toInt()
}
