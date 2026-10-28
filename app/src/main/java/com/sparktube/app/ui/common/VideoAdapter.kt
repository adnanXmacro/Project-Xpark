package com.sparktube.app.ui.common

import android.view.LayoutInflater
import android.view.ViewGroup
import androidx.recyclerview.widget.DiffUtil
import androidx.recyclerview.widget.ListAdapter
import androidx.recyclerview.widget.RecyclerView
import com.sparktube.app.databinding.ItemVideoBinding
import com.sparktube.app.util.Thumbs

class VideoAdapter(
    private val onClick: (VideoUiModel) -> Unit,
    private val onLongClick: ((VideoUiModel) -> Boolean)? = null,
    private val onMore: ((android.view.View, VideoUiModel) -> Unit)? = null
) : ListAdapter<VideoUiModel, VideoAdapter.VH>(DIFF) {

    companion object {
        private val DIFF = object : DiffUtil.ItemCallback<VideoUiModel>() {
            override fun areItemsTheSame(a: VideoUiModel, b: VideoUiModel): Boolean =
                a.url == b.url

            override fun areContentsTheSame(a: VideoUiModel, b: VideoUiModel): Boolean =
                a.url == b.url && a.title == b.title && a.viewsLabel == b.viewsLabel
        }
    }

    class VH(val binding: ItemVideoBinding) : RecyclerView.ViewHolder(binding.root)

    override fun onCreateViewHolder(parent: ViewGroup, viewType: Int): VH =
        VH(ItemVideoBinding.inflate(LayoutInflater.from(parent.context), parent, false))

    override fun onBindViewHolder(holder: VH, position: Int) {
        val item = getItem(position)
        val b = holder.binding

        b.title.text = item.title
        val metaParts = listOf(item.uploader, item.viewsLabel, item.uploadDate).filter { it.isNotBlank() }
        b.meta.text = metaParts.joinToString(" • ")

        if (item.durationLabel.isEmpty()) {
            b.duration.visibility = android.view.View.GONE
        } else {
            b.duration.visibility = android.view.View.VISIBLE
            b.duration.text = item.durationLabel
        }

        Thumbs.load(b.thumbnail, item.thumbnailUrl)
        Thumbs.load(b.avatar, item.uploaderAvatarUrl)

        b.root.setOnClickListener { onClick(item) }
        b.root.setOnLongClickListener {
            onLongClick?.invoke(item) ?: false
        }
        if (onMore != null) {
            b.moreButton.visibility = android.view.View.VISIBLE
            b.moreButton.setOnClickListener { view -> onMore.invoke(view, item) }
        } else {
            b.moreButton.visibility = android.view.View.GONE
        }
    }
}
