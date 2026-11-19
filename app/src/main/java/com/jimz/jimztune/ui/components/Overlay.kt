package com.jimz.jimztune.ui.components

import androidx.compose.runtime.compositionLocalOf
import androidx.compose.runtime.staticCompositionLocalOf
import androidx.compose.ui.unit.Dp
import androidx.compose.ui.unit.dp

/** Tinggi area yang tertutup navbar kaca dan mini player, agar daftar bisa digulir sampai melewatinya. */
val LocalOverlayPadding = compositionLocalOf<Dp> { 0.dp }

/** Membuka halaman artis/album dari mana saja (hasil pencarian, menu lagu, halaman lain). */
val LocalOpenDetail = staticCompositionLocalOf<(com.jimz.jimztune.data.model.SearchItem) -> Unit> { {} }

/** Aksi antrean yang bisa dipanggil dari menu lagu mana pun (diisi oleh JimzTuneApp). */
class QueueActions(
    val playNext: (com.jimz.jimztune.data.model.SearchItem) -> Unit = {},
    val addToQueue: (com.jimz.jimztune.data.model.SearchItem) -> Unit = {}
)

val LocalQueueActions = staticCompositionLocalOf { QueueActions() }
