package com.jimz.jimztune.data.search

import com.jimz.jimztune.data.model.SearchItem
import com.jimz.jimztune.data.model.SearchType
import org.json.JSONObject
import java.text.SimpleDateFormat
import java.util.Date
import java.util.Locale

/**
 * Pencarian YouTube biasa (bukan YouTube Music). Di sinilah remix, DJ, dan unggahan fans berada,
 * karena rak "Lagu" di YouTube Music hanya berisi rilisan resmi.
 */
class YouTubeWebProvider : SearchProvider {
    override val name = "YouTube"

    override suspend fun search(query: String, type: SearchType): List<SearchItem> {
        if (type != SearchType.SONG) throw UnsupportedTypeException("YouTube biasa hanya untuk lagu")
        val version = "2." + SimpleDateFormat("yyyyMMdd", Locale.US).format(Date()) + ".00.00"
        val body = JSONObject()
            .put(
                "context",
                JSONObject().put(
                    "client",
                    JSONObject().put("clientName", "WEB").put("clientVersion", version).put("hl", "id").put("gl", "ID")
                )
            )
            .put("query", query)
            .put("params", "EgIQAQ==") // filter: hanya video
            .toString()
        val json = JSONObject(
            Http.postJson(
                "https://www.youtube.com/youtubei/v1/search?prettyPrint=false",
                body,
                mapOf("Origin" to "https://www.youtube.com", "Cookie" to "SOCS=CAI")
            )
        )

        val items = mutableListOf<SearchItem>()
        for (v in json.findAll("videoRenderer")) {
            val id = v.optString("videoId").takeIf { it.isNotEmpty() } ?: continue
            val title = YtmParse.runsText(v.optJSONObject("title")).takeIf { it.isNotBlank() } ?: continue
            // Siaran langsung tidak punya durasi; lewati.
            val duration = v.obj("lengthText")?.optString("simpleText")?.takeIf { it.isNotBlank() }?.let { com.jimz.jimztune.data.model.normalizeDuration(it) } ?: continue
            val channel = YtmParse.runsText(v.optJSONObject("ownerText")).ifEmpty { YtmParse.runsText(v.optJSONObject("longBylineText")) }
            val thumb = v.obj("thumbnail")?.arr("thumbnails")?.objects()?.lastOrNull()?.optString("url")
                ?.takeIf { it.isNotEmpty() }
                ?: "https://i.ytimg.com/vi/$id/mqdefault.jpg"
            items.add(SearchItem(id, SearchType.SONG, title, channel, thumb, duration))
        }
        return items
    }
}
