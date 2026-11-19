package com.jimz.jimztune.data.cloud

import android.content.Context
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.setValue
import com.jimz.jimztune.AppGraph
import kotlinx.coroutines.CoroutineScope
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.Job
import kotlinx.coroutines.NonCancellable
import kotlinx.coroutines.SupervisorJob
import kotlinx.coroutines.delay
import kotlinx.coroutines.launch
import kotlinx.coroutines.sync.Mutex
import kotlinx.coroutines.sync.withLock
import kotlinx.coroutines.withContext
import org.json.JSONArray
import org.json.JSONObject
import com.jimz.jimztune.data.stats.PlayLog
import com.jimz.jimztune.data.stats.Streak
import com.jimz.jimztune.ui.theme.AppTheme
import java.io.File

sealed interface SyncStatus {
    data object Idle : SyncStatus
    data object Syncing : SyncStatus
    data class Done(val atMs: Long) : SyncStatus
    data class Failed(val message: String) : SyncStatus
}

/**
 * Sinkronisasi favorit, playlist, riwayat putar, riwayat pencarian, dan kualitas audio ke tabel `user_data`.
 *
 * Aturan sederhana (cukup untuk satu orang di satu-dua perangkat):
 *  - Login pertama di perangkat ini (mis. habis pasang ulang): GABUNGKAN data lokal + cloud, lalu unggah.
 *  - Ada perubahan lokal yang belum terkirim: perangkat ini menang, unggah.
 *  - Tidak ada perubahan lokal tapi cloud lebih baru (diubah dari perangkat lain): ambil dari cloud.
 * Setiap perubahan lokal otomatis diunggah ±2,5 detik kemudian. Kalau offline, ditandai dan dicoba lagi nanti.
 */
class CloudSync(context: Context) {
    private val prefs = context.applicationContext.getSharedPreferences("jimztune_sync", Context.MODE_PRIVATE)
    private val scope = CoroutineScope(SupervisorJob() + Dispatchers.Default)
    private val lock = Mutex()
    private var debounce: Job? = null
    private var statsJob: Job? = null
    private var lastCheck = 0L

    @Volatile
    private var applying = false

    var status by mutableStateOf<SyncStatus>(
        prefs.getLong("last_ok", 0L).let { if (it > 0) SyncStatus.Done(it) else SyncStatus.Idle }
    )
        private set

    /** Dipanggil setiap data lokal berubah (favorit, playlist, riwayat, kualitas audio). Aman dipanggil dari thread mana pun. */
    fun markDirty() {
        if (applying || !AppGraph.auth.isLoggedIn) return
        prefs.edit().putBoolean("dirty", true).apply()
        debounce?.cancel()
        debounce = scope.launch {
            delay(2500)
            withContext(NonCancellable) { syncNow() }
        }
    }

    /**
     * Dipanggil setiap catatan dengar / streak berubah. Memakai jeda tetap (bukan debounce) supaya tetap terunggah
     * walau musik diputar terus-menerus: paling lambat ±60 detik, atau langsung kalau [urgent].
     */
    fun markStatsDirty(urgent: Boolean = false) {
        if (applying || !AppGraph.auth.isLoggedIn) return
        prefs.edit().putBoolean("stats_dirty", true).apply()
        if (statsJob?.isActive == true && !urgent) return
        statsJob?.cancel()
        statsJob = scope.launch {
            delay(if (urgent) 1500 else 60_000)
            withContext(NonCancellable) { syncNow() }
        }
    }

    /** Layanan pemutar berhenti: kirim catatan dengar terakhir sekarang. */
    fun flushStats() {
        if (!AppGraph.auth.isLoggedIn || !prefs.getBoolean("stats_dirty", false)) return
        statsJob?.cancel()
        statsJob = scope.launch { withContext(NonCancellable) { syncNow() } }
    }

    /** Mengosongkan semua data lokal milik akun (dipakai saat keluar akun dan saat HP berganti pemilik). */
    private fun clearLocalData() {
        applying = true
        try {
            AppGraph.library.clearAll()
            AppGraph.history.clear()
            AppGraph.searchHistory.clear()
            AppGraph.profile.clear()
            PlayLog.clear()
            Streak.clear()
        } finally {
            applying = false
        }
    }

    fun onLogin() {
        scope.launch {
            AppGraph.profile.refresh()
            AppGraph.account.refresh()
            AppGraph.listen.refresh()
            // Sambungkan ulang realtime supaya ikut memantau status akun yang baru masuk.
            AppGraph.realtime.restart()
            syncNow()
        }
    }

    /** Dipanggil saat aplikasi dibuka: sinkron kalau sudah lewat 90 detik dari pengecekan terakhir. */
    fun syncIfDue() {
        if (!AppGraph.auth.isLoggedIn) return
        val now = System.currentTimeMillis()
        if (now - lastCheck < 90_000L) return
        lastCheck = now
        scope.launch {
            AppGraph.profile.refresh()
            AppGraph.account.refresh()
            syncNow()
        }
    }

    fun syncManually() {
        scope.launch {
            AppGraph.profile.refresh()
            AppGraph.account.refresh()
            syncNow()
        }
    }

    /** Dipanggil setelah keluar akun: kosongkan data lokal supaya tidak bocor ke akun lain di HP ini. */
    fun onLogout() {
        debounce?.cancel()
        statsJob?.cancel()
        clearLocalData()
        prefs.edit().clear().apply()
        status = SyncStatus.Idle
        AppGraph.account.clear()
        AppGraph.listen.clear()
        AppGraph.realtime.restart()
    }

    suspend fun syncNow(): Boolean = lock.withLock {
        val auth = AppGraph.auth
        val session = auth.session ?: return@withLock false
        val token = auth.validToken() ?: run {
            status = SyncStatus.Failed("Sesi habis atau tidak ada koneksi")
            return@withLock false
        }
        status = SyncStatus.Syncing
        try {
            val get = CloudApi.json("GET", "/rest/v1/user_data?user_id=eq.${session.userId}&select=*", token = token)
            if (!get.ok) {
                com.jimz.jimztune.util.UserError.log("Sinkron", "GET ${get.code}: ${get.body.take(200)}")
                status = SyncStatus.Failed(CloudApi.errorText(get))
                return@withLock false
            }
            val rows = JSONArray(get.body)
            val cloud = rows.optJSONObject(0)
            val owner = prefs.getString("owner", null)
            val dirty = prefs.getBoolean("dirty", false)
            val knownVersion = prefs.getString("version", null)

            when {
                cloud == null -> {
                    if (owner != null && owner != session.userId) clearLocalData()
                    push(token, session.userId)
                }
                owner != session.userId -> {
                    // Data lokal milik akun LAIN (mis. sesi lama habis lalu masuk dengan akun berbeda): jangan dicampur.
                    if (owner != null) clearLocalData()
                    apply(cloud, merge = true)
                    push(token, session.userId)
                }
                dirty -> push(token, session.userId)
                cloud.optString("updated_at") != knownVersion -> {
                    apply(cloud, merge = false)
                    prefs.edit().putString("version", cloud.optString("updated_at")).apply()
                }
            }
            if (!syncStats(token, session.userId)) {
                status = SyncStatus.Failed("Streak dan rekap belum bisa disimpan")
                return@withLock false
            }
            val now = System.currentTimeMillis()
            prefs.edit().putLong("last_ok", now).putString("owner", session.userId).apply()
            if (status is SyncStatus.Syncing) status = SyncStatus.Done(now)
            true
        } catch (e: Exception) {
            if (e is kotlinx.coroutines.CancellationException) throw e
            com.jimz.jimztune.util.UserError.log("Sinkron", "${e.javaClass.simpleName}: ${e.message}")
            status = SyncStatus.Failed(e.message ?: "Gagal sinkron")
            false
        }
    }

    /**
     * Streak + rekap dengar <-> tabel `listening_days` / `streak_state` (lewat fungsi sync_listening).
     * Penggabungan = nilai terbesar per lagu per hari, jadi aman diulang. Mengembalikan false hanya kalau gagal karena jaringan / server,
     * supaya keluar akun tidak menghapus data yang belum tersimpan. Kalau SQL tahap 26 belum dijalankan (404), dilewati tanpa menghalangi apa pun.
     */
    private suspend fun syncStats(token: String, userId: String): Boolean {
        val firstTime = prefs.getString("stats_owner", null) != userId
        val dirty = firstTime || prefs.getBoolean("stats_dirty", false)
        val since = if (firstTime) null else prefs.getString("stats_since", null)
        prefs.edit().putBoolean("stats_dirty", false).apply()
        try {
            val payload = if (dirty) PlayLog.exportDays(if (firstTime) null else 21) else JSONObject()
            val recovered = JSONArray().also { a -> Streak.recoveredList().forEach { a.put(it) } }
            val keys = payload.keys().asSequence().toList().sorted()
            // Unggahan pertama bisa besar: kirim bertahap (60 hari sekali), hanya yang terakhir meminta data balik.
            val chunks = keys.chunked(60)
            for (i in 0 until chunks.size - 1) {
                val part = JSONObject().also { o -> chunks[i].forEach { k -> o.put(k, payload.get(k)) } }
                val r = CloudApi.json("POST", "/rest/v1/rpc/sync_listening", JSONObject().put("p_days", part).put("p_pull", false).toString(), token)
                if (!r.ok && !statsUnavailable(r)) return failStats(r)
                if (statsUnavailable(r)) return true
            }
            val last = JSONObject().also { o -> chunks.lastOrNull()?.forEach { k -> o.put(k, payload.get(k)) } }
            val body = JSONObject()
                .put("p_days", last)
                .put("p_recovered", recovered)
                .put("p_enabled", Streak.isEnabled())
                .put("p_pull", true)
            if (since != null) body.put("p_since", since)
            val r = CloudApi.json("POST", "/rest/v1/rpc/sync_listening", body.toString(), token)
            if (statsUnavailable(r)) return true
            if (!r.ok) return failStats(r)

            val o = JSONObject(r.body)
            PlayLog.mergeFrom(o.optJSONObject("days"))
            val rec = o.optJSONArray("recovered")
            Streak.mergeState(
                (0 until (rec?.length() ?: 0)).mapNotNull { rec?.optString(it)?.takeIf { s -> s.isNotBlank() } },
                if (o.has("enabled")) o.optBoolean("enabled", true) else null,
                applyEnabled = firstTime || !dirty
            )
            // Sisakan tumpang tindih 15 detik; aman karena penggabungan idempotent.
            val st = o.optString("server_time")
            val sinceNext = runCatching { java.time.OffsetDateTime.parse(st).minusSeconds(15).toString() }.getOrNull()
            val ed = prefs.edit().putString("stats_owner", userId)
            if (sinceNext != null) ed.putString("stats_since", sinceNext)
            ed.apply()
            return true
        } catch (e: Exception) {
            if (e is kotlinx.coroutines.CancellationException) throw e
            prefs.edit().putBoolean("stats_dirty", true).apply()
            com.jimz.jimztune.util.UserError.log("Sinkron", "STATS ${e.javaClass.simpleName}: ${e.message}")
            return false
        }
    }

    private fun statsUnavailable(r: CloudApi.Resp): Boolean {
        if (r.code != 404) return false
        com.jimz.jimztune.util.UserError.log("Sinkron", "sync_listening belum ada: fungsi sync_listening belum dipasang di server")
        return true
    }

    private fun failStats(r: CloudApi.Resp): Boolean {
        prefs.edit().putBoolean("stats_dirty", true).apply()
        com.jimz.jimztune.util.UserError.log("Sinkron", "STATS ${r.code}: ${r.body.take(200)}")
        return false
    }

    private suspend fun push(token: String, userId: String) {
        // Tandai bersih SEBELUM mengambil data: perubahan yang masuk selama unggahan akan menandai kotor lagi.
        prefs.edit().putBoolean("dirty", false).apply()
        val body = JSONObject()
            .put("user_id", userId)
            .put("library", AppGraph.library.exportCloud())
            .put("history", AppGraph.history.exportJson())
            .put("search_history", AppGraph.searchHistory.exportJson())
            .put("settings", JSONObject().put("quality", AppGraph.settings.quality.name).put("theme", AppTheme.exportJson()))
        val r = CloudApi.json(
            "POST", "/rest/v1/user_data?on_conflict=user_id", body.toString(), token,
            mapOf("Prefer" to "resolution=merge-duplicates,return=representation")
        )
        if (!r.ok) {
            prefs.edit().putBoolean("dirty", true).apply()
            com.jimz.jimztune.util.UserError.log("Sinkron", "PUSH ${r.code}: ${r.body.take(200)}")
            status = SyncStatus.Failed(CloudApi.errorText(r))
            throw java.io.IOException(CloudApi.errorText(r))
        }
        JSONArray(r.body).optJSONObject(0)?.optString("updated_at")?.takeIf { it.isNotEmpty() }?.let {
            prefs.edit().putString("version", it).apply()
        }
    }

    private fun apply(row: JSONObject, merge: Boolean) {
        applying = true
        try {
            AppGraph.library.applyCloud(row.optJSONObject("library") ?: JSONObject(), merge)
            AppGraph.history.applyCloud(row.optJSONArray("history"), merge)
            AppGraph.searchHistory.applyCloud(row.optJSONArray("search_history"), merge)
            AppGraph.settings.applyCloudQuality(row.optJSONObject("settings")?.optString("quality"))
            AppTheme.applyCloud(row.optJSONObject("settings")?.optJSONObject("theme"))
        } finally {
            applying = false
        }
    }

    /** Mengunggah sampul playlist pilihan user ke Storage supaya tidak hilang saat aplikasi dihapus. */
    fun uploadPlaylistCover(playlistId: String, file: File) {
        val session = AppGraph.auth.session ?: return
        scope.launch {
            val token = AppGraph.auth.validToken() ?: return@launch
            val bytes = try { file.readBytes() } catch (_: Exception) { return@launch }
            val path = "${session.userId}/${playlistId}_${System.currentTimeMillis()}.jpg"
            val r = CloudApi.bytes("/storage/v1/object/covers/$path", bytes, "image/jpeg", token)
            if (r.ok) AppGraph.library.setCoverUrl(playlistId, CloudApi.publicUrl("covers", path))
        }
    }
}
