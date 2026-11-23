package com.jimz.jimztune.ui.social

import androidx.compose.animation.core.LinearEasing
import androidx.compose.animation.core.RepeatMode
import androidx.compose.animation.core.animateFloat
import androidx.compose.animation.core.infiniteRepeatable
import androidx.compose.animation.core.rememberInfiniteTransition
import androidx.compose.animation.core.tween
import androidx.compose.foundation.Canvas
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.BoxScope
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.requiredSize
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.runtime.LaunchedEffect
import androidx.compose.runtime.getValue
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
    "legendary" -> Color(0xFFFFC857)
    "epic" -> Color(0xFFC77DFF)
    "rare" -> Color(0xFF4FC3F7)
    else -> Color(0xFF9AA5B1)
}

fun rarityLabel(r: String): String = when (r) {
    "legendary" -> "Legendaris"
    "epic" -> "Epik"
    "rare" -> "Langka"
    else -> "Umum"
}

fun kindLabel(k: String): String = when (k) {
    "frame" -> "Bingkai"
    "plate" -> "Plat nama"
    else -> "Efek profil"
}

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
    }
}

// ============================================================ efek profil

/** Partikel melayang di atas kartu profil. Tidak menangkap sentuhan. */
@Composable
fun ProfileEffect(effect: String?, modifier: Modifier = Modifier) {
    if (effect == null) return
    val t = rememberCosmeticClock()
    Canvas(modifier.fillMaxSize()) {
        val w = size.width
        val h = size.height
        when (effect) {
            "fx_sparkle" -> for (i in 0 until 22) {
                val a = abs(sin((t * 2f + rnd(i, 1)) * PI.toFloat()))
                star(Offset(w * rnd(i, 2), h * rnd(i, 3)), 4.dp.toPx() + 8.dp.toPx() * rnd(i, 4) * a, (if (i % 3 == 0) Color.White else Gold).copy(alpha = a))
            }
            "fx_bubbles" -> for (i in 0 until 16) {
                val y = (t * (0.5f + rnd(i, 1)) + rnd(i, 2)) % 1f
                val r = 4.dp.toPx() + 10.dp.toPx() * rnd(i, 3)
                val x = w * rnd(i, 4) + sin((y * 6f + i) * PI.toFloat()) * 8.dp.toPx()
                drawCircle(Color(0xFF9BE7FF).copy(alpha = 0.55f * (1f - y)), r, Offset(x, h * (1f - y)), style = Stroke(1.6.dp.toPx()))
                drawCircle(Color.White.copy(alpha = 0.35f * (1f - y)), r * 0.25f, Offset(x - r * 0.3f, h * (1f - y) - r * 0.3f))
            }
            "fx_petals" -> for (i in 0 until 14) {
                val y = (t * (0.35f + 0.5f * rnd(i, 1)) + rnd(i, 2)) % 1f
                val x = w * rnd(i, 3) + sin((y * 3f + rnd(i, 5)) * 2f * PI.toFloat()) * 18.dp.toPx()
                rotate(y * 360f * (if (i % 2 == 0) 1f else -1f), Offset(x, h * y)) {
                    drawOval(Color(0xFFFF9FC6).copy(alpha = 0.85f), Offset(x - 5.dp.toPx(), h * y - 3.dp.toPx()), Size(10.dp.toPx(), 6.dp.toPx()))
                }
            }
            "fx_embers" -> for (i in 0 until 20) {
                val y = (t * (0.4f + 0.8f * rnd(i, 1)) + rnd(i, 2)) % 1f
                val x = w * rnd(i, 3) + sin((y * 4f + i) * PI.toFloat()) * 10.dp.toPx()
                drawCircle(Color(0xFFFF8A3D).copy(alpha = 1f - y), 1.5.dp.toPx() + 2.5.dp.toPx() * rnd(i, 4), Offset(x, h * (1f - y)))
            }
        }
    }
}

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
