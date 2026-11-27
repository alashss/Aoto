package com.jimz.jimztune.ui.social

import androidx.compose.animation.AnimatedContent
import androidx.compose.animation.core.tween
import androidx.compose.animation.fadeIn
import androidx.compose.animation.fadeOut
import androidx.compose.animation.slideInVertically
import androidx.compose.animation.slideOutVertically
import androidx.compose.animation.togetherWith
import androidx.compose.foundation.layout.Box
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.runtime.DisposableEffect
import androidx.compose.runtime.LaunchedEffect
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableLongStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.setValue
import androidx.compose.runtime.withFrameMillis
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.text.SpanStyle
import androidx.compose.ui.text.buildAnnotatedString
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.text.style.TextAlign
import androidx.compose.ui.text.withStyle
import androidx.compose.ui.unit.TextUnit
import com.jimz.jimztune.data.model.SearchItem
import com.jimz.jimztune.player.PlayerViewModel
import kotlinx.coroutines.delay

/** Penanda bahwa potongan lagu catatan sedang diputar (kartu mini pemutar disembunyikan selama itu). */
object NoteAudio {
    /** Jumlah sheet catatan yang sedang memutar; kartu mini disembunyikan selama > 0 (termasuk saat lagu dikembalikan). */
    var count by androidx.compose.runtime.mutableStateOf(0)
    val active: Boolean get() = count > 0
}

/** Satu baris lirik di dalam potongan catatan: [ms] = waktu baris itu di lagu aslinya. */
data class ClipLine(val ms: Long, val text: String)

/**
 * Potongan lagu pada catatan (mirip catatan musik Instagram): beberapa baris lirik berikut waktunya,
 * disimpan sebagai teks "ms|baris" per baris. Catatan lama yang hanya satu baris tanpa waktu tetap terbaca.
 */
object NoteClip {
    const val MAX_LINES = 4

    fun encode(lines: List<ClipLine>): String? =
        lines.takeIf { it.isNotEmpty() }?.joinToString("\n") { "${it.ms}|${it.text.replace('\n', ' ').replace('|', '/')}" }

    fun decode(s: String?): List<ClipLine> {
        if (s.isNullOrBlank()) return emptyList()
        val out = s.split('\n').mapNotNull { p ->
            val i = p.indexOf('|')
            val ms = if (i > 0) p.substring(0, i).toLongOrNull() else null
            if (ms != null) ClipLine(ms, p.substring(i + 1).trim()).takeIf { it.text.isNotBlank() } else null
        }
        return out.ifEmpty { listOf(ClipLine(-1L, s.trim())) }
    }

    fun startMs(lines: List<ClipLine>): Long? = lines.firstOrNull()?.ms?.takeIf { it >= 0 }
    fun endMs(lines: List<ClipLine>): Long = (lines.lastOrNull()?.ms ?: 0L) + 4_000L
    fun firstText(s: String?): String? = decode(s).firstOrNull()?.text
}

/** Teks yang terisi putih kata demi kata mengikuti [progress] (0..1), seperti lirik catatan Instagram. */
@Composable
fun KaraokeWords(text: String, progress: Float, size: TextUnit, modifier: Modifier = Modifier, bright: Color = Color.White) {
    val words = text.split(' ').filter { it.isNotEmpty() }
    val n = words.size.coerceAtLeast(1)
    val pos = progress.coerceIn(0f, 1f) * n
    val a = buildAnnotatedString {
        words.forEachIndexed { i, w ->
            val f = (pos - i).coerceIn(0f, 1f)
            withStyle(SpanStyle(color = bright.copy(alpha = 0.34f + 0.66f * f))) { append(w) }
            if (i < words.lastIndex) append(" ")
        }
    }
    Text(a, fontSize = size, fontWeight = FontWeight.SemiBold, textAlign = TextAlign.Center, lineHeight = size * 1.25f, modifier = modifier)
}

/** Menampilkan baris yang sedang "dinyanyikan" pada waktu [clockMs] (waktu lagu); ganti baris dengan animasi geser. */
@Composable
fun ClipView(lines: List<ClipLine>, clockMs: Long, size: TextUnit, modifier: Modifier = Modifier) {
    if (lines.isEmpty()) return
    if (lines.first().ms < 0) {
        KaraokeLine(lines.first().text, size, modifier)
        return
    }
    val idx = lines.indexOfLast { it.ms <= clockMs }.coerceAtLeast(0)
    val l = lines[idx]
    val end = lines.getOrNull(idx + 1)?.ms ?: (l.ms + 3_500L)
    val progress = ((clockMs - l.ms).toFloat() / (end - l.ms).coerceAtLeast(600L) * 1.12f).coerceIn(0f, 1f)
    Box(modifier, contentAlignment = Alignment.Center) {
        AnimatedContent(
            targetState = idx,
            transitionSpec = { (slideInVertically(tween(380)) { it / 2 } + fadeIn(tween(380))) togetherWith (slideOutVertically(tween(260)) { -it / 2 } + fadeOut(tween(220))) },
            label = "clipLine"
        ) { i ->
            val ln = lines[i.coerceIn(0, lines.lastIndex)]
            val p = if (i == idx) progress else 1f
            // Persis komponen halaman lirik: isi per kata dengan gaya animasi yang sama.
            com.jimz.jimztune.ui.player.LyricRow(
                text = ln.text, words = emptyList(), lineStartMs = ln.ms, synced = true, distance = 0, center = true,
                sizeScale = size.value / 16f, translation = null, roman = null,
                animStyle = com.jimz.jimztune.AppGraph.lyricsPrefs.animStyle, blurOthers = false,
                progress = { p }, onClick = {}
            )
        }
    }
}

/** Jam tiruan yang berputar di dalam potongan (tanpa suara), untuk pratinjau di editor. */
@Composable
fun rememberLoopClock(lines: List<ClipLine>): Long {
    val start = lines.firstOrNull()?.ms?.coerceAtLeast(0L) ?: 0L
    val span = (NoteClip.endMs(lines) - start).coerceAtLeast(1_500L)
    var t by remember(lines) { mutableLongStateOf(start) }
    LaunchedEffect(lines) {
        val t0 = withFrameMillis { it }
        while (true) withFrameMillis { now -> t = start + (now - t0) % span }
    }
    return t
}

/**
 * Memutar lagu catatan mulai dari awal potongan, mengulang potongannya, dan mengembalikan waktu lagu yang sudah dihaluskan.
 * Saat dilepas (sheet ditutup) lagunya dijeda.
 */
@Composable
fun rememberNoteClipClock(song: SearchItem?, lines: List<ClipLine>, player: PlayerViewModel): Long {
    val start = NoteClip.startMs(lines)
    val end = NoteClip.endMs(lines) + 500L
    LaunchedEffect(song?.id, start) {
        if (song == null) return@LaunchedEffect
        if (start != null) {
            var tries = 0
            while (tries < 150 && !(player.ui.current?.id == song.id && player.ui.durationMs > 0L)) { delay(100); tries++ }
            if (kotlin.math.abs(player.ui.positionMs - start) > 1500L) player.seekTo(start)
        }
    }
    val ui = player.ui
    val onSong = song != null && ui.current?.id == song.id
    LaunchedEffect(ui.positionMs) { if (onSong && start != null && ui.positionMs > end) player.seekTo(start) }
    var smooth by remember { mutableLongStateOf(0L) }
    LaunchedEffect(ui.positionMs, ui.isPlaying, onSong) {
        smooth = ui.positionMs
        if (!ui.isPlaying || !onSong) return@LaunchedEffect
        val base = ui.positionMs
        val t0 = System.nanoTime()
        while (true) withFrameMillis { smooth = base + (System.nanoTime() - t0) / 1_000_000 }
    }
    // Kartu mini disembunyikan SEJAK komposisi pertama (bukan setelah frame pertama), jadi tidak sempat berkedip muncul.
    remember(song?.id) {
        object : androidx.compose.runtime.RememberObserver {
            private var live = false
            private var counted = false
            init { if (song != null) { NoteAudio.count++; counted = true } }
            override fun onRemembered() { live = true; if (song != null) player.beginNoteClip(song, start ?: 0L) }
            override fun onForgotten() { finish() }
            override fun onAbandoned() { finish() }
            private fun finish() {
                if (!counted) return
                counted = false
                if (live && song != null) player.endNoteClip { NoteAudio.count = (NoteAudio.count - 1).coerceAtLeast(0) }
                else NoteAudio.count = (NoteAudio.count - 1).coerceAtLeast(0)
            }
        }
    }
    return if (onSong) smooth else (start ?: 0L)
}
