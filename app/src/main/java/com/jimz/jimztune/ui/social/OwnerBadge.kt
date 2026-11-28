package com.jimz.jimztune.ui.social

import androidx.compose.foundation.Canvas
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.width
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clip
import androidx.compose.ui.draw.drawBehind
import androidx.compose.ui.geometry.Offset
import androidx.compose.ui.graphics.Brush
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.graphics.Path
import androidx.compose.ui.graphics.StrokeCap
import androidx.compose.ui.graphics.StrokeJoin
import androidx.compose.ui.graphics.drawscope.DrawScope
import androidx.compose.ui.graphics.drawscope.Stroke
import androidx.compose.ui.graphics.drawscope.rotate
import androidx.compose.ui.text.TextStyle
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.unit.Dp
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import kotlin.math.PI
import kotlin.math.abs
import kotlin.math.cos
import kotlin.math.sin

val DemonRed = Color(0xFFE0102A)
val DemonDeep = Color(0xFF3A0610)
val DemonFlame = Color(0xFFFF6A3D)
val DemonViolet = Color(0xFF7B1FD1)

/**
 * Lencana OWNER Raja Iblis: pelat hitam-merah berkilau dengan bingkai emas, kilatan cahaya yang menyusuri tepinya,
 * sepasang tanduk kecil dan lidah api di atasnya, diapit sepasang sayap naga yang mengepak dengan selaput & tulang.
 */
@Composable
fun OwnerBadge(modifier: Modifier = Modifier, height: Dp = 16.dp) {
    val t = rememberCosmeticClock()
    val phase = t * 2f * PI.toFloat() * 5f
    val density = androidx.compose.ui.platform.LocalDensity.current
    val span = with(density) { (height * 3.6f).toPx() }
    val wingW = height * 1.9f
    val wingH = height * 1.5f
    Row(modifier, verticalAlignment = Alignment.CenterVertically) {
        Canvas(Modifier.width(wingW).height(wingH)) {
            dragonWing(Offset(size.width, size.height * 0.62f), Offset(0f, -1f), Offset(-1f, 0f), size.height * 0.8f, phase, 1f)
        }
        Box(
            Modifier
                .padding(horizontal = 1.dp)
                .drawBehind {
                    val w = size.width
                    val h = size.height
                    val glow = 0.5f + 0.5f * abs(sin(t * 2f * PI.toFloat() * 2f))
                    // aura
                    drawRoundRect(DemonRed.copy(alpha = 0.20f * glow), cornerRadius = androidx.compose.ui.geometry.CornerRadius(h), size = size, style = Stroke(h * 0.55f))
                    // tanduk kecil di kedua sudut atas
                    for (sg in listOf(-1f, 1f)) {
                        val bx = if (sg < 0f) h * 0.55f else w - h * 0.55f
                        val horn = Path().apply {
                            moveTo(bx - sg * h * 0.16f, h * 0.06f)
                            quadraticBezierTo(bx + sg * h * 0.05f, -h * 0.45f, bx + sg * h * 0.42f, -h * 0.5f)
                            quadraticBezierTo(bx + sg * h * 0.18f, -h * 0.22f, bx + sg * h * 0.2f, h * 0.1f)
                            close()
                        }
                        drawPath(horn, Brush.linearGradient(listOf(Color(0xFF3A0610), Color(0xFFFFD9BE)), Offset(bx, h * 0.1f), Offset(bx + sg * h * 0.42f, -h * 0.5f)))
                    }
                    // lidah api kecil di tengah atas
                    for (k in -1..1) {
                        val fl = 0.65f + 0.35f * abs(sin(t * 2f * PI.toFloat() * 4f + k * 1.7f))
                        val x = w / 2f + k * h * 0.42f
                        val ht = h * (if (k == 0) 0.62f else 0.4f) * fl
                        val fire = Path().apply {
                            moveTo(x - h * 0.13f, h * 0.05f)
                            quadraticBezierTo(x - h * 0.06f, -ht * 0.5f, x + h * 0.02f * sin(t * 25f), -ht)
                            quadraticBezierTo(x + h * 0.06f, -ht * 0.5f, x + h * 0.13f, h * 0.05f)
                            close()
                        }
                        drawPath(fire, Brush.verticalGradient(listOf(Color(0xFFFFE08A), Color(0xFFFF7A2E), Color(0xFFD4142B).copy(alpha = 0.6f)), startY = -ht, endY = h * 0.05f))
                    }
                }
                .clip(RoundedCornerShape(50))
                .drawBehind {
                    val w = size.width
                    val h = size.height
                    drawRect(Brush.horizontalGradient(listOf(Color(0xFF14020A), Color(0xFF7A0A1C), Color(0xFFE0182E), Color(0xFF7A0A1C), Color(0xFF14020A))))
                    drawRect(Brush.verticalGradient(listOf(Color.White.copy(alpha = 0.28f), Color.Transparent, Color.Black.copy(alpha = 0.35f)), startY = 0f, endY = h))
                    // sapuan kilau yang melintas
                    val sx = ((t * 1.5f) % 1.5f - 0.25f) * w
                    drawRect(Brush.horizontalGradient(listOf(Color.Transparent, Color.White.copy(alpha = 0.32f), Color.Transparent), startX = sx - w * 0.14f, endX = sx + w * 0.14f))
                    // bingkai emas
                    drawRoundRect(
                        Brush.horizontalGradient(listOf(Color(0xFFFFE9A0), Color(0xFFC9951A), Color(0xFFFFE9A0), Color(0xFFC9951A), Color(0xFFFFE9A0))),
                        cornerRadius = androidx.compose.ui.geometry.CornerRadius(h / 2f), style = Stroke(h * 0.07f)
                    )
                    // titik cahaya menyusuri tepi
                    val r = h / 2f
                    val straight = (w - h).coerceAtLeast(0f)
                    val perim = 2f * straight + (PI.toFloat() * h)
                    for (k in 0 until 5) {
                        var s = ((t * 2f - k * 0.012f) % 1f + 1f) % 1f * perim
                        val o: Offset = when {
                            s < straight -> Offset(r + s, h * 0.035f)
                            else -> {
                                s -= straight
                                val arc = PI.toFloat() * r
                                if (s < arc) { val a = -PI.toFloat() / 2f + s / r; Offset(w - r + r * cos(a), r + r * sin(a)) }
                                else {
                                    s -= arc
                                    if (s < straight) Offset(w - r - s, h * 0.965f)
                                    else { s -= straight; val a = PI.toFloat() / 2f + s / r; Offset(r + r * cos(a), r + r * sin(a)) }
                                }
                            }
                        }
                        drawCircle(Color.White.copy(alpha = 0.9f - k * 0.17f), h * (0.07f - k * 0.01f), o)
                    }
                }
                .padding(horizontal = height * 0.55f, vertical = height * 0.07f),
            contentAlignment = Alignment.Center
        ) {
            val shift = span * ((t * 2f) % 1f)
            Text(
                "OWNER",
                style = TextStyle(
                    brush = Brush.linearGradient(
                        listOf(Color(0xFFFFF6D6), Color(0xFFFFB347), Color(0xFFFF4A2E), Color(0xFFFFB347), Color(0xFFFFF6D6)),
                        start = Offset(shift - span, 0f), end = Offset(shift, 0f), tileMode = androidx.compose.ui.graphics.TileMode.Mirror
                    ),
                    fontSize = (height.value * 0.56f).sp, fontWeight = FontWeight.Black, letterSpacing = 1.2.sp,
                    shadow = androidx.compose.ui.graphics.Shadow(Color(0xFFFF2A1A), blurRadius = height.value * 0.5f)
                )
            )
        }
        Canvas(Modifier.width(wingW).height(wingH)) {
            dragonWing(Offset(0f, size.height * 0.62f), Offset(0f, -1f), Offset(1f, 0f), size.height * 0.8f, phase + 0.35f, 1f)
        }
    }
}

/**
 * Sayap iblis: lengan, empat jari bertulang dengan cakar, selaput bergerigi berwarna gradasi merah tua ke hitam,
 * garis tulang yang menyala, dan kilau tepi. [mirror] true = sayap kanan.
 */
fun DrawScope.drawDemonWing(mirror: Boolean, flap: Float) {
    val w = size.width
    val h = size.height
    // sayap kiri digambar menghadap kiri; kanan = cermin
    fun px(x: Float) = if (mirror) x * w else (1f - x) * w
    fun pt(x: Float, y: Float) = Offset(px(x), y * h)
    // titik tempel di dekat badan: sisi dalam
    val root = pt(0.02f, 0.58f)
    val wrist = pt(0.34f, 0.10f)
    val tip1 = pt(0.98f, 0.00f)
    val tip2 = pt(0.96f, 0.36f)
    val tip3 = pt(0.76f, 0.70f)
    val tip4 = pt(0.42f, 0.98f)
    val low = pt(0.02f, 0.86f)

    rotate(flap * 5f * (if (mirror) 1f else -1f), root) {
        val membrane = Path().apply {
            moveTo(root.x, root.y)
            lineTo(wrist.x, wrist.y)
            lineTo(tip1.x, tip1.y)
            quadraticBezierTo(px(0.80f), 0.30f * h, tip2.x, tip2.y)
            quadraticBezierTo(px(0.84f), 0.52f * h, tip3.x, tip3.y)
            quadraticBezierTo(px(0.58f), 0.70f * h, tip4.x, tip4.y)
            quadraticBezierTo(px(0.22f), 0.80f * h, low.x, low.y)
            close()
        }
        drawPath(membrane, Brush.linearGradient(listOf(Color(0xFFB3122A), Color(0xFF5E0A18), Color(0xFF15030A)), pt(0.9f, 0f), pt(0.05f, 1f)))
        // kilau selaput
        drawPath(membrane, Brush.radialGradient(listOf(DemonFlame.copy(alpha = 0.28f), Color.Transparent), pt(0.55f, 0.35f), w * 0.55f))
        val boneW = (h * 0.075f).coerceAtLeast(1.2f)
        val glowCol = Color(0xFFFF4A3A)
        fun bone(a: Offset, b: Offset, wd: Float) {
            drawLine(Color(0xFF1B0207), a, b, wd * 1.8f, StrokeCap.Round)
            drawLine(glowCol.copy(alpha = 0.9f), a, b, wd * 0.7f, StrokeCap.Round)
        }
        // lengan dan jari
        bone(root, wrist, boneW * 1.35f)
        bone(wrist, tip1, boneW)
        bone(wrist, tip2, boneW)
        bone(wrist, tip3, boneW * 0.95f)
        bone(wrist, tip4, boneW * 0.9f)
        // cakar di ujung jari
        listOf(tip1 to pt(1.0f, -0.05f), tip2 to pt(1.0f, 0.40f), tip3 to pt(0.80f, 0.77f), tip4 to pt(0.40f, 1.0f)).forEach { (a, b) ->
            drawLine(Color(0xFFFFD2B0), a, b, boneW * 0.8f, StrokeCap.Round)
        }
        // tepi bergerigi bercahaya
        drawPath(
            Path().apply {
                moveTo(tip1.x, tip1.y)
                quadraticBezierTo(px(0.80f), 0.30f * h, tip2.x, tip2.y)
                quadraticBezierTo(px(0.84f), 0.52f * h, tip3.x, tip3.y)
                quadraticBezierTo(px(0.58f), 0.70f * h, tip4.x, tip4.y)
            },
            Color(0xFFFF7A5A).copy(alpha = 0.85f), style = Stroke(boneW * 0.5f, cap = StrokeCap.Round, join = StrokeJoin.Round)
        )
        // sendi pergelangan
        drawCircle(Color(0xFFFFB199), boneW * 0.9f, wrist)
    }
}
