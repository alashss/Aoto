package com.jimz.jimztune.data.social

import com.jimz.jimztune.AppGraph
import com.jimz.jimztune.data.cloud.CloudApi
import com.jimz.jimztune.data.owner.Res
import org.json.JSONArray
import org.json.JSONObject

data class UserStats(val seconds: Long, val plays: Int, val level: Int, val weekSeconds: Long, val days: Int)
data class TopSongRow(val title: String, val artist: String, val cover: String?, val plays: Int)
data class NowPlayingInfo(val title: String, val artist: String, val cover: String?)

/** Profil pengguna (dilihat siapa pun yang login). Bagian yang disembunyikan pemiliknya bernilai null. */
data class UserProfile(
    val id: String,
    val name: String,
    val username: String?,
    val avatar: String?,
    val banner: String?,
    val bio: String,
    val accentHex: String,
    val statusEmoji: String,
    val statusText: String,
    val badges: List<String>,
    val joined: String?,
    val isOwner: Boolean,
    /** self, friend, pending_in, pending_out, none */
    val relation: String,
    val requestId: Long?,
    val blockedByMe: Boolean,
    val acceptsRequests: Boolean,
    /** True = pemilik membatasi profilnya; hanya identitas dasar yang terlihat. */
    val restricted: Boolean,
    val stats: UserStats?,
    val top: List<TopSongRow>?,
    val friends: Int?,
    /** null = disembunyikan / tidak diketahui. */
    val online: Boolean?,
    val now: NowPlayingInfo?,
    val note: String?,
    val lastSeen: String?
)

data class FriendRow(
    val userId: String, val name: String, val username: String?, val avatar: String?,
    val online: Boolean?, val playing: NowPlayingInfo?, val note: String?,
    val lastBody: String?, val lastAt: String?, val lastMine: Boolean, val unread: Int,
    val noteColor: String? = null, val noteSong: String? = null,
    val noteSongId: String? = null, val noteLyric: String? = null
)

data class PersonRow(
    val requestId: Long, val userId: String, val name: String, val username: String?, val avatar: String?, val createdAt: String?
)

data class FriendRequests(val incoming: List<PersonRow>, val outgoing: List<PersonRow>)

data class SearchRow(val userId: String, val name: String, val username: String?, val avatar: String?, val relation: String)

data class BlockedRow(val userId: String, val name: String, val username: String?, val avatar: String?)

data class DmMessage(val id: Long, val mine: Boolean, val body: String, val createdAt: String, val read: Boolean)

data class ChatMessage(
    val id: Long, val userId: String, val name: String, val username: String?, val avatar: String?,
    val body: String, val createdAt: String, val owner: Boolean
)

data class ChatPinned(val id: Long, val name: String, val body: String)
data class ChatInfo(val enabled: Boolean, val slow: Int, val maxLen: Int, val muted: Boolean)
data class ChatSnapshot(val messages: List<ChatMessage>, val pinned: ChatPinned?, val info: ChatInfo)

data class PrivacySettings(
    /** everyone, friends, nobody */
    val view: String = "everyone",
    val stats: Boolean = true,
    val top: Boolean = true,
    val now: Boolean = true,
    val online: Boolean = true,
    val friends: Boolean = true,
    val requests: Boolean = true
)

data class SocialBadges(val dmUnread: Int = 0, val requests: Int = 0) {
    val total: Int get() = dmUnread + requests
}

private fun JSONObject.s(key: String): String? = if (isNull(key)) null else optString(key).takeIf { it.isNotBlank() }
private fun JSONObject.long(key: String): Long? = if (isNull(key)) null else optLong(key)

/** Semua panggilan sosial ke server. Setiap fungsi mengembalikan [Res] (error berisi pesan yang bisa ditampilkan). */
object SocialApi {

    private suspend fun rpc(name: String, body: JSONObject = JSONObject()): Res<String> {
        if (!CloudApi.configured) return Res(null, "Server belum diatur")
        val token = AppGraph.auth.validToken() ?: return Res(null, "Masuk dulu untuk memakai fitur ini")
        val r = CloudApi.json("POST", "/rest/v1/rpc/$name", body.toString(), token)
        return if (r.ok) Res(r.body.ifBlank { "null" }) else Res(null, CloudApi.errorText(r))
    }

    /** Untuk fungsi yang tidak mengembalikan apa-apa: null = berhasil, selain itu pesan error. */
    private suspend fun rpcDo(name: String, body: JSONObject = JSONObject()): String? {
        val r = rpc(name, body)
        return if (r.error != null) r.error else null
    }

    suspend fun pushRegister(token: String): String? =
        rpcDo("push_register", JSONObject().put("p_token", token).put("p_platform", "android"))

    suspend fun pushUnregister(token: String): String? = rpcDo("push_unregister", JSONObject().put("p_token", token))

    private fun <T> Res<String>.parse(block: (String) -> T): Res<T> {
        val text = value ?: return Res(null, error ?: "Gagal memuat")
        return try {
            Res(block(text))
        } catch (e: Exception) {
            Res(null, "Respons tidak dikenali")
        }
    }

    private fun nowOf(o: JSONObject?): NowPlayingInfo? =
        o?.let { NowPlayingInfo(it.optString("title"), it.optString("artist"), it.s("cover")) }

    // ------------------------------------------------------------------ profil

    suspend fun profile(userId: String): Res<UserProfile> =
        rpc("get_user_profile", JSONObject().put("p_user", userId)).parse { text ->
            if (text.trim() == "null") throw IllegalStateException("kosong")
            val o = JSONObject(text)
            val st = o.optJSONObject("stats")
            val tp = o.optJSONArray("top")
            UserProfile(
                id = o.optString("id"), name = o.optString("name"), username = o.s("username"),
                avatar = o.s("avatar"), banner = o.s("banner"), bio = o.optString("bio"),
                accentHex = o.s("accent") ?: "#6C93FF", statusEmoji = o.optString("status_emoji"),
                statusText = o.optString("status_text"),
                badges = o.optJSONArray("badges")?.let { a -> (0 until a.length()).map { i -> a.optString(i) } }.orEmpty(),
                joined = o.s("joined"), isOwner = o.optBoolean("is_owner"),
                relation = o.optString("relation", "none"), requestId = o.long("request_id"),
                blockedByMe = o.optBoolean("blocked_by_me"), acceptsRequests = o.optBoolean("accepts_requests", true),
                restricted = o.optBoolean("restricted", false),
                stats = st?.let { UserStats(it.optLong("seconds"), it.optInt("plays"), it.optInt("level", 1), it.optLong("week_seconds"), it.optInt("days")) },
                top = tp?.let { a ->
                    (0 until a.length()).mapNotNull { i -> a.optJSONObject(i) }
                        .map { t -> TopSongRow(t.optString("title"), t.optString("artist"), t.s("cover"), t.optInt("plays")) }
                },
                friends = if (o.isNull("friends")) null else o.optInt("friends"),
                online = if (o.isNull("online")) null else o.optBoolean("online"),
                now = nowOf(o.optJSONObject("now")), note = o.s("note"), lastSeen = o.s("last_seen")
            )
        }

    // ------------------------------------------------------------------ privasi

    suspend fun privacy(): Res<PrivacySettings> = rpc("my_privacy").parse { text ->
        val o = JSONObject(text)
        PrivacySettings(
            view = o.optString("view", "everyone"), stats = o.optBoolean("stats", true), top = o.optBoolean("top", true),
            now = o.optBoolean("now", true), online = o.optBoolean("online", true),
            friends = o.optBoolean("friends", true), requests = o.optBoolean("requests", true)
        )
    }

    suspend fun savePrivacy(p: PrivacySettings): String? = rpcDo(
        "set_privacy",
        JSONObject().put("p_view", p.view).put("p_stats", p.stats).put("p_top", p.top).put("p_now", p.now)
            .put("p_online", p.online).put("p_friends", p.friends).put("p_requests", p.requests)
    )

    // ------------------------------------------------------------------ pertemanan

    suspend fun friends(): Res<List<FriendRow>> = rpc("friend_list").parse { text ->
        val a = JSONArray(text)
        (0 until a.length()).mapNotNull { a.optJSONObject(it) }.map {
            FriendRow(
                userId = it.optString("user_id"), name = it.optString("name"), username = it.s("username"), avatar = it.s("avatar"),
                online = if (it.isNull("online")) null else it.optBoolean("online"),
                playing = nowOf(it.optJSONObject("playing")), note = it.s("note"),
                lastBody = it.s("last_body"), lastAt = it.s("last_at"), lastMine = it.optBoolean("last_mine"),
                unread = it.optInt("unread"), noteColor = it.s("note_color"), noteSong = it.s("note_song"), noteSongId = it.s("note_song_id"), noteLyric = it.s("note_lyric")
            )
        }
    }

    private fun people(a: JSONArray?): List<PersonRow> =
        (0 until (a?.length() ?: 0)).mapNotNull { a?.optJSONObject(it) }.map {
            PersonRow(it.optLong("id"), it.optString("user_id"), it.optString("name"), it.s("username"), it.s("avatar"), it.s("created_at"))
        }

    suspend fun requests(): Res<FriendRequests> = rpc("friend_requests").parse { text ->
        val o = JSONObject(text)
        FriendRequests(people(o.optJSONArray("incoming")), people(o.optJSONArray("outgoing")))
    }

    suspend fun search(q: String): Res<List<SearchRow>> = rpc("user_search", JSONObject().put("p_q", q)).parse { text ->
        val a = JSONArray(text)
        (0 until a.length()).mapNotNull { a.optJSONObject(it) }.map {
            SearchRow(it.optString("user_id"), it.optString("name"), it.s("username"), it.s("avatar"), it.optString("relation", "none"))
        }
    }

    /** Mengirim permintaan teman. Hasil: "pending" atau "accepted". */
    suspend fun requestFriend(userId: String): Res<String> =
        rpc("friend_request", JSONObject().put("p_user", userId)).parse { it.trim().trim('"') }

    suspend fun respond(requestId: Long, accept: Boolean): String? =
        rpcDo("friend_respond", JSONObject().put("p_id", requestId).put("p_accept", accept))

    suspend fun removeFriend(userId: String): String? = rpcDo("friend_remove", JSONObject().put("p_user", userId))
    suspend fun block(userId: String): String? = rpcDo("block_user", JSONObject().put("p_user", userId))
    suspend fun unblock(userId: String): String? = rpcDo("unblock_user", JSONObject().put("p_user", userId))

    suspend fun blocked(): Res<List<BlockedRow>> = rpc("blocked_list").parse { text ->
        val a = JSONArray(text)
        (0 until a.length()).mapNotNull { a.optJSONObject(it) }.map {
            BlockedRow(it.optString("user_id"), it.optString("name"), it.s("username"), it.s("avatar"))
        }
    }

    suspend fun badges(): Res<SocialBadges> = rpc("social_badges").parse { text ->
        val o = JSONObject(text)
        SocialBadges(o.optInt("dm_unread"), o.optInt("requests"))
    }

    // ------------------------------------------------------------------ catatan & status

    /** Catatan singkat 24 jam (seperti catatan Instagram). Kosong = hapus. */
    suspend fun setNote(text: String): String? = rpcDo("set_note", JSONObject().put("p_body", text))

    suspend fun setNoteV3(text: String, color: String?, song: String?, songId: String?, lyric: String?): String? = rpcDo(
        "set_note_v3",
        JSONObject().put("p_body", text).put("p_color", color ?: JSONObject.NULL).put("p_song", song ?: JSONObject.NULL)
            .put("p_song_id", songId ?: JSONObject.NULL).put("p_lyric", lyric ?: JSONObject.NULL)
    )

    /** Catatan dengan potongan lirik panjang. Kalau SQL tahap 32 belum dipasang, jatuh ke v3 dengan satu baris saja. */
    suspend fun setNoteV4(text: String, color: String?, song: String?, songId: String?, lyric: String?): String? {
        val err = rpcDo(
            "set_note_v4",
            JSONObject().put("p_body", text).put("p_color", color ?: JSONObject.NULL).put("p_song", song ?: JSONObject.NULL)
                .put("p_song_id", songId ?: JSONObject.NULL).put("p_lyric", lyric ?: JSONObject.NULL)
        ) ?: return null
        if (!err.contains("set_note_v4", true) && !err.contains("function", true)) return err
        val first = lyric?.lines()?.firstOrNull()?.substringAfter('|')?.take(80)
        return setNoteV3(text, color, song, songId, first)
    }

    class MyNote(val body: String, val color: String?, val song: String?, val songId: String?, val lyric: String?)

    /** Catatan milik sendiri (masih berlaku 24 jam). null = tidak ada atau SQL tahap 32 belum dipasang. */
    suspend fun myNote(): MyNote? {
        val r = rpc("my_note").value ?: return null
        if (r.trim() == "null" || r.isBlank()) return null
        return try {
            val o = JSONObject(r)
            MyNote(o.optString("body"), o.s("color"), o.s("song"), o.s("song_id"), o.s("lyric"))
        } catch (_: Exception) { null }
    }

    suspend fun setNoteV2(text: String, color: String?, song: String?): String? = rpcDo(
        "set_note_v2",
        JSONObject().put("p_body", text).put("p_color", color ?: JSONObject.NULL).put("p_song", song ?: JSONObject.NULL)
    )

    suspend fun setPresence(songId: String?, title: String?, artist: String?, thumb: String?, playing: Boolean) {
        rpc(
            "set_presence",
            JSONObject()
                .put("p_song_id", songId ?: JSONObject.NULL).put("p_title", title ?: JSONObject.NULL)
                .put("p_artist", artist ?: JSONObject.NULL).put("p_thumb", thumb ?: JSONObject.NULL)
                .put("p_playing", playing)
        )
    }

    // ------------------------------------------------------------------ chat teman

    suspend fun dmThread(userId: String): Res<List<DmMessage>> =
        rpc("dm_thread", JSONObject().put("p_with", userId).put("p_limit", 100)).parse { text ->
            val a = JSONArray(text)
            (0 until a.length()).mapNotNull { a.optJSONObject(it) }.map {
                DmMessage(it.optLong("id"), it.optBoolean("mine"), it.optString("body"), it.optString("created_at"), it.optBoolean("read"))
            }
        }

    suspend fun dmSend(userId: String, body: String): String? =
        rpcDo("dm_send", JSONObject().put("p_to", userId).put("p_body", body))

    suspend fun dmTypingSet(userId: String, on: Boolean = true) { rpc("dm_typing_set", JSONObject().put("p_to", userId).put("p_on", on)) }

    suspend fun dmTyping(userId: String): Boolean =
        rpc("dm_typing_get", JSONObject().put("p_with", userId)).value?.trim() == "true"

    suspend fun dmMarkRead(userId: String) { rpc("dm_mark_read", JSONObject().put("p_with", userId)) }

    // ------------------------------------------------------------------ chat global

    suspend fun chat(): Res<ChatSnapshot> = rpc("chat_global_list", JSONObject().put("p_limit", 60)).parse { text ->
        val o = JSONObject(text)
        val a = o.optJSONArray("messages") ?: JSONArray()
        val info = o.optJSONObject("info")
        val pin = o.optJSONObject("pinned")
        ChatSnapshot(
            messages = (0 until a.length()).mapNotNull { a.optJSONObject(it) }.map {
                ChatMessage(
                    it.optLong("id"), it.optString("user_id"), it.optString("name"), it.s("username"), it.s("avatar"),
                    it.optString("body"), it.optString("created_at"), it.optBoolean("owner")
                )
            },
            pinned = pin?.let { ChatPinned(it.optLong("id"), it.optString("name"), it.optString("body")) },
            info = ChatInfo(
                enabled = info?.optBoolean("enabled", true) ?: true, slow = info?.optInt("slow") ?: 0,
                maxLen = info?.optInt("max_len", 300) ?: 300, muted = info?.optBoolean("muted") ?: false
            )
        )
    }

    suspend fun chatSend(body: String): String? = rpcDo("chat_global_send", JSONObject().put("p_body", body))
    suspend fun chatReport(id: Long): String? = rpcDo("chat_report", JSONObject().put("p_id", id))
}
