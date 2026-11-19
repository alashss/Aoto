package com.jimz.jimztune.island

import android.animation.ValueAnimator
import android.content.Context
import android.graphics.Canvas
import android.graphics.Paint
import android.graphics.Path
import android.graphics.RectF
import android.view.MotionEvent
import android.view.View
import android.view.animation.LinearInterpolator
import kotlin.math.PI
import kotlin.math.min
import kotlin.math.sin

/** Batang equalizer kecil: bergerak selama musik diputar, diam (rendah) saat dijeda. */
class IslandEqView(context: Context) : View(context) {
    private val paint = Paint(Paint.ANTI_ALIAS_FLAG).apply { style = Paint.Style.FILL }
    private val rect = RectF()
    private var phase = 0f
    private var anim: ValueAnimator? = null

    var color: Int = 0xFFFFFFFF.toInt()
        set(value) {
            field = value
            invalidate()
        }

    var active: Boolean = false
        set(value) {
            if (field == value) return
            field = value
            syncAnim()
            invalidate()
        }

    private fun syncAnim() {
        if (active && isAttachedToWindow) {
            if (anim == null) {
                anim = ValueAnimator.ofFloat(0f, 1f).apply {
                    duration = 1100L
                    repeatCount = ValueAnimator.INFINITE
                    interpolator = LinearInterpolator()
                    addUpdateListener {
                        phase = it.animatedValue as Float
                        invalidate()
                    }
                    start()
                }
            }
        } else {
            anim?.cancel()
            anim = null
        }
    }

    override fun onAttachedToWindow() {
        super.onAttachedToWindow()
        syncAnim()
    }

    override fun onDetachedFromWindow() {
        anim?.cancel()
        anim = null
        super.onDetachedFromWindow()
    }

    override fun onDraw(canvas: Canvas) {
        paint.color = color
        val n = 4
        val w = width.toFloat()
        val h = height.toFloat()
        val gap = w * 0.12f
        val bw = (w - gap * (n - 1)) / n
        for (i in 0 until n) {
            val level = if (active) {
                val wave = sin(2.0 * PI * (phase * (1 + i % 2) + i * 0.27)).toFloat()
                0.3f + 0.7f * (0.5f + 0.5f * wave)
            } else {
                0.22f
            }
            val bh = h * level
            val x = i * (bw + gap)
            rect.set(x, (h - bh) / 2f, x + bw, (h + bh) / 2f)
            canvas.drawRoundRect(rect, bw / 2f, bw / 2f, paint)
        }
    }
}

/** Tombol bergambar (sebelumnya / putar / jeda / berikutnya) yang digambar sendiri, dengan efek tekan. */
class IslandGlyphView(context: Context, initialKind: Int) : View(context) {
    companion object {
        const val PREV = 0
        const val PLAY = 1
        const val PAUSE = 2
        const val NEXT = 3
    }

    private val paint = Paint(Paint.ANTI_ALIAS_FLAG).apply { style = Paint.Style.FILL }
    private val path = Path()
    private val rect = RectF()

    var kind: Int = initialKind
        set(value) {
            if (field == value) return
            field = value
            invalidate()
        }

    var tint: Int = 0xFFFFFFFF.toInt()
        set(value) {
            field = value
            invalidate()
        }

    /** Warna lingkaran latar; 0 berarti tanpa lingkaran. */
    var circleColor: Int = 0
        set(value) {
            field = value
            invalidate()
        }

    /** false = hanya gambar: sentuhan diteruskan ke pil di belakangnya (dipakai untuk ikon putar kecil saat jeda). */
    var touchable: Boolean = true

    init {
        isClickable = true
    }

    private fun tri(x1: Float, y1: Float, x2: Float, y2: Float, x3: Float, y3: Float, canvas: Canvas) {
        path.reset()
        path.moveTo(x1, y1)
        path.lineTo(x2, y2)
        path.lineTo(x3, y3)
        path.close()
        canvas.drawPath(path, paint)
    }

    override fun onDraw(canvas: Canvas) {
        val w = width.toFloat()
        val h = height.toFloat()
        val cx = w / 2f
        val cy = h / 2f
        if (circleColor != 0) {
            paint.color = circleColor
            canvas.drawCircle(cx, cy, min(w, h) / 2f, paint)
        }
        paint.color = tint
        val s = min(w, h) * (if (circleColor != 0) 0.30f else 0.30f)
        when (kind) {
            PLAY -> tri(cx - s * 0.7f, cy - s, cx + s * 0.95f, cy, cx - s * 0.7f, cy + s, canvas)
            PAUSE -> {
                val bw = s * 0.55f
                val r = bw * 0.25f
                rect.set(cx - s * 0.8f, cy - s, cx - s * 0.8f + bw, cy + s)
                canvas.drawRoundRect(rect, r, r, paint)
                rect.set(cx + s * 0.8f - bw, cy - s, cx + s * 0.8f, cy + s)
                canvas.drawRoundRect(rect, r, r, paint)
            }
            NEXT -> {
                tri(cx - s, cy - s * 0.78f, cx, cy, cx - s, cy + s * 0.78f, canvas)
                tri(cx, cy - s * 0.78f, cx + s, cy, cx, cy + s * 0.78f, canvas)
            }
            PREV -> {
                tri(cx + s, cy - s * 0.78f, cx, cy, cx + s, cy + s * 0.78f, canvas)
                tri(cx, cy - s * 0.78f, cx - s, cy, cx, cy + s * 0.78f, canvas)
            }
        }
    }

    override fun onTouchEvent(event: MotionEvent): Boolean {
        if (!touchable) return false
        when (event.actionMasked) {
            MotionEvent.ACTION_DOWN -> animate().scaleX(0.86f).scaleY(0.86f).setDuration(80L).start()
            MotionEvent.ACTION_UP -> {
                animate().scaleX(1f).scaleY(1f).setDuration(160L).start()
                if (event.x in 0f..width.toFloat() && event.y in 0f..height.toFloat()) performClick()
            }
            MotionEvent.ACTION_CANCEL -> animate().scaleX(1f).scaleY(1f).setDuration(160L).start()
        }
        return true
    }

    override fun performClick(): Boolean {
        return super.performClick()
    }
}

/** Bilah progres lagu yang bisa digeser untuk mencari posisi. */
class IslandProgressView(context: Context) : View(context) {
    private val paint = Paint(Paint.ANTI_ALIAS_FLAG).apply { style = Paint.Style.FILL }
    private val rect = RectF()
    private var dragging = false
    private var dragFraction = 0f
    private var shown = 0f

    var trackColor: Int = 0x33FFFFFF
        set(value) {
            field = value
            invalidate()
        }
    var fillColor: Int = 0xFFFFFFFF.toInt()
        set(value) {
            field = value
            invalidate()
        }

    /** Dipanggil saat jari dilepas, dengan posisi 0..1. */
    var onSeek: ((Float) -> Unit)? = null

    /** Dipanggil selama pengguna menyentuh bilah (supaya pulau tidak menutup sendiri). */
    var onTouched: (() -> Unit)? = null

    fun setFraction(value: Float) {
        if (dragging) return
        shown = value.coerceIn(0f, 1f)
        invalidate()
    }

    override fun onDraw(canvas: Canvas) {
        val w = width.toFloat()
        val h = height.toFloat()
        val density = resources.displayMetrics.density
        val th = (if (dragging) 6f else 4f) * density
        val top = (h - th) / 2f
        paint.color = trackColor
        rect.set(0f, top, w, top + th)
        canvas.drawRoundRect(rect, th / 2f, th / 2f, paint)
        val f = if (dragging) dragFraction else shown
        paint.color = fillColor
        rect.set(0f, top, w * f, top + th)
        canvas.drawRoundRect(rect, th / 2f, th / 2f, paint)
        if (dragging) canvas.drawCircle(w * f, h / 2f, 7f * density, paint)
    }

    override fun onTouchEvent(event: MotionEvent): Boolean {
        val w = width.toFloat().coerceAtLeast(1f)
        when (event.actionMasked) {
            MotionEvent.ACTION_DOWN -> {
                parent?.requestDisallowInterceptTouchEvent(true)
                dragging = true
                dragFraction = (event.x / w).coerceIn(0f, 1f)
                onTouched?.invoke()
                invalidate()
            }
            MotionEvent.ACTION_MOVE -> {
                dragFraction = (event.x / w).coerceIn(0f, 1f)
                onTouched?.invoke()
                invalidate()
            }
            MotionEvent.ACTION_UP -> {
                dragFraction = (event.x / w).coerceIn(0f, 1f)
                dragging = false
                shown = dragFraction
                onSeek?.invoke(dragFraction)
                invalidate()
            }
            MotionEvent.ACTION_CANCEL -> {
                dragging = false
                invalidate()
            }
        }
        return true
    }
}
