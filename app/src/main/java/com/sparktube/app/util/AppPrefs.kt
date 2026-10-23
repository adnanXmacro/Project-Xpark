package com.sparktube.app.util

import android.content.Context
import android.content.SharedPreferences

object AppPrefs {

    private const val PREFS_NAME = "sparktube_prefs"
    private const val KEY_COUNTRY = "country"
    private const val KEY_THEME = "theme"
    private const val KEY_ACCENT = "accent"
    private const val KEY_VIDEO_AUTOPLAY_NEXT = "video_autoplay_next"
    private const val KEY_SMART_STREAMING = "smart_streaming"
    private const val KEY_MUSIC_AUDIO_QUALITY = "music_audio_quality"
    private const val KEY_MUSIC_AUTOPLAY_NEXT = "music_autoplay_next"
    private const val KEY_MUSIC_LOOP_MODE = "music_loop_mode"
    private const val KEY_MUSIC_LOOP_COUNT = "music_loop_count"
    private const val KEY_MUSIC_LOOP_MINUTES = "music_loop_minutes"
    private const val KEY_ANIMATIONS = "animations"
    private const val KEY_OPEN_YOUTUBE_LINKS_HINT = "open_youtube_links_hint_shown_v128"

    // Default video quality: separate values for Wi-Fi and mobile data.
    private const val KEY_DEFAULT_VIDEO_HEIGHT_WIFI = "default_video_height_wifi"
    private const val KEY_DEFAULT_VIDEO_HEIGHT_DATA = "default_video_height_data"
    // Legacy single-quality key (pre 1.1.0) used for the one-time migration.
    private const val KEY_DEFAULT_VIDEO_HEIGHT_LEGACY = "default_video_height"

    // Fullscreen video scale mode: fit / crop / stretch.
    private const val KEY_FULLSCREEN_SCALE = "fullscreen_scale"

    // ----- Custom video player controls -----

    /** Ordered, comma-separated ids of the controls visible in the player. */
    private const val KEY_PLAYER_CONTROLS = "player_controls"
    /** Ordered, comma-separated playback-speed presets. */
    private const val KEY_PLAYER_SPEED_PRESETS = "player_speed_presets"
    /** Default speed applied when a video starts. */
    private const val KEY_PLAYER_DEFAULT_SPEED = "player_default_speed"
    /** Hide the overlay after this many milliseconds of inactivity. */
    private const val KEY_PLAYER_CONTROLS_TIMEOUT = "player_controls_timeout"
    /** Loop the current video forever instead of advancing the queue. */
    private const val KEY_PLAYER_LOOP_VIDEO = "player_loop_video"

    val DEFAULT_SPEED_PRESETS = listOf(0.5f, 0.75f, 1f, 1.25f, 1.5f, 1.75f, 2f)

    /** Sensible choices for the auto-hide delay, in milliseconds. */
    val CONTROLS_TIMEOUT_CHOICES = listOf(2000, 3000, 5000, 8000, 12000)

    const val DEFAULT_COUNTRY = "US"

    const val THEME_AUTO = "auto"
    const val THEME_LIGHT = "light"
    const val THEME_DARK = "dark"
    const val THEME_PITCH_BLACK = "pitch_black"

    const val AUDIO_QUALITY_HIGH = "high"
    const val AUDIO_QUALITY_MEDIUM = "medium"
    const val AUDIO_QUALITY_LOW = "low"

    const val SCALE_FIT = "fit"
    const val SCALE_CROP = "crop"
    const val SCALE_STRETCH = "stretch"

    // Music loop modes: the current song repeats forever, a fixed number of
    // extra times, or until a timer runs out.
    const val LOOP_OFF = "off"
    const val LOOP_INFINITE = "infinite"
    const val LOOP_COUNT = "count"
    const val LOOP_TIMER = "timer"

    const val DEFAULT_LOOP_COUNT = 3
    const val DEFAULT_LOOP_MINUTES = 30

    private lateinit var sp: SharedPreferences

    fun init(context: Context) {
        sp = context.applicationContext.getSharedPreferences(PREFS_NAME, Context.MODE_PRIVATE)
        migrateLegacyQuality()
    }

    /**
     * One-time migration: carries the pre-1.1.0 single "default video quality"
     * into the Wi-Fi slot (and a data-friendly value into the data slot) so
     * existing users keep a sensible starting point.
     */
    private fun migrateLegacyQuality() {
        if (!sp.contains(KEY_DEFAULT_VIDEO_HEIGHT_LEGACY)) return
        if (!sp.contains(KEY_DEFAULT_VIDEO_HEIGHT_WIFI) && !sp.contains(KEY_DEFAULT_VIDEO_HEIGHT_DATA)) {
            val legacy = sp.getInt(KEY_DEFAULT_VIDEO_HEIGHT_LEGACY, 720)
            sp.edit()
                .putInt(KEY_DEFAULT_VIDEO_HEIGHT_WIFI, legacy.coerceAtLeast(720))
                .putInt(KEY_DEFAULT_VIDEO_HEIGHT_DATA, legacy.coerceAtMost(480))
                .apply()
        }
        sp.edit().remove(KEY_DEFAULT_VIDEO_HEIGHT_LEGACY).apply()
    }

    var country: String?
        get() = sp.getString(KEY_COUNTRY, null)
        set(value) {
            sp.edit().putString(KEY_COUNTRY, value).apply()
        }

    val countryOrDefault: String
        get() = country ?: DEFAULT_COUNTRY

    /** UI theme: auto / light / dark / pitch_black (default keeps the old always-dark look). */
    var theme: String
        get() = sp.getString(KEY_THEME, THEME_DARK) ?: THEME_DARK
        set(value) {
            sp.edit().putString(KEY_THEME, value).apply()
        }

    /** Accent color key: red / blue / purple / green / orange / pink / teal. */
    var accent: String
        get() = sp.getString(KEY_ACCENT, "red") ?: "red"
        set(value) {
            sp.edit().putString(KEY_ACCENT, value).apply()
        }

    // ----- Default video quality (per network type) -----

    /** 0 = auto (best available), otherwise a pixel height like 720. Default: 720p (fast start). */
    var defaultVideoHeightWifi: Int
        get() = sp.getInt(KEY_DEFAULT_VIDEO_HEIGHT_WIFI, 720)
        set(value) {
            sp.edit().putInt(KEY_DEFAULT_VIDEO_HEIGHT_WIFI, value).apply()
        }

    /** Mobile-data default. Lower default: 480p keeps buffered starts smooth on slow radios. */
    var defaultVideoHeightData: Int
        get() = sp.getInt(KEY_DEFAULT_VIDEO_HEIGHT_DATA, 480)
        set(value) {
            sp.edit().putInt(KEY_DEFAULT_VIDEO_HEIGHT_DATA, value).apply()
        }

    /**
     * The default that applies right now: the Wi-Fi pick when the current
     * network is unmetered Wi-Fi, the data pick otherwise.
     */
    fun defaultVideoHeightNow(context: Context): Int =
        if (Net.isOnWifi(context)) defaultVideoHeightWifi else defaultVideoHeightData

    /**
     * Adaptive (DASH) start for "Auto" / default-quality playback: begins with small
     * segments and adapts the quality like the official player. Turn off to always
     * use the plain single-file stream.
     */
    var smartStreaming: Boolean
        get() = sp.getBoolean(KEY_SMART_STREAMING, true)
        set(value) {
            sp.edit().putBoolean(KEY_SMART_STREAMING, value).apply()
        }

    var videoAutoplayNext: Boolean
        get() = sp.getBoolean(KEY_VIDEO_AUTOPLAY_NEXT, true)
        set(value) {
            sp.edit().putBoolean(KEY_VIDEO_AUTOPLAY_NEXT, value).apply()
        }

    /** Music streaming quality: high / medium / low. */
    var musicAudioQuality: String
        get() = sp.getString(KEY_MUSIC_AUDIO_QUALITY, AUDIO_QUALITY_HIGH) ?: AUDIO_QUALITY_HIGH
        set(value) {
            sp.edit().putString(KEY_MUSIC_AUDIO_QUALITY, value).apply()
        }

    var musicAutoplayNext: Boolean
        get() = sp.getBoolean(KEY_MUSIC_AUTOPLAY_NEXT, true)
        set(value) {
            sp.edit().putBoolean(KEY_MUSIC_AUTOPLAY_NEXT, value).apply()
        }

    /**
     * How the current song loops: [LOOP_OFF], [LOOP_INFINITE], [LOOP_COUNT]
     * or [LOOP_TIMER]. Persisted so the choice survives app restarts.
     */
    var musicLoopMode: String
        get() = sp.getString(KEY_MUSIC_LOOP_MODE, LOOP_OFF) ?: LOOP_OFF
        set(value) {
            sp.edit().putString(KEY_MUSIC_LOOP_MODE, value).apply()
        }

    /** Extra repeats for [LOOP_COUNT] (min 1). */
    var musicLoopCount: Int
        get() = sp.getInt(KEY_MUSIC_LOOP_COUNT, DEFAULT_LOOP_COUNT).coerceAtLeast(1)
        set(value) {
            sp.edit().putInt(KEY_MUSIC_LOOP_COUNT, value.coerceAtLeast(1)).apply()
        }

    /** Minutes to keep looping for [LOOP_TIMER] (min 1). */
    var musicLoopMinutes: Int
        get() = sp.getInt(KEY_MUSIC_LOOP_MINUTES, DEFAULT_LOOP_MINUTES).coerceAtLeast(1)
        set(value) {
            sp.edit().putInt(KEY_MUSIC_LOOP_MINUTES, value.coerceAtLeast(1)).apply()
        }

    /** UI animations (tab switches, transitions): on by default. */
    var animations: Boolean
        get() = sp.getBoolean(KEY_ANIMATIONS, true)
        set(value) {
            sp.edit().putBoolean(KEY_ANIMATIONS, value).apply()
        }

    /** One-shot "this update adds Open YouTube links" tip. */
    var openYoutubeLinksHintShown: Boolean
        get() = sp.getBoolean(KEY_OPEN_YOUTUBE_LINKS_HINT, false)
        set(value) {
            sp.edit().putBoolean(KEY_OPEN_YOUTUBE_LINKS_HINT, value).apply()
        }

    /** Fullscreen video scale preference: fit / crop / stretch (default: fit). */
    var fullscreenScale: String
        get() = sp.getString(KEY_FULLSCREEN_SCALE, SCALE_FIT) ?: SCALE_FIT
        set(value) {
            sp.edit().putString(KEY_FULLSCREEN_SCALE, value).apply()
        }

    // ----- Custom video player controls -----

    /**
     * Ordered ids of the controls visible in the video player. Unknown ids are
     * filtered out lazily by the caller (see PlayerControlRegistry), so an old
     * preference never crashes the player after a catalog change.
     */
    var playerControls: List<String>
        get() = sp.getString(KEY_PLAYER_CONTROLS, null)
            ?.split(',')
            ?.filter { it.isNotBlank() }
            ?: com.sparktube.app.player.PlayerControlRegistry.defaultVisibleIds
        set(value) {
            sp.edit().putString(KEY_PLAYER_CONTROLS, value.joinToString(",")).apply()
        }

    /** Ordered playback-speed choices offered by the speed control. */
    var playerSpeedPresets: List<Float>
        get() = sp.getString(KEY_PLAYER_SPEED_PRESETS, null)
            ?.split(',')
            ?.mapNotNull { it.toFloatOrNull() }
            ?.takeIf { it.isNotEmpty() }
            ?: DEFAULT_SPEED_PRESETS
        set(value) {
            sp.edit().putString(KEY_PLAYER_SPEED_PRESETS, value.joinToString(",")).apply()
        }

    /** Speed applied automatically when a video starts (1.0 = normal). */
    var playerDefaultSpeed: Float
        get() = sp.getFloat(KEY_PLAYER_DEFAULT_SPEED, 1.0f)
        set(value) {
            sp.edit().putFloat(KEY_PLAYER_DEFAULT_SPEED, value).apply()
        }

    /** Milliseconds of inactivity before the player overlay fades out. */
    var playerControlsTimeout: Int
        get() = sp.getInt(KEY_PLAYER_CONTROLS_TIMEOUT, 4000)
        set(value) {
            sp.edit().putInt(KEY_PLAYER_CONTROLS_TIMEOUT, value).apply()
        }

    /** Loop the current video (not just music) forever, YouTube-style. */
    var playerLoopVideo: Boolean
        get() = sp.getBoolean(KEY_PLAYER_LOOP_VIDEO, false)
        set(value) {
            sp.edit().putBoolean(KEY_PLAYER_LOOP_VIDEO, value).apply()
        }
}
