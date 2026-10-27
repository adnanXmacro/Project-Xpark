package com.sparktube.app.ui.music

import android.content.Context
import android.content.Intent
import android.os.Bundle
import android.view.LayoutInflater
import android.view.ViewGroup
import android.widget.Toast
import androidx.appcompat.app.AppCompatActivity
import androidx.core.view.isVisible
import androidx.recyclerview.widget.LinearLayoutManager
import androidx.recyclerview.widget.RecyclerView
import com.sparktube.app.R
import com.sparktube.app.data.ArtistEntry
import com.sparktube.app.data.LocalStore
import com.sparktube.app.databinding.ActivityArtistPickerBinding
import com.sparktube.app.databinding.ItemArtistPickBinding
import com.sparktube.app.util.Themes
import com.sparktube.app.util.Thumbs

/**
 * Pick an artist from recent listens / song favorites to add to Your artists.
 */
class ArtistPickerActivity : AppCompatActivity() {

    private lateinit var binding: ActivityArtistPickerBinding

    override fun onCreate(savedInstanceState: Bundle?) {
        Themes.apply(this)
        super.onCreate(savedInstanceState)
        binding = ActivityArtistPickerBinding.inflate(layoutInflater)
        setContentView(binding.root)

        binding.backButton.setOnClickListener { finish() }
        binding.list.layoutManager = LinearLayoutManager(this)
        reload()
    }

    override fun onResume() {
        super.onResume()
        Themes.recreateIfNeeded(this)
        reload()
    }

    private fun reload() {
        val artists = LocalStore.inferredArtists(this)
        binding.emptyView.isVisible = artists.isEmpty()
        binding.list.isVisible = artists.isNotEmpty()
        binding.list.adapter = PickAdapter(artists) { artist ->
            LocalStore.toggleFavoriteArtist(this, artist)
            Toast.makeText(this, R.string.artist_added, Toast.LENGTH_SHORT).show()
            finish()
        }
    }

    private class PickAdapter(
        private val items: List<ArtistEntry>,
        private val onPick: (ArtistEntry) -> Unit
    ) : RecyclerView.Adapter<PickAdapter.VH>() {

        class VH(val binding: ItemArtistPickBinding) : RecyclerView.ViewHolder(binding.root)

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
