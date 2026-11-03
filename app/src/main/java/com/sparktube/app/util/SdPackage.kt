package com.sparktube.app.util

import android.content.Context
import android.net.Uri
import com.sparktube.app.data.VideoEntry
import org.json.JSONArray
import org.json.JSONObject
import java.io.File
import java.io.InputStream
import java.util.zip.ZipEntry
import java.util.zip.ZipInputStream
import java.util.zip.ZipOutputStream

object SdPackage {

    const val KIND = "music-playlist"
    const val MANIFEST = "manifest.json"

    data class PackedItem(
        val entry: VideoEntry,
        val zipPath: String?
    )

    data class PackedPlaylist(
        val name: String,
        val items: List<PackedItem>
    )

    fun write(
        context: Context,
        uri: Uri,
        name: String,
        items: List<PackedItem>,
        files: Map<String, File>
    ) {
        context.contentResolver.openOutputStream(uri)?.use { out ->
            ZipOutputStream(out).use { zip ->
                val playlist = JSONObject()
                playlist.put("name", name)
                val arr = JSONArray()
                items.forEach { item ->
                    val obj = JSONObject()
                    obj.put("url", item.entry.url)
                    obj.put("title", item.entry.title)
                    obj.put("uploader", item.entry.uploader)
                    obj.put("thumb", item.entry.thumbnailUrl)
                    obj.put("duration", item.entry.durationSec)
                    obj.put("music", true)
                    if (item.zipPath != null) obj.put("file", item.zipPath)
                    arr.put(obj)
                }
                playlist.put("items", arr)
                val manifest = JSONObject()
                manifest.put("app", "OpenTube")
                manifest.put("kind", KIND)
                manifest.put("version", 1)
                manifest.put("playlist", playlist)
                zip.putNextEntry(ZipEntry(MANIFEST))
                zip.write(manifest.toString().toByteArray(Charsets.UTF_8))
                zip.closeEntry()
                files.forEach { (path, file) ->
                    if (!file.isFile) return@forEach
                    zip.putNextEntry(ZipEntry(path))
                    file.inputStream().use { it.copyTo(zip) }
                    zip.closeEntry()
                }
            }
        } ?: error("Could not open destination")
    }

    fun read(context: Context, uri: Uri): PackedPlaylist {
        val manifest = context.contentResolver.openInputStream(uri)?.use { input ->
            findManifest(input)
        } ?: error("empty")
        val root = JSONObject(manifest)
        if (root.optString("kind") != KIND) error("kind")
        val playlist = root.getJSONObject("playlist")
        val name = playlist.optString("name").ifBlank { "Playlist" }
        val items = mutableListOf<PackedItem>()
        val arr = playlist.optJSONArray("items") ?: JSONArray()
        for (i in 0 until arr.length()) {
            val obj = arr.getJSONObject(i)
            val url = obj.optString("url")
            if (url.isBlank()) continue
            items += PackedItem(
                entry = VideoEntry(
                    url = url,
                    title = obj.optString("title"),
                    uploader = obj.optString("uploader"),
                    thumbnailUrl = obj.optString("thumb"),
                    durationSec = obj.optLong("duration"),
                    isMusic = true
                ),
                zipPath = obj.optString("file").takeIf { it.isNotBlank() }
            )
        }
        if (items.isEmpty()) error("empty")
        return PackedPlaylist(name, items)
    }

    fun extractEntry(context: Context, uri: Uri, zipPath: String, dest: File): Boolean {
        dest.parentFile?.mkdirs()
        context.contentResolver.openInputStream(uri)?.use { input ->
            ZipInputStream(input).use { zip ->
                var entry = zip.nextEntry
                while (entry != null) {
                    if (entry.name == zipPath) {
                        dest.outputStream().use { zip.copyTo(it) }
                        return dest.isFile && dest.length() > 0L
                    }
                    entry = zip.nextEntry
                }
            }
        }
        return false
    }

    private fun findManifest(input: InputStream): String {
        ZipInputStream(input).use { zip ->
            var entry = zip.nextEntry
            while (entry != null) {
                if (entry.name == MANIFEST) {
                    return zip.readBytes().toString(Charsets.UTF_8)
                }
                entry = zip.nextEntry
            }
        }
        error("manifest")
    }

    fun sanitizeFileName(raw: String): String {
        val cleaned = raw.replace(Regex("[\\\\/:*?\"<>|]"), "_").trim().trim('.')
        return cleaned.take(80).ifBlank { "track" }
    }
}
