package com.jimz.jimztune.ui.social

import androidx.compose.foundation.background
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
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.layout.width
import androidx.compose.foundation.lazy.LazyColumn
import androidx.compose.foundation.lazy.LazyRow
import androidx.compose.foundation.lazy.items
import androidx.compose.foundation.shape.CircleShape
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.rounded.Edit
import androidx.compose.material.icons.rounded.Forum
import androidx.compose.material.icons.rounded.Groups
import androidx.compose.material.icons.rounded.PersonAdd
import androidx.compose.material.icons.rounded.Search
import androidx.compose.material3.AlertDialog
import androidx.compose.material3.CircularProgressIndicator
import androidx.compose.material3.Icon
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.OutlinedTextField
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
import androidx.compose.ui.draw.clip
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.text.style.TextAlign
import androidx.compose.ui.text.style.TextOverflow
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import com.jimz.jimztune.AppGraph
import com.jimz.jimztune.data.social.FriendRequests
import com.jimz.jimztune.data.social.FriendRow
import com.jimz.jimztune.data.social.PersonRow
import com.jimz.jimztune.data.social.SearchRow
import com.jimz.jimztune.data.social.SocialApi
import com.jimz.jimztune.data.social.SocialNav
import com.jimz.jimztune.data.social.SocialPage
import com.jimz.jimztune.ui.auth.jimzFieldColors
import com.jimz.jimztune.ui.components.GlassIconButton
import com.jimz.jimztune.ui.components.PillTextButton
import com.jimz.jimztune.ui.ranking.RankAvatar
import com.jimz.jimztune.ui.theme.LocalCoverTheme
import kotlinx.coroutines.delay
import kotlinx.coroutines.launch

@Composable
private fun EmptyHint(text: String) {
    Box(Modifier.fillMaxWidth().padding(40.dp), Alignment.Center) {
        Text(text, color = Color.White.copy(alpha = 0.55f), textAlign = TextAlign.Center, fontSize = 14.sp)
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

    val incoming = reqs?.incoming.orEmpty()
    SocialScaffold(title = "Teman", onBack = onBack) {
        Row(
            modifier = Modifier.padding(horizontal = 16.dp, vertical = 4.dp),
            horizontalArrangement = Arrangement.spacedBy(8.dp)
        ) {
            listOf("Teman" to 0, ("Permintaan" + (if (incoming.isNotEmpty()) " (${incoming.size})" else "")) to 1, "Cari" to 2).forEach { (label, i) ->
                Text(
                    label, fontSize = 13.sp, fontWeight = FontWeight.SemiBold,
                    color = if (tab == i) Color.Black else Color.White,
                    modifier = Modifier.clip(RoundedCornerShape(50))
                        .background(if (tab == i) accent else Color.White.copy(alpha = 0.08f))
                        .clickable { tab = i }.padding(horizontal = 16.dp, vertical = 8.dp)
                )
            }
        }
        if (msg != null) Text(msg ?: "", color = accent, fontSize = 13.sp, modifier = Modifier.padding(horizontal = 20.dp, vertical = 4.dp))
        when (tab) {
            0 -> {
                val list = friends
                when {
                    list == null -> Box(Modifier.fillMaxSize(), Alignment.Center) { CircularProgressIndicator(color = Color.White) }
                    list.isEmpty() -> EmptyHint("Belum ada teman.\nCari pengguna di tab Cari atau dari papan peringkat.")
                    else -> LazyColumn(contentPadding = androidx.compose.foundation.layout.PaddingValues(bottom = 24.dp)) {
                        items(list, key = { it.userId }) { f ->
                            PersonLine(
                                f.name, f.username, f.avatar, f.online,
                                sub = f.playing?.let { "🎵 ${it.title} · ${it.artist}" } ?: if (f.online == true) "Online" else null,
                                onClick = { SocialNav.open(SocialPage.Profile(f.userId)) }
                            ) {
                                GlassIconButton(Icons.Rounded.Forum, "Pesan", onClick = { SocialNav.open(SocialPage.Dm(f.userId, f.name, f.avatar)) })
                            }
                        }
                    }
                }
            }
            1 -> {
                val r = reqs
                if (r == null) Box(Modifier.fillMaxSize(), Alignment.Center) { CircularProgressIndicator(color = Color.White) }
                else if (r.incoming.isEmpty() && r.outgoing.isEmpty()) EmptyHint("Tidak ada permintaan teman.")
                else LazyColumn(contentPadding = androidx.compose.foundation.layout.PaddingValues(bottom = 24.dp)) {
                    if (r.incoming.isNotEmpty()) item { SectionLabel("Masuk") }
                    items(r.incoming, key = { "i" + it.requestId }) { p ->
                        PersonLine(p.name, p.username, p.avatar, null, null, onClick = { SocialNav.open(SocialPage.Profile(p.userId)) }) {
                            Row(horizontalArrangement = Arrangement.spacedBy(6.dp)) {
                                PillTextButton("Terima", { scope.launch { msg = SocialApi.respond(p.requestId, true); reload++ } })
                                PillTextButton("Tolak", { scope.launch { msg = SocialApi.respond(p.requestId, false); reload++ } }, primary = false)
                            }
                        }
                    }
                    if (r.outgoing.isNotEmpty()) item { SectionLabel("Terkirim") }
                    items(r.outgoing, key = { "o" + it.requestId }) { p ->
                        PersonLine(p.name, p.username, p.avatar, null, "Menunggu jawaban", onClick = { SocialNav.open(SocialPage.Profile(p.userId)) }) {
                            PillTextButton("Batal", { scope.launch { msg = SocialApi.removeFriend(p.userId); reload++ } }, primary = false)
                        }
                    }
                }
            }
            else -> {
                OutlinedTextField(
                    value = query, onValueChange = { query = it.take(40) },
                    placeholder = { Text("Cari nama atau @username", color = Color.White.copy(alpha = 0.4f)) },
                    leadingIcon = { Icon(Icons.Rounded.Search, null, tint = Color.White.copy(alpha = 0.6f)) },
                    singleLine = true, shape = RoundedCornerShape(16.dp), colors = jimzFieldColors(accent),
                    modifier = Modifier.fillMaxWidth().padding(horizontal = 16.dp, vertical = 8.dp)
                )
                val res = results
                if (res == null) EmptyHint("Ketik minimal 2 huruf.")
                else if (res.isEmpty()) EmptyHint("Tidak ada pengguna yang cocok.")
                else LazyColumn(contentPadding = androidx.compose.foundation.layout.PaddingValues(bottom = 24.dp)) {
                    items(res, key = { it.userId }) { u ->
                        PersonLine(u.name, u.username, u.avatar, null, when (u.relation) { "friend" -> "Teman"; "pending_out" -> "Permintaan terkirim"; "pending_in" -> "Mengirim permintaan"; else -> null },
                            onClick = { SocialNav.open(SocialPage.Profile(u.userId)) }) {
                            if (u.relation == "none") GlassIconButton(Icons.Rounded.PersonAdd, "Tambah", onClick = {
                                scope.launch {
                                    val r = SocialApi.requestFriend(u.userId)
                                    msg = r.error ?: if (r.value == "accepted") "Kalian sekarang berteman" else "Permintaan terkirim"
                                    reload++
                                    results = SocialApi.search(query.trim()).value
                                }
                            })
                        }
                    }
                }
            }
        }
    }
}

@Composable
private fun SectionLabel(text: String) {
    Text(text, color = Color.White.copy(alpha = 0.6f), fontSize = 12.sp, fontWeight = FontWeight.Bold, modifier = Modifier.padding(start = 20.dp, top = 14.dp, bottom = 4.dp))
}

@Composable
internal fun PersonLine(
    name: String, username: String?, avatar: String?, online: Boolean?, sub: String?,
    onClick: () -> Unit, trailing: @Composable () -> Unit = {}
) {
    Row(
        modifier = Modifier.fillMaxWidth().clickable(onClick = onClick).padding(horizontal = 16.dp, vertical = 8.dp),
        verticalAlignment = Alignment.CenterVertically
    ) {
        AvatarWithDot(avatar, name, 48.dp, online)
        Spacer(Modifier.width(12.dp))
        Column(Modifier.weight(1f)) {
            Text(name, color = Color.White, fontWeight = FontWeight.SemiBold, maxLines = 1, overflow = TextOverflow.Ellipsis)
            val line = sub ?: username?.let { "@$it" }
            if (line != null) Text(line, color = Color.White.copy(alpha = 0.6f), fontSize = 12.sp, maxLines = 1, overflow = TextOverflow.Ellipsis)
        }
        Spacer(Modifier.width(8.dp))
        trailing()
    }
}

// ============================================================ Kotak masuk (gaya Instagram)

@Composable
fun InboxScreen(onBack: () -> Unit) {
    val accent = LocalCoverTheme.current.accent
    val scope = rememberCoroutineScope()
    var friends by remember { mutableStateOf<List<FriendRow>?>(null) }
    var editNote by remember { mutableStateOf(false) }
    var myNote by remember { mutableStateOf("") }
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

    SocialScaffold(
        title = "Pesan", onBack = onBack,
        actions = {
            GlassIconButton(Icons.Rounded.Groups, "Chat global", onClick = { SocialNav.open(SocialPage.GlobalChat) })
            Spacer(Modifier.width(8.dp))
            GlassIconButton(Icons.Rounded.PersonAdd, "Teman", onClick = { SocialNav.open(SocialPage.Friends) })
        }
    ) {
        val list = friends
        if (list == null) {
            Box(Modifier.fillMaxSize(), Alignment.Center) { CircularProgressIndicator(color = Color.White) }
            return@SocialScaffold
        }
        // Baris catatan: catatan sendiri + catatan teman (hilang setelah 24 jam)
        LazyRow(
            contentPadding = androidx.compose.foundation.layout.PaddingValues(horizontal = 14.dp, vertical = 6.dp),
            horizontalArrangement = Arrangement.spacedBy(14.dp)
        ) {
            item {
                NoteBubbleItem(myProfile?.displayName ?: "Kamu", myProfile?.avatarUrl, myNote.ifBlank { null }, null, "Catatanmu", accent) { editNote = true }
            }
            items(list.filter { !it.note.isNullOrBlank() }, key = { "n" + it.userId }) { f ->
                NoteBubbleItem(f.name, f.avatar, f.note, f.online, f.name.substringBefore(' '), accent) {
                    SocialNav.open(SocialPage.Dm(f.userId, f.name, f.avatar))
                }
            }
        }
        if (list.isEmpty()) {
            EmptyHint("Belum ada percakapan.\nTambahkan teman dulu, lalu mulai ngobrol.")
        } else {
            val sorted = list.sortedWith(compareByDescending<FriendRow> { it.lastAt ?: "" }.thenBy { it.name })
            LazyColumn(contentPadding = androidx.compose.foundation.layout.PaddingValues(bottom = 24.dp)) {
                items(sorted, key = { it.userId }) { f ->
                    val preview = when {
                        f.lastBody == null -> f.playing?.let { "🎵 ${it.title}" } ?: "Mulai percakapan"
                        f.lastMine -> "Kamu: " + f.lastBody
                        else -> f.lastBody
                    }
                    Row(
                        modifier = Modifier.fillMaxWidth().clickable { SocialNav.open(SocialPage.Dm(f.userId, f.name, f.avatar)) }.padding(horizontal = 16.dp, vertical = 9.dp),
                        verticalAlignment = Alignment.CenterVertically
                    ) {
                        AvatarWithDot(f.avatar, f.name, 52.dp, f.online)
                        Spacer(Modifier.width(12.dp))
                        Column(Modifier.weight(1f)) {
                            Text(f.name, color = Color.White, fontWeight = if (f.unread > 0) FontWeight.Bold else FontWeight.SemiBold, maxLines = 1, overflow = TextOverflow.Ellipsis)
                            Text(
                                preview, maxLines = 1, overflow = TextOverflow.Ellipsis, fontSize = 13.sp,
                                color = if (f.unread > 0) Color.White else Color.White.copy(alpha = 0.6f),
                                fontWeight = if (f.unread > 0) FontWeight.SemiBold else FontWeight.Normal
                            )
                        }
                        Spacer(Modifier.width(8.dp))
                        Column(horizontalAlignment = Alignment.End) {
                            if (f.lastAt != null) Text(agoText(f.lastAt), color = Color.White.copy(alpha = 0.45f), fontSize = 11.sp)
                            if (f.unread > 0) {
                                Spacer(Modifier.height(4.dp))
                                Box(Modifier.clip(CircleShape).background(accent).size(20.dp), Alignment.Center) {
                                    Text(if (f.unread > 9) "9+" else f.unread.toString(), color = Color.Black, fontSize = 10.sp, fontWeight = FontWeight.Black)
                                }
                            }
                        }
                    }
                }
            }
        }
    }

    if (editNote) {
        var text by remember { mutableStateOf(myNote) }
        AlertDialog(
            onDismissRequest = { editNote = false },
            title = { Text("Catatanmu") },
            text = {
                Column {
                    Text("Terlihat oleh temanmu selama 24 jam.", fontSize = 13.sp)
                    Spacer(Modifier.height(8.dp))
                    OutlinedTextField(value = text, onValueChange = { text = it.take(60) }, placeholder = { Text("Lagi mikirin apa?") }, singleLine = false, maxLines = 3)
                    Text("${text.length}/60", fontSize = 11.sp, modifier = Modifier.align(Alignment.End))
                }
            },
            confirmButton = {
                TextButton(onClick = {
                    editNote = false
                    scope.launch { SocialApi.setNote(text.trim()); myNote = text.trim() }
                }) { Text("Simpan") }
            },
            dismissButton = {
                if (myNote.isNotBlank()) TextButton(onClick = {
                    editNote = false
                    scope.launch { SocialApi.setNote(""); myNote = "" }
                }) { Text("Hapus") }
            }
        )
    }
}

@Composable
private fun NoteBubbleItem(name: String, avatar: String?, note: String?, online: Boolean?, label: String, accent: Color, onClick: () -> Unit) {
    Column(modifier = Modifier.width(76.dp).clickable(onClick = onClick), horizontalAlignment = Alignment.CenterHorizontally) {
        Box(
            modifier = Modifier.height(40.dp).fillMaxWidth(),
            contentAlignment = Alignment.BottomCenter
        ) {
            Text(
                note ?: "+ Catatan", fontSize = 10.sp, maxLines = 2, overflow = TextOverflow.Ellipsis, textAlign = TextAlign.Center,
                color = if (note == null) Color.White.copy(alpha = 0.6f) else Color.White, lineHeight = 12.sp,
                modifier = Modifier.clip(RoundedCornerShape(12.dp)).background(Color.White.copy(alpha = 0.12f)).padding(horizontal = 7.dp, vertical = 4.dp)
            )
        }
        Spacer(Modifier.height(4.dp))
        AvatarWithDot(avatar, name, 56.dp, online, ring = if (note != null) accent else null)
        Text(label, color = Color.White.copy(alpha = 0.75f), fontSize = 11.sp, maxLines = 1, overflow = TextOverflow.Ellipsis)
    }
}
