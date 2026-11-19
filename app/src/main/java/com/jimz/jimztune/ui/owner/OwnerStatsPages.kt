package com.jimz.jimztune.ui.owner

import androidx.compose.foundation.background
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.fillMaxHeight
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.width
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.runtime.LaunchedEffect
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.rememberCoroutineScope
import androidx.compose.runtime.setValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clip
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.platform.LocalContext
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.text.style.TextOverflow
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import com.jimz.jimztune.data.owner.OwnerApi
import com.jimz.jimztune.data.stats.formatListen
import com.jimz.jimztune.ui.components.GroupCard
import com.jimz.jimztune.ui.components.OptionChip
import com.jimz.jimztune.ui.components.PillTextButton
import com.jimz.jimztune.ui.auth.ProfileAvatar
import com.jimz.jimztune.ui.theme.LocalCoverTheme
import kotlinx.coroutines.launch

private val HourDays = listOf(1, 7, 30)
private val HourLabels = listOf("Hari ini", "7 hari", "30 hari")

/** Statistik lengkap: angka besar, jam ramai, artis populer, dan pendengar teratas. */
@Composable
internal fun StatsPage(onBack: () -> Unit) {
    val accent = LocalCoverTheme.current.accent
    val overview = rememberLoader { OwnerApi.overview() }
    val top = rememberLoader { OwnerApi.topListeners(20) }
    var dayIndex by remember { mutableStateOf(1) }
    var hours by remember { mutableStateOf<List<Int>?>(null) }
    LaunchedEffect(dayIndex) {
        val r = OwnerApi.hourly(HourDays[dayIndex])
        if (r.ok) hours = r.value
    }

    OwnerScaffold(title = "Statistik lengkap", subtitle = "Ringkasan pemakaian seluruh pengguna", onBack = onBack) {
        val o = overview.value
        when {
            o == null && overview.error != null -> ErrorRow(overview.error ?: "", overview::reload)
            o == null -> LoadingRow()
            else -> {
                Row(horizontalArrangement = Arrangement.spacedBy(10.dp)) {
                    BigStat("Pengguna", "${o.totalUsers}", Modifier.weight(1f))
                    BigStat("Aktif 7 hari", "${o.active7d}", Modifier.weight(1f))
                }
                Row(horizontalArrangement = Arrangement.spacedBy(10.dp)) {
                    BigStat("Total mendengar", formatListen(o.totalListenSeconds), Modifier.weight(1f))
                    BigStat("Hari ini", formatListen(o.listenTodaySeconds), Modifier.weight(1f))
                }
                Row(horizontalArrangement = Arrangement.spacedBy(10.dp)) {
                    BigStat("Total putaran", "${o.totalPlays}", Modifier.weight(1f))
                    BigStat("Rata-rata level", String.format("%.1f", o.avgLevel), Modifier.weight(1f))
                }
                if (o.topArtists.isNotEmpty()) {
                    OwnerSection("Artis populer 7 hari")
                    GroupCard {
                        val max = o.topArtists.maxOf { it.second }.coerceAtLeast(1)
                        o.topArtists.forEachIndexed { i, (name, plays) ->
                            if (i > 0) OwnerDivider()
                            Column(Modifier.padding(horizontal = 16.dp, vertical = 10.dp)) {
                                Row {
                                    Text(name, modifier = Modifier.weight(1f), maxLines = 1, overflow = TextOverflow.Ellipsis, fontWeight = FontWeight.Medium)
                                    Text("$plays×", color = Color.White.copy(alpha = 0.7f))
                                }
                                Spacer(Modifier.height(6.dp))
                                Box(Modifier.fillMaxWidth().height(5.dp).clip(RoundedCornerShape(50)).background(Color.White.copy(alpha = 0.1f))) {
                                    Box(Modifier.fillMaxWidth(plays.toFloat() / max).fillMaxHeight().clip(RoundedCornerShape(50)).background(accent))
                                }
                            }
                        }
                    }
                }
            }
        }

        OwnerSection("Jam ramai (WIB)")
        Row(horizontalArrangement = Arrangement.spacedBy(8.dp)) {
            HourLabels.forEachIndexed { i, l -> OptionChip(l, i == dayIndex, { dayIndex = i }) }
        }
        val h = hours
        if (h == null) LoadingRow() else GroupCard {
            val peak = h.max().coerceAtLeast(1)
            Column(Modifier.padding(16.dp)) {
                Row(Modifier.fillMaxWidth().height(110.dp), horizontalArrangement = Arrangement.spacedBy(3.dp), verticalAlignment = Alignment.Bottom) {
                    h.forEach { c ->
                        Box(
                            Modifier
                                .weight(1f)
                                .fillMaxHeight((c.toFloat() / peak).coerceAtLeast(0.03f))
                                .clip(RoundedCornerShape(topStart = 4.dp, topEnd = 4.dp))
                                .background(if (c == peak && c > 0) accent else accent.copy(alpha = 0.4f))
                        )
                    }
                }
                Spacer(Modifier.height(6.dp))
                Row(Modifier.fillMaxWidth(), horizontalArrangement = Arrangement.SpaceBetween) {
                    listOf("00", "06", "12", "18", "23").forEach { Text(it, fontSize = 10.sp, color = Color.White.copy(alpha = 0.5f)) }
                }
                val ph = h.indexOf(peak)
                if (peak > 1) {
                    Text(
                        "Paling ramai pukul %02d.00 (%d putaran)".format(ph, peak),
                        fontSize = 12.sp,
                        color = Color.White.copy(alpha = 0.65f),
                        modifier = Modifier.padding(top = 8.dp)
                    )
                }
            }
        }

        OwnerSection("Pendengar teratas")
        val t = top.value
        when {
            t == null && top.error != null -> ErrorRow(top.error ?: "", top::reload)
            t == null -> LoadingRow()
            t.isEmpty() -> Text("Belum ada data.", color = MaterialTheme.colorScheme.onSurfaceVariant)
            else -> GroupCard {
                t.forEachIndexed { i, u ->
                    if (i > 0) OwnerDivider()
                    Row(Modifier.padding(horizontal = 16.dp, vertical = 9.dp), verticalAlignment = Alignment.CenterVertically) {
                        Text("${i + 1}", fontWeight = FontWeight.Black, fontSize = 16.sp, modifier = Modifier.width(28.dp), color = if (i < 3) accent else Color.White.copy(alpha = 0.5f))
                        ProfileAvatar(url = u.avatarUrl, name = u.name, size = 38.dp, accent = accent)
                        Spacer(Modifier.width(12.dp))
                        Column(Modifier.weight(1f)) {
                            Text(u.name, fontWeight = FontWeight.Medium, maxLines = 1, overflow = TextOverflow.Ellipsis)
                            Text(
                                (u.username?.let { "@$it · " } ?: "") + "Lvl. ${u.level} · ${u.plays} putaran",
                                fontSize = 12.sp, color = Color.White.copy(alpha = 0.55f), maxLines = 1, overflow = TextOverflow.Ellipsis
                            )
                        }
                        Text(formatListen(u.listenSeconds), fontWeight = FontWeight.Bold)
                    }
                }
            }
        }
    }
}

@Composable
private fun BigStat(label: String, value: String, modifier: Modifier) {
    GroupCard(modifier = modifier) {
        Column(Modifier.padding(horizontal = 16.dp, vertical = 14.dp)) {
            Text(label, fontSize = 11.sp, color = Color.White.copy(alpha = 0.55f))
            Text(value, fontSize = 24.sp, fontWeight = FontWeight.Black, maxLines = 1)
        }
    }
}

/** Blok di halaman detail pengguna: statistik dengar + editor lencana. */
@Composable
internal fun UserListenBlock(userId: String) {
    val context = LocalContext.current
    val scope = rememberCoroutineScope()
    val stats = rememberLoader { OwnerApi.userStats(userId) }
    val badgesLoader = rememberLoader { OwnerApi.badgesOf(userId) }
    var badges by remember { mutableStateOf<List<String>?>(null) }
    var custom by remember { mutableStateOf("") }
    var saving by remember { mutableStateOf(false) }
    val loaded = badgesLoader.value
    LaunchedEffect(loaded) { if (loaded != null && badges == null) badges = loaded }

    OwnerSection("Statistik mendengar")
    val s = stats.value
    when {
        s == null && stats.error != null -> ErrorRow(stats.error ?: "", stats::reload)
        s == null -> LoadingRow()
        else -> GroupCard {
            Column(Modifier.padding(16.dp), verticalArrangement = Arrangement.spacedBy(6.dp)) {
                Text("Level ${s.level} · ${formatListen(s.totalSeconds)}", fontWeight = FontWeight.Bold, fontSize = 18.sp)
                Text("Hari ini ${formatListen(s.todaySeconds)} · 7 hari ${formatListen(s.weekSeconds)}", fontSize = 13.sp, color = Color.White.copy(alpha = 0.7f))
                Text("${s.plays} putaran · ${s.activeDays} hari aktif", fontSize = 13.sp, color = Color.White.copy(alpha = 0.7f))
                if (s.topArtist != null) Text("Artis favorit: ${s.topArtist}", fontSize = 13.sp, color = Color.White.copy(alpha = 0.7f))
            }
        }
    }

    OwnerSection("Lencana")
    val cur = badges
    if (cur == null) LoadingRow() else GroupCard {
        Column(Modifier.padding(16.dp), verticalArrangement = Arrangement.spacedBy(10.dp)) {
            val all = (listOf("Supporter", "Tester", "VIP") + cur).distinct()
            Row(Modifier.fillMaxWidth(), horizontalArrangement = Arrangement.spacedBy(8.dp)) {
                all.take(4).forEach { b -> OptionChip(b, b in cur, { badges = if (b in cur) cur - b else cur + b }) }
            }
            if (all.size > 4) {
                Row(Modifier.fillMaxWidth(), horizontalArrangement = Arrangement.spacedBy(8.dp)) {
                    all.drop(4).forEach { b -> OptionChip(b, b in cur, { badges = if (b in cur) cur - b else cur + b }) }
                }
            }
            Row(verticalAlignment = Alignment.CenterVertically, horizontalArrangement = Arrangement.spacedBy(8.dp)) {
                OwnerField(custom, { custom = it.take(16) }, "Lencana baru", modifier = Modifier.weight(1f))
                PillTextButton("Tambah", onClick = {
                    val c = custom.trim()
                    if (c.isNotEmpty() && c !in cur) badges = cur + c
                    custom = ""
                }, primary = false)
            }
            PillTextButton(if (saving) "Menyimpan…" else "Simpan lencana", onClick = {
                if (saving) return@PillTextButton
                scope.launch {
                    saving = true
                    val err = OwnerApi.setBadges(userId, cur)
                    toast(context, err ?: "Lencana disimpan")
                    saving = false
                }
            }, modifier = Modifier.fillMaxWidth())
        }
    }
}
