package com.jimz.jimztune.data.lyrics

import android.util.Base64
import com.jimz.jimztune.data.search.Http
import com.jimz.jimztune.data.search.arr
import com.jimz.jimztune.data.search.obj
import com.jimz.jimztune.data.search.objects
import com.jimz.jimztune.data.search.str
import kotlinx.coroutines.CancellationException
import org.json.JSONObject
import java.net.URLEncoder

/**
 * KuGou: sumber yang dipakai ArchiveTune. Lirik per kata (KRC) kalau ada, kalau tidak LRC per baris.
 * Alur: cari lagu (hash) -> cari lirik untuk hash itu -> unduh.
 */
class KuGouProvider : LyricsProvider {
    override val name = "KuGou"

    private fun enc(s: String) = URLEncoder.encode(s, "UTF-8")

    override suspend fun fetch(q: LyricsQuery): List<LyricsCandidate> {
        val keyword = "${LyricsMatch.searchTitle(q.title)} ${q.artist}".trim()
        val search = JSONObject(
            Http.get("https://mobileservice.kugou.com/api/v3/search/song?version=9108&plat=0&pagesize=8&showtype=0&keyword=${enc(keyword)}")
        )
        val songs = search.obj("data")?.arr("info")?.objects().orEmpty()

        val picks = songs.mapNotNull { s ->
            val hash = s.str("hash").ifEmpty { return@mapNotNull null }
            val dur = s.optInt("duration", 0)
            val title = s.str("songname")
            val artist = s.str("singername")
            val score = LyricsMatch.score(q, title, artist, dur) ?: return@mapNotNull null
            arrayOf<Any>(hash, dur, title, artist, score)
        }.sortedByDescending { it[4] as Double }.take(2)

        val out = ArrayList<LyricsCandidate>()
        for (p in picks) {
            val hash = p[0] as String
            val dur = p[1] as Int
            val lyrics = lyricsFor(keyword, hash, dur) ?: continue
            out += LyricsCandidate(lyrics, p[2] as String, p[3] as String, dur)
        }
        return out
    }

    private suspend fun lyricsFor(keyword: String, hash: String, durationSec: Int): Lyrics? {
        val found = JSONObject(
            Http.get(
                "https://lyrics.kugou.com/search?ver=1&man=yes&client=pc&keyword=${enc(keyword)}" +
                    "&duration=${durationSec * 1000}&hash=$hash"
            )
        )
        val cand = found.arr("candidates").objects().firstOrNull() ?: return null
        val id = cand.str("id").ifEmpty { return null }
        val key = cand.str("accesskey").ifEmpty { return null }

        // KRC (per kata) dulu; kalau gagal dibuka, LRC biasa.
        try {
            val krc = download(id, key, "krc")
            if (krc != null) KrcDecoder.decode(krc)?.let { raw -> KrcParser.parse(raw, name)?.let { return it } }
        } catch (e: CancellationException) {
            throw e
        } catch (_: Exception) {
        }
        val lrc = download(id, key, "lrc") ?: return null
        val text = String(Base64.decode(lrc, Base64.DEFAULT), Charsets.UTF_8).removePrefix("\uFEFF")
        return LrcParser.parse(text, name)
    }

    private suspend fun download(id: String, key: String, fmt: String): String? {
        val r = JSONObject(
            Http.get("https://lyrics.kugou.com/download?ver=1&client=pc&id=$id&accesskey=$key&fmt=$fmt&charset=utf8")
        )
        return r.str("content").takeIf { it.isNotEmpty() }
    }
}
