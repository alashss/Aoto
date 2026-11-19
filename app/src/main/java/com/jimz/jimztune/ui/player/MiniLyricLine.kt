package com.jimz.jimztune.ui.player

import androidx.compose.animation.AnimatedContent
import androidx.compose.animation.core.tween
import androidx.compose.animation.fadeIn
import androidx.compose.animation.fadeOut
import androidx.compose.animation.slideInVertically
import androidx.compose.animation.slideOutVertically
import androidx.compose.animation.togetherWith
import androidx.compose.foundation.clickable
import androidx.compose.foundation.interaction.MutableInteractionSource
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.padding
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.runtime.remember
import androidx.compose.ui.Modifier
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.text.style.TextOverflow
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import com.jimz.jimztune.data.lyrics.LyricsState

/**
 * Satu baris lirik yang sedang dinyanyikan, di atas judul lagu pada tab Cover. Ketuk untuk membuka lirik penuh.
 * Tidak tampil kalau lirik belum ada atau tidak bersinkron.
 */
@Composable
fun MiniLyricLine(state: LyricsState, positionMs: Long, onClick: () -> Unit, modifier: Modifier = Modifier) {
    val lyrics = (state as? LyricsState.Ready)?.lyrics?.takeIf { it.synced && it.lines.isNotEmpty() } ?: return

    val p = positionMs + 180L
    val index = remember(lyrics, p / 100) {
        var lo = 0
        var hi = lyrics.lines.lastIndex
        var answer = -1
        while (lo <= hi) {
            val mid = (lo + hi) / 2
            if (lyrics.lines[mid].timeMs <= p) {
                answer = mid
                lo = mid + 1
            } else {
                hi = mid - 1
            }
        }
        answer
    }
    val line = if (index < 0) "" else lyrics.lines[index].text
    val shown = line.ifBlank { "♪" }
    val next = if (com.jimz.jimztune.AppGraph.lyricsPrefs.preview == 2) {
        lyrics.lines.getOrNull(index + 1)?.text.orEmpty()
    } else {
        ""
    }

    androidx.compose.foundation.layout.Column(
        modifier = modifier
            .fillMaxWidth()
            .padding(bottom = 10.dp)
            .clickable(interactionSource = remember { MutableInteractionSource() }, indication = null, onClick = onClick)
    ) {
        AnimatedContent(
            targetState = shown,
            transitionSpec = {
                (slideInVertically(tween(320)) { it / 2 } + fadeIn(tween(320))) togetherWith
                    (slideOutVertically(tween(240)) { -it / 2 } + fadeOut(tween(200)))
            },
            label = "miniLyric"
        ) { text ->
            Text(
                text,
                color = Color.White.copy(alpha = if (line.isBlank()) 0.5f else 0.92f),
                fontSize = 17.sp,
                lineHeight = 22.sp,
                fontWeight = FontWeight.Bold,
                maxLines = 2,
                overflow = TextOverflow.Ellipsis
            )
        }
        if (next.isNotBlank()) {
            Text(
                next,
                color = Color.White.copy(alpha = 0.5f),
                fontSize = 14.sp,
                lineHeight = 18.sp,
                fontWeight = FontWeight.Medium,
                maxLines = 1,
                overflow = TextOverflow.Ellipsis
            )
        }
    }
}
