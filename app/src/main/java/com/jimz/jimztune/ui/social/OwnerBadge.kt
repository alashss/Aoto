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
import kotlin.math.sin

val DemonRed = Color(0xFFE0102A)
val DemonDeep = Color(0xFF3A0610)
val DemonFlame = Color(0xFFFF6A3D)
val DemonViolet = Color(0xFF7B1FD1)

/**
 * Lencana OWNER bertema raja iblis: pil merah darah-hitam dengan tulisan api, diapit sepasang sayap iblis
 * yang mengepak pelan. Dipakai di Chat Global dan profil.
 */
@Composable
fun OwnerBadge(modifier: Modifier = Modifier, height: Dp = 16.dp) {
    val t = rememberCosmeticClock()
    val flap = sin(t * 2f * PI.toFloat()) // -1..1
    val glow = 0.55f + 0.45f * abs(sin(t * 3f * PI.toFloat()))
    Row(modifier, verticalAlignment = Alignment.CenterVertically) {
        Canvas(Modifier.width(height * 1.55f).height(height * 1.35f)) { drawDemonWing(false, flap) }
        Box(
            Modifier
                .padding(horizontal = 1.dp)
                .drawBehind {
                    drawRoundRect(DemonRed.copy(alpha = 0.22f * glow), cornerRadius = androidx.compose.ui.geometry.CornerRadius(size.height), size = size, style = Stroke(size.height * 0.5f))
                }
                .clip(RoundedCornerShape(50))
                .drawBehind {
                    drawRect(Brush.horizontalGradient(listOf(Color(0xFF14020A), Color(0xFF8E0A1C), Color(0xFFD4142B), Color(0xFF8E0A1C), Color(0xFF14020A))))
                    drawRect(Brush.verticalGradient(listOf(Color.White.copy(alpha = 0.22f), Color.Transparent), endY = size.height * 0.55f))
                }
                .padding(horizontal = height * 0.5f, vertical = height * 0.06f),
            contentAlignment = Alignment.Center
        ) {
            Text(
                "OWNER",
                style = TextStyle(
                    brush = Brush.verticalGradient(listOf(Color(0xFFFFF1C2), Color(0xFFFFB35A), Color(0xFFFF5A2E))),
                    fontSize = (height.value * 0.56f).sp, fontWeight = FontWeight.Black, letterSpacing = 1.sp
                )
            )
        }
        Canvas(Modifier.width(height * 1.55f).height(height * 1.35f)) { drawDemonWing(true, flap) }
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
