package com.jimz.jimztune.data.search

import com.jimz.jimztune.data.model.SearchItem
import com.jimz.jimztune.data.model.SearchType
import kotlinx.coroutines.CancellationException
import org.json.JSONObject
import java.io.IOException

/**
 * YouTube Music (API internal, sama seperti yang dipakai ArchiveTune). Mengambil beberapa halaman sekaligus
 * supaya daftarnya panjang. Mode [videosOnly] mencari di rak "Video" (tempat remix/DJ dan unggahan non-resmi).
 */
class InnerTubeProvider(private val videosOnly: Boolean = false) : SearchProvider {
    override val name = if (videosOnly) "YouTube Music Video" else "YouTube Music"

    private fun paramsFor(type: SearchType) = when {
        videosOnly -> "EgWKAQIQAWoMEA4QChADEAQQCRAF"
        type == SearchType.SONG -> "EgWKAQIIAWoMEA4QChADEAQQCRAF"
        type == SearchType.PLAYLIST -> "Eg-KAQwIABAAGAAgACgBMABqChAEEAMQCRAFEAo%3D"
        type == SearchType.ALBUM -> "EgWKAQIYAWoMEA4QChADEAQQCRAF"
        else -> "EgWKAQIgAWoMEA4QChADEAQQCRAF"
    }

    override suspend fun search(query: String, type: SearchType): List<SearchItem> {
        if (videosOnly && type != SearchType.SONG) throw UnsupportedTypeException("Rak video hanya untuk lagu")
        val body = JSONObject()
            .put("context", Ytm.context())
            .put("query", query)
            .put("params", paramsFor(type))
            .toString()
        val first = JSONObject(Http.postJson("${Ytm.BASE}/search?prettyPrint=false", body, Ytm.headers))

        val sections = first.obj("contents", "tabbedSearchResultsRenderer")
            ?.arr("tabs")?.objects()?.firstOrNull()
            ?.obj("tabRenderer", "content", "sectionListRenderer")
            ?.arr("contents")?.objects().orEmpty()

        val items = mutableListOf<SearchItem>()
        var token: String? = null
        for (section in sections) {
            val shelf = section.optJSONObject("musicShelfRenderer") ?: continue
            for (entry in shelf.arr("contents").objects()) {
                val renderer = entry.optJSONObject("musicResponsiveListItemRenderer") ?: continue
                YtmParse.parseListItem(renderer, type)?.let { items.add(it) }
            }
            if (token == null) token = nextToken(shelf.arr("continuations").objects())
        }
        if (items.isEmpty() && first.has("error")) throw IOException("YouTube Music menolak permintaan")

        // Halaman lanjutan: kalau gagal di tengah jalan, pakai yang sudah didapat.
        var pages = 1
        while (token != null && pages < MAX_PAGES) {
            try {
                val b = JSONObject().put("context", Ytm.context()).put("continuation", token).toString()
                val url = "${Ytm.BASE}/search?ctoken=$token&continuation=$token&type=next&prettyPrint=false"
                val next = JSONObject(Http.postJson(url, b, Ytm.headers))
                val shelf = next.obj("continuationContents", "musicShelfContinuation") ?: break
                for (entry in shelf.arr("contents").objects()) {
                    val renderer = entry.optJSONObject("musicResponsiveListItemRenderer") ?: continue
                    YtmParse.parseListItem(renderer, type)?.let { items.add(it) }
                }
                token = nextToken(shelf.arr("continuations").objects())
                pages++
            } catch (e: CancellationException) {
                throw e
            } catch (_: Exception) {
                break
            }
        }
        return items
    }

    private fun nextToken(continuations: List<JSONObject>): String? =
        continuations.firstOrNull()?.optJSONObject("nextContinuationData")
            ?.optString("continuation")?.takeIf { it.isNotEmpty() }

    suspend fun suggestions(query: String): List<String> {
        val body = JSONObject().put("context", Ytm.context()).put("input", query).toString()
        val json = JSONObject(
            Http.postJson("${Ytm.BASE}/music/get_search_suggestions?prettyPrint=false", body, Ytm.headers)
        )
        val result = mutableListOf<String>()
        for (section in json.arr("contents").objects()) {
            val contents = section.optJSONObject("searchSuggestionsSectionRenderer")
                ?.arr("contents")?.objects().orEmpty()
            for (c in contents) {
                val runs = c.obj("searchSuggestionRenderer", "suggestion")?.arr("runs")?.objects().orEmpty()
                val text = runs.joinToString("") { it.optString("text") }.trim()
                if (text.isNotEmpty()) result.add(text)
            }
        }
        return result
    }

    private companion object {
        const val MAX_PAGES = 3
    }
}
