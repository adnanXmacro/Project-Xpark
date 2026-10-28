package com.sparktube.app.ui.music

import android.content.Context
import android.content.Intent
import android.os.Bundle
import android.text.Editable
import android.text.TextWatcher
import android.view.LayoutInflater
import android.view.ViewGroup
import android.view.inputmethod.EditorInfo
import android.view.inputmethod.InputMethodManager
import android.widget.Toast
import androidx.appcompat.app.AppCompatActivity
import androidx.core.view.isVisible
import androidx.lifecycle.lifecycleScope
import androidx.recyclerview.widget.LinearLayoutManager
import androidx.recyclerview.widget.RecyclerView
import com.sparktube.app.R
import com.sparktube.app.data.ArtistEntry
import com.sparktube.app.data.LocalStore
import com.sparktube.app.data.YtRepository
import com.sparktube.app.databinding.ActivityArtistPickerBinding
import com.sparktube.app.databinding.ItemArtistPickBinding
import com.sparktube.app.util.Formatters
import com.sparktube.app.util.Themes
import com.sparktube.app.util.Thumbs
import kotlinx.coroutines.CancellationException
import kotlinx.coroutines.Job
import kotlinx.coroutines.async
import kotlinx.coroutines.coroutineScope
import kotlinx.coroutines.delay
import kotlinx.coroutines.launch

/**
 * Pick an artist from recent listens / song favorites, or search YouTube.
 */
class ArtistPickerActivity : AppCompatActivity() {

    private lateinit var binding: ActivityArtistPickerBinding
    private val adapter = PickAdapter { artist -> addArtist(artist) }
    private var searchJob: Job? = null

    override fun onCreate(savedInstanceState: Bundle?) {
        Themes.apply(this)
        super.onCreate(savedInstanceState)
        binding = ActivityArtistPickerBinding.inflate(layoutInflater)
        setContentView(binding.root)

        binding.backButton.setOnClickListener { finish() }
        binding.list.layoutManager = LinearLayoutManager(this)
        binding.list.adapter = adapter

        binding.searchInput.setOnEditorActionListener { _, actionId, _ ->
            if (actionId == EditorInfo.IME_ACTION_SEARCH) {
                runSearch(binding.searchInput.text?.toString().orEmpty(), immediate = true)
                val imm = getSystemService(INPUT_METHOD_SERVICE) as InputMethodManager
                imm.hideSoftInputFromWindow(binding.searchInput.windowToken, 0)
                true
            } else {
                false
            }
        }
        binding.searchInput.addTextChangedListener(object : TextWatcher {
            override fun beforeTextChanged(s: CharSequence?, start: Int, count: Int, after: Int) = Unit
            override fun afterTextChanged(s: Editable?) = Unit
            override fun onTextChanged(s: CharSequence?, start: Int, before: Int, count: Int) {
                runSearch(s?.toString().orEmpty(), immediate = false)
            }
        })
        showInferred()
    }

    override fun onResume() {
        super.onResume()
        Themes.recreateIfNeeded(this)
        if (binding.searchInput.text.isNullOrBlank()) showInferred()
    }

    private fun addArtist(artist: ArtistEntry) {
        val imm = getSystemService(INPUT_METHOD_SERVICE) as InputMethodManager
        imm.hideSoftInputFromWindow(binding.searchInput.windowToken, 0)
        if (LocalStore.isFavoriteArtist(this, artist.name)) {
            Toast.makeText(this, R.string.artist_added, Toast.LENGTH_SHORT).show()
            finish()
            return
        }
        LocalStore.toggleFavoriteArtist(this, artist)
        Toast.makeText(this, R.string.artist_added, Toast.LENGTH_SHORT).show()
        finish()
    }

    private fun showInferred() {
        searchJob?.cancel()
        val artists = LocalStore.inferredArtists(this)
        adapter.submit(artists)
        binding.emptyView.setText(R.string.pick_artist_empty)
        binding.emptyView.isVisible = artists.isEmpty()
        binding.list.isVisible = artists.isNotEmpty()
    }

    private fun runSearch(raw: String, immediate: Boolean) {
        val text = raw.trim()
        if (text.length < 2) {
            showInferred()
            return
        }
        searchJob?.cancel()
        searchJob = lifecycleScope.launch {
            if (!immediate) delay(280)
            binding.emptyView.setText(R.string.pick_artist_searching)
            binding.emptyView.isVisible = true
            binding.list.isVisible = false
            try {
                val artists = searchArtists(text)
                adapter.submit(artists)
                binding.emptyView.setText(R.string.pick_artist_search_empty)
                binding.emptyView.isVisible = artists.isEmpty()
                binding.list.isVisible = artists.isNotEmpty()
            } catch (e: CancellationException) {
                throw e
            } catch (e: Exception) {
                binding.emptyView.text = Formatters.friendlyException(e)
                binding.emptyView.isVisible = true
                binding.list.isVisible = false
            }
        }
    }

    private suspend fun searchArtists(text: String): List<ArtistEntry> = coroutineScope {
        val channels = async { runCatching { YtRepository.searchChannels(text) }.getOrNull() }
        val songs = async { runCatching { YtRepository.searchMusic(text) }.getOrNull() }
        val byName = LinkedHashMap<String, ArtistEntry>()
        channels.await()?.items?.forEach { channel ->
            val name = channel.name.trim()
            if (name.isEmpty()) return@forEach
            byName[name.lowercase()] = ArtistEntry(
                name = name,
                avatarUrl = channel.avatarUrl,
                channelUrl = channel.url
            )
        }
        songs.await()?.items?.forEach { item ->
            val name = item.uploaderName.orEmpty()
                .removeSuffix(" - Topic")
                .trim()
            val key = name.lowercase()
            if (name.isEmpty() || key in byName) return@forEach
            byName[key] = ArtistEntry(
                name = name,
                avatarUrl = item.uploaderAvatars.maxByOrNull { it.height }?.url.orEmpty()
                    .ifBlank { item.thumbnails.maxByOrNull { it.height }?.url.orEmpty() },
                channelUrl = item.uploaderUrl.orEmpty()
            )
        }
        byName.values.toList()
    }

    private class PickAdapter(
        private val onPick: (ArtistEntry) -> Unit
    ) : RecyclerView.Adapter<PickAdapter.VH>() {

        private var items: List<ArtistEntry> = emptyList()

        class VH(val binding: ItemArtistPickBinding) : RecyclerView.ViewHolder(binding.root)

        fun submit(newItems: List<ArtistEntry>) {
            items = newItems
            notifyDataSetChanged()
        }

        override fun onCreateViewHolder(parent: ViewGroup, viewType: Int): VH =
            VH(ItemArtistPickBinding.inflate(LayoutInflater.from(parent.context), parent, false))

        override fun onBindViewHolder(holder: VH, position: Int) {
            val artist = items[position]
            holder.binding.name.text = artist.name
            if (artist.avatarUrl.isNotBlank()) {
                Thumbs.load(holder.binding.avatar, artist.avatarUrl)
            } else {
                holder.binding.avatar.setImageResource(R.drawable.ic_person)
            }
            holder.binding.root.setOnClickListener { onPick(artist) }
        }

        override fun getItemCount(): Int = items.size
    }

    companion object {
        fun start(context: Context) {
            context.startActivity(Intent(context, ArtistPickerActivity::class.java))
        }
    }
}
