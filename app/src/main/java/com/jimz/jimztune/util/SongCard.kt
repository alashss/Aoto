package com.jimz.jimztune.util

import android.graphics.Bitmap
import android.graphics.Canvas
import android.graphics.Color
import android.graphics.LinearGradient
import android.graphics.Paint
import android.graphics.Path
import android.graphics.RadialGradient
import android.graphics.RectF
import android.graphics.Shader
import android.graphics.Typeface
import android.text.Layout
import android.text.StaticLayout
import android.text.TextPaint
import android.text.TextUtils

/** Kartu "Sedang kuputar": sampul besar, judul, artis. Kotak 1080x1080 atau Story 1080x1920. */
object SongCard {
    fun render(story: Boolean, accent: Int, title: String, artist: String, cover: Bitmap?, watermark: Boolean, scale: Float = 1f): Bitmap {
        val w = 1080
        val h = if (story) 1920 else 1080
        val bmp = Bitmap.createBitmap((w * scale).toInt(), (h * scale).toInt(), Bitmap.Config.ARGB_8888)
        val c = Canvas(bmp)
        c.scale(scale, scale)
        val p = Paint(Paint.ANTI_ALIAS_FLAG)
        val hsv = FloatArray(3)
        Color.colorToHSV(accent, hsv)

        // ---- latar: sampul diburamkan + gelap, atau gradasi aksen kalau tidak ada sampul ----
        if (cover != null) {
            c.drawBitmap(LyricCard.blurred(cover, w, h), 0f, 0f, null)
            p.color = Color.argb(110, 0, 0, 0)
            c.drawRect(0f, 0f, w.toFloat(), h.toFloat(), p)
        } else {
            val top = Color.HSVToColor(floatArrayOf(hsv[0], 0.55f, 0.46f))
            val bot = Color.HSVToColor(floatArrayOf((hsv[0] + 14f) % 360f, 0.55f, 0.09f))
            p.shader = LinearGradient(0f, 0f, 0f, h.toFloat(), top, bot, Shader.TileMode.CLAMP)
            c.drawRect(0f, 0f, w.toFloat(), h.toFloat(), p)
            p.shader = null
        }
        p.shader = RadialGradient(w * 0.2f, h * 0.05f, w * 0.9f, Color.argb(70, 255, 255, 255), Color.TRANSPARENT, Shader.TileMode.CLAMP)
        c.drawRect(0f, 0f, w.toFloat(), h.toFloat(), p)
        p.shader = null

        // ---- sampul ----
        val side = if (story) 760f else 540f
        val left = (w - side) / 2f
        val top = if (story) 330f else 110f
        val rect = RectF(left, top, left + side, top + side)
        val r = side * 0.07f
        // bayangan lembut di bawah sampul
        p.color = Color.argb(90, 0, 0, 0)
        c.drawRoundRect(RectF(rect.left + 6f, rect.top + 26f, rect.right - 6f, rect.bottom + 26f), r, r, p)
        if (cover != null) {
            c.save()
            c.clipPath(Path().apply { addRoundRect(rect, r, r, Path.Direction.CW) })
            c.drawBitmap(cover, null, rect, Paint(Paint.FILTER_BITMAP_FLAG))
            c.restore()
        } else {
            p.color = Color.argb(60, 255, 255, 255)
            c.drawRoundRect(rect, r, r, p)
        }

        fun text(t: String, size: Float, bold: Boolean, alpha: Int, maxLines: Int, y: Float): Float {
            if (t.isBlank()) return 0f
            val tp = TextPaint(Paint.ANTI_ALIAS_FLAG).apply {
                textSize = size; color = Color.WHITE; this.alpha = alpha
                typeface = if (bold) Typeface.DEFAULT_BOLD else Typeface.DEFAULT
            }
            val width = w - 160
            val l = StaticLayout.Builder.obtain(t, 0, t.length, tp, width)
                .setAlignment(Layout.Alignment.ALIGN_CENTER).setMaxLines(maxLines)
                .setEllipsize(TextUtils.TruncateAt.END).build()
            c.save(); c.translate(80f, y); l.draw(c); c.restore()
            return l.height.toFloat()
        }

        var y = rect.bottom + (if (story) 90f else 56f)
        text("SEDANG KUPUTAR", 28f, true, 150, 1, top - 64f)
        y += text(title, if (story) 68f else 58f, true, 255, 2, y) + 10f
        text(artist, if (story) 44f else 38f, false, 200, 1, y)
        if (watermark) {
            val fp = TextPaint(Paint.ANTI_ALIAS_FLAG).apply {
                textSize = 32f; color = Color.WHITE; alpha = 160; typeface = Typeface.DEFAULT_BOLD
                textAlign = Paint.Align.CENTER
            }
            c.drawText("Aoto", w / 2f, h - (if (story) 150f else 40f), fp)
        }
        return bmp
    }
}
