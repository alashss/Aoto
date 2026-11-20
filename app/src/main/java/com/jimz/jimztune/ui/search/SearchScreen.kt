package com.jimz.jimztune.ui.search

import androidx.compose.foundation.background
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
import androidx.compose.foundation.horizontalScroll
import androidx.compose.foundation.rememberScrollState
import androidx.compose.foundation.lazy.LazyColumn
import androidx.compose.foundation.lazy.items
import androidx.compose.foundation.shape.CircleShape
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.foundation.text.KeyboardActions
import androidx.compose.foundation.text.KeyboardOptions
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.rounded.Close
import androidx.compose.material.icons.rounded.History
import androidx.compose.material.icons.rounded.MusicNote
import androidx.compose.material.icons.rounded.Search
import androidx.compose.material3.CircularProgressIndicator
import androidx.compose.material3.FilterChip
import androidx.compose.material3.FilterChipDefaults
import androidx.compose.material3.Icon
import androidx.compose.material3.IconButton
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.OutlinedTextField
import androidx.compose.material3.OutlinedTextFieldDefaults
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.runtime.getValue
import androidx.compose.runtime.setValue
import androidx.compose.runtime.remember
import androidx.compose.runtime.mutableIntStateOf
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clip
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.layout.ContentScale
import androidx.compose.ui.focus.onFocusChanged
import androidx.compose.ui.platform.LocalFocusManager
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.platform.LocalSoftwareKeyboardController
import androidx.compose.ui.text.input.ImeAction
import androidx.compose.ui.text.style.TextOverflow
import androidx.compose.ui.unit.dp
import androidx.lifecycle.viewmodel.compose.viewModel
import coil.compose.AsyncImage
import com.jimz.jimztune.data.model.SearchItem
import com.jimz.jimztune.data.model.SearchType
import com.jimz.jimztune.data.model.isLongVersion
import com.jimz.jimztune.ui.components.EmptyState
import com.jimz.jimztune.ui.components.LocalOpenDetail
import com.jimz.jimztune.ui.components.LocalOverlayPadding
import com.jimz.jimztune.ui.theme.LocalCoverTheme
import com.jimz.jimztune.ui.theme.JimzBlue
import com.jimz.jimztune.ui.theme.JimzBlueSoft
import com.jimz.jimztune.ui.theme.JimzIce
import com.jimz.jimztune.ui.theme.JimzSurface
import com.jimz.jimztune.ui.theme.NeonTitleStyle
import com.jimz.jimztune.ui.components.SongMenuButton
import com.jimz.jimztune.ui.components.PlaylistRefMenuButton
import com.jimz.jimztune.ui.components.isPlaylistRef
import com.jimz.jimztune.ui.components.GradientPillButton
import androidx.compose.material.icons.rounded.Refresh

@Composable
fun SearchScreen(
    currentId: String?,
    onPlay: (List<SearchItem>, Int) -> Unit,
    vm: SearchViewModel = viewModel()
) {
    val keyboard = LocalSoftwareKeyboardController.current
    val focusManager = LocalFocusManager.current
    val openDetail = LocalOpenDetail.current
    val onItemClick: (SearchItem) -> Unit = { item ->
        if (item.type == SearchType.SONG) {
            if (item.isLongVersion) {
                // Video loop / mix berjam-jam diputar sendiri, tidak masuk antrean lagu biasa.
                onPlay(listOf(item), 0)
            } else {
                val songs = (vm.state as? SearchUiState.Success)?.items.orEmpty()
                    .filter { it.type == SearchType.SONG && !it.isLongVersion }
                onPlay(songs, songs.indexOfFirst { it.id == item.id }.coerceAtLeast(0))
            }
            keyboard?.hide()
        } else {
            keyboard?.hide()
            openDetail(item)
        }
    }
    // Setelah mencari: tutup keyboard dan lepas fokus, supaya mengetuk kolom lagi memunculkan riwayat.
    val doSearch: (String) -> Unit = { text ->
        vm.submit(text)
        keyboard?.hide()
        focusManager.clearFocus()
    }

    Column(
        modifier = Modifier
            .fillMaxSize()
            .statusBarsPadding()
    ) {
        Text(
            "Cari",
            style = NeonTitleStyle,
            modifier = Modifier.padding(start = 20.dp, top = 16.dp, bottom = 12.dp)
        )

        OutlinedTextField(
            value = vm.query,
            onValueChange = vm::onQueryChange,
            modifier = Modifier
                .fillMaxWidth()
                .padding(horizontal = 20.dp)
                .onFocusChanged { vm.onFocusChange(it.isFocused) },
            placeholder = {
                Text(
                    "Cari lagu, album, atau artis",
                    maxLines = 1,
                    softWrap = false,
                    overflow = TextOverflow.Ellipsis
                )
            },
            leadingIcon = { Icon(Icons.Rounded.Search, contentDescription = null) },
            trailingIcon = {
                if (vm.query.isNotEmpty()) {
                    IconButton(onClick = vm::clear) {
                        Icon(Icons.Rounded.Close, contentDescription = "Hapus teks")
                    }
                }
            },
            singleLine = true,
            shape = RoundedCornerShape(28.dp),
            colors = OutlinedTextFieldDefaults.colors(
                focusedBorderColor = JimzBlueSoft,
                unfocusedBorderColor = Color.White.copy(alpha = 0.16f),
                focusedContainerColor = JimzSurface.copy(alpha = 0.6f),
                unfocusedContainerColor = JimzSurface.copy(alpha = 0.6f),
                cursorColor = JimzBlueSoft
            ),
            keyboardOptions = KeyboardOptions(imeAction = ImeAction.Search),
            keyboardActions = KeyboardActions(onSearch = { doSearch(vm.query) })
        )

        if (vm.state !is SearchUiState.Idle) {
            Row(
                modifier = Modifier
                    .horizontalScroll(rememberScrollState())
                    .padding(horizontal = 20.dp, vertical = 12.dp),
                horizontalArrangement = Arrangement.spacedBy(8.dp)
            ) {
                SearchType.entries.forEach { t ->
                    FilterChip(
                        selected = vm.type == t,
                        onClick = { vm.selectType(t) },
                        label = { Text(t.label) },
                        colors = FilterChipDefaults.filterChipColors(
                            selectedContainerColor = Color.White.copy(alpha = 0.16f),
                            selectedLabelColor = JimzIce
                        )
                    )
                }
            }
        } else {
            Spacer(Modifier.height(8.dp))
        }

        Box(modifier = Modifier.weight(1f)) {
            val rows = vm.suggestionRows
            if (vm.showSuggestions && rows.isNotEmpty()) {
                SuggestionList(
                    rows = rows,
                    showClearAll = vm.query.isBlank(),
                    onPick = doSearch,
                    onRemove = vm::removeHistory,
                    onClearAll = vm::clearHistory
                )
            } else {
                when (val s = vm.state) {
                    SearchUiState.Idle -> GenreGrid(
                        covers = vm.genreCovers,
                        onNeedCover = vm::loadGenreCover,
                        history = vm.history.take(6),
                        onPick = doSearch,
                        onRemoveHistory = vm::removeHistory,
                        onClearHistory = vm::clearHistory
                    )
                    SearchUiState.Loading -> Box(Modifier.fillMaxSize(), Alignment.Center) {
                        CircularProgressIndicator(color = JimzBlue)
                    }
                    is SearchUiState.Error -> Column(
                        modifier = Modifier
                            .fillMaxSize()
                            .padding(32.dp),
                        horizontalAlignment = Alignment.CenterHorizontally,
                        verticalArrangement = Arrangement.Center
                    ) {
                        Text(
                            s.message,
                            style = MaterialTheme.typography.bodyLarge,
                            color = MaterialTheme.colorScheme.onSurfaceVariant
                        )
                        Spacer(Modifier.height(16.dp))
                        GradientPillButton("Coba lagi", Icons.Rounded.Refresh, vm::retry)
                    }
                    is SearchUiState.Success ->
                        if (s.items.isEmpty()) {
                            EmptyState(
                                icon = Icons.Rounded.MusicNote,
                                title = "Tidak ada hasil",
                                body = "Coba kata kunci lain."
                            )
                        } else {
                            ResultList(s.items, s.source, currentId, onItemClick)
                        }
                }
            }
        }
    }
}

@Composable
private fun SuggestionList(
    rows: List<SuggestionRow>,
    showClearAll: Boolean,
    onPick: (String) -> Unit,
    onRemove: (String) -> Unit,
    onClearAll: () -> Unit
) {
    val accent = LocalCoverTheme.current.accent
    LazyColumn(contentPadding = PaddingValues(vertical = 4.dp)) {
        if (showClearAll && rows.any { it.fromHistory }) {
            item {
                Row(
                    modifier = Modifier
                        .fillMaxWidth()
                        .padding(start = 20.dp, end = 20.dp, top = 6.dp, bottom = 2.dp),
                    verticalAlignment = Alignment.CenterVertically
                ) {
                    Text(
                        "Riwayat pencarian",
                        style = MaterialTheme.typography.titleSmall,
                        fontWeight = FontWeight.SemiBold,
                        modifier = Modifier.weight(1f)
                    )
                    Text(
                        "Hapus semua",
                        style = MaterialTheme.typography.labelLarge,
                        color = accent,
                        modifier = Modifier
                            .clip(RoundedCornerShape(8.dp))
                            .clickable(onClick = onClearAll)
                            .padding(horizontal = 8.dp, vertical = 6.dp)
                    )
                }
            }
        }
        items(rows) { row ->
            Row(
                modifier = Modifier
                    .fillMaxWidth()
                    .clickable { onPick(row.text) }
                    .padding(start = 20.dp, end = if (row.fromHistory) 8.dp else 20.dp, top = 6.dp, bottom = 6.dp),
                verticalAlignment = Alignment.CenterVertically
            ) {
                Icon(
                    if (row.fromHistory) Icons.Rounded.History else Icons.Rounded.Search,
                    contentDescription = null,
                    tint = JimzBlueSoft,
                    modifier = Modifier.size(20.dp)
                )
                Spacer(Modifier.width(16.dp))
                Text(
                    row.text,
                    style = MaterialTheme.typography.bodyLarge,
                    maxLines = 1,
                    overflow = TextOverflow.Ellipsis,
                    modifier = Modifier.weight(1f)
                )
                if (row.fromHistory) {
                    IconButton(onClick = { onRemove(row.text) }) {
                        Icon(
                            Icons.Rounded.Close,
                            contentDescription = "Hapus dari riwayat",
                            tint = MaterialTheme.colorScheme.onSurfaceVariant,
                            modifier = Modifier.size(18.dp)
                        )
                    }
                } else {
                    Spacer(Modifier.height(36.dp))
                }
            }
        }
    }
}

@Composable
private fun ResultList(
    items: List<SearchItem>,
    source: String,
    currentId: String?,
    onItemClick: (SearchItem) -> Unit
) {
    // Kalau tidak ada lagu biasa sama sekali (mis. mencari "mix 1 jam"), langsung buka tab kumpulan.
    var tab by remember(items) {
        mutableIntStateOf(if (items.none { !it.isLongVersion } && items.any { it.isLongVersion }) 1 else 0)
    }
    LazyColumn(contentPadding = PaddingValues(bottom = LocalOverlayPadding.current)) {
        item {
            Text(
                "Sumber: $source",
                style = MaterialTheme.typography.labelMedium,
                color = MaterialTheme.colorScheme.onSurfaceVariant,
                modifier = Modifier.padding(horizontal = 20.dp, vertical = 4.dp)
            )
        }
        // Lagu biasa dan kumpulan / loop / mix panjang dipisah jadi dua tab, supaya hasil teratas selalu satu lagu.
        val normal = items.filter { !it.isLongVersion }
        val long = items.filter { it.isLongVersion }
        val showLong = tab == 1 && long.isNotEmpty()
        if (long.isNotEmpty()) {
            item {
                Row(
                    modifier = Modifier.padding(horizontal = 20.dp, vertical = 8.dp),
                    horizontalArrangement = Arrangement.spacedBy(8.dp)
                ) {
                    ResultTab("Lagu (${normal.size})", selected = !showLong) { tab = 0 }
                    ResultTab("Kumpulan & loop (${long.size})", selected = showLong) { tab = 1 }
                }
            }
            if (showLong) {
                item {
                    Text(
                        "Satu lagu diulang berjam-jam atau kumpulan banyak lagu dalam satu video",
                        style = MaterialTheme.typography.bodySmall,
                        color = MaterialTheme.colorScheme.onSurfaceVariant,
                        modifier = Modifier.padding(horizontal = 20.dp, vertical = 2.dp)
                    )
                }
            }
        }
        // Kunci stabil + buang duplikat: tanpa ini kartu bisa tertukar/hilang saat daftar berganti, dan kunci kembar membuat crash.
        items((if (showLong) long else normal).distinctBy { "${it.type}:${it.id}" }, key = { "${it.type}:${it.id}" }) { entry ->
            ResultRow(
                item = entry,
                playing = entry.type == SearchType.SONG && entry.id == currentId,
                onClick = { onItemClick(entry) }
            )
        }
    }
}

@Composable
private fun ResultTab(label: String, selected: Boolean, onClick: () -> Unit) {
    Text(
        label,
        style = MaterialTheme.typography.labelLarge,
        fontWeight = FontWeight.SemiBold,
        color = if (selected) Color.Black else MaterialTheme.colorScheme.onSurface,
        modifier = Modifier
            .clip(RoundedCornerShape(50))
            .background(if (selected) JimzBlueSoft else Color.White.copy(alpha = 0.10f))
            .clickable(onClick = onClick)
            .padding(horizontal = 14.dp, vertical = 8.dp)
    )
}

@Composable
private fun ResultRow(item: SearchItem, playing: Boolean, onClick: () -> Unit) {
    val shape = if (item.type == SearchType.ARTIST) CircleShape else RoundedCornerShape(10.dp)
    Row(
        modifier = Modifier
            .fillMaxWidth()
            .clickable(onClick = onClick)
            .padding(
                start = 20.dp,
                end = if (item.type == SearchType.SONG || item.isPlaylistRef) 4.dp else 20.dp,
                top = 8.dp,
                bottom = 8.dp
            ),
        verticalAlignment = Alignment.CenterVertically
    ) {
        AsyncImage(
            model = item.thumbnailUrl,
            contentDescription = null,
            contentScale = ContentScale.Crop,
            modifier = Modifier
                .size(56.dp)
                .clip(shape)
                .background(MaterialTheme.colorScheme.surfaceVariant)
        )
        Spacer(Modifier.width(14.dp))
        Column(modifier = Modifier.weight(1f)) {
            Text(
                item.title,
                style = MaterialTheme.typography.bodyLarge,
                color = if (playing) JimzBlueSoft else MaterialTheme.colorScheme.onSurface,
                maxLines = 1,
                overflow = TextOverflow.Ellipsis
            )
            if (item.subtitle.isNotEmpty()) {
                Text(
                    item.subtitle,
                    style = MaterialTheme.typography.bodyMedium,
                    color = MaterialTheme.colorScheme.onSurfaceVariant,
                    maxLines = 1,
                    overflow = TextOverflow.Ellipsis
                )
            }
        }
        item.durationText?.let {
            Spacer(Modifier.width(12.dp))
            Text(it, style = MaterialTheme.typography.labelMedium, color = MaterialTheme.colorScheme.onSurfaceVariant)
        }
        if (item.type == SearchType.SONG) SongMenuButton(item)
        else if (item.isPlaylistRef) PlaylistRefMenuButton(item, onOpen = onClick)
    }
}
