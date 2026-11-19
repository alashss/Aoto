package com.jimz.jimztune.ui.components

import android.net.Uri
import androidx.activity.compose.rememberLauncherForActivityResult
import androidx.activity.result.PickVisualMediaRequest
import androidx.activity.result.contract.ActivityResultContracts
import androidx.compose.foundation.background
import androidx.compose.foundation.border
import androidx.compose.foundation.clickable
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.fillMaxHeight
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.shape.CircleShape
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.foundation.text.KeyboardOptions
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.rounded.Check
import androidx.compose.material.icons.rounded.MusicNote
import androidx.compose.material3.Icon
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.OutlinedTextField
import androidx.compose.material3.OutlinedTextFieldDefaults
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableIntStateOf
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.setValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clip
import androidx.compose.ui.graphics.Brush
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.graphics.lerp
import androidx.compose.ui.layout.ContentScale
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.text.input.ImeAction
import androidx.compose.ui.text.input.KeyboardCapitalization
import androidx.compose.ui.unit.Dp
import androidx.compose.ui.unit.dp
import coil.compose.AsyncImage
import com.jimz.jimztune.data.library.Playlist
import com.jimz.jimztune.ui.theme.LocalCoverTheme
import java.io.File

/** Pilihan warna tema playlist (dipakai kalau playlist belum punya gambar). */
val PlaylistColors: List<Int> = listOf(
    0xFF6C63FF, 0xFF00C6FF, 0xFFFF6584, 0xFF43E97B, 0xFFFA8231, 0xFFA29BFE
).map { it.toInt() }

/**
 * Gambar sampul playlist, urutan prioritas:
 * 1. gambar pilihan pengguna ([model]),
 * 2. kolase 2x2 dari cover empat lagu di dalamnya,
 * 3. cover lagu pertama,
 * 4. gradasi warna tema + ikon nada (playlist kosong).
 */
@Composable
fun PlaylistArt(
    model: Any?,
    thumbs: List<String>,
    colorArgb: Int,
    size: Dp,
    modifier: Modifier = Modifier,
    radius: Dp = 12.dp
) {
    val base = if (colorArgb == 0) LocalCoverTheme.current.accent else Color(colorArgb)
    Box(
        modifier = modifier
            .size(size)
            .clip(RoundedCornerShape(radius))
            .background(Brush.linearGradient(listOf(base.copy(alpha = 0.9f), lerp(base, Color(0xFF0E1230), 0.6f)))),
        contentAlignment = Alignment.Center
    ) {
        when {
            model != null -> AsyncImage(
                model = model,
                contentDescription = null,
                contentScale = ContentScale.Crop,
                modifier = Modifier.fillMaxSize()
            )
            thumbs.size >= 4 -> Column(modifier = Modifier.fillMaxSize()) {
                for (r in 0..1) {
                    Row(modifier = Modifier.weight(1f)) {
                        for (c in 0..1) {
                            AsyncImage(
                                model = thumbs[r * 2 + c],
                                contentDescription = null,
                                contentScale = ContentScale.Crop,
                                modifier = Modifier
                                    .weight(1f)
                                    .fillMaxHeight()
                            )
                        }
                    }
                }
            }
            thumbs.isNotEmpty() -> AsyncImage(
                model = thumbs[0],
                contentDescription = null,
                contentScale = ContentScale.Crop,
                modifier = Modifier.fillMaxSize()
            )
            else -> Icon(
                Icons.Rounded.MusicNote,
                contentDescription = null,
                tint = Color.White.copy(alpha = 0.92f),
                modifier = Modifier.size(size * 0.45f)
            )
        }
    }
}

@Composable
fun PlaylistCover(playlist: Playlist, size: Dp, modifier: Modifier = Modifier, radius: Dp = 12.dp) {
    PlaylistArt(
        model = playlist.coverPath?.let { File(it) } ?: playlist.coverUrl,
        thumbs = playlist.songs.mapNotNull { it.thumbnailUrl }.distinct().take(4),
        colorArgb = playlist.color,
        size = size,
        modifier = modifier,
        radius = radius
    )
}

/**
 * Sheet untuk membuat atau mengedit playlist: nama, gambar sampul (dari galeri), dan warna tema.
 * [onConfirm] menerima gambar baru (kalau dipilih) dan penanda apakah gambar lama harus dihapus.
 */
@Composable
fun PlaylistEditorSheet(
    title: String,
    confirmLabel: String,
    onConfirm: (name: String, color: Int, newCover: Uri?, removeCover: Boolean) -> Unit,
    onDismiss: () -> Unit,
    initialName: String = "",
    initialColor: Int = 0,
    initialCoverPath: String? = null,
    thumbs: List<String> = emptyList()
) {
    var name by remember { mutableStateOf(initialName) }
    var color by remember { mutableIntStateOf(if (initialColor != 0) initialColor else PlaylistColors[0]) }
    var picked by remember { mutableStateOf<Uri?>(null) }
    var removeCover by remember { mutableStateOf(false) }

    val picker = rememberLauncherForActivityResult(ActivityResultContracts.PickVisualMedia()) { uri ->
        if (uri != null) {
            picked = uri
            removeCover = false
        }
    }
    val existing: File? = if (removeCover) null else initialCoverPath?.let { File(it) }
    val shownModel: Any? = picked ?: existing
    val accent = Color(color)

    JimzBottomSheet(onDismiss = onDismiss) {
        Column(
            modifier = Modifier
                .fillMaxWidth()
                .padding(horizontal = 24.dp),
            horizontalAlignment = Alignment.CenterHorizontally
        ) {
            Text(title, style = MaterialTheme.typography.titleLarge, fontWeight = FontWeight.Bold, color = Color.White)
            Spacer(Modifier.height(16.dp))
            PlaylistArt(model = shownModel, thumbs = thumbs, colorArgb = color, size = 116.dp, radius = 24.dp)
            Spacer(Modifier.height(14.dp))
            Row(horizontalArrangement = Arrangement.spacedBy(10.dp)) {
                PillTextButton(
                    text = if (shownModel != null) "Ganti gambar" else "Pilih gambar",
                    onClick = {
                        picker.launch(PickVisualMediaRequest(ActivityResultContracts.PickVisualMedia.ImageOnly))
                    },
                    primary = false
                )
                if (shownModel != null) {
                    PillTextButton(
                        text = "Hapus gambar",
                        onClick = {
                            picked = null
                            removeCover = true
                        },
                        primary = false
                    )
                }
            }
            Spacer(Modifier.height(18.dp))
            OutlinedTextField(
                value = name,
                onValueChange = { name = it.take(60) },
                label = { Text("Nama playlist", color = Color.White.copy(alpha = 0.6f)) },
                singleLine = true,
                keyboardOptions = KeyboardOptions(
                    capitalization = KeyboardCapitalization.Words,
                    imeAction = ImeAction.Done
                ),
                colors = OutlinedTextFieldDefaults.colors(
                    focusedBorderColor = accent,
                    unfocusedBorderColor = Color.White.copy(alpha = 0.20f),
                    focusedTextColor = Color.White,
                    unfocusedTextColor = Color.White,
                    cursorColor = accent
                ),
                shape = RoundedCornerShape(16.dp),
                modifier = Modifier.fillMaxWidth()
            )
            Spacer(Modifier.height(18.dp))
            Text(
                "Warna tema",
                style = MaterialTheme.typography.labelMedium,
                color = Color.White.copy(alpha = 0.65f),
                modifier = Modifier.fillMaxWidth()
            )
            Spacer(Modifier.height(10.dp))
            Row(
                modifier = Modifier.fillMaxWidth(),
                horizontalArrangement = Arrangement.SpaceBetween
            ) {
                PlaylistColors.forEach { c ->
                    val selected = c == color
                    Box(
                        modifier = Modifier
                            .size(38.dp)
                            .clip(CircleShape)
                            .background(Color(c))
                            .border(
                                width = if (selected) 2.5.dp else 0.dp,
                                color = Color.White.copy(alpha = if (selected) 0.9f else 0f),
                                shape = CircleShape
                            )
                            .clickable { color = c },
                        contentAlignment = Alignment.Center
                    ) {
                        if (selected) {
                            Icon(Icons.Rounded.Check, "Dipilih", tint = Color.White, modifier = Modifier.size(18.dp))
                        }
                    }
                }
            }
            Spacer(Modifier.height(24.dp))
            Row(
                modifier = Modifier.fillMaxWidth(),
                horizontalArrangement = Arrangement.spacedBy(10.dp)
            ) {
                PillTextButton("Batal", onDismiss, Modifier.weight(1f), primary = false)
                PillTextButton(
                    text = confirmLabel,
                    onClick = { onConfirm(name.trim(), color, picked, removeCover) },
                    modifier = Modifier.weight(2f),
                    accent = accent,
                    enabled = name.isNotBlank()
                )
            }
        }
    }
}
