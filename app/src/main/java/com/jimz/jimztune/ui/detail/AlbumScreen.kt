package com.jimz.jimztune.ui.detail

import androidx.compose.material3.Icon
import androidx.compose.material.icons.rounded.MoreVert
import androidx.compose.foundation.background
import androidx.compose.foundation.clickable
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.PaddingValues
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.WindowInsets
import androidx.compose.foundation.layout.asPaddingValues
import androidx.compose.foundation.layout.statusBars
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.layout.statusBarsPadding
import androidx.compose.foundation.layout.width
import androidx.compose.foundation.lazy.LazyColumn
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.rounded.ArrowBack
import androidx.compose.material.icons.rounded.PlayArrow
import androidx.compose.material.icons.rounded.Shuffle
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.runtime.LaunchedEffect
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableIntStateOf
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.setValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clip
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.layout.ContentScale
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.text.style.TextAlign
import androidx.compose.ui.text.style.TextOverflow
import androidx.compose.ui.unit.dp
import coil.compose.AsyncImage
import com.jimz.jimztune.AppGraph
import com.jimz.jimztune.data.model.AlbumPage
import com.jimz.jimztune.data.model.SearchItem
import com.jimz.jimztune.data.model.SearchType
import com.jimz.jimztune.ui.components.GlassIconButton
import com.jimz.jimztune.ui.components.GradientPillButton
import com.jimz.jimztune.ui.components.LocalOverlayPadding
import com.jimz.jimztune.ui.components.MiniEqualizer
import com.jimz.jimztune.ui.components.SongMenuButton
import com.jimz.jimztune.ui.components.CompactIconButton
import com.jimz.jimztune.ui.components.DownloadAllIconButton
import com.jimz.jimztune.ui.components.LikeColor
import com.jimz.jimztune.ui.components.saveSearchPlaylist
import com.jimz.jimztune.ui.components.sharePlaylistRef
import com.jimz.jimztune.ui.components.toggleLikePlaylist
import com.jimz.jimztune.util.shareText
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.material.icons.rounded.Favorite
import androidx.compose.material.icons.rounded.FavoriteBorder
import androidx.compose.material.icons.rounded.LibraryAdd
import androidx.compose.material.icons.rounded.Share
import androidx.compose.runtime.rememberCoroutineScope
import androidx.compose.ui.platform.LocalContext
import com.jimz.jimztune.ui.components.TonalPillButton
import com.jimz.jimztune.ui.theme.LocalCoverTheme
import kotlinx.coroutines.CancellationException

@Composable
fun AlbumScreen(
    ref: SearchItem,
    currentId: String?,
    onPlay: (List<SearchItem>, Int) -> Unit,
    onOpen: (SearchItem) -> Unit,
    onBack: () -> Unit
) {
    val cover = LocalCoverTheme.current
    var attempt by remember { mutableIntStateOf(0) }
    var state by remember(ref.id) { mutableStateOf<Load<AlbumPage>>(Load.Loading) }

    LaunchedEffect(ref.id, attempt) {
        state = Load.Loading
        state = try {
            Load.Ok(AppGraph.detail.album(ref.id))
        } catch (e: CancellationException) {
            throw e
        } catch (e: Exception) {
            Load.Fail("Album tidak bisa dimuat. Periksa koneksi lalu coba lagi.")
        }
    }

    Box(
        Modifier
            .fillMaxSize()
            .detailBackground(cover)
    ) {
        when (val s = state) {
            Load.Loading -> DetailLoading()
            is Load.Fail -> DetailError(s.message, onRetry = { attempt++ })
            is Load.Ok -> AlbumContent(
                s.value, currentId, onPlay, onOpen,
                ref = ref,
                showThumbs = ref.type == SearchType.PLAYLIST || ref.id.startsWith("VL") || ref.id.startsWith("PL") || ref.id.startsWith("RDCLAK")
            )
        }
        Box(
            Modifier
                .statusBarsPadding()
                .padding(start = 16.dp, top = 10.dp)
        ) {
            GlassIconButton(Icons.Rounded.ArrowBack, "Kembali", onClick = onBack)
        }
    }
}

@Composable
private fun AlbumContent(
    page: AlbumPage,
    currentId: String?,
    onPlay: (List<SearchItem>, Int) -> Unit,
    onOpen: (SearchItem) -> Unit,
    ref: SearchItem,
    showThumbs: Boolean = false
) {
    val context = LocalContext.current
    val scope = rememberCoroutineScope()
    val overlay = LocalOverlayPadding.current
    val accent = LocalCoverTheme.current.accent
    val topInset = WindowInsets.statusBars.asPaddingValues().calculateTopPadding()

    LazyColumn(
        modifier = Modifier.fillMaxSize(),
        contentPadding = PaddingValues(top = topInset + 60.dp, bottom = overlay)
    ) {
        item {
            Column(
                Modifier
                    .fillMaxWidth()
                    .padding(horizontal = 24.dp),
                horizontalAlignment = Alignment.CenterHorizontally
            ) {
                AsyncImage(
                    model = page.thumbnailUrl,
                    contentDescription = null,
                    contentScale = ContentScale.Crop,
                    modifier = Modifier
                        .size(224.dp)
                        .clip(RoundedCornerShape(18.dp))
                        .background(Color.White.copy(alpha = 0.08f))
                )
                Spacer(Modifier.height(18.dp))
                Text(
                    page.title,
                    style = MaterialTheme.typography.headlineSmall,
                    fontWeight = FontWeight.Bold,
                    color = Color.White,
                    textAlign = TextAlign.Center,
                    maxLines = 3,
                    overflow = TextOverflow.Ellipsis
                )
                if (page.artistName.isNotBlank()) {
                    val canOpen = page.artistId != null
                    Text(
                        page.artistName,
                        style = MaterialTheme.typography.titleMedium,
                        color = if (canOpen) accent else Color.White.copy(alpha = 0.8f),
                        modifier = Modifier
                            .padding(top = 4.dp)
                            .then(
                                if (canOpen) Modifier.clickable {
                                    onOpen(
                                        SearchItem(page.artistId.orEmpty(), SearchType.ARTIST, page.artistName, "", null)
                                    )
                                } else Modifier
                            )
                    )
                }
                if (page.info.isNotBlank()) {
                    Text(
                        page.info,
                        style = MaterialTheme.typography.bodySmall,
                        color = Color.White.copy(alpha = 0.6f),
                        textAlign = TextAlign.Center,
                        modifier = Modifier.padding(top = 4.dp)
                    )
                }
                Spacer(Modifier.height(18.dp))
                Row(verticalAlignment = Alignment.CenterVertically) {
                    GradientPillButton("Putar", Icons.Rounded.PlayArrow, { onPlay(page.tracks, 0) })
                    Spacer(Modifier.width(10.dp))
                    TonalPillButton("Acak", Icons.Rounded.Shuffle, { onPlay(page.tracks.shuffled(), 0) })
                }
                if (page.tracks.isNotEmpty()) {
                    Spacer(Modifier.height(12.dp))
                    // Aksi sekunder berupa ikon kecil: unduh semua, suka, simpan ke koleksi, bagikan.
                    Row(
                        horizontalArrangement = Arrangement.spacedBy(10.dp),
                        verticalAlignment = Alignment.CenterVertically
                    ) {
                        DownloadAllIconButton(page.tracks)
                        if (showThumbs) {
                            val likeItem = SearchItem(
                                id = ref.id,
                                type = SearchType.PLAYLIST,
                                title = page.title.ifBlank { ref.title },
                                subtitle = page.artistName.ifBlank { ref.subtitle },
                                thumbnailUrl = page.thumbnailUrl ?: ref.thumbnailUrl
                            )
                            val liked = AppGraph.library.isPlaylistLiked(ref.id)
                            CompactIconButton(
                                if (liked) Icons.Rounded.Favorite else Icons.Rounded.FavoriteBorder,
                                if (liked) "Batal suka playlist" else "Suka playlist",
                                onClick = { toggleLikePlaylist(context, likeItem) },
                                tint = if (liked) LikeColor else Color.White,
                                active = liked
                            )
                            // Simpan & bagikan digabung ke satu menu supaya deretan tombol tidak penuh.
                            val moreOpen = remember { mutableStateOf(false) }
                            Box {
                                CompactIconButton(
                                    Icons.Rounded.MoreVert,
                                    "Lainnya",
                                    onClick = { moreOpen.value = true }
                                )
                                androidx.compose.material3.DropdownMenu(
                                    expanded = moreOpen.value,
                                    onDismissRequest = { moreOpen.value = false }
                                ) {
                                    androidx.compose.material3.DropdownMenuItem(
                                        text = { Text("Simpan ke koleksi") },
                                        leadingIcon = { Icon(Icons.Rounded.LibraryAdd, null) },
                                        onClick = { moreOpen.value = false; saveSearchPlaylist(scope, context, ref) }
                                    )
                                    if (com.jimz.jimztune.AppConfig.SHARE_ENABLED) androidx.compose.material3.DropdownMenuItem(
                                        text = { Text("Bagikan") },
                                        leadingIcon = { Icon(Icons.Rounded.Share, null) },
                                        onClick = { moreOpen.value = false; sharePlaylistRef(context, likeItem) }
                                    )
                                }
                            }
                        } else {
                            if (com.jimz.jimztune.AppConfig.SHARE_ENABLED) CompactIconButton(
                                Icons.Rounded.Share,
                                "Bagikan",
                                onClick = {
                                    val by = page.artistName.takeIf { it.isNotBlank() }?.let { " – $it" }.orEmpty()
                                    context.shareText(
                                        page.title,
                                        "${page.title}$by\nhttps://music.youtube.com/browse/${page.id}\n\nDibagikan lewat Aoto"
                                    )
                                }
                            )
                        }
                    }
                }
                Spacer(Modifier.height(10.dp))
            }
        }

        items(page.tracks.size) { i ->
            TrackRow(
                number = i + 1,
                song = page.tracks[i],
                playing = page.tracks[i].id == currentId,
                accent = accent,
                showThumb = showThumbs,
                onClick = { onPlay(page.tracks, i) }
            )
        }
    }
}

@Composable
private fun TrackRow(number: Int, song: SearchItem, playing: Boolean, accent: Color, showThumb: Boolean, onClick: () -> Unit) {
    Row(
        modifier = Modifier
            .fillMaxWidth()
            .padding(horizontal = 10.dp, vertical = 1.dp)
            .clip(RoundedCornerShape(14.dp))
            .background(if (playing) accent.copy(alpha = 0.10f) else Color.Transparent)
            .clickable(onClick = onClick)
            .padding(start = 8.dp, top = 6.dp, bottom = 6.dp),
        verticalAlignment = Alignment.CenterVertically
    ) {
        if (showThumb) {
            // Playlist: tiap lagu punya gambar sendiri (album: semua lagu satu sampul, jadi cukup nomor).
            Box(Modifier.size(52.dp).clip(RoundedCornerShape(10.dp)), contentAlignment = Alignment.Center) {
                com.jimz.jimztune.ui.components.SongImage(
                    url = song.thumbnailUrl,
                    videoId = song.id,
                    shape = RoundedCornerShape(10.dp),
                    modifier = Modifier.fillMaxSize()
                )
                if (playing) {
                    Box(Modifier.fillMaxSize().background(Color.Black.copy(alpha = 0.5f)), contentAlignment = Alignment.Center) {
                        MiniEqualizer(color = accent)
                    }
                }
            }
            Spacer(Modifier.width(12.dp))
        } else {
            Box(Modifier.width(34.dp), contentAlignment = Alignment.Center) {
                if (playing) {
                    MiniEqualizer(color = accent)
                } else {
                    Text(
                        "$number",
                        style = MaterialTheme.typography.bodyMedium,
                        color = Color.White.copy(alpha = 0.55f)
                    )
                }
            }
            Spacer(Modifier.width(8.dp))
        }
        Column(Modifier.weight(1f)) {
            Text(
                song.title,
                style = MaterialTheme.typography.bodyLarge,
                fontWeight = if (playing) FontWeight.SemiBold else FontWeight.Medium,
                color = if (playing) accent else Color.White,
                maxLines = 1,
                overflow = TextOverflow.Ellipsis
            )
            if (song.subtitle.isNotBlank()) {
                Text(
                    song.subtitle.substringBefore(" • "),
                    style = MaterialTheme.typography.bodySmall,
                    color = Color.White.copy(alpha = 0.6f),
                    maxLines = 1,
                    overflow = TextOverflow.Ellipsis
                )
            }
        }
        song.durationText?.let {
            Spacer(Modifier.width(8.dp))
            Text(it, style = MaterialTheme.typography.labelMedium, color = Color.White.copy(alpha = 0.6f))
        }
        SongMenuButton(song)
    }
}
