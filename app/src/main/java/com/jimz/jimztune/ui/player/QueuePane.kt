package com.jimz.jimztune.ui.player

import androidx.compose.foundation.ExperimentalFoundationApi
import androidx.compose.foundation.background
import androidx.compose.foundation.clickable
import androidx.compose.foundation.gestures.detectDragGesturesAfterLongPress
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.layout.width
import androidx.compose.foundation.lazy.LazyColumn
import androidx.compose.foundation.lazy.itemsIndexed
import androidx.compose.foundation.lazy.rememberLazyListState
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.rounded.Delete
import androidx.compose.material.icons.rounded.DragHandle
import androidx.compose.material.icons.rounded.MusicNote
import androidx.compose.material3.ExperimentalMaterial3Api
import androidx.compose.material3.Icon
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.SwipeToDismissBox
import androidx.compose.material3.SwipeToDismissBoxValue
import androidx.compose.material3.Text
import androidx.compose.material3.rememberSwipeToDismissBoxState
import androidx.compose.runtime.Composable
import androidx.compose.runtime.LaunchedEffect
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableFloatStateOf
import androidx.compose.runtime.mutableIntStateOf
import androidx.compose.runtime.mutableStateListOf
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.rememberUpdatedState
import androidx.compose.runtime.setValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clip
import androidx.compose.ui.draw.shadow
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.graphics.graphicsLayer
import androidx.compose.ui.input.pointer.pointerInput
import androidx.compose.ui.layout.ContentScale
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.text.style.TextOverflow
import androidx.compose.ui.unit.dp
import androidx.compose.ui.zIndex
import coil.compose.AsyncImage
import com.jimz.jimztune.data.model.SearchItem

/** Satu baris antrean + kunci stabil (lagu yang sama bisa muncul dua kali, jadi id saja tidak cukup). */
private class QueueEntry(val key: String, val item: SearchItem)

private fun List<SearchItem>.toEntries(): List<QueueEntry> {
    val seen = HashMap<String, Int>()
    return map { item ->
        val n = seen.getOrDefault(item.id, 0)
        seen[item.id] = n + 1
        QueueEntry("${item.id}#$n", item)
    }
}

/**
 * Isi tab Antrean.
 * - Ikon garis tiga di kanan: tahan lalu geser untuk memindah posisi lagu.
 * - Geser baris dari kanan ke kiri: hapus lagu dari antrean.
 */
@OptIn(ExperimentalMaterial3Api::class, ExperimentalFoundationApi::class)
@Composable
fun QueuePane(
    queue: List<SearchItem>,
    queueIndex: Int,
    sourceName: String?,
    onSkipTo: (Int) -> Unit,
    onRemove: (Int) -> Unit,
    onMove: (Int, Int) -> Unit
) {
    val white70 = Color.White.copy(alpha = 0.7f)
    val listState = rememberLazyListState()

    // Salinan lokal supaya posisi langsung ikut jari saat menggeser; baru dikirim ke pemutar saat dilepas.
    val local = remember { mutableStateListOf<QueueEntry>().apply { addAll(queue.toEntries()) } }
    var draggedKey by remember { mutableStateOf<String?>(null) }
    var dragStartIndex by remember { mutableIntStateOf(-1) }
    var dragDelta by remember { mutableFloatStateOf(0f) }

    LaunchedEffect(queue) {
        if (draggedKey == null) {
            local.clear()
            local.addAll(queue.toEntries())
        }
    }
    // Saat tab Antrean dibuka, langsung gulir ke lagu yang sedang diputar.
    LaunchedEffect(Unit) { listState.scrollToItem(queueIndex.coerceAtLeast(0)) }

    val currentOnMove by rememberUpdatedState(onMove)
    val currentOnRemove by rememberUpdatedState(onRemove)

    Column(modifier = Modifier.fillMaxSize()) {
        Text(
            if (sourceName != null) "${local.size} lagu · ikut berubah dengan playlist “$sourceName”"
            else "${local.size} lagu di antrean",
            style = MaterialTheme.typography.labelMedium,
            color = white70,
            modifier = Modifier.padding(horizontal = 4.dp, vertical = 6.dp)
        )
        LazyColumn(state = listState, modifier = Modifier.fillMaxSize()) {
            itemsIndexed(local, key = { _, e -> e.key }) { index, entry ->
                val active = index == queueIndex
                val dragging = entry.key == draggedKey

                val dismissState = rememberSwipeToDismissBoxState(
                    confirmValueChange = { value ->
                        if (value == SwipeToDismissBoxValue.EndToStart) {
                            val i = local.indexOfFirst { it.key == entry.key }
                            if (i >= 0) {
                                local.removeAt(i)
                                currentOnRemove(i)
                            }
                            true
                        } else false
                    }
                )

                SwipeToDismissBox(
                    state = dismissState,
                    enableDismissFromStartToEnd = false,
                    enableDismissFromEndToStart = draggedKey == null,
                    modifier = Modifier
                        .then(if (dragging) Modifier else Modifier.animateItem())
                        .zIndex(if (dragging) 1f else 0f)
                        .graphicsLayer { translationY = if (dragging) dragDelta else 0f },
                    backgroundContent = {
                        if (dismissState.dismissDirection == SwipeToDismissBoxValue.EndToStart) {
                            Box(
                                modifier = Modifier
                                    .fillMaxSize()
                                    .clip(RoundedCornerShape(12.dp))
                                    .background(Color(0xFFB3261E)),
                                contentAlignment = Alignment.CenterEnd
                            ) {
                                Icon(
                                    Icons.Rounded.Delete,
                                    contentDescription = "Hapus dari antrean",
                                    tint = Color.White,
                                    modifier = Modifier.padding(end = 20.dp)
                                )
                            }
                        }
                    }
                ) {
                    Row(
                        modifier = Modifier
                            .fillMaxWidth()
                            .then(
                                if (dragging) Modifier.shadow(8.dp, RoundedCornerShape(12.dp))
                                else Modifier
                            )
                            .clip(RoundedCornerShape(12.dp))
                            .background(if (dragging) Color(0xFF2A2F45) else Color.Transparent)
                            .clickable { onSkipTo(local.indexOfFirst { it.key == entry.key }.coerceAtLeast(0)) }
                            .padding(vertical = 6.dp, horizontal = 4.dp),
                        verticalAlignment = Alignment.CenterVertically
                    ) {
                        AsyncImage(
                            model = entry.item.thumbnailUrl,
                            contentDescription = null,
                            contentScale = ContentScale.Crop,
                            modifier = Modifier
                                .size(48.dp)
                                .clip(RoundedCornerShape(10.dp))
                                .background(MaterialTheme.colorScheme.surfaceVariant)
                        )
                        Spacer(Modifier.width(12.dp))
                        Column(modifier = Modifier.weight(1f)) {
                            Text(
                                entry.item.title,
                                style = MaterialTheme.typography.bodyLarge,
                                fontWeight = if (active) FontWeight.Bold else FontWeight.Normal,
                                maxLines = 1,
                                overflow = TextOverflow.Ellipsis
                            )
                            Text(
                                entry.item.subtitle,
                                style = MaterialTheme.typography.bodySmall,
                                color = white70,
                                maxLines = 1,
                                overflow = TextOverflow.Ellipsis
                            )
                        }
                        if (active) {
                            Icon(Icons.Rounded.MusicNote, contentDescription = null)
                        }
                        // Pegangan geser: tahan lalu seret naik / turun.
                        Icon(
                            Icons.Rounded.DragHandle,
                            contentDescription = "Tahan lalu geser untuk memindah",
                            tint = white70,
                            modifier = Modifier
                                .padding(horizontal = 8.dp)
                                .size(28.dp)
                                .pointerInput(entry.key) {
                                    detectDragGesturesAfterLongPress(
                                        onDragStart = {
                                            draggedKey = entry.key
                                            dragStartIndex = local.indexOfFirst { it.key == entry.key }
                                            dragDelta = 0f
                                        },
                                        onDrag = { change, amount ->
                                            change.consume()
                                            dragDelta += amount.y
                                            val infos = listState.layoutInfo.visibleItemsInfo
                                            val me = infos.firstOrNull { it.key == entry.key }
                                                ?: return@detectDragGesturesAfterLongPress
                                            val center = me.offset + me.size / 2f + dragDelta
                                            val target = infos.firstOrNull {
                                                it.key != entry.key &&
                                                    center >= it.offset && center < it.offset + it.size
                                            }
                                            if (target != null) {
                                                val from = local.indexOfFirst { it.key == entry.key }
                                                val to = local.indexOfFirst { it.key == target.key }
                                                if (from >= 0 && to >= 0 && from != to) {
                                                    local.add(to, local.removeAt(from))
                                                    // Item pindah slot: sesuaikan supaya tetap menempel di jari.
                                                    dragDelta += (me.offset - target.offset).toFloat()
                                                }
                                            }
                                        },
                                        onDragEnd = {
                                            val end = local.indexOfFirst { it.key == entry.key }
                                            val start = dragStartIndex
                                            draggedKey = null
                                            dragDelta = 0f
                                            dragStartIndex = -1
                                            if (start >= 0 && end >= 0 && start != end) currentOnMove(start, end)
                                        },
                                        onDragCancel = {
                                            draggedKey = null
                                            dragDelta = 0f
                                            dragStartIndex = -1
                                            local.clear()
                                            local.addAll(queue.toEntries())
                                        }
                                    )
                                }
                        )
                    }
                }
            }
        }
    }
}
