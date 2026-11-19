package com.jimz.jimztune.data.lyrics

import com.jimz.jimztune.data.search.Http
import com.jimz.jimztune.data.search.objects
import com.jimz.jimztune.data.search.str
import org.json.JSONObject

/** Lirik berdasarkan id video YouTube (SimpMusic): cocok persis dengan video yang diputar. */
class SimpMusicProvider : LyricsProvider {
    override val name = "SimpMusic"

    override suspend fun fetch(q: LyricsQuery): List<LyricsCandidate> {
        val root = JSONObject(Http.get("https://api-lyrics.simpmusic.org/v1/${q.videoId}"))
        val items = root.optJSONArray("data")?.objects() ?: listOfNotNull(root.optJSONObject("data"))
        return items.mapNotNull { d ->
            val synced = d.str("syncedLyrics")
            val plain = d.str("plainLyric").ifEmpty { d.str("plainLyrics") }
            val lyrics = (if (synced.isNotEmpty()) LrcParser.parse(synced, name) else null)
                ?: plain.takeIf { it.isNotEmpty() }?.let { LrcParser.plain(it, name) }
                ?: return@mapNotNull null
            LyricsCandidate(
                lyrics = lyrics,
                title = d.str("songTitle"),
                artist = d.str("artistName"),
                durationSec = d.optInt("durationSeconds", 0),
                trust = Trust.EXACT_ID
            )
        }
    }
}
