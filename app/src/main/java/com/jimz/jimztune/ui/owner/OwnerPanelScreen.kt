package com.jimz.jimztune.ui.owner

import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.padding
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.rounded.Campaign
import androidx.compose.material.icons.rounded.Coffee
import androidx.compose.material.icons.rounded.Construction
import androidx.compose.material.icons.rounded.People
import androidx.compose.material.icons.rounded.SystemUpdate
import androidx.compose.material.icons.rounded.Sensors
import androidx.compose.material.icons.rounded.ViewCarousel
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
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.platform.LocalContext
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.unit.dp
import com.jimz.jimztune.AppGraph
import com.jimz.jimztune.data.owner.AdminConfig
import com.jimz.jimztune.data.owner.AdminStats
import com.jimz.jimztune.data.owner.OwnerApi
import com.jimz.jimztune.ui.components.GroupCard
import com.jimz.jimztune.ui.components.JimzDialog
import com.jimz.jimztune.ui.components.PillTextButton
import com.jimz.jimztune.ui.theme.LocalCoverTheme
import kotlinx.coroutines.launch
import org.json.JSONObject

private enum class OwnerPage { Hub, Control, Live, Banners, Announcements, Donate, Update, Users, Stats }

/**
 * Panel Owner. Hanya bisa dibuka akun yang didaftarkan di tabel `owners` server.
 * (Pengamanan sebenarnya ada di database: semua tulis/baca data sensitif diperiksa is_owner() di server.)
 */
@Composable
fun OwnerPanelScreen(onBack: () -> Unit) {
    var page by rememberSaveable { mutableStateOf(OwnerPage.Hub) }
    val back = { page = OwnerPage.Hub }
    when (page) {
        OwnerPage.Hub -> HubPage(onBack = onBack, onOpen = { page = it })
        OwnerPage.Control -> ControlPage(onBack = back)
        OwnerPage.Live -> LivePage(onBack = back)
        OwnerPage.Update -> UpdatePage(onBack = back)
        OwnerPage.Banners -> BannersPage(onBack = back)
        OwnerPage.Announcements -> AnnouncementsPage(onBack = back)
        OwnerPage.Donate -> DonatePage(onBack = back)
        OwnerPage.Users -> UsersPage(onBack = back)
        OwnerPage.Stats -> StatsPage(onBack = back)
    }
}

/** Kelompok saklar fitur: judul, lalu (kunci, nama, keterangan). Kunci yang tidak tersimpan = menyala. */
private val FeatureGroups: List<Pair<String, List<Triple<String, String, String>>>> = listOf(
    "Pemutar & lagu" to listOf(
        Triple("search", "Pencarian", "Tab Cari bisa dipakai"),
        Triple("lyrics", "Lirik", "Lirik & terjemahan di pemutar dan widget"),
        Triple("downloads", "Unduhan offline", "Pengguna bisa mengunduh lagu"),
        Triple("player_tools", "Timer tidur & kontrol suara", "Tombol timer, equalizer, dan efek suara di pemutar"),
        Triple("import_playlist", "Impor playlist", "Impor playlist dari layanan lain"),
        Triple("share", "Bagikan playlist & lagu", "Tautan bagikan playlist dan lagu")
    ),
    "Sosial & peringkat" to listOf(
        Triple("ranking", "Peringkat (semua)", "Layar peringkat dan kartunya di Beranda. Mati = semua peringkat hilang"),
        Triple("top_listeners", "Top pendengar", "Kartu dan tab peringkat waktu mendengar"),
        Triple("top_donors", "Top donatur", "Kartu dan tab peringkat donasi"),
        Triple("profile", "Profil pengguna", "Halaman profil dan ubah profil"),
        Triple("profile_stats", "Statistik di profil", "Statistik mendengar dan riwayat putar di profil")
    ),
    "Statistik pribadi" to listOf(
        Triple("streak", "Streak", "Kartu streak di Beranda dan menu Streak di Setelan"),
        Triple("recap", "Rekap dengar", "Menu Rekap dengar di Setelan")
    ),
    "Donasi" to listOf(
        Triple("donate", "Menu & popup donasi", "Tombol Traktir kopi dan popup donasi. Mati = semua tentang donasi hilang")
    ),
    "Akun" to listOf(
        Triple("signup", "Pendaftaran akun baru", "Orang baru bisa membuat akun")
    ),
    "Tampilan tambahan" to listOf(
        Triple("island", "Dynamic Island", "Menu Dynamic Island di Setelan"),
        Triple("widget", "Widget lirik", "Menu Widget lirik di Setelan")
    )
)

@Composable
private fun HubPage(onBack: () -> Unit, onOpen: (OwnerPage) -> Unit) {
    val stats = rememberLoader { OwnerApi.stats() }
    // Ringkasan diperbarui sendiri tiap 30 detik selama halaman terbuka.
    LaunchedEffect(Unit) {
        while (true) {
            kotlinx.coroutines.delay(30_000)
            stats.reload()
        }
    }

    OwnerScaffold(title = "Panel Owner", subtitle = "Kontrol penuh Aoto", onBack = onBack) {
        val s = stats.value
        when {
            s != null -> StatsCard(s)
            stats.error != null -> ErrorRow(stats.error ?: "", stats::reload)
            else -> LoadingRow()
        }

        GroupCard {
            OwnerNavRow(Icons.Rounded.Construction, "Kontrol aplikasi", "Maintenance, saklar fitur, matikan aplikasi") { onOpen(OwnerPage.Control) }
            OwnerDivider()
            OwnerNavRow(Icons.Rounded.Sensors, "Statistik lengkap", "Waktu dengar, jam ramai, pendengar teratas") { onOpen(OwnerPage.Stats) }
            OwnerDivider()
            OwnerNavRow(Icons.Rounded.Sensors, "Aktivitas live", "Siapa memutar apa sekarang, lagu terpopuler") { onOpen(OwnerPage.Live) }
            OwnerDivider()
            OwnerNavRow(Icons.Rounded.ViewCarousel, "Banner beranda", "Pasang, urutkan, dan hapus banner") { onOpen(OwnerPage.Banners) }
            OwnerDivider()
            OwnerNavRow(Icons.Rounded.Campaign, "Pengumuman", "Kirim realtime ke semua pengguna") { onOpen(OwnerPage.Announcements) }
            OwnerDivider()
            OwnerNavRow(Icons.Rounded.Coffee, "Donasi & popup", "Atur teks, tautan, dan popup realtime") { onOpen(OwnerPage.Donate) }
            OwnerDivider()
            OwnerNavRow(Icons.Rounded.SystemUpdate, "Versi & update", "Versi terbaru, wajib update, catatan rilis") { onOpen(OwnerPage.Update) }
            OwnerDivider()
            OwnerNavRow(Icons.Rounded.People, "Pengguna", "Daftar, aktivitas, suspend & ban") { onOpen(OwnerPage.Users) }
        }
    }
}

@Composable
private fun StatsCard(s: AdminStats) {
    GroupCard {
        Column(modifier = Modifier.padding(vertical = 14.dp, horizontal = 8.dp), verticalArrangement = Arrangement.spacedBy(14.dp)) {
            Row(modifier = Modifier.fillMaxWidth(), horizontalArrangement = Arrangement.SpaceEvenly) {
                Stat("Pengguna", s.users.toString())
                Stat("Online", s.online.toString(), valueColor = OwnerOk)
                Stat("Aktif 24 jam", s.active24h.toString())
            }
            Row(modifier = Modifier.fillMaxWidth(), horizontalArrangement = Arrangement.SpaceEvenly) {
                Stat("Baru 7 hari", s.new7d.toString())
                Stat("Putar hari ini", s.playsToday.toString())
                Stat("Diblokir", s.blocked.toString(), valueColor = if (s.blocked > 0) OwnerDanger else Color.White)
            }
        }
    }
}

@Composable
private fun Stat(label: String, value: String, valueColor: Color = Color.White) {
    Column(horizontalAlignment = Alignment.CenterHorizontally) {
        Text(value, style = MaterialTheme.typography.headlineSmall, fontWeight = FontWeight.Bold, color = valueColor)
        Text(label, style = MaterialTheme.typography.labelSmall, color = Color.White.copy(alpha = 0.6f))
    }
}

// ============================================================================ KONTROL APLIKASI

@Composable
private fun ControlPage(onBack: () -> Unit) {
    val context = LocalContext.current
    val scope = rememberCoroutineScope()
    val loader = rememberLoader { OwnerApi.config() }
    val cfg = loader.value

    var title by remember { mutableStateOf("") }
    var message by remember { mutableStateOf("") }
    var offTitle by remember { mutableStateOf("") }
    var offMessage by remember { mutableStateOf("") }
    var confirmOff by remember { mutableStateOf(false) }
    var busy by remember { mutableStateOf(false) }

    // Isi kolom teks sekali, begitu data pertama tiba.
    LaunchedEffect(cfg?.let { true }) {
        if (cfg != null) {
            title = cfg.homeMaintenanceTitle
            message = cfg.homeMaintenanceMessage
            offTitle = cfg.disabledTitle
            offMessage = cfg.disabledMessage
        }
    }

    fun patch(changes: JSONObject, okText: String) {
        if (busy) return
        scope.launch {
            busy = true
            val err = OwnerApi.patchConfig(changes)
            toast(context, err ?: okText)
            busy = false
            loader.reload()
        }
    }

    OwnerScaffold(title = "Kontrol aplikasi", subtitle = "Perubahan langsung sampai ke semua pengguna", onBack = onBack) {
        if (cfg == null) {
            if (loader.error != null) ErrorRow(loader.error ?: "", loader::reload) else LoadingRow()
            return@OwnerScaffold
        }

        OwnerSection("Maintenance beranda")
        GroupCard {
            OwnerSwitchRow(
                title = "Beranda sedang diperbaiki",
                subtitle = if (cfg.homeMaintenance) "AKTIF: user hanya melihat playlist, favorit, dan unduhan (banner tetap tampil)"
                else "Mati: beranda tampil normal",
                checked = cfg.homeMaintenance,
                enabled = !busy,
                onChange = { on ->
                    patch(JSONObject().put("home_maintenance", on), if (on) "Mode perbaikan beranda dinyalakan" else "Beranda kembali normal")
                }
            )
        }
        OwnerField(title, { title = it.take(80) }, "Judul yang dilihat user")
        OwnerField(message, { message = it.take(300) }, "Pesan yang dilihat user", singleLine = false, minLines = 3)
        PillTextButton(
            "Simpan teks perbaikan",
            onClick = {
                patch(
                    JSONObject().put("home_maintenance_title", title.trim()).put("home_maintenance_message", message.trim()),
                    "Teks disimpan"
                )
            },
            modifier = Modifier.fillMaxWidth(),
            enabled = !busy && title.isNotBlank() && message.isNotBlank()
        )

        OwnerSection("Saklar fitur")
        Text(
            "Mati = fitur hilang / tidak bisa dibuka untuk SEMUA pengguna. Kamu sebagai owner tetap bisa memakainya untuk menguji.",
            style = MaterialTheme.typography.bodySmall,
            color = Color.White.copy(alpha = 0.6f),
            modifier = Modifier.padding(bottom = 8.dp)
        )
        FeatureGroups.forEach { (groupTitle, items) ->
            Text(
                groupTitle,
                style = MaterialTheme.typography.labelLarge,
                fontWeight = FontWeight.Bold,
                color = Color.White.copy(alpha = 0.75f),
                modifier = Modifier.padding(top = 10.dp, bottom = 6.dp)
            )
            GroupCard {
                items.forEachIndexed { i, (key, name, desc) ->
                    if (i > 0) OwnerDivider()
                    val on = cfg.features[key] != false
                    OwnerSwitchRow(
                        title = name,
                        subtitle = if (on) desc else "DIMATIKAN untuk semua pengguna (kamu tetap bisa memakainya)",
                        checked = on,
                        enabled = !busy,
                        onChange = { v ->
                            if (!busy) scope.launch {
                                busy = true
                                val err = OwnerApi.setFeature(cfg.features, key, v)
                                toast(context, err ?: if (v) "$name dinyalakan" else "$name dimatikan")
                                busy = false
                                loader.reload()
                            }
                        }
                    )
                }
            }
        }
        val offCount = FeatureGroups.sumOf { g -> g.second.count { cfg.features[it.first] == false } }
        Row(Modifier.fillMaxWidth().padding(top = 12.dp), horizontalArrangement = Arrangement.spacedBy(8.dp)) {
            PillTextButton(
                "Nyalakan semua" + if (offCount > 0) " ($offCount mati)" else "",
                onClick = {
                    if (!busy) scope.launch {
                        busy = true
                        val err = OwnerApi.setAllFeatures(true)
                        toast(context, err ?: "Semua fitur dinyalakan")
                        busy = false
                        loader.reload()
                    }
                },
                primary = false,
                enabled = !busy && offCount > 0,
                modifier = Modifier.weight(1f)
            )
        }

        OwnerSection("Matikan seluruh aplikasi")
        GroupCard {
            OwnerSwitchRow(
                title = "Aplikasi aktif",
                subtitle = if (cfg.appEnabled) "Semua user bisa memakai aplikasi" else "DIMATIKAN: user melihat layar terkunci (kamu tetap bisa masuk)",
                checked = cfg.appEnabled,
                enabled = !busy,
                onChange = { on ->
                    if (on) patch(JSONObject().put("app_enabled", true), "Aplikasi dinyalakan kembali") else confirmOff = true
                }
            )
        }
        OwnerField(offTitle, { offTitle = it.take(80) }, "Judul layar terkunci")
        OwnerField(offMessage, { offMessage = it.take(300) }, "Pesan layar terkunci", singleLine = false, minLines = 3)
        PillTextButton(
            "Simpan teks layar terkunci",
            onClick = {
                patch(
                    JSONObject().put("disabled_title", offTitle.trim()).put("disabled_message", offMessage.trim()),
                    "Teks disimpan"
                )
            },
            modifier = Modifier.fillMaxWidth(),
            primary = false,
            enabled = !busy && offTitle.isNotBlank() && offMessage.isNotBlank()
        )
    }

    if (confirmOff) {
        JimzDialog(
            onDismiss = { confirmOff = false },
            icon = Icons.Rounded.Construction,
            title = "Matikan aplikasi?",
            message = "Semua pengguna (selain kamu) akan langsung melihat layar terkunci dan musiknya berhenti.",
            confirmLabel = "Matikan",
            destructive = true,
            onConfirm = {
                confirmOff = false
                patch(JSONObject().put("app_enabled", false), "Aplikasi dimatikan")
            }
        )
    }
}
