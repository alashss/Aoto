package com.jimz.jimztune.data.owner

import com.jimz.jimztune.AppGraph
import com.jimz.jimztune.data.cloud.CloudApi
import org.json.JSONArray
import org.json.JSONObject

data class AdminChatMsg(
    val id: Long, val userId: String, val name: String, val username: String?, val body: String,
    val createdAt: String, val deleted: Boolean, val pinned: Boolean, val reports: Int, val muted: Boolean
)

data class AdminMute(val userId: String, val name: String, val username: String?, val until: String?, val reason: String)

data class ChatRules(
    val globalEnabled: Boolean = true, val dmEnabled: Boolean = true, val slowSeconds: Int = 3,
    val blockLinks: Boolean = true, val maxLen: Int = 300, val badWordMode: String = "mask"
)

/** Moderasi chat global untuk owner. Semua dijaga is_owner() di server. */
object OwnerChatApi {
    private suspend fun call(method: String, path: String, body: String? = null): Res<String> {
        if (!CloudApi.configured) return Res(null, "Server belum diatur")
        val t = AppGraph.auth.validToken() ?: return Res(null, "Sesi habis, masuk lagi ya")
        val r = CloudApi.json(method, path, body, t, mapOf("Prefer" to "return=representation"))
        return if (r.ok) Res(r.body.ifBlank { "null" }) else Res(null, CloudApi.errorText(r))
    }

    private suspend fun rpc(name: String, body: JSONObject = JSONObject()) = call("POST", "/rest/v1/rpc/$name", body.toString())

    private fun <T> Res<String>.parse(block: (String) -> T): Res<T> {
        val text = value ?: return Res(null, error ?: "Gagal memuat")
        return try { Res(block(text)) } catch (e: Exception) { Res(null, "Respons tidak dikenali") }
    }

    private fun JSONObject.s(key: String): String? = if (isNull(key)) null else optString(key).takeIf { it.isNotBlank() }

    suspend fun messages(limit: Int = 100): Res<List<AdminChatMsg>> =
        rpc("owner_chat_list", JSONObject().put("p_limit", limit)).parse { text ->
            val a = JSONArray(text)
            (0 until a.length()).mapNotNull { a.optJSONObject(it) }.map {
                AdminChatMsg(
                    it.optLong("id"), it.optString("user_id"), it.optString("name"), it.s("username"), it.optString("body"),
                    it.optString("created_at"), it.optBoolean("deleted"), it.optBoolean("pinned"), it.optInt("reports"), it.optBoolean("muted")
                )
            }
        }

    suspend fun delete(id: Long): String? = rpc("owner_chat_delete", JSONObject().put("p_id", id)).error
    suspend fun deleteUser(userId: String): String? = rpc("owner_chat_delete_user", JSONObject().put("p_user", userId)).error
    suspend fun pin(id: Long, pin: Boolean): String? = rpc("owner_chat_pin", JSONObject().put("p_id", id).put("p_pin", pin)).error
    suspend fun clear(): String? = rpc("owner_chat_clear").error

    /** [minutes] <= 0 berarti selamanya. */
    suspend fun mute(userId: String, minutes: Int, reason: String = ""): String? =
        rpc("owner_chat_mute", JSONObject().put("p_user", userId).put("p_minutes", minutes).put("p_reason", reason)).error

    suspend fun unmute(userId: String): String? = rpc("owner_chat_unmute", JSONObject().put("p_user", userId)).error

    suspend fun mutes(): Res<List<AdminMute>> = rpc("owner_chat_mutes").parse { text ->
        val a = JSONArray(text)
        (0 until a.length()).mapNotNull { a.optJSONObject(it) }.map {
            AdminMute(it.optString("user_id"), it.optString("name"), it.s("username"), it.s("until"), it.optString("reason"))
        }
    }

    suspend fun rules(): Res<ChatRules> = call("GET", "/rest/v1/chat_settings?id=eq.1&select=*").parse { text ->
        val a = JSONArray(text)
        val o = a.optJSONObject(0) ?: return@parse ChatRules()
        ChatRules(
            o.optBoolean("global_enabled", true), o.optBoolean("dm_enabled", true), o.optInt("slow_seconds", 3),
            o.optBoolean("block_links", true), o.optInt("max_len", 300), o.optString("bad_word_mode", "mask")
        )
    }

    suspend fun saveRules(r: ChatRules): String? {
        val body = JSONObject().put("id", 1).put("global_enabled", r.globalEnabled).put("dm_enabled", r.dmEnabled)
            .put("slow_seconds", r.slowSeconds).put("block_links", r.blockLinks).put("max_len", r.maxLen)
            .put("bad_word_mode", r.badWordMode).put("updated_at", java.time.Instant.now().toString())
        val t = AppGraph.auth.validToken() ?: return "Sesi habis, masuk lagi ya"
        val resp = CloudApi.json(
            "POST", "/rest/v1/chat_settings?on_conflict=id", body.toString(), t,
            mapOf("Prefer" to "resolution=merge-duplicates,return=minimal")
        )
        return if (resp.ok) null else CloudApi.errorText(resp)
    }

    suspend fun words(): Res<List<String>> = call("GET", "/rest/v1/banned_words?select=word&order=word.asc").parse { text ->
        val a = JSONArray(text)
        (0 until a.length()).mapNotNull { a.optJSONObject(it)?.optString("word") }
    }

    suspend fun addWord(word: String): String? {
        val w = word.trim().lowercase()
        if (w.length < 2) return "Kata minimal 2 huruf"
        val t = AppGraph.auth.validToken() ?: return "Sesi habis, masuk lagi ya"
        val r = CloudApi.json(
            "POST", "/rest/v1/banned_words?on_conflict=word", JSONObject().put("word", w).toString(), t,
            mapOf("Prefer" to "resolution=ignore-duplicates,return=minimal")
        )
        return if (r.ok) null else CloudApi.errorText(r)
    }

    suspend fun removeWord(word: String): String? {
        val t = AppGraph.auth.validToken() ?: return "Sesi habis, masuk lagi ya"
        val enc = java.net.URLEncoder.encode(word, "UTF-8")
        val r = CloudApi.json("DELETE", "/rest/v1/banned_words?word=eq.$enc", null, t, mapOf("Prefer" to "return=minimal"))
        return if (r.ok) null else CloudApi.errorText(r)
    }
}
