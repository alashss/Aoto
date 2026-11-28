package com.jimz.jimztune.ui.social

import androidx.compose.animation.core.LinearEasing
import androidx.compose.animation.core.RepeatMode
import androidx.compose.animation.core.animateFloat
import androidx.compose.animation.core.infiniteRepeatable
import androidx.compose.animation.core.rememberInfiniteTransition
import androidx.compose.animation.core.tween
import androidx.compose.foundation.Canvas
import androidx.compose.foundation.background
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.BoxScope
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.requiredSize
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.runtime.LaunchedEffect
import androidx.compose.runtime.getValue
import androidx.compose.ui.draw.drawWithContent
import androidx.compose.ui.graphics.graphicsLayer
import androidx.compose.runtime.remember
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clip
import androidx.compose.ui.draw.drawBehind
import androidx.compose.ui.geometry.Offset
import androidx.compose.ui.geometry.Size
import androidx.compose.ui.graphics.Brush
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.graphics.Path
import androidx.compose.ui.graphics.Shape
import androidx.compose.ui.graphics.StrokeCap
import androidx.compose.ui.graphics.StrokeJoin
import androidx.compose.ui.graphics.drawscope.DrawScope
import androidx.compose.ui.graphics.drawscope.Fill
import androidx.compose.ui.graphics.drawscope.Stroke
import androidx.compose.ui.graphics.drawscope.rotate
import androidx.compose.ui.text.TextStyle
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.text.style.TextOverflow
import androidx.compose.ui.unit.Dp
import androidx.compose.ui.unit.TextUnit
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import com.jimz.jimztune.data.social.CosmeticsStore
import com.jimz.jimztune.data.social.Equipped
import kotlin.math.PI
import kotlin.math.abs
import kotlin.math.cos
import kotlin.math.floor
import kotlin.math.sin

// ============================================================ warna & util

val GoldLight = Color(0xFFFFE9A0)
val Gold = Color(0xFFE2B007)
val GoldDark = Color(0xFF9A6B00)

fun rarityColor(r: String): Color = when (r) {
    "mythic" -> Color(0xFFFF2D44)
    "legendary" -> Color(0xFFFFC857)
    "epic" -> Color(0xFFC77DFF)
    "rare" -> Color(0xFF4FC3F7)
    else -> Color(0xFF9AA5B1)
}

fun rarityLabel(r: String): String = when (r) {
    "mythic" -> "Raja Iblis"
    "legendary" -> "Legendaris"
    "epic" -> "Epik"
    "rare" -> "Langka"
    else -> "Umum"
}

fun kindLabel(k: String): String = when (k) {
    "frame" -> "Bingkai"
    "plate" -> "Plat nama"
    "bubble" -> "Gelembung chat"
    else -> "Efek profil"
}

val CosmeticKinds = listOf("frame", "plate", "effect", "bubble")
val RarityOrder = listOf("mythic", "legendary", "epic", "rare", "common")

private fun rnd(i: Int, k: Int): Float {
    val x = sin(i * 127.1 + k * 311.7) * 43758.5453
    return (x - floor(x)).toFloat()
}

/** Jam animasi 0..1 yang berulang tiap 6 detik. */
@Composable
fun rememberCosmeticClock(): Float {
    val t = rememberInfiniteTransition(label = "cosmetic")
    val v by t.animateFloat(0f, 1f, infiniteRepeatable(tween(6000, easing = LinearEasing), RepeatMode.Restart), label = "clock")
    return v
}

private fun DrawScope.star(c: Offset, r: Float, color: Color) {
    val p = Path()
    for (i in 0 until 8) {
        val a = (PI / 4 * i - PI / 2).toFloat()
        val rr = if (i % 2 == 0) r else r * 0.28f
        val x = c.x + cos(a) * rr
        val y = c.y + sin(a) * rr
        if (i == 0) p.moveTo(x, y) else p.lineTo(x, y)
    }
    p.close()
    drawPath(p, color, style = Fill)
}

// ============================================================ bingkai foto profil

/**
 * Foto profil dengan bingkai kosmetik. Kalau [userId] diisi, bingkai orang itu dicari otomatis;
 * kalau [equipped] diisi (pratinjau), itu yang dipakai.
 */
@Composable
fun FramedAvatar(size: Dp, userId: String? = null, equipped: Equipped? = null, content: @Composable () -> Unit) {
    if (userId != null && equipped == null) LaunchedEffect(userId) { CosmeticsStore.request(userId) }
    val eq = equipped ?: userId?.let { CosmeticsStore.map[it] }
    val frame = eq?.frame
    Box(Modifier.size(size), contentAlignment = Alignment.Center) {
        content()
        if (frame != null) {
            val t = rememberCosmeticClock()
            Canvas(Modifier.requiredSize(size * 1.34f).align(Alignment.Center)) { drawFrame(frame, t) }
        }
    }
}

private fun DrawScope.drawFrame(id: String, t: Float) {
    val c = center
    val ar = size.minDimension / 2f / 1.34f      // jari-jari foto
    val sw = ar * 0.17f
    val rr = ar + sw * 0.15f                      // jari-jari tengah cincin
    when (id) {
        "frame_gold" -> {
            rotate(t * 360f, c) {
                drawCircle(
                    Brush.sweepGradient(0f to GoldLight, 0.25f to Gold, 0.5f to GoldLight, 0.75f to GoldDark, 1f to GoldLight, center = c),
                    radius = rr, style = Stroke(sw)
                )
            }
            drawCircle(Color.White.copy(alpha = 0.35f), radius = rr - sw / 2f, style = Stroke(1.2f))
            // mahkota kecil di atas
            val w = ar * 0.62f
            val baseY = c.y - rr - sw * 0.1f
            val h = ar * 0.42f
            val crown = Path().apply {
                moveTo(c.x - w / 2, baseY)
                lineTo(c.x - w / 2, baseY - h * 0.55f)
                lineTo(c.x - w / 4, baseY - h * 0.25f)
                lineTo(c.x, baseY - h)
                lineTo(c.x + w / 4, baseY - h * 0.25f)
                lineTo(c.x + w / 2, baseY - h * 0.55f)
                lineTo(c.x + w / 2, baseY)
                close()
            }
            drawPath(crown, Brush.verticalGradient(listOf(GoldLight, Gold, GoldDark), startY = baseY - h, endY = baseY))
            drawCircle(Color(0xFFFF4D6D), radius = ar * 0.06f, center = Offset(c.x, baseY - h * 0.45f))
            // kilau berputar
            val a = (t * 2 * PI).toFloat()
            star(Offset(c.x + cos(a) * rr, c.y + sin(a) * rr), sw * 0.9f * (0.6f + 0.4f * sin(t * 12f).let { abs(it) }), Color.White)
        }
        "frame_lace" -> {
            drawCircle(Color.White.copy(alpha = 0.95f), radius = rr, style = Stroke(sw * 0.55f))
            val n = 18
            for (i in 0 until n) {
                val a = (2 * PI * i / n).toFloat()
                drawCircle(Color.White, radius = sw * 0.5f, center = Offset(c.x + cos(a) * (rr + sw * 0.42f), c.y + sin(a) * (rr + sw * 0.42f)))
            }
            drawCircle(Color(0xFFFFD6E7), radius = rr - sw * 0.3f, style = Stroke(1.5f))
            for (i in 0 until n) {
                val a = (2 * PI * i / n + PI / n).toFloat()
                drawCircle(Color(0xFFE8EEF9), radius = sw * 0.2f, center = Offset(c.x + cos(a) * (rr + sw * 0.05f), c.y + sin(a) * (rr + sw * 0.05f)))
            }
        }
        "frame_neon" -> {
            val pulse = 0.55f + 0.45f * abs(sin(t * 2 * PI.toFloat()))
            val cy = Color(0xFF29F0FF)
            for (k in 3 downTo 1) drawCircle(cy.copy(alpha = 0.10f * pulse), radius = rr, style = Stroke(sw * (1f + k * 0.7f)))
            drawCircle(Color(0xFF3D7BFF), radius = rr, style = Stroke(sw * 0.7f))
            drawCircle(cy.copy(alpha = 0.6f + 0.4f * pulse), radius = rr, style = Stroke(sw * 0.28f))
        }
        "frame_flame" -> {
            drawCircle(Brush.sweepGradient(0f to Color(0xFFFFD23F), 0.5f to Color(0xFFFF5A1F), 1f to Color(0xFFFFD23F), center = c), radius = rr, style = Stroke(sw * 0.8f))
            val n = 14
            for (i in 0 until n) {
                val a = (2 * PI * i / n).toFloat()
                val flick = 0.7f + 0.5f * abs(sin((t * 6f + rnd(i, 1) * 6f) * PI.toFloat()))
                val len = sw * 2.3f * flick
                rotate(Math.toDegrees(a.toDouble()).toFloat() + 90f, Offset(c.x + cos(a) * rr, c.y + sin(a) * rr)) {
                    val bx = c.x + cos(a) * rr
                    val by = c.y + sin(a) * rr
                    val p = Path().apply {
                        moveTo(bx - sw * 0.5f, by + sw * 0.3f)
                        quadraticBezierTo(bx - sw * 0.3f, by - len * 0.5f, bx, by - len)
                        quadraticBezierTo(bx + sw * 0.3f, by - len * 0.5f, bx + sw * 0.5f, by + sw * 0.3f)
                        close()
                    }
                    drawPath(p, Brush.verticalGradient(listOf(Color(0xFFFFE27A), Color(0xFFFF5A1F)), startY = by - len, endY = by + sw * 0.3f))
                }
            }
        }
        "frame_frost" -> {
            drawCircle(Brush.sweepGradient(0f to Color(0xFFE3F7FF), 0.5f to Color(0xFF6CC8FF), 1f to Color(0xFFE3F7FF), center = c), radius = rr, style = Stroke(sw * 0.7f))
            val n = 10
            for (i in 0 until n) {
                val a = (2 * PI * i / n).toFloat()
                val len = sw * (if (i % 2 == 0) 2.2f else 1.4f)
                val bx = c.x + cos(a) * (rr + sw * 0.2f)
                val by = c.y + sin(a) * (rr + sw * 0.2f)
                val tx = c.x + cos(a) * (rr + sw * 0.2f + len)
                val ty = c.y + sin(a) * (rr + sw * 0.2f + len)
                val nx = -sin(a) * sw * 0.45f
                val ny = cos(a) * sw * 0.45f
                val p = Path().apply { moveTo(bx + nx, by + ny); lineTo(tx, ty); lineTo(bx - nx, by - ny); close() }
                drawPath(p, Color(0xFFD9F3FF))
            }
            val a = (t * 2 * PI).toFloat()
            star(Offset(c.x + cos(a) * rr, c.y + sin(a) * rr), sw * 0.8f, Color.White)
        }
        "frame_sakura" -> {
            drawCircle(Color(0xFFFFB7D5), radius = rr, style = Stroke(sw * 0.5f))
            val n = 8
            for (i in 0 until n) {
                val a = (2 * PI * i / n).toFloat() + t * 0.6f
                val bx = c.x + cos(a) * rr
                val by = c.y + sin(a) * rr
                for (k in 0 until 5) {
                    val pa = (2 * PI * k / 5).toFloat() + a
                    drawCircle(Color(0xFFFF8FBF), radius = sw * 0.42f, center = Offset(bx + cos(pa) * sw * 0.5f, by + sin(pa) * sw * 0.5f))
                }
                drawCircle(Color(0xFFFFE066), radius = sw * 0.2f, center = Offset(bx, by))
            }
        }
        "frame_shark" -> {
            drawCircle(Brush.sweepGradient(0f to Color(0xFF3A0A12), 0.5f to Color(0xFF9B1B30), 1f to Color(0xFF3A0A12), center = c), radius = rr, style = Stroke(sw * 0.9f))
            val n = 16
            for (i in 0 until n) {
                val a = (2 * PI * i / n).toFloat()
                val ox = c.x + cos(a) * (rr + sw * 0.45f)
                val oy = c.y + sin(a) * (rr + sw * 0.45f)
                val ix = c.x + cos(a) * (rr - sw * 0.9f)
                val iy = c.y + sin(a) * (rr - sw * 0.9f)
                val nx = -sin(a) * sw * 0.42f
                val ny = cos(a) * sw * 0.42f
                drawPath(Path().apply { moveTo(ox + nx, oy + ny); lineTo(ix, iy); lineTo(ox - nx, oy - ny); close() }, Color.White)
            }
        }
        "frame_demon" -> drawDemonFrame(t, c, sw, rr)
        else -> FrameSpecs[id]?.let { drawSpecFrame(it, t, c, ar, sw, rr) }
    }
}

// ---- bingkai berbasis tabel: mode 0 statis, 1 sapuan berputar, 2 denyut bercahaya, 3 busur putus-putus berputar.
// deco 1 bintang mengorbit, 2 hati mengorbit, 3 petir berkedip, 4 titik mengorbit.
private class FrameSpec(val colors: List<Color>, val mode: Int, val deco: Int = 0)

private val FrameSpecs = mapOf(
    "frame_simple" to FrameSpec(listOf(Color.White, Color(0xFFCFD8E3)), 0),
    "frame_pixel" to FrameSpec(listOf(Color(0xFF00E676), Color(0xFF00B0FF), Color(0xFFFFEA00), Color(0xFFFF4081)), 3),
    "frame_heart" to FrameSpec(listOf(Color(0xFFFF5A8A), Color(0xFFFF2D6F)), 2, 2),
    "frame_ocean" to FrameSpec(listOf(Color(0xFF0B3B7A), Color(0xFF29B6F6), Color(0xFFB3E5FC)), 1, 4),
    "frame_toxic" to FrameSpec(listOf(Color(0xFF76FF03), Color(0xFF00C853)), 2, 4),
    "frame_rainbow" to FrameSpec(listOf(Color(0xFFFF5252), Color(0xFFFFAB40), Color(0xFFFFEE58), Color(0xFF66BB6A), Color(0xFF42A5F5), Color(0xFF7E57C2)), 1),
    "frame_emerald" to FrameSpec(listOf(Color(0xFF00695C), Color(0xFF00E676), Color(0xFFB9F6CA)), 2, 1),
    "frame_thunder" to FrameSpec(listOf(Color(0xFFFFEB3B), Color(0xFFFF9800)), 2, 3),
    "frame_aurora" to FrameSpec(listOf(Color(0xFF00E5A0), Color(0xFF00B8D4), Color(0xFF9C6BFF), Color(0xFFFF6FB5)), 1, 1),
    "frame_ruby" to FrameSpec(listOf(Color(0xFF7F0020), Color(0xFFFF1744), Color(0xFFFF8A9B)), 2, 1),
    "frame_cyber" to FrameSpec(listOf(Color(0xFF00F0FF), Color(0xFFFF2BD6)), 3, 4),
    "frame_galaxy" to FrameSpec(listOf(Color(0xFF1A0B4A), Color(0xFF6A3DFF), Color(0xFFD63DFF), Color(0xFF29B6F6)), 1, 1),
    "frame_royal" to FrameSpec(listOf(Color(0xFF4A148C), Color(0xFFFFD54F), Color(0xFF7B1FA2), Color(0xFFFFE9A0)), 1, 1)
)

/** Bingkai raja iblis: cincin api merah-ungu yang berputar, lidah api berkobar, sepasang tanduk melengkung di atas. */
private fun DrawScope.drawDemonFrame(t: Float, c: Offset, sw: Float, rr: Float) {
    val tau = (2 * PI).toFloat()
    // aura gelap berdenyut
    val pulse = 0.6f + 0.4f * abs(sin(t * tau))
    for (k in 3 downTo 1) drawCircle(Color(0xFFB3001B).copy(alpha = 0.10f * pulse), rr, c, style = Stroke(sw * (1.2f + k * 0.9f)))
    // cincin
    val cols = listOf(Color(0xFF1A0208), Color(0xFFE0102A), Color(0xFF7B1FD1), Color(0xFFFF6A3D), Color(0xFF1A0208))
    val sweep = Brush.sweepGradient(*cols.mapIndexed { i, col -> i / 4f to col }.toTypedArray(), center = c)
    rotate(t * 360f, c) { drawCircle(sweep, rr, c, style = Stroke(sw * 0.95f)) }
    drawCircle(Color.White.copy(alpha = 0.12f), rr - sw * 0.35f, c, style = Stroke(sw * 0.12f))
    // lidah api
    val n = 20
    for (i in 0 until n) {
        val a = tau * i / n + t * tau * 0.15f
        val ux = cos(a); val uy = sin(a)
        val tx = -uy; val ty = ux
        val hgt = sw * (1.5f + 1.1f * abs(sin(t * tau * 3f + i * 1.7f)))
        val bx = c.x + ux * (rr + sw * 0.2f); val by = c.y + uy * (rr + sw * 0.2f)
        val half = sw * 0.38f
        val tipX = bx + ux * hgt + tx * sw * 0.25f * sin(t * tau * 4f + i)
        val tipY = by + uy * hgt + ty * sw * 0.25f * sin(t * tau * 4f + i)
        val path = Path().apply {
            moveTo(bx - tx * half, by - ty * half)
            quadraticBezierTo(bx - tx * half * 0.9f + ux * hgt * 0.55f, by - ty * half * 0.9f + uy * hgt * 0.55f, tipX, tipY)
            quadraticBezierTo(bx + tx * half * 0.9f + ux * hgt * 0.55f, by + ty * half * 0.9f + uy * hgt * 0.55f, bx + tx * half, by + ty * half)
            close()
        }
        drawPath(path, Brush.linearGradient(listOf(Color(0xFFFFB34A).copy(alpha = 0.95f), Color(0xFFE0102A).copy(alpha = 0.8f), Color(0xFF3A0A5E).copy(alpha = 0.0f)), Offset(bx, by), Offset(tipX, tipY)))
    }
    // tanduk
    for (sg in listOf(-1f, 1f)) {
        val ang = (Math.toRadians(32.0)).toFloat() * sg
        val bx = c.x + sin(ang) * rr; val by = c.y - cos(ang) * rr
        val tipX = c.x + sg * rr * 0.92f; val tipY = c.y - rr * 1.55f
        val horn = Path().apply {
            moveTo(bx - sg * sw * 0.9f, by + sw * 0.1f)
            quadraticBezierTo(bx + sg * sw * 0.2f, by - rr * 0.55f, tipX, tipY)
            quadraticBezierTo(bx + sg * sw * 1.3f, by - rr * 0.42f, bx + sg * sw * 0.9f, by + sw * 0.35f)
            close()
        }
        drawPath(horn, Brush.linearGradient(listOf(Color(0xFF0B0105), Color(0xFF5E0A18), Color(0xFFFFB199)), Offset(bx, by), Offset(tipX, tipY)))
        drawPath(horn, Color(0xFFFF5A4A).copy(alpha = 0.7f), style = Stroke(sw * 0.1f, cap = StrokeCap.Round, join = StrokeJoin.Round))
    }
    // naga kecil yang mengepak mengelilingi foto
    drawBabyDragon(c, rr + sw * 0.9f, sw * 0.8f, t)
    // percik bara
    for (k in 0 until 6) {
        val p = (t * (0.6f + 0.1f * k) + k / 6f) % 1f
        val a = tau * (k / 6f) + 1f
        drawCircle(Color(0xFFFFC857).copy(alpha = 1f - p), sw * 0.14f, Offset(c.x + cos(a) * (rr + sw * 1.4f * p + sw), c.y + sin(a) * (rr + sw * 1.4f * p + sw) - p * sw * 2f))
    }
}

private fun DrawScope.heart(c: Offset, r: Float, color: Color) {
    drawCircle(color, r * 0.55f, Offset(c.x - r * 0.5f, c.y - r * 0.25f))
    drawCircle(color, r * 0.55f, Offset(c.x + r * 0.5f, c.y - r * 0.25f))
    drawPath(Path().apply { moveTo(c.x - r * 1.02f, c.y - r * 0.05f); lineTo(c.x, c.y + r * 1.1f); lineTo(c.x + r * 1.02f, c.y - r * 0.05f); close() }, color)
}

private fun DrawScope.drawSpecFrame(spec: FrameSpec, t: Float, c: Offset, ar: Float, sw: Float, rr: Float) {
    val cols = spec.colors + spec.colors.first()
    val stops = cols.mapIndexed { i, col -> i / (cols.size - 1f) to col }.toTypedArray()
    val sweep = Brush.sweepGradient(*stops, center = c)
    val tl = Offset(c.x - rr, c.y - rr)
    val sz = Size(rr * 2, rr * 2)
    when (spec.mode) {
        0 -> drawCircle(sweep, rr, style = Stroke(sw * 0.8f))
        1 -> {
            drawCircle(spec.colors[0].copy(alpha = 0.16f), rr, style = Stroke(sw * 2f))
            rotate(t * 360f, c) { drawCircle(sweep, rr, style = Stroke(sw * 0.85f)) }
        }
        2 -> {
            val p = 0.55f + 0.45f * abs(sin(t * 2 * PI.toFloat()))
            for (k in 3 downTo 1) drawCircle(spec.colors.last().copy(alpha = 0.11f * p), rr, style = Stroke(sw * (1f + k * 0.8f)))
            drawCircle(sweep, rr, style = Stroke(sw * 0.8f))
        }
        else -> {
            val n = 20
            val seg = 360f / n
            for (i in 0 until n) drawArc(spec.colors[i % spec.colors.size], i * seg + t * 360f, seg * 0.62f, false, tl, sz, style = Stroke(sw * 0.85f))
        }
    }
    when (spec.deco) {
        1 -> for (k in 0 until 2) {
            val a = ((t + k * 0.5f) * 2 * PI).toFloat()
            star(Offset(c.x + cos(a) * rr, c.y + sin(a) * rr), sw * (0.7f + 0.3f * abs(sin(t * 10f + k))), Color.White)
        }
        2 -> for (k in 0 until 3) {
            val a = ((t + k / 3f) * 2 * PI).toFloat()
            heart(Offset(c.x + cos(a) * (rr + sw * 0.3f), c.y + sin(a) * (rr + sw * 0.3f)), sw * 0.55f, Color(0xFFFF8FB0))
        }
        3 -> if (sin(t * 70f) > -0.15f) for (i in 0 until 6) {
            val a = (2 * PI * i / 6 + 0.4).toFloat()
            val x0 = c.x + cos(a) * rr; val y0 = c.y + sin(a) * rr
            val x1 = c.x + cos(a) * (rr + sw * 1.1f); val y1 = c.y + sin(a) * (rr + sw * 1.1f)
            val x2 = c.x + cos(a + 0.18f) * (rr + sw * 2.1f); val y2 = c.y + sin(a + 0.18f) * (rr + sw * 2.1f)
            drawPath(Path().apply { moveTo(x0, y0); lineTo(x1 + sw * 0.3f, y1 - sw * 0.2f); lineTo(x1 - sw * 0.2f, y1 + sw * 0.2f); lineTo(x2, y2) }, Color.White, style = Stroke(sw * 0.22f, cap = StrokeCap.Round))
        }
        4 -> for (k in 0 until 6) {
            val a = ((t + k / 6f) * 2 * PI).toFloat()
            drawCircle(Color.White.copy(alpha = 0.55f + 0.4f * abs(sin(t * 8f + k))), sw * 0.26f, Offset(c.x + cos(a) * (rr + sw * 0.1f), c.y + sin(a) * (rr + sw * 0.1f)))
        }
    }
}

// ============================================================ plat nama

/** Latar plat nama (seperti banner di daftar anggota). Kalau [plate] null, hanya isi biasa. */
@Composable
fun PlateBox(plate: String?, modifier: Modifier = Modifier, shape: Shape = RoundedCornerShape(16.dp), content: @Composable BoxScope.() -> Unit) {
    if (plate == null) {
        Box(modifier, content = content)
        return
    }
    val t = rememberCosmeticClock()
    Box(modifier.clip(shape).drawBehind { drawPlate(plate, t) }, content = content)
}

private fun DrawScope.drawPlate(id: String, t: Float) {
    val w = size.width
    val h = size.height
    when (id) {
        "plate_gold" -> {
            drawRect(Brush.horizontalGradient(listOf(Color(0xFF3B2A06), Color(0xFF8A6410), Color(0xFFE2B007))))
            val x = (t * 1.6f - 0.3f) * w
            drawRect(Brush.horizontalGradient(listOf(Color.Transparent, Color.White.copy(alpha = 0.28f), Color.Transparent), startX = x - w * 0.15f, endX = x + w * 0.15f))
            for (i in 0 until 4) drawLine(GoldLight.copy(alpha = 0.35f), Offset(w * (0.62f + i * 0.09f), h), Offset(w * (0.70f + i * 0.09f), 0f), 2f)
        }
        "plate_shark" -> {
            drawRect(Brush.horizontalGradient(listOf(Color(0xFF1D0A10), Color(0xFF5C0F1F), Color(0xFF9B1B30))))
            val fin = Path().apply { moveTo(w * 0.72f, h); lineTo(w * 0.86f, h * 0.1f); lineTo(w * 0.94f, h); close() }
            drawPath(fin, Color(0xFF2A2F3A))
            val fin2 = Path().apply { moveTo(w * 0.84f, h); lineTo(w * 0.95f, h * 0.35f); lineTo(w * 1.02f, h); close() }
            drawPath(fin2, Color(0xFF4A5060))
            for (i in 0 until 6) {
                val y = ((t * (0.6f + rnd(i, 4)) + rnd(i, 5)) % 1f)
                drawCircle(Color.White.copy(alpha = 0.25f * (1f - y)), radius = h * (0.05f + 0.05f * rnd(i, 6)), center = Offset(w * (0.5f + 0.4f * rnd(i, 7)), h * (1f - y)), style = Stroke(1.5f))
            }
        }
        "plate_night" -> {
            drawRect(Brush.horizontalGradient(listOf(Color(0xFF080B24), Color(0xFF1B1F5C), Color(0xFF3B2A7A))))
            for (i in 0 until 14) {
                val a = 0.3f + 0.7f * abs(sin((t * 2f + rnd(i, 1)) * PI.toFloat()))
                star(Offset(w * rnd(i, 2), h * rnd(i, 3)), h * (0.06f + 0.08f * rnd(i, 4)), Color.White.copy(alpha = a))
            }
        }
        "plate_wave" -> {
            drawRect(Brush.horizontalGradient(listOf(Color(0xFF0B3B7A), Color(0xFF1E88E5), Color(0xFF4FC3F7))))
            for (k in 0 until 2) {
                val p = Path()
                p.moveTo(0f, h)
                var x = 0f
                while (x <= w) {
                    p.lineTo(x, h * (0.7f - 0.1f * k) + sin((x / w * 4f + t * 2f * (if (k == 0) 1f else -1f)) * PI.toFloat()) * h * 0.08f)
                    x += 6f
                }
                p.lineTo(w, h); p.close()
                drawPath(p, Color.White.copy(alpha = 0.16f))
            }
        }
        "plate_sunset" -> {
            drawRect(Brush.horizontalGradient(listOf(Color(0xFF5B2A86), Color(0xFFE5547A), Color(0xFFFFA14A))))
            drawCircle(Color(0xFFFFE08A).copy(alpha = 0.9f), radius = h * 0.55f, center = Offset(w * 0.9f, h * (0.85f - 0.05f * sin(t * 2 * PI.toFloat()))))
        }
        else -> PlateSpecs[id]?.let { drawSpecPlate(it, t) }
    }
}

// deco 0 kilau saja, 1 bintang, 2 gelembung naik, 3 garis miring, 4 bara naik, 5 kelopak melayang
private class PlateSpec(val colors: List<Color>, val deco: Int = 0)

private val PlateSpecs = mapOf(
    "plate_mono" to PlateSpec(listOf(Color(0xFF23262D), Color(0xFF383C46), Color(0xFF4B505C))),
    "plate_forest" to PlateSpec(listOf(Color(0xFF0B2E22), Color(0xFF1E7B4F), Color(0xFF7BD389)), 5),
    "plate_candy" to PlateSpec(listOf(Color(0xFFFF8FB8), Color(0xFFFFC2A8), Color(0xFFA8F0D8)), 2),
    "plate_rose" to PlateSpec(listOf(Color(0xFF4A0A1E), Color(0xFFB3123F), Color(0xFFFF8FAF)), 5),
    "plate_ice" to PlateSpec(listOf(Color(0xFF0C2D5C), Color(0xFF3F8FE0), Color(0xFFD9F3FF)), 1),
    "plate_lava" to PlateSpec(listOf(Color(0xFF1A0505), Color(0xFFB3200D), Color(0xFFFF9F1A)), 4),
    "plate_aurora" to PlateSpec(listOf(Color(0xFF06222B), Color(0xFF12A88A), Color(0xFF8A5CFF)), 1),
    "plate_cyber" to PlateSpec(listOf(Color(0xFF14082E), Color(0xFFB0189E), Color(0xFF14D6F0)), 3),
    "plate_royal" to PlateSpec(listOf(Color(0xFF2A0A4A), Color(0xFF7B2FBF), Color(0xFFE2B007)), 1),
    "plate_demon" to PlateSpec(listOf(Color(0xFF0A0106), Color(0xFF6E0A1A), Color(0xFFD4142B), Color(0xFF3A0A5E)), 6)
)

private fun DrawScope.drawSpecPlate(spec: PlateSpec, t: Float) {
    val w = size.width
    val h = size.height
    drawRect(Brush.horizontalGradient(spec.colors))
    when (spec.deco) {
        1 -> for (i in 0 until 12) star(Offset(w * rnd(i, 2), h * rnd(i, 3)), h * (0.05f + 0.07f * rnd(i, 4)), Color.White.copy(alpha = 0.3f + 0.7f * abs(sin((t * 2f + rnd(i, 1)) * PI.toFloat()))))
        2 -> for (i in 0 until 7) {
            val y = (t * (0.5f + rnd(i, 4)) + rnd(i, 5)) % 1f
            drawCircle(Color.White.copy(alpha = 0.4f * (1f - y)), h * (0.06f + 0.07f * rnd(i, 6)), Offset(w * rnd(i, 7), h * (1f - y)), style = Stroke(1.5f))
        }
        3 -> for (i in 0 until 6) {
            val x = ((t + i / 6f) % 1f) * w * 1.4f - w * 0.2f
            drawLine(Color.White.copy(alpha = 0.22f), Offset(x, h), Offset(x + h * 0.7f, 0f), 2.5f)
        }
        4 -> for (i in 0 until 10) {
            val y = (t * (0.5f + rnd(i, 4)) + rnd(i, 5)) % 1f
            drawCircle(Color(0xFFFFC857).copy(alpha = 1f - y), 1.5f + 2.5f * rnd(i, 6), Offset(w * rnd(i, 7), h * (1f - y)))
        }
        6 -> {
            for (i in 0 until 14) {
                val y = (t * (0.5f + rnd(i, 4)) + rnd(i, 5)) % 1f
                drawCircle(Color(0xFFFF8A3D).copy(alpha = 1f - y), 1.4f + 2.4f * rnd(i, 6), Offset(w * rnd(i, 7), h * (1f - y)))
            }
            // retakan petir ungu yang berkedip
            if (sin(t * 55f) > 0.35f) {
                var x = w * (0.2f + 0.6f * ((t * 7f) % 1f))
                var y = 0f
                val path = Path().apply { moveTo(x, y) }
                for (k in 1..5) { x += (rnd(k, 8) - 0.5f) * h * 0.9f; y = h * k / 5f; path.lineTo(x, y) }
                drawPath(path, Color(0xFFC58BFF).copy(alpha = 0.9f), style = Stroke(1.8f, cap = StrokeCap.Round))
            }
        }
        5 -> for (i in 0 until 8) {
            val y = (t * (0.3f + 0.4f * rnd(i, 1)) + rnd(i, 2)) % 1f
            drawOval(Color.White.copy(alpha = 0.5f * (1f - y)), Offset(w * rnd(i, 3) + sin((y * 3f + i) * PI.toFloat()) * 8f, h * y), Size(h * 0.2f, h * 0.12f))
        }
    }
    val x = ((t * 1.4f) % 1.4f - 0.2f) * w
    drawRect(Brush.horizontalGradient(listOf(Color.Transparent, Color.White.copy(alpha = 0.16f), Color.Transparent), startX = x - w * 0.12f, endX = x + w * 0.12f))
}

// ============================================================ efek profil

/** Partikel melayang di atas kartu profil. Tidak menangkap sentuhan. */
@Composable
fun ProfileEffect(effect: String?, modifier: Modifier = Modifier, entrance: Boolean = false) {
    if (effect == null) return
    val t = rememberCosmeticClock()
    // Saat profil orang dibuka: gelombang kejut berwarna dari atas, lalu partikel muncul perlahan.
    val intro = remember(effect, entrance) { androidx.compose.animation.core.Animatable(if (entrance) 0f else 1f) }
    LaunchedEffect(effect, entrance) { if (entrance) intro.animateTo(1f, tween(1700, easing = androidx.compose.animation.core.FastOutSlowInEasing)) }
    // Umur efek (0 -> 1 selama 6 detik): di awal efek penuh + pinggiran layar berkilau, lalu menipis jadi sisa partikel jarang.
    val age = remember(effect, entrance) { androidx.compose.animation.core.Animatable(if (entrance) 0f else 1f) }
    LaunchedEffect(effect, entrance) { if (entrance) age.animateTo(1f, tween(6000, easing = androidx.compose.animation.core.LinearEasing)) }
    // Adegan pembuka khusus (naga, komet, pemindai, badai) untuk efek legendary & mythic, ~4 detik lalu hilang.
    val hero = remember(effect, entrance) { androidx.compose.animation.core.Animatable(if (entrance && effect in HeroEffects) 0f else 1f) }
    LaunchedEffect(effect, entrance) { if (entrance && effect in HeroEffects) hero.animateTo(1f, tween(if (effect == "fx_demon") 9000 else 4200, easing = androidx.compose.animation.core.LinearEasing)) }
    Canvas(modifier.fillMaxSize().graphicsLayer { alpha = (intro.value * 2.5f).coerceIn(0f, 1f) }) {
        val w = size.width
        val h = size.height
        val a01 = age.value
        // kerapatan partikel: 1 di awal, turun mulus ke 0,3 (sisa)
        val dens = if (!entrance) 1f else (1f - 0.7f * ((a01 - 0.35f) / 0.4f).coerceIn(0f, 1f))
        // pinggiran layar menyala sesuai warna efek, lalu hilang
        if (entrance && a01 < 0.7f) {
            val va = (1f - a01 / 0.7f)
            val ec = effectColor(effect)
            drawRect(Brush.radialGradient(listOf(Color.Transparent, ec.copy(alpha = 0.28f * va)), center = Offset(w / 2f, h / 2f), radius = maxOf(w, h) * 0.75f))
            if (effect == "fx_frost") {
                // kristal es merambat dari tepi
                val reach = (a01 / 0.25f).coerceIn(0f, 1f) * 60.dp.toPx() * va
                for (k in 0 until 18) {
                    val x = w * (k + 0.5f) / 18f
                    val l = reach * (0.4f + 0.6f * rnd(k, 31))
                    val tri = Path().apply { moveTo(x - 9.dp.toPx(), 0f); lineTo(x, l); lineTo(x + 9.dp.toPx(), 0f); close() }
                    drawPath(tri, Color(0xFFDFF6FF).copy(alpha = 0.55f * va))
                    val tri2 = Path().apply { moveTo(x - 9.dp.toPx(), h); lineTo(x, h - l); lineTo(x + 9.dp.toPx(), h); close() }
                    drawPath(tri2, Color(0xFFDFF6FF).copy(alpha = 0.45f * va))
                }
                for (k in 0 until 26) {
                    val y = h * (k + 0.5f) / 26f
                    val l = reach * (0.3f + 0.7f * rnd(k, 32))
                    drawPath(Path().apply { moveTo(0f, y - 8.dp.toPx()); lineTo(l, y); lineTo(0f, y + 8.dp.toPx()); close() }, Color(0xFFCFF1FF).copy(alpha = 0.5f * va))
                    drawPath(Path().apply { moveTo(w, y - 8.dp.toPx()); lineTo(w - l, y); lineTo(w, y + 8.dp.toPx()); close() }, Color(0xFFCFF1FF).copy(alpha = 0.5f * va))
                }
            }
        }
        if (entrance && intro.value < 1f) {
            val col = effectColor(effect)
            val p = intro.value
            for (k in 0 until 2) {
                val q = (p - k * 0.12f).coerceIn(0f, 1f)
                drawCircle(col.copy(alpha = (1f - q) * 0.55f), radius = q * w * 0.9f, center = Offset(w / 2f, h * 0.3f), style = Stroke(3.dp.toPx() * (1f - q) + 1f))
            }
        }
        when (effect) {
            "fx_sparkle" -> for (i in 0 until ((22 * dens).toInt().coerceAtLeast(2))) {
                val a = abs(sin((t * 2f + rnd(i, 1)) * PI.toFloat()))
                star(Offset(w * rnd(i, 2), h * rnd(i, 3)), 4.dp.toPx() + 8.dp.toPx() * rnd(i, 4) * a, (if (i % 3 == 0) Color.White else Gold).copy(alpha = a))
            }
            "fx_bubbles" -> for (i in 0 until ((16 * dens).toInt().coerceAtLeast(2))) {
                val y = (t * (0.5f + rnd(i, 1)) + rnd(i, 2)) % 1f
                val r = 4.dp.toPx() + 10.dp.toPx() * rnd(i, 3)
                val x = w * rnd(i, 4) + sin((y * 6f + i) * PI.toFloat()) * 8.dp.toPx()
                drawCircle(Color(0xFF9BE7FF).copy(alpha = 0.55f * (1f - y)), r, Offset(x, h * (1f - y)), style = Stroke(1.6.dp.toPx()))
                drawCircle(Color.White.copy(alpha = 0.35f * (1f - y)), r * 0.25f, Offset(x - r * 0.3f, h * (1f - y) - r * 0.3f))
            }
            "fx_petals" -> for (i in 0 until ((14 * dens).toInt().coerceAtLeast(2))) {
                val y = (t * (0.35f + 0.5f * rnd(i, 1)) + rnd(i, 2)) % 1f
                val x = w * rnd(i, 3) + sin((y * 3f + rnd(i, 5)) * 2f * PI.toFloat()) * 18.dp.toPx()
                rotate(y * 360f * (if (i % 2 == 0) 1f else -1f), Offset(x, h * y)) {
                    drawOval(Color(0xFFFF9FC6).copy(alpha = 0.85f), Offset(x - 5.dp.toPx(), h * y - 3.dp.toPx()), Size(10.dp.toPx(), 6.dp.toPx()))
                }
            }
            "fx_snow" -> for (i in 0 until ((26 * dens).toInt().coerceAtLeast(2))) {
                val y = (t * (0.25f + 0.5f * rnd(i, 1)) + rnd(i, 2)) % 1f
                val x = w * rnd(i, 3) + sin((y * 3f + i) * PI.toFloat()) * 10.dp.toPx()
                drawCircle(Color.White.copy(alpha = 0.85f), 1.5.dp.toPx() + 2.5.dp.toPx() * rnd(i, 4), Offset(x, h * y))
            }
            "fx_rain" -> for (i in 0 until ((30 * dens).toInt().coerceAtLeast(2))) {
                val y = (t * (1.6f + 1.2f * rnd(i, 1)) * 2f + rnd(i, 2)) % 1f
                val x = w * rnd(i, 3) + y * 14.dp.toPx()
                drawLine(Color(0xFF8CC8FF).copy(alpha = 0.55f), Offset(x, h * y), Offset(x - 3.dp.toPx(), h * y + 12.dp.toPx()), 1.4.dp.toPx())
            }
            "fx_stars" -> for (i in 0 until ((26 * dens).toInt().coerceAtLeast(2))) {
                val a = abs(sin((t * 2f + rnd(i, 1)) * PI.toFloat()))
                star(Offset(w * rnd(i, 2), h * rnd(i, 3)), 3.dp.toPx() + 6.dp.toPx() * rnd(i, 4) * a, (if (i % 2 == 0) Color.White else Color(0xFFB3D4FF)).copy(alpha = 0.3f + 0.7f * a))
            }
            "fx_hearts" -> for (i in 0 until ((12 * dens).toInt().coerceAtLeast(2))) {
                val y = (t * (0.3f + 0.5f * rnd(i, 1)) + rnd(i, 2)) % 1f
                val x = w * rnd(i, 3) + sin((y * 3f + i) * PI.toFloat()) * 12.dp.toPx()
                heart(Offset(x, h * (1f - y)), (4.dp.toPx() + 5.dp.toPx() * rnd(i, 4)), Color(0xFFFF5A8A).copy(alpha = 0.8f * (1f - y * 0.7f)))
            }
            "fx_fireflies" -> for (i in 0 until ((14 * dens).toInt().coerceAtLeast(2))) {
                val a = (t * 2f * PI + rnd(i, 1) * 6.28f).toFloat()
                val x = w * rnd(i, 2) + cos(a * (1 + i % 3)) * 16.dp.toPx()
                val y = h * rnd(i, 3) + sin(a * (1 + (i + 1) % 3)) * 12.dp.toPx()
                val g = 0.35f + 0.65f * abs(sin((t * 3f + rnd(i, 4)) * PI.toFloat()))
                drawCircle(Brush.radialGradient(listOf(Color(0xFFD7FF4D).copy(alpha = 0.7f * g), Color.Transparent), Offset(x, y), 9.dp.toPx()), 9.dp.toPx(), Offset(x, y))
                drawCircle(Color(0xFFF4FFB0).copy(alpha = g), 1.8.dp.toPx(), Offset(x, y))
            }
            "fx_confetti" -> for (i in 0 until ((22 * dens).toInt().coerceAtLeast(2))) {
                val y = (t * (0.4f + 0.6f * rnd(i, 1)) + rnd(i, 2)) % 1f
                val x = w * rnd(i, 3) + sin((y * 4f + i) * PI.toFloat()) * 10.dp.toPx()
                val col = listOf(Color(0xFFFFD23F), Color(0xFFFF5A8A), Color(0xFF4FC3F7), Color(0xFF8CFF6B), Color(0xFFC77DFF))[i % 5]
                rotate(y * 540f + i * 30f, Offset(x, h * y)) { drawRect(col, Offset(x - 3.dp.toPx(), h * y - 2.dp.toPx()), Size(6.dp.toPx(), 4.dp.toPx())) }
            }
            "fx_frost" -> {
                // serpihan es jatuh berputar
                for (i in 0 until (30 * dens).toInt().coerceAtLeast(3)) {
                    val y = (t * (0.2f + 0.45f * rnd(i, 1)) + rnd(i, 2)) % 1f
                    val x = w * rnd(i, 3) + sin((y * 2.5f + i) * PI.toFloat()) * 14.dp.toPx()
                    val r = 2.dp.toPx() + 4.5.dp.toPx() * rnd(i, 4)
                    rotate(y * 540f * (if (i % 2 == 0) 1f else -1f), Offset(x, h * y)) {
                        val d = Path().apply { moveTo(x, h * y - r * 1.5f); lineTo(x + r, h * y); lineTo(x, h * y + r * 1.5f); lineTo(x - r, h * y); close() }
                        drawPath(d, Color(0xFFE8F8FF).copy(alpha = 0.85f))
                        drawPath(d, Color(0xFF7FD3FF).copy(alpha = 0.9f), style = Stroke(0.8.dp.toPx()))
                    }
                }
                // kilau biru es
                for (i in 0 until (8 * dens).toInt().coerceAtLeast(2)) {
                    val a = abs(sin((t * 2f + rnd(i, 7)) * PI.toFloat()))
                    star(Offset(w * rnd(i, 8), h * rnd(i, 9)), 3.dp.toPx() + 6.dp.toPx() * a, Color(0xFFB7ECFF).copy(alpha = a * 0.9f))
                }
            }
            "fx_demon" -> {
                // asap gelap naik
                for (i in 0 until ((7 * dens).toInt().coerceAtLeast(2))) {
                    val y = (t * (0.18f + 0.2f * rnd(i, 1)) + rnd(i, 2)) % 1f
                    drawCircle(Color(0xFF2A0A3E).copy(alpha = 0.30f * (1f - y)), 26.dp.toPx() * (0.6f + y), Offset(w * rnd(i, 3) + sin((y * 2f + i) * PI.toFloat()) * 14.dp.toPx(), h * (1.05f - y)))
                }
                // bara
                for (i in 0 until ((24 * dens).toInt().coerceAtLeast(2))) {
                    val y = (t * (0.35f + 0.6f * rnd(i, 4)) + rnd(i, 5)) % 1f
                    val x = w * rnd(i, 6) + sin((y * 3f + i) * PI.toFloat()) * 8.dp.toPx()
                    drawCircle(Color(0xFFFF8A3D).copy(alpha = 0.9f * (1f - y)), 1.2.dp.toPx() + 2.2.dp.toPx() * rnd(i, 7), Offset(x, h * (1f - y)))
                }
                // kilat ungu sesekali
                if (sin(t * 2f * PI.toFloat() * 3f) > 0.93f) {
                    var x = w * (0.25f + 0.5f * ((t * 5f) % 1f))
                    val path = Path().apply { moveTo(x, 0f) }
                    for (k in 1..6) { x += (rnd(k, 9) - 0.5f) * w * 0.18f; path.lineTo(x, h * 0.5f * k / 6f) }
                    drawPath(path, Color(0xFFC58BFF), style = Stroke(2.dp.toPx(), cap = StrokeCap.Round))
                }
            }
            "fx_matrix" -> for (i in 0 until ((16 * dens).toInt().coerceAtLeast(2))) {
                val head = (t * (0.7f + 0.8f * rnd(i, 1)) * 2f + rnd(i, 2)) % 1.4f
                val x = w * (i + 0.5f) / 16f
                for (k in 0 until 9) {
                    val y = (head - k * 0.045f) * h
                    if (y in 0f..h) drawRect(Color(0xFF00FF66).copy(alpha = (1f - k / 9f) * 0.8f), Offset(x, y), Size(3.dp.toPx(), 7.dp.toPx()))
                }
            }
            "fx_embers" -> for (i in 0 until ((20 * dens).toInt().coerceAtLeast(2))) {
                val y = (t * (0.4f + 0.8f * rnd(i, 1)) + rnd(i, 2)) % 1f
                val x = w * rnd(i, 3) + sin((y * 4f + i) * PI.toFloat()) * 10.dp.toPx()
                drawCircle(Color(0xFFFF8A3D).copy(alpha = 1f - y), 1.5.dp.toPx() + 2.5.dp.toPx() * rnd(i, 4), Offset(x, h * (1f - y)))
            }
        }
        if (entrance && effect in HeroEffects && hero.value < 1f) drawHeroIntro(effect, hero.value)
    }
}

private fun effectColor(id: String): Color = when (id) {
    "fx_sparkle" -> Gold
    "fx_bubbles" -> Color(0xFF9BE7FF)
    "fx_petals" -> Color(0xFFFF9FC6)
    "fx_embers" -> Color(0xFFFF8A3D)
    "fx_snow" -> Color.White
    "fx_rain" -> Color(0xFF6CB4FF)
    "fx_stars" -> Color(0xFFB3D4FF)
    "fx_hearts" -> Color(0xFFFF5A8A)
    "fx_fireflies" -> Color(0xFFD7FF4D)
    "fx_confetti" -> Color(0xFFFFD23F)
    "fx_demon" -> Color(0xFFE0102A)
    "fx_frost" -> Color(0xFF9FE3FF)
    else -> Color(0xFF00FF66)
}

// ============================================================ gelembung chat

/** mode: 0 statis, 1 kilau menyapu, 2 tepi berdenyut, 3 gradasi bergeser. */
class BubbleSpec(val colors: List<Color>, val text: Color = Color.White, val border: Color? = null, val anim: Int = 0)

val BubbleSpecs = mapOf(
    "bubble_mono" to BubbleSpec(listOf(Color(0xFF3A3F4A), Color(0xFF2B2F38)), border = Color.White.copy(alpha = 0.14f)),
    "bubble_ocean" to BubbleSpec(listOf(Color(0xFF1565C0), Color(0xFF29B6F6))),
    "bubble_mint" to BubbleSpec(listOf(Color(0xFF00897B), Color(0xFF4DD0A1))),
    "bubble_sunset" to BubbleSpec(listOf(Color(0xFFFF6B6B), Color(0xFFFFA45B))),
    "bubble_candy" to BubbleSpec(listOf(Color(0xFFFF7EB6), Color(0xFFB388FF))),
    "bubble_midnight" to BubbleSpec(listOf(Color(0xFF141E30), Color(0xFF243B55)), border = Color(0x5586A8FF)),
    "bubble_sakura" to BubbleSpec(listOf(Color(0xFFFFC2DA), Color(0xFFFF9EC4)), text = Color(0xFF4A1530)),
    "bubble_neon" to BubbleSpec(listOf(Color(0xFF0A0F2A), Color(0xFF14153D)), border = Color(0xFF29F0FF), anim = 2),
    "bubble_lava" to BubbleSpec(listOf(Color(0xFF7A0F0F), Color(0xFFFF5A1F)), anim = 1),
    "bubble_aurora" to BubbleSpec(listOf(Color(0xFF00C9A7), Color(0xFF845EC2), Color(0xFFFF6F91)), anim = 3),
    "bubble_galaxy" to BubbleSpec(listOf(Color(0xFF0B0C2A), Color(0xFF4B2A9A), Color(0xFFB5179E)), border = Color(0x66FFFFFF), anim = 3),
    "bubble_demon" to BubbleSpec(listOf(Color(0xFF14020A), Color(0xFF7A0A1E), Color(0xFFD4142B)), text = Color(0xFFFFE3D0), border = Color(0xFFFF3B30), anim = 5),
    "bubble_gold" to BubbleSpec(listOf(Color(0xFF8A6410), Color(0xFFE2B007), Color(0xFFFFE9A0)), text = Color(0xFF2B1D00), anim = 1)
)

/** Latar gelembung chat kosmetik. [id] null / tidak dikenal: pakai [fallback] biasa. */
@Composable
fun CosmeticBubble(id: String?, shape: Shape, modifier: Modifier = Modifier, fallback: Brush? = null, content: @Composable BoxScope.() -> Unit) {
    val spec = id?.let { BubbleSpecs[it] }
    if (spec != null && spec.anim == 5) { DemonBubble(shape, modifier, spec, content); return }
    if (spec == null) {
        Box(modifier.clip(shape).then(if (fallback != null) Modifier.background(fallback) else Modifier), content = content)
        return
    }
    val t = if (spec.anim != 0) rememberCosmeticClock() else 0f
    Box(
        modifier.clip(shape).drawWithContent {
            val w = size.width
            val h = size.height
            val brush = if (spec.anim == 3)
                Brush.linearGradient(spec.colors + spec.colors.first(), Offset(w * (t * 2f - 1f), 0f), Offset(w * (t * 2f), h), androidx.compose.ui.graphics.TileMode.Mirror)
            else Brush.linearGradient(spec.colors, Offset(0f, 0f), Offset(w, h))
            drawRect(brush)
            if (spec.anim == 1 || spec.anim == 5) {
                val x = ((t * 1.6f) % 1.6f - 0.3f) * w
                drawRect(Brush.horizontalGradient(listOf(Color.Transparent, Color.White.copy(alpha = 0.30f), Color.Transparent), startX = x - w * 0.15f, endX = x + w * 0.15f))
            }
            drawContent()
            val b = spec.border
            if (b != null) {
                val a = if (spec.anim == 2 || spec.anim == 5) 0.45f + 0.55f * abs(sin(t * 2 * PI.toFloat())) else 1f
                val r = (shape as? RoundedCornerShape)?.let { 22.dp.toPx() } ?: 16.dp.toPx()
                drawRoundRect(b.copy(alpha = b.alpha * a), cornerRadius = androidx.compose.ui.geometry.CornerRadius(r), style = Stroke(1.4.dp.toPx()))
            }
        },
        content = content
    )
}

fun bubbleTextColor(id: String?): Color? = id?.let { BubbleSpecs[it]?.text }

// ============================================================ nama

/** Nama dengan gradasi emas kalau pemiliknya memakai kosmetik emas. */
@Composable
fun CosmeticName(
    text: String, eq: Equipped?, modifier: Modifier = Modifier, color: Color = Color.White,
    fontSize: TextUnit = 14.sp, fontWeight: FontWeight = FontWeight.SemiBold, maxLines: Int = 1
) {
    val style = if (eq?.gold == true)
        TextStyle(brush = Brush.horizontalGradient(listOf(GoldLight, Gold, GoldLight)), fontSize = fontSize, fontWeight = FontWeight.Bold)
    else TextStyle(color = color, fontSize = fontSize, fontWeight = fontWeight)
    Text(text, style = style, maxLines = maxLines, overflow = TextOverflow.Ellipsis, modifier = modifier)
}


/**
 * Gelembung Raja Iblis: gelembung merah-hitam berurat api, dan seekor naga bertengger di tepi atasnya
 * (ekornya menjuntai & berayun, sesekali menyemburkan api kecil). Ruang di atas gelembung disediakan untuk naga.
 */
@Composable
private fun DemonBubble(shape: Shape, modifier: Modifier, spec: BubbleSpec, content: @Composable BoxScope.() -> Unit) {
    val t = rememberCosmeticClock()
    val perch = 26.dp
    Box(
        modifier.drawWithContent {
            drawContent()
            val top = perch.toPx()
            val u = 3.2.dp.toPx()
            val cx = (size.width - 3.9f * u).coerceAtLeast(size.width * 0.55f)
            drawPerchedDragon(Offset(cx, top + 0.5f * u), u, t)
        }
    ) {
        Box(
            Modifier.padding(top = perch).clip(shape).drawWithContent {
                val w = size.width
                val h = size.height
                drawRect(Brush.linearGradient(spec.colors, Offset(0f, 0f), Offset(w, h)))
                // lava bergerak di dasar gelembung
                for (k in 0 until 3) {
                    val x = ((t * 1.2f + k / 3f) % 1f) * w
                    drawCircle(Color(0xFFFF6A3D).copy(alpha = 0.16f), h * 0.9f, Offset(x, h * 1.1f))
                }
                val sx = ((t * 1.6f) % 1.6f - 0.3f) * w
                drawRect(Brush.horizontalGradient(listOf(Color.Transparent, Color.White.copy(alpha = 0.26f), Color.Transparent), startX = sx - w * 0.15f, endX = sx + w * 0.15f))
                drawContent()
                val b = spec.border
                if (b != null) {
                    val a = 0.45f + 0.55f * abs(sin(t * 2 * PI.toFloat()))
                    drawRoundRect(b.copy(alpha = b.alpha * a), cornerRadius = androidx.compose.ui.geometry.CornerRadius(22.dp.toPx()), style = Stroke(1.6.dp.toPx()))
                }
            },
            content = content
        )
    }
}
