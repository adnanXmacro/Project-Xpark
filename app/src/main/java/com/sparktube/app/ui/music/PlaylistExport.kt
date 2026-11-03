package com.sparktube.app.ui.music

import android.content.Context
import android.net.Uri
import androidx.documentfile.provider.DocumentFile
import com.sparktube.app.data.LocalStore
import com.sparktube.app.data.Playlist
import com.sparktube.app.data.VideoEntry
import com.sparktube.app.data.YtRepository
import com.sparktube.app.download.DownloadCenter
import com.sparktube.app.playback.StreamCatalog
import com.sparktube.app.util.AppPrefs
import com.sparktube.app.util.SdPackage
import com.sparktube.app.util.YoutubeLinks
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.withContext
import org.schabi.newpipe.extractor.stream.AudioStream
import java.io.File

object PlaylistExport {

    suspend fun downloadMissing(context: Context, items: List<VideoEntry>, onProgress: (Int, Int) -> Unit): Int {
        val missing = items.filter {
            DownloadCenter.doneAudioFile(context, it.url) == null &&
                !DownloadCenter.hasActiveDownload(context, it.url)
        }
        if (missing.isEmpty()) return 0
        var started = 0
        missing.forEachIndexed { index, entry ->
            onProgress(index + 1, missing.size)
            runCatching {
                val info = YtRepository.streamInfo(entry.url)
                val stream = pickAudio(StreamCatalog(info)) ?: return@runCatching
                val kbps = if (stream.averageBitrate > 0) "${stream.averageBitrate} kbps" else "Audio"
                DownloadCenter.startSingle(
                    context,
                    entry.url,
                    entry.title,
                    entry.uploader,
                    entry.thumbnailUrl,
                    DownloadCenter.TYPE_AUDIO,
                    kbps,
                    stream.content,
                    "m4a"
                )
                started++
            }
        }
        return started
    }

    fun downloadedItems(context: Context, items: List<VideoEntry>): List<Pair<VideoEntry, File>> =
        items.mapNotNull { entry ->
            DownloadCenter.doneAudioFile(context, entry.url)?.let { entry to it }
        }

    suspend fun writeMp3(context: Context, treeUri: Uri, playlist: Playlist, files: List<Pair<VideoEntry, File>>) = withContext(Dispatchers.IO) {
        val tree = DocumentFile.fromTreeUri(context, treeUri) ?: error("tree")
        val folderName = SdPackage.sanitizeFileName(playlist.name)
        val folder = tree.findFile(folderName)?.takeIf { it.isDirectory }
            ?: tree.createDirectory(folderName)
            ?: error("folder")
        val names = mutableListOf<String>()
        files.forEachIndexed { index, (entry, file) ->
            val name = "%02d %s.m4a".format(index + 1, SdPackage.sanitizeFileName(entry.title.ifBlank { "track" }))
            val dest = folder.createFile("audio/mp4", name) ?: return@forEachIndexed
            context.contentResolver.openOutputStream(dest.uri)?.use { out ->
                file.inputStream().use { it.copyTo(out) }
            }
            names += dest.name ?: name
        }
        val m3u = folder.createFile("audio/x-mpegurl", "playlist.m3u") ?: return@withContext
        context.contentResolver.openOutputStream(m3u.uri)?.use { out ->
            out.write(names.joinToString("\n").toByteArray())
        }
    }

    suspend fun writePackage(context: Context, dest: Uri, playlist: Playlist, files: List<Pair<VideoEntry, File>>) = withContext(Dispatchers.IO) {
        val byUrl = files.associate { (entry, file) -> entry.url to file }
        val packed = playlist.items.map { entry ->
            val id = YoutubeLinks.videoIdFromUrl(entry.url) ?: DownloadCenter.videoIdOf(entry.url)
            val file = byUrl[entry.url]
            val zipPath = if (file != null) "audio/$id.m4a" else null
            SdPackage.PackedItem(entry, zipPath)
        }
        val zipFiles = packed.mapNotNull { item ->
            val file = byUrl[item.entry.url] ?: return@mapNotNull null
            val path = item.zipPath ?: return@mapNotNull null
            path to file
        }.toMap()
        SdPackage.write(context, dest, playlist.name, packed, zipFiles)
    }

    suspend fun importPackage(
        context: Context,
        uri: Uri,
        storeOnDevice: Boolean
    ): Playlist = withContext(Dispatchers.IO) {
        val packed = SdPackage.read(context, uri)
        val name = LocalStore.uniqueMusicPlaylistName(context, packed.name)
        val playlist = LocalStore.createMusicPlaylist(context, name) ?: error("create")
        packed.items.forEach { item ->
            LocalStore.addToMusicPlaylist(context, playlist.id, item.entry)
            val zipPath = item.zipPath ?: return@forEach
            if (storeOnDevice) {
                val tmp = File(context.cacheDir, "sdimport/${DownloadCenter.videoIdOf(item.entry.url)}.m4a")
                if (SdPackage.extractEntry(context, uri, zipPath, tmp)) {
                    DownloadCenter.registerImportedAudio(
                        context,
                        item.entry.url,
                        item.entry.title,
                        item.entry.uploader,
                        item.entry.thumbnailUrl,
                        tmp
                    )
                    tmp.delete()
                }
            } else {
                DownloadCenter.setSdPlay(
                    item.entry.url,
                    DownloadCenter.SdPlayRef(uri, zipPath, playlist.id)
                )
            }
        }
        LocalStore.musicPlaylist(context, playlist.id) ?: playlist
    }

    private fun pickAudio(catalog: StreamCatalog): AudioStream? {
        val group = catalog.audioTracks.firstOrNull() ?: return null
        val streams = group.streams.filter { it.isUrl && it.averageBitrate > 0 }
        if (streams.isEmpty()) return group.best
        return when (AppPrefs.musicAudioQuality) {
            AppPrefs.AUDIO_QUALITY_LOW -> streams.last()
            AppPrefs.AUDIO_QUALITY_MEDIUM -> streams[streams.size / 2]
            else -> streams.first()
        }
    }
}
