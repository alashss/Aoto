package com.jimz.jimztune.ui.social

import androidx.compose.ui.geometry.Offset
import androidx.compose.ui.graphics.Brush
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.graphics.Path
import androidx.compose.ui.graphics.StrokeCap
import androidx.compose.ui.graphics.StrokeJoin
import androidx.compose.ui.graphics.drawscope.DrawScope
import androidx.compose.ui.graphics.drawscope.Stroke
import androidx.compose.ui.graphics.drawscope.rotate
import androidx.compose.ui.unit.dp
import kotlin.math.PI
import kotlin.math.abs
import kotlin.math.cos
import kotlin.math.sin
import kotlin.math.sqrt

/** Efek legendaris dan mythic yang punya adegan pembuka saat profil dibuka. */
internal val HeroEffects = setOf("fx_demon", "fx_sparkle", "fx_matrix", "fx_frost")

private const val TAU = (2 * PI).toFloat()
private fun hr(i: Int, k: Int): Float { val x = sin((i * 12.9898f + k * 78.233f).toDouble()) * 43758.5453; return (x - kotlin.math.floor(x)).toFloat() }
private fun ease(x: Float): Float { val c = x.coerceIn(0f, 1f); return c * c * (3f - 2f * c) }

/** [p] 0..1 selama adegan pembuka (sekitar 4 detik). Semuanya hilang menjelang p = 1. */
internal fun DrawScope.drawHeroIntro(effect: String, p: Float) {
    if (effect == "fx_demon") { drawDragonHero(p); return }
    val fade = (1f - ((p - 0.82f) / 0.18f).coerceIn(0f, 1f))
    if (fade <= 0f) return
    when (effect) {
        "fx_sparkle" -> goldComets(p, fade)
        "fx_matrix" -> matrixScan(p, fade)
        "fx_frost" -> frostBloom(p, fade)
    }
}

// ------------------------------------------------------------------------------------ Kilau Emas

private fun DrawScope.goldComets(p: Float, fade: Float) {
    val w = size.width; val h = size.height; val dp = 1.dp.toPx()
    for (k in 0 until 5) {
        val local = ((p * 1.25f) - k * 0.12f).coerceIn(0f, 1f)
        if (local <= 0f || local >= 1f) continue
        val sx = w * (0.1f + 0.22f * k); val sy = -40f * dp
        val ex = sx + w * 0.5f; val ey = h * (0.55f + 0.1f * hr(k, 3))
        val x = sx + (ex - sx) * local; val y = sy + (ey - sy) * local
        val a = (1f - local) * fade
        val tail = Brush.linearGradient(listOf(Color.Transparent, GoldLight.copy(alpha = a)), Offset(x - (ex - sx) * 0.22f, y - (ey - sy) * 0.22f), Offset(x, y))
        drawLine(tail, Offset(x - (ex - sx) * 0.22f, y - (ey - sy) * 0.22f), Offset(x, y), 5f * dp, StrokeCap.Round)
        drawCircle(Color.White.copy(alpha = a), 5f * dp, Offset(x, y))
        drawCircle(Gold.copy(alpha = 0.5f * a), 14f * dp, Offset(x, y))
        if (local > 0.82f) { // ledakan kilau di ujung
            val b = (local - 0.82f) / 0.18f
            for (i in 0 until 12) {
                val ang = TAU * i / 12f
                drawLine(GoldLight.copy(alpha = (1f - b) * fade), Offset(x + cos(ang) * 6f * dp, y + sin(ang) * 6f * dp), Offset(x + cos(ang) * (10f + 46f * b) * dp, y + sin(ang) * (10f + 46f * b) * dp), 2f * dp, StrokeCap.Round)
            }
        }
    }
    // sapuan cahaya emas diagonal
    val sweep = ((p - 0.1f) / 0.6f).coerceIn(0f, 1f)
    if (sweep in 0.01f..0.99f) {
        val x = -w * 0.3f + w * 1.6f * sweep
        drawLine(Brush.linearGradient(listOf(Color.Transparent, Gold.copy(alpha = 0.35f * fade), Color.Transparent), Offset(x - 80f * dp, 0f), Offset(x + 80f * dp, 0f)), Offset(x, -20f * dp), Offset(x - h * 0.35f, h + 20f * dp), 160f * dp)
    }
}

// ------------------------------------------------------------------------------------ Hujan kode

private fun DrawScope.matrixScan(p: Float, fade: Float) {
    val w = size.width; val h = size.height; val dp = 1.dp.toPx()
    val y = h * ease(p / 0.6f)
    if (p < 0.65f) {
        drawRect(Brush.verticalGradient(listOf(Color.Transparent, Color(0xFF00FF66).copy(alpha = 0.32f * fade)), startY = (y - 140f * dp).coerceAtLeast(0f), endY = y), Offset(0f, (y - 140f * dp).coerceAtLeast(0f)), androidx.compose.ui.geometry.Size(w, (y - (y - 140f * dp).coerceAtLeast(0f)).coerceAtLeast(0f)))
        drawLine(Color(0xFFB8FFD2).copy(alpha = fade), Offset(0f, y), Offset(w, y), 2.5f * dp)
    }
    // kolom kode padat jatuh sampai bawah
    val cols = 26
    for (c in 0 until cols) {
        val x = w * (c + 0.5f) / cols
        val speed = 0.55f + 0.9f * hr(c, 1)
        val head = ((p * speed * 1.6f) + hr(c, 2)) % 1.3f
        if (head * h > y + 40f * dp && p < 0.6f) continue
        val n = 12 + (hr(c, 3) * 8).toInt()
        for (k in 0 until n) {
            val yy = head * h - k * 11f * dp
            if (yy < 0f || yy > h) continue
            val a = (1f - k / n.toFloat()) * fade
            val col = if (k == 0) Color(0xFFD8FFE6) else Color(0xFF00FF66)
            drawRect(col.copy(alpha = 0.85f * a), Offset(x - 2f * dp, yy), androidx.compose.ui.geometry.Size(4f * dp, 8f * dp))
        }
    }
}

// ------------------------------------------------------------------------------------ Badai es

private fun DrawScope.frostBloom(p: Float, fade: Float) {
    val w = size.width; val h = size.height; val dp = 1.dp.toPx()
    val c = Offset(w / 2f, h * 0.4f)
    val grow = ease(p / 0.4f)
    val r = (40f + 190f * grow) * dp
    // kepingan salju raksasa di tengah
    rotate(p * 70f, c) {
        for (arm in 0 until 6) {
            val ang = TAU * arm / 6f
            val ex = c.x + cos(ang) * r; val ey = c.y + sin(ang) * r
            drawLine(Color(0xFFE8FAFF).copy(alpha = 0.85f * fade), c, Offset(ex, ey), 3.2f * dp, StrokeCap.Round)
            for (b in listOf(0.45f, 0.7f)) {
                val bx = c.x + cos(ang) * r * b; val by = c.y + sin(ang) * r * b
                for (sg in listOf(-1f, 1f)) {
                    val ba = ang + sg * 0.75f
                    drawLine(Color(0xFFBFEFFF).copy(alpha = 0.8f * fade), Offset(bx, by), Offset(bx + cos(ba) * r * 0.22f, by + sin(ba) * r * 0.22f), 2.2f * dp, StrokeCap.Round)
                }
            }
        }
    }
    drawCircle(Brush.radialGradient(listOf(Color(0xFFB7ECFF).copy(alpha = 0.35f * fade), Color.Transparent), c, r * 1.3f), r * 1.3f, c)
    // angin dingin: garis-garis menyapu dari kiri ke kanan
    for (k in 0 until 9) {
        val local = ((p * 1.5f) - k * 0.05f).coerceIn(0f, 1f)
        if (local <= 0f || local >= 1f) continue
        val y = h * (0.1f + 0.1f * k) + sin(local * TAU) * 14f * dp
        val x = -w * 0.2f + w * 1.4f * local
        drawLine(Brush.linearGradient(listOf(Color.Transparent, Color(0xFFDFF6FF).copy(alpha = 0.6f * fade)), Offset(x - 160f * dp, y), Offset(x, y)), Offset(x - 160f * dp, y), Offset(x, y), 2.5f * dp, StrokeCap.Round)
    }
}
