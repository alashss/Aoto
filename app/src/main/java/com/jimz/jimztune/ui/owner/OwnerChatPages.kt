package com.jimz.jimztune.ui.owner

import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.width
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.rounded.Delete
import androidx.compose.material3.AlertDialog
import androidx.compose.material3.MaterialTheme
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
import com.jimz.jimztune.data.owner.AdminChatMsg
import com.jimz.jimztune.data.owner.ChatRules
import com.jimz.jimztune.data.owner.OwnerChatApi
import com.jimz.jimztune.ui.components.GroupCard
import com.jimz.jimztune.ui.components.PillTextButton
import com.jimz.jimztune.ui.social.agoText
import kotlinx.coroutines.delay
import kotlinx.coroutines.launch

private val MuteLabels = listOf("10 menit", "1 jam", "24 jam", "7 hari", "Selamanya")
private val MuteMinutes = listOf(10, 60, 1440, 10080, 0)

// ============================================================================ MODERASI CHAT GLOBAL

@Composable
internal fun ChatModerationPage(onBack: () -> Unit) {
    val context = LocalContext.current
    val scope = rememberCoroutineScope()
    val loader = rememberLoader { OwnerChatApi.messages(120) }
    var muteTarget by remember { mutableStateOf<AdminChatMsg?>(null) }
    var muteChoice by remember { mutableStateOf(1) }
    var confirmClear by remember { mutableStateOf(false) }
    var onlyReported by remember { mutableStateOf(false) }

    // Daftar menyegarkan diri tiap 5 detik supaya terasa live.
    LaunchedEffect(Unit) { while (true) { delay(5_000); loader.reload() } }

    fun act(block: suspend () -> String?, ok: String) {
        scope.launch {
            toast(context, block() ?: ok)
            loader.reload()
        }
    }

    OwnerScaffold(title = "Moderasi chat", subtitle = "Chat global · hapus, bisukan, sematkan", onBack = onBack) {
        val list = loader.value
        if (list == null) {
            if (loader.error != null) ErrorRow(loader.error ?: "", loader::reload) else LoadingRow()
            return@OwnerScaffold
        }
        ChipRow(listOf("Semua", "Dilaporkan"), if (onlyReported) 1 else 0) { onlyReported = it == 1 }
        val shown = list.filter { !it.deleted && (!onlyReported || it.reports > 0) }
        if (shown.isEmpty()) {
            Text("Tidak ada pesan.", color = Color.White.copy(alpha = 0.55f), modifier = Modifier.padding(24.dp))
        }
        GroupCard {
            shown.forEachIndexed { i, m ->
                if (i > 0) OwnerDivider()
                Column(Modifier.fillMaxWidth().padding(horizontal = 16.dp, vertical = 10.dp)) {
                    Row(verticalAlignment = Alignment.CenterVertically) {
                        Text(m.name, fontWeight = FontWeight.Bold, color = Color.White, fontSize = 14.sp)
                        if (!m.username.isNullOrBlank()) Text("  @" + m.username, color = Color.White.copy(alpha = 0.5f), fontSize = 12.sp)
                        Spacer(Modifier.weight(1f))
                        Text(agoText(m.createdAt), color = Color.White.copy(alpha = 0.45f), fontSize = 11.sp)
                    }
                    Text(m.body, color = Color.White.copy(alpha = 0.92f), fontSize = 14.sp, modifier = Modifier.padding(vertical = 4.dp))
                    Row(horizontalArrangement = Arrangement.spacedBy(6.dp), verticalAlignment = Alignment.CenterVertically) {
                        if (m.pinned) StatusPill("Disematkan", OwnerOk)
                        if (m.muted) StatusPill("Dibisukan", OwnerDanger)
                        if (m.reports > 0) StatusPill("${m.reports} laporan", Color(0xFFFFB74D))
                    }
                    Row(horizontalArrangement = Arrangement.spacedBy(6.dp), modifier = Modifier.padding(top = 8.dp)) {
                        PillTextButton("Hapus", { act({ OwnerChatApi.delete(m.id) }, "Pesan dihapus") }, primary = false)
                        PillTextButton(if (m.pinned) "Lepas" else "Sematkan", { act({ OwnerChatApi.pin(m.id, !m.pinned) }, if (m.pinned) "Sematan dilepas" else "Pesan disematkan") }, primary = false)
                        PillTextButton(if (m.muted) "Buka bisu" else "Bisukan", {
                            if (m.muted) act({ OwnerChatApi.unmute(m.userId) }, "Bisu dibuka") else muteTarget = m
                        }, primary = false)
                    }
                    TextButton(onClick = { act({ OwnerChatApi.deleteUser(m.userId) }, "Semua pesan ${m.name} dihapus") }) {
                        Text("Hapus semua pesan ${m.name}", color = OwnerDanger, fontSize = 12.sp)
                    }
                }
            }
        }
        OwnerSection("Bahaya")
        GroupCard {
            OwnerNavRow(Icons.Rounded.Delete, "Kosongkan chat global", "Menghapus semua pesan sekaligus") { confirmClear = true }
        }
        Spacer(Modifier.height(24.dp))
    }

    val target = muteTarget
    if (target != null) {
        AlertDialog(
            onDismissRequest = { muteTarget = null },
            title = { Text("Bisukan ${target.name}?") },
            text = {
                Column {
                    Text("Dia tidak bisa mengirim di chat global selama waktu ini.", fontSize = 13.sp)
                    Spacer(Modifier.height(10.dp))
                    ChipRow(MuteLabels, muteChoice) { muteChoice = it }
                }
            },
            confirmButton = {
                TextButton(onClick = {
                    muteTarget = null
                    act({ OwnerChatApi.mute(target.userId, MuteMinutes[muteChoice]) }, "${target.name} dibisukan")
                }) { Text("Bisukan") }
            },
            dismissButton = { TextButton(onClick = { muteTarget = null }) { Text("Batal") } }
        )
    }
    if (confirmClear) {
        AlertDialog(
            onDismissRequest = { confirmClear = false },
            title = { Text("Kosongkan chat global?") },
            text = { Text("Semua pesan akan dihapus dan tidak bisa dikembalikan.") },
            confirmButton = {
                TextButton(onClick = { confirmClear = false; act({ OwnerChatApi.clear() }, "Chat dikosongkan") }) { Text("Kosongkan", color = OwnerDanger) }
            },
            dismissButton = { TextButton(onClick = { confirmClear = false }) { Text("Batal") } }
        )
    }
}

// ============================================================================ ATURAN CHAT

@Composable
internal fun ChatRulesPage(onBack: () -> Unit) {
    val context = LocalContext.current
    val scope = rememberCoroutineScope()
    val rulesLoader = rememberLoader { OwnerChatApi.rules() }
    val wordsLoader = rememberLoader { OwnerChatApi.words() }
    val mutesLoader = rememberLoader { OwnerChatApi.mutes() }
    var rules by remember { mutableStateOf<ChatRules?>(null) }
    var slowText by remember { mutableStateOf("") }
    var maxText by remember { mutableStateOf("") }
    var newWord by remember { mutableStateOf("") }

    val loaded = rulesLoader.value
    LaunchedEffect(loaded) {
        if (loaded != null && rules == null) {
            rules = loaded
            slowText = loaded.slowSeconds.toString()
            maxText = loaded.maxLen.toString()
        }
    }

    fun save(n: ChatRules, ok: String = "Tersimpan") {
        rules = n
        scope.launch { toast(context, OwnerChatApi.saveRules(n) ?: ok) }
    }

    OwnerScaffold(title = "Aturan chat", subtitle = "Kata terlarang, mode lambat, bisu", onBack = onBack) {
        val r = rules
        if (r == null) {
            if (rulesLoader.error != null) ErrorRow(rulesLoader.error ?: "", rulesLoader::reload) else LoadingRow()
            return@OwnerScaffold
        }
        OwnerSection("Saklar")
        GroupCard {
            OwnerSwitchRow("Chat global", if (r.globalEnabled) "Menyala" else "Mati: semua orang melihat pesan 'dimatikan'", r.globalEnabled, { save(r.copy(globalEnabled = it)) })
            OwnerDivider()
            OwnerSwitchRow("Chat teman (DM)", if (r.dmEnabled) "Menyala" else "Mati: tidak ada yang bisa kirim pesan pribadi", r.dmEnabled, { save(r.copy(dmEnabled = it)) })
            OwnerDivider()
            OwnerSwitchRow("Blokir tautan", "Pesan berisi link ditolak", r.blockLinks, { save(r.copy(blockLinks = it)) })
        }

        OwnerSection("Batas pesan")
        OwnerField(slowText, { slowText = it.filter(Char::isDigit).take(3) }, "Mode lambat (detik antar pesan, 0 = mati)")
        OwnerField(maxText, { maxText = it.filter(Char::isDigit).take(4) }, "Panjang maksimal pesan (20–1000)")
        PillTextButton("Simpan batas", {
            val slow = (slowText.toIntOrNull() ?: r.slowSeconds).coerceIn(0, 300)
            val max = (maxText.toIntOrNull() ?: r.maxLen).coerceIn(20, 1000)
            slowText = slow.toString(); maxText = max.toString()
            save(r.copy(slowSeconds = slow, maxLen = max))
        }, modifier = Modifier.fillMaxWidth())

        OwnerSection("Saat ada kata terlarang")
        ChipRow(listOf("Sensor (****)", "Tolak pesan"), if (r.badWordMode == "reject") 1 else 0) {
            save(r.copy(badWordMode = if (it == 1) "reject" else "mask"))
        }

        OwnerSection("Kata terlarang")
        OwnerField(newWord, { newWord = it.take(40) }, "Tambah kata", hint = "Huruf kecil otomatis. Cocok walau di tengah kata.")
        PillTextButton("Tambah", {
            val w = newWord
            scope.launch {
                toast(context, OwnerChatApi.addWord(w) ?: "Ditambahkan")
                newWord = ""
                wordsLoader.reload()
            }
        }, modifier = Modifier.fillMaxWidth())
        val words = wordsLoader.value.orEmpty()
        if (words.isEmpty()) Text("Belum ada kata.", color = Color.White.copy(alpha = 0.5f), fontSize = 13.sp)
        GroupCard {
            words.forEachIndexed { i, w ->
                if (i > 0) OwnerDivider()
                Row(Modifier.fillMaxWidth().padding(start = 16.dp, end = 8.dp), verticalAlignment = Alignment.CenterVertically) {
                    Text(w, color = Color.White, modifier = Modifier.weight(1f))
                    TextButton(onClick = { scope.launch { OwnerChatApi.removeWord(w); wordsLoader.reload() } }) { Text("Hapus", color = OwnerDanger) }
                }
            }
        }

        OwnerSection("Sedang dibisukan")
        val mutes = mutesLoader.value.orEmpty()
        if (mutes.isEmpty()) Text("Tidak ada.", color = Color.White.copy(alpha = 0.5f), fontSize = 13.sp)
        GroupCard {
            mutes.forEachIndexed { i, m ->
                if (i > 0) OwnerDivider()
                Row(Modifier.fillMaxWidth().padding(start = 16.dp, end = 8.dp, top = 6.dp, bottom = 6.dp), verticalAlignment = Alignment.CenterVertically) {
                    Column(Modifier.weight(1f)) {
                        Text(m.name, color = Color.White, fontWeight = FontWeight.SemiBold)
                        Text(if (m.until == null) "Selamanya" else "Sampai " + com.jimz.jimztune.util.TimeUtil.format(m.until), color = Color.White.copy(alpha = 0.55f), fontSize = 12.sp)
                    }
                    TextButton(onClick = { scope.launch { OwnerChatApi.unmute(m.userId); mutesLoader.reload() } }) { Text("Buka bisu") }
                }
            }
        }
        Spacer(Modifier.height(24.dp))
    }
}
