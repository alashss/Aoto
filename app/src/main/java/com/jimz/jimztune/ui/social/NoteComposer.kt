@file:OptIn(androidx.compose.foundation.ExperimentalFoundationApi::class)

package com.jimz.jimztune.ui.social

import androidx.compose.animation.AnimatedContent
import androidx.compose.animation.core.tween
import androidx.compose.animation.fadeIn
import androidx.compose.animation.fadeOut
import androidx.compose.animation.slideInHorizontally
import androidx.compose.animation.slideOutHorizontally
import androidx.compose.animation.togetherWith
import androidx.activity.compose.BackHandler
import androidx.compose.foundation.background
import androidx.compose.foundation.border
import androidx.compose.foundation.clickable
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.PaddingValues
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.imePadding
import androidx.compose.foundation.layout.navigationBarsPadding
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.layout.statusBarsPadding
import androidx.compose.foundation.layout.width
import androidx.compose.foundation.layout.widthIn
import androidx.compose.foundation.lazy.LazyColumn
import androidx.compose.foundation.lazy.items
import androidx.compose.foundation.pager.HorizontalPager
import androidx.compose.foundation.basicMarquee
import androidx.compose.animation.core.LinearEasing
import androidx.compose.animation.core.RepeatMode
import androidx.compose.animation.core.animateFloat
import androidx.compose.animation.core.infiniteRepeatable
import androidx.compose.animation.core.rememberInfiniteTransition
import androidx.compose.material.icons.rounded.Album
import androidx.compose.material.icons.rounded.BookmarkBorder
import androidx.compose.material.icons.rounded.Check
import androidx.compose.material.icons.rounded.Pause
import androidx.compose.material.icons.rounded.PlayArrow
import androidx.compose.ui.graphics.Brush
import androidx.compose.ui.unit.TextUnit
import androidx.compose.foundation.pager.rememberPagerState
import androidx.compose.foundation.shape.CircleShape
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.foundation.text.BasicTextField
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.automirrored.rounded.ArrowBack
import androidx.compose.material.icons.rounded.Close
import androidx.compose.material.icons.rounded.FormatQuote
import androidx.compose.material.icons.rounded.MusicNote
import androidx.compose.material.icons.rounded.Palette
import androidx.compose.material.icons.rounded.Search
import androidx.compose.material3.CircularProgressIndicator
import androidx.compose.material3.Icon
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.runtime.LaunchedEffect
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.setValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clip
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.graphics.SolidColor
import androidx.compose.ui.text.TextStyle
import androidx.compose.ui.text.font.FontStyle
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.text.style.TextAlign
import androidx.compose.ui.text.style.TextOverflow
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import com.jimz.jimztune.AppGraph
import com.jimz.jimztune.data.model.SearchItem
import com.jimz.jimztune.data.model.SearchType
import com.jimz.jimztune.ui.components.SongImage
import com.jimz.jimztune.ui.theme.LocalCoverTheme
import kotlinx.coroutines.delay

private val NoteBg = Color(0xFF0B0E1F)
private val NoteDefault = Color(0xFF2B3050)

private fun durSec(t: String?): Int {
    val p = t?.trim()?.split(':', '.')?.mapNotNull { it.toIntOrNull() } ?: return 0
    return when (p.size) { 2 -> p[0] * 60 + p[1]; 3 -> p[0] * 3600 + p[1] * 60 + p[2]; else -> 0 }
}

fun noteSongLabel(s: SearchItem) = s.title + " · " + s.subtitle.substringBefore(" • ")

/** Baris lirik yang terisi putih dari kiri ke kanan berulang, seperti lirik catatan Instagram. */
@Composable
internal fun KaraokeLine(text: String, size: TextUnit, modifier: Modifier = Modifier) {
    val t = rememberInfiniteTransition(label = "karaoke")
    val f by t.animateFloat(0f, 1f, infiniteRepeatable(androidx.compose.animation.core.tween(4800, easing = LinearEasing), RepeatMode.Restart), label = "kf")
    val f2 = (f + 0.06f).coerceAtMost(1f)
    val dim = Color.White.copy(alpha = 0.42f)
    val brush = Brush.horizontalGradient(0f to Color.White, f to Color.White, f2 to dim, 1f to dim)
    Text(text, style = TextStyle(brush = brush, fontSize = size, fontWeight = FontWeight.SemiBold, textAlign = TextAlign.Center), modifier = modifier)
}

/** Pil lagu di dalam gelembung: ikon piringan + judul berjalan (marquee). */
@Composable
internal fun NoteSongPill(label: String, modifier: Modifier = Modifier, size: TextUnit = 12.sp) {
    Row(modifier, verticalAlignment = Alignment.CenterVertically) {
        Icon(Icons.Rounded.Album, null, tint = Color.White, modifier = Modifier.size(15.dp))
        Text(label, color = Color.White, fontSize = size, fontWeight = FontWeight.Bold, maxLines = 1, modifier = Modifier.padding(start = 4.dp).weight(1f, fill = false).basicMarquee(iterations = Int.MAX_VALUE, initialDelayMillis = 800))
    }
}

/**
 * Pembuat catatan gaya Instagram: halaman utama (teks + tombol lagu + palet), halaman cari lagu,
 * halaman pilih satu baris lirik, dan halaman editor warna terpisah (Terapkan / Hapus).
 */
@Composable
fun NoteComposer(
    name: String, avatar: String?, userId: String?, initialText: String,
    onClose: () -> Unit, onShare: (text: String, color: String?, song: String?, songId: String?, lyric: String?) -> Unit
) {
    val player: com.jimz.jimztune.player.PlayerViewModel = androidx.lifecycle.viewmodel.compose.viewModel()
    var stage by remember { mutableStateOf("edit") }
    var text by remember { mutableStateOf(initialText) }
    var color by remember { mutableStateOf<String?>(null) }
    var song by remember { mutableStateOf<SearchItem?>(null) }
    var lyric by remember { mutableStateOf<String?>(null) }

    BackHandler { if (stage == "edit") onClose() else stage = if (stage == "lyric") "song" else "edit" }
    Box(Modifier.fillMaxSize().background(NoteBg)) {
        AnimatedContent(
            targetState = stage,
            transitionSpec = {
                val deeper = targetState != "edit"
                (slideInHorizontally(tween(260)) { if (deeper) it / 3 else -it / 3 } + fadeIn(tween(260))) togetherWith
                    (slideOutHorizontally(tween(260)) { if (deeper) -it / 3 else it / 3 } + fadeOut(tween(200)))
            },
            label = "noteStage"
        ) { st ->
            when (st) {
                "color" -> ColorStage(text, color, onBack = { stage = "edit" }, onApply = { color = it; stage = "edit" }, onClear = { color = null; stage = "edit" })
                "song" -> SongStage(player, onBack = { stage = "edit" }, onPick = { song = it; lyric = null; stage = "lyric" })
                "lyric" -> LyricStage(song, onBack = { stage = "song" }, onPick = { lyric = it; stage = "edit" })
                else -> EditStage(
                    name, avatar, userId, text, { text = it }, color, song, lyric,
                    onClose = onClose, onPalette = { stage = "color" }, onSong = { stage = "song" }, onLyric = { stage = "lyric" },
                    onRemoveSong = { song = null; lyric = null },
                    onShare = { onShare(text.trim(), color, song?.let { noteSongLabel(it) }, song?.id, lyric) }
                )
            }
        }
    }
}

@Composable
private fun NoteBubble(text: String, color: String?, song: SearchItem?, lyric: String?, big: Boolean = true) {
    val bg = color?.let { parseHex(it, NoteDefault) } ?: NoteDefault
    Column(
        Modifier.clip(RoundedCornerShape(26.dp)).background(bg).padding(horizontal = 22.dp, vertical = 14.dp),
        horizontalAlignment = Alignment.CenterHorizontally
    ) {
        if (song != null) Row(verticalAlignment = Alignment.CenterVertically) {
            Icon(Icons.Rounded.MusicNote, null, tint = Color.White, modifier = Modifier.size(16.dp))
            Text(noteSongLabel(song), color = Color.White, fontSize = 12.sp, fontWeight = FontWeight.Bold, maxLines = 1, overflow = TextOverflow.Ellipsis, modifier = Modifier.padding(start = 4.dp))
        }
        Text(text, color = Color.White, fontSize = if (big) 20.sp else 18.sp, textAlign = TextAlign.Center)
        if (!lyric.isNullOrBlank()) Text("“$lyric”", color = Color.White.copy(alpha = 0.85f), fontSize = 12.sp, fontStyle = FontStyle.Italic, textAlign = TextAlign.Center, modifier = Modifier.padding(top = 4.dp))
    }
}

@Composable
private fun EditStage(
    name: String, avatar: String?, userId: String?, text: String, onText: (String) -> Unit, color: String?,
    song: SearchItem?, lyric: String?, onClose: () -> Unit, onPalette: () -> Unit, onSong: () -> Unit, onLyric: () -> Unit,
    onRemoveSong: () -> Unit, onShare: () -> Unit
) {
    val bg = color?.let { parseHex(it, NoteDefault) } ?: NoteDefault
    val blue = Color(0xFF4F5BFF)
    Column(Modifier.fillMaxSize().statusBarsPadding().imePadding().navigationBarsPadding()) {
        Icon(Icons.Rounded.Close, "Tutup", tint = Color.White, modifier = Modifier.padding(16.dp).size(28.dp).clickable(onClick = onClose))
        Column(Modifier.weight(1f).fillMaxWidth(), horizontalAlignment = Alignment.CenterHorizontally) {
            Spacer(Modifier.weight(0.6f))
            // gelembung yang bisa diketik langsung
            Column(
                Modifier.widthIn(min = 120.dp, max = 230.dp).clip(RoundedCornerShape(24.dp)).background(bg).padding(horizontal = 18.dp, vertical = 12.dp),
                horizontalAlignment = Alignment.CenterHorizontally
            ) {
                if (song != null) {
                    NoteSongPill(song.title, Modifier.fillMaxWidth(), 12.sp)
                    Text(song.subtitle.substringBefore(" • "), color = Color.White.copy(alpha = 0.75f), fontSize = 11.sp, maxLines = 1, overflow = TextOverflow.Ellipsis)
                }
                Box(contentAlignment = Alignment.Center, modifier = Modifier.fillMaxWidth().padding(top = 2.dp)) {
                    if (text.isEmpty()) Text("Catatan…", color = Color.White.copy(alpha = 0.55f), fontSize = 15.sp, textAlign = TextAlign.Center)
                    BasicTextField(
                        text, { onText(it.take(60)) }, textStyle = TextStyle(color = Color.White, fontSize = 15.sp, textAlign = TextAlign.Center),
                        cursorBrush = SolidColor(Color.White), modifier = Modifier.fillMaxWidth()
                    )
                }
                if (!lyric.isNullOrBlank()) KaraokeLine(lyric, 13.sp, Modifier.padding(top = 4.dp))
            }
            Box(Modifier.padding(top = 4.dp).size(10.dp).clip(CircleShape).background(bg))
            Box {
                AvatarWithDot(avatar, name, 110.dp, null, userId = userId)
                Box(
                    Modifier.align(Alignment.BottomEnd).size(34.dp).clip(CircleShape).background(Color(0xFF1B1F33)).border(2.dp, NoteBg, CircleShape).clickable(onClick = onPalette),
                    Alignment.Center
                ) { Icon(Icons.Rounded.Palette, "Warna gelembung", tint = Color(0xFFFF4D8D), modifier = Modifier.size(18.dp)) }
            }
            Spacer(Modifier.height(16.dp))
            Row(horizontalArrangement = Arrangement.spacedBy(14.dp)) {
                RoundTool(Icons.Rounded.MusicNote, Color(0xFFFF4D8D), onSong)
                if (song != null) RoundTool(Icons.Rounded.FormatQuote, Color(0xFF8E7BFF), onLyric)
                if (song != null) RoundTool(Icons.Rounded.Close, Color(0xFFFF8A80), onRemoveSong)
            }
            Spacer(Modifier.weight(1f))
            Text("Lagu yang Anda pilih mungkin tidak tersedia di semua negara.", color = Color.White.copy(alpha = 0.55f), fontSize = 11.sp, textAlign = TextAlign.Center, modifier = Modifier.padding(horizontal = 24.dp))
        }
        Row(Modifier.fillMaxWidth().padding(horizontal = 16.dp, vertical = 12.dp), verticalAlignment = Alignment.CenterVertically) {
            Row(
                Modifier.clip(RoundedCornerShape(50)).background(Color.White.copy(alpha = 0.08f)).padding(horizontal = 12.dp, vertical = 9.dp),
                verticalAlignment = Alignment.CenterVertically
            ) {
                Text("Bagikan kepada teman", color = Color.White, fontSize = 12.sp, fontWeight = FontWeight.SemiBold)
                Text("  ›", color = Color.White.copy(alpha = 0.7f), fontSize = 13.sp)
            }
            Spacer(Modifier.weight(1f))
            val ok = text.isNotBlank() || song != null
            Text(
                "Bagikan", color = if (ok) Color.White else Color.White.copy(alpha = 0.4f), fontWeight = FontWeight.Bold, fontSize = 14.sp,
                modifier = Modifier.clip(RoundedCornerShape(50)).background(if (ok) blue else Color.White.copy(alpha = 0.1f))
                    .clickable(enabled = ok, onClick = onShare).padding(horizontal = 24.dp, vertical = 11.dp)
            )
        }
    }
}

@Composable
private fun RoundTool(icon: androidx.compose.ui.graphics.vector.ImageVector, tint: Color, onClick: () -> Unit) {
    Box(Modifier.size(44.dp).clip(CircleShape).background(Color.White.copy(alpha = 0.10f)).clickable(onClick = onClick), Alignment.Center) {
        Icon(icon, null, tint = tint, modifier = Modifier.size(22.dp))
    }
}

@Composable
private fun ToolChip(icon: androidx.compose.ui.graphics.vector.ImageVector, label: String, tint: Color, onClick: () -> Unit) {
    Row(
        Modifier.clip(RoundedCornerShape(50)).background(Color.White.copy(alpha = 0.08f)).border(1.dp, Color.White.copy(alpha = 0.12f), RoundedCornerShape(50))
            .clickable(onClick = onClick).padding(horizontal = 14.dp, vertical = 10.dp),
        verticalAlignment = Alignment.CenterVertically
    ) {
        Icon(icon, null, tint = tint, modifier = Modifier.size(18.dp))
        Text(" $label", color = Color.White, fontSize = 13.sp, fontWeight = FontWeight.SemiBold)
    }
}

// ============================================================ editor warna (halaman terpisah)

@Composable
private fun ColorStage(text: String, current: String?, onBack: () -> Unit, onApply: (String) -> Unit, onClear: () -> Unit) {
    var temp by remember { mutableStateOf(current ?: NoteColorPages[0][4]) }
    val pager = rememberPagerState(pageCount = { NoteColorPages.size })
    val accent = LocalCoverTheme.current.accent
    Column(Modifier.fillMaxSize().statusBarsPadding().navigationBarsPadding()) {
        Box(Modifier.fillMaxWidth().padding(16.dp)) {
            Icon(Icons.AutoMirrored.Rounded.ArrowBack, "Kembali", tint = Color.White, modifier = Modifier.size(30.dp).clickable(onClick = onBack))
            Text("Editor Gelembung", color = Color.White, fontWeight = FontWeight.Bold, fontSize = 18.sp, modifier = Modifier.align(Alignment.Center))
        }
        Box(Modifier.weight(1f).fillMaxWidth(), Alignment.Center) {
            NoteBubble(text.ifBlank { "Siap untuk…" }, temp, null, null)
        }
        HorizontalPager(pager, Modifier.fillMaxWidth()) { page ->
            Row(Modifier.fillMaxWidth().padding(horizontal = 20.dp), horizontalArrangement = Arrangement.SpaceBetween) {
                NoteColorPages[page].forEach { c ->
                    val on = c.equals(temp, true)
                    Box(
                        Modifier.size(52.dp).clip(RoundedCornerShape(16.dp)).background(Color.White.copy(alpha = 0.1f))
                            .border(if (on) 2.dp else 0.dp, Color.White, RoundedCornerShape(16.dp)).clickable { temp = c }.padding(6.dp)
                    ) { Box(Modifier.fillMaxSize().clip(RoundedCornerShape(11.dp)).background(parseHex(c))) }
                }
            }
        }
        Row(Modifier.fillMaxWidth().padding(vertical = 14.dp), horizontalArrangement = Arrangement.Center) {
            repeat(NoteColorPages.size) { i ->
                Box(Modifier.padding(horizontal = 4.dp).size(8.dp).clip(CircleShape).background(if (pager.currentPage == i) Color.White else Color.White.copy(alpha = 0.3f)))
            }
        }
        Text(
            "Terapkan", color = Color.Black, fontWeight = FontWeight.Bold, textAlign = TextAlign.Center,
            modifier = Modifier.fillMaxWidth().padding(horizontal = 16.dp).clip(RoundedCornerShape(50)).background(accent).clickable { onApply(temp) }.padding(vertical = 15.dp)
        )
        Text("Hapus", color = accent, fontWeight = FontWeight.SemiBold, textAlign = TextAlign.Center, modifier = Modifier.fillMaxWidth().clickable(onClick = onClear).padding(vertical = 18.dp))
    }
}

// ============================================================ cari lagu (lembar musik gaya Instagram)

@Composable
private fun SongStage(player: com.jimz.jimztune.player.PlayerViewModel, onBack: () -> Unit, onPick: (SearchItem) -> Unit) {
    var q by remember { mutableStateOf("") }
    var items by remember { mutableStateOf<List<SearchItem>?>(null) }
    var loading by remember { mutableStateOf(false) }
    var chip by remember { mutableStateOf(0) }
    var sel by remember { mutableStateOf<SearchItem?>(null) }
    val recent = remember { AppGraph.history.load().filter { it.type == SearchType.SONG }.take(30) }
    val saved = remember { AppGraph.library.favorites.filter { it.type == SearchType.SONG } }
    LaunchedEffect(q, chip) {
        val query = if (q.trim().length >= 2) q.trim() else if (chip == 1) "lagu populer indonesia terbaru" else null
        if (query == null) { items = null; return@LaunchedEffect }
        if (q.isNotBlank()) delay(400)
        loading = true
        items = try {
            AppGraph.searchRepository.search(query, SearchType.SONG, quick = true).items.filter { it.type == SearchType.SONG }.take(25)
        } catch (e: kotlinx.coroutines.CancellationException) { throw e } catch (_: Throwable) { emptyList() }
        loading = false
    }
    Column(Modifier.fillMaxSize().statusBarsPadding().imePadding().navigationBarsPadding()) {
        Box(Modifier.align(Alignment.CenterHorizontally).padding(top = 8.dp).width(40.dp).height(4.dp).clip(CircleShape).background(Color.White.copy(alpha = 0.35f)))
        Row(Modifier.fillMaxWidth().padding(horizontal = 12.dp, vertical = 10.dp), verticalAlignment = Alignment.CenterVertically) {
            Icon(Icons.AutoMirrored.Rounded.ArrowBack, "Kembali", tint = Color.White, modifier = Modifier.padding(4.dp).size(26.dp).clickable(onClick = onBack))
            Row(
                Modifier.padding(start = 8.dp).weight(1f).clip(RoundedCornerShape(12.dp)).background(Color.White.copy(alpha = 0.09f)).padding(horizontal = 12.dp, vertical = 10.dp),
                verticalAlignment = Alignment.CenterVertically
            ) {
                Icon(Icons.Rounded.Search, null, tint = Color.White.copy(alpha = 0.6f), modifier = Modifier.size(20.dp))
                Spacer(Modifier.width(8.dp))
                Box(Modifier.weight(1f)) {
                    if (q.isEmpty()) Text("Cari…", color = Color.White.copy(alpha = 0.45f), fontSize = 15.sp)
                    BasicTextField(q, { q = it.take(60) }, singleLine = true, textStyle = TextStyle(color = Color.White, fontSize = 15.sp), cursorBrush = SolidColor(Color.White), modifier = Modifier.fillMaxWidth())
                }
            }
        }
        Row(Modifier.padding(horizontal = 16.dp, vertical = 4.dp), horizontalArrangement = Arrangement.spacedBy(8.dp)) {
            listOf("Untuk Anda", "Sedang tren", "Tersimpan").forEachIndexed { i, l ->
                Text(
                    l, fontSize = 13.sp, fontWeight = FontWeight.SemiBold, color = if (chip == i) Color.Black else Color.White,
                    modifier = Modifier.clip(RoundedCornerShape(10.dp)).background(if (chip == i) Color.White else Color.White.copy(alpha = 0.1f)).clickable { chip = i }.padding(horizontal = 14.dp, vertical = 8.dp)
                )
            }
        }
        val playing = AppGraph.activity.curId?.let { id -> AppGraph.activity.curTitle?.let { t ->
            SearchItem(id, SearchType.SONG, t, AppGraph.activity.curArtist.orEmpty(), "https://i.ytimg.com/vi/$id/hqdefault.jpg")
        } }
        fun choose(s: SearchItem) { sel = s; player.play(listOf(s), 0) }
        LazyColumn(Modifier.weight(1f), contentPadding = PaddingValues(bottom = 16.dp)) {
            if (q.isBlank() && chip == 0) {
                if (playing != null) {
                    item { Text("Sedang diputar", color = Color.White.copy(alpha = 0.6f), fontSize = 12.sp, fontWeight = FontWeight.Bold, modifier = Modifier.padding(start = 16.dp, top = 8.dp, bottom = 2.dp)) }
                    item { SongRow(playing, sel?.id == playing.id) { choose(it) } }
                }
                if (recent.isNotEmpty()) item { Text("Terakhir didengar", color = Color.White.copy(alpha = 0.6f), fontSize = 12.sp, fontWeight = FontWeight.Bold, modifier = Modifier.padding(start = 16.dp, top = 10.dp, bottom = 2.dp)) }
                items(recent, key = { "r" + it.id }) { SongRow(it, sel?.id == it.id) { s -> choose(s) } }
            }
            if (q.isBlank() && chip == 2) {
                if (saved.isEmpty()) item { Text("Belum ada lagu favorit.", color = Color.White.copy(alpha = 0.55f), modifier = Modifier.padding(24.dp)) }
                items(saved, key = { "s" + it.id }) { SongRow(it, sel?.id == it.id) { s -> choose(s) } }
            }
            val l = if (chip == 2 && q.isBlank()) null else items
            if (loading) item { Box(Modifier.fillMaxWidth().padding(24.dp), Alignment.Center) { CircularProgressIndicator(color = Color.White) } }
            else if (l != null && l.isEmpty()) item { Text("Lagu tidak ditemukan.", color = Color.White.copy(alpha = 0.55f), modifier = Modifier.padding(24.dp)) }
            else if (l != null) items(l, key = { "q" + it.id }) { SongRow(it, sel?.id == it.id) { s -> choose(s) } }
            else if (q.isBlank() && chip == 0 && playing == null && recent.isEmpty()) item { Text("Ketik judul atau nama artis.", color = Color.White.copy(alpha = 0.55f), modifier = Modifier.padding(24.dp)) }
        }
        // Bilah pratinjau: jeda / lanjut + centang untuk memakai lagu ini.
        val chosen = sel
        androidx.compose.animation.AnimatedVisibility(visible = chosen != null, enter = androidx.compose.animation.slideInVertically { it } + fadeIn(), exit = androidx.compose.animation.slideOutVertically { it } + fadeOut()) {
            if (chosen != null) Row(
                Modifier.fillMaxWidth().padding(10.dp).clip(RoundedCornerShape(14.dp)).background(Color(0xFF7A1620)).padding(horizontal = 12.dp, vertical = 10.dp),
                verticalAlignment = Alignment.CenterVertically
            ) {
                val playingNow = player.ui.isPlaying
                Box(Modifier.size(38.dp).clip(CircleShape).background(Color(0xFFE53935)).clickable { player.togglePlay() }, Alignment.Center) {
                    Icon(if (playingNow) Icons.Rounded.Pause else Icons.Rounded.PlayArrow, "Putar/jeda", tint = Color.White, modifier = Modifier.size(24.dp))
                }
                Column(Modifier.padding(horizontal = 12.dp).weight(1f)) {
                    Text(chosen.title, color = Color.White, fontWeight = FontWeight.SemiBold, fontSize = 14.sp, maxLines = 1, overflow = TextOverflow.Ellipsis)
                    Text(chosen.subtitle.substringBefore(" • "), color = Color.White.copy(alpha = 0.75f), fontSize = 12.sp, maxLines = 1, overflow = TextOverflow.Ellipsis)
                }
                Box(Modifier.size(38.dp).clip(CircleShape).clickable { onPick(chosen) }, Alignment.Center) {
                    Icon(Icons.Rounded.Check, "Pakai lagu ini", tint = Color.White, modifier = Modifier.size(26.dp))
                }
            }
        }
    }
}

@Composable
private fun SongRow(s: SearchItem, selected: Boolean, onPick: (SearchItem) -> Unit) {
    Row(
        Modifier.fillMaxWidth().background(if (selected) Color.White.copy(alpha = 0.07f) else Color.Transparent).clickable { onPick(s) }.padding(horizontal = 16.dp, vertical = 8.dp),
        verticalAlignment = Alignment.CenterVertically
    ) {
        SongImage(s.thumbnailUrl, s.id, Modifier.size(50.dp), RoundedCornerShape(6.dp))
        Spacer(Modifier.width(12.dp))
        Column(Modifier.weight(1f)) {
            Text(s.title, color = if (selected) Color(0xFF8FA0FF) else Color.White, fontWeight = FontWeight.SemiBold, fontSize = 15.sp, maxLines = 1, overflow = TextOverflow.Ellipsis)
            Text(s.subtitle.substringBefore(" • ") + (s.durationText?.let { " • $it" } ?: ""), color = Color.White.copy(alpha = 0.6f), fontSize = 13.sp, maxLines = 1, overflow = TextOverflow.Ellipsis)
        }
        Icon(Icons.Rounded.BookmarkBorder, null, tint = Color.White.copy(alpha = 0.8f), modifier = Modifier.padding(start = 8.dp).size(24.dp))
    }
}

// ============================================================ pilih satu baris lirik

@Composable
private fun LyricStage(song: SearchItem?, onBack: () -> Unit, onPick: (String?) -> Unit) {
    var lines by remember(song?.id) { mutableStateOf<List<String>?>(null) }
    LaunchedEffect(song?.id) {
        if (song == null) { lines = emptyList(); return@LaunchedEffect }
        lines = try {
            val opts = AppGraph.lyricsRepo.get(song, durSec(song.durationText))
            opts.firstOrNull()?.lines.orEmpty().map { it.text.trim() }.filter { it.length in 3..80 }.distinct().take(120)
        } catch (e: kotlinx.coroutines.CancellationException) { throw e } catch (_: Throwable) { emptyList() }
    }
    Column(Modifier.fillMaxSize().statusBarsPadding().navigationBarsPadding()) {
        Row(Modifier.fillMaxWidth().padding(16.dp), verticalAlignment = Alignment.CenterVertically) {
            Icon(Icons.AutoMirrored.Rounded.ArrowBack, "Kembali", tint = Color.White, modifier = Modifier.size(28.dp).clickable(onClick = onBack))
            Column(Modifier.padding(start = 14.dp).weight(1f)) {
                Text("Pilih satu baris lirik", color = Color.White, fontWeight = FontWeight.Bold, fontSize = 17.sp)
                if (song != null) Text(noteSongLabel(song), color = Color.White.copy(alpha = 0.6f), fontSize = 12.sp, maxLines = 1, overflow = TextOverflow.Ellipsis)
            }
            Text("Lewati", color = LocalCoverTheme.current.accent, fontWeight = FontWeight.SemiBold, modifier = Modifier.clickable { onPick(null) }.padding(8.dp))
        }
        val l = lines
        if (l == null) Box(Modifier.fillMaxSize(), Alignment.Center) { CircularProgressIndicator(color = Color.White) }
        else if (l.isEmpty()) Box(Modifier.fillMaxSize().padding(32.dp), Alignment.Center) {
            Text("Lirik lagu ini tidak ditemukan. Catatan tetap bisa dibagikan tanpa lirik.", color = Color.White.copy(alpha = 0.6f), textAlign = TextAlign.Center)
        } else LazyColumn(contentPadding = PaddingValues(horizontal = 16.dp, vertical = 8.dp), verticalArrangement = Arrangement.spacedBy(8.dp)) {
            items(l) { line ->
                Text(
                    line, color = Color.White, fontSize = 16.sp,
                    modifier = Modifier.fillMaxWidth().clip(RoundedCornerShape(16.dp)).background(Color.White.copy(alpha = 0.07f)).clickable { onPick(line) }.padding(horizontal = 16.dp, vertical = 14.dp)
                )
            }
        }
    }
}
