package com.sparktube.app.ui.music

import android.content.Context
import android.content.Intent
import android.net.Uri
import android.os.Bundle
import android.os.storage.StorageManager
import android.widget.PopupMenu
import android.widget.Toast
import androidx.activity.result.contract.ActivityResultContracts
import androidx.appcompat.app.AlertDialog
import androidx.appcompat.app.AppCompatActivity
import androidx.core.view.isVisible
import androidx.lifecycle.lifecycleScope
import androidx.recyclerview.widget.LinearLayoutManager
import com.sparktube.app.R
import com.sparktube.app.data.LocalStore
import com.sparktube.app.data.Playlist
import com.sparktube.app.databinding.ActivityPlaylistBinding
import com.sparktube.app.download.DownloadCenter
import com.sparktube.app.playback.PlaybackCenter
import com.sparktube.app.ui.common.MusicRowAdapter
import com.sparktube.app.ui.common.PlaylistSheets
import com.sparktube.app.ui.common.VideoUiModel
import com.sparktube.app.ui.common.toQueueEntry
import com.sparktube.app.ui.common.toUiModel
import com.sparktube.app.util.SdPackage
import com.sparktube.app.util.Themes
import kotlinx.coroutines.launch

class MusicPlaylistActivity : AppCompatActivity() {

    private lateinit var binding: ActivityPlaylistBinding
    private lateinit var adapter: MusicRowAdapter
    private var playlistId = -1L
    private var songs: List<VideoUiModel> = emptyList()

    private val treePicker = registerForActivityResult(ActivityResultContracts.OpenDocumentTree()) { uri ->
        if (uri == null) return@registerForActivityResult
        exportMp3(uri)
    }

    private val packagePicker = registerForActivityResult(ActivityResultContracts.CreateDocument("application/octet-stream")) { uri ->
        if (uri == null) return@registerForActivityResult
        exportPackage(uri)
    }

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
        binding.moreButton.isVisible = true
        binding.moreButton.setOnClickListener { showMore() }
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

    private fun showMore() {
        val menu = PopupMenu(this, binding.moreButton)
        menu.menu.add(0, 1, 0, R.string.share_playlist)
        menu.menu.add(0, 2, 0, R.string.download_all)
        menu.menu.add(0, 3, 0, R.string.export_playlist)
        menu.setOnMenuItemClickListener { item ->
            when (item.itemId) {
                1 -> PlaylistShareActivity.start(this, playlistId)
                2 -> downloadAll()
                3 -> startExport()
            }
            true
        }
        menu.show()
    }

    private fun playlistOrNull(): Playlist? = LocalStore.musicPlaylist(this, playlistId)

    private fun downloadAll() {
        val playlist = playlistOrNull() ?: return
        val missing = playlist.items.count {
            DownloadCenter.doneAudioFile(this, it.url) == null
        }
        if (missing == 0) {
            Toast.makeText(this, R.string.download_all_none, Toast.LENGTH_SHORT).show()
            return
        }
        lifecycleScope.launch {
            PlaylistExport.downloadMissing(this@MusicPlaylistActivity, playlist.items) { x, y ->
                Toast.makeText(
                    this@MusicPlaylistActivity,
                    getString(R.string.downloading_n_of_m, x, y),
                    Toast.LENGTH_SHORT
                ).show()
            }
        }
    }

    private fun startExport() {
        val playlist = playlistOrNull() ?: return
        if (playlist.items.isEmpty()) {
            Toast.makeText(this, R.string.export_empty, Toast.LENGTH_SHORT).show()
            return
        }
        val missing = playlist.items.any { DownloadCenter.doneAudioFile(this, it.url) == null }
        if (!missing) {
            pickFormat()
            return
        }
        AlertDialog.Builder(this)
            .setTitle(R.string.export_missing_title)
            .setItems(
                arrayOf(
                    getString(R.string.export_download_remaining),
                    getString(R.string.export_downloaded_only),
                    getString(android.R.string.cancel)
                )
            ) { _, which ->
                when (which) {
                    0 -> {
                        downloadAll()
                        Toast.makeText(this, R.string.export_download_then_retry, Toast.LENGTH_LONG).show()
                    }
                    1 -> pickFormat()
                }
            }
            .show()
    }

    private fun pickFormat() {
        AlertDialog.Builder(this)
            .setTitle(R.string.export_format_title)
            .setItems(
                arrayOf(
                    getString(R.string.export_pocket_mp3),
                    getString(R.string.export_android)
                )
            ) { _, which ->
                val playlist = playlistOrNull() ?: return@setItems
                if (which == 0) {
                    treePicker.launch(null)
                } else {
                    packagePicker.launch("${SdPackage.sanitizeFileName(playlist.name)}.opentube")
                }
            }
            .show()
    }

    private fun exportMp3(uri: Uri) {
        val playlist = playlistOrNull() ?: return
        val files = PlaylistExport.downloadedItems(this, playlist.items)
        if (files.isEmpty()) {
            Toast.makeText(this, R.string.export_no_files, Toast.LENGTH_SHORT).show()
            return
        }
        warnIfAdopted(uri)
        lifecycleScope.launch {
            runCatching { PlaylistExport.writeMp3(this@MusicPlaylistActivity, uri, playlist, files) }
                .onSuccess { Toast.makeText(this@MusicPlaylistActivity, R.string.export_done_mp3, Toast.LENGTH_SHORT).show() }
                .onFailure { Toast.makeText(this@MusicPlaylistActivity, R.string.export_failed, Toast.LENGTH_SHORT).show() }
        }
    }

    private fun exportPackage(uri: Uri) {
        val playlist = playlistOrNull() ?: return
        val files = PlaylistExport.downloadedItems(this, playlist.items)
        warnIfAdopted(uri)
        lifecycleScope.launch {
            runCatching { PlaylistExport.writePackage(this@MusicPlaylistActivity, uri, playlist, files) }
                .onSuccess { Toast.makeText(this@MusicPlaylistActivity, R.string.export_done_package, Toast.LENGTH_SHORT).show() }
                .onFailure { Toast.makeText(this@MusicPlaylistActivity, R.string.export_failed, Toast.LENGTH_SHORT).show() }
        }
    }

    private fun warnIfAdopted(uri: Uri) {
        if (android.os.Build.VERSION.SDK_INT < 29) return
        val sm = getSystemService(StorageManager::class.java) ?: return
        val volume = sm.getStorageVolume(uri) ?: return
        if (volume.isRemovable && volume.isEmulated) {
            Toast.makeText(this, R.string.sd_adopted_warning, Toast.LENGTH_LONG).show()
        }
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
