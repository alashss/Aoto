package com.jimz.jimztune.ui.profile

import androidx.compose.foundation.background
import com.jimz.jimztune.ui.theme.cardSurface
import androidx.compose.foundation.border
import androidx.compose.foundation.clickable
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.fillMaxHeight
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.layout.width
import androidx.compose.foundation.shape.CircleShape
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.runtime.LaunchedEffect
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateListOf
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.rememberCoroutineScope
import androidx.compose.runtime.setValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clip
import androidx.compose.ui.graphics.Brush
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.layout.ContentScale
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.text.style.TextOverflow
import androidx.compose.ui.unit.Dp
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import coil.compose.AsyncImage
import com.jimz.jimztune.AppGraph
import com.jimz.jimztune.data.profile.ListenApi
import com.jimz.jimztune.data.stats.ListenLevel
import com.jimz.jimztune.data.stats.formatListen
import com.jimz.jimztune.ui.components.GroupCard
import kotlinx.coroutines.launch
import java.time.Duration
import java.time.OffsetDateTime

private const val PAGE = 15

/** Blok "Statistik mendengar" (level + durasi) dan "Riwayat putar" untuk halaman Profil. */
@Composable
fun ListeningSection(accent: Color) {
    var stats by remember { mutableStateOf<ListenApi.Stats?>(null) }
    var failed by remember { mutableStateOf(false) }
    val history = remember { mutableStateListOf<ListenApi.HistoryItem>() }
    var historyLoaded by remember { mutableStateOf(false) }
    var more by remember { mutableStateOf(true) }
    var loadingMore by remember { mutableStateOf(false) }
    val scope = rememberCoroutineScope()

    LaunchedEffect(Unit) {
        AppGraph.listen.refresh()
        val s = ListenApi.stats()
        stats = s
        failed = s == null
        val h = ListenApi.history(PAGE, 0)
        if (h != null) {
            history.clear()
            history.addAll(h)
            more = h.size >= PAGE
        }
        historyLoaded = true
    }

    // Selama server belum menjawab, pakai angka dari perangkat (sudah termasuk detik yang belum terkirim).
    val total = maxOf(stats?.totalSeconds ?: 0L, AppGraph.listen.totalSeconds)
    val level = ListenLevel.level(total)
    val progress = ListenLevel.progress(total)

    Spacer(Modifier.height(14.dp))
    GroupCard(modifier = Modifier.padding(horizontal = 20.dp)) {
        Column(Modifier.padding(18.dp)) {
            SectionLabel("STATISTIK MENDENGAR", accent)
            Spacer(Modifier.height(12.dp))
            Row(verticalAlignment = Alignment.CenterVertically) {
                Box(
                    Modifier
                        .size(56.dp)
                        .clip(RoundedCornerShape(18.dp))
                        .background(Brush.linearGradient(listOf(accent, accent.copy(alpha = 0.5f)))),
                    contentAlignment = Alignment.Center
                ) {
                    Column(horizontalAlignment = Alignment.CenterHorizontally) {
                        Text("LVL", fontSize = 9.sp, fontWeight = FontWeight.Bold, color = Color.White.copy(alpha = 0.8f))
                        Text("$level", fontSize = 22.sp, fontWeight = FontWeight.Black, color = Color.White)
                    }
                }
                Spacer(Modifier.width(14.dp))
                Column(Modifier.weight(1f)) {
                    Text(formatListen(total), fontSize = 22.sp, fontWeight = FontWeight.Black)
                    Text("total waktu mendengar", fontSize = 12.sp, color = Color.White.copy(alpha = 0.6f))
                }
            }
            Spacer(Modifier.height(14.dp))
            Box(
                Modifier
                    .fillMaxWidth()
                    .height(8.dp)
                    .clip(CircleShape)
                    .background(Color.White.copy(alpha = 0.10f))
            ) {
                Box(
                    Modifier
                        .fillMaxHeight()
                        .fillMaxWidth(progress.coerceAtLeast(0.03f))
                        .clip(CircleShape)
                        .background(Brush.horizontalGradient(listOf(accent, Color(0xFFFFC857))))
                )
            }
            Spacer(Modifier.height(6.dp))
            Text(
                "${formatListen(ListenLevel.remaining(total))} lagi menuju Lvl. ${level + 1}",
                fontSize = 11.sp,
                color = Color.White.copy(alpha = 0.55f)
            )
            Spacer(Modifier.height(14.dp))
            val s = stats
            Row(horizontalArrangement = Arrangement.spacedBy(8.dp)) {
                MiniStat("Hari ini", s?.let { formatListen(it.todaySeconds) } ?: "–", Modifier.weight(1f))
                MiniStat("7 hari", s?.let { formatListen(it.weekSeconds) } ?: "–", Modifier.weight(1f))
                MiniStat("Hari aktif", s?.activeDays?.toString() ?: "–", Modifier.weight(1f))
                MiniStat("Diputar", s?.plays?.toString() ?: "–", Modifier.weight(1f))
            }
            if (s?.topArtist != null) {
                Spacer(Modifier.height(12.dp))
                Text(
                    "Artis favorit: ${s.topArtist}",
                    fontSize = 13.sp,
                    color = Color.White.copy(alpha = 0.75f),
                    maxLines = 1,
                    overflow = TextOverflow.Ellipsis
                )
            }
            if (failed) {
                Spacer(Modifier.height(10.dp))
                Text(
                    "Belum bisa mengambil statistik dari server. Angka di atas dari perangkat ini.",
                    fontSize = 11.sp,
                    color = Color.White.copy(alpha = 0.45f)
                )
            }
        }
    }

    Spacer(Modifier.height(14.dp))
    GroupCard(modifier = Modifier.padding(horizontal = 20.dp)) {
        Column(Modifier.padding(vertical = 18.dp)) {
            Box(Modifier.padding(horizontal = 18.dp)) { SectionLabel("RIWAYAT PUTAR", accent) }
            Spacer(Modifier.height(8.dp))
            when {
                !historyLoaded -> Text("Memuat…", color = Color.White.copy(alpha = 0.5f), modifier = Modifier.padding(horizontal = 18.dp, vertical = 8.dp))
                history.isEmpty() -> Text(
                    "Belum ada riwayat. Putar lagu dan riwayatnya akan tersimpan di akunmu.",
                    color = Color.White.copy(alpha = 0.5f),
                    style = MaterialTheme.typography.bodyMedium,
                    modifier = Modifier.padding(horizontal = 18.dp, vertical = 8.dp)
                )
                else -> history.forEach { h -> HistoryRow(h) }
            }
            if (historyLoaded && history.isNotEmpty() && more) {
                Text(
                    if (loadingMore) "Memuat…" else "Muat lebih banyak",
                    color = accent,
                    fontWeight = FontWeight.SemiBold,
                    modifier = Modifier
                        .padding(horizontal = 10.dp, vertical = 2.dp)
                        .clip(RoundedCornerShape(12.dp))
                        .clickable(enabled = !loadingMore) {
                            loadingMore = true
                            scope.launch {
                                val next = ListenApi.history(PAGE, history.size)
                                if (next != null) {
                                    history.addAll(next)
                                    more = next.size >= PAGE
                                }
                                loadingMore = false
                            }
                        }
                        .padding(horizontal = 8.dp, vertical = 10.dp)
                )
            }
        }
    }
}

@Composable
private fun SectionLabel(text: String, accent: Color) {
    Text(text, fontSize = 11.sp, fontWeight = FontWeight.Bold, letterSpacing = 1.2.sp, color = accent)
}

@Composable
private fun MiniStat(label: String, value: String, modifier: Modifier) {
    val shape = RoundedCornerShape(14.dp)
    Column(
        modifier
            .clip(shape)
            .cardSurface(shape)
            .padding(vertical = 10.dp, horizontal = 8.dp),
        horizontalAlignment = Alignment.CenterHorizontally
    ) {
        Text(value, fontSize = 15.sp, fontWeight = FontWeight.Black, maxLines = 1)
        Text(label, fontSize = 10.sp, color = Color.White.copy(alpha = 0.55f), maxLines = 1)
    }
}

@Composable
private fun HistoryRow(h: ListenApi.HistoryItem) {
    Row(
        Modifier
            .fillMaxWidth()
            .padding(horizontal = 18.dp, vertical = 7.dp),
        verticalAlignment = Alignment.CenterVertically
    ) {
        AsyncImage(
            model = h.thumbnail,
            contentDescription = null,
            contentScale = ContentScale.Crop,
            modifier = Modifier
                .size(46.dp)
                .clip(RoundedCornerShape(10.dp))
                .background(Color.White.copy(alpha = 0.08f))
        )
        Spacer(Modifier.width(12.dp))
        Column(Modifier.weight(1f)) {
            Text(h.title, fontWeight = FontWeight.SemiBold, maxLines = 1, overflow = TextOverflow.Ellipsis)
            Text(
                listOf(h.artist, ago(h.playedAt)).filter { it.isNotBlank() }.joinToString(" · "),
                fontSize = 12.sp,
                color = Color.White.copy(alpha = 0.55f),
                maxLines = 1,
                overflow = TextOverflow.Ellipsis
            )
        }
        if (h.seconds > 0) {
            Text(formatListen(h.seconds.toLong()), fontSize = 12.sp, color = Color.White.copy(alpha = 0.7f))
        }
    }
}

/** "baru saja", "5 menit lalu", "3 jam lalu", "2 hari lalu". */
private fun ago(iso: String): String = try {
    val d = Duration.between(OffsetDateTime.parse(iso).toInstant(), java.time.Instant.now())
    val m = d.toMinutes()
    when {
        m < 1 -> "baru saja"
        m < 60 -> "$m menit lalu"
        m < 1440 -> "${m / 60} jam lalu"
        m < 43200 -> "${m / 1440} hari lalu"
        else -> "${m / 43200} bulan lalu"
    }
} catch (_: Exception) {
    ""
}
