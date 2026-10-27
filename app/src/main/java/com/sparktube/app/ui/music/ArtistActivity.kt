package com.sparktube.app.ui.music

import android.content.Context
import android.content.Intent
import android.os.Bundle
import android.widget.Toast
import androidx.appcompat.app.AppCompatActivity
import androidx.core.view.isVisible
import androidx.lifecycle.lifecycleScope
import androidx.recyclerview.widget.LinearLayoutManager
import com.sparktube.app.R
import com.sparktube.app.data.ArtistEntry
import com.sparktube.app.data.LocalStore
import com.sparktube.app.data.YtRepository
import com.sparktube.app.databinding.ActivityArtistBinding
import com.sparktube.app.playback.PlaybackCenter
import com.sparktube.app.ui.common.MusicRowAdapter
import com.sparktube.app.ui.common.VideoUiModel
import com.sparktube.app.ui.common.toQueueEntry
import com.sparktube.app.ui.common.toUiModel
import com.sparktube.app.util.Formatters
import com.sparktube.app.util.Themes
import com.sparktube.app.util.Thumbs
import kotlinx.coroutines.CancellationException
import kotlinx.coroutines.launch

/**
 * Songs for one starred (or tapped) artist. Play all queues this list;
 * tapping a row starts a radio from that song.
 */
class ArtistActivity : AppCompatActivity() {

    private lateinit var binding: ActivityArtistBinding
    private lateinit var adapter: MusicRowAdapter

    private var artistName: String = ""
    private var artistAvatar: String = ""
    private var artistChannel: String = ""
    private var songs: List<VideoUiModel> = emptyList()

    override fun onCreate(savedInstanceState: Bundle?) {
        Themes.apply(this)
        super.onCreate(savedInstanceState)
        binding = ActivityArtistBinding.inflate(layoutInflater)
        setContentView(binding.root)

        artistName = intent.getStringExtra(EXTRA_NAME).orEmpty()
        artistAvatar = intent.getStringExtra(EXTRA_AVATAR).orEmpty()
        artistChannel = intent.getStringExtra(EXTRA_CHANNEL).orEmpty()
        if (artistName.isBlank()) {
            finish()
            return
        }

        binding.title.text = artistName
        if (artistAvatar.isNotBlank()) {
            Thumbs.load(binding.avatar, artistAvatar)
        } else {
            binding.avatar.setImageResource(R.drawable.ic_person)
        }

        adapter = MusicRowAdapter(
            onClick = { model ->
                PlaybackCenter.playMusic(model.toQueueEntry(isMusic = true), radio = true)
                NowPlayingActivity.start(this)
            }
        )
        binding.list.layoutManager = LinearLayoutManager(this)
        binding.list.adapter = adapter

        binding.backButton.setOnClickListener { finish() }
        binding.starButton.setOnClickListener { toggleStar() }
        binding.playAllButton.setOnClickListener {
            if (songs.isEmpty()) return@setOnClickListener
            PlaybackCenter.playMusicQueue(songs.map { it.toQueueEntry(isMusic = true) }, 0)
            NowPlayingActivity.start(this)
        }

        bindStar()
        load()
    }

    override fun onResume() {
        super.onResume()
        Themes.recreateIfNeeded(this)
        bindStar()
    }

    private fun artistEntry(): ArtistEntry = ArtistEntry(
        name = artistName,
        avatarUrl = artistAvatar,
        channelUrl = artistChannel
    )

    private fun bindStar() {
        val starred = LocalStore.isFavoriteArtist(this, artistName)
        binding.starButton.setImageResource(
            if (starred) R.drawable.ic_star else R.drawable.ic_star_border
        )
        binding.starButton.setColorFilter(
            getColor(if (starred) R.color.music_green else R.color.on_surface)
        )
        binding.starButton.contentDescription = getString(
            if (starred) R.string.artist_unstar else R.string.artist_star
        )
    }

    private fun toggleStar() {
        val now = LocalStore.toggleFavoriteArtist(this, artistEntry())
        bindStar()
        Toast.makeText(
            this,
            if (now) R.string.artist_added else R.string.artist_removed,
            Toast.LENGTH_SHORT
        ).show()
    }

    private fun load() {
        binding.loading.isVisible = true
        binding.emptyView.isVisible = false
        lifecycleScope.launch {
            try {
                val result = YtRepository.searchMusic(artistName)
                songs = result.items.map { it.toUiModel() }
                adapter.submitList(songs)
                binding.subtitle.text = getString(R.string.music_songs_fmt, songs.size)
                binding.emptyView.isVisible = songs.isEmpty()
                if (songs.isEmpty()) {
                    binding.emptyView.setText(R.string.artist_songs_error)
                }
            } catch (e: CancellationException) {
                throw e
            } catch (e: Exception) {
                songs = emptyList()
                adapter.submitList(emptyList())
                binding.emptyView.isVisible = true
                binding.emptyView.text = Formatters.friendlyException(e)
            } finally {
                binding.loading.isVisible = false
            }
        }
    }

    companion object {
        private const val EXTRA_NAME = "extra_name"
        private const val EXTRA_AVATAR = "extra_avatar"
        private const val EXTRA_CHANNEL = "extra_channel"

        fun start(context: Context, artist: ArtistEntry) {
            context.startActivity(
                Intent(context, ArtistActivity::class.java)
                    .putExtra(EXTRA_NAME, artist.name)
                    .putExtra(EXTRA_AVATAR, artist.avatarUrl)
                    .putExtra(EXTRA_CHANNEL, artist.channelUrl)
            )
        }

        fun start(context: Context, name: String, avatarUrl: String = "", channelUrl: String = "") {
            start(context, ArtistEntry(name = name, avatarUrl = avatarUrl, channelUrl = channelUrl))
        }
    }
}
