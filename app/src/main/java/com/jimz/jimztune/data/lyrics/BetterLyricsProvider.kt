package com.jimz.jimztune.data.lyrics

import com.jimz.jimztune.data.search.Http
import com.jimz.jimztune.data.search.str
import kotlinx.coroutines.CancellationException
import org.json.JSONObject
import java.net.URLEncoder

/**
 * BetterLyrics: lirik TTML gaya Apple Music dengan waktu per suku kata, sumber yang dipakai ArchiveTune
 * untuk tampilan karaoke yang pas. Server mencocokkan lagunya sendiri, jadi hasilnya diperiksa dari panjang lirik.
 */
class BetterLyricsProvider : LyricsProvider {
    override val name = "BetterLyrics"

    private fun enc(s: String) = URLEncoder.encode(s, "UTF-8")

    override suspend fun fetch(q: LyricsQuery): List<LyricsCandidate> {
        val title = LyricsMatch.searchTitle(q.title)
        var url = "https://lyrics-api.boidu.dev/getLyrics?s=${enc(title)}&a=${enc(q.artist)}"
        if (q.durationSec > 0) url += "&d=${q.durationSec}"
        val ttml = try {
            val json = JSONObject(Http.get(url))
            json.str("ttml").ifEmpty { json.str("lyrics") }
        } catch (e: CancellationException) {
            throw e
        } catch (_: Exception) {
            ""
        }
        val lyrics = TtmlParser.parse(ttml, name) ?: return emptyList()
        return listOf(LyricsCandidate(lyrics, q.title, q.artist, 0, Trust.BLIND))
    }
}
