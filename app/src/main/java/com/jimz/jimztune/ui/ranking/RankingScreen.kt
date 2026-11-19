package com.jimz.jimztune.ui.ranking

import androidx.compose.foundation.background
import com.jimz.jimztune.ui.theme.cardSurface
import androidx.compose.foundation.border
import androidx.compose.foundation.clickable
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.PaddingValues
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.navigationBarsPadding
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.layout.statusBarsPadding
import androidx.compose.foundation.layout.width
import androidx.compose.foundation.lazy.LazyColumn
import androidx.compose.foundation.lazy.items
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.rounded.ArrowBack
import androidx.compose.material.icons.rounded.EmojiEvents
import androidx.compose.material.icons.rounded.Favorite
import androidx.compose.material.icons.rounded.Headphones
import androidx.compose.material3.CircularProgressIndicator
import androidx.compose.material3.Icon
import androidx.compose.material3.IconButton
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
import androidx.compose.ui.text.style.TextAlign
import androidx.compose.ui.text.style.TextOverflow
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import com.jimz.jimztune.AppGraph
import com.jimz.jimztune.data.stats.RankBoard
import com.jimz.jimztune.data.stats.RankKind
import com.jimz.jimztune.data.stats.RankPeriod
import com.jimz.jimztune.data.stats.RankRow
import com.jimz.jimztune.data.stats.format
import com.jimz.jimztune.data.stats.formatShort
import com.jimz.jimztune.ui.components.EmptyState
import com.jimz.jimztune.ui.components.GradientPillButton
import com.jimz.jimztune.ui.theme.JimzBackground
import com.jimz.jimztune.ui.theme.LocalCoverTheme
import kotlinx.coroutines.delay

/**
 * Halaman peringkat: pilih jenis (Mendengar / Donasi) dan periode (Semua waktu / 30 hari / 7 hari).
 * "The Big 3" tampil sebagai podium, peringkat 4 ke bawah sebagai daftar, dan posisimu tampil di bar bawah.
 * Datanya diperbarui sendiri tiap 30 detik selama halaman terbuka.
 */
@Composable
fun RankingScreen(initialKind: RankKind, initialPeriod: RankPeriod, onBack: () -> Unit) {
    var kind by remember { mutableStateOf(initialKind) }
    var period by remember { mutableStateOf(initialPeriod) }
    val store = AppGraph.ranking
    val accent = LocalCoverTheme.current.accent

    LaunchedEffect(kind, period) {
        store.load(kind, period, force = true)
        while (true) {
            delay(30_000)
            store.load(kind, period, force = true)
        }
    }

    val board = store.board(kind, period)
    val myId = AppGraph.auth.session?.userId

    Box(
        modifier = Modifier
            .fillMaxSize()
            .background(JimzBackground)
    ) {
        Column(modifier = Modifier.fillMaxSize().statusBarsPadding()) {
            Row(
                modifier = Modifier.padding(start = 6.dp, end = 16.dp, top = 6.dp),
                verticalAlignment = Alignment.CenterVertically
            ) {
                IconButton(onClick = onBack) { Icon(Icons.Rounded.ArrowBack, contentDescription = "Kembali") }
                Text(
                    "Peringkat",
                    style = MaterialTheme.typography.titleLarge,
                    fontWeight = FontWeight.Bold
                )
            }

            // Jenis peringkat
            Row(
                modifier = Modifier.padding(horizontal = 16.dp, vertical = 8.dp),
                horizontalArrangement = Arrangement.spacedBy(10.dp)
            ) {
                KindChip("Mendengar", Icons.Rounded.Headphones, kind == RankKind.LISTEN, Modifier.weight(1f)) { kind = RankKind.LISTEN }
                KindChip("Donasi", Icons.Rounded.Favorite, kind == RankKind.DONATE, Modifier.weight(1f)) { kind = RankKind.DONATE }
            }

            // Periode
            PeriodSwitch(period, accent, Modifier.padding(horizontal = 16.dp, vertical = 4.dp)) { period = it }

            when {
                board == null && store.hasFailed(kind, period) -> Column(
                    modifier = Modifier.fillMaxSize().padding(32.dp),
                    horizontalAlignment = Alignment.CenterHorizontally,
                    verticalArrangement = Arrangement.Center
                ) {
                    Text(
                        "Gagal memuat peringkat. Cek internetmu.",
                        style = MaterialTheme.typography.bodyLarge,
                        color = MaterialTheme.colorScheme.onSurfaceVariant,
                        textAlign = TextAlign.Center
                    )
                    Spacer(Modifier.height(16.dp))
                    GradientPillButton("Coba lagi", Icons.Rounded.EmojiEvents, { store.load(kind, period, force = true) })
                }
                board == null -> Box(Modifier.fillMaxSize(), Alignment.Center) { CircularProgressIndicator(color = accent) }
                board.rows.isEmpty() -> EmptyState(
                    icon = if (kind == RankKind.LISTEN) Icons.Rounded.Headphones else Icons.Rounded.Favorite,
                    title = "Belum ada peringkat",
                    body = if (kind == RankKind.LISTEN) "Putar lagu dan jadilah yang pertama di papan peringkat."
                    else "Belum ada donasi yang tercatat. Dukung Aoto dan masuk peringkat."
                )
                else -> BoardList(kind, period, board, myId, bottomPad = if (myId != null) 84 else 24)
            }
        }

        // Posisimu, menempel di bawah.
        if (myId != null && (board != null || store.hasFailed(kind, period).not())) {
            MyRankBar(
                kind = kind,
                board = board,
                modifier = Modifier.align(Alignment.BottomCenter)
            )
        }
    }
}

@Composable
private fun KindChip(label: String, icon: androidx.compose.ui.graphics.vector.ImageVector, selected: Boolean, modifier: Modifier, onClick: () -> Unit) {
    val shape = RoundedCornerShape(16.dp)
    Row(
        modifier = modifier
            .height(46.dp)
            .clip(shape)
            .background(
                if (selected) Brush.linearGradient(listOf(com.jimz.jimztune.ui.theme.AppTheme.colors.accent, com.jimz.jimztune.ui.theme.AppTheme.colors.accentSoft))
                else Brush.linearGradient(listOf(Color.White.copy(alpha = 0.07f), Color.White.copy(alpha = 0.07f)))
            )
            .border(1.dp, Color.White.copy(alpha = if (selected) 0f else 0.12f), shape)
            .clickable(onClick = onClick),
        horizontalArrangement = Arrangement.Center,
        verticalAlignment = Alignment.CenterVertically
    ) {
        Icon(icon, contentDescription = null, modifier = Modifier.size(19.dp), tint = if (selected) Color.White else Color.White.copy(alpha = 0.75f))
        Spacer(Modifier.width(8.dp))
        Text(label, fontWeight = FontWeight.SemiBold, color = if (selected) Color.White else Color.White.copy(alpha = 0.75f))
    }
}

@Composable
private fun PeriodSwitch(selected: RankPeriod, accent: Color, modifier: Modifier, onSelect: (RankPeriod) -> Unit) {
    val shape = RoundedCornerShape(14.dp)
    Row(
        modifier = modifier
            .fillMaxWidth()
            .clip(shape)
            .cardSurface(shape)
            .padding(4.dp),
        horizontalArrangement = Arrangement.spacedBy(4.dp)
    ) {
        RankPeriod.entries.forEach { p ->
            val on = p == selected
            Box(
                modifier = Modifier
                    .weight(1f)
                    .height(38.dp)
                    .clip(RoundedCornerShape(11.dp))
                    .background(if (on) accent.copy(alpha = 0.9f) else Color.Transparent)
                    .clickable { onSelect(p) },
                contentAlignment = Alignment.Center
            ) {
                Text(
                    p.label,
                    fontWeight = if (on) FontWeight.Bold else FontWeight.Medium,
                    fontSize = 13.sp,
                    color = if (on) Color.Black else Color.White.copy(alpha = 0.78f)
                )
            }
        }
    }
}

@Composable
private fun BoardList(kind: RankKind, period: RankPeriod, board: RankBoard, myId: String?, bottomPad: Int) {
    val rest = board.rows.filter { it.rank > 3 }
    LazyColumn(contentPadding = PaddingValues(top = 10.dp, bottom = bottomPad.dp)) {
        item { Podium(kind, period, board.rows) }
        if (rest.isNotEmpty()) {
            item {
                Row(
                    modifier = Modifier.padding(start = 20.dp, end = 20.dp, top = 18.dp, bottom = 6.dp),
                    verticalAlignment = Alignment.CenterVertically
                ) {
                    Text(
                        "Peringkat selanjutnya",
                        style = MaterialTheme.typography.titleMedium,
                        fontWeight = FontWeight.SemiBold,
                        modifier = Modifier.weight(1f)
                    )
                    Text(
                        "4–${rest.last().rank}",
                        style = MaterialTheme.typography.labelLarge,
                        color = MaterialTheme.colorScheme.onSurfaceVariant
                    )
                }
            }
        }
        items(rest, key = { "r" + it.rank + (it.userId ?: it.name) }) { row ->
            RankListRow(row, kind, mine = row.userId != null && row.userId == myId)
        }
    }
}

/** "The Big 3": tiga teratas berdiri di podium, juara 1 di tengah dan paling tinggi. */
@Composable
private fun Podium(kind: RankKind, period: RankPeriod, rows: List<RankRow>) {
    val shape = RoundedCornerShape(24.dp)
    Column(
        modifier = Modifier
            .padding(horizontal = 16.dp)
            .fillMaxWidth()
            .clip(shape)
            .background(Brush.verticalGradient(RankColors.gradient(kind) + Color(0xFF12163A)))
            .border(1.dp, Color.White.copy(alpha = 0.10f), shape)
            .padding(top = 14.dp)
    ) {
        Row(
            modifier = Modifier.padding(horizontal = 16.dp),
            verticalAlignment = Alignment.CenterVertically
        ) {
            Icon(Icons.Rounded.EmojiEvents, contentDescription = null, tint = RankColors.Gold, modifier = Modifier.size(20.dp))
            Spacer(Modifier.width(8.dp))
            Text("The Big 3", fontWeight = FontWeight.Bold, style = MaterialTheme.typography.titleMedium, modifier = Modifier.weight(1f))
            Text(period.label, style = MaterialTheme.typography.labelMedium, color = Color.White.copy(alpha = 0.7f))
        }
        Spacer(Modifier.height(12.dp))
        Row(
            modifier = Modifier.fillMaxWidth().padding(horizontal = 8.dp),
            verticalAlignment = Alignment.Bottom
        ) {
            PodiumSlot(rows.firstOrNull { it.rank == 2 }, 2, kind, Modifier.weight(1f))
            PodiumSlot(rows.firstOrNull { it.rank == 1 }, 1, kind, Modifier.weight(1.12f))
            PodiumSlot(rows.firstOrNull { it.rank == 3 }, 3, kind, Modifier.weight(1f))
        }
    }
}

@Composable
private fun PodiumSlot(row: RankRow?, place: Int, kind: RankKind, modifier: Modifier) {
    val medal = RankColors.medal(place)
    val avatar = if (place == 1) 74.dp else 56.dp
    val block = when (place) { 1 -> 78.dp; 2 -> 54.dp; else -> 42.dp }
    Column(modifier = modifier.padding(horizontal = 4.dp), horizontalAlignment = Alignment.CenterHorizontally) {
        if (place == 1) Text("👑", fontSize = 22.sp)
        if (row != null) {
            RankAvatar(row.avatarUrl, row.name, avatar, ring = medal)
            Spacer(Modifier.height(6.dp))
            Text(
                row.name,
                fontSize = 12.sp,
                fontWeight = FontWeight.SemiBold,
                maxLines = 1,
                overflow = TextOverflow.Ellipsis,
                textAlign = TextAlign.Center
            )
            Text(
                kind.formatShort(row.value),
                fontSize = 13.sp,
                fontWeight = FontWeight.Bold,
                color = medal,
                maxLines = 1
            )
        } else {
            Box(
                modifier = Modifier
                    .size(avatar)
                    .clip(androidx.compose.foundation.shape.CircleShape)
                    .background(Color.White.copy(alpha = 0.06f))
                    .border(1.dp, Color.White.copy(alpha = 0.12f), androidx.compose.foundation.shape.CircleShape),
                contentAlignment = Alignment.Center
            ) { Text("?", color = Color.White.copy(alpha = 0.4f), fontWeight = FontWeight.Bold) }
            Spacer(Modifier.height(6.dp))
            Text("Kosong", fontSize = 12.sp, color = Color.White.copy(alpha = 0.4f))
            Text(" ", fontSize = 13.sp)
        }
        Spacer(Modifier.height(8.dp))
        Box(
            modifier = Modifier
                .fillMaxWidth()
                .height(block)
                .clip(RoundedCornerShape(topStart = 14.dp, topEnd = 14.dp))
                .background(Brush.verticalGradient(listOf(medal.copy(alpha = 0.55f), medal.copy(alpha = 0.10f)))),
            contentAlignment = Alignment.TopCenter
        ) {
            Text(
                place.toString(),
                fontSize = 28.sp,
                fontWeight = FontWeight.Black,
                color = Color.White.copy(alpha = 0.92f),
                modifier = Modifier.padding(top = 4.dp)
            )
        }
    }
}

@Composable
private fun RankListRow(row: RankRow, kind: RankKind, mine: Boolean) {
    val accent = LocalCoverTheme.current.accent
    val shape = RoundedCornerShape(16.dp)
    Row(
        modifier = Modifier
            .fillMaxWidth()
            .padding(horizontal = 12.dp, vertical = 3.dp)
            .clip(shape)
            .background(if (mine) accent.copy(alpha = 0.16f) else Color.White.copy(alpha = 0.04f))
            .then(if (mine) Modifier.border(1.dp, accent.copy(alpha = 0.6f), shape) else Modifier)
            .padding(horizontal = 12.dp, vertical = 10.dp),
        verticalAlignment = Alignment.CenterVertically
    ) {
        Text(
            row.rank.toString(),
            modifier = Modifier.width(34.dp),
            fontWeight = FontWeight.Bold,
            fontSize = 16.sp,
            color = Color.White.copy(alpha = 0.6f)
        )
        RankAvatar(row.avatarUrl, row.name, 44.dp)
        Spacer(Modifier.width(12.dp))
        Column(modifier = Modifier.weight(1f)) {
            Text(
                if (mine) row.name + "  (kamu)" else row.name,
                style = MaterialTheme.typography.bodyLarge,
                fontWeight = FontWeight.SemiBold,
                maxLines = 1,
                overflow = TextOverflow.Ellipsis
            )
            if (!row.username.isNullOrBlank()) {
                Text(
                    "@" + row.username,
                    style = MaterialTheme.typography.bodySmall,
                    color = MaterialTheme.colorScheme.onSurfaceVariant,
                    maxLines = 1,
                    overflow = TextOverflow.Ellipsis
                )
            }
        }
        Spacer(Modifier.width(8.dp))
        Text(kind.format(row.value), fontWeight = FontWeight.Bold, color = accent, maxLines = 1)
    }
}

@Composable
private fun MyRankBar(kind: RankKind, board: RankBoard?, modifier: Modifier) {
    val accent = LocalCoverTheme.current.accent
    val shape = RoundedCornerShape(20.dp)
    val rank = board?.meRank
    val value = board?.meValue
    Row(
        modifier = modifier
            .fillMaxWidth()
            .navigationBarsPadding()
            .padding(horizontal = 14.dp, vertical = 10.dp)
            .clip(shape)
            .background(Brush.horizontalGradient(listOf(Color(0xFF3B2F8F), Color(0xFF5B3AA8))))
            .border(1.dp, Color.White.copy(alpha = 0.18f), shape)
            .padding(horizontal = 16.dp, vertical = 12.dp),
        verticalAlignment = Alignment.CenterVertically
    ) {
        if (rank != null && value != null) {
            Text("#$rank", fontWeight = FontWeight.Black, fontSize = 20.sp)
            Spacer(Modifier.width(12.dp))
            Column(modifier = Modifier.weight(1f)) {
                Text("Peringkatmu", fontWeight = FontWeight.SemiBold)
                Text("Terus naik ya!", style = MaterialTheme.typography.bodySmall, color = Color.White.copy(alpha = 0.75f))
            }
            Text(kind.format(value), fontWeight = FontWeight.Bold)
        } else {
            Icon(
                if (kind == RankKind.LISTEN) Icons.Rounded.Headphones else Icons.Rounded.Favorite,
                contentDescription = null,
                tint = accent
            )
            Spacer(Modifier.width(12.dp))
            Text(
                if (kind == RankKind.LISTEN) "Putar lagu untuk masuk peringkat"
                else "Dukung Aoto untuk masuk peringkat donasi",
                style = MaterialTheme.typography.bodyMedium,
                modifier = Modifier.weight(1f)
            )
        }
    }
}
