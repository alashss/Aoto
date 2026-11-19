package com.jimz.jimztune.ui.player

import androidx.compose.foundation.background
import androidx.compose.foundation.clickable
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.heightIn
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.width
import androidx.compose.foundation.lazy.LazyColumn
import androidx.compose.foundation.lazy.itemsIndexed
import androidx.compose.foundation.lazy.rememberLazyListState
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.setValue
import androidx.compose.runtime.toMutableStateList
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clip
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.graphics.toArgb
import androidx.compose.ui.platform.LocalContext
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.unit.dp
import com.jimz.jimztune.data.lyrics.Lyrics
import com.jimz.jimztune.ui.components.JimzBottomSheet
import com.jimz.jimztune.ui.components.OptionChip
import com.jimz.jimztune.ui.theme.LocalCoverTheme
import com.jimz.jimztune.util.ShareImage

private const val MAX_LINES = 8

/** Memilih beberapa baris lirik lalu membagikannya sebagai gambar (kotak 1:1 atau story 9:16). */
@Composable
fun LyricShareSheet(lyrics: Lyrics, positionMs: Long, title: String, artist: String, onDismiss: () -> Unit) {
    val context = LocalContext.current
    val accent = LocalCoverTheme.current.accent
    val lines = remember(lyrics) { lyrics.lines.map { it.text }.withIndex().filter { it.value.isNotBlank() } }

    // Baris yang sedang dinyanyikan (dan dua setelahnya) terpilih lebih dulu.
    val startPos = remember(lyrics) {
        if (!lyrics.synced) 0 else lines.indexOfLast { lyrics.lines[it.index].timeMs <= positionMs }.coerceAtLeast(0)
    }
    val selected = remember(lyrics) {
        (startPos until minOf(startPos + 3, lines.size)).toList().toMutableStateList()
    }
    var story by remember { mutableStateOf(false) }
    val listState = rememberLazyListState(initialFirstVisibleItemIndex = (startPos - 1).coerceAtLeast(0))

    JimzBottomSheet(onDismiss = onDismiss) {
        Column(Modifier.padding(horizontal = 24.dp)) {
            Text("Bagikan lirik", style = MaterialTheme.typography.titleLarge, fontWeight = FontWeight.Bold)
            Text(
                "Pilih sampai $MAX_LINES baris (${selected.size} dipilih)",
                style = MaterialTheme.typography.bodySmall,
                color = Color.White.copy(alpha = 0.6f),
                modifier = Modifier.padding(top = 2.dp, bottom = 8.dp)
            )
            LazyColumn(state = listState, modifier = Modifier.heightIn(max = 320.dp)) {
                itemsIndexed(lines) { i, entry ->
                    val on = i in selected
                    Text(
                        entry.value,
                        style = MaterialTheme.typography.bodyLarge,
                        color = Color.White,
                        modifier = Modifier
                            .fillMaxWidth()
                            .padding(vertical = 2.dp)
                            .clip(RoundedCornerShape(12.dp))
                            .background(if (on) accent.copy(alpha = 0.35f) else Color.Transparent)
                            .clickable {
                                if (on) selected.remove(i) else if (selected.size < MAX_LINES) selected.add(i)
                            }
                            .padding(horizontal = 12.dp, vertical = 8.dp)
                    )
                }
            }
            Spacer(Modifier.height(12.dp))
            Row(horizontalArrangement = Arrangement.spacedBy(8.dp)) {
                OptionChip("Kotak", !story, { story = false })
                OptionChip("Story", story, { story = true })
                Spacer(Modifier.width(4.dp))
                OptionChip("Bagikan", false, {
                    if (selected.isNotEmpty()) {
                        val picked = selected.sorted().map { lines[it].value }
                        val bmp = ShareImage.render(
                            story = story,
                            accent = accent.toArgb(),
                            heading = title,
                            quote = picked,
                            footer = (if (artist.isNotBlank()) "$artist · " else "") + "Aoto"
                        )
                        ShareImage.share(context, bmp, "lirik")
                    }
                })
            }
            Spacer(Modifier.height(20.dp))
        }
    }
}
