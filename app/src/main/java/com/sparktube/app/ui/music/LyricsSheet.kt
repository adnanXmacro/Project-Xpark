package com.sparktube.app.ui.music

import android.content.Context
import android.graphics.Typeface
import android.os.Handler
import android.os.Looper
import android.view.LayoutInflater
import android.view.ViewGroup
import android.widget.TextView
import androidx.core.view.isVisible
import androidx.recyclerview.widget.LinearLayoutManager
import androidx.recyclerview.widget.RecyclerView
import com.google.android.material.bottomsheet.BottomSheetBehavior
import com.google.android.material.bottomsheet.BottomSheetDialog
import com.sparktube.app.R
import com.sparktube.app.data.LyricLine
import com.sparktube.app.data.Lyrics
import com.sparktube.app.data.LyricsRepository
import com.sparktube.app.databinding.SheetLyricsBinding
import com.sparktube.app.playback.PlaybackCenter
import com.sparktube.app.playback.QueueEntry
import kotlinx.coroutines.CoroutineScope
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.Job
import kotlinx.coroutines.SupervisorJob
import kotlinx.coroutines.cancel
import kotlinx.coroutines.launch

class LyricsSheet(context: Context) : BottomSheetDialog(context) {

    private val binding = SheetLyricsBinding.inflate(LayoutInflater.from(context))
    private val adapter = LineAdapter()
    private val scope = CoroutineScope(SupervisorJob() + Dispatchers.Main.immediate)
    private var loadJob: Job? = null
    private var lyrics: Lyrics? = null
    private var lastIndex = -1
    private val handler = Handler(Looper.getMainLooper())

    private val tick = object : Runnable {
        override fun run() {
            highlight()
            handler.postDelayed(this, 400)
        }
    }

    private val listener = object : PlaybackCenter.Listener {
        override fun onItemChanged(entry: QueueEntry?) {
            lastIndex = -1
            load()
        }
    }

    init {
        val height = (context.resources.displayMetrics.heightPixels * 0.78f).toInt()
        binding.sheetRoot.layoutParams = ViewGroup.LayoutParams(
            ViewGroup.LayoutParams.MATCH_PARENT, height
        )
        setContentView(binding.root)
        behavior.skipCollapsed = true
        setOnShowListener { behavior.state = BottomSheetBehavior.STATE_EXPANDED }
        binding.list.layoutManager = LinearLayoutManager(context)
        binding.list.adapter = adapter
        binding.closeButton.setOnClickListener { dismiss() }
        setOnDismissListener {
            handler.removeCallbacks(tick)
            loadJob?.cancel()
            scope.cancel()
        }
    }

    override fun onStart() {
        super.onStart()
        PlaybackCenter.addListener(listener)
        load()
        handler.post(tick)
    }

    override fun onStop() {
        super.onStop()
        PlaybackCenter.removeListener(listener)
        handler.removeCallbacks(tick)
        loadJob?.cancel()
    }

    private fun load() {
        val entry = PlaybackCenter.currentEntry ?: run {
            showEmpty()
            return
        }
        binding.loading.isVisible = true
        binding.emptyView.isVisible = false
        binding.list.isVisible = false
        adapter.submit(emptyList(), -1)
        loadJob?.cancel()
        loadJob = scope.launch {
            val result = runCatching {
                LyricsRepository.load(
                    entry.url,
                    entry.title,
                    entry.uploader,
                    entry.durationSec
                )
            }.getOrNull()
            if (!isShowing) return@launch
            lyrics = result
            binding.loading.isVisible = false
            if (result == null || result.lines.isEmpty()) {
                showEmpty()
            } else {
                binding.emptyView.isVisible = false
                binding.list.isVisible = true
                lastIndex = -1
                highlight()
            }
        }
    }

    private fun showEmpty() {
        binding.loading.isVisible = false
        binding.list.isVisible = false
        binding.emptyView.isVisible = true
    }

    private fun highlight() {
        val data = lyrics ?: return
        if (data.lines.isEmpty()) return
        val index = if (data.synced) {
            val pos = PlaybackCenter.positionMs
            var i = 0
            while (i + 1 < data.lines.size && data.lines[i + 1].timeMs <= pos) i++
            i
        } else {
            -1
        }
        if (index == lastIndex && adapter.itemCount == data.lines.size) return
        lastIndex = index
        adapter.submit(data.lines, index)
        if (index >= 0 && binding.list.height > 0) {
            (binding.list.layoutManager as LinearLayoutManager)
                .scrollToPositionWithOffset(index, binding.list.height / 3)
        }
    }

    private class LineAdapter : RecyclerView.Adapter<LineAdapter.VH>() {
        private var lines: List<LyricLine> = emptyList()
        private var current = -1

        class VH(val view: TextView) : RecyclerView.ViewHolder(view)

        fun submit(newLines: List<LyricLine>, currentIndex: Int) {
            lines = newLines
            current = currentIndex
            notifyDataSetChanged()
        }

        override fun onCreateViewHolder(parent: ViewGroup, viewType: Int): VH {
            val view = LayoutInflater.from(parent.context)
                .inflate(R.layout.item_lyric_line, parent, false) as TextView
            return VH(view)
        }

        override fun onBindViewHolder(holder: VH, position: Int) {
            val active = position == current
            holder.view.text = lines[position].text
            holder.view.setTypeface(null, if (active) Typeface.BOLD else Typeface.NORMAL)
            holder.view.alpha = if (current < 0 || active) 1f else 0.45f
            holder.view.setTextColor(
                holder.view.context.getColor(
                    if (active) R.color.on_surface else R.color.on_surface_variant
                )
            )
        }

        override fun getItemCount(): Int = lines.size
    }
}
