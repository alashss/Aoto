package com.jimz.jimztune.ui.ranking

import androidx.compose.foundation.background
import androidx.compose.foundation.border
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.shape.CircleShape
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clip
import androidx.compose.ui.graphics.Brush
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.layout.ContentScale
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.unit.Dp
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import coil.compose.AsyncImage
import com.jimz.jimztune.data.stats.RankKind

object RankColors {
    val Gold = Color(0xFFFFC857)
    val Silver = Color(0xFFC9D2E3)
    val Bronze = Color(0xFFE39A6B)

    fun medal(place: Int): Color = when (place) {
        1 -> Gold
        2 -> Silver
        3 -> Bronze
        else -> Color.White.copy(alpha = 0.6f)
    }

    /** Warna latar kartu / hero tiap jenis peringkat. */
    fun gradient(kind: RankKind): List<Color> = when (kind) {
        RankKind.LISTEN -> listOf(Color(0xFF17406B), Color(0xFF1D2C6E))
        RankKind.DONATE -> listOf(Color(0xFF5E2D55), Color(0xFF2B2B6B))
    }
}

private val AvatarPalette = listOf(
    Color(0xFF5B7CFA) to Color(0xFF8E5BFA),
    Color(0xFF2FA89A) to Color(0xFF3F7BC4),
    Color(0xFFD9627E) to Color(0xFF9A4AA3),
    Color(0xFFE08A3A) to Color(0xFFC4505A),
    Color(0xFF4AA3D6) to Color(0xFF5B5BD6),
    Color(0xFF6AB06A) to Color(0xFF2F8AA8)
)

/** Foto profil bulat; kalau belum ada, huruf pertama nama di atas warna yang konsisten per nama. */
@Composable
fun RankAvatar(
    url: String?,
    name: String,
    size: Dp,
    modifier: Modifier = Modifier,
    ring: Color? = null
) {
    val (a, b) = AvatarPalette[Math.floorMod(name.hashCode(), AvatarPalette.size)]
    val ringMod = if (ring != null) Modifier.border(2.dp, ring, CircleShape).padding(2.5.dp) else Modifier
    Box(
        modifier = modifier
            .size(size)
            .then(ringMod)
            .clip(CircleShape)
            .background(Brush.linearGradient(listOf(a, b))),
        contentAlignment = Alignment.Center
    ) {
        Text(
            name.trim().firstOrNull()?.uppercase() ?: "?",
            color = Color.White,
            fontWeight = FontWeight.Bold,
            fontSize = (size.value * 0.4f).sp
        )
        if (!url.isNullOrBlank()) {
            // Ukuran decode dibatasi sesuai tampilan (px): foto besar tidak didecode penuh, jadi jauh lebih cepat.
            val px = with(androidx.compose.ui.platform.LocalDensity.current) { (size * 2f).roundToPx().coerceIn(48, 512) }
            AsyncImage(
                model = coil.request.ImageRequest.Builder(androidx.compose.ui.platform.LocalContext.current)
                    .data(url).size(px).memoryCacheKey("av|$url|$px").build(),
                contentDescription = null,
                contentScale = ContentScale.Crop,
                modifier = Modifier.fillMaxSize()
            )
        }
    }
}
