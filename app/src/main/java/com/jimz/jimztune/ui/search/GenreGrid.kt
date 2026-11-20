package com.jimz.jimztune.ui.search

import androidx.compose.foundation.background
import androidx.compose.foundation.clickable
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.width
import androidx.compose.foundation.layout.PaddingValues
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.offset
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.lazy.grid.GridCells
import androidx.compose.foundation.lazy.grid.GridItemSpan
import androidx.compose.foundation.lazy.grid.LazyVerticalGrid
import androidx.compose.foundation.lazy.grid.items
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.rounded.Close
import androidx.compose.material.icons.rounded.History
import androidx.compose.material.icons.rounded.MusicNote
import androidx.compose.material3.Icon
import androidx.compose.material3.IconButton
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.runtime.LaunchedEffect
import androidx.compose.ui.draw.shadow
import androidx.compose.ui.layout.ContentScale
import coil.compose.AsyncImage
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clip
import androidx.compose.ui.draw.rotate
import androidx.compose.ui.graphics.Brush
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.text.style.TextOverflow
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import com.jimz.jimztune.ui.components.LocalOverlayPadding
import com.jimz.jimztune.ui.theme.LocalCoverTheme

private data class Genre(val label: String, val query: String, val from: Color, val to: Color)

// Warna sengaja kalem (tidak mencolok) supaya tidak mengganggu.
private val GENRES = listOf(
    Genre("Pop", "lagu pop terbaru", Color(0xFF3F5BC4), Color(0xFF5A4AA3)),
    Genre("Rock", "rock songs classic hits", Color(0xFFA33F55), Color(0xFF5E2542)),
    Genre("Dangdut", "lagu dangdut terbaru", Color(0xFFB8683A), Color(0xFF9A3F4E)),
    Genre("Hip-Hop", "hip hop rap indonesia", Color(0xFF4A3FA3), Color(0xFF232A63)),
    Genre("R&B", "r&b soul songs", Color(0xFF6E4AA3), Color(0xFF9A3F82)),
    Genre("Jazz", "jazz santai playlist", Color(0xFF2F6AA8), Color(0xFF244A78)),
    Genre("Indie", "indie indonesia senja", Color(0xFF2F9A94), Color(0xFF3F5BA8)),
    Genre("K-Pop", "kpop hits", Color(0xFFA84A7E), Color(0xFF6E4AA3)),
    Genre("Anime & J-Pop", "anime songs jpop", Color(0xFF3F5BC4), Color(0xFF2F9A94)),
    Genre("EDM", "edm party hits", Color(0xFF2F8AB0), Color(0xFF4A3FA3)),
    Genre("Lo-fi", "lofi chill beats", Color(0xFF4A5499), Color(0xFF252A63)),
    Genre("Akustik", "lagu akustik cover", Color(0xFFA8823A), Color(0xFF9A4A4E)),
    Genre("Metal", "metal songs best", Color(0xFF3C4160), Color(0xFF14172B)),
    Genre("Reggae", "reggae ska indonesia", Color(0xFF3A9A63), Color(0xFF2F6AA8)),
    Genre("Religi", "lagu religi", Color(0xFF2F8AA8), Color(0xFF244A78)),
    Genre("Klasik", "classical music relaxing", Color(0xFF6A5AA8), Color(0xFF3F4B7E))
)

@Composable
fun GenreGrid(
    covers: Map<String, String>,
    onNeedCover: (String) -> Unit,
    history: List<String>,
    onPick: (String) -> Unit,
    onRemoveHistory: (String) -> Unit,
    onClearHistory: () -> Unit
) {
    val overlay = LocalOverlayPadding.current
    val accent = LocalCoverTheme.current.accent
    LazyVerticalGrid(
        columns = GridCells.Fixed(2),
        contentPadding = PaddingValues(start = 20.dp, end = 20.dp, top = 4.dp, bottom = overlay),
        horizontalArrangement = Arrangement.spacedBy(14.dp),
        verticalArrangement = Arrangement.spacedBy(14.dp)
    ) {
        if (history.isNotEmpty()) {
            item(span = { GridItemSpan(maxLineSpan) }) {
                Row(verticalAlignment = Alignment.CenterVertically, modifier = Modifier.padding(top = 8.dp)) {
                    Text(
                        "Riwayat pencarian",
                        style = MaterialTheme.typography.titleLarge,
                        fontWeight = FontWeight.SemiBold,
                        modifier = Modifier.weight(1f)
                    )
                    Text(
                        "Hapus semua",
                        style = MaterialTheme.typography.labelLarge,
                        color = accent,
                        modifier = Modifier
                            .clip(RoundedCornerShape(8.dp))
                            .clickable(onClick = onClearHistory)
                            .padding(horizontal = 8.dp, vertical = 6.dp)
                    )
                }
            }
            history.forEach { q ->
                item(span = { GridItemSpan(maxLineSpan) }, key = "history:$q") {
                    Row(
                        modifier = Modifier
                            .fillMaxWidth()
                            .clip(RoundedCornerShape(12.dp))
                            .clickable { onPick(q) }
                            .padding(start = 4.dp, top = 2.dp, bottom = 2.dp),
                        verticalAlignment = Alignment.CenterVertically
                    ) {
                        Icon(
                            Icons.Rounded.History,
                            contentDescription = null,
                            tint = Color.White.copy(alpha = 0.6f),
                            modifier = Modifier.size(20.dp)
                        )
                        Spacer(Modifier.width(14.dp))
                        Text(
                            q,
                            style = MaterialTheme.typography.bodyLarge,
                            maxLines = 1,
                            overflow = TextOverflow.Ellipsis,
                            modifier = Modifier.weight(1f)
                        )
                        IconButton(onClick = { onRemoveHistory(q) }) {
                            Icon(
                                Icons.Rounded.Close,
                                contentDescription = "Hapus dari riwayat",
                                tint = Color.White.copy(alpha = 0.5f),
                                modifier = Modifier.size(18.dp)
                            )
                        }
                    }
                }
            }
        }
        item(span = { GridItemSpan(maxLineSpan) }) {
            Text(
                "Jelajahi genre",
                style = MaterialTheme.typography.titleLarge,
                fontWeight = FontWeight.SemiBold,
                modifier = Modifier.padding(top = 10.dp, bottom = 0.dp)
            )
        }
        items(GENRES, key = { it.query }) { genre ->
            LaunchedEffect(genre.query) { onNeedCover(genre.query) }
            GenreCard(genre, covers[genre.query]) { onPick(genre.query) }
        }
    }
}

@Composable
private fun GenreCard(genre: Genre, coverUrl: String?, onClick: () -> Unit) {
    Box(
        modifier = Modifier
            .fillMaxWidth()
            .height(84.dp)
            .clip(RoundedCornerShape(18.dp))
            .background(Brush.linearGradient(listOf(
                androidx.compose.ui.graphics.lerp(genre.from, com.jimz.jimztune.ui.theme.AppTheme.colors.accent, 0.30f),
                androidx.compose.ui.graphics.lerp(genre.to, com.jimz.jimztune.ui.theme.AppTheme.colors.surface, 0.45f)
            )))
            .clickable(onClick = onClick)
            .padding(14.dp)
    ) {
        if (coverUrl != null) {
            // Sampul contoh lagu genre ini, miring di pojok kanan bawah seperti kartu genre Spotify.
            AsyncImage(
                model = coverUrl,
                contentDescription = null,
                contentScale = ContentScale.Crop,
                modifier = Modifier
                    .align(Alignment.BottomEnd)
                    .offset(x = 14.dp, y = 12.dp)
                    .size(62.dp)
                    .rotate(18f)
                    .shadow(6.dp, RoundedCornerShape(8.dp))
                    .clip(RoundedCornerShape(8.dp))
            )
        } else {
            Icon(
                Icons.Rounded.MusicNote,
                contentDescription = null,
                tint = Color.White.copy(alpha = 0.14f),
                modifier = Modifier
                    .align(Alignment.BottomEnd)
                    .offset(x = 8.dp, y = 8.dp)
                    .size(60.dp)
                    .rotate(16f)
            )
        }
        Text(
            genre.label,
            color = Color.White,
            fontSize = 18.sp,
            fontWeight = FontWeight.Bold,
            modifier = Modifier.align(Alignment.TopStart)
        )
    }
}
