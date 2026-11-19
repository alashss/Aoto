package com.jimz.jimztune.ui.owner

import android.net.Uri
import androidx.activity.compose.rememberLauncherForActivityResult
import androidx.activity.result.PickVisualMediaRequest
import androidx.activity.result.contract.ActivityResultContracts
import androidx.compose.foundation.background
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.aspectRatio
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.layout.width
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.automirrored.rounded.Send
import androidx.compose.material.icons.rounded.ArrowDownward
import androidx.compose.material.icons.rounded.ArrowUpward
import androidx.compose.material.icons.rounded.Campaign
import androidx.compose.material.icons.rounded.Delete
import androidx.compose.material.icons.rounded.Image
import androidx.compose.material.icons.rounded.Coffee
import androidx.compose.material3.Icon
import androidx.compose.material3.IconButton
import androidx.compose.material3.MaterialTheme
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
import androidx.compose.ui.layout.ContentScale
import androidx.compose.ui.platform.LocalContext
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.text.style.TextOverflow
import androidx.compose.ui.unit.dp
import coil.compose.AsyncImage
import com.jimz.jimztune.AppGraph
import com.jimz.jimztune.data.owner.AdminBanner
import com.jimz.jimztune.data.owner.OwnerApi
import com.jimz.jimztune.ui.components.GroupCard
import com.jimz.jimztune.ui.components.JimzDialog
import com.jimz.jimztune.ui.components.PillTextButton
import com.jimz.jimztune.ui.theme.LocalCoverTheme
import com.jimz.jimztune.util.ImageUtil
import com.jimz.jimztune.util.TimeUtil
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.launch
import kotlinx.coroutines.withContext
import org.json.JSONObject

// ============================================================================ BANNER

@Composable
internal fun BannersPage(onBack: () -> Unit) {
    val context = LocalContext.current
    val scope = rememberCoroutineScope()
    val loader = rememberLoader { OwnerApi.banners() }
    val banners = loader.value.orEmpty()

    var pickedUri by remember { mutableStateOf<Uri?>(null) }
    var imageUrl by remember { mutableStateOf("") }
    var title by remember { mutableStateOf("") }
    var link by remember { mutableStateOf("") }
    var busy by remember { mutableStateOf(false) }
    var deleteTarget by remember { mutableStateOf<AdminBanner?>(null) }

    val picker = rememberLauncherForActivityResult(ActivityResultContracts.PickVisualMedia()) { uri ->
        if (uri != null) pickedUri = uri
    }

    fun run(block: suspend () -> String?, okText: String? = null) {
        if (busy) return
        scope.launch {
            busy = true
            val err = block()
            if (err != null) toast(context, err) else if (okText != null) toast(context, okText)
            busy = false
            loader.reload()
        }
    }

    fun add() {
        if (busy) return
        scope.launch {
            busy = true
            var url = imageUrl.trim()
            val uri = pickedUri
            if (uri != null) {
                val pick = withContext(Dispatchers.IO) { ImageUtil.prepare(context, uri, 1600, 8f / 3f, 8 * 1024 * 1024) }
                if (pick is ImageUtil.Picked.TooBig) {
                    toast(context, "GIF terlalu besar (maks ${pick.limitMb} MB)")
                    busy = false
                    return@launch
                }
                val ready = pick as? ImageUtil.Picked.Ready
                if (ready == null) {
                    toast(context, "Gambar tidak bisa dibaca")
                    busy = false
                    return@launch
                }
                val up = OwnerApi.uploadBannerImage(ready.bytes, ready.contentType, ready.ext)
                if (!up.ok) {
                    toast(context, up.error ?: "Gagal mengunggah gambar")
                    busy = false
                    return@launch
                }
                url = up.value.orEmpty()
            }
            if (url.isBlank()) {
                toast(context, "Pilih gambar atau isi alamat gambar dulu")
                busy = false
                return@launch
            }
            val nextOrder = (banners.maxOfOrNull { it.sortOrder } ?: 0) + 1
            val err = OwnerApi.addBanner(url, title.trim(), link.trim(), nextOrder)
            if (err != null) {
                toast(context, err)
            } else {
                toast(context, "Banner dipasang")
                pickedUri = null
                imageUrl = ""
                title = ""
                link = ""
            }
            busy = false
            loader.reload()
        }
    }

    /** Menukar urutan dengan banner di sebelahnya. Urutan dinormalkan dulu supaya penukaran selalu berefek. */
    fun move(index: Int, delta: Int) {
        val target = index + delta
        if (target !in banners.indices) return
        run({
            var err: String? = null
            banners.forEachIndexed { i, b ->
                if (err == null) {
                    val newOrder = when (i) {
                        index -> target
                        target -> index
                        else -> i
                    }
                    err = OwnerApi.setBannerOrder(b.id, newOrder)
                }
            }
            err
        })
    }

    OwnerScaffold(title = "Banner beranda", subtitle = "Tampil di paling atas Beranda; bisa digeser kalau lebih dari satu", onBack = onBack) {
        OwnerSection("Banner terpasang (${banners.size})")
        if (loader.value == null) {
            if (loader.error != null) ErrorRow(loader.error ?: "", loader::reload) else LoadingRow()
        } else if (banners.isEmpty()) {
            Text("Belum ada banner.", style = MaterialTheme.typography.bodyMedium, color = MaterialTheme.colorScheme.onSurfaceVariant)
        }
        banners.forEachIndexed { index, b ->
            GroupCard {
                AsyncImage(
                    model = b.imageUrl,
                    contentDescription = null,
                    contentScale = ContentScale.Crop,
                    modifier = Modifier
                        .fillMaxWidth()
                        .aspectRatio(8f / 3f)
                        .background(Color.White.copy(alpha = 0.05f))
                )
                Row(
                    modifier = Modifier.padding(start = 16.dp, end = 6.dp, top = 6.dp, bottom = 6.dp),
                    verticalAlignment = Alignment.CenterVertically
                ) {
                    Column(modifier = Modifier.weight(1f)) {
                        Text(
                            "${index + 1}. ${b.title ?: "(tanpa judul)"}",
                            style = MaterialTheme.typography.bodyMedium,
                            fontWeight = FontWeight.Medium,
                            maxLines = 1,
                            overflow = TextOverflow.Ellipsis
                        )
                        Text(
                            if (b.active) (b.linkUrl ?: "Tanpa tautan") else "DISEMBUNYIKAN",
                            style = MaterialTheme.typography.bodySmall,
                            color = if (b.active) MaterialTheme.colorScheme.onSurfaceVariant else OwnerWarn,
                            maxLines = 1,
                            overflow = TextOverflow.Ellipsis
                        )
                    }
                    IconButton(onClick = { move(index, -1) }, enabled = !busy && index > 0) {
                        Icon(Icons.Rounded.ArrowUpward, contentDescription = "Naikkan")
                    }
                    IconButton(onClick = { move(index, 1) }, enabled = !busy && index < banners.lastIndex) {
                        Icon(Icons.Rounded.ArrowDownward, contentDescription = "Turunkan")
                    }
                    IconButton(onClick = { deleteTarget = b }, enabled = !busy) {
                        Icon(Icons.Rounded.Delete, contentDescription = "Hapus", tint = OwnerDanger)
                    }
                }
                OwnerSwitchRow(
                    title = "Tampilkan",
                    subtitle = "Matikan untuk menyembunyikan tanpa menghapus",
                    checked = b.active,
                    enabled = !busy,
                    onChange = { on -> run({ OwnerApi.setBannerActive(b.id, on) }) }
                )
            }
        }

        OwnerSection("Tambah banner")
        val preview: Any? = pickedUri ?: imageUrl.trim().takeIf { it.startsWith("http") }
        if (preview != null) {
            AsyncImage(
                model = preview,
                contentDescription = null,
                contentScale = ContentScale.Crop,
                modifier = Modifier
                    .fillMaxWidth()
                    .aspectRatio(8f / 3f)
                    .clip(RoundedCornerShape(18.dp))
                    .background(Color.White.copy(alpha = 0.05f))
            )
        }
        PillTextButton(
            if (pickedUri == null) "Pilih gambar dari galeri" else "Ganti gambar",
            onClick = { picker.launch(PickVisualMediaRequest(ActivityResultContracts.PickVisualMedia.ImageOnly)) },
            modifier = Modifier.fillMaxWidth(),
            primary = false,
            enabled = !busy
        )
        OwnerField(
            imageUrl, { imageUrl = it.trim(); if (it.isNotBlank()) pickedUri = null },
            "…atau alamat gambar (https://…)",
            hint = "Ukuran terbaik 1600×600 (rasio 8:3) supaya tidak terpotong. GIF juga bisa."
        )
        OwnerField(title, { title = it.take(60) }, "Judul (opsional)")
        OwnerField(link, { link = it.trim() }, "Tautan saat banner diketuk (opsional)")
        PillTextButton(
            if (busy) "Memproses…" else "Pasang banner",
            onClick = { add() },
            modifier = Modifier.fillMaxWidth(),
            enabled = !busy
        )
    }

    deleteTarget?.let { b ->
        JimzDialog(
            onDismiss = { deleteTarget = null },
            icon = Icons.Rounded.Image,
            title = "Hapus banner ini?",
            message = "Banner langsung hilang dari beranda semua pengguna.",
            confirmLabel = "Hapus",
            destructive = true,
            onConfirm = {
                deleteTarget = null
                run({ OwnerApi.deleteBanner(b.id) }, "Banner dihapus")
            }
        )
    }
}

// ============================================================================ PENGUMUMAN

private val ExpiryLabels = listOf("Sampai kutarik", "Hari ini", "3 hari", "7 hari", "30 hari")
private val ExpiryDays = listOf(-1L, 0L, 3L, 7L, 30L)

@Composable
internal fun AnnouncementsPage(onBack: () -> Unit) {
    val context = LocalContext.current
    val scope = rememberCoroutineScope()
    val loader = rememberLoader { OwnerApi.announcements() }
    val items = loader.value.orEmpty()

    var title by remember { mutableStateOf("") }
    var message by remember { mutableStateOf("") }
    var url by remember { mutableStateOf("") }
    var urlLabel by remember { mutableStateOf("") }
    var interrupt by remember { mutableStateOf(true) }
    var expiryIndex by remember { mutableStateOf(0) }
    var confirmSend by remember { mutableStateOf(false) }
    var busy by remember { mutableStateOf(false) }

    fun send() {
        if (busy) return
        scope.launch {
            busy = true
            val days = ExpiryDays[expiryIndex]
            val expires = if (days < 0L) null else java.time.LocalDate.now().plusDays(days).toString()
            val r = OwnerApi.sendAnnouncement(title.trim(), message.trim(), url.trim(), urlLabel.trim(), interrupt, expires)
            if (r.ok) {
                // Pengirim tidak perlu melihat pop-up pengumumannya sendiri.
                r.value?.takeIf { it.isNotEmpty() }?.let { AppGraph.remote.markSeen(it) }
                toast(context, "Pengumuman terkirim ke semua pengguna")
                title = ""
                message = ""
                url = ""
                urlLabel = ""
            } else {
                toast(context, r.error ?: "Gagal mengirim")
            }
            busy = false
            loader.reload()
        }
    }

    fun act(block: suspend () -> String?) {
        if (busy) return
        scope.launch {
            busy = true
            val err = block()
            if (err != null) toast(context, err)
            busy = false
            loader.reload()
        }
    }

    OwnerScaffold(title = "Pengumuman", subtitle = "Muncul langsung di HP pengguna, tanpa menunggu aplikasi dibuka", onBack = onBack) {
        OwnerSection("Buat pengumuman")
        OwnerField(title, { title = it.take(80) }, "Judul")
        OwnerField(message, { message = it.take(500) }, "Isi pengumuman", singleLine = false, minLines = 4)
        OwnerField(url, { url = it.trim() }, "Tautan (opsional)")
        if (url.isNotBlank()) OwnerField(urlLabel, { urlLabel = it.take(24) }, "Tulisan tombol tautan", hint = "Contoh: Buka, Lihat, Unduh")
        GroupCard {
            OwnerSwitchRow(
                title = "Hentikan musik pengguna",
                subtitle = if (interrupt) "Musik berhenti & pemutar ditutup saat pengumuman muncul"
                else "Musik tetap jalan, pengumuman tampil di atasnya",
                checked = interrupt,
                onChange = { interrupt = it }
            )
        }
        Text("Tampil sampai", style = MaterialTheme.typography.labelMedium, color = Color.White.copy(alpha = 0.6f), modifier = Modifier.padding(start = 6.dp))
        ChipRow(ExpiryLabels, expiryIndex) { expiryIndex = it }
        PillTextButton(
            if (busy) "Mengirim…" else "Kirim sekarang",
            onClick = { confirmSend = true },
            modifier = Modifier.fillMaxWidth(),
            enabled = !busy && title.isNotBlank() && message.isNotBlank()
        )

        OwnerSection("Riwayat")
        if (loader.value == null) {
            if (loader.error != null) ErrorRow(loader.error ?: "", loader::reload) else LoadingRow()
        } else if (items.isEmpty()) {
            Text("Belum ada pengumuman.", style = MaterialTheme.typography.bodyMedium, color = MaterialTheme.colorScheme.onSurfaceVariant)
        }
        items.forEach { a ->
            GroupCard {
                Row(
                    modifier = Modifier.padding(start = 16.dp, end = 6.dp, top = 10.dp, bottom = 2.dp),
                    verticalAlignment = Alignment.CenterVertically
                ) {
                    Column(modifier = Modifier.weight(1f)) {
                        Text(a.title, style = MaterialTheme.typography.bodyLarge, fontWeight = FontWeight.SemiBold, maxLines = 1, overflow = TextOverflow.Ellipsis)
                        Text(
                            TimeUtil.format(a.createdAt) + if (a.interrupt) " · hentikan musik" else "",
                            style = MaterialTheme.typography.labelSmall,
                            color = MaterialTheme.colorScheme.onSurfaceVariant
                        )
                    }
                    IconButton(onClick = { act { OwnerApi.deleteAnnouncement(a.id) } }, enabled = !busy) {
                        Icon(Icons.Rounded.Delete, contentDescription = "Hapus", tint = OwnerDanger)
                    }
                }
                Text(
                    a.message,
                    style = MaterialTheme.typography.bodySmall,
                    color = Color.White.copy(alpha = 0.75f),
                    maxLines = 3,
                    overflow = TextOverflow.Ellipsis,
                    modifier = Modifier.padding(horizontal = 16.dp)
                )
                OwnerSwitchRow(
                    title = "Aktif",
                    subtitle = "Matikan untuk menarik pengumuman ini dari semua pengguna",
                    checked = a.active,
                    enabled = !busy,
                    onChange = { on -> act { OwnerApi.setAnnouncementActive(a.id, on) } }
                )
            }
        }
    }

    if (confirmSend) {
        JimzDialog(
            onDismiss = { confirmSend = false },
            icon = Icons.AutoMirrored.Rounded.Send,
            title = "Kirim ke semua pengguna?",
            message = if (interrupt) "Pengumuman langsung muncul di HP mereka dan musik yang sedang diputar akan berhenti."
            else "Pengumuman langsung muncul di HP mereka. Musik tetap berjalan.",
            confirmLabel = "Kirim",
            onConfirm = {
                confirmSend = false
                send()
            }
        )
    }
}

// ============================================================================ DONASI & POPUP

@Composable
internal fun DonatePage(onBack: () -> Unit) {
    val context = LocalContext.current
    val scope = rememberCoroutineScope()
    val cfgLoader = rememberLoader { OwnerApi.config() }
    val linksLoader = rememberLoader { OwnerApi.donationLinks() }
    val donationsLoader = rememberLoader { OwnerApi.donations() }
    val donations = donationsLoader.value.orEmpty()
    var dUser by remember { mutableStateOf("") }
    var dName by remember { mutableStateOf("") }
    var dAmount by remember { mutableStateOf("") }
    var dNote by remember { mutableStateOf("") }
    val cfg = cfgLoader.value
    val links = linksLoader.value.orEmpty()

    var title by remember { mutableStateOf("") }
    var message by remember { mutableStateOf("") }
    var newLabel by remember { mutableStateOf("") }
    var newUrl by remember { mutableStateOf("") }
    var busy by remember { mutableStateOf(false) }
    var confirmPush by remember { mutableStateOf(false) }

    LaunchedEffect(cfg?.let { true }) {
        if (cfg != null) {
            title = cfg.donationTitle
            message = cfg.donationMessage
        }
    }

    fun act(okText: String? = null, block: suspend () -> String?) {
        if (busy) return
        scope.launch {
            busy = true
            val err = block()
            if (err != null) toast(context, err) else if (okText != null) toast(context, okText)
            busy = false
            cfgLoader.reload()
            linksLoader.reload()
            donationsLoader.reload()
        }
    }

    OwnerScaffold(title = "Donasi & popup", subtitle = "Popup hanya muncul di Beranda dan tidak menghentikan musik", onBack = onBack) {
        if (cfg == null) {
            if (cfgLoader.error != null) ErrorRow(cfgLoader.error ?: "", cfgLoader::reload) else LoadingRow()
            return@OwnerScaffold
        }

        OwnerSection("Popup donasi realtime")
        GroupCard {
            OwnerSwitchRow(
                title = "Popup diaktifkan",
                subtitle = if (cfg.popupEnabled) "Aktif: tombol \"Kirim popup sekarang\" bisa dipakai"
                else "Mati: tidak ada popup yang muncul",
                checked = cfg.popupEnabled,
                enabled = !busy,
                onChange = { on -> act { OwnerApi.patchConfig(JSONObject().put("donation_popup_enabled", on)) } }
            )
        }
        PillTextButton(
            "Kirim popup sekarang",
            onClick = { confirmPush = true },
            modifier = Modifier.fillMaxWidth(),
            enabled = !busy && links.isNotEmpty()
        )
        if (links.isEmpty()) {
            Text(
                "Tambahkan minimal satu tautan donasi di bawah dulu.",
                style = MaterialTheme.typography.bodySmall,
                color = OwnerWarn
            )
        }

        OwnerSection("Catat donasi (Peringkat Donatur)")
        Text(
            "Catat donasi yang sudah masuk. Isi username kalau donaturnya punya akun Aoto, supaya peringkatnya atas nama akun itu.",
            style = MaterialTheme.typography.bodySmall,
            color = MaterialTheme.colorScheme.onSurfaceVariant
        )
        OwnerField(dUser, { dUser = it.trim().removePrefix("@").take(30) }, "Username Aoto (opsional)")
        OwnerField(dName, { dName = it.take(40) }, "Nama donatur (kalau tanpa akun)")
        OwnerField(dAmount, { dAmount = it.filter(Char::isDigit).take(12) }, "Nominal (Rp)", hint = "Hanya angka, mis. 25000")
        OwnerField(dNote, { dNote = it.take(120) }, "Catatan (opsional)")
        PillTextButton(
            "Catat donasi",
            onClick = {
                act("Donasi dicatat") {
                    val err = OwnerApi.addDonation(dUser, dName, dAmount.toLongOrNull() ?: 0L, dNote)
                    if (err == null) {
                        dUser = ""
                        dName = ""
                        dAmount = ""
                        dNote = ""
                    }
                    err
                }
            },
            modifier = Modifier.fillMaxWidth(),
            enabled = !busy && (dAmount.toLongOrNull() ?: 0L) > 0L && (dUser.isNotBlank() || dName.isNotBlank())
        )
        donations.forEach { d ->
            GroupCard {
                Row(
                    modifier = Modifier.padding(start = 16.dp, end = 6.dp, top = 6.dp, bottom = 6.dp),
                    verticalAlignment = Alignment.CenterVertically
                ) {
                    Column(modifier = Modifier.weight(1f)) {
                        Text(
                            d.name + if (d.linked) "  ✓ akun" else "",
                            style = MaterialTheme.typography.bodyLarge,
                            fontWeight = FontWeight.Medium,
                            maxLines = 1,
                            overflow = TextOverflow.Ellipsis
                        )
                        Text(
                            com.jimz.jimztune.data.stats.formatRupiah(d.amount) + if (d.note.isNotBlank()) " · ${d.note}" else "",
                            style = MaterialTheme.typography.bodySmall,
                            color = MaterialTheme.colorScheme.onSurfaceVariant,
                            maxLines = 1,
                            overflow = TextOverflow.Ellipsis
                        )
                    }
                    IconButton(onClick = { act { OwnerApi.deleteDonation(d.id) } }, enabled = !busy) {
                        Icon(Icons.Rounded.Delete, contentDescription = "Hapus", tint = OwnerDanger)
                    }
                }
            }
        }

        OwnerSection("Teks donasi")
        GroupCard {
            OwnerSwitchRow(
                title = "Kartu donasi di Beranda & Setelan",
                subtitle = "Kartu \"Suka Aoto?\" di akhir Beranda",
                checked = cfg.donationEnabled,
                enabled = !busy,
                onChange = { on -> act { OwnerApi.patchConfig(JSONObject().put("donation_enabled", on)) } }
            )
        }
        OwnerField(title, { title = it.take(60) }, "Judul")
        OwnerField(message, { message = it.take(300) }, "Pesan", singleLine = false, minLines = 3)
        PillTextButton(
            "Simpan teks",
            onClick = {
                act("Teks disimpan") {
                    OwnerApi.patchConfig(JSONObject().put("donation_title", title.trim()).put("donation_message", message.trim()))
                }
            },
            modifier = Modifier.fillMaxWidth(),
            primary = false,
            enabled = !busy && title.isNotBlank()
        )

        OwnerSection("Tautan donasi (${links.size})")
        links.forEach { l ->
            GroupCard {
                Row(
                    modifier = Modifier.padding(start = 16.dp, end = 6.dp, top = 6.dp, bottom = 6.dp),
                    verticalAlignment = Alignment.CenterVertically
                ) {
                    Column(modifier = Modifier.weight(1f)) {
                        Text(l.label, style = MaterialTheme.typography.bodyLarge, fontWeight = FontWeight.Medium)
                        Text(
                            l.url,
                            style = MaterialTheme.typography.bodySmall,
                            color = MaterialTheme.colorScheme.onSurfaceVariant,
                            maxLines = 1,
                            overflow = TextOverflow.Ellipsis
                        )
                    }
                    IconButton(onClick = { act { OwnerApi.deleteDonationLink(l.id) } }, enabled = !busy) {
                        Icon(Icons.Rounded.Delete, contentDescription = "Hapus", tint = OwnerDanger)
                    }
                }
            }
        }
        OwnerField(newLabel, { newLabel = it.take(24) }, "Nama tombol (mis. Saweria)")
        OwnerField(newUrl, { newUrl = it.trim() }, "Alamat (https://…)")
        PillTextButton(
            "Tambah tautan",
            onClick = {
                act("Tautan ditambahkan") {
                    val err = OwnerApi.addDonationLink(newLabel.trim(), newUrl.trim(), (links.size + 1))
                    if (err == null) {
                        newLabel = ""
                        newUrl = ""
                    }
                    err
                }
            },
            modifier = Modifier.fillMaxWidth(),
            primary = false,
            enabled = !busy && newLabel.isNotBlank() && newUrl.startsWith("http")
        )
    }

    if (confirmPush && cfg != null) {
        JimzDialog(
            onDismiss = { confirmPush = false },
            icon = Icons.Rounded.Coffee,
            title = "Kirim popup donasi?",
            message = "Popup langsung muncul di Beranda semua pengguna yang sedang membuka aplikasi, dan muncul saat mereka membuka Beranda kalau belum. Musik mereka tidak berhenti.",
            confirmLabel = "Kirim",
            onConfirm = {
                confirmPush = false
                act("Popup dikirim") { OwnerApi.pushDonatePopup(cfg.popupNonce) }
            }
        )
    }
}
