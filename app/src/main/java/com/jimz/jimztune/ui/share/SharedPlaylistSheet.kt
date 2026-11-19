package com.jimz.jimztune.ui.share

import android.widget.Toast
import androidx.compose.foundation.background
import androidx.compose.foundation.clickable
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.heightIn
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.layout.width
import androidx.compose.foundation.lazy.LazyColumn
import androidx.compose.foundation.lazy.itemsIndexed
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.rounded.GraphicEq
import androidx.compose.material.icons.rounded.PlayArrow
import androidx.compose.material.icons.rounded.Save
import androidx.compose.material3.Icon
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.runtime.LaunchedEffect
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.setValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clip
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.layout.ContentScale
import androidx.compose.ui.platform.LocalContext
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.text.style.TextOverflow
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import coil.compose.AsyncImage
import com.jimz.jimztune.AppGraph
import com.jimz.jimztune.data.share.ShareLinks
import com.jimz.jimztune.data.share.SharedPlaylist
import com.jimz.jimztune.player.PlayerViewModel
import com.jimz.jimztune.ui.auth.ProfileAvatar
import com.jimz.jimztune.ui.components.JimzBottomSheet
import com.jimz.jimztune.ui.components.PillTextButton
import com.jimz.jimztune.ui.components.SheetTitle
import com.jimz.jimztune.ui.theme.LocalCoverTheme

/** Muncul saat tautan bagikan dibuka: isi playlist/lagu, siapa pembuatnya, lalu putar atau simpan ke koleksi. */
@Composable
fun SharedPlaylistHost(player: PlayerViewModel) {
    val code = ShareLinks.pending ?: return
    val context = LocalContext.current
    var data by remember(code) { mutableStateOf<SharedPlaylist?>(null) }
    var failed by remember(code) { mutableStateOf(false) }
    LaunchedEffect(code) {
        val r = ShareLinks.fetch(code)
        data = r
        failed = r == null
    }
    JimzBottomSheet(onDismiss = { ShareLinks.pending = null }) {
        val d = data
        when {
            failed -> {
                SheetTitle(Icons.Rounded.GraphicEq, "Tautan tidak ditemukan", Modifier.padding(horizontal = 24.dp), "Tautannya salah, sudah dihapus, atau kamu sedang offline.")
                Spacer(Modifier.height(24.dp))
            }
            d == null -> {
                SheetTitle(Icons.Rounded.GraphicEq, "Memuat…", Modifier.padding(horizontal = 24.dp))
                Spacer(Modifier.height(32.dp))
            }
            else -> {
                val accent = LocalCoverTheme.current.accent
                Row(
                    Modifier.fillMaxWidth().padding(horizontal = 24.dp, vertical = 4.dp),
                    verticalAlignment = Alignment.CenterVertically
                ) {
                    AsyncImage(
                        model = d.songs.firstOrNull()?.thumbnailUrl,
                        contentDescription = null,
                        contentScale = ContentScale.Crop,
                        modifier = Modifier.size(84.dp).clip(RoundedCornerShape(18.dp)).background(Color.White.copy(alpha = 0.08f))
                    )
                    Spacer(Modifier.width(16.dp))
                    Column(Modifier.weight(1f)) {
                        Text(d.title, style = MaterialTheme.typography.titleLarge, fontWeight = FontWeight.Bold, maxLines = 2, overflow = TextOverflow.Ellipsis)
                        Text(
                            if (d.songs.size == 1) "1 lagu" else "${d.songs.size} lagu",
                            style = MaterialTheme.typography.bodyMedium, color = Color.White.copy(alpha = 0.6f)
                        )
                    }
                }
                // Pembuat
                Row(
                    Modifier
                        .padding(horizontal = 24.dp, vertical = 10.dp)
                        .fillMaxWidth()
                        .clip(RoundedCornerShape(16.dp))
                        .background(Color.White.copy(alpha = 0.06f))
                        .padding(12.dp),
                    verticalAlignment = Alignment.CenterVertically
                ) {
                    ProfileAvatar(url = d.creator.avatarUrl, name = d.creator.name, size = 40.dp, accent = accent)
                    Spacer(Modifier.width(12.dp))
                    Column(Modifier.weight(1f)) {
                        Text("Dibuat oleh", fontSize = 11.sp, color = Color.White.copy(alpha = 0.55f))
                        Text(d.creator.name, fontWeight = FontWeight.Bold, maxLines = 1, overflow = TextOverflow.Ellipsis)
                        if (d.creator.username != null) Text("@${d.creator.username}", fontSize = 12.sp, color = Color.White.copy(alpha = 0.6f))
                    }
                    Text("Lvl. ${d.creator.level}", fontWeight = FontWeight.Bold, color = accent)
                }
                Row(Modifier.padding(horizontal = 24.dp), horizontalArrangement = Arrangement.spacedBy(10.dp)) {
                    PillTextButton("Putar", onClick = {
                        player.play(d.songs, 0)
                        ShareLinks.pending = null
                    }, modifier = Modifier.weight(1f))
                    PillTextButton("Simpan ke koleksi", onClick = {
                        if (d.songs.size == 1) {
                            if (!AppGraph.library.isFavorite(d.songs.first().id)) AppGraph.library.toggleFavorite(d.songs.first())
                            Toast.makeText(context, "Ditambahkan ke favorit", Toast.LENGTH_SHORT).show()
                        } else {
                            AppGraph.library.importPlaylist(d.title, d.songs)
                            Toast.makeText(context, "Disimpan ke koleksimu", Toast.LENGTH_SHORT).show()
                        }
                        ShareLinks.pending = null
                    }, modifier = Modifier.weight(1f), primary = false)
                }
                Spacer(Modifier.height(10.dp))
                LazyColumn(Modifier.heightIn(max = 340.dp)) {
                    itemsIndexed(d.songs) { i, s ->
                        Row(
                            Modifier
                                .fillMaxWidth()
                                .clickable {
                                    player.play(d.songs, i)
                                    ShareLinks.pending = null
                                }
                                .padding(horizontal = 24.dp, vertical = 8.dp),
                            verticalAlignment = Alignment.CenterVertically
                        ) {
                            Text("${i + 1}", modifier = Modifier.width(28.dp), color = Color.White.copy(alpha = 0.45f), fontSize = 13.sp)
                            AsyncImage(
                                model = s.thumbnailUrl, contentDescription = null, contentScale = ContentScale.Crop,
                                modifier = Modifier.size(44.dp).clip(RoundedCornerShape(10.dp)).background(Color.White.copy(alpha = 0.08f))
                            )
                            Spacer(Modifier.width(12.dp))
                            Column(Modifier.weight(1f)) {
                                Text(s.title, fontWeight = FontWeight.Medium, maxLines = 1, overflow = TextOverflow.Ellipsis)
                                Text(s.subtitle.substringBefore(" • "), fontSize = 12.sp, color = Color.White.copy(alpha = 0.55f), maxLines = 1, overflow = TextOverflow.Ellipsis)
                            }
                            Icon(Icons.Rounded.PlayArrow, contentDescription = null, tint = Color.White.copy(alpha = 0.4f))
                        }
                    }
                }
                Spacer(Modifier.height(16.dp))
            }
        }
    }
}
