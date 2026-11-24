package com.jimz.jimztune.ui.player

import androidx.activity.compose.BackHandler
import androidx.compose.animation.AnimatedVisibility
import androidx.compose.animation.core.tween
import androidx.compose.animation.fadeIn
import androidx.compose.animation.fadeOut
import androidx.compose.animation.slideOutVertically
import androidx.compose.foundation.background
import androidx.compose.foundation.border
import androidx.compose.foundation.clickable
import androidx.compose.foundation.gestures.detectVerticalDragGestures
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.navigationBarsPadding
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.layout.statusBarsPadding
import androidx.compose.foundation.layout.width
import androidx.compose.foundation.lazy.LazyColumn
import androidx.compose.foundation.lazy.itemsIndexed
import androidx.compose.foundation.lazy.rememberLazyListState
import androidx.compose.foundation.shape.CircleShape
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.rounded.Lock
import androidx.compose.material.icons.rounded.Pause
import androidx.compose.material.icons.rounded.PlayArrow
import androidx.compose.material.icons.rounded.SkipNext
import androidx.compose.material.icons.rounded.SkipPrevious
import androidx.compose.material3.Icon
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.runtime.DisposableEffect
import androidx.compose.runtime.LaunchedEffect
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.setValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clip
import androidx.compose.ui.graphics.Brush
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.graphics.compositeOver
import androidx.compose.ui.input.pointer.pointerInput
import androidx.compose.ui.layout.ContentScale
import androidx.compose.ui.platform.LocalView
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.text.style.TextAlign
import androidx.compose.ui.text.style.TextOverflow
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import coil.compose.AsyncImage
import com.jimz.jimztune.data.lyrics.LyricsState
import com.jimz.jimztune.player.PlayerViewModel
import com.jimz.jimztune.ui.theme.LocalCoverTheme
import kotlinx.coroutines.delay
import java.text.SimpleDateFormat
import java.util.Date
import java.util.Locale

/** Saklar global Mode Kunci Layar (dibuka dari panel lirik atau Pengaturan). */
object LockMode {
    var active by mutableStateOf(false)
}

private fun fmt(ms: Long): String {
    val s = (ms / 1000).coerceAtLeast(0)
    return "%d:%02d".format(s / 60, s % 60)
}

/**
 * Tampilan seperti layar kunci: jam besar, lirik berjalan, kartu pemutar dengan tombol sebelumnya / jeda / berikutnya.
 * Layar tetap menyala selama mode ini aktif. Geser ke atas (atau tombol kembali) untuk keluar.
 */
@Composable
fun LockModeHost(player: PlayerViewModel) {
    AnimatedVisibility(
        visible = LockMode.active && player.ui.current != null,
        enter = fadeIn(tween(300)),
        exit = slideOutVertically(tween(300)) { -it } + fadeOut(tween(300))
    ) { LockModeScreen(player) }
}

@Composable
private fun LockModeScreen(player: PlayerViewModel) {
    val cover = LocalCoverTheme.current
    val ui = player.ui
    val song = ui.current ?: return
    val view = LocalView.current
    BackHandler { LockMode.active = false }
    DisposableEffect(Unit) {
        view.keepScreenOn = true
        onDispose { view.keepScreenOn = false }
    }
    // Pastikan lirik dimuat walau panel lirik tidak sedang dibuka.
    LaunchedEffect(song.id) { player.setLyricsVisible(true) }
    DisposableEffect(Unit) { onDispose { player.setLyricsVisible(false) } }

    var now by remember { mutableStateOf(System.currentTimeMillis()) }
    LaunchedEffect(Unit) { while (true) { now = System.currentTimeMillis(); delay(1000) } }
    val locale = Locale("id", "ID")
    val dateText = SimpleDateFormat("EEEE d MMMM", locale).format(Date(now))
    val timeText = SimpleDateFormat("HH.mm", locale).format(Date(now))

    var dragSum by remember { mutableStateOf(0f) }
    Box(
        Modifier.fillMaxSize()
            .background(Brush.verticalGradient(listOf(cover.accent.copy(alpha = 0.55f).compositeOver(Color(0xFF120A0C)), Color(0xFF120A0C), Color(0xFF0A0A0C))))
            .pointerInput(Unit) {
                detectVerticalDragGestures(
                    onDragStart = { dragSum = 0f },
                    onVerticalDrag = { _, d -> dragSum += d },
                    onDragEnd = { if (dragSum < -160f) LockMode.active = false }
                )
            }
    ) {
        Column(Modifier.fillMaxSize().statusBarsPadding().navigationBarsPadding(), horizontalAlignment = Alignment.CenterHorizontally) {
            Spacer(Modifier.height(10.dp))
            Icon(Icons.Rounded.Lock, null, tint = Color.White.copy(alpha = 0.85f), modifier = Modifier.size(16.dp))
            Text(dateText, color = Color.White, fontSize = 15.sp, fontWeight = FontWeight.Medium, modifier = Modifier.padding(top = 4.dp))
            Text(timeText, color = Color.White, fontSize = 78.sp, fontWeight = FontWeight.Bold)

            // Lirik berjalan
            Box(Modifier.weight(1f).fillMaxWidth().padding(horizontal = 22.dp)) {
                val st = player.lyrics
                if (st is LyricsState.Ready) {
                    val lines = st.lyrics.lines
                    val synced = st.lyrics.synced
                    val cur = if (synced) lines.indexOfLast { it.timeMs <= ui.positionMs + 150 }.coerceAtLeast(0) else 0
                    val listState = rememberLazyListState()
                    LaunchedEffect(cur, st.songId, lines.size) {
                        if (lines.isNotEmpty()) listState.animateScrollToItem((cur - 1).coerceAtLeast(0))
                    }
                    LazyColumn(state = listState, modifier = Modifier.fillMaxSize(), userScrollEnabled = !synced) {
                        itemsIndexed(lines) { i, l ->
                            val dist = if (synced) kotlin.math.abs(i - cur) else 1
                            val active = synced && i == cur
                            Text(
                                l.text.ifBlank { "♪" },
                                color = Color.White.copy(alpha = if (active) 1f else (0.42f - 0.08f * (dist - 1)).coerceAtLeast(0.15f)),
                                fontSize = 26.sp, lineHeight = 32.sp,
                                fontWeight = if (active) FontWeight.Bold else FontWeight.SemiBold,
                                modifier = Modifier.fillMaxWidth().padding(vertical = 9.dp)
                            )
                        }
                    }
                } else Box(Modifier.fillMaxSize(), Alignment.Center) {
                    Text(
                        when (st) { LyricsState.NotFound -> "Lirik belum ditemukan"; LyricsState.Loading, LyricsState.Idle -> "Memuat lirik…"; else -> "Lirik tidak tersedia" },
                        color = Color.White.copy(alpha = 0.6f), fontSize = 16.sp, textAlign = TextAlign.Center
                    )
                }
                // pudar di tepi atas
                Box(Modifier.fillMaxWidth().height(36.dp).background(Brush.verticalGradient(listOf(Color(0x99120A0C), Color.Transparent))))
            }

            // Kartu pemutar
            Column(
                Modifier.fillMaxWidth().padding(horizontal = 14.dp).clip(RoundedCornerShape(26.dp))
                    .background(Color.White.copy(alpha = 0.10f)).border(1.dp, Color.White.copy(alpha = 0.14f), RoundedCornerShape(26.dp)).padding(14.dp)
            ) {
                Row(verticalAlignment = Alignment.CenterVertically) {
                    AsyncImage(song.thumbnailUrl, null, Modifier.size(46.dp).clip(RoundedCornerShape(8.dp)), contentScale = ContentScale.Crop)
                    Column(Modifier.padding(start = 12.dp).weight(1f)) {
                        Text(song.title, color = Color.White, fontWeight = FontWeight.SemiBold, fontSize = 14.sp, maxLines = 1, overflow = TextOverflow.Ellipsis)
                        Text(song.subtitle.substringBefore(" • "), color = Color.White.copy(alpha = 0.65f), fontSize = 12.sp, maxLines = 1, overflow = TextOverflow.Ellipsis)
                    }
                }
                val frac = if (ui.durationMs > 0) (ui.positionMs.toFloat() / ui.durationMs).coerceIn(0f, 1f) else 0f
                Box(Modifier.fillMaxWidth().padding(top = 14.dp).height(4.dp).clip(CircleShape).background(Color.White.copy(alpha = 0.25f))) {
                    Box(Modifier.fillMaxWidth(frac).fillMaxSize().background(Color.White))
                }
                Row(Modifier.fillMaxWidth().padding(top = 4.dp)) {
                    Text(fmt(ui.positionMs), color = Color.White.copy(alpha = 0.7f), fontSize = 10.sp, modifier = Modifier.weight(1f))
                    Text("-" + fmt((ui.durationMs - ui.positionMs).coerceAtLeast(0)), color = Color.White.copy(alpha = 0.7f), fontSize = 10.sp)
                }
                Row(Modifier.fillMaxWidth().padding(top = 6.dp), horizontalArrangement = Arrangement.SpaceEvenly, verticalAlignment = Alignment.CenterVertically) {
                    Icon(Icons.Rounded.SkipPrevious, "Sebelumnya", tint = Color.White, modifier = Modifier.size(34.dp).clip(CircleShape).clickable { player.previous() })
                    Icon(
                        if (ui.isPlaying) Icons.Rounded.Pause else Icons.Rounded.PlayArrow, "Putar/jeda", tint = Color.White,
                        modifier = Modifier.size(44.dp).clip(CircleShape).clickable { player.togglePlay() }
                    )
                    Icon(Icons.Rounded.SkipNext, "Berikutnya", tint = Color.White, modifier = Modifier.size(34.dp).clip(CircleShape).clickable { player.next() })
                }
            }
            Text("Geser ke atas untuk membuka", color = Color.White.copy(alpha = 0.75f), fontSize = 11.sp, modifier = Modifier.padding(top = 12.dp))
            Box(Modifier.padding(top = 8.dp, bottom = 10.dp).width(120.dp).height(4.dp).clip(CircleShape).background(Color.White.copy(alpha = 0.8f)))
        }
    }
}
