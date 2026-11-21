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
import androidx.compose.foundation.verticalScroll
import kotlinx.coroutines.launch
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
import com.jimz.jimztune.util.LyricCard
import com.jimz.jimztune.util.ShareImage
import androidx.compose.foundation.horizontalScroll
import androidx.compose.ui.graphics.asImageBitmap

private const val MAX_LINES = 8

/** Memilih beberapa baris lirik lalu membagikannya sebagai gambar (kotak 1:1 atau story 9:16). */
@Composable
fun LyricShareSheet(lyrics: Lyrics, positionMs: Long, title: String, artist: String, coverUrl: String?, onDismiss: () -> Unit) {
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
    var bg by remember { mutableStateOf(LyricCard.Bg.GLASS) }
    var align by remember { mutableStateOf(LyricCard.Align.LEFT) }
    var font by remember { mutableStateOf(LyricCard.Font.DEFAULT) }
    var bold by remember { mutableStateOf(true) }
    var ink by remember { mutableStateOf(LyricCard.Ink.AUTO) }
    var showCover by remember { mutableStateOf(true) }
    var watermark by remember { mutableStateOf(true) }
    val scope = androidx.compose.runtime.rememberCoroutineScope()
    var busy by remember { mutableStateOf(false) }
    var cover by remember { mutableStateOf<android.graphics.Bitmap?>(null) }
    var preview by remember { mutableStateOf<android.graphics.Bitmap?>(null) }
    // Sampul diunduh sekali lewat Coil (bitmap software supaya bisa digambar ke kanvas).
    androidx.compose.runtime.LaunchedEffect(coverUrl) {
        if (coverUrl != null) {
            try {
                val req = coil.request.ImageRequest.Builder(context).data(coverUrl).allowHardware(false).build()
                val res = coil.Coil.imageLoader(context).execute(req)
                cover = (res.drawable as? android.graphics.drawable.BitmapDrawable)?.bitmap
            } catch (_: Exception) {
            }
        }
    }
    val options = LyricCard.Options(story, bg, align, font, bold, ink, showCover, watermark)
    // Pratinjau dirender kecil (0,4x) dan ditunda sebentar supaya mengutak-atik opsi tidak membuat layar patah-patah.
    androidx.compose.runtime.LaunchedEffect(selected.toList(), story, bg, align, font, bold, ink, showCover, watermark, cover) {
        val picked = selected.sorted().mapNotNull { lines.getOrNull(it)?.value }
        if (picked.isEmpty()) { preview = null; return@LaunchedEffect }
        kotlinx.coroutines.delay(120)
        preview = kotlinx.coroutines.withContext(kotlinx.coroutines.Dispatchers.Default) {
            LyricCard.render(options, accent.toArgb(), title, artist, picked, cover, scale = 0.4f)
        }
    }
    // Ekspor memakai ukuran penuh, dirender di thread latar saat tombol ditekan.
    fun export(action: (android.graphics.Bitmap) -> Unit) {
        val picked = selected.sorted().mapNotNull { lines.getOrNull(it)?.value }
        if (picked.isEmpty() || busy) return
        busy = true
        scope.launch {
            val full = kotlinx.coroutines.withContext(kotlinx.coroutines.Dispatchers.Default) {
                LyricCard.render(options, accent.toArgb(), title, artist, picked, cover)
            }
            busy = false
            action(full)
        }
    }
    val listState = rememberLazyListState(initialFirstVisibleItemIndex = (startPos - 1).coerceAtLeast(0))

    JimzBottomSheet(onDismiss = onDismiss) {
        Column(
            Modifier
                .weight(1f, fill = false)
                .verticalScroll(androidx.compose.foundation.rememberScrollState())
                .padding(horizontal = 24.dp)
        ) {
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
            preview?.let { bmp ->
                androidx.compose.foundation.Image(
                    bitmap = bmp.asImageBitmap(),
                    contentDescription = "Pratinjau kartu lirik",
                    modifier = Modifier
                        .align(androidx.compose.ui.Alignment.CenterHorizontally)
                        .heightIn(max = 260.dp)
                        .clip(RoundedCornerShape(16.dp))
                )
                Spacer(Modifier.height(12.dp))
            }
            ChipRow("Latar", LyricCard.Bg.entries, bg, { it.label }) { bg = it }
            ChipRow("Format", listOf(false, true), story, { if (it) "Story 9:16" else "Kotak" }) { story = it }
            ChipRow("Font", LyricCard.Font.entries, font, { it.label }) { font = it }
            ChipRow("Tebal", listOf(false, true), bold, { if (it) "Tebal" else "Biasa" }) { bold = it }
            ChipRow("Rata", LyricCard.Align.entries, align, { it.label }) { align = it }
            ChipRow("Warna", LyricCard.Ink.entries, ink, { it.label }) { ink = it }
            ChipRow("Sampul", listOf(true, false), showCover, { if (it) "Tampil" else "Sembunyi" }) { showCover = it }
            ChipRow("Tanda", listOf(true, false), watermark, { if (it) "Logo Aoto" else "Tanpa" }) { watermark = it }
            Spacer(Modifier.height(12.dp))
            Row(horizontalArrangement = Arrangement.spacedBy(10.dp), modifier = Modifier.fillMaxWidth()) {
                androidx.compose.material3.OutlinedButton(
                    onClick = {
                        export { bmp ->
                            val ok = ShareImage.saveToGallery(context, bmp, "lirik")
                            android.widget.Toast.makeText(
                                context,
                                if (ok) "Tersimpan di galeri (Pictures/Aoto)" else "Gagal menyimpan. Coba Bagikan.",
                                android.widget.Toast.LENGTH_SHORT
                            ).show()
                        }
                    },
                    enabled = preview != null && !busy,
                    modifier = Modifier.weight(1f)
                ) { Text(if (busy) "Memproses…" else "Simpan") }
                androidx.compose.material3.Button(
                    onClick = { export { bmp -> ShareImage.share(context, bmp, "lirik") } },
                    enabled = preview != null && !busy,
                    modifier = Modifier.weight(1f)
                ) { Text("Bagikan") }
            }
            Spacer(Modifier.height(20.dp))
        }
    }
}

@Composable
private fun <T> ChipRow(label: String, items: List<T>, selected: T, name: (T) -> String, onPick: (T) -> Unit) {
    Row(
        verticalAlignment = androidx.compose.ui.Alignment.CenterVertically,
        modifier = Modifier
            .fillMaxWidth()
            .padding(vertical = 3.dp)
            .horizontalScroll(androidx.compose.foundation.rememberScrollState())
    ) {
        Text(label, style = MaterialTheme.typography.labelMedium, color = Color.White.copy(alpha = 0.55f), modifier = Modifier.width(56.dp))
        Row(horizontalArrangement = Arrangement.spacedBy(8.dp)) {
            items.forEach { OptionChip(name(it), it == selected, { onPick(it) }) }
        }
    }
}
