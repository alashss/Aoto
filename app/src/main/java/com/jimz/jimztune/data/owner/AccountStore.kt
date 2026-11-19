package com.jimz.jimztune.data.owner

import android.content.Context
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.setValue
import com.jimz.jimztune.AppGraph
import com.jimz.jimztune.data.cloud.CloudApi
import com.jimz.jimztune.util.TimeUtil
import org.json.JSONObject

/**
 * Status akun yang sedang login: apakah OWNER (didaftarkan lewat tabel `owners` di server)
 * dan apakah akun sedang di-suspend / di-ban. Hasil terakhir disimpan di perangkat,
 * jadi owner tetap dikenali dan akun yang diblokir tetap terkunci walau sedang offline.
 */
class AccountStore(context: Context) {
    private val prefs = context.applicationContext.getSharedPreferences("jimztune_account", Context.MODE_PRIVATE)

    var isOwner by mutableStateOf(prefs.getBoolean("owner", false))
        private set

    /** active | suspended | banned */
    var status by mutableStateOf(prefs.getString("status", "active") ?: "active")
        private set

    /** Batas akhir suspend (ms sejak epoch). Null = tanpa batas waktu. */
    var suspendedUntilMs by mutableStateOf(prefs.getLong("until", 0L).takeIf { it > 0 })
        private set

    var reason by mutableStateOf(prefs.getString("reason", "") ?: "")
        private set

    /** Dibaca di dalam komposisi: akun sedang tidak boleh memakai aplikasi. */
    val blocked: Boolean
        get() = when (status) {
            "banned" -> true
            "suspended" -> suspendedUntilMs?.let { it > System.currentTimeMillis() } ?: true
            else -> false
        }

    /** Mengambil status terbaru dari server. Gagal / offline = pakai catatan terakhir. */
    suspend fun refresh() {
        val token = AppGraph.auth.validToken() ?: return
        val r = CloudApi.json("POST", "/rest/v1/rpc/my_account", "{}", token)
        if (!r.ok) return
        val o = try { JSONObject(r.body) } catch (_: Exception) { return }
        val until = TimeUtil.parse(if (o.isNull("until")) null else o.optString("until"))
        isOwner = o.optBoolean("is_owner", false)
        status = o.optString("status", "active").ifBlank { "active" }
        suspendedUntilMs = until
        reason = o.optString("reason", "")
        prefs.edit()
            .putBoolean("owner", isOwner)
            .putString("status", status)
            .putLong("until", until ?: 0L)
            .putString("reason", reason)
            .apply()
    }

    /** Dipanggil saat keluar akun. */
    fun clear() {
        isOwner = false
        status = "active"
        suspendedUntilMs = null
        reason = ""
        prefs.edit().clear().apply()
    }
}
