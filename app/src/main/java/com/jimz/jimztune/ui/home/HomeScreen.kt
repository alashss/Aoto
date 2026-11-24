package com.jimz.jimztune.ui.home

import androidx.compose.foundation.Image
import androidx.compose.foundation.background
import androidx.compose.foundation.border
import androidx.compose.foundation.clickable
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.aspectRatio
import androidx.compose.foundation.horizontalScroll
import androidx.compose.runtime.remember
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.PaddingValues
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.layout.statusBarsPadding
import androidx.compose.foundation.layout.width
import androidx.compose.foundation.lazy.LazyColumn
import androidx.compose.foundation.lazy.LazyRow
import androidx.compose.foundation.lazy.grid.GridCells
import androidx.compose.foundation.lazy.grid.LazyHorizontalGrid
import androidx.compose.foundation.lazy.grid.itemsIndexed as gridItemsIndexed
import androidx.compose.foundation.lazy.items
import androidx.compose.foundation.lazy.itemsIndexed
import androidx.compose.foundation.shape.CircleShape
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.rounded.CloudOff
import androidx.compose.material.icons.rounded.Construction
import androidx.compose.material.icons.rounded.PlayArrow
import androidx.compose.material.icons.rounded.Search
import androidx.compose.material.icons.rounded.Shuffle
import androidx.compose.runtime.LaunchedEffect
import androidx.compose.material3.Icon
import androidx.compose.material3.IconButton
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.Text
import androidx.compose.material3.TextButton
import androidx.compose.runtime.Composable
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clip
import androidx.compose.ui.draw.clipToBounds
import androidx.compose.ui.unit.Dp
import androidx.compose.ui.geometry.Size
import androidx.compose.ui.graphics.graphicsLayer
import androidx.compose.ui.graphics.CompositingStrategy
import androidx.compose.ui.graphics.Brush
import androidx.compose.ui.graphics.BlendMode
import androidx.compose.ui.draw.drawWithContent
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.layout.ContentScale
import androidx.compose.ui.res.painterResource
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.text.style.TextOverflow
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import androidx.lifecycle.viewmodel.compose.viewModel
import coil.compose.AsyncImage
import com.jimz.jimztune.data.stats.RankKind
import com.jimz.jimztune.data.stats.RankPeriod
import com.jimz.jimztune.R
import com.jimz.jimztune.data.model.SearchItem
import com.jimz.jimztune.ui.components.LocalOverlayPadding
import com.jimz.jimztune.ui.components.PlaylistCover
import com.jimz.jimztune.ui.theme.LocalCoverTheme
import com.jimz.jimztune.data.remote.HomeMaintenance
import com.jimz.jimztune.ui.theme.BrandTextStyle
import com.jimz.jimztune.ui.theme.JimzBlueSoft
import java.util.Calendar
import com.jimz.jimztune.AppGraph
import com.jimz.jimztune.ui.components.TonalPillButton
import com.jimz.jimztune.ui.components.EmptyState
import com.jimz.jimztune.ui.components.GradientPillButton
import com.jimz.jimztune.ui.components.SongListRow
import com.jimz.jimztune.util.rememberIsOnline
import androidx.compose.material.icons.rounded.Refresh

@Composable
fun HomeScreen(
    recent: List<SearchItem>,
    currentId: String?,
    onPlay: (List<SearchItem>, Int) -> Unit,
    onOpenSearch: () -> Unit,
    onOpenProfile: () -> Unit = {},
    onOpenAnnouncements: () -> Unit = {},
    onOpenRanking: (RankKind, RankPeriod) -> Unit = { _, _ -> },
    vm: HomeViewModel = viewModel()
) {
    val overlay = LocalOverlayPadding.current
    val quick = vm.shelves.firstOrNull()
    val online = rememberIsOnline()
    val ctx = androidx.compose.ui.platform.LocalContext.current
    // Speed dial: lagu yang paling sering diputar 30 hari terakhir; kalau belum cukup, dilengkapi dari favorit.
    val dial = remember(recent) {
        val today = java.time.LocalDate.now()
        val top = com.jimz.jimztune.data.stats.PlayLog.topSongs(today.minusDays(30), today, 6).map {
            SearchItem(it.id, com.jimz.jimztune.data.model.SearchType.SONG, it.title, it.artist, it.art)
        }
        (top + AppGraph.library.favorites.filter { it.type == com.jimz.jimztune.data.model.SearchType.SONG })
            .distinctBy { it.id }.take(6)
    }

    // Saat internet kembali, muat ulang rak yang tadinya kosong/gagal.
    LaunchedEffect(online) { if (online) vm.reloadIfNeeded() }

    if (!online) {
        OfflineHome(currentId = currentId, onPlay = onPlay)
        return
    }

    val remote = AppGraph.remote
    val banners = remote.banners
    val maintenance = remote.homeMaintenance
    val isOwner = AppGraph.account.isOwner

    // Profil, cari, dan banner ada di paling atas dan ikut tergulir bersama isi Beranda.
    val top: @Composable () -> Unit = {
        HomeTop(
            banners = banners,
            onOpenSearch = onOpenSearch,
            onOpenProfile = onOpenProfile,
            onOpenAnnouncements = onOpenAnnouncements,
            onOpenRanking = onOpenRanking
        )
    }

    Column(
        modifier = Modifier
            .fillMaxSize()
            .statusBarsPadding()
    ) {
        // Beranda sedang diperbaiki: user biasa hanya melihat playlist, favorit, dan unduhannya (banner tetap tampil).
        if (maintenance.enabled && !isOwner) {
            MaintenanceHome(
                maintenance = maintenance,
                currentId = currentId,
                onPlay = onPlay,
                onOpenSearch = onOpenSearch,
                top = top,
                modifier = Modifier.weight(1f)
            )
        } else LazyColumn(
        modifier = Modifier
            .weight(1f)
            .fillMaxWidth(),
        contentPadding = PaddingValues(bottom = overlay)
    ) {
        item { top() }
        if (AppGraph.remote.featureOn("streak")) item { com.jimz.jimztune.ui.stats.StreakCard() }
        if (AppGraph.remote.featureOn("ranking")) item {
            com.jimz.jimztune.ui.ranking.RankBanner(
                com.jimz.jimztune.data.stats.RankKind.LISTEN, com.jimz.jimztune.ui.theme.LocalCoverTheme.current.accent,
                Modifier.padding(horizontal = 16.dp, vertical = 6.dp)
            ) { onOpenRanking(com.jimz.jimztune.data.stats.RankKind.LISTEN, com.jimz.jimztune.data.stats.RankPeriod.ALL) }
        }
        if (maintenance.enabled && isOwner) {
            item { OwnerMaintenanceNotice() }
        }

        item {
            MoodChips(busy = vm.moodBusy) { label, query ->
                vm.playMood(label, query, onResult = { onPlay(it, 0) }, onFail = {
                    android.widget.Toast.makeText(ctx, "Belum bisa memuat lagu. Coba lagi.", android.widget.Toast.LENGTH_SHORT).show()
                })
            }
        }

        if (quick != null) {
            item {
                SectionTitle("Pilihan cepat untukmu")
                when {
                    quick.loading -> PlaceholderRows()
                    quick.failed -> RetryRow(vm::load)
                    else -> QuickPicks(quick.items.take(12), currentId, onPlay)
                }
            }
        }

        if (dial.size >= 3) {
            item {
                SectionTitle("Speed dial")
                SpeedDial(dial, currentId, onPlay)
            }
        }

        if (recent.isNotEmpty()) {
            item {
                SectionTitle("Lanjut dengar")
                SongRow(recent, currentId, onPlay)
            }
        }

        items(vm.shelves.drop(1)) { shelf ->
            SectionTitle(shelf.title)
            when {
                shelf.loading -> PlaceholderCards()
                shelf.failed -> RetryRow(vm::load)
                else -> SongRow(shelf.items, currentId, onPlay)
            }
        }

    }
    }
}

/** Catatan untuk owner: beranda normal tetap terlihat olehnya walau mode perbaikan sedang menyala. */
@Composable
private fun OwnerMaintenanceNotice() {
    Row(
        modifier = Modifier
            .padding(horizontal = 20.dp, vertical = 6.dp)
            .clip(RoundedCornerShape(50))
            .background(Color(0xFFFACC15).copy(alpha = 0.16f))
            .border(1.dp, Color(0xFFFACC15).copy(alpha = 0.4f), RoundedCornerShape(50))
            .padding(horizontal = 14.dp, vertical = 8.dp),
        verticalAlignment = Alignment.CenterVertically
    ) {
        Icon(Icons.Rounded.Construction, contentDescription = null, modifier = Modifier.size(18.dp), tint = Color(0xFFFACC15))
        Spacer(Modifier.width(8.dp))
        Text(
            "Mode perbaikan beranda AKTIF. Hanya kamu (owner) yang melihat beranda normal.",
            style = MaterialTheme.typography.bodySmall,
            color = Color.White.copy(alpha = 0.9f)
        )
    }
}

/**
 * Beranda saat mode perbaikan: tulisan "sedang dalam perbaikan", lalu hanya playlist buatan user,
 * lagu favorit, dan lagu yang sudah diunduh.
 */
@Composable
private fun MaintenanceHome(
    maintenance: HomeMaintenance,
    currentId: String?,
    onPlay: (List<SearchItem>, Int) -> Unit,
    onOpenSearch: () -> Unit,
    top: @Composable () -> Unit,
    modifier: Modifier = Modifier
) {
    val overlay = LocalOverlayPadding.current
    val accent = LocalCoverTheme.current.accent
    val playlists = AppGraph.library.playlists
    val favorites = AppGraph.library.favorites
    val downloads = AppGraph.downloads.songs

    LazyColumn(
        modifier = modifier.fillMaxWidth(),
        contentPadding = PaddingValues(bottom = overlay)
    ) {
        item { top() }
        if (AppGraph.remote.featureOn("streak")) item { com.jimz.jimztune.ui.stats.StreakCard() }
        if (AppGraph.remote.featureOn("ranking")) item {
            com.jimz.jimztune.ui.ranking.RankBanner(
                com.jimz.jimztune.data.stats.RankKind.LISTEN, com.jimz.jimztune.ui.theme.LocalCoverTheme.current.accent,
                Modifier.padding(horizontal = 16.dp, vertical = 6.dp)
            ) { onOpenRanking(com.jimz.jimztune.data.stats.RankKind.LISTEN, com.jimz.jimztune.data.stats.RankPeriod.ALL) }
        }
        item {
            val shape = RoundedCornerShape(22.dp)
            Row(
                modifier = Modifier
                    .fillMaxWidth()
                    .padding(horizontal = 20.dp, vertical = 10.dp)
                    .clip(shape)
                    .background(accent.copy(alpha = 0.14f))
                    .border(1.dp, accent.copy(alpha = 0.35f), shape)
                    .padding(16.dp),
                verticalAlignment = Alignment.CenterVertically
            ) {
                Icon(Icons.Rounded.Construction, contentDescription = null, tint = accent, modifier = Modifier.size(30.dp))
                Spacer(Modifier.width(14.dp))
                Column {
                    Text(maintenance.title, style = MaterialTheme.typography.titleSmall, fontWeight = FontWeight.Bold)
                    Text(
                        maintenance.message,
                        style = MaterialTheme.typography.bodySmall,
                        color = Color.White.copy(alpha = 0.72f),
                        modifier = Modifier.padding(top = 2.dp)
                    )
                }
            }
        }

        // ---- Playlist milik user ----
        item {
            SectionTitle("Playlist kamu")
            if (playlists.isEmpty()) {
                SoftHint("Belum ada playlist. Buat dari tab Koleksi.")
            } else {
                LazyRow(
                    contentPadding = PaddingValues(horizontal = 20.dp),
                    horizontalArrangement = Arrangement.spacedBy(16.dp)
                ) {
                    items(playlists, key = { it.id }) { p ->
                        Column(
                            modifier = Modifier
                                .width(148.dp)
                                .clip(RoundedCornerShape(16.dp))
                                .clickable(enabled = p.songs.isNotEmpty()) { onPlay(p.songs, 0) }
                        ) {
                            PlaylistCover(p, size = 148.dp, radius = 16.dp)
                            Spacer(Modifier.height(8.dp))
                            Text(
                                p.name,
                                style = MaterialTheme.typography.bodyMedium,
                                fontWeight = FontWeight.Medium,
                                maxLines = 1,
                                overflow = TextOverflow.Ellipsis
                            )
                            Text(
                                "${p.songs.size} lagu",
                                style = MaterialTheme.typography.bodySmall,
                                color = MaterialTheme.colorScheme.onSurfaceVariant
                            )
                        }
                    }
                }
            }
        }

        // ---- Lagu favorit ----
        item {
            SectionTitle("Lagu favorit")
            if (favorites.isEmpty()) SoftHint("Belum ada lagu favorit.")
            else Row(
                modifier = Modifier.padding(horizontal = 20.dp, vertical = 2.dp),
                horizontalArrangement = Arrangement.spacedBy(10.dp)
            ) {
                GradientPillButton("Putar semua", Icons.Rounded.PlayArrow, { onPlay(favorites, 0) })
                TonalPillButton("Acak", Icons.Rounded.Shuffle, { onPlay(favorites.shuffled(), 0) })
            }
        }
        itemsIndexed(favorites, key = { _, s -> "fav_" + s.id }) { index, song ->
            SongListRow(song = song, playing = song.id == currentId, onClick = { onPlay(favorites, index) })
        }

        // ---- Unduhan ----
        item {
            SectionTitle("Diunduh")
            if (downloads.isEmpty()) SoftHint("Belum ada lagu yang diunduh.")
            else Row(
                modifier = Modifier.padding(horizontal = 20.dp, vertical = 2.dp),
                horizontalArrangement = Arrangement.spacedBy(10.dp)
            ) {
                GradientPillButton("Putar semua", Icons.Rounded.PlayArrow, { onPlay(downloads, 0) })
                TonalPillButton("Acak", Icons.Rounded.Shuffle, { onPlay(downloads.shuffled(), 0) })
            }
        }
        itemsIndexed(downloads, key = { _, s -> "dl_" + s.id }) { index, song ->
            SongListRow(song = song, playing = song.id == currentId, onClick = { onPlay(downloads, index) })
        }
    }
}

@Composable
private fun SoftHint(text: String) {
    Text(
        text,
        style = MaterialTheme.typography.bodyMedium,
        color = MaterialTheme.colorScheme.onSurfaceVariant,
        modifier = Modifier.padding(horizontal = 20.dp)
    )
}

/**
 * Beranda saat offline: hanya lagu yang sudah diunduh (rak online dan "Terakhir diputar" yang belum diunduh
 * disembunyikan karena tidak bisa diputar tanpa internet).
 */
@Composable
private fun OfflineHome(currentId: String?, onPlay: (List<SearchItem>, Int) -> Unit) {
    val overlay = LocalOverlayPadding.current
    val downloads = AppGraph.downloads
    val songs = downloads.songs

    LazyColumn(
        modifier = Modifier
            .fillMaxSize()
            .statusBarsPadding(),
        contentPadding = PaddingValues(bottom = overlay)
    ) {
        item { Header(onOpenSearch = null) }
        item {
            Row(
                modifier = Modifier
                    .padding(horizontal = 20.dp, vertical = 6.dp)
                    .clip(RoundedCornerShape(50))
                    .background(Color.White.copy(alpha = 0.09f))
                    .border(1.dp, Color.White.copy(alpha = 0.14f), RoundedCornerShape(50))
                    .padding(horizontal = 14.dp, vertical = 8.dp),
                verticalAlignment = Alignment.CenterVertically
            ) {
                Icon(Icons.Rounded.CloudOff, contentDescription = null, modifier = Modifier.size(18.dp))
                Spacer(Modifier.width(8.dp))
                Text(
                    "Sedang offline · hanya lagu yang sudah diunduh",
                    style = MaterialTheme.typography.bodySmall,
                    color = Color.White.copy(alpha = 0.85f)
                )
            }
        }
        if (songs.isEmpty()) {
            item {
                EmptyState(
                    Icons.Rounded.CloudOff,
                    "Belum ada lagu yang diunduh",
                    "Saat online, pilih “Unduh untuk offline” di menu ⋮ lagu, atau “Unduh semua” di sebuah playlist."
                )
            }
        } else {
            item {
                SectionTitle("Lagu yang diunduh")
                Row(
                    modifier = Modifier.padding(horizontal = 20.dp, vertical = 2.dp),
                    horizontalArrangement = Arrangement.spacedBy(10.dp)
                ) {
                    GradientPillButton("Putar semua", Icons.Rounded.PlayArrow, { onPlay(songs, 0) })
                    TonalPillButton("Acak", Icons.Rounded.Shuffle, { onPlay(songs.shuffled(), 0) })
                }
                Spacer(Modifier.height(8.dp))
            }
            itemsIndexed(songs, key = { _, s -> s.id }) { index, song ->
                SongListRow(
                    song = song,
                    playing = song.id == currentId,
                    onClick = { onPlay(songs, index) }
                )
            }
        }
    }
}

@Composable
internal fun Header(onOpenSearch: (() -> Unit)?) {
    Row(
        modifier = Modifier.padding(start = 20.dp, end = 16.dp, top = 14.dp, bottom = 6.dp),
        verticalAlignment = Alignment.CenterVertically
    ) {
        Image(
            painter = painterResource(R.drawable.aoto_logo),
            contentDescription = null,
            modifier = Modifier
                .size(46.dp)
                .border(1.5.dp, Color.White.copy(alpha = 0.18f), CircleShape)
                .padding(2.dp)
                .clip(CircleShape)
        )
        Spacer(Modifier.width(14.dp))
        Column(modifier = Modifier.weight(1f)) {
            // Spasi tipis di ujung memberi ruang bagi huruf miring terakhir, kalau tidak "e" terlihat terpotong.
            Text(
                "Aoto ",
                style = BrandTextStyle.copy(fontSize = 24.sp, lineHeight = 28.sp),
                maxLines = 1
            )
            Text(
                greeting(),
                style = MaterialTheme.typography.bodyMedium,
                color = Color.White.copy(alpha = 0.72f),
                modifier = Modifier.padding(top = 1.dp)
            )
        }
        if (onOpenSearch != null) {
            Box(
                modifier = Modifier
                    .size(44.dp)
                    .clip(CircleShape)
                    .background(Color.White.copy(alpha = 0.09f))
                    .border(1.dp, Color.White.copy(alpha = 0.12f), CircleShape)
                    .clickable(onClick = onOpenSearch),
                contentAlignment = Alignment.Center
            ) {
                Icon(Icons.Rounded.Search, contentDescription = "Cari", modifier = Modifier.size(24.dp))
            }
        }
    }
}

private val MOODS = listOf(
    "Bahagia" to "lagu bahagia ceria playlist",
    "Santai" to "lagu santai chill playlist",
    "Perjalanan" to "lagu perjalanan road trip playlist",
    "Semangat" to "lagu semangat workout energik",
    "Galau" to "lagu galau sedih indonesia",
    "Fokus" to "musik fokus belajar instrumental"
)

/** Chip suasana: ketuk salah satu untuk langsung memutar campuran lagu yang cocok. */
@Composable
private fun MoodChips(busy: String?, onPick: (label: String, query: String) -> Unit) {
    androidx.compose.foundation.layout.Row(
        modifier = Modifier
            .padding(top = 14.dp)
            .horizontalScroll(androidx.compose.foundation.rememberScrollState())
            .padding(horizontal = 20.dp),
        horizontalArrangement = Arrangement.spacedBy(8.dp)
    ) {
        MOODS.forEach { (label, query) ->
            val loading = busy == label
            Text(
                if (loading) "$label…" else label,
                style = MaterialTheme.typography.labelLarge,
                fontWeight = FontWeight.Medium,
                color = if (loading) JimzBlueSoft else MaterialTheme.colorScheme.onSurface,
                modifier = Modifier
                    .clip(RoundedCornerShape(50))
                    .background(Color.White.copy(alpha = 0.10f))
                    .clickable(enabled = busy == null) { onPick(label, query) }
                    .padding(horizontal = 16.dp, vertical = 9.dp)
            )
        }
    }
}

/** Kisi sampul 3 kolom: akses cepat ke lagu yang paling sering diputar. */
@Composable
private fun SpeedDial(songs: List<SearchItem>, currentId: String?, onPlay: (List<SearchItem>, Int) -> Unit) {
    Column(
        modifier = Modifier.padding(horizontal = 20.dp),
        verticalArrangement = Arrangement.spacedBy(10.dp)
    ) {
        songs.chunked(3).forEachIndexed { r, rowSongs ->
            Row(horizontalArrangement = Arrangement.spacedBy(10.dp)) {
                rowSongs.forEachIndexed { c, song ->
                    val index = r * 3 + c
                    val playing = song.id == currentId
                    Box(
                        modifier = Modifier
                            .weight(1f)
                            .aspectRatio(1f)
                            .clip(RoundedCornerShape(16.dp))
                            .background(MaterialTheme.colorScheme.surfaceVariant)
                            .clickable { onPlay(songs, index) }
                    ) {
                        AsyncImage(
                            model = song.thumbnailUrl,
                            contentDescription = null,
                            contentScale = ContentScale.Crop,
                            modifier = Modifier.fillMaxSize()
                        )
                        Box(
                            Modifier
                                .fillMaxSize()
                                .background(Brush.verticalGradient(listOf(Color.Transparent, Color.Black.copy(alpha = 0.72f)), startY = 120f))
                        )
                        Text(
                            song.title,
                            style = MaterialTheme.typography.labelMedium,
                            fontWeight = FontWeight.SemiBold,
                            color = if (playing) JimzBlueSoft else Color.White,
                            maxLines = 2,
                            overflow = TextOverflow.Ellipsis,
                            modifier = Modifier.align(Alignment.BottomStart).padding(8.dp)
                        )
                    }
                }
                repeat(3 - rowSongs.size) { Spacer(Modifier.weight(1f)) }
            }
        }
    }
}

@Composable
private fun SectionTitle(title: String) {
    Text(
        title,
        style = MaterialTheme.typography.titleLarge,
        fontWeight = FontWeight.SemiBold,
        modifier = Modifier.padding(start = 20.dp, end = 20.dp, top = 28.dp, bottom = 12.dp)
    )
}

/** Daftar lagu ringkas dalam kolom, bisa digeser ke samping (gaya "Quick picks"). */
@Composable
private fun QuickPicks(songs: List<SearchItem>, currentId: String?, onPlay: (List<SearchItem>, Int) -> Unit) {
    val rows = minOf(4, songs.size).coerceAtLeast(1)
    LazyHorizontalGrid(
        rows = GridCells.Fixed(rows),
        modifier = Modifier.height(64.dp * rows),
        contentPadding = PaddingValues(horizontal = 20.dp),
        horizontalArrangement = Arrangement.spacedBy(16.dp)
    ) {
        gridItemsIndexed(songs) { index, song ->
            QuickPickRow(song, playing = song.id == currentId) { onPlay(songs, index) }
        }
    }
}

@Composable
private fun QuickPickRow(song: SearchItem, playing: Boolean, onClick: () -> Unit) {
    Row(
        modifier = Modifier
            .width(300.dp)
            .height(64.dp)
            .clip(RoundedCornerShape(14.dp))
            .clickable(onClick = onClick)
            .padding(vertical = 6.dp),
        verticalAlignment = Alignment.CenterVertically
    ) {
        AsyncImage(
            model = song.thumbnailUrl,
            contentDescription = null,
            contentScale = ContentScale.Crop,
            modifier = Modifier
                .size(52.dp)
                .clip(RoundedCornerShape(10.dp))
                .background(MaterialTheme.colorScheme.surfaceVariant)
        )
        Spacer(Modifier.width(12.dp))
        Column {
            Text(
                song.title,
                style = MaterialTheme.typography.bodyLarge,
                color = if (playing) JimzBlueSoft else MaterialTheme.colorScheme.onSurface,
                maxLines = 1,
                overflow = TextOverflow.Ellipsis
            )
            Text(
                song.subtitle.substringBefore(" • "),
                style = MaterialTheme.typography.bodySmall,
                color = MaterialTheme.colorScheme.onSurfaceVariant,
                maxLines = 1,
                overflow = TextOverflow.Ellipsis
            )
        }
    }
}

@Composable
private fun SongRow(songs: List<SearchItem>, currentId: String?, onPlay: (List<SearchItem>, Int) -> Unit) {
    LazyRow(
        contentPadding = PaddingValues(horizontal = 20.dp),
        horizontalArrangement = Arrangement.spacedBy(16.dp)
    ) {
        itemsIndexed(songs) { index, song ->
            SongCard(song, playing = song.id == currentId) { onPlay(songs, index) }
        }
    }
}

@Composable
private fun SongCard(song: SearchItem, playing: Boolean, onClick: () -> Unit) {
    val shape = RoundedCornerShape(16.dp)
    Column(
        modifier = Modifier
            .width(148.dp)
            .clip(shape)
            .clickable(onClick = onClick)
    ) {
        AsyncImage(
            model = song.thumbnailUrl,
            contentDescription = null,
            contentScale = ContentScale.Crop,
            modifier = Modifier
                .size(148.dp)
                .clip(shape)
                .background(MaterialTheme.colorScheme.surfaceVariant)
        )
        Spacer(Modifier.height(8.dp))
        Text(
            song.title,
            style = MaterialTheme.typography.bodyMedium,
            fontWeight = FontWeight.Medium,
            color = if (playing) JimzBlueSoft else MaterialTheme.colorScheme.onSurface,
            maxLines = 1,
            overflow = TextOverflow.Ellipsis
        )
        Text(
            song.subtitle.substringBefore(" • "),
            style = MaterialTheme.typography.bodySmall,
            color = MaterialTheme.colorScheme.onSurfaceVariant,
            maxLines = 1,
            overflow = TextOverflow.Ellipsis
        )
    }
}

@Composable
private fun RetryRow(onRetry: () -> Unit) {
    Row(
        modifier = Modifier.padding(horizontal = 20.dp),
        verticalAlignment = Alignment.CenterVertically
    ) {
        Text(
            "Gagal memuat.",
            style = MaterialTheme.typography.bodyMedium,
            color = MaterialTheme.colorScheme.onSurfaceVariant
        )
        TonalPillButton("Coba lagi", Icons.Rounded.Refresh, onRetry)
    }
}

@Composable
private fun PlaceholderCards() {
    Row(
        modifier = Modifier.padding(horizontal = 20.dp),
        horizontalArrangement = Arrangement.spacedBy(16.dp)
    ) {
        repeat(3) {
            Spacer(
                Modifier
                    .size(148.dp)
                    .clip(RoundedCornerShape(16.dp))
                    .background(MaterialTheme.colorScheme.surfaceVariant.copy(alpha = 0.45f))
            )
        }
    }
}

@Composable
private fun PlaceholderRows() {
    Column(
        modifier = Modifier.padding(horizontal = 20.dp),
        verticalArrangement = Arrangement.spacedBy(12.dp)
    ) {
        repeat(4) {
            Spacer(
                Modifier
                    .width(300.dp)
                    .height(52.dp)
                    .clip(RoundedCornerShape(12.dp))
                    .background(MaterialTheme.colorScheme.surfaceVariant.copy(alpha = 0.45f))
            )
        }
    }
}

private fun greeting(): String {
    val hour = Calendar.getInstance().get(Calendar.HOUR_OF_DAY)
    return when {
        hour < 11 -> "Selamat pagi"
        hour < 15 -> "Selamat siang"
        hour < 19 -> "Selamat sore"
        else -> "Selamat malam"
    }
}

/**
 * Memudarkan tepi atas daftar, jadi konten yang tergulir ke bawah banner menghilang halus
 * (bukan terpotong keras di tepi banner).
 */
private fun Modifier.fadeTopEdge(height: Dp = 22.dp): Modifier = this
    .graphicsLayer { compositingStrategy = CompositingStrategy.Offscreen }
    .drawWithContent {
        drawContent()
        val h = height.toPx()
        drawRect(
            brush = Brush.verticalGradient(
                colors = listOf(Color.Transparent, Color.Black),
                startY = 0f,
                endY = h
            ),
            size = Size(size.width, h),
            blendMode = BlendMode.DstIn
        )
    }
