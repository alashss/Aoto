package com.jimz.jimztune.ui.social

import androidx.compose.foundation.background
import androidx.compose.foundation.clickable
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.width
import androidx.compose.foundation.rememberScrollState
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.foundation.verticalScroll
import androidx.compose.material3.CircularProgressIndicator
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
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import com.jimz.jimztune.data.social.BlockedRow
import com.jimz.jimztune.data.social.PrivacySettings
import com.jimz.jimztune.data.social.SocialApi
import com.jimz.jimztune.ui.components.PillTextButton
import com.jimz.jimztune.ui.ranking.RankAvatar
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

    LaunchedEffect(Unit) {
        val r = SocialApi.privacy()
        if (r.value != null) p = r.value else error = r.error
        blocked = SocialApi.blocked().value ?: emptyList()
    }

    fun save(n: PrivacySettings) {
        p = n
        scope.launch { error = SocialApi.savePrivacy(n) }
    }

    SocialScaffold(title = "Privasi profil", onBack = onBack) {
        val s = p
        if (s == null) {
            Box(Modifier.fillMaxSize(), Alignment.Center) {
                if (error != null) Text(error ?: "", color = Color.White.copy(alpha = 0.7f), modifier = Modifier.padding(32.dp)) else CircularProgressIndicator(color = Color.White)
            }
            return@SocialScaffold
        }
        Column(Modifier.fillMaxSize().verticalScroll(rememberScrollState()).padding(horizontal = 16.dp).padding(bottom = 32.dp)) {
            Label("Siapa yang bisa melihat profilmu")
            Row(horizontalArrangement = Arrangement.spacedBy(8.dp)) {
                listOf("everyone" to "Semua orang", "friends" to "Teman saja", "nobody" to "Tidak ada").forEach { (key, label) ->
                    Text(
                        label, fontSize = 13.sp, fontWeight = FontWeight.SemiBold,
                        color = if (s.view == key) Color.Black else Color.White,
                        modifier = Modifier.clip(RoundedCornerShape(50)).background(if (s.view == key) accent else Color.White.copy(alpha = 0.08f))
                            .clickable { save(s.copy(view = key)) }.padding(horizontal = 14.dp, vertical = 9.dp)
                    )
                }
            }
            Text("Yang dibatasi hanya akan melihat nama & foto kamu.", color = Color.White.copy(alpha = 0.5f), fontSize = 12.sp, modifier = Modifier.padding(top = 6.dp))

            Label("Bagian yang ditampilkan")
            Card {
                SwitchRow("Statistik dengar", "Level, jumlah putar, waktu dengar", s.stats, accent) { save(s.copy(stats = it)) }
                SwitchRow("Lagu favorit", "5 lagu yang paling sering kamu putar", s.top, accent) { save(s.copy(top = it)) }
                SwitchRow("Sedang mendengarkan", "Teman bisa melihat lagu yang kamu putar", s.now, accent) { save(s.copy(now = it)) }
                SwitchRow("Status online", "Titik hijau & terakhir aktif", s.online, accent) { save(s.copy(online = it)) }
                SwitchRow("Jumlah teman", null, s.friends, accent) { save(s.copy(friends = it)) }
            }

            Label("Pertemanan")
            Card { SwitchRow("Terima permintaan teman", "Matikan agar tidak ada yang bisa menambahmu", s.requests, accent) { save(s.copy(requests = it)) } }

            if (error != null) Text(error ?: "", color = Color(0xFFFF8A80), fontSize = 12.sp, modifier = Modifier.padding(top = 8.dp))

            Label("Diblokir (${blocked.size})")
            if (blocked.isEmpty()) Text("Tidak ada pengguna yang diblokir.", color = Color.White.copy(alpha = 0.5f), fontSize = 13.sp)
            blocked.forEach { b ->
                Row(Modifier.fillMaxWidth().padding(vertical = 6.dp), verticalAlignment = Alignment.CenterVertically) {
                    RankAvatar(b.avatar, b.name, 40.dp)
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

@Composable
private fun Label(text: String) {
    Text(text, color = Color.White.copy(alpha = 0.7f), fontSize = 12.sp, fontWeight = FontWeight.Bold, modifier = Modifier.padding(top = 20.dp, bottom = 8.dp))
}

@Composable
private fun Card(content: @Composable () -> Unit) {
    Column(Modifier.fillMaxWidth().clip(RoundedCornerShape(18.dp)).background(Color.White.copy(alpha = 0.06f))) { content() }
}

@Composable
private fun SwitchRow(title: String, sub: String?, checked: Boolean, accent: Color, onChange: (Boolean) -> Unit) {
    Row(Modifier.fillMaxWidth().clickable { onChange(!checked) }.padding(horizontal = 16.dp, vertical = 12.dp), verticalAlignment = Alignment.CenterVertically) {
        Column(Modifier.weight(1f)) {
            Text(title, color = Color.White, fontSize = 15.sp)
            if (sub != null) Text(sub, color = Color.White.copy(alpha = 0.55f), fontSize = 12.sp)
        }
        Switch(
            checked = checked, onCheckedChange = onChange,
            colors = SwitchDefaults.colors(checkedTrackColor = accent, checkedThumbColor = Color.Black)
        )
    }
}
