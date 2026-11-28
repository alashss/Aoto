package com.jimz.jimztune.ui.social

import androidx.compose.ui.geometry.Offset
import androidx.compose.ui.graphics.BlendMode
import androidx.compose.ui.graphics.Brush
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.graphics.Path
import androidx.compose.ui.graphics.StrokeCap
import androidx.compose.ui.graphics.StrokeJoin
import androidx.compose.ui.graphics.drawscope.DrawScope
import androidx.compose.ui.graphics.drawscope.Stroke
import androidx.compose.ui.unit.dp
import kotlin.math.PI
import kotlin.math.abs
import kotlin.math.cos
import kotlin.math.floor
import kotlin.math.max
import kotlin.math.min
import kotlin.math.pow
import kotlin.math.sin
import kotlin.math.sqrt

/**
 * Pustaka gambar naga Raja Iblis. Satu naga yang sama dipakai di tiga tempat dengan ukuran berbeda:
 * naga raksasa yang berputar saat profil dibuka, naga kecil yang mengorbit bingkai foto, dan naga yang bertengger
 * di atas gelembung chat. Semuanya digambar dari tulang punggung (daftar titik) sehingga badannya mengalir.
 */
private const val TAU = (2 * PI).toFloat()

internal object Dragon {
    val Dark = Color(0xFF15030A)
    val Mid = Color(0xFF5E0A18)
    val Red = Color(0xFFD4142B)
    val Glow = Color(0xFFFF5A3A)
    val Belly = Color(0xFF9A3A22)
    val Bone = Color(0xFFFFD9BE)
    val Eye = Color(0xFFFFD23F)
}

private fun nrm(o: Offset): Offset {
    val l = sqrt(o.x * o.x + o.y * o.y)
    return if (l < 1e-4f) Offset(1f, 0f) else Offset(o.x / l, o.y / l)
}

private fun mix(a: Offset, b: Offset, k: Float) = Offset(a.x + (b.x - a.x) * k, a.y + (b.y - a.y) * k)
private fun dist(a: Offset, b: Offset): Float { val dx = a.x - b.x; val dy = a.y - b.y; return sqrt(dx * dx + dy * dy) }

private fun rn(i: Int, k: Int): Float {
    val x = sin((i * 12.9898f + k * 78.233f).toDouble()) * 43758.5453
    return (x - floor(x)).toFloat()
}

private fun smooth(x: Float): Float { val c = x.coerceIn(0f, 1f); return c * c * (3f - 2f * c) }

/** Sisi punggung relatif terhadap arah hadap. sgn = 1: punggung di atas saat menghadap kanan; -1: dicerminkan. */
internal fun dragonBack(fwd: Offset, sgn: Float) = Offset(fwd.y * sgn, -fwd.x * sgn)

// ------------------------------------------------------------------------------------ badan

/** [pts] dari leher (indeks 0, dekat kepala) sampai ujung ekor; [half] setengah lebar badan di tiap titik. */
internal fun DrawScope.dragonBody(pts: List<Offset>, half: List<Float>, u: Float, sgn: Float = 1f) {
    val n = pts.size
    if (n < 3) return
    val fwd = List(n) { i -> nrm(pts[max(i - 1, 0)] - pts[min(i + 1, n - 1)]) }
    val up = List(n) { dragonBack(fwd[it], sgn) }
    fun side(i: Int, k: Float) = Offset(pts[i].x + up[i].x * half[i] * k, pts[i].y + up[i].y * half[i] * k)

    val outline = Path().apply {
        val a = side(0, 1f)
        moveTo(a.x, a.y)
        for (i in 1 until n) { val p = side(i, 1f); lineTo(p.x, p.y) }
        for (i in n - 1 downTo 0) { val p = side(i, -1f); lineTo(p.x, p.y) }
        close()
    }
    drawPath(outline, Dragon.Glow.copy(alpha = 0.20f), style = Stroke(u * 0.9f, join = StrokeJoin.Round))
    drawPath(outline, Dragon.Dark)
    // perut berlapis
    for (i in 0 until n - 1) {
        drawLine(Dragon.Belly.copy(alpha = 0.9f), side(i, -0.45f), side(i + 1, -0.45f), (half[i] * 0.8f).coerceAtLeast(1f), StrokeCap.Round)
    }
    for (i in 1 until n - 1 step 2) {
        drawLine(Dragon.Dark.copy(alpha = 0.6f), side(i, -0.95f), side(i, -0.05f), (u * 0.12f).coerceAtLeast(0.8f))
    }
    // punggung menyala
    for (i in 0 until n - 1) {
        drawLine(Dragon.Glow.copy(alpha = 0.75f), side(i, 0.62f), side(i + 1, 0.62f), (u * 0.16f).coerceAtLeast(0.8f), StrokeCap.Round)
        drawLine(Color.White.copy(alpha = 0.07f), side(i, 0.2f), side(i + 1, 0.2f), (half[i] * 0.35f).coerceAtLeast(0.8f), StrokeCap.Round)
    }
    // duri punggung
    for (i in 2 until n - 2 step 2) {
        val h = half[i]
        val baseC = pts[i] + up[i] * (h * 0.85f)
        val a = baseC + fwd[i] * (u * 0.42f)
        val b = baseC - fwd[i] * (u * 0.42f)
        val tip = pts[i] + up[i] * (h + u * 0.85f * ((0.35f + h / (u * 1.2f)).coerceAtMost(1f))) - fwd[i] * (u * 0.95f)
        val spike = Path().apply { moveTo(a.x, a.y); lineTo(tip.x, tip.y); lineTo(b.x, b.y); close() }
        drawPath(spike, Dragon.Glow.copy(alpha = 0.95f))
        drawPath(spike, Dragon.Dark.copy(alpha = 0.6f), style = Stroke((u * 0.07f).coerceAtLeast(0.6f), join = StrokeJoin.Round))
    }
    // ujung ekor berbentuk tombak
    val c0 = pts[n - 1]
    val tailDir = fwd[n - 1] * -1f
    val ux = up[n - 1]
    val spade = Path().apply {
        moveTo(c0.x, c0.y)
        val c1 = c0 + tailDir * (u * 0.7f) + ux * (u * 1.4f)
        val e = c0 + tailDir * (u * 2.6f)
        quadraticBezierTo(c1.x, c1.y, e.x, e.y)
        val c2 = c0 + tailDir * (u * 0.7f) - ux * (u * 1.4f)
        quadraticBezierTo(c2.x, c2.y, c0.x, c0.y)
        close()
    }
    drawPath(spade, Brush.linearGradient(listOf(Dragon.Red, Dragon.Glow), c0, c0 + tailDir * (u * 2.6f)))
}

// ------------------------------------------------------------------------------------ kepala

/** [fwd] arah hadap kepala, [mouth] 0..1 seberapa lebar rahangnya terbuka, [t] jam 0..1 (asap hidung, kumis). */
internal fun DrawScope.dragonHead(pos: Offset, fwd: Offset, u: Float, mouth: Float, t: Float, sgn: Float = 1f) {
    val up = dragonBack(fwd, sgn)
    fun p(x: Float, y: Float) = Offset(pos.x + fwd.x * x * u + up.x * y * u, pos.y + fwd.y * x * u + up.y * y * u)
    val sw = (u * 0.09f).coerceAtLeast(0.7f)

    // kumis berkibar
    for (s in 0 until 2) {
        val path = Path()
        val o = p(1.2f, -0.15f - 0.2f * s)
        path.moveTo(o.x, o.y)
        for (k in 1..9) {
            val x = 1.2f - k * 0.62f
            val y = -0.15f - 0.2f * s - k * 0.14f + sin(k * 0.75f - t * TAU * 3f + s * 1.7f) * 0.38f
            val q = p(x, y)
            path.lineTo(q.x, q.y)
        }
        drawPath(path, Dragon.Glow.copy(alpha = 0.7f), style = Stroke(sw * 1.1f, cap = StrokeCap.Round, join = StrokeJoin.Round))
    }
    // tanduk besar & kecil menyapu ke belakang
    val horn1 = Path().apply {
        val a = p(0.35f, 0.6f); moveTo(a.x, a.y)
        val c = p(-0.9f, 2.1f); val e = p(-3.2f, 1.7f); quadraticBezierTo(c.x, c.y, e.x, e.y)
        val c2 = p(-1.0f, 1.15f); val e2 = p(-0.3f, 0.3f); quadraticBezierTo(c2.x, c2.y, e2.x, e2.y)
        close()
    }
    drawPath(horn1, Brush.linearGradient(listOf(Dragon.Mid, Dragon.Bone), p(0.3f, 0.5f), p(-3.2f, 1.7f)))
    val horn2 = Path().apply {
        val a = p(0.0f, 0.4f); moveTo(a.x, a.y)
        val c = p(-0.8f, 1.2f); val e = p(-2.1f, 0.65f); quadraticBezierTo(c.x, c.y, e.x, e.y)
        val c2 = p(-0.8f, 0.45f); val e2 = p(-0.2f, 0.05f); quadraticBezierTo(c2.x, c2.y, e2.x, e2.y)
        close()
    }
    drawPath(horn2, Brush.linearGradient(listOf(Dragon.Mid, Dragon.Bone), p(0f, 0.3f), p(-2.1f, 0.65f)))
    // duri pipi
    val cheek = Path().apply {
        val a = p(0.1f, -0.45f); moveTo(a.x, a.y)
        val b = p(-1.1f, -1.0f); lineTo(b.x, b.y)
        val c = p(0.6f, -0.75f); lineTo(c.x, c.y)
        close()
    }
    drawPath(cheek, Dragon.Bone.copy(alpha = 0.85f))

    // rahang bawah (turun saat mulut terbuka)
    val drop = mouth * 1.15f
    val jaw = Path().apply {
        val a = p(-0.5f, -0.05f); moveTo(a.x, a.y)
        val b = p(2.15f, -0.1f - drop); lineTo(b.x, b.y)
        val c = p(2.0f, -0.42f - drop * 1.05f); lineTo(c.x, c.y)
        val d = p(0.2f, -0.72f - drop * 0.4f); lineTo(d.x, d.y)
        close()
    }
    drawPath(jaw, Brush.linearGradient(listOf(Dragon.Dark, Color(0xFF6A0C1B)), p(0f, -0.7f), p(2f, -0.1f)))
    drawPath(jaw, Dragon.Glow.copy(alpha = 0.6f), style = Stroke(sw, join = StrokeJoin.Round))
    // bagian dalam mulut menyala
    if (mouth > 0.12f) {
        val inner = Path().apply {
            val a = p(0.5f, -0.1f); moveTo(a.x, a.y)
            val b = p(2.25f, -0.08f); lineTo(b.x, b.y)
            val c = p(2.1f, -0.12f - drop); lineTo(c.x, c.y)
            close()
        }
        drawPath(inner, Color(0xFFFF9A3D).copy(alpha = (mouth * 1.4f).coerceAtMost(1f)))
    }
    // kepala atas
    val skull = Path().apply {
        val a = p(-1.0f, 0.2f); moveTo(a.x, a.y)
        val c1 = p(-0.9f, 0.98f); val e1 = p(0.3f, 0.88f); quadraticBezierTo(c1.x, c1.y, e1.x, e1.y)
        val c2 = p(1.1f, 0.82f); val e2 = p(1.55f, 0.5f); quadraticBezierTo(c2.x, c2.y, e2.x, e2.y)
        val e3 = p(2.5f, 0.38f); lineTo(e3.x, e3.y)
        val c4 = p(2.78f, 0.3f); val e4 = p(2.62f, 0.05f); quadraticBezierTo(c4.x, c4.y, e4.x, e4.y)
        val e5 = p(2.2f, -0.08f); lineTo(e5.x, e5.y)
        val e6 = p(0.4f, -0.1f); lineTo(e6.x, e6.y)
        val e7 = p(-0.7f, -0.15f); lineTo(e7.x, e7.y)
        close()
    }
    drawPath(skull, Brush.linearGradient(listOf(Color(0xFF2A0610), Color(0xFF9A1226)), p(-1f, 0.2f), p(2.6f, 0.3f)))
    drawPath(skull, Dragon.Glow.copy(alpha = 0.75f), style = Stroke(sw, join = StrokeJoin.Round))
    // gigi
    if (mouth > 0.15f) {
        for (k in 0 until 5) {
            val x = 0.9f + k * 0.36f
            val tooth = Path().apply {
                val a = p(x, -0.08f); moveTo(a.x, a.y)
                val b = p(x + 0.17f, -0.08f); lineTo(b.x, b.y)
                val c = p(x + 0.085f, -0.08f - 0.34f * (0.5f + mouth)); lineTo(c.x, c.y)
                close()
            }
            drawPath(tooth, Dragon.Bone)
        }
    }
    // mata menyala
    val eye = p(0.85f, 0.46f)
    drawCircle(Dragon.Eye.copy(alpha = 0.35f), u * 0.7f, eye, blendMode = BlendMode.Plus)
    drawCircle(Dragon.Eye, u * 0.28f, eye)
    drawLine(Dragon.Dark, p(0.85f, 0.2f), p(0.85f, 0.72f), (u * 0.1f).coerceAtLeast(0.6f), StrokeCap.Round)
    // lubang hidung + asap
    drawCircle(Dragon.Dark, u * 0.09f, p(2.35f, 0.25f))
    for (k in 0 until 3) {
        val life = (t * 2f + k / 3f) % 1f
        drawCircle(Color(0xFF6A6A78).copy(alpha = 0.32f * (1f - life)), u * (0.14f + 0.34f * life), p(2.5f + life * 1.6f, 0.35f + life * 1.1f))
    }
}

// ------------------------------------------------------------------------------------ sayap

/**
 * Sayap naga: lengan, empat jari bertulang, selaput bergerigi. [up] menjauhi badan, [trail] ke arah ekor,
 * [phase] sudut kepakan (radian), [amp] 1 = kepakan penuh.
 */
internal fun DrawScope.dragonWing(root: Offset, up: Offset, trail: Offset, span: Float, phase: Float, shade: Float = 1f, amp: Float = 1f) {
    fun at(o: Offset, a: Float, b: Float) = Offset(o.x + trail.x * span * a + up.x * span * b, o.y + trail.y * span * a + up.y * span * b)
    val flap = sin(phase) * amp
    val wrist = at(root, 0.22f, 0.5f + 0.34f * flap)
    val fa = floatArrayOf(0.70f, 1.00f, 0.98f, 0.68f)
    val fb = floatArrayOf(0.35f, 0.02f, -0.32f, -0.58f)
    val fc = floatArrayOf(0.55f, 0.50f, 0.42f, 0.30f)
    val tips = List(4) { k -> at(wrist, fa[k], fb[k] + fc[k] * amp * sin(phase - 0.32f * k)) }
    val membrane = Path().apply {
        moveTo(root.x, root.y)
        lineTo(wrist.x, wrist.y)
        lineTo(tips[0].x, tips[0].y)
        for (k in 1..3) {
            val mid = mix(tips[k - 1], tips[k], 0.5f)
            val ctrl = mix(wrist, mid, 0.72f)
            quadraticBezierTo(ctrl.x, ctrl.y, tips[k].x, tips[k].y)
        }
        val back = at(root, 0.42f, -0.06f)
        val ctrl = mix(tips[3], back, 0.5f)
        quadraticBezierTo(ctrl.x, ctrl.y, back.x, back.y)
        lineTo(root.x, root.y)
        close()
    }
    drawPath(membrane, Brush.linearGradient(listOf(Color(0xFF2A0610).copy(alpha = shade), Color(0xFFC0182E).copy(alpha = shade)), root, tips[1]))
    drawPath(membrane, Brush.radialGradient(listOf(Color(0xFFFF8A3D).copy(alpha = 0.30f * shade), Color.Transparent), mix(wrist, tips[1], 0.5f), span * 0.7f))
    val bw = (span * 0.032f).coerceAtLeast(0.8f)
    drawLine(Dragon.Dark.copy(alpha = shade), root, wrist, bw * 2.2f, StrokeCap.Round)
    drawLine(Dragon.Glow.copy(alpha = 0.9f * shade), root, wrist, bw * 0.8f, StrokeCap.Round)
    for (k in 0..3) {
        drawLine(Dragon.Dark.copy(alpha = shade), wrist, tips[k], bw * 1.7f, StrokeCap.Round)
        drawLine(Dragon.Glow.copy(alpha = 0.9f * shade), wrist, tips[k], bw * 0.65f, StrokeCap.Round)
    }
    drawPath(membrane, Dragon.Glow.copy(alpha = 0.55f * shade), style = Stroke(bw * 0.7f, join = StrokeJoin.Round))
    // cakar di pergelangan
    drawLine(Dragon.Bone.copy(alpha = shade), wrist, wrist + up * (span * 0.13f) + trail * (-span * 0.03f), bw * 1.3f, StrokeCap.Round)
}

// ------------------------------------------------------------------------------------ api

/** Semburan api dari [origin] ke arah [dir]. [intensity] 0..1; [len] panjang maksimum; [spread] kelebaran. */
internal fun DrawScope.dragonFire(origin: Offset, dir: Offset, len: Float, spread: Float, intensity: Float, t: Float) {
    if (intensity <= 0.03f) return
    val side = Offset(dir.y, -dir.x)
    for (j in 0 until 44) {
        val life = (t * 2.4f + rn(j, 1)) % 1f
        val d = len * (0.1f + 0.9f * life)
        val lateral = (rn(j, 2) - 0.5f) * 2f * spread * life * len + sin(life * 9f + j) * 0.035f * len
        val c = origin + dir * d + side * lateral
        val r = len * (0.025f + 0.15f * life) * intensity
        val col = when {
            life < 0.22f -> Color(0xFFFFF3B0)
            life < 0.5f -> Color(0xFFFFA52E)
            life < 0.8f -> Color(0xFFE0200F)
            else -> Color(0xFF3A1010)
        }
        val a = (1f - life) * (if (life > 0.8f) 0.5f else 0.9f) * intensity
        drawCircle(col.copy(alpha = a), r, c, blendMode = BlendMode.Plus)
    }
}

// ------------------------------------------------------------------------------------ naga raksasa (intro profil)

/** [p] 0..1 selama ±9 detik: masuk dari kiri bawah, berputar keliling layar sambil menyemburkan api, lalu melesat pergi. */
internal fun DrawScope.drawDragonHero(p: Float) {
    val w = size.width
    val h = size.height
    val dp = 1.dp.toPx()
    val uH = 12.5f * dp
    val env = smooth(p / 0.05f) * smooth((1f - p) / 0.07f)

    // suasana neraka: tepi layar memerah, kilatan saat naga mengaum
    drawRect(
        Brush.radialGradient(listOf(Color.Transparent, Color(0xFF8A0A1A).copy(alpha = 0.30f * env)), Offset(w / 2f, h / 2f), max(w, h) * 0.75f)
    )
    val roar = (1f - abs(p - 0.17f) / 0.035f).coerceAtLeast(0f)
    if (roar > 0f) {
        drawRect(Color(0xFFFF6A3D).copy(alpha = 0.22f * roar))
        val rr = ((p - 0.14f) / 0.12f).coerceIn(0f, 1f) * w * 0.9f
        drawCircle(Color(0xFFFFB347).copy(alpha = 0.5f * roar), rr, Offset(w * 0.3f, h * 0.45f), style = Stroke(6f * dp))
    }

    // lintasan kepala
    val start = Offset(-0.42f * w, 0.96f * h)
    val cx = 0.5f * w; val cy = 0.44f * h; val rx = 0.37f * w; val ry = 0.29f * h
    val c0 = Offset(cx - rx, cy)
    val ctrl = Offset(cx - rx, cy + ry * 1.9f)
    val turns = 1.25f
    val thEnd = PI.toFloat() + turns * TAU
    val circEnd = Offset(cx + rx * cos(thEnd), cy + ry * sin(thEnd))
    val exitCtrl = Offset(circEnd.x + 0.45f * w, circEnd.y)
    val exitEnd = Offset(1.55f * w, -0.2f * h)
    val speed = 2900f * dp
    fun bez(a: Offset, b: Offset, c: Offset, s: Float): Offset {
        val k = 1f - s
        return Offset(k * k * a.x + 2f * k * s * b.x + s * s * c.x, k * k * a.y + 2f * k * s * b.y + s * s * c.y)
    }
    fun pos(u: Float): Offset = when {
        u < 0f -> start + nrm(ctrl - start) * (u * speed)
        u < 0.2f -> bez(start, ctrl, c0, u / 0.2f)
        u < 0.8f -> {
            val th = PI.toFloat() + turns * TAU * ((u - 0.2f) / 0.6f)
            Offset(cx + rx * cos(th), cy + ry * sin(th))
        }
        u <= 1f -> bez(circEnd, exitCtrl, exitEnd, (u - 0.8f) / 0.2f)
        else -> exitEnd + nrm(exitEnd - exitCtrl) * ((u - 1f) * speed)
    }

    // tulang punggung: ambil titik dengan jarak tetap menyusuri lintasan ke belakang kepala
    val headU = p * 1.13f
    val n = 46
    val segLen = 0.6f * uH
    val raw = ArrayList<Offset>(n)
    var prev = pos(headU)
    raw += prev
    var acc = 0f
    var uu = headU
    var guard = 0
    while (raw.size < n && guard < 1200) {
        uu -= 0.0004f
        val q = pos(uu)
        acc += dist(prev, q)
        prev = q
        if (acc >= segLen) { raw += q; acc = 0f }
        guard++
    }
    if (raw.size < 12) return
    val m = raw.size
    // geliat badan
    val pts = List(m) { i ->
        val f = nrm(raw[max(i - 1, 0)] - raw[min(i + 1, m - 1)])
        val b = dragonBack(f, 1f)
        val wv = sin(i * 0.5f - p * TAU * 6f) * uH * 0.4f * (i / (m - 1f))
        Offset(raw[i].x + b.x * wv, raw[i].y + b.y * wv)
    }
    val half = List(m) { i ->
        if (i <= 9) uH * (0.62f + 0.06f * i)
        else uH * (1.16f * (1f - (i - 9f) / (m - 9f)).coerceAtLeast(0f).pow(0.85f) + 0.14f)
    }

    // percikan bara jatuh dari badan
    for (k in 0 until 34) {
        val idx = (k * 3 + 2) % m
        val life = (p * 9f * 0.7f + rn(k, 3)) % 1f
        val o = pts[idx]
        drawCircle(
            Color(0xFFFF8A3D).copy(alpha = 0.85f * (1f - life) * env),
            (1.2f + 2.2f * rn(k, 4)) * dp,
            Offset(o.x + (rn(k, 5) - 0.5f) * 30f * dp * life, o.y + life * 70f * dp)
        )
    }

    val shoulder = 8
    val fS = nrm(pts[shoulder - 1] - pts[shoulder + 1])
    val upS = dragonBack(fS, 1f)
    val trail = fS * -1f
    val phase = p * 9f * 1.9f * TAU
    val rootNear = pts[shoulder] + upS * (half[shoulder] * 0.7f)
    // sayap jauh (lebih gelap, miring ke belakang)
    val upFar = nrm(upS * 0.8f + trail * 0.5f)
    dragonWing(pts[shoulder - 1] + upS * (half[shoulder] * 0.6f), upFar, trail, uH * 8.2f, phase + 0.9f, 0.55f)
    dragonBody(pts, half, uH)
    dragonWing(rootNear, upS, trail, uH * 10.5f, phase, 1f)

    // kepala + api
    val fwd = nrm(pts[0] - pts[2])
    val win = { a: Float, b: Float -> smooth((p - a) / 0.03f) * smooth((b - p) / 0.03f) }
    val breath = max(max(win(0.12f, 0.32f), win(0.40f, 0.54f)), win(0.60f, 0.74f))
    dragonHead(pts[0], fwd, uH, 0.12f + 0.88f * breath, p * 9f)
    if (breath > 0.03f) {
        val mouth = pts[0] + fwd * (uH * 2.5f) + dragonBack(fwd, 1f) * (uH * -0.5f)
        val dir = nrm(fwd + dragonBack(fwd, 1f) * -0.12f)
        drawCircle(Brush.radialGradient(listOf(Color(0xFFFFB347).copy(alpha = 0.5f * breath), Color.Transparent), mouth, uH * 5f), uH * 5f, mouth, blendMode = BlendMode.Plus)
        dragonFire(mouth, dir, uH * 19f, 0.2f, breath, p * 9f)
    }
}

// ------------------------------------------------------------------------------------ naga kecil mengorbit foto profil

/** Naga mungil yang terbang melingkari bingkai foto sambil mengepak. [t] jam 0..1 (6 detik = 1 putaran). */
internal fun DrawScope.drawBabyDragon(c: Offset, orbit: Float, u: Float, t: Float) {
    val n = 18
    val segLen = 0.55f * u
    val step = segLen / orbit
    val head = t * TAU
    val pts = List(n) { i ->
        val a = head - i * step
        val r = orbit + sin(i * 0.7f - t * TAU * 8f) * u * 0.3f
        Offset(c.x + cos(a) * r, c.y + sin(a) * r)
    }
    val half = List(n) { i ->
        if (i < 4) u * (0.45f + 0.08f * i) else u * (0.77f * (1f - (i - 4f) / (n - 4f)).coerceAtLeast(0f).pow(0.9f) + 0.1f)
    }
    val sh = 4
    val f = nrm(pts[sh - 1] - pts[sh + 1])
    val up = dragonBack(f, 1f)
    val trail = f * -1f
    val phase = t * 8f * TAU
    dragonWing(pts[sh - 1] + up * (half[sh] * 0.5f), nrm(up * 0.85f + trail * 0.45f), trail, u * 3.4f, phase + 0.8f, 0.55f)
    dragonBody(pts, half, u)
    dragonWing(pts[sh] + up * (half[sh] * 0.6f), up, trail, u * 4.4f, phase, 1f)
    val fwd = nrm(pts[0] - pts[2])
    val burst = ((sin(t * TAU * 2f + 0.5f) - 0.55f) / 0.45f).coerceAtLeast(0f)
    dragonHead(pts[0], fwd, u * 0.95f, 0.1f + 0.7f * burst, t)
    if (burst > 0.03f) {
        val mouth = pts[0] + fwd * (u * 2.4f)
        dragonFire(mouth, fwd, u * 6.5f, 0.22f, burst, t)
    }
}

// ------------------------------------------------------------------------------------ naga bertengger di gelembung chat

/**
 * Naga yang duduk di tepi atas gelembung, menghadap kiri. [origin] = titik di tepi atas tempat badannya bertumpu,
 * ekornya menjuntai ke sisi gelembung dan berayun. [u] satuan ukuran, [t] jam 0..1.
 */
internal fun DrawScope.drawPerchedDragon(origin: Offset, u: Float, t: Float) {
    val base = arrayOf(
        -4.4f to -6.0f, -3.6f to -5.0f, -2.8f to -4.0f, -2.0f to -2.9f, -1.0f to -2.0f, 0.2f to -1.6f, 1.4f to -1.5f,
        2.4f to -1.1f, 3.2f to 0.0f, 3.7f to 1.4f, 3.7f to 2.8f, 3.2f to 4.0f, 3.3f to 5.0f
    )
    val hw = floatArrayOf(0.45f, 0.5f, 0.6f, 0.8f, 0.95f, 1.05f, 1.05f, 0.95f, 0.8f, 0.6f, 0.45f, 0.3f, 0.15f)
    val pts = List(base.size) { i ->
        var x = base[i].first
        var y = base[i].second
        if (i >= 8) x += sin(t * TAU * 2f - i * 0.7f) * 0.4f * (i - 7f) / 5f
        if (i < 3) { y += sin(t * TAU) * 0.2f; x += sin(t * TAU * 2f) * 0.12f }
        Offset(origin.x + x * u, origin.y + y * u)
    }
    val half = List(base.size) { hw[it] * u }
    val sgn = -1f
    val f4 = nrm(pts[3] - pts[5])
    val up4 = dragonBack(f4, sgn)
    val trail4 = f4 * -1f
    // sayap terlipat, sesekali bergetar
    val flutter = if (sin(t * TAU * 1f + 2f) > 0.6f) 1f else 0.25f
    dragonWing(pts[4] + up4 * (half[4] * 0.6f), up4, trail4, u * 3.6f, t * TAU * 6f, 0.9f, 0.35f * flutter + 0.12f)
    dragonBody(pts, half, u, sgn)
    val look = sin(t * TAU) * 0.25f
    val fwd = nrm(Offset(-1f, -0.35f + look))
    val burst = ((sin(t * TAU + 1f) - 0.82f) / 0.18f).coerceAtLeast(0f)
    dragonHead(pts[0], fwd, u * 0.9f, 0.1f + 0.75f * burst, t, sgn)
    if (burst > 0.03f) dragonFire(pts[0] + fwd * (u * 2.3f), fwd, u * 6f, 0.2f, burst, t)
}
