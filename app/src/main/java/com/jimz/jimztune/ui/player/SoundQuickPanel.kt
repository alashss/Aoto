package com.jimz.jimztune.ui.player

import androidx.compose.foundation.background
import androidx.compose.foundation.border
import androidx.compose.foundation.clickable
import androidx.compose.foundation.horizontalScroll
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
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.rounded.Add
import androidx.compose.material.icons.rounded.Bedtime
import androidx.compose.material.icons.rounded.ChevronRight
import androidx.compose.material.icons.rounded.GraphicEq
import androidx.compose.material.icons.rounded.Remove
import androidx.compose.material.icons.rounded.Tune
import androidx.compose.material3.Icon
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clip
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.semantics.Role
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.text.style.TextAlign
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import com.jimz.jimztune.player.SleepTimer
import com.jimz.jimztune.player.fx.AudioFx
import com.jimz.jimztune.player.fx.SoundStyles
import com.jimz.jimztune.ui.components.GroupCard
import com.jimz.jimztune.ui.components.IconBadge
import com.jimz.jimztune.ui.components.MenuDivider
import com.jimz.jimztune.ui.components.MenuRow
import com.jimz.jimztune.ui.components.OptionChip
import com.jimz.jimztune.ui.theme.LocalCoverTheme
import kotlin.math.roundToInt

/**
 * Pengatur suara ringkas untuk menu ⋮ di layar pemutar: gaya suara, kecepatan, dan tiga saklar cepat.
 * Kontrol lengkap ada di [AudioFxSheet] (dibuka lewat [onOpenFull]).
 */
@Composable
fun SoundQuickPanel(onOpenFull: () -> Unit, onOpenSleep: () -> Unit) {
    val s = AudioFx.state
    val accent = LocalCoverTheme.current.accent

    GroupCard(modifier = Modifier.padding(horizontal = 20.dp, vertical = 6.dp)) {
        Column(Modifier.padding(horizontal = 16.dp, vertical = 14.dp)) {
            Row(verticalAlignment = Alignment.CenterVertically) {
                IconBadge(Icons.Rounded.GraphicEq, size = 38.dp)
                Spacer(Modifier.width(14.dp))
                Column(Modifier.weight(1f)) {
                    Text("Pengaturan suara", style = MaterialTheme.typography.bodyLarge, fontWeight = FontWeight.SemiBold)
                    Text("Gaya: ${s.style}", style = MaterialTheme.typography.bodySmall, color = Color.White.copy(alpha = 0.55f))
                }
            }

            Spacer(Modifier.height(14.dp))
            Row(
                Modifier.horizontalScroll(rememberScrollState()),
                horizontalArrangement = Arrangement.spacedBy(8.dp)
            ) {
                SoundStyles.all.forEach { st ->
                    OptionChip(st.name, s.style == st.name, { AudioFx.applyStyle(st) })
                }
            }

            Spacer(Modifier.height(14.dp))
            Row(verticalAlignment = Alignment.CenterVertically) {
                Text("Kecepatan", style = MaterialTheme.typography.bodyMedium, modifier = Modifier.weight(1f))
                StepButton(Icons.Rounded.Remove, "Pelankan") {
                    AudioFx.update { copy(speed = (((speed - 0.05f).coerceAtLeast(0.5f)) * 20).roundToInt() / 20f) }
                }
                Text(
                    "%.2fx".format(s.speed),
                    fontSize = 16.sp,
                    fontWeight = FontWeight.Bold,
                    textAlign = TextAlign.Center,
                    modifier = Modifier.width(72.dp)
                )
                StepButton(Icons.Rounded.Add, "Percepat") {
                    AudioFx.update { copy(speed = (((speed + 0.05f).coerceAtMost(2f)) * 20).roundToInt() / 20f) }
                }
            }

            Spacer(Modifier.height(12.dp))
            Row(
                Modifier.horizontalScroll(rememberScrollState()),
                horizontalArrangement = Arrangement.spacedBy(8.dp)
            ) {
                OptionChip("Equalizer", s.eqOn, { AudioFx.update { copy(eqOn = !eqOn) } }, accent)
                OptionChip("8D", s.pan8d, { AudioFx.update { copy(pan8d = !pan8d) } }, accent)
                OptionChip("Lewati hening", s.skipSilence, { AudioFx.update(markCustom = false) { copy(skipSilence = !skipSilence) } }, accent)
                OptionChip("Normalisasi", s.normalize, { AudioFx.update(markCustom = false) { copy(normalize = !normalize) } }, accent)
            }
        }
        MenuDivider()
        MenuRow(
            icon = Icons.Rounded.Tune,
            label = "Kontrol suara lengkap",
            subtitle = "Equalizer, reverb, nada, crossfade",
            onClick = onOpenFull,
            trailing = { Icon(Icons.Rounded.ChevronRight, contentDescription = null, tint = Color.White.copy(alpha = 0.35f)) }
        )
        MenuDivider()
        MenuRow(
            icon = Icons.Rounded.Bedtime,
            label = "Timer tidur",
            subtitle = when {
                SleepTimer.endOfSong -> "Berhenti setelah lagu ini"
                SleepTimer.endAtMs > 0 -> "Aktif"
                else -> "Matikan musik otomatis"
            },
            onClick = onOpenSleep,
            trailing = { Icon(Icons.Rounded.ChevronRight, contentDescription = null, tint = Color.White.copy(alpha = 0.35f)) }
        )
    }
}

@Composable
private fun StepButton(icon: androidx.compose.ui.graphics.vector.ImageVector, desc: String, onClick: () -> Unit) {
    Box(
        Modifier
            .size(36.dp)
            .clip(CircleShape)
            .background(Color.White.copy(alpha = 0.09f))
            .border(1.dp, Color.White.copy(alpha = 0.12f), CircleShape)
            .clickable(role = Role.Button, onClick = onClick),
        contentAlignment = Alignment.Center
    ) {
        Icon(icon, contentDescription = desc, tint = Color.White, modifier = Modifier.size(18.dp))
    }
}
