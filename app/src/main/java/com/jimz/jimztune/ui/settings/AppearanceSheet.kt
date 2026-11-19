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
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.rounded.Check
import androidx.compose.material3.Icon
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clip
import androidx.compose.ui.graphics.Brush
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.semantics.Role
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.text.style.TextOverflow
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import com.jimz.jimztune.AppGraph
import com.jimz.jimztune.ui.components.ChoiceRow
import com.jimz.jimztune.ui.components.JimzBottomSheet
import com.jimz.jimztune.ui.components.LabeledSlider
import com.jimz.jimztune.ui.components.PillTextButton
import com.jimz.jimztune.ui.components.SectionHeader
import com.jimz.jimztune.ui.components.SwitchRow
import com.jimz.jimztune.ui.theme.AppTheme
import com.jimz.jimztune.ui.theme.BgFx
import com.jimz.jimztune.ui.theme.Corner
import com.jimz.jimztune.ui.theme.DefaultCoverTheme
import com.jimz.jimztune.ui.theme.MiniBg
import com.jimz.jimztune.ui.theme.PlayerBg
import com.jimz.jimztune.ui.theme.ThemeGroup
import com.jimz.jimztune.ui.theme.ThemeMode
import com.jimz.jimztune.ui.theme.ThemePreset
import com.jimz.jimztune.ui.theme.UiStyle
import com.jimz.jimztune.ui.theme.appBackground
import com.jimz.jimztune.ui.theme.cardSurface

/** Tampilan & tema: pratinjau langsung, preset warna, warna kustom, gaya kartu, sudut, latar, pemutar. */
@Composable
fun AppearanceSheet(onDismiss: () -> Unit) {
    JimzBottomSheet(onDismiss = onDismiss) {
        Column(Modifier.verticalScroll(rememberScrollState()).padding(horizontal = 24.dp)) {
            Text("Tampilan & tema", style = MaterialTheme.typography.titleLarge, fontWeight = FontWeight.Bold)
            Text(
                if (AppGraph.auth.isLoggedIn) "Tersimpan di akunmu, jadi ikut pindah saat ganti HP."
                else "Masuk ke akun supaya pilihan tema ikut tersimpan.",
                style = MaterialTheme.typography.bodySmall,
                color = MaterialTheme.colorScheme.onSurfaceVariant,
                modifier = Modifier.padding(top = 2.dp, bottom = 14.dp)
            )

            ThemePreview()

            SectionHeader("Mode")
            ChoiceRow("Mode", ThemeMode.entries, AppTheme.mode, { it.label }, AppTheme::updateMode)
            Hint(AppTheme.mode.hint)

            SectionHeader("Tema warna")
            if (AppTheme.mode == ThemeMode.DYNAMIC) {
                Hint("Mode Dinamis memakai warna wallpaper, jadi tema warna di bawah diabaikan (di bawah Android 12 dipakai tema biasa).")
            }
            ThemeGroup.entries.forEach { g ->
                val list = ThemePreset.entries.filter { it.group == g }
                Text(
                    g.label,
                    style = MaterialTheme.typography.labelMedium,
                    color = Color.White.copy(alpha = 0.6f),
                    modifier = Modifier.padding(top = 6.dp, bottom = 8.dp)
                )
                Grid(list.size, 3) { i, m ->
                    PresetSwatch(list[i], !AppTheme.customOn && AppTheme.preset == list[i], { AppTheme.updatePreset(list[i]) }, m)
                }
            }

            SectionHeader("Warna sendiri")
            SwitchRow(
                "Pakai warna kustom",
                "Atur sendiri rona dan kepekatan warna tema",
                AppTheme.customOn,
                { AppTheme.updateCustom(it) }
            )
            if (AppTheme.customOn) {
                Box(
                    Modifier
                        .fillMaxWidth()
                        .padding(top = 6.dp, bottom = 2.dp)
                        .height(10.dp)
                        .clip(CircleShape)
                        .background(Brush.horizontalGradient((0..6).map { Color.hsv(it * 60f, 0.75f, 1f) }))
                )
                LabeledSlider(
                    "Rona", "${AppTheme.customHue.toInt()}°", AppTheme.customHue, 0f..360f,
                    { AppTheme.updateCustom(true, hue = it) }
                )
                LabeledSlider(
                    "Kepekatan", "${(AppTheme.customSat * 100).toInt()}%", AppTheme.customSat, 0.2f..1f,
                    { AppTheme.updateCustom(true, sat = it) }
                )
            }

            SectionHeader("Gaya kartu")
            Grid(UiStyle.entries.size, 2) { i, m ->
                StyleTile(UiStyle.entries[i], AppTheme.uiStyle == UiStyle.entries[i], { AppTheme.updateStyle(UiStyle.entries[i]) }, m)
            }
            Hint(AppTheme.uiStyle.hint)

            SectionHeader("Bentuk")
            ChoiceRow("Kebulatan sudut", Corner.entries, AppTheme.corner, { it.label }, AppTheme::updateCorner)

            SectionHeader("Latar")
            ChoiceRow("Latar aplikasi", BgFx.entries, AppTheme.bgFx, { it.label }, AppTheme::updateBgFx)
            Hint(AppTheme.bgFx.hint)
            ChoiceRow("Latar layar pemutar", PlayerBg.entries, AppTheme.playerBg, { it.label }, AppTheme::updatePlayerBg)
            ChoiceRow("Latar mini-player", MiniBg.entries, AppTheme.miniBg, { it.label }, AppTheme::updateMiniBg)

            Spacer(Modifier.height(18.dp))
            PillTextButton(
                text = "Kembalikan ke bawaan",
                onClick = { AppTheme.resetAll() },
                primary = false,
                modifier = Modifier.fillMaxWidth()
            )
            Spacer(Modifier.height(16.dp))
        }
    }
}

@Composable
private fun Hint(text: String) {
    Text(
        text,
        style = MaterialTheme.typography.bodySmall,
        color = MaterialTheme.colorScheme.onSurfaceVariant,
        modifier = Modifier.padding(top = 2.dp, bottom = 4.dp)
    )
}

/** Baris-baris item [perRow] per baris (tanpa grid lazy supaya aman di dalam kolom yang bisa digulir). */
@Composable
private fun Grid(count: Int, perRow: Int, item: @Composable (Int, Modifier) -> Unit) {
    (0 until count).chunked(perRow).forEach { idx ->
        Row(Modifier.fillMaxWidth().padding(bottom = 10.dp), horizontalArrangement = Arrangement.spacedBy(10.dp)) {
            idx.forEach { i -> item(i, Modifier.weight(1f)) }
            repeat(perRow - idx.size) { Spacer(Modifier.weight(1f)) }
        }
    }
}

/** Pratinjau langsung: latar, kartu, tombol, dan chip memakai pilihan yang sedang aktif. */
@Composable
private fun ThemePreview() {
    val c = AppTheme.colors
    val outer = RoundedCornerShape(AppTheme.round(24.dp))
    val inner = RoundedCornerShape(AppTheme.round(16.dp))
    Box(
        Modifier
            .fillMaxWidth()
            .height(178.dp)
            .clip(outer)
            .appBackground(DefaultCoverTheme)
            .border(1.dp, Color.White.copy(alpha = 0.12f), outer)
            .padding(14.dp)
    ) {
        Column(verticalArrangement = Arrangement.spacedBy(10.dp)) {
            Row(verticalAlignment = Alignment.CenterVertically) {
                Text("Aoto", color = Color.White, fontSize = 20.sp, fontWeight = FontWeight.Black)
                Spacer(Modifier.weight(1f))
                Box(Modifier.size(22.dp).clip(CircleShape).background(Brush.linearGradient(listOf(c.accent, c.accentSoft))))
            }
            Row(horizontalArrangement = Arrangement.spacedBy(10.dp)) {
                repeat(2) { n ->
                    Column(
                        Modifier
                            .weight(1f)
                            .height(62.dp)
                            .cardSurface(inner)
                            .clip(inner)
                            .padding(10.dp),
                        verticalArrangement = Arrangement.spacedBy(6.dp)
                    ) {
                        Box(Modifier.width(if (n == 0) 56.dp else 40.dp).height(6.dp).clip(CircleShape).background(Color.White.copy(alpha = 0.85f)))
                        Box(Modifier.width(if (n == 0) 34.dp else 52.dp).height(5.dp).clip(CircleShape).background(Color.White.copy(alpha = 0.4f)))
                        Box(Modifier.width(22.dp).height(5.dp).clip(CircleShape).background(c.accent))
                    }
                }
            }
            Row(horizontalArrangement = Arrangement.spacedBy(8.dp), verticalAlignment = Alignment.CenterVertically) {
                Box(
                    Modifier
                        .clip(CircleShape)
                        .background(Brush.horizontalGradient(listOf(c.accent, c.accentSoft)))
                        .padding(horizontal = 18.dp, vertical = 8.dp)
                ) { Text("Putar", color = c.background, fontSize = 13.sp, fontWeight = FontWeight.Bold) }
                Box(
                    Modifier
                        .clip(CircleShape)
                        .background(c.accent.copy(alpha = 0.28f))
                        .border(1.dp, c.accent.copy(alpha = 0.75f), CircleShape)
                        .padding(horizontal = 14.dp, vertical = 7.dp)
                ) { Text("Favorit", color = Color.White, fontSize = 12.sp, fontWeight = FontWeight.Bold) }
                Box(
                    Modifier
                        .clip(CircleShape)
                        .background(Color.White.copy(alpha = 0.08f))
                        .padding(horizontal = 14.dp, vertical = 7.dp)
                ) { Text("Playlist", color = Color.White.copy(alpha = 0.8f), fontSize = 12.sp) }
            }
        }
    }
}

/** Kotak preset: warna latar, permukaan, dan aksen tema. Yang terpilih diberi garis tepi dan centang. */
@Composable
private fun PresetSwatch(preset: ThemePreset, selected: Boolean, onClick: () -> Unit, modifier: Modifier = Modifier) {
    val accent = AppTheme.colors.accent
    val shape = RoundedCornerShape(AppTheme.round(16.dp))
    Column(modifier.clickable(role = Role.RadioButton, onClick = onClick), horizontalAlignment = Alignment.CenterHorizontally) {
        Box(
            Modifier
                .fillMaxWidth()
                .height(64.dp)
                .clip(shape)
                .background(Brush.linearGradient(listOf(preset.high, preset.bg)))
                .border(if (selected) 2.dp else 1.dp, if (selected) preset.accent else Color.White.copy(alpha = 0.12f), shape)
        ) {
            Column(Modifier.padding(start = 10.dp, top = 11.dp)) {
                Box(Modifier.width(28.dp).height(4.dp).clip(CircleShape).background(Color.White.copy(alpha = 0.85f)))
                Spacer(Modifier.height(4.dp))
                Box(Modifier.width(18.dp).height(3.dp).clip(CircleShape).background(preset.soft.copy(alpha = 0.7f)))
            }
            Box(
                Modifier
                    .align(Alignment.BottomEnd)
                    .padding(8.dp)
                    .size(22.dp)
                    .clip(CircleShape)
                    .background(Brush.linearGradient(listOf(preset.accent, preset.soft))),
                contentAlignment = Alignment.Center
            ) {
                if (selected) Icon(Icons.Rounded.Check, contentDescription = "Terpilih", tint = preset.bg, modifier = Modifier.size(14.dp))
            }
        }
        Text(
            preset.label,
            style = MaterialTheme.typography.labelSmall,
            fontWeight = if (selected) FontWeight.Bold else FontWeight.Medium,
            color = Color.White.copy(alpha = if (selected) 1f else 0.7f),
            maxLines = 1,
            overflow = TextOverflow.Ellipsis,
            modifier = Modifier.padding(top = 5.dp)
        )
        if (selected) Box(Modifier.padding(top = 3.dp).size(5.dp).clip(CircleShape).background(accent))
    }
}

/** Kotak gaya kartu: contoh kartu sungguhan di atas latar bergradasi supaya efek kaca / neon kelihatan. */
@Composable
private fun StyleTile(style: UiStyle, selected: Boolean, onClick: () -> Unit, modifier: Modifier = Modifier) {
    val c = AppTheme.colors
    val outer = RoundedCornerShape(AppTheme.round(16.dp))
    val inner = RoundedCornerShape(AppTheme.round(12.dp))
    Column(modifier.clickable(role = Role.RadioButton, onClick = onClick)) {
        Box(
            Modifier
                .fillMaxWidth()
                .height(78.dp)
                .clip(outer)
                .background(Brush.linearGradient(listOf(c.accent.copy(alpha = 0.55f), c.background, c.accentSoft.copy(alpha = 0.35f))))
                .border(if (selected) 2.dp else 1.dp, if (selected) c.accent else Color.White.copy(alpha = 0.12f), outer)
                .padding(12.dp)
        ) {
            Column(
                Modifier
                    .fillMaxWidth()
                    .height(50.dp)
                    .cardSurface(inner, style)
                    .clip(inner)
                    .padding(horizontal = 10.dp, vertical = 9.dp),
                verticalArrangement = Arrangement.spacedBy(5.dp)
            ) {
                Box(Modifier.width(46.dp).height(5.dp).clip(CircleShape).background(Color.White.copy(alpha = 0.85f)))
                Box(Modifier.width(30.dp).height(4.dp).clip(CircleShape).background(Color.White.copy(alpha = 0.4f)))
                Box(Modifier.width(18.dp).height(4.dp).clip(CircleShape).background(c.accent))
            }
        }
        Text(
            style.label,
            style = MaterialTheme.typography.labelMedium,
            fontWeight = if (selected) FontWeight.Bold else FontWeight.Medium,
            color = Color.White.copy(alpha = if (selected) 1f else 0.7f),
            modifier = Modifier.padding(top = 5.dp, start = 2.dp)
        )
    }
}
