package com.sparktube.app.ui.music

import android.graphics.Color
import android.graphics.Typeface
import android.graphics.drawable.GradientDrawable
import android.os.Bundle
import android.util.TypedValue
import android.view.Gravity
import android.view.LayoutInflater
import android.view.View
import android.view.ViewGroup
import android.view.ViewOutlineProvider
import android.widget.FrameLayout
import android.widget.GridLayout
import android.widget.ImageButton
import android.widget.ImageView
import android.widget.LinearLayout
import android.widget.PopupMenu
import android.widget.TextView
import android.widget.Toast
import androidx.core.view.isVisible
import androidx.fragment.app.Fragment
import androidx.lifecycle.lifecycleScope
import com.sparktube.app.R
import com.sparktube.app.data.ArtistEntry
import com.sparktube.app.data.LocalStore
import com.sparktube.app.data.YtRepository
import com.sparktube.app.databinding.FragmentMusicBinding
import com.sparktube.app.playback.PlaybackCenter
import com.sparktube.app.ui.common.SkeletonPulse
import com.sparktube.app.ui.common.VideoUiModel
import com.sparktube.app.ui.common.toQueueEntry
import com.sparktube.app.ui.common.toUiModel
import com.sparktube.app.ui.search.SearchActivity
import com.sparktube.app.util.AppPrefs
import com.sparktube.app.util.Formatters
import com.sparktube.app.util.Thumbs
import kotlinx.coroutines.launch

private data class Genre(val label: String, val query: String?, val top: Int, val bottom: Int)

/**
 * YouTube Music-style home: mood chips, Your artists, Quick picks with
 * Play all, mix cards, trending shelf, genres and a popular list.
 */
class MusicFragment : Fragment() {

    private var _binding: FragmentMusicBinding? = null
    private val binding get() = _binding!!

    private var loadJob: kotlinx.coroutines.Job? = null
    private var skeletonPulse: SkeletonPulse? = null
    private var quickPicks: List<VideoUiModel> = emptyList()

    override fun onCreateView(
        inflater: LayoutInflater,
        container: ViewGroup?,
        savedInstanceState: Bundle?
    ): View {
        _binding = FragmentMusicBinding.inflate(inflater, container, false)
        return binding.root
    }

    override fun onViewCreated(view: View, savedInstanceState: Bundle?) {
        super.onViewCreated(view, savedInstanceState)

        binding.searchButton.setOnClickListener {
            startActivity(SearchActivity.intent(requireContext(), music = true))
        }
        binding.favoritesButton.setOnClickListener {
            MusicListActivity.start(requireContext(), favourites = true)
        }
        binding.downloadsButton.setOnClickListener {
            MusicListActivity.start(requireContext(), favourites = false)
        }
        binding.addArtistButton.setOnClickListener {
            ArtistPickerActivity.start(requireContext())
        }
        binding.playAllButton.setOnClickListener { playAllQuickPicks() }

        buildMoodChips()
        buildGenreTiles()
        bindYourArtists()
        load()
    }

    override fun onResume() {
        super.onResume()
        bindYourArtists()
        if (binding.quickPicksList.childCount == 0 && binding.shelfRow.childCount == 0) {
            load()
        }
    }

    private fun playSong(model: VideoUiModel, radio: Boolean) {
        PlaybackCenter.playMusic(model.toQueueEntry(isMusic = true), radio = radio)
        NowPlayingActivity.start(requireContext())
    }

    private fun playAllQuickPicks() {
        if (quickPicks.isEmpty()) return
        PlaybackCenter.playMusicQueue(quickPicks.map { it.toQueueEntry(isMusic = true) }, 0)
        NowPlayingActivity.start(requireContext())
    }

    private fun load() {
        loadJob?.cancel()
        val b = _binding ?: return
        loadJob = viewLifecycleOwner.lifecycleScope.launch {
            b.errorView.isVisible = false
            showMusicSkeleton(b)
            try {
                val result = YtRepository.musicTrending(AppPrefs.countryOrDefault)
                val models = result.items.map { it.toUiModel() }
                clearMusicSkeleton(b)
                bindHome(b, models)
            } catch (e: kotlinx.coroutines.CancellationException) {
                throw e
            } catch (e: Exception) {
                clearMusicSkeleton(b)
                b.errorText.text = Formatters.friendlyException(e)
                b.errorView.isVisible = true
            }
        }
    }

    private fun showMusicSkeleton(b: FragmentMusicBinding) {
        clearMusicSkeleton(b)
        b.quickPicksHeader.isVisible = true
        b.shelfTitle.isVisible = true
        b.shelfScroller.isVisible = true
        b.popularTitle.isVisible = true
        val pulse = SkeletonPulse()
        skeletonPulse = pulse
        repeat(4) { i ->
            val row = layoutInflater.inflate(R.layout.item_skeleton_row, b.quickPicksList, false)
            b.quickPicksList.addView(row)
            pulse.attach(row, i)
        }
        repeat(6) { i ->
            val card = layoutInflater.inflate(R.layout.item_skeleton_music_card, b.shelfRow, false)
            b.shelfRow.addView(card)
            pulse.attach(card, i)
        }
        repeat(4) { i ->
            val row = layoutInflater.inflate(R.layout.item_skeleton_row, b.popularList, false)
            b.popularList.addView(row)
            pulse.attach(row, i)
        }
    }

    private fun clearMusicSkeleton(b: FragmentMusicBinding) {
        skeletonPulse?.cancel()
        skeletonPulse = null
        b.quickPicksList.removeAllViews()
        b.shelfRow.removeAllViews()
        b.popularList.removeAllViews()
        b.mixesRow.removeAllViews()
    }

    private fun bindHome(b: FragmentMusicBinding, models: List<VideoUiModel>) {
        quickPicks = models.take(8)
        b.quickPicksHeader.isVisible = quickPicks.isNotEmpty()
        b.quickPicksList.removeAllViews()
        quickPicks.forEachIndexed { index, model ->
            val row = layoutInflater.inflate(R.layout.item_quick_pick, b.quickPicksList, false)
            Thumbs.load(row.findViewById(R.id.art), model.thumbnailUrl)
            row.findViewById<TextView>(R.id.title).text = model.title
            val plays = model.playsLabel
            row.findViewById<TextView>(R.id.subtitle).text =
                if (plays.isNotEmpty()) "${model.uploader} · $plays" else model.uploader
            row.setOnClickListener { playSong(model, radio = true) }
            row.findViewById<ImageButton>(R.id.moreButton).setOnClickListener { anchor ->
                showSongMenu(anchor, model)
            }
            if (index == 0) {
                row.setBackgroundResource(R.drawable.bg_quick_pick_active)
            }
            b.quickPicksList.addView(row)
        }

        bindMixes(b, models)

        val shelf = models.drop(8).take(12)
        b.shelfTitle.isVisible = shelf.isNotEmpty()
        b.shelfScroller.isVisible = shelf.isNotEmpty()
        b.shelfRow.removeAllViews()
        shelf.forEach { model ->
            val card = layoutInflater.inflate(R.layout.item_music_card, b.shelfRow, false)
            Thumbs.load(card.findViewById(R.id.art), model.thumbnailUrl)
            card.findViewById<TextView>(R.id.title).text = model.title
            card.findViewById<TextView>(R.id.subtitle).text = model.uploader
            card.setOnClickListener { playSong(model, radio = true) }
            b.shelfRow.addView(card)
        }

        val popular = models.drop(20).take(12)
        b.popularTitle.isVisible = popular.isNotEmpty()
        b.popularList.removeAllViews()
        popular.forEach { model ->
            val row = layoutInflater.inflate(R.layout.item_music_row, b.popularList, false)
            Thumbs.load(row.findViewById(R.id.art), model.thumbnailUrl)
            row.findViewById<TextView>(R.id.title).text = model.title
            row.findViewById<TextView>(R.id.subtitle).text = model.uploader
            row.findViewById<TextView>(R.id.duration).text = model.durationLabel
            row.setOnClickListener { playSong(model, radio = true) }
            b.popularList.addView(row)
        }
    }

    private fun bindMixes(b: FragmentMusicBinding, models: List<VideoUiModel>) {
        val ctx = requireContext()
        val cards = mutableListOf<Pair<String, List<VideoUiModel>>>()
        val favs = LocalStore.musicFavorites(ctx)
        if (favs.isNotEmpty()) {
            cards += getString(R.string.music_mix_favorites) to favs.map { it.toUiModel() }
        }
        val recents = LocalStore.musicHistory(ctx)
        if (recents.isNotEmpty()) {
            cards += getString(R.string.music_mix_recents) to recents.map { it.toUiModel() }
        }
        LocalStore.favoriteArtists(ctx).take(3).forEach { artist ->
            val songs = models.filter { it.uploader.equals(artist.name, ignoreCase = true) }
            val queue = if (songs.isNotEmpty()) songs else models.take(8)
            cards += getString(R.string.music_mix_artist_fmt, artist.name) to queue
        }
        if (cards.isEmpty() && models.isNotEmpty()) {
            cards += getString(R.string.music_get_started) to models.take(12)
        }

        b.mixesTitle.isVisible = cards.isNotEmpty()
        b.mixesScroller.isVisible = cards.isNotEmpty()
        b.mixesRow.removeAllViews()
        cards.forEach { (title, songs) ->
            val card = layoutInflater.inflate(R.layout.item_mix_card, b.mixesRow, false)
            card.findViewById<TextView>(R.id.title).text = title
            Thumbs.load(card.findViewById(R.id.art), songs.firstOrNull()?.thumbnailUrl.orEmpty())
            card.setOnClickListener {
                if (songs.isEmpty()) return@setOnClickListener
                PlaybackCenter.playMusicQueue(songs.map { it.toQueueEntry(isMusic = true) }, 0)
                NowPlayingActivity.start(requireContext())
            }
            b.mixesRow.addView(card)
        }
    }

    private fun bindYourArtists() {
        val b = _binding ?: return
        val artists = LocalStore.favoriteArtists(requireContext())
        b.artistsEmpty.isVisible = artists.isEmpty()
        b.artistsScroller.isVisible = artists.isNotEmpty()
        b.artistsRow.removeAllViews()
        artists.forEach { artist ->
            val chip = layoutInflater.inflate(R.layout.item_artist_circle, b.artistsRow, false)
            chip.findViewById<TextView>(R.id.name).text = artist.name
            val avatar = chip.findViewById<ImageView>(R.id.avatar)
            if (artist.avatarUrl.isNotBlank()) {
                Thumbs.load(avatar, artist.avatarUrl)
            } else {
                avatar.setImageResource(R.drawable.ic_person)
            }
            chip.setOnClickListener { ArtistActivity.start(requireContext(), artist) }
            chip.setOnLongClickListener {
                val now = LocalStore.toggleFavoriteArtist(requireContext(), artist)
                Toast.makeText(
                    requireContext(),
                    if (now) R.string.artist_added else R.string.artist_removed,
                    Toast.LENGTH_SHORT
                ).show()
                bindYourArtists()
                true
            }
            b.artistsRow.addView(chip)
        }
    }

    private fun showSongMenu(anchor: View, model: VideoUiModel) {
        val menu = PopupMenu(requireContext(), anchor)
        menu.menu.add(0, 1, 0, R.string.cd_play_song)
        menu.menu.add(0, 2, 1, R.string.go_to_artist)
        if (model.uploader.isNotBlank()) {
            val starred = LocalStore.isFavoriteArtist(requireContext(), model.uploader)
            menu.menu.add(
                0,
                3,
                2,
                if (starred) R.string.artist_unstar else R.string.artist_star
            )
        }
        menu.setOnMenuItemClickListener { item ->
            when (item.itemId) {
                1 -> playSong(model, radio = true)
                2 -> ArtistActivity.start(
                    requireContext(),
                    model.uploader,
                    model.uploaderAvatarUrl.ifBlank { model.thumbnailUrl }
                )
                3 -> {
                    val now = LocalStore.toggleFavoriteArtist(
                        requireContext(),
                        ArtistEntry(
                            name = model.uploader,
                            avatarUrl = model.uploaderAvatarUrl.ifBlank { model.thumbnailUrl }
                        )
                    )
                    Toast.makeText(
                        requireContext(),
                        if (now) R.string.artist_added else R.string.artist_removed,
                        Toast.LENGTH_SHORT
                    ).show()
                    bindYourArtists()
                }
            }
            true
        }
        menu.show()
    }

    private fun buildMoodChips() {
        val row = binding.moodRow
        row.removeAllViews()
        MOODS.forEach { mood ->
            val chip = TextView(requireContext()).apply {
                text = mood.label
                textSize = 13f
                setTextColor(resources.getColor(R.color.on_surface, null))
                setTypeface(null, Typeface.BOLD)
                background = resources.getDrawable(R.drawable.bg_mood_chip, requireContext().theme)
                gravity = Gravity.CENTER
                setPadding(dp(16), dp(8), dp(16), dp(8))
                isClickable = true
                isFocusable = true
                setOnClickListener {
                    startActivity(
                        SearchActivity.intent(requireContext(), music = true, prefill = mood.query)
                    )
                }
            }
            val params = LinearLayout.LayoutParams(
                ViewGroup.LayoutParams.WRAP_CONTENT,
                ViewGroup.LayoutParams.WRAP_CONTENT
            ).apply { marginEnd = dp(8) }
            row.addView(chip, params)
        }
    }

    private fun buildGenreTiles() {
        val grid = binding.genreGrid
        grid.removeAllViews()
        val margin = dp(6)
        GENRES.forEach { genre ->
            val ctx = requireContext()
            val tile = FrameLayout(ctx).apply {
                minimumHeight = dp(84)
                background = GradientDrawable().apply {
                    orientation = GradientDrawable.Orientation.TL_BR
                    cornerRadius = dp(16).toFloat()
                    colors = intArrayOf(genre.top, genre.bottom)
                }
                clipToOutline = true
                outlineProvider = ViewOutlineProvider.BACKGROUND
                isClickable = true
                isFocusable = true
                addView(
                    ImageView(ctx).apply {
                        setImageResource(R.drawable.ic_music_note)
                        setColorFilter(Color.WHITE)
                        alpha = 0.22f
                        rotation = 22f
                        importantForAccessibility = View.IMPORTANT_FOR_ACCESSIBILITY_NO
                    },
                    FrameLayout.LayoutParams(dp(64), dp(64), Gravity.END or Gravity.BOTTOM).apply {
                        marginEnd = -dp(8)
                        bottomMargin = -dp(10)
                    }
                )
                addView(
                    TextView(ctx).apply {
                        text = genre.label
                        textSize = 16f
                        setTextColor(Color.WHITE)
                        setTypeface(null, Typeface.BOLD)
                    },
                    FrameLayout.LayoutParams(
                        ViewGroup.LayoutParams.WRAP_CONTENT,
                        ViewGroup.LayoutParams.WRAP_CONTENT,
                        Gravity.START or Gravity.TOP
                    ).apply { setMargins(dp(14), dp(14), dp(14), dp(14)) }
                )
                setOnClickListener {
                    if (genre.query == null) {
                        binding.root.smoothScrollTo(0, 0)
                        load()
                    } else {
                        startActivity(
                            SearchActivity.intent(requireContext(), music = true, prefill = genre.query)
                        )
                    }
                }
            }
            val params = GridLayout.LayoutParams().apply {
                width = 0
                height = ViewGroup.LayoutParams.WRAP_CONTENT
                columnSpec = GridLayout.spec(GridLayout.UNDEFINED, 1f)
                topMargin = margin
                bottomMargin = margin
                marginStart = margin / 2
                marginEnd = margin / 2
            }
            grid.addView(tile, params)
        }
    }

    private fun dp(value: Int): Int = TypedValue.applyDimension(
        TypedValue.COMPLEX_UNIT_DIP, value.toFloat(), resources.displayMetrics
    ).toInt()

    override fun onDestroyView() {
        super.onDestroyView()
        _binding = null
    }

    companion object {
        private data class Mood(val label: String, val query: String)

        private val MOODS = listOf(
            Mood("Energize", "energizing songs"),
            Mood("Relax", "relaxing music"),
            Mood("Feel good", "feel good hits"),
            Mood("Workout", "workout songs"),
            Mood("Party", "party hits"),
            Mood("Commute", "commute playlist"),
            Mood("Focus", "focus music"),
            Mood("Sleep", "sleep music")
        )

        private val GENRES = listOf(
            Genre("Trending", null, 0xFF1DB954.toInt(), 0xFF126B3A.toInt()),
            Genre("Pop", "pop hits", 0xFFF06292.toInt(), 0xFFAD1457.toInt()),
            Genre("Hip-Hop", "hip hop hits", 0xFF7E57C2.toInt(), 0xFF4527A0.toInt()),
            Genre("Bollywood", "bollywood songs", 0xFFFFA726.toInt(), 0xFFE65100.toInt()),
            Genre("Bangla", "bangla songs", 0xFF66BB6A.toInt(), 0xFF2E7D32.toInt()),
            Genre("Rock", "rock hits", 0xFFEF5350.toInt(), 0xFFB71C1C.toInt()),
            Genre("EDM", "edm hits", 0xFF26C6DA.toInt(), 0xFF00838F.toInt()),
            Genre("Lofi", "lofi songs", 0xFF8D6E63.toInt(), 0xFF4E342E.toInt()),
            Genre("Classical", "classical music", 0xFFAB47BC.toInt(), 0xFF6A1B9A.toInt()),
            Genre("Indie", "indie songs", 0xFF42A5F5.toInt(), 0xFF1565C0.toInt()),
            Genre("Devotional", "devotional songs", 0xFFFFB300.toInt(), 0xFFFF8F00.toInt()),
            Genre("Instrumental", "instrumental music", 0xFF26A69A.toInt(), 0xFF00695C.toInt())
        )
    }
}
