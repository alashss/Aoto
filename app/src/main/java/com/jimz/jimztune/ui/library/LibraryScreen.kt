package com.jimz.jimztune.ui.library

import androidx.activity.compose.BackHandler
import com.jimz.jimztune.ui.theme.cardSurface
import androidx.compose.animation.AnimatedContent
import androidx.compose.animation.core.FastOutSlowInEasing
import androidx.compose.animation.core.tween
import androidx.compose.animation.fadeIn
import androidx.compose.animation.fadeOut
import androidx.compose.animation.slideInHorizontally
import androidx.compose.animation.slideOutHorizontally
import androidx.compose.animation.togetherWith
import androidx.compose.foundation.background
import androidx.compose.foundation.border
import androidx.compose.foundation.clickable
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Box
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
import androidx.compose.foundation.lazy.items
import androidx.compose.foundation.lazy.grid.items as gridItems
import androidx.compose.foundation.lazy.itemsIndexed
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.rounded.Add
import androidx.compose.material.icons.rounded.AutoFixHigh
import androidx.compose.material.icons.rounded.CallMerge
import androidx.compose.material.icons.rounded.TrendingUp
import androidx.compose.material.icons.rounded.Replay
import com.jimz.jimztune.ui.components.JimzBottomSheet
import com.jimz.jimztune.ui.components.SheetAction
import androidx.compose.material.icons.rounded.SwapVert
import androidx.compose.material.icons.rounded.CloudDownload
import androidx.compose.material.icons.rounded.ArrowBack
import androidx.compose.material.icons.rounded.ChevronRight
import androidx.compose.material.icons.rounded.Close
import androidx.compose.material.icons.rounded.Download
import androidx.compose.material.icons.rounded.GridView
import androidx.compose.material.icons.rounded.ViewAgenda
import com.jimz.jimztune.ui.components.SortMode
import androidx.compose.material.icons.rounded.Favorite
import androidx.compose.material.icons.rounded.History
import androidx.compose.material.icons.rounded.LibraryMusic
import androidx.compose.material.icons.rounded.PlayArrow
import androidx.compose.material.icons.rounded.Shuffle
import androidx.compose.material3.AlertDialog
import androidx.compose.material3.Icon
import androidx.compose.material3.IconButton
import androidx.compose.material3.LinearProgressIndicator
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.Text
import androidx.compose.material3.TextButton
import androidx.compose.runtime.Composable
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableIntStateOf
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.saveable.rememberSaveable
import androidx.compose.runtime.setValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clip
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.graphics.vector.ImageVector
import androidx.compose.ui.layout.ContentScale
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.text.style.TextOverflow
import androidx.compose.ui.unit.dp
import coil.compose.AsyncImage
import com.jimz.jimztune.AppGraph
import com.jimz.jimztune.data.download.ActiveDownload
import com.jimz.jimztune.data.library.Playlist
import com.jimz.jimztune.data.model.SearchItem
import com.jimz.jimztune.ui.components.EmptyState
import com.jimz.jimztune.ui.components.GradientPillButton
import com.jimz.jimztune.ui.components.IconBadge
import com.jimz.jimztune.ui.components.LocalOverlayPadding
import com.jimz.jimztune.ui.components.ScreenHeader
import com.jimz.jimztune.ui.components.SegmentedTabs
import com.jimz.jimztune.ui.components.SongListRow
import com.jimz.jimztune.ui.components.CompactIconButton
import com.jimz.jimztune.ui.components.LibraryView
import com.jimz.jimztune.ui.components.SongGrid
import com.jimz.jimztune.ui.components.ViewModeButton
import com.jimz.jimztune.ui.components.SortButton
import com.jimz.jimztune.ui.components.rememberSortMode
import com.jimz.jimztune.ui.components.sortSongs
import com.jimz.jimztune.ui.components.CompactPillButton
import com.jimz.jimztune.ui.components.DownloadAllIconButton
import com.jimz.jimztune.ui.components.LikeColor
import com.jimz.jimztune.ui.components.LocalOpenDetail
import com.jimz.jimztune.util.libraryPlaylistShareText
import com.jimz.jimztune.util.shareText
import androidx.compose.foundation.layout.RowScope
import androidx.compose.material.icons.rounded.FavoriteBorder
import androidx.compose.material.icons.rounded.Share
import androidx.compose.ui.platform.LocalContext
import com.jimz.jimztune.ui.components.DownloadRingOverlay
import com.jimz.jimztune.ui.components.TonalPillButton
import com.jimz.jimztune.ui.theme.LocalCoverTheme
import androidx.compose.animation.AnimatedVisibility
import androidx.compose.animation.core.Spring
import androidx.compose.animation.core.animateFloatAsState
import androidx.compose.animation.core.spring
import androidx.compose.animation.slideInVertically
import androidx.compose.animation.slideOutVertically
import androidx.compose.foundation.interaction.MutableInteractionSource
import androidx.compose.foundation.interaction.collectIsPressedAsState
import androidx.compose.foundation.shape.CircleShape
import androidx.compose.foundation.text.KeyboardActions
import androidx.compose.foundation.text.KeyboardOptions
import androidx.compose.material.icons.rounded.AutoAwesome
import androidx.compose.material.icons.rounded.Check
import androidx.compose.material.icons.rounded.MusicNote
import androidx.compose.material3.OutlinedTextField
import androidx.compose.material3.OutlinedTextFieldDefaults
import androidx.compose.material3.Surface
import androidx.compose.ui.draw.blur
import androidx.compose.ui.draw.shadow
import androidx.compose.ui.graphics.Brush
import androidx.compose.ui.graphics.graphicsLayer
import androidx.compose.ui.platform.LocalFocusManager
import androidx.compose.ui.text.input.ImeAction
import androidx.compose.ui.text.input.KeyboardCapitalization
import androidx.compose.ui.unit.sp
import androidx.compose.ui.window.Dialog
import androidx.compose.ui.window.DialogProperties
import androidx.compose.material.icons.rounded.Delete
import androidx.compose.material.icons.rounded.Edit
import androidx.compose.material.icons.rounded.Refresh
import com.jimz.jimztune.ui.components.GlassIconButton
import com.jimz.jimztune.ui.components.JimzDialog
import com.jimz.jimztune.ui.components.PillTextButton
import com.jimz.jimztune.ui.components.PlaylistCover
import com.jimz.jimztune.ui.components.PlaylistEditorSheet

private val TABS = listOf("Favorit", "Playlist", "Riwayat", "Unduhan")

@Composable
fun LibraryScreen(
    recent: List<SearchItem>,
    currentId: String?,
    onPlay: (List<SearchItem>, Int) -> Unit,
    onClearHistory: () -> Unit
) {
    val library = AppGraph.library
    val downloads = AppGraph.downloads
    var tab by rememberSaveable { mutableIntStateOf(0) }
    var openPlaylistId by rememberSaveable { mutableStateOf<String?>(null) }

    BackHandler(enabled = tab == 1 && openPlaylistId != null) { openPlaylistId = null }

    Column(modifier = Modifier.fillMaxSize().statusBarsPadding()) {
        ScreenHeader(
            title = "Koleksi",
            subtitle = "${library.favorites.size} favorit  •  ${library.playlists.size} playlist  •  ${downloads.songs.size} unduhan"
        )
        SegmentedTabs(
            labels = TABS,
            selected = tab,
            onSelect = {
                tab = it
                openPlaylistId = null
            },
            modifier = Modifier.padding(horizontal = 20.dp)
        )
        Spacer(Modifier.height(6.dp))
        Box(modifier = Modifier.weight(1f)) {
            // Pindah tab: isi lama bergeser keluar sambil memudar, isi baru masuk dari arah tab yang dipilih.
            AnimatedContent(
                targetState = tab,
                transitionSpec = {
                    val dir = if (targetState > initialState) 1 else -1
                    (slideInHorizontally(tween(340, easing = FastOutSlowInEasing)) { (it * 0.12f).toInt() * dir } +
                        fadeIn(tween(260, delayMillis = 50))) togetherWith
                        (slideOutHorizontally(tween(260, easing = FastOutSlowInEasing)) { -(it * 0.08f).toInt() * dir } +
                            fadeOut(tween(150)))
                },
                label = "library-tab"
            ) { t ->
                when (t) {
                    0 -> SongList(
                        songs = library.favorites,
                        sortKey = "fav",
                        currentId = currentId,
                        onPlay = onPlay,
                        emptyIcon = Icons.Rounded.Favorite,
                        emptyTitle = "Belum ada favorit",
                        emptyBody = "Ketuk ikon hati di pemutar, atau menu ⋮ pada lagu mana pun."
                    )
                    1 -> PlaylistTab(
                        currentId = currentId,
                        onPlay = onPlay,
                        openId = openPlaylistId,
                        onOpen = { openPlaylistId = it }
                    )
                    2 -> SongList(
                        songs = recent,
                        currentId = currentId,
                        onPlay = onPlay,
                        emptyIcon = Icons.Rounded.History,
                        emptyTitle = "Riwayat masih kosong",
                        emptyBody = "Lagu yang kamu putar akan tercatat di sini.",
                        extraHeader = {
                            PillTextButton(
                                text = "Hapus riwayat",
                                onClick = onClearHistory,
                                primary = false,
                                modifier = Modifier.padding(horizontal = 20.dp, vertical = 4.dp)
                            )
                        }
                    )
                    else -> DownloadsTab(currentId, onPlay)
                }
            }
        }
    }
}

@Composable
private fun PlayAllBar(
    onPlayAll: () -> Unit,
    onShuffle: () -> Unit,
    trailing: @Composable RowScope.() -> Unit = {}
) {
    Row(
        modifier = Modifier
            .fillMaxWidth()
            .padding(horizontal = 20.dp, vertical = 8.dp),
        verticalAlignment = Alignment.CenterVertically,
        horizontalArrangement = Arrangement.spacedBy(8.dp)
    ) {
        CompactPillButton("Putar semua", Icons.Rounded.PlayArrow, onPlayAll, primary = true)
        CompactIconButton(Icons.Rounded.Shuffle, "Acak", onShuffle)
        Spacer(Modifier.weight(1f))
        trailing()
    }
}

@Composable
private fun SongList(
    songs: List<SearchItem>,
    currentId: String?,
    onPlay: (List<SearchItem>, Int) -> Unit,
    emptyIcon: ImageVector,
    emptyTitle: String,
    emptyBody: String,
    extraHeader: (@Composable () -> Unit)? = null,
    extraFooter: (@Composable () -> Unit)? = null,
    barEntries: List<com.jimz.jimztune.ui.components.BarEntry> = emptyList(),
    sortKey: String? = null,
    extraMenu: (SearchItem) -> List<Pair<String, () -> Unit>> = { emptyList() }
) {
    if (songs.isEmpty()) {
        EmptyState(emptyIcon, emptyTitle, emptyBody)
        return
    }
    val (sortMode, setSort) = rememberSortMode(sortKey ?: "none")
    val shown = remember(songs, sortMode, sortKey) { if (sortKey == null) songs else sortSongs(songs, sortMode) }
    val listHeader: @Composable () -> Unit = {
        PlayAllBar(
            onPlayAll = { onPlay(shown, 0) },
            onShuffle = { onPlay(shown.shuffled(), 0) },
            trailing = {
                val viewEntry = com.jimz.jimztune.ui.components.BarEntry(
                    if (LibraryView.grid) "Tampilan daftar" else "Tampilan kotak",
                    if (LibraryView.grid) Icons.Rounded.ViewAgenda else Icons.Rounded.GridView
                ) { LibraryView.toggle() }
                com.jimz.jimztune.ui.components.BarMenuButton(
                    actions = barEntries + viewEntry,
                    sort = if (sortKey != null) SortMode.entries.map { m -> com.jimz.jimztune.ui.components.BarEntry(m.label, null, m == sortMode) { setSort(m) } } else emptyList()
                )
            }
        )
        extraHeader?.invoke()
    }
    if (LibraryView.grid) {
        SongGrid(shown, currentId, onPlay, LocalOverlayPadding.current, listHeader, extraFooter)
        return
    }
    LazyColumn(contentPadding = PaddingValues(bottom = LocalOverlayPadding.current)) {
        item { listHeader() }
        itemsIndexed(shown, key = { _, s -> s.id }) { index, song ->
            SongListRow(
                song = song,
                playing = song.id == currentId,
                onClick = { onPlay(shown, index) },
                modifier = Modifier.animateItem(),
                extraMenu = extraMenu(song)
            )
        }
        if (extraFooter != null) item { extraFooter() }
    }
}

@Composable
private fun PlaylistTab(
    currentId: String?,
    onPlay: (List<SearchItem>, Int) -> Unit,
    openId: String?,
    onOpen: (String?) -> Unit
) {
    val library = AppGraph.library
    val context = LocalContext.current
    val openDetail = LocalOpenDetail.current
    var showCreate by remember { mutableStateOf(false) }
    var showImport by remember { mutableStateOf(false) }
    var showAddSongs by remember { mutableStateOf(false) }
    var editing by remember { mutableStateOf(false) }
    var reordering by remember { mutableStateOf(false) }
    var confirmDelete by remember { mutableStateOf(false) }
    val open = library.playlist(openId)
    androidx.compose.runtime.LaunchedEffect(openId) { if (openId != null && !openId.startsWith("smart:")) library.bump(openId) }
    var showMerge by remember { mutableStateOf(false) }

    if (openId != null && openId.startsWith("smart:")) {
        val smart = remember(openId) { SmartLists.build().firstOrNull { "smart:" + it.id == openId } }
        if (smart != null) {
            Column(modifier = Modifier.fillMaxSize()) {
                Row(
                    modifier = Modifier.fillMaxWidth().padding(start = 20.dp, end = 20.dp, top = 4.dp),
                    verticalAlignment = Alignment.CenterVertically
                ) {
                    CompactIconButton(Icons.Rounded.ArrowBack, "Kembali", onClick = { onOpen(null) })
                }
                Column(modifier = Modifier.padding(horizontal = 20.dp, vertical = 10.dp)) {
                    Text(smart.title, style = MaterialTheme.typography.titleLarge, fontWeight = FontWeight.Bold)
                    Text(
                        "${smart.songs.size} lagu · ${smart.hint}",
                        style = MaterialTheme.typography.bodyMedium,
                        color = MaterialTheme.colorScheme.onSurfaceVariant
                    )
                }
                Box(modifier = Modifier.weight(1f)) {
                    SongList(
                        songs = smart.songs,
                        sortKey = null,
                        currentId = currentId,
                        onPlay = onPlay,
                        emptyIcon = Icons.Rounded.LibraryMusic,
                        emptyTitle = "Belum ada isinya",
                        emptyBody = "Putar beberapa lagu dulu, daftar ini terisi otomatis."
                    )
                }
            }
            return
        }
    }

    if (open == null) {
        val smartLists = remember { SmartLists.build() }
        if (LibraryView.grid) {
            androidx.compose.foundation.lazy.grid.LazyVerticalGrid(
                columns = androidx.compose.foundation.lazy.grid.GridCells.Fixed(2),
                contentPadding = PaddingValues(start = 20.dp, end = 20.dp, bottom = LocalOverlayPadding.current),
                horizontalArrangement = Arrangement.spacedBy(14.dp),
                verticalArrangement = Arrangement.spacedBy(14.dp),
                modifier = Modifier.fillMaxSize()
            ) {
                val span = { androidx.compose.foundation.lazy.grid.GridItemSpan(2) }
                item(span = { span() }) {
                    Row(Modifier.fillMaxWidth().padding(top = 10.dp), verticalAlignment = Alignment.CenterVertically, horizontalArrangement = Arrangement.spacedBy(10.dp)) {
                        NewPlaylistButton(modifier = Modifier.weight(1f), onClick = { showCreate = true })
                        ViewModeButton()
                    }
                }
                item(span = { span() }) { ImportPlaylistButton(onClick = { showImport = true }) }
                item(span = { span() }) { ListLabel("Playlist cerdas") }
                gridItems(smartLists, key = { "smart_" + it.id }) { sm ->
                    PlaylistTile(sm.title, "${sm.songs.size} lagu · ${sm.hint}", { sz -> Box(Modifier.size(sz).clip(RoundedCornerShape(18.dp)).cardSurface(RoundedCornerShape(18.dp)), contentAlignment = Alignment.Center) { Icon(Icons.Rounded.AutoAwesome, null, tint = LocalCoverTheme.current.accent, modifier = Modifier.size(42.dp)) } }) { onOpen("smart:" + sm.id) }
                }
                if (library.playlists.isNotEmpty()) item(span = { span() }) { ListLabel("Playlist saya") }
                gridItems(library.playlists, key = { it.id }) { p ->
                    PlaylistTile(p.name, "${p.songs.size} lagu", { sz -> PlaylistCover(p, size = sz, radius = 18.dp) }) { onOpen(p.id) }
                }
                if (library.likedPlaylists.isNotEmpty()) item(span = { span() }) { ListLabel("Playlist disukai") }
                gridItems(library.likedPlaylists, key = { "liked_" + it.id }) { ref ->
                    PlaylistTile(ref.title.ifBlank { "Playlist" }, ref.subtitle.substringBefore(" • "), { sz ->
                        AsyncImage(ref.thumbnailUrl, null, contentScale = ContentScale.Crop, modifier = Modifier.size(sz).clip(RoundedCornerShape(18.dp)))
                    }) { openDetail(ref) }
                }
            }
        } else LazyColumn(contentPadding = PaddingValues(bottom = LocalOverlayPadding.current)) {
            item {
                NewPlaylistButton(
                    modifier = Modifier.padding(horizontal = 20.dp, vertical = 10.dp),
                    onClick = { showCreate = true }
                )
            }
            item {
                ImportPlaylistButton(
                    modifier = Modifier.padding(start = 20.dp, end = 20.dp, bottom = 10.dp),
                    onClick = { showImport = true }
                )
            }
            if (library.playlists.isEmpty() && library.likedPlaylists.isEmpty()) {
                item {
                    Text(
                        "Belum ada playlist. Buat satu, lalu simpan lagu lewat menu ⋮. Playlist dari pencarian bisa disuka atau disimpan ke sini.",
                        style = MaterialTheme.typography.bodyMedium,
                        color = MaterialTheme.colorScheme.onSurfaceVariant,
                        modifier = Modifier.padding(horizontal = 20.dp, vertical = 8.dp)
                    )
                }
            }
            item { ListLabel("Playlist cerdas") }
            items(smartLists, key = { "smart_" + it.id }) { sm ->
                SmartRow(sm, Modifier.animateItem()) { onOpen("smart:" + sm.id) }
            }
            if (library.playlists.isNotEmpty()) {
                item { ListLabel("Playlist saya") }
            }
            items(library.playlists, key = { it.id }) { p ->
                PlaylistRow(p, modifier = Modifier.animateItem()) { onOpen(p.id) }
            }
            if (library.likedPlaylists.isNotEmpty()) {
                item { ListLabel("Playlist disukai") }
                items(library.likedPlaylists, key = { "liked_" + it.id }) { ref ->
                    LikedPlaylistRow(
                        ref = ref,
                        modifier = Modifier.animateItem(),
                        onClick = { openDetail(ref) },
                        onUnlike = { library.togglePlaylistLike(ref) }
                    )
                }
            }
        }
    } else {
        Column(modifier = Modifier.fillMaxSize()) {
            Row(
                modifier = Modifier
                    .fillMaxWidth()
                    .padding(start = 20.dp, end = 20.dp, top = 4.dp),
                verticalAlignment = Alignment.CenterVertically,
                horizontalArrangement = Arrangement.spacedBy(10.dp)
            ) {
                CompactIconButton(Icons.Rounded.ArrowBack, "Kembali", onClick = { onOpen(null) })
                Spacer(Modifier.weight(1f))
                CompactIconButton(Icons.Rounded.AutoFixHigh, "Hapus lagu kembar", onClick = {
                    val n = library.dedupePlaylist(open.id)
                    android.widget.Toast.makeText(
                        context,
                        if (n > 0) "$n lagu kembar dibuang" else "Tidak ada lagu kembar",
                        android.widget.Toast.LENGTH_SHORT
                    ).show()
                })
                if (library.playlists.size > 1) CompactIconButton(Icons.Rounded.CallMerge, "Gabungkan playlist", onClick = { showMerge = true })
                CompactIconButton(Icons.Rounded.Edit, "Edit playlist", onClick = { editing = true })
                CompactIconButton(
                    Icons.Rounded.Delete,
                    "Hapus playlist",
                    onClick = { confirmDelete = true },
                    tint = Color(0xFFFF6B7A)
                )
            }
            Row(
                modifier = Modifier
                    .fillMaxWidth()
                    .padding(horizontal = 20.dp, vertical = 10.dp),
                verticalAlignment = Alignment.CenterVertically
            ) {
                PlaylistCover(open, size = 92.dp, radius = 20.dp)
                Spacer(Modifier.width(16.dp))
                Column(modifier = Modifier.weight(1f)) {
                    Text(
                        open.name,
                        style = MaterialTheme.typography.titleLarge,
                        fontWeight = FontWeight.Bold,
                        maxLines = 2,
                        overflow = TextOverflow.Ellipsis
                    )
                    Text(
                        "${open.songs.size} lagu",
                        style = MaterialTheme.typography.bodyMedium,
                        color = MaterialTheme.colorScheme.onSurfaceVariant,
                        modifier = Modifier.padding(top = 2.dp)
                    )
                    // Playlist kosong tidak punya baris aksi (Putar/Acak), jadi tombol tambah lagu ada di sini.
                    if (open.songs.isEmpty()) {
                        CompactPillButton(
                            "Tambah lagu",
                            Icons.Rounded.Add,
                            { showAddSongs = true },
                            Modifier.padding(top = 10.dp)
                        )
                    }
                }
            }
            Box(modifier = Modifier.weight(1f)) {
              if (open.songs.isEmpty()) {
                androidx.compose.foundation.lazy.LazyColumn(contentPadding = PaddingValues(bottom = LocalOverlayPadding.current)) {
                    item {
                        Text(
                            "Playlist masih kosong. Mulai dari saran di bawah, atau simpan lagu lewat menu ⋮ di pencarian dan pemutar.",
                            style = MaterialTheme.typography.bodyMedium,
                            color = MaterialTheme.colorScheme.onSurfaceVariant,
                            modifier = Modifier.padding(horizontal = 20.dp, vertical = 8.dp)
                        )
                    }
                    item { PlaylistSuggestions(open.id, open.songs, onPlay) }
                }
              } else {
                SongList(
                    songs = open.songs,
                    sortKey = "pl_" + open.id,
                    extraFooter = { PlaylistSuggestions(open.id, open.songs, onPlay) },
                    currentId = currentId,
                    onPlay = onPlay,
                    emptyIcon = Icons.Rounded.LibraryMusic,
                    emptyTitle = "Playlist kosong",
                    emptyBody = "Simpan lagu lewat menu ⋮ di hasil pencarian atau pemutar.",
                    barEntries = buildList {
                        val uniq = open.songs.distinctBy { it.id }
                        val remaining = uniq.count { !AppGraph.downloads.isDownloaded(it.id) }
                        if (uniq.isNotEmpty()) add(com.jimz.jimztune.ui.components.BarEntry(
                            if (remaining == 0) "Semua sudah diunduh" else "Unduh semua ($remaining)", Icons.Rounded.Download
                        ) {
                            if (remaining == 0) android.widget.Toast.makeText(context, "Semua lagu sudah tersimpan untuk offline", android.widget.Toast.LENGTH_SHORT).show()
                            else android.widget.Toast.makeText(context, "Mengunduh ${AppGraph.downloads.downloadAll(uniq)} lagu…", android.widget.Toast.LENGTH_SHORT).show()
                        })
                        add(com.jimz.jimztune.ui.components.BarEntry("Tambah lagu", Icons.Rounded.Add) { showAddSongs = true })
                        if (open.songs.size > 1) add(com.jimz.jimztune.ui.components.BarEntry("Atur urutan", Icons.Rounded.SwapVert) { reordering = true })
                        if (com.jimz.jimztune.AppConfig.SHARE_ENABLED) add(com.jimz.jimztune.ui.components.BarEntry("Bagikan playlist", Icons.Rounded.Share) {
                            if (open.songs.isEmpty()) android.widget.Toast.makeText(context, "Playlist masih kosong", android.widget.Toast.LENGTH_SHORT).show()
                            else com.jimz.jimztune.data.share.ShareLinks.sharePlaylist(context, open.id, open.name, open.songs, libraryPlaylistShareText(open.name, open.songs))
                        })
                    },
                    extraMenu = { s -> listOf("Hapus dari playlist" to { library.removeFromPlaylist(open.id, s.id) }) }
                )
              }
            }
        }
    }

    if (showMerge && open != null) {
        JimzBottomSheet(onDismiss = { showMerge = false }) {
            Text(
                "Gabungkan ke \"${open.name}\"",
                style = MaterialTheme.typography.titleMedium,
                fontWeight = FontWeight.Bold,
                modifier = Modifier.padding(horizontal = 24.dp, vertical = 4.dp)
            )
            Text(
                "Lagu dari playlist yang dipilih ditambahkan ke sini. Lagu yang sudah ada dilewati; playlist asal tidak berubah.",
                style = MaterialTheme.typography.bodySmall,
                color = MaterialTheme.colorScheme.onSurfaceVariant,
                modifier = Modifier.padding(horizontal = 24.dp, vertical = 4.dp)
            )
            library.playlists.filter { it.id != open.id }.forEach { other ->
                SheetAction(
                    icon = Icons.Rounded.LibraryMusic,
                    label = other.name,
                    subtitle = "${other.songs.size} lagu",
                    onClick = {
                        val n = library.mergeInto(open.id, other.id)
                        showMerge = false
                        android.widget.Toast.makeText(
                            context,
                            if (n > 0) "$n lagu ditambahkan" else "Tidak ada lagu baru",
                            android.widget.Toast.LENGTH_SHORT
                        ).show()
                    }
                )
            }
        }
    }
    if (reordering && open != null) {
        ReorderPlaylistDialog(open.id) { reordering = false }
    }
    if (showAddSongs && open != null) {
        AddSongsDialog(open.id) { showAddSongs = false }
    }
    if (showImport && !AppGraph.remote.featureOn("import_playlist")) {
        val ctx = androidx.compose.ui.platform.LocalContext.current
        androidx.compose.runtime.LaunchedEffect(Unit) {
            android.widget.Toast.makeText(ctx, "Impor playlist sedang dimatikan", android.widget.Toast.LENGTH_SHORT).show()
            showImport = false
        }
    } else if (showImport) {
        ImportPlaylistSheet(
            onDismiss = { showImport = false },
            onCreated = { id ->
                showImport = false
                onOpen(id)
            }
        )
    }
    if (showCreate) {
        PlaylistEditorSheet(
            title = "Playlist baru",
            confirmLabel = "Buat playlist",
            onConfirm = { name, color, cover, _ ->
                val p = library.createPlaylist(name, color = color)
                if (cover != null) library.setCover(p.id, cover)
                showCreate = false
            },
            onDismiss = { showCreate = false }
        )
    }
    if (editing && open != null) {
        PlaylistEditorSheet(
            title = "Edit playlist",
            confirmLabel = "Simpan",
            initialName = open.name,
            initialColor = open.color,
            initialCoverPath = open.coverPath,
            thumbs = open.songs.mapNotNull { it.thumbnailUrl }.distinct().take(4),
            onConfirm = { name, color, cover, remove ->
                library.update(open.id, name, color)
                if (cover != null) library.setCover(open.id, cover) else if (remove) library.setCover(open.id, null)
                editing = false
            },
            onDismiss = { editing = false }
        )
    }
    if (confirmDelete && open != null) {
        JimzDialog(
            onDismiss = { confirmDelete = false },
            icon = Icons.Rounded.Delete,
            title = "Hapus playlist?",
            message = "“${open.name}” akan dihapus. Lagu di dalamnya tidak ikut terhapus dari perangkat.",
            confirmLabel = "Hapus",
            destructive = true,
            onConfirm = {
                library.delete(open.id)
                confirmDelete = false
                onOpen(null)
            }
        )
    }
}

/** Kotak playlist untuk mode grid: sampul persegi besar, nama, jumlah lagu. */
@Composable
private fun PlaylistTile(title: String, subtitle: String, cover: @Composable (androidx.compose.ui.unit.Dp) -> Unit, onClick: () -> Unit) {
    androidx.compose.foundation.layout.BoxWithConstraints(Modifier.clip(RoundedCornerShape(18.dp)).clickable(onClick = onClick)) {
        Column {
            cover(this@BoxWithConstraints.maxWidth)
            Spacer(Modifier.height(6.dp))
            Text(title, style = MaterialTheme.typography.bodyMedium, fontWeight = FontWeight.SemiBold, maxLines = 1, overflow = TextOverflow.Ellipsis)
            Text(subtitle, style = MaterialTheme.typography.bodySmall, color = MaterialTheme.colorScheme.onSurfaceVariant, maxLines = 1, overflow = TextOverflow.Ellipsis)
        }
    }
}

@Composable
private fun PlaylistRow(playlist: Playlist, modifier: Modifier = Modifier, onClick: () -> Unit) {
    val accent = LocalCoverTheme.current.accent
    val cardShape = RoundedCornerShape(18.dp)

    Row(
        modifier = modifier
            .fillMaxWidth()
            .padding(horizontal = 20.dp, vertical = 5.dp)
            .clip(cardShape)
            .cardSurface(cardShape)
            .clickable(onClick = onClick)
            .padding(10.dp),
        verticalAlignment = Alignment.CenterVertically
    ) {
        PlaylistCover(playlist, size = 56.dp)
        Spacer(Modifier.width(14.dp))
        Column(modifier = Modifier.weight(1f)) {
            Text(
                playlist.name,
                style = MaterialTheme.typography.bodyLarge,
                fontWeight = FontWeight.SemiBold,
                maxLines = 1,
                overflow = TextOverflow.Ellipsis
            )
            Text(
                "${playlist.songs.size} lagu",
                style = MaterialTheme.typography.bodyMedium,
                color = MaterialTheme.colorScheme.onSurfaceVariant
            )
        }
        Icon(Icons.Rounded.ChevronRight, contentDescription = null, tint = accent.copy(alpha = 0.8f))
    }
}

@Composable
private fun SmartRow(smart: SmartLists.Smart, modifier: Modifier = Modifier, onClick: () -> Unit) {
    val accent = LocalCoverTheme.current.accent
    val cardShape = RoundedCornerShape(18.dp)
    val icon = when (smart.id) {
        "month" -> Icons.Rounded.TrendingUp
        "old" -> Icons.Rounded.Replay
        else -> Icons.Rounded.History
    }
    Row(
        modifier = modifier
            .fillMaxWidth()
            .padding(horizontal = 20.dp, vertical = 5.dp)
            .clip(cardShape)
            .cardSurface(cardShape)
            .clickable(onClick = onClick)
            .padding(10.dp),
        verticalAlignment = Alignment.CenterVertically
    ) {
        Box(
            modifier = Modifier
                .size(56.dp)
                .clip(RoundedCornerShape(14.dp))
                .background(accent.copy(alpha = 0.18f)),
            contentAlignment = Alignment.Center
        ) {
            Icon(icon, contentDescription = null, tint = accent, modifier = Modifier.size(28.dp))
        }
        Spacer(Modifier.width(14.dp))
        Column(modifier = Modifier.weight(1f)) {
            Text(smart.title, style = MaterialTheme.typography.bodyLarge, fontWeight = FontWeight.SemiBold, maxLines = 1, overflow = TextOverflow.Ellipsis)
            Text(
                "${smart.songs.size} lagu · ${smart.hint}",
                style = MaterialTheme.typography.bodySmall,
                color = MaterialTheme.colorScheme.onSurfaceVariant,
                maxLines = 1,
                overflow = TextOverflow.Ellipsis
            )
        }
        Icon(Icons.Rounded.ChevronRight, contentDescription = null, tint = accent.copy(alpha = 0.8f))
    }
}

@Composable
private fun ListLabel(text: String) {
    Text(
        text,
        style = MaterialTheme.typography.titleSmall,
        fontWeight = FontWeight.SemiBold,
        color = LocalCoverTheme.current.accent,
        modifier = Modifier.padding(start = 24.dp, end = 20.dp, top = 14.dp, bottom = 4.dp)
    )
}

/** Playlist hasil pencarian yang disukai. Ketuk untuk membuka isinya; hati untuk berhenti menyukai. */
@Composable
private fun LikedPlaylistRow(
    ref: SearchItem,
    modifier: Modifier = Modifier,
    onClick: () -> Unit,
    onUnlike: () -> Unit
) {
    val cardShape = RoundedCornerShape(18.dp)
    Row(
        modifier = modifier
            .fillMaxWidth()
            .padding(horizontal = 20.dp, vertical = 5.dp)
            .clip(cardShape)
            .cardSurface(cardShape)
            .clickable(onClick = onClick)
            .padding(start = 10.dp, top = 10.dp, bottom = 10.dp, end = 4.dp),
        verticalAlignment = Alignment.CenterVertically
    ) {
        AsyncImage(
            model = ref.thumbnailUrl,
            contentDescription = null,
            contentScale = ContentScale.Crop,
            modifier = Modifier
                .size(56.dp)
                .clip(RoundedCornerShape(14.dp))
                .background(Color.White.copy(alpha = 0.08f))
        )
        Spacer(Modifier.width(14.dp))
        Column(modifier = Modifier.weight(1f)) {
            Text(
                ref.title,
                style = MaterialTheme.typography.bodyLarge,
                fontWeight = FontWeight.SemiBold,
                maxLines = 1,
                overflow = TextOverflow.Ellipsis
            )
            Text(
                ref.subtitle.ifBlank { "Playlist YouTube Music" },
                style = MaterialTheme.typography.bodyMedium,
                color = MaterialTheme.colorScheme.onSurfaceVariant,
                maxLines = 1,
                overflow = TextOverflow.Ellipsis
            )
        }
        val ctx = LocalContext.current
        IconButton(onClick = { com.jimz.jimztune.ui.components.sharePlaylistRef(ctx, ref) }) {
            Icon(Icons.Rounded.Share, contentDescription = "Bagikan", tint = MaterialTheme.colorScheme.onSurfaceVariant)
        }
        IconButton(onClick = onUnlike) {
            Icon(Icons.Rounded.Favorite, contentDescription = "Batal suka playlist", tint = LikeColor)
        }
    }
}

@Composable
private fun DlMenu(mode: SortMode, set: (SortMode) -> Unit) {
    com.jimz.jimztune.ui.components.BarMenuButton(
        actions = listOf(com.jimz.jimztune.ui.components.BarEntry(
            if (LibraryView.grid) "Tampilan daftar" else "Tampilan kotak",
            if (LibraryView.grid) Icons.Rounded.ViewAgenda else Icons.Rounded.GridView
        ) { LibraryView.toggle() }),
        sort = SortMode.entries.map { m -> com.jimz.jimztune.ui.components.BarEntry(m.label, null, m == mode) { set(m) } }
    )
}

@Composable
private fun DownloadsTab(currentId: String?, onPlay: (List<SearchItem>, Int) -> Unit) {
    val downloads = AppGraph.downloads
    val active = downloads.active.values.toList()
    val songs = downloads.songs
    if (active.isEmpty() && songs.isEmpty()) {
        EmptyState(
            Icons.Rounded.Download,
            "Belum ada unduhan",
            "Pilih “Unduh untuk offline” di menu ⋮ sebuah lagu."
        )
        return
    }
    val sizeText = remember(songs) { formatBytes(downloads.totalBytes()) }
    val (dlMode, setDlMode) = rememberSortMode("downloads")
    val shownSongs = remember(songs.toList(), dlMode) { sortSongs(songs.toList(), dlMode) }

    if (LibraryView.grid && songs.isNotEmpty()) {
        SongGrid(shownSongs, currentId, onPlay, LocalOverlayPadding.current) {
            Column {
                if (active.isNotEmpty()) {
                    Text("Sedang diunduh", style = MaterialTheme.typography.titleSmall, modifier = Modifier.padding(horizontal = 8.dp, vertical = 8.dp))
                    active.forEach { ActiveRow(it) }
                }
                Text(
                    "${songs.size} lagu tersimpan · $sizeText",
                    style = MaterialTheme.typography.bodyMedium, fontWeight = FontWeight.SemiBold,
                    modifier = Modifier.padding(horizontal = 8.dp, vertical = 6.dp)
                )
                PlayAllBar(
                    onPlayAll = { onPlay(shownSongs, 0) },
                    onShuffle = { onPlay(shownSongs.shuffled(), 0) },
                    trailing = { DlMenu(dlMode, setDlMode) }
                )
            }
        }
        return
    }
    LazyColumn(contentPadding = PaddingValues(bottom = LocalOverlayPadding.current)) {
        if (active.isNotEmpty()) {
            item {
                Text(
                    "Sedang diunduh",
                    style = MaterialTheme.typography.titleSmall,
                    modifier = Modifier.padding(horizontal = 20.dp, vertical = 8.dp)
                )
            }
            items(active, key = { "active_" + it.song.id }) { ActiveRow(it, Modifier.animateItem()) }
        }
        if (songs.isNotEmpty()) {
            item {
                Row(
                    modifier = Modifier.padding(start = 20.dp, end = 20.dp, top = 6.dp),
                    verticalAlignment = Alignment.CenterVertically
                ) {
                    IconBadge(Icons.Rounded.Download, size = 36.dp)
                    Spacer(Modifier.width(12.dp))
                    Column {
                        Text(
                            "${songs.size} lagu tersimpan",
                            style = MaterialTheme.typography.bodyLarge,
                            fontWeight = FontWeight.SemiBold
                        )
                        Text(
                            "$sizeText di perangkat ini",
                            style = MaterialTheme.typography.bodySmall,
                            color = MaterialTheme.colorScheme.onSurfaceVariant
                        )
                    }
                }
                PlayAllBar(
                    onPlayAll = { onPlay(shownSongs, 0) },
                    onShuffle = { onPlay(shownSongs.shuffled(), 0) },
                    trailing = { DlMenu(dlMode, setDlMode) }
                )
            }
            itemsIndexed(shownSongs, key = { _, s -> s.id }) { index, song ->
                SongListRow(
                    song = song,
                    playing = song.id == currentId,
                    onClick = { onPlay(shownSongs, index) },
                    modifier = Modifier.animateItem()
                )
            }
        }
    }
}

@Composable
private fun ActiveRow(item: ActiveDownload, modifier: Modifier = Modifier) {
    val downloads = AppGraph.downloads
    Row(
        modifier = modifier
            .fillMaxWidth()
            .padding(start = 20.dp, end = 4.dp, top = 6.dp, bottom = 6.dp),
        verticalAlignment = Alignment.CenterVertically
    ) {
        Box(modifier = Modifier.size(52.dp).clip(RoundedCornerShape(10.dp))) {
            AsyncImage(
                model = item.song.thumbnailUrl,
                contentDescription = null,
                contentScale = ContentScale.Crop,
                modifier = Modifier
                    .fillMaxSize()
                    .background(MaterialTheme.colorScheme.surfaceVariant)
            )
            if (item.error == null) DownloadRingOverlay(item.progress, ring = 38.dp)
        }
        Spacer(Modifier.width(12.dp))
        Column(modifier = Modifier.weight(1f)) {
            Text(item.song.title, style = MaterialTheme.typography.bodyMedium, maxLines = 1, overflow = TextOverflow.Ellipsis)
            if (item.error != null) {
                Text(
                    com.jimz.jimztune.util.UserError.text("Gagal mengunduh. Ketuk ulangi.", item.error),
                    style = MaterialTheme.typography.labelSmall,
                    color = MaterialTheme.colorScheme.tertiary,
                    maxLines = 2,
                    overflow = TextOverflow.Ellipsis
                )
            } else if (item.progress < 0f) {
                LinearProgressIndicator(
                    color = LocalCoverTheme.current.accent,
                    trackColor = Color.White.copy(alpha = 0.10f),
                    modifier = Modifier.fillMaxWidth().padding(top = 8.dp).height(5.dp).clip(CircleShape)
                )
            } else {
                LinearProgressIndicator(
                    progress = { item.progress },
                    color = LocalCoverTheme.current.accent,
                    trackColor = Color.White.copy(alpha = 0.10f),
                    modifier = Modifier.fillMaxWidth().padding(top = 8.dp).height(5.dp).clip(CircleShape)
                )
            }
        }
        if (item.error != null) {
            GlassIconButton(Icons.Rounded.Refresh, "Ulangi", onClick = {
                downloads.cancel(item.song.id)
                downloads.download(item.song)
            })
            Spacer(Modifier.width(8.dp))
        }
        GlassIconButton(Icons.Rounded.Close, "Batalkan", onClick = { downloads.cancel(item.song.id) })
        Spacer(Modifier.width(16.dp))
    }
}

private fun formatBytes(bytes: Long): String = when {
    bytes >= 1_000_000_000 -> "%.1f GB".format(bytes / 1_000_000_000.0)
    bytes >= 1_000_000 -> "%.1f MB".format(bytes / 1_000_000.0)
    bytes >= 1_000 -> "%.0f KB".format(bytes / 1_000.0)
    else -> "$bytes B"
}

// =====================================================================
// TOMBOL "PLAYLIST BARU" — jauh lebih fancy dari TonalPillButton biasa
// =====================================================================

@Composable
private fun NewPlaylistButton(modifier: Modifier = Modifier, onClick: () -> Unit) {
    val accent = LocalCoverTheme.current.accent
    val interaction = remember { MutableInteractionSource() }
    val pressed by interaction.collectIsPressedAsState()
    val scale by animateFloatAsState(
        targetValue = if (pressed) 0.95f else 1f,
        animationSpec = spring(dampingRatio = 0.55f, stiffness = Spring.StiffnessMedium),
        label = "newPLScale"
    )
    val shape = RoundedCornerShape(18.dp)

    Box(
        modifier = modifier
            .fillMaxWidth()
            .graphicsLayer { scaleX = scale; scaleY = scale }
            .clip(shape)
            .background(
                Brush.linearGradient(
                    listOf(accent.copy(alpha = 0.22f), Color.White.copy(alpha = 0.06f))
                )
            )
            .border(1.dp, accent.copy(alpha = 0.40f), shape)
            .clickable(interactionSource = interaction, indication = null, onClick = onClick)
            .padding(horizontal = 20.dp, vertical = 16.dp)
    ) {
        Row(verticalAlignment = Alignment.CenterVertically) {
            // Ikon plus dalam lingkaran aksen
            Box(
                modifier = Modifier
                    .size(42.dp)
                    .clip(CircleShape)
                    .background(accent.copy(alpha = 0.20f))
                    .border(1.dp, accent.copy(alpha = 0.50f), CircleShape),
                contentAlignment = Alignment.Center
            ) {
                Icon(
                    Icons.Rounded.Add,
                    contentDescription = null,
                    tint = accent,
                    modifier = Modifier.size(22.dp)
                )
            }
            Spacer(Modifier.width(14.dp))
            Column(modifier = Modifier.weight(1f)) {
                Text(
                    "Buat Playlist Baru",
                    style = MaterialTheme.typography.bodyLarge,
                    fontWeight = FontWeight.SemiBold,
                    color = Color.White
                )
                Text(
                    "Kumpulkan lagu favoritmu",
                    style = MaterialTheme.typography.bodySmall,
                    color = Color.White.copy(alpha = 0.55f)
                )
            }
            Icon(
                Icons.Rounded.AutoAwesome,
                contentDescription = null,
                tint = accent.copy(alpha = 0.6f),
                modifier = Modifier.size(18.dp)
            )
        }
    }
}


/** Tombol "Impor playlist": lebih kalem dari tombol Buat Playlist Baru, berbingkai tipis. */
@Composable
private fun ImportPlaylistButton(modifier: Modifier = Modifier, onClick: () -> Unit) {
    val shape = RoundedCornerShape(18.dp)
    val accent = LocalCoverTheme.current.accent
    Row(
        modifier = modifier
            .fillMaxWidth()
            .clip(shape)
            .cardSurface(shape)
            .clickable(onClick = onClick)
            .padding(horizontal = 20.dp, vertical = 14.dp),
        verticalAlignment = Alignment.CenterVertically
    ) {
        Icon(Icons.Rounded.CloudDownload, contentDescription = null, tint = accent, modifier = Modifier.size(24.dp))
        Spacer(Modifier.width(14.dp))
        Column(modifier = Modifier.weight(1f)) {
            Text("Impor Playlist", style = MaterialTheme.typography.bodyLarge, fontWeight = FontWeight.SemiBold, color = Color.White)
            Text(
                "Dari Spotify, YouTube Music, Deezer, Apple Music",
                style = MaterialTheme.typography.bodySmall,
                color = Color.White.copy(alpha = 0.55f)
            )
        }
    }
}
