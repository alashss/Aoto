package com.jimz.jimztune.ui.owner

import androidx.compose.foundation.background
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.layout.width
import androidx.compose.foundation.shape.RoundedCornerShape
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
import com.jimz.jimztune.data.owner.OwnerApi
import com.jimz.jimztune.ui.components.GroupCard
import com.jimz.jimztune.ui.components.PillTextButton
import com.jimz.jimztune.util.TimeUtil
import kotlinx.coroutines.delay
import kotlinx.coroutines.launch
import org.json.JSONObject

// ============================================================================ AKTIVITAS LIVE

private val TopDaysLabels = listOf("Hari ini", "7 hari", "30 hari")
private val TopDays = listOf(1, 7, 30)

@Composable
internal fun LivePage(onBack: () -> Unit) {
    val scope = rememberCoroutineScope()
    val recent = rememberLoader { OwnerApi.recentPlays() }
    var dayIndex by remember { mutableStateOf(1) }
    var topSongs by remember { mutableStateOf<List<com.jimz.jimztune.data.owner.TopSong>?>(null) }
    var topError by remember { mutableStateOf<String?>(null) }

    fun loadTop() {
        scope.launch {
            val r = OwnerApi.topSongs(TopDays[dayIndex])
            if (r.ok) {
                topSongs = r.value
                topError = null
            } else {
                topError = r.error
            }
        }
    }

    LaunchedEffect(dayIndex) { loadTop() }
    // Diperbarui sendiri tiap 15 detik selama halaman terbuka.
    LaunchedEffect(Unit) {
        while (true) {
            delay(15_000)
            recent.reload()
            loadTop()
        }
    }

    OwnerScaffold(title = "Aktivitas live", subtitle = "Diperbarui otomatis tiap 15 detik", onBack = onBack) {
        OwnerSection("Baru diputar")
        val plays = recent.value
        when {
            plays == null && recent.error != null -> ErrorRow(recent.error ?: "", recent::reload)
            plays == null -> LoadingRow()
            plays.isEmpty() -> Text("Belum ada lagu yang tercatat.", style = MaterialTheme.typography.bodyMedium, color = MaterialTheme.colorScheme.onSurfaceVariant)
            else -> GroupCard {
                plays.forEachIndexed { i, p ->
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
                            Text(
                                "${p.userName} · ${p.artist}",
                                style = MaterialTheme.typography.bodySmall,
                                color = MaterialTheme.colorScheme.onSurfaceVariant,
                                maxLines = 1,
                                overflow = TextOverflow.Ellipsis
                            )
                        }
                        Spacer(Modifier.width(8.dp))
                        Text(
                            TimeUtil.parse(p.playedAt)?.let { TimeUtil.ago(it) } ?: "-",
                            style = MaterialTheme.typography.labelSmall,
                            color = Color.White.copy(alpha = 0.55f)
                        )
                    }
                }
            }
        }

        OwnerSection("Lagu terpopuler")
        ChipRow(TopDaysLabels, dayIndex) { dayIndex = it }
        val top = topSongs
        when {
            top == null && topError != null -> ErrorRow(topError ?: "", { loadTop() })
            top == null -> LoadingRow()
            top.isEmpty() -> Text("Belum ada data.", style = MaterialTheme.typography.bodyMedium, color = MaterialTheme.colorScheme.onSurfaceVariant)
            else -> GroupCard {
                top.forEachIndexed { i, t ->
                    if (i > 0) OwnerDivider()
                    Row(
                        modifier = Modifier.padding(horizontal = 16.dp, vertical = 8.dp),
                        verticalAlignment = Alignment.CenterVertically
                    ) {
                        Text("${i + 1}", style = MaterialTheme.typography.titleMedium, fontWeight = FontWeight.Bold, modifier = Modifier.width(26.dp))
                        AsyncImage(
                            model = t.thumbnail,
                            contentDescription = null,
                            contentScale = ContentScale.Crop,
                            modifier = Modifier
                                .size(44.dp)
                                .clip(RoundedCornerShape(10.dp))
                                .background(Color.White.copy(alpha = 0.08f))
                        )
                        Spacer(Modifier.width(12.dp))
                        Column(modifier = Modifier.weight(1f)) {
                            Text(t.title, style = MaterialTheme.typography.bodyMedium, fontWeight = FontWeight.Medium, maxLines = 1, overflow = TextOverflow.Ellipsis)
                            Text(t.artist, style = MaterialTheme.typography.bodySmall, color = MaterialTheme.colorScheme.onSurfaceVariant, maxLines = 1, overflow = TextOverflow.Ellipsis)
                        }
                        Spacer(Modifier.width(8.dp))
                        Column(horizontalAlignment = Alignment.End) {
                            Text("${t.plays}x", style = MaterialTheme.typography.labelLarge, fontWeight = FontWeight.SemiBold)
                            Text("${t.listeners} orang", style = MaterialTheme.typography.labelSmall, color = Color.White.copy(alpha = 0.55f))
                        }
                    }
                }
            }
        }
    }
}

// ============================================================================ VERSI & UPDATE

@Composable
internal fun UpdatePage(onBack: () -> Unit) {
    val context = LocalContext.current
    val scope = rememberCoroutineScope()
    val loader = rememberLoader { OwnerApi.config() }
    val cfg = loader.value

    var latestName by remember { mutableStateOf("") }
    var latestCode by remember { mutableStateOf("") }
    var minCode by remember { mutableStateOf("") }
    var notes by remember { mutableStateOf("") }
    var pageUrl by remember { mutableStateOf("") }
    var apkUrl by remember { mutableStateOf("") }
    var busy by remember { mutableStateOf(false) }

    LaunchedEffect(cfg?.let { true }) {
        if (cfg != null) {
            latestName = cfg.latestVersionName
            latestCode = cfg.latestVersionCode.toString()
            minCode = cfg.minVersionCode.toString()
            notes = cfg.updateNotes
            pageUrl = cfg.updatePageUrl
            apkUrl = cfg.apkUrl
        }
    }

    val myCode = AppGraph.remote.versionCode
    val latest = latestCode.toLongOrNull()
    val min = minCode.toLongOrNull()
    val valid = latest != null && min != null && min <= latest

    OwnerScaffold(title = "Versi & update", subtitle = "Kamu sedang memakai v${AppGraph.remote.versionName} (kode $myCode)", onBack = onBack) {
        if (cfg == null) {
            if (loader.error != null) ErrorRow(loader.error ?: "", loader::reload) else LoadingRow()
            return@OwnerScaffold
        }
        OwnerSection("Versi terbaru")
        OwnerField(latestName, { latestName = it.take(20) }, "Nama versi (mis. 1.4.0)")
        OwnerField(
            latestCode, { latestCode = it.filter(Char::isDigit).take(9) }, "Kode versi terbaru",
            hint = "Pengguna dengan kode lebih kecil akan ditawari update (boleh dilewati)."
        )
        OwnerSection("Wajib update")
        OwnerField(
            minCode, { minCode = it.filter(Char::isDigit).take(9) }, "Kode versi minimum",
            hint = "Pengguna dengan kode di bawah ini DIPAKSA update dan tidak bisa memakai aplikasi. 0 = tidak ada paksaan."
        )
        if (min != null && latest != null && min > latest) {
            Text("Kode minimum tidak boleh lebih besar dari kode terbaru.", style = MaterialTheme.typography.bodySmall, color = OwnerDanger)
        }
        OwnerSection("Isi update")
        OwnerField(notes, { notes = it.take(500) }, "Catatan rilis", singleLine = false, minLines = 3)
        OwnerField(pageUrl, { pageUrl = it.trim() }, "Halaman unduhan (opsional)")
        OwnerField(apkUrl, { apkUrl = it.trim() }, "Alamat APK langsung (opsional)")
        PillTextButton(
            if (busy) "Menyimpan…" else "Simpan",
            onClick = {
                if (busy) return@PillTextButton
                scope.launch {
                    busy = true
                    val err = OwnerApi.patchConfig(
                        JSONObject()
                            .put("latest_version_name", latestName.trim())
                            .put("latest_version_code", latest ?: 0L)
                            .put("min_version_code", min ?: 0L)
                            .put("update_notes", notes.trim())
                            .put("update_page_url", if (pageUrl.isBlank()) JSONObject.NULL else pageUrl)
                            .put("apk_url", if (apkUrl.isBlank()) JSONObject.NULL else apkUrl)
                    )
                    toast(context, err ?: "Disimpan. Langsung berlaku untuk semua pengguna")
                    busy = false
                    loader.reload()
                }
            },
            modifier = Modifier.fillMaxWidth(),
            enabled = !busy && valid
        )
    }
}
