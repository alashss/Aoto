package com.jimz.jimztune.ui.social

import androidx.compose.foundation.background
import androidx.compose.foundation.border
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.layout.width
import androidx.compose.foundation.rememberScrollState
import androidx.compose.foundation.shape.CircleShape
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.foundation.clickable
import androidx.compose.foundation.verticalScroll
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.rounded.Block
import androidx.compose.material.icons.rounded.Chat
import androidx.compose.material.icons.rounded.Lock
import androidx.compose.material.icons.rounded.MoreVert
import androidx.compose.material.icons.rounded.PersonAdd
import androidx.compose.material.icons.rounded.PersonRemove
import androidx.compose.material.icons.rounded.Security
import androidx.compose.material.icons.rounded.Check
import androidx.compose.material3.CircularProgressIndicator
import androidx.compose.material3.DropdownMenu
import androidx.compose.material3.DropdownMenuItem
import androidx.compose.material3.Icon
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
import androidx.compose.ui.graphics.Brush
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.layout.ContentScale
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.text.style.TextAlign
import androidx.compose.ui.text.style.TextOverflow
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import coil.compose.AsyncImage
import com.jimz.jimztune.AppGraph
import com.jimz.jimztune.data.social.SocialApi
import com.jimz.jimztune.data.social.SocialNav
import com.jimz.jimztune.data.social.SocialPage
import com.jimz.jimztune.data.social.UserProfile
import com.jimz.jimztune.ui.components.PillTextButton
import kotlinx.coroutines.launch

/** Profil seseorang (juga dipakai untuk profil sendiri). Bagian yang disembunyikan pemiliknya tidak ditampilkan. */
@Composable
fun ProfileViewScreen(userId: String, onBack: () -> Unit) {
    val scope = rememberCoroutineScope()
    var profile by remember(userId) { mutableStateOf<UserProfile?>(null) }
    var error by remember(userId) { mutableStateOf<String?>(null) }
    var reload by remember { mutableStateOf(0) }
    var busy by remember { mutableStateOf(false) }
    var info by remember { mutableStateOf<String?>(null) }
    var menu by remember { mutableStateOf(false) }

    LaunchedEffect(userId, reload) {
        val r = SocialApi.profile(userId)
        if (r.value != null) { profile = r.value; error = null } else if (profile == null) error = r.error ?: "Profil tidak ditemukan"
    }
    // Segarkan berkala supaya status online & lagu yang diputar ikut berubah.
    LaunchedEffect(userId) {
        while (true) {
            kotlinx.coroutines.delay(20_000)
            reload++
        }
    }

    val p = profile
    SocialScaffold(
        title = p?.name ?: "Profil", onBack = onBack,
        actions = {
            if (p != null && p.relation != "self") {
                Box {
                    com.jimz.jimztune.ui.components.GlassIconButton(Icons.Rounded.MoreVert, "Menu", onClick = { menu = true })
                    DropdownMenu(expanded = menu, onDismissRequest = { menu = false }) {
                        if (p.relation == "friend") {
                            DropdownMenuItem(
                                text = { Text("Hapus teman") },
                                leadingIcon = { Icon(Icons.Rounded.PersonRemove, null) },
                                onClick = {
                                    menu = false
                                    scope.launch { info = SocialApi.removeFriend(p.id) ?: "Teman dihapus"; reload++ }
                                }
                            )
                        }
                        DropdownMenuItem(
                            text = { Text(if (p.blockedByMe) "Buka blokir" else "Blokir") },
                            leadingIcon = { Icon(Icons.Rounded.Block, null) },
                            onClick = {
                                menu = false
                                scope.launch {
                                    info = (if (p.blockedByMe) SocialApi.unblock(p.id) else SocialApi.block(p.id))
                                        ?: if (p.blockedByMe) "Blokir dibuka" else "Pengguna diblokir"
                                    reload++
                                }
                            }
                        )
                    }
                }
            }
        }
    ) {
        when {
            p == null && error == null -> Box(Modifier.fillMaxSize(), Alignment.Center) { CircularProgressIndicator(color = Color.White) }
            p == null -> Box(Modifier.fillMaxSize().padding(32.dp), Alignment.Center) {
                Text(error ?: "", color = Color.White.copy(alpha = 0.7f), textAlign = TextAlign.Center)
            }
            else -> ProfileBody(p, busy, info, onInfoShown = { info = null }) { action ->
                scope.launch {
                    busy = true
                    when (action) {
                        "add" -> info = SocialApi.requestFriend(p.id).let { it.error ?: if (it.value == "accepted") "Kalian sekarang berteman" else "Permintaan terkirim" }
                        "accept" -> info = p.requestId?.let { SocialApi.respond(it, true) } ?: "Kalian sekarang berteman"
                        "decline" -> info = p.requestId?.let { SocialApi.respond(it, false) } ?: "Permintaan ditolak"
                        "cancel" -> info = SocialApi.removeFriend(p.id) ?: "Permintaan dibatalkan"
                        "chat" -> SocialNav.open(SocialPage.Dm(p.id, p.name, p.avatar))
                        "privacy" -> SocialNav.open(SocialPage.Privacy)
                    }
                    busy = false
                    reload++
                }
            }
        }
    }
}

@Composable
private fun ProfileBody(p: UserProfile, busy: Boolean, info: String?, onInfoShown: () -> Unit, onAction: (String) -> Unit) {
    val accent = parseHex(p.accentHex)
    androidx.compose.runtime.LaunchedEffect(p.id) { com.jimz.jimztune.data.social.CosmeticsStore.request(p.id) }
    val eq = com.jimz.jimztune.data.social.CosmeticsStore.map[p.id]
    Box(Modifier.fillMaxSize()) {
    Column(modifier = Modifier.fillMaxSize().verticalScroll(rememberScrollState()).padding(bottom = 40.dp)) {
        // Banner + foto
        Box(modifier = Modifier.fillMaxWidth().height(190.dp)) {
            Box(
                modifier = Modifier.fillMaxWidth().height(130.dp)
                    .background(Brush.linearGradient(listOf(accent.copy(alpha = 0.9f), accent.copy(alpha = 0.25f), Color(0xFF12163A))))
            ) {
                if (!p.banner.isNullOrBlank()) {
                    AsyncImage(model = p.banner, contentDescription = null, contentScale = ContentScale.Crop, modifier = Modifier.fillMaxSize())
                    Box(Modifier.fillMaxSize().background(Brush.verticalGradient(listOf(Color.Transparent, Color(0x99000000)))))
                }
            }
            Box(modifier = Modifier.align(Alignment.BottomStart).padding(start = 28.dp, bottom = 10.dp)) {
                AvatarWithDot(p.avatar, p.name, 88.dp, p.online, ring = accent, userId = p.id)
            }
        }
        Column(modifier = Modifier.padding(horizontal = 20.dp)) {
            PlateBox(eq?.plate, Modifier.fillMaxWidth().padding(top = 4.dp)) {
            Row(verticalAlignment = Alignment.CenterVertically, modifier = Modifier.padding(horizontal = if (eq?.plate != null) 14.dp else 0.dp, vertical = if (eq?.plate != null) 12.dp else 0.dp)) {
                CosmeticName(p.name, eq, fontSize = 24.sp, fontWeight = FontWeight.Bold, modifier = Modifier.weight(1f, fill = false))
                if (p.isOwner) {
                    Spacer(Modifier.width(8.dp))
                    OwnerBadge(Modifier, 20.dp)
                }
            }
            }
            if (!p.username.isNullOrBlank()) Text("@" + p.username, color = Color.White.copy(alpha = 0.6f), fontSize = 14.sp)

            // Status online / sedang mendengarkan
            if (!p.restricted) {
                Spacer(Modifier.height(8.dp))
                val now = p.now
                if (now != null) {
                    NowPlayingCard(now.title, now.artist, now.cover, accent)
                } else if (p.online == true) {
                    StatusPill("Online", OnlineGreen)
                } else if (p.online == false && !p.lastSeen.isNullOrBlank()) {
                    StatusPill("Terakhir aktif " + agoText(p.lastSeen), Color.White.copy(alpha = 0.5f))
                }
            }

            if (p.statusText.isNotBlank() || p.statusEmoji.isNotBlank()) {
                Spacer(Modifier.height(10.dp))
                Row(
                    modifier = Modifier.clip(RoundedCornerShape(50)).background(Color.White.copy(alpha = 0.08f)).padding(horizontal = 12.dp, vertical = 6.dp),
                    verticalAlignment = Alignment.CenterVertically
                ) {
                    if (p.statusEmoji.isNotBlank()) { Text(p.statusEmoji, fontSize = 16.sp); Spacer(Modifier.width(6.dp)) }
                    Text(p.statusText, color = Color.White.copy(alpha = 0.9f), fontSize = 13.sp)
                }
            }
            if (!p.note.isNullOrBlank()) {
                Spacer(Modifier.height(10.dp))
                Text("💭  " + p.note, color = Color.White.copy(alpha = 0.85f), fontSize = 14.sp)
            }
            if (p.bio.isNotBlank()) {
                Spacer(Modifier.height(12.dp))
                Text(p.bio, color = Color.White.copy(alpha = 0.85f), fontSize = 14.sp, lineHeight = 20.sp)
            }
            if (p.badges.isNotEmpty()) {
                Spacer(Modifier.height(12.dp))
                Row(horizontalArrangement = Arrangement.spacedBy(6.dp)) {
                    p.badges.take(5).forEach { b ->
                        Text(
                            b, fontSize = 12.sp, color = accent, fontWeight = FontWeight.SemiBold,
                            modifier = Modifier.clip(RoundedCornerShape(50)).border(1.dp, accent.copy(alpha = 0.5f), RoundedCornerShape(50)).padding(horizontal = 10.dp, vertical = 4.dp)
                        )
                    }
                }
            }

            Spacer(Modifier.height(18.dp))
            ActionRow(p, busy, onAction)

            if (info != null) {
                Spacer(Modifier.height(10.dp))
                Text(info, color = accent, fontSize = 13.sp)
                LaunchedEffect(info) { kotlinx.coroutines.delay(2500); onInfoShown() }
            }

            if (p.restricted) {
                Spacer(Modifier.height(28.dp))
                Column(
                    modifier = Modifier.fillMaxWidth().clip(RoundedCornerShape(20.dp)).background(Color.White.copy(alpha = 0.06f)).padding(24.dp),
                    horizontalAlignment = Alignment.CenterHorizontally
                ) {
                    Icon(Icons.Rounded.Lock, null, tint = Color.White.copy(alpha = 0.6f), modifier = Modifier.size(30.dp))
                    Spacer(Modifier.height(8.dp))
                    Text("Profil ini dibatasi", fontWeight = FontWeight.SemiBold, color = Color.White)
                    Text("Pemiliknya hanya membagikan profil ke teman.", color = Color.White.copy(alpha = 0.6f), fontSize = 13.sp, textAlign = TextAlign.Center)
                }
            } else {
                p.stats?.let { s ->
                    Spacer(Modifier.height(22.dp))
                    Row(horizontalArrangement = Arrangement.spacedBy(10.dp)) {
                        StatTile("Level", s.level.toString(), accent, Modifier.weight(1f))
                        StatTile("Diputar", s.plays.toString(), accent, Modifier.weight(1f))
                        StatTile("Waktu", durationText(s.seconds), accent, Modifier.weight(1f))
                        if (p.friends != null) StatTile("Teman", p.friends.toString(), accent, Modifier.weight(1f))
                    }
                }
                val top = p.top
                if (!top.isNullOrEmpty()) {
                    Spacer(Modifier.height(24.dp))
                    Text("Lagu favorit", fontWeight = FontWeight.Bold, fontSize = 17.sp, color = Color.White)
                    Spacer(Modifier.height(8.dp))
                    top.take(5).forEachIndexed { i, t ->
                        Row(
                            modifier = Modifier.fillMaxWidth().padding(vertical = 4.dp).clip(RoundedCornerShape(14.dp)).background(Color.White.copy(alpha = 0.05f)).padding(10.dp),
                            verticalAlignment = Alignment.CenterVertically
                        ) {
                            Text((i + 1).toString(), color = accent, fontWeight = FontWeight.Black, modifier = Modifier.width(26.dp))
                            Cover(t.cover, 42.dp)
                            Spacer(Modifier.width(12.dp))
                            Column(Modifier.weight(1f)) {
                                Text(t.title, color = Color.White, fontWeight = FontWeight.SemiBold, maxLines = 1, overflow = TextOverflow.Ellipsis)
                                Text(t.artist, color = Color.White.copy(alpha = 0.6f), fontSize = 12.sp, maxLines = 1, overflow = TextOverflow.Ellipsis)
                            }
                            Text("${t.plays}×", color = Color.White.copy(alpha = 0.7f), fontSize = 13.sp)
                        }
                    }
                }
            }
        }
    }
    // Efek kosmetik memenuhi seluruh halaman (atas sampai bawah), tidak menangkap sentuhan.
    ProfileEffect(eq?.effect, Modifier.matchParentSize(), entrance = true)
    }
}

@Composable
private fun ActionRow(p: UserProfile, busy: Boolean, onAction: (String) -> Unit) {
    when (p.relation) {
        "self" -> Row(horizontalArrangement = Arrangement.spacedBy(10.dp)) {
            PillTextButton("Pengaturan privasi", { onAction("privacy") }, primary = false, modifier = Modifier.weight(1f))
        }
        "friend" -> PillTextButton("Kirim pesan", { onAction("chat") }, modifier = Modifier.fillMaxWidth(), enabled = !busy)
        "pending_out" -> PillTextButton("Batalkan permintaan", { onAction("cancel") }, primary = false, modifier = Modifier.fillMaxWidth(), enabled = !busy)
        "pending_in" -> Row(horizontalArrangement = Arrangement.spacedBy(10.dp)) {
            PillTextButton("Terima", { onAction("accept") }, modifier = Modifier.weight(1f), enabled = !busy)
            PillTextButton("Tolak", { onAction("decline") }, primary = false, modifier = Modifier.weight(1f), enabled = !busy)
        }
        else -> when {
            p.blockedByMe -> Text("Kamu memblokir pengguna ini.", color = Color.White.copy(alpha = 0.6f), fontSize = 13.sp)
            !p.acceptsRequests -> Text("Pengguna ini tidak menerima permintaan teman.", color = Color.White.copy(alpha = 0.6f), fontSize = 13.sp)
            else -> PillTextButton("Tambah teman", { onAction("add") }, modifier = Modifier.fillMaxWidth(), enabled = !busy)
        }
    }
}

@Composable
private fun StatusPill(text: String, color: Color) {
    Row(
        modifier = Modifier.clip(RoundedCornerShape(50)).background(color.copy(alpha = 0.14f)).padding(horizontal = 12.dp, vertical = 6.dp),
        verticalAlignment = Alignment.CenterVertically
    ) {
        Box(Modifier.size(8.dp).clip(CircleShape).background(color))
        Spacer(Modifier.width(8.dp))
        Text(text, color = color, fontSize = 13.sp, fontWeight = FontWeight.SemiBold)
    }
}

@Composable
private fun NowPlayingCard(title: String, artist: String, cover: String?, accent: Color) {
    Row(
        modifier = Modifier.fillMaxWidth().clip(RoundedCornerShape(16.dp))
            .background(Brush.horizontalGradient(listOf(accent.copy(alpha = 0.28f), Color.White.copy(alpha = 0.05f))))
            .padding(10.dp),
        verticalAlignment = Alignment.CenterVertically
    ) {
        Cover(cover, 46.dp)
        Spacer(Modifier.width(12.dp))
        Column(Modifier.weight(1f)) {
            Text("Sedang mendengarkan", color = OnlineGreen, fontSize = 11.sp, fontWeight = FontWeight.Bold)
            Text(title, color = Color.White, fontWeight = FontWeight.SemiBold, maxLines = 1, overflow = TextOverflow.Ellipsis)
            Text(artist, color = Color.White.copy(alpha = 0.65f), fontSize = 12.sp, maxLines = 1, overflow = TextOverflow.Ellipsis)
        }
    }
}

@Composable
private fun Cover(url: String?, size: androidx.compose.ui.unit.Dp) {
    Box(modifier = Modifier.size(size).clip(RoundedCornerShape(10.dp)).background(Color.White.copy(alpha = 0.1f))) {
        if (!url.isNullOrBlank()) AsyncImage(model = url, contentDescription = null, contentScale = ContentScale.Crop, modifier = Modifier.fillMaxSize())
    }
}

@Composable
private fun StatTile(label: String, value: String, accent: Color, modifier: Modifier) {
    Column(
        modifier = modifier.clip(RoundedCornerShape(16.dp)).background(Color.White.copy(alpha = 0.06f)).padding(vertical = 12.dp),
        horizontalAlignment = Alignment.CenterHorizontally
    ) {
        Text(value, color = accent, fontWeight = FontWeight.Black, fontSize = 17.sp, maxLines = 1)
        Text(label, color = Color.White.copy(alpha = 0.6f), fontSize = 11.sp)
    }
}

private fun durationText(sec: Long): String {
    val h = sec / 3600
    val m = (sec % 3600) / 60
    return if (h > 0) "${h}j ${m}m" else "${m}m"
}
