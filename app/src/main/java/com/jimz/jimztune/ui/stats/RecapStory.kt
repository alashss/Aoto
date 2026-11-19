package com.jimz.jimztune.ui.stats

import androidx.compose.animation.core.FastOutSlowInEasing
import androidx.compose.animation.core.RepeatMode
import androidx.compose.animation.core.infiniteRepeatable
import androidx.compose.animation.core.rememberInfiniteTransition
import androidx.compose.animation.core.animateFloat
import androidx.compose.animation.core.LinearEasing
import androidx.compose.animation.core.animate
import androidx.compose.animation.core.animateFloatAsState
import androidx.compose.animation.core.animateIntAsState
import androidx.compose.animation.core.tween
import androidx.compose.foundation.Canvas
import androidx.compose.foundation.background
import androidx.compose.foundation.border
import androidx.compose.foundation.clickable
import androidx.compose.foundation.gestures.detectTapGestures
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.BoxScope
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.fillMaxHeight
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.navigationBarsPadding
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.layout.statusBarsPadding
import androidx.compose.foundation.layout.width
import androidx.compose.foundation.pager.HorizontalPager
import androidx.compose.foundation.pager.rememberPagerState
import androidx.compose.foundation.shape.CircleShape
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.rounded.Close
import androidx.compose.material3.Icon
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.runtime.LaunchedEffect
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableFloatStateOf
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.rememberCoroutineScope
import androidx.compose.runtime.setValue
import androidx.compose.runtime.snapshotFlow
import androidx.compose.runtime.snapshots.Snapshot
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clip
import androidx.compose.ui.draw.shadow
import androidx.compose.ui.geometry.Offset
import androidx.compose.ui.graphics.Brush
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.graphics.graphicsLayer
import androidx.compose.ui.graphics.toArgb
import androidx.compose.ui.input.pointer.pointerInput
import androidx.compose.ui.layout.ContentScale
import androidx.compose.ui.platform.LocalContext
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.text.style.TextAlign
import androidx.compose.ui.text.style.TextOverflow
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import androidx.compose.ui.window.Dialog
import androidx.compose.ui.window.DialogProperties
import coil.compose.AsyncImage
import com.jimz.jimztune.data.stats.ArtistStat
import com.jimz.jimztune.data.stats.PlayLog
import com.jimz.jimztune.data.stats.SongStat
import com.jimz.jimztune.data.stats.Streak
import com.jimz.jimztune.data.stats.StreakInfo
import com.jimz.jimztune.ui.components.PillTextButton
import com.jimz.jimztune.util.ShareImage
import com.jimz.jimztune.data.stats.ArtistPhotos
import kotlinx.coroutines.NonCancellable
import kotlinx.coroutines.launch
import kotlinx.coroutines.withContext
import kotlin.math.abs
import java.time.LocalDate
import java.time.format.TextStyle
import java.util.Locale

private val ID = Locale("id", "ID")
private const val SLIDE_MS = 6500
private val Ink = Color(0xFF0B1030)

private enum class RecapPeriod(val label: String) {
    THIS_MONTH("Bulan ini"), LAST_MONTH("Bulan lalu"), D30("30 hari"), THIS_YEAR("Tahun ini")
}

private class RecapData(
    val title: String,
    val totalSec: Int,
    val activeDays: Int,
    val songs: List<SongStat>,
    val artists: List<ArtistStat>,
    val byDay: Map<String, Int>,
    val streak: StreakInfo
) {
    val minutes: Int get() = totalSec / 60
    val isEmpty: Boolean get() = songs.isEmpty() || totalSec < 60
}

private fun loadRecap(p: RecapPeriod): RecapData {
    val today = LocalDate.now()
    val (from, to, title) = when (p) {
        RecapPeriod.THIS_MONTH -> Triple(
            today.withDayOfMonth(1), today,
            today.month.getDisplayName(TextStyle.FULL, ID).replaceFirstChar { it.uppercase() } + " ${today.year}"
        )
        RecapPeriod.LAST_MONTH -> {
            val first = today.withDayOfMonth(1).minusMonths(1)
            Triple(
                first, first.withDayOfMonth(first.lengthOfMonth()),
                first.month.getDisplayName(TextStyle.FULL, ID).replaceFirstChar { it.uppercase() } + " ${first.year}"
            )
        }
        RecapPeriod.D30 -> Triple(today.minusDays(29), today, "30 hari terakhir")
        RecapPeriod.THIS_YEAR -> Triple(today.withDayOfYear(1), today, "Tahun ${today.year}")
    }
    return RecapData(
        title = title,
        totalSec = PlayLog.totalSeconds(from, to),
        activeDays = PlayLog.activeDays(from, to),
        songs = PlayLog.topSongs(from, to, 5),
        artists = PlayLog.topArtists(from, to, 5),
        byDay = PlayLog.secondsByDay(from, to),
        streak = Streak.info()
    )
}

/** Satu palet gradasi per slide: (atas, tengah, bawah). */
private val palettes = listOf(
    listOf(Color(0xFF1B1464), Color(0xFF6A2FD6), Color(0xFFE0399B)),
    listOf(Color(0xFF062B3A), Color(0xFF0E6E7E), Color(0xFF2ED3B7)),
    listOf(Color(0xFF3A0F2E), Color(0xFFB83267), Color(0xFFFF8A5B)),
    listOf(Color(0xFF0B1F4D), Color(0xFF2557D6), Color(0xFF7FB2FF)),
    listOf(Color(0xFF2A1055), Color(0xFF7B3FE4), Color(0xFFFFB75E)),
    listOf(Color(0xFF0B3B2E), Color(0xFF1E9E6A), Color(0xFFD6F36B)),
    listOf(Color(0xFF16124A), Color(0xFF3A2D9C), Color(0xFFFF6FB5))
)

/**
 * Rekap dengar gaya "story": layar penuh, geser atau ketuk sisi layar untuk pindah slide,
 * tahan untuk menjeda. Nama dipertahankan supaya pemanggil lama tidak berubah.
 */
@Composable
fun RecapSheet(onDismiss: () -> Unit) {
    Dialog(
        onDismissRequest = onDismiss,
        properties = DialogProperties(usePlatformDefaultWidth = false, decorFitsSystemWindows = false)
    ) {
        RecapStory(onClose = onDismiss)
    }
}

@Composable
private fun RecapStory(onClose: () -> Unit) {
    val context = LocalContext.current
    val scope = rememberCoroutineScope()
    var period by remember { mutableStateOf(RecapPeriod.THIS_MONTH) }
    // Dibekukan saat dibuka: catatan baru yang masuk selagi menonton tidak mengacak slide.
    val data = remember(period) { Snapshot.withoutReadObservation { loadRecap(period) } }
    val pageCount = if (data.isEmpty) 1 else 7
    val pager = rememberPagerState(pageCount = { pageCount })
    var paused by remember { mutableStateOf(false) }
    var progress by remember { mutableFloatStateOf(0f) }
    val page = pager.currentPage

    LaunchedEffect(page) { progress = 0f }
    LaunchedEffect(page, paused, pageCount) {
        // Intro menunggu pilihan periode, slide terakhir menunggu tombol; sisanya jalan otomatis.
        if (page == 0 || page >= pageCount - 1 || paused) return@LaunchedEffect
        val start = progress
        animate(
            initialValue = start,
            targetValue = 1f,
            animationSpec = tween(((1f - start) * SLIDE_MS).toInt().coerceAtLeast(1), easing = LinearEasing)
        ) { v, _ -> progress = v }
        // Pindah halaman dilindungi dari pembatalan: kalau jari menyentuh layar (jeda) tepat saat bergeser, geseran
        // tetap selesai. Tanpa ini halaman bisa berhenti di tengah-tengah dua slide.
        if (pager.currentPage == page) {
            withContext(NonCancellable) { pager.animateScrollToPage(page + 1) }
        }
    }

    // Jaring pengaman: kalau geseran apa pun berhenti di antara dua slide, rapatkan ke slide terdekat.
    LaunchedEffect(pager) {
        snapshotFlow { pager.isScrollInProgress }.collect { scrolling ->
            if (!scrolling && abs(pager.currentPageOffsetFraction) > 0.002f) {
                pager.animateScrollToPage(pager.currentPage)
            }
        }
    }

    Box(
        Modifier
            .fillMaxSize()
            .background(Ink)
            .pointerInput(pageCount) {
                detectTapGestures(
                    onPress = {
                        paused = true
                        tryAwaitRelease()
                        paused = false
                    },
                    onTap = { o ->
                        val cur = pager.currentPage
                        scope.launch {
                            if (o.x < size.width * 0.3f) {
                                if (cur > 0) pager.animateScrollToPage(cur - 1)
                            } else if (cur < pageCount - 1) {
                                pager.animateScrollToPage(cur + 1)
                            }
                        }
                    }
                )
            }
    ) {
        HorizontalPager(state = pager, modifier = Modifier.fillMaxSize()) { i ->
          Box(
            Modifier
                .fillMaxSize()
                .graphicsLayer {
                    // Slide yang sedang pergi / datang mengecil dan memudar sedikit: peralihan terasa lebih halus.
                    val off = abs((pager.currentPage - i) + pager.currentPageOffsetFraction).coerceIn(0f, 1f)
                    alpha = 1f - off * 0.55f
                    scaleX = 1f - off * 0.06f
                    scaleY = 1f - off * 0.06f
                }
          ) {
            when (i) {
                0 -> IntroSlide(data, period) { period = it }
                1 -> TotalSlide(data)
                2 -> TopSongSlide(data)
                3 -> SongListSlide(data)
                4 -> ArtistSlide(data)
                5 -> RhythmSlide(data)
                else -> FinalSlide(data, onClose) {
                    val bmp = ShareImage.render(
                        story = true,
                        accent = palettes[0][1].toArgb(),
                        heading = "Rekap ${data.title}",
                        big = "${data.minutes}",
                        bigCaption = "menit mendengarkan · ${data.activeDays} hari aktif",
                        sections = listOf(
                            ShareImage.Section("Lagu teratas", data.songs.map { it.title }),
                            ShareImage.Section("Artis teratas", data.artists.map { it.name })
                        )
                    )
                    ShareImage.share(context, bmp, "rekap")
                }
            }
          }
        }

        // Batang kemajuan dan tombol tutup, di atas semua slide.
        Column(
            Modifier
                .fillMaxWidth()
                .statusBarsPadding()
                .padding(horizontal = 14.dp, vertical = 10.dp)
        ) {
            Row(Modifier.fillMaxWidth(), horizontalArrangement = Arrangement.spacedBy(4.dp)) {
                for (i in 0 until pageCount) {
                    val fill = when {
                        i < page -> 1f
                        i == page -> if (page == 0 || page == pageCount - 1) 1f else progress
                        else -> 0f
                    }
                    Box(
                        Modifier
                            .weight(1f)
                            .height(3.dp)
                            .clip(CircleShape)
                            .background(Color.White.copy(alpha = 0.28f))
                    ) {
                        Box(
                            Modifier
                                .fillMaxHeight()
                                .fillMaxWidth(fill)
                                .background(Color.White)
                        )
                    }
                }
            }
            Row(
                Modifier
                    .fillMaxWidth()
                    .padding(top = 10.dp),
                verticalAlignment = Alignment.CenterVertically
            ) {
                Text(
                    "Aoto · ${data.title}",
                    fontSize = 13.sp,
                    fontWeight = FontWeight.SemiBold,
                    color = Color.White.copy(alpha = 0.85f),
                    modifier = Modifier.weight(1f)
                )
                Box(
                    Modifier
                        .size(34.dp)
                        .clip(CircleShape)
                        .background(Color.Black.copy(alpha = 0.25f))
                        .clickable(onClick = onClose),
                    contentAlignment = Alignment.Center
                ) {
                    Icon(Icons.Rounded.Close, contentDescription = "Tutup", tint = Color.White, modifier = Modifier.size(18.dp))
                }
            }
        }
    }
}

// ---------------------------------------------------------------- bagian bersama

/** Latar slide: gradasi diagonal + lingkaran cahaya lembut yang melayang pelan. */
@Composable
private fun SlideFrame(index: Int, content: @Composable BoxScope.() -> Unit) {
    val pal = palettes[index % palettes.size]
    val t = rememberInfiniteTransition(label = "recapBg")
    val drift by t.animateFloat(
        initialValue = 0f,
        targetValue = 1f,
        animationSpec = infiniteRepeatable(tween(9000, easing = FastOutSlowInEasing), RepeatMode.Reverse),
        label = "drift"
    )
    Box(
        Modifier
            .fillMaxSize()
            .background(Brush.linearGradient(listOf(pal[0], pal[1], pal[2])))
    ) {
        Canvas(Modifier.fillMaxSize()) {
            val w = size.width
            val h = size.height
            drawCircle(
                Brush.radialGradient(listOf(Color.White.copy(alpha = 0.16f), Color.Transparent), center = Offset(w * (0.95f - 0.12f * drift), h * (0.06f + 0.05f * drift)), radius = w * 0.75f),
                radius = w * 0.75f,
                center = Offset(w * (0.95f - 0.12f * drift), h * (0.06f + 0.05f * drift))
            )
            drawCircle(
                Brush.radialGradient(listOf(pal[2].copy(alpha = 0.42f), Color.Transparent), center = Offset(w * (0.05f + 0.15f * drift), h * (0.96f - 0.06f * drift)), radius = w * 0.7f),
                radius = w * 0.7f,
                center = Offset(w * (0.05f + 0.15f * drift), h * (0.96f - 0.06f * drift))
            )
            drawCircle(
                Color.White.copy(alpha = 0.05f),
                radius = w * 0.32f,
                center = Offset(w * (0.2f + 0.1f * drift), h * 0.42f)
            )
        }
        Box(
            Modifier
                .fillMaxSize()
                .statusBarsPadding()
                .navigationBarsPadding()
                .padding(start = 28.dp, end = 28.dp, top = 74.dp, bottom = 28.dp),
            content = content
        )
    }
}

/** Muncul pelan dari bawah, bergiliran menurut [index]. Mulai saat slide tampil. */
@Composable
private fun Reveal(index: Int, modifier: Modifier = Modifier, content: @Composable () -> Unit) {
    var go by remember { mutableStateOf(false) }
    LaunchedEffect(Unit) { go = true }
    val a by animateFloatAsState(
        targetValue = if (go) 1f else 0f,
        animationSpec = tween(520, delayMillis = 120 + index * 130, easing = FastOutSlowInEasing),
        label = "reveal"
    )
    Box(
        modifier.graphicsLayer {
            alpha = a
            translationY = (1f - a) * 40f
        }
    ) { content() }
}

@Composable
private fun Kicker(text: String) {
    Text(text.uppercase(), fontSize = 12.sp, letterSpacing = 1.6.sp, fontWeight = FontWeight.Bold, color = Color.White.copy(alpha = 0.75f))
}

private fun fmtDuration(minutes: Int): String = when {
    minutes >= 60 -> "${minutes / 60} jam ${minutes % 60} menit"
    else -> "$minutes menit"
}

// ---------------------------------------------------------------- slide

@Composable
private fun IntroSlide(data: RecapData, period: RecapPeriod, onPeriod: (RecapPeriod) -> Unit) {
    SlideFrame(0) {
        Column(Modifier.fillMaxSize(), verticalArrangement = Arrangement.Center) {
            Reveal(0) { Kicker("Aoto") }
            Spacer(Modifier.height(10.dp))
            Reveal(1) { Text("Rekap\ndengar", fontSize = 64.sp, lineHeight = 64.sp, fontWeight = FontWeight.Black, color = Color.White) }
            Spacer(Modifier.height(12.dp))
            Reveal(2) {
                Text(
                    if (data.isEmpty) "Belum cukup data untuk ${data.title.lowercase()}. Dengarkan beberapa lagu dulu, atau pilih periode lain."
                    else "Perjalanan musikmu selama ${data.title.lowercase()}.",
                    fontSize = 17.sp,
                    color = Color.White.copy(alpha = 0.85f)
                )
            }
            Spacer(Modifier.height(28.dp))
            Reveal(3) {
                Column(verticalArrangement = Arrangement.spacedBy(8.dp)) {
                    RecapPeriod.entries.chunked(2).forEach { row ->
                        Row(horizontalArrangement = Arrangement.spacedBy(8.dp)) {
                            row.forEach { p ->
                                val sel = p == period
                                Text(
                                    p.label,
                                    fontSize = 14.sp,
                                    fontWeight = FontWeight.SemiBold,
                                    color = if (sel) Ink else Color.White,
                                    modifier = Modifier
                                        .clip(CircleShape)
                                        .background(if (sel) Color.White else Color.White.copy(alpha = 0.16f))
                                        .border(1.dp, Color.White.copy(alpha = if (sel) 0f else 0.25f), CircleShape)
                                        .clickable { onPeriod(p) }
                                        .padding(horizontal = 18.dp, vertical = 10.dp)
                                )
                            }
                        }
                    }
                }
            }
            if (!data.isEmpty) {
                Spacer(Modifier.height(34.dp))
                Reveal(4) { Text("Ketuk untuk mulai  →", fontSize = 14.sp, color = Color.White.copy(alpha = 0.7f)) }
            }
        }
    }
}

@Composable
private fun TotalSlide(data: RecapData) {
    var go by remember { mutableStateOf(false) }
    LaunchedEffect(Unit) { go = true }
    val shown by animateIntAsState(
        targetValue = if (go) data.minutes else 0,
        animationSpec = tween(1700, easing = FastOutSlowInEasing),
        label = "countUp"
    )
    val perDay = if (data.activeDays > 0) data.minutes / data.activeDays else 0
    SlideFrame(1) {
        Column(Modifier.fillMaxSize(), verticalArrangement = Arrangement.Center) {
            Reveal(0) { Kicker("Kamu mendengarkan selama") }
            Text("$shown", fontSize = 112.sp, lineHeight = 112.sp, fontWeight = FontWeight.Black, color = Color.White)
            Reveal(1) { Text("menit", fontSize = 34.sp, fontWeight = FontWeight.Bold, color = Color.White) }
            Spacer(Modifier.height(18.dp))
            Reveal(2) {
                Text(
                    "Itu sekitar ${fmtDuration(data.minutes)}, tersebar di ${data.activeDays} hari aktif.",
                    fontSize = 17.sp,
                    color = Color.White.copy(alpha = 0.88f)
                )
            }
            if (perDay > 0) {
                Spacer(Modifier.height(10.dp))
                Reveal(3) {
                    Text("Rata-rata $perDay menit di setiap hari kamu mendengarkan.", fontSize = 15.sp, color = Color.White.copy(alpha = 0.7f))
                }
            }
        }
    }
}

@Composable
private fun TopSongSlide(data: RecapData) {
    val top = data.songs.first()
    var go by remember { mutableStateOf(false) }
    LaunchedEffect(Unit) { go = true }
    val scale by animateFloatAsState(if (go) 1f else 0.75f, tween(700, easing = FastOutSlowInEasing), label = "coverScale")
    SlideFrame(2) {
        Column(Modifier.fillMaxSize(), horizontalAlignment = Alignment.CenterHorizontally, verticalArrangement = Arrangement.Center) {
            Reveal(0) { Kicker("Lagu nomor satu") }
            Spacer(Modifier.height(22.dp))
            AsyncImage(
                model = top.art,
                contentDescription = null,
                contentScale = ContentScale.Crop,
                modifier = Modifier
                    .size(236.dp)
                    .graphicsLayer {
                        scaleX = scale
                        scaleY = scale
                    }
                    .shadow(24.dp, RoundedCornerShape(28.dp))
                    .clip(RoundedCornerShape(28.dp))
                    .background(Color.White.copy(alpha = 0.12f))
            )
            Spacer(Modifier.height(26.dp))
            Reveal(1) {
                Text(
                    top.title,
                    fontSize = 28.sp,
                    lineHeight = 32.sp,
                    fontWeight = FontWeight.Black,
                    color = Color.White,
                    textAlign = TextAlign.Center,
                    maxLines = 2,
                    overflow = TextOverflow.Ellipsis
                )
            }
            if (top.artist.isNotBlank()) {
                Reveal(2) { Text(top.artist, fontSize = 17.sp, color = Color.White.copy(alpha = 0.85f), maxLines = 1, overflow = TextOverflow.Ellipsis) }
            }
            Spacer(Modifier.height(14.dp))
            Reveal(3) {
                Text(
                    "Diputar ${top.plays} kali · ${fmtDuration(top.seconds / 60)}",
                    fontSize = 14.sp,
                    fontWeight = FontWeight.SemiBold,
                    color = Color.White,
                    modifier = Modifier
                        .clip(CircleShape)
                        .background(Color.White.copy(alpha = 0.18f))
                        .padding(horizontal = 16.dp, vertical = 8.dp)
                )
            }
        }
    }
}

@Composable
private fun SongListSlide(data: RecapData) {
    SlideFrame(3) {
        Column(Modifier.fillMaxSize(), verticalArrangement = Arrangement.Center) {
            Reveal(0) { Kicker("Lima lagu teratas") }
            Spacer(Modifier.height(18.dp))
            data.songs.forEachIndexed { i, s ->
                Reveal(i + 1, Modifier.padding(vertical = 7.dp)) {
                    Row(Modifier.fillMaxWidth(), verticalAlignment = Alignment.CenterVertically) {
                        Text(
                            "${i + 1}",
                            fontSize = 30.sp,
                            fontWeight = FontWeight.Black,
                            color = Color.White.copy(alpha = if (i == 0) 1f else 0.5f),
                            modifier = Modifier.width(36.dp)
                        )
                        AsyncImage(
                            model = s.art,
                            contentDescription = null,
                            contentScale = ContentScale.Crop,
                            modifier = Modifier
                                .size(54.dp)
                                .clip(RoundedCornerShape(12.dp))
                                .background(Color.White.copy(alpha = 0.12f))
                        )
                        Spacer(Modifier.width(14.dp))
                        Column(Modifier.weight(1f)) {
                            Text(s.title, fontSize = 16.sp, fontWeight = FontWeight.Bold, color = Color.White, maxLines = 1, overflow = TextOverflow.Ellipsis)
                            if (s.artist.isNotBlank()) {
                                Text(s.artist, fontSize = 13.sp, color = Color.White.copy(alpha = 0.7f), maxLines = 1, overflow = TextOverflow.Ellipsis)
                            }
                        }
                        Text("${s.plays}×", fontSize = 14.sp, fontWeight = FontWeight.SemiBold, color = Color.White.copy(alpha = 0.85f))
                    }
                }
            }
        }
    }
}

/** Foto artis dari cache perangkat; kalau belum ada, dicari sekali di latar belakang. */
@Composable
private fun rememberArtistPhoto(name: String): String? {
    var url by remember(name) { mutableStateOf(ArtistPhotos.cached(name)) }
    LaunchedEffect(name) {
        if (url == null) url = ArtistPhotos.fetch(name)
    }
    return url
}

/** Foto bulat artis; kalau tidak ada fotonya, tampil huruf pertama di atas gradasi. */
@Composable
private fun ArtistAvatar(name: String, size: androidx.compose.ui.unit.Dp, modifier: Modifier = Modifier) {
    val url = rememberArtistPhoto(name)
    Box(
        modifier
            .size(size)
            .clip(CircleShape)
            .background(Brush.linearGradient(listOf(Color.White.copy(alpha = 0.30f), Color.White.copy(alpha = 0.10f)))),
        contentAlignment = Alignment.Center
    ) {
        Text(
            name.trim().take(1).uppercase(),
            fontSize = (size.value * 0.42f).sp,
            fontWeight = FontWeight.Black,
            color = Color.White.copy(alpha = 0.85f)
        )
        if (url != null) {
            AsyncImage(
                model = url,
                contentDescription = name,
                contentScale = ContentScale.Crop,
                modifier = Modifier.fillMaxSize()
            )
        }
    }
}

@Composable
private fun ArtistSlide(data: RecapData) {
    SlideFrame(4) {
        Column(Modifier.fillMaxSize(), horizontalAlignment = Alignment.CenterHorizontally, verticalArrangement = Arrangement.Center) {
            if (data.artists.isEmpty()) {
                Reveal(0) { Text("Belum ada data artis.", fontSize = 20.sp, color = Color.White) }
                return@Column
            }
            val top = data.artists.first()
            var go by remember { mutableStateOf(false) }
            LaunchedEffect(Unit) { go = true }
            val pop by animateFloatAsState(if (go) 1f else 0.7f, tween(700, easing = FastOutSlowInEasing), label = "artistPop")
            Reveal(0) { Kicker("Artis yang paling menemanimu") }
            Spacer(Modifier.height(18.dp))
            // Foto artis nomor satu: lingkaran besar dengan cincin cahaya.
            Box(
                Modifier
                    .graphicsLayer {
                        scaleX = pop
                        scaleY = pop
                    },
                contentAlignment = Alignment.Center
            ) {
                Box(
                    Modifier
                        .size(190.dp)
                        .clip(CircleShape)
                        .background(Brush.sweepGradient(listOf(Color.White.copy(alpha = 0.9f), Color.White.copy(alpha = 0.15f), Color.White.copy(alpha = 0.9f))))
                )
                ArtistAvatar(top.name, 176.dp)
            }
            Spacer(Modifier.height(16.dp))
            Reveal(1) {
                Text(
                    top.name,
                    fontSize = 32.sp,
                    lineHeight = 36.sp,
                    fontWeight = FontWeight.Black,
                    color = Color.White,
                    textAlign = TextAlign.Center,
                    maxLines = 2,
                    overflow = TextOverflow.Ellipsis
                )
            }
            Spacer(Modifier.height(6.dp))
            Reveal(2) {
                Text(
                    "${fmtDuration(top.seconds / 60)} bersamanya · ${top.plays}× putar",
                    fontSize = 14.sp,
                    fontWeight = FontWeight.SemiBold,
                    color = Color.White,
                    modifier = Modifier
                        .clip(CircleShape)
                        .background(Color.White.copy(alpha = 0.18f))
                        .padding(horizontal = 14.dp, vertical = 7.dp)
                )
            }
            Spacer(Modifier.height(22.dp))
            data.artists.drop(1).forEachIndexed { i, a ->
                Reveal(i + 3, Modifier.padding(vertical = 5.dp)) {
                    Row(Modifier.fillMaxWidth(), verticalAlignment = Alignment.CenterVertically) {
                        Text("${i + 2}", fontSize = 18.sp, fontWeight = FontWeight.Black, color = Color.White.copy(alpha = 0.55f), modifier = Modifier.width(26.dp))
                        ArtistAvatar(a.name, 40.dp)
                        Spacer(Modifier.width(12.dp))
                        Column(Modifier.weight(1f)) {
                            Row(Modifier.fillMaxWidth(), verticalAlignment = Alignment.CenterVertically) {
                                Text(a.name, fontSize = 15.sp, fontWeight = FontWeight.SemiBold, color = Color.White, maxLines = 1, overflow = TextOverflow.Ellipsis, modifier = Modifier.weight(1f))
                                Text("${a.seconds / 60} mnt", fontSize = 12.sp, color = Color.White.copy(alpha = 0.7f))
                            }
                            Spacer(Modifier.height(5.dp))
                            Box(Modifier.fillMaxWidth().height(5.dp).clip(CircleShape).background(Color.White.copy(alpha = 0.16f))) {
                                Box(
                                    Modifier
                                        .fillMaxWidth((a.seconds.toFloat() / top.seconds.toFloat().coerceAtLeast(1f)).coerceIn(0.04f, 1f))
                                        .height(5.dp)
                                        .clip(CircleShape)
                                        .background(Color.White.copy(alpha = 0.9f))
                                )
                            }
                        }
                    }
                }
            }
        }
    }
}

@Composable
private fun RhythmSlide(data: RecapData) {
    // Total menit per hari dalam minggu (Senin = 0).
    val week = IntArray(7)
    data.byDay.forEach { (k, sec) ->
        runCatching { LocalDate.parse(k) }.getOrNull()?.let { week[it.dayOfWeek.value - 1] += sec }
    }
    val maxWeek = (week.maxOrNull() ?: 0).coerceAtLeast(1)
    val peakIdx = week.indices.maxByOrNull { week[it] } ?: 0
    val busiest = data.byDay.maxByOrNull { it.value }
    val labels = listOf("Sen", "Sel", "Rab", "Kam", "Jum", "Sab", "Min")
    val dayNames = listOf("Senin", "Selasa", "Rabu", "Kamis", "Jumat", "Sabtu", "Minggu")
    SlideFrame(5) {
        Column(Modifier.fillMaxSize(), verticalArrangement = Arrangement.Center) {
            Reveal(0) { Kicker("Ritme mendengarmu") }
            Spacer(Modifier.height(8.dp))
            Reveal(1) {
                Text("Paling sering di hari ${dayNames[peakIdx]}", fontSize = 30.sp, lineHeight = 34.sp, fontWeight = FontWeight.Black, color = Color.White)
            }
            Spacer(Modifier.height(24.dp))
            Reveal(2) {
                Row(
                    Modifier.fillMaxWidth().height(150.dp),
                    horizontalArrangement = Arrangement.spacedBy(10.dp),
                    verticalAlignment = Alignment.Bottom
                ) {
                    week.forEachIndexed { i, sec ->
                        Column(Modifier.weight(1f).fillMaxHeight(), horizontalAlignment = Alignment.CenterHorizontally, verticalArrangement = Arrangement.Bottom) {
                            Box(
                                Modifier
                                    .fillMaxWidth()
                                    .fillMaxHeight((sec.toFloat() / maxWeek * 0.82f).coerceAtLeast(0.03f))
                                    .clip(RoundedCornerShape(topStart = 10.dp, topEnd = 10.dp))
                                    .background(Color.White.copy(alpha = if (i == peakIdx) 1f else 0.35f))
                            )
                            Spacer(Modifier.height(6.dp))
                            Text(labels[i], fontSize = 11.sp, color = Color.White.copy(alpha = if (i == peakIdx) 1f else 0.65f))
                        }
                    }
                }
            }
            Spacer(Modifier.height(26.dp))
            if (busiest != null) {
                val d = runCatching { LocalDate.parse(busiest.key) }.getOrNull()
                if (d != null) {
                    Reveal(3) {
                        Text(
                            "Hari tersibukmu: ${d.dayOfWeek.getDisplayName(TextStyle.FULL, ID)}, ${d.dayOfMonth} ${d.month.getDisplayName(TextStyle.SHORT, ID)} dengan ${fmtDuration(busiest.value / 60)}.",
                            fontSize = 16.sp,
                            color = Color.White.copy(alpha = 0.9f)
                        )
                    }
                }
            }
            Spacer(Modifier.height(16.dp))
            Reveal(4) {
                Row(
                    Modifier
                        .clip(RoundedCornerShape(20.dp))
                        .background(Color.White.copy(alpha = 0.16f))
                        .padding(horizontal = 16.dp, vertical = 12.dp),
                    verticalAlignment = Alignment.CenterVertically
                ) {
                    FlameIcon(data.streak.tier.level, 36.dp)
                    Spacer(Modifier.width(12.dp))
                    Column {
                        Text("Streak ${data.streak.current} hari", fontSize = 16.sp, fontWeight = FontWeight.Bold, color = Color.White)
                        Text("Terbaikmu ${data.streak.best} hari", fontSize = 12.sp, color = Color.White.copy(alpha = 0.75f))
                    }
                }
            }
        }
    }
}

@Composable
private fun FinalSlide(data: RecapData, onClose: () -> Unit, onShare: () -> Unit) {
    SlideFrame(6) {
        Column(Modifier.fillMaxSize(), verticalArrangement = Arrangement.Center) {
            Reveal(0) { Kicker("Itu dia") }
            Spacer(Modifier.height(8.dp))
            Reveal(1) { Text("Rekapmu,\n${data.title}", fontSize = 40.sp, lineHeight = 44.sp, fontWeight = FontWeight.Black, color = Color.White) }
            Spacer(Modifier.height(22.dp))
            Reveal(2) {
                Column(
                    Modifier
                        .fillMaxWidth()
                        .clip(RoundedCornerShape(24.dp))
                        .background(Color.White.copy(alpha = 0.14f))
                        .border(1.dp, Color.White.copy(alpha = 0.2f), RoundedCornerShape(24.dp))
                        .padding(20.dp),
                    verticalArrangement = Arrangement.spacedBy(14.dp)
                ) {
                    SummaryLine("Waktu mendengar", fmtDuration(data.minutes))
                    SummaryLine("Hari aktif", "${data.activeDays} hari")
                    data.songs.firstOrNull()?.let { SummaryLine("Lagu #1", it.title) }
                    data.artists.firstOrNull()?.let {
                        Row(verticalAlignment = Alignment.CenterVertically) {
                            ArtistAvatar(it.name, 38.dp)
                            Spacer(Modifier.width(12.dp))
                            Box(Modifier.weight(1f)) { SummaryLine("Artis #1", it.name) }
                        }
                    }
                }
            }
            Spacer(Modifier.height(24.dp))
            Reveal(3) {
                Row(horizontalArrangement = Arrangement.spacedBy(10.dp)) {
                    PillTextButton("Tutup", onClick = onClose, modifier = Modifier.weight(1f), primary = false)
                    PillTextButton("Bagikan", onClick = onShare, modifier = Modifier.weight(1f), accent = Color.White)
                }
            }
        }
    }
}

@Composable
private fun SummaryLine(label: String, value: String) {
    Column {
        Text(label, fontSize = 12.sp, color = Color.White.copy(alpha = 0.7f))
        Text(value, fontSize = 18.sp, fontWeight = FontWeight.Bold, color = Color.White, maxLines = 1, overflow = TextOverflow.Ellipsis)
    }
}
