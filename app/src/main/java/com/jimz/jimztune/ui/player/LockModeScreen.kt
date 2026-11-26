package com.jimz.jimztune.ui.player

import androidx.activity.compose.BackHandler
import androidx.compose.animation.AnimatedVisibility
import androidx.compose.animation.core.tween
import androidx.compose.animation.fadeIn
import androidx.compose.animation.fadeOut
import androidx.compose.animation.slideOutVertically
import androidx.compose.animation.AnimatedContent
import androidx.compose.animation.core.FastOutSlowInEasing
import androidx.compose.animation.core.LinearEasing
import androidx.compose.animation.core.RepeatMode
import androidx.compose.animation.core.Spring
import androidx.compose.animation.core.animateFloat
import androidx.compose.animation.core.animateFloatAsState
import androidx.compose.animation.core.infiniteRepeatable
import androidx.compose.animation.core.rememberInfiniteTransition
import androidx.compose.animation.core.spring
import androidx.compose.animation.scaleIn
import androidx.compose.animation.scaleOut
import androidx.compose.animation.togetherWith
import androidx.compose.foundation.Canvas
import androidx.compose.foundation.interaction.MutableInteractionSource
import androidx.compose.foundation.interaction.collectIsPressedAsState
import androidx.compose.ui.geometry.Offset
import androidx.compose.ui.graphics.TransformOrigin
import androidx.compose.ui.graphics.graphicsLayer
import androidx.compose.ui.graphics.lerp
import androidx.compose.material.icons.rounded.KeyboardArrowUp
import androidx.compose.foundation.background
import androidx.compose.foundation.border
import androidx.compose.foundation.clickable
import androidx.compose.foundation.gestures.detectVerticalDragGestures
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.navigationBarsPadding
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.layout.statusBarsPadding
import androidx.compose.foundation.layout.width
import com.jimz.jimztune.ui.components.LiquidGlass
import com.jimz.jimztune.AppGraph
import androidx.compose.ui.draw.blur
import androidx.compose.ui.draw.drawWithContent
import androidx.compose.foundation.layout.PaddingValues
import androidx.compose.foundation.gestures.animateScrollBy
import androidx.compose.runtime.getValue
import androidx.compose.foundation.lazy.LazyColumn
import androidx.compose.foundation.lazy.itemsIndexed
import androidx.compose.foundation.lazy.rememberLazyListState
import androidx.compose.foundation.shape.CircleShape
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.rounded.Lock
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
import androidx.compose.runtime.remember
import androidx.compose.runtime.setValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clip
import androidx.compose.ui.graphics.Brush
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.graphics.compositeOver
import androidx.compose.ui.draw.drawBehind
import androidx.compose.ui.input.pointer.pointerInput
import androidx.compose.ui.layout.ContentScale
import androidx.compose.ui.platform.LocalView
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.text.style.TextAlign
import androidx.compose.ui.text.style.TextOverflow
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import coil.compose.AsyncImage
import com.jimz.jimztune.data.lyrics.LyricsState
import com.jimz.jimztune.player.PlayerViewModel
import com.jimz.jimztune.ui.theme.LocalCoverTheme
import kotlinx.coroutines.delay
import java.text.SimpleDateFormat
import androidx.compose.ui.platform.LocalContext
import java.util.Date
import java.util.Locale

/** Saklar global Mode Kunci Layar (dibuka dari panel lirik atau Pengaturan). */
object LockMode {
    var active by mutableStateOf(false)
}

private fun fmt(ms: Long): String {
    val s = (ms / 1000).coerceAtLeast(0)
    return "%d:%02d".format(s / 60, s % 60)
}

/**
 * Tampilan seperti layar kunci: jam besar, lirik berjalan, kartu pemutar dengan tombol sebelumnya / jeda / berikutnya.
 * Layar tetap menyala selama mode ini aktif. Geser ke atas (atau tombol kembali) untuk keluar.
 */
@Composable
fun LockModeHost(player: PlayerViewModel) {
    val act = LocalContext.current as? android.app.Activity
    LaunchedEffect(LockMode.active) {
        if (!LockMode.active && act != null) com.jimz.jimztune.util.LockScreenLyrics.leaveKeyguard(act, true)
    }
    AnimatedVisibility(
        visible = LockMode.active && player.ui.current != null,
        enter = fadeIn(tween(300)),
        exit = slideOutVertically(tween(300)) { -it } + fadeOut(tween(300))
    ) { LockModeScreen(player) }
}

@Composable
private fun LockModeScreen(player: PlayerViewModel) {
    val cover = LocalCoverTheme.current
    val ui = player.ui
    val song = ui.current ?: return
    val view = LocalView.current
    BackHandler { LockMode.active = false }
    DisposableEffect(Unit) {
        view.keepScreenOn = true
        onDispose { view.keepScreenOn = false }
    }
    LaunchedEffect(song.id) { player.setLyricsVisible(true) }
    DisposableEffect(Unit) { onDispose { player.setLyricsVisible(false) } }

    var now by remember { mutableStateOf(System.currentTimeMillis()) }
    LaunchedEffect(Unit) { while (true) { now = System.currentTimeMillis(); delay(1000) } }
    val locale = Locale("id", "ID")
    val dateText = SimpleDateFormat("EEEE d MMMM", locale).format(Date(now))
    val hh = SimpleDateFormat("HH", locale).format(Date(now))
    val mm = SimpleDateFormat("mm", locale).format(Date(now))

    // Masuk: jam turun halus, kartu naik; latar bergerak pelan terus-menerus.
    var shown by remember { mutableStateOf(false) }
    LaunchedEffect(Unit) { shown = true }
    val enter by animateFloatAsState(if (shown) 1f else 0f, tween(700, easing = FastOutSlowInEasing), label = "enter")
    val inf = rememberInfiniteTransition(label = "lock")
    val drift by inf.animateFloat(0f, 1f, infiniteRepeatable(tween(14000, easing = LinearEasing), RepeatMode.Reverse), label = "drift")
    val blink by inf.animateFloat(1f, 0.25f, infiniteRepeatable(tween(900), RepeatMode.Reverse), label = "blink")
    val bob by inf.animateFloat(0f, 1f, infiniteRepeatable(tween(1100, easing = FastOutSlowInEasing), RepeatMode.Reverse), label = "bob")
    val beat by inf.animateFloat(1f, 1.06f, infiniteRepeatable(tween(1300, easing = FastOutSlowInEasing), RepeatMode.Reverse), label = "beat")
    val artScale = if (ui.isPlaying) beat else 1f

    var drag by remember { mutableStateOf(0f) }
    val dragAnim by animateFloatAsState(drag, spring(stiffness = Spring.StiffnessMedium), label = "drag")
    val base = Color(0xFF0A0A0E)
    val backdrop: @Composable (Modifier) -> Unit = { m -> LockBackdrop(m, song.thumbnailUrl, cover.accent, drift) }
    Box(
        Modifier.fillMaxSize().background(base)
            .graphicsLayer { translationY = dragAnim; alpha = (1f + dragAnim / 900f).coerceIn(0.2f, 1f) }
            .pointerInput(Unit) {
                detectVerticalDragGestures(
                    onDragStart = { drag = 0f },
                    onVerticalDrag = { _, d -> drag = (drag + d).coerceAtMost(0f) },
                    onDragEnd = { if (drag < -160f) LockMode.active = false else drag = 0f },
                    onDragCancel = { drag = 0f }
                )
            }
    ) {
        backdrop(Modifier.fillMaxSize())
        Column(Modifier.fillMaxSize().statusBarsPadding().navigationBarsPadding(), horizontalAlignment = Alignment.CenterHorizontally) {
            Column(
                Modifier.graphicsLayer { alpha = enter; translationY = (1f - enter) * -60f },
                horizontalAlignment = Alignment.CenterHorizontally
            ) {
                Spacer(Modifier.height(10.dp))
                Icon(Icons.Rounded.Lock, null, tint = Color.White.copy(alpha = 0.85f), modifier = Modifier.size(16.dp))
                Text(dateText, color = Color.White, fontSize = 15.sp, fontWeight = FontWeight.Medium, modifier = Modifier.padding(top = 4.dp))
                Row(verticalAlignment = Alignment.CenterVertically) {
                    Text(hh, color = Color.White, fontSize = 78.sp, fontWeight = FontWeight.Bold)
                    Text(":", color = Color.White.copy(alpha = blink), fontSize = 70.sp, fontWeight = FontWeight.Bold, modifier = Modifier.padding(bottom = 6.dp))
                    Text(mm, color = Color.White, fontSize = 78.sp, fontWeight = FontWeight.Bold)
                }
            }

            // Lirik berjalan: baris aktif terisi putih per kata (karaoke) seperti di halaman lirik.
            LyricsFeed(player, Modifier.weight(1f).fillMaxWidth().padding(horizontal = 22.dp).graphicsLayer { alpha = enter })

            // Kartu pemutar
            LiquidGlass(
                Modifier.fillMaxWidth().padding(horizontal = 14.dp).graphicsLayer { alpha = enter; translationY = (1f - enter) * 120f },
                RoundedCornerShape(30.dp), backdrop
            ) {
            Column(Modifier.padding(16.dp)) {
                Row(verticalAlignment = Alignment.CenterVertically) {
                    AsyncImage(
                        song.thumbnailUrl, null,
                        Modifier.size(48.dp).graphicsLayer { scaleX = artScale; scaleY = artScale }.clip(RoundedCornerShape(10.dp)),
                        contentScale = ContentScale.Crop
                    )
                    Column(Modifier.padding(start = 12.dp).weight(1f)) {
                        AnimatedContent(song.title, transitionSpec = { fadeIn(tween(300)) togetherWith fadeOut(tween(200)) }, label = "t") { t ->
                            Text(t, color = Color.White, fontWeight = FontWeight.SemiBold, fontSize = 14.sp, maxLines = 1, overflow = TextOverflow.Ellipsis)
                        }
                        Text(song.subtitle.substringBefore(" • "), color = Color.White.copy(alpha = 0.65f), fontSize = 12.sp, maxLines = 1, overflow = TextOverflow.Ellipsis)
                    }
                }
                val frac by animateFloatAsState(
                    if (ui.durationMs > 0) (ui.positionMs.toFloat() / ui.durationMs).coerceIn(0f, 1f) else 0f,
                    tween(900, easing = LinearEasing), label = "frac"
                )
                Box(Modifier.fillMaxWidth().padding(top = 14.dp).height(5.dp).clip(CircleShape).background(Color.White.copy(alpha = 0.25f))) {
                    Box(Modifier.fillMaxWidth(frac).fillMaxSize().clip(CircleShape).background(Color.White))
                }
                Row(Modifier.fillMaxWidth().padding(top = 4.dp)) {
                    Text(fmt(ui.positionMs), color = Color.White.copy(alpha = 0.7f), fontSize = 10.sp, modifier = Modifier.weight(1f))
                    Text("-" + fmt((ui.durationMs - ui.positionMs).coerceAtLeast(0)), color = Color.White.copy(alpha = 0.7f), fontSize = 10.sp)
                }
                Row(Modifier.fillMaxWidth().padding(top = 6.dp), horizontalArrangement = Arrangement.SpaceEvenly, verticalAlignment = Alignment.CenterVertically) {
                    PressIcon(Icons.Rounded.SkipPrevious, "Sebelumnya", 34.dp) { player.previous() }
                    PressBox(54.dp, onClick = { player.togglePlay() }) {
                        AnimatedContent(
                            ui.isPlaying,
                            transitionSpec = { (scaleIn(tween(200)) + fadeIn(tween(200))) togetherWith (scaleOut(tween(150)) + fadeOut(tween(150))) },
                            label = "pp"
                        ) { playing ->
                            Icon(if (playing) Icons.Rounded.Pause else Icons.Rounded.PlayArrow, "Putar/jeda", tint = Color.White, modifier = Modifier.size(46.dp))
                        }
                    }
                    PressIcon(Icons.Rounded.SkipNext, "Berikutnya", 34.dp) { player.next() }
                }
            }
            }
            Icon(Icons.Rounded.KeyboardArrowUp, null, tint = Color.White.copy(alpha = 0.8f), modifier = Modifier.padding(top = 8.dp).size(22.dp).graphicsLayer { translationY = -6f * bob })
            Text("Geser ke atas untuk membuka", color = Color.White.copy(alpha = 0.7f), fontSize = 11.sp)
            Box(Modifier.padding(top = 8.dp, bottom = 10.dp).width(120.dp).height(4.dp).clip(CircleShape).background(Color.White.copy(alpha = 0.8f)))
        }
    }
}

@Composable
private fun PressBox(size: androidx.compose.ui.unit.Dp, onClick: () -> Unit, content: @Composable () -> Unit) {
    val src = remember { MutableInteractionSource() }
    val pressed by src.collectIsPressedAsState()
    val sc by animateFloatAsState(if (pressed) 0.82f else 1f, spring(dampingRatio = 0.45f, stiffness = 500f), label = "press")
    Box(
        Modifier.size(size).graphicsLayer { scaleX = sc; scaleY = sc }.clip(CircleShape)
            .clickable(interactionSource = src, indication = null, onClick = onClick),
        Alignment.Center
    ) { content() }
}

@Composable
private fun PressIcon(icon: androidx.compose.ui.graphics.vector.ImageVector, desc: String, size: androidx.compose.ui.unit.Dp, onClick: () -> Unit) {
    PressBox(size + 14.dp, onClick) { Icon(icon, desc, tint = Color.White, modifier = Modifier.size(size)) }
}

/** Latar layar kunci: sampul lagu yang dikaburkan, ditambah cahaya warna yang bergerak pelan. Dipakai juga sebagai isi lensa kaca. */
@Composable
private fun LockBackdrop(modifier: Modifier, art: String?, accent: Color, drift: Float) {
    Box(modifier.background(Color(0xFF0A0A0E))) {
        if (android.os.Build.VERSION.SDK_INT >= 31 && art != null) {
            AsyncImage(
                art, null, Modifier.fillMaxSize().graphicsLayer { scaleX = 1.5f; scaleY = 1.5f }.blur(48.dp),
                contentScale = ContentScale.Crop, alpha = 0.55f,
                colorFilter = androidx.compose.ui.graphics.ColorFilter.colorMatrix(androidx.compose.ui.graphics.ColorMatrix().apply { setToSaturation(1.6f) })
            )
        }
        Box(
            Modifier.fillMaxSize().drawBehind {
                val w = size.width; val h = size.height
                val c1 = Offset(w * (0.2f + 0.5f * drift), h * (0.18f + 0.1f * drift))
                val c2 = Offset(w * (0.85f - 0.5f * drift), h * (0.75f - 0.1f * drift))
                drawCircle(Brush.radialGradient(listOf(accent.copy(alpha = 0.50f), Color.Transparent), c1, w * 0.95f), radius = w * 0.95f, center = c1)
                drawCircle(Brush.radialGradient(listOf(Color(0xFF6A3DFF).copy(alpha = 0.30f), Color.Transparent), c2, w * 0.9f), radius = w * 0.9f, center = c2)
            }
        )
    }
}

/** Daftar lirik berjalan (karaoke per kata) yang dipakai Mode Kunci Layar dan Mode Lirik. */
@Composable
internal fun LyricsFeed(player: PlayerViewModel, modifier: Modifier = Modifier, sizeScale: Float = 1.15f, center: Boolean = false) {
    val ui = player.ui
    Box(
        modifier
            .graphicsLayer { compositingStrategy = androidx.compose.ui.graphics.CompositingStrategy.Offscreen }
            .drawWithContent {
                drawContent()
                drawRect(
                    Brush.verticalGradient(0f to Color.Transparent, 0.10f to Color.Black, 0.86f to Color.Black, 1f to Color.Transparent),
                    blendMode = androidx.compose.ui.graphics.BlendMode.DstIn
                )
            }
    ) {
        val st = player.lyrics
        if (st is LyricsState.Ready) {
            val lyr = st.lyrics
            val lines = lyr.lines
            val synced = lyr.synced
            val prefs = AppGraph.lyricsPrefs
            val offset = remember(st.songId, prefs.offsetVersion) { prefs.offsetMs(st.songId) }
            var smoothPos by remember { androidx.compose.runtime.mutableLongStateOf(ui.positionMs) }
            LaunchedEffect(ui.positionMs, ui.isPlaying) {
                smoothPos = ui.positionMs
                if (!ui.isPlaying) return@LaunchedEffect
                val base0 = ui.positionMs
                val t0 = System.nanoTime()
                while (true) androidx.compose.runtime.withFrameMillis { smoothPos = base0 + (System.nanoTime() - t0) / 1_000_000 }
            }
            val cur by remember(lines) {
                androidx.compose.runtime.derivedStateOf {
                    if (!synced) -1 else {
                        val p = smoothPos + 180L + offset
                        var lo = 0; var hi = lines.lastIndex; var ans = -1
                        while (lo <= hi) { val mid = (lo + hi) / 2; if (lines[mid].timeMs <= p) { ans = mid; lo = mid + 1 } else hi = mid - 1 }
                        ans
                    }
                }
            }
            val listState = rememberLazyListState()
            LaunchedEffect(cur, st.songId) {
                if (!synced || cur < 0) return@LaunchedEffect
                val info = listState.layoutInfo
                val target = info.viewportSize.height * 0.30f
                val item = info.visibleItemsInfo.firstOrNull { it.index == cur }
                if (item != null) listState.animateScrollBy(item.offset + item.size / 2f - target, tween(650, easing = FastOutSlowInEasing))
                else listState.scrollToItem(cur, -target.toInt())
            }
            LazyColumn(state = listState, modifier = Modifier.fillMaxSize(), userScrollEnabled = !synced, contentPadding = PaddingValues(vertical = 24.dp)) {
                itemsIndexed(lines) { i, l ->
                    if (l.text.isBlank()) { Spacer(Modifier.height(14.dp)); return@itemsIndexed }
                    val endMs = lines.getOrNull(i + 1)?.timeMs ?: (l.timeMs + 4_000L)
                    val sweepMs = remember(l, endMs) {
                        if (l.words.isNotEmpty()) LyricTiming.wordSweepMs(l) else LyricTiming.sweepMs(l.text, endMs - l.timeMs)
                    }
                    LyricRow(
                        text = l.text, words = l.words, lineStartMs = l.timeMs, synced = synced,
                        distance = if (synced) kotlin.math.abs(i - cur) else 1,
                        center = center, sizeScale = sizeScale, translation = null, roman = null,
                        animStyle = prefs.animStyle, blurOthers = prefs.blurOthers,
                        progress = { (((smoothPos + 180L + offset) - l.timeMs).toFloat() / sweepMs).coerceIn(0f, 1f) },
                        onClick = { if (synced) player.seekTo((l.timeMs - offset).coerceAtLeast(0L)) }
                    )
                }
            }
        } else Box(Modifier.fillMaxSize(), Alignment.Center) {
            Text(
                when (st) { LyricsState.NotFound -> "Lirik belum ditemukan"; LyricsState.Loading, LyricsState.Idle -> "Memuat lirik…"; else -> "Lirik tidak tersedia" },
                color = Color.White.copy(alpha = 0.6f), fontSize = 16.sp, textAlign = TextAlign.Center
            )
        }

    }
}
