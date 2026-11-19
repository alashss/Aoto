package com.jimz.jimztune.ui.player

import androidx.compose.foundation.background
import androidx.compose.foundation.clickable
import androidx.compose.foundation.gestures.detectTapGestures
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.aspectRatio
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.navigationBarsPadding
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.layout.statusBarsPadding
import androidx.compose.foundation.layout.width
import androidx.compose.foundation.lazy.LazyColumn
import androidx.compose.foundation.lazy.rememberLazyListState
import androidx.compose.foundation.lazy.itemsIndexed
import androidx.compose.foundation.shape.CircleShape
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.rounded.KeyboardArrowDown
import androidx.compose.material.icons.rounded.MusicNote
import androidx.compose.material.icons.rounded.Pause
import androidx.compose.material.icons.rounded.PlayArrow
import androidx.compose.material.icons.rounded.Repeat
import androidx.compose.material.icons.rounded.RepeatOne
import androidx.compose.material.icons.rounded.Shuffle
import androidx.compose.material.icons.rounded.SkipNext
import androidx.compose.material.icons.rounded.SkipPrevious
import androidx.compose.material3.CircularProgressIndicator
import androidx.compose.material3.Icon
import androidx.compose.material.icons.rounded.KeyboardArrowUp
import androidx.compose.material.icons.rounded.Close
import androidx.compose.runtime.LaunchedEffect
import androidx.compose.material3.IconButton
import androidx.compose.material3.LocalContentColor
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.Slider
import androidx.compose.material3.SliderDefaults
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.runtime.CompositionLocalProvider
import androidx.compose.runtime.DisposableEffect
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.setValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.BlurredEdgeTreatment
import androidx.compose.ui.draw.blur
import androidx.compose.ui.draw.clip
import androidx.compose.ui.draw.clipToBounds
import androidx.compose.ui.draw.shadow
import androidx.compose.ui.graphics.Brush
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.input.pointer.pointerInput
import androidx.compose.ui.layout.ContentScale
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.text.style.TextOverflow
import androidx.compose.ui.unit.dp
import androidx.media3.common.Player
import coil.compose.AsyncImage
import com.jimz.jimztune.data.lyrics.LyricsState
import com.jimz.jimztune.data.lyrics.TranslationState
import com.jimz.jimztune.player.PlayerUiState
import com.jimz.jimztune.player.formatMs
import com.jimz.jimztune.player.hiRes
import com.jimz.jimztune.ui.theme.JimzBackground
import com.jimz.jimztune.ui.theme.LocalCoverTheme
import androidx.compose.material.icons.rounded.Favorite
import androidx.compose.material.icons.rounded.FavoriteBorder
import com.jimz.jimztune.AppGraph
import com.jimz.jimztune.ui.components.SongMenuButton

private enum class PlayerPane(val label: String) {
    Cover("Cover"),
    Lyrics("Lirik"),
    Queue("Antrean")
}

@Composable
fun PlayerScreen(
    state: PlayerUiState,
    onClose: () -> Unit,
    onToggle: () -> Unit,
    onNext: () -> Unit,
    onPrevious: () -> Unit,
    onSeek: (Long) -> Unit,
    onShuffle: () -> Unit,
    onRepeat: () -> Unit,
    onSkipTo: (Int) -> Unit,
    lyrics: LyricsState,
    queueSourceName: String? = null,
    onRemoveFromQueue: (Int) -> Unit = {},
    onMoveInQueue: (Int, Int) -> Unit = { _, _ -> },
    onRetryLyrics: () -> Unit,
    onNextLyricsSource: () -> Unit = {},
    onPickLyricsSource: (Int) -> Unit = {},
    translation: TranslationState = TranslationState.Off,
    onLyricsVisible: (Boolean) -> Unit = {},
    onTranslationSettingsChanged: () -> Unit = {}
) {
    val song = state.current ?: return
    var pane by remember { mutableStateOf(PlayerPane.Cover) }
    var dragging by remember { mutableStateOf<Float?>(null) }

    // Terjemahan hanya diminta ke internet saat halaman lirik penuh terbuka (hemat permintaan).
    DisposableEffect(pane) {
        onLyricsVisible(pane == PlayerPane.Lyrics)
        onDispose { onLyricsVisible(false) }
    }

    val fraction = if (state.durationMs > 0) (state.positionMs.toFloat() / state.durationMs).coerceIn(0f, 1f) else 0f
    val sliderValue = dragging ?: fraction
    val shownPosition = dragging?.let { (it * state.durationMs).toLong() } ?: state.positionMs
    val white70 = Color.White.copy(alpha = 0.7f)
    val cover = LocalCoverTheme.current

    // Pemutar ada di luar Scaffold, jadi warna teks default harus ditentukan sendiri (kalau tidak, hitam).
    CompositionLocalProvider(LocalContentColor provides Color.White) {
        Box(
            modifier = Modifier
                .fillMaxSize()
                .background(JimzBackground)
                .clipToBounds()
                .pointerInput(Unit) { detectTapGestures { } }
        ) {
            // Latar sesuai pilihan di Setelan -> Tampilan: sampul diburamkan (bawaan), polos, atau cerah.
            val bgMode = com.jimz.jimztune.ui.theme.AppTheme.playerBg
            if (bgMode != com.jimz.jimztune.ui.theme.PlayerBg.SOLID) {
                AsyncImage(
                    model = song.thumbnailUrl,
                    contentDescription = null,
                    contentScale = ContentScale.Crop,
                    modifier = Modifier
                        .fillMaxSize()
                        .blur(64.dp, BlurredEdgeTreatment.Unbounded)
                )
            }
            val a1: Float
            val a2: Float
            val a3: Float
            when (bgMode) {
                com.jimz.jimztune.ui.theme.PlayerBg.SOLID -> { a1 = 1f; a2 = 1f; a3 = 1f }
                com.jimz.jimztune.ui.theme.PlayerBg.VIVID -> { a1 = 0.5f; a2 = 0.62f; a3 = 0.85f }
                else -> { a1 = 0.72f; a2 = 0.84f; a3 = 0.96f }
            }
            Box(
                modifier = Modifier
                    .fillMaxSize()
                    .background(
                        if (bgMode == com.jimz.jimztune.ui.theme.PlayerBg.SOLID) {
                            Brush.verticalGradient(
                                0f to com.jimz.jimztune.ui.theme.JimzSurfaceHigh,
                                1f to JimzBackground
                            )
                        } else {
                            Brush.verticalGradient(
                                0f to cover.top.copy(alpha = a1),
                                0.5f to cover.mid.copy(alpha = a2),
                                1f to cover.bottom.copy(alpha = a3)
                            )
                        }
                    )
            )

            Column(
                modifier = Modifier
                    .fillMaxSize()
                    .statusBarsPadding()
                    .navigationBarsPadding()
                    .padding(horizontal = 24.dp)
            ) {
                Row(
                    modifier = Modifier
                        .fillMaxWidth()
                        .padding(top = 4.dp),
                    verticalAlignment = Alignment.CenterVertically
                ) {
                    IconButton(onClick = onClose) {
                        Icon(Icons.Rounded.KeyboardArrowDown, contentDescription = "Tutup pemutar", modifier = Modifier.size(30.dp))
                    }
                    Row(modifier = Modifier.weight(1f), horizontalArrangement = Arrangement.Center) {
                        PlayerPane.entries.forEach { p ->
                            PaneTab(p.label, pane == p) { pane = p }
                        }
                    }
                    Spacer(Modifier.size(48.dp))
                }

                Box(
                    modifier = Modifier
                        .weight(1f)
                        .fillMaxWidth()
                        .padding(vertical = 8.dp),
                    contentAlignment = Alignment.Center
                ) {
                    when (pane) {
                        PlayerPane.Cover -> {
                            val shape = RoundedCornerShape(20.dp)
                            Box(
                                modifier = Modifier
                                    .fillMaxWidth()
                                    .aspectRatio(1f)
                                    .shadow(24.dp, shape)
                                    .clip(shape)
                                    .background(MaterialTheme.colorScheme.surfaceVariant)
                            ) {
                                AsyncImage(
                                    model = song.thumbnailUrl?.hiRes(),
                                    contentDescription = null,
                                    contentScale = ContentScale.Crop,
                                    modifier = Modifier.fillMaxSize()
                                )
                            }
                        }
                        PlayerPane.Lyrics -> LyricsPane(
                            state = lyrics,
                            positionMs = state.positionMs,
                            isPlaying = state.isPlaying,
                            onSeek = onSeek,
                            onRetry = onRetryLyrics,
                            onNextSource = onNextLyricsSource,
                            onPickSource = onPickLyricsSource,
                            songTitle = song.title,
                            songArtist = song.subtitle.substringBefore(" • ").trim(),
                            translation = translation,
                            onTranslationSettingsChanged = onTranslationSettingsChanged
                        )
                        PlayerPane.Queue -> QueuePane(
                            queue = state.queue,
                            queueIndex = state.queueIndex,
                            sourceName = queueSourceName,
                            onSkipTo = onSkipTo,
                            onRemove = onRemoveFromQueue,
                            onMove = onMoveInQueue
                        )
                    }
                }

                // Satu baris lirik yang sedang dinyanyikan, tepat di atas judul (hanya di tab Cover).
                if (pane == PlayerPane.Cover && com.jimz.jimztune.AppGraph.lyricsPrefs.preview != 0) {
                    MiniLyricLine(state = lyrics, positionMs = state.positionMs, onClick = { pane = PlayerPane.Lyrics })
                }
                Row(verticalAlignment = Alignment.CenterVertically) {
                    Column(modifier = Modifier.weight(1f)) {
                        Text(
                            song.title,
                            style = MaterialTheme.typography.headlineSmall,
                            fontWeight = FontWeight.Bold,
                            maxLines = 1,
                            overflow = TextOverflow.Ellipsis
                        )
                        Spacer(Modifier.height(2.dp))
                        Text(
                            song.subtitle.substringBefore(" • "),
                            style = MaterialTheme.typography.bodyLarge,
                            color = white70,
                            maxLines = 1,
                            overflow = TextOverflow.Ellipsis
                        )
                    }
                    val favorite = AppGraph.library.isFavorite(song.id)
                    IconButton(onClick = { AppGraph.library.toggleFavorite(song) }) {
                        Icon(
                            imageVector = if (favorite) Icons.Rounded.Favorite else Icons.Rounded.FavoriteBorder,
                            contentDescription = if (favorite) "Hapus dari favorit" else "Tambah ke favorit",
                            tint = if (favorite) MaterialTheme.colorScheme.tertiary else Color.White
                        )
                    }
                    SongMenuButton(song, tint = Color.White, showPlayerControls = true)
                }

                if (state.error != null && !state.isPlaying && !state.isBuffering) {
                    Spacer(Modifier.height(6.dp))
                    Text(
                        "Lagu belum bisa diputar. Ketuk tombol putar untuk mencoba lagi.",
                        style = MaterialTheme.typography.labelSmall,
                        color = white70
                    )
                }

                Spacer(Modifier.height(8.dp))
                Slider(
                    value = sliderValue,
                    onValueChange = { dragging = it },
                    onValueChangeFinished = {
                        dragging?.let { onSeek((it * state.durationMs).toLong()) }
                        dragging = null
                    },
                    colors = SliderDefaults.colors(
                        thumbColor = Color.White,
                        activeTrackColor = Color.White,
                        inactiveTrackColor = Color.White.copy(alpha = 0.25f)
                    )
                )
                Row(modifier = Modifier.fillMaxWidth(), horizontalArrangement = Arrangement.SpaceBetween) {
                    Text(formatMs(shownPosition), style = MaterialTheme.typography.labelMedium, color = white70)
                    Text(formatMs(state.durationMs), style = MaterialTheme.typography.labelMedium, color = white70)
                }

                if (com.jimz.jimztune.AppGraph.remote.featureOn("player_tools")) {
                    SleepTimerButton(modifier = Modifier.padding(top = 10.dp), trailing = { AudioFxButton() })
                }

                Row(
                    modifier = Modifier
                        .fillMaxWidth()
                        .padding(top = 8.dp, bottom = 20.dp),
                    horizontalArrangement = Arrangement.SpaceEvenly,
                    verticalAlignment = Alignment.CenterVertically
                ) {
                    IconButton(onClick = onShuffle) {
                        Icon(
                            Icons.Rounded.Shuffle,
                            contentDescription = "Acak",
                            tint = if (state.shuffle) Color.White else Color.White.copy(alpha = 0.45f)
                        )
                    }
                    IconButton(onClick = onPrevious, modifier = Modifier.size(52.dp)) {
                        Icon(Icons.Rounded.SkipPrevious, contentDescription = "Sebelumnya", modifier = Modifier.size(36.dp))
                    }
                    Box(
                        modifier = Modifier
                            .size(68.dp)
                            .clip(CircleShape)
                            .background(Color.White)
                            .clickable(onClick = onToggle),
                        contentAlignment = Alignment.Center
                    ) {
                        if (state.isBuffering && !state.isPlaying) {
                            CircularProgressIndicator(color = Color.Black, strokeWidth = 3.dp, modifier = Modifier.size(30.dp))
                        } else {
                            Icon(
                                imageVector = if (state.isPlaying) Icons.Rounded.Pause else Icons.Rounded.PlayArrow,
                                contentDescription = if (state.isPlaying) "Jeda" else "Putar",
                                tint = Color.Black,
                                modifier = Modifier.size(38.dp)
                            )
                        }
                    }
                    IconButton(onClick = onNext, modifier = Modifier.size(52.dp)) {
                        Icon(Icons.Rounded.SkipNext, contentDescription = "Berikutnya", modifier = Modifier.size(36.dp))
                    }
                    IconButton(onClick = onRepeat) {
                        Icon(
                            imageVector = if (state.repeat == Player.REPEAT_MODE_ONE) Icons.Rounded.RepeatOne else Icons.Rounded.Repeat,
                            contentDescription = "Ulangi",
                            tint = if (state.repeat == Player.REPEAT_MODE_OFF) Color.White.copy(alpha = 0.45f) else Color.White
                        )
                    }
                }
            }
        }
    }
}

@Composable
private fun PaneTab(label: String, selected: Boolean, onClick: () -> Unit) {
    Column(
        modifier = Modifier
            .clip(RoundedCornerShape(12.dp))
            .clickable(onClick = onClick)
            .padding(horizontal = 14.dp, vertical = 8.dp),
        horizontalAlignment = Alignment.CenterHorizontally
    ) {
        Text(
            label,
            style = MaterialTheme.typography.titleSmall,
            fontWeight = if (selected) FontWeight.Bold else FontWeight.Medium,
            color = Color.White.copy(alpha = if (selected) 1f else 0.55f)
        )
        Spacer(Modifier.height(4.dp))
        Box(
            modifier = Modifier
                .width(16.dp)
                .height(2.dp)
                .clip(CircleShape)
                .background(if (selected) Color.White else Color.Transparent)
        )
    }
}
