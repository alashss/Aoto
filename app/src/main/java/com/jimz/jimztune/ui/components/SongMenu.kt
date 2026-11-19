package com.jimz.jimztune.ui.components

import android.widget.Toast
import androidx.compose.foundation.background
import androidx.compose.foundation.clickable
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.heightIn
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.layout.width
import androidx.compose.foundation.lazy.LazyColumn
import androidx.compose.foundation.rememberScrollState
import androidx.compose.foundation.verticalScroll
import androidx.compose.foundation.lazy.items
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.rounded.Add
import androidx.compose.material.icons.rounded.Check
import androidx.compose.material.icons.rounded.Close
import androidx.compose.material.icons.rounded.Delete
import androidx.compose.material.icons.rounded.Download
import androidx.compose.material.icons.rounded.DownloadDone
import androidx.compose.material.icons.rounded.Favorite
import androidx.compose.material.icons.rounded.FavoriteBorder
import androidx.compose.material.icons.rounded.Album
import androidx.compose.material.icons.rounded.MoreVert
import androidx.compose.material.icons.rounded.Person
import androidx.compose.material.icons.rounded.Share
import androidx.compose.material.icons.rounded.PlaylistAdd
import androidx.compose.material3.Icon
import androidx.compose.material3.IconButton
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.rememberCoroutineScope
import androidx.compose.material.icons.automirrored.rounded.PlaylistPlay
import androidx.compose.material.icons.automirrored.rounded.QueueMusic
import kotlinx.coroutines.launch
import androidx.compose.runtime.setValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clip
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.layout.ContentScale
import androidx.compose.ui.platform.LocalContext
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.text.style.TextOverflow
import androidx.compose.ui.unit.dp
import coil.compose.AsyncImage
import com.jimz.jimztune.AppGraph
import com.jimz.jimztune.data.model.SearchItem
import com.jimz.jimztune.data.model.SearchType
import com.jimz.jimztune.ui.player.AudioFxSheet
import com.jimz.jimztune.ui.player.SleepTimerSheet
import com.jimz.jimztune.ui.player.SoundQuickPanel
import com.jimz.jimztune.ui.theme.LocalCoverTheme
import com.jimz.jimztune.util.shareText
import com.jimz.jimztune.util.songShareText

/**
 * Tombol ⋮ untuk satu lagu. Menu muncul sebagai sheet dari bawah: kepala berisi cover dan tombol hati,
 * ubin aksi cepat (favorit, playlist, unduh, bagikan), daftar aksi lain, dan, kalau dibuka dari layar pemutar
 * ([showPlayerControls]), pengatur suara ringkas plus timer tidur.
 */
@Composable
fun SongMenuButton(
    song: SearchItem,
    modifier: Modifier = Modifier,
    tint: Color = MaterialTheme.colorScheme.onSurfaceVariant,
    extraItems: List<Pair<String, () -> Unit>> = emptyList(),
    showPlayerControls: Boolean = false
) {
    var open by remember { mutableStateOf(false) }
    var picker by remember { mutableStateOf(false) }
    var fxSheet by remember { mutableStateOf(false) }
    var sleepSheet by remember { mutableStateOf(false) }
    val library = AppGraph.library
    val downloads = AppGraph.downloads
    val context = LocalContext.current
    val openDetail = LocalOpenDetail.current
    val queueActions = LocalQueueActions.current
    val scope = rememberCoroutineScope()

    IconButton(onClick = { open = true }, modifier = modifier) {
        Icon(Icons.Rounded.MoreVert, contentDescription = "Menu lagu", tint = tint)
    }

    if (open) {
        val artistName = song.subtitle.substringBefore(" • ").trim()
        val fav = library.isFavorite(song.id)
        val accent = LocalCoverTheme.current.accent
        JimzBottomSheet(onDismiss = { open = false }) {
            Column(Modifier.verticalScroll(rememberScrollState())) {
                SheetHero(
                    thumbnailUrl = song.thumbnailUrl,
                    title = song.title,
                    subtitle = artistName.ifBlank { null },
                    trailing = {
                        IconButton(onClick = { library.toggleFavorite(song) }) {
                            Icon(
                                if (fav) Icons.Rounded.Favorite else Icons.Rounded.FavoriteBorder,
                                contentDescription = if (fav) "Hapus dari favorit" else "Tambah ke favorit",
                                tint = if (fav) accent else Color.White.copy(alpha = 0.8f)
                            )
                        }
                    }
                )

                // Aksi cepat.
                QuickTileRow {
                    QuickTile(
                        icon = Icons.Rounded.PlaylistAdd,
                        label = "Playlist",
                        modifier = Modifier.weight(1f),
                        onClick = {
                            open = false
                            picker = true
                        }
                    )
                    when {
                        downloads.isDownloaded(song.id) -> QuickTile(
                            icon = Icons.Rounded.DownloadDone,
                            label = "Terunduh",
                            active = true,
                            modifier = Modifier.weight(1f),
                            onClick = { Toast.makeText(context, "Sudah tersimpan untuk offline", Toast.LENGTH_SHORT).show() }
                        )
                        downloads.active.containsKey(song.id) -> QuickTile(
                            icon = Icons.Rounded.Close,
                            label = "Batalkan",
                            modifier = Modifier.weight(1f),
                            onClick = {
                                downloads.cancel(song.id)
                                open = false
                            }
                        )
                        else -> QuickTile(
                            icon = Icons.Rounded.Download,
                            label = "Unduh",
                            modifier = Modifier.weight(1f),
                            onClick = {
                                if (AppGraph.remote.featureOn("downloads")) {
                                    downloads.download(song)
                                    Toast.makeText(context, "Mengunduh “${song.title}”…", Toast.LENGTH_SHORT).show()
                                } else {
                                    Toast.makeText(context, "Fitur unduhan sedang dimatikan", Toast.LENGTH_SHORT).show()
                                }
                                open = false
                            }
                        )
                    }
                    if (com.jimz.jimztune.AppConfig.SHARE_ENABLED) QuickTile(
                        icon = Icons.Rounded.Share,
                        label = "Bagikan",
                        modifier = Modifier.weight(1f),
                        onClick = {
                            open = false
                            com.jimz.jimztune.data.share.ShareLinks.shareSong(context, song, songShareText(song))
                        }
                    )
                }

                if (showPlayerControls) {
                    SoundQuickPanel(
                        onOpenFull = {
                            open = false
                            fxSheet = true
                        },
                        onOpenSleep = {
                            open = false
                            sleepSheet = true
                        }
                    )
                }

                MenuGroup {
                    MenuRow(
                        icon = Icons.AutoMirrored.Rounded.PlaylistPlay,
                        label = "Putar berikutnya",
                        onClick = {
                            open = false
                            queueActions.playNext(song)
                            Toast.makeText(context, "Diputar setelah lagu ini", Toast.LENGTH_SHORT).show()
                        }
                    )
                    MenuDivider()
                    MenuRow(
                        icon = Icons.AutoMirrored.Rounded.QueueMusic,
                        label = "Tambah ke antrean",
                        onClick = {
                            open = false
                            queueActions.addToQueue(song)
                            Toast.makeText(context, "Ditambahkan ke antrean", Toast.LENGTH_SHORT).show()
                        }
                    )
                    if (song.artistId != null || artistName.isNotBlank()) {
                        MenuDivider()
                        MenuRow(
                            icon = Icons.Rounded.Person,
                            label = "Lihat artis",
                            subtitle = artistName.ifBlank { null },
                            onClick = {
                                open = false
                                val id = song.artistId
                                if (id != null) {
                                    openDetail(SearchItem(id, SearchType.ARTIST, artistName, "", null))
                                } else {
                                    // Lagu lama / hasil impor belum menyimpan id artis: cari artisnya lewat nama.
                                    scope.launch {
                                        val hit = try {
                                            AppGraph.searchRepository.search(artistName, SearchType.ARTIST, quick = true)
                                                .items.firstOrNull { it.type == SearchType.ARTIST }
                                        } catch (_: Exception) {
                                            null
                                        }
                                        if (hit != null) openDetail(hit)
                                        else Toast.makeText(context, "Artis tidak ditemukan", Toast.LENGTH_SHORT).show()
                                    }
                                }
                            }
                        )
                    }
                    song.albumId?.let { albumId ->
                        MenuDivider()
                        MenuRow(
                            icon = Icons.Rounded.Album,
                            label = "Lihat album",
                            onClick = {
                                open = false
                                openDetail(SearchItem(albumId, SearchType.ALBUM, song.title, "", song.thumbnailUrl))
                            }
                        )
                    }
                }

                val removable = downloads.isDownloaded(song.id)
                if (removable || extraItems.isNotEmpty()) {
                    MenuGroup {
                        if (removable) {
                            MenuRow(
                                icon = Icons.Rounded.Delete,
                                label = "Hapus unduhan",
                                subtitle = "Lagu tetap ada, hanya file offline yang dihapus",
                                destructive = true,
                                onClick = {
                                    downloads.delete(song.id)
                                    open = false
                                }
                            )
                        }
                        extraItems.forEachIndexed { i, (label, action) ->
                            if (removable || i > 0) MenuDivider()
                            MenuRow(
                                icon = Icons.Rounded.Delete,
                                label = label,
                                destructive = true,
                                onClick = {
                                    open = false
                                    action()
                                }
                            )
                        }
                    }
                }
                Spacer(Modifier.size(8.dp))
            }
        }
    }

    if (picker) AddToPlaylistDialog(song) { picker = false }
    if (fxSheet) AudioFxSheet(onDismiss = { fxSheet = false })
    if (sleepSheet) SleepTimerSheet(onDismiss = { sleepSheet = false })
}

/** Sheet "Simpan ke playlist": daftar playlist dengan gambar sampulnya, plus tombol playlist baru. */
@Composable
fun AddToPlaylistDialog(song: SearchItem, onDismiss: () -> Unit) {
    val library = AppGraph.library
    val context = LocalContext.current
    var creating by remember { mutableStateOf(false) }

    if (creating) {
        PlaylistEditorSheet(
            title = "Playlist baru",
            confirmLabel = "Buat",
            thumbs = listOfNotNull(song.thumbnailUrl),
            onConfirm = { name, color, cover, _ ->
                val p = library.createPlaylist(name, first = song, color = color)
                if (cover != null) library.setCover(p.id, cover)
                Toast.makeText(context, "Disimpan ke “${p.name}”", Toast.LENGTH_SHORT).show()
                onDismiss()
            },
            onDismiss = { creating = false }
        )
        return
    }

    JimzBottomSheet(onDismiss = onDismiss) {
        SheetTitle(
            Icons.Rounded.PlaylistAdd,
            "Simpan ke playlist",
            modifier = Modifier.padding(horizontal = 24.dp),
            subtitle = song.title
        )
        Spacer(Modifier.size(6.dp))
        SheetAction(
            icon = Icons.Rounded.Add,
            label = "Playlist baru",
            subtitle = "Beri nama, gambar, dan warna sendiri",
            onClick = { creating = true }
        )
        LazyColumn(modifier = Modifier.heightIn(max = 320.dp)) {
            items(library.playlists, key = { it.id }) { p ->
                val already = p.songs.any { it.id == song.id }
                Row(
                    modifier = Modifier
                        .fillMaxWidth()
                        .clickable {
                            val added = library.addToPlaylist(p.id, song)
                            Toast.makeText(
                                context,
                                if (added) "Disimpan ke “${p.name}”" else "Sudah ada di “${p.name}”",
                                Toast.LENGTH_SHORT
                            ).show()
                            onDismiss()
                        }
                        .padding(horizontal = 24.dp, vertical = 8.dp),
                    verticalAlignment = Alignment.CenterVertically
                ) {
                    PlaylistCover(p, size = 48.dp)
                    Spacer(Modifier.width(16.dp))
                    Column(modifier = Modifier.weight(1f)) {
                        Text(
                            p.name,
                            style = MaterialTheme.typography.bodyLarge,
                            fontWeight = FontWeight.Medium,
                            color = Color.White,
                            maxLines = 1,
                            overflow = TextOverflow.Ellipsis
                        )
                        Text(
                            "${p.songs.size} lagu",
                            style = MaterialTheme.typography.bodySmall,
                            color = Color.White.copy(alpha = 0.55f)
                        )
                    }
                    if (already) {
                        Icon(
                            Icons.Rounded.Check,
                            contentDescription = "Sudah ada",
                            tint = LocalCoverTheme.current.accent
                        )
                    }
                }
            }
        }
    }
}
