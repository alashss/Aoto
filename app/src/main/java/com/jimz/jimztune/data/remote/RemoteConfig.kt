package com.jimz.jimztune.data.remote

import org.json.JSONArray
import org.json.JSONObject
import java.text.SimpleDateFormat
import java.util.Date
import java.util.Locale

data class DonateLink(val label: String, val url: String)

data class Donation(
    val enabled: Boolean = false,
    val title: String = "Traktir kopi ☕",
    val message: String = "",
    val links: List<DonateLink> = emptyList(),
    /** Popup donasi realtime di Beranda. Dikirim ulang ke semua user setiap [popupNonce] naik. */
    val popupEnabled: Boolean = false,
    val popupNonce: Long = 0
)

/** Banner di bagian paling atas Beranda. */
data class Banner(
    val id: String,
    val imageUrl: String,
    val title: String? = null,
    val linkUrl: String? = null
)

/** Mode perbaikan Beranda (tidak termasuk banner). */
data class HomeMaintenance(
    val enabled: Boolean = false,
    val title: String = "Halaman beranda sedang dalam perbaikan",
    val message: String = "Kami sedang memperbaiki beranda. Sementara itu kamu tetap bisa memutar playlist, lagu favorit, dan unduhanmu."
)

data class Announcement(
    val id: String,
    val title: String,
    val message: String,
    val url: String? = null,
    val urlLabel: String? = null,
    /** Format yyyy-MM-dd. Setelah tanggal ini pengumuman tidak ditampilkan lagi. Kosong = tidak kedaluwarsa. */
    val expires: String? = null,
    /** True = musik user dihentikan & pemutar ditutup saat pengumuman ini muncul. */
    val interrupt: Boolean = true
) {
    fun isActive(): Boolean {
        val e = expires ?: return true
        val today = SimpleDateFormat("yyyy-MM-dd", Locale.US).format(Date())
        return today <= e
    }
}

data class RemoteConfig(
    val appEnabled: Boolean = true,
    val disabledTitle: String = "Aplikasi sedang dinonaktifkan",
    val disabledMessage: String = "Silakan coba lagi nanti.",
    val disabledUrl: String? = null,
    val minVersionCode: Long = 0,
    val latestVersionCode: Long = 0,
    val latestVersionName: String = "",
    val updateNotes: String = "",
    val updateUrl: String? = null,
    val apkUrl: String? = null,
    val announcements: List<Announcement> = emptyList(),
    val donation: Donation = Donation(),
    val homeMaintenance: HomeMaintenance = HomeMaintenance(),
    val banners: List<Banner> = emptyList(),
    /** Saklar fitur dari owner. Kunci yang tidak ada = menyala. */
    val features: Map<String, Boolean> = emptyMap()
)

private fun JSONObject.str(key: String): String? =
    if (isNull(key)) null else optString(key).takeIf { it.isNotBlank() }

fun parseRemoteConfig(text: String): RemoteConfig {
    val o = JSONObject(text)
    val update = o.optJSONObject("update")
    val off = o.optJSONObject("disabled")
    val donate = o.optJSONObject("donation")
    val maint = o.optJSONObject("home_maintenance")
    val defaultMaint = HomeMaintenance()
    return RemoteConfig(
        appEnabled = o.optBoolean("app_enabled", true),
        disabledTitle = off?.str("title") ?: "Aplikasi sedang dinonaktifkan",
        disabledMessage = off?.str("message") ?: "Silakan coba lagi nanti.",
        disabledUrl = off?.str("url"),
        minVersionCode = update?.optLong("min_version_code", 0) ?: 0,
        latestVersionCode = update?.optLong("latest_version_code", 0) ?: 0,
        latestVersionName = update?.str("latest_version_name").orEmpty(),
        updateNotes = update?.str("notes").orEmpty(),
        updateUrl = update?.str("page_url"),
        apkUrl = update?.str("apk_url"),
        announcements = (o.optJSONArray("announcements") ?: JSONArray()).let { arr ->
            (0 until arr.length()).mapNotNull { arr.optJSONObject(it) }.mapNotNull {
                val id = it.str("id") ?: return@mapNotNull null
                Announcement(
                    id = id,
                    title = it.str("title").orEmpty(),
                    message = it.str("message").orEmpty(),
                    url = it.str("url"),
                    urlLabel = it.str("url_label"),
                    expires = it.str("expires"),
                    interrupt = it.optBoolean("interrupt", true)
                )
            }
        },
        donation = Donation(
            enabled = donate?.optBoolean("enabled", false) ?: false,
            title = donate?.str("title") ?: "Traktir kopi ☕",
            message = donate?.str("message").orEmpty(),
            popupEnabled = donate?.optBoolean("popup_enabled", false) ?: false,
            popupNonce = donate?.optLong("popup_nonce", 0) ?: 0,
            links = (donate?.optJSONArray("links") ?: JSONArray()).let { arr ->
                (0 until arr.length()).mapNotNull { arr.optJSONObject(it) }.mapNotNull {
                    val url = it.str("url") ?: return@mapNotNull null
                    DonateLink(it.str("label") ?: "Buka", url)
                }
            }
        ),
        features = o.optJSONObject("features")?.let { f ->
            f.keys().asSequence().associateWith { k -> f.optBoolean(k, true) }
        } ?: emptyMap(),
        homeMaintenance = HomeMaintenance(
            enabled = maint?.optBoolean("enabled", false) ?: false,
            title = maint?.str("title") ?: defaultMaint.title,
            message = maint?.str("message") ?: defaultMaint.message
        ),
        banners = (o.optJSONArray("banners") ?: JSONArray()).let { arr ->
            (0 until arr.length()).mapNotNull { arr.optJSONObject(it) }.mapNotNull {
                val image = it.str("image_url") ?: return@mapNotNull null
                Banner(
                    id = it.str("id") ?: image,
                    imageUrl = image,
                    title = it.str("title"),
                    linkUrl = it.str("link_url")
                )
            }
        }
    )
}
