package com.jimz.jimztune.ui.splash

import androidx.compose.animation.core.Animatable
import androidx.compose.animation.core.FastOutSlowInEasing
import androidx.compose.animation.core.LinearEasing
import androidx.compose.animation.core.RepeatMode
import androidx.compose.animation.core.animateFloat
import androidx.compose.animation.core.infiniteRepeatable
import androidx.compose.animation.core.rememberInfiniteTransition
import androidx.compose.animation.core.tween
import androidx.compose.foundation.Canvas
import androidx.compose.foundation.background
import androidx.compose.foundation.gestures.detectTapGestures
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.navigationBarsPadding
import androidx.compose.foundation.layout.offset
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.layout.width
import androidx.compose.foundation.shape.CircleShape
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.runtime.LaunchedEffect
import androidx.compose.runtime.getValue
import androidx.compose.runtime.remember
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clip
import androidx.compose.ui.geometry.Offset
import androidx.compose.ui.geometry.Size
import androidx.compose.ui.graphics.Brush
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.graphics.Path
import androidx.compose.ui.graphics.PathMeasure
import androidx.compose.ui.graphics.StrokeCap
import androidx.compose.ui.graphics.StrokeJoin
import androidx.compose.ui.graphics.drawscope.DrawScope
import androidx.compose.ui.graphics.drawscope.Stroke
import androidx.compose.ui.graphics.drawscope.scale
import androidx.compose.ui.graphics.drawscope.translate
import androidx.compose.ui.graphics.graphicsLayer
import androidx.compose.ui.input.pointer.pointerInput
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp

private val Navy = Color(0xFF0A1330)
private val Deep = Color(0xFF05091F)
private val Blue = Color(0xFF2F6BFF)
private val Cyan = Color(0xFF6FE7FF)
private val Ice = Color(0xFF8FD8FF)

/** Potongan jalur dari 0..1 (sebagian panjang), dipakai untuk efek "digambar". */
private fun trimmed(src: Path, from: Float, to: Float): Path {
    val m = PathMeasure()
    m.setPath(src, false)
    val out = Path()
    val len = m.length
    if (to > from) m.getSegment(len * from.coerceIn(0f, 1f), len * to.coerceIn(0f, 1f), out, true)
    return out
}

private fun ringPath(r: Float, sweepDeg: Float): Path = Path().apply {
    arcTo(
        androidx.compose.ui.geometry.Rect(256f - r, 256f - r, 256f + r, 256f + r),
        startAngleDegrees = 0f, sweepAngleDegrees = sweepDeg, forceMoveTo = true
    )
}

private val aPath = Path().apply {
    moveTo(168f, 352f); lineTo(256f, 148f); lineTo(344f, 352f)
}
private val barPath = Path().apply {
    moveTo(195f, 292f)
    cubicTo(215f, 252f, 235f, 252f, 256f, 292f)
    cubicTo(277f, 332f, 297f, 332f, 317f, 292f)
}
private val wave1 = Path().apply { moveTo(430f, 214f); quadraticBezierTo(448f, 256f, 430f, 298f) }
private val wave2 = Path().apply { moveTo(456f, 192f); quadraticBezierTo(488f, 256f, 456f, 320f) }

/**
 * Layar pembuka Aoto: cincin vinyl digambar berputar, huruf A tergambar, gelombang suara memancar; logo tepat di tengah layar,
 * lalu nama "Aoto" muncul di bawahnya dan titik-titik memuat berdenyut. Menahan sentuhan selama tampil.
 */
@Composable
fun AotoLoader(modifier: Modifier = Modifier) {
    val intro = remember { Animatable(0f) }
    LaunchedEffect(Unit) { intro.animateTo(1f, tween(1500, easing = FastOutSlowInEasing)) }
    val inf = rememberInfiniteTransition(label = "aotoLoader")
    val pulse by inf.animateFloat(0f, 1f, infiniteRepeatable(tween(1500, easing = FastOutSlowInEasing), RepeatMode.Reverse), label = "pulse")
    val spin by inf.animateFloat(0f, 360f, infiniteRepeatable(tween(14000, easing = LinearEasing)), label = "spin")
    val dots by inf.animateFloat(0f, 3f, infiniteRepeatable(tween(1200, easing = LinearEasing)), label = "dots")

    Box(
        modifier
            .fillMaxSize()
            .background(Brush.linearGradient(listOf(Navy, Deep), Offset.Zero, Offset(900f, 1800f)))
            .pointerInput(Unit) { detectTapGestures { } },
        contentAlignment = Alignment.Center
    ) {
        Canvas(Modifier.size(210.dp).graphicsLayer {
            val s = 0.86f + 0.14f * intro.value
            scaleX = s
            scaleY = s
            alpha = (intro.value * 3f).coerceIn(0f, 1f)
        }) {
            val k = size.minDimension / 512f
            // cahaya lembut di belakang logo
            drawCircle(
                Brush.radialGradient(
                    listOf(Blue.copy(alpha = 0.30f + 0.18f * pulse), Color.Transparent),
                    center = Offset(center.x - 8f * k, center.y), radius = size.minDimension * (0.62f + 0.06f * pulse)
                ),
                radius = size.minDimension * 0.7f
            )
            scale(k, pivot = Offset.Zero) { translate(-8f, 0f) { drawMark(intro.value, pulse, spin) } }
        }
        // Teks diletakkan di bawah logo dengan offset tetap, sehingga logo tetap tepat di tengah layar.
        Column(
            Modifier.offset(y = 150.dp),
            horizontalAlignment = Alignment.CenterHorizontally
        ) {
            val textA = ((intro.value - 0.55f) / 0.45f).coerceIn(0f, 1f)
            Text(
                "Aoto",
                fontSize = 38.sp,
                fontWeight = FontWeight.Black,
                letterSpacing = 6.sp,
                color = Color.White,
                modifier = Modifier.graphicsLayer {
                    alpha = textA
                    translationY = (1f - textA) * 24f
                }
            )
            Spacer(Modifier.height(6.dp))
            Text(
                "Musik tanpa batas",
                fontSize = 13.sp,
                letterSpacing = 2.sp,
                color = Cyan.copy(alpha = 0.8f * textA)
            )
        }
        Row(
            Modifier
                .align(Alignment.BottomCenter)
                .navigationBarsPadding()
                .padding(bottom = 48.dp),
            horizontalArrangement = Arrangement.spacedBy(8.dp)
        ) {
            for (i in 0 until 3) {
                // Titik ke-i menyala bergantian.
                val d = ((dots - i + 3f) % 3f)
                val a = (1f - d).coerceIn(0.25f, 1f)
                Box(Modifier.size(7.dp).clip(CircleShape).background(Cyan.copy(alpha = a)))
            }
        }
    }
}

/** Menggambar logo di kanvas 512x512. [t] = kemajuan animasi masuk (0..1). */
private fun DrawScope.drawMark(t: Float, pulse: Float, spin: Float) {
    fun seg(a: Float, b: Float) = ((t - a) / (b - a)).coerceIn(0f, 1f)
    val ringGrad = Brush.linearGradient(listOf(Blue, Cyan), Offset(86f, 426f), Offset(426f, 86f))
    val aGrad = Brush.verticalGradient(listOf(Color.White, Ice), startY = 148f, endY = 352f)

    // Cincin luar: tergambar searah jarum jam dari kanan, celah di kanan atas.
    val sweep = 269.6f * seg(0f, 0.6f)
    if (sweep > 0.5f) {
        val ring = ringPath(170f, sweep)
        drawPath(ring, Blue.copy(alpha = 0.28f), style = Stroke(34f, cap = StrokeCap.Round))
        drawPath(ring, ringGrad, style = Stroke(22f, cap = StrokeCap.Round))
    }
    val inner = 269.6f * seg(0.1f, 0.7f)
    if (inner > 0.5f) drawPath(ringPath(136f, inner), Blue.copy(alpha = 0.4f), style = Stroke(4f, cap = StrokeCap.Round))

    // Huruf A tergambar, lalu palang gelombangnya.
    val aT = seg(0.25f, 0.7f)
    if (aT > 0f) {
        drawPath(trimmed(aPath, 0f, aT), Ice.copy(alpha = 0.25f), style = Stroke(40f, cap = StrokeCap.Round, join = StrokeJoin.Round))
        drawPath(trimmed(aPath, 0f, aT), aGrad, style = Stroke(26f, cap = StrokeCap.Round, join = StrokeJoin.Round))
    }
    val bT = seg(0.6f, 0.88f)
    if (bT > 0f) drawPath(trimmed(barPath, 0f, bT), Cyan, style = Stroke(14f, cap = StrokeCap.Round))

    // Gelombang suara memancar dari celah, berdenyut setelah logo selesai.
    val w = seg(0.75f, 1f)
    if (w > 0f) {
        val p1 = 0.55f + 0.45f * pulse
        val p2 = 0.25f + 0.55f * (1f - pulse)
        drawPath(wave1, Cyan.copy(alpha = w * p1), style = Stroke(10f, cap = StrokeCap.Round))
        drawPath(wave2, Cyan.copy(alpha = w * p2), style = Stroke(10f, cap = StrokeCap.Round))
    }
    // Titik kecil di ujung cincin berputar pelan sebagai penanda memuat.
    if (t >= 1f) {
        val ang = Math.toRadians((spin).toDouble())
        val cx = 256f + 205f * kotlin.math.cos(ang).toFloat()
        val cy = 256f + 205f * kotlin.math.sin(ang).toFloat()
        drawCircle(Cyan.copy(alpha = 0.9f), radius = 6f, center = Offset(cx, cy))
    }
}
