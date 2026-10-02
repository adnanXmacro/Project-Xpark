package com.sparktube.app.ui.widget

import android.animation.ValueAnimator
import android.content.Context
import android.graphics.Canvas
import android.graphics.LinearGradient
import android.graphics.Paint
import android.graphics.RectF
import android.graphics.Shader
import android.util.AttributeSet
import android.view.MotionEvent
import android.view.View
import android.view.ViewGroup
import android.view.ViewOutlineProvider
import android.view.animation.PathInterpolator
import android.widget.ImageView
import androidx.core.content.ContextCompat
import com.sparktube.app.R
import com.sparktube.app.util.AppPrefs
import kotlin.math.abs
import kotlin.math.ceil
import kotlin.math.floor
import kotlin.math.max
import kotlin.math.roundToInt

class GlassCapsuleNav @JvmOverloads constructor(
    context: Context,
    attrs: AttributeSet? = null,
    defStyleAttr: Int = 0
) : ViewGroup(context, attrs, defStyleAttr) {

    var onTabSelected: ((Int) -> Unit)? = null

    private val pillPaint = Paint(Paint.ANTI_ALIAS_FLAG)
    private val borderPaint = Paint(Paint.ANTI_ALIAS_FLAG).apply {
        style = Paint.Style.STROKE
    }
    private val bubblePaint = Paint(Paint.ANTI_ALIAS_FLAG)
    private val bubbleSheenPaint = Paint(Paint.ANTI_ALIAS_FLAG)
    private val innerHighlightPaint = Paint(Paint.ANTI_ALIAS_FLAG).apply {
        style = Paint.Style.STROKE
    }

    private val pillRect = RectF()
    private val bubbleRect = RectF()

    private val spring = PathInterpolator(0.34f, 1.56f, 0.64f, 1f)

    private var selectedIndex = 0
    private var highlightIndex = 0
    private var bubbleLeft = 0f
    private var bubbleWidth = 0f
    private var bubbleAnimator: ValueAnimator? = null

    private var dragStartX = 0f
    private var dragCurrentX = 0f
    private var dragStartIdx = 0
    private var dragging = false

    private val slop = (DRAG_SLOP_DP * resources.displayMetrics.density)

    init {
        setWillNotDraw(false)
        clipToOutline = true
        outlineProvider = object : ViewOutlineProvider() {
            override fun getOutline(view: View, outline: android.graphics.Outline) {
                outline.setRoundRect(0, 0, view.width, view.height, view.height / 2f)
            }
        }
        resolveColors()
    }

    fun setSelectedTab(id: Int, animate: Boolean) {
        val idx = indexOfId(id)
        if (idx < 0) return
        selectedIndex = idx
        highlightIndex = idx
        if (width == 0 || getChildAt(idx)?.width == 0) return
        snapBubble(idx, animate && AppPrefs.animations, TAP_MS)
        applyTabVisuals(animate && AppPrefs.animations)
    }

    override fun onViewAdded(child: View?) {
        super.onViewAdded(child)
        val tab = child ?: return
        tab.isClickable = true
        tab.isFocusable = true
        tab.setOnClickListener {
            val idx = indexOfChild(tab)
            commitTab(idx, AppPrefs.animations, TAP_MS)
        }
    }

    override fun onMeasure(widthMeasureSpec: Int, heightMeasureSpec: Int) {
        val width = MeasureSpec.getSize(widthMeasureSpec)
        val hPad = paddingLeft + paddingRight
        val vPad = paddingTop + paddingBottom
        val n = childCount.coerceAtLeast(1)
        val childW = ((width - hPad) / n).coerceAtLeast(0)
        var maxH = 0
        for (i in 0 until childCount) {
            val child = getChildAt(i)
            child.measure(
                MeasureSpec.makeMeasureSpec(childW, MeasureSpec.EXACTLY),
                MeasureSpec.makeMeasureSpec(0, MeasureSpec.UNSPECIFIED)
            )
            maxH = max(maxH, child.measuredHeight)
        }
        val minTouch = (48 * resources.displayMetrics.density).toInt()
        maxH = max(maxH, minTouch - vPad)
        setMeasuredDimension(width, maxH + vPad)
    }

    override fun onLayout(changed: Boolean, l: Int, t: Int, r: Int, b: Int) {
        val rtl = layoutDirection == LAYOUT_DIRECTION_RTL
        val n = childCount
        if (n == 0) return
        val childW = (width - paddingLeft - paddingRight) / n
        val top = paddingTop
        val bottom = top + (height - paddingTop - paddingBottom)
        for (i in 0 until n) {
            val child = getChildAt(i)
            val start = if (rtl) {
                width - paddingRight - (i + 1) * childW
            } else {
                paddingLeft + i * childW
            }
            child.layout(start, top, start + childW, bottom)
        }
        val running = bubbleAnimator?.isRunning == true
        if (!running && !dragging) {
            val child = getChildAt(selectedIndex)
            if (child != null && (bubbleWidth == 0f || changed)) {
                snapBubble(selectedIndex, false, 0)
                applyTabVisuals(false)
            }
        }
    }

    override fun onSizeChanged(w: Int, h: Int, oldw: Int, oldh: Int) {
        super.onSizeChanged(w, h, oldw, oldh)
        pillRect.set(0f, 0f, w.toFloat(), h.toFloat())
        resolveColors()
        invalidateOutline()
    }

    override fun onDraw(canvas: Canvas) {
        val radius = height / 2f
        canvas.drawRoundRect(pillRect, radius, radius, pillPaint)

        if (bubbleWidth > 0f) {
            val inset = 6f * resources.displayMetrics.density
            bubbleRect.set(
                bubbleLeft,
                inset,
                bubbleLeft + bubbleWidth,
                height - inset
            )
            val br = bubbleRect.height() / 2f
            canvas.drawRoundRect(bubbleRect, br, br, bubblePaint)
            if (bubbleSheenPaint.shader != null) {
                canvas.drawRoundRect(bubbleRect, br, br, bubbleSheenPaint)
            }
        }

        val stroke = borderPaint.strokeWidth
        val half = stroke / 2f
        canvas.drawRoundRect(
            half,
            half,
            width - half,
            height - half,
            radius,
            radius,
            borderPaint
        )
        canvas.drawRoundRect(
            half + stroke,
            half + stroke,
            width - half - stroke,
            height - half - stroke,
            radius,
            radius,
            innerHighlightPaint
        )
    }

    override fun onInterceptTouchEvent(ev: MotionEvent): Boolean {
        if (!AppPrefs.animations) return false
        when (ev.actionMasked) {
            MotionEvent.ACTION_DOWN -> {
                dragStartX = ev.x
                dragCurrentX = ev.x
                dragStartIdx = selectedIndex
                dragging = false
            }
            MotionEvent.ACTION_MOVE -> {
                if (abs(ev.x - dragStartX) > slop) {
                    dragging = true
                    bubbleAnimator?.cancel()
                    parent.requestDisallowInterceptTouchEvent(true)
                    return true
                }
            }
        }
        return false
    }

    override fun onTouchEvent(event: MotionEvent): Boolean {
        if (!AppPrefs.animations) return super.onTouchEvent(event)
        when (event.actionMasked) {
            MotionEvent.ACTION_DOWN -> {
                dragStartX = event.x
                dragCurrentX = event.x
                dragStartIdx = selectedIndex
                dragging = false
                return true
            }
            MotionEvent.ACTION_MOVE -> {
                val dx = event.x - dragStartX
                if (!dragging && abs(dx) > slop) {
                    dragging = true
                    bubbleAnimator?.cancel()
                    parent.requestDisallowInterceptTouchEvent(true)
                }
                if (!dragging) return true
                dragCurrentX = event.x
                trackDrag(dx)
                return true
            }
            MotionEvent.ACTION_UP, MotionEvent.ACTION_CANCEL -> {
                parent.requestDisallowInterceptTouchEvent(false)
                if (dragging) {
                    dragging = false
                    val n = childCount.coerceAtLeast(1)
                    val tabW = width.toFloat() / n
                    val directed = if (layoutDirection == LAYOUT_DIRECTION_RTL) {
                        dragStartX - dragCurrentX
                    } else {
                        dragCurrentX - dragStartX
                    }
                    val shift = (directed / tabW).roundToInt()
                    val newIdx = (dragStartIdx + shift).coerceIn(0, n - 1)
                    commitTab(newIdx, true, SNAP_MS)
                }
                return true
            }
        }
        return super.onTouchEvent(event)
    }

    private fun trackDrag(dx: Float) {
        val n = childCount
        if (n == 0) return
        val tabW = width.toFloat() / n
        val directed = if (layoutDirection == LAYOUT_DIRECTION_RTL) -dx else dx
        val rawShift = directed / tabW
        val clamped = rawShift.coerceIn(-dragStartIdx.toFloat(), (n - 1 - dragStartIdx).toFloat())
        val targetIdx = dragStartIdx + clamped
        val floorIdx = floor(targetIdx).toInt().coerceIn(0, n - 1)
        val ceilIdx = ceil(targetIdx).toInt().coerceIn(0, n - 1)
        val frac = targetIdx - floorIdx
        val from = getChildAt(floorIdx) ?: return
        val to = getChildAt(ceilIdx) ?: return
        var interpX = from.left + (to.left - from.left) * frac
        var interpW = from.width + (to.width - from.width) * frac
        if (clamped != rawShift) {
            val overshoot = rawShift - clamped
            interpX += overshoot * tabW * RUBBER
        }
        bubbleLeft = interpX
        bubbleWidth = interpW
        val nearest = targetIdx.roundToInt().coerceIn(0, n - 1)
        if (nearest != highlightIndex) {
            highlightIndex = nearest
            applyTabVisuals(false)
        }
        invalidate()
    }

    private fun commitTab(idx: Int, animate: Boolean, duration: Long) {
        if (idx !in 0 until childCount) return
        selectedIndex = idx
        highlightIndex = idx
        snapBubble(idx, animate, duration)
        applyTabVisuals(animate)
        val id = getChildAt(idx)?.id ?: return
        onTabSelected?.invoke(id)
    }

    private fun snapBubble(idx: Int, animate: Boolean, duration: Long) {
        val child = getChildAt(idx) ?: return
        val targetL = child.left.toFloat()
        val targetW = child.width.toFloat()
        bubbleAnimator?.cancel()
        if (!animate || duration <= 0L) {
            bubbleLeft = targetL
            bubbleWidth = targetW
            invalidate()
            return
        }
        val startL = bubbleLeft
        val startW = if (bubbleWidth == 0f) targetW else bubbleWidth
        bubbleAnimator = ValueAnimator.ofFloat(0f, 1f).apply {
            this.duration = duration
            interpolator = spring
            addUpdateListener {
                val f = it.animatedValue as Float
                bubbleLeft = startL + (targetL - startL) * f
                bubbleWidth = startW + (targetW - startW) * f
                invalidate()
            }
            start()
        }
    }

    private fun applyTabVisuals(animate: Boolean) {
        for (i in 0 until childCount) {
            val child = getChildAt(i)
            val on = i == highlightIndex
            child.isSelected = on
            val icon = findImage(child) ?: continue
            val scale = if (on) ACTIVE_SCALE else 1f
            icon.animate().cancel()
            if (animate && AppPrefs.animations) {
                icon.animate()
                    .scaleX(scale)
                    .scaleY(scale)
                    .setDuration(SCALE_MS)
                    .setInterpolator(spring)
                    .start()
            } else {
                icon.scaleX = scale
                icon.scaleY = scale
            }
        }
    }

    private fun findImage(view: View): ImageView? {
        if (view is ImageView) return view
        if (view is ViewGroup) {
            for (i in 0 until view.childCount) {
                findImage(view.getChildAt(i))?.let { return it }
            }
        }
        return null
    }

    private fun indexOfId(id: Int): Int {
        for (i in 0 until childCount) {
            if (getChildAt(i).id == id) return i
        }
        return -1
    }

    private fun resolveColors() {
        pillPaint.color = ContextCompat.getColor(context, R.color.nav_pill)
        bubblePaint.color = ContextCompat.getColor(context, R.color.nav_bubble)
        borderPaint.color = ContextCompat.getColor(context, R.color.nav_border)
        borderPaint.strokeWidth = resources.displayMetrics.density
        innerHighlightPaint.color = ContextCompat.getColor(context, R.color.nav_pill_sheen)
        innerHighlightPaint.strokeWidth = resources.displayMetrics.density
        val sheen = ContextCompat.getColor(context, R.color.nav_bubble_sheen)
        bubbleSheenPaint.shader = LinearGradient(
            0f,
            0f,
            width.toFloat().coerceAtLeast(1f),
            height.toFloat().coerceAtLeast(1f),
            intArrayOf(sheen, 0x00FFFFFF, sheen),
            floatArrayOf(0f, 0.55f, 1f),
            Shader.TileMode.CLAMP
        )
    }

    companion object {
        private const val TAP_MS = 380L
        private const val SNAP_MS = 320L
        private const val SCALE_MS = 220L
        private const val ACTIVE_SCALE = 1.08f
        private const val RUBBER = 0.22f
        private const val DRAG_SLOP_DP = 4f
    }
}
