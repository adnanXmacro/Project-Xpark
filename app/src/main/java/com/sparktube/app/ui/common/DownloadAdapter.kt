package com.sparktube.app.ui.common

import android.view.LayoutInflater
import android.view.View
import android.view.ViewGroup
import androidx.recyclerview.widget.DiffUtil
import androidx.recyclerview.widget.ListAdapter
import androidx.recyclerview.widget.RecyclerView
import com.sparktube.app.R
import com.sparktube.app.data.DownloadRecord
import com.sparktube.app.databinding.ItemDownloadBinding
import com.sparktube.app.download.DownloadCenter
import com.sparktube.app.util.Formatters
import com.sparktube.app.util.Thumbs

data class DownloadUiModel(
    val record: DownloadRecord,
    val percent: Int = -1,
    val downloadedBytes: Long = 0L,
    val totalBytes: Long = 0L
)

/** Download entries on the Library > Downloads tab. */
class DownloadAdapter(
    private val onClick: (DownloadRecord) -> Unit,
    private val onLongClick: (DownloadRecord) -> Unit
) : ListAdapter<DownloadUiModel, DownloadAdapter.VH>(DIFF) {

    companion object {
        private const val PAYLOAD_PROGRESS = "payload_progress"

        private val DIFF = object : DiffUtil.ItemCallback<DownloadUiModel>() {
            override fun areItemsTheSame(a: DownloadUiModel, b: DownloadUiModel): Boolean =
                a.record.videoId == b.record.videoId &&
                    a.record.type == b.record.type &&
                    a.record.quality == b.record.quality

            override fun areContentsTheSame(a: DownloadUiModel, b: DownloadUiModel): Boolean =
                a == b

            override fun getChangePayload(a: DownloadUiModel, b: DownloadUiModel): Any? {
                return if (a.record.videoId == b.record.videoId &&
                    a.record.type == b.record.type &&
                    a.record.quality == b.record.quality &&
                    a.record.thumbnailUrl == b.record.thumbnailUrl &&
                    a.record.title == b.record.title
                ) {
                    PAYLOAD_PROGRESS
                } else null
            }
        }
    }

    class VH(val binding: ItemDownloadBinding) : RecyclerView.ViewHolder(binding.root)

    override fun onCreateViewHolder(parent: ViewGroup, viewType: Int): VH =
        VH(ItemDownloadBinding.inflate(LayoutInflater.from(parent.context), parent, false))

    override fun onBindViewHolder(holder: VH, position: Int, payloads: MutableList<Any>) {
        if (payloads.contains(PAYLOAD_PROGRESS)) {
            bindStatusAndProgress(holder.binding, getItem(position))
        } else {
            super.onBindViewHolder(holder, position, payloads)
        }
    }

    override fun onBindViewHolder(holder: VH, position: Int) {
        val model = getItem(position)
        val item = model.record
        val b = holder.binding

        b.title.text = item.title
        Thumbs.load(b.thumb, item.thumbnailUrl)
        bindStatusAndProgress(b, model)

        b.root.setOnClickListener { if (item.status == DownloadCenter.STATUS_DONE) onClick(item) }
        b.root.setOnLongClickListener {
            onLongClick(item)
            true
        }
    }

    private fun bindStatusAndProgress(b: ItemDownloadBinding, model: DownloadUiModel) {
        val item = model.record
        val typeLabel = when (item.type) {
            DownloadCenter.TYPE_AUDIO -> b.root.context.getString(R.string.download_type_audio)
            DownloadCenter.TYPE_AV -> b.root.context.getString(R.string.download_type_av)
            else -> b.root.context.getString(R.string.download_type_video)
        }

        val isRunning = item.status == DownloadCenter.STATUS_RUNNING || item.status == DownloadCenter.STATUS_PENDING
        if (isRunning) {
            b.progressBar.visibility = View.VISIBLE
            if (model.percent >= 0) {
                b.progressBar.isIndeterminate = false
                b.progressBar.progress = model.percent
                if (model.totalBytes > 0L) {
                    val done = Formatters.formatFileSize(model.downloadedBytes)
                    val total = Formatters.formatFileSize(model.totalBytes)
                    b.meta.text = "$typeLabel • ${item.quality} • ${model.percent}% ($done / $total)"
                } else {
                    b.meta.text = "$typeLabel • ${item.quality} • ${model.percent}%"
                }
            } else {
                b.progressBar.isIndeterminate = true
                val statusLabel = if (item.status == DownloadCenter.STATUS_PENDING) {
                    b.root.context.getString(R.string.download_status_pending)
                } else {
                    b.root.context.getString(R.string.download_status_running)
                }
                b.meta.text = "$typeLabel • ${item.quality} • $statusLabel"
            }
        } else {
            b.progressBar.visibility = View.GONE
            val statusLabel = when (item.status) {
                DownloadCenter.STATUS_DONE -> b.root.context.getString(R.string.download_status_done)
                DownloadCenter.STATUS_FAILED -> b.root.context.getString(R.string.download_status_failed)
                else -> b.root.context.getString(R.string.download_status_pending)
            }
            if (item.status == DownloadCenter.STATUS_DONE && model.totalBytes > 0L) {
                val size = Formatters.formatFileSize(model.totalBytes)
                b.meta.text = "$typeLabel • ${item.quality} • $statusLabel • $size"
            } else {
                b.meta.text = "$typeLabel • ${item.quality} • $statusLabel"
            }
        }
    }
}
