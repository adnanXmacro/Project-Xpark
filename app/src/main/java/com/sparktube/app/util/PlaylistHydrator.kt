package com.sparktube.app.util

import android.content.Context
import com.sparktube.app.data.LocalStore
import com.sparktube.app.data.VideoEntry
import com.sparktube.app.data.YtRepository
import kotlinx.coroutines.CoroutineScope
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.SupervisorJob
import kotlinx.coroutines.launch
import kotlinx.coroutines.sync.Mutex
import kotlinx.coroutines.sync.withLock
import org.schabi.newpipe.extractor.stream.StreamInfo

object PlaylistHydrator {

    private val scope = CoroutineScope(SupervisorJob() + Dispatchers.IO)
    private val mutex = Mutex()

    fun needsHydrate(title: String, url: String): Boolean {
        val id = YoutubeLinks.videoIdFromUrl(url) ?: return false
        return title.isBlank() || title == id
    }

    fun start(context: Context, playlistId: Long) {
        val app = context.applicationContext
        scope.launch { hydrate(app, playlistId) }
    }

    suspend fun hydrate(context: Context, playlistId: Long): Boolean {
        return mutex.withLock { hydrateLocked(context, playlistId) }
    }

    private suspend fun hydrateLocked(context: Context, playlistId: Long): Boolean {
        val app = context.applicationContext
        val playlist = LocalStore.musicPlaylist(app, playlistId) ?: return false
        var changed = false
        for (entry in playlist.items) {
            if (!needsHydrate(entry.title, entry.url)) continue
            val info = runCatching { YtRepository.streamInfo(entry.url) }.getOrNull() ?: continue
            val filled = fromStream(entry, info)
            if (filled.title == entry.title && filled.thumbnailUrl == entry.thumbnailUrl) continue
            LocalStore.updateMusicPlaylistItem(app, playlistId, filled)
            LocalStore.updateMusicHistoryItem(app, filled)
            changed = true
        }
        return changed
    }

    fun fromStream(entry: VideoEntry, info: StreamInfo): VideoEntry {
        val thumb = info.thumbnails.maxByOrNull { it.height }?.url.orEmpty()
        val fallback = YoutubeLinks.videoIdFromUrl(entry.url)?.let { PlaylistShare.thumbUrl(it) }.orEmpty()
        return entry.copy(
            title = info.name.orEmpty().ifBlank { entry.title },
            uploader = info.uploaderName.orEmpty().ifBlank { entry.uploader },
            thumbnailUrl = thumb.ifBlank { fallback.ifBlank { entry.thumbnailUrl } },
            durationSec = if (info.duration > 0) info.duration else entry.durationSec,
            isMusic = true
        )
    }
}
