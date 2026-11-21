package com.jimz.jimztune.ui.components

import androidx.compose.foundation.Image
import androidx.compose.foundation.horizontalScroll
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.heightIn
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.width
import androidx.compose.foundation.rememberScrollState
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.foundation.verticalScroll
import androidx.compose.material3.Button
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.OutlinedButton
import androidx.compose.material3.Text
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
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.graphics.asImageBitmap
import androidx.compose.ui.graphics.toArgb
import androidx.compose.ui.platform.LocalContext
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.unit.dp
import com.jimz.jimztune.data.model.SearchItem
import com.jimz.jimztune.ui.theme.LocalCoverTheme
import com.jimz.jimztune.util.ShareImage
import com.jimz.jimztune.util.SongCard
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.delay
import kotlinx.coroutines.launch
import kotlinx.coroutines.withContext

/** Membagikan atau menyimpan kartu "Sedang kuputar" untuk sebuah lagu. */
@Composable
fun SongCardSheet(song: SearchItem, onDismiss: () -> Unit) {
    val context = LocalContext.current
    val accent = LocalCoverTheme.current.accent
    val scope = rememberCoroutineScope()
    val artist = song.subtitle.substringBefore(" • ").trim()
    var story by remember { mutableStateOf(false) }
    var watermark by remember { mutableStateOf(true) }
    var cover by remember { mutableStateOf<android.graphics.Bitmap?>(null) }
    var preview by remember { mutableStateOf<android.graphics.Bitmap?>(null) }
    var busy by remember { mutableStateOf(false) }

    LaunchedEffect(song.thumbnailUrl) {
        val url = song.thumbnailUrl ?: return@LaunchedEffect
        try {
            val req = coil.request.ImageRequest.Builder(context).data(url).allowHardware(false).build()
            val res = coil.Coil.imageLoader(context).execute(req)
            cover = (res.drawable as? android.graphics.drawable.BitmapDrawable)?.bitmap
        } catch (_: Exception) {
        }
    }
    LaunchedEffect(story, watermark, cover) {
        delay(100)
        preview = withContext(Dispatchers.Default) {
            SongCard.render(story, accent.toArgb(), song.title, artist, cover, watermark, scale = 0.4f)
        }
    }
    fun export(action: (android.graphics.Bitmap) -> Unit) {
        if (busy) return
        busy = true
        scope.launch {
            val full = withContext(Dispatchers.Default) {
                SongCard.render(story, accent.toArgb(), song.title, artist, cover, watermark)
            }
            busy = false
            action(full)
        }
    }

    JimzBottomSheet(onDismiss = onDismiss) {
        Column(
            Modifier
                .weight(1f, fill = false)
                .verticalScroll(rememberScrollState())
                .padding(horizontal = 24.dp)
        ) {
            Text("Bagikan kartu lagu", style = MaterialTheme.typography.titleLarge, fontWeight = FontWeight.Bold)
            Spacer(Modifier.height(12.dp))
            preview?.let { bmp ->
                Image(
                    bitmap = bmp.asImageBitmap(),
                    contentDescription = "Pratinjau kartu",
                    modifier = Modifier
                        .align(Alignment.CenterHorizontally)
                        .heightIn(max = 300.dp)
                        .clip(RoundedCornerShape(16.dp))
                )
                Spacer(Modifier.height(12.dp))
            }
            Row(Modifier.fillMaxWidth().horizontalScroll(rememberScrollState()), verticalAlignment = Alignment.CenterVertically) {
                Text("Format", style = MaterialTheme.typography.labelMedium, color = Color.White.copy(alpha = 0.55f), modifier = Modifier.width(56.dp))
                Row(horizontalArrangement = Arrangement.spacedBy(8.dp)) {
                    OptionChip("Kotak", !story, { story = false })
                    OptionChip("Story 9:16", story, { story = true })
                }
            }
            Spacer(Modifier.height(6.dp))
            Row(Modifier.fillMaxWidth().horizontalScroll(rememberScrollState()), verticalAlignment = Alignment.CenterVertically) {
                Text("Tanda", style = MaterialTheme.typography.labelMedium, color = Color.White.copy(alpha = 0.55f), modifier = Modifier.width(56.dp))
                Row(horizontalArrangement = Arrangement.spacedBy(8.dp)) {
                    OptionChip("Logo Aoto", watermark, { watermark = true })
                    OptionChip("Tanpa", !watermark, { watermark = false })
                }
            }
            Spacer(Modifier.height(14.dp))
            Row(horizontalArrangement = Arrangement.spacedBy(10.dp), modifier = Modifier.fillMaxWidth()) {
                OutlinedButton(
                    onClick = {
                        export { bmp ->
                            val ok = ShareImage.saveToGallery(context, bmp, "kartu")
                            android.widget.Toast.makeText(
                                context,
                                if (ok) "Tersimpan di galeri (Pictures/Aoto)" else "Gagal menyimpan. Coba Bagikan.",
                                android.widget.Toast.LENGTH_SHORT
                            ).show()
                        }
                    },
                    enabled = preview != null && !busy,
                    modifier = Modifier.weight(1f)
                ) { Text(if (busy) "Memproses…" else "Simpan") }
                Button(
                    onClick = { export { bmp -> ShareImage.share(context, bmp, "kartu") } },
                    enabled = preview != null && !busy,
                    modifier = Modifier.weight(1f)
                ) { Text("Bagikan") }
            }
            Spacer(Modifier.height(20.dp))
        }
    }
}
