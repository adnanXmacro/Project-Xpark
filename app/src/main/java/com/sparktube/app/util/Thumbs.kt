package com.sparktube.app.util

import android.graphics.drawable.ColorDrawable
import android.widget.ImageView
import androidx.core.content.ContextCompat
import coil.load
import com.sparktube.app.R

/**
 * Thumbnail loading for YouTube video images.
 *
 * Feed items often come with `hqdefault.jpg` thumbnails, which are 4:3 with
 * black bars baked into the image. SparkTube upgrades those to the true 16:9
 * `hq720` variant and falls back to `mqdefault` (also 16:9, always present)
 * if the larger one is missing for an old video.
 */
object Thumbs {

    private fun placeholderDrawable(view: ImageView): ColorDrawable =
        ColorDrawable(ContextCompat.getColor(view.context, R.color.thumbnail_placeholder))

    fun load(view: ImageView, url: String?) {
        val original = url.orEmpty()
        if (original.isBlank()) {
            // Recycled views (RecyclerView) must not keep showing whatever
            // the previously bound item's image was — clear to the plain
            // placeholder instead of silently leaving the stale bitmap.
            view.setImageDrawable(placeholderDrawable(view))
            return
        }
        val upgraded = original.replace("/hqdefault.", "/hq720.")
        if (upgraded == original) {
            // Channel avatars and already-16:9 thumbs pass straight through.
            view.load(original) {
                placeholder(placeholderDrawable(view))
                error(placeholderDrawable(view))
                crossfade(true)
            }
            return
        }
        view.load(upgraded) {
            placeholder(placeholderDrawable(view))
            error(placeholderDrawable(view))
            crossfade(true)
            listener(
                onError = { _, _ ->
                    val fallback = original.replace("/hqdefault.", "/mqdefault.")
                    view.load(fallback) {
                        placeholder(placeholderDrawable(view))
                        error(placeholderDrawable(view))
                    }
                }
            )
        }
    }

    /**
     * High-resolution still for the music now-playing square. Walks
     * maxresdefault → sddefault → hq720 → original so a large ImageView
     * is not stuck with a 480px hqdefault bitmap.
     */
    fun loadHiRes(view: ImageView, url: String?, pixelSize: Int = 0) {
        val original = url.orEmpty()
        if (original.isBlank()) {
            view.setImageDrawable(placeholderDrawable(view))
            return
        }
        loadHiResChain(view, stillChain(original), 0, pixelSize)
    }

    private fun loadHiResChain(view: ImageView, chain: List<String>, index: Int, pixelSize: Int) {
        if (index !in chain.indices) {
            view.setImageDrawable(placeholderDrawable(view))
            return
        }
        view.load(chain[index]) {
            placeholder(placeholderDrawable(view))
            error(placeholderDrawable(view))
            crossfade(true)
            allowHardware(true)
            if (pixelSize > 0) size(pixelSize)
            listener(
                onError = { _, _ ->
                    loadHiResChain(view, chain, index + 1, pixelSize)
                }
            )
        }
    }

    /** YouTube still filenames from sharpest to the original URL. */
    private fun stillChain(url: String): List<String> {
        val match = STILL_FILE.find(url) ?: return listOf(url)
        val ext = match.groupValues[2]
        val variants = listOf("maxresdefault", "sddefault", "hq720", "hqdefault", "mqdefault")
            .map { url.replace(match.value, "/$it$ext") }
        return (variants + url).distinct()
    }

    private val STILL_FILE =
        Regex("/(maxresdefault|sddefault|hq720|hqdefault|mqdefault|hqdefault_live)(\\.[A-Za-z0-9]+)")
}
