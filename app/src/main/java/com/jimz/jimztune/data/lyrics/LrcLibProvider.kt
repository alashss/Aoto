package com.jimz.jimztune.data.lyrics

import com.jimz.jimztune.data.search.Http
import com.jimz.jimztune.data.search.objects
import com.jimz.jimztune.data.search.str
import kotlinx.coroutines.CancellationException
import org.json.JSONArray
import org.json.JSONObject
import java.net.URLEncoder

/** LrcLib: gratis, tanpa iklan. Mengembalikan beberapa kandidat; yang cocok dipilih LyricsMatch. */
class LrcLibProvider : LyricsProvider {
    override val name = "LrcLib"

    private val headers = mapOf("User-Agent" to "Aoto/1.1 (personal use)")

    private fun enc(s: String) = URLEncoder.encode(s, "UTF-8")

    private fun toCandidate(o: JSONObject): LyricsCandidate? {
        if (o.optBoolean("instrumental", false)) return null
        val synced = o.str("syncedLyrics")
        val lyrics = (if (synced.isNotEmpty()) LrcParser.parse(synced, name) else null)
            ?: o.str("plainLyrics").takeIf { it.isNotEmpty() }?.let { LrcParser.plain(it, name) }
            ?: return null
        return LyricsCandidate(
            lyrics = lyrics,
            title = o.str("trackName").ifEmpty { o.str("name") },
            artist = o.str("artistName"),
            durationSec = o.optDouble("duration", 0.0).toInt()
        )
    }

    override suspend fun fetch(q: LyricsQuery): List<LyricsCandidate> {
        val out = ArrayList<LyricsCandidate>()
        val title = LyricsMatch.searchTitle(q.title)

        // 1. Pencocokan tepat (judul + artis + durasi).
        if (q.artist.isNotEmpty()) {
            try {
                var url = "https://lrclib.net/api/get?track_name=${enc(title)}&artist_name=${enc(q.artist)}"
                if (q.durationSec > 0) url += "&duration=${q.durationSec}"
                toCandidate(JSONObject(Http.get(url, headers)))?.let { out += it }
            } catch (e: CancellationException) {
                throw e
            } catch (_: Exception) {
            }
        }

        // 2. Pencarian (judul+artis, lalu kata kunci bebas kalau masih kosong).
        val searches = buildList {
            if (q.artist.isNotEmpty()) add("https://lrclib.net/api/search?track_name=${enc(title)}&artist_name=${enc(q.artist)}")
            add("https://lrclib.net/api/search?q=${enc((title + " " + q.artist).trim())}")
            if (q.artist.isNotEmpty()) add("https://lrclib.net/api/search?q=${enc(title)}")
        }
        for (url in searches) {
            if (out.size >= 6) break
            try {
                JSONArray(Http.get(url, headers)).objects().forEach { o -> toCandidate(o)?.let { out += it } }
            } catch (e: CancellationException) {
                throw e
            } catch (_: Exception) {
            }
        }
        return out.take(20)
    }
}
