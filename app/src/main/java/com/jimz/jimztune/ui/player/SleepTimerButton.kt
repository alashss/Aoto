package com.jimz.jimztune.ui.player

import androidx.compose.foundation.background
import com.jimz.jimztune.ui.theme.cardSurface
import androidx.compose.foundation.border
import androidx.compose.foundation.clickable
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.layout.width
import androidx.compose.foundation.rememberScrollState
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.foundation.verticalScroll
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.rounded.Bedtime
import androidx.compose.material.icons.rounded.MusicNote
import androidx.compose.material3.Icon
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.runtime.LaunchedEffect
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableLongStateOf
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.setValue
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
import com.jimz.jimztune.ui.components.JimzBottomSheet
import com.jimz.jimztune.ui.components.PillTextButton
import com.jimz.jimztune.ui.components.SectionHeader
import com.jimz.jimztune.ui.components.SheetTitle
import com.jimz.jimztune.ui.theme.LocalCoverTheme
import kotlinx.coroutines.delay

private val presets = listOf(5, 15, 30, 45, 60, 90)

/** Kapsul kecil "Timer tidur" di layar pemutar; ketuk untuk memilih durasi. Menampilkan sisa waktu saat aktif. */
@Composable
fun SleepTimerButton(modifier: Modifier = Modifier, trailing: @Composable () -> Unit = {}) {
    var open by remember { mutableStateOf(false) }
    var now by remember { mutableLongStateOf(System.currentTimeMillis()) }
    val active = SleepTimer.active
    LaunchedEffect(active) {
        while (active) {
            now = System.currentTimeMillis()
            delay(1_000)
        }
    }

    val label = when {
        SleepTimer.endOfSong -> "Berhenti setelah lagu ini"
        SleepTimer.endAtMs > 0 -> "Tidur ${formatLeft(SleepTimer.endAtMs - now)}"
        else -> "Timer tidur"
    }

    Row(
        modifier = modifier.fillMaxWidth(),
        horizontalArrangement = Arrangement.spacedBy(8.dp, Alignment.CenterHorizontally)
    ) {
        Row(
            modifier = Modifier
                .clip(RoundedCornerShape(50))
                .background(Color.White.copy(alpha = if (active) 0.22f else 0.10f))
                .clickable { open = true }
                .padding(horizontal = 14.dp, vertical = 7.dp),
            verticalAlignment = Alignment.CenterVertically
        ) {
            Icon(Icons.Rounded.Bedtime, contentDescription = null, modifier = Modifier.size(16.dp), tint = Color.White)
            Spacer(Modifier.width(6.dp))
            Text(label, style = MaterialTheme.typography.labelMedium, color = Color.White)
        }
        trailing()
    }

    if (open) SleepTimerSheet(onDismiss = { open = false })
}

/** Sheet pilih durasi timer tidur. Dipakai tombol kapsul di pemutar dan menu ⋮ lagu. */
@Composable
fun SleepTimerSheet(onDismiss: () -> Unit) {
    var now by remember { mutableLongStateOf(System.currentTimeMillis()) }
    val active = SleepTimer.active
    LaunchedEffect(active) {
        while (active) {
            now = System.currentTimeMillis()
            delay(1_000)
        }
    }
    val accent = LocalCoverTheme.current.accent
    JimzBottomSheet(onDismiss = onDismiss) {
        Column(
            Modifier
                .verticalScroll(rememberScrollState())
                .padding(horizontal = 24.dp)
        ) {
            SheetTitle(
                Icons.Rounded.Bedtime,
                "Timer tidur",
                subtitle = "Musik berhenti sendiri saat kamu tertidur"
            )

            if (active) {
                Spacer(Modifier.height(10.dp))
                val shape = RoundedCornerShape(22.dp)
                Column(
                    Modifier
                        .fillMaxWidth()
                        .clip(shape)
                        .background(accent.copy(alpha = 0.2f))
                        .border(1.dp, accent.copy(alpha = 0.5f), shape)
                        .padding(18.dp),
                    horizontalAlignment = Alignment.CenterHorizontally
                ) {
                    Text(
                        if (SleepTimer.endOfSong) "Setelah lagu ini" else formatLeft(SleepTimer.endAtMs - now),
                        fontSize = 38.sp,
                        fontWeight = FontWeight.Black
                    )
                    Text("sampai musik berhenti", style = MaterialTheme.typography.bodySmall, color = Color.White.copy(alpha = 0.65f))
                    Spacer(Modifier.height(12.dp))
                    PillTextButton("Matikan timer", onClick = {
                        SleepTimer.cancel()
                        onDismiss()
                    }, modifier = Modifier.fillMaxWidth(), primary = false)
                }
            }

            SectionHeader("Pilih durasi")
            presets.chunked(3).forEach { row ->
                Row(Modifier.fillMaxWidth().padding(bottom = 10.dp), horizontalArrangement = Arrangement.spacedBy(10.dp)) {
                    row.forEach { min ->
                        val (big, unit) = if (min >= 60) {
                            (if (min % 60 == 0) "${min / 60}" else "${min / 60},5") to "jam"
                        } else {
                            "$min" to "menit"
                        }
                        DurationTile(big, unit, Modifier.weight(1f)) {
                            SleepTimer.startMinutes(min)
                            onDismiss()
                        }
                    }
                }
            }
            val shape = RoundedCornerShape(18.dp)
            Row(
                Modifier
                    .fillMaxWidth()
                    .clip(shape)
                    .cardSurface(shape)
                    .clickable(role = Role.Button) {
                        SleepTimer.startEndOfSong()
                        onDismiss()
                    }
                    .padding(horizontal = 18.dp, vertical = 16.dp),
                verticalAlignment = Alignment.CenterVertically
            ) {
                Icon(Icons.Rounded.MusicNote, contentDescription = null, tint = accent)
                Spacer(Modifier.width(14.dp))
                Column(Modifier.weight(1f)) {
                    Text("Sampai lagu ini selesai", style = MaterialTheme.typography.bodyLarge, fontWeight = FontWeight.Medium)
                    Text("Berhenti begitu lagu yang sedang main habis", style = MaterialTheme.typography.bodySmall, color = Color.White.copy(alpha = 0.55f))
                }
            }
            Spacer(Modifier.height(20.dp))
        }
    }
}

@Composable
private fun DurationTile(big: String, unit: String, modifier: Modifier, onClick: () -> Unit) {
    val shape = RoundedCornerShape(18.dp)
    Column(
        modifier = modifier
            .clip(shape)
            .cardSurface(shape)
            .clickable(role = Role.Button, onClick = onClick)
            .padding(vertical = 16.dp),
        horizontalAlignment = Alignment.CenterHorizontally
    ) {
        Text(big, fontSize = 28.sp, fontWeight = FontWeight.Black, textAlign = TextAlign.Center)
        Text(unit, fontSize = 12.sp, color = Color.White.copy(alpha = 0.6f))
    }
}

private fun formatLeft(ms: Long): String {
    val total = (ms / 1000).coerceAtLeast(0)
    val h = total / 3600
    val m = (total % 3600) / 60
    val s = total % 60
    return if (h > 0) "%d:%02d:%02d".format(h, m, s) else "%d:%02d".format(m, s)
}
