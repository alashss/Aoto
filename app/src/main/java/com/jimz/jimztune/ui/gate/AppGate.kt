package com.jimz.jimztune.ui.gate

import android.app.Activity
import androidx.compose.animation.AnimatedVisibility
import androidx.compose.animation.core.FastOutSlowInEasing
import androidx.compose.animation.core.MutableTransitionState
import androidx.compose.animation.core.tween
import androidx.compose.animation.fadeIn
import androidx.compose.animation.scaleIn
import androidx.compose.foundation.Image
import androidx.compose.foundation.background
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.shape.CircleShape
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.automirrored.rounded.OpenInNew
import androidx.compose.material.icons.rounded.Close
import androidx.compose.material3.AlertDialog
import androidx.compose.material3.Button
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.OutlinedButton
import androidx.compose.material3.Text
import androidx.compose.material3.TextButton
import androidx.compose.runtime.Composable
import androidx.compose.runtime.LaunchedEffect
import androidx.compose.runtime.remember
import androidx.compose.runtime.rememberCoroutineScope
import androidx.compose.material.icons.automirrored.rounded.Logout
import com.jimz.jimztune.util.TimeUtil
import kotlinx.coroutines.delay
import kotlinx.coroutines.launch
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clip
import androidx.compose.ui.graphics.Brush
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.platform.LocalContext
import androidx.compose.ui.res.painterResource
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.text.style.TextAlign
import androidx.compose.ui.unit.dp
import com.jimz.jimztune.AppGraph
import com.jimz.jimztune.R
import com.jimz.jimztune.data.remote.Announcement
import com.jimz.jimztune.data.remote.Gate
import com.jimz.jimztune.data.remote.RemoteConfig
import com.jimz.jimztune.ui.components.GradientPillButton
import com.jimz.jimztune.ui.components.TonalPillButton
import com.jimz.jimztune.ui.theme.JimzBackground
import com.jimz.jimztune.ui.theme.JimzBlue
import com.jimz.jimztune.ui.theme.JimzIce
import com.jimz.jimztune.ui.theme.JimzIceDim
import com.jimz.jimztune.util.openUrl
import androidx.compose.material.icons.rounded.Campaign
import androidx.compose.material.icons.rounded.SystemUpdate
import com.jimz.jimztune.ui.components.JimzDialog

/**
 * Penjaga di atas seluruh aplikasi:
 *  - app dimatikan dari jauh  -> layar terkunci
 *  - versi di bawah minimum   -> layar wajib update
 *  - ada versi baru           -> dialog update (boleh ditunda)
 *  - ada pengumuman baru      -> dialog pengumuman (sekali tampil per pengumuman)
 */
@Composable
fun AppGate(onBlocked: () -> Unit, onInterrupt: () -> Unit = {}, content: @Composable () -> Unit) {
    val remote = AppGraph.remote
    val account = AppGraph.account
    val context = LocalContext.current

    // Akun di-ban / di-suspend oleh owner: terkunci sampai dipulihkan (berubah realtime, tanpa buka ulang aplikasi).
    if (account.blocked && AppGraph.auth.isLoggedIn) {
        LaunchedEffect(Unit) { onBlocked() }
        AccountBlockedScreen()
        return
    }

    // Owner tidak ikut terkunci saat aplikasi dimatikan dari jauh, supaya tetap bisa menyalakannya lagi dari Panel Owner.
    val rawGate = remote.gate()
    val gate = if (rawGate is Gate.Disabled && account.isOwner) Gate.Open else rawGate

    when (gate) {
        is Gate.Disabled -> {
            LaunchedEffect(Unit) { onBlocked() }
            BlockedScreen(
                title = gate.config.disabledTitle,
                message = gate.config.disabledMessage,
                actionLabel = gate.config.disabledUrl?.let { "Info selengkapnya" },
                onAction = { gate.config.disabledUrl?.let(context::openUrl) }
            )
        }
        is Gate.ForceUpdate -> {
            LaunchedEffect(Unit) { onBlocked() }
            val c = gate.config
            BlockedScreen(
                title = "Perlu pembaruan",
                message = "Versi aplikasimu (${remote.versionName}) sudah tidak didukung. " +
                    "Perbarui ke versi ${c.latestVersionName.ifBlank { "terbaru" }} untuk lanjut." +
                    (if (c.updateNotes.isNotBlank()) "\n\n${c.updateNotes}" else ""),
                actionLabel = "Perbarui sekarang",
                onAction = { (c.apkUrl ?: c.updateUrl)?.let(context::openUrl) }
            )
        }
        Gate.Open -> {
            content()
            val update = remote.pendingUpdate()
            val announcement = remote.pendingAnnouncement()
            when {
                update != null -> UpdateDialog(
                    config = update,
                    onDismiss = { remote.dismissUpdate(update.latestVersionCode) }
                )
                announcement != null -> {
                    // Pengumuman realtime: kalau owner menandainya "hentikan musik", pemutar dihentikan lalu pengumuman tampil.
                    if (announcement.interrupt) LaunchedEffect(announcement.id) { onInterrupt() }
                    AnnouncementDialog(
                        announcement = announcement,
                        onDismiss = { remote.markSeen(announcement.id) }
                    )
                }
            }
        }
    }
}

@Composable
private fun BlockedScreen(
    title: String,
    message: String,
    actionLabel: String?,
    onAction: () -> Unit,
    extraLabel: String? = null,
    onExtra: () -> Unit = {}
) {
    val activity = LocalContext.current as? Activity
    // Muncul pelan: logo membesar sedikit sambil memudar masuk.
    val visible = remember { MutableTransitionState(false).apply { targetState = true } }

    Box(
        modifier = Modifier
            .fillMaxSize()
            .background(
                Brush.verticalGradient(
                    0f to Color(0xFF111A3C),
                    0.5f to JimzBackground,
                    1f to JimzBackground
                )
            )
            .padding(32.dp),
        contentAlignment = Alignment.Center
    ) {
        AnimatedVisibility(
            visibleState = visible,
            enter = fadeIn(tween(600)) + scaleIn(tween(600, easing = FastOutSlowInEasing), initialScale = 0.92f)
        ) {
            Column(
                horizontalAlignment = Alignment.CenterHorizontally,
                verticalArrangement = Arrangement.Center
            ) {
                // Cahaya lembut di belakang logo.
                Box(contentAlignment = Alignment.Center) {
                    Box(
                        modifier = Modifier
                            .size(190.dp)
                            .background(
                                Brush.radialGradient(
                                    listOf(JimzBlue.copy(alpha = 0.28f), Color.Transparent)
                                )
                            )
                    )
                    Image(
                        painter = painterResource(R.drawable.aoto_logo),
                        contentDescription = null,
                        modifier = Modifier
                            .size(104.dp)
                            .clip(CircleShape)
                    )
                }
                Spacer(Modifier.height(20.dp))
                // Warna ditulis eksplisit: di luar aplikasi utama, teks tanpa warna bisa jadi hitam di latar gelap.
                Text(
                    title,
                    style = MaterialTheme.typography.headlineSmall,
                    fontWeight = FontWeight.Bold,
                    color = JimzIce,
                    textAlign = TextAlign.Center
                )
                Spacer(Modifier.height(10.dp))
                Text(
                    message,
                    style = MaterialTheme.typography.bodyMedium,
                    color = JimzIceDim,
                    textAlign = TextAlign.Center
                )
                Spacer(Modifier.height(32.dp))
                if (actionLabel != null) {
                    GradientPillButton(
                        text = actionLabel,
                        icon = Icons.AutoMirrored.Rounded.OpenInNew,
                        onClick = onAction,
                        modifier = Modifier.fillMaxWidth()
                    )
                    Spacer(Modifier.height(10.dp))
                }
                if (extraLabel != null) {
                    TonalPillButton(
                        text = extraLabel,
                        icon = Icons.AutoMirrored.Rounded.Logout,
                        onClick = onExtra,
                        modifier = Modifier.fillMaxWidth()
                    )
                    Spacer(Modifier.height(10.dp))
                }
                TonalPillButton(
                    text = "Tutup aplikasi",
                    icon = Icons.Rounded.Close,
                    onClick = { activity?.finishAffinity() },
                    modifier = Modifier.fillMaxWidth()
                )
            }
        }
    }
}

/** Layar untuk akun yang di-ban atau di-suspend. Satu-satunya pilihan: keluar akun atau menutup aplikasi. */
@Composable
private fun AccountBlockedScreen() {
    val account = AppGraph.account
    val scope = rememberCoroutineScope()
    val until = account.suspendedUntilMs
    val banned = account.status == "banned"

    // Suspend berbatas waktu: begitu waktunya habis, cek ke server supaya akun terbuka otomatis.
    LaunchedEffect(until) {
        if (!banned && until != null) {
            delay((until - System.currentTimeMillis()).coerceAtLeast(0) + 1_000)
            account.refresh()
        }
    }

    val message = buildString {
        if (banned) append("Akun ini diblokir dan tidak bisa dipakai lagi.")
        else if (until != null) append("Akun ini ditangguhkan sementara sampai ${TimeUtil.format(until)}.")
        else append("Akun ini ditangguhkan sementara oleh pemilik aplikasi.")
        if (account.reason.isNotBlank()) append("\n\nAlasan: ${account.reason}")
    }
    BlockedScreen(
        title = if (banned) "Akun diblokir" else "Akun ditangguhkan",
        message = message,
        actionLabel = null,
        onAction = {},
        extraLabel = "Keluar dari akun",
        onExtra = { scope.launch { AppGraph.auth.signOut() } }
    )
}

@Composable
fun UpdateDialog(config: RemoteConfig, onDismiss: () -> Unit) {
    val context = LocalContext.current
    JimzDialog(
        onDismiss = onDismiss,
        icon = Icons.Rounded.SystemUpdate,
        title = "Versi ${config.latestVersionName.ifBlank { "baru" }} tersedia",
        message = config.updateNotes.ifBlank { "Ada pembaruan Aoto dengan perbaikan dan fitur baru." },
        confirmLabel = "Perbarui",
        dismissLabel = "Nanti",
        onConfirm = {
            (config.apkUrl ?: config.updateUrl)?.let(context::openUrl)
            onDismiss()
        }
    )
}

@Composable
fun AnnouncementDialog(announcement: Announcement, onDismiss: () -> Unit) {
    val context = LocalContext.current
    val url = announcement.url
    JimzDialog(
        onDismiss = onDismiss,
        icon = Icons.Rounded.Campaign,
        title = announcement.title.ifBlank { "Pengumuman" },
        message = announcement.message,
        confirmLabel = if (url != null) (announcement.urlLabel ?: "Buka") else "Oke",
        dismissLabel = if (url != null) "Tutup" else null,
        onConfirm = {
            if (url != null) context.openUrl(url)
            onDismiss()
        }
    )
}
