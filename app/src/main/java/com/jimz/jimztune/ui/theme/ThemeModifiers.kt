package com.jimz.jimztune.ui.theme

import androidx.compose.animation.core.LinearEasing
import androidx.compose.animation.core.RepeatMode
import androidx.compose.animation.core.animateFloat
import androidx.compose.animation.core.infiniteRepeatable
import androidx.compose.animation.core.rememberInfiniteTransition
import androidx.compose.animation.core.tween
import androidx.compose.foundation.background
import androidx.compose.foundation.border
import androidx.compose.runtime.Composable
import androidx.compose.runtime.getValue
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.drawBehind
import androidx.compose.ui.draw.shadow
import androidx.compose.ui.geometry.Offset
import androidx.compose.ui.graphics.Brush
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.graphics.Shape
import androidx.compose.ui.graphics.drawscope.clipPath
import androidx.compose.ui.graphics.lerp
import androidx.compose.ui.unit.dp
import kotlin.math.PI
import kotlin.math.cos
import kotlin.math.sin

/**
 * Permukaan kartu sesuai gaya pilihan (Setelan -> Tampilan). Satu-satunya tempat yang menentukan tampilan kartu,
 * jadi mengganti gaya langsung mengubah semua kartu yang memakainya.
 * Urutan pemakaian: `.cardSurface(shape).clip(shape)` (bayangan harus digambar sebelum dipotong).
 */
fun Modifier.cardSurface(shape: Shape, style: UiStyle = AppTheme.uiStyle): Modifier {
    val accent = AppTheme.colors.accent
    val high = AppTheme.colors.surfaceHigh
    val surface = AppTheme.colors.surface
    return when (style) {
        UiStyle.GLASS -> this.realGlass(shape, accent)

        UiStyle.LIQUID -> this.liquidGlass(shape, accent)

        UiStyle.FROSTED -> this
            .background(
                Brush.verticalGradient(listOf(Color.White.copy(alpha = 0.15f), Color.White.copy(alpha = 0.05f))),
                shape
            )
            .border(
                1.dp,
                Brush.verticalGradient(listOf(Color.White.copy(alpha = 0.38f), Color.White.copy(alpha = 0.05f))),
                shape
            )

        UiStyle.FLAT -> this.background(high, shape)

        UiStyle.SOFT -> this
            .shadow(8.dp, shape, clip = false, ambientColor = Color.Black, spotColor = Color.Black)
            .background(Brush.verticalGradient(listOf(lerp(high, Color.White, 0.04f), surface)), shape)
            .border(1.dp, Color.White.copy(alpha = 0.04f), shape)

        UiStyle.OUTLINE -> this
            .border(1.2.dp, accent.copy(alpha = 0.50f), shape)

        UiStyle.NEON -> this
            .shadow(10.dp, shape, clip = false, ambientColor = accent, spotColor = accent)
            .background(accent.copy(alpha = 0.07f), shape)
            .border(1.3.dp, accent.copy(alpha = 0.85f), shape)

        UiStyle.TINT -> this
            .background(
                Brush.linearGradient(listOf(accent.copy(alpha = 0.24f), accent.copy(alpha = 0.05f))),
                shape
            )
            .border(1.dp, accent.copy(alpha = 0.28f), shape)

        UiStyle.MINIMAL -> this
            .background(Color.White.copy(alpha = 0.025f), shape)
    }
}

/**
 * Kaca sungguhan: tebal, bening, dan memantulkan cahaya. Bukan sekadar buram.
 *  - badan tembus pandang dengan sedikit rona aksen dan gradasi diagonal (sisi kena cahaya lebih terang),
 *  - kilap lengkung (specular) di sudut kiri atas dan pantulan lembut di kanan bawah,
 *  - tepi bercahaya: terang di sisi yang menghadap cahaya, redup di sisi lain, plus garis dalam tipis
 *    yang memberi kesan ketebalan kaca.
 */
fun Modifier.realGlass(shape: Shape, accent: Color): Modifier = this
    .background(
        Brush.linearGradient(
            0f to Color.White.copy(alpha = 0.20f),
            0.45f to lerp(Color.White, accent, 0.25f).copy(alpha = 0.05f),
            1f to Color.White.copy(alpha = 0.12f)
        ),
        shape
    )
    .drawBehind {
        val outline = shape.createOutline(size, layoutDirection, this)
        val path = outlineToPath(outline)
        val w = size.width
        val h = size.height
        val r = maxOf(w, h)
        clipPath(path) {
            // kilap utama di kiri atas
            drawRect(
                Brush.radialGradient(
                    listOf(Color.White.copy(alpha = 0.34f), Color.Transparent),
                    center = Offset(w * 0.12f, -h * 0.05f),
                    radius = r * 0.75f
                )
            )
            // pantulan balik di kanan bawah
            drawRect(
                Brush.radialGradient(
                    listOf(lerp(Color.White, accent, 0.4f).copy(alpha = 0.16f), Color.Transparent),
                    center = Offset(w * 0.95f, h * 1.05f),
                    radius = r * 0.6f
                )
            )
            // pita cahaya tipis memotong diagonal
            drawRect(
                Brush.linearGradient(
                    0f to Color.Transparent,
                    0.38f to Color.Transparent,
                    0.47f to Color.White.copy(alpha = 0.07f),
                    0.56f to Color.Transparent,
                    1f to Color.Transparent,
                    start = Offset(0f, 0f),
                    end = Offset(w, h)
                )
            )
            // garis dalam: ketebalan kaca
            drawPath(
                path,
                Brush.linearGradient(
                    listOf(Color.White.copy(alpha = 0.22f), Color.Transparent, Color.White.copy(alpha = 0.10f)),
                    start = Offset(0f, 0f),
                    end = Offset(w, h)
                ),
                style = androidx.compose.ui.graphics.drawscope.Stroke(width = 3.dp.toPx())
            )
        }
    }
    .border(
        1.dp,
        Brush.linearGradient(
            0f to Color.White.copy(alpha = 0.85f),
            0.35f to Color.White.copy(alpha = 0.14f),
            0.65f to Color.White.copy(alpha = 0.06f),
            1f to Color.White.copy(alpha = 0.50f),
            start = Offset.Zero,
            end = Offset.Infinite
        ),
        shape
    )

/**
 * Liquid Glass: lebih bening daripada [realGlass], seperti tetesan kaca yang tebal.
 *  - badan hampir tak berwarna (isi di belakangnya tetap kelihatan),
 *  - lekuk cahaya tebal di separuh atas dan bayangan dalam yang halus di dasar (kesan cembung),
 *  - tepi berpendar: putih di sisi yang kena cahaya, sedikit sian di kanan atas dan magenta di kiri bawah
 *    (pecahan warna seperti kaca sungguhan), plus garis dalam tipis.
 */
fun Modifier.liquidGlass(shape: Shape, accent: Color): Modifier = this
    .background(
        Brush.verticalGradient(
            0f to Color.White.copy(alpha = 0.14f),
            0.5f to Color.White.copy(alpha = 0.04f),
            1f to lerp(Color.White, accent, 0.35f).copy(alpha = 0.09f)
        ),
        shape
    )
    .drawBehind {
        val path = outlineToPath(shape.createOutline(size, layoutDirection, this))
        val w = size.width
        val h = size.height
        clipPath(path) {
            // lekuk cahaya atas
            drawRect(
                Brush.verticalGradient(
                    0f to Color.White.copy(alpha = 0.34f),
                    0.28f to Color.White.copy(alpha = 0.08f),
                    0.5f to Color.Transparent,
                    startY = 0f,
                    endY = h
                )
            )
            // bayangan dalam di dasar (kedalaman)
            drawRect(
                Brush.verticalGradient(
                    0.6f to Color.Transparent,
                    1f to Color.Black.copy(alpha = 0.16f),
                    startY = 0f,
                    endY = h
                )
            )
            // kilau titik di kiri atas
            drawRect(
                Brush.radialGradient(
                    listOf(Color.White.copy(alpha = 0.40f), Color.Transparent),
                    center = Offset(w * 0.10f, h * 0.02f),
                    radius = maxOf(w, h) * 0.45f
                )
            )
            // garis dalam
            drawPath(
                path,
                Brush.linearGradient(
                    listOf(Color.White.copy(alpha = 0.30f), Color.Transparent, Color.White.copy(alpha = 0.14f)),
                    start = Offset(0f, 0f),
                    end = Offset(w, h)
                ),
                style = androidx.compose.ui.graphics.drawscope.Stroke(width = 4.dp.toPx())
            )
        }
    }
    .border(
        1.4.dp,
        Brush.linearGradient(
            0f to Color.White.copy(alpha = 0.95f),
            0.30f to Color(0xFF9FE9FF).copy(alpha = 0.40f),
            0.55f to Color.White.copy(alpha = 0.10f),
            0.80f to Color(0xFFFFA6F2).copy(alpha = 0.42f),
            1f to Color.White.copy(alpha = 0.70f),
            start = Offset.Zero,
            end = Offset.Infinite
        ),
        shape
    )

/** Latar utama aplikasi sesuai pilihan "Latar aplikasi". [cover] dipakai kalau pilihannya "Ikuti lagu". */
@Composable
fun Modifier.appBackground(cover: CoverTheme): Modifier {
    val c = AppTheme.colors
    return when (AppTheme.bgFx) {
        BgFx.COVER -> this.background(
            Brush.verticalGradient(
                0f to cover.top,
                0.40f to cover.mid,
                0.75f to cover.bottom,
                1f to c.background
            )
        )

        BgFx.SOLID -> this.background(c.background)

        BgFx.GRADIENT -> this.background(
            Brush.verticalGradient(
                0f to lerp(c.background, c.accent, 0.28f),
                0.45f to lerp(c.background, c.accent, 0.09f),
                1f to c.background
            )
        )

        BgFx.DUO -> this.background(
            Brush.linearGradient(
                0f to lerp(c.background, c.accent, 0.30f),
                0.55f to c.background,
                1f to lerp(c.background, c.accentSoft, 0.24f)
            )
        )

        BgFx.AURORA -> this.background(c.background).aurora(c.accent, c.accentSoft)
    }
}

/** Dua gumpalan cahaya yang bergerak pelan. Hanya menggambar ulang (tidak menyusun ulang) tiap frame. */
@Composable
private fun Modifier.aurora(a: Color, b: Color): Modifier {
    val t = rememberInfiniteTransition(label = "aurora")
    val p by t.animateFloat(
        initialValue = 0f,
        targetValue = 1f,
        animationSpec = infiniteRepeatable(tween(22_000, easing = LinearEasing), RepeatMode.Restart),
        label = "auroraPhase"
    )
    return this.drawBehind {
        val w = size.width
        val h = size.height
        val r = (w * 0.95f).coerceAtLeast(1f)
        val ang = p * 2f * PI.toFloat()
        val c1 = Offset(w * (0.25f + 0.22f * sin(ang)), h * (0.16f + 0.08f * cos(ang)))
        val c2 = Offset(w * (0.80f - 0.20f * cos(ang)), h * (0.55f + 0.10f * sin(ang)))
        drawCircle(Brush.radialGradient(listOf(a.copy(alpha = 0.38f), Color.Transparent), center = c1, radius = r), radius = r, center = c1)
        drawCircle(Brush.radialGradient(listOf(b.copy(alpha = 0.26f), Color.Transparent), center = c2, radius = r), radius = r, center = c2)
    }
}
