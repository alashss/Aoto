package com.jimz.jimztune.ui.components

import android.widget.Toast
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.padding
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.rounded.Close
import androidx.compose.material.icons.rounded.Download
import androidx.compose.material.icons.rounded.DownloadDone
import androidx.compose.runtime.Composable
import androidx.compose.ui.Modifier
import androidx.compose.ui.platform.LocalContext
import androidx.compose.ui.unit.Dp
import androidx.compose.ui.unit.dp
import com.jimz.jimztune.AppGraph
import com.jimz.jimztune.data.model.SearchItem

/**
 * Tombol "Unduh semua" untuk satu playlist/album: mengantre semua lagu yang belum terunduh sekaligus.
 * Berubah jadi penghitung "Mengunduh 3/12" (ketuk untuk membatalkan sisanya), lalu "Semua terunduh".
 * Lagu yang gagal diunduh tetap bisa diulang lewat tombol yang sama.
 */
@Composable
fun DownloadAllBar(
    songs: List<SearchItem>,
    modifier: Modifier = Modifier,
    horizontalPadding: Dp = 20.dp,
    center: Boolean = false
) {
    val downloads = AppGraph.downloads
    val context = LocalContext.current
    val unique = songs.distinctBy { it.id }
    if (unique.isEmpty()) return

    val total = unique.size
    val done = unique.count { downloads.isDownloaded(it.id) }
    val running = unique.count { val a = downloads.active[it.id]; a != null && a.error == null }
    val remaining = total - done

    Row(
        modifier = modifier.fillMaxWidth().padding(horizontal = horizontalPadding, vertical = 4.dp),
        horizontalArrangement = if (center) Arrangement.Center else Arrangement.Start
    ) {
        when {
            remaining == 0 -> TonalPillButton("Semua terunduh ($total)", Icons.Rounded.DownloadDone, {
                Toast.makeText(context, "Semua lagu sudah tersimpan untuk offline", Toast.LENGTH_SHORT).show()
            })
            running > 0 -> TonalPillButton("Mengunduh $done/$total · Batalkan", Icons.Rounded.Close, {
                downloads.cancelAll(unique)
            })
            else -> TonalPillButton(
                if (done == 0) "Unduh semua ($total)" else "Unduh sisanya ($remaining)",
                Icons.Rounded.Download,
                {
                    val queued = downloads.downloadAll(unique)
                    Toast.makeText(context, "Mengunduh $queued lagu…", Toast.LENGTH_SHORT).show()
                }
            )
        }
    }
}
