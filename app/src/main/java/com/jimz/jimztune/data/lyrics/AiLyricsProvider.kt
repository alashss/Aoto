package com.jimz.jimztune.data.lyrics

import com.jimz.jimztune.AppGraph
import com.jimz.jimztune.data.cloud.CloudApi
import org.json.JSONObject

/**
 * Pilihan TERAKHIR: dipakai hanya kalau semua sumber lirik gagal. Meminta Edge Function `ai-lyrics`
 * (kunci AI hanya ada di server) memperkirakan lirik. Hasilnya diberi label "AI" dan tidak bersinkron:
 * bisa tidak akurat, dan server boleh menjawab kosong kalau tidak yakin. Butuh login; ada batas harian per akun.
 */
class AiLyricsProvider {
    suspend fun fetch(q: LyricsQuery): Lyrics? {
        if (!CloudApi.configured || !AppGraph.auth.isLoggedIn) return null
        val token = AppGraph.auth.validToken() ?: return null
        val body = JSONObject().put("video_id", q.videoId).put("title", q.title).put("artist", q.artist)
        val r = CloudApi.json("POST", "/functions/v1/ai-lyrics", body.toString(), token)
        if (!r.ok) return null
        val text = try { JSONObject(r.body).optString("lyrics").trim() } catch (_: Exception) { "" }
        if (text.length < 40) return null
        return LrcParser.plain(text, SOURCE)
    }

    companion object {
        const val SOURCE = "AI"
    }
}
