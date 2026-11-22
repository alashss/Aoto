package com.jimz.jimztune.ui.social

import androidx.compose.foundation.background
import androidx.compose.foundation.clickable
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.PaddingValues
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.navigationBarsPadding
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.widthIn
import androidx.compose.foundation.layout.width
import androidx.compose.foundation.lazy.LazyColumn
import androidx.compose.foundation.lazy.items
import androidx.compose.foundation.lazy.rememberLazyListState
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.automirrored.rounded.Send
import androidx.compose.material.icons.rounded.PushPin
import androidx.compose.material3.CircularProgressIndicator
import androidx.compose.material3.DropdownMenu
import androidx.compose.material3.DropdownMenuItem
import androidx.compose.material3.Icon
import androidx.compose.material3.OutlinedTextField
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
import androidx.compose.ui.text.style.TextAlign
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import com.jimz.jimztune.AppGraph
import com.jimz.jimztune.data.social.ChatMessage
import com.jimz.jimztune.data.social.ChatSnapshot
import com.jimz.jimztune.data.social.DmMessage
import com.jimz.jimztune.data.social.SocialApi
import com.jimz.jimztune.data.social.SocialNav
import com.jimz.jimztune.ui.auth.jimzFieldColors
import com.jimz.jimztune.ui.components.GlassIconButton
import com.jimz.jimztune.ui.theme.LocalCoverTheme
import kotlinx.coroutines.delay
import kotlinx.coroutines.launch

@Composable
private fun Composer(
    value: String, onValueChange: (String) -> Unit, placeholder: String, enabled: Boolean, maxLen: Int,
    sending: Boolean, onSend: () -> Unit
) {
    val accent = LocalCoverTheme.current.accent
    Row(
        modifier = Modifier.fillMaxWidth().navigationBarsPadding().padding(horizontal = 12.dp, vertical = 8.dp),
        verticalAlignment = Alignment.CenterVertically
    ) {
        OutlinedTextField(
            value = value, onValueChange = { onValueChange(it.take(maxLen)) }, enabled = enabled,
            placeholder = { Text(placeholder, color = Color.White.copy(alpha = 0.4f)) },
            maxLines = 4, shape = RoundedCornerShape(24.dp), colors = jimzFieldColors(accent),
            modifier = Modifier.weight(1f)
        )
        Spacer(Modifier.width(8.dp))
        val ready = enabled && !sending && value.isNotBlank()
        Box(
            modifier = Modifier.clip(RoundedCornerShape(50)).background(if (ready) accent else Color.White.copy(alpha = 0.1f))
                .clickable(enabled = ready, onClick = onSend).padding(14.dp)
        ) {
            Icon(Icons.AutoMirrored.Rounded.Send, "Kirim", tint = if (ready) Color.Black else Color.White.copy(alpha = 0.4f))
        }
    }
}

// ============================================================ Chat teman (DM)

@Composable
fun DmScreen(userId: String, name: String, avatar: String?, onBack: () -> Unit) {
    val scope = rememberCoroutineScope()
    val accent = LocalCoverTheme.current.accent
    var msgs by remember(userId) { mutableStateOf<List<DmMessage>?>(null) }
    var text by remember { mutableStateOf("") }
    var sending by remember { mutableStateOf(false) }
    var error by remember { mutableStateOf<String?>(null) }
    var online by remember { mutableStateOf<Boolean?>(null) }
    val listState = rememberLazyListState()

    LaunchedEffect(userId) {
        while (true) {
            SocialApi.dmThread(userId).value?.let { fresh ->
                if (fresh.lastOrNull()?.id != msgs?.lastOrNull()?.id || msgs == null) msgs = fresh
                if (fresh.any { !it.mine && !it.read }) { SocialApi.dmMarkRead(userId); SocialNav.refreshBadges() }
            }
            if (msgs == null) msgs = emptyList()
            SocialApi.profile(userId).value?.let { online = it.online }
            delay(3_000)
        }
    }
    LaunchedEffect(msgs?.size) {
        val n = msgs?.size ?: 0
        if (n > 0) listState.scrollToItem(n - 1)
    }

    SocialScaffold(
        title = name, onBack = onBack,
        subtitle = if (online == true) "Online" else null,
        actions = { AvatarWithDot(avatar, name, 38.dp, online) }
    ) {
        Box(Modifier.weight(1f).fillMaxWidth()) {
            val list = msgs
            if (list == null) Box(Modifier.fillMaxSize(), Alignment.Center) { CircularProgressIndicator(color = Color.White) }
            else if (list.isEmpty()) Box(Modifier.fillMaxSize(), Alignment.Center) {
                Text("Belum ada pesan.\nSapa $name 👋", color = Color.White.copy(alpha = 0.55f), textAlign = TextAlign.Center)
            } else LazyColumn(state = listState, contentPadding = PaddingValues(horizontal = 12.dp, vertical = 8.dp), verticalArrangement = Arrangement.spacedBy(4.dp)) {
                items(list, key = { it.id }) { m -> Bubble(m.mine, m.body, hourText(m.createdAt), accent, footer = if (m.mine && m.read) "Dibaca" else null) }
            }
        }
        if (error != null) Text(error ?: "", color = Color(0xFFFF8A80), fontSize = 12.sp, modifier = Modifier.padding(horizontal = 20.dp))
        Composer(text, { text = it }, "Tulis pesan…", true, 500, sending) {
            val body = text.trim()
            scope.launch {
                sending = true
                val err = SocialApi.dmSend(userId, body)
                error = err
                if (err == null) {
                    text = ""
                    SocialApi.dmThread(userId).value?.let { msgs = it }
                }
                sending = false
            }
        }
    }
}

@Composable
private fun Bubble(mine: Boolean, body: String, time: String, accent: Color, footer: String? = null) {
    Column(Modifier.fillMaxWidth(), horizontalAlignment = if (mine) Alignment.End else Alignment.Start) {
        Column(
            modifier = Modifier.widthIn(max = 290.dp)
                .clip(RoundedCornerShape(topStart = 20.dp, topEnd = 20.dp, bottomStart = if (mine) 20.dp else 5.dp, bottomEnd = if (mine) 5.dp else 20.dp))
                .background(if (mine) accent else Color.White.copy(alpha = 0.10f))
                .padding(horizontal = 14.dp, vertical = 8.dp)
        ) {
            Text(body, color = if (mine) Color.Black else Color.White, fontSize = 15.sp)
            Text(time, color = (if (mine) Color.Black else Color.White).copy(alpha = 0.5f), fontSize = 10.sp, modifier = Modifier.align(Alignment.End))
        }
        if (footer != null) Text(footer, color = Color.White.copy(alpha = 0.45f), fontSize = 10.sp, modifier = Modifier.padding(end = 6.dp, top = 1.dp))
    }
}

// ============================================================ Chat global

@Composable
fun GlobalChatScreen(onBack: () -> Unit) {
    val scope = rememberCoroutineScope()
    val accent = LocalCoverTheme.current.accent
    val me = AppGraph.auth.session?.userId
    var snap by remember { mutableStateOf<ChatSnapshot?>(null) }
    var failed by remember { mutableStateOf<String?>(null) }
    var text by remember { mutableStateOf("") }
    var sending by remember { mutableStateOf(false) }
    var error by remember { mutableStateOf<String?>(null) }
    var menuFor by remember { mutableStateOf<Long?>(null) }
    val listState = rememberLazyListState()

    LaunchedEffect(Unit) {
        while (true) {
            val r = SocialApi.chat()
            if (r.value != null) {
                val old = snap?.messages?.lastOrNull()?.id
                snap = r.value; failed = null
                if (old != r.value.messages.lastOrNull()?.id) listState.scrollToItem((r.value.messages.size - 1).coerceAtLeast(0))
            } else if (snap == null) failed = r.error
            delay(3_000)
        }
    }

    SocialScaffold(title = "Chat global", subtitle = "Semua pengguna AOTO", onBack = onBack) {
        val s = snap
        val pin = s?.pinned
        if (pin != null) {
            Row(
                modifier = Modifier.fillMaxWidth().padding(horizontal = 12.dp).clip(RoundedCornerShape(14.dp)).background(accent.copy(alpha = 0.18f)).padding(10.dp),
                verticalAlignment = Alignment.CenterVertically
            ) {
                Icon(Icons.Rounded.PushPin, null, tint = accent)
                Spacer(Modifier.width(8.dp))
                Text("${pin.name}: ${pin.body}", color = Color.White, fontSize = 13.sp, maxLines = 2)
            }
        }
        Box(Modifier.weight(1f).fillMaxWidth()) {
            when {
                s == null && failed == null -> Box(Modifier.fillMaxSize(), Alignment.Center) { CircularProgressIndicator(color = Color.White) }
                s == null -> Box(Modifier.fillMaxSize().padding(32.dp), Alignment.Center) { Text(failed ?: "", color = Color.White.copy(alpha = 0.7f), textAlign = TextAlign.Center) }
                !s.info.enabled -> Box(Modifier.fillMaxSize().padding(32.dp), Alignment.Center) { Text("Chat global sedang dimatikan oleh owner.", color = Color.White.copy(alpha = 0.7f), textAlign = TextAlign.Center) }
                s.messages.isEmpty() -> Box(Modifier.fillMaxSize(), Alignment.Center) { Text("Belum ada pesan. Jadi yang pertama!", color = Color.White.copy(alpha = 0.55f)) }
                else -> LazyColumn(state = listState, contentPadding = PaddingValues(horizontal = 12.dp, vertical = 8.dp), verticalArrangement = Arrangement.spacedBy(10.dp)) {
                    items(s.messages, key = { it.id }) { m -> ChatRow(m, m.userId == me, accent, menuFor == m.id, { menuFor = if (it) m.id else null }) {
                        scope.launch { error = SocialApi.chatReport(m.id) ?: "Laporan terkirim ke owner" }
                    } }
                }
            }
        }
        if (error != null) Text(error ?: "", color = Color(0xFFFFCC80), fontSize = 12.sp, modifier = Modifier.padding(horizontal = 20.dp))
        val info = s?.info
        val blocked = info == null || !info.enabled || info.muted
        if (info?.muted == true) Text("Kamu sedang dibisukan oleh owner.", color = Color(0xFFFF8A80), fontSize = 12.sp, modifier = Modifier.padding(horizontal = 20.dp))
        else if (info != null && info.slow > 0) Text("Mode lambat: ${info.slow} detik antar pesan", color = Color.White.copy(alpha = 0.5f), fontSize = 11.sp, modifier = Modifier.padding(horizontal = 20.dp))
        Composer(text, { text = it }, "Tulis ke semua orang…", !blocked, info?.maxLen ?: 300, sending) {
            val body = text.trim()
            scope.launch {
                sending = true
                val err = SocialApi.chatSend(body)
                error = err
                if (err == null) { text = ""; SocialApi.chat().value?.let { snap = it; listState.scrollToItem((it.messages.size - 1).coerceAtLeast(0)) } }
                sending = false
            }
        }
    }
}

@Composable
private fun ChatRow(m: ChatMessage, mine: Boolean, accent: Color, menuOpen: Boolean, setMenu: (Boolean) -> Unit, onReport: () -> Unit) {
    Row(
        modifier = Modifier.fillMaxWidth().clickable { if (!mine) setMenu(true) },
        verticalAlignment = Alignment.Top
    ) {
        Box(Modifier.clickable { SocialNav.openProfile(m.userId) }) {
            com.jimz.jimztune.ui.ranking.RankAvatar(m.avatar, m.name, 36.dp)
        }
        Spacer(Modifier.width(10.dp))
        Column(Modifier.weight(1f)) {
            Row(verticalAlignment = Alignment.CenterVertically) {
                Text(
                    m.name + if (mine) " (kamu)" else "", color = if (m.owner) accent else Color.White.copy(alpha = 0.9f),
                    fontWeight = FontWeight.Bold, fontSize = 13.sp,
                    modifier = Modifier.clickable { SocialNav.openProfile(m.userId) }
                )
                if (m.owner) Text("  OWNER", color = accent, fontSize = 9.sp, fontWeight = FontWeight.Black)
                Spacer(Modifier.width(8.dp))
                Text(hourText(m.createdAt), color = Color.White.copy(alpha = 0.4f), fontSize = 10.sp)
            }
            Text(m.body, color = Color.White, fontSize = 14.sp, lineHeight = 19.sp)
        }
        Box {
            DropdownMenu(expanded = menuOpen, onDismissRequest = { setMenu(false) }) {
                DropdownMenuItem(text = { Text("Lihat profil") }, onClick = { setMenu(false); SocialNav.openProfile(m.userId) })
                DropdownMenuItem(text = { Text("Laporkan") }, onClick = { setMenu(false); onReport() })
            }
        }
    }
}
