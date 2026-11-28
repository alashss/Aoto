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
    val fade = (1f - ((p - 0.82f) / 0.18f).coerceIn(0f, 1f))
    if (fade <= 0f) return
    when (effect) {
        "fx_demon" -> dragonFlight(p, fade)
        "fx_sparkle" -> goldComets(p, fade)
        "fx_matrix" -> matrixScan(p, fade)
        "fx_frost" -> frostBloom(p, fade)
    }
}

// ------------------------------------------------------------------------------------ Naga raja iblis

private fun DrawScope.dragonFlight(p: Float, fade: Float) {
    val w = size.width; val h = size.height
    val dp = 1.dp.toPx()
    // lintasan: masuk dari kiri bawah, melengkung ke kanan atas dengan gelombang
    fun pos(s: Float) = Offset(
        w * (-0.12f + 1.24f * s),
        h * (0.66f - 0.44f * s) + sin(s * PI.toFloat() * 3.2f) * h * 0.075f
    )
    val headS = -0.08f + 1.3f * ease(p / 0.82f).coerceIn(0f, 1f)
    val segs = 46
    val gap = 0.0135f
    fun tan(s: Float): Offset { val a = pos(s + 0.004f); val b = pos(s - 0.004f); val d = a - b; val l = sqrt(d.x * d.x + d.y * d.y).coerceAtLeast(0.001f); return Offset(d.x / l, d.y / l) }
    val flap = sin(p * TAU * 9f)

    // jejak bara di belakang naga
    for (k in 0 until 26) {
        val s = headS - (0.03f + k * 0.022f)
        if (s < -0.1f) continue
        val o = pos(s)
        val drift = (k * 0.9f + 3f) * dp * (1f + hr(k, 1))
        drawCircle(Color(0xFFFF8A3D).copy(alpha = 0.7f * fade * (1f - k / 26f)), (1.3f + 2.2f * hr(k, 2)) * dp, Offset(o.x - drift * 0.4f, o.y + drift * 1.4f))
    }

    // sayap belakang (lebih gelap, lebih kecil) lalu badan lalu sayap depan
    val shoulder = 7
    val shS = headS - shoulder * gap
    fun wing(scale: Float, shade: Float, back: Boolean) {
        val base = pos(shS)
        val u = tan(shS)
        val v = Offset(u.y, -u.x).let { if (it.y > 0f) Offset(-it.x, -it.y) else it } // ke atas layar
        val vv = if (back) Offset(-v.x * 0.55f - u.x * 0.35f, -v.y * 0.55f - u.y * 0.35f) else v
        val L = 150.dp.toPx() * scale
        val f = if (back) -flap * 0.8f else flap
        fun pt(a: Float, b: Float) = Offset(base.x - u.x * L * a + vv.x * L * (b + 0.38f * f), base.y - u.y * L * a + vv.y * L * (b + 0.38f * f))
        val tip = pt(0.55f, 0.95f)
        val path = Path().apply {
            moveTo(base.x, base.y)
            cubicTo(pt(0.05f, 0.45f).x, pt(0.05f, 0.45f).y, pt(0.3f, 0.9f).x, pt(0.3f, 0.9f).y, tip.x, tip.y)
            // tepi belakang bergerigi (jari-jari sayap)
            val e1 = pt(0.85f, 0.62f); val e2 = pt(1.05f, 0.36f); val e3 = pt(1.0f, 0.12f)
            quadraticBezierTo(pt(0.75f, 0.95f).x, pt(0.75f, 0.95f).y, e1.x, e1.y)
            quadraticBezierTo(pt(0.88f, 0.52f).x, pt(0.88f, 0.52f).y, e2.x, e2.y)
            quadraticBezierTo(pt(0.98f, 0.3f).x, pt(0.98f, 0.3f).y, e3.x, e3.y)
            quadraticBezierTo(pt(0.6f, 0.1f).x, pt(0.6f, 0.1f).y, base.x - u.x * L * 0.35f, base.y - u.y * L * 0.35f)
            close()
        }
        drawPath(path, Brush.linearGradient(listOf(Color(0xFF3A0610).copy(alpha = fade), Color(0xFFB3122A).copy(alpha = fade * shade)), base, tip))
        drawPath(path, Color(0xFFFF5A4A).copy(alpha = 0.8f * fade), style = Stroke(1.4f * dp, join = StrokeJoin.Round))
        for (e in listOf(pt(0.85f, 0.62f), pt(1.05f, 0.36f), pt(1.0f, 0.12f))) {
            drawLine(Color(0xFFFF7A5A).copy(alpha = 0.55f * fade), base, e, 1.1f * dp)
        }
    }
    wing(0.78f, 0.7f, true)

    // badan: dari ekor ke kepala, menebal di bahu lalu mengecil ke ekor
    for (k in segs downTo 1) {
        val s = headS - k * gap
        if (s < -0.12f || s > 1.2f) continue
        val a = pos(s + gap); val b = pos(s)
        val thick = (if (k < 9) 8f + k * 1.1f else 18f * (1f - (k - 9) / (segs - 9f)) + 2.5f) * dp
        drawLine(Color(0xFFFF4A2A).copy(alpha = 0.55f * fade), a, b, thick + 5f * dp, StrokeCap.Round)
        drawLine(Color(0xFF16030A).copy(alpha = fade), a, b, thick, StrokeCap.Round)
        drawLine(Color(0xFF7B0F22).copy(alpha = 0.8f * fade), a, b, thick * 0.35f, StrokeCap.Round)
        if (k % 3 == 0 && k > 2) { // duri punggung
            val t = tan(s); val n = Offset(t.y, -t.x).let { if (it.y > 0f) Offset(-it.x, -it.y) else it }
            val tipP = Offset(b.x + n.x * (thick * 0.9f) - t.x * 4f * dp, b.y + n.y * (thick * 0.9f) - t.y * 4f * dp)
            drawPath(Path().apply { moveTo(b.x + n.x * thick * 0.4f - t.x * 3f * dp, b.y + n.y * thick * 0.4f - t.y * 3f * dp); lineTo(tipP.x, tipP.y); lineTo(b.x + n.x * thick * 0.4f + t.x * 3f * dp, b.y + n.y * thick * 0.4f + t.y * 3f * dp); close() },
                Color(0xFFFF5A4A).copy(alpha = fade))
        }
    }
    wing(1f, 1f, false)

    // kepala + tanduk + mata
    val hp = pos(headS)
    val hu = tan(headS)
    val hv = Offset(hu.y, -hu.x).let { if (it.y > 0f) Offset(-it.x, -it.y) else it }
    val snout = Offset(hp.x + hu.x * 34f * dp, hp.y + hu.y * 34f * dp)
    drawPath(Path().apply {
        moveTo(hp.x + hv.x * 11f * dp, hp.y + hv.y * 11f * dp)
        lineTo(snout.x, snout.y)
        lineTo(hp.x - hv.x * 11f * dp, hp.y - hv.y * 11f * dp)
        lineTo(hp.x - hu.x * 8f * dp, hp.y - hu.y * 8f * dp)
        close()
    }, Color(0xFF16030A).copy(alpha = fade))
    drawPath(Path().apply {
        moveTo(hp.x + hv.x * 11f * dp, hp.y + hv.y * 11f * dp); lineTo(snout.x, snout.y)
        lineTo(hp.x - hv.x * 11f * dp, hp.y - hv.y * 11f * dp)
    }, Color(0xFFFF4A2A).copy(alpha = fade), style = Stroke(1.6f * dp, join = StrokeJoin.Round))
    for (sg in listOf(1f, -1f)) { // tanduk
        val bx = hp.x + hv.x * 9f * dp * sg - hu.x * 4f * dp; val by = hp.y + hv.y * 9f * dp * sg - hu.y * 4f * dp
        drawLine(Color(0xFFFFB199).copy(alpha = fade), Offset(bx, by), Offset(bx - hu.x * 22f * dp + hv.x * sg * 12f * dp, by - hu.y * 22f * dp + hv.y * sg * 12f * dp), 3.2f * dp, StrokeCap.Round)
    }
    drawCircle(Color(0xFFFFD23F).copy(alpha = fade), 3.4f * dp, Offset(hp.x + hu.x * 10f * dp + hv.x * 4f * dp, hp.y + hu.y * 10f * dp + hv.y * 4f * dp))
    drawCircle(Color.Black.copy(alpha = fade), 1.3f * dp, Offset(hp.x + hu.x * 10.8f * dp + hv.x * 4f * dp, hp.y + hu.y * 10.8f * dp + hv.y * 4f * dp))

    // napas api menyembur ke depan
    val breath = ((p - 0.18f) / 0.5f).coerceIn(0f, 1f)
    if (breath > 0f && p < 0.78f) {
        for (j in 0 until 22) {
            val t = j / 22f
            val reach = (22f + t * 190f * breath) * dp
            val wob = sin(p * TAU * 6f + j) * (4f + 20f * t) * dp
            val o = Offset(snout.x + hu.x * reach + hv.x * wob, snout.y + hu.y * reach + hv.y * wob)
            val r = (3f + 20f * t) * dp
            val col = if (t < 0.35f) Color(0xFFFFE08A) else if (t < 0.7f) Color(0xFFFF8A3D) else Color(0xFFE0102A)
            drawCircle(col.copy(alpha = (0.85f - 0.7f * t) * fade), r, o)
        }
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
