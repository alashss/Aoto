package com.jimz.jimztune.util

import android.content.Context
import android.graphics.Bitmap
import android.graphics.BitmapFactory
import android.net.Uri
import java.io.ByteArrayOutputStream

object ImageUtil {
    /** Hasil menyiapkan gambar pilihan pengguna untuk diunggah. */
    sealed interface Picked {
        class Ready(val bytes: ByteArray, val contentType: String, val ext: String) : Picked
        class TooBig(val limitMb: Int) : Picked
        object Failed : Picked
    }

    /**
     * GIF / WebP bergerak diunggah apa adanya (tidak dipotong dan tidak diubah ke JPEG, kalau tidak geraknya hilang).
     * Gambar diam tetap dipotong + dikecilkan jadi JPEG seperti biasa.
     */
    fun prepare(context: Context, uri: Uri, maxSide: Int, aspect: Float?, animatedLimitBytes: Int): Picked {
        val head = try {
            context.contentResolver.openInputStream(uri)?.use { ins ->
                val buf = ByteArray(32)
                var n = 0
                while (n < buf.size) {
                    val r = ins.read(buf, n, buf.size - n)
                    if (r < 0) break
                    n += r
                }
                buf.copyOf(n)
            }
        } catch (_: Exception) {
            null
        } ?: return Picked.Failed
        val type = animatedType(head)
        if (type == null) {
            val bytes = jpeg(context, uri, maxSide, aspect) ?: return Picked.Failed
            return Picked.Ready(bytes, "image/jpeg", "jpg")
        }
        return try {
            val all = context.contentResolver.openInputStream(uri)?.use { it.readBytes() } ?: return Picked.Failed
            if (all.size > animatedLimitBytes) Picked.TooBig(animatedLimitBytes / (1024 * 1024))
            else Picked.Ready(all, type, if (type == "image/gif") "gif" else "webp")
        } catch (_: Exception) {
            Picked.Failed
        }
    }

    /** "image/gif" untuk GIF, "image/webp" untuk WebP bergerak, null untuk gambar diam / lainnya (dikenali dari isi file). */
    private fun animatedType(h: ByteArray): String? {
        fun at(i: Int) = if (i < h.size) h[i].toInt().toChar() else '\u0000'
        if (at(0) == 'G' && at(1) == 'I' && at(2) == 'F' && at(3) == '8') return "image/gif"
        if (at(0) == 'R' && at(1) == 'I' && at(2) == 'F' && at(3) == 'F' &&
            at(8) == 'W' && at(9) == 'E' && at(10) == 'B' && at(11) == 'P' &&
            at(12) == 'V' && at(13) == 'P' && at(14) == '8' && at(15) == 'X' &&
            h.size > 20 && (h[20].toInt() and 0x02) != 0
        ) return "image/webp"
        return null
    }

    /**
     * Membaca gambar dari galeri, memotong dari tengah sesuai [aspect] (lebar/tinggi; null = tidak dipotong),
     * memperkecil sampai sisi terpanjang maksimal [maxSide] px, lalu mengubahnya jadi JPEG.
     * Jalankan di thread latar (Dispatchers.IO).
     */
    fun jpeg(context: Context, uri: Uri, maxSide: Int, aspect: Float?, quality: Int = 86): ByteArray? {
        return try {
            val resolver = context.contentResolver
            val bounds = BitmapFactory.Options().apply { inJustDecodeBounds = true }
            resolver.openInputStream(uri)?.use { BitmapFactory.decodeStream(it, null, bounds) }
            if (bounds.outWidth <= 0 || bounds.outHeight <= 0) return null
            var sample = 1
            while (bounds.outWidth / sample > maxSide * 2 || bounds.outHeight / sample > maxSide * 2) sample *= 2
            val opts = BitmapFactory.Options().apply { inSampleSize = sample }
            val src = resolver.openInputStream(uri)?.use { BitmapFactory.decodeStream(it, null, opts) } ?: return null

            var bmp = src
            if (aspect != null) {
                val w = src.width
                val h = src.height
                val cropW: Int
                val cropH: Int
                if (w.toFloat() / h > aspect) {
                    cropH = h
                    cropW = (h * aspect).toInt()
                } else {
                    cropW = w
                    cropH = (w / aspect).toInt()
                }
                bmp = Bitmap.createBitmap(src, (w - cropW) / 2, (h - cropH) / 2, cropW.coerceAtLeast(1), cropH.coerceAtLeast(1))
            }
            val longest = maxOf(bmp.width, bmp.height)
            if (longest > maxSide) {
                val k = maxSide.toFloat() / longest
                bmp = Bitmap.createScaledBitmap(bmp, (bmp.width * k).toInt().coerceAtLeast(1), (bmp.height * k).toInt().coerceAtLeast(1), true)
            }
            ByteArrayOutputStream().also { bmp.compress(Bitmap.CompressFormat.JPEG, quality, it) }.toByteArray()
        } catch (_: Exception) {
            null
        }
    }
}
