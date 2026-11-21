package com.jimz.jimztune.ui.library

import androidx.compose.foundation.background
import androidx.compose.foundation.gestures.detectDragGestures
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.layout.statusBarsPadding
import androidx.compose.foundation.layout.width
import androidx.compose.foundation.lazy.LazyColumn
import androidx.compose.foundation.lazy.rememberLazyListState
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.rounded.Close
import androidx.compose.material.icons.rounded.DragHandle
import androidx.compose.material.icons.rounded.KeyboardDoubleArrowUp
import androidx.compose.material3.Icon
import androidx.compose.material3.IconButton
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.Surface
import androidx.compose.material3.Text
import androidx.compose.material3.TextButton
import androidx.compose.runtime.Composable
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableFloatStateOf
import androidx.compose.runtime.mutableStateListOf
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.setValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clip
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.graphics.graphicsLayer
import androidx.compose.ui.input.pointer.pointerInput
import androidx.compose.ui.layout.ContentScale
import androidx.compose.ui.platform.LocalDensity
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.text.style.TextOverflow
import androidx.compose.ui.unit.dp
import androidx.compose.ui.window.Dialog
import androidx.compose.ui.window.DialogProperties
import androidx.compose.ui.zIndex
import coil.compose.AsyncImage
import com.jimz.jimztune.AppGraph
import com.jimz.jimztune.data.model.SearchItem

/**
 * Atur urutan lagu di dalam playlist dengan menyeret pegangan di kanan.
 * Urutan baru baru disimpan saat menekan "Simpan"; menutupnya tidak mengubah apa-apa.
 */
@Composable
fun ReorderPlaylistDialog(playlistId: String, onDismiss: () -> Unit) {
    val library = AppGraph.library
    val list = remember { mutableStateListOf<SearchItem>().apply { addAll(library.playlist(playlistId)?.songs.orEmpty()) } }
    val rowH = 60.dp
    val rowPx = with(LocalDensity.current) { rowH.toPx() }
    var dragging by remember { mutableStateOf<String?>(null) }
    var offset by remember { mutableFloatStateOf(0f) }
    val listState = rememberLazyListState()

    Dialog(onDismissRequest = onDismiss, properties = DialogProperties(usePlatformDefaultWidth = false)) {
        Surface(modifier = Modifier.fillMaxSize(), color = MaterialTheme.colorScheme.background) {
            Column(Modifier.statusBarsPadding()) {
                Row(
                    verticalAlignment = Alignment.CenterVertically,
                    modifier = Modifier.fillMaxWidth().padding(horizontal = 8.dp, vertical = 6.dp)
                ) {
                    IconButton(onClick = onDismiss) { Icon(Icons.Rounded.Close, contentDescription = "Tutup") }
                    Column(Modifier.weight(1f)) {
                        Text("Atur urutan", style = MaterialTheme.typography.titleMedium, fontWeight = FontWeight.SemiBold)
                        Text(
                            "Seret pegangan di kanan untuk memindahkan lagu",
                            style = MaterialTheme.typography.labelSmall,
                            color = MaterialTheme.colorScheme.onSurfaceVariant
                        )
                    }
                    TextButton(onClick = {
                        library.reorderPlaylist(playlistId, list.toList())
                        onDismiss()
                    }) { Text("Simpan", fontWeight = FontWeight.SemiBold) }
                }
                LazyColumn(state = listState, modifier = Modifier.fillMaxSize()) {
                    items(list.size, key = { list[it].id }) { i ->
                        val s = list[i]
                        val isDrag = dragging == s.id
                        val base = Modifier
                            .fillMaxWidth()
                            .height(rowH)
                            .padding(horizontal = 16.dp, vertical = 4.dp)
                        Row(
                            verticalAlignment = Alignment.CenterVertically,
                            modifier = if (isDrag) {
                                base
                                    .zIndex(1f)
                                    .graphicsLayer { translationY = offset; shadowElevation = 16f }
                                    .clip(RoundedCornerShape(12.dp))
                                    .background(MaterialTheme.colorScheme.surfaceVariant)
                            } else base.animateItem()
                        ) {
                            AsyncImage(
                                model = s.thumbnailUrl,
                                contentDescription = null,
                                contentScale = ContentScale.Crop,
                                modifier = Modifier
                                    .size(44.dp)
                                    .clip(RoundedCornerShape(8.dp))
                                    .background(Color.White.copy(alpha = 0.08f))
                            )
                            Spacer(Modifier.width(12.dp))
                            Column(Modifier.weight(1f)) {
                                Text(s.title, style = MaterialTheme.typography.bodyMedium, maxLines = 1, overflow = TextOverflow.Ellipsis)
                                Text(
                                    s.subtitle.substringBefore(" • "),
                                    style = MaterialTheme.typography.bodySmall,
                                    color = MaterialTheme.colorScheme.onSurfaceVariant,
                                    maxLines = 1,
                                    overflow = TextOverflow.Ellipsis
                                )
                            }
                            IconButton(
                                onClick = { val idx = list.indexOfFirst { it.id == s.id }; if (idx > 0) list.add(0, list.removeAt(idx)) },
                                modifier = Modifier.size(40.dp)
                            ) {
                                Icon(
                                    Icons.Rounded.KeyboardDoubleArrowUp, contentDescription = "Pindah ke paling atas",
                                    tint = MaterialTheme.colorScheme.onSurfaceVariant, modifier = Modifier.size(20.dp)
                                )
                            }
                            Icon(
                                Icons.Rounded.DragHandle,
                                contentDescription = "Seret untuk memindahkan",
                                tint = MaterialTheme.colorScheme.onSurfaceVariant,
                                modifier = Modifier
                                    .size(40.dp)
                                    .padding(8.dp)
                                    .pointerInput(s.id) {
                                        detectDragGestures(
                                            onDragStart = { dragging = s.id; offset = 0f },
                                            onDragEnd = { dragging = null; offset = 0f },
                                            onDragCancel = { dragging = null; offset = 0f }
                                        ) { change, amount ->
                                            change.consume()
                                            offset += amount.y
                                            val idx = list.indexOfFirst { it.id == s.id }
                                            if (offset > rowPx / 2 && idx in 0 until list.lastIndex) {
                                                list.add(idx + 1, list.removeAt(idx)); offset -= rowPx
                                            } else if (offset < -rowPx / 2 && idx > 0) {
                                                list.add(idx - 1, list.removeAt(idx)); offset += rowPx
                                            }
                                        }
                                    }
                            )
                        }
                    }
                }
            }
        }
    }
}
