package com.jimz.jimztune.ui.social

import androidx.compose.animation.core.Spring
import androidx.compose.animation.core.animateFloatAsState
import androidx.compose.animation.core.spring
import androidx.compose.foundation.background
import androidx.compose.foundation.border
import androidx.compose.foundation.clickable
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.BoxWithConstraints
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.PaddingValues
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.offset
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.layout.width
import androidx.compose.foundation.lazy.LazyColumn
import androidx.compose.foundation.lazy.LazyRow
import androidx.compose.foundation.lazy.items
import androidx.compose.foundation.shape.CircleShape
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.foundation.text.BasicTextField
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.rounded.Check
import androidx.compose.material.icons.rounded.Favorite
import androidx.compose.material.icons.rounded.Groups
import androidx.compose.material.icons.rounded.MusicNote
import androidx.compose.material.icons.rounded.PlayArrow
import androidx.compose.material.icons.rounded.PersonAdd
import androidx.compose.material.icons.rounded.Search
import androidx.compose.material3.CircularProgressIndicator
import androidx.compose.material3.Icon
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.ui.graphics.graphicsLayer
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
import androidx.compose.ui.text.TextStyle
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.text.style.TextAlign
import androidx.compose.ui.text.style.TextOverflow
import androidx.compose.ui.unit.IntOffset
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import com.jimz.jimztune.AppGraph
import com.jimz.jimztune.data.social.CosmeticsStore
import com.jimz.jimztune.data.social.FriendRequests
import com.jimz.jimztune.data.social.FriendRow
import com.jimz.jimztune.data.social.SearchRow
import com.jimz.jimztune.data.social.SocialApi
import com.jimz.jimztune.data.social.SocialNav
import com.jimz.jimztune.data.social.SocialPage
import com.jimz.jimztune.ui.components.GlassIconButton
import com.jimz.jimztune.ui.theme.LocalCoverTheme
import kotlinx.coroutines.delay
import kotlinx.coroutines.launch
import kotlin.math.roundToInt

@Composable
private fun EmptyHint(emoji: String, text: String) {
    Column(Modifier.fillMaxWidth().padding(40.dp), horizontalAlignment = Alignment.CenterHorizontally) {
        Text(emoji, fontSize = 40.sp)
        Spacer(Modifier.height(8.dp))
        Text(text, color = Color.White.copy(alpha = 0.55f), textAlign = TextAlign.Center, fontSize = 14.sp)
    }
}

@Composable
private fun SearchPill(value: String, onChange: (String) -> Unit, hint: String, modifier: Modifier = Modifier) {
    Row(
        modifier = modifier.fillMaxWidth().padding(horizontal = 16.dp, vertical = 6.dp).clip(RoundedCornerShape(50))
            .background(Color.White.copy(alpha = 0.09f)).padding(horizontal = 16.dp, vertical = 12.dp),
        verticalAlignment = Alignment.CenterVertically
    ) {
        Icon(Icons.Rounded.Search, null, tint = Color.White.copy(alpha = 0.6f), modifier = Modifier.size(20.dp))
        Spacer(Modifier.width(10.dp))
        Box(Modifier.weight(1f)) {
            if (value.isEmpty()) Text(hint, color = Color.White.copy(alpha = 0.4f), fontSize = 15.sp)
            BasicTextField(
                value = value, onValueChange = { onChange(it.take(40)) }, singleLine = true,
                textStyle = TextStyle(color = Color.White, fontSize = 15.sp), cursorBrush = SolidColor(Color.White),
                modifier = Modifier.fillMaxWidth()
            )
        }
    }
}

/** Tab dengan garis penanda yang meluncur. */
@Composable
private fun SlideTabs(labels: List<String>, selected: Int, accent: Color, onSelect: (Int) -> Unit) {
    BoxWithConstraints(Modifier.fillMaxWidth().padding(horizontal = 8.dp)) {
        val w = maxWidth / labels.size
        val x by animateFloatAsState(selected * w.value, spring(dampingRatio = 0.75f, stiffness = Spring.StiffnessMediumLow), label = "tabX")
        Row {
            labels.forEachIndexed { i, l ->
                Box(Modifier.weight(1f).clickable { onSelect(i) }.padding(vertical = 12.dp), Alignment.Center) {
                    Text(l, color = if (selected == i) Color.White else Color.White.copy(alpha = 0.5f), fontWeight = if (selected == i) FontWeight.Bold else FontWeight.Medium, fontSize = 14.sp)
                }
            }
        }
        Box(Modifier.align(Alignment.BottomStart).offset(x = x.dp).width(w).padding(horizontal = 18.dp).height(3.dp).clip(RoundedCornerShape(50)).background(accent))
    }
}

// ============================================================ Teman

@Composable
fun FriendsScreen(onBack: () -> Unit) {
    val scope = rememberCoroutineScope()
    val accent = LocalCoverTheme.current.accent
    var tab by remember { mutableStateOf(0) }
    var friends by remember { mutableStateOf<List<FriendRow>?>(null) }
    var reqs by remember { mutableStateOf<FriendRequests?>(null) }
    var reload by remember { mutableStateOf(0) }
    var query by remember { mutableStateOf("") }
    var results by remember { mutableStateOf<List<SearchRow>?>(null) }
    var msg by remember { mutableStateOf<String?>(null) }

    LaunchedEffect(reload) {
        friends = SocialApi.friends().value ?: friends ?: emptyList()
        reqs = SocialApi.requests().value ?: reqs
    }
    LaunchedEffect(query) {
        if (query.trim().length < 2) { results = null; return@LaunchedEffect }
        delay(350)
        results = SocialApi.search(query.trim()).value ?: emptyList()
    }
    LaunchedEffect(friends) { friends?.let { l -> l.forEach { CosmeticsStore.request(it.userId) } } }

    val incoming = reqs?.incoming.orEmpty()
    SocialScaffold(title = "Teman", subtitle = friends?.let { "${it.size} teman" }, onBack = onBack) {
        SlideTabs(listOf("Teman", if (incoming.isNotEmpty()) "Permintaan (${incoming.size})" else "Permintaan", "Cari"), tab, accent) { tab = it }
        if (msg != null) Text(msg ?: "", color = accent, fontSize = 13.sp, modifier = Modifier.padding(horizontal = 20.dp, vertical = 6.dp))
        when (tab) {
            0 -> {
                val list = friends
                when {
                    list == null -> Box(Modifier.fillMaxSize(), Alignment.Center) { CircularProgressIndicator(color = Color.White) }
                    list.isEmpty() -> EmptyHint("🫶", "Belum ada teman.\nCari pengguna di tab Cari atau dari papan peringkat.")
                    else -> {
                        val on = list.filter { it.online == true }.sortedBy { it.name.lowercase() }
                        val off = list.filter { it.online != true }.sortedBy { it.name.lowercase() }
                        LazyColumn(contentPadding = PaddingValues(top = 6.dp, bottom = 24.dp)) {
                            if (on.isNotEmpty()) item { SectionLabel("Online · ${on.size}") }
                            items(on, key = { "on" + it.userId }) { f ->
                                PersonLine(
                                    f.name, f.username, f.avatar, f.online,
                                    sub = f.playing?.let { "🎵 ${it.title} · ${it.artist}" } ?: "Online",
                                    userId = f.userId, onClick = { SocialNav.open(SocialPage.Profile(f.userId)) }
                                ) { ActionPill("Pesan", true) { SocialNav.open(SocialPage.Dm(f.userId, f.name, f.avatar)) } }
                            }
                            if (off.isNotEmpty()) item { SectionLabel("Offline · ${off.size}") }
                            items(off, key = { "off" + it.userId }) { f ->
                                PersonLine(
                                    f.name, f.username, f.avatar, f.online,
                                    sub = f.playing?.let { "🎵 ${it.title} · ${it.artist}" } ?: if (f.online == true) "Online" else f.username?.let { "@$it" },
                                    userId = f.userId, onClick = { SocialNav.open(SocialPage.Profile(f.userId)) }
                                ) {
                                    ActionPill("Pesan", true) { SocialNav.open(SocialPage.Dm(f.userId, f.name, f.avatar)) }
                                }
                            }
                        }
                    }
                }
            }
            1 -> {
                val r = reqs
                if (r == null) Box(Modifier.fillMaxSize(), Alignment.Center) { CircularProgressIndicator(color = Color.White) }
                else if (r.incoming.isEmpty() && r.outgoing.isEmpty()) EmptyHint("📭", "Tidak ada permintaan teman.")
                else LazyColumn(contentPadding = PaddingValues(bottom = 24.dp)) {
                    if (r.incoming.isNotEmpty()) item { SectionLabel("Permintaan masuk") }
                    items(r.incoming, key = { "i" + it.requestId }) { p ->
                        PersonLine(p.name, p.username, p.avatar, null, null, userId = p.userId, onClick = { SocialNav.open(SocialPage.Profile(p.userId)) }) {
                            Row(horizontalArrangement = Arrangement.spacedBy(6.dp)) {
                                ActionPill("Konfirmasi", true) { scope.launch { msg = SocialApi.respond(p.requestId, true); reload++ } }
                                ActionPill("Hapus", false) { scope.launch { msg = SocialApi.respond(p.requestId, false); reload++ } }
                            }
                        }
                    }
                    if (r.outgoing.isNotEmpty()) item { SectionLabel("Terkirim") }
                    items(r.outgoing, key = { "o" + it.requestId }) { p ->
                        PersonLine(p.name, p.username, p.avatar, null, "Menunggu jawaban", userId = p.userId, onClick = { SocialNav.open(SocialPage.Profile(p.userId)) }) {
                            ActionPill("Batalkan", false) { scope.launch { msg = SocialApi.removeFriend(p.userId); reload++ } }
                        }
                    }
                }
            }
            else -> {
                SearchPill(query, { query = it }, "Cari nama atau @username")
                val res = results
                if (res == null) EmptyHint("🔎", "Ketik minimal 2 huruf.")
                else if (res.isEmpty()) EmptyHint("🤷", "Tidak ada pengguna yang cocok.")
                else LazyColumn(contentPadding = PaddingValues(bottom = 24.dp)) {
                    items(res, key = { it.userId }) { u ->
                        PersonLine(
                            u.name, u.username, u.avatar, null,
                            when (u.relation) { "friend" -> "Teman"; "pending_out" -> "Permintaan terkirim"; "pending_in" -> "Mengirim permintaan"; else -> u.username?.let { "@$it" } },
                            userId = u.userId, onClick = { SocialNav.open(SocialPage.Profile(u.userId)) }
                        ) {
                            if (u.relation == "none") ActionPill("Tambah", true) {
                                scope.launch {
                                    val r = SocialApi.requestFriend(u.userId)
                                    msg = r.error ?: if (r.value == "accepted") "Kalian sekarang berteman" else "Permintaan terkirim"
                                    reload++
                                    results = SocialApi.search(query.trim()).value
                                }
                            }
                        }
                    }
                }
            }
        }
    }
}

@Composable
internal fun ActionPill(label: String, primary: Boolean, onClick: () -> Unit) {
    val accent = LocalCoverTheme.current.accent
    Text(
        label, fontSize = 13.sp, fontWeight = FontWeight.Bold,
        color = if (primary) Color.Black else Color.White,
        modifier = Modifier.clip(RoundedCornerShape(50))
            .background(if (primary) accent else Color.White.copy(alpha = 0.12f))
            .clickable(onClick = onClick).padding(horizontal = 16.dp, vertical = 8.dp)
    )
}

@Composable
private fun SectionLabel(text: String) {
    Text(text, color = Color.White.copy(alpha = 0.6f), fontSize = 12.sp, fontWeight = FontWeight.Bold, modifier = Modifier.padding(start = 20.dp, top = 14.dp, bottom = 4.dp))
}

@Composable
internal fun PersonLine(
    name: String, username: String?, avatar: String?, online: Boolean?, sub: String?,
    onClick: () -> Unit, userId: String? = null, trailing: @Composable () -> Unit = {}
) {
    val eq = userId?.let { CosmeticsStore.map[it] }
    PlateBox(eq?.plate, Modifier.fillMaxWidth().padding(horizontal = if (eq?.plate != null) 10.dp else 0.dp, vertical = if (eq?.plate != null) 3.dp else 0.dp)) {
        Row(
            modifier = Modifier.fillMaxWidth().clickable(onClick = onClick).padding(horizontal = if (eq?.plate != null) 10.dp else 16.dp, vertical = 8.dp),
            verticalAlignment = Alignment.CenterVertically
        ) {
            AvatarWithDot(avatar, name, 50.dp, online, userId = userId)
            Spacer(Modifier.width(if (eq?.frame != null) 18.dp else 12.dp))
            Column(Modifier.weight(1f)) {
                CosmeticName(name, eq, fontSize = 15.sp)
                val line = sub ?: username?.let { "@$it" }
                if (line != null) Text(line, color = Color.White.copy(alpha = 0.62f), fontSize = 12.sp, maxLines = 1, overflow = TextOverflow.Ellipsis)
            }
            Spacer(Modifier.width(8.dp))
            trailing()
        }
    }
}

// ============================================================ Kotak masuk (gaya Instagram)

@Composable
fun InboxScreen(onBack: () -> Unit) {
    val accent = LocalCoverTheme.current.accent
    val scope = rememberCoroutineScope()
    var friends by remember { mutableStateOf<List<FriendRow>?>(null) }
    var query by remember { mutableStateOf("") }
    var editNote by remember { mutableStateOf(false) }
    var viewNote by remember { mutableStateOf<FriendRow?>(null) }
    var myNote by remember { mutableStateOf("") }
    var myColor by remember { mutableStateOf<String?>(null) }
    var mySong by remember { mutableStateOf<String?>(null) }
    var myLyric by remember { mutableStateOf<String?>(null) }
    val player: com.jimz.jimztune.player.PlayerViewModel = androidx.lifecycle.viewmodel.compose.viewModel()
    val me = AppGraph.auth.session?.userId
    val myProfile = AppGraph.profile.profile

    LaunchedEffect(Unit) {
        while (true) {
            friends = SocialApi.friends().value ?: friends ?: emptyList()
            if (me != null) SocialApi.profile(me).value?.let { myNote = it.note.orEmpty() }
            SocialNav.refreshBadges()
            delay(5_000)
        }
    }
    LaunchedEffect(friends) { friends?.forEach { CosmeticsStore.request(it.userId) } }

    Box(Modifier.fillMaxSize()) {
        SocialScaffold(
            title = "Pesan", onBack = onBack,
            actions = {
                GlassIconButton(Icons.Rounded.Groups, "Chat global", onClick = { SocialNav.open(SocialPage.GlobalChat) })
                Spacer(Modifier.width(8.dp))
                Box {
                    GlassIconButton(Icons.Rounded.PersonAdd, "Teman", onClick = { SocialNav.open(SocialPage.Friends) })
                    if (SocialNav.badges.requests > 0) Box(Modifier.align(Alignment.TopEnd).size(10.dp).clip(CircleShape).background(Color(0xFFFF4D6D)))
                }
            }
        ) {
            val list = friends
            if (list == null) {
                Box(Modifier.fillMaxSize(), Alignment.Center) { CircularProgressIndicator(color = Color.White) }
                return@SocialScaffold
            }
            SearchPill(query, { query = it }, "Cari teman")
            LazyRow(
                contentPadding = PaddingValues(horizontal = 14.dp, vertical = 6.dp),
                horizontalArrangement = Arrangement.spacedBy(12.dp)
            ) {
                item {
                    NoteItem(
                        myProfile?.displayName ?: "Kamu", myProfile?.avatarUrl, me, myNote.ifBlank { null }, myColor, mySong, myLyric, null,
                        "Catatan Anda", accent
                    ) { editNote = true }
                }
                items(list.filter { !it.note.isNullOrBlank() || !it.noteSong.isNullOrBlank() }, key = { "n" + it.userId }) { f ->
                    NoteItem(f.name, f.avatar, f.userId, f.note, f.noteColor, f.noteSong, f.noteLyric, f.online, f.name.substringBefore(' '), accent) { viewNote = f }
                }
            }
            Row(Modifier.fillMaxWidth().padding(horizontal = 20.dp, vertical = 8.dp), verticalAlignment = Alignment.CenterVertically) {
                Text("Pesan", color = Color.White, fontWeight = FontWeight.Bold, fontSize = 16.sp, modifier = Modifier.weight(1f))
                if (SocialNav.badges.requests > 0) Text(
                    "Permintaan (${SocialNav.badges.requests})", color = accent, fontSize = 13.sp, fontWeight = FontWeight.SemiBold,
                    modifier = Modifier.clickable { SocialNav.open(SocialPage.Friends) }
                )
            }
            val shown = list.filter { query.isBlank() || it.name.contains(query, true) || (it.username?.contains(query, true) == true) }
            if (shown.isEmpty()) {
                EmptyHint("💬", if (list.isEmpty()) "Belum ada percakapan.\nTambahkan teman dulu, lalu mulai ngobrol." else "Tidak ada yang cocok.")
            } else {
                val sorted = shown.sortedWith(compareByDescending<FriendRow> { it.lastAt ?: "" }.thenBy { it.name })
                LazyColumn(contentPadding = PaddingValues(bottom = 24.dp)) {
                    items(sorted, key = { it.userId }) { f ->
                        val preview = when {
                            f.lastBody == null -> f.playing?.let { "🎵 ${it.title}" } ?: "Mulai percakapan"
                            f.lastMine -> "Kamu: " + f.lastBody
                            else -> f.lastBody
                        }
                        val eq = CosmeticsStore.map[f.userId]
                        PlateBox(eq?.plate, Modifier.fillMaxWidth().padding(horizontal = if (eq?.plate != null) 10.dp else 0.dp, vertical = if (eq?.plate != null) 3.dp else 0.dp)) {
                            Row(
                                modifier = Modifier.fillMaxWidth().clickable { SocialNav.open(SocialPage.Dm(f.userId, f.name, f.avatar)) }
                                    .padding(horizontal = if (eq?.plate != null) 10.dp else 16.dp, vertical = 9.dp),
                                verticalAlignment = Alignment.CenterVertically
                            ) {
                                AvatarWithDot(f.avatar, f.name, 54.dp, f.online, userId = f.userId)
                                Spacer(Modifier.width(if (eq?.frame != null) 20.dp else 14.dp))
                                Column(Modifier.weight(1f)) {
                                    CosmeticName(f.name, eq, fontSize = 15.sp, fontWeight = if (f.unread > 0) FontWeight.Bold else FontWeight.SemiBold)
                                    Text(
                                        preview + if (f.lastAt != null) "  ·  " + agoText(f.lastAt) else "", maxLines = 1, overflow = TextOverflow.Ellipsis, fontSize = 13.sp,
                                        color = if (f.unread > 0) Color.White else Color.White.copy(alpha = 0.6f),
                                        fontWeight = if (f.unread > 0) FontWeight.SemiBold else FontWeight.Normal
                                    )
                                }
                                if (f.unread > 0) Box(Modifier.padding(start = 8.dp).size(11.dp).clip(CircleShape).background(accent))
                            }
                        }
                    }
                }
            }
        }

        // Lihat catatan teman + balas cepat
        val vn = viewNote
        BottomSheetLite(visible = vn != null, onDismiss = { viewNote = null }) {
            if (vn != null) NoteReplySheet(vn, accent, onPlay = { id ->
                player.play(listOf(com.jimz.jimztune.data.model.SearchItem(id, com.jimz.jimztune.data.model.SearchType.SONG, (vn.noteSong ?: "").substringBefore(" · "), (vn.noteSong ?: "").substringAfter(" · ", ""), "https://i.ytimg.com/vi/$id/hqdefault.jpg")), 0)
            }) { reply ->
                scope.launch {
                    val body = "💬 “${vn.note.orEmpty().ifBlank { vn.noteLyric ?: vn.noteSong.orEmpty() }.take(60)}”\n$reply"
                    SocialApi.dmSend(vn.userId, body.take(500))
                    viewNote = null
                    SocialNav.open(SocialPage.Dm(vn.userId, vn.name, vn.avatar))
                }
            }
        }
        androidx.compose.animation.AnimatedVisibility(
            visible = editNote,
            enter = androidx.compose.animation.slideInVertically(androidx.compose.animation.core.tween(300)) { it } + androidx.compose.animation.fadeIn(),
            exit = androidx.compose.animation.slideOutVertically(androidx.compose.animation.core.tween(240)) { it } + androidx.compose.animation.fadeOut()
        ) {
            NoteComposer(
                name = myProfile?.displayName ?: "Kamu", avatar = myProfile?.avatarUrl, userId = me, initialText = myNote,
                onClose = { editNote = false },
                onShare = { t, c, sg, sid, ly ->
                    editNote = false
                    scope.launch {
                        SocialNav.message = SocialApi.setNoteV3(t, c, sg, sid, ly) ?: "Catatan dibagikan"
                        myNote = t; myColor = c; mySong = sg; myLyric = ly
                    }
                }
            )
        }
    }
}

private fun noteColor(hex: String?): Color? = hex?.let { parseHex(it, Color.Unspecified).takeIf { c -> c != Color.Unspecified } }

/** Catatan gaya Instagram: gelembung di atas foto, dengan dua titik "ekor" di kiri bawah. */
@Composable
private fun NoteItem(name: String, avatar: String?, userId: String?, note: String?, color: String?, song: String?, lyric: String?, online: Boolean?, label: String, accent: Color, onClick: () -> Unit) {
    val bg = noteColor(color)
    val text = buildString {
        if (!song.isNullOrBlank()) append("♪ ").append(song.substringBefore(" · ")).append(if (note.isNullOrBlank() && lyric.isNullOrBlank()) "" else "\n")
        if (!note.isNullOrBlank()) append(note) else if (!lyric.isNullOrBlank()) append(lyric)
    }.ifBlank { null }
    Column(modifier = Modifier.width(88.dp).clickable(onClick = onClick), horizontalAlignment = Alignment.CenterHorizontally) {
        Box(Modifier.height(58.dp).fillMaxWidth(), contentAlignment = Alignment.BottomCenter) {
            Text(
                text ?: "Siap untuk…", fontSize = 11.sp, maxLines = 3, overflow = TextOverflow.Ellipsis, textAlign = TextAlign.Center, lineHeight = 13.sp,
                color = if (text == null) Color.White.copy(alpha = 0.55f) else if (bg != null) Color.White else Color.White,
                modifier = Modifier.clip(RoundedCornerShape(18.dp))
                    .background(bg ?: Color.White.copy(alpha = if (text == null) 0.08f else 0.16f))
                    .padding(horizontal = 9.dp, vertical = 6.dp)
            )
        }
        Row(Modifier.fillMaxWidth().padding(start = 12.dp).height(8.dp)) {
            Box(Modifier.offset(y = (-3).dp).size(8.dp).clip(CircleShape).background(bg ?: Color.White.copy(alpha = 0.16f)))
        }
        AvatarWithDot(avatar, name, 66.dp, online, ring = null, userId = userId)
        Spacer(Modifier.height(2.dp))
        Text(label, color = Color.White.copy(alpha = 0.75f), fontSize = 11.sp, maxLines = 1, overflow = TextOverflow.Ellipsis)
    }
}

@Composable
private fun NoteReplySheet(f: FriendRow, accent: Color, onPlay: (String) -> Unit, onReply: (String) -> Unit) {
    var reply by remember(f.userId) { mutableStateOf("") }
    val bg = noteColor(f.noteColor) ?: Color.White.copy(alpha = 0.14f)
    // Seperti Instagram: lagu catatan langsung terdengar saat catatan dibuka.
    LaunchedEffect(f.userId, f.noteSongId) { f.noteSongId?.let(onPlay) }
    Text(f.name, color = Color.White, fontWeight = FontWeight.Bold, fontSize = 14.sp)
    Spacer(Modifier.height(14.dp))
    Row(verticalAlignment = Alignment.CenterVertically) {
        AvatarWithDot(f.avatar, f.name, 64.dp, f.online, userId = f.userId)
        Spacer(Modifier.width(10.dp))
        Column(
            Modifier.weight(1f, fill = false).clip(RoundedCornerShape(24.dp)).background(bg).padding(horizontal = 16.dp, vertical = 12.dp)
        ) {
            if (!f.noteSong.isNullOrBlank()) Row(
                verticalAlignment = Alignment.CenterVertically,
                modifier = Modifier.clickable(enabled = f.noteSongId != null) { f.noteSongId?.let(onPlay) }
            ) {
                Icon(if (f.noteSongId != null) Icons.Rounded.PlayArrow else Icons.Rounded.MusicNote, null, tint = Color.White, modifier = Modifier.size(18.dp))
                Text(" " + (f.noteSong ?: ""), color = Color.White, fontWeight = FontWeight.SemiBold, fontSize = 14.sp, maxLines = 1, overflow = TextOverflow.Ellipsis)
            }
            if (!f.note.isNullOrBlank()) Text(f.note ?: "", color = Color.White, fontSize = 15.sp)
        }
    }
    if (!f.noteLyric.isNullOrBlank()) {
        var lyricShown by remember(f.userId) { mutableStateOf(false) }
        LaunchedEffect(f.userId) { delay(250); lyricShown = true }
        val la by androidx.compose.animation.core.animateFloatAsState(if (lyricShown) 1f else 0f, androidx.compose.animation.core.tween(600), label = "noteLyric")
        Text(
            f.noteLyric ?: "", color = Color.White, fontSize = 17.sp, fontWeight = FontWeight.Medium, textAlign = TextAlign.Center,
            modifier = Modifier.fillMaxWidth().padding(top = 18.dp, start = 12.dp, end = 12.dp).graphicsLayer { alpha = la; translationY = (1f - la) * 24f }
        )
    }
    Spacer(Modifier.height(16.dp))
    Row(
        Modifier.fillMaxWidth().clip(RoundedCornerShape(50)).background(Color.White.copy(alpha = 0.09f)).padding(horizontal = 16.dp, vertical = 6.dp),
        verticalAlignment = Alignment.CenterVertically
    ) {
        Box(Modifier.weight(1f)) {
            if (reply.isEmpty()) Text("Balas ${f.name.substringBefore(' ')}", color = Color.White.copy(alpha = 0.45f), fontSize = 15.sp)
            BasicTextField(reply, { reply = it.take(300) }, textStyle = TextStyle(color = Color.White, fontSize = 15.sp), cursorBrush = SolidColor(Color.White), modifier = Modifier.fillMaxWidth())
        }
        listOf("🔥", "😂", "😍").forEach { e -> Text(e, fontSize = 22.sp, modifier = Modifier.clickable { onReply(e) }.padding(horizontal = 4.dp)) }
        if (reply.isNotBlank()) Icon(Icons.Rounded.Check, "Kirim", tint = accent, modifier = Modifier.clickable { onReply(reply.trim()) }.padding(6.dp))
        else Icon(Icons.Rounded.Favorite, "Suka", tint = Color.White, modifier = Modifier.clickable { onReply("❤️") }.padding(6.dp))
    }
}
