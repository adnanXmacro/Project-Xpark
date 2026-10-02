package com.sparktube.app.ui.playlist

import android.content.Context
import android.content.Intent
import android.os.Bundle
import androidx.appcompat.app.AppCompatActivity
import androidx.core.view.isVisible
import androidx.lifecycle.lifecycleScope
import androidx.recyclerview.widget.LinearLayoutManager
import androidx.recyclerview.widget.RecyclerView
import com.sparktube.app.R
import com.sparktube.app.data.YtRepository
import com.sparktube.app.databinding.ActivityYoutubePlaylistBinding
import com.sparktube.app.playback.PlaybackCenter
import com.sparktube.app.ui.common.SkeletonAdapter
import com.sparktube.app.ui.common.VideoAdapter
import com.sparktube.app.ui.common.VideoUiModel
import com.sparktube.app.ui.common.showSkeleton
import com.sparktube.app.ui.common.toQueueEntry
import com.sparktube.app.ui.common.toUiModel
import com.sparktube.app.ui.player.PlayerActivity
import com.sparktube.app.util.Formatters
import com.sparktube.app.util.Themes
import kotlinx.coroutines.CancellationException
import kotlinx.coroutines.launch
import org.schabi.newpipe.extractor.Page

class YoutubePlaylistActivity : AppCompatActivity() {

    private lateinit var binding: ActivityYoutubePlaylistBinding
    private lateinit var adapter: VideoAdapter

    private val videos = mutableListOf<VideoUiModel>()
    private var page: Page? = null
    private var isLoading = false
    private var playlistUrl: String = ""
    private var playlistName: String = ""
    private var streamCount: Long = -1L

    override fun onCreate(savedInstanceState: Bundle?) {
        Themes.apply(this)
        super.onCreate(savedInstanceState)
        binding = ActivityYoutubePlaylistBinding.inflate(layoutInflater)
        setContentView(binding.root)

        playlistUrl = intent.getStringExtra(EXTRA_URL).orEmpty()
        playlistName = intent.getStringExtra(EXTRA_NAME).orEmpty()
        if (playlistUrl.isBlank()) {
            finish()
            return
        }

        binding.title.text = playlistName

        adapter = VideoAdapter(onClick = { model ->
            playFrom(videos.indexOfFirst { it.url == model.url })
        })
        binding.list.layoutManager = LinearLayoutManager(this)
        binding.list.adapter = adapter

        binding.list.addOnScrollListener(object : RecyclerView.OnScrollListener() {
            override fun onScrolled(recyclerView: RecyclerView, dx: Int, dy: Int) {
                if (dy <= 0) return
                val lm = binding.list.layoutManager as? LinearLayoutManager ?: return
                val lastVisible = lm.findLastVisibleItemPosition()
                if (lastVisible >= adapter.itemCount - 4 && page != null && !isLoading) {
                    loadMore()
                }
            }
        })

        binding.backButton.setOnClickListener { finish() }
        binding.playAllButton.setOnClickListener { playFrom(0) }

        load()
    }

    override fun onResume() {
        super.onResume()
        Themes.recreateIfNeeded(this)
    }

    private fun load() {
        binding.errorView.isVisible = false
        binding.emptyView.isVisible = false
        binding.playAllRow.isVisible = false
        showSkeleton(binding.list, SkeletonAdapter.STYLE_VIDEO, count = 8)
        lifecycleScope.launch {
            try {
                val result = YtRepository.youtubePlaylist(playlistUrl)
                playlistName = result.name.ifBlank { playlistName }
                streamCount = result.streamCount
                binding.title.text = playlistName
                page = result.nextPage
                videos.clear()
                videos.addAll(result.items.map { it.toUiModel() })
                binding.list.adapter = adapter
                adapter.submitList(videos.toList())
                updateHeader()
                binding.emptyView.isVisible = videos.isEmpty()
            } catch (e: CancellationException) {
                throw e
            } catch (e: Exception) {
                binding.list.adapter = adapter
                adapter.submitList(emptyList())
                binding.playAllRow.isVisible = false
                binding.errorText.text = Formatters.friendlyException(e)
                binding.errorView.isVisible = true
            }
        }
    }

    private fun loadMore() {
        val nextPage = page ?: return
        isLoading = true
        lifecycleScope.launch {
            try {
                val (items, next) = YtRepository.youtubePlaylistMore(playlistUrl, nextPage)
                page = next
                val known = videos.map { it.url }.toSet()
                videos.addAll(items.map { it.toUiModel() }.filter { it.url !in known })
                adapter.submitList(videos.toList())
                updateHeader()
            } catch (e: Exception) {
                page = null
            } finally {
                isLoading = false
            }
        }
    }

    private fun updateHeader() {
        val count = if (streamCount > 0) streamCount.toInt() else videos.size
        binding.playAllRow.isVisible = videos.isNotEmpty()
        binding.videoCount.text = resources.getQuantityString(
            R.plurals.playlist_video_count, count, count
        )
    }

    private fun playFrom(index: Int) {
        if (videos.isEmpty()) return
        PlaybackCenter.playPlaylist(videos.map { it.toQueueEntry() }, index.coerceAtLeast(0))
        PlayerActivity.startResume(this)
    }

    companion object {
        private const val EXTRA_URL = "extra_playlist_url"
        private const val EXTRA_NAME = "extra_playlist_name"

        fun start(context: Context, url: String, name: String) {
            context.startActivity(
                Intent(context, YoutubePlaylistActivity::class.java)
                    .putExtra(EXTRA_URL, url)
                    .putExtra(EXTRA_NAME, name)
            )
        }
    }
}
