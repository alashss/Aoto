package com.jimz.jimztune.ui.profile

import androidx.compose.foundation.background
import com.jimz.jimztune.ui.theme.cardSurface
import androidx.compose.foundation.border
import androidx.compose.foundation.clickable
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.offset
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.layout.statusBarsPadding
import androidx.compose.foundation.rememberScrollState
import androidx.compose.foundation.layout.fillMaxHeight
import androidx.compose.foundation.layout.width
import androidx.compose.material.icons.automirrored.rounded.QueueMusic
import androidx.compose.material.icons.rounded.CalendarMonth
import androidx.compose.material.icons.rounded.Favorite
import androidx.compose.material.icons.rounded.History
import androidx.compose.material.icons.rounded.Sync
import androidx.compose.material3.Icon
import androidx.compose.ui.draw.clipToBounds
import androidx.compose.ui.graphics.graphicsLayer
import androidx.compose.ui.graphics.vector.ImageVector
import androidx.compose.animation.core.LinearEasing
import androidx.compose.animation.core.RepeatMode
import androidx.compose.animation.core.animateFloat
import androidx.compose.animation.core.infiniteRepeatable
import androidx.compose.animation.core.rememberInfiniteTransition
import androidx.compose.animation.core.tween
import androidx.compose.ui.draw.rotate
import androidx.compose.foundation.shape.CircleShape
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.foundation.verticalScroll
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.automirrored.rounded.ArrowBack
import androidx.compose.material.icons.automirrored.rounded.Logout
import androidx.compose.material.icons.rounded.CloudSync
import androidx.compose.material.icons.rounded.Edit
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.runtime.CompositionLocalProvider
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.rememberCoroutineScope
import androidx.compose.runtime.setValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clip
import androidx.compose.ui.graphics.Brush
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.graphics.lerp
import androidx.compose.ui.layout.ContentScale
import androidx.compose.ui.platform.LocalContext
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import android.widget.Toast
import coil.compose.AsyncImage
import com.jimz.jimztune.AppGraph
import com.jimz.jimztune.data.cloud.SyncStatus
import com.jimz.jimztune.data.profile.Profile
import com.jimz.jimztune.ui.auth.ProfileAvatar
import com.jimz.jimztune.ui.components.GlassIconButton
import com.jimz.jimztune.ui.components.GradientPillButton
import com.jimz.jimztune.ui.components.GroupCard
import com.jimz.jimztune.ui.components.JimzDialog
import com.jimz.jimztune.ui.components.LocalOverlayPadding
import com.jimz.jimztune.ui.components.TonalPillButton
import com.jimz.jimztune.ui.theme.JimzBackground
import com.jimz.jimztune.ui.theme.LocalCoverTheme
import kotlinx.coroutines.launch
import java.text.SimpleDateFormat
import java.util.Date
import java.util.Locale

/**
 * Halaman profil: banner (bisa GIF) dengan efek paralaks yang memudar ke latar, foto bercincin warna aksen,
 * kartu statistik koleksi, tentang saya, dan status sinkronisasi. Semua warna mengikuti warna tema pilihan pemilik profil.
 */
@Composable
fun ProfileScreen(onBack: () -> Unit, onEdit: () -> Unit) {
    var publicView by remember { mutableStateOf(false) }
    val me = AppGraph.auth.session?.userId
    Box(Modifier.fillMaxSize()) {
        androidx.compose.animation.Crossfade(publicView, animationSpec = androidx.compose.animation.core.tween(280), label = "profileMode") { pub ->
            if (pub && me != null) com.jimz.jimztune.ui.social.ProfileViewScreen(me, onBack)
            else PrivateProfileScreen(onBack, onEdit)
        }
        // Dua tampilan: "Saya" (pribadi, dengan statistik dan pengaturan) dan "Publik" (persis seperti yang dilihat orang lain).
        Row(
            Modifier.align(Alignment.TopCenter).statusBarsPadding().padding(top = 10.dp)
                .clip(RoundedCornerShape(50)).background(Color.Black.copy(alpha = 0.55f)).padding(3.dp)
        ) {
            listOf(false to "Saya", true to "Publik").forEach { (v, label) ->
                val sel = publicView == v
                val bg by androidx.compose.animation.animateColorAsState(if (sel) Color.White else Color.Transparent, label = "modeBg")
                Text(
                    label, color = if (sel) Color.Black else Color.White, fontSize = 13.sp, fontWeight = FontWeight.SemiBold,
                    modifier = Modifier.clip(RoundedCornerShape(50)).background(bg).clickable { publicView = v }.padding(horizontal = 18.dp, vertical = 7.dp)
                )
            }
        }
    }
}

@Composable
private fun PrivateProfileScreen(onBack: () -> Unit, onEdit: () -> Unit) {
    val auth = AppGraph.auth
    val store = AppGraph.profile
    val cloud = AppGraph.cloud
    val context = LocalContext.current
    val scope = rememberCoroutineScope()
    val overlay = LocalOverlayPadding.current
    val p = store.profile
    val email = auth.session?.email.orEmpty()
    val accent = p?.accent ?: LocalCoverTheme.current.accent
    var confirmLogout by remember { mutableStateOf(false) }
    var loggingOut by remember { mutableStateOf(false) }
    val scroll = rememberScrollState()
    val pageTop = lerp(JimzBackground, accent, 0.22f)

    // Warna tombol di halaman ini mengikuti warna profil, bukan warna cover lagu.
    CompositionLocalProvider(LocalCoverTheme provides LocalCoverTheme.current.copy(accent = accent)) {
        Box(
            modifier = Modifier
                .fillMaxSize()
                .background(Brush.verticalGradient(listOf(pageTop, JimzBackground)))
        ) {
            Column(
                modifier = Modifier
                    .fillMaxSize()
                    .verticalScroll(scroll)
                    .padding(bottom = overlay)
            ) {
                // ---- Banner + foto ----
                Box(modifier = Modifier.fillMaxWidth()) {
                    Box(
                        modifier = Modifier
                            .fillMaxWidth()
                            .height(210.dp)
                            .clipToBounds()
                    ) {
                        Box(
                            modifier = Modifier
                                .fillMaxSize()
                                .graphicsLayer { translationY = scroll.value * 0.45f }
                                .background(Brush.linearGradient(listOf(accent, lerp(accent, Color.Black, 0.6f))))
                        ) {
                            if (p?.bannerUrl != null) {
                                // Coil memutar GIF / WebP bergerak otomatis.
                                AsyncImage(
                                    model = com.jimz.jimztune.util.SafeUrl.image(p.bannerUrl),
                                    contentDescription = null,
                                    contentScale = ContentScale.Crop,
                                    modifier = Modifier.fillMaxSize()
                                )
                            }
                        }
                        // Memudar halus ke latar halaman, supaya banner tidak terpotong tegas.
                        Box(
                            modifier = Modifier
                                .fillMaxSize()
                                .background(
                                    Brush.verticalGradient(
                                        0f to Color.Black.copy(alpha = 0.35f),
                                        0.35f to Color.Transparent,
                                        1f to pageTop
                                    )
                                )
                        )
                    }
                    Box(
                        modifier = Modifier
                            .align(Alignment.BottomStart)
                            .offset(x = 20.dp, y = 52.dp)
                            .size(112.dp)
                            .background(JimzBackground, CircleShape)
                            .border(
                                3.dp,
                                Brush.sweepGradient(listOf(accent, lerp(accent, Color.White, 0.65f), accent)),
                                CircleShape
                            )
                            .padding(6.dp)
                    ) {
                        ProfileAvatar(
                            url = p?.avatarUrl,
                            name = (p?.displayName ?: email).substringBefore("@"),
                            size = 100.dp,
                            accent = accent
                        )
                    }
                    GlassIconButton(
                        icon = Icons.AutoMirrored.Rounded.ArrowBack,
                        contentDescription = "Kembali",
                        onClick = onBack,
                        modifier = Modifier
                            .statusBarsPadding()
                            .padding(start = 14.dp, top = 8.dp)
                    )
                }
                Spacer(Modifier.height(64.dp))

                // ---- Nama, username, status ----
                Column(modifier = Modifier.padding(horizontal = 20.dp)) {
                    Text(
                        p?.displayName ?: "Pengguna",
                        style = MaterialTheme.typography.headlineMedium,
                        fontWeight = FontWeight.ExtraBold,
                        color = Color.White
                    )
                    Spacer(Modifier.height(6.dp))
                    Row(verticalAlignment = Alignment.CenterVertically) {
                        p?.username?.let {
                            Text(
                                "@$it",
                                color = lerp(accent, Color.White, 0.55f),
                                fontSize = 13.sp,
                                fontWeight = FontWeight.SemiBold,
                                modifier = Modifier
                                    .clip(RoundedCornerShape(10.dp))
                                    .background(accent.copy(alpha = 0.22f))
                                    .padding(horizontal = 10.dp, vertical = 4.dp)
                            )
                            Spacer(Modifier.size(10.dp))
                        }
                    }
                    if (p != null && p.badges.isNotEmpty()) {
                        Spacer(Modifier.height(12.dp))
                        Row(horizontalArrangement = Arrangement.spacedBy(6.dp)) {
                            p.badges.forEach { Badge(it, accent) }
                        }
                    }
                    if (p != null && (p.statusText.isNotBlank() || p.statusEmoji.isNotBlank())) {
                        Spacer(Modifier.height(12.dp))
                        Row(
                            modifier = Modifier
                                .clip(RoundedCornerShape(16.dp))
                                .background(Color.White.copy(alpha = 0.09f))
                                .padding(horizontal = 12.dp, vertical = 8.dp),
                            verticalAlignment = Alignment.CenterVertically
                        ) {
                            if (p.statusEmoji.isNotBlank()) Text(p.statusEmoji, fontSize = 16.sp)
                            if (p.statusEmoji.isNotBlank() && p.statusText.isNotBlank()) Spacer(Modifier.size(8.dp))
                            if (p.statusText.isNotBlank()) {
                                Text(p.statusText, color = Color.White.copy(alpha = 0.9f), fontSize = 14.sp)
                            }
                        }
                    }
                }

                // ---- Tombol aksi ----
                Spacer(Modifier.height(18.dp))
                Row(
                    modifier = Modifier.padding(horizontal = 20.dp),
                    horizontalArrangement = Arrangement.spacedBy(10.dp),
                    verticalAlignment = Alignment.CenterVertically
                ) {
                    GradientPillButton("Edit profil", Icons.Rounded.Edit, onEdit)
                    SyncIconButton(syncing = cloud.status is SyncStatus.Syncing, onClick = { cloud.syncManually() })
                }

                // ---- Statistik koleksi ----
                Spacer(Modifier.height(20.dp))
                Row(
                    modifier = Modifier.padding(horizontal = 20.dp),
                    horizontalArrangement = Arrangement.spacedBy(10.dp)
                ) {
                    StatTile(Icons.Rounded.Favorite, AppGraph.library.favorites.size, "Favorit", accent, Modifier.weight(1f))
                    StatTile(Icons.AutoMirrored.Rounded.QueueMusic, AppGraph.library.playlists.size, "Playlist", accent, Modifier.weight(1f))
                    StatTile(Icons.Rounded.History, AppGraph.history.items.size, "Riwayat", accent, Modifier.weight(1f))
                }

                // ---- Kartu tentang ----
                Spacer(Modifier.height(14.dp))
                GroupCard(modifier = Modifier.padding(horizontal = 20.dp)) {
                    Column(modifier = Modifier.padding(18.dp)) {
                        Label("TENTANG SAYA", accent)
                        Text(
                            p?.bio?.ifBlank { null } ?: "Belum ada bio. Ketuk Edit profil untuk menulisnya.",
                            color = Color.White.copy(alpha = if (p?.bio.isNullOrBlank()) 0.45f else 0.92f),
                            style = MaterialTheme.typography.bodyLarge,
                            modifier = Modifier.padding(top = 6.dp)
                        )
                        val since = p?.createdAt?.let(::formatDate)
                        if (since != null) {
                            Spacer(Modifier.height(16.dp))
                            Row(verticalAlignment = Alignment.CenterVertically) {
                                Icon(
                                    Icons.Rounded.CalendarMonth,
                                    contentDescription = null,
                                    tint = accent,
                                    modifier = Modifier.size(18.dp)
                                )
                                Spacer(Modifier.size(8.dp))
                                Text(
                                    "Anggota sejak $since",
                                    color = Color.White.copy(alpha = 0.7f),
                                    style = MaterialTheme.typography.bodyMedium
                                )
                            }
                        }
                    }
                }

                // ---- Statistik mendengar + riwayat putar ----
                if (com.jimz.jimztune.AppGraph.remote.featureOn("profile_stats")) ListeningSection(accent)

                // ---- Sinkronisasi ----
                Spacer(Modifier.height(14.dp))
                GroupCard(modifier = Modifier.padding(horizontal = 20.dp)) {
                    Row(
                        modifier = Modifier.padding(horizontal = 18.dp, vertical = 14.dp),
                        verticalAlignment = Alignment.CenterVertically
                    ) {
                        Box(
                            modifier = Modifier
                                .size(10.dp)
                                .clip(CircleShape)
                                .background(syncColor(cloud.status))
                        )
                        Spacer(Modifier.size(12.dp))
                        Column(modifier = Modifier.weight(1f)) {
                            Text(
                                "Sinkronisasi",
                                style = MaterialTheme.typography.labelMedium,
                                fontWeight = FontWeight.SemiBold,
                                color = Color.White.copy(alpha = 0.6f)
                            )
                            Text(
                                syncText(cloud.status),
                                color = Color.White.copy(alpha = 0.92f),
                                style = MaterialTheme.typography.bodyMedium
                            )
                        }
                    }
                }

                // ---- Keluar ----
                Spacer(Modifier.height(22.dp))
                Row(
                    modifier = Modifier
                        .padding(horizontal = 20.dp)
                        .fillMaxWidth()
                        .clip(RoundedCornerShape(18.dp))
                        .border(1.dp, Color(0xFFFF6B6B).copy(alpha = 0.45f), RoundedCornerShape(18.dp))
                        .clickable { confirmLogout = true }
                        .padding(vertical = 14.dp),
                    horizontalArrangement = Arrangement.Center,
                    verticalAlignment = Alignment.CenterVertically
                ) {
                    Icon(Icons.AutoMirrored.Rounded.Logout, contentDescription = null, tint = Color(0xFFFF8A8A), modifier = Modifier.size(20.dp))
                    Spacer(Modifier.size(8.dp))
                    Text("Keluar", color = Color(0xFFFF8A8A), fontWeight = FontWeight.SemiBold)
                }
                Spacer(Modifier.height(24.dp))
            }
        }
    }

    if (confirmLogout) {
        JimzDialog(
            onDismiss = { if (!loggingOut) confirmLogout = false },
            icon = Icons.AutoMirrored.Rounded.Logout,
            title = "Keluar dari akun?",
            message = "Data disimpan ke cloud dulu, lalu dihapus dari HP ini. Masuk lagi nanti dan semuanya kembali.",
            confirmLabel = if (loggingOut) "Menyimpan…" else "Keluar",
            destructive = true,
            onConfirm = {
                if (!loggingOut) {
                    loggingOut = true
                    scope.launch {
                        val r = auth.signOut()
                        loggingOut = false
                        confirmLogout = false
                        if (r.ok) onBack() else Toast.makeText(context, r.message, Toast.LENGTH_LONG).show()
                    }
                }
            }
        )
    }
}

/** Kartu angka kecil: ikon, jumlah, nama koleksi. */
@Composable
private fun StatTile(icon: ImageVector, value: Int, label: String, accent: Color, modifier: Modifier = Modifier) {
    val shape = RoundedCornerShape(20.dp)
    Column(
        modifier = modifier
            .clip(shape)
            .cardSurface(shape)
            .padding(vertical = 14.dp),
        horizontalAlignment = Alignment.CenterHorizontally
    ) {
        Icon(icon, contentDescription = null, tint = accent, modifier = Modifier.size(20.dp))
        Spacer(Modifier.height(6.dp))
        Text(value.toString(), color = Color.White, fontSize = 22.sp, fontWeight = FontWeight.Bold)
        Text(label, color = Color.White.copy(alpha = 0.6f), fontSize = 12.sp)
    }
}

/** Tombol bulat sinkronkan; ikonnya berputar selama sinkronisasi berjalan. */
@Composable
private fun SyncIconButton(syncing: Boolean, onClick: () -> Unit) {
    val transition = rememberInfiniteTransition(label = "sync")
    // Animasi hanya berjalan saat sinkronisasi berlangsung (tidak menghabiskan baterai saat diam).
    var angle = 0f
    if (syncing) {
        val a by transition.animateFloat(
            initialValue = 0f,
            targetValue = 360f,
            animationSpec = infiniteRepeatable(tween(1000, easing = LinearEasing), RepeatMode.Restart),
            label = "syncAngle"
        )
        angle = a
    }
    Box(
        modifier = Modifier
            .size(46.dp)
            .clip(CircleShape)
            .background(Color.White.copy(alpha = 0.09f))
            .border(1.dp, Color.White.copy(alpha = 0.12f), CircleShape)
            .clickable(enabled = !syncing, onClick = onClick),
        contentAlignment = Alignment.Center
    ) {
        Icon(
            Icons.Rounded.Sync,
            contentDescription = "Sinkronkan sekarang",
            tint = Color.White,
            modifier = Modifier.size(22.dp).rotate(angle)
        )
    }
}

private fun syncColor(s: SyncStatus): Color = when (s) {
    SyncStatus.Idle -> Color(0xFF8A90A8)
    SyncStatus.Syncing -> Color(0xFFFFC857)
    is SyncStatus.Done -> Color(0xFF4CD98A)
    is SyncStatus.Failed -> Color(0xFFFF6B6B)
}

@Composable
private fun Label(text: String, accent: Color) {
    Text(
        text,
        style = MaterialTheme.typography.labelMedium,
        fontWeight = FontWeight.SemiBold,
        letterSpacing = 1.2.sp,
        color = accent
    )
}

@Composable
private fun Badge(text: String, accent: Color) {
    Text(
        text,
        color = Color.White,
        fontSize = 12.sp,
        fontWeight = FontWeight.SemiBold,
        modifier = Modifier
            .clip(RoundedCornerShape(8.dp))
            .background(accent.copy(alpha = 0.28f))
            .border(1.dp, accent.copy(alpha = 0.5f), RoundedCornerShape(8.dp))
            .padding(horizontal = 8.dp, vertical = 3.dp)
    )
}

private fun syncText(s: SyncStatus): String = when (s) {
    SyncStatus.Idle -> "Belum pernah disinkronkan"
    SyncStatus.Syncing -> "Sedang menyinkronkan…"
    is SyncStatus.Done -> "Tersimpan di cloud • " + SimpleDateFormat("d MMM, HH:mm", Locale("id", "ID")).format(Date(s.atMs))
    is SyncStatus.Failed -> com.jimz.jimztune.util.UserError.text("Gagal menyinkronkan. Coba lagi nanti.", s.message)
}

private fun formatDate(iso: String): String? = try {
    val d = SimpleDateFormat("yyyy-MM-dd", Locale.US).parse(iso.take(10))
    SimpleDateFormat("d MMMM yyyy", Locale("id", "ID")).format(d!!)
} catch (_: Exception) {
    null
}
