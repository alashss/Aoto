package com.jimz.jimztune.ui.social

import androidx.compose.animation.core.Spring
import androidx.compose.animation.core.animateFloatAsState
import androidx.compose.animation.core.spring
import androidx.compose.foundation.background
import androidx.compose.foundation.clickable
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.BoxWithConstraints
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.offset
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.layout.width
import androidx.compose.foundation.rememberScrollState
import androidx.compose.foundation.shape.CircleShape
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.foundation.verticalScroll
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.rounded.Block
import androidx.compose.material.icons.rounded.ChevronRight
import androidx.compose.material.icons.rounded.EmojiEvents
import androidx.compose.material.icons.rounded.Group
import androidx.compose.material.icons.rounded.GraphicEq
import androidx.compose.material.icons.rounded.Insights
import androidx.compose.material.icons.rounded.Lock
import androidx.compose.material.icons.rounded.Public
import androidx.compose.material.icons.rounded.PersonAdd
import androidx.compose.material.icons.rounded.Sensors
import androidx.compose.material.icons.rounded.Star
import androidx.compose.material.icons.rounded.AutoAwesome
import androidx.compose.material3.CircularProgressIndicator
import androidx.compose.material3.Icon
import androidx.compose.material3.Switch
import androidx.compose.material3.SwitchDefaults
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
import androidx.compose.ui.graphics.vector.ImageVector
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.text.style.TextAlign
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import com.jimz.jimztune.AppGraph
import com.jimz.jimztune.data.social.BlockedRow
import com.jimz.jimztune.data.social.CosmeticsStore
import com.jimz.jimztune.data.social.PrivacySettings
import com.jimz.jimztune.data.social.SocialApi
import com.jimz.jimztune.data.social.SocialNav
import com.jimz.jimztune.data.social.SocialPage
import com.jimz.jimztune.ui.components.PillTextButton
import com.jimz.jimztune.ui.theme.LocalCoverTheme
import kotlinx.coroutines.launch

/** Atur siapa yang boleh melihat profil dan bagian mana yang disembunyikan. Tersimpan otomatis. */
@Composable
fun PrivacyScreen(onBack: () -> Unit) {
    val scope = rememberCoroutineScope()
    val accent = LocalCoverTheme.current.accent
    var p by remember { mutableStateOf<PrivacySettings?>(null) }
    var blocked by remember { mutableStateOf<List<BlockedRow>>(emptyList()) }
    var error by remember { mutableStateOf<String?>(null) }
    val me = AppGraph.auth.session?.userId
    val profile = AppGraph.profile.profile

    LaunchedEffect(Unit) {
        me?.let { CosmeticsStore.request(it) }
        val r = SocialApi.privacy()
        if (r.value != null) p = r.value else error = r.error
        blocked = SocialApi.blocked().value ?: emptyList()
    }

    fun save(n: PrivacySettings) {
        p = n
        scope.launch { error = SocialApi.savePrivacy(n) }
    }

    SocialScaffold(title = "Privasi & tampilan profil", onBack = onBack) {
        val s = p
        if (s == null) {
            Box(Modifier.fillMaxSize(), Alignment.Center) {
                if (error != null) Text(error ?: "", color = Color.White.copy(alpha = 0.7f), modifier = Modifier.padding(32.dp)) else CircularProgressIndicator(color = Color.White)
            }
            return@SocialScaffold
        }
        Column(Modifier.fillMaxSize().verticalScroll(rememberScrollState()).padding(horizontal = 16.dp).padding(bottom = 32.dp)) {
            // Kartu ringkas: begini orang lain melihatmu + jalan ke Kosmetik
            val eq = me?.let { CosmeticsStore.map[it] }
            Box(
                Modifier.fillMaxWidth().padding(top = 6.dp).clip(RoundedCornerShape(24.dp))
                    .background(Brush.linearGradient(listOf(accent.copy(alpha = 0.40f), Color(0xFF12163A))))
            ) {
                ProfileEffect(eq?.effect, Modifier.matchParentSize())
                Row(Modifier.fillMaxWidth().padding(18.dp), verticalAlignment = Alignment.CenterVertically) {
                    AvatarWithDot(profile?.avatarUrl, profile?.displayName ?: "Kamu", 64.dp, null, userId = me)
                    Spacer(Modifier.width(if (eq?.frame != null) 22.dp else 14.dp))
                    Column(Modifier.weight(1f)) {
                        CosmeticName(profile?.displayName ?: "Kamu", eq, fontSize = 18.sp, fontWeight = FontWeight.Bold)
                        Text(
                            when (s.view) { "everyone" -> "Profil terbuka untuk semua"; "friends" -> "Profil hanya untuk teman"; else -> "Profil dikunci" },
                            color = Color.White.copy(alpha = 0.65f), fontSize = 12.sp
                        )
                    }
                }
            }
            Row(
                Modifier.fillMaxWidth().padding(top = 10.dp).clip(RoundedCornerShape(18.dp)).background(Color.White.copy(alpha = 0.07f))
                    .clickable { SocialNav.open(SocialPage.Cosmetics) }.padding(horizontal = 16.dp, vertical = 14.dp),
                verticalAlignment = Alignment.CenterVertically
            ) {
                Icon(Icons.Rounded.AutoAwesome, null, tint = Gold)
                Spacer(Modifier.width(14.dp))
                Column(Modifier.weight(1f)) {
                    Text("Kosmetik profil", color = Color.White, fontWeight = FontWeight.SemiBold, fontSize = 15.sp)
                    Text("Bingkai foto, plat nama, efek profil", color = Color.White.copy(alpha = 0.55f), fontSize = 12.sp)
                }
                Icon(Icons.Rounded.ChevronRight, null, tint = Color.White.copy(alpha = 0.5f))
            }

            Label("Siapa yang bisa melihat profilmu")
            Segmented(
                listOf(Triple("everyone", "Semua", Icons.Rounded.Public), Triple("friends", "Teman", Icons.Rounded.Group), Triple("nobody", "Tidak ada", Icons.Rounded.Lock)),
                s.view, accent
            ) { save(s.copy(view = it)) }
            Text("Yang dibatasi hanya melihat nama & foto kamu.", color = Color.White.copy(alpha = 0.5f), fontSize = 12.sp, modifier = Modifier.padding(top = 8.dp, start = 4.dp))

            Label("Bagian yang ditampilkan")
            Card {
                SwitchRow(Icons.Rounded.Insights, "Statistik dengar", "Level, jumlah putar, waktu dengar", s.stats, accent) { save(s.copy(stats = it)) }
                SwitchRow(Icons.Rounded.EmojiEvents, "Lagu favorit", "5 lagu yang paling sering kamu putar", s.top, accent) { save(s.copy(top = it)) }
                SwitchRow(Icons.Rounded.GraphicEq, "Sedang mendengarkan", "Teman melihat lagu yang kamu putar", s.now, accent) { save(s.copy(now = it)) }
                SwitchRow(Icons.Rounded.Sensors, "Status online", "Titik hijau & terakhir aktif", s.online, accent) { save(s.copy(online = it)) }
                SwitchRow(Icons.Rounded.Star, "Jumlah teman", null, s.friends, accent) { save(s.copy(friends = it)) }
            }

            Label("Pertemanan")
            Card { SwitchRow(Icons.Rounded.PersonAdd, "Terima permintaan teman", "Matikan agar tidak ada yang bisa menambahmu", s.requests, accent) { save(s.copy(requests = it)) } }

            if (error != null) Text(error ?: "", color = Color(0xFFFF8A80), fontSize = 12.sp, modifier = Modifier.padding(top = 8.dp))

            Label("Diblokir (${blocked.size})")
            Card {
                if (blocked.isEmpty()) Row(Modifier.padding(16.dp), verticalAlignment = Alignment.CenterVertically) {
                    Icon(Icons.Rounded.Block, null, tint = Color.White.copy(alpha = 0.4f))
                    Spacer(Modifier.width(12.dp))
                    Text("Tidak ada pengguna yang diblokir.", color = Color.White.copy(alpha = 0.5f), fontSize = 13.sp)
                }
                blocked.forEach { b ->
                    Row(Modifier.fillMaxWidth().padding(horizontal = 14.dp, vertical = 8.dp), verticalAlignment = Alignment.CenterVertically) {
                        com.jimz.jimztune.ui.ranking.RankAvatar(b.avatar, b.name, 40.dp)
                        Spacer(Modifier.width(12.dp))
                        Text(b.name, color = Color.White, modifier = Modifier.weight(1f))
                        PillTextButton("Buka blokir", {
                            scope.launch { SocialApi.unblock(b.userId); blocked = SocialApi.blocked().value ?: blocked.filter { it.userId != b.userId } }
                        }, primary = false)
                    }
                }
            }
        }
    }
}

@Composable
private fun Segmented(options: List<Triple<String, String, ImageVector>>, selected: String, accent: Color, onSelect: (String) -> Unit) {
    val idx = options.indexOfFirst { it.first == selected }.coerceAtLeast(0)
    BoxWithConstraints(Modifier.fillMaxWidth().clip(RoundedCornerShape(50)).background(Color.White.copy(alpha = 0.08f)).padding(4.dp)) {
        val w = maxWidth / options.size
        val x by animateFloatAsState(idx * w.value, spring(dampingRatio = 0.72f, stiffness = Spring.StiffnessMediumLow), label = "seg")
        Box(Modifier.offset(x = x.dp).width(w).height(44.dp).clip(RoundedCornerShape(50)).background(accent))
        Row {
            options.forEach { (key, label, icon) ->
                val on = key == selected
                Row(
                    Modifier.weight(1f).height(44.dp).clip(RoundedCornerShape(50)).clickable { onSelect(key) },
                    horizontalArrangement = Arrangement.Center, verticalAlignment = Alignment.CenterVertically
                ) {
                    Icon(icon, null, tint = if (on) Color.Black else Color.White.copy(alpha = 0.7f), modifier = Modifier.size(18.dp))
                    Spacer(Modifier.width(6.dp))
                    Text(label, color = if (on) Color.Black else Color.White.copy(alpha = 0.8f), fontWeight = FontWeight.SemiBold, fontSize = 13.sp)
                }
            }
        }
    }
}

@Composable
private fun Label(text: String) {
    Text(text, color = Color.White.copy(alpha = 0.7f), fontSize = 12.sp, fontWeight = FontWeight.Bold, modifier = Modifier.padding(top = 22.dp, bottom = 8.dp, start = 4.dp))
}

@Composable
private fun Card(content: @Composable () -> Unit) {
    Column(Modifier.fillMaxWidth().clip(RoundedCornerShape(20.dp)).background(Color.White.copy(alpha = 0.06f))) { content() }
}

@Composable
private fun SwitchRow(icon: ImageVector, title: String, sub: String?, checked: Boolean, accent: Color, onChange: (Boolean) -> Unit) {
    Row(Modifier.fillMaxWidth().clickable { onChange(!checked) }.padding(horizontal = 16.dp, vertical = 11.dp), verticalAlignment = Alignment.CenterVertically) {
        Box(Modifier.size(36.dp).clip(CircleShape).background(accent.copy(alpha = if (checked) 0.25f else 0.1f)), Alignment.Center) {
            Icon(icon, null, tint = Color.White, modifier = Modifier.size(19.dp))
        }
        Spacer(Modifier.width(14.dp))
        Column(Modifier.weight(1f)) {
            Text(title, color = Color.White, fontSize = 15.sp)
            if (sub != null) Text(sub, color = Color.White.copy(alpha = 0.55f), fontSize = 12.sp)
        }
        Switch(checked = checked, onCheckedChange = onChange, colors = SwitchDefaults.colors(checkedTrackColor = accent, checkedThumbColor = Color.Black))
    }
}
