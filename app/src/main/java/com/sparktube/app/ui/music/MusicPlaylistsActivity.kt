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
import com.sparktube.app.data.Playlist
import com.sparktube.app.databinding.ActivityMusicListBinding
import com.sparktube.app.ui.common.PlaylistAdapter
import com.sparktube.app.ui.common.PlaylistSheets
import com.sparktube.app.util.Themes

class MusicPlaylistsActivity : AppCompatActivity() {

    private lateinit var binding: ActivityMusicListBinding
    private lateinit var adapter: PlaylistAdapter

    override fun onCreate(savedInstanceState: Bundle?) {
        Themes.apply(this)
        super.onCreate(savedInstanceState)
        binding = ActivityMusicListBinding.inflate(layoutInflater)
        setContentView(binding.root)

        binding.title.setText(R.string.music_playlists)
        adapter = PlaylistAdapter(
            onClick = { playlist -> MusicPlaylistActivity.start(this, playlist.id) },
            onLongClick = { playlist -> showPlaylistActions(playlist) },
            songs = true
        )
        binding.list.layoutManager = LinearLayoutManager(this)
        binding.list.adapter = adapter
        binding.backButton.setOnClickListener { finish() }
        binding.playAllButton.setOnClickListener {
            PlaylistSheets.showNewDialog(this, music = true) { reload() }
        }
        (binding.playAllButton.getChildAt(1) as? android.widget.TextView)
            ?.setText(R.string.new_playlist)
        (binding.playAllButton.getChildAt(0) as? android.widget.ImageView)
            ?.setImageResource(R.drawable.ic_add)
    }

    override fun onResume() {
        super.onResume()
        if (Themes.recreateIfNeeded(this)) return
        reload()
    }

    private fun reload() {
        val playlists = LocalStore.musicPlaylists(this)
        adapter.submitList(playlists)
        binding.emptyView.setText(R.string.empty_music_playlists)
        binding.emptyView.isVisible = playlists.isEmpty()
        binding.summaryRow.isVisible = true
        binding.subtitle.text = ""
    }

    private fun showPlaylistActions(playlist: Playlist) {
        AlertDialog.Builder(this)
            .setTitle(playlist.name)
            .setItems(
                arrayOf(
                    getString(R.string.rename_playlist),
                    getString(R.string.delete_playlist_confirm)
                )
            ) { _, which ->
                when (which) {
                    0 -> PlaylistSheets.showRenameDialog(this, playlist.id, playlist.name, music = true) {
                        reload()
                    }
                    1 -> confirmDelete(playlist)
                }
            }
            .show()
    }

    private fun confirmDelete(playlist: Playlist) {
        AlertDialog.Builder(this)
            .setTitle(R.string.delete_playlist_confirm)
            .setMessage(playlist.name)
            .setPositiveButton(android.R.string.ok) { _, _ ->
                LocalStore.deleteMusicPlaylist(this, playlist.id)
                Toast.makeText(this, R.string.playlist_deleted, Toast.LENGTH_SHORT).show()
                reload()
            }
            .setNegativeButton(android.R.string.cancel, null)
            .show()
    }

    companion object {
        fun start(context: Context) {
            context.startActivity(Intent(context, MusicPlaylistsActivity::class.java))
        }
    }
}
