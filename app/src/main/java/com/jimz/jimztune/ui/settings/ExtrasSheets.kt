package com.jimz.jimztune.ui.settings

import android.content.Context
import android.content.Intent
import android.net.Uri
import android.provider.Settings
import android.widget.Toast
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.rememberScrollState
import androidx.compose.foundation.verticalScroll
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableIntStateOf
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.setValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.platform.LocalContext
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.unit.dp
import com.jimz.jimztune.AppGraph
import com.jimz.jimztune.island.IslandPrefs
import com.jimz.jimztune.ui.components.ChoiceRow
import com.jimz.jimztune.ui.components.JimzBottomSheet
import com.jimz.jimztune.ui.components.OptionChip
import com.jimz.jimztune.ui.components.SectionHeader
import com.jimz.jimztune.ui.components.SwitchRow
import com.jimz.jimztune.widget.LyricsWidgetProvider
import com.jimz.jimztune.widget.LyricsWidgetStore
import java.io.File

private fun mb(bytes: Long): String = when {
    bytes >= 1024L * 1024 * 1024 -> "%.1f GB".format(bytes / 1024.0 / 1024 / 1024)
    bytes >= 1024L * 1024 -> "%.1f MB".format(bytes / 1024.0 / 1024)
    bytes >= 1024 -> "%.0f KB".format(bytes / 1024.0)
    else -> "$bytes B"
}

private fun dirSize(f: File): Long = when {
    !f.exists() -> 0L
    f.isFile -> f.length()
    else -> f.listFiles()?.sumOf { dirSize(it) } ?: 0L
}

/** Rincian pemakaian penyimpanan Aoto, dengan tombol bersihkan untuk bagian yang aman dibuang. */
@Composable
fun StorageSheet(onDismiss: () -> Unit) {
    val context = LocalContext.current
    var refresh by remember { mutableIntStateOf(0) }
    val downloads = remember(refresh) { AppGraph.downloads.totalBytes() }
    val audio = remember(refresh) { dirSize(File(context.cacheDir, "audio_v2")) }
    val images = remember(refresh) { dirSize(File(context.cacheDir, "image_cache")) }
    val lyrics = remember(refresh) {
        dirSize(File(context.filesDir, "lyrics_v3")) + dirSize(File(context.filesDir, "lyrics_tr_v3"))
    }
    val total = downloads + audio + images + lyrics

    JimzBottomSheet(onDismiss = onDismiss) {
        Column(Modifier.verticalScroll(rememberScrollState()).padding(horizontal = 24.dp)) {
            Text("Penyimpanan", style = MaterialTheme.typography.titleLarge, fontWeight = FontWeight.Bold)
            Text("Total: ${mb(total)}", style = MaterialTheme.typography.bodyMedium, color = Color.White.copy(alpha = 0.7f))
            Spacer(Modifier.height(8.dp))
            UsageRow("Unduhan offline", "Lagu yang kamu unduh", downloads)
            UsageRow("Cache audio", "Potongan lagu yang sudah diputar. Dibatasi 300 MB dan dibersihkan otomatis", audio)
            UsageRow("Gambar", "Sampul dan foto", images, "Bersihkan") {
                File(context.cacheDir, "image_cache").deleteRecursively()
                refresh++
                Toast.makeText(context, "Cache gambar dibersihkan", Toast.LENGTH_SHORT).show()
            }
            UsageRow("Lirik dan terjemahan", "Diunduh ulang saat dibutuhkan", lyrics, "Bersihkan") {
                listOf("lyrics_v3", "lyrics_tr_v3").forEach { d -> File(context.filesDir, d).listFiles()?.toList()?.forEach { f -> f.delete() } }
                refresh++
                Toast.makeText(context, "Cache lirik dibersihkan", Toast.LENGTH_SHORT).show()
            }
            Spacer(Modifier.height(20.dp))
        }
    }
}

@Composable
private fun UsageRow(title: String, hint: String, bytes: Long, action: String? = null, onAction: () -> Unit = {}) {
    Row(Modifier.fillMaxWidth().padding(vertical = 8.dp), verticalAlignment = Alignment.CenterVertically) {
        Column(Modifier.weight(1f)) {
            Text(title, style = MaterialTheme.typography.bodyLarge, fontWeight = FontWeight.Medium)
            Text(hint, style = MaterialTheme.typography.bodySmall, color = Color.White.copy(alpha = 0.55f))
        }
        Text(mb(bytes), style = MaterialTheme.typography.bodyMedium, modifier = Modifier.padding(horizontal = 10.dp))
        if (action != null) OptionChip(action, false, onAction)
    }
}

internal fun canDraw(context: Context) = try {
    Settings.canDrawOverlays(context)
} catch (_: Exception) {
    false
}

internal fun requestOverlay(context: Context) {
    try {
        context.startActivity(
            Intent(Settings.ACTION_MANAGE_OVERLAY_PERMISSION, Uri.parse("package:${context.packageName}"))
                .addFlags(Intent.FLAG_ACTIVITY_NEW_TASK)
        )
    } catch (_: Exception) {
        Toast.makeText(context, "Buka Setelan > Aplikasi > Aoto > Tampil di atas aplikasi lain", Toast.LENGTH_LONG).show()
    }
}
