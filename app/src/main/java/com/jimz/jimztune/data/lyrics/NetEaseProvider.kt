package com.jimz.jimztune.data.lyrics

import com.jimz.jimztune.data.search.Http
import com.jimz.jimztune.data.search.arr
import com.jimz.jimztune.data.search.obj
import com.jimz.jimztune.data.search.objects
import com.jimz.jimztune.data.search.str
import kotlinx.coroutines.CancellationException
import org.json.JSONObject
import java.net.URLEncoder

/** NetEase Cloud Music: koleksi besar, termasuk lirik per kata (YRC). Hanya lagu yang judul/artis/durasinya cocok yang diambil. */
class NetEaseProvider : LyricsProvider {
    override val name = "NetEase"

    private val headers = mapOf(
        "Referer" to "https://music.163.com/",
        "Cookie" to "os=pc; appver=2.9.7"
    )

    override suspend fun fetch(q: LyricsQuery): List<LyricsCandidate> {
        val keyword = URLEncoder.encode("${LyricsMatch.searchTitle(q.title)} ${q.artist}".trim(), "UTF-8")
        val search = JSONObject(
            Http.get("https://music.163.com/api/search/get/web?csrf_token=&s=$keyword&type=1&offset=0&limit=8", headers)
        )
        val songs = search.obj("result")?.arr("songs")?.objects().orEmpty()

        // Hanya 2 lagu paling cocok yang diambil liriknya (hemat permintaan).
        val picks = songs.mapNotNull { s ->
            val artist = s.arr("artists").objects().joinToString(", ") { it.str("name") }
            val dur = (s.optLong("duration") / 1000).toInt()
            val score = LyricsMatch.score(q, s.str("name"), artist, dur) ?: return@mapNotNull null
            Triple(s, score, artist to dur)
        }.sortedByDescending { it.second }.take(2)

        val out = ArrayList<LyricsCandidate>()
        for ((song, _, meta) in picks) {
            val lyrics = lyricsFor(song.getLong("id")) ?: continue
            out += LyricsCandidate(lyrics, song.str("name"), meta.first, meta.second)
        }
        return out
    }

    private suspend fun lyricsFor(id: Long): Lyrics? {
        // Utamakan format per kata (yrc). Kalau tidak ada, jatuh ke LRC per baris.
        try {
            val v1 = JSONObject(
                Http.get(
                    "https://music.163.com/api/song/lyric/v1?id=$id&cp=false&tv=0&lv=0&rv=0&kv=0&yv=0&ytv=0&yrv=0",
                    headers
                )
            )
            val yrc = v1.obj("yrc")?.str("lyric").orEmpty()
            if (yrc.isNotEmpty()) YrcParser.parse(yrc, name)?.let { return it }
            val lrcV1 = v1.obj("lrc")?.str("lyric").orEmpty()
            if (lrcV1.isNotEmpty()) LrcParser.parse(lrcV1, name)?.let { return it }
        } catch (e: CancellationException) {
            throw e
        } catch (_: Exception) {
        }
        return try {
            val lyric = JSONObject(Http.get("https://music.163.com/api/song/lyric?id=$id&lv=1&kv=1&tv=-1", headers))
            val lrc = lyric.obj("lrc")?.str("lyric").orEmpty()
            if (lrc.isNotEmpty()) LrcParser.parse(lrc, name) else null
        } catch (e: CancellationException) {
            throw e
        } catch (_: Exception) {
            null
        }
    }
}
