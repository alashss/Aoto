package com.jimz.jimztune.data.owner

import com.jimz.jimztune.AppGraph
import com.jimz.jimztune.data.cloud.CloudApi
import org.json.JSONArray
import org.json.JSONObject

/** Hasil panggilan ke server: [error] kosong = berhasil. */
class Res<T>(val value: T?, val error: String? = null) {
    val ok: Boolean get() = error == null && value != null
}

data class AdminStats(val users: Int, val online: Int, val active24h: Int, val new7d: Int, val playsToday: Int, val blocked: Int)

data class LivePlay(val userName: String, val title: String, val artist: String, val thumbnail: String?, val playedAt: String)
data class TopSong(val title: String, val artist: String, val thumbnail: String?, val plays: Int, val listeners: Int)

data class AdminUser(
    val id: String,
    val email: String,
    val displayName: String,
    val username: String?,
    val avatarUrl: String?,
    val createdAt: String?,
    val status: String,
    val until: String?,
    val reason: String,
    val lastSeen: String?,
    val online: Boolean,
    val isOwner: Boolean,
    val level: Int = 1,
    val listenSeconds: Long = 0,
    val plays: Int = 0
)

data class AdminOverview(
    val totalUsers: Int, val totalListenSeconds: Long, val totalPlays: Int, val playsToday: Int,
    val listenTodaySeconds: Long, val active7d: Int, val avgLevel: Double, val topArtists: List<Pair<String, Int>>
)
data class TopListener(val userId: String, val name: String, val username: String?, val avatarUrl: String?, val level: Int, val listenSeconds: Long, val plays: Int)
data class UserStatsRow(val totalSeconds: Long, val level: Int, val plays: Int, val todaySeconds: Long, val weekSeconds: Long, val activeDays: Int, val topArtist: String?)

data class PlayEvent(val songId: String, val title: String, val artist: String, val thumbnail: String?, val playedAt: String)
data class SessionRow(val startedAt: String, val lastSeenAt: String, val appVersion: String)
data class UserActivity(val plays: List<PlayEvent>, val sessions: List<SessionRow>)

data class AdminAnnouncement(
    val id: Long, val title: String, val message: String, val url: String?,
    val active: Boolean, val interrupt: Boolean, val createdAt: String,
    val urlLabel: String? = null, val expires: String? = null, val starts: String? = null,
    val days: List<Int> = emptyList(), val startTime: String? = null, val endTime: String? = null,
    val repeatDaily: Boolean = false
)

data class AdminMessage(
    val id: Long, val username: String, val name: String, val title: String, val message: String,
    val active: Boolean, val createdAt: String
)

data class DonationSummary(val today: Long, val month: Long, val total: Long, val count: Int)

data class AdminBanner(
    val id: Long, val imageUrl: String, val title: String?, val linkUrl: String?,
    val sortOrder: Int, val active: Boolean
)

data class AdminDonation(val id: Long, val name: String, val amount: Long, val note: String, val createdAt: String, val linked: Boolean)

data class AdminLink(val id: Long, val label: String, val url: String, val active: Boolean)

/** Pengaturan di tabel app_config (1 baris) yang dipakai panel owner. */
data class AdminConfig(
    val appEnabled: Boolean,
    val disabledTitle: String,
    val disabledMessage: String,
    val homeMaintenance: Boolean,
    val homeMaintenanceTitle: String,
    val homeMaintenanceMessage: String,
    val donationEnabled: Boolean,
    val donationTitle: String,
    val donationMessage: String,
    val popupEnabled: Boolean,
    val popupNonce: Int,
    val minVersionCode: Long,
    val latestVersionCode: Long,
    val latestVersionName: String,
    val updateNotes: String,
    val updatePageUrl: String,
    val apkUrl: String,
    /** Saklar fitur; kunci yang tidak ada = menyala. */
    val features: Map<String, Boolean>
)

/**
 * Semua panggilan khusus owner. Keamanan TIDAK bergantung pada aplikasi ini: setiap fungsi / tabel di sisi
 * Server memeriksa `is_owner()`, jadi user biasa yang memanggilnya akan ditolak server.
 */
object OwnerApi {
    private suspend fun token(): String? = AppGraph.auth.validToken()

    private fun JSONObject.s(key: String): String? = if (isNull(key)) null else optString(key).takeIf { it.isNotBlank() }

    private suspend fun <T> read(path: String, method: String = "GET", body: String? = null, parse: (String) -> T): Res<T> {
        val t = token() ?: return Res(null, "Sesi habis, masuk lagi ya")
        val r = CloudApi.json(method, path, body, t)
        if (!r.ok) return Res(null, CloudApi.errorText(r))
        return try { Res(parse(r.body)) } catch (e: Exception) { Res(null, "Respons tidak dikenali") }
    }

    /** Menulis ke server. Mengembalikan pesan error, atau null kalau berhasil. */
    private suspend fun write(method: String, path: String, body: String? = null): String? {
        val t = token() ?: return "Sesi habis, masuk lagi ya"
        val r = CloudApi.json(method, path, body, t, mapOf("Prefer" to "return=minimal"))
        return if (r.ok) null else CloudApi.errorText(r)
    }

    // ------------------------------------------------------------------ ringkasan & pengguna

    suspend fun stats(): Res<AdminStats> = read("/rest/v1/rpc/admin_stats", "POST", "{}") {
        val o = JSONObject(it)
        AdminStats(o.optInt("users"), o.optInt("online"), o.optInt("active_24h"), o.optInt("new_7d"), o.optInt("plays_today"), o.optInt("blocked"))
    }

    suspend fun overview(): Res<AdminOverview> = read("/rest/v1/rpc/admin_overview", "POST", "{}") {
        val o = JSONObject(it)
        val ta = o.optJSONArray("top_artists")
        AdminOverview(
            o.optInt("total_users"), o.optLong("total_listen_seconds"), o.optInt("total_plays"), o.optInt("plays_today"),
            o.optLong("listen_today_seconds"), o.optInt("active_7d"), o.optDouble("avg_level", 1.0),
            (0 until (ta?.length() ?: 0)).mapNotNull { i -> ta?.optJSONObject(i) }.map { x -> x.optString("artist") to x.optInt("plays") }
        )
    }

    suspend fun topListeners(limit: Int = 20): Res<List<TopListener>> =
        read("/rest/v1/rpc/admin_top_listeners", "POST", JSONObject().put("p_limit", limit).toString()) { text ->
            val a = JSONArray(text)
            (0 until a.length()).mapNotNull { a.optJSONObject(it) }.map { o ->
                TopListener(
                    o.optString("user_id"),
                    o.s("display_name") ?: o.s("username") ?: "Pengguna",
                    o.s("username"), o.s("avatar_url"),
                    o.optInt("level", 1), o.optLong("listen_seconds"), o.optInt("plays_count")
                )
            }
        }

    /** Jumlah putaran per jam (0..23, WIB) dalam [days] hari terakhir. */
    suspend fun hourly(days: Int): Res<List<Int>> =
        read("/rest/v1/rpc/admin_hourly", "POST", JSONObject().put("p_days", days).toString()) { text ->
            val a = JSONArray(text)
            val out = IntArray(24)
            for (i in 0 until a.length()) {
                val o = a.optJSONObject(i) ?: continue
                val h = o.optInt("hour", -1)
                if (h in 0..23) out[h] = o.optInt("plays")
            }
            out.toList()
        }

    suspend fun userStats(userId: String): Res<UserStatsRow> =
        read("/rest/v1/rpc/admin_user_stats", "POST", JSONObject().put("p_user", userId).toString()) {
            val o = JSONObject(it)
            UserStatsRow(
                o.optLong("total_seconds"), o.optInt("level", 1), o.optInt("plays"), o.optLong("today_seconds"),
                o.optLong("week_seconds"), o.optInt("active_days"), o.s("top_artist")
            )
        }

    suspend fun badgesOf(userId: String): Res<List<String>> =
        read("/rest/v1/profiles?id=eq.$userId&select=badges") { text ->
            val a = JSONArray(text)
            val b = a.optJSONObject(0)?.optJSONArray("badges")
            (0 until (b?.length() ?: 0)).map { i -> b!!.optString(i) }.filter { it.isNotBlank() }
        }

    suspend fun setBadges(userId: String, badges: List<String>): String? =
        write(
            "POST", "/rest/v1/rpc/admin_set_badges",
            JSONObject().put("p_user", userId).put("p_badges", JSONArray(badges)).toString()
        )

    suspend fun users(search: String, offset: Int = 0, limit: Int = 50): Res<List<AdminUser>> =
        read(
            "/rest/v1/rpc/admin_list_users", "POST",
            JSONObject().put("p_search", search.trim()).put("p_limit", limit).put("p_offset", offset).toString()
        ) { text ->
            val a = JSONArray(text)
            (0 until a.length()).mapNotNull { a.optJSONObject(it) }.map { o ->
                AdminUser(
                    id = o.optString("id"),
                    email = o.optString("email"),
                    displayName = o.s("display_name") ?: o.optString("email").substringBefore("@"),
                    username = o.s("username"),
                    avatarUrl = o.s("avatar_url"),
                    createdAt = o.s("created_at"),
                    status = o.optString("status", "active"),
                    until = o.s("until"),
                    reason = o.optString("reason"),
                    lastSeen = o.s("last_seen"),
                    online = o.optBoolean("online"),
                    isOwner = o.optBoolean("is_owner"),
                    level = o.optInt("level", 1),
                    listenSeconds = o.optLong("listen_seconds"),
                    plays = o.optInt("plays_count")
                )
            }
        }

    suspend fun activity(userId: String): Res<UserActivity> =
        read(
            "/rest/v1/rpc/admin_user_activity", "POST",
            JSONObject().put("p_user", userId).put("p_limit", 150).toString()
        ) { text ->
            val o = JSONObject(text)
            val plays = o.optJSONArray("plays") ?: JSONArray()
            val sessions = o.optJSONArray("sessions") ?: JSONArray()
            UserActivity(
                plays = (0 until plays.length()).mapNotNull { plays.optJSONObject(it) }.map {
                    PlayEvent(it.optString("song_id"), it.optString("title"), it.optString("artist"), it.s("thumbnail"), it.optString("played_at"))
                },
                sessions = (0 until sessions.length()).mapNotNull { sessions.optJSONObject(it) }.map {
                    SessionRow(it.optString("started_at"), it.optString("last_seen_at"), it.optString("app_version"))
                }
            )
        }

    /** [status]: active (pulihkan) | suspended | banned. [untilIso] kosong = tanpa batas waktu. */
    suspend fun setStatus(userId: String, status: String, untilIso: String?, reason: String): String? =
        write(
            "POST", "/rest/v1/rpc/admin_set_status",
            JSONObject()
                .put("p_user", userId)
                .put("p_status", status)
                .put("p_until", untilIso ?: JSONObject.NULL)
                .put("p_reason", reason)
                .toString()
        )

    // ------------------------------------------------------------------ pengaturan aplikasi

    suspend fun config(): Res<AdminConfig> = read("/rest/v1/app_config?id=eq.1&select=*") { text ->
        val o = JSONArray(text).getJSONObject(0)
        AdminConfig(
            appEnabled = o.optBoolean("app_enabled", true),
            disabledTitle = o.optString("disabled_title"),
            disabledMessage = o.optString("disabled_message"),
            homeMaintenance = o.optBoolean("home_maintenance", false),
            homeMaintenanceTitle = o.optString("home_maintenance_title"),
            homeMaintenanceMessage = o.optString("home_maintenance_message"),
            donationEnabled = o.optBoolean("donation_enabled", false),
            donationTitle = o.optString("donation_title"),
            donationMessage = o.optString("donation_message"),
            popupEnabled = o.optBoolean("donation_popup_enabled", false),
            popupNonce = o.optInt("donation_popup_nonce", 0),
            minVersionCode = o.optLong("min_version_code", 0),
            latestVersionCode = o.optLong("latest_version_code", 0),
            latestVersionName = o.optString("latest_version_name"),
            updateNotes = o.optString("update_notes"),
            updatePageUrl = o.optString("update_page_url"),
            apkUrl = o.optString("apk_url"),
            features = o.optJSONObject("features")?.let { f ->
                f.keys().asSequence().associateWith { k -> f.optBoolean(k, true) }
            } ?: emptyMap()
        )
    }

    suspend fun patchConfig(changes: JSONObject): String? = write("PATCH", "/rest/v1/app_config?id=eq.1", changes.toString())

    /** Menyalakan / mematikan satu fitur untuk semua pengguna. [current] = saklar yang sekarang, supaya yang lain tidak hilang. */
    suspend fun setFeature(current: Map<String, Boolean>, key: String, on: Boolean): String? {
        val merged = JSONObject()
        current.forEach { (k, v) -> merged.put(k, v) }
        merged.put(key, on)
        return patchConfig(JSONObject().put("features", merged))
    }

    /** Menyalakan semua fitur sekaligus (mengosongkan daftar yang dimatikan). */
    suspend fun setAllFeatures(on: Boolean): String? {
        require(on) { "hanya untuk menyalakan semua" }
        return patchConfig(JSONObject().put("features", JSONObject()))
    }

    // ------------------------------------------------------------------ aktivitas live

    suspend fun recentPlays(): Res<List<LivePlay>> =
        read("/rest/v1/rpc/admin_recent_plays", "POST", JSONObject().put("p_limit", 60).toString()) { text ->
            val a = JSONArray(text)
            (0 until a.length()).mapNotNull { a.optJSONObject(it) }.map {
                LivePlay(it.optString("name"), it.optString("title"), it.optString("artist"), it.s("thumbnail"), it.optString("played_at"))
            }
        }

    suspend fun topSongs(days: Int): Res<List<TopSong>> =
        read("/rest/v1/rpc/admin_top_songs", "POST", JSONObject().put("p_days", days).toString()) { text ->
            val a = JSONArray(text)
            (0 until a.length()).mapNotNull { a.optJSONObject(it) }.map {
                TopSong(it.optString("title"), it.optString("artist"), it.s("thumbnail"), it.optInt("plays"), it.optInt("listeners"))
            }
        }

    // ------------------------------------------------------------------ peringkat donasi

    /** Mencatat donasi yang masuk. [username] diisi kalau donaturnya punya akun Aoto (masuk peringkat atas namanya). */
    suspend fun addDonation(username: String, name: String, amount: Long, note: String): String? =
        write(
            "POST", "/rest/v1/rpc/owner_add_donation",
            JSONObject().put("p_username", username).put("p_name", name).put("p_amount", amount).put("p_note", note).toString()
        )

    suspend fun donations(): Res<List<AdminDonation>> =
        read("/rest/v1/rpc/owner_list_donations", "POST", JSONObject().put("p_limit", 15).toString()) { text ->
            val a = JSONArray(text)
            (0 until a.length()).mapNotNull { a.optJSONObject(it) }.map {
                AdminDonation(
                    it.optLong("id"), it.optString("name"), it.optLong("amount"),
                    it.optString("note"), it.optString("created_at"), it.optBoolean("linked")
                )
            }
        }

    suspend fun deleteDonation(id: Long): String? = write("DELETE", "/rest/v1/donations?id=eq.$id")

    /** Naikkan nonce = popup donasi dikirim ulang ke semua user yang sedang / akan membuka Beranda. */
    suspend fun pushDonatePopup(currentNonce: Int): String? =
        patchConfig(JSONObject().put("donation_popup_enabled", true).put("donation_popup_nonce", currentNonce + 1))

    // ------------------------------------------------------------------ pengumuman

    suspend fun announcements(): Res<List<AdminAnnouncement>> =
        read("/rest/v1/announcements?select=*&order=created_at.desc&limit=30") { text ->
            val a = JSONArray(text)
            (0 until a.length()).mapNotNull { a.optJSONObject(it) }.map {
                AdminAnnouncement(
                    id = it.optLong("id"), title = it.optString("title"), message = it.optString("message"),
                    url = it.s("url"), active = it.optBoolean("active", true),
                    interrupt = it.optBoolean("interrupt_playback", true), createdAt = it.optString("created_at"),
                    urlLabel = it.s("url_label"), expires = it.s("expires_at"), starts = it.s("starts_at"),
                    days = it.optJSONArray("days")?.let { d -> (0 until d.length()).map { i -> d.optInt(i) } }.orEmpty(),
                    startTime = it.s("start_time"), endTime = it.s("end_time"), repeatDaily = it.optBoolean("repeat_daily", false)
                )
            }
        }

    /** Mengirim pengumuman. Mengembalikan id pengumuman baru, atau error. */
    suspend fun sendAnnouncement(
        title: String, message: String, url: String?, urlLabel: String?, interrupt: Boolean, expiresDate: String? = null,
        startsDate: String? = null, days: Set<Int> = emptySet(), startTime: String? = null, endTime: String? = null,
        repeatDaily: Boolean = false
    ): Res<String> {
        val body = JSONObject()
            .put("title", title).put("message", message)
            .put("url", if (url.isNullOrBlank()) JSONObject.NULL else url)
            .put("url_label", if (url.isNullOrBlank() || urlLabel.isNullOrBlank()) JSONObject.NULL else urlLabel)
            .put("interrupt_playback", interrupt)
            .put("expires_at", if (expiresDate.isNullOrBlank()) JSONObject.NULL else expiresDate)
            .put("starts_at", if (startsDate.isNullOrBlank()) JSONObject.NULL else startsDate)
            .put("days", JSONArray(days.sorted()))
            .put("start_time", if (startTime.isNullOrBlank()) JSONObject.NULL else startTime)
            .put("end_time", if (endTime.isNullOrBlank()) JSONObject.NULL else endTime)
            .put("repeat_daily", repeatDaily)
            .put("active", true)
        val t = token() ?: return Res(null, "Sesi habis, masuk lagi ya")
        val r = CloudApi.json("POST", "/rest/v1/announcements", body.toString(), t, mapOf("Prefer" to "return=representation"))
        if (!r.ok) return Res(null, CloudApi.errorText(r))
        return try {
            Res(JSONArray(r.body).getJSONObject(0).optLong("id").toString())
        } catch (_: Exception) {
            Res("")
        }
    }

    // ------------------------------------------------------------------ pesan ke satu pengguna

    suspend fun messages(): Res<List<AdminMessage>> =
        read("/rest/v1/rpc/owner_list_messages", "POST", JSONObject().put("p_limit", 30).toString()) { text ->
            val a = JSONArray(text)
            (0 until a.length()).mapNotNull { a.optJSONObject(it) }.map {
                AdminMessage(
                    it.optLong("id"), it.optString("username"), it.optString("name"), it.optString("title"),
                    it.optString("message"), it.optBoolean("active", true), it.optString("created_at")
                )
            }
        }

    /** Mengirim pesan ke satu username. Mengembalikan pesan error, atau null kalau berhasil. */
    suspend fun sendMessage(username: String, title: String, message: String, url: String?, urlLabel: String?, interrupt: Boolean, expiresDate: String?): String? =
        write(
            "POST", "/rest/v1/rpc/owner_send_message",
            JSONObject()
                .put("p_username", username).put("p_title", title).put("p_message", message)
                .put("p_url", if (url.isNullOrBlank()) JSONObject.NULL else url)
                .put("p_url_label", if (url.isNullOrBlank() || urlLabel.isNullOrBlank()) JSONObject.NULL else urlLabel)
                .put("p_interrupt", interrupt)
                .put("p_expires", if (expiresDate.isNullOrBlank()) JSONObject.NULL else expiresDate)
                .toString()
        )

    suspend fun deleteMessage(id: Long): String? = write("DELETE", "/rest/v1/user_messages?id=eq.$id")

    // ------------------------------------------------------------------ ringkasan donasi

    suspend fun donationSummary(): Res<DonationSummary> = read("/rest/v1/rpc/owner_donation_summary", "POST", "{}") {
        val o = JSONObject(it)
        DonationSummary(o.optLong("today"), o.optLong("month"), o.optLong("total"), o.optInt("count"))
    }

    suspend fun setAnnouncementActive(id: Long, active: Boolean): String? =
        write("PATCH", "/rest/v1/announcements?id=eq.$id", JSONObject().put("active", active).toString())

    suspend fun deleteAnnouncement(id: Long): String? = write("DELETE", "/rest/v1/announcements?id=eq.$id")

    // ------------------------------------------------------------------ banner

    suspend fun banners(): Res<List<AdminBanner>> =
        read("/rest/v1/banners?select=*&order=sort_order.asc,id.asc") { text ->
            val a = JSONArray(text)
            (0 until a.length()).mapNotNull { a.optJSONObject(it) }.map {
                AdminBanner(
                    id = it.optLong("id"), imageUrl = it.optString("image_url"), title = it.s("title"),
                    linkUrl = it.s("link_url"), sortOrder = it.optInt("sort_order"), active = it.optBoolean("active", true)
                )
            }
        }

    suspend fun addBanner(imageUrl: String, title: String?, linkUrl: String?, sortOrder: Int): String? =
        write(
            "POST", "/rest/v1/banners",
            JSONObject()
                .put("image_url", imageUrl)
                .put("title", if (title.isNullOrBlank()) JSONObject.NULL else title)
                .put("link_url", if (linkUrl.isNullOrBlank()) JSONObject.NULL else linkUrl)
                .put("sort_order", sortOrder)
                .put("active", true)
                .toString()
        )

    suspend fun setBannerActive(id: Long, active: Boolean): String? =
        write("PATCH", "/rest/v1/banners?id=eq.$id", JSONObject().put("active", active).toString())

    suspend fun setBannerOrder(id: Long, order: Int): String? =
        write("PATCH", "/rest/v1/banners?id=eq.$id", JSONObject().put("sort_order", order).toString())

    suspend fun deleteBanner(id: Long): String? = write("DELETE", "/rest/v1/banners?id=eq.$id")

    /** Mengunggah gambar banner ke bucket `app-banners` (hanya owner yang diizinkan). Mengembalikan alamat publiknya. */
    suspend fun uploadBannerImage(bytes: ByteArray, contentType: String, ext: String): Res<String> {
        val t = token() ?: return Res(null, "Sesi habis, masuk lagi ya")
        val path = "${System.currentTimeMillis()}.$ext"
        val r = CloudApi.bytes("/storage/v1/object/app-banners/$path", bytes, contentType, t)
        return if (r.ok) Res(CloudApi.publicUrl("app-banners", path)) else Res(null, CloudApi.errorText(r))
    }

    // ------------------------------------------------------------------ tautan donasi

    suspend fun donationLinks(): Res<List<AdminLink>> =
        read("/rest/v1/donation_links?select=*&order=sort_order.asc,id.asc") { text ->
            val a = JSONArray(text)
            (0 until a.length()).mapNotNull { a.optJSONObject(it) }.map {
                AdminLink(it.optLong("id"), it.optString("label"), it.optString("url"), it.optBoolean("active", true))
            }
        }

    suspend fun addDonationLink(label: String, url: String, sortOrder: Int): String? =
        write(
            "POST", "/rest/v1/donation_links",
            JSONObject().put("label", label).put("url", url).put("sort_order", sortOrder).put("active", true).toString()
        )

    suspend fun deleteDonationLink(id: Long): String? = write("DELETE", "/rest/v1/donation_links?id=eq.$id")
}
