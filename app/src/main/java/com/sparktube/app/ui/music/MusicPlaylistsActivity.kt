package com.sparktube.app.ui.music

import android.Manifest
import android.content.Context
import android.content.Intent
import android.content.pm.PackageManager
import android.net.Uri
import android.os.Bundle
import android.widget.EditText
import android.widget.Toast
import androidx.activity.result.contract.ActivityResultContracts
import androidx.appcompat.app.AlertDialog
import androidx.appcompat.app.AppCompatActivity
import androidx.core.content.ContextCompat
import androidx.core.view.isVisible
import androidx.lifecycle.lifecycleScope
import androidx.recyclerview.widget.LinearLayoutManager
import com.google.zxing.integration.android.IntentIntegrator
import com.sparktube.app.R
import com.sparktube.app.data.LocalStore
import com.sparktube.app.data.Playlist
import com.sparktube.app.databinding.ActivityMusicListBinding
import com.sparktube.app.download.DownloadCenter
import com.sparktube.app.ui.common.PlaylistAdapter
import com.sparktube.app.ui.common.PlaylistSheets
import com.sparktube.app.util.PlaylistShare
import com.sparktube.app.util.Themes
import kotlinx.coroutines.launch

class MusicPlaylistsActivity : AppCompatActivity() {

    private lateinit var binding: ActivityMusicListBinding
    private lateinit var adapter: PlaylistAdapter

    private val scanLauncher = registerForActivityResult(ActivityResultContracts.StartActivityForResult()) { result ->
        val contents = IntentIntegrator.parseActivityResult(result.resultCode, result.data)?.contents
        if (contents.isNullOrBlank()) return@registerForActivityResult
        importLink(contents)
    }

    private val cameraPermission = registerForActivityResult(ActivityResultContracts.RequestPermission()) { granted ->
        if (granted) startScan() else {
            Toast.makeText(this, R.string.camera_denied_paste, Toast.LENGTH_LONG).show()
            pasteLink()
        }
    }

    private val openPackage = registerForActivityResult(ActivityResultContracts.OpenDocument()) { uri ->
        if (uri == null) return@registerForActivityResult
        runCatching {
            contentResolver.takePersistableUriPermission(
                uri,
                Intent.FLAG_GRANT_READ_URI_PERMISSION
            )
        }
        promptLoad(uri)
    }

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
        binding.moreButton.isVisible = true
        binding.moreButton.setOnClickListener { showMore() }
    }

    private fun showMore() {
        val items = arrayOf(
            getString(R.string.scan_playlist),
            getString(R.string.paste_playlist_link),
            getString(R.string.load_from_sd)
        )
        AlertDialog.Builder(this)
            .setItems(items) { _, which ->
                when (which) {
                    0 -> requestScan()
                    1 -> pasteLink()
                    2 -> openPackage.launch(arrayOf("*/*"))
                }
            }
            .show()
    }

    private fun requestScan() {
        if (ContextCompat.checkSelfPermission(this, Manifest.permission.CAMERA) ==
            PackageManager.PERMISSION_GRANTED
        ) {
            startScan()
        } else {
            cameraPermission.launch(Manifest.permission.CAMERA)
        }
    }

    private fun startScan() {
        val integrator = IntentIntegrator(this)
        integrator.setDesiredBarcodeFormats(IntentIntegrator.QR_CODE)
        integrator.setBeepEnabled(false)
        integrator.setOrientationLocked(true)
        integrator.setPrompt(getString(R.string.scan_playlist))
        scanLauncher.launch(integrator.createScanIntent())
    }

    private fun pasteLink() {
        val input = EditText(this)
        input.hint = getString(R.string.paste_playlist_hint)
        AlertDialog.Builder(this)
            .setTitle(R.string.paste_playlist_link)
            .setView(input)
            .setPositiveButton(android.R.string.ok) { _, _ -> importLink(input.text.toString()) }
            .setNegativeButton(android.R.string.cancel, null)
            .show()
    }

    private fun importLink(raw: String) {
        val payload = PlaylistShare.decode(raw)
        if (payload == null) {
            Toast.makeText(this, R.string.invalid_playlist_link, Toast.LENGTH_SHORT).show()
            return
        }
        val created = PlaylistImportActivity.importPayload(this, payload)
        if (created == null) {
            Toast.makeText(this, R.string.invalid_playlist_link, Toast.LENGTH_SHORT).show()
            return
        }
        Toast.makeText(this, R.string.playlist_imported, Toast.LENGTH_SHORT).show()
        MusicPlaylistActivity.start(this, created.id)
    }

    private fun promptLoad(uri: Uri) {
        AlertDialog.Builder(this)
            .setMessage(R.string.sd_store_title)
            .setPositiveButton(R.string.sd_store_on_device) { _, _ -> importPackage(uri, true) }
            .setNegativeButton(R.string.sd_play_from_card) { _, _ -> importPackage(uri, false) }
            .setNeutralButton(android.R.string.cancel, null)
            .show()
    }

    private fun importPackage(uri: Uri, store: Boolean) {
        lifecycleScope.launch {
            runCatching { PlaylistExport.importPackage(this@MusicPlaylistsActivity, uri, store) }
                .onSuccess { playlist ->
                    Toast.makeText(this@MusicPlaylistsActivity, R.string.playlist_imported, Toast.LENGTH_SHORT).show()
                    MusicPlaylistActivity.start(this@MusicPlaylistsActivity, playlist.id)
                }
                .onFailure {
                    Toast.makeText(this@MusicPlaylistsActivity, R.string.sd_invalid_package, Toast.LENGTH_SHORT).show()
                }
        }
    }

    override fun onResume() {
        super.onResume()
        if (Themes.recreateIfNeeded(this)) return
        if (DownloadCenter.pruneMissingSd(this)) {
            Toast.makeText(this, R.string.sd_card_gone, Toast.LENGTH_SHORT).show()
        }
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
                    getString(R.string.share_playlist),
                    getString(R.string.rename_playlist),
                    getString(R.string.delete_playlist_confirm)
                )
            ) { _, which ->
                when (which) {
                    0 -> PlaylistShareActivity.start(this, playlist.id)
                    1 -> PlaylistSheets.showRenameDialog(this, playlist.id, playlist.name, music = true) {
                        reload()
                    }
                    2 -> confirmDelete(playlist)
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
