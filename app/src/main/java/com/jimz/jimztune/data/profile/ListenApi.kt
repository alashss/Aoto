package com.jimz.jimztune.data.profile

import com.jimz.jimztune.AppGraph
import com.jimz.jimztune.data.cloud.CloudApi
import org.json.JSONArray
import org.json.JSONObject

/** Statistik dengar & riwayat putar milik user yang sedang login, dibaca dari server (RPC my_stats / my_play_history). */
object ListenApi {
    data class Stats(
        val totalSeconds: Long,
        val level: Int,
        val plays: Int,
        val todaySeconds: Long,
        val weekSeconds: Long,
        val activeDays: Int,
        val topArtist: String?
    )

    data class HistoryItem(
        val id: Long,
        val songId: String,
        val title: String,
        val artist: String,
        val thumbnail: String?,
        val playedAt: String,
        val seconds: Int
    )

    suspend fun stats(): Stats? {
        val token = AppGraph.auth.validToken() ?: return null
        val r = CloudApi.json("POST", "/rest/v1/rpc/my_stats", "{}", token)
        if (!r.ok) return null
        return try {
            val o = JSONObject(r.body)
            Stats(
                totalSeconds = o.optLong("total_seconds"),
                level = o.optInt("level", 1),
                plays = o.optInt("plays"),
                todaySeconds = o.optLong("today_seconds"),
                weekSeconds = o.optLong("week_seconds"),
                activeDays = o.optInt("active_days"),
                topArtist = if (o.isNull("top_artist")) null else o.optString("top_artist").takeIf { it.isNotBlank() }
            )
        } catch (_: Exception) {
            null
        }
    }

    suspend fun history(limit: Int, offset: Int): List<HistoryItem>? {
        val token = AppGraph.auth.validToken() ?: return null
        val r = CloudApi.json(
            "POST", "/rest/v1/rpc/my_play_history",
            JSONObject().put("p_limit", limit).put("p_offset", offset).toString(), token
        )
        if (!r.ok) return null
        return try {
            val a = JSONArray(r.body)
            (0 until a.length()).mapNotNull { a.optJSONObject(it) }.map { o ->
                HistoryItem(
                    id = o.optLong("id"),
                    songId = o.optString("song_id"),
                    title = o.optString("title"),
                    artist = o.optString("artist"),
                    thumbnail = if (o.isNull("thumbnail")) null else o.optString("thumbnail").takeIf { it.isNotBlank() },
                    playedAt = o.optString("played_at"),
                    seconds = o.optInt("seconds")
                )
            }
        } catch (_: Exception) {
            null
        }
    }
}
