package com.jimz.jimztune.ui.library

import androidx.compose.animation.AnimatedVisibility
import androidx.compose.animation.fadeIn
import androidx.compose.animation.fadeOut
import androidx.compose.animation.shrinkVertically
import androidx.compose.foundation.background
import androidx.compose.foundation.clickable
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.layout.width
import androidx.compose.foundation.shape.CircleShape
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.rounded.Add
import androidx.compose.material.icons.rounded.Refresh
import androidx.compose.material3.CircularProgressIndicator
import androidx.compose.material3.Icon
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.runtime.LaunchedEffect
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableIntStateOf
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.setValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clip
import androidx.compose.ui.layout.ContentScale
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.text.style.TextOverflow
import androidx.compose.ui.unit.dp
import coil.compose.AsyncImage
import com.jimz.jimztune.AppGraph
import com.jimz.jimztune.data.model.SearchItem
import com.jimz.jimztune.data.model.SearchType
import com.jimz.jimztune.data.model.isLongVersion
import com.jimz.jimztune.data.stats.PlayLog
import java.time.LocalDate
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.async
import kotlinx.coroutines.awaitAll
import kotlinx.coroutines.coroutineScope
import kotlinx.coroutines.withContext
import kotlinx.coroutines.withTimeoutOrNull

/**
 * Saran lagu untuk sebuah playlist, seperti di Spotify: diambil dari artis lagu-lagu yang terakhir ditambahkan
 * (jadi ikut berubah mengikuti tema playlist). Playlist kosong memakai artis yang paling sering didengar.
 */
object SuggestionEngine {
    private val spam = Regex("(?i)\\b(lirik|lyrics?|cover|karaoke|nightcore|slowed|reverb|8d|sped up|instrumental)\\b")

    fun artistOf(s: SearchItem): String =
        s.subtitle.substringBefore(" • ").split(",", " & ", " x ", " feat.", " ft.").first()
            .removeSuffix(" - Topic").trim()

    /** Artis acuan: dari lagu terbaru di playlist, kalau kosong dari riwayat dengar. */
    fun seeds(songs: List<SearchItem>): List<String> {
        val fromList = songs.asReversed().map { artistOf(it) }.filter { it.isNotBlank() }.distinct().take(3)
        if (fromList.isNotEmpty()) return fromList
        val today = LocalDate.now()
        val top = try {
            PlayLog.topSongs(today.minusDays(60), today, 40).map { it.artist.substringBefore(",").trim() }
        } catch (_: Exception) { emptyList() }
        val recent = AppGraph.history.items.filter { it.type == SearchType.SONG }.map { artistOf(it) }
        return (top + recent).filter { it.isNotBlank() }.distinct().take(3)
    }

    suspend fun load(seeds: List<String>, existing: List<SearchItem>): List<SearchItem> {
        if (seeds.isEmpty()) return emptyList()
        val have = existing.map { it.id }.toHashSet()
        val haveKeys = existing.map { it.title.lowercase().trim() }.toHashSet()
        val lists = coroutineScope {
            seeds.map { a ->
                async(Dispatchers.IO) {
                    withTimeoutOrNull(9_000) {
                        try {
                            AppGraph.searchRepository.search(a, SearchType.SONG, fastest = true).items
                        } catch (_: Exception) { emptyList() }
                    }.orEmpty()
                }
            }.awaitAll()
        }
        // Selang-seling antar artis acuan supaya saran beragam.
        val out = ArrayList<SearchItem>()
        val seen = HashSet<String>()
        var i = 0
        while (out.size < 30 && lists.any { i < it.size }) {
            for (l in lists) {
                val s = l.getOrNull(i) ?: continue
                if (s.type != SearchType.SONG || s.isLongVersion || spam.containsMatchIn(s.title)) continue
                if (s.id in have || s.title.lowercase().trim() in haveKeys) continue
                if (seen.add(s.id)) out += s
            }
            i++
        }
        return out
    }
}

@Composable
fun PlaylistSuggestions(
    playlistId: String,
    songs: List<SearchItem>,
    onPlay: (List<SearchItem>, Int) -> Unit,
    modifier: Modifier = Modifier
) {
    val seeds = remember(songs.lastOrNull()?.id, songs.size) { SuggestionEngine.seeds(songs) }
    var pool by remember(playlistId) { mutableStateOf<List<SearchItem>>(emptyList()) }
    var loading by remember(playlistId) { mutableStateOf(true) }
    var page by remember(playlistId) { mutableIntStateOf(0) }
    var added by remember(playlistId) { mutableStateOf(setOf<String>()) }

    LaunchedEffect(seeds.joinToString("|")) {
        loading = true
        pool = withContext(Dispatchers.Default) { SuggestionEngine.load(seeds, songs) }
        page = 0
        loading = false
    }
    val ids = songs.map { it.id }.toHashSet()
    val remaining = pool.filter { it.id !in ids && it.id !in added }
    val shown = if (remaining.isEmpty()) emptyList() else {
        val start = (page * 6) % remaining.size
        (remaining + remaining).drop(start).take(minOf(6, remaining.size))
    }
    if (!loading && shown.isEmpty()) return

    Column(modifier = modifier.fillMaxWidth().padding(top = 8.dp, bottom = 6.dp)) {
        Row(Modifier.fillMaxWidth().padding(horizontal = 20.dp), verticalAlignment = Alignment.CenterVertically) {
            Column(Modifier.weight(1f)) {
                Text("Saran lagu", style = MaterialTheme.typography.titleMedium, fontWeight = FontWeight.Bold)
                Text(
                    if (seeds.isEmpty()) "Dari yang sering kamu dengar" else "Mirip " + seeds.take(2).joinToString(" & "),
                    style = MaterialTheme.typography.bodySmall,
                    color = MaterialTheme.colorScheme.onSurfaceVariant,
                    maxLines = 1, overflow = TextOverflow.Ellipsis
                )
            }
            if (loading) CircularProgressIndicator(Modifier.size(20.dp), strokeWidth = 2.dp)
            else Box(
                Modifier.size(36.dp).clip(CircleShape).clickable { page++ },
                contentAlignment = Alignment.Center
            ) { Icon(Icons.Rounded.Refresh, "Saran lain", modifier = Modifier.size(20.dp), tint = MaterialTheme.colorScheme.onSurfaceVariant) }
        }
        shown.forEachIndexed { idx, s ->
            AnimatedVisibility(
                visible = true,
                enter = fadeIn(),
                exit = fadeOut() + shrinkVertically()
            ) {
                Row(
                    Modifier.fillMaxWidth().clickable { onPlay(shown, idx) }.padding(horizontal = 20.dp, vertical = 6.dp),
                    verticalAlignment = Alignment.CenterVertically
                ) {
                    AsyncImage(
                        s.thumbnailUrl, null, contentScale = ContentScale.Crop,
                        modifier = Modifier.size(48.dp).clip(RoundedCornerShape(10.dp))
                            .background(MaterialTheme.colorScheme.surfaceVariant)
                    )
                    Spacer(Modifier.width(12.dp))
                    Column(Modifier.weight(1f)) {
                        Text(s.title, style = MaterialTheme.typography.bodyLarge, fontWeight = FontWeight.Medium, maxLines = 1, overflow = TextOverflow.Ellipsis)
                        Text(s.subtitle.substringBefore(" • "), style = MaterialTheme.typography.bodySmall, color = MaterialTheme.colorScheme.onSurfaceVariant, maxLines = 1, overflow = TextOverflow.Ellipsis)
                    }
                    Box(
                        Modifier.size(38.dp).clip(CircleShape).background(MaterialTheme.colorScheme.primary.copy(alpha = 0.16f))
                            .clickable {
                                if (AppGraph.library.addToPlaylist(playlistId, s)) added = added + s.id
                            },
                        contentAlignment = Alignment.Center
                    ) { Icon(Icons.Rounded.Add, "Tambah ke playlist", modifier = Modifier.size(22.dp), tint = MaterialTheme.colorScheme.primary) }
                }
            }
        }
    }
}
