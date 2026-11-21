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

/** Kartu lirik untuk dibagikan: ada sampul, judul, dan beberapa baris lirik dengan gaya yang bisa dipilih. */
object LyricCard {
    enum class Bg(val label: String) { GRADIENT("Gradasi"), SOLID("Polos"), BLUR("Buram"), GLASS("Kaca") }
    enum class Align(val label: String) { LEFT("Kiri"), CENTER("Tengah"), RIGHT("Kanan") }
    enum class Font(val label: String) {
        DEFAULT("Bawaan"), ROUNDED("Bulat"), SERIF("Serif"), MONO("Mono"),
        CONDENSED("Ramping"), CASUAL("Santai"), CURSIVE("Tulis")
    }
    /** Warna teks: Otomatis mengikuti warna sampul, sisanya warna tetap. [rgb] = null berarti otomatis. */
    enum class Ink(val label: String, val rgb: Int?) {
        AUTO("Otomatis", null), WHITE("Putih", 0xFFFFFFFF.toInt()), BLACK("Hitam", 0xFF000000.toInt()),
        CREAM("Krem", 0xFFFFF1D6.toInt()), YELLOW("Kuning", 0xFFFFE066.toInt()), ORANGE("Oranye", 0xFFFFA24C.toInt()),
        PINK("Pink", 0xFFFF8FB8.toInt()), MINT("Mint", 0xFF8CF0C4.toInt()), SKY("Biru", 0xFF8EC8FF.toInt())
    }

    class Options(
        val story: Boolean = false,
        val bg: Bg = Bg.GLASS,
        val align: Align = Align.LEFT,
        val font: Font = Font.DEFAULT,
        val bold: Boolean = true,
        val ink: Ink = Ink.AUTO,
        val showCover: Boolean = true,
        val watermark: Boolean = true
    )

    private fun faceOf(f: Font, bold: Boolean): Typeface {
        val style = if (bold) Typeface.BOLD else Typeface.NORMAL
        return when (f) {
            Font.DEFAULT -> Typeface.create(Typeface.DEFAULT, style)
            Font.SERIF -> Typeface.create(Typeface.SERIF, style)
            Font.MONO -> Typeface.create(Typeface.MONOSPACE, style)
            Font.ROUNDED -> Typeface.create("sans-serif-rounded", style)
            Font.CONDENSED -> Typeface.create("sans-serif-condensed", style)
            Font.CASUAL -> Typeface.create("casual", style)
            Font.CURSIVE -> Typeface.create("cursive", style)
        }
    }

    /** Buram murah tapi halus: perkecil sangat jauh lalu perbesar dengan filter (dua kali). */
    private fun blurred(src: Bitmap, w: Int, h: Int): Bitmap {
        val small = Bitmap.createScaledBitmap(src, 24, 24, true)
        val mid = Bitmap.createScaledBitmap(small, 96, 96, true)
        // isi penuh (crop tengah)
        val out = Bitmap.createBitmap(w, h, Bitmap.Config.ARGB_8888)
        val c = Canvas(out)
        val s = maxOf(w, h) / 96f
        val p = Paint(Paint.FILTER_BITMAP_FLAG or Paint.ANTI_ALIAS_FLAG)
        c.translate((w - 96 * s) / 2f, (h - 96 * s) / 2f)
        c.scale(s, s)
        c.drawBitmap(mid, 0f, 0f, p)
        return out
    }

    fun render(
        o: Options,
        accent: Int,
        title: String,
        artist: String,
        lines: List<String>,
        cover: Bitmap?,
        scale: Float = 1f
    ): Bitmap {
        val w = 1080
        val h = if (o.story) 1920 else 1080
        // [scale] < 1 dipakai untuk pratinjau (jauh lebih ringan); ekspor memakai 1f.
        val bmp = Bitmap.createBitmap((w * scale).toInt(), (h * scale).toInt(), Bitmap.Config.ARGB_8888)
        val c = Canvas(bmp)
        c.scale(scale, scale)
        val p = Paint(Paint.ANTI_ALIAS_FLAG)
        val hsv = FloatArray(3)
        Color.colorToHSV(accent, hsv)

        // ---- latar ----
        val useCover = cover != null && (o.bg == Bg.BLUR || o.bg == Bg.GLASS)
        if (useCover) {
            c.drawBitmap(blurred(cover!!, w, h), 0f, 0f, null)
            p.color = Color.argb(if (o.bg == Bg.BLUR) 120 else 90, 0, 0, 0)
            c.drawRect(0f, 0f, w.toFloat(), h.toFloat(), p)
        } else if (o.bg == Bg.SOLID) {
            c.drawColor(Color.HSVToColor(floatArrayOf(hsv[0], 0.45f, 0.16f)))
        } else {
            val top = Color.HSVToColor(floatArrayOf(hsv[0], 0.55f, 0.46f))
            val mid = Color.HSVToColor(floatArrayOf(hsv[0], 0.6f, 0.24f))
            val bot = Color.HSVToColor(floatArrayOf((hsv[0] + 14f) % 360f, 0.55f, 0.09f))
            p.shader = LinearGradient(0f, 0f, 0f, h.toFloat(), intArrayOf(top, mid, bot), floatArrayOf(0f, 0.5f, 1f), Shader.TileMode.CLAMP)
            c.drawRect(0f, 0f, w.toFloat(), h.toFloat(), p)
            p.shader = RadialGradient(w * 0.2f, h * 0.05f, w * 0.9f, Color.argb(80, 255, 255, 255), Color.TRANSPARENT, Shader.TileMode.CLAMP)
            c.drawRect(0f, 0f, w.toFloat(), h.toFloat(), p)
            p.shader = null
        }

        val glass = o.bg == Bg.GLASS
        val margin = if (glass) 64f else 0f
        val card = RectF(margin, margin + if (o.story) 120f else 0f, w - margin, h - margin - if (o.story) 120f else 0f)
        if (glass) {
            val r = 56f
            p.style = Paint.Style.FILL
            p.shader = LinearGradient(card.left, card.top, card.right, card.bottom,
                intArrayOf(Color.argb(70, 255, 255, 255), Color.argb(22, 255, 255, 255), Color.argb(44, 255, 255, 255)), null, Shader.TileMode.CLAMP)
            c.drawRoundRect(card, r, r, p)
            p.shader = LinearGradient(card.left, card.top, card.right, card.bottom,
                intArrayOf(Color.argb(210, 255, 255, 255), Color.argb(30, 255, 255, 255), Color.argb(120, 255, 255, 255)), null, Shader.TileMode.CLAMP)
            p.style = Paint.Style.STROKE
            p.strokeWidth = 3f
            c.drawRoundRect(card, r, r, p)
            p.shader = null
            p.style = Paint.Style.FILL
        }

        // Otomatis: putih yang sedikit condong ke warna sampul; pilihan lain memakai warna tetapnya.
        val ink = o.ink.rgb ?: Color.HSVToColor(floatArrayOf(hsv[0], 0.08f, 1f))
        val pad = if (glass) 56f else 84f
        val left = card.left + pad
        val textW = (card.width() - pad * 2).toInt()
        var y = card.top + if (glass) 56f else (if (o.story) 150f else 84f)

        // ---- sampul + judul ----
        val tile = if (o.showCover) cover else null
        val cs = if (o.story) 190f else 150f
        if (tile != null) {
            val rect = RectF(left, y, left + cs, y + cs)
            val path = Path().apply { addRoundRect(rect, 28f, 28f, Path.Direction.CW) }
            c.save()
            c.clipPath(path)
            c.drawBitmap(tile, null, rect, Paint(Paint.FILTER_BITMAP_FLAG))
            c.restore()
        }
        val tx = left + if (tile != null) cs + 32f else 0f
        val tw = (card.right - pad - tx).toInt().coerceAtLeast(200)
        fun block(text: String, size: Float, bold: Boolean, alpha: Int, maxLines: Int, at: Float, x: Float, width: Int): Float {
            if (text.isBlank()) return 0f
            val tp = TextPaint(Paint.ANTI_ALIAS_FLAG).apply {
                textSize = size; color = ink; this.alpha = alpha; typeface = faceOf(o.font, bold)
            }
            val l = StaticLayout.Builder.obtain(text, 0, text.length, tp, width)
                .setAlignment(Layout.Alignment.ALIGN_NORMAL).setMaxLines(maxLines)
                .setEllipsize(TextUtils.TruncateAt.END).setLineSpacing(0f, 1.05f).build()
            c.save(); c.translate(x, at); l.draw(c); c.restore()
            return l.height.toFloat()
        }
        val headH = block(title, if (o.story) 54f else 46f, true, 255, 2, y + 6f, tx, tw)
        block(artist, 34f, false, 200, 1, y + 6f + headH + 6f, tx, tw)
        y += maxOf(cs, headH + 50f) + if (o.story) 70f else 50f

        // ---- lirik ----
        val footerH = 80f
        val avail = card.bottom - footerH - pad * 0.5f - y
        val text = lines.joinToString("\n")
        var size = if (o.story) 84f else 70f
        var layout: StaticLayout? = null
        val align = when (o.align) {
            Align.LEFT -> Layout.Alignment.ALIGN_NORMAL
            Align.CENTER -> Layout.Alignment.ALIGN_CENTER
            Align.RIGHT -> Layout.Alignment.ALIGN_OPPOSITE
        }
        while (size >= 28f) {
            val tp = TextPaint(Paint.ANTI_ALIAS_FLAG).apply { textSize = size; color = ink; typeface = faceOf(o.font, o.bold) }
            val l = StaticLayout.Builder.obtain(text, 0, text.length, tp, textW).setAlignment(align).setLineSpacing(0f, 1.14f).build()
            layout = l
            if (l.height <= avail) break
            size -= 4f
        }
        layout?.let { l ->
            c.save()
            c.translate(left, y + maxOf(0f, (avail - l.height) / 2f).coerceAtMost(if (o.story) 160f else 40f))
            l.draw(c)
            c.restore()
        }

        // ---- kaki ----
        val fp = TextPaint(Paint.ANTI_ALIAS_FLAG).apply { textSize = 32f; color = ink; alpha = 170; typeface = Typeface.DEFAULT_BOLD }
        if (o.watermark) c.drawText("Aoto", left, card.bottom - pad * 0.7f, fp)
        return bmp
    }
}
