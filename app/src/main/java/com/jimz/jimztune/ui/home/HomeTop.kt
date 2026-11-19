package com.jimz.jimztune.ui.home

import androidx.compose.foundation.background
import com.jimz.jimztune.ui.theme.cardSurface
import androidx.compose.foundation.border
import androidx.compose.foundation.clickable
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.fillMaxHeight
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.layout.width
import androidx.compose.foundation.shape.CircleShape
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.rounded.ChevronRight
import androidx.compose.material.icons.rounded.EmojiEvents
import androidx.compose.material.icons.rounded.Favorite
import androidx.compose.material.icons.rounded.Notifications
import androidx.compose.material.icons.rounded.Search
import androidx.compose.material3.Icon
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
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
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.text.style.TextOverflow
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import com.jimz.jimztune.AppGraph
import com.jimz.jimztune.data.remote.Banner
import com.jimz.jimztune.data.stats.ListenLevel
import com.jimz.jimztune.data.stats.RankKind
import com.jimz.jimztune.data.stats.RankPeriod
import com.jimz.jimztune.data.stats.RankRow
import com.jimz.jimztune.data.stats.formatListen
import com.jimz.jimztune.data.stats.formatShort
import com.jimz.jimztune.ui.ranking.RankAvatar
import com.jimz.jimztune.ui.ranking.RankColors
import com.jimz.jimztune.ui.theme.JimzBlueSoft

/**
 * Bagian atas Beranda: kartu profil (level + waktu mendengar), kolom cari + tombol Peringkat,
 * lalu carousel banner yang diselingi kartu Top Pendengar dan Top Donatur. (Tombol donasi hanya ada di Setelan.) Semuanya ikut tergulir bersama isi Beranda.
 */
@Composable
fun HomeTop(
    banners: List<Banner>,
    onOpenSearch: () -> Unit,
    onOpenProfile: () -> Unit,
    onOpenAnnouncements: () -> Unit,
    onOpenRanking: (RankKind, RankPeriod) -> Unit
) {
    Column {
        if (AppGraph.auth.isLoggedIn) {
            ProfileCard(onOpenProfile, onOpenAnnouncements)
        } else {
            Header(onOpenSearch = null)
        }

        SearchRow(
            onOpenSearch,
            onOpenRanking = { onOpenRanking(RankKind.LISTEN, RankPeriod.ALL) },
            showRanking = AppGraph.remote.featureOn("ranking")
        )

        BannerCarousel(
            banners = banners,
            modifier = Modifier.padding(top = 4.dp),
            extraPages = buildList<@Composable () -> Unit> {
                if (AppGraph.remote.featureOn("ranking")) {
                    if (AppGraph.remote.featureOn("top_listeners")) add { RankingCard(RankKind.LISTEN) { p -> onOpenRanking(RankKind.LISTEN, p) } }
                    if (AppGraph.remote.featureOn("top_donors")) add { RankingCard(RankKind.DONATE) { p -> onOpenRanking(RankKind.DONATE, p) } }
                }
            }
        )
    }
}

// ------------------------------------------------------------------------------------------ profil

@Composable
private fun ProfileCard(onOpenProfile: () -> Unit, onOpenAnnouncements: () -> Unit) {
    val profile = AppGraph.profile.profile
    // Nama saja; email tidak pernah ditampilkan di sini.
    val name = (profile?.displayName ?: "Pengguna").substringBefore("@").ifBlank { "Pengguna" }
    val accent = profile?.accent ?: JimzBlueSoft
    val total = AppGraph.listen.totalSeconds
    val level = ListenLevel.level(total)
    val progress = ListenLevel.progress(total)
    val unread = AppGraph.remote.unreadCount()

    LaunchedEffect(Unit) { AppGraph.listen.refresh() }

    val shape = RoundedCornerShape(24.dp)
    Column(
        modifier = Modifier
            .padding(start = 16.dp, end = 16.dp, top = 14.dp)
            .fillMaxWidth()
            .clip(shape)
            .background(Brush.linearGradient(listOf(Color.White.copy(alpha = 0.10f), Color.White.copy(alpha = 0.04f))))
            .border(1.dp, Color.White.copy(alpha = 0.12f), shape)
            .padding(start = 14.dp, end = 12.dp, top = 12.dp, bottom = 14.dp)
    ) {
        Row(verticalAlignment = Alignment.CenterVertically) {
            Box(modifier = Modifier.clip(CircleShape).clickable(onClick = onOpenProfile)) {
                RankAvatar(profile?.avatarUrl, name, 52.dp, ring = accent)
            }
            Spacer(Modifier.width(12.dp))
            Column(
                modifier = Modifier
                    .weight(1f)
                    .clickable(onClick = onOpenProfile)
            ) {
                Text(
                    name,
                    style = MaterialTheme.typography.titleMedium,
                    fontWeight = FontWeight.Bold,
                    maxLines = 1,
                    overflow = TextOverflow.Ellipsis
                )
                Spacer(Modifier.height(4.dp))
                Row(verticalAlignment = Alignment.CenterVertically, horizontalArrangement = Arrangement.spacedBy(6.dp)) {
                    Pill("Lvl. $level", accent)
                    val status = profile?.statusText.orEmpty()
                    if (status.isNotBlank()) {
                        Text(
                            (profile?.statusEmoji.orEmpty() + " " + status).trim(),
                            style = MaterialTheme.typography.labelSmall,
                            color = Color.White.copy(alpha = 0.65f),
                            maxLines = 1,
                            overflow = TextOverflow.Ellipsis
                        )
                    }
                }
            }
            Box(
                modifier = Modifier
                    .size(44.dp)
                    .clip(CircleShape)
                    .background(Color.White.copy(alpha = 0.08f))
                    .clickable(onClick = onOpenAnnouncements),
                contentAlignment = Alignment.Center
            ) {
                Icon(Icons.Rounded.Notifications, contentDescription = "Pengumuman", modifier = Modifier.size(24.dp))
                if (unread > 0) {
                    Box(
                        modifier = Modifier
                            .align(Alignment.TopEnd)
                            .padding(top = 2.dp, end = 0.dp)
                            .clip(CircleShape)
                            .background(Color(0xFFE5484D))
                            .padding(horizontal = 5.dp, vertical = 1.dp)
                    ) {
                        Text(if (unread > 99) "99+" else unread.toString(), fontSize = 10.sp, fontWeight = FontWeight.Bold, color = Color.White)
                    }
                }
            }
        }

        Spacer(Modifier.height(12.dp))
        Row(verticalAlignment = Alignment.CenterVertically) {
            Text(
                "WAKTU MENDENGAR",
                fontSize = 11.sp,
                fontWeight = FontWeight.SemiBold,
                letterSpacing = 0.8.sp,
                color = Color.White.copy(alpha = 0.6f),
                modifier = Modifier.weight(1f)
            )
            Text(formatListen(total), fontWeight = FontWeight.Bold, fontSize = 15.sp)
        }
        Spacer(Modifier.height(6.dp))
        Box(
            modifier = Modifier
                .fillMaxWidth()
                .height(8.dp)
                .clip(CircleShape)
                .background(Color.White.copy(alpha = 0.10f))
        ) {
            Box(
                modifier = Modifier
                    .fillMaxHeight()
                    .fillMaxWidth(progress.coerceAtLeast(0.03f))
                    .clip(CircleShape)
                    .background(Brush.horizontalGradient(listOf(accent, Color(0xFFFFC857))))
            )
        }
        Spacer(Modifier.height(5.dp))
        Text(
            "${formatListen(ListenLevel.remaining(total))} lagi menuju Lvl. ${level + 1}",
            fontSize = 11.sp,
            color = Color.White.copy(alpha = 0.55f)
        )
    }
}

@Composable
private fun Pill(text: String, color: Color) {
    Text(
        text,
        fontSize = 11.sp,
        fontWeight = FontWeight.Bold,
        color = Color.White,
        modifier = Modifier
            .clip(RoundedCornerShape(8.dp))
            .background(color.copy(alpha = 0.35f))
            .border(1.dp, color.copy(alpha = 0.7f), RoundedCornerShape(8.dp))
            .padding(horizontal = 8.dp, vertical = 2.dp)
    )
}

// ------------------------------------------------------------------------------------------ cari + peringkat

@Composable
private fun SearchRow(onOpenSearch: () -> Unit, onOpenRanking: () -> Unit, showRanking: Boolean = true) {
    Row(
        modifier = Modifier.padding(horizontal = 16.dp, vertical = 12.dp),
        horizontalArrangement = Arrangement.spacedBy(10.dp),
        verticalAlignment = Alignment.CenterVertically
    ) {
        val shape = RoundedCornerShape(16.dp)
        Row(
            modifier = Modifier
                .weight(1f)
                .height(48.dp)
                .clip(shape)
                .cardSurface(shape)
                .clickable(onClick = onOpenSearch)
                .padding(horizontal = 14.dp),
            verticalAlignment = Alignment.CenterVertically
        ) {
            Icon(Icons.Rounded.Search, contentDescription = null, tint = Color.White.copy(alpha = 0.7f), modifier = Modifier.size(22.dp))
            Spacer(Modifier.width(10.dp))
            Text(
                "Cari lagu, album, atau artis",
                color = Color.White.copy(alpha = 0.6f),
                style = MaterialTheme.typography.bodyMedium,
                maxLines = 1,
                overflow = TextOverflow.Ellipsis
            )
        }
        if (showRanking) {
        Row(
            modifier = Modifier
                .height(48.dp)
                .clip(shape)
                .background(Brush.linearGradient(listOf(Color(0xFFFFC857), Color(0xFFE0883A))))
                .clickable(onClick = onOpenRanking)
                .padding(horizontal = 14.dp),
            verticalAlignment = Alignment.CenterVertically
        ) {
            Icon(Icons.Rounded.EmojiEvents, contentDescription = null, tint = Color(0xFF3A2400), modifier = Modifier.size(20.dp))
            Spacer(Modifier.width(6.dp))
            Text("Peringkat", color = Color(0xFF3A2400), fontWeight = FontWeight.Bold, fontSize = 14.sp, maxLines = 1)
        }
        }
    }
}

// ------------------------------------------------------------------------------------------ kartu peringkat

/** Kartu di carousel: judul + tab ALL/30D/7D, juara 1 besar di kiri, juara 2-3 dan 4-5 di kanan. */
@Composable
private fun RankingCard(kind: RankKind, onOpen: (RankPeriod) -> Unit) {
    var period by remember { mutableStateOf(RankPeriod.ALL) }
    val store = AppGraph.ranking
    LaunchedEffect(kind, period) { store.load(kind, period) }
    val board = store.board(kind, period)
    val rows = board?.rows.orEmpty()
    val gold = RankColors.Gold

    Column(
        modifier = Modifier
            .fillMaxSize()
            .background(Brush.linearGradient(RankColors.gradient(kind)))
            .clickable { onOpen(period) }
            .padding(start = 12.dp, end = 12.dp, top = 9.dp, bottom = 10.dp)
    ) {
        Row(verticalAlignment = Alignment.CenterVertically) {
            Icon(
                if (kind == RankKind.LISTEN) Icons.Rounded.EmojiEvents else Icons.Rounded.Favorite,
                contentDescription = null, tint = gold, modifier = Modifier.size(14.dp)
            )
            Spacer(Modifier.width(5.dp))
            Text(kind.cardTitle, fontSize = 11.sp, fontWeight = FontWeight.Bold, letterSpacing = 0.8.sp, color = gold)
            Icon(Icons.Rounded.ChevronRight, contentDescription = null, tint = gold, modifier = Modifier.size(14.dp))
            Spacer(Modifier.weight(1f))
            RankPeriod.entries.forEach { p ->
                val on = p == period
                Text(
                    p.short,
                    fontSize = 10.sp,
                    fontWeight = if (on) FontWeight.Bold else FontWeight.Medium,
                    color = if (on) Color.White else Color.White.copy(alpha = 0.55f),
                    modifier = Modifier
                        .padding(start = 4.dp)
                        .clip(RoundedCornerShape(8.dp))
                        .background(if (on) Color.White.copy(alpha = 0.20f) else Color.Transparent)
                        .clickable { period = p }
                        .padding(horizontal = 7.dp, vertical = 3.dp)
                )
            }
        }
        Spacer(Modifier.height(6.dp))

        when {
            rows.isEmpty() -> Box(Modifier.weight(1f).fillMaxWidth(), Alignment.Center) {
                Text(
                    when {
                        board == null && store.hasFailed(kind, period) -> "Gagal memuat. Ketuk untuk membuka."
                        board == null -> "Memuat peringkat…"
                        kind == RankKind.LISTEN -> "Belum ada yang mendengar. Jadilah yang pertama!"
                        else -> "Belum ada donasi. Jadilah donatur pertama!"
                    },
                    fontSize = 12.sp,
                    color = Color.White.copy(alpha = 0.65f)
                )
            }
            else -> Row(
                modifier = Modifier.weight(1f).fillMaxWidth(),
                horizontalArrangement = Arrangement.spacedBy(8.dp)
            ) {
                TopOneTile(rows.firstOrNull { it.rank == 1 }, kind, Modifier.weight(0.9f).fillMaxHeight())
                Column(
                    modifier = Modifier.weight(2f).fillMaxHeight(),
                    verticalArrangement = Arrangement.spacedBy(3.dp)
                ) {
                    Row(horizontalArrangement = Arrangement.spacedBy(6.dp), modifier = Modifier.weight(1.6f)) {
                        MiniTile(rows.firstOrNull { it.rank == 2 }, 2, kind, Modifier.weight(1f).fillMaxHeight())
                        MiniTile(rows.firstOrNull { it.rank == 3 }, 3, kind, Modifier.weight(1f).fillMaxHeight())
                    }
                    SmallRow(rows.firstOrNull { it.rank == 4 }, 4, kind, Modifier.weight(0.7f))
                    SmallRow(rows.firstOrNull { it.rank == 5 }, 5, kind, Modifier.weight(0.7f))
                }
            }
        }
    }
}

@Composable
private fun TopOneTile(row: RankRow?, kind: RankKind, modifier: Modifier) {
    val shape = RoundedCornerShape(14.dp)
    Column(
        modifier = modifier
            .clip(shape)
            .background(Color.White.copy(alpha = 0.10f))
            .border(1.dp, RankColors.Gold.copy(alpha = 0.45f), shape)
            .padding(4.dp),
        horizontalAlignment = Alignment.CenterHorizontally,
        verticalArrangement = Arrangement.Center
    ) {
        if (row == null) return@Column
        Text("👑", fontSize = 13.sp)
        RankAvatar(row.avatarUrl, row.name, 38.dp, ring = RankColors.Gold)
        Text(
            kind.formatShort(row.value),
            fontSize = 12.sp,
            fontWeight = FontWeight.Bold,
            color = RankColors.Gold,
            maxLines = 1,
            modifier = Modifier.padding(top = 2.dp)
        )
        Text(row.name, fontSize = 10.sp, color = Color.White.copy(alpha = 0.8f), maxLines = 1, overflow = TextOverflow.Ellipsis)
    }
}

@Composable
private fun MiniTile(row: RankRow?, place: Int, kind: RankKind, modifier: Modifier) {
    val shape = RoundedCornerShape(12.dp)
    Row(
        modifier = modifier
            .clip(shape)
            .background(Color.White.copy(alpha = 0.08f))
            .padding(horizontal = 6.dp),
        verticalAlignment = Alignment.CenterVertically
    ) {
        if (row == null) return@Row
        Box {
            RankAvatar(row.avatarUrl, row.name, 28.dp, ring = RankColors.medal(place))
        }
        Spacer(Modifier.width(5.dp))
        Column {
            Text(kind.formatShort(row.value), fontSize = 11.sp, fontWeight = FontWeight.Bold, maxLines = 1, color = RankColors.medal(place))
            Text(row.name, fontSize = 9.sp, color = Color.White.copy(alpha = 0.75f), maxLines = 1, overflow = TextOverflow.Ellipsis)
        }
    }
}

@Composable
private fun SmallRow(row: RankRow?, place: Int, kind: RankKind, modifier: Modifier) {
    Row(
        modifier = modifier.fillMaxWidth().padding(horizontal = 4.dp),
        verticalAlignment = Alignment.CenterVertically
    ) {
        if (row == null) return@Row
        Text(place.toString(), fontSize = 10.sp, fontWeight = FontWeight.Bold, color = Color.White.copy(alpha = 0.55f), modifier = Modifier.width(12.dp))
        RankAvatar(row.avatarUrl, row.name, 16.dp)
        Spacer(Modifier.width(6.dp))
        Text(row.name, fontSize = 10.sp, maxLines = 1, overflow = TextOverflow.Ellipsis, modifier = Modifier.weight(1f))
        Text(kind.formatShort(row.value), fontSize = 10.sp, fontWeight = FontWeight.Bold, maxLines = 1)
    }
}
