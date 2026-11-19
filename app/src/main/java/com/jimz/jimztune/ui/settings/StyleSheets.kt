package com.jimz.jimztune.ui.settings

import androidx.compose.foundation.background
import androidx.compose.foundation.border
import androidx.compose.foundation.clickable
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.layout.width
import androidx.compose.foundation.rememberScrollState
import androidx.compose.foundation.shape.CircleShape
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.foundation.verticalScroll
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.runtime.LaunchedEffect
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableFloatStateOf
import androidx.compose.runtime.mutableIntStateOf
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.setValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.alpha
import androidx.compose.ui.draw.clip
import androidx.compose.ui.graphics.Brush
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.platform.LocalContext
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.text.style.TextOverflow
import androidx.compose.ui.unit.Dp
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import kotlinx.coroutines.delay
import com.jimz.jimztune.island.IslandPrefs
import com.jimz.jimztune.island.IslandTheme
import com.jimz.jimztune.ui.components.ChoiceRow
import com.jimz.jimztune.ui.components.JimzBottomSheet
import com.jimz.jimztune.ui.components.LabeledSlider
import com.jimz.jimztune.ui.components.OptionChip
import com.jimz.jimztune.ui.components.SectionHeader
import com.jimz.jimztune.ui.components.SoftCard
import com.jimz.jimztune.ui.components.SwitchRow
import com.jimz.jimztune.ui.theme.LocalCoverTheme
import com.jimz.jimztune.widget.LyricsWidgetDriver
import com.jimz.jimztune.widget.LyricsWidgetProvider
import com.jimz.jimztune.widget.LyricsWidgetStore
import com.jimz.jimztune.widget.WidgetTheme
import com.jimz.jimztune.widget.WidgetThemes

/** Warna contoh untuk tema yang mengikuti sampul (pratinjau saja). */
private val SAMPLE_COVER = intArrayOf(0xFF8E3FB5.toInt(), 0xFF2A1245.toInt())

private fun brushOf(cols: IntArray): Brush =
    if (cols.size == 1) Brush.linearGradient(listOf(Color(cols[0]), Color(cols[0])))
    else Brush.linearGradient(cols.map { Color(it) })

/** Kotak tema: gradasi contoh + nama. Yang terpilih diberi garis tepi aksen. */
@Composable
private fun ThemeSwatch(
    name: String,
    colors: IntArray,
    light: Boolean,
    selected: Boolean,
    onClick: () -> Unit,
    modifier: Modifier = Modifier
) {
    val accent = LocalCoverTheme.current.accent
    val shape = RoundedCornerShape(16.dp)
    val fg = if (light) Color(0xFF14172B) else Color.White
    Column(modifier.clickable(onClick = onClick), horizontalAlignment = Alignment.CenterHorizontally) {
        Box(
            Modifier
                .fillMaxWidth()
                .height(58.dp)
                .clip(shape)
                .background(Color(0xFF0B0F26))
                .background(brushOf(colors))
                .border(if (selected) 2.dp else 1.dp, if (selected) accent else Color.White.copy(alpha = 0.12f), shape),
            contentAlignment = Alignment.CenterStart
        ) {
            Column(Modifier.padding(start = 10.dp)) {
                Box(Modifier.width(26.dp).height(4.dp).clip(CircleShape).background(fg))
                Spacer(Modifier.height(4.dp))
                Box(Modifier.width(16.dp).height(3.dp).clip(CircleShape).background(fg.copy(alpha = 0.55f)))
            }
            if (selected) {
                Box(
                    Modifier
                        .align(Alignment.TopEnd)
                        .padding(6.dp)
                        .size(8.dp)
                        .clip(CircleShape)
                        .background(accent)
                )
            }
        }
        Text(
            name,
            style = MaterialTheme.typography.labelSmall,
            fontWeight = if (selected) FontWeight.Bold else FontWeight.Medium,
            color = Color.White.copy(alpha = if (selected) 1f else 0.7f),
            maxLines = 1,
            overflow = TextOverflow.Ellipsis,
            modifier = Modifier.padding(top = 5.dp)
        )
    }
}

/** Baris-baris kotak tema, [perRow] per baris (tanpa grid lazy supaya aman di dalam kolom yang bisa digulir). */
@Composable
private fun ThemeGrid(count: Int, perRow: Int, item: @Composable (Int, Modifier) -> Unit) {
    (0 until count).chunked(perRow).forEach { rowIdx ->
        Row(Modifier.fillMaxWidth().padding(bottom = 10.dp), horizontalArrangement = Arrangement.spacedBy(10.dp)) {
            rowIdx.forEach { i -> item(i, Modifier.weight(1f)) }
            repeat(perRow - rowIdx.size) { Spacer(Modifier.weight(1f)) }
        }
    }
}

/** Satu pil Dynamic Island versi pratinjau. */
@Composable
private fun PillPreview(theme: IslandTheme, width: Dp, height: Dp, paused: Boolean, lyricOn: Boolean) {
    val cols = theme.colors ?: SAMPLE_COVER
    val fg = if (theme.light) Color(0xFF14172B) else Color.White
    val accent = if (theme.accent != 0) Color(theme.accent) else Color(0xFFFF7AD9)
    val shape = CircleShape
    Box(
        Modifier
            .width(width)
            .height(height)
            .clip(shape)
            .background(Color(0xFF0B0F26))
            .background(brushOf(cols))
            .border(1.5.dp, accent.copy(alpha = if (paused) 0.25f else 0.7f), shape)
    ) {
        Box(
            Modifier
                .padding(start = 5.dp)
                .size(height - 10.dp)
                .align(Alignment.CenterStart)
                .clip(CircleShape)
                .background(Brush.linearGradient(listOf(Color(0xFFFF8A5B), Color(0xFFB5179E))))
                .alpha(if (paused) 0.7f else 1f)
        )
        if (paused) {
            // Ikon putar kecil
            Text("▶", color = fg, fontSize = 11.sp, modifier = Modifier.align(Alignment.CenterEnd).padding(end = 14.dp))
        } else {
            if (lyricOn) {
                Text(
                    "Baris lirik yang sedang…",
                    color = fg,
                    fontSize = 11.sp,
                    fontWeight = FontWeight.SemiBold,
                    maxLines = 1,
                    overflow = TextOverflow.Ellipsis,
                    modifier = Modifier.align(Alignment.CenterStart).padding(start = 38.dp, end = 30.dp)
                )
            }
            Row(
                Modifier.align(Alignment.CenterEnd).padding(end = 13.dp).height(12.dp),
                horizontalArrangement = Arrangement.spacedBy(2.dp),
                verticalAlignment = Alignment.CenterVertically
            ) {
                listOf(0.5f, 1f, 0.7f, 0.9f).forEach { h ->
                    Box(Modifier.width(2.5.dp).height(12.dp * h).clip(CircleShape).background(accent))
                }
            }
        }
    }
}

@Composable
private fun IslandPreview(style: Int, lyricOn: Boolean) {
    val theme = IslandPrefs.theme(style)
    Column(
        Modifier
            .fillMaxWidth()
            .clip(RoundedCornerShape(22.dp))
            .background(Brush.verticalGradient(listOf(Color(0xFF232A5C), Color(0xFF0A0E24))))
            .padding(vertical = 18.dp),
        horizontalAlignment = Alignment.CenterHorizontally
    ) {
        PillPreview(theme, 220.dp, 38.dp, paused = false, lyricOn = lyricOn)
        Spacer(Modifier.height(6.dp))
        Text("Sedang memutar", style = MaterialTheme.typography.labelSmall, color = Color.White.copy(alpha = 0.55f))
        Spacer(Modifier.height(14.dp))
        PillPreview(theme, 82.dp, 34.dp, paused = true, lyricOn = false)
        Spacer(Modifier.height(6.dp))
        Text("Saat dijeda, ketuk untuk lanjut", style = MaterialTheme.typography.labelSmall, color = Color.White.copy(alpha = 0.55f))
    }
}

/** Dynamic Island: saklar, izin overlay, tema, dan perilaku. */
@Composable
fun IslandSheet(onDismiss: () -> Unit) {
    val context = LocalContext.current
    var granted by remember { mutableStateOf(canDraw(context)) }
    JimzBottomSheet(onDismiss = onDismiss) {
        Column(Modifier.verticalScroll(rememberScrollState()).padding(horizontal = 24.dp)) {
            Text("Dynamic Island", style = MaterialTheme.typography.titleLarge, fontWeight = FontWeight.Bold)
            Text(
                "Pil musik di atas layar saat kamu membuka aplikasi lain. Ketuk untuk membuka kontrol, geser kiri atau kanan untuk ganti lagu, tahan untuk membuka Aoto.",
                style = MaterialTheme.typography.bodyMedium,
                color = Color.White.copy(alpha = 0.7f),
                modifier = Modifier.padding(top = 8.dp, bottom = 14.dp)
            )
            IslandPreview(IslandPrefs.style, IslandPrefs.showLyrics)

            Spacer(Modifier.height(14.dp))
            SoftCard {
                SwitchRow("Aktifkan", "Tampil di atas aplikasi lain saat musik diputar", IslandPrefs.enabled, { on ->
                    granted = canDraw(context)
                    if (on && !granted) requestOverlay(context)
                    IslandPrefs.updateEnabled(on)
                })
                if (!granted) {
                    Text(
                        "Izin \"tampil di atas aplikasi lain\" belum diberikan.",
                        style = MaterialTheme.typography.bodySmall,
                        color = MaterialTheme.colorScheme.error,
                        modifier = Modifier.padding(top = 6.dp)
                    )
                    Row(Modifier.padding(top = 8.dp), horizontalArrangement = Arrangement.spacedBy(8.dp)) {
                        OptionChip("Beri izin", false, { requestOverlay(context) })
                        OptionChip("Sudah, periksa lagi", false, { granted = canDraw(context) })
                    }
                }
            }

            SectionHeader("Tema")
            ThemeGrid(IslandPrefs.THEMES.size, 4) { i, mod ->
                val t = IslandPrefs.THEMES[i]
                ThemeSwatch(
                    name = t.name,
                    colors = t.colors ?: SAMPLE_COVER,
                    light = t.light,
                    selected = IslandPrefs.style == i,
                    onClick = { IslandPrefs.updateStyle(i) },
                    modifier = mod
                )
            }

            SectionHeader("Perilaku")
            SoftCard {
                SwitchRow("Tampilkan lirik", "Baris lirik yang sedang dinyanyikan (kalau lagunya punya lirik bersinkron)", IslandPrefs.showLyrics, IslandPrefs::updateShowLyrics)
                SwitchRow("Buka otomatis saat ganti lagu", "Pil melebar sebentar menampilkan lagu baru", IslandPrefs.autoExpand, IslandPrefs::updateAutoExpand)
                SwitchRow("Tetap tampil saat dijeda", "Pil mengecil (tidak hilang). Ketuk untuk melanjutkan lagu", IslandPrefs.keepPaused, IslandPrefs::updateKeepPaused)
            }
            SectionHeader("Animasi")
            SoftCard {
                SwitchRow("Sampul berputar", "Seperti piringan hitam saat musik diputar", IslandPrefs.spinCover, IslandPrefs::updateSpinCover)
                SwitchRow("Garis tepi berdenyut", "Pendar halus di tepi pil", IslandPrefs.glowEdge, IslandPrefs::updateGlowEdge)
            }
            Spacer(Modifier.height(24.dp))
        }
    }
}

// ---------------------------------------------------------------------------------------------
// Widget lirik
// ---------------------------------------------------------------------------------------------

/** Pratinjau widget: meniru tampilan widget asli (tema, kepekatan, sudut, sampul, baris berikutnya, progres). */
@Composable
private fun WidgetPreview(
    theme: WidgetTheme,
    opacity: Int,
    corner: Int,
    showCover: Boolean,
    showNext: Boolean,
    showProgress: Boolean
) {
    val cols = theme.colors ?: SAMPLE_COVER
    val radius = when (corner) {
        0 -> 8.dp
        2 -> 36.dp
        else -> 24.dp
    }
    val shape = RoundedCornerShape(radius)
    val fg = Color(theme.main)
    val soft = Color(theme.soft)
    Box(
        Modifier
            .fillMaxWidth()
            .clip(RoundedCornerShape(22.dp))
            .background(Brush.linearGradient(listOf(Color(0xFF3B4A7A), Color(0xFF151B3A), Color(0xFF2A1E4A))))
            .padding(16.dp)
    ) {
        Box(
            Modifier
                .fillMaxWidth()
                .height(150.dp)
                .clip(shape)
                .background(Color(0xFF0B0F26).copy(alpha = opacity / 100f))
        ) {
            Box(
                Modifier
                    .matchParentSize()
                    .alpha(opacity / 100f)
                    .background(brushOf(cols))
                    .let { if (theme.stroke != 0) it.border(1.5.dp, Color(theme.stroke), shape) else it }
            )
            Column(Modifier.padding(horizontal = 14.dp, vertical = 12.dp)) {
                Row(verticalAlignment = Alignment.CenterVertically) {
                    if (showCover) {
                        Box(
                            Modifier
                                .size(34.dp)
                                .clip(RoundedCornerShape(7.dp))
                                .background(Brush.linearGradient(listOf(Color(0xFFFF8A5B), Color(0xFFB5179E))))
                        )
                        Spacer(Modifier.width(10.dp))
                    }
                    Column(Modifier.weight(1f)) {
                        Text("Judul Lagu", color = fg, fontSize = 13.sp, fontWeight = FontWeight.Bold, maxLines = 1)
                        Text("Nama Artis", color = soft, fontSize = 11.sp, maxLines = 1)
                    }
                    Text("⏮  ⏸  ⏭", color = fg, fontSize = 13.sp)
                }
                Spacer(Modifier.weight(1f))
                Text("Baris lirik yang sedang dinyanyikan", color = fg, fontSize = 18.sp, fontWeight = FontWeight.Bold, maxLines = 2, lineHeight = 22.sp)
                if (showNext) {
                    Text("Baris berikutnya muncul di sini", color = soft, fontSize = 12.sp, maxLines = 1, modifier = Modifier.padding(top = 3.dp))
                }
                if (showProgress) {
                    Spacer(Modifier.height(8.dp))
                    Row(verticalAlignment = Alignment.CenterVertically) {
                        Text("1:12", color = soft, fontSize = 10.sp)
                        Box(
                            Modifier
                                .weight(1f)
                                .padding(horizontal = 6.dp)
                                .height(4.dp)
                                .clip(CircleShape)
                                .background(Color(theme.track))
                        ) {
                            Box(Modifier.fillMaxWidth(0.4f).height(4.dp).clip(CircleShape).background(fg))
                        }
                        Text("3:05", color = soft, fontSize = 10.sp)
                    }
                }
            }
        }
    }
}

/** Widget lirik: tema, tampilan, dan tombol pasang ke layar utama. Setiap perubahan langsung diterapkan ke widget. */
@Composable
fun LyricsWidgetSheet(onDismiss: () -> Unit) {
    val context = LocalContext.current
    var style by remember { mutableIntStateOf(LyricsWidgetStore.style(context)) }
    var opacity by remember { mutableFloatStateOf(LyricsWidgetStore.opacity(context).toFloat()) }
    var corner by remember { mutableIntStateOf(LyricsWidgetStore.corner(context)) }
    var showCover by remember { mutableStateOf(LyricsWidgetStore.showCover(context)) }
    var showNext by remember { mutableStateOf(LyricsWidgetStore.showNext(context)) }
    var showProgress by remember { mutableStateOf(LyricsWidgetStore.showProgress(context)) }
    var animate by remember { mutableStateOf(LyricsWidgetStore.animate(context)) }

    // Slider tidak langsung mengirim ke widget tiap geseran; tunggu sebentar setelah berhenti.
    LaunchedEffect(opacity) {
        delay(350L)
        if (opacity.toInt() != LyricsWidgetStore.opacity(context)) {
            LyricsWidgetStore.setOpacity(context, opacity.toInt())
            LyricsWidgetProvider.push(context, LyricsWidgetDriver.instance?.currentSnapshot() ?: LyricsWidgetStore.load(context))
        }
    }

    fun apply() {
        LyricsWidgetProvider.push(context, LyricsWidgetDriver.instance?.currentSnapshot() ?: LyricsWidgetStore.load(context))
    }

    JimzBottomSheet(onDismiss = onDismiss) {
        Column(Modifier.verticalScroll(rememberScrollState()).padding(horizontal = 24.dp)) {
            Text("Widget lirik", style = MaterialTheme.typography.titleLarge, fontWeight = FontWeight.Bold)
            Text(
                "Lirik lagu yang sedang diputar di layar utama, baris demi baris.",
                style = MaterialTheme.typography.bodyMedium,
                color = Color.White.copy(alpha = 0.7f),
                modifier = Modifier.padding(top = 8.dp, bottom = 14.dp)
            )
            WidgetPreview(WidgetThemes.get(style), opacity.toInt(), corner, showCover, showNext, showProgress)

            SectionHeader("Tema")
            ThemeGrid(WidgetThemes.ALL.size, 4) { i, mod ->
                val t = WidgetThemes.ALL[i]
                ThemeSwatch(
                    name = t.name,
                    colors = t.colors ?: SAMPLE_COVER,
                    light = t.light,
                    selected = style == i,
                    onClick = {
                        style = i
                        LyricsWidgetStore.setStyle(context, i)
                        apply()
                    },
                    modifier = mod
                )
            }

            SectionHeader("Tampilan")
            SoftCard {
                LabeledSlider(
                    label = "Kepekatan latar",
                    valueText = "${opacity.toInt()}%",
                    value = opacity,
                    range = 30f..100f,
                    onChange = { opacity = it },
                )
                ChoiceRow("Sudut", listOf(0, 1, 2), corner, { listOf("Tajam", "Membulat", "Sangat bulat")[it] }) { v ->
                    corner = v
                    LyricsWidgetStore.setCorner(context, v)
                    apply()
                }
            }

            SectionHeader("Isi widget")
            SoftCard {
                SwitchRow("Tampilkan sampul", "", showCover, { v ->
                    showCover = v
                    LyricsWidgetStore.setShowCover(context, v)
                    apply()
                })
                SwitchRow("Tampilkan baris berikutnya", "", showNext, { v ->
                    showNext = v
                    LyricsWidgetStore.setShowNext(context, v)
                    apply()
                })
                SwitchRow("Tampilkan progres dan waktu", "Ketuk bar untuk loncat ke bagian lagu", showProgress, { v ->
                    showProgress = v
                    LyricsWidgetStore.setShowProgress(context, v)
                    apply()
                })
            }

            Row(Modifier.padding(top = 18.dp, bottom = 24.dp), horizontalArrangement = Arrangement.spacedBy(8.dp)) {
                OptionChip("Pasang widget lirik", true, { LyricsWidgetProvider.requestPin(context) })
            }
        }
    }
}
