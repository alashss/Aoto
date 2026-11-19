package com.jimz.jimztune.data.lyrics

import com.jimz.jimztune.data.search.Http
import com.jimz.jimztune.data.search.str
import kotlinx.coroutines.CancellationException
import org.json.JSONObject
import java.net.URLEncoder

/**
 * lyrics.ovh: sumber cadangan untuk lirik biasa (tanpa waktu). Hanya dipakai kalau sumber bersinkron tidak menemukan apa-apa,
 * karena [LyricsRepository] selalu mengutamakan lirik bersinkron.
 */
class LyricsOvhProvider : LyricsProvider {
    override val name = "Lyrics.ovh"

    private fun enc(s: String) = URLEncoder.encode(s, "UTF-8").replace("+", "%20")

    override suspend fun fetch(q: LyricsQuery): List<LyricsCandidate> {
        // Butuh artis yang jelas; tanpa itu hasilnya hampir selalu lagu lain.
        if (q.artist.isBlank() || q.title.isBlank()) return emptyList()
        val title = LyricsMatch.searchTitle(q.title)
        return try {
            val root = JSONObject(Http.get("https://api.lyrics.ovh/v1/${enc(q.artist)}/${enc(title)}"))
            val text = root.str("lyrics").trim()
            if (text.isEmpty()) return emptyList()
            val lyrics = LrcParser.plain(text, name) ?: return emptyList()
            listOf(
                LyricsCandidate(
                    lyrics = lyrics,
                    title = q.title,
                    artist = q.artist,
                    durationSec = q.durationSec
                )
            )
        } catch (e: CancellationException) {
            throw e
        } catch (_: Exception) {
            emptyList()
        }
    }
}
