package com.jimz.jimztune.ui.components

import androidx.compose.foundation.background
import androidx.compose.foundation.clickable
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.PaddingValues
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.aspectRatio
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.lazy.grid.GridCells
import androidx.compose.foundation.lazy.grid.GridItemSpan
import androidx.compose.foundation.lazy.grid.LazyVerticalGrid
import androidx.compose.foundation.lazy.grid.itemsIndexed
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.rounded.Check
import androidx.compose.material.icons.rounded.GridView
import androidx.compose.material.icons.rounded.MoreVert
import androidx.compose.material3.DropdownMenu
import androidx.compose.material3.DropdownMenuItem
import androidx.compose.material3.HorizontalDivider
import androidx.compose.material3.Icon
import androidx.compose.runtime.remember
import androidx.compose.ui.graphics.vector.ImageVector
import androidx.compose.material.icons.rounded.ViewAgenda
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.setValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clip
import androidx.compose.ui.layout.layout
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.layout.ContentScale
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.text.style.TextOverflow
import androidx.compose.ui.unit.dp
import coil.compose.AsyncImage
import com.jimz.jimztune.AppGraph
import com.jimz.jimztune.data.model.SearchItem
import com.jimz.jimztune.ui.theme.LocalCoverTheme

/** Pilihan tampilan daftar lagu di Koleksi: baris (daftar) atau kotak persegi (grid). Diingat antar sesi. */
object LibraryView {
    private val prefs get() = AppGraph.appContext.getSharedPreferences("jimztune_view", android.content.Context.MODE_PRIVATE)
    var grid by mutableStateOf(runCatching { prefs.getBoolean("grid", false) }.getOrDefault(false))
        private set
    fun toggle() { grid = !grid; prefs.edit().putBoolean("grid", grid).apply() }
}

/** Tombol pengganti tampilan daftar / kotak. */
@Composable
fun ViewModeButton() {
    CompactIconButton(
        if (LibraryView.grid) Icons.Rounded.ViewAgenda else Icons.Rounded.GridView,
        if (LibraryView.grid) "Tampilan daftar" else "Tampilan kotak",
        onClick = { LibraryView.toggle() }
    )
}

/** Satu kotak lagu: cover persegi, judul, artis, menu ⋮ di pojok. */
@Composable
fun SongGridTile(song: SearchItem, playing: Boolean, onClick: () -> Unit, modifier: Modifier = Modifier) {
    val accent = LocalCoverTheme.current.accent
    val shape = RoundedCornerShape(16.dp)
    Column(modifier.clip(shape).clickable(onClick = onClick)) {
        Box(Modifier.fillMaxWidth().aspectRatio(1f).clip(shape).background(MaterialTheme.colorScheme.surfaceVariant)) {
            AsyncImage(song.thumbnailUrl, null, contentScale = ContentScale.Crop, modifier = Modifier.fillMaxSize())
            if (playing) Box(Modifier.fillMaxSize().background(Color.Black.copy(alpha = 0.4f)), contentAlignment = Alignment.Center) { MiniEqualizer(color = accent) }
            Box(Modifier.align(Alignment.TopEnd).padding(2.dp).clip(RoundedCornerShape(50)).background(Color.Black.copy(alpha = 0.35f))) {
                SongMenuButton(song)
            }
        }
        Spacer(Modifier.height(6.dp))
        Text(song.title, style = MaterialTheme.typography.bodyMedium, fontWeight = FontWeight.SemiBold,
            color = if (playing) accent else MaterialTheme.colorScheme.onSurface, maxLines = 1, overflow = TextOverflow.Ellipsis)
        Text(song.subtitle.substringBefore(" • "), style = MaterialTheme.typography.bodySmall,
            color = MaterialTheme.colorScheme.onSurfaceVariant, maxLines = 1, overflow = TextOverflow.Ellipsis)
    }
}

val GridSide = 20.dp

/** Isi item grid dilebarkan keluar sejajar tepi layar (membatalkan padding samping grid), supaya bilah atas sama seperti di mode daftar. */
@Composable
fun FullBleed(content: @Composable () -> Unit) {
    Box(
        Modifier.layout { m, c ->
            val extra = GridSide.roundToPx() * 2
            val p = m.measure(c.copy(minWidth = c.maxWidth + extra, maxWidth = c.maxWidth + extra))
            layout(c.maxWidth, p.height) { p.place(-extra / 2, 0) }
        }
    ) { content() }
}

/** Grid 3 kolom; [header] mengisi satu baris penuh di atas. */
@Composable
fun SongGrid(
    songs: List<SearchItem>,
    currentId: String?,
    onPlay: (List<SearchItem>, Int) -> Unit,
    bottomPadding: androidx.compose.ui.unit.Dp,
    header: @Composable () -> Unit = {}
) {
    LazyVerticalGrid(
        columns = GridCells.Fixed(3),
        contentPadding = PaddingValues(start = GridSide, end = GridSide, bottom = bottomPadding),
        horizontalArrangement = androidx.compose.foundation.layout.Arrangement.spacedBy(12.dp),
        verticalArrangement = androidx.compose.foundation.layout.Arrangement.spacedBy(10.dp),
        modifier = Modifier.fillMaxSize()
    ) {
        item(span = { GridItemSpan(maxLineSpan) }) { FullBleed { header() } }
        itemsIndexed(songs, key = { _, s -> s.id }) { i, s ->
            SongGridTile(s, s.id == currentId, onClick = { onPlay(songs, i) })
        }
    }
}

/** Satu baris di menu ⋮ bilah daftar. [checked] menampilkan centang (untuk pilihan urutan). */
class BarEntry(val label: String, val icon: ImageVector? = null, val checked: Boolean = false, val onClick: () -> Unit)

/** Satu tombol ⋮ yang memuat semua aksi daftar: unduh, urutkan, tampilan, tambah, bagikan, dst. */
@Composable
fun BarMenuButton(actions: List<BarEntry>, sortTitle: String? = null, sort: List<BarEntry> = emptyList()) {
    var open by remember { mutableStateOf(false) }
    Box {
        CompactIconButton(Icons.Rounded.MoreVert, "Menu", onClick = { open = true })
        DropdownMenu(expanded = open, onDismissRequest = { open = false }) {
            actions.forEach { e ->
                DropdownMenuItem(
                    text = { Text(e.label) },
                    leadingIcon = e.icon?.let { ic -> { Icon(ic, null) } },
                    onClick = { open = false; e.onClick() }
                )
            }
            if (sort.isNotEmpty()) {
                HorizontalDivider()
                Text(sortTitle ?: "Urutkan", style = MaterialTheme.typography.labelMedium, color = MaterialTheme.colorScheme.onSurfaceVariant,
                    modifier = Modifier.padding(horizontal = 16.dp, vertical = 6.dp))
                sort.forEach { e ->
                    DropdownMenuItem(
                        text = { Text(e.label) },
                        trailingIcon = { if (e.checked) Icon(Icons.Rounded.Check, null) },
                        onClick = { open = false; e.onClick() }
                    )
                }
            }
        }
    }
}
