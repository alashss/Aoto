package com.jimz.jimztune.ui.components

import androidx.compose.foundation.background
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.size
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.rounded.MusicNote
import androidx.compose.material3.Icon
import androidx.compose.runtime.Composable
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.setValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clip
import androidx.compose.ui.graphics.Brush
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.graphics.Shape
import androidx.compose.ui.layout.ContentScale
import androidx.compose.ui.graphics.graphicsLayer
import androidx.compose.ui.platform.LocalContext
import androidx.compose.ui.unit.dp
import coil.compose.AsyncImage
import coil.request.CachePolicy
import coil.request.ImageRequest

private val VideoIdRegex = Regex("^[A-Za-z0-9_-]{11}$")

/**
 * Gambar sampul yang nyaris selalu tampil: kalau alamat gambar utama gagal dimuat, dicoba cadangan
 * (thumbnail YouTube dari id lagu, ukuran besar lalu kecil). Kalau semuanya gagal, tampil kotak bergradien
 * dengan not musik, bukan area kosong. Ketuk tidak diperlukan; gambar dimuat ulang otomatis saat daftar
 * digulir kembali karena kegagalan tidak disimpan di cache.
 */
@Composable
fun SongImage(
    url: String?,
    videoId: String?,
    modifier: Modifier = Modifier,
    shape: Shape = androidx.compose.foundation.shape.RoundedCornerShape(10.dp),
    contentScale: ContentScale = ContentScale.Crop
) {
    val context = LocalContext.current
    val candidates = remember(url, videoId) {
        buildList {
            if (!url.isNullOrBlank()) add(url.replace("http://", "https://"))
            if (videoId != null && VideoIdRegex.matches(videoId)) {
                add("https://i.ytimg.com/vi/$videoId/hqdefault.jpg")
                add("https://i.ytimg.com/vi/$videoId/mqdefault.jpg")
                add("https://i.ytimg.com/vi/$videoId/default.jpg")
            }
        }.distinct()
    }
    var index by remember(candidates) { mutableStateOf(0) }
    Box(
        modifier = modifier
            .clip(shape)
            .background(Brush.linearGradient(listOf(Color(0xFF2B2F6B), Color(0xFF14183F)))),
        contentAlignment = Alignment.Center
    ) {
        Icon(Icons.Rounded.MusicNote, null, tint = Color.White.copy(alpha = 0.35f), modifier = Modifier.size(24.dp))
        if (index < candidates.size) {
            AsyncImage(
                model = ImageRequest.Builder(context)
                    .data(candidates[index])
                    .crossfade(180)
                    .memoryCachePolicy(CachePolicy.ENABLED)
                    .diskCachePolicy(CachePolicy.ENABLED)
                    .build(),
                contentDescription = null,
                contentScale = contentScale,
                onError = { index++ },
                // Thumbnail ytimg berbingkai hitam atas-bawah (4:3 berisi 16:9): perbesar agar tidak ada ruang kosong.
                modifier = Modifier.fillMaxSize().then(
                    if (candidates[index].contains("ytimg.com/vi/") && !candidates[index].contains("maxres")) Modifier.graphicsLayer { scaleX = 1.34f; scaleY = 1.34f } else Modifier
                )
            )
        }
    }
}
