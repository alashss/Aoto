package com.jimz.jimztune.ui.announcements

import androidx.activity.compose.BackHandler
import androidx.compose.animation.AnimatedVisibility
import androidx.compose.animation.core.FastOutSlowInEasing
import androidx.compose.animation.core.tween
import androidx.compose.animation.fadeIn
import androidx.compose.animation.slideInVertically
import androidx.compose.foundation.background
import androidx.compose.foundation.border
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.PaddingValues
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.layout.statusBarsPadding
import androidx.compose.foundation.layout.width
import androidx.compose.foundation.lazy.LazyColumn
import androidx.compose.foundation.lazy.itemsIndexed
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.rounded.ArrowBack
import androidx.compose.material.icons.rounded.Campaign
import androidx.compose.material.icons.rounded.Link
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
import androidx.compose.ui.platform.LocalContext
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import com.jimz.jimztune.AppGraph
import com.jimz.jimztune.data.remote.Announcement
import com.jimz.jimztune.ui.components.EmptyState
import com.jimz.jimztune.ui.components.GlassIconButton
import com.jimz.jimztune.ui.components.IconBadge
import com.jimz.jimztune.ui.components.LocalOverlayPadding
import com.jimz.jimztune.ui.components.PillTextButton
import com.jimz.jimztune.ui.theme.LocalCoverTheme
import com.jimz.jimztune.util.openUrl
import kotlinx.coroutines.delay

/** Halaman penuh berisi semua pengumuman aktif, satu kartu per pengumuman. */
@Composable
fun AnnouncementsScreen(onBack: () -> Unit) {
    val remote = AppGraph.remote
    val cover = LocalCoverTheme.current
    val items = remember { remote.activeAnnouncements() }
    // Dicatat sebelum ditandai terbaca, supaya label "BARU" masih tampil di kunjungan ini.
    val newIds = remember { items.filter { !remote.isSeen(it.id) }.map { it.id }.toSet() }

    LaunchedEffect(Unit) { items.forEach { remote.markSeen(it.id) } }
    BackHandler(onBack = onBack)

    Column(
        modifier = Modifier
            .fillMaxSize()
            .background(Brush.verticalGradient(0f to cover.top, 0.5f to cover.mid, 1f to cover.bottom))
            .statusBarsPadding()
    ) {
        Row(
            modifier = Modifier
                .fillMaxWidth()
                .padding(start = 20.dp, end = 20.dp, top = 12.dp, bottom = 8.dp),
            verticalAlignment = Alignment.CenterVertically
        ) {
            GlassIconButton(Icons.Rounded.ArrowBack, "Kembali", onClick = onBack)
            Spacer(Modifier.width(16.dp))
            Column {
                Text("Pengumuman", style = MaterialTheme.typography.headlineSmall, fontWeight = FontWeight.Bold, color = Color.White)
                Text(
                    if (items.isEmpty()) "Belum ada kabar baru" else "${items.size} pengumuman aktif",
                    style = MaterialTheme.typography.bodySmall,
                    color = Color.White.copy(alpha = 0.6f)
                )
            }
        }

        if (items.isEmpty()) {
            EmptyState(
                Icons.Rounded.Campaign,
                "Belum ada pengumuman",
                "Kabar terbaru dari Aoto akan muncul di sini."
            )
        } else {
            LazyColumn(
                contentPadding = PaddingValues(start = 20.dp, end = 20.dp, top = 8.dp, bottom = LocalOverlayPadding.current),
                verticalArrangement = Arrangement.spacedBy(14.dp)
            ) {
                itemsIndexed(items, key = { _, a -> a.id }) { index, a ->
                    AnnouncementCard(a, isNew = a.id in newIds, index = index)
                }
            }
        }
    }
}

@Composable
private fun AnnouncementCard(a: Announcement, isNew: Boolean, index: Int) {
    val accent = LocalCoverTheme.current.accent
    val context = LocalContext.current
    val shape = RoundedCornerShape(24.dp)

    // Kartu muncul bergantian dari atas ke bawah.
    var shown by remember { mutableStateOf(false) }
    LaunchedEffect(Unit) {
        delay(index * 70L)
        shown = true
    }

    AnimatedVisibility(
        visible = shown,
        enter = fadeIn(tween(350)) + slideInVertically(tween(420, easing = FastOutSlowInEasing)) { it / 6 }
    ) {
        Column(
            modifier = Modifier
                .fillMaxWidth()
                .clip(shape)
                .background(Brush.linearGradient(listOf(accent.copy(alpha = 0.22f), Color.White.copy(alpha = 0.05f))))
                .border(1.dp, accent.copy(alpha = if (isNew) 0.55f else 0.28f), shape)
                .padding(18.dp)
        ) {
            Row(verticalAlignment = Alignment.CenterVertically) {
                IconBadge(Icons.Rounded.Campaign, size = 44.dp)
                Spacer(Modifier.width(14.dp))
                Column(modifier = Modifier.weight(1f)) {
                    Text(
                        a.title.ifBlank { "Pengumuman" },
                        style = MaterialTheme.typography.titleMedium,
                        fontWeight = FontWeight.Bold,
                        color = Color.White
                    )
                    if (!a.expires.isNullOrBlank()) {
                        Text(
                            "Berlaku sampai ${a.expires}",
                            style = MaterialTheme.typography.labelSmall,
                            color = Color.White.copy(alpha = 0.5f)
                        )
                    }
                }
                if (isNew) {
                    Box(
                        modifier = Modifier
                            .clip(RoundedCornerShape(50))
                            .background(accent)
                            .padding(horizontal = 10.dp, vertical = 4.dp)
                    ) {
                        Text("BARU", color = Color(0xFF0B1030), fontSize = 10.sp, fontWeight = FontWeight.ExtraBold, letterSpacing = 0.8.sp)
                    }
                }
            }
            Spacer(Modifier.height(12.dp))
            Text(
                a.message,
                style = MaterialTheme.typography.bodyMedium,
                color = Color.White.copy(alpha = 0.85f),
                lineHeight = 22.sp
            )
            val url = a.url
            if (!url.isNullOrBlank()) {
                Spacer(Modifier.height(14.dp))
                Row(
                    modifier = Modifier.fillMaxWidth(),
                    verticalAlignment = Alignment.CenterVertically
                ) {
                    Icon(Icons.Rounded.Link, null, tint = accent, modifier = Modifier.size(18.dp))
                    Spacer(Modifier.width(8.dp))
                    PillTextButton(
                        text = a.urlLabel ?: "Buka tautan",
                        onClick = { context.openUrl(url) }
                    )
                }
            }
        }
    }
}
