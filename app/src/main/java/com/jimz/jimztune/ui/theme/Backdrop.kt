package com.jimz.jimztune.ui.theme

import android.os.Build
import androidx.compose.runtime.Composable
import androidx.compose.runtime.Stable
import androidx.compose.runtime.compositionLocalOf
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.setValue
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.drawBehind
import androidx.compose.ui.draw.drawWithContent
import androidx.compose.ui.geometry.Offset
import androidx.compose.ui.graphics.BlurEffect
import androidx.compose.ui.graphics.Outline
import androidx.compose.ui.graphics.Path
import androidx.compose.ui.graphics.Shape
import androidx.compose.ui.graphics.TileMode
import androidx.compose.ui.graphics.drawscope.clipPath
import androidx.compose.ui.graphics.drawscope.translate
import androidx.compose.ui.graphics.layer.GraphicsLayer
import androidx.compose.ui.graphics.layer.drawLayer
import androidx.compose.ui.graphics.rememberGraphicsLayer
import androidx.compose.ui.layout.onGloballyPositioned
import androidx.compose.ui.layout.positionInRoot
import androidx.compose.ui.platform.LocalDensity
import androidx.compose.ui.unit.Dp
import androidx.compose.ui.unit.IntSize
import androidx.compose.ui.unit.dp

/**
 * Sumber latar untuk kaca sungguhan: isi layar direkam ke satu layer, lalu bar kaca menggambar potongan layer itu
 * yang diburamkan, tepat di belakangnya. Hasilnya blur dari konten yang benar-benar ada di belakang bar.
 * Blur butuh Android 12 (API 31) ke atas; di bawah itu bar memakai tampilan kaca biasa.
 */
@Stable
class BackdropHolder(val raw: GraphicsLayer) {
    var origin by mutableStateOf(Offset.Zero)

    /** Kalau false, isi layar tidak direkam dan blur tidak digambar (hemat tenaga, mis. saat pemutar penuh terbuka). */
    var active by mutableStateOf(true)
}

val LocalBackdrop = compositionLocalOf<BackdropHolder?> { null }

val backdropSupported: Boolean get() = Build.VERSION.SDK_INT >= Build.VERSION_CODES.S

@Composable
fun rememberBackdropHolder(): BackdropHolder {
    val layer = rememberGraphicsLayer()
    return remember(layer) { BackdropHolder(layer) }
}

/** Pasang di wadah yang isinya ingin dijadikan latar kaca. Bar kaca harus berada di LUAR wadah ini. */
fun Modifier.backdropSource(h: BackdropHolder): Modifier = this
    .onGloballyPositioned { h.origin = it.positionInRoot() }
    .drawWithContent {
        if (h.active) {
            h.raw.record { this@drawWithContent.drawContent() }
            drawLayer(h.raw)
        } else {
            drawContent()
        }
    }

/** Gambar latar yang diburamkan di belakang elemen ini (dipotong mengikuti [shape]). */
@Composable
fun Modifier.glassBackdrop(shape: Shape, blur: Dp = 22.dp): Modifier {
    val h = LocalBackdrop.current ?: return this
    if (!backdropSupported) return this
    var pos by remember { mutableStateOf(Offset.Zero) }
    val blurLayer = rememberGraphicsLayer()
    val blurPx = with(LocalDensity.current) { blur.toPx() }
    return this
        .onGloballyPositioned { pos = it.positionInRoot() }
        .drawBehind {
            val w = size.width.toInt()
            val hh = size.height.toInt()
            if (w <= 0 || hh <= 0 || !h.active) return@drawBehind
            blurLayer.renderEffect = BlurEffect(blurPx, blurPx, TileMode.Clamp)
            val dx = h.origin.x - pos.x
            val dy = h.origin.y - pos.y
            blurLayer.record(size = IntSize(w, hh)) {
                translate(dx, dy) { drawLayer(h.raw) }
            }
            val path = outlineToPath(shape.createOutline(size, layoutDirection, this@drawBehind))
            clipPath(path) { drawLayer(blurLayer) }
        }
}

/** Mengubah Outline (bentuk) menjadi Path supaya bisa dipakai memotong gambar. */
fun outlineToPath(outline: Outline): Path = when (outline) {
    is Outline.Rectangle -> Path().apply { addRect(outline.rect) }
    is Outline.Rounded -> Path().apply { addRoundRect(outline.roundRect) }
    is Outline.Generic -> outline.path
}
