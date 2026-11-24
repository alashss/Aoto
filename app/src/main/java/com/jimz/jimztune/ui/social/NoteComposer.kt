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
import androidx.compose.foundation.lazy.LazyColumn
import androidx.compose.foundation.lazy.items
import androidx.compose.foundation.pager.HorizontalPager
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

/**
 * Pembuat catatan gaya Instagram: halaman utama (teks + tombol lagu + palet), halaman cari lagu,
 * halaman pilih satu baris lirik, dan halaman editor warna terpisah (Terapkan / Hapus).
 */
@Composable
fun NoteComposer(
    name: String, avatar: String?, userId: String?, initialText: String,
    onClose: () -> Unit, onShare: (text: String, color: String?, song: String?, songId: String?, lyric: String?) -> Unit
) {
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
                "song" -> SongStage(onBack = { stage = "edit" }, onPick = { song = it; lyric = null; stage = "lyric" })
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
    val accent = LocalCoverTheme.current.accent
    val bg = color?.let { parseHex(it, NoteDefault) } ?: NoteDefault
    Column(Modifier.fillMaxSize().statusBarsPadding().imePadding().navigationBarsPadding()) {
        Icon(Icons.Rounded.Close, "Tutup", tint = Color.White, modifier = Modifier.padding(16.dp).size(30.dp).clickable(onClick = onClose))
        Column(Modifier.weight(1f).fillMaxWidth().padding(top = 24.dp), horizontalAlignment = Alignment.CenterHorizontally) {
            // gelembung yang bisa diketik langsung
            Column(
                Modifier.clip(RoundedCornerShape(26.dp)).background(bg).padding(horizontal = 22.dp, vertical = 14.dp),
                horizontalAlignment = Alignment.CenterHorizontally
            ) {
                if (song != null) Row(verticalAlignment = Alignment.CenterVertically) {
                    Icon(Icons.Rounded.MusicNote, null, tint = Color.White, modifier = Modifier.size(16.dp))
                    Text(noteSongLabel(song), color = Color.White, fontSize = 12.sp, fontWeight = FontWeight.Bold, maxLines = 1, overflow = TextOverflow.Ellipsis, modifier = Modifier.padding(start = 4.dp))
                }
                Box(contentAlignment = Alignment.Center, modifier = Modifier.width(220.dp)) {
                    if (text.isEmpty()) Text("Siap untuk…", color = Color.White.copy(alpha = 0.6f), fontSize = 20.sp, textAlign = TextAlign.Center)
                    BasicTextField(
                        text, { onText(it.take(60)) }, textStyle = TextStyle(color = Color.White, fontSize = 20.sp, textAlign = TextAlign.Center),
                        cursorBrush = SolidColor(Color.White), modifier = Modifier.fillMaxWidth()
                    )
                }
                if (!lyric.isNullOrBlank()) Text("“$lyric”", color = Color.White.copy(alpha = 0.85f), fontSize = 12.sp, fontStyle = FontStyle.Italic, textAlign = TextAlign.Center, modifier = Modifier.padding(top = 2.dp))
            }
            Box(Modifier.size(10.dp).clip(CircleShape).background(bg))
            Box {
                AvatarWithDot(avatar, name, 96.dp, null, userId = userId)
                Box(
                    Modifier.align(Alignment.BottomEnd).size(34.dp).clip(CircleShape).background(Color(0xFF1B1F33)).border(2.dp, NoteBg, CircleShape).clickable(onClick = onPalette),
                    Alignment.Center
                ) { Icon(Icons.Rounded.Palette, "Warna gelembung", tint = Color(0xFFFF4D8D), modifier = Modifier.size(18.dp)) }
            }
            Spacer(Modifier.height(18.dp))
            Row(horizontalArrangement = Arrangement.spacedBy(12.dp)) {
                ToolChip(Icons.Rounded.MusicNote, if (song == null) "Lagu" else "Ganti", Color(0xFFFF4D8D), onSong)
                if (song != null) ToolChip(Icons.Rounded.FormatQuote, if (lyric == null) "Lirik" else "Ubah lirik", Color(0xFF8E7BFF), onLyric)
                if (song != null) ToolChip(Icons.Rounded.Close, "Hapus lagu", Color(0xFFFF8A80), onRemoveSong)
            }
        }
        Row(Modifier.fillMaxWidth().padding(horizontal = 20.dp, vertical = 14.dp), verticalAlignment = Alignment.CenterVertically) {
            Text("Terlihat 24 jam oleh teman", color = Color.White.copy(alpha = 0.6f), fontSize = 13.sp, modifier = Modifier.weight(1f))
            val ok = text.isNotBlank() || song != null
            Text(
                "Bagikan", color = if (ok) Color.Black else Color.White.copy(alpha = 0.4f), fontWeight = FontWeight.Bold,
                modifier = Modifier.clip(RoundedCornerShape(50)).background(if (ok) accent else Color.White.copy(alpha = 0.1f))
                    .clickable(enabled = ok, onClick = onShare).padding(horizontal = 26.dp, vertical = 13.dp)
            )
        }
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

// ============================================================ cari lagu

@Composable
private fun SongStage(onBack: () -> Unit, onPick: (SearchItem) -> Unit) {
    var q by remember { mutableStateOf("") }
    var items by remember { mutableStateOf<List<SearchItem>?>(null) }
    var loading by remember { mutableStateOf(false) }
    var chip by remember { mutableStateOf(0) }
    val recent = remember { AppGraph.history.load().filter { it.type == SearchType.SONG }.take(30) }
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
        Row(Modifier.fillMaxWidth().padding(12.dp), verticalAlignment = Alignment.CenterVertically) {
            Icon(Icons.AutoMirrored.Rounded.ArrowBack, "Kembali", tint = Color.White, modifier = Modifier.padding(4.dp).size(28.dp).clickable(onClick = onBack))
            Row(
                Modifier.padding(start = 8.dp).weight(1f).clip(RoundedCornerShape(50)).background(Color.White.copy(alpha = 0.09f)).padding(horizontal = 14.dp, vertical = 11.dp),
                verticalAlignment = Alignment.CenterVertically
            ) {
                Icon(Icons.Rounded.Search, null, tint = Color.White.copy(alpha = 0.6f), modifier = Modifier.size(20.dp))
                Spacer(Modifier.width(8.dp))
                Box(Modifier.weight(1f)) {
                    if (q.isEmpty()) Text("Cari lagu…", color = Color.White.copy(alpha = 0.4f), fontSize = 15.sp)
                    BasicTextField(q, { q = it.take(60) }, singleLine = true, textStyle = TextStyle(color = Color.White, fontSize = 15.sp), cursorBrush = SolidColor(Color.White), modifier = Modifier.fillMaxWidth())
                }
            }
        }
        Row(Modifier.padding(horizontal = 16.dp, vertical = 4.dp), horizontalArrangement = Arrangement.spacedBy(8.dp)) {
            listOf("Untuk Anda", "Sedang tren").forEachIndexed { i, l ->
                Text(
                    l, fontSize = 13.sp, fontWeight = FontWeight.SemiBold, color = if (chip == i) Color.Black else Color.White,
                    modifier = Modifier.clip(RoundedCornerShape(50)).background(if (chip == i) Color.White else Color.White.copy(alpha = 0.1f)).clickable { chip = i }.padding(horizontal = 16.dp, vertical = 8.dp)
                )
            }
        }
        val playing = AppGraph.activity.curId?.let { id -> AppGraph.activity.curTitle?.let { t ->
            SearchItem(id, SearchType.SONG, t, AppGraph.activity.curArtist.orEmpty(), "https://i.ytimg.com/vi/$id/hqdefault.jpg")
        } }
        LazyColumn(contentPadding = PaddingValues(bottom = 24.dp)) {
            if (q.isBlank() && chip == 0 && recent.isNotEmpty()) {
                items(recent, key = { "r" + it.id }) { SongRow(it, onPick) }
            }
            if (q.isBlank() && chip == 0 && playing != null) {
                item { Text("Sedang diputar", color = Color.White.copy(alpha = 0.6f), fontSize = 12.sp, fontWeight = FontWeight.Bold, modifier = Modifier.padding(start = 20.dp, top = 8.dp, bottom = 4.dp)) }
                item { SongRow(playing, onPick) }
            }
            val l = items
            if (loading) item { Box(Modifier.fillMaxWidth().padding(24.dp), Alignment.Center) { CircularProgressIndicator(color = Color.White) } }
            else if (l != null && l.isEmpty()) item { Text("Lagu tidak ditemukan.", color = Color.White.copy(alpha = 0.55f), modifier = Modifier.padding(24.dp)) }
            else if (l != null) items(l, key = { it.id }) { SongRow(it, onPick) }
            else if (q.isBlank() && playing == null) item { Text("Ketik judul atau nama artis.", color = Color.White.copy(alpha = 0.55f), modifier = Modifier.padding(24.dp)) }
        }
    }
}

@Composable
private fun SongRow(s: SearchItem, onPick: (SearchItem) -> Unit) {
    Row(Modifier.fillMaxWidth().clickable { onPick(s) }.padding(horizontal = 16.dp, vertical = 8.dp), verticalAlignment = Alignment.CenterVertically) {
        SongImage(s.thumbnailUrl, s.id, Modifier.size(50.dp), RoundedCornerShape(8.dp))
        Spacer(Modifier.width(12.dp))
        Column(Modifier.weight(1f)) {
            Text(s.title, color = Color.White, fontWeight = FontWeight.SemiBold, fontSize = 15.sp, maxLines = 1, overflow = TextOverflow.Ellipsis)
            Text(s.subtitle.substringBefore(" • ") + (s.durationText?.let { " • $it" } ?: ""), color = Color.White.copy(alpha = 0.6f), fontSize = 13.sp, maxLines = 1, overflow = TextOverflow.Ellipsis)
        }
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
