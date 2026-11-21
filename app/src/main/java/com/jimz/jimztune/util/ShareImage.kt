package com.jimz.jimztune.util

import android.content.Context
import android.content.Intent
import android.graphics.Bitmap
import android.graphics.Canvas
import android.graphics.Color
import android.graphics.LinearGradient
import android.graphics.Paint
import android.graphics.RadialGradient
import android.graphics.Shader
import android.graphics.Typeface
import android.text.Layout
import android.text.StaticLayout
import android.text.TextPaint
import android.text.TextUtils
import android.widget.Toast
import androidx.core.content.FileProvider
import java.io.File

/** Membuat kartu gambar (kotak 1080x1080 atau story 1080x1920) untuk dibagikan: lirik, rekap, streak. */
object ShareImage {
    class Section(val title: String, val lines: List<String>)

    fun render(
        story: Boolean,
        accent: Int,
        heading: String,
        big: String? = null,
        bigCaption: String? = null,
        quote: List<String> = emptyList(),
        sections: List<Section> = emptyList(),
        footer: String = "Aoto"
    ): Bitmap {
        val w = 1080
        val h = if (story) 1920 else 1080
        val bmp = Bitmap.createBitmap(w, h, Bitmap.Config.ARGB_8888)
        val c = Canvas(bmp)
        val hsv = FloatArray(3)
        Color.colorToHSV(accent, hsv)
        val top = Color.HSVToColor(floatArrayOf(hsv[0], 0.55f, 0.42f))
        val mid = Color.HSVToColor(floatArrayOf(hsv[0], 0.6f, 0.22f))
        val bottom = Color.HSVToColor(floatArrayOf((hsv[0] + 12f) % 360f, 0.55f, 0.08f))
        val p = Paint(Paint.ANTI_ALIAS_FLAG)
        p.shader = LinearGradient(0f, 0f, 0f, h.toFloat(), intArrayOf(top, mid, bottom), floatArrayOf(0f, 0.5f, 1f), Shader.TileMode.CLAMP)
        c.drawRect(0f, 0f, w.toFloat(), h.toFloat(), p)
        p.shader = RadialGradient(w * 0.2f, h * 0.05f, w * 0.9f, Color.argb(80, 255, 255, 255), Color.TRANSPARENT, Shader.TileMode.CLAMP)
        c.drawRect(0f, 0f, w.toFloat(), h.toFloat(), p)

        val pad = 84f
        val textW = (w - pad * 2).toInt()
        var y = if (story) 170f else 96f

        fun draw(text: String, size: Float, color: Int, bold: Boolean, maxLines: Int = 3, alpha: Int = 255): Float {
            if (text.isBlank()) return 0f
            val tp = TextPaint(Paint.ANTI_ALIAS_FLAG)
            tp.textSize = size
            tp.color = color
            tp.alpha = alpha
            tp.typeface = if (bold) Typeface.DEFAULT_BOLD else Typeface.DEFAULT
            val l = StaticLayout.Builder.obtain(text, 0, text.length, tp, textW)
                .setAlignment(Layout.Alignment.ALIGN_NORMAL)
                .setMaxLines(maxLines)
                .setEllipsize(TextUtils.TruncateAt.END)
                .setLineSpacing(0f, 1.08f)
                .build()
            c.save()
            c.translate(pad, y)
            l.draw(c)
            c.restore()
            return l.height.toFloat()
        }

        y += draw(heading.uppercase(), 34f, Color.WHITE, true, 2, 190) + 28f

        if (big != null) {
            y += draw(big, if (story) 190f else 150f, Color.WHITE, true, 1) + 4f
            if (bigCaption != null) y += draw(bigCaption, 40f, Color.WHITE, false, 2, 200) + 48f
        }

        if (quote.isNotEmpty()) {
            // Teks lirik dikecilkan sampai muat di ruang yang tersedia.
            val avail = (h - y - 190f)
            val text = quote.joinToString("\n")
            var size = if (story) 76f else 66f
            while (size > 30f) {
                val tp = TextPaint(Paint.ANTI_ALIAS_FLAG).apply { textSize = size; typeface = Typeface.DEFAULT_BOLD }
                val l = StaticLayout.Builder.obtain(text, 0, text.length, tp, textW).setLineSpacing(0f, 1.12f).build()
                if (l.height <= avail) break
                size -= 4f
            }
            y += draw(text, size, Color.WHITE, true, 30) + 24f
        }

        for (s in sections) {
            y += 26f
            y += draw(s.title.uppercase(), 30f, Color.WHITE, true, 1, 170) + 14f
            s.lines.forEachIndexed { i, line ->
                y += draw("${i + 1}. $line", 42f, Color.WHITE, i == 0, 1) + 12f
            }
        }

        val fp = TextPaint(Paint.ANTI_ALIAS_FLAG)
        fp.textSize = 34f
        fp.color = Color.WHITE
        fp.alpha = 170
        fp.typeface = Typeface.DEFAULT_BOLD
        c.drawText(footer, pad, h - 90f, fp)
        return bmp
    }

    /** Menyimpan [bmp] sementara lalu membuka lembar bagikan Android. */
    /** Menyimpan gambar ke galeri (Pictures/Aoto). Android 10 ke atas tanpa izin tambahan; di bawahnya tidak didukung. */
    fun saveToGallery(context: Context, bmp: Bitmap, name: String = "aoto"): Boolean {
        if (android.os.Build.VERSION.SDK_INT < android.os.Build.VERSION_CODES.Q) return false
        return try {
            val resolver = context.contentResolver
            val values = android.content.ContentValues().apply {
                put(android.provider.MediaStore.Images.Media.DISPLAY_NAME, "${name}_${System.currentTimeMillis()}.png")
                put(android.provider.MediaStore.Images.Media.MIME_TYPE, "image/png")
                put(android.provider.MediaStore.Images.Media.RELATIVE_PATH, android.os.Environment.DIRECTORY_PICTURES + "/Aoto")
                put(android.provider.MediaStore.Images.Media.IS_PENDING, 1)
            }
            val uri = resolver.insert(android.provider.MediaStore.Images.Media.EXTERNAL_CONTENT_URI, values) ?: return false
            val out = resolver.openOutputStream(uri)
            if (out == null) { resolver.delete(uri, null, null); return false }
            out.use { bmp.compress(Bitmap.CompressFormat.PNG, 100, it) }
            values.clear()
            values.put(android.provider.MediaStore.Images.Media.IS_PENDING, 0)
            resolver.update(uri, values, null, null)
            true
        } catch (_: Exception) {
            false
        }
    }

    fun share(context: Context, bmp: Bitmap, name: String = "aoto") {
        try {
            val dir = File(context.cacheDir, "shared").apply { mkdirs() }
            dir.listFiles()?.forEach { if (System.currentTimeMillis() - it.lastModified() > 3_600_000L) it.delete() }
            val f = File(dir, "${name}_${System.currentTimeMillis()}.png")
            f.outputStream().use { bmp.compress(Bitmap.CompressFormat.PNG, 100, it) }
            val uri = FileProvider.getUriForFile(context, "${context.packageName}.files", f)
            val send = Intent(Intent.ACTION_SEND)
                .setType("image/png")
                .putExtra(Intent.EXTRA_STREAM, uri)
                .addFlags(Intent.FLAG_GRANT_READ_URI_PERMISSION)
            context.startActivity(Intent.createChooser(send, "Bagikan").addFlags(Intent.FLAG_ACTIVITY_NEW_TASK))
        } catch (_: Exception) {
            Toast.makeText(context, "Tidak bisa membagikan gambar", Toast.LENGTH_SHORT).show()
        }
    }
}
