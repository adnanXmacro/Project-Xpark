package com.sparktube.app.ui.common

import android.app.Activity
import android.content.Context
import android.view.View
import android.widget.PopupMenu
import android.widget.Toast
import com.sparktube.app.R
import com.sparktube.app.data.LocalStore
import com.sparktube.app.data.YtRepository
import com.sparktube.app.playback.PlaybackCenter
import com.sparktube.app.playback.StreamCatalog
import com.sparktube.app.ui.player.DownloadSheet
import com.sparktube.app.util.Formatters
import kotlinx.coroutines.CoroutineScope
import kotlinx.coroutines.launch

/** Overflow actions on a video row: favorite, download, save to queue. */
object VideoMenu {

    fun show(anchor: View, model: VideoUiModel, scope: CoroutineScope) {
        val context = anchor.context
        val fav = LocalStore.isFavorite(context, model.url)
        val menu = PopupMenu(context, anchor)
        menu.menu.add(
            0,
            ID_FAVORITE,
            0,
            if (fav) R.string.action_unfavorite else R.string.action_favorite
        )
        menu.menu.add(0, ID_DOWNLOAD, 1, R.string.action_download)
        menu.menu.add(0, ID_QUEUE, 2, R.string.action_add_to_queue)
        menu.setOnMenuItemClickListener { item ->
            when (item.itemId) {
                ID_FAVORITE -> toggleFavorite(context, model)
                ID_DOWNLOAD -> scope.launch { openDownload(context, model) }
                ID_QUEUE -> enqueue(context, model)
            }
            true
        }
        menu.show()
    }

    private fun toggleFavorite(context: Context, model: VideoUiModel) {
        val now = LocalStore.toggleFavorite(context, model.toEntry())
        Toast.makeText(
            context,
            if (now) R.string.added_to_favorites else R.string.removed_from_favorites,
            Toast.LENGTH_SHORT
        ).show()
    }

    private fun enqueue(context: Context, model: VideoUiModel) {
        val added = PlaybackCenter.enqueueVideo(model.toQueueEntry())
        Toast.makeText(
            context,
            if (added) R.string.added_to_queue else R.string.already_in_queue,
            Toast.LENGTH_SHORT
        ).show()
    }

    private suspend fun openDownload(context: Context, model: VideoUiModel) {
        Toast.makeText(context, R.string.loading_formats, Toast.LENGTH_SHORT).show()
        try {
            val info = YtRepository.streamInfo(model.url)
            val catalog = StreamCatalog(info)
            val entry = model.toQueueEntry()
            if (context is Activity && (context.isFinishing || context.isDestroyed)) return
            DownloadSheet(context, entry, catalog).show()
        } catch (e: Exception) {
            if (context is Activity && (context.isFinishing || context.isDestroyed)) return
            Toast.makeText(context, Formatters.friendlyException(e), Toast.LENGTH_SHORT).show()
        }
    }

    private const val ID_FAVORITE = 1
    private const val ID_DOWNLOAD = 2
    private const val ID_QUEUE = 3
}
