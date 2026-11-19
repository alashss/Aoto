package com.jimz.jimztune.ui.theme

import android.content.Context
import androidx.compose.animation.animateColorAsState
import androidx.compose.animation.core.tween
import androidx.compose.runtime.Composable
import androidx.compose.runtime.Immutable
import androidx.compose.runtime.LaunchedEffect
import androidx.compose.runtime.compositionLocalOf
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.setValue
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.platform.LocalContext
import androidx.core.graphics.drawable.toBitmap
import coil.imageLoader
import coil.request.ImageRequest
import coil.request.SuccessResult
import kotlinx.coroutines.CancellationException
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.withContext
import kotlin.math.max

/** Warna yang mengikuti cover lagu yang sedang diputar. */
@Immutable
data class CoverTheme(val top: Color, val mid: Color, val bottom: Color, val accent: Color)

/** Tema biru bawaan (dipakai saat belum ada lagu, atau cover gagal dibaca). */
val DefaultCoverTheme: CoverTheme
    get() = CoverTheme(
        top = JimzSurface,
        mid = JimzSurfaceHigh,
        bottom = JimzBackground,
        accent = JimzBlue
    )

val LocalCoverTheme = compositionLocalOf { DefaultCoverTheme }

/** Mengambil warna dominan dari cover lalu beralih ke warna itu dengan animasi halus. */
@Composable
fun rememberCoverTheme(url: String?): CoverTheme {
    val context = LocalContext.current
    var target by remember { mutableStateOf(DefaultCoverTheme) }

    // Ikut diulang saat tema diganti, supaya warna bawaan (tanpa sampul) langsung mengikuti tema baru.
    val themeColors = AppTheme.colors
    LaunchedEffect(url, themeColors) {
        target = if (url.isNullOrBlank()) {
            DefaultCoverTheme
        } else {
            withContext(Dispatchers.IO) { extractCoverTheme(context, url) } ?: DefaultCoverTheme
        }
    }

    val top by animateColorAsState(target.top, tween(800), label = "coverTop")
    val mid by animateColorAsState(target.mid, tween(800), label = "coverMid")
    val bottom by animateColorAsState(target.bottom, tween(800), label = "coverBottom")
    val accent by animateColorAsState(target.accent, tween(800), label = "coverAccent")
    return remember(top, mid, bottom, accent) { CoverTheme(top, mid, bottom, accent) }
}

private suspend fun extractCoverTheme(context: Context, url: String): CoverTheme? {
    return try {
        val request = ImageRequest.Builder(context)
            .data(url)
            .allowHardware(false)
            .size(64)
            .build()
        val result = context.imageLoader.execute(request) as? SuccessResult ?: return null
        val bitmap = result.drawable.toBitmap(48, 48)
        themeFromBitmap(bitmap)
    } catch (e: CancellationException) {
        throw e
    } catch (_: Throwable) {
        null
    }
}

/** Memilih warna yang paling "hidup" (jenuh dan cukup terang), lalu membuat gradasi gelap darinya. */
private fun themeFromBitmap(bitmap: android.graphics.Bitmap): CoverTheme {
    val w = bitmap.width
    val h = bitmap.height
    val pixels = IntArray(w * h)
    bitmap.getPixels(pixels, 0, w, 0, 0, w, h)

    val bins = 12
    val weight = FloatArray(bins)
    val hueSum = FloatArray(bins)
    val satSum = FloatArray(bins)
    val hsv = FloatArray(3)
    var lumaSum = 0f

    for (px in pixels) {
        android.graphics.Color.colorToHSV(px, hsv)
        lumaSum += hsv[2]
        if (hsv[1] < 0.15f || hsv[2] < 0.18f) continue
        val bin = ((hsv[0] / 360f) * bins).toInt().coerceIn(0, bins - 1)
        val wgt = hsv[1] * hsv[2]
        weight[bin] += wgt
        hueSum[bin] += hsv[0] * wgt
        satSum[bin] += hsv[1] * wgt
    }

    var best = 0
    for (i in 1 until bins) if (weight[i] > weight[best]) best = i

    // Cover hampir hitam-putih: pakai abu kebiruan supaya tetap serasi dengan tema.
    if (weight[best] < 2f) {
        val v = (lumaSum / pixels.size).coerceIn(0.25f, 0.6f) * 0.6f
        return CoverTheme(
            top = Color.hsv(220f, 0.18f, v + 0.12f),
            mid = Color.hsv(220f, 0.2f, v * 0.7f),
            bottom = Color.hsv(225f, 0.25f, 0.08f),
            accent = Color.hsv(220f, 0.3f, 0.9f)
        )
    }

    val hue = hueSum[best] / max(weight[best], 0.0001f)
    // Jenuh dan terang dibatasi: cover berwarna kuning/hijau zaitun gampang jadi keruh kalau terlalu pekat.
    val rawSat = (satSum[best] / max(weight[best], 0.0001f)).coerceIn(0.4f, 0.85f)
    val sat = (rawSat * 0.72f).coerceIn(0.3f, 0.6f)
    return CoverTheme(
        top = Color.hsv(hue, sat, 0.34f),
        mid = Color.hsv(hue, (sat + 0.04f).coerceAtMost(0.7f), 0.21f),
        bottom = Color.hsv(hue, sat * 0.8f, 0.09f),
        accent = Color.hsv(hue, (rawSat * 0.7f).coerceIn(0.35f, 0.75f), 0.96f)
    )
}
