package com.jimz.jimztune.data.share

import android.content.Context
import android.net.Uri
import android.widget.Toast
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.setValue
import com.jimz.jimztune.AppConfig
import com.jimz.jimztune.AppGraph
import com.jimz.jimztune.data.cloud.CloudApi
import com.jimz.jimztune.data.model.SearchItem
import com.jimz.jimztune.data.model.SearchType
import com.jimz.jimztune.util.shareText
import kotlinx.coroutines.CoroutineScope
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.SupervisorJob
import kotlinx.coroutines.launch
import org.json.JSONArray
import org.json.JSONObject

class SharedCreator(val name: String, val username: String?, val avatarUrl: String?, val level: Int)

class SharedPlaylist(
    val code: String,
    val title: String,
    val songs: List<SearchItem>,
    val creator: SharedCreator,
    val views: Int
)

/**
 * Tautan bagikan. Playlist / lagu disimpan di server (RPC share_playlist) dan dibagikan sebagai tautan.
 * Tautan membuka halaman web bagikan (kalau SHARE_BASE_URL diisi) yang punya tombol "Buka di Aoto",
 * atau langsung membuka aplikasi lewat aoto://p/KODE. Di aplikasi, tautan menampilkan isi dan pembuatnya.
 */
object ShareLinks {
    /** Kode tautan yang baru dibuka dan menunggu ditampilkan (diisi MainActivity, dibaca layar utama). */
    var pending by mutableStateOf<String?>(null)

    private val scope = CoroutineScope(SupervisorJob() + Dispatchers.Main)

    fun link(code: String): String {
        val base = AppConfig.SHARE_BASE_URL.trim()
        return if (base.startsWith("http")) base.trimEnd('/') + "/p/" + code else "aoto://p/$code"
    }

    /** Mengambil kode dari tautan yang dibuka (jimztune://p/KODE atau https://host/...?c=KODE), atau null. */
    fun parse(uri: Uri): String? {
        if (!AppConfig.SHARE_ENABLED) return null
        val code = when (uri.scheme) {
            "aoto", "jimztune" -> if (uri.host == "p") uri.pathSegments.firstOrNull() else null
            "https", "http" -> uri.getQueryParameter("c")
                ?: uri.pathSegments.let { seg -> if (seg.size >= 2 && seg[seg.size - 2] == "p") seg.last() else null }
            else -> null
        }
        return code?.takeIf { it.matches(Regex("[A-Za-z0-9]{4,16}")) }
    }

    private fun toJson(songs: List<SearchItem>): JSONArray = JSONArray().also { arr ->
        songs.take(500).forEach { s ->
            arr.put(
                JSONObject()
                    .put("id", s.id).put("title", s.title).put("subtitle", s.subtitle)
                    .put("thumbnail", s.thumbnailUrl ?: JSONObject.NULL)
                    .put("duration", s.durationText ?: JSONObject.NULL)
                    .put("artistId", s.artistId ?: JSONObject.NULL)
                    .put("albumId", s.albumId ?: JSONObject.NULL)
            )
        }
    }

    private fun prefs(context: Context) = context.applicationContext.getSharedPreferences("jimztune_share", Context.MODE_PRIVATE)

    /** Membuat (atau memperbarui) tautan. Mengembalikan kode, atau null kalau gagal / belum login. */
    private suspend fun create(context: Context, key: String?, title: String, songs: List<SearchItem>): String? {
        if (!CloudApi.configured || !AppGraph.auth.isLoggedIn || songs.isEmpty()) return null
        val token = AppGraph.auth.validToken() ?: return null
        val body = JSONObject().put("p_title", title).put("p_songs", toJson(songs))
        val old = key?.let { prefs(context).getString(it, null) }
        if (old != null) body.put("p_code", old)
        val r = CloudApi.json("POST", "/rest/v1/rpc/share_playlist", body.toString(), token)
        if (!r.ok) return null
        val code = r.body.trim().trim('"').takeIf { it.isNotBlank() } ?: return null
        if (key != null) prefs(context).edit().putString(key, code).apply()
        return code
    }

    private fun message(title: String, count: Int, code: String): String {
        val who = AppGraph.profile.profile?.displayName?.substringBefore("@")?.ifBlank { null }
        return buildString {
            append("🎵 ").append(title).append('\n')
            append(if (count == 1) "Lagu" else "Playlist · $count lagu")
            if (who != null) append(" · dibagikan oleh ").append(who)
            append("\n\n").append(link(code))
        }
    }

    /** Bagikan playlist koleksi sebagai tautan. Gagal / offline: kembali ke daftar teks. */
    fun sharePlaylist(context: Context, playlistId: String, name: String, songs: List<SearchItem>, fallbackText: String) {
        if (!AppConfig.SHARE_ENABLED) return
        if (!com.jimz.jimztune.AppGraph.remote.featureOn("share")) {
            Toast.makeText(context, "Fitur bagikan sedang dimatikan", Toast.LENGTH_SHORT).show()
            return
        }
        Toast.makeText(context, "Membuat tautan…", Toast.LENGTH_SHORT).show()
        scope.launch {
            val code = create(context, "pl:$playlistId", name, songs)
            if (code != null) context.shareText(name, message(name, songs.size, code))
            else {
                Toast.makeText(context, "Tautan belum bisa dibuat, dibagikan sebagai teks", Toast.LENGTH_SHORT).show()
                context.shareText(name, fallbackText)
            }
        }
    }

    /** Bagikan satu lagu sebagai tautan. Gagal / offline: kembali ke tautan YouTube Music. */
    fun shareSong(context: Context, song: SearchItem, fallbackText: String) {
        if (!AppConfig.SHARE_ENABLED) return
        scope.launch {
            val code = create(context, null, song.title, listOf(song))
            if (code != null) context.shareText(song.title, message(song.title, 1, code))
            else context.shareText(song.title, fallbackText)
        }
    }

    suspend fun fetch(code: String): SharedPlaylist? {
        if (!CloudApi.configured) return null
        val token = AppGraph.auth.validToken()
        val r = CloudApi.json("POST", "/rest/v1/rpc/get_shared_playlist", JSONObject().put("p_code", code).toString(), token)
        if (!r.ok || r.body.isBlank() || r.body.trim() == "null") return null
        return try {
            val o = JSONObject(r.body)
            val arr = o.getJSONArray("songs")
            fun JSONObject.s(k: String) = if (isNull(k)) null else optString(k).takeIf { it.isNotBlank() }
            val songs = (0 until arr.length()).mapNotNull { arr.optJSONObject(it) }.map { x ->
                SearchItem(
                    id = x.optString("id"), type = SearchType.SONG, title = x.optString("title"),
                    subtitle = x.optString("subtitle"), thumbnailUrl = x.s("thumbnail"), durationText = x.s("duration"),
                    artistId = x.s("artistId"), albumId = x.s("albumId")
                )
            }
            val c = o.optJSONObject("creator")
            SharedPlaylist(
                code = o.optString("code"), title = o.optString("title"), songs = songs,
                creator = SharedCreator(
                    name = c?.s("display_name") ?: c?.s("username") ?: "Pengguna Aoto",
                    username = c?.s("username"), avatarUrl = c?.s("avatar_url"), level = c?.optInt("level", 1) ?: 1
                ),
                views = o.optInt("views")
            )
        } catch (_: Exception) {
            null
        }
    }
}
