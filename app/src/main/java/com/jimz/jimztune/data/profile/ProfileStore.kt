package com.jimz.jimztune.data.profile

import android.content.Context
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.setValue
import androidx.compose.ui.graphics.Color
import com.jimz.jimztune.AppGraph
import com.jimz.jimztune.data.auth.AuthResult
import com.jimz.jimztune.data.cloud.CloudApi
import org.json.JSONArray
import org.json.JSONObject

data class Profile(
    val id: String,
    val username: String?,
    val displayName: String,
    val bio: String,
    val avatarUrl: String?,
    val bannerUrl: String?,
    /** Warna tema profil, format #RRGGBB. */
    val accentHex: String,
    val statusEmoji: String,
    val statusText: String,
    /** Lencana. Hanya bisa diubah dari dashboard server (bukan oleh user). */
    val badges: List<String>,
    val createdAt: String?
) {
    val accent: Color
        get() = try {
            Color(android.graphics.Color.parseColor(accentHex))
        } catch (_: Exception) {
            Color(0xFF6C93FF)
        }
}

/** Profil pengguna (nama, username, bio, avatar, banner, warna, status). Disimpan di tabel `profiles`. */
class ProfileStore(context: Context) {
    private val prefs = context.applicationContext.getSharedPreferences("jimztune_profile", Context.MODE_PRIVATE)

    var profile by mutableStateOf<Profile?>(loadCache())
        private set

    suspend fun refresh() {
        val session = AppGraph.auth.session ?: return
        val token = AppGraph.auth.validToken() ?: return
        val r = CloudApi.json("GET", "/rest/v1/profiles?id=eq.${session.userId}&select=*", token = token)
        if (!r.ok) return
        val arr = try { JSONArray(r.body) } catch (_: Exception) { return }
        if (arr.length() > 0) {
            apply(arr.getJSONObject(0))
        } else {
            // Trigger database belum membuat profil (akun lama): buat dari sisi aplikasi.
            val name = session.email.substringBefore("@").ifEmpty { "Pengguna" }
            val created = CloudApi.json(
                "POST", "/rest/v1/profiles",
                JSONObject().put("id", session.userId).put("display_name", name).toString(),
                token, mapOf("Prefer" to "return=representation")
            )
            if (created.ok) JSONArray(created.body).optJSONObject(0)?.let { apply(it) }
        }
    }

    /** Menyimpan perubahan kolom tertentu. Kunci JSON = nama kolom di tabel profiles. */
    suspend fun save(changes: JSONObject): AuthResult {
        val session = AppGraph.auth.session ?: return AuthResult(false, "Belum masuk")
        val token = AppGraph.auth.validToken() ?: return AuthResult(false, "Sesi habis, masuk lagi ya")
        val r = CloudApi.json(
            "PATCH", "/rest/v1/profiles?id=eq.${session.userId}", changes.toString(), token,
            mapOf("Prefer" to "return=representation")
        )
        if (!r.ok) {
            val msg = when {
                r.code == 409 || "duplicate" in r.body.lowercase() -> "Username itu sudah dipakai orang lain"
                "profiles_username_check" in r.body -> "Username hanya boleh huruf kecil, angka, _ dan . (3–20 karakter)"
                else -> CloudApi.errorText(r)
            }
            return AuthResult(false, msg)
        }
        JSONArray(r.body).optJSONObject(0)?.let { apply(it) }
        return AuthResult(true)
    }

    /** Mengunggah gambar (JPEG, atau GIF/WebP bergerak) ke bucket publik ([bucket] = avatars / banners). Mengembalikan alamatnya, atau null kalau gagal. */
    suspend fun uploadImage(bucket: String, jpeg: ByteArray, contentType: String = "image/jpeg", ext: String = "jpg"): String? {
        val session = AppGraph.auth.session ?: return null
        val token = AppGraph.auth.validToken() ?: return null
        val path = "${session.userId}/${System.currentTimeMillis()}.$ext"
        val r = CloudApi.bytes("/storage/v1/object/$bucket/$path", jpeg, contentType, token)
        return if (r.ok) CloudApi.publicUrl(bucket, path) else null
    }

    fun clear() {
        profile = null
        prefs.edit().clear().apply()
    }

    private fun apply(o: JSONObject) {
        profile = parse(o)
        prefs.edit().putString("cache", o.toString()).apply()
    }

    private fun loadCache(): Profile? = try {
        prefs.getString("cache", null)?.let { parse(JSONObject(it)) }
    } catch (_: Exception) {
        null
    }

    private fun JSONObject.s(key: String): String? = if (isNull(key)) null else optString(key).takeIf { it.isNotBlank() }

    private fun parse(o: JSONObject): Profile {
        val badges = o.optJSONArray("badges")?.let { a -> (0 until a.length()).map { a.optString(it) } }.orEmpty()
        return Profile(
            id = o.optString("id"),
            username = o.s("username"),
            displayName = o.s("display_name") ?: "Pengguna",
            bio = o.s("bio").orEmpty(),
            avatarUrl = o.s("avatar_url"),
            bannerUrl = o.s("banner_url"),
            accentHex = o.s("accent_color") ?: "#6C93FF",
            statusEmoji = o.s("status_emoji").orEmpty(),
            statusText = o.s("status_text").orEmpty(),
            badges = badges.filter { it.isNotBlank() },
            createdAt = o.s("created_at")
        )
    }
}
