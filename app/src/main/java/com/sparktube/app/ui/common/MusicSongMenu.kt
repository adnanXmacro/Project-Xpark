package com.sparktube.app.ui.common

import android.content.Context
import android.view.View
import android.widget.PopupMenu
import android.widget.Toast
import com.sparktube.app.R
import com.sparktube.app.data.ArtistEntry
import com.sparktube.app.data.LocalStore
import com.sparktube.app.playback.PlaybackCenter
import com.sparktube.app.ui.music.ArtistActivity
import com.sparktube.app.ui.music.NowPlayingActivity

/** Overflow actions shared by every music song row. */
object MusicSongMenu {

    fun playNext(context: Context, model: VideoUiModel) {
        val started = PlaybackCenter.playNextMusic(model.toQueueEntry(isMusic = true))
        if (started) {
            NowPlayingActivity.start(context)
        } else {
            Toast.makeText(context, R.string.play_next_queued, Toast.LENGTH_SHORT).show()
        }
    }

    fun show(
        anchor: View,
        model: VideoUiModel,
        play: Boolean = false,
        goToArtist: Boolean = false,
        addToPlaylist: Boolean = false,
        starArtist: Boolean = false,
        removeFromPlaylist: (() -> Unit)? = null,
        onStarChanged: (() -> Unit)? = null
    ) {
        val context = anchor.context
        val menu = PopupMenu(context, anchor)
        var order = 0
        menu.menu.add(0, ID_PLAY_NEXT, order++, R.string.action_play_next)
        if (play) menu.menu.add(0, ID_PLAY, order++, R.string.cd_play_song)
        if (goToArtist) menu.menu.add(0, ID_ARTIST, order++, R.string.go_to_artist)
        if (addToPlaylist) menu.menu.add(0, ID_PLAYLIST, order++, R.string.action_playlist)
        if (starArtist && model.uploader.isNotBlank()) {
            val starred = LocalStore.isFavoriteArtist(context, model.uploader)
            menu.menu.add(
                0,
                ID_STAR,
                order++,
                if (starred) R.string.artist_unstar else R.string.artist_star
            )
        }
        if (removeFromPlaylist != null) {
            menu.menu.add(0, ID_REMOVE, order, R.string.action_remove_from_playlist)
        }
        menu.setOnMenuItemClickListener { item ->
            when (item.itemId) {
                ID_PLAY_NEXT -> playNext(context, model)
                ID_PLAY -> {
                    PlaybackCenter.playMusic(model.toQueueEntry(isMusic = true), radio = true)
                    NowPlayingActivity.start(context)
                }
                ID_ARTIST -> ArtistActivity.start(
                    context,
                    model.uploader,
                    model.uploaderAvatarUrl.ifBlank { model.thumbnailUrl }
                )
                ID_PLAYLIST -> PlaylistSheets.showAdd(
                    context,
                    model.toEntry().copy(isMusic = true),
                    music = true
                )
                ID_STAR -> toggleStar(context, model, onStarChanged)
                ID_REMOVE -> removeFromPlaylist?.invoke()
            }
            true
        }
        menu.show()
    }

    private fun toggleStar(context: Context, model: VideoUiModel, onStarChanged: (() -> Unit)?) {
        val now = LocalStore.toggleFavoriteArtist(
            context,
            ArtistEntry(
                name = model.uploader,
                avatarUrl = model.uploaderAvatarUrl.ifBlank { model.thumbnailUrl }
            )
        )
        Toast.makeText(
            context,
            if (now) R.string.artist_added else R.string.artist_removed,
            Toast.LENGTH_SHORT
        ).show()
        onStarChanged?.invoke()
    }

    private const val ID_PLAY_NEXT = 1
    private const val ID_PLAY = 2
    private const val ID_ARTIST = 3
    private const val ID_PLAYLIST = 4
    private const val ID_STAR = 5
    private const val ID_REMOVE = 6
}
