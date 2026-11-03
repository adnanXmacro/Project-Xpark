package com.sparktube.app.util

import android.net.Uri
import com.sparktube.app.data.VideoEntry

object PlaylistShare {

    const val SCHEME = "opentube"
    const val HOST = "playlist"
    const val QR_MAX_IDS = 80

    fun encode(name: String, ids: List<String>, maxIds: Int = Int.MAX_VALUE): String {
        val cleanName = name.trim()
        val taken = ids.filter { it.isNotBlank() }.take(maxIds)
        return Uri.Builder()
            .scheme(SCHEME)
            .authority(HOST)
            .appendQueryParameter("n", cleanName)
            .appendQueryParameter("v", taken.joinToString(","))
            .build()
            .toString()
    }

    fun idsOf(items: List<VideoEntry>): List<String> =
        items.mapNotNull { YoutubeLinks.videoIdFromUrl(it.url) }.distinct()

    data class Payload(val name: String, val ids: List<String>)

    fun decode(raw: String?): Payload? {
        if (raw.isNullOrBlank()) return null
        val trimmed = raw.trim()
        val uri = runCatching { Uri.parse(trimmed) }.getOrNull() ?: return null
        if (uri.scheme.equals(SCHEME, ignoreCase = true) && uri.host.equals(HOST, ignoreCase = true)) {
            return fromUri(uri)
        }
        val nested = Regex("$SCHEME://$HOST\\S*").find(trimmed)?.value
        if (nested != null) {
            val nestedUri = runCatching { Uri.parse(nested) }.getOrNull() ?: return null
            return fromUri(nestedUri)
        }
        return null
    }

    fun fromUri(uri: Uri): Payload? {
        if (!uri.scheme.equals(SCHEME, ignoreCase = true)) return null
        if (!uri.host.equals(HOST, ignoreCase = true)) return null
        val ids = uri.getQueryParameter("v")
            ?.split(',')
            ?.map { it.trim() }
            ?.filter { YoutubeLinks.videoIdFromUrl(it) != null }
            .orEmpty()
        if (ids.isEmpty()) return null
        val name = uri.getQueryParameter("n")?.trim().orEmpty()
        return Payload(name, ids)
    }

    fun watchUrl(id: String): String = "https://www.youtube.com/watch?v=$id"

    fun thumbUrl(id: String): String = "https://i.ytimg.com/vi/$id/hqdefault.jpg"
}
