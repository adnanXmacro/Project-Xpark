package com.sparktube.app.ui.channel

import android.content.Context
import android.content.Intent
import android.os.Bundle
import android.widget.Toast
import androidx.appcompat.app.AppCompatActivity
import androidx.core.view.isVisible
import androidx.lifecycle.lifecycleScope
import androidx.recyclerview.widget.LinearLayoutManager
import androidx.recyclerview.widget.RecyclerView
import com.sparktube.app.R
import com.sparktube.app.data.ChannelEntry
import com.sparktube.app.data.ChannelVideoSort
import com.sparktube.app.data.LocalStore
import com.sparktube.app.data.YoutubePlaylistItem
import com.sparktube.app.data.YtRepository
import com.sparktube.app.databinding.ActivityChannelBinding
import com.sparktube.app.ui.common.SkeletonAdapter
import com.sparktube.app.ui.common.VideoAdapter
import com.sparktube.app.ui.common.VideoUiModel
import com.sparktube.app.ui.common.YoutubePlaylistAdapter
import com.sparktube.app.ui.common.showSkeleton
import com.sparktube.app.ui.common.toUiModel
import com.sparktube.app.ui.player.PlayerActivity
import com.sparktube.app.ui.playlist.YoutubePlaylistActivity
import com.sparktube.app.util.Formatters
import com.sparktube.app.util.Thumbs
import com.sparktube.app.util.Themes
import kotlinx.coroutines.CancellationException
import kotlinx.coroutines.Job
import kotlinx.coroutines.launch
import org.schabi.newpipe.extractor.Page

class ChannelActivity : AppCompatActivity() {

    private enum class ChannelTab { VIDEOS, PLAYLISTS }

    private data class VideoTabState(
        val items: MutableList<VideoUiModel> = mutableListOf(),
        var page: Page? = null,
        var loaded: Boolean = false
    )

    private lateinit var binding: ActivityChannelBinding
    private lateinit var videoAdapter: VideoAdapter
    private lateinit var playlistAdapter: YoutubePlaylistAdapter

    private var tab: ChannelTab = ChannelTab.VIDEOS
    private var sort: ChannelVideoSort = ChannelVideoSort.LATEST
    private val videoState = mutableMapOf(
        ChannelVideoSort.LATEST to VideoTabState(),
        ChannelVideoSort.POPULAR to VideoTabState(),
        ChannelVideoSort.OLDEST to VideoTabState()
    )
    private val playlistItems = mutableListOf<YoutubePlaylistItem>()
    private var playlistPage: Page? = null
    private var playlistsLoaded = false

    private var isLoading = false
    private var loadJob: Job? = null
    private var channelUrl: String = ""
    private var channelAvatar: String = ""
    private var subscriberCount: Long = -1L
    private var channelName: String = ""

    override fun onCreate(savedInstanceState: Bundle?) {
        Themes.apply(this)
        super.onCreate(savedInstanceState)
        binding = ActivityChannelBinding.inflate(layoutInflater)
        setContentView(binding.root)

        channelUrl = intent.getStringExtra(EXTRA_URL).orEmpty()
        channelName = intent.getStringExtra(EXTRA_NAME).orEmpty()
        if (channelUrl.isBlank()) {
            finish()
            return
        }

        binding.channelName.text = channelName

        videoAdapter = VideoAdapter(onClick = { model ->
            PlayerActivity.start(this, model)
        })
        playlistAdapter = YoutubePlaylistAdapter(onClick = { item ->
            YoutubePlaylistActivity.start(this, item.url, item.name)
        })
        binding.list.layoutManager = LinearLayoutManager(this)
        binding.list.adapter = videoAdapter

        binding.list.addOnScrollListener(object : RecyclerView.OnScrollListener() {
            override fun onScrolled(recyclerView: RecyclerView, dx: Int, dy: Int) {
                if (dy <= 0) return
                val lm = binding.list.layoutManager as? LinearLayoutManager ?: return
                val lastVisible = lm.findLastVisibleItemPosition()
                if (lastVisible >= currentItemCount() - 4 && nextPage() != null && !isLoading) {
                    loadMore()
                }
            }
        })

        binding.backButton.setOnClickListener { finish() }
        binding.subscribeButton.setOnClickListener { toggleSubscribe() }
        binding.tabVideos.setOnClickListener { switchTab(ChannelTab.VIDEOS) }
        binding.tabPlaylists.setOnClickListener { switchTab(ChannelTab.PLAYLISTS) }
        binding.sortLatest.setOnClickListener { switchSort(ChannelVideoSort.LATEST) }
        binding.sortPopular.setOnClickListener { switchSort(ChannelVideoSort.POPULAR) }
        binding.sortOldest.setOnClickListener { switchSort(ChannelVideoSort.OLDEST) }

        updateTabUi()
        loadHeaderAndVideos()
    }

    override fun onResume() {
        super.onResume()
        Themes.recreateIfNeeded(this)
        updateSubscribeUi()
    }

    private fun currentVideos(): VideoTabState = videoState.getValue(sort)

    private fun currentItemCount(): Int = when (tab) {
        ChannelTab.VIDEOS -> videoAdapter.itemCount
        ChannelTab.PLAYLISTS -> playlistAdapter.itemCount
    }

    private fun nextPage(): Page? = when (tab) {
        ChannelTab.VIDEOS -> currentVideos().page
        ChannelTab.PLAYLISTS -> playlistPage
    }

    private fun switchTab(newTab: ChannelTab) {
        if (newTab == tab) return
        tab = newTab
        updateTabUi()
        when (tab) {
            ChannelTab.VIDEOS -> {
                val state = currentVideos()
                if (state.loaded) {
                    showVideos(state)
                } else {
                    loadVideos(reset = true)
                }
            }
            ChannelTab.PLAYLISTS -> {
                if (playlistsLoaded) {
                    showPlaylists()
                } else {
                    loadPlaylists()
                }
            }
        }
    }

    private fun switchSort(newSort: ChannelVideoSort) {
        if (tab != ChannelTab.VIDEOS || newSort == sort) return
        sort = newSort
        updateTabUi()
        val state = currentVideos()
        if (state.loaded) {
            showVideos(state)
        } else {
            loadVideos(reset = true)
        }
    }

    private fun updateTabUi() {
        binding.tabVideos.isSelected = tab == ChannelTab.VIDEOS
        binding.tabPlaylists.isSelected = tab == ChannelTab.PLAYLISTS
        binding.sortRow.isVisible = tab == ChannelTab.VIDEOS
        binding.sortLatest.isSelected = sort == ChannelVideoSort.LATEST
        binding.sortPopular.isSelected = sort == ChannelVideoSort.POPULAR
        binding.sortOldest.isSelected = sort == ChannelVideoSort.OLDEST
    }

    private fun loadHeaderAndVideos() {
        binding.errorView.isVisible = false
        binding.emptyView.isVisible = false
        showSkeleton(binding.list, SkeletonAdapter.STYLE_VIDEO, count = 8)
        loadJob?.cancel()
        loadJob = lifecycleScope.launch {
            try {
                val channel = YtRepository.channelInfo(channelUrl)
                channelName = channel.name
                channelAvatar = channel.avatarUrl
                subscriberCount = channel.subscriberCount
                binding.channelName.text = channel.name
                binding.channelSubs.text =
                    getString(R.string.subscribers_fmt, Formatters.formatViewCount(channel.subscriberCount))
                Thumbs.load(binding.channelAvatar, channel.avatarUrl)
                updateSubscribeUi()
                fetchVideos()
            } catch (e: CancellationException) {
                throw e
            } catch (e: Exception) {
                binding.list.adapter = videoAdapter
                videoAdapter.submitList(emptyList())
                binding.errorText.text = Formatters.friendlyException(e)
                binding.errorView.isVisible = true
            }
        }
    }

    private fun loadVideos(reset: Boolean) {
        if (reset) {
            binding.errorView.isVisible = false
            binding.emptyView.isVisible = false
            showSkeleton(binding.list, SkeletonAdapter.STYLE_VIDEO, count = 8)
        }
        loadJob?.cancel()
        loadJob = lifecycleScope.launch {
            try {
                fetchVideos()
            } catch (e: CancellationException) {
                throw e
            } catch (e: Exception) {
                onVideosError(e)
            }
        }
    }

    private suspend fun fetchVideos() {
        val requested = sort
        val (videos, nextPage) = YtRepository.channelVideos(channelUrl, requested)
        val state = videoState.getValue(requested)
        state.items.clear()
        state.items.addAll(videos.map { it.toUiModel() })
        state.page = nextPage
        state.loaded = true
        if (tab == ChannelTab.VIDEOS && sort == requested) {
            showVideos(state)
        }
    }

    private fun onVideosError(e: Exception) {
        if (sort != ChannelVideoSort.LATEST) {
            val latest = videoState.getValue(ChannelVideoSort.LATEST)
            if (latest.loaded) {
                sort = ChannelVideoSort.LATEST
                updateTabUi()
                showVideos(latest)
                return
            }
        }
        binding.list.adapter = videoAdapter
        videoAdapter.submitList(emptyList())
        binding.errorText.text = Formatters.friendlyException(e)
        binding.errorView.isVisible = true
        binding.emptyView.isVisible = false
    }

    private fun showVideos(state: VideoTabState) {
        binding.errorView.isVisible = false
        binding.list.adapter = videoAdapter
        videoAdapter.submitList(state.items.toList())
        binding.emptyView.isVisible = state.items.isEmpty()
        if (state.items.isEmpty()) {
            binding.emptyView.setText(R.string.error_no_results)
        }
    }

    private fun loadPlaylists() {
        binding.errorView.isVisible = false
        binding.emptyView.isVisible = false
        showSkeleton(binding.list, SkeletonAdapter.STYLE_ROW, count = 8)
        loadJob?.cancel()
        loadJob = lifecycleScope.launch {
            try {
                val (playlists, next) = YtRepository.channelPlaylists(channelUrl)
                playlistItems.clear()
                playlistItems.addAll(playlists)
                playlistPage = next
                playlistsLoaded = true
                if (tab == ChannelTab.PLAYLISTS) {
                    showPlaylists()
                }
            } catch (e: CancellationException) {
                throw e
            } catch (e: Exception) {
                binding.list.adapter = playlistAdapter
                playlistAdapter.submitList(emptyList())
                binding.errorText.text = Formatters.friendlyException(e)
                binding.errorView.isVisible = true
            }
        }
    }

    private fun showPlaylists() {
        binding.errorView.isVisible = false
        binding.list.adapter = playlistAdapter
        playlistAdapter.submitList(playlistItems.toList())
        binding.emptyView.setText(R.string.empty_channel_playlists)
        binding.emptyView.isVisible = playlistItems.isEmpty()
    }

    private fun loadMore() {
        val next = nextPage() ?: return
        isLoading = true
        lifecycleScope.launch {
            try {
                if (tab == ChannelTab.VIDEOS) {
                    val requested = sort
                    val (videos, more) = YtRepository.channelVideosMore(channelUrl, requested, next)
                    val state = videoState.getValue(requested)
                    val known = state.items.map { it.url }.toSet()
                    state.items.addAll(videos.map { it.toUiModel() }.filter { it.url !in known })
                    state.page = more
                    if (tab == ChannelTab.VIDEOS && sort == requested) {
                        videoAdapter.submitList(state.items.toList())
                    }
                } else {
                    val (playlists, more) = YtRepository.channelPlaylistsMore(channelUrl, next)
                    val known = playlistItems.map { it.url }.toSet()
                    playlistItems.addAll(playlists.filter { it.url !in known })
                    playlistPage = more
                    if (tab == ChannelTab.PLAYLISTS) {
                        playlistAdapter.submitList(playlistItems.toList())
                    }
                }
            } catch (e: Exception) {
                if (tab == ChannelTab.VIDEOS) {
                    currentVideos().page = null
                } else {
                    playlistPage = null
                }
            } finally {
                isLoading = false
            }
        }
    }

    private fun toggleSubscribe() {
        val nowSubscribed = LocalStore.toggleSubscription(
            this,
            ChannelEntry(
                url = channelUrl,
                name = channelName,
                avatarUrl = channelAvatar,
                subscriberCount = subscriberCount
            )
        )
        Toast.makeText(
            this,
            if (nowSubscribed) R.string.subscribed_toast else R.string.unsubscribed_toast,
            Toast.LENGTH_SHORT
        ).show()
        updateSubscribeUi()
    }

    private fun updateSubscribeUi() {
        val subscribed = LocalStore.isSubscribed(this, channelUrl)
        binding.subscribeButton.text = getString(if (subscribed) R.string.subscribed else R.string.subscribe)
        binding.subscribeButton.setBackgroundResource(
            if (subscribed) R.drawable.bg_subscribe_on else R.drawable.bg_subscribe_off
        )
    }

    companion object {
        private const val EXTRA_URL = "extra_channel_url"
        private const val EXTRA_NAME = "extra_channel_name"

        fun start(context: Context, url: String, name: String) {
            val intent = Intent(context, ChannelActivity::class.java).apply {
                putExtra(EXTRA_URL, url)
                putExtra(EXTRA_NAME, name)
            }
            context.startActivity(intent)
        }
    }
}
