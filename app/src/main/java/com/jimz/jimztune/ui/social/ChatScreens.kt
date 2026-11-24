package com.jimz.jimztune.ui.social

import androidx.compose.animation.animateContentSize
import androidx.compose.foundation.background
import androidx.compose.foundation.clickable
import androidx.compose.foundation.combinedClickable
import androidx.compose.foundation.ExperimentalFoundationApi
import androidx.compose.foundation.layout.WindowInsets
import androidx.compose.foundation.layout.ime
import androidx.compose.foundation.layout.navigationBars
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
import androidx.compose.foundation.layout.widthIn
import androidx.compose.foundation.layout.width
import androidx.compose.foundation.lazy.LazyColumn
import androidx.compose.foundation.lazy.items
import androidx.compose.foundation.lazy.rememberLazyListState
import androidx.compose.foundation.shape.CircleShape
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.foundation.text.BasicTextField
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.automirrored.rounded.Send
import androidx.compose.material.icons.automirrored.rounded.Reply
import com.jimz.jimztune.data.social.SocialPage
import androidx.compose.material.icons.rounded.Favorite
import androidx.compose.material.icons.rounded.Flag
import androidx.compose.material.icons.rounded.Person
import androidx.compose.material.icons.rounded.PushPin
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
import androidx.compose.ui.graphics.SolidColor
import androidx.compose.ui.graphics.lerp
import androidx.compose.ui.text.TextStyle
import androidx.compose.ui.text.font.FontStyle
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.text.style.TextAlign
import androidx.compose.ui.text.style.TextOverflow
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import com.jimz.jimztune.AppGraph
import com.jimz.jimztune.data.social.ChatMessage
import com.jimz.jimztune.data.social.ChatSnapshot
import com.jimz.jimztune.data.social.CosmeticsStore
import com.jimz.jimztune.data.social.DmMessage
import com.jimz.jimztune.data.social.SocialApi
import com.jimz.jimztune.data.social.SocialNav
import com.jimz.jimztune.ui.theme.LocalCoverTheme
import kotlinx.coroutines.delay
import kotlinx.coroutines.launch

@Composable
private fun Composer(
    value: String, onValueChange: (String) -> Unit, placeholder: String, enabled: Boolean, maxLen: Int,
    sending: Boolean, onSend: () -> Unit, onHeart: (() -> Unit)? = null
) {
    val accent = LocalCoverTheme.current.accent
    Row(
        modifier = Modifier.fillMaxWidth().padding(horizontal = 12.dp, vertical = 8.dp),
        verticalAlignment = Alignment.CenterVertically
    ) {
        Row(
            Modifier.weight(1f).clip(RoundedCornerShape(26.dp)).background(Color.White.copy(alpha = 0.10f)).padding(horizontal = 18.dp, vertical = 12.dp),
            verticalAlignment = Alignment.CenterVertically
        ) {
            Box(Modifier.weight(1f)) {
                if (value.isEmpty()) Text(placeholder, color = Color.White.copy(alpha = 0.42f), fontSize = 15.sp)
                BasicTextField(
                    value = value, onValueChange = { onValueChange(it.take(maxLen)) }, enabled = enabled, maxLines = 5,
                    textStyle = TextStyle(color = Color.White, fontSize = 15.sp), cursorBrush = SolidColor(Color.White),
                    modifier = Modifier.fillMaxWidth()
                )
            }
        }
        Spacer(Modifier.width(8.dp))
        val ready = enabled && !sending && value.isNotBlank()
        if (ready || onHeart == null) {
            Box(
                modifier = Modifier.size(46.dp).clip(CircleShape).background(if (ready) accent else Color.White.copy(alpha = 0.1f))
                    .clickable(enabled = ready, onClick = onSend),
                contentAlignment = Alignment.Center
            ) { Icon(Icons.AutoMirrored.Rounded.Send, "Kirim", tint = if (ready) Color.Black else Color.White.copy(alpha = 0.4f), modifier = Modifier.size(22.dp)) }
        } else {
            Box(Modifier.size(46.dp).clip(CircleShape).clickable(enabled = enabled && !sending, onClick = onHeart), contentAlignment = Alignment.Center) {
                Icon(Icons.Rounded.Favorite, "Kirim hati", tint = Color(0xFFFF4D6D), modifier = Modifier.size(28.dp))
            }
        }
    }
}

// ============================================================ Chat teman (DM)

@Composable
fun DmScreen(userId: String, name: String, avatar: String?, onBack: () -> Unit) {
    val scope = rememberCoroutineScope()
    var msgs by remember(userId) { mutableStateOf<List<DmMessage>?>(null) }
    var text by remember { mutableStateOf("") }
    var sending by remember { mutableStateOf(false) }
    var error by remember { mutableStateOf<String?>(null) }
    var online by remember { mutableStateOf<Boolean?>(null) }
    var lastSeen by remember { mutableStateOf<String?>(null) }
    val listState = rememberLazyListState()

    LaunchedEffect(userId) {
        CosmeticsStore.request(userId)
        while (true) {
            SocialApi.dmThread(userId).value?.let { fresh ->
                if (fresh.lastOrNull()?.id != msgs?.lastOrNull()?.id || msgs == null) msgs = fresh
                if (fresh.any { !it.mine && !it.read }) { SocialApi.dmMarkRead(userId); SocialNav.refreshBadges() }
            }
            if (msgs == null) msgs = emptyList()
            SocialApi.profile(userId).value?.let { online = it.online; lastSeen = it.lastSeen }
            delay(3_000)
        }
    }
    LaunchedEffect(msgs?.size) {
        val n = msgs?.size ?: 0
        if (n > 0) listState.animateScrollToItem(n)
    }

    SocialScaffold(
        title = name,
        subtitle = if (online == true) "Aktif sekarang" else lastSeen?.let { "Aktif ${agoText(it)}" },
        onBack = onBack,
        actions = { Box(Modifier.clickable { SocialNav.openProfile(userId) }) { AvatarWithDot(avatar, name, 38.dp, online, userId = userId) } }
    ) {
        val eq = CosmeticsStore.map[userId]
        Box(Modifier.weight(1f).fillMaxWidth()) {
            val list = msgs
            if (list == null) Box(Modifier.fillMaxSize(), Alignment.Center) { CircularProgressIndicator(color = Color.White) }
            else if (list.isEmpty()) Column(Modifier.fillMaxSize(), Arrangement.Center, Alignment.CenterHorizontally) {
                AvatarWithDot(avatar, name, 84.dp, online, userId = userId)
                Spacer(Modifier.height(14.dp))
                CosmeticName(name, eq, fontSize = 18.sp)
                Text("Belum ada pesan. Sapa duluan 👋", color = Color.White.copy(alpha = 0.55f), textAlign = TextAlign.Center, fontSize = 13.sp)
            } else LazyColumn(state = listState, contentPadding = PaddingValues(horizontal = 12.dp, vertical = 8.dp)) {
                item {
                    Column(Modifier.fillMaxWidth().padding(vertical = 20.dp), horizontalAlignment = Alignment.CenterHorizontally) {
                        AvatarWithDot(avatar, name, 76.dp, online, userId = userId)
                        Spacer(Modifier.height(10.dp))
                        CosmeticName(name, eq, fontSize = 18.sp, fontWeight = FontWeight.Bold)
                        Text(
                            "Lihat profil", color = LocalCoverTheme.current.accent, fontSize = 13.sp, fontWeight = FontWeight.SemiBold,
                            modifier = Modifier.padding(top = 6.dp).clip(RoundedCornerShape(50)).background(Color.White.copy(alpha = 0.08f)).clickable { SocialNav.openProfile(userId) }.padding(horizontal = 16.dp, vertical = 7.dp)
                        )
                    }
                }
                items(list.size, key = { list[it].id }) { i ->
                    val m = list[i]
                    val prev = list.getOrNull(i - 1)
                    val next = list.getOrNull(i + 1)
                    val firstOfGroup = prev == null || prev.mine != m.mine
                    val lastOfGroup = next == null || next.mine != m.mine
                    val gap = if (firstOfGroup && prev != null) 10.dp else 2.dp
                    Column(Modifier.fillMaxWidth().padding(top = gap)) {
                        Bubble(m, firstOfGroup, lastOfGroup, isLast = next == null)
                    }
                }
            }
        }
        if (error != null) Text(error ?: "", color = Color(0xFFFF8A80), fontSize = 12.sp, modifier = Modifier.padding(horizontal = 20.dp))
        fun send(body: String) {
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
        Composer(text, { text = it }, "Kirim pesan…", true, 500, sending, onSend = { send(text.trim()) }, onHeart = { send("❤️") })
    }
}

@Composable
private fun Bubble(m: DmMessage, first: Boolean, last: Boolean, isLast: Boolean) {
    val accent = LocalCoverTheme.current.accent
    val mine = m.mine
    val big = m.body == "❤️"
    var quote: String? = null
    var body = m.body
    if (body.startsWith("💬 “") && body.contains("”\n")) {
        quote = body.substringAfter("💬 “").substringBefore("”\n")
        body = body.substringAfter("”\n")
    }
    Column(Modifier.fillMaxWidth(), horizontalAlignment = if (mine) Alignment.End else Alignment.Start) {
        if (quote != null) Text(
            "Membalas catatan: $quote", color = Color.White.copy(alpha = 0.55f), fontSize = 11.sp, fontStyle = FontStyle.Italic, maxLines = 1, overflow = TextOverflow.Ellipsis,
            modifier = Modifier.widthIn(max = 260.dp).padding(bottom = 2.dp, start = 6.dp, end = 6.dp)
        )
        if (big) {
            Text("❤️", fontSize = 44.sp)
        } else {
            val r = 22.dp
            val tight = 6.dp
            val shape = RoundedCornerShape(
                topStart = if (!mine && !first) tight else r, topEnd = if (mine && !first) tight else r,
                bottomStart = if (!mine && !last) tight else r, bottomEnd = if (mine && !last) tight else r
            )
            val mineBrush = Brush.linearGradient(listOf(lerp(accent, Color.Black, 0.5f), lerp(accent, Color(0xFF6A3DFF), 0.35f).let { lerp(it, Color.Black, 0.35f) }))
            Text(
                body, color = Color.White, fontSize = 15.sp,
                modifier = Modifier.widthIn(max = 290.dp).clip(shape)
                    .then(if (mine) Modifier.background(mineBrush) else Modifier.background(Color.White.copy(alpha = 0.12f)))
                    .padding(horizontal = 14.dp, vertical = 9.dp)
            )
        }
        if (last || isLast) Text(
            hourText(m.createdAt) + if (mine && m.read && isLast) "  ·  Dilihat" else "",
            color = Color.White.copy(alpha = 0.42f), fontSize = 10.sp, modifier = Modifier.padding(horizontal = 6.dp, vertical = 2.dp)
        )
    }
}

// ============================================================ Chat global

@OptIn(ExperimentalFoundationApi::class)
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
    var actionFor by remember { mutableStateOf<ChatMessage?>(null) }
    var replyTo by remember { mutableStateOf<ChatMessage?>(null) }
    var unseen by remember { mutableStateOf(0) }
    val listState = rememberLazyListState()

    LaunchedEffect(Unit) {
        while (true) {
            val r = SocialApi.chat()
            val v = r.value
            if (v != null) {
                val oldLast = snap?.messages?.lastOrNull()?.id
                val atBottom = !listState.canScrollBackward
                val newCount = if (oldLast == null) 0 else v.messages.count { it.id > oldLast }
                snap = v; failed = null
                if (oldLast == null || v.messages.lastOrNull()?.id != oldLast) {
                    if (oldLast == null || atBottom) { unseen = 0; listState.scrollToItem(0) }
                    else unseen += newCount
                }
            } else if (snap == null) failed = r.error
            delay(3_000)
        }
    }
    LaunchedEffect(listState.canScrollBackward) { if (!listState.canScrollBackward) unseen = 0 }

    Box(Modifier.fillMaxSize()) {
        val imeOpen = WindowInsets.ime.getBottom(androidx.compose.ui.platform.LocalDensity.current) > 0
        SocialScaffold(title = "Chat Global", onBack = onBack) {
            // Peringatan
            if (!imeOpen) Row(
                Modifier.fillMaxWidth().padding(horizontal = 12.dp).clip(RoundedCornerShape(12.dp)).background(Color(0xFF4A3A10)).padding(horizontal = 12.dp, vertical = 9.dp),
                verticalAlignment = Alignment.CenterVertically
            ) {
                Text("⚠", color = Color(0xFFFFC857), fontSize = 14.sp)
                Spacer(Modifier.width(8.dp))
                Text("Hati-hati penipuan. Jangan bagikan data pribadi, dan jaga sopan santun. Pesan diawasi owner.", color = Color(0xFFFFE9A8), fontSize = 11.sp, lineHeight = 14.sp)
            }
            val s = snap
            val pin = s?.pinned
            if (pin != null) Row(
                Modifier.fillMaxWidth().padding(horizontal = 12.dp, vertical = 6.dp).clip(RoundedCornerShape(12.dp)).background(accent.copy(alpha = 0.18f)).padding(10.dp),
                verticalAlignment = Alignment.Top
            ) {
                Icon(Icons.Rounded.PushPin, null, tint = accent, modifier = Modifier.size(16.dp))
                Spacer(Modifier.width(8.dp))
                Text("${pin.name}: ${pin.body}", color = Color.White, fontSize = 12.sp, maxLines = 3, overflow = TextOverflow.Ellipsis)
            }
            if (s != null && !imeOpen) Row(Modifier.padding(horizontal = 16.dp, vertical = 6.dp), verticalAlignment = Alignment.CenterVertically) {
                Box(Modifier.size(7.dp).clip(CircleShape).background(OnlineGreen))
                Text("  ${s.messages.map { it.userId }.distinct().size} pengirim aktif", color = Color.White.copy(alpha = 0.6f), fontSize = 11.sp)
            }
            Box(Modifier.weight(1f).fillMaxWidth()) {
                when {
                    s == null && failed == null -> Box(Modifier.fillMaxSize(), Alignment.Center) { CircularProgressIndicator(color = Color.White) }
                    s == null -> Box(Modifier.fillMaxSize().padding(32.dp), Alignment.Center) { Text(failed ?: "", color = Color.White.copy(alpha = 0.7f), textAlign = TextAlign.Center) }
                    !s.info.enabled -> Box(Modifier.fillMaxSize().padding(32.dp), Alignment.Center) { Text("Chat global sedang dimatikan oleh owner.", color = Color.White.copy(alpha = 0.7f), textAlign = TextAlign.Center) }
                    s.messages.isEmpty() -> Box(Modifier.fillMaxSize(), Alignment.Center) { Text("Belum ada pesan. Jadi yang pertama! 🎉", color = Color.White.copy(alpha = 0.55f)) }
                    else -> LazyColumn(state = listState, reverseLayout = true, contentPadding = PaddingValues(horizontal = 10.dp, vertical = 8.dp), verticalArrangement = Arrangement.spacedBy(8.dp)) {
                        val rev = s.messages.asReversed()
                        items(rev.size, key = { rev[it].id }) { i ->
                            val m = rev[i]
                            ChatCardRow(m, m.userId == me, accent, onLong = { actionFor = m }, onTap = { SocialNav.openProfile(m.userId) })
                        }
                    }
                }
                if (unseen > 0) Row(
                    Modifier.align(Alignment.BottomEnd).padding(12.dp).clip(RoundedCornerShape(50)).background(Color(0xFF2B2F3A))
                        .clickable { scope.launch { listState.animateScrollToItem(0); unseen = 0 } }
                        .padding(horizontal = 12.dp, vertical = 8.dp),
                    verticalAlignment = Alignment.CenterVertically
                ) {
                    Text("▾ Pesan baru", color = Color.White, fontSize = 12.sp, fontWeight = FontWeight.SemiBold)
                    Text(" $unseen ", color = Color.White, fontSize = 10.sp, fontWeight = FontWeight.Bold, modifier = Modifier.padding(start = 6.dp).clip(CircleShape).background(Color(0xFFFF4D6D)).padding(horizontal = 6.dp, vertical = 1.dp))
                }
            }
            if (error != null) Text(error ?: "", color = Color(0xFFFFCC80), fontSize = 12.sp, modifier = Modifier.padding(horizontal = 20.dp))
            val info = s?.info
            val blocked = info == null || !info.enabled || info.muted
            if (info?.muted == true) Text("Kamu sedang dibisukan oleh owner.", color = Color(0xFFFF8A80), fontSize = 12.sp, modifier = Modifier.padding(horizontal = 20.dp))
            else if (info != null && info.slow > 0) Text("Mode lambat: ${info.slow} detik antar pesan", color = Color.White.copy(alpha = 0.5f), fontSize = 11.sp, modifier = Modifier.padding(horizontal = 20.dp))
            val rt = replyTo
            if (rt != null) Row(
                Modifier.fillMaxWidth().padding(horizontal = 12.dp, vertical = 4.dp).clip(RoundedCornerShape(12.dp)).background(Color(0xFF1B2028)).padding(10.dp),
                verticalAlignment = Alignment.CenterVertically
            ) {
                Column(Modifier.weight(1f)) {
                    Text("Membalas ${rt.name}", color = accent, fontSize = 11.sp, fontWeight = FontWeight.Bold)
                    Text(rt.body.substringAfter("\n").take(70), color = Color.White.copy(alpha = 0.7f), fontSize = 12.sp, maxLines = 1, overflow = TextOverflow.Ellipsis)
                }
                Text("✕", color = Color.White.copy(alpha = 0.6f), modifier = Modifier.clickable { replyTo = null }.padding(8.dp))
            }
            ChatComposer(text, { text = it }, !blocked, info?.maxLen ?: 255, sending) {
                val raw = text.trim()
                val body = if (rt != null) "↩ ${rt.name}: ${rt.body.substringAfter("\n").take(60)}\n$raw" else raw
                scope.launch {
                    sending = true
                    val err = SocialApi.chatSend(body)
                    error = err
                    if (err == null) { text = ""; replyTo = null; SocialApi.chat().value?.let { snap = it; listState.scrollToItem(0) } }
                    sending = false
                }
            }
        }
        val am = actionFor
        BottomSheetLite(visible = am != null, onDismiss = { actionFor = null }) {
            if (am != null) {
                Text(am.name, color = Color.White, fontWeight = FontWeight.Bold)
                Text(am.body.substringAfter("\n"), color = Color.White.copy(alpha = 0.6f), fontSize = 13.sp, maxLines = 2, overflow = TextOverflow.Ellipsis, modifier = Modifier.padding(vertical = 6.dp))
                SheetAction(Icons.AutoMirrored.Rounded.Reply, "Balas") { replyTo = am; actionFor = null }
                SheetAction(Icons.Rounded.Person, "Lihat profil") { actionFor = null; SocialNav.openProfile(am.userId) }
                if (am.userId != me) SheetAction(Icons.Rounded.Flag, "Laporkan ke owner", Color(0xFFFF8A80)) {
                    actionFor = null
                    scope.launch { error = SocialApi.chatReport(am.id) ?: "Laporan terkirim ke owner" }
                }
            }
        }
    }
}

@Composable
private fun ChatComposer(value: String, onValueChange: (String) -> Unit, enabled: Boolean, maxLen: Int, sending: Boolean, onSend: () -> Unit) {
    Row(Modifier.fillMaxWidth().padding(horizontal = 10.dp, vertical = 6.dp), verticalAlignment = Alignment.CenterVertically) {
        Column(Modifier.weight(1f).clip(RoundedCornerShape(22.dp)).background(Color(0xFF1B2028)).padding(horizontal = 16.dp, vertical = 9.dp)) {
            Box {
                if (value.isEmpty()) Text("Silakan masukkan pesan…", color = Color.White.copy(alpha = 0.4f), fontSize = 14.sp)
                BasicTextField(
                    value, { onValueChange(it.take(maxLen)) }, enabled = enabled, maxLines = 4,
                    textStyle = TextStyle(color = Color.White, fontSize = 14.sp), cursorBrush = SolidColor(Color.White), modifier = Modifier.fillMaxWidth()
                )
            }
            Text("${value.length}/$maxLen", color = Color.White.copy(alpha = 0.35f), fontSize = 10.sp, modifier = Modifier.align(Alignment.End))
        }
        Spacer(Modifier.width(8.dp))
        val ready = enabled && !sending && value.isNotBlank()
        Box(
            Modifier.size(46.dp).clip(CircleShape).background(if (ready) Color(0xFF2196F3) else Color(0xFF2196F3).copy(alpha = 0.35f)).clickable(enabled = ready, onClick = onSend),
            Alignment.Center
        ) { Icon(Icons.AutoMirrored.Rounded.Send, "Kirim", tint = Color.White, modifier = Modifier.size(22.dp)) }
    }
}

@Composable
private fun SheetAction(icon: androidx.compose.ui.graphics.vector.ImageVector, label: String, tint: Color = Color.White, onClick: () -> Unit) {
    Row(Modifier.fillMaxWidth().clip(RoundedCornerShape(14.dp)).clickable(onClick = onClick).padding(horizontal = 8.dp, vertical = 14.dp), verticalAlignment = Alignment.CenterVertically) {
        Icon(icon, null, tint = tint)
        Spacer(Modifier.width(14.dp))
        Text(label, color = tint, fontSize = 15.sp)
    }
}

/** Kartu pesan gaya Chat Global: foto di kiri bawah, kartu gelap berisi nama, kutipan balasan, isi, dan jam di kanan bawah. */
@OptIn(ExperimentalFoundationApi::class)
@Composable
private fun ChatCardRow(m: ChatMessage, mine: Boolean, accent: Color, onLong: () -> Unit, onTap: () -> Unit) {
    LaunchedEffect(m.userId) { CosmeticsStore.request(m.userId) }
    val eq = CosmeticsStore.map[m.userId]
    var quote: String? = null
    var body = m.body
    if (body.startsWith("↩ ") && body.contains("\n")) { quote = body.substring(2).substringBefore("\n"); body = body.substringAfter("\n") }
    val shape = RoundedCornerShape(14.dp)
    Row(Modifier.fillMaxWidth(), verticalAlignment = Alignment.Bottom) {
        Box(Modifier.width(if (eq?.frame != null) 50.dp else 42.dp).clickable(onClick = onTap), contentAlignment = Alignment.BottomStart) {
            AvatarWithDot(m.avatar, m.name, 36.dp, null, userId = m.userId)
        }
        PlateBox(eq?.plate, Modifier.weight(1f, fill = false).combinedClickable(onClick = onTap, onLongClick = onLong), shape) {
            Column(
                Modifier.then(if (eq?.plate == null) Modifier.clip(shape).background(if (mine) accent.copy(alpha = 0.22f) else Color(0xFF1E2229)) else Modifier)
                    .padding(horizontal = 12.dp, vertical = 8.dp)
            ) {
                Row(verticalAlignment = Alignment.CenterVertically) {
                    CosmeticName(m.name, eq, color = if (m.owner) accent else Color(0xFF9CCB6A), fontSize = 13.sp, fontWeight = FontWeight.Bold, modifier = Modifier.weight(1f, fill = false))
                    if (m.owner) Text(
                        "OWNER", color = Color.Black, fontSize = 8.sp, fontWeight = FontWeight.Black,
                        modifier = Modifier.padding(start = 6.dp).clip(RoundedCornerShape(50)).background(accent).padding(horizontal = 5.dp, vertical = 1.dp)
                    )
                }
                if (quote != null) Text(
                    quote, color = Color.White.copy(alpha = 0.55f), fontSize = 11.sp, maxLines = 2, overflow = TextOverflow.Ellipsis,
                    modifier = Modifier.padding(vertical = 4.dp).clip(RoundedCornerShape(6.dp)).background(Color.White.copy(alpha = 0.06f)).padding(6.dp)
                )
                Text(body, color = Color.White, fontSize = 14.sp, lineHeight = 19.sp, modifier = Modifier.padding(top = 2.dp))
                Text(hourText(m.createdAt), color = Color.White.copy(alpha = 0.4f), fontSize = 10.sp, modifier = Modifier.align(Alignment.End).padding(top = 2.dp))
            }
        }
    }
}
