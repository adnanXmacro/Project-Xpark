package com.sparktube.app.ui.common

import android.view.LayoutInflater
import android.view.ViewGroup
import androidx.recyclerview.widget.DiffUtil
import androidx.recyclerview.widget.ListAdapter
import androidx.recyclerview.widget.RecyclerView
import com.sparktube.app.R
import com.sparktube.app.data.YoutubePlaylistItem
import com.sparktube.app.databinding.ItemPlaylistBinding
import com.sparktube.app.util.Thumbs

class YoutubePlaylistAdapter(
    private val onClick: (YoutubePlaylistItem) -> Unit
) : ListAdapter<YoutubePlaylistItem, YoutubePlaylistAdapter.VH>(DIFF) {

    companion object {
        private val DIFF = object : DiffUtil.ItemCallback<YoutubePlaylistItem>() {
            override fun areItemsTheSame(a: YoutubePlaylistItem, b: YoutubePlaylistItem): Boolean =
                a.url == b.url

            override fun areContentsTheSame(a: YoutubePlaylistItem, b: YoutubePlaylistItem): Boolean =
                a == b
        }
    }

    class VH(val binding: ItemPlaylistBinding) : RecyclerView.ViewHolder(binding.root)

    override fun onCreateViewHolder(parent: ViewGroup, viewType: Int): VH =
        VH(ItemPlaylistBinding.inflate(LayoutInflater.from(parent.context), parent, false))

    override fun onBindViewHolder(holder: VH, position: Int) {
        val item = getItem(position)
        val b = holder.binding
        b.title.text = item.name
        b.meta.text = if (item.streamCount >= 0) {
            val count = item.streamCount.coerceAtMost(Int.MAX_VALUE.toLong()).toInt()
            b.root.context.resources.getQuantityString(R.plurals.playlist_video_count, count, count)
        } else {
            ""
        }
        Thumbs.load(b.thumb, item.thumbnailUrl)
        b.root.setOnClickListener { onClick(item) }
        b.root.setOnLongClickListener(null)
    }
}
