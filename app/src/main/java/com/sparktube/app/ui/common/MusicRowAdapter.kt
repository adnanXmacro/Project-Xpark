package com.sparktube.app.ui.common

import android.view.LayoutInflater
import android.view.ViewGroup
import androidx.recyclerview.widget.DiffUtil
import androidx.recyclerview.widget.RecyclerView
import com.sparktube.app.databinding.ItemMusicRowBinding
import com.sparktube.app.util.Thumbs

/** Spotify-style song row: square art, bold title, gray artist line. */
class MusicRowAdapter(
    private val onClick: (VideoUiModel) -> Unit,
    private val onLongClick: ((VideoUiModel) -> Unit)? = null,
    private val onMore: ((android.view.View, VideoUiModel) -> Unit)? = null
) : RecyclerView.Adapter<MusicRowAdapter.VH>() {

    private val items = mutableListOf<VideoUiModel>()

    val currentList: List<VideoUiModel>
        get() = items.toList()

    fun submitList(list: List<VideoUiModel>) {
        val old = items.toList()
        val diff = DiffUtil.calculateDiff(object : DiffUtil.Callback() {
            override fun getOldListSize(): Int = old.size
            override fun getNewListSize(): Int = list.size
            override fun areItemsTheSame(oldItemPosition: Int, newItemPosition: Int): Boolean =
                old[oldItemPosition].url == list[newItemPosition].url
            override fun areContentsTheSame(oldItemPosition: Int, newItemPosition: Int): Boolean {
                val a = old[oldItemPosition]
                val b = list[newItemPosition]
                return a.url == b.url &&
                    a.title == b.title &&
                    a.uploader == b.uploader &&
                    a.thumbnailUrl == b.thumbnailUrl &&
                    a.durationSec == b.durationSec
            }
        })
        items.clear()
        items.addAll(list)
        diff.dispatchUpdatesTo(this)
    }

    fun moveRow(from: Int, to: Int) {
        if (from == to) return
        if (from !in items.indices || to !in items.indices) return
        val item = items.removeAt(from)
        items.add(to, item)
        notifyItemMoved(from, to)
    }

    class VH(val binding: ItemMusicRowBinding) : RecyclerView.ViewHolder(binding.root)

    override fun onCreateViewHolder(parent: ViewGroup, viewType: Int): VH =
        VH(ItemMusicRowBinding.inflate(LayoutInflater.from(parent.context), parent, false))

    override fun getItemCount(): Int = items.size

    override fun onBindViewHolder(holder: VH, position: Int) {
        val item = items[position]
        val b = holder.binding

        b.title.text = item.title
        b.subtitle.text = item.uploader
        b.duration.text = item.durationLabel

        Thumbs.load(b.art, item.thumbnailUrl)

        b.root.setOnClickListener { onClick(item) }
        if (onLongClick != null) {
            b.root.setOnLongClickListener {
                onLongClick.invoke(item)
                true
            }
        } else {
            b.root.setOnLongClickListener(null)
        }
        if (onMore != null) {
            b.moreButton.visibility = android.view.View.VISIBLE
            b.moreButton.setOnClickListener { view -> onMore.invoke(view, item) }
        } else {
            b.moreButton.visibility = android.view.View.GONE
            b.moreButton.setOnClickListener(null)
        }
    }
}
