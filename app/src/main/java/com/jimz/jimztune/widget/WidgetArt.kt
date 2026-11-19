package com.jimz.jimztune.widget

import android.content.Context
import android.graphics.Bitmap
import android.graphics.Canvas
import android.graphics.Color
import android.graphics.LinearGradient
import android.graphics.Paint
import android.graphics.PorterDuff
import android.graphics.PorterDuffXfermode
import android.graphics.RadialGradient
import android.graphics.RectF
import android.graphics.Shader
import java.io.File

/** Gambar untuk widget: sampul bersudut membulat, warna dominan sampul, dan latar gradasi dari warna itu. */
object WidgetArt {
    const val DEFAULT_COLOR = 0xFF1B2A6B.toInt()
    private const val COVER_PX = 200

    private fun coverFile(context: Context) = File(context.filesDir, "widget_cover.png")

    /** Memotong persegi dari tengah, memperkecil, dan membulatkan sudutnya. */
    fun roundedCover(src: Bitmap): Bitmap {
        val side = minOf(src.width, src.height)
        val square = Bitmap.createBitmap(src, (src.width - side) / 2, (src.height - side) / 2, side, side)
        val scaled = Bitmap.createScaledBitmap(square, COVER_PX, COVER_PX, true)
        val out = Bitmap.createBitmap(COVER_PX, COVER_PX, Bitmap.Config.ARGB_8888)
        val c = Canvas(out)
        val paint = Paint(Paint.ANTI_ALIAS_FLAG)
        val r = COVER_PX * 0.18f
        c.drawRoundRect(RectF(0f, 0f, COVER_PX.toFloat(), COVER_PX.toFloat()), r, r, paint)
        paint.xfermode = PorterDuffXfermode(PorterDuff.Mode.SRC_IN)
        c.drawBitmap(scaled, 0f, 0f, paint)
        return out
    }

    /**
     * Warna paling "hidup" dari sampul: piksel dikelompokkan per rona (hue), kelompok yang paling
     * banyak dan paling berwarna menang. Hasilnya dicerahkan/digelapkan ke rentang yang tetap nyaman
     * untuk teks putih. Sampul hitam-putih tetap menghasilkan abu-abu kebiruan yang netral.
     */
    fun dominantColor(src: Bitmap): Int {
        val small = Bitmap.createScaledBitmap(src, 32, 32, true)
        val buckets = 18
        val wSum = DoubleArray(buckets)
        val rSum = DoubleArray(buckets)
        val gSum = DoubleArray(buckets)
        val bSum = DoubleArray(buckets)
        var avgR = 0.0
        var avgG = 0.0
        var avgB = 0.0
        var n = 0
        val hsv = FloatArray(3)
        for (y in 0 until small.height) for (x in 0 until small.width) {
            val p = small.getPixel(x, y)
            if (Color.alpha(p) < 128) continue
            Color.colorToHSV(p, hsv)
            avgR += Color.red(p); avgG += Color.green(p); avgB += Color.blue(p); n++
            // Abaikan piksel yang nyaris putih / hitam / abu-abu: mereka bukan "warna" sampul.
            if (hsv[1] < 0.18f || hsv[2] < 0.15f) continue
            val w = (hsv[1] * hsv[1] * 1.4 + 0.1) * (0.35 + hsv[2])
            val b = ((hsv[0] / 360f) * buckets).toInt().coerceIn(0, buckets - 1)
            wSum[b] += w
            rSum[b] += Color.red(p) * w
            gSum[b] += Color.green(p) * w
            bSum[b] += Color.blue(p) * w
        }
        if (n == 0) return DEFAULT_COLOR
        var best = -1
        var bestW = 0.0
        for (i in 0 until buckets) {
            // Gabungkan dengan tetangganya agar rona yang berdekatan dihitung satu kelompok.
            val w = wSum[i] + 0.5 * (wSum[(i + 1) % buckets] + wSum[(i + buckets - 1) % buckets])
            if (w > bestW) { bestW = w; best = i }
        }
        val out = FloatArray(3)
        if (best < 0 || bestW < 0.4) {
            // Sampul hampir tanpa warna: pakai kecerahan rata-rata dengan sedikit nuansa biru.
            Color.colorToHSV(Color.rgb((avgR / n).toInt(), (avgG / n).toInt(), (avgB / n).toInt()), out)
            out[0] = 224f
            out[1] = 0.28f
            out[2] = out[2].coerceIn(0.26f, 0.42f)
            return Color.HSVToColor(out)
        }
        val ww = wSum[best]
        Color.colorToHSV(Color.rgb((rSum[best] / ww).toInt(), (gSum[best] / ww).toInt(), (bSum[best] / ww).toInt()), out)
        out[1] = out[1].coerceIn(0.45f, 0.92f)
        out[2] = out[2].coerceIn(0.38f, 0.66f)
        return Color.HSVToColor(out)
    }

    /**
     * Latar widget seukuran widget ([w] x [h] px) dengan sudut membulat [radius] px.
     * Tema "Warna sampul": gradasi dari warna sampul + cahaya lembut + bayangan bawah. Tema lain: gradasi tetap
     * dari [WidgetTheme.colors], opsional dengan garis tepi. [alpha] 0..255 = kepekatan seluruh latar.
     */
    fun background(color: Int, w: Int, h: Int, radius: Float, theme: WidgetTheme, alpha: Int = 255): Bitmap {
        val base = Bitmap.createBitmap(w, h, Bitmap.Config.ARGB_8888)
        val c = Canvas(base)
        val rect = RectF(0f, 0f, w.toFloat(), h.toFloat())
        val paint = Paint(Paint.ANTI_ALIAS_FLAG)
        val cols = theme.colors
        if (cols == null) {
            val hsv = FloatArray(3)
            Color.colorToHSV(color, hsv)
            val light = Color.HSVToColor(floatArrayOf(hsv[0], (hsv[1] * 0.92f).coerceIn(0f, 1f), (hsv[2] * 1.18f).coerceAtMost(0.8f)))
            val mid = Color.HSVToColor(floatArrayOf(hsv[0], hsv[1], hsv[2] * 0.72f))
            val dark = Color.HSVToColor(floatArrayOf((hsv[0] + 14f) % 360f, hsv[1], hsv[2] * 0.3f))
            paint.shader = LinearGradient(0f, 0f, w.toFloat(), h.toFloat(), intArrayOf(light, mid, dark), floatArrayOf(0f, 0.5f, 1f), Shader.TileMode.CLAMP)
        } else {
            val pos = if (cols.size == 3) floatArrayOf(0f, 0.55f, 1f) else null
            paint.shader = LinearGradient(0f, 0f, w.toFloat(), h.toFloat(), cols, pos, Shader.TileMode.CLAMP)
        }
        c.drawRoundRect(rect, radius, radius, paint)

        if (cols == null || cols.size >= 3) {
            // Cahaya lembut di pojok kiri atas (dekat sampul).
            val glow = Paint(Paint.ANTI_ALIAS_FLAG)
            val gr = maxOf(w, h) * 0.75f
            glow.shader = RadialGradient(
                w * 0.12f, h * 0.05f, gr,
                intArrayOf(Color.argb(if (cols == null) 70 else 46, 255, 255, 255), Color.argb(0, 255, 255, 255)), null, Shader.TileMode.CLAMP
            )
            c.drawRoundRect(rect, radius, radius, glow)
        }
        if (!theme.light) {
            // Bayangan bawah untuk keterbacaan teks.
            val scrim = Paint(Paint.ANTI_ALIAS_FLAG)
            scrim.shader = LinearGradient(
                0f, h * 0.35f, 0f, h.toFloat(),
                Color.argb(0, 0, 0, 0), Color.argb(if (cols == null) 95 else 60, 0, 0, 0), Shader.TileMode.CLAMP
            )
            c.drawRoundRect(rect, radius, radius, scrim)
        }
        if (theme.stroke != 0) {
            val sp = Paint(Paint.ANTI_ALIAS_FLAG)
            sp.style = Paint.Style.STROKE
            sp.strokeWidth = maxOf(2f, minOf(w, h) * 0.012f)
            sp.color = theme.stroke
            val inset = sp.strokeWidth / 2f
            c.drawRoundRect(RectF(inset, inset, w - inset, h - inset), radius, radius, sp)
        }
        if (alpha >= 255) return base
        val out = Bitmap.createBitmap(w, h, Bitmap.Config.ARGB_8888)
        val fade = Paint(Paint.ANTI_ALIAS_FLAG)
        fade.alpha = alpha.coerceIn(0, 255)
        Canvas(out).drawBitmap(base, 0f, 0f, fade)
        base.recycle()
        return out
    }

    fun saveCover(context: Context, cover: Bitmap?) {
        val f = coverFile(context)
        try {
            if (cover == null) f.delete() else f.outputStream().use { cover.compress(Bitmap.CompressFormat.PNG, 100, it) }
        } catch (_: Exception) {
        }
    }

    fun loadCover(context: Context): Bitmap? = try {
        coverFile(context).takeIf { it.exists() }?.let { android.graphics.BitmapFactory.decodeFile(it.absolutePath) }
    } catch (_: Exception) {
        null
    }
}
