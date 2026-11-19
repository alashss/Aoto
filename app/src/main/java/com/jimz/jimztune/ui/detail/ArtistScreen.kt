package com.jimz.jimztune.ui.detail

import androidx.compose.animation.animateContentSize
import androidx.compose.foundation.background
import androidx.compose.foundation.clickable
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.PaddingValues
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.statusBarsPadding
import androidx.compose.foundation.layout.width
import androidx.compose.foundation.lazy.LazyColumn
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
import androidx.compose.ui.graphics.Brush
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.layout.ContentScale
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.text.style.TextOverflow
import androidx.compose.ui.unit.dp
import coil.compose.AsyncImage
import com.jimz.jimztune.AppGraph
import com.jimz.jimztune.data.model.ArtistPage
import com.jimz.jimztune.data.model.SearchItem
import com.jimz.jimztune.data.model.SearchType
import com.jimz.jimztune.ui.components.GlassIconButton
import com.jimz.jimztune.ui.components.GradientPillButton
import com.jimz.jimztune.ui.components.LocalOverlayPadding
import com.jimz.jimztune.ui.components.SongListRow
import com.jimz.jimztune.ui.components.TonalPillButton
import com.jimz.jimztune.ui.theme.LocalCoverTheme
import kotlinx.coroutines.CancellationException

private const val TOP_COUNT = 5

@Composable
fun ArtistScreen(
    ref: SearchItem,
    currentId: String?,
    onPlay: (List<SearchItem>, Int) -> Unit,
    onOpen: (SearchItem) -> Unit,
    onBack: () -> Unit
) {
    val cover = LocalCoverTheme.current
    var attempt by remember { mutableIntStateOf(0) }
    var state by remember(ref.id) { mutableStateOf<Load<ArtistPage>>(Load.Loading) }

    LaunchedEffect(ref.id, attempt) {
        state = Load.Loading
        state = try {
            Load.Ok(AppGraph.detail.artist(ref.id))
        } catch (e: CancellationException) {
            throw e
        } catch (e: Exception) {
            Load.Fail("Halaman artis tidak bisa dimuat. Periksa koneksi lalu coba lagi.")
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
            is Load.Ok -> ArtistContent(s.value, currentId, onPlay, onOpen)
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
private fun ArtistContent(
    page: ArtistPage,
    currentId: String?,
    onPlay: (List<SearchItem>, Int) -> Unit,
    onOpen: (SearchItem) -> Unit
) {
    val overlay = LocalOverlayPadding.current
    var extra by remember(page.id) { mutableStateOf<List<SearchItem>?>(null) }
    var loadingAll by remember(page.id) { mutableStateOf(false) }
    var expandBio by remember(page.id) { mutableStateOf(false) }
    val songs = extra ?: page.topSongs
    val shown = if (extra != null) songs else songs.take(TOP_COUNT)
    val canExpand = extra == null && (songs.size > TOP_COUNT || page.allSongsId != null)

    LaunchedEffect(loadingAll) {
        if (!loadingAll) return@LaunchedEffect
        val id = page.allSongsId
        val loaded: List<SearchItem>? = try {
            if (id != null) AppGraph.detail.allSongs(id, page.name) else null
        } catch (e: CancellationException) {
            throw e
        } catch (_: Exception) {
            null
        }
        extra = loaded ?: page.topSongs
        loadingAll = false
    }

    LazyColumn(
        modifier = Modifier.fillMaxSize(),
        contentPadding = PaddingValues(bottom = overlay)
    ) {
        // Kepala: foto besar yang memudar ke latar, nama artis di atasnya.
        item {
            Box(Modifier.fillMaxWidth().height(300.dp)) {
                AsyncImage(
                    model = page.thumbnailUrl,
                    contentDescription = null,
                    contentScale = ContentScale.Crop,
                    modifier = Modifier
                        .fillMaxSize()
                        .background(Color.White.copy(alpha = 0.06f))
                )
                Box(
                    Modifier
                        .fillMaxSize()
                        .background(
                            Brush.verticalGradient(
                                0f to Color.Black.copy(alpha = 0.25f),
                                0.55f to Color.Transparent,
                                1f to Color.Black.copy(alpha = 0.78f)
                            )
                        )
                )
                Column(
                    Modifier
                        .align(Alignment.BottomStart)
                        .padding(start = 20.dp, end = 20.dp, bottom = 16.dp)
                ) {
                    Text(
                        page.name,
                        style = MaterialTheme.typography.headlineLarge,
                        fontWeight = FontWeight.Bold,
                        color = Color.White,
                        maxLines = 2,
                        overflow = TextOverflow.Ellipsis
                    )
                    page.subscribers?.let {
                        Text(it, style = MaterialTheme.typography.bodyMedium, color = Color.White.copy(alpha = 0.75f))
                    }
                }
            }
        }

        if (songs.isNotEmpty()) {
            item {
                Row(
                    Modifier.padding(horizontal = 20.dp, vertical = 16.dp),
                    verticalAlignment = Alignment.CenterVertically
                ) {
                    GradientPillButton("Putar", Icons.Rounded.PlayArrow, { onPlay(songs, 0) })
                    Spacer(Modifier.width(10.dp))
                    TonalPillButton("Acak", Icons.Rounded.Shuffle, { onPlay(songs.shuffled(), 0) })
                }
            }
            item { SectionTitle("Lagu teratas", Modifier.padding(top = 0.dp)) }
            items(shown.size) { i ->
                val song = shown[i]
                SongListRow(
                    song = song,
                    playing = song.id == currentId,
                    onClick = { onPlay(songs, i) }
                )
            }
            if (canExpand) {
                item {
                    Box(
                        Modifier
                            .fillMaxWidth()
                            .padding(horizontal = 20.dp, vertical = 10.dp),
                        contentAlignment = Alignment.CenterStart
                    ) {
                        TonalPillButton(
                            if (loadingAll) "Memuat…" else "Tampilkan semua lagu",
                            Icons.Rounded.PlayArrow,
                            onClick = {
                                if (loadingAll) return@TonalPillButton
                                if (page.allSongsId != null) loadingAll = true else extra = songs
                            }
                        )
                    }
                }
            }
        }

        page.sections.forEachIndexed { idx, section ->
            item(key = "section:$idx") {
                val playable = section.items.filter { it.type == SearchType.SONG }
                Column {
                    SectionTitle(section.title)
                    CardRow(section.items) { entry, _ ->
                        if (entry.type == SearchType.SONG) {
                            onPlay(playable, playable.indexOfFirst { it.id == entry.id }.coerceAtLeast(0))
                        } else {
                            onOpen(entry)
                        }
                    }
                }
            }
        }

        page.description?.let { bio ->
            item {
                Column {
                    SectionTitle("Tentang")
                    Column(Modifier.padding(horizontal = 20.dp)) {
                        Text(
                            bio,
                            style = MaterialTheme.typography.bodyMedium,
                            color = Color.White.copy(alpha = 0.78f),
                            maxLines = if (expandBio) Int.MAX_VALUE else 4,
                            overflow = TextOverflow.Ellipsis,
                            modifier = Modifier
                                .animateContentSize()
                                .clickable { expandBio = !expandBio }
                        )
                        Text(
                            if (expandBio) "Ciutkan" else "Selengkapnya",
                            style = MaterialTheme.typography.labelLarge,
                            color = LocalCoverTheme.current.accent,
                            modifier = Modifier
                                .padding(top = 6.dp)
                                .clickable { expandBio = !expandBio }
                        )
                    }
                }
            }
        }
    }
}
