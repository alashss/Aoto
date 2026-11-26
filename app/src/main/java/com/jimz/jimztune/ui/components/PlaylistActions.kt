package com.jimz.jimztune.ui.components

import android.content.Context
import android.widget.Toast
import androidx.compose.foundation.background
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.layout.width
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.rounded.Favorite
import androidx.compose.material.icons.rounded.FavoriteBorder
import androidx.compose.material.icons.rounded.LibraryAdd
import androidx.compose.material.icons.rounded.MoreVert
import androidx.compose.material.icons.rounded.LibraryMusic
import androidx.compose.material.icons.rounded.Share
import androidx.compose.material3.Icon
import androidx.compose.material3.IconButton
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.rememberCoroutineScope
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
import com.jimz.jimztune.util.searchPlaylistShareText
import com.jimz.jimztune.util.shareText
import kotlinx.coroutines.CancellationException
import kotlinx.coroutines.CoroutineScope
import kotlinx.coroutines.launch

/** Warna hati "disukai", sama dengan hati favorit lagu. */
val LikeColor = Color(0xFFFF5C8A)

/** Playlist YouTube (bukan album / artis). Dipakai untuk memutuskan apakah aksi like / simpan / bagikan ditampilkan. */
val SearchItem.isPlaylistRef: Boolean
    get() = type == SearchType.PLAYLIST || (type != SearchType.SONG && type != SearchType.ARTIST &&
        (id.startsWith("VL") || id.startsWith("PL") || id.startsWith("RDCLAK")))

/**
 * Menyalin seluruh isi playlist hasil pencarian ke koleksi sebagai playlist baru milik sendiri.
 * Dipanggil dari tombol "Simpan ke koleksi"; [onSaved] dipanggil di thread utama bila berhasil.
 */
fun saveSearchPlaylist(scope: CoroutineScope, context: Context, ref: SearchItem, onSaved: () -> Unit = {}) {
    Toast.makeText(context, "Menyimpan playlist…", Toast.LENGTH_SHORT).show()
    scope.launch {
        try {
            val page = try {
                AppGraph.detail.playlistAll(ref.id)
            } catch (e: CancellationException) {
                throw e
            } catch (_: Exception) {
                AppGraph.detail.album(ref.id)
            }
            val songs = page.tracks.filter { it.type == SearchType.SONG }
            if (songs.isEmpty()) throw IllegalStateException("kosong")
            val name = page.title.ifBlank { ref.title }.ifBlank { "Playlist tersimpan" }
            AppGraph.library.importPlaylist(name, songs)
            Toast.makeText(context, "“$name” disimpan (${songs.size} lagu) di Koleksi → Playlist", Toast.LENGTH_LONG).show()
            onSaved()
        } catch (e: CancellationException) {
            throw e
        } catch (_: Exception) {
            Toast.makeText(context, "Playlist tidak bisa disimpan. Periksa koneksi lalu coba lagi.", Toast.LENGTH_LONG).show()
        }
    }
}

/** Menyukai / batal menyukai playlist, lengkap dengan umpan balik singkat. */
fun toggleLikePlaylist(context: Context, ref: SearchItem) {
    val liked = AppGraph.library.togglePlaylistLike(ref)
    Toast.makeText(
        context,
        if (liked) "Playlist disukai. Ada di Koleksi → Playlist" else "Dihapus dari playlist disukai",
        Toast.LENGTH_SHORT
    ).show()
}

fun sharePlaylistRef(context: Context, ref: SearchItem) {
    if (!com.jimz.jimztune.AppConfig.SHARE_ENABLED) return
    if (!AppGraph.remote.featureOn("share")) {
        Toast.makeText(context, "Fitur bagikan sedang dimatikan", Toast.LENGTH_SHORT).show()
        return
    }
    com.jimz.jimztune.data.share.ShareLinks.shareSearchPlaylist(context, ref, searchPlaylistShareText(ref.title.ifBlank { "Playlist" }, ref.id))
}

/**
 * Tombol hati kecil + menu ⋮ untuk baris playlist di hasil pencarian: suka, simpan ke koleksi, bagikan, buka.
 */
@Composable
fun PlaylistRefMenuButton(
    item: SearchItem,
    onOpen: () -> Unit,
    modifier: Modifier = Modifier
) {
    val context = LocalContext.current
    val scope = rememberCoroutineScope()
    val library = AppGraph.library
    var open by remember { mutableStateOf(false) }
    val liked = library.isPlaylistLiked(item.id)

    Row(modifier = modifier, verticalAlignment = Alignment.CenterVertically) {
        IconButton(onClick = { toggleLikePlaylist(context, item) }) {
            Icon(
                if (liked) Icons.Rounded.Favorite else Icons.Rounded.FavoriteBorder,
                contentDescription = if (liked) "Batal suka playlist" else "Suka playlist",
                tint = if (liked) LikeColor else MaterialTheme.colorScheme.onSurfaceVariant
            )
        }
        IconButton(onClick = { open = true }) {
            Icon(
                Icons.Rounded.MoreVert,
                contentDescription = "Menu playlist",
                tint = MaterialTheme.colorScheme.onSurfaceVariant
            )
        }
    }

    if (open) {
        JimzBottomSheet(onDismiss = { open = false }) {
            SheetHero(
                thumbnailUrl = item.thumbnailUrl,
                title = item.title,
                subtitle = item.subtitle.ifBlank { null }
            )
            Spacer(Modifier.size(4.dp))
            MenuGroup {
                MenuRow(
                    icon = if (liked) Icons.Rounded.Favorite else Icons.Rounded.FavoriteBorder,
                    label = if (liked) "Batal suka playlist" else "Suka playlist",
                    subtitle = "Muncul di Koleksi → Playlist",
                    onClick = {
                        toggleLikePlaylist(context, item)
                        open = false
                    }
                )
                MenuDivider()
                MenuRow(
                    icon = Icons.Rounded.LibraryAdd,
                    label = "Simpan ke koleksi",
                    subtitle = "Salin semua lagunya jadi playlist milikmu",
                    onClick = {
                        open = false
                        saveSearchPlaylist(scope, context, item)
                    }
                )
                if (com.jimz.jimztune.AppConfig.SHARE_ENABLED) {
                    MenuDivider()
                    MenuRow(
                        icon = Icons.Rounded.Share,
                        label = "Bagikan",
                        onClick = {
                            open = false
                            sharePlaylistRef(context, item)
                        }
                    )
                }
                MenuDivider()
                MenuRow(
                    icon = Icons.Rounded.LibraryMusic,
                    label = "Buka playlist",
                    onClick = {
                        open = false
                        onOpen()
                    }
                )
            }
        }
    }
}
