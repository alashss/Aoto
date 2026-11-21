package com.jimz.jimztune.island

import android.animation.ValueAnimator
import android.content.Context
import android.graphics.Canvas
import android.graphics.Paint
import android.graphics.Path
import android.graphics.Shader
import android.graphics.LinearGradient
import android.graphics.RectF
import android.view.MotionEvent
import android.view.View
import android.view.animation.LinearInterpolator
import kotlin.math.PI
import kotlin.math.max
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

    /** Segitiga bersudut membulat (diisi + garis tepi bersambung bulat). */
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
        val r = min(w, h) / 2f
        paint.style = Paint.Style.FILL
        paint.shader = null
        if (circleColor != 0) {
            paint.color = circleColor
            canvas.drawCircle(cx, cy, r, paint)
            // kilau lembut di separuh atas, lalu cincin tipis: kesan tombol kaca
            paint.shader = LinearGradient(0f, cy - r, 0f, cy + r, 0x38FFFFFF, 0x00FFFFFF, Shader.TileMode.CLAMP)
            canvas.drawCircle(cx, cy, r, paint)
            paint.shader = null
            paint.style = Paint.Style.STROKE
            paint.strokeWidth = max(1f, r * 0.05f)
            paint.color = 0x30FFFFFF
            canvas.drawCircle(cx, cy, r - paint.strokeWidth / 2f, paint)
        }
        paint.color = tint
        paint.style = Paint.Style.FILL_AND_STROKE
        paint.strokeJoin = Paint.Join.ROUND
        paint.strokeCap = Paint.Cap.ROUND
        val s = min(w, h) * 0.25f
        paint.strokeWidth = s * 0.34f
        when (kind) {
            PLAY -> tri(cx - s * 0.62f, cy - s * 0.95f, cx + s * 0.98f, cy, cx - s * 0.62f, cy + s * 0.95f, canvas)
            PAUSE -> {
                val bw = s * 0.62f
                val rr = bw * 0.5f
                paint.strokeWidth = 0f
                paint.style = Paint.Style.FILL
                rect.set(cx - s * 0.86f, cy - s * 0.95f, cx - s * 0.86f + bw, cy + s * 0.95f)
                canvas.drawRoundRect(rect, rr, rr, paint)
                rect.set(cx + s * 0.86f - bw, cy - s * 0.95f, cx + s * 0.86f, cy + s * 0.95f)
                canvas.drawRoundRect(rect, rr, rr, paint)
            }
            NEXT -> {
                tri(cx - s * 0.95f, cy - s * 0.82f, cx + s * 0.45f, cy, cx - s * 0.95f, cy + s * 0.82f, canvas)
                canvas.drawLine(cx + s * 0.98f, cy - s * 0.82f, cx + s * 0.98f, cy + s * 0.82f, paint)
            }
            PREV -> {
                tri(cx + s * 0.95f, cy - s * 0.82f, cx - s * 0.45f, cy, cx + s * 0.95f, cy + s * 0.82f, canvas)
                canvas.drawLine(cx - s * 0.98f, cy - s * 0.82f, cx - s * 0.98f, cy + s * 0.82f, paint)
            }
        }
        paint.style = Paint.Style.FILL
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
