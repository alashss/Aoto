package com.jimz.jimztune.ui.player

import android.app.Activity
import android.content.pm.ActivityInfo
import androidx.activity.compose.BackHandler
import androidx.compose.animation.AnimatedVisibility
import androidx.compose.animation.core.tween
import androidx.compose.animation.fadeIn
import androidx.compose.animation.fadeOut
import androidx.compose.foundation.background
import androidx.compose.foundation.clickable
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.aspectRatio
import androidx.compose.foundation.layout.fillMaxHeight
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.navigationBarsPadding
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.layout.statusBarsPadding
import androidx.compose.foundation.layout.width
import androidx.compose.foundation.shape.CircleShape
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.rounded.Close
import androidx.compose.material.icons.rounded.Pause
import androidx.compose.material.icons.rounded.PlayArrow
import androidx.compose.material.icons.rounded.SkipNext
import androidx.compose.material.icons.rounded.SkipPrevious
import androidx.compose.material3.Icon
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.runtime.DisposableEffect
import androidx.compose.runtime.LaunchedEffect
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.setValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clip
import androidx.compose.ui.draw.drawBehind
import androidx.compose.ui.geometry.Offset
import androidx.compose.ui.graphics.Brush
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.input.pointer.pointerInput
import androidx.compose.foundation.gestures.detectTapGestures
import androidx.compose.ui.layout.ContentScale
import androidx.compose.ui.platform.LocalContext
import androidx.compose.ui.platform.LocalView
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.text.style.TextOverflow
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import coil.compose.AsyncImage
import com.jimz.jimztune.player.PlayerViewModel
import com.jimz.jimztune.ui.theme.LocalCoverTheme

/** Saklar global Mode Lirik: lirik layar penuh, bisa potret atau lanskap. */
object LyricsMode {
    var active by mutableStateOf(false)
    var landscape by mutableStateOf(false)
}

@Composable
fun LyricsModeHost(player: PlayerViewModel) {
    AnimatedVisibility(
        visible = LyricsMode.active && player.ui.current != null,
        enter = fadeIn(tween(280)),
        exit = fadeOut(tween(220))
    ) { LyricsModeScreen(player) }
}

@Composable
private fun LyricsModeScreen(player: PlayerViewModel) {
    val ui = player.ui
    val song = ui.current ?: return
    val cover = LocalCoverTheme.current
    val activity = LocalContext.current as? Activity
    val view = LocalView.current
    BackHandler { LyricsMode.active = false }
    DisposableEffect(Unit) {
        view.keepScreenOn = true
        onDispose { view.keepScreenOn = false }
    }
    LaunchedEffect(song.id) { player.setLyricsVisible(true) }
    DisposableEffect(Unit) { onDispose { player.setLyricsVisible(false) } }
    // Putar layar sesuai pilihan; saat keluar kembalikan ke bebas.
    DisposableEffect(LyricsMode.landscape) {
        activity?.requestedOrientation =
            if (LyricsMode.landscape) ActivityInfo.SCREEN_ORIENTATION_SENSOR_LANDSCAPE else ActivityInfo.SCREEN_ORIENTATION_PORTRAIT
        onDispose { }
    }
    DisposableEffect(Unit) { onDispose { activity?.requestedOrientation = ActivityInfo.SCREEN_ORIENTATION_UNSPECIFIED } }

    val accent = cover.accent
    val land = LyricsMode.landscape
    Box(
        Modifier.fillMaxSize().background(Color(0xFF0A0A0E)).pointerInput(Unit) { detectTapGestures { } }.drawBehind {
            drawCircle(Brush.radialGradient(listOf(accent.copy(alpha = 0.45f), Color.Transparent), Offset(size.width * 0.2f, size.height * 0.15f), size.width * 0.9f), size.width * 0.9f, Offset(size.width * 0.2f, size.height * 0.15f))
            drawCircle(Brush.radialGradient(listOf(Color(0xFF6A3DFF).copy(alpha = 0.28f), Color.Transparent), Offset(size.width * 0.9f, size.height * 0.85f), size.width * 0.8f), size.width * 0.8f, Offset(size.width * 0.9f, size.height * 0.85f))
        }
    ) {
        if (!land) {
            Column(Modifier.fillMaxSize().statusBarsPadding().navigationBarsPadding()) {
                TopRow(song.title, song.subtitle, Modifier.padding(horizontal = 14.dp, vertical = 8.dp))
                LyricsFeed(player, Modifier.weight(1f).fillMaxWidth().padding(horizontal = 22.dp), 1.2f)
                Controls(player, Modifier.fillMaxWidth().padding(horizontal = 28.dp, vertical = 14.dp))
            }
        } else {
            Row(Modifier.fillMaxSize().statusBarsPadding().navigationBarsPadding().padding(horizontal = 20.dp, vertical = 10.dp)) {
                Column(Modifier.width(250.dp).fillMaxHeight(), verticalArrangement = Arrangement.Center, horizontalAlignment = Alignment.CenterHorizontally) {
                    AsyncImage(song.thumbnailUrl, null, Modifier.fillMaxWidth().aspectRatio(1f).clip(RoundedCornerShape(20.dp)), contentScale = ContentScale.Crop)
                    Text(song.title, color = Color.White, fontWeight = FontWeight.Bold, fontSize = 17.sp, maxLines = 1, overflow = TextOverflow.Ellipsis, modifier = Modifier.padding(top = 14.dp))
                    Text(song.subtitle, color = Color.White.copy(alpha = 0.6f), fontSize = 13.sp, maxLines = 1, overflow = TextOverflow.Ellipsis)
                    Controls(player, Modifier.fillMaxWidth().padding(top = 10.dp))
                }
                Spacer(Modifier.width(24.dp))
                Box(Modifier.weight(1f).fillMaxHeight()) {
                    LyricsFeed(player, Modifier.fillMaxSize(), 1.35f)
                    Row(Modifier.align(Alignment.TopEnd)) { TopButtons() }
                }
            }
        }
    }
}

@Composable
private fun TopButtons() {
    Box(Modifier.size(40.dp).clip(CircleShape).background(Color.White.copy(alpha = 0.12f)).clickable { LyricsMode.landscape = !LyricsMode.landscape }, Alignment.Center) {
        Text(if (LyricsMode.landscape) "⤢" else "⟳", color = Color.White, fontSize = 20.sp)
    }
    Spacer(Modifier.width(8.dp))
    RoundIcon(Icons.Rounded.Close, "Tutup") { LyricsMode.active = false }
}

@Composable
private fun TopRow(title: String, artist: String, modifier: Modifier) {
    Row(modifier.fillMaxWidth(), verticalAlignment = Alignment.CenterVertically) {
        Column(Modifier.weight(1f)) {
            Text(title, color = Color.White, fontWeight = FontWeight.Bold, fontSize = 16.sp, maxLines = 1, overflow = TextOverflow.Ellipsis)
            Text(artist, color = Color.White.copy(alpha = 0.6f), fontSize = 12.sp, maxLines = 1, overflow = TextOverflow.Ellipsis)
        }
        TopButtons()
    }
}

@Composable
private fun RoundIcon(icon: androidx.compose.ui.graphics.vector.ImageVector, desc: String, onClick: () -> Unit) {
    Box(Modifier.size(40.dp).clip(CircleShape).background(Color.White.copy(alpha = 0.12f)).clickable(onClick = onClick), Alignment.Center) {
        Icon(icon, desc, tint = Color.White, modifier = Modifier.size(22.dp))
    }
}

@Composable
private fun Controls(player: PlayerViewModel, modifier: Modifier) {
    val ui = player.ui
    Row(modifier, horizontalArrangement = Arrangement.SpaceEvenly, verticalAlignment = Alignment.CenterVertically) {
        Icon(Icons.Rounded.SkipPrevious, "Sebelumnya", tint = Color.White, modifier = Modifier.size(38.dp).clip(CircleShape).clickable { player.previous() })
        Box(Modifier.size(58.dp).clip(CircleShape).background(Color.White).clickable { player.togglePlay() }, Alignment.Center) {
            Icon(if (ui.isPlaying) Icons.Rounded.Pause else Icons.Rounded.PlayArrow, "Putar/jeda", tint = Color.Black, modifier = Modifier.size(34.dp))
        }
        Icon(Icons.Rounded.SkipNext, "Berikutnya", tint = Color.White, modifier = Modifier.size(38.dp).clip(CircleShape).clickable { player.next() })
    }
}
