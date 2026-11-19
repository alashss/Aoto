package com.jimz.jimztune.ui.library

import androidx.compose.foundation.background
import androidx.compose.foundation.clickable
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.imePadding
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.layout.systemBarsPadding
import androidx.compose.foundation.layout.width
import androidx.compose.foundation.lazy.LazyColumn
import androidx.compose.foundation.lazy.items
import androidx.compose.foundation.shape.CircleShape
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.foundation.text.KeyboardActions
import androidx.compose.foundation.text.KeyboardOptions
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.rounded.Add
import androidx.compose.material.icons.rounded.Check
import androidx.compose.material.icons.rounded.Close
import androidx.compose.material.icons.rounded.Search
import androidx.compose.material3.CircularProgressIndicator
import androidx.compose.material3.Icon
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.Text
import androidx.compose.material3.TextField
import androidx.compose.material3.TextFieldDefaults
import androidx.compose.runtime.Composable
import androidx.compose.runtime.LaunchedEffect
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.rememberCoroutineScope
import androidx.compose.runtime.setValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clip
import androidx.compose.ui.focus.FocusRequester
import androidx.compose.ui.focus.focusRequester
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.layout.ContentScale
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.text.input.ImeAction
import androidx.compose.ui.text.style.TextOverflow
import androidx.compose.ui.unit.dp
import androidx.compose.ui.window.Dialog
import androidx.compose.ui.window.DialogProperties
import coil.compose.AsyncImage
import com.jimz.jimztune.AppGraph
import com.jimz.jimztune.data.model.SearchItem
import com.jimz.jimztune.data.model.SearchType
import com.jimz.jimztune.ui.components.EmptyState
import com.jimz.jimztune.ui.components.GlassIconButton
import com.jimz.jimztune.ui.theme.JimzBackground
import com.jimz.jimztune.ui.theme.LocalCoverTheme
import kotlinx.coroutines.CancellationException
import kotlinx.coroutines.delay

private sealed interface AddState {
    data object Idle : AddState
    data object Loading : AddState
    data class Done(val items: List<SearchItem>) : AddState
    data object Failed : AddState
}

/**
 * Layar "Tambah lagu" untuk satu playlist: cari lagu, ketuk untuk menambahkan (cover diberi tanda centang),
 * lalu langsung cari lagi tanpa keluar. Ketuk lagu yang sudah bercentang untuk membatalkannya.
 */
@Composable
fun AddSongsDialog(playlistId: String, onDismiss: () -> Unit) {
    val library = AppGraph.library
    val accent = LocalCoverTheme.current.accent
    val playlist = library.playlist(playlistId)
    val inPlaylist = playlist?.songs?.map { it.id }?.toSet().orEmpty()

    var query by remember { mutableStateOf("") }
    var state by remember { mutableStateOf<AddState>(AddState.Idle) }
    var retry by remember { mutableStateOf(0) }
    var addedNow by remember { mutableStateOf(0) }
    val focus = remember { FocusRequester() }

    // Cari otomatis setelah berhenti mengetik sebentar.
    LaunchedEffect(query, retry) {
        val q = query.trim()
        if (q.length < 2) {
            state = AddState.Idle
            return@LaunchedEffect
        }
        delay(450)
        state = AddState.Loading
        state = try {
            AddState.Done(AppGraph.searchRepository.search(q, SearchType.SONG).items.filter { it.type == SearchType.SONG })
        } catch (e: CancellationException) {
            throw e
        } catch (_: Exception) {
            AddState.Failed
        }
    }
    LaunchedEffect(Unit) { runCatching { focus.requestFocus() } }

    Dialog(
        onDismissRequest = onDismiss,
        properties = DialogProperties(usePlatformDefaultWidth = false)
    ) {
        Column(
            modifier = Modifier
                .fillMaxSize()
                .background(JimzBackground)
                .systemBarsPadding()
                .imePadding()
        ) {
            Row(
                modifier = Modifier.fillMaxWidth().padding(start = 20.dp, end = 12.dp, top = 8.dp, bottom = 4.dp),
                verticalAlignment = Alignment.CenterVertically
            ) {
                Column(modifier = Modifier.weight(1f)) {
                    Text(
                        "Tambah lagu",
                        style = MaterialTheme.typography.titleLarge,
                        fontWeight = FontWeight.Bold
                    )
                    Text(
                        (playlist?.name ?: "") + if (addedNow > 0) "  •  $addedNow ditambahkan" else "",
                        style = MaterialTheme.typography.bodySmall,
                        color = MaterialTheme.colorScheme.onSurfaceVariant,
                        maxLines = 1,
                        overflow = TextOverflow.Ellipsis
                    )
                }
                Box(
                    modifier = Modifier
                        .clip(CircleShape)
                        .background(accent)
                        .clickable(onClick = onDismiss)
                        .padding(horizontal = 18.dp, vertical = 10.dp)
                ) {
                    Text("Selesai", color = Color.Black, fontWeight = FontWeight.SemiBold)
                }
            }

            TextField(
                value = query,
                onValueChange = { query = it },
                singleLine = true,
                placeholder = { Text("Cari lagu atau artis") },
                leadingIcon = { Icon(Icons.Rounded.Search, contentDescription = null) },
                trailingIcon = {
                    if (query.isNotEmpty()) {
                        Icon(
                            Icons.Rounded.Close,
                            contentDescription = "Hapus",
                            modifier = Modifier.clip(CircleShape).clickable { query = "" }.padding(4.dp)
                        )
                    }
                },
                keyboardOptions = KeyboardOptions(imeAction = ImeAction.Search),
                keyboardActions = KeyboardActions(onSearch = { retry++ }),
                shape = RoundedCornerShape(16.dp),
                colors = TextFieldDefaults.colors(
                    focusedContainerColor = Color.White.copy(alpha = 0.09f),
                    unfocusedContainerColor = Color.White.copy(alpha = 0.09f),
                    focusedIndicatorColor = Color.Transparent,
                    unfocusedIndicatorColor = Color.Transparent
                ),
                modifier = Modifier
                    .fillMaxWidth()
                    .padding(horizontal = 16.dp, vertical = 8.dp)
                    .focusRequester(focus)
            )

            Box(modifier = Modifier.weight(1f).fillMaxWidth()) {
                when (val s = state) {
                    AddState.Idle -> EmptyState(
                        Icons.Rounded.Search,
                        "Cari lagu untuk ditambahkan",
                        "Ketuk lagu untuk memasukkannya ke playlist. Kamu bisa terus mencari dan menambah lagu lain."
                    )
                    AddState.Loading -> Box(Modifier.fillMaxSize(), contentAlignment = Alignment.Center) {
                        CircularProgressIndicator(color = accent)
                    }
                    AddState.Failed -> EmptyState(
                        Icons.Rounded.Search,
                        "Pencarian gagal",
                        "Periksa koneksi internet, lalu ketuk tombol cari di keyboard untuk mencoba lagi."
                    )
                    is AddState.Done -> if (s.items.isEmpty()) {
                        EmptyState(Icons.Rounded.Search, "Tidak ditemukan", "Coba kata kunci lain.")
                    } else {
                        LazyColumn(contentPadding = androidx.compose.foundation.layout.PaddingValues(bottom = 24.dp)) {
                            items(s.items, key = { it.id }) { song ->
                                val added = song.id in inPlaylist
                                AddSongRow(song, added, accent) {
                                    if (added) {
                                        library.removeFromPlaylist(playlistId, song.id)
                                        if (addedNow > 0) addedNow--
                                    } else if (library.addToPlaylist(playlistId, song)) {
                                        addedNow++
                                    }
                                }
                            }
                        }
                    }
                }
            }
        }
    }
}

@Composable
private fun AddSongRow(song: SearchItem, added: Boolean, accent: Color, onClick: () -> Unit) {
    Row(
        modifier = Modifier
            .fillMaxWidth()
            .padding(horizontal = 10.dp, vertical = 2.dp)
            .clip(RoundedCornerShape(16.dp))
            .background(if (added) accent.copy(alpha = 0.10f) else Color.Transparent)
            .clickable(onClick = onClick)
            .padding(start = 10.dp, end = 12.dp, top = 6.dp, bottom = 6.dp),
        verticalAlignment = Alignment.CenterVertically
    ) {
        Box(modifier = Modifier.size(52.dp).clip(RoundedCornerShape(12.dp))) {
            AsyncImage(
                model = song.thumbnailUrl,
                contentDescription = null,
                contentScale = ContentScale.Crop,
                modifier = Modifier
                    .fillMaxSize()
                    .background(MaterialTheme.colorScheme.surfaceVariant)
            )
            if (added) {
                Box(
                    modifier = Modifier.fillMaxSize().background(Color.Black.copy(alpha = 0.55f)),
                    contentAlignment = Alignment.Center
                ) {
                    Box(
                        modifier = Modifier.size(28.dp).clip(CircleShape).background(accent),
                        contentAlignment = Alignment.Center
                    ) {
                        Icon(Icons.Rounded.Check, contentDescription = "Sudah ditambahkan", tint = Color.Black, modifier = Modifier.size(18.dp))
                    }
                }
            }
        }
        Spacer(Modifier.width(14.dp))
        Column(modifier = Modifier.weight(1f), verticalArrangement = Arrangement.Center) {
            Text(
                song.title,
                style = MaterialTheme.typography.bodyLarge,
                fontWeight = FontWeight.Medium,
                color = if (added) accent else MaterialTheme.colorScheme.onSurface,
                maxLines = 1,
                overflow = TextOverflow.Ellipsis
            )
            Text(
                song.subtitle.substringBefore(" • "),
                style = MaterialTheme.typography.bodyMedium,
                color = MaterialTheme.colorScheme.onSurfaceVariant,
                maxLines = 1,
                overflow = TextOverflow.Ellipsis
            )
        }
        Spacer(Modifier.width(8.dp))
        Icon(
            if (added) Icons.Rounded.Check else Icons.Rounded.Add,
            contentDescription = if (added) "Ketuk untuk membatalkan" else "Tambahkan",
            tint = if (added) accent else Color.White.copy(alpha = 0.8f)
        )
    }
}
