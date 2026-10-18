package com.sparktube.app.util

import android.content.Context
import com.sparktube.app.R

/**
 * One group of a release's "what's new" list, e.g. title "Added" with a few
 * bullet items. A null [title] means an ungrouped list (rendered without a
 * heading).
 */
data class ChangelogSection(val title: String?, val items: List<String>)

/**
 * Turns a GitHub release body into clean, grouped sections for the update
 * prompt, and provides a built-in fallback for releases whose notes are empty
 * or nothing more than the auto-generated "Full Changelog" link.
 */
object Changelog {

    private val LINK = Regex("\\[([^\\]]+)]\\([^)]*\\)")
    private val URL = Regex("https?://\\S+")
    private val BYLINE = Regex("\\s+by\\s+@[^\\s]+")
    private val NOISE = setOf(
        "full changelog", "what's changed", "whats changed",
        "what's new", "whats new", "new contributors", "contributors"
    )

    /**
     * Parses [body] into sections. Markdown headings become section titles,
     * bullet lines become items, links / bare URLs / GitHub by-lines are
     * stripped, and noise lines are dropped. Returns an empty list when
     * nothing useful remains, so the caller can fall back.
     */
    fun parse(body: String?): List<ChangelogSection> {
        if (body.isNullOrBlank()) return emptyList()
        val out = mutableListOf<ChangelogSection>()
        var title: String? = null
        val items = mutableListOf<String>()

        fun flush() {
            if (items.isNotEmpty()) {
                out += ChangelogSection(title, items.toList())
                items.clear()
            }
        }

        for (raw in body.replace("\r", "").split('\n')) {
            val line = raw.trim()
            if (line.isEmpty()) continue
            when {
                line.startsWith("#") -> {
                    flush()
                    val heading = line.trimStart('#').trim()
                    title = if (heading.lowercase() in NOISE) null else heading.ifBlank { null }
                }
                line.startsWith("-") || line.startsWith("*") || line.startsWith("+") -> {
                    cleanItem(line.drop(1))?.let { items += it }
                }
                else -> cleanItem(line)?.let { items += it }
            }
        }
        flush()
        return out
    }

    private fun cleanItem(text: String): String? {
        var s = text.replace(LINK, "$1")
        s = s.replace(URL, "")
        s = s.replace(BYLINE, "")
        s = s.replace(Regex("\\s+in\\s*$"), "")
        s = s.replace(Regex("\\s+"), " ").trim().trim('-', '*', ' ')
        if (s.isEmpty() || s.lowercase() in NOISE) return null
        return s
    }

    /**
     * Built-in notes used when a release has no usable body. [version] is the
     * release being offered (without a leading "v"); unknown versions get a
     * generic stability blurb.
     */
    fun fallback(context: Context, version: String): List<ChangelogSection> {
        val v = version.removePrefix("v").removePrefix("V")
        if (v == "1.2.5") {
            return listOf(
                ChangelogSection(
                    context.getString(R.string.update_section_added),
                    listOf(
                        context.getString(R.string.changelog_125_added_controls),
                        context.getString(R.string.changelog_125_added_catalog)
                    )
                ),
                ChangelogSection(
                    context.getString(R.string.update_section_improved),
                    listOf(
                        context.getString(R.string.changelog_125_improved_overlay),
                        context.getString(R.string.changelog_125_improved_speed_quality)
                    )
                )
            )
        }
        if (v == "1.2.4") {
            return listOf(
                ChangelogSection(
                    context.getString(R.string.update_section_added),
                    listOf(context.getString(R.string.changelog_124_added_gestures))
                ),
                ChangelogSection(
                    context.getString(R.string.update_section_improved),
                    listOf(
                        context.getString(R.string.changelog_124_improved_tap),
                        context.getString(R.string.changelog_124_improved_scrub),
                        context.getString(R.string.changelog_124_improved_feedback)
                    )
                )
            )
        }
        if (v == "1.2.3") {
            return listOf(
                ChangelogSection(
                    context.getString(R.string.update_section_added),
                    listOf(context.getString(R.string.changelog_123_added_loop))
                ),
                ChangelogSection(
                    context.getString(R.string.update_section_improved),
                    listOf(
                        context.getString(R.string.changelog_123_improved_update_ui),
                        context.getString(R.string.changelog_123_improved_loop_ui),
                        context.getString(R.string.changelog_123_improved_notif)
                    )
                )
            )
        }
        return listOf(
            ChangelogSection(null, listOf(context.getString(R.string.changelog_generic)))
        )
    }
}
