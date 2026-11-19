package com.jimz.jimztune.ui.player

import androidx.compose.foundation.background
import androidx.compose.foundation.clickable
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.layout.width
import androidx.compose.foundation.shape.CircleShape
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.rounded.Add
import androidx.compose.material.icons.rounded.AutoAwesome
import androidx.compose.material.icons.rounded.BlurOn
import androidx.compose.material.icons.rounded.Check
import androidx.compose.material.icons.rounded.Language
import androidx.compose.material.icons.rounded.Share
import androidx.compose.material.icons.rounded.Timer
import androidx.compose.material.icons.rounded.TouchApp
import androidx.compose.material.icons.rounded.Visibility
import androidx.compose.material.icons.rounded.FormatAlignCenter
import androidx.compose.material.icons.rounded.FormatAlignLeft
import androidx.compose.material.icons.rounded.Refresh
import androidx.compose.material.icons.rounded.Remove
import androidx.compose.material.icons.rounded.SwapHoriz
import androidx.compose.material.icons.rounded.TextFields
import androidx.compose.material.icons.rounded.Translate
import androidx.compose.material.icons.rounded.Tune
import androidx.compose.material3.Icon
import androidx.compose.material3.IconButton
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.Switch
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.setValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clip
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.unit.dp
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.rememberScrollState
import androidx.compose.foundation.verticalScroll
import com.jimz.jimztune.ui.components.ChoiceRow
import com.jimz.jimztune.ui.components.MenuDivider
import com.jimz.jimztune.ui.components.MenuRow
import com.jimz.jimztune.ui.components.OptionChip
import com.jimz.jimztune.ui.components.SectionHeader
import com.jimz.jimztune.ui.components.SheetTitle
import com.jimz.jimztune.ui.components.SoftCard
import com.jimz.jimztune.ui.components.SwitchRow
import com.jimz.jimztune.AppGraph
import com.jimz.jimztune.data.lyrics.LyricsState
import com.jimz.jimztune.data.lyrics.TranslationState
import com.jimz.jimztune.ui.components.JimzBottomSheet
import com.jimz.jimztune.ui.components.SheetAction

/** Tombol kecil di pojok halaman lirik yang membuka pengaturan lirik (ukuran, rata teks, terjemahan, sumber). */
@Composable
fun LyricsSettingsButton(
    state: LyricsState.Ready,
    translation: TranslationState,
    onRetry: () -> Unit,
    onNextSource: () -> Unit,
    onTranslationChanged: () -> Unit,
    modifier: Modifier = Modifier,
    positionMs: Long = 0L,
    songTitle: String = "",
    songArtist: String = "",
    onPickSource: (Int) -> Unit = {},
    onStartTapSync: () -> Unit = {}
) {
    var open by remember { mutableStateOf(false) }
    var sharing by remember { mutableStateOf(false) }

    IconButton(
        onClick = { open = true },
        modifier = modifier
            .padding(top = 4.dp)
            .size(40.dp)
            .clip(CircleShape)
            .background(Color.White.copy(alpha = 0.12f))
    ) {
        Icon(Icons.Rounded.Tune, contentDescription = "Pengaturan lirik", tint = Color.White, modifier = Modifier.size(22.dp))
    }

    if (sharing) {
        LyricShareSheet(state.lyrics, positionMs, songTitle, songArtist, onDismiss = { sharing = false })
    }
    if (!open) return
    val prefs = AppGraph.lyricsPrefs
    val sizeNames = listOf("Kecil", "Normal", "Besar", "Sangat besar")

    val names = com.jimz.jimztune.data.lyrics.LyricsPrefs.ANIM_NAMES
    val previews = com.jimz.jimztune.data.lyrics.LyricsPrefs.PREVIEW_NAMES

    JimzBottomSheet(onDismiss = { open = false }) {
        Column(
            Modifier
                .verticalScroll(rememberScrollState())
                .padding(horizontal = 20.dp)
        ) {
            SheetTitle(
                Icons.Rounded.Tune,
                "Pengaturan lirik",
                subtitle = songTitle.ifBlank { null },
                modifier = Modifier.padding(horizontal = 4.dp)
            )

            // ---------------------------------------------------------- tampilan teks
            SectionHeader("Tampilan teks")
            SoftCard {
                ChoiceRow("Ukuran teks", sizeNames.indices.toList(), prefs.sizeLevel, { sizeNames[it] }) { target ->
                    var guard = 0
                    while (prefs.sizeLevel < target && guard++ < 6) prefs.sizeUp()
                    while (prefs.sizeLevel > target && guard++ < 12) prefs.sizeDown()
                }
                ChoiceRow("Rata teks", listOf(0, 1), prefs.align, { if (it == 1) "Tengah" else "Kiri" }) { target ->
                    if (prefs.align != target) prefs.toggleAlign()
                }
                ChoiceRow("Animasi baris", names.indices.toList(), prefs.animStyle, { names.getOrElse(it) { "Karaoke" } }) {
                    prefs.updateAnimStyle(it)
                }
                ChoiceRow("Pratinjau di bawah sampul", previews.indices.toList(), prefs.preview, { previews.getOrElse(it) { "Satu baris" } }) {
                    prefs.updatePreview(it)
                }
                SwitchRow("Buramkan baris lain", "Baris yang belum / sudah lewat dibuat buram", prefs.blurOthers, prefs::updateBlurOthers)
            }

            // ---------------------------------------------------------- bahasa
            SectionHeader("Bahasa")
            SoftCard {
                SwitchRow(
                    "Terjemahan ke Indonesia",
                    when {
                        !prefs.translateEnabled -> "Mati"
                        translation is TranslationState.Loading -> "Menerjemahkan…"
                        translation is TranslationState.NotNeeded -> "Tidak perlu, lirik ini sudah berbahasa Indonesia"
                        translation is TranslationState.Failed -> "Gagal menghubungi layanan terjemahan. Matikan lalu nyalakan lagi untuk mencoba"
                        translation is TranslationState.Ready -> "Dari bahasa ${translation.languageName} (mesin, bisa kurang pas)"
                        else -> "Aktif"
                    },
                    prefs.translateEnabled,
                    {
                        prefs.updateTranslateEnabled(it)
                        onTranslationChanged()
                    }
                )
                if (prefs.translateEnabled) {
                    SwitchRow(
                        "Lagu bahasa Inggris juga",
                        "Untuk lagu yang seluruhnya berbahasa Inggris. Bagian Inggris di lagu Indonesia / Jawa selalu diterjemahkan.",
                        prefs.translateEnglish,
                        {
                            prefs.updateTranslateEnglish(it)
                            onTranslationChanged()
                        }
                    )
                }
                SwitchRow(
                    "Romanisasi (huruf Latin)",
                    "Jepang dibaca per kata termasuk kanji (partikel は → wa), Korea, Arab, Rusia, dst. tampil di bawah liriknya",
                    prefs.romanize,
                    prefs::updateRomanize
                )
            }

            // ---------------------------------------------------------- waktu lirik
            if (state.lyrics.synced) {
                SectionHeader("Waktu lirik")
                SoftCard {
                    val off = prefs.offsetMs(state.songId)
                    Row(verticalAlignment = Alignment.CenterVertically) {
                        Column(Modifier.weight(1f)) {
                            Text(
                                (if (off > 0) "+$off" else "$off") + " ms",
                                style = MaterialTheme.typography.titleLarge,
                                fontWeight = FontWeight.Black
                            )
                            Text("Plus = lirik muncul lebih cepat", style = MaterialTheme.typography.bodySmall, color = Color.White.copy(alpha = 0.6f))
                        }
                        IconButton(onClick = { prefs.setOffsetMs(state.songId, (off - 100).coerceAtLeast(-15_000L)) }) {
                            Icon(Icons.Rounded.Remove, contentDescription = "Lebih lambat")
                        }
                        IconButton(onClick = { prefs.setOffsetMs(state.songId, (off + 100).coerceAtMost(15_000L)) }) {
                            Icon(Icons.Rounded.Add, contentDescription = "Lebih cepat")
                        }
                    }
                    Spacer(Modifier.size(8.dp))
                    Row(horizontalArrangement = Arrangement.spacedBy(8.dp)) {
                        OptionChip("Reset", off == 0L, { prefs.setOffsetMs(state.songId, 0L) })
                        OptionChip("Atur dengan ketuk", false, {
                            open = false
                            onStartTapSync()
                        })
                    }
                }
            }

            // ---------------------------------------------------------- sumber
            if (state.optionCount > 1) {
                SectionHeader("Sumber lirik")
                MenuGroupFlat {
                    state.optionInfos.forEachIndexed { i, info ->
                        if (i > 0) MenuDivider()
                        MenuRow(
                            icon = if (i == state.optionIndex) Icons.Rounded.Check else Icons.Rounded.SwapHoriz,
                            label = info,
                            subtitle = if (i == state.optionIndex) "Sedang dipakai" else "Ketuk untuk memakai sumber ini",
                            onClick = { onPickSource(i) }
                        )
                    }
                }
            }

            // ---------------------------------------------------------- aksi
            SectionHeader("Lainnya")
            MenuGroupFlat {
                MenuRow(
                    icon = Icons.Rounded.Share,
                    label = "Bagikan lirik sebagai gambar",
                    subtitle = "Pilih baris, lalu kirim sebagai kotak atau story",
                    onClick = {
                        open = false
                        sharing = true
                    }
                )
                MenuDivider()
                MenuRow(
                    icon = Icons.Rounded.Refresh,
                    label = "Cari ulang lirik",
                    subtitle = "Abaikan simpanan, ambil lagi dari semua sumber",
                    onClick = {
                        open = false
                        onRetry()
                    }
                )
            }
            Spacer(Modifier.size(20.dp))
        }
    }
}

/** Kelompok baris menu tanpa jarak samping tambahan (sudah berada di dalam kolom ber-padding). */
@Composable
private fun MenuGroupFlat(content: @Composable androidx.compose.foundation.layout.ColumnScope.() -> Unit) {
    com.jimz.jimztune.ui.components.GroupCard(content = content)
}
