package com.sparktube.app.util

import android.net.Uri
import java.util.Locale

/**
 * Parses YouTube *video* URLs only (watch, youtu.be, shorts, embed, live,
 * music.youtube.com watch). Playlists and channels are ignored so those
 * still open in whatever app the user already uses.
 */
object YoutubeLinks {

    private val VIDEO_ID = Regex("^[A-Za-z0-9_-]{11}$")
    private val HOSTS = setOf(
        "youtube.com",
        "www.youtube.com",
        "m.youtube.com",
        "music.youtube.com",
        "youtu.be",
        "www.youtu.be",
        "youtube-nocookie.com",
        "www.youtube-nocookie.com"
    )

    fun videoUrlFrom(raw: String?): String? {
        if (raw.isNullOrBlank()) return null
        val trimmed = raw.trim()
        val uri = runCatching { Uri.parse(trimmed) }.getOrNull() ?: return null
        val id = videoIdFrom(uri) ?: return null
        return "https://www.youtube.com/watch?v=$id"
    }

    fun firstVideoUrlIn(text: String?): String? {
        if (text.isNullOrBlank()) return null
        videoUrlFrom(text.trim())?.let { return it }
        Regex("https?://\\S+").findAll(text).forEach { match ->
            val token = match.value.trimEnd(')', '.', ',', '"', '\'', ']')
            videoUrlFrom(token)?.let { return it }
        }
        Regex("(?:youtu\\.be/|v=)([A-Za-z0-9_-]{11})").find(text)?.let {
            return "https://www.youtube.com/watch?v=${it.groupValues[1]}"
        }
        return null
    }

    fun videoIdFrom(uri: Uri): String? {
        val scheme = uri.scheme?.lowercase(Locale.US)
        if (scheme == "vnd.youtube" || scheme == "vnd.youtube.launch") {
            val ssp = uri.schemeSpecificPart?.substringBefore('?')?.substringBefore('/')
            return validId(ssp) ?: validId(uri.getQueryParameter("v"))
        }
        val host = uri.host?.lowercase(Locale.US) ?: return null
        if (host !in HOSTS) return null
        if (host == "youtu.be" || host == "www.youtu.be") {
            return validId(uri.pathSegments.firstOrNull())
        }
        val segments = uri.pathSegments
        if (segments.isEmpty()) {
            return validId(uri.getQueryParameter("v"))
        }
        val first = segments[0].lowercase(Locale.US)
        return when (first) {
            "watch", "watch_popup" -> validId(uri.getQueryParameter("v"))
            "shorts", "embed", "live", "v", "e" ->
                validId(segments.getOrNull(1))
            "attribution_link" -> {
                val nested = uri.getQueryParameter("u") ?: return null
                videoUrlFrom("https://www.youtube.com$nested")
                    ?.let { Uri.parse(it).getQueryParameter("v") }
            }
            else -> validId(uri.getQueryParameter("v"))
        }
    }

    private fun validId(id: String?): String? =
        id?.takeIf { VIDEO_ID.matches(it) }
}
