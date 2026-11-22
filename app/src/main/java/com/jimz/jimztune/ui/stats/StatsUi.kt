package com.jimz.jimztune.ui.stats

import androidx.compose.ui.draw.drawBehind
import androidx.compose.animation.core.animateFloat
import android.widget.Toast
import androidx.compose.foundation.background
import androidx.compose.foundation.border
import androidx.compose.foundation.clickable
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.aspectRatio
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
import androidx.compose.material.icons.rounded.ChevronRight
import androidx.compose.material3.Icon
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.setValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clip
import androidx.compose.ui.graphics.Brush
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.graphics.toArgb
import androidx.compose.ui.platform.LocalContext
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.text.style.TextAlign
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import com.jimz.jimztune.AppGraph
import com.jimz.jimztune.data.cloud.SyncStatus
import com.jimz.jimztune.data.stats.PlayLog
import com.jimz.jimztune.data.stats.Streak
import com.jimz.jimztune.data.stats.StreakInfo
import com.jimz.jimztune.ui.components.JimzBottomSheet
import com.jimz.jimztune.ui.components.PillTextButton
import com.jimz.jimztune.ui.components.SectionHeader
import com.jimz.jimztune.ui.components.SoftCard
import com.jimz.jimztune.ui.components.SwitchRow
import com.jimz.jimztune.ui.theme.cardSurface
import com.jimz.jimztune.util.ShareImage
import java.time.LocalDate
import java.time.format.TextStyle
import java.util.Locale

private val ID = Locale("id", "ID")

private fun levelColor(level: Int): Color = flamePalette(level).glow

/** Hari-hari terakhir (paling lama di kiri) sampai hari ini. */
private fun lastDays(n: Int): List<LocalDate> {
    val today = LocalDate.now()
    return (n - 1 downTo 0).map { today.minusDays(it.toLong()) }
}

/** Tujuh hari terakhir: lingkaran kecil per hari dengan inisial hari di bawahnya; hari ini diberi cincin. */
@Composable
private fun WeekDots(info: StreakInfo, modifier: Modifier = Modifier) {
    val tone = levelColor(info.tier.level)
    @Suppress("UNUSED_VARIABLE") val v = PlayLog.version
    val today = LocalDate.now()
    Row(modifier, horizontalArrangement = Arrangement.spacedBy(8.dp)) {
        lastDays(7).forEach { d ->
            val on = Streak.counted(d)
            Column(horizontalAlignment = Alignment.CenterHorizontally) {
                Box(
                    Modifier
                        .size(14.dp)
                        .clip(CircleShape)
                        .background(if (on) tone else Color.White.copy(alpha = 0.12f))
                        .then(if (d == today && !on) Modifier.border(1.5.dp, tone.copy(alpha = 0.85f), CircleShape) else Modifier)
                )
                Text(
                    d.dayOfWeek.getDisplayName(TextStyle.NARROW, ID).uppercase(),
                    fontSize = 9.sp,
                    color = Color.White.copy(alpha = if (d == today) 0.9f else 0.4f),
                    fontWeight = if (d == today) FontWeight.Bold else FontWeight.Normal,
                    modifier = Modifier.padding(top = 3.dp)
                )
            }
        }
    }
}

/** Kartu streak di Beranda. Hilang kalau dimatikan di Setelan. */
@Composable
fun StreakCard() {
    if (!Streak.isEnabled()) return
    val info = Streak.info()
    var open by remember { mutableStateOf(false) }
    val tone = levelColor(info.tier.level)
    val sub = when {
        info.listenedToday -> "Hari ini sudah tercatat · ${info.weekCount}/7 minggu ini"
        info.canRecover -> "Kemarin terlewat. Ketuk untuk memulihkan"
        info.atRisk -> "Hampir putus! Sisa ${info.hoursLeft.coerceAtLeast(1)} jam, dengarkan 1 menit"
        info.current > 0 -> "Dengarkan 1 menit hari ini supaya tidak putus"
        else -> "Dengarkan 1 menit hari ini untuk mulai"
    }
    val shape = RoundedCornerShape(24.dp)
    Row(
        modifier = Modifier
            .padding(horizontal = 20.dp, vertical = 6.dp)
            .fillMaxWidth()
            .clip(shape)
            .background(
                Brush.linearGradient(
                    listOf(tone.copy(alpha = if (info.current > 0) 0.26f else 0.10f), Color.White.copy(alpha = 0.05f))
                )
            )
            .border(1.dp, tone.copy(alpha = if (info.current > 0) 0.35f else 0.12f), shape)
            .clickable { open = true }
            .padding(horizontal = 16.dp, vertical = 14.dp),
        verticalAlignment = Alignment.CenterVertically
    ) {
        FlameIcon(info.tier.level, 52.dp)
        Spacer(Modifier.width(14.dp))
        Column(Modifier.weight(1f)) {
            Row(verticalAlignment = Alignment.Bottom) {
                Text("${info.current}", fontSize = 28.sp, fontWeight = FontWeight.Black, lineHeight = 30.sp)
                Spacer(Modifier.width(6.dp))
                Text(
                    if (info.current > 0) "hari berturut-turut" else "streak belum mulai",
                    style = MaterialTheme.typography.bodyMedium,
                    color = Color.White.copy(alpha = 0.75f),
                    modifier = Modifier.padding(bottom = 3.dp)
                )
            }
            Text(
                sub,
                style = MaterialTheme.typography.bodySmall,
                color = if (info.atRisk) Color(0xFFFFB02E) else Color.White.copy(alpha = 0.6f),
                maxLines = 2
            )
            if (!info.listenedToday) {
                Spacer(Modifier.height(8.dp))
                Box(
                    Modifier
                        .fillMaxWidth()
                        .height(5.dp)
                        .clip(CircleShape)
                        .background(Color.White.copy(alpha = 0.12f))
                ) {
                    Box(
                        Modifier
                            .fillMaxWidth(info.todayProgress.coerceAtLeast(0.02f))
                            .height(5.dp)
                            .clip(CircleShape)
                            .background(tone)
                    )
                }
            }
            Spacer(Modifier.height(10.dp))
            WeekDots(info)
        }
        Icon(
            Icons.Rounded.ChevronRight,
            contentDescription = null,
            tint = Color.White.copy(alpha = 0.4f),
            modifier = Modifier.size(22.dp)
        )
    }
    if (open) StreakSheet(onDismiss = { open = false })
}

@Composable
fun StreakSheet(onDismiss: () -> Unit) {
    val context = LocalContext.current
    val info = Streak.info()
    var showRecap by remember { mutableStateOf(false) }
    val tone = levelColor(info.tier.level)
    JimzBottomSheet(onDismiss = onDismiss) {
        Column(
            Modifier
                .verticalScroll(rememberScrollState())
                .padding(horizontal = 20.dp)
        ) {
            StreakHero(info, tone)

            SectionHeader("Minggu ini")
            WeekStrip(info, tone)

            SectionHeader("Ringkasan")
            Row(horizontalArrangement = Arrangement.spacedBy(10.dp)) {
                StatTile("Terbaik", "${info.best}", "hari", Modifier.weight(1f))
                StatTile("Hari ini", "${PlayLog.secondsOn(LocalDate.now()) / 60}", "menit", Modifier.weight(1f))
                StatTile("Pemulihan", "${info.recoveriesLeft}", "sisa bulan ini", Modifier.weight(1f))
            }

            if (info.canRecover) {
                Spacer(Modifier.height(14.dp))
                SoftCard {
                    Text("Kemarin terlewat", style = MaterialTheme.typography.titleSmall, fontWeight = FontWeight.Bold)
                    Text(
                        "Pulihkan supaya streak ${info.current} harimu tidak putus. Sisa ${info.recoveriesLeft} kali bulan ini.",
                        style = MaterialTheme.typography.bodySmall,
                        color = Color.White.copy(alpha = 0.65f),
                        modifier = Modifier.padding(top = 4.dp, bottom = 12.dp)
                    )
                    PillTextButton(
                        "Pulihkan kemarin",
                        onClick = {
                            if (Streak.recoverYesterday()) {
                                Toast.makeText(context, "Streak dipulihkan", Toast.LENGTH_SHORT).show()
                            }
                        },
                        modifier = Modifier.fillMaxWidth()
                    )
                }
            }

            SectionHeader("Lima minggu terakhir")
            Heatmap(tone)

            Spacer(Modifier.height(18.dp))
            Row(horizontalArrangement = Arrangement.spacedBy(10.dp)) {
                PillTextButton(
                    "Bagikan",
                    onClick = {
                        val bmp = ShareImage.render(
                            story = true,
                            accent = tone.toArgb(),
                            heading = "Streak mendengar",
                            big = "${info.current}",
                            bigCaption = "hari berturut-turut (terbaik ${info.best})",
                            quote = listOf(info.tier.name)
                        )
                        ShareImage.share(context, bmp, "streak")
                    },
                    modifier = Modifier.weight(1f),
                    primary = false
                )
                PillTextButton("Lihat rekap", onClick = { showRecap = true }, modifier = Modifier.weight(1f))
            }
            Spacer(Modifier.height(10.dp))
            PillTextButton(
                "Pasang widget streak",
                onClick = { com.jimz.jimztune.widget.StreakWidgetProvider.requestPin(context) },
                modifier = Modifier.fillMaxWidth(),
                primary = false
            )

            SectionHeader("Penyimpanan")
            CloudStatusCard()

            SectionHeader("Pengaturan")
            SoftCard {
                SwitchRow("Tampilkan di Beranda", "Kartu streak di halaman utama", Streak.enabled, { Streak.updateEnabled(it) })
            }
            Text(
                "Satu hari dihitung kalau kamu mendengarkan minimal 1 menit. Pemulihan maksimal 2 kali per bulan, hanya untuk satu hari yang terlewat.",
                style = MaterialTheme.typography.bodySmall,
                color = Color.White.copy(alpha = 0.45f),
                modifier = Modifier.padding(top = 10.dp, start = 4.dp, end = 4.dp)
            )
            Spacer(Modifier.height(18.dp))
        }
    }
    if (showRecap) RecapSheet(onDismiss = { showRecap = false })
}

/** Status penyimpanan streak dan rekap ke akun, dengan tombol sinkron manual. */
@Composable
private fun CloudStatusCard() {
    val loggedIn = AppGraph.auth.isLoggedIn
    val st = AppGraph.cloud.status
    val text = when {
        !loggedIn -> "Masuk ke akun supaya streak dan rekapmu tersimpan di cloud dan tidak hilang saat ganti HP atau akun."
        st is SyncStatus.Syncing -> "Menyimpan ke akunmu…"
        st is SyncStatus.Failed -> "Belum tersimpan: ${st.message}"
        st is SyncStatus.Done -> "Tersimpan di akunmu · ${java.text.SimpleDateFormat("d MMM, HH:mm", ID).format(java.util.Date(st.atMs))}"
        else -> "Belum disinkronkan."
    }
    SoftCard {
        Text("Cloud", style = MaterialTheme.typography.titleSmall, fontWeight = FontWeight.Bold)
        Text(
            text,
            style = MaterialTheme.typography.bodySmall,
            color = Color.White.copy(alpha = 0.65f),
            modifier = Modifier.padding(top = 4.dp)
        )
        if (loggedIn) {
            Spacer(Modifier.height(12.dp))
            PillTextButton(
                if (st is SyncStatus.Syncing) "Menyimpan…" else "Sinkronkan sekarang",
                onClick = { AppGraph.cloud.syncManually() },
                enabled = st !is SyncStatus.Syncing,
                primary = false,
                modifier = Modifier.fillMaxWidth()
            )
        } else {
            Spacer(Modifier.height(12.dp))
            PillTextButton(
                "Masuk",
                onClick = { AppGraph.auth.openLogin() },
                modifier = Modifier.fillMaxWidth()
            )
        }
    }
}

@Composable
private fun StreakHero(info: StreakInfo, tone: Color) {
    val shape = RoundedCornerShape(28.dp)
    Column(
        modifier = Modifier
            .fillMaxWidth()
            .clip(shape)
            .background(
                Brush.verticalGradient(
                    listOf(tone.copy(alpha = if (info.current > 0) 0.30f else 0.10f), Color.White.copy(alpha = 0.03f))
                )
            )
            .border(1.dp, tone.copy(alpha = if (info.current > 0) 0.35f else 0.10f), shape)
            .padding(horizontal = 20.dp, vertical = 22.dp),
        horizontalAlignment = Alignment.CenterHorizontally
    ) {
        // Cahaya berdenyut di belakang api (makin kuat kalau streak aktif) + angka yang menghitung naik.
        val pulse = androidx.compose.animation.core.rememberInfiniteTransition(label = "flamePulse")
        val glow by pulse.animateFloat(
            initialValue = 0.55f, targetValue = 1f,
            animationSpec = androidx.compose.animation.core.infiniteRepeatable(
                androidx.compose.animation.core.tween(1600, easing = androidx.compose.animation.core.FastOutSlowInEasing),
                androidx.compose.animation.core.RepeatMode.Reverse
            ),
            label = "glow"
        )
        val shownDays by androidx.compose.animation.core.animateIntAsState(
            targetValue = info.current,
            animationSpec = androidx.compose.animation.core.tween(900, easing = androidx.compose.animation.core.FastOutSlowInEasing),
            label = "shownDays"
        )
        Box(
            modifier = Modifier
                .size(150.dp)
                .drawBehind {
                    val a = if (info.current > 0) 0.42f * glow else 0.10f
                    drawCircle(
                        Brush.radialGradient(listOf(tone.copy(alpha = a), Color.Transparent), radius = size.minDimension / 2f)
                    )
                },
            contentAlignment = Alignment.Center
        ) {
            FlameIcon(info.tier.level, 104.dp)
        }
        Spacer(Modifier.height(2.dp))
        Text("$shownDays", fontSize = 60.sp, fontWeight = FontWeight.Black, lineHeight = 62.sp)
        Text(
            "hari berturut-turut",
            style = MaterialTheme.typography.bodyMedium,
            color = Color.White.copy(alpha = 0.7f)
        )
        Spacer(Modifier.height(10.dp))
        Text(
            "Api ${info.tier.name}",
            style = MaterialTheme.typography.labelLarge,
            fontWeight = FontWeight.Bold,
            color = Color.White,
            modifier = Modifier
                .clip(RoundedCornerShape(50))
                .background(tone.copy(alpha = 0.28f))
                .padding(horizontal = 14.dp, vertical = 5.dp)
        )

        val next = Streak.nextMilestone(info.current)
        Spacer(Modifier.height(16.dp))
        if (next != null) {
            val from = Streak.currentMilestone(info.current)
            val fracTarget = ((info.current - from).toFloat() / (next - from).toFloat()).coerceIn(0f, 1f)
            val frac by androidx.compose.animation.core.animateFloatAsState(
                targetValue = fracTarget,
                animationSpec = androidx.compose.animation.core.tween(900, easing = androidx.compose.animation.core.FastOutSlowInEasing),
                label = "frac"
            )
            Box(
                Modifier
                    .fillMaxWidth()
                    .height(8.dp)
                    .clip(CircleShape)
                    .background(Color.White.copy(alpha = 0.12f))
            ) {
                Box(
                    Modifier
                        .fillMaxWidth(frac)
                        .height(8.dp)
                        .clip(CircleShape)
                        .background(Brush.horizontalGradient(listOf(tone, Color.White.copy(alpha = 0.9f))))
                )
            }
            Text(
                "${next - info.current} hari lagi menuju api ${Streak.tierOf(next).name}",
                style = MaterialTheme.typography.bodySmall,
                color = Color.White.copy(alpha = 0.65f),
                textAlign = TextAlign.Center,
                modifier = Modifier.padding(top = 8.dp)
            )
        } else {
            Text(
                "Tingkat api tertinggi. Pertahankan!",
                style = MaterialTheme.typography.bodySmall,
                color = Color.White.copy(alpha = 0.65f)
            )
        }
    }
}

@Composable
private fun WeekStrip(info: StreakInfo, tone: Color) {
    @Suppress("UNUSED_VARIABLE") val v = PlayLog.version
    val today = LocalDate.now()
    SoftCard {
        Row(Modifier.fillMaxWidth(), horizontalArrangement = Arrangement.SpaceBetween) {
            lastDays(7).forEach { d ->
                val on = Streak.counted(d)
                val isToday = d == today
                Column(horizontalAlignment = Alignment.CenterHorizontally) {
                    Text(
                        d.dayOfWeek.getDisplayName(TextStyle.SHORT, ID).take(3),
                        fontSize = 11.sp,
                        color = Color.White.copy(alpha = if (isToday) 0.95f else 0.5f),
                        fontWeight = if (isToday) FontWeight.Bold else FontWeight.Normal
                    )
                    Spacer(Modifier.height(8.dp))
                    Box(
                        Modifier
                            .size(36.dp)
                            .clip(CircleShape)
                            .background(if (on) tone.copy(alpha = 0.9f) else Color.White.copy(alpha = 0.07f))
                            .then(
                                if (isToday && !on) Modifier.border(1.5.dp, tone.copy(alpha = 0.8f), CircleShape) else Modifier
                            ),
                        contentAlignment = Alignment.Center
                    ) {
                        if (on) {
                            Icon(Icons.Rounded.Check, contentDescription = null, tint = Color(0xFF1A0E00), modifier = Modifier.size(20.dp))
                        }
                    }
                    Text(
                        "${d.dayOfMonth}",
                        fontSize = 10.sp,
                        color = Color.White.copy(alpha = if (isToday) 0.9f else 0.4f),
                        modifier = Modifier.padding(top = 6.dp)
                    )
                }
            }
        }
        if (!info.listenedToday) {
            Text(
                if (info.current > 0) "Dengarkan 1 menit hari ini supaya streak tidak putus." else "Dengarkan 1 menit hari ini untuk memulai.",
                style = MaterialTheme.typography.bodySmall,
                color = Color.White.copy(alpha = 0.55f),
                modifier = Modifier.padding(top = 12.dp)
            )
        }
    }
}

@Composable
private fun StatTile(label: String, value: String, unit: String, modifier: Modifier = Modifier) {
    val shape = RoundedCornerShape(com.jimz.jimztune.ui.theme.AppTheme.round(18.dp))
    Column(
        modifier = modifier
            .cardSurface(shape)
            .clip(shape)
            .padding(horizontal = 12.dp, vertical = 12.dp)
    ) {
        Text(label, fontSize = 11.sp, color = Color.White.copy(alpha = 0.55f))
        Text(value, fontSize = 24.sp, fontWeight = FontWeight.Black)
        Text(unit, fontSize = 11.sp, color = Color.White.copy(alpha = 0.5f), maxLines = 1)
    }
}

/** Kisi 5 minggu (Senin di kiri). Makin lama mendengar, makin terang kotaknya. */
@Composable
private fun Heatmap(tone: Color) {
    @Suppress("UNUSED_VARIABLE") val v = PlayLog.version
    val today = LocalDate.now()
    val start = today.minusDays((today.dayOfWeek.value - 1).toLong()).minusWeeks(4)
    SoftCard {
        Row(Modifier.fillMaxWidth(), horizontalArrangement = Arrangement.spacedBy(6.dp)) {
            listOf("Sen", "Sel", "Rab", "Kam", "Jum", "Sab", "Min").forEach {
                Text(it, fontSize = 10.sp, color = Color.White.copy(alpha = 0.4f), textAlign = TextAlign.Center, modifier = Modifier.weight(1f))
            }
        }
        Spacer(Modifier.height(6.dp))
        for (w in 0 until 5) {
            Row(Modifier.fillMaxWidth().padding(vertical = 3.dp), horizontalArrangement = Arrangement.spacedBy(6.dp)) {
                for (i in 0 until 7) {
                    val d = start.plusDays((w * 7 + i).toLong())
                    val future = d.isAfter(today)
                    val mins = if (future) 0 else PlayLog.secondsOn(d) / 60
                    val alpha = when {
                        future -> 0f
                        Streak.counted(d) && mins < 5 -> 0.4f
                        mins >= 60 -> 1f
                        mins >= 30 -> 0.8f
                        mins >= 10 -> 0.6f
                        mins >= 1 -> 0.4f
                        else -> 0f
                    }
                    Box(
                        Modifier
                            .weight(1f)
                            .aspectRatio(1f)
                            .clip(RoundedCornerShape(8.dp))
                            .background(if (alpha > 0f) tone.copy(alpha = alpha) else Color.White.copy(alpha = if (future) 0.02f else 0.06f))
                            .then(if (d == today) Modifier.border(1.5.dp, Color.White.copy(alpha = 0.8f), RoundedCornerShape(8.dp)) else Modifier)
                    )
                }
            }
        }
        Row(
            Modifier.fillMaxWidth().padding(top = 8.dp),
            horizontalArrangement = Arrangement.End,
            verticalAlignment = Alignment.CenterVertically
        ) {
            Text("Sedikit", fontSize = 10.sp, color = Color.White.copy(alpha = 0.45f))
            listOf(0.4f, 0.6f, 0.8f, 1f).forEach { a ->
                Box(
                    Modifier
                        .padding(start = 4.dp)
                        .size(10.dp)
                        .clip(RoundedCornerShape(3.dp))
                        .background(tone.copy(alpha = a))
                )
            }
            Text("Banyak", fontSize = 10.sp, color = Color.White.copy(alpha = 0.45f), modifier = Modifier.padding(start = 6.dp))
        }
    }
}
