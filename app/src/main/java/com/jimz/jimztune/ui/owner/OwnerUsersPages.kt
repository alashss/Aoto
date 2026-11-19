package com.jimz.jimztune.ui.owner

import androidx.compose.foundation.background
import androidx.compose.foundation.clickable
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.layout.width
import androidx.compose.foundation.shape.CircleShape
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.rounded.Block
import androidx.compose.material.icons.rounded.Gavel
import androidx.compose.material.icons.rounded.Search
import androidx.compose.material3.Icon
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.runtime.LaunchedEffect
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.rememberCoroutineScope
import androidx.compose.runtime.saveable.rememberSaveable
import androidx.compose.runtime.setValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clip
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.platform.LocalContext
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.text.style.TextOverflow
import androidx.compose.ui.unit.dp
import coil.compose.AsyncImage
import androidx.compose.ui.layout.ContentScale
import com.jimz.jimztune.data.owner.AdminUser
import com.jimz.jimztune.data.owner.OwnerApi
import com.jimz.jimztune.data.owner.UserActivity
import com.jimz.jimztune.ui.auth.ProfileAvatar
import com.jimz.jimztune.ui.components.GroupCard
import com.jimz.jimztune.ui.components.JimzDialog
import com.jimz.jimztune.ui.components.PillTextButton
import com.jimz.jimztune.ui.theme.LocalCoverTheme
import com.jimz.jimztune.util.TimeUtil
import kotlinx.coroutines.delay
import kotlinx.coroutines.launch

private const val PAGE_SIZE = 50

private fun AdminUser.suspendedNow(): Boolean {
    if (status == "banned") return true
    if (status != "suspended") return false
    val ms = TimeUtil.parse(until) ?: return true
    return ms > System.currentTimeMillis()
}

private fun AdminUser.statusLabel(): String = when {
    status == "banned" -> "DIBAN"
    suspendedNow() -> "DISUSPEND"
    else -> ""
}

// ============================================================================ DAFTAR PENGGUNA

@Composable
internal fun UsersPage(onBack: () -> Unit) {
    val scope = rememberCoroutineScope()
    var query by rememberSaveable { mutableStateOf("") }
    var users by remember { mutableStateOf<List<AdminUser>>(emptyList()) }
    var loading by remember { mutableStateOf(true) }
    var error by remember { mutableStateOf<String?>(null) }
    var canLoadMore by remember { mutableStateOf(false) }
    var selected by remember { mutableStateOf<AdminUser?>(null) }
    var tick by remember { mutableStateOf(0) }

    // Muat ulang saat kata kunci berubah (dengan jeda ketik) atau saat tick berubah (ulang manual / otomatis 30 detik).
    LaunchedEffect(query, tick) {
        if (tick == 0) delay(0) else delay(250)
        loading = true
        val r = OwnerApi.users(query)
        if (r.ok) {
            users = r.value.orEmpty()
            canLoadMore = users.size >= PAGE_SIZE
            error = null
        } else {
            error = r.error ?: "Gagal memuat"
        }
        loading = false
    }
    // Status online diperbarui sendiri selama daftar terbuka.
    LaunchedEffect(Unit) {
        while (true) {
            delay(30_000)
            tick++
        }
    }

    val current = selected
    if (current != null) {
        // Selalu pakai data terbaru dari daftar (status berubah setelah suspend / ban).
        val fresh = users.firstOrNull { it.id == current.id } ?: current
        UserDetailPage(
            user = fresh,
            onBack = { selected = null },
            onChanged = { tick++ }
        )
        return
    }

    OwnerScaffold(title = "Pengguna", subtitle = "Ketuk pengguna untuk melihat aktivitas & mengatur akun", onBack = onBack) {
        OwnerField(
            query, { query = it.take(60) }, "Cari nama, username, atau email"
        )
        val online = users.count { it.online }
        Text(
            "${users.size} ditampilkan · $online online",
            style = MaterialTheme.typography.labelMedium,
            color = Color.White.copy(alpha = 0.6f),
            modifier = Modifier.padding(start = 6.dp)
        )
        if (error != null) ErrorRow(error ?: "", { tick++ })
        if (loading && users.isEmpty()) LoadingRow()
        if (!loading && users.isEmpty() && error == null) {
            Text("Tidak ada pengguna yang cocok.", style = MaterialTheme.typography.bodyMedium, color = MaterialTheme.colorScheme.onSurfaceVariant)
        }
        GroupCard {
            users.forEachIndexed { i, u ->
                if (i > 0) OwnerDivider()
                UserRow(u) { selected = u }
            }
        }
        if (canLoadMore) {
            PillTextButton(
                "Muat lebih banyak",
                onClick = {
                    scope.launch {
                        val r = OwnerApi.users(query, offset = users.size)
                        if (r.ok) {
                            val more = r.value.orEmpty()
                            users = users + more
                            canLoadMore = more.size >= PAGE_SIZE
                        }
                    }
                },
                modifier = Modifier.fillMaxWidth(),
                primary = false
            )
        }
    }
}

@Composable
private fun UserRow(u: AdminUser, onClick: () -> Unit) {
    val accent = LocalCoverTheme.current.accent
    Row(
        modifier = Modifier
            .fillMaxWidth()
            .clickable(onClick = onClick)
            .padding(horizontal = 16.dp, vertical = 10.dp),
        verticalAlignment = Alignment.CenterVertically
    ) {
        Box {
            ProfileAvatar(url = u.avatarUrl, name = u.displayName, size = 44.dp, accent = accent)
            if (u.online) {
                Box(
                    modifier = Modifier
                        .align(Alignment.BottomEnd)
                        .size(13.dp)
                        .clip(CircleShape)
                        .background(Color(0xFF0B1030))
                        .padding(2.dp)
                        .clip(CircleShape)
                        .background(OwnerOk)
                )
            }
        }
        Spacer(Modifier.width(14.dp))
        Column(modifier = Modifier.weight(1f)) {
            Row(verticalAlignment = Alignment.CenterVertically) {
                Text(
                    u.displayName,
                    style = MaterialTheme.typography.bodyLarge,
                    fontWeight = FontWeight.Medium,
                    maxLines = 1,
                    overflow = TextOverflow.Ellipsis,
                    modifier = Modifier.weight(1f, fill = false)
                )
                if (u.isOwner) {
                    Spacer(Modifier.width(6.dp))
                    StatusPill("OWNER", accent)
                }
                val label = u.statusLabel()
                if (label.isNotEmpty()) {
                    Spacer(Modifier.width(6.dp))
                    StatusPill(label, OwnerDanger)
                }
            }
            Text(
                u.email,
                style = MaterialTheme.typography.bodySmall,
                color = MaterialTheme.colorScheme.onSurfaceVariant,
                maxLines = 1,
                overflow = TextOverflow.Ellipsis
            )
            Text(
                "Lvl. ${u.level} · ${com.jimz.jimztune.data.stats.formatListen(u.listenSeconds)} · ${u.plays} putaran",
                style = MaterialTheme.typography.labelSmall,
                color = Color.White.copy(alpha = 0.7f)
            )
            Text(
                when {
                    u.online -> "Online sekarang"
                    u.lastSeen != null -> "Terakhir aktif " + (TimeUtil.parse(u.lastSeen)?.let { TimeUtil.ago(it) } ?: "-")
                    else -> "Belum pernah aktif sejak fitur ini ada"
                },
                style = MaterialTheme.typography.labelSmall,
                color = if (u.online) OwnerOk else Color.White.copy(alpha = 0.5f)
            )
        }
    }
}

// ============================================================================ DETAIL PENGGUNA

private val SuspendLabels = listOf("1 jam", "1 hari", "7 hari", "30 hari", "Tanpa batas")
private val SuspendMillis = listOf(3_600_000L, 86_400_000L, 7 * 86_400_000L, 30 * 86_400_000L, 0L)

private enum class ModAction { Suspend, Ban, Restore }

@Composable
private fun UserDetailPage(user: AdminUser, onBack: () -> Unit, onChanged: () -> Unit) {
    val context = LocalContext.current
    val scope = rememberCoroutineScope()
    val accent = LocalCoverTheme.current.accent
    val loader = rememberLoader { OwnerApi.activity(user.id) }
    val activity: UserActivity? = loader.value

    var action by remember { mutableStateOf<ModAction?>(null) }
    var reason by remember { mutableStateOf("") }
    var durationIndex by remember { mutableStateOf(1) }
    var busy by remember { mutableStateOf(false) }

    // Aktivitas diperbarui sendiri tiap 30 detik, jadi lagu yang sedang didengar terpantau.
    LaunchedEffect(user.id) {
        while (true) {
            delay(30_000)
            loader.reload()
        }
    }

    fun apply(status: String, untilMs: Long?, why: String, okText: String) {
        if (busy) return
        scope.launch {
            busy = true
            val err = OwnerApi.setStatus(user.id, status, untilMs?.let { TimeUtil.toIso(it) }, why)
            toast(context, err ?: okText)
            busy = false
            action = null
            reason = ""
            onChanged()
        }
    }

    OwnerScaffold(title = user.displayName, subtitle = user.email, onBack = onBack) {
        GroupCard {
            Row(modifier = Modifier.padding(16.dp), verticalAlignment = Alignment.CenterVertically) {
                ProfileAvatar(url = user.avatarUrl, name = user.displayName, size = 56.dp, accent = accent)
                Spacer(Modifier.width(14.dp))
                Column(modifier = Modifier.weight(1f)) {
                    Text(user.username?.let { "@$it" } ?: "(belum ada username)", style = MaterialTheme.typography.bodyLarge, fontWeight = FontWeight.Medium)
                    Text("Daftar: ${TimeUtil.format(user.createdAt)}", style = MaterialTheme.typography.bodySmall, color = MaterialTheme.colorScheme.onSurfaceVariant)
                    Text(
                        if (user.online) "Online sekarang"
                        else "Terakhir aktif: " + (TimeUtil.parse(user.lastSeen)?.let { TimeUtil.format(it) } ?: "-"),
                        style = MaterialTheme.typography.bodySmall,
                        color = if (user.online) OwnerOk else MaterialTheme.colorScheme.onSurfaceVariant
                    )
                }
            }
        }

        UserListenBlock(user.id)

        // ---- Status akun ----
        OwnerSection("Status akun")
        val label = user.statusLabel()
        if (label.isNotEmpty()) {
            GroupCard {
                Column(modifier = Modifier.padding(16.dp)) {
                    Text(
                        if (user.status == "banned") "Akun ini DIBAN"
                        else TimeUtil.parse(user.until)?.let { "Disuspend sampai ${TimeUtil.format(it)}" } ?: "Disuspend tanpa batas waktu",
                        style = MaterialTheme.typography.bodyLarge,
                        fontWeight = FontWeight.SemiBold,
                        color = OwnerDanger
                    )
                    if (user.reason.isNotBlank()) {
                        Text("Alasan: ${user.reason}", style = MaterialTheme.typography.bodySmall, color = Color.White.copy(alpha = 0.75f))
                    }
                }
            }
            PillTextButton(
                "Pulihkan akun",
                onClick = { action = ModAction.Restore },
                modifier = Modifier.fillMaxWidth(),
                enabled = !busy
            )
        } else {
            Text("Akun aktif dan normal.", style = MaterialTheme.typography.bodyMedium, color = OwnerOk)
        }
        if (user.isOwner) {
            Text("Akun owner tidak bisa di-ban atau disuspend.", style = MaterialTheme.typography.bodySmall, color = OwnerWarn)
        } else {
            Row(horizontalArrangement = Arrangement.spacedBy(10.dp)) {
                PillTextButton("Suspend", onClick = { action = ModAction.Suspend }, modifier = Modifier.weight(1f), primary = false, enabled = !busy)
                PillTextButton("Ban", onClick = { action = ModAction.Ban }, modifier = Modifier.weight(1f), primary = false, enabled = !busy)
            }
        }

        // ---- Sesi ----
        OwnerSection("Kapan online")
        when {
            activity == null && loader.error != null -> ErrorRow(loader.error ?: "", loader::reload)
            activity == null -> LoadingRow()
            activity.sessions.isEmpty() -> Text("Belum ada catatan.", style = MaterialTheme.typography.bodyMedium, color = MaterialTheme.colorScheme.onSurfaceVariant)
            else -> GroupCard {
                activity.sessions.take(15).forEachIndexed { i, s ->
                    if (i > 0) OwnerDivider()
                    val start = TimeUtil.parse(s.startedAt)
                    val end = TimeUtil.parse(s.lastSeenAt)
                    Column(modifier = Modifier.padding(horizontal = 16.dp, vertical = 10.dp)) {
                        Text(
                            (start?.let { TimeUtil.format(it) } ?: "-") + (end?.let { " → " + TimeUtil.formatHour(it) } ?: ""),
                            style = MaterialTheme.typography.bodyMedium
                        )
                        val minutes = if (start != null && end != null) ((end - start) / 60_000).coerceAtLeast(0) else 0
                        Text(
                            "Dipakai sekitar $minutes menit" + if (s.appVersion.isNotBlank()) " · v${s.appVersion}" else "",
                            style = MaterialTheme.typography.labelSmall,
                            color = Color.White.copy(alpha = 0.55f)
                        )
                    }
                }
            }
        }

        // ---- Lagu yang diputar ----
        OwnerSection("Lagu yang diputar")
        when {
            activity == null -> Unit
            activity.plays.isEmpty() -> Text("Belum ada lagu yang tercatat.", style = MaterialTheme.typography.bodyMedium, color = MaterialTheme.colorScheme.onSurfaceVariant)
            else -> GroupCard {
                activity.plays.forEachIndexed { i, p ->
                    if (i > 0) OwnerDivider()
                    Row(
                        modifier = Modifier.padding(horizontal = 16.dp, vertical = 8.dp),
                        verticalAlignment = Alignment.CenterVertically
                    ) {
                        AsyncImage(
                            model = p.thumbnail,
                            contentDescription = null,
                            contentScale = ContentScale.Crop,
                            modifier = Modifier
                                .size(44.dp)
                                .clip(RoundedCornerShape(10.dp))
                                .background(Color.White.copy(alpha = 0.08f))
                        )
                        Spacer(Modifier.width(12.dp))
                        Column(modifier = Modifier.weight(1f)) {
                            Text(p.title, style = MaterialTheme.typography.bodyMedium, fontWeight = FontWeight.Medium, maxLines = 1, overflow = TextOverflow.Ellipsis)
                            Text(p.artist, style = MaterialTheme.typography.bodySmall, color = MaterialTheme.colorScheme.onSurfaceVariant, maxLines = 1, overflow = TextOverflow.Ellipsis)
                        }
                        Spacer(Modifier.width(8.dp))
                        Text(
                            TimeUtil.parse(p.playedAt)?.let { TimeUtil.format(it) } ?: "-",
                            style = MaterialTheme.typography.labelSmall,
                            color = Color.White.copy(alpha = 0.55f)
                        )
                    }
                }
            }
        }
    }

    when (action) {
        ModAction.Suspend -> JimzDialog(
            onDismiss = { if (!busy) action = null },
            icon = Icons.Rounded.Gavel,
            title = "Suspend ${user.displayName}?",
            message = "Akun langsung terkunci di HP-nya dan musiknya berhenti. Dia tidak bisa memakai aplikasi sampai waktunya habis atau kamu pulihkan.",
            confirmLabel = "Suspend",
            destructive = true,
            onConfirm = {
                val ms = SuspendMillis[durationIndex]
                apply("suspended", if (ms == 0L) null else System.currentTimeMillis() + ms, reason.trim(), "Akun disuspend")
            }
        ) {
            ChipRow(SuspendLabels, durationIndex) { durationIndex = it }
            Spacer(Modifier.height(10.dp))
            OwnerField(reason, { reason = it.take(120) }, "Alasan (dilihat user)")
        }
        ModAction.Ban -> JimzDialog(
            onDismiss = { if (!busy) action = null },
            icon = Icons.Rounded.Block,
            title = "Ban ${user.displayName}?",
            message = "Akun diblokir permanen sampai kamu pulihkan. Akun yang diban tidak bisa memakai aplikasi.",
            confirmLabel = "Ban",
            destructive = true,
            onConfirm = { apply("banned", null, reason.trim(), "Akun diban") }
        ) {
            OwnerField(reason, { reason = it.take(120) }, "Alasan (dilihat user)")
        }
        ModAction.Restore -> JimzDialog(
            onDismiss = { if (!busy) action = null },
            icon = Icons.Rounded.Gavel,
            title = "Pulihkan akun?",
            message = "${user.displayName} bisa memakai aplikasi lagi.",
            confirmLabel = "Pulihkan",
            onConfirm = { apply("active", null, "", "Akun dipulihkan") }
        )
        null -> Unit
    }
}
