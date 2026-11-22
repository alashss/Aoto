package com.jimz.jimztune.data.remote

import android.content.Context
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.setValue
import androidx.core.content.pm.PackageInfoCompat
import com.jimz.jimztune.AppConfig
import com.jimz.jimztune.data.search.Http
import kotlinx.coroutines.CancellationException
import java.text.SimpleDateFormat
import java.util.Date
import java.util.Locale

sealed class Gate {
    data object Open : Gate()
    data class Disabled(val config: RemoteConfig) : Gate()
    data class ForceUpdate(val config: RemoteConfig) : Gate()
}

/**
 * Mengambil pengaturan dari server (fungsi get_app_config) (kill switch, update, pengumuman, donasi).
 * Hasil terakhir disimpan di perangkat, jadi aplikasi yang sudah dinonaktifkan tetap terkunci walau offline.
 * Kalau belum pernah berhasil mengambil config dan jaringan mati, aplikasi tetap jalan normal.
 */
class RemoteConfigRepository(context: Context) {
    private val app = context.applicationContext
    private val prefs = app.getSharedPreferences("jimztune_remote", Context.MODE_PRIVATE)

    val versionCode: Long
    val versionName: String

    init {
        val info = app.packageManager.getPackageInfo(app.packageName, 0)
        versionCode = PackageInfoCompat.getLongVersionCode(info)
        versionName = info.versionName ?: "?"
    }

    var config by mutableStateOf<RemoteConfig?>(loadCached())
        private set
    var checking by mutableStateOf(false)
        private set
    var lastError by mutableStateOf<String?>(null)
        private set

    private var seen by mutableStateOf(prefs.getStringSet("seen", emptySet())?.toSet().orEmpty())
    private var dismissedUpdate by mutableStateOf(prefs.getLong("dismissed_update", 0L))
    private var lastFetch = 0L

    /** True kalau URL dan anon key server sudah dikonfigurasi saat build (lihat README). */
    val configured: Boolean
        get() = !AppConfig.CLOUD_URL.contains("GANTI_") && !AppConfig.CLOUD_KEY.contains("GANTI_")

    fun gate(): Gate {
        val c = config ?: return Gate.Open
        if (!c.appEnabled) return Gate.Disabled(c)
        if (c.minVersionCode > versionCode) return Gate.ForceUpdate(c)
        return Gate.Open
    }

    /** Update opsional yang belum pernah ditolak pengguna (untuk dialog otomatis). */
    fun pendingUpdate(): RemoteConfig? =
        config?.takeIf { it.latestVersionCode > versionCode && it.latestVersionCode > dismissedUpdate }

    /** Update yang tersedia, tanpa memperhatikan apakah pernah ditolak (untuk tombol "Cek pembaruan"). */
    fun availableUpdate(): RemoteConfig? = config?.takeIf { it.latestVersionCode > versionCode }

    fun dismissUpdate(latestCode: Long) {
        dismissedUpdate = latestCode
        prefs.edit().putLong("dismissed_update", latestCode).apply()
    }

    /** Pesan khusus untuk akun ini (dikirim owner ke satu pengguna). Disimpan supaya tetap ada saat offline. */
    var personal by mutableStateOf<List<Announcement>>(loadPersonal())
        private set

    fun activeAnnouncements(): List<Announcement> =
        (config?.announcements.orEmpty() + personal).filter { it.isActive() }

    private fun seenKey(a: Announcement): String =
        if (a.repeatDaily) a.id + "@" + SimpleDateFormat("yyyy-MM-dd", Locale.US).format(Date()) else a.id

    fun isSeen(id: String): Boolean {
        val a = activeAnnouncements().firstOrNull { it.id == id } ?: return id in seen
        return seenKey(a) in seen
    }

    fun unreadCount(): Int = activeAnnouncements().count { seenKey(it) !in seen }

    fun pendingAnnouncement(): Announcement? = activeAnnouncements().firstOrNull { seenKey(it) !in seen }

    fun markSeen(id: String) {
        val a = activeAnnouncements().firstOrNull { it.id == id }
        val key = if (a != null) seenKey(a) else id
        // Buang catatan "sudah dibaca" harian yang sudah lewat supaya tidak menumpuk.
        val today = SimpleDateFormat("yyyy-MM-dd", Locale.US).format(Date())
        seen = seen.filter { !it.contains("@") || it.endsWith("@$today") }.toSet() + key
        prefs.edit().putStringSet("seen", seen).apply()
    }

    /** Mengambil pesan pribadi milik akun yang sedang masuk. Tanpa login: kosong. */
    suspend fun refreshPersonal() {
        if (!configured) return
        val token = com.jimz.jimztune.AppGraph.auth.validToken()
        if (token == null) {
            if (personal.isNotEmpty()) { personal = emptyList(); prefs.edit().remove("personal").apply() }
            return
        }
        try {
            val text = Http.postJson(
                AppConfig.CLOUD_URL.trimEnd('/') + "/rest/v1/rpc/my_messages", "{}",
                mapOf("apikey" to AppConfig.CLOUD_KEY, "Authorization" to "Bearer $token")
            )
            personal = parseAnnouncements(text)
            prefs.edit().putString("personal", text).apply()
        } catch (e: CancellationException) {
            throw e
        } catch (_: Exception) {
            // Biarkan pesan tersimpan; coba lagi pada pengambilan berikutnya.
        }
    }

    private fun loadPersonal(): List<Announcement> = try {
        prefs.getString("personal", null)?.let { parseAnnouncements(it) }.orEmpty()
    } catch (_: Exception) {
        emptyList()
    }

    val donationAvailable: Boolean
        get() = config?.donation?.let { it.enabled && it.links.isNotEmpty() } == true

    /** Banner aktif di bagian atas Beranda (urutannya diatur owner). */
    val banners: List<Banner> get() = config?.banners.orEmpty()

    /**
     * Apakah fitur [key] boleh dipakai (search, downloads, lyrics, import_playlist, signup).
     * Owner selalu boleh, supaya bisa menguji fitur yang sedang dimatikan untuk user.
     */
    fun featureOn(key: String): Boolean =
        com.jimz.jimztune.AppGraph.account.isOwner || config?.features?.get(key) != false

    /** Status mode perbaikan Beranda. */
    val homeMaintenance: HomeMaintenance get() = config?.homeMaintenance ?: HomeMaintenance()

    private var popupSeen by mutableStateOf(prefs.getLong("popup_seen", 0L))

    /** True kalau owner baru saja mengirim popup donasi yang belum pernah ditampilkan di perangkat ini. */
    fun pendingDonatePopup(): Boolean {
        val d = config?.donation ?: return false
        return d.popupEnabled && d.links.isNotEmpty() && d.popupNonce > popupSeen
    }

    fun markDonatePopupSeen() {
        val n = config?.donation?.popupNonce ?: return
        popupSeen = n
        prefs.edit().putLong("popup_seen", n).apply()
    }

    /** Mengambil config terbaru. Tanpa [force], dibatasi sekali per 3 menit. Mengembalikan true kalau berhasil. */
    suspend fun refresh(force: Boolean = false): Boolean {
        if (!configured) return false
        val now = System.currentTimeMillis()
        if (!force && now - lastFetch < MIN_INTERVAL_MS) return true
        checking = true
        try {
            val text = Http.postJson(
                AppConfig.CLOUD_URL.trimEnd('/') + "/rest/v1/rpc/get_app_config",
                "{}",
                mapOf(
                    "apikey" to AppConfig.CLOUD_KEY,
                    "Authorization" to "Bearer " + AppConfig.CLOUD_KEY
                )
            )
            val parsed = parseRemoteConfig(text)
            prefs.edit().putString("cache", text).apply()
            config = parsed
            lastFetch = now
            lastError = null
            refreshPersonal()
            return true
        } catch (e: CancellationException) {
            throw e
        } catch (e: Exception) {
            lastError = e.message ?: e.javaClass.simpleName
            return false
        } finally {
            checking = false
        }
    }

    private fun loadCached(): RemoteConfig? = try {
        prefs.getString("cache", null)?.let { parseRemoteConfig(it) }
    } catch (_: Exception) {
        null
    }

    private companion object {
        const val MIN_INTERVAL_MS = 3 * 60_000L
    }
}
