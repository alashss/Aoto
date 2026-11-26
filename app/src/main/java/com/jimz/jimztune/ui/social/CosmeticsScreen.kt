package com.jimz.jimztune.ui.social

import androidx.compose.foundation.background
import androidx.compose.foundation.border
import androidx.compose.foundation.clickable
import androidx.compose.foundation.shape.CircleShape
import androidx.compose.foundation.rememberScrollState
import androidx.compose.foundation.horizontalScroll
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
import androidx.compose.foundation.lazy.grid.GridCells
import androidx.compose.foundation.lazy.grid.LazyVerticalGrid
import androidx.compose.foundation.lazy.grid.items
import androidx.compose.foundation.layout.PaddingValues
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.rounded.Lock
import androidx.compose.material3.CircularProgressIndicator
import androidx.compose.material3.Icon
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
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.text.style.TextAlign
import androidx.compose.ui.text.style.TextOverflow
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import com.jimz.jimztune.AppGraph
import com.jimz.jimztune.data.social.CosmeticItem
import com.jimz.jimztune.data.social.CosmeticsApi
import com.jimz.jimztune.data.social.CosmeticsCatalog
import com.jimz.jimztune.data.social.CosmeticsStore
import com.jimz.jimztune.data.social.Equipped
import com.jimz.jimztune.ui.ranking.RankAvatar
import com.jimz.jimztune.ui.theme.LocalCoverTheme
import kotlinx.coroutines.launch

/** Kosmetik profil: pratinjau langsung, pilih bingkai / plat nama / efek. Yang terkunci tampil dengan syarat dapatnya. */
@Composable
fun CosmeticsScreen(onBack: () -> Unit) {
    val scope = rememberCoroutineScope()
    val accent = LocalCoverTheme.current.accent
    var cat by remember { mutableStateOf<CosmeticsCatalog?>(null) }
    var err by remember { mutableStateOf<String?>(null) }
    var eq by remember { mutableStateOf(Equipped()) }
    var tab by remember { mutableStateOf("frame") }
    var saving by remember { mutableStateOf(false) }
    val profile = AppGraph.profile.profile
    val me = AppGraph.auth.session?.userId

    LaunchedEffect(Unit) {
        val r = CosmeticsApi.catalog()
        if (r.value != null) { cat = r.value; eq = r.value.equipped } else err = r.error
    }

    SocialScaffold(title = "Kosmetik profil", subtitle = cat?.let { "Level ${it.level}" }, onBack = onBack) {
        val c = cat
        if (c == null) {
            Box(Modifier.fillMaxSize(), Alignment.Center) {
                if (err != null) Text(err ?: "", color = Color.White.copy(alpha = 0.7f), modifier = Modifier.padding(32.dp), textAlign = TextAlign.Center) else CircularProgressIndicator(color = Color.White)
            }
            return@SocialScaffold
        }
        // Pratinjau
        Box(
            Modifier.fillMaxWidth().padding(horizontal = 16.dp, vertical = 6.dp).clip(RoundedCornerShape(24.dp))
                .background(Brush.linearGradient(listOf(accent.copy(alpha = 0.40f), Color(0xFF12163A))))
        ) {
            ProfileEffect(eq.effect, Modifier.matchParentSize())
            Column(Modifier.fillMaxWidth().padding(vertical = 18.dp, horizontal = 16.dp), horizontalAlignment = Alignment.CenterHorizontally) {
                FramedAvatar(86.dp, equipped = eq) { RankAvatar(profile?.avatarUrl, profile?.displayName ?: "Kamu", 86.dp) }
                Spacer(Modifier.height(16.dp))
                PlateBox(eq.plate, Modifier.fillMaxWidth()) {
                    Box(Modifier.fillMaxWidth().padding(vertical = 12.dp), Alignment.Center) {
                        CosmeticName(profile?.displayName ?: "Kamu", eq, fontSize = 20.sp, fontWeight = FontWeight.Bold)
                    }
                }
                Spacer(Modifier.height(12.dp))
                CosmeticBubble(eq.bubble, RoundedCornerShape(20.dp), Modifier.align(Alignment.End), Brush.linearGradient(listOf(Color(0xFF8A4DFF), Color(0xFF3D7BFF)))) {
                    Text("Ini contoh gelembung chat", color = bubbleTextColor(eq.bubble) ?: Color.White, fontSize = 14.sp, modifier = Modifier.padding(horizontal = 14.dp, vertical = 9.dp))
                }
            }
        }
        // Tab jenis
        Row(Modifier.horizontalScroll(rememberScrollState()).padding(horizontal = 16.dp, vertical = 8.dp), horizontalArrangement = Arrangement.spacedBy(8.dp)) {
            CosmeticKinds.forEach { k ->
                Text(
                    kindLabel(k), fontSize = 13.sp, fontWeight = FontWeight.SemiBold, color = if (tab == k) Color.Black else Color.White,
                    modifier = Modifier.clip(RoundedCornerShape(50)).background(if (tab == k) accent else Color.White.copy(alpha = 0.08f))
                        .clickable { tab = k }.padding(horizontal = 16.dp, vertical = 8.dp)
                )
            }
        }
        val list = c.items.filter { it.kind == tab }
        LazyVerticalGrid(
            columns = GridCells.Fixed(3), modifier = Modifier.weight(1f),
            contentPadding = PaddingValues(horizontal = 16.dp, vertical = 4.dp),
            horizontalArrangement = Arrangement.spacedBy(10.dp), verticalArrangement = Arrangement.spacedBy(10.dp)
        ) {
            item(span = { androidx.compose.foundation.lazy.grid.GridItemSpan(3) }) {
                val none = when (tab) { "frame" -> eq.frame == null; "plate" -> eq.plate == null; "bubble" -> eq.bubble == null; else -> eq.effect == null }
                Text(
                    "Tanpa ${kindLabel(tab).lowercase()}", color = if (none) Color.Black else Color.White, fontSize = 13.sp, fontWeight = FontWeight.SemiBold, textAlign = TextAlign.Center,
                    modifier = Modifier.fillMaxWidth().clip(RoundedCornerShape(14.dp)).background(if (none) accent else Color.White.copy(alpha = 0.08f))
                        .clickable { eq = when (tab) { "frame" -> eq.copy(frame = null); "plate" -> eq.copy(plate = null); "bubble" -> eq.copy(bubble = null); else -> eq.copy(effect = null) } }
                        .padding(vertical = 10.dp)
                )
            }
            // Dikelompokkan per rarity: Legendaris, Epik, Langka, Umum.
            RarityOrder.forEach { r ->
                val group = list.filter { it.rarity == r }
                if (group.isNotEmpty()) {
                    item(span = { androidx.compose.foundation.lazy.grid.GridItemSpan(3) }, key = "h_$r") {
                        Row(Modifier.fillMaxWidth().padding(top = 10.dp), verticalAlignment = Alignment.CenterVertically) {
                            Box(Modifier.size(8.dp).clip(CircleShape).background(rarityColor(r)))
                            Text(rarityLabel(r), color = rarityColor(r), fontWeight = FontWeight.Bold, fontSize = 13.sp, modifier = Modifier.padding(start = 8.dp))
                            Text("${group.count { it.owned }}/${group.size}", color = Color.White.copy(alpha = 0.45f), fontSize = 11.sp, modifier = Modifier.padding(start = 8.dp))
                        }
                    }
                    items(group, key = { it.id }) { item ->
                        val selected = when (tab) { "frame" -> eq.frame == item.id; "plate" -> eq.plate == item.id; "bubble" -> eq.bubble == item.id; else -> eq.effect == item.id }
                        CosmeticTile(item, selected, accent) {
                            if (item.owned) eq = when (tab) { "frame" -> eq.copy(frame = item.id); "plate" -> eq.copy(plate = item.id); "bubble" -> eq.copy(bubble = item.id); else -> eq.copy(effect = item.id) }
                            else err = if (item.unlockLevel != null) "Terbuka di level ${item.unlockLevel} (kamu level ${c.level}), atau dari owner." else "Item eksklusif, hanya diberikan owner."
                        }
                    }
                }
            }
        }
        if (err != null) Text(err ?: "", color = Color(0xFFFFCC80), fontSize = 12.sp, modifier = Modifier.padding(horizontal = 20.dp, vertical = 4.dp), textAlign = TextAlign.Center)
        Text(
            if (saving) "Menyimpan…" else "Terapkan", color = Color.Black, fontWeight = FontWeight.Bold, textAlign = TextAlign.Center,
            modifier = Modifier.fillMaxWidth().padding(16.dp).clip(RoundedCornerShape(50)).background(accent)
                .clickable(enabled = !saving) {
                    scope.launch {
                        saving = true
                        val e = CosmeticsApi.equip(eq)
                        err = e
                        if (e == null && me != null) { CosmeticsStore.put(me, eq); onBack() }
                        saving = false
                    }
                }.padding(vertical = 14.dp)
        )
    }
}

@Composable
private fun CosmeticTile(item: CosmeticItem, selected: Boolean, accent: Color, onClick: () -> Unit) {
    val rc = rarityColor(item.rarity)
    Column(
        Modifier.clip(RoundedCornerShape(18.dp)).background(Color.White.copy(alpha = 0.07f))
            .border(if (selected) 2.dp else 1.dp, if (selected) accent else rc.copy(alpha = 0.35f), RoundedCornerShape(18.dp))
            .clickable(onClick = onClick).padding(8.dp),
        horizontalAlignment = Alignment.CenterHorizontally
    ) {
        Box(Modifier.fillMaxWidth().height(78.dp).clip(RoundedCornerShape(12.dp)), Alignment.Center) {
            when (item.kind) {
                "frame" -> FramedAvatar(44.dp, equipped = Equipped(frame = item.id)) { RankAvatar(null, "A", 44.dp) }
                "plate" -> PlateBox(item.id, Modifier.fillMaxSize(), RoundedCornerShape(12.dp)) { Text("Nama", color = Color.White, fontWeight = FontWeight.Bold, modifier = Modifier.align(Alignment.Center)) }
                "bubble" -> Box(Modifier.fillMaxSize().background(Color(0xFF12163A)), Alignment.Center) {
                    CosmeticBubble(item.id, RoundedCornerShape(16.dp), Modifier.padding(horizontal = 8.dp)) {
                        Text("Halo!", color = bubbleTextColor(item.id) ?: Color.White, fontSize = 13.sp, modifier = Modifier.padding(horizontal = 14.dp, vertical = 8.dp))
                    }
                }
                else -> Box(Modifier.fillMaxSize().background(Color(0xFF12163A))) { ProfileEffect(item.id) }
            }
            if (!item.owned) Box(Modifier.fillMaxSize().background(Color.Black.copy(alpha = 0.55f)), Alignment.Center) {
                Icon(Icons.Rounded.Lock, null, tint = Color.White, modifier = Modifier.size(22.dp))
            }
        }
        Spacer(Modifier.height(6.dp))
        Text(item.name, color = Color.White, fontSize = 12.sp, fontWeight = FontWeight.SemiBold, maxLines = 1, overflow = TextOverflow.Ellipsis)
        Text(
            if (item.owned) rarityLabel(item.rarity) else item.unlockLevel?.let { "Level $it" } ?: "Owner",
            color = if (item.owned) rc else Color.White.copy(alpha = 0.5f), fontSize = 10.sp, fontWeight = FontWeight.Bold
        )
    }
}
