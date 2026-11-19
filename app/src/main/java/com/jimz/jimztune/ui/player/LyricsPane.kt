package com.jimz.jimztune.ui.player

import androidx.compose.animation.core.FastOutSlowInEasing
import androidx.compose.animation.core.animateFloatAsState
import androidx.compose.animation.core.tween
import androidx.compose.foundation.Canvas
import androidx.compose.foundation.background
import androidx.compose.foundation.clickable
import androidx.compose.foundation.horizontalScroll
import androidx.compose.foundation.rememberScrollState
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.ui.draw.clip
import androidx.compose.ui.graphics.Shadow
import androidx.compose.ui.text.TextStyle
import androidx.compose.ui.text.style.TextAlign
import com.jimz.jimztune.AppGraph
import androidx.compose.foundation.gestures.animateScrollBy
import androidx.compose.foundation.gestures.awaitEachGesture
import androidx.compose.foundation.gestures.awaitFirstDown
import androidx.compose.foundation.interaction.MutableInteractionSource
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.PaddingValues
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.lazy.LazyColumn
import androidx.compose.foundation.lazy.itemsIndexed
import androidx.compose.foundation.lazy.rememberLazyListState
import androidx.compose.material3.CircularProgressIndicator
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.Text
import androidx.compose.material3.TextButton
import androidx.compose.runtime.Composable
import androidx.compose.runtime.LaunchedEffect
import androidx.compose.runtime.derivedStateOf
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableLongStateOf
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.setValue
import androidx.compose.runtime.withFrameMillis
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.BlurredEdgeTreatment
import androidx.compose.ui.draw.blur
import androidx.compose.ui.draw.drawWithContent
import androidx.compose.ui.geometry.Offset
import androidx.compose.ui.geometry.Rect
import androidx.compose.ui.geometry.Size
import androidx.compose.ui.graphics.BlendMode
import androidx.compose.ui.graphics.Brush
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.graphics.CompositingStrategy
import androidx.compose.ui.graphics.Paint
import androidx.compose.ui.graphics.TransformOrigin
import androidx.compose.ui.graphics.drawscope.clipRect
import androidx.compose.ui.graphics.drawscope.drawIntoCanvas
import androidx.compose.ui.graphics.graphicsLayer
import androidx.compose.ui.input.pointer.PointerEventPass
import androidx.compose.ui.input.pointer.pointerInput
import androidx.compose.ui.text.TextLayoutResult
import androidx.compose.ui.text.drawText
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import com.jimz.jimztune.data.lyrics.LyricLine
import com.jimz.jimztune.data.lyrics.LyricWord
import com.jimz.jimztune.data.lyrics.Lyrics
import com.jimz.jimztune.data.lyrics.LyricsState
import com.jimz.jimztune.data.lyrics.TranslationState
import kotlin.math.abs
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.withContext
import com.jimz.jimztune.data.lyrics.Romanizer
import com.jimz.jimztune.ui.components.TonalPillButton
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.rounded.Refresh

/** Lirik muncul sedikit lebih awal dari audio supaya terasa pas dengan animasinya. */
private const val LEAD_MS = 180L

/** Jeda instrumental minimal (ms) yang ditampilkan sebagai tiga titik. */
private const val GAP_DOTS_MS = 4_000L

@Composable
fun LyricsPane(
    state: LyricsState,
    positionMs: Long,
    isPlaying: Boolean,
    onSeek: (Long) -> Unit,
    onRetry: () -> Unit,
    onNextSource: () -> Unit = {},
    onPickSource: (Int) -> Unit = {},
    songTitle: String = "",
    songArtist: String = "",
    translation: TranslationState = TranslationState.Off,
    onTranslationSettingsChanged: () -> Unit = {},
    modifier: Modifier = Modifier
) {
    when (state) {
        LyricsState.Idle, LyricsState.Loading -> Box(modifier.fillMaxSize(), Alignment.Center) {
            CircularProgressIndicator(color = Color.White, strokeWidth = 3.dp)
        }
        LyricsState.NotFound -> Column(
            modifier = modifier.fillMaxSize(),
            horizontalAlignment = Alignment.CenterHorizontally,
            verticalArrangement = Arrangement.Center
        ) {
            Text(
                "Lirik belum ditemukan",
                style = MaterialTheme.typography.titleMedium,
                color = Color.White.copy(alpha = 0.7f)
            )
            TonalPillButton("Coba lagi", Icons.Rounded.Refresh, onRetry)
        }
        is LyricsState.Unavailable -> Box(modifier.fillMaxSize(), Alignment.Center) {
            Text(
                state.reason,
                style = MaterialTheme.typography.titleMedium,
                color = Color.White.copy(alpha = 0.6f),
                textAlign = TextAlign.Center,
                modifier = Modifier.padding(horizontal = 32.dp)
            )
        }
        is LyricsState.Ready -> {
            var tapSync by remember(state.songId) { mutableStateOf(false) }
            Box(modifier.fillMaxSize()) {
                LyricsList(state, positionMs, isPlaying, onSeek, translation, tapSync, { tapSync = false }, Modifier.fillMaxSize())
                LyricsSettingsButton(
                    state = state,
                    translation = translation,
                    positionMs = positionMs,
                    songTitle = songTitle,
                    songArtist = songArtist,
                    onRetry = onRetry,
                    onNextSource = onNextSource,
                    onPickSource = onPickSource,
                    onStartTapSync = { tapSync = true },
                    onTranslationChanged = onTranslationSettingsChanged,
                    modifier = Modifier.align(Alignment.TopEnd)
                )
            }
        }
    }
}

@Composable
private fun LyricsList(
    state: LyricsState.Ready,
    positionMs: Long,
    isPlaying: Boolean,
    onSeek: (Long) -> Unit,
    translation: TranslationState,
    tapSync: Boolean,
    onTapSyncDone: () -> Unit,
    modifier: Modifier
) {
    val lyrics = state.lyrics
    val prefs = AppGraph.lyricsPrefs
    val center = prefs.align == 1
    val sizeScale = prefs.sizeScale
    // Geseran waktu per lagu: positif = lirik muncul lebih cepat. Dipakai kalau versi lagunya beda intro dengan sumber lirik.
    var offset by remember(state.songId) { mutableLongStateOf(prefs.offsetMs(state.songId)) }
    // Geseran bisa diubah dari pengaturan lirik (tombol +/- atau atur dengan ketuk).
    LaunchedEffect(prefs.offsetVersion, state.songId) { offset = prefs.offsetMs(state.songId) }

    // Romanisasi dihitung di thread lain, hanya kalau dinyalakan.
    var roman by remember(lyrics) { mutableStateOf<List<String?>?>(null) }
    LaunchedEffect(lyrics, prefs.romanize) {
        roman = if (prefs.romanize) {
            withContext(Dispatchers.Default) { Romanizer.lines(lyrics.lines.map { it.text }) }
        } else {
            null
        }
    }

    // Kalau lagu mulai dengan intro panjang, sisipkan baris kosong di awal supaya titik-titik tampil.
    val lines = remember(lyrics) {
        val raw = lyrics.lines
        if (lyrics.synced && raw.isNotEmpty() && raw.first().timeMs > GAP_DOTS_MS && raw.first().text.isNotBlank()) {
            listOf(LyricLine(0L, "")) + raw
        } else {
            raw
        }
    }
    // Baris kosong yang disisipkan di awal menggeser indeks; terjemahan sejajar dengan lirik aslinya.
    val shift = lines.size - lyrics.lines.size
    val translated = (translation as? TranslationState.Ready)?.lines
    val listState = rememberLazyListState()
    var lastTouch by remember { mutableLongStateOf(0L) }

    // Jam halus: posisi dari pemutar hanya datang tiap 0,5 detik, jadi diinterpolasi per frame.
    var smoothPos by remember { mutableLongStateOf(positionMs) }
    LaunchedEffect(positionMs, isPlaying) {
        smoothPos = positionMs
        if (!isPlaying) return@LaunchedEffect
        val base = positionMs
        val start = System.nanoTime()
        while (true) {
            withFrameMillis { smoothPos = base + (System.nanoTime() - start) / 1_000_000 }
        }
    }

    val activeIndex by remember(lines) {
        derivedStateOf {
            if (!lyrics.synced) {
                -1
            } else {
                val p = (smoothPos + LEAD_MS + offset)
                var lo = 0
                var hi = lines.lastIndex
                var answer = -1
                while (lo <= hi) {
                    val mid = (lo + hi) / 2
                    if (lines[mid].timeMs <= p) {
                        answer = mid
                        lo = mid + 1
                    } else {
                        hi = mid - 1
                    }
                }
                answer
            }
        }
    }

    // Menggulir halus agar baris aktif selalu berada di sekitar sepertiga atas layar.
    LaunchedEffect(activeIndex) {
        if (!lyrics.synced || activeIndex < 0) return@LaunchedEffect
        if (System.currentTimeMillis() - lastTouch < 3500) return@LaunchedEffect
        val info = listState.layoutInfo
        val targetY = info.viewportSize.height * 0.32f
        val item = info.visibleItemsInfo.firstOrNull { it.index == activeIndex }
        if (item != null) {
            val delta = item.offset + item.size / 2f - targetY
            listState.animateScrollBy(delta, tween(600, easing = FastOutSlowInEasing))
        } else {
            listState.scrollToItem(activeIndex, -targetY.toInt())
        }
    }

    Column(modifier = modifier.fillMaxSize()) {
        if (tapSync) {
            Text(
                "Ketuk baris yang SEDANG dinyanyikan sekarang untuk menyelaraskan waktu",
                style = MaterialTheme.typography.labelLarge,
                color = Color.White,
                textAlign = TextAlign.Center,
                modifier = Modifier
                    .fillMaxWidth()
                    .padding(top = 8.dp, start = 48.dp, end = 48.dp)
            )
        }
        LazyColumn(
            state = listState,
            modifier = Modifier
                .weight(1f)
                .fillMaxWidth()
                .graphicsLayer { compositingStrategy = CompositingStrategy.Offscreen }
                .drawWithContent {
                    drawContent()
                    drawRect(
                        brush = Brush.verticalGradient(
                            0f to Color.Transparent,
                            0.10f to Color.Black,
                            0.88f to Color.Black,
                            1f to Color.Transparent
                        ),
                        blendMode = BlendMode.DstIn
                    )
                }
                .pointerInput(Unit) {
                    awaitEachGesture {
                        awaitFirstDown(requireUnconsumed = false, pass = PointerEventPass.Initial)
                        lastTouch = System.currentTimeMillis()
                        do {
                            val event = awaitPointerEvent(PointerEventPass.Initial)
                            lastTouch = System.currentTimeMillis()
                        } while (event.changes.any { it.pressed })
                    }
                },
            contentPadding = PaddingValues(top = 64.dp, bottom = 260.dp)
        ) {
            itemsIndexed(lines) { index, line ->
                val distance = when {
                    !lyrics.synced -> 0
                    activeIndex < 0 -> index + 1
                    else -> abs(index - activeIndex)
                }
                val endMs = lines.getOrNull(index + 1)?.timeMs ?: (line.timeMs + GAP_DOTS_MS)
                val seek = {
                    if (tapSync && lyrics.synced && line.text.isNotBlank()) {
                        // Baris ini sedang terdengar sekarang: geseran = waktu baris - posisi sekarang.
                        val newOffset = (line.timeMs - smoothPos - LEAD_MS).coerceIn(-15_000L, 15_000L)
                        prefs.setOffsetMs(state.songId, newOffset)
                        offset = newOffset
                        onTapSyncDone()
                    } else if (lyrics.synced) {
                        onSeek((line.timeMs - offset).coerceAtLeast(0L))
                        lastTouch = 0L
                    }
                }

                when {
                    // Lirik tanpa waktu: baris kosong hanya jadi jarak antar bait.
                    !lyrics.synced && line.text.isBlank() -> Spacer(Modifier.height(16.dp))

                    // Bagian instrumental: tiga titik yang terisi pelan-pelan sampai baris berikutnya.
                    line.text.isBlank() -> GapRow(
                        gapMs = endMs - line.timeMs,
                        distance = distance,
                        center = center,
                        progress = {
                            val gap = (endMs - line.timeMs).coerceAtLeast(1L)
                            (((smoothPos + LEAD_MS + offset) - line.timeMs).toFloat() / gap).coerceIn(0f, 1f)
                        }
                    )

                    else -> {
                        // Lama sapuan ditentukan dari jumlah suku kata baris ini (bukan sekadar jumlah huruf),
                        // dan tidak melewati baris berikutnya. Pembagian per kata ada di LyricRow.
                        val sweepMs = remember(line, endMs) {
                            if (line.words.isNotEmpty()) LyricTiming.wordSweepMs(line)
                            else LyricTiming.sweepMs(line.text, endMs - line.timeMs)
                        }
                        LyricRow(
                            text = line.text,
                            words = line.words,
                            lineStartMs = line.timeMs,
                            synced = lyrics.synced,
                            distance = distance,
                            center = center,
                            sizeScale = sizeScale,
                            translation = translated?.getOrNull(index - shift),
                            roman = roman?.getOrNull(index - shift),
                            animStyle = prefs.animStyle,
                            blurOthers = prefs.blurOthers,
                            progress = {
                                (((smoothPos + LEAD_MS + offset) - line.timeMs).toFloat() / sweepMs).coerceIn(0f, 1f)
                            },
                            onClick = seek
                        )
                    }
                }
            }
        }
    }
}

/**
 * Baris aktif terisi putih dari kiri ke kanan mengikuti lagu (seperti karaoke), sisanya masih redup.
 * Baris lain makin jauh makin redup, mengecil, dan buram.
 */
@Composable
private fun LyricRow(
    text: String,
    words: List<LyricWord>,
    lineStartMs: Long,
    synced: Boolean,
    distance: Int,
    center: Boolean,
    sizeScale: Float,
    translation: String?,
    roman: String?,
    animStyle: Int,
    blurOthers: Boolean,
    progress: () -> Float,
    onClick: () -> Unit
) {
    val active = synced && distance == 0
    val targetAlpha = when {
        !synced -> 0.92f
        active -> 1f
        else -> (0.55f - 0.07f * (distance - 1).coerceAtMost(4)).coerceAtLeast(0.22f)
    }
    val targetBlur = if (!synced || active || !blurOthers) 0f else (distance * 1.4f).coerceAtMost(6f)

    val alpha by animateFloatAsState(targetAlpha, tween(400), label = "lyricAlpha")
    val blurRadius by animateFloatAsState(targetBlur, tween(400), label = "lyricBlur")
    val scale by animateFloatAsState(if (!synced || active) 1f else 0.92f, tween(400), label = "lyricScale")
    // Bagian yang belum "dinyanyikan" di baris aktif dibuat lebih redup daripada bagian yang sudah terisi.
    val baseAlpha by animateFloatAsState(if (active) 0.40f else 1f, tween(400), label = "lyricBase")
    val sweepAlpha by animateFloatAsState(if (active) 1f else 0f, tween(300), label = "lyricSweep")
    // Gaya Slide: baris yang belum aktif bergeser sedikit ke kanan lalu meluncur masuk saat aktif.
    val slideX by animateFloatAsState(if (animStyle == 3 && synced && !active) 16f else 0f, tween(400), label = "lyricSlide")

    var layout by remember { mutableStateOf<TextLayoutResult?>(null) }
    // Posisi tiap kata dihitung sekali per tata letak (bukan tiap frame).
    // Kalau sumber punya waktu per kata, pakai itu; kalau tidak, waktu kata diperkirakan dari suku katanya.
    val sweepMap = remember(layout, text, words) {
        layout?.let {
            if (words.isNotEmpty()) LyricTiming.buildSweepMapFromWords(it, text, words, lineStartMs)
                ?: LyricTiming.buildSweepMap(it, text)
            else LyricTiming.buildSweepMap(it, text)
        }
    }

    // Baris asli + terjemahannya satu kesatuan: ikut bergulir, meredup, dan membesar bersama baris aktif.
    Column(
        modifier = Modifier
            .fillMaxWidth()
            .clickable(
                interactionSource = remember { MutableInteractionSource() },
                indication = null,
                onClick = onClick
            )
            .padding(horizontal = 4.dp, vertical = 10.dp)
            .graphicsLayer {
                this.alpha = alpha
                scaleX = scale
                scaleY = scale
                translationX = slideX * density
                transformOrigin = TransformOrigin(if (center) 0.5f else 0f, 0.5f)
            }
            .then(
                if (blurRadius > 0.1f) Modifier.blur(blurRadius.dp, BlurredEdgeTreatment.Unbounded)
                else Modifier
            )
    ) {
        Text(
            text = text,
            color = Color.White.copy(alpha = baseAlpha),
            fontSize = (28f * sizeScale).sp,
            lineHeight = (36f * sizeScale).sp,
            fontWeight = FontWeight.Bold,
            textAlign = if (center) TextAlign.Center else TextAlign.Start,
            // Baris yang sedang dinyanyikan berpendar halus (seperti ArchiveTune).
            style = if (active && animStyle != 1) {
                val glowAlpha = when (animStyle) { 2 -> 0.8f; 3 -> 0.2f; else -> 0.30f }
                val glowRadius = when (animStyle) { 2 -> 48f; 3 -> 20f; else -> 26f }
                TextStyle(shadow = Shadow(Color.White.copy(alpha = glowAlpha), Offset.Zero, glowRadius))
            } else {
                TextStyle.Default
            },
            onTextLayout = { layout = it },
            modifier = Modifier
                .fillMaxWidth()
                .drawWithContent {
                    drawContent()
                    val result = layout ?: return@drawWithContent
                    val map = sweepMap ?: return@drawWithContent
                    if (sweepAlpha <= 0.01f) return@drawWithContent
                    val p = progress()
                    if (p <= 0f) return@drawWithContent

                    // Tepi isian mengikuti kata demi kata (kata panjang lebih lama), bukan meluncur rata.
                    val edge = map.xAt(p)
                    // Karaoke: tepi lembut sedang. Fade: sangat lembut. Glow: lembut. Slide: tegas.
                    val feather = when (animStyle) { 1 -> 64.dp; 2 -> 24.dp; 3 -> 3.dp; else -> 14.dp }.toPx()
                    val fillColor = Color.White.copy(alpha = sweepAlpha)

                    // Isi putih mengalir baris demi baris (kalau teks terbungkus jadi dua baris).
                    var cum = 0f
                    for (i in 0 until result.lineCount) {
                        val left = result.getLineLeft(i)
                        val lineWidth = result.getLineRight(i) - left
                        val fill = (edge - cum).coerceIn(0f, lineWidth)
                        cum += lineWidth
                        if (fill <= 0f) continue
                        val top = result.getLineTop(i)
                        val bottom = result.getLineBottom(i)

                        if (fill >= lineWidth - 0.5f) {
                            // Baris ini sudah selesai dinyanyikan: isi penuh, tanpa tepi.
                            clipRect(left = left, top = top, right = left + lineWidth, bottom = bottom) {
                                drawText(result, color = fillColor)
                            }
                        } else {
                            // Tepi depan dibuat memudar (feather) supaya sapuan terasa lembut, bukan garis tajam.
                            val edgeX = left + fill
                            val right = edgeX + feather
                            clipRect(left = left, top = top, right = right, bottom = bottom) {
                                drawIntoCanvas { canvas ->
                                    canvas.saveLayer(Rect(left, top, right, bottom), Paint())
                                    drawText(result, color = fillColor)
                                    drawRect(
                                        brush = Brush.horizontalGradient(
                                            0f to Color.Black,
                                            1f to Color.Transparent,
                                            startX = edgeX - feather,
                                            endX = edgeX + feather
                                        ),
                                        topLeft = Offset(left, top),
                                        size = Size(right - left, bottom - top),
                                        blendMode = BlendMode.DstIn
                                    )
                                    canvas.restore()
                                }
                            }
                        }
                    }

                }
        )
        if (roman != null) {
            Text(
                text = roman,
                color = Color.White.copy(alpha = if (active || !synced) 0.8f else 0.6f),
                fontSize = (16f * sizeScale).sp,
                lineHeight = (21f * sizeScale).sp,
                fontStyle = androidx.compose.ui.text.font.FontStyle.Italic,
                textAlign = if (center) TextAlign.Center else TextAlign.Start,
                modifier = Modifier
                    .fillMaxWidth()
                    .padding(top = 3.dp)
            )
        }
        if (translation != null) {
            Text(
                text = translation,
                color = Color.White.copy(alpha = if (active || !synced) 0.74f else 0.6f),
                fontSize = (16f * sizeScale).sp,
                lineHeight = (21f * sizeScale).sp,
                fontWeight = FontWeight.Medium,
                textAlign = if (center) TextAlign.Center else TextAlign.Start,
                modifier = Modifier
                    .fillMaxWidth()
                    .padding(top = 3.dp)
            )
        }
    }
}

/** Tiga titik instrumental: terisi satu per satu selama jeda, lalu lirik berikutnya muncul. */
@Composable
private fun GapRow(gapMs: Long, distance: Int, center: Boolean, progress: () -> Float) {
    if (gapMs < GAP_DOTS_MS) {
        Spacer(Modifier.height(6.dp))
        return
    }
    val active = distance == 0
    val alpha by animateFloatAsState(if (active) 1f else 0.35f, tween(400), label = "gapAlpha")
    val scale by animateFloatAsState(if (active) 1f else 0.8f, tween(400), label = "gapScale")

    Box(Modifier.fillMaxWidth(), contentAlignment = if (center) Alignment.Center else Alignment.CenterStart) {
    Canvas(
        modifier = Modifier
            .padding(horizontal = 6.dp, vertical = 14.dp)
            .size(width = 64.dp, height = 16.dp)
            .graphicsLayer {
                this.alpha = alpha
                scaleX = scale
                scaleY = scale
                transformOrigin = TransformOrigin(if (center) 0.5f else 0f, 0.5f)
            }
    ) {
        val p = progress()
        val radius = size.height / 2f
        for (i in 0 until 3) {
            val fill = (p * 3f - i).coerceIn(0f, 1f)
            drawCircle(
                color = Color.White.copy(alpha = 0.28f + 0.72f * fill),
                radius = radius,
                center = Offset(radius + i * (radius * 2f + 8.dp.toPx()), size.height / 2f)
            )
        }
    }
    }
}
