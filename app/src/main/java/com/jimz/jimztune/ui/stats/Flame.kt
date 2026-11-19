package com.jimz.jimztune.ui.stats

import androidx.compose.animation.core.FastOutSlowInEasing
import androidx.compose.animation.core.RepeatMode
import androidx.compose.animation.core.animateFloat
import androidx.compose.animation.core.infiniteRepeatable
import androidx.compose.animation.core.rememberInfiniteTransition
import androidx.compose.animation.core.tween
import androidx.compose.foundation.Canvas
import androidx.compose.foundation.layout.size
import androidx.compose.runtime.Composable
import androidx.compose.runtime.getValue
import androidx.compose.ui.Modifier
import androidx.compose.ui.geometry.Offset
import androidx.compose.ui.graphics.Brush
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.graphics.Path
import androidx.compose.ui.graphics.drawscope.Stroke
import androidx.compose.ui.unit.Dp

/** Warna api per tingkat: (ujung atas, pangkal, inti, cahaya). Tingkat 6 = api biru. */
class FlamePalette(val tip: Color, val base: Color, val core: Color, val glow: Color)

fun flamePalette(level: Int): FlamePalette = when {
    level >= 6 -> FlamePalette(Color(0xFF2F6BFF), Color(0xFF7FD6FF), Color(0xFFEAFBFF), Color(0xFF4DA3FF))
    level >= 3 -> FlamePalette(Color(0xFFFF3D2E), Color(0xFFFFB02E), Color(0xFFFFF3B0), Color(0xFFFF7A2E))
    level >= 1 -> FlamePalette(Color(0xFFFF7A1A), Color(0xFFFFCB47), Color(0xFFFFF6CC), Color(0xFFFFA62E))
    else -> FlamePalette(Color(0xFF6B7391), Color(0xFF8E96B5), Color(0xFFB9BFD6), Color(0xFF6B7391))
}

/** Bentuk api dalam kotak 0..1; [k] mengecilkan bentuk ke arah pangkalnya (dipakai untuk inti api). */
private fun flamePath(w: Float, h: Float, k: Float): Path {
    fun x(v: Float) = (0.5f + (v - 0.5f) * k) * w
    fun y(v: Float) = (0.98f - (0.98f - v) * k) * h
    return Path().apply {
        moveTo(x(0.5f), y(0.02f))
        cubicTo(x(0.58f), y(0.22f), x(0.93f), y(0.38f), x(0.9f), y(0.66f))
        cubicTo(x(0.88f), y(0.88f), x(0.7f), y(0.98f), x(0.5f), y(0.98f))
        cubicTo(x(0.3f), y(0.98f), x(0.12f), y(0.88f), x(0.1f), y(0.66f))
        cubicTo(x(0.09f), y(0.5f), x(0.2f), y(0.4f), x(0.3f), y(0.3f))
        cubicTo(x(0.32f), y(0.4f), x(0.36f), y(0.44f), x(0.42f), y(0.46f))
        cubicTo(x(0.4f), y(0.3f), x(0.44f), y(0.14f), x(0.5f), y(0.02f))
        close()
    }
}

/**
 * Api gambar sendiri (bukan emoji): bergoyang halus, berpendar, dan berubah warna sesuai tingkat streak.
 * Tingkat 0 digambar abu-abu dan diam (api padam).
 */
@Composable
fun FlameIcon(level: Int, size: Dp, modifier: Modifier = Modifier) {
    val pal = flamePalette(level)
    val lit = level > 0
    val transition = rememberInfiniteTransition(label = "flame")
    val sway by transition.animateFloat(
        initialValue = 0f,
        targetValue = 1f,
        animationSpec = infiniteRepeatable(tween(1300, easing = FastOutSlowInEasing), RepeatMode.Reverse),
        label = "flameSway"
    )
    val pulse by transition.animateFloat(
        initialValue = 0f,
        targetValue = 1f,
        animationSpec = infiniteRepeatable(tween(1900, easing = FastOutSlowInEasing), RepeatMode.Reverse),
        label = "flamePulse"
    )
    Canvas(modifier.size(size)) {
        val w = this.size.width
        val h = this.size.height
        if (lit) {
            val r = w * (0.62f + 0.08f * pulse)
            drawCircle(
                brush = Brush.radialGradient(
                    colors = listOf(pal.glow.copy(alpha = 0.34f + 0.12f * pulse), Color.Transparent),
                    center = Offset(w / 2f, h * 0.62f),
                    radius = r
                ),
                radius = r,
                center = Offset(w / 2f, h * 0.62f)
            )
        }
        val s = if (lit) 1f + 0.035f * sway else 1f
        val dx = if (lit) (sway - 0.5f) * w * 0.02f else 0f
        val ox = w * (1f - s) / 2f + dx
        val oy = h * (1f - s)
        val outer = flamePath(w * s, h * s, 1f).also { it.translate(Offset(ox, oy)) }
        if (lit) {
            drawPath(outer, Brush.verticalGradient(listOf(pal.tip, pal.base), startY = oy, endY = oy + h * s))
            val inner = flamePath(w * s, h * s, 0.52f).also { it.translate(Offset(ox, oy)) }
            drawPath(inner, Brush.verticalGradient(listOf(pal.base, pal.core), startY = oy + h * s * 0.4f, endY = oy + h * s))
        } else {
            drawPath(outer, pal.tip.copy(alpha = 0.18f))
            drawPath(outer, pal.base.copy(alpha = 0.55f), style = Stroke(width = w * 0.045f))
        }
    }
}
