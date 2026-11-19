package com.jimz.jimztune.data.lyrics

import com.jimz.jimztune.data.search.Http
import com.jimz.jimztune.data.search.arr
import com.jimz.jimztune.data.search.obj
import com.jimz.jimztune.data.search.objects
import org.json.JSONObject
import java.text.SimpleDateFormat
import java.util.Date
import java.util.Locale

/** Cadangan terakhir: lirik biasa (tanpa sinkronisasi) dari YouTube Music. */
class YouTubeMusicLyricsProvider : LyricsProvider {
    override val name = "YouTube Music"

    private val base = "https://music.youtube.com/youtubei/v1"
    private val headers = mapOf(
        "Origin" to "https://music.youtube.com",
        "Referer" to "https://music.youtube.com/",
        "X-Goog-Api-Format-Version" to "1",
        "Cookie" to "SOCS=CAI"
    )

    private fun context(): JSONObject {
        val version = "1." + SimpleDateFormat("yyyyMMdd", Locale.US).format(Date()) + ".01.00"
        return JSONObject().put(
            "client",
            JSONObject().put("clientName", "WEB_REMIX").put("clientVersion", version).put("hl", "id").put("gl", "ID")
        )
    }

    override suspend fun fetch(q: LyricsQuery): List<LyricsCandidate> {
        val next = JSONObject(
            Http.postJson(
                "$base/next?prettyPrint=false",
                JSONObject().put("context", context()).put("videoId", q.videoId).toString(),
                headers
            )
        )
        val tabs = next.obj(
            "contents", "singleColumnMusicWatchNextResultsRenderer", "tabbedRenderer",
            "watchNextTabbedResultsRenderer"
        )?.arr("tabs")?.objects().orEmpty()
        val browseId = tabs.getOrNull(1)?.obj("tabRenderer", "endpoint", "browseEndpoint")
            ?.optString("browseId")?.takeIf { it.startsWith("MPLY") } ?: return emptyList()

        val browse = JSONObject(
            Http.postJson(
                "$base/browse?prettyPrint=false",
                JSONObject().put("context", context()).put("browseId", browseId).toString(),
                headers
            )
        )
        val text = browse.obj("contents", "sectionListRenderer")?.arr("contents")?.objects()?.firstOrNull()
            ?.obj("musicDescriptionShelfRenderer", "description")?.arr("runs")?.objects()?.firstOrNull()
            ?.optString("text").orEmpty()
        val lyrics = if (text.isNotBlank()) LrcParser.plain(text, name) else null
        return listOfNotNull(lyrics?.let { LyricsCandidate(it, q.title, q.artist, 0, Trust.EXACT_ID) })
    }
}
