package com.sparktube.app.ui.music

import android.content.Context
import android.content.Intent
import android.os.Bundle
import android.widget.Toast
import androidx.appcompat.app.AlertDialog
import androidx.appcompat.app.AppCompatActivity
import androidx.core.view.isVisible
import androidx.recyclerview.widget.LinearLayoutManager
import com.sparktube.app.R
import com.sparktube.app.data.LocalStore
import com.sparktube.app.databinding.ActivityPlaylistBinding
import com.sparktube.app.playback.PlaybackCenter
import com.sparktube.app.ui.common.MusicRowAdapter
import com.sparktube.app.ui.common.PlaylistSheets
import com.sparktube.app.ui.common.VideoUiModel
import com.sparktube.app.ui.common.toQueueEntry
import com.sparktube.app.ui.common.toUiModel
import com.sparktube.app.util.Themes

class MusicPlaylistActivity : AppCompatActivity() {

    private lateinit var binding: ActivityPlaylistBinding
    private lateinit var adapter: MusicRowAdapter
    private var playlistId = -1L
    private var songs: List<VideoUiModel> = emptyList()

    override fun onCreate(savedInstanceState: Bundle?) {
        Themes.apply(this)
        super.onCreate(savedInstanceState)
        binding = ActivityPlaylistBinding.inflate(layoutInflater)
        setContentView(binding.root)

        playlistId = intent.getLongExtra(EXTRA_ID, -1L)
        adapter = MusicRowAdapter(
            onClick = { model -> playFrom(songs.indexOfFirst { it.url == model.url }) },
            onLongClick = { model ->
                confirmRemove(model)
            }
        )
        binding.list.layoutManager = LinearLayoutManager(this)
        binding.list.adapter = adapter
        binding.backButton.setOnClickListener { finish() }
        binding.playAllButton.setOnClickListener { playFrom(0) }
        binding.title.setOnLongClickListener {
            val playlist = LocalStore.musicPlaylist(this, playlistId) ?: return@setOnLongClickListener true
            PlaylistSheets.showRenameDialog(this, playlist.id, playlist.name, music = true) { reload() }
            true
        }
    }

    override fun onResume() {
        super.onResume()
        if (Themes.recreateIfNeeded(this)) return
        reload()
    }

    private fun reload() {
        val playlist = LocalStore.musicPlaylist(this, playlistId)
        if (playlist == null) {
            finish()
            return
        }
        binding.title.text = playlist.name
        songs = playlist.items.map { it.toUiModel() }
        adapter.submitList(songs)
        binding.playAllRow.isVisible = songs.isNotEmpty()
        binding.videoCount.text = resources.getQuantityString(
            R.plurals.playlist_song_count, songs.size, songs.size
        )
        binding.emptyView.setText(R.string.empty_playlist_songs)
        binding.emptyView.isVisible = songs.isEmpty()
    }

    private fun playFrom(index: Int) {
        if (songs.isEmpty()) return
        PlaybackCenter.playMusicQueue(songs.map { it.toQueueEntry(isMusic = true) }, index.coerceAtLeast(0))
        NowPlayingActivity.start(this)
    }

    private fun confirmRemove(model: VideoUiModel) {
        AlertDialog.Builder(this)
            .setTitle(R.string.remove_from_playlist_confirm)
            .setMessage(model.title)
            .setPositiveButton(android.R.string.ok) { _, _ ->
                LocalStore.removeFromMusicPlaylist(this, playlistId, model.url)
                Toast.makeText(this, R.string.removed_from_playlist, Toast.LENGTH_SHORT).show()
                reload()
            }
            .setNegativeButton(android.R.string.cancel, null)
            .show()
    }

    companion object {
        private const val EXTRA_ID = "extra_playlist_id"

        fun start(context: Context, playlistId: Long) {
            context.startActivity(
                Intent(context, MusicPlaylistActivity::class.java).putExtra(EXTRA_ID, playlistId)
            )
        }
    }
}
