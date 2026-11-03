package com.sparktube.app.ui.music

import android.content.Context
import android.content.Intent
import android.net.Uri
import android.os.Bundle
import android.widget.Toast
import androidx.appcompat.app.AppCompatActivity
import com.sparktube.app.R
import com.sparktube.app.data.LocalStore
import com.sparktube.app.data.VideoEntry
import com.sparktube.app.util.PlaylistHydrator
import com.sparktube.app.util.PlaylistShare
import com.sparktube.app.util.Themes

class PlaylistImportActivity : AppCompatActivity() {

    override fun onCreate(savedInstanceState: Bundle?) {
        Themes.apply(this)
        super.onCreate(savedInstanceState)
        handle(intent)
    }

    override fun onNewIntent(intent: Intent) {
        super.onNewIntent(intent)
        setIntent(intent)
        handle(intent)
    }

    private fun handle(intent: Intent?) {
        val payload = PlaylistShare.fromUri(intent?.data ?: Uri.EMPTY)
            ?: PlaylistShare.decode(intent?.getStringExtra(Intent.EXTRA_TEXT))
        if (payload == null) {
            Toast.makeText(this, R.string.invalid_playlist_link, Toast.LENGTH_SHORT).show()
            finish()
            return
        }
        val created = importPayload(this, payload)
        if (created != null) {
            Toast.makeText(this, R.string.playlist_imported, Toast.LENGTH_SHORT).show()
            MusicPlaylistActivity.start(this, created.id)
        } else {
            Toast.makeText(this, R.string.invalid_playlist_link, Toast.LENGTH_SHORT).show()
        }
        finish()
    }

    companion object {
        fun importPayload(context: Context, payload: PlaylistShare.Payload): com.sparktube.app.data.Playlist? {
            val fallback = context.getString(R.string.shared_playlist_fallback)
            val name = LocalStore.uniqueMusicPlaylistName(
                context,
                payload.name.ifBlank { fallback }
            )
            val playlist = LocalStore.createMusicPlaylist(context, name) ?: return null
            payload.ids.forEach { id ->
                LocalStore.addToMusicPlaylist(
                    context,
                    playlist.id,
                    VideoEntry(
                        url = PlaylistShare.watchUrl(id),
                        title = id,
                        uploader = "",
                        thumbnailUrl = PlaylistShare.thumbUrl(id),
                        durationSec = 0L,
                        isMusic = true
                    )
                )
            }
            PlaylistHydrator.start(context, playlist.id)
            return LocalStore.musicPlaylist(context, playlist.id) ?: playlist
        }
    }
}
