package com.jimz.jimztune.ui.components

import androidx.compose.animation.core.LinearEasing
import androidx.compose.animation.core.RepeatMode
import androidx.compose.animation.core.animateFloat
import androidx.compose.animation.core.infiniteRepeatable
import androidx.compose.animation.core.rememberInfiniteTransition
import androidx.compose.animation.core.tween
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.BoxScope
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.runtime.Composable
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.setValue
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clip
import androidx.compose.ui.draw.drawWithContent
import androidx.compose.ui.geometry.CornerRadius
import androidx.compose.ui.geometry.Offset
import androidx.compose.ui.geometry.Size
import androidx.compose.ui.graphics.Brush
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.graphics.Shape
import androidx.compose.ui.graphics.TransformOrigin
import androidx.compose.ui.graphics.drawOutline
import androidx.compose.ui.graphics.drawscope.Stroke
import androidx.compose.ui.graphics.graphicsLayer
import androidx.compose.ui.layout.layout
import androidx.compose.ui.layout.onGloballyPositioned
import androidx.compose.ui.layout.positionInRoot
import androidx.compose.ui.platform.LocalDensity
import androidx.compose.ui.unit.Constraints
import androidx.compose.ui.unit.IntSize
import androidx.compose.ui.unit.dp

/**
 * Kaca cair (liquid glass), bukan sekadar kotak transparan:
 *  1. Lensa: isi latar di belakang kartu digambar ulang, diperbesar, dikaburkan, dan dibuat lebih pekat warnanya,
 *     jadi yang di balik kaca terlihat "terbiaskan".
 *  2. Tint tipis dengan gradasi: lebih terang di atas, lebih gelap di bawah.
 *  3. Tepi bercahaya: garis tepi terang di sudut kiri atas dan kanan bawah, dengan sedikit pinggiran warna (dispersi).
 *  4. Kilap: sorot lengkung di bagian atas dan kilau yang pelan menyapu permukaan.
 *
 * [backdrop] harus menggambar latar yang sama dengan yang ada di belakang kartu, memenuhi seluruh layar.
 */
@Composable
fun LiquidGlass(
    modifier: Modifier = Modifier,
    shape: Shape,
    backdrop: (@Composable (Modifier) -> Unit)? = null,
    magnify: Float = 1.12f,
    content: @Composable BoxScope.() -> Unit
) {
    var pos by remember { mutableStateOf(Offset.Zero) }
    var cardSize by remember { mutableStateOf(IntSize.Zero) }
    var root by remember { mutableStateOf(IntSize.Zero) }
    val inf = rememberInfiniteTransition(label = "glass")
    val sweep by inf.animateFloat(-0.4f, 1.4f, infiniteRepeatable(tween(5200, easing = LinearEasing), RepeatMode.Restart), label = "sweep")

    Box(
        modifier
            .onGloballyPositioned {
                pos = it.positionInRoot()
                cardSize = it.size
                root = it.findRootSize()
            }
            .clip(shape)
    ) {
        // 1. lensa
        if (backdrop != null && root.width > 0 && cardSize.width > 0) {
            val cx = (pos.x + cardSize.width / 2f) / root.width
            val cy = (pos.y + cardSize.height / 2f) / root.height
            Box(
                Modifier
                    .layout { m, _ ->
                        val p = m.measure(Constraints.fixed(root.width, root.height))
                        layout(cardSize.width, cardSize.height) { p.place(-pos.x.toInt(), -pos.y.toInt()) }
                    }
                    .graphicsLayer { scaleX = magnify; scaleY = magnify; transformOrigin = TransformOrigin(cx, cy) }
            ) { backdrop(Modifier.fillMaxSize()) }
        }
        // 2. tint + 3/4. tepi dan kilap digambar di atas isi
        Box(
            Modifier
                .matchParentSize()
                .drawWithContent {
                    // tint
                    drawOutline(
                        shape.createOutline(size, layoutDirection, this),
                        Brush.verticalGradient(
                            0f to Color.White.copy(alpha = 0.16f),
                            0.5f to Color.White.copy(alpha = 0.05f),
                            1f to Color.Black.copy(alpha = 0.14f)
                        )
                    )
                    drawContent()
                    val w = size.width; val h = size.height
                    // kilau yang menyapu
                    val sx = w * sweep
                    drawRect(
                        Brush.linearGradient(
                            0f to Color.Transparent, 0.5f to Color.White.copy(alpha = 0.10f), 1f to Color.Transparent,
                            start = Offset(sx - w * 0.25f, 0f), end = Offset(sx + w * 0.05f, h)
                        )
                    )
                    // sorot lengkung di atas
                    drawOval(
                        Brush.verticalGradient(listOf(Color.White.copy(alpha = 0.22f), Color.Transparent), startY = 0f, endY = h * 0.45f),
                        topLeft = Offset(-w * 0.1f, -h * 0.55f), size = Size(w * 1.2f, h * 0.95f)
                    )
                    // tepi: pinggiran warna tipis (dispersi), lalu garis terang di dua sudut
                    val outline = shape.createOutline(size, layoutDirection, this)
                    val d = 1.2.dp.toPx()
                    drawOutline(outline, Brush.linearGradient(listOf(Color(0x5500E5FF), Color.Transparent, Color(0x55FF4DD2)), Offset(0f, 0f), Offset(w, h)), style = Stroke(d * 2.4f))
                    drawOutline(
                        outline,
                        Brush.linearGradient(
                            0f to Color.White.copy(alpha = 0.85f), 0.28f to Color.White.copy(alpha = 0.10f),
                            0.72f to Color.White.copy(alpha = 0.10f), 1f to Color.White.copy(alpha = 0.55f),
                            start = Offset(0f, 0f), end = Offset(w, h)
                        ),
                        style = Stroke(d)
                    )
                    // bayangan dalam tipis di tepi bawah
                    drawOutline(outline, Brush.verticalGradient(listOf(Color.Transparent, Color.Black.copy(alpha = 0.18f)), startY = h * 0.6f, endY = h), style = Stroke(d * 5f))
                }
        )
        content()
    }
}

private fun androidx.compose.ui.layout.LayoutCoordinates.findRootSize(): IntSize {
    var c = this
    while (c.parentLayoutCoordinates != null) c = c.parentLayoutCoordinates!!
    return c.size
}
