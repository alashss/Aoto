package com.jimz.jimztune.ui.components

import android.widget.Toast
import androidx.compose.foundation.background
import androidx.compose.foundation.border
import androidx.compose.foundation.clickable
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.layout.width
import androidx.compose.foundation.shape.CircleShape
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.rounded.Close
import androidx.compose.material.icons.rounded.Download
import androidx.compose.material.icons.rounded.DownloadDone
import androidx.compose.material3.CircularProgressIndicator
import androidx.compose.material3.Icon
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clip
import androidx.compose.ui.graphics.Brush
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.graphics.lerp
import androidx.compose.ui.graphics.vector.ImageVector
import androidx.compose.ui.platform.LocalContext
import androidx.compose.ui.semantics.Role
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import com.jimz.jimztune.AppGraph
import com.jimz.jimztune.data.model.SearchItem
import com.jimz.jimztune.ui.theme.LocalCoverTheme

private val CompactInk = Color(0xFF0B1030)

/** Tombol kapsul kecil (tinggi 38 dp) untuk barisan aksi playlist. Utama = gradasi aksen, sekunder = kaca. */
@Composable
fun CompactPillButton(
    text: String,
    icon: ImageVector,
    onClick: () -> Unit,
    modifier: Modifier = Modifier,
    primary: Boolean = false
) {
    val accent = LocalCoverTheme.current.accent
    val bg = if (primary) {
        Modifier.background(Brush.horizontalGradient(listOf(accent, lerp(accent, Color.White, 0.38f))))
    } else {
        Modifier
            .background(Color.White.copy(alpha = 0.09f))
            .border(1.dp, Color.White.copy(alpha = 0.14f), CircleShape)
    }
    val fg = if (primary) CompactInk else Color.White
    Row(
        modifier = modifier
            .height(38.dp)
            .clip(CircleShape)
            .then(bg)
            .clickable(role = Role.Button, onClick = onClick)
            .padding(horizontal = 14.dp),
        verticalAlignment = Alignment.CenterVertically
    ) {
        Icon(icon, contentDescription = null, tint = fg, modifier = Modifier.size(18.dp))
        Spacer(Modifier.width(6.dp))
        Text(text, color = fg, fontSize = 13.sp, fontWeight = FontWeight.SemiBold, maxLines = 1)
    }
}

/** Tombol ikon bulat kecil (38 dp). [active] = menyala dengan warna [tint] (mis. hati yang sudah ditekan). */
@Composable
fun CompactIconButton(
    icon: ImageVector,
    contentDescription: String,
    onClick: () -> Unit,
    modifier: Modifier = Modifier,
    tint: Color = Color.White,
    active: Boolean = false
) {
    Box(
        modifier = modifier
            .size(38.dp)
            .clip(CircleShape)
            .background(if (active) tint.copy(alpha = 0.16f) else Color.White.copy(alpha = 0.09f))
            .border(1.dp, if (active) tint.copy(alpha = 0.45f) else Color.White.copy(alpha = 0.12f), CircleShape)
            .clickable(role = Role.Button, onClick = onClick),
        contentAlignment = Alignment.Center
    ) {
        Icon(icon, contentDescription = contentDescription, tint = tint, modifier = Modifier.size(19.dp))
    }
}

/**
 * Pengganti tombol lebar "Unduh semua": satu ikon bulat. Menampilkan unduh (belum), cincin kemajuan
 * (sedang berjalan, ketuk untuk membatalkan sisanya), atau centang (semua sudah offline).
 */
@Composable
fun DownloadAllIconButton(songs: List<SearchItem>, modifier: Modifier = Modifier) {
    val downloads = AppGraph.downloads
    val context = LocalContext.current
    val accent = LocalCoverTheme.current.accent
    val unique = songs.distinctBy { it.id }
    if (unique.isEmpty()) return

    val total = unique.size
    val done = unique.count { downloads.isDownloaded(it.id) }
    val running = unique.count { val a = downloads.active[it.id]; a != null && a.error == null }
    val remaining = total - done

    when {
        remaining == 0 -> CompactIconButton(
            Icons.Rounded.DownloadDone, "Semua terunduh", modifier = modifier, tint = accent, active = true,
            onClick = { Toast.makeText(context, "Semua $total lagu sudah tersimpan untuk offline", Toast.LENGTH_SHORT).show() }
        )
        running > 0 -> Box(
            modifier = modifier
                .size(38.dp)
                .clip(CircleShape)
                .background(Color.White.copy(alpha = 0.09f))
                .border(1.dp, accent.copy(alpha = 0.45f), CircleShape)
                .clickable(role = Role.Button) {
                    downloads.cancelAll(unique)
                    Toast.makeText(context, "Unduhan dibatalkan", Toast.LENGTH_SHORT).show()
                },
            contentAlignment = Alignment.Center
        ) {
            CircularProgressIndicator(
                progress = { (done.toFloat() / total).coerceIn(0.04f, 1f) },
                color = accent,
                trackColor = Color.White.copy(alpha = 0.12f),
                strokeWidth = 2.dp,
                modifier = Modifier.size(26.dp)
            )
            Icon(Icons.Rounded.Close, contentDescription = "Batalkan unduhan", tint = Color.White, modifier = Modifier.size(12.dp))
        }
        else -> CompactIconButton(
            Icons.Rounded.Download,
            if (done == 0) "Unduh semua ($total)" else "Unduh sisanya ($remaining)",
            modifier = modifier,
            onClick = {
                val queued = downloads.downloadAll(unique)
                Toast.makeText(context, "Mengunduh $queued lagu…", Toast.LENGTH_SHORT).show()
            }
        )
    }
}
