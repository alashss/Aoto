package com.jimz.jimztune.island

import android.content.Context
import android.graphics.Canvas
import android.graphics.Paint
import android.graphics.RectF
import android.view.View

/** Garis progres tipis di dasar pil ringkas (hanya tampilan, tidak bisa disentuh). */
class IslandThinProgress(context: Context) : View(context) {
    private val paint = Paint(Paint.ANTI_ALIAS_FLAG).apply { style = Paint.Style.FILL }
    private val rect = RectF()

    var trackColor: Int = 0x22FFFFFF
        set(v) { field = v; invalidate() }
    var fillColor: Int = 0xFFFFFFFF.toInt()
        set(v) { field = v; invalidate() }
    var fraction: Float = 0f
        set(v) {
            val c = v.coerceIn(0f, 1f)
            if (c != field) { field = c; invalidate() }
        }

    override fun onDraw(canvas: Canvas) {
        val w = width.toFloat()
        val h = height.toFloat()
        val r = h / 2f
        paint.color = trackColor
        rect.set(0f, 0f, w, h)
        canvas.drawRoundRect(rect, r, r, paint)
        if (fraction > 0f) {
            paint.color = fillColor
            rect.set(0f, 0f, maxOf(h, w * fraction), h)
            canvas.drawRoundRect(rect, r, r, paint)
        }
    }
}
