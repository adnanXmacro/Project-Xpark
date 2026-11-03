package com.sparktube.app.ui.music

import android.content.ClipData
import android.content.ClipboardManager
import android.content.Context
import android.content.Intent
import android.graphics.Bitmap
import android.graphics.Color
import android.os.Bundle
import android.widget.Toast
import androidx.appcompat.app.AppCompatActivity
import androidx.core.view.isVisible
import com.google.zxing.BarcodeFormat
import com.google.zxing.EncodeHintType
import com.google.zxing.qrcode.QRCodeWriter
import com.sparktube.app.R
import com.sparktube.app.data.LocalStore
import com.sparktube.app.databinding.ActivityPlaylistShareBinding
import com.sparktube.app.util.PlaylistShare
import com.sparktube.app.util.Themes

class PlaylistShareActivity : AppCompatActivity() {

    override fun onCreate(savedInstanceState: Bundle?) {
        Themes.apply(this)
        super.onCreate(savedInstanceState)
        val binding = ActivityPlaylistShareBinding.inflate(layoutInflater)
        setContentView(binding.root)

        val id = intent.getLongExtra(EXTRA_ID, -1L)
        val playlist = LocalStore.musicPlaylist(this, id)
        if (playlist == null) {
            finish()
            return
        }
        val ids = PlaylistShare.idsOf(playlist.items)
        val fullLink = PlaylistShare.encode(playlist.name, ids)
        val qrLink = PlaylistShare.encode(playlist.name, ids, PlaylistShare.QR_MAX_IDS)

        binding.title.text = playlist.name
        binding.truncated.isVisible = ids.size > PlaylistShare.QR_MAX_IDS
        if (ids.size > PlaylistShare.QR_MAX_IDS) {
            binding.truncated.text = getString(
                R.string.qr_truncated,
                PlaylistShare.QR_MAX_IDS,
                ids.size
            )
        }
        binding.qr.setImageBitmap(qrBitmap(qrLink))
        binding.copyButton.setOnClickListener {
            val clipboard = getSystemService(CLIPBOARD_SERVICE) as ClipboardManager
            clipboard.setPrimaryClip(ClipData.newPlainText(playlist.name, fullLink))
            Toast.makeText(this, R.string.link_copied, Toast.LENGTH_SHORT).show()
        }
        binding.shareButton.setOnClickListener {
            val send = Intent(Intent.ACTION_SEND).apply {
                type = "text/plain"
                putExtra(Intent.EXTRA_SUBJECT, playlist.name)
                putExtra(Intent.EXTRA_TEXT, "${playlist.name}\n$fullLink")
            }
            startActivity(Intent.createChooser(send, getString(R.string.share_playlist)))
        }
        binding.closeButton.setOnClickListener { finish() }
        binding.backButton.setOnClickListener { finish() }
    }

    private fun qrBitmap(content: String): Bitmap {
        val size = 512
        val matrix = QRCodeWriter().encode(
            content,
            BarcodeFormat.QR_CODE,
            size,
            size,
            mapOf(EncodeHintType.MARGIN to 1)
        )
        val pixels = IntArray(size * size)
        for (y in 0 until size) {
            for (x in 0 until size) {
                pixels[y * size + x] = if (matrix.get(x, y)) Color.BLACK else Color.WHITE
            }
        }
        return Bitmap.createBitmap(size, size, Bitmap.Config.RGB_565).apply {
            setPixels(pixels, 0, size, 0, 0, size, size)
        }
    }

    companion object {
        private const val EXTRA_ID = "extra_playlist_id"

        fun start(context: Context, playlistId: Long) {
            context.startActivity(
                Intent(context, PlaylistShareActivity::class.java).putExtra(EXTRA_ID, playlistId)
            )
        }
    }
}
