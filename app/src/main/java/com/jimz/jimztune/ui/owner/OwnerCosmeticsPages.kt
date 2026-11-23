package com.jimz.jimztune.ui.owner

import androidx.compose.foundation.clickable
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.width
import androidx.compose.material3.AlertDialog
import androidx.compose.material3.Text
import androidx.compose.material3.TextButton
import androidx.compose.runtime.Composable
import androidx.compose.runtime.LaunchedEffect
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.rememberCoroutineScope
import androidx.compose.runtime.setValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.platform.LocalContext
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import com.jimz.jimztune.data.social.CosmeticItem
import com.jimz.jimztune.data.social.CosmeticsApi
import com.jimz.jimztune.data.social.HolderRow
import com.jimz.jimztune.data.social.SearchRow
import com.jimz.jimztune.data.social.SocialApi
import com.jimz.jimztune.ui.components.GroupCard
import com.jimz.jimztune.ui.components.PillTextButton
import com.jimz.jimztune.ui.social.kindLabel
import com.jimz.jimztune.ui.social.rarityColor
import com.jimz.jimztune.ui.social.rarityLabel
import kotlinx.coroutines.delay
import kotlinx.coroutines.launch

// ============================================================================ KOSMETIK

@Composable
internal fun CosmeticsAdminPage(onBack: () -> Unit) {
    val context = LocalContext.current
    val scope = rememberCoroutineScope()
    val loader = rememberLoader { CosmeticsApi.ownerItems() }
    var kind by remember { mutableStateOf(0) }
    var edit by remember { mutableStateOf<CosmeticItem?>(null) }
    var giveFor by remember { mutableStateOf<CosmeticItem?>(null) }
    var holdersFor by remember { mutableStateOf<CosmeticItem?>(null) }

    OwnerScaffold(title = "Kosmetik", subtitle = "Bingkai, plat nama, efek · gift & syarat level", onBack = onBack) {
        val all = loader.value
        if (all == null) {
            if (loader.error != null) ErrorRow(loader.error ?: "", loader::reload) else LoadingRow()
            return@OwnerScaffold
        }
        ChipRow(listOf("Bingkai", "Plat nama", "Efek"), kind) { kind = it }
        val key = listOf("frame", "plate", "effect")[kind]
        OwnerSection(kindLabel(key))
        GroupCard {
            all.filter { it.kind == key }.forEachIndexed { i, it ->
                if (i > 0) OwnerDivider()
                Column(Modifier.fillMaxWidth().clickable { edit = it }.padding(horizontal = 16.dp, vertical = 12.dp)) {
                    Row(verticalAlignment = Alignment.CenterVertically) {
                        Text(it.name, color = Color.White, fontWeight = FontWeight.SemiBold, modifier = Modifier.weight(1f))
                        StatusPill(rarityLabel(it.rarity), rarityColor(it.rarity))
                        if (!it.active) { Spacer(Modifier.width(6.dp)); StatusPill("Nonaktif", Color(0xFFFF8A80)) }
                    }
                    Text(
                        (it.unlockLevel?.let { l -> "Otomatis di level $l" } ?: "Hanya dari owner") + " · ${it.holders} pemegang gift",
                        color = Color.White.copy(alpha = 0.6f), fontSize = 12.sp
                    )
                    Row(Modifier.padding(top = 8.dp), horizontalArrangement = Arrangement.spacedBy(8.dp)) {
                        PillTextButton("Beri ke pengguna", { giveFor = it })
                        PillTextButton("Pemegang", { holdersFor = it }, primary = false)
                    }
                }
            }
        }
    }

    edit?.let { item ->
        var lvl by remember(item.id) { mutableStateOf(item.unlockLevel?.toString() ?: "") }
        var active by remember(item.id) { mutableStateOf(item.active) }
        AlertDialog(
            onDismissRequest = { edit = null },
            title = { Text(item.name) },
            text = {
                Column {
                    OwnerField(lvl, { lvl = it.filter(Char::isDigit).take(3) }, "Level otomatis (kosong = hanya owner)")
                    Spacer(Modifier.height(8.dp))
                    OwnerSwitchRow("Aktif", "Nonaktif = tidak bisa dipakai siapa pun", active) { active = !active }
                }
            },
            confirmButton = {
                TextButton(onClick = {
                    edit = null
                    scope.launch { toast(context, CosmeticsApi.ownerSet(item.id, lvl.toIntOrNull(), active) ?: "Tersimpan"); loader.reload() }
                }) { Text("Simpan") }
            },
            dismissButton = { TextButton(onClick = { edit = null }) { Text("Batal") } }
        )
    }

    giveFor?.let { item ->
        var q by remember { mutableStateOf("") }
        var res by remember { mutableStateOf<List<SearchRow>>(emptyList()) }
        LaunchedEffect(q) {
            if (q.trim().length < 2) { res = emptyList(); return@LaunchedEffect }
            delay(350)
            res = SocialApi.search(q.trim()).value ?: emptyList()
        }
        AlertDialog(
            onDismissRequest = { giveFor = null },
            title = { Text("Beri \"${item.name}\"") },
            text = {
                Column {
                    OwnerField(q, { q = it.take(40) }, "Cari nama atau @username")
                    Spacer(Modifier.height(8.dp))
                    res.take(8).forEach { u ->
                        Row(Modifier.fillMaxWidth().padding(vertical = 4.dp), verticalAlignment = Alignment.CenterVertically) {
                            Column(Modifier.weight(1f)) {
                                Text(u.name, color = Color.White, fontSize = 14.sp)
                                u.username?.let { Text("@$it", color = Color.White.copy(alpha = 0.55f), fontSize = 11.sp) }
                            }
                            PillTextButton("Beri", {
                                scope.launch {
                                    toast(context, CosmeticsApi.grant(u.userId, item.id) ?: "Diberikan ke ${u.name}")
                                    loader.reload()
                                }
                            })
                        }
                    }
                }
            },
            confirmButton = { TextButton(onClick = { giveFor = null }) { Text("Selesai") } }
        )
    }

    holdersFor?.let { item ->
        var list by remember(item.id) { mutableStateOf<List<HolderRow>?>(null) }
        LaunchedEffect(item.id) { list = CosmeticsApi.holders(item.id).value ?: emptyList() }
        AlertDialog(
            onDismissRequest = { holdersFor = null },
            title = { Text("Pemegang ${item.name}") },
            text = {
                Column {
                    val l = list
                    if (l == null) Text("Memuat…")
                    else if (l.isEmpty()) Text("Belum ada yang diberi.")
                    else l.take(30).forEach { h ->
                        Row(Modifier.fillMaxWidth().padding(vertical = 4.dp), verticalAlignment = Alignment.CenterVertically) {
                            Text(h.name + (h.username?.let { " (@$it)" } ?: ""), modifier = Modifier.weight(1f), fontSize = 14.sp)
                            PillTextButton("Cabut", {
                                scope.launch {
                                    toast(context, CosmeticsApi.revoke(h.userId, item.id) ?: "Dicabut")
                                    list = CosmeticsApi.holders(item.id).value ?: emptyList()
                                    loader.reload()
                                }
                            }, primary = false)
                        }
                    }
                }
            },
            confirmButton = { TextButton(onClick = { holdersFor = null }) { Text("Tutup") } }
        )
    }
}

// ============================================================================ RINGKASAN SOSIAL

@Composable
internal fun SocialOverviewPage(onBack: () -> Unit) {
    val loader = rememberLoader { CosmeticsApi.socialStats() }
    LaunchedEffect(Unit) { while (true) { delay(10_000); loader.reload() } }
    OwnerScaffold(title = "Ringkasan sosial", subtitle = "Teman, pesan, chat, kosmetik", onBack = onBack) {
        val m = loader.value
        if (m == null) {
            if (loader.error != null) ErrorRow(loader.error ?: "", loader::reload) else LoadingRow()
            return@OwnerScaffold
        }
        val rows = listOf(
            "online" to "Pengguna online sekarang", "friendships" to "Total pertemanan", "pending" to "Permintaan menunggu",
            "dm_today" to "Pesan teman (24 jam)", "chat_today" to "Pesan chat global (24 jam)", "notes" to "Catatan aktif",
            "blocks" to "Total blokir", "cosmetics_given" to "Kosmetik yang di-gift", "equipped" to "Pengguna memakai kosmetik"
        )
        GroupCard {
            rows.forEachIndexed { i, (k, label) ->
                if (i > 0) OwnerDivider()
                Row(Modifier.fillMaxWidth().padding(horizontal = 16.dp, vertical = 14.dp), verticalAlignment = Alignment.CenterVertically) {
                    Text(label, color = Color.White, modifier = Modifier.weight(1f))
                    Text((m[k] ?: 0).toString(), color = Color.White, fontWeight = FontWeight.Bold, fontSize = 18.sp)
                }
            }
        }
    }
}
