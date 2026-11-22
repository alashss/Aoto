package com.jimz.jimztune.ui.settings

import androidx.activity.compose.BackHandler
import androidx.compose.runtime.saveable.rememberSaveable
import androidx.compose.material.icons.automirrored.rounded.ArrowBack
import androidx.compose.material.icons.rounded.Lock
import androidx.compose.material.icons.rounded.PlayCircle
import androidx.compose.material.icons.rounded.AdminPanelSettings
import androidx.compose.material.icons.rounded.BatteryChargingFull
import androidx.compose.material.icons.rounded.Bolt
import androidx.compose.material.icons.rounded.Info
import androidx.compose.material.icons.rounded.PowerSettingsNew
import androidx.compose.runtime.DisposableEffect
import androidx.compose.ui.platform.LocalLifecycleOwner
import androidx.lifecycle.Lifecycle
import androidx.lifecycle.LifecycleEventObserver
import com.jimz.jimztune.util.BackgroundPlayback
import com.jimz.jimztune.util.shareText
import android.widget.Toast
import androidx.compose.ui.graphics.graphicsLayer
import androidx.compose.foundation.horizontalScroll
import androidx.compose.animation.AnimatedVisibility
import androidx.compose.animation.Crossfade
import androidx.compose.animation.core.tween
import androidx.compose.animation.expandVertically
import androidx.compose.animation.fadeIn
import androidx.compose.animation.fadeOut
import androidx.compose.animation.shrinkVertically
import androidx.compose.foundation.Image
import androidx.compose.foundation.background
import androidx.compose.foundation.border
import androidx.compose.foundation.clickable
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.ColumnScope
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.layout.statusBarsPadding
import androidx.compose.foundation.layout.width
import androidx.compose.foundation.rememberScrollState
import com.jimz.jimztune.player.PlaybackDiagnostics
import androidx.compose.ui.text.font.FontFamily
import androidx.compose.material.icons.rounded.Description
import androidx.compose.foundation.layout.heightIn
import android.content.Context
import android.content.ClipboardManager
import android.content.ClipData
import androidx.compose.foundation.shape.CircleShape
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.foundation.verticalScroll
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.rounded.Campaign
import androidx.compose.material.icons.rounded.AccountCircle
import androidx.compose.material.icons.rounded.CloudSync
import androidx.compose.material.icons.rounded.Person
import com.jimz.jimztune.data.cloud.SyncStatus
import com.jimz.jimztune.ui.auth.ProfileAvatar
import androidx.compose.material.icons.rounded.Bedtime
import androidx.compose.material.icons.rounded.BatterySaver
import androidx.compose.material.icons.rounded.BlurOn
import androidx.compose.material.icons.rounded.ChevronRight
import androidx.compose.material.icons.rounded.Coffee
import androidx.compose.material.icons.rounded.DeleteSweep
import androidx.compose.material.icons.rounded.ExpandLess
import androidx.compose.material.icons.rounded.ExpandMore
import androidx.compose.material.icons.rounded.GraphicEq
import androidx.compose.material.icons.rounded.Storage
import androidx.compose.material.icons.rounded.PhoneAndroid
import androidx.compose.material.icons.rounded.Insights
import androidx.compose.material.icons.rounded.LocalFireDepartment
import androidx.compose.material.icons.rounded.Palette
import androidx.compose.material.icons.rounded.Link
import androidx.compose.material.icons.rounded.NewReleases
import androidx.compose.material.icons.rounded.Subtitles
import androidx.compose.material.icons.rounded.SystemUpdate
import androidx.compose.material3.HorizontalDivider
import androidx.compose.material3.Icon
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.Switch
import androidx.compose.material3.Text
import androidx.compose.material3.TextButton
import androidx.compose.runtime.Composable
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
import androidx.compose.ui.graphics.vector.ImageVector
import androidx.compose.ui.platform.LocalContext
import androidx.compose.ui.platform.LocalUriHandler
import androidx.compose.ui.res.painterResource
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import com.jimz.jimztune.AppGraph
import com.jimz.jimztune.R
import com.jimz.jimztune.data.remote.Announcement
import com.jimz.jimztune.data.settings.AudioQuality
import com.jimz.jimztune.ui.components.GroupCard
import com.jimz.jimztune.ui.components.IconBadge
import com.jimz.jimztune.ui.components.LocalOverlayPadding
import com.jimz.jimztune.ui.components.ScreenHeader
import com.jimz.jimztune.ui.components.SegmentedTabs
import com.jimz.jimztune.ui.gate.UpdateDialog
import com.jimz.jimztune.ui.theme.LocalCoverTheme
import kotlinx.coroutines.launch
import java.io.File
import com.jimz.jimztune.ui.components.JimzDialog
import com.jimz.jimztune.util.openUrl

@Composable
fun SettingsScreen(
    onDonate: () -> Unit,
    onOpenAnnouncements: () -> Unit,
    onOpenProfile: () -> Unit,
    onOpenOwner: () -> Unit = {}
) {
    val overlay = LocalOverlayPadding.current
    val context = LocalContext.current
    val scope = rememberCoroutineScope()
    val settings = AppGraph.settings
    val downloads = AppGraph.downloads
    val remote = AppGraph.remote
    val accent = LocalCoverTheme.current.accent

    var confirmClearDownloads by remember { mutableStateOf(false) }
    var page by rememberSaveable { mutableStateOf("hub") }
    BackHandler(enabled = page != "hub") { page = "hub" }
    var showAudioFx by remember { mutableStateOf(false) }
    var showAppearance by remember { mutableStateOf(false) }
    var showStreak by remember { mutableStateOf(false) }
    var showRecap by remember { mutableStateOf(false) }
    var showIsland by remember { mutableStateOf(false) }
    var showStorage by remember { mutableStateOf(false) }
    var showLyricsWidget by remember { mutableStateOf(false) }
    var updateToShow by remember { mutableStateOf<com.jimz.jimztune.data.remote.RemoteConfig?>(null) }

    Column(
        modifier = Modifier
            .fillMaxSize()
            .statusBarsPadding()
            .verticalScroll(rememberScrollState())
            .padding(bottom = overlay)
    ) {
        if (page == "hub") {
            ScreenHeader("Setelan", "Akun, audio, tampilan, dan privasi")
        } else {
            Row(
                modifier = Modifier.padding(start = 16.dp, end = 20.dp, top = 14.dp, bottom = 4.dp),
                verticalAlignment = Alignment.CenterVertically
            ) {
                com.jimz.jimztune.ui.components.GlassIconButton(Icons.AutoMirrored.Rounded.ArrowBack, "Kembali", onClick = { page = "hub" })
                Spacer(Modifier.width(14.dp))
                Text(
                    when (page) {
                        "akun" -> "Akun"; "privasi" -> "Privasi"; "tampilan" -> "Tampilan"; "audio" -> "Audio"
                        "pemutaran" -> "Pemutaran"; "data" -> "Penyimpanan"; else -> "Tentang & bantuan"
                    },
                    style = MaterialTheme.typography.titleLarge, fontWeight = FontWeight.Bold
                )
            }
        }
        if (page == "hub") {
            GroupCard(modifier = Modifier.padding(horizontal = 20.dp)) {
                SettingRow(Icons.Rounded.Person, "Akun", "Profil, login, profil publik", { page = "akun" }, trailing = { HubChevron() })
                RowDivider()
                SettingRow(Icons.Rounded.Lock, "Privasi", "Siapa yang bisa melihat profilmu, daftar blokir, cadangan", { page = "privasi" }, trailing = { HubChevron() })
                RowDivider()
                SettingRow(Icons.Rounded.Palette, "Tampilan", "Tema, gaya, animasi, personalisasi", { page = "tampilan" }, trailing = { HubChevron() })
                RowDivider()
                SettingRow(Icons.Rounded.GraphicEq, "Audio", "Equalizer, efek suara, kualitas", { page = "audio" }, trailing = { HubChevron() })
                if (AppGraph.account.isOwner) {
                    RowDivider()
                    SettingRow(Icons.Rounded.PlayCircle, "Pemutaran", "Catatan pemutar (khusus owner)", { page = "pemutaran" }, trailing = { HubChevron() })
                }
                RowDivider()
                SettingRow(Icons.Rounded.Storage, "Penyimpanan", "Unduhan dan cache", { page = "data" }, trailing = { HubChevron() })
                RowDivider()
                SettingRow(Icons.Rounded.Info, "Tentang & bantuan", "Pengumuman, widget, donasi, versi", { page = "aplikasi" }, trailing = { HubChevron() })
            }
        }


        if (page == "akun") {
        // ---- Akun ----
        if (com.jimz.jimztune.data.cloud.CloudApi.configured) {
            Section("Akun")
            val auth = AppGraph.auth
            val profile = AppGraph.profile.profile
            GroupCard(modifier = Modifier.padding(horizontal = 20.dp)) {
                if (auth.isLoggedIn) {
                    Row(
                        modifier = Modifier
                            .fillMaxWidth()
                            .clickable(onClick = onOpenProfile)
                            .padding(horizontal = 16.dp, vertical = 12.dp),
                        verticalAlignment = Alignment.CenterVertically
                    ) {
                        ProfileAvatar(
                            url = profile?.avatarUrl,
                            name = (profile?.displayName ?: auth.session?.email.orEmpty()).substringBefore("@"),
                            size = 44.dp,
                            accent = profile?.accent ?: accent
                        )
                        Spacer(Modifier.width(14.dp))
                        Column(modifier = Modifier.weight(1f)) {
                            Text(profile?.displayName ?: "Profilku", style = MaterialTheme.typography.bodyLarge, fontWeight = FontWeight.Medium)
                            Text(
                                profile?.username?.let { "@$it" } ?: "Profilku",
                                style = MaterialTheme.typography.bodySmall,
                                color = MaterialTheme.colorScheme.onSurfaceVariant
                            )
                        }
                        Icon(Icons.Rounded.ChevronRight, contentDescription = null, tint = Color.White.copy(alpha = 0.35f))
                    }
                    RowDivider()
                    val sync = AppGraph.cloud.status
                    SettingRow(
                        icon = Icons.Rounded.CloudSync,
                        title = "Sinkronkan sekarang",
                        subtitle = when (sync) {
                            SyncStatus.Idle -> "Favorit, playlist, dan riwayat disimpan ke akun"
                            SyncStatus.Syncing -> "Sedang menyinkronkan…"
                            is SyncStatus.Done -> "Tersimpan di cloud"
                            is SyncStatus.Failed -> com.jimz.jimztune.util.UserError.text("Gagal menyinkronkan. Coba lagi nanti.", sync.message)
                        },
                        onClick = { AppGraph.cloud.syncManually() }
                    )

                var pubOn by remember { mutableStateOf(false) }
                var pubReady by remember { mutableStateOf(false) }
                androidx.compose.runtime.LaunchedEffect(Unit) {
                    com.jimz.jimztune.data.profile.PublicProfile.isOn()?.let { pubOn = it; pubReady = true }
                }
                val togglePub: (Boolean) -> Unit = { want ->
                    scope.launch {
                        val r = com.jimz.jimztune.data.profile.PublicProfile.set(want)
                        if (r == null) Toast.makeText(context, "Gagal mengubah. Coba lagi.", Toast.LENGTH_SHORT).show()
                        else pubOn = r
                    }
                }
                RowDivider()
                SettingRow(
                    icon = Icons.Rounded.Link,
                    title = "Profil publik",
                    subtitle = if (pubOn) "Aktif: link profilmu bisa dibuka tanpa aplikasi" else "Mati: tampilkan level, total dengar, dan 5 lagu teratas lewat link",
                    onClick = { if (pubReady) togglePub(!pubOn) },
                    trailing = { Switch(checked = pubOn, enabled = pubReady, onCheckedChange = { togglePub(it) }) }
                )
                if (pubOn) {
                    val link = com.jimz.jimztune.data.profile.PublicProfile.link()
                    RowDivider()
                    SettingRow(
                        icon = Icons.Rounded.Link,
                        title = "Bagikan link profil",
                        subtitle = link ?: "Alamat web belum diatur di build ini",
                        onClick = {
                            if (link != null) context.shareText("Profil Aoto", link)
                            else Toast.makeText(context, "SHARE_BASE_URL belum diisi", Toast.LENGTH_SHORT).show()
                        }
                    )
                }
                    // Hanya muncul untuk akun yang didaftarkan sebagai owner di server.
                    if (AppGraph.account.isOwner) {
                        RowDivider()
                        SettingRow(
                            icon = Icons.Rounded.AdminPanelSettings,
                            title = "Panel Owner",
                            subtitle = "Maintenance, pengumuman, banner, pengguna, dan aktivitas",
                            onClick = onOpenOwner
                        )
                    }
                } else {
                    SettingRow(
                        icon = Icons.Rounded.AccountCircle,
                        title = "Masuk / Daftar",
                        subtitle = "Simpan favorit, playlist, dan riwayat ke akun",
                        onClick = { auth.openLogin() }
                    )
                }
            }
        }

        if (com.jimz.jimztune.data.cloud.CloudApi.configured && AppGraph.auth.isLoggedIn) {
            Text(
                "Saat masuk akun, lagu yang kamu putar dan waktu aplikasi dipakai dicatat untuk pemilik aplikasi.",
                style = MaterialTheme.typography.bodySmall,
                color = MaterialTheme.colorScheme.onSurfaceVariant,
                modifier = Modifier.padding(start = 26.dp, end = 26.dp, top = 8.dp)
            )
        }

        }
        if (page == "audio") {
        // ---- Audio ----
        Section("Audio")
        GroupCard(modifier = Modifier.padding(horizontal = 20.dp)) {
            Column(modifier = Modifier.padding(16.dp)) {
                Row(verticalAlignment = Alignment.CenterVertically) {
                    IconBadge(Icons.Rounded.GraphicEq)
                    Spacer(Modifier.width(14.dp))
                    Column {
                        Text("Kualitas audio", style = MaterialTheme.typography.bodyLarge, fontWeight = FontWeight.SemiBold)
                        Text(
                            "Berlaku mulai lagu berikutnya",
                            style = MaterialTheme.typography.bodySmall,
                            color = MaterialTheme.colorScheme.onSurfaceVariant
                        )
                    }
                }
                Spacer(Modifier.height(14.dp))
                SegmentedTabs(
                    labels = AudioQuality.entries.map { it.label },
                    selected = AudioQuality.entries.indexOf(settings.quality),
                    onSelect = { settings.updateQuality(AudioQuality.entries[it]) }
                )
                Crossfade(targetState = settings.quality.hint, label = "qualityHint") { hint ->
                    Text(
                        hint,
                        style = MaterialTheme.typography.bodySmall,
                        color = MaterialTheme.colorScheme.onSurfaceVariant,
                        modifier = Modifier.padding(top = 10.dp, start = 4.dp)
                    )
                }
            }
            RowDivider()
            SettingRow(
                icon = Icons.Rounded.GraphicEq,
                title = "Kontrol suara",
                subtitle = "Gaya suara, kecepatan, equalizer, crossfade",
                onClick = { showAudioFx = true }
            )
        }

        if (showAudioFx) com.jimz.jimztune.ui.player.AudioFxSheet(onDismiss = { showAudioFx = false })
        }
        if (page == "tampilan") {

        Section("Tampilan & fitur")
        GroupCard(modifier = Modifier.padding(horizontal = 20.dp)) {
            SettingRow(
                icon = Icons.Rounded.Palette,
                title = "Tampilan & tema",
                subtitle = "${if (com.jimz.jimztune.ui.theme.AppTheme.customOn) "Warna kustom" else com.jimz.jimztune.ui.theme.AppTheme.preset.label} · gaya ${com.jimz.jimztune.ui.theme.AppTheme.uiStyle.label}",
                onClick = { showAppearance = true }
            )
            if (remote.featureOn("streak")) {
                RowDivider()
                SettingRow(
                    icon = Icons.Rounded.LocalFireDepartment,
                    title = "Streak",
                    subtitle = "Hari berturut-turut mendengarkan",
                    onClick = { showStreak = true }
                )
            }
            if (remote.featureOn("recap")) {
                RowDivider()
                SettingRow(
                    icon = Icons.Rounded.Insights,
                    title = "Rekap dengar",
                    subtitle = "Lagu dan artis teratas, bisa dibagikan",
                    onClick = { showRecap = true }
                )
            }
            if (remote.featureOn("island")) {
                RowDivider()
                SettingRow(
                    icon = Icons.Rounded.PhoneAndroid,
                    title = "Dynamic Island",
                    subtitle = if (com.jimz.jimztune.island.IslandPrefs.enabled) "Aktif" else "Mati",
                    onClick = { showIsland = true }
                )
            }
        }

        // ---- Personalisasi ----
        Section("Personalisasi")
        var hiRefresh by remember { mutableStateOf(com.jimz.jimztune.util.PrivacyPrefs.highRefresh(context)) }
        GroupCard(modifier = Modifier.padding(horizontal = 20.dp)) {
            SettingRow(
                icon = Icons.Rounded.Bolt,
                title = "Refresh rate tinggi",
                subtitle = "Minta layar 120 Hz kalau didukung (lebih mulus, sedikit lebih boros)",
                onClick = null,
                trailing = {
                    Switch(checked = hiRefresh, onCheckedChange = {
                        hiRefresh = it
                        com.jimz.jimztune.util.PrivacyPrefs.setHighRefresh(context, it)
                        (context as? android.app.Activity)?.let { a -> com.jimz.jimztune.util.PrivacyPrefs.applyRefresh(a) }
                    })
                }
            )
            RowDivider()
            var autoNight by remember { mutableStateOf(com.jimz.jimztune.ui.theme.AppTheme.autoNight) }
            SettingRow(
                icon = Icons.Rounded.Bedtime,
                title = "Tema ikut jam",
                subtitle = "Pukul 22.00 sampai 05.00 latar jadi hitam pekat dan aksen lebih lembut",
                onClick = null,
                trailing = {
                    Switch(checked = autoNight, onCheckedChange = {
                        autoNight = it
                        com.jimz.jimztune.ui.theme.AppTheme.updateAutoNight(it)
                    })
                }
            )
            RowDivider()
            var saver by remember { mutableStateOf(com.jimz.jimztune.ui.theme.AppTheme.saver) }
            SettingRow(
                icon = Icons.Rounded.BatterySaver,
                title = "Mode hemat baterai",
                subtitle = "Matikan blur latar, aurora, dan refresh rate tinggi sekaligus",
                onClick = null,
                trailing = {
                    Switch(checked = saver, onCheckedChange = {
                        saver = it
                        com.jimz.jimztune.ui.theme.AppTheme.updateSaver(it)
                        (context as? android.app.Activity)?.let { a -> com.jimz.jimztune.util.PrivacyPrefs.applyRefresh(a) }
                    })
                }
            )
            if (com.jimz.jimztune.ui.theme.backdropSupported) {
                RowDivider()
                var liveBlur by remember { mutableStateOf(com.jimz.jimztune.ui.theme.AppTheme.liveBlur) }
                SettingRow(
                    icon = Icons.Rounded.BlurOn,
                    title = "Blur latar sungguhan",
                    subtitle = "Bar bawah memburamkan isi di belakangnya (gaya Kaca/Liquid Glass). Matikan kalau terasa berat.",
                    onClick = null,
                    trailing = {
                        Switch(checked = liveBlur, onCheckedChange = {
                            liveBlur = it
                            com.jimz.jimztune.ui.theme.AppTheme.updateLiveBlur(it)
                        })
                    }
                )
            }
        }

        }
        if (page == "pemutaran") {
        // ---- Pemutaran (log hanya untuk owner; user biasa tidak melihatnya) ----
        if (AppGraph.account.isOwner) {
        Section("Pemutaran")
        GroupCard(modifier = Modifier.padding(horizontal = 20.dp)) {
            var showPlayerLog by remember { mutableStateOf(false) }
            SettingRow(
                icon = Icons.Rounded.Description,
                title = "Log pemutar",
                subtitle = "Catatan pencarian & pengunduhan lagu terakhir. Salin lalu kirim kalau lagu bermasalah",
                onClick = { showPlayerLog = true }
            )
            if (showPlayerLog) {
                val logText = (PlaybackDiagnostics.dump() + "\n" + com.jimz.jimztune.util.UserError.dump()).trim().ifBlank { "Belum ada catatan. Putar sebuah lagu dulu." }
                JimzDialog(
                    onDismiss = { showPlayerLog = false },
                    icon = Icons.Rounded.Description,
                    title = "Log pemutar",
                    confirmLabel = "Salin",
                    dismissLabel = "Tutup",
                    onConfirm = {
                        try {
                            val cm = context.getSystemService(Context.CLIPBOARD_SERVICE) as ClipboardManager
                            cm.setPrimaryClip(ClipData.newPlainText("Log pemutar", logText))
                        } catch (_: Exception) {
                        }
                        showPlayerLog = false
                    },
                    content = {
                        Column(modifier = Modifier.heightIn(max = 320.dp).verticalScroll(rememberScrollState())) {
                            Text(
                                logText,
                                style = MaterialTheme.typography.bodySmall,
                                fontFamily = FontFamily.Monospace,
                                color = Color.White.copy(alpha = 0.85f)
                            )
                        }
                    }
                )
            }
        }

        }

        }
        if (page == "data") {
        // ---- Penyimpanan ----
        Section("Penyimpanan")
        GroupCard(modifier = Modifier.padding(horizontal = 20.dp)) {
            SettingRow(
                icon = Icons.Rounded.Storage,
                title = "Rincian penyimpanan",
                subtitle = "Pemakaian unduhan dan cache, bisa dibersihkan",
                onClick = { showStorage = true }
            )
            RowDivider()
            SettingRow(
                icon = Icons.Rounded.DeleteSweep,
                title = "Hapus semua unduhan",
                subtitle = "${downloads.songs.size} lagu tersimpan offline",
                onClick = {
                    if (downloads.songs.isEmpty()) {
                        Toast.makeText(context, "Belum ada unduhan", Toast.LENGTH_SHORT).show()
                    } else confirmClearDownloads = true
                }
            )
            if (remote.featureOn("widget")) {
                RowDivider()
                SettingRow(
                    icon = Icons.Rounded.Subtitles,
                    title = "Hapus cache lirik",
                    subtitle = "Lirik akan diunduh ulang saat dibutuhkan",
                    onClick = {
                        val files: List<File> = listOf("lyrics_v3", "lyrics_tr_v3").flatMap { File(context.filesDir, it).listFiles()?.toList().orEmpty() }
                        files.forEach { it.delete() }
                        Toast.makeText(context, "${files.size} lirik dihapus dari cache", Toast.LENGTH_SHORT).show()
                    }
                )
            }
        }

        }
        if (page == "privasi") {
            if (AppGraph.auth.isLoggedIn) {
                Section("Profil sosial")
                GroupCard(modifier = Modifier.padding(horizontal = 20.dp)) {
                    SettingRow(
                        Icons.Rounded.Lock, "Privasi profil",
                        "Atur siapa yang bisa melihat profilmu, bagian yang disembunyikan, dan daftar blokir",
                        { com.jimz.jimztune.data.social.SocialNav.open(com.jimz.jimztune.data.social.SocialPage.Privacy) },
                        trailing = { HubChevron() }
                    )
                }
            }
        // ---- Privasi & cadangan ----
        Section("Privasi & cadangan")
        var blockShots by remember { mutableStateOf(com.jimz.jimztune.util.ScreenGuard.enabled(context)) }
        val exportLauncher = androidx.activity.compose.rememberLauncherForActivityResult(
            androidx.activity.result.contract.ActivityResultContracts.CreateDocument("application/json")
        ) { uri ->
            if (uri != null) {
                val r = com.jimz.jimztune.data.backup.BackupManager.export(context, uri)
                Toast.makeText(
                    context,
                    if (r.isSuccess) "Cadangan tersimpan (${r.getOrNull()} pengaturan)" else "Gagal menyimpan cadangan",
                    Toast.LENGTH_LONG
                ).show()
            }
        }
        val importLauncher = androidx.activity.compose.rememberLauncherForActivityResult(
            androidx.activity.result.contract.ActivityResultContracts.OpenDocument()
        ) { uri ->
            if (uri != null) {
                val r = com.jimz.jimztune.data.backup.BackupManager.restore(context, uri)
                Toast.makeText(
                    context,
                    if (r.isSuccess) "Dipulihkan (${r.getOrNull()} pengaturan). Tutup lalu buka lagi Aoto."
                    else "Berkas bukan cadangan Aoto yang valid",
                    Toast.LENGTH_LONG
                ).show()
            }
        }
        val m3uExport = androidx.activity.compose.rememberLauncherForActivityResult(
            androidx.activity.result.contract.ActivityResultContracts.CreateDocument("audio/x-mpegurl")
        ) { uri ->
            if (uri != null) {
                val r = com.jimz.jimztune.data.backup.M3uPlaylists.export(context, uri, com.jimz.jimztune.AppGraph.library.playlists)
                Toast.makeText(context, if (r.isSuccess) "${r.getOrNull()} lagu diekspor" else "Gagal mengekspor", Toast.LENGTH_LONG).show()
            }
        }
        val m3uImport = androidx.activity.compose.rememberLauncherForActivityResult(
            androidx.activity.result.contract.ActivityResultContracts.OpenDocument()
        ) { uri ->
            if (uri != null) {
                val r = com.jimz.jimztune.data.backup.M3uPlaylists.import(context, uri)
                Toast.makeText(
                    context,
                    if (r.isSuccess) "${r.getOrNull()} lagu diimpor" else (r.exceptionOrNull()?.message ?: "Gagal mengimpor"),
                    Toast.LENGTH_LONG
                ).show()
            }
        }
        var btResume by remember { mutableStateOf(com.jimz.jimztune.util.PrivacyPrefs.btResume(context)) }
        GroupCard(modifier = Modifier.padding(horizontal = 20.dp)) {
            SettingRow(
                icon = Icons.Rounded.PhoneAndroid,
                title = "Blokir tangkapan layar",
                subtitle = "Sembunyikan Aoto dari screenshot dan tampilan Recents",
                onClick = {
                    blockShots = !blockShots
                    com.jimz.jimztune.util.ScreenGuard.set(context, blockShots)
                },
                trailing = {
                    Switch(checked = blockShots, onCheckedChange = {
                        blockShots = it
                        com.jimz.jimztune.util.ScreenGuard.set(context, it)
                    })
                }
            )
            RowDivider()
            SettingRow(
                icon = Icons.Rounded.Bolt,
                title = "Lanjut otomatis saat Bluetooth tersambung",
                subtitle = "Putar lagi kalau baru dijeda (maks 10 menit) saat earphone/speaker tersambung",
                onClick = {
                    btResume = !btResume
                    com.jimz.jimztune.util.PrivacyPrefs.setBtResume(context, btResume)
                },
                trailing = {
                    Switch(checked = btResume, onCheckedChange = {
                        btResume = it
                        com.jimz.jimztune.util.PrivacyPrefs.setBtResume(context, it)
                    })
                }
            )
            RowDivider()
            SettingRow(
                icon = Icons.Rounded.Link,
                title = "Ekspor playlist (M3U)",
                subtitle = "Semua playlist jadi satu berkas M3U",
                onClick = { m3uExport.launch("aoto-playlist.m3u") }
            )
            RowDivider()
            SettingRow(
                icon = Icons.Rounded.Link,
                title = "Impor playlist (M3U)",
                subtitle = "Baca berkas M3U berisi tautan YouTube / YouTube Music",
                onClick = { m3uImport.launch(arrayOf("*/*")) }
            )
            RowDivider()
            SettingRow(
                icon = Icons.Rounded.Storage,
                title = "Cadangkan pengaturan",
                subtitle = "Simpan tema, gaya, widget, streak, dan riwayat ke satu berkas",
                onClick = { exportLauncher.launch("aoto-cadangan.json") }
            )
            RowDivider()
            SettingRow(
                icon = Icons.Rounded.SystemUpdate,
                title = "Pulihkan dari cadangan",
                subtitle = "Pilih berkas cadangan Aoto (akun tidak ikut)",
                onClick = { importLauncher.launch(arrayOf("application/json", "text/plain", "*/*")) }
            )
        }

        }
        if (page == "aplikasi") {
        // ---- Aplikasi ----
        Section("Aplikasi")
        val announcements = remote.activeAnnouncements()
        GroupCard(modifier = Modifier.padding(horizontal = 20.dp)) {
            SettingRow(
                icon = Icons.Rounded.SystemUpdate,
                title = "Cek pembaruan",
                subtitle = "Versi terpasang: ${remote.versionName} (${remote.versionCode})",
                onClick = {
                    if (!remote.configured) {
                        Toast.makeText(
                            context,
                            com.jimz.jimztune.util.UserError.text("Pembaruan belum tersedia saat ini", "Server belum dikonfigurasi (lihat README)"),
                            Toast.LENGTH_LONG
                        ).show()
                    } else {
                        scope.launch {
                            val ok = remote.refresh(force = true)
                            val update = remote.availableUpdate()
                            when {
                                update != null -> updateToShow = update
                                ok -> Toast.makeText(context, "Kamu sudah memakai versi terbaru", Toast.LENGTH_SHORT).show()
                                else -> Toast.makeText(context, "Tidak bisa menghubungi server", Toast.LENGTH_SHORT).show()
                            }
                        }
                    }
                }
            )
            RowDivider()
            SettingRow(
                icon = Icons.Rounded.Info,
                title = "Lisensi & kode sumber",
                subtitle = "GNU GPL v3 atau lebih baru. Ketuk untuk membuka kode sumber",
                onClick = {
                    val src = com.jimz.jimztune.AppConfig.SOURCE_URL
                    context.openUrl(
                        if (src.startsWith("http")) src else "https://www.gnu.org/licenses/gpl-3.0.html"
                    )
                }
            )
            RowDivider()
            val unread = remote.unreadCount()
            SettingRow(
                icon = Icons.Rounded.Campaign,
                title = "Pengumuman",
                subtitle = when {
                    unread > 0 -> "$unread pengumuman belum dibaca"
                    announcements.isEmpty() -> "Belum ada pengumuman"
                    else -> "${announcements.size} pengumuman aktif"
                },
                onClick = onOpenAnnouncements,
                trailing = {
                    if (unread > 0) {
                        Box(
                            modifier = Modifier
                                .clip(CircleShape)
                                .background(accent)
                                .padding(horizontal = 9.dp, vertical = 3.dp)
                        ) {
                            Text(
                                "$unread",
                                color = Color(0xFF0B1030),
                                fontSize = 12.sp,
                                fontWeight = FontWeight.Bold
                            )
                        }
                    } else {
                        Icon(
                            Icons.Rounded.ChevronRight,
                            contentDescription = null,
                            tint = Color.White.copy(alpha = 0.35f)
                        )
                    }
                }
            )
            RowDivider()
            SettingRow(
                icon = Icons.Rounded.Subtitles,
                title = "Widget lirik",
                subtitle = "Gaya dan pasang di layar utama",
                onClick = { showLyricsWidget = true }
            )
            if (remote.donationAvailable && remote.featureOn("donate")) {
                RowDivider()
                SettingRow(
                    icon = Icons.Rounded.Coffee,
                    title = "Traktir kopi",
                    subtitle = "Dukung pengembangan Aoto",
                    onClick = onDonate
                )
            }
        }

        }
        if (showAppearance) AppearanceSheet(onDismiss = { showAppearance = false })
        if (showStreak) com.jimz.jimztune.ui.stats.StreakSheet(onDismiss = { showStreak = false })
        if (showRecap) com.jimz.jimztune.ui.stats.RecapSheet(onDismiss = { showRecap = false })
        if (showIsland) IslandSheet(onDismiss = { showIsland = false })
        if (showStorage) StorageSheet(onDismiss = { showStorage = false })
        if (showLyricsWidget) LyricsWidgetSheet(onDismiss = { showLyricsWidget = false })
        // ---- Footer ----
        Column(
            modifier = Modifier
                .fillMaxWidth()
                .padding(top = 28.dp, bottom = 8.dp),
            horizontalAlignment = Alignment.CenterHorizontally
        ) {
            Image(
                painter = painterResource(R.drawable.aoto_logo),
                contentDescription = null,
                modifier = Modifier
                    .size(44.dp)
                    .clip(CircleShape)
            )
            Spacer(Modifier.height(8.dp))
            Text(
                "Aoto ${remote.versionName}",
                style = MaterialTheme.typography.labelMedium,
                color = MaterialTheme.colorScheme.onSurfaceVariant
            )
        }
    }

    updateToShow?.let { UpdateDialog(it) { updateToShow = null } }

    if (confirmClearDownloads) {
        JimzDialog(
            onDismiss = { confirmClearDownloads = false },
            icon = Icons.Rounded.DeleteSweep,
            title = "Hapus semua unduhan?",
            message = "Lagu offline akan dihapus dari perangkat. Kamu bisa mengunduhnya lagi nanti.",
            confirmLabel = "Hapus",
            destructive = true,
            onConfirm = {
                downloads.deleteAll()
                confirmClearDownloads = false
            }
        )
    }
}

@Composable
private fun Section(title: String) {
    Text(
        title.uppercase(),
        style = MaterialTheme.typography.labelMedium,
        fontWeight = FontWeight.SemiBold,
        letterSpacing = 1.2.sp,
        color = LocalCoverTheme.current.accent,
        modifier = Modifier.padding(start = 26.dp, end = 20.dp, top = 22.dp, bottom = 8.dp)
    )
}

@Composable
private fun ColumnScope.RowDivider() {
    HorizontalDivider(
        modifier = Modifier.padding(start = 70.dp),
        thickness = 1.dp,
        color = Color.White.copy(alpha = 0.06f)
    )
}

@Composable
private fun SettingRow(
    icon: ImageVector,
    title: String,
    subtitle: String,
    onClick: (() -> Unit)?,
    trailing: (@Composable () -> Unit)? = null
) {
    Row(
        modifier = Modifier
            .fillMaxWidth()
            .then(if (onClick != null) Modifier.clickable(onClick = onClick) else Modifier)
            .heightIn(min = 64.dp)
            .padding(horizontal = 16.dp, vertical = 10.dp),
        verticalAlignment = Alignment.CenterVertically
    ) {
        IconBadge(icon)
        Spacer(Modifier.width(14.dp))
        Column(modifier = Modifier.weight(1f)) {
            Text(title, style = MaterialTheme.typography.bodyLarge, fontWeight = FontWeight.Medium)
            if (subtitle.isNotBlank()) {
                Text(
                    subtitle,
                    style = MaterialTheme.typography.bodySmall,
                    color = MaterialTheme.colorScheme.onSurfaceVariant
                )
            }
        }
        when {
            trailing != null -> {
                Spacer(Modifier.width(8.dp))
                trailing()
            }
            onClick != null -> Icon(
                Icons.Rounded.ChevronRight,
                contentDescription = null,
                tint = Color.White.copy(alpha = 0.35f)
            )
        }
    }
}


@Composable
private fun HubChevron() {
    Icon(Icons.Rounded.ChevronRight, contentDescription = null, tint = Color.White.copy(alpha = 0.35f))
}
