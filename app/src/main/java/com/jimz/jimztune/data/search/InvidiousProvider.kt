package com.jimz.jimztune.data.search

import com.jimz.jimztune.data.model.SearchItem
import com.jimz.jimztune.data.model.SearchType
import org.json.JSONArray
import java.net.URLEncoder

/** Instance Invidious publik. Hanya mendukung pencarian lagu (video). */
class InvidiousProvider(
    private val instances: List<String> = PublicInstances.invidious
) : SearchProvider {
    override val name = "Invidious"

    override suspend fun search(query: String, type: SearchType): List<SearchItem> {
        if (type != SearchType.SONG) throw UnsupportedTypeException("Invidious hanya untuk lagu")
        val q = URLEncoder.encode(query, "UTF-8")
        return raceInstances(instances) { base ->
            val array = JSONArray(Http.get("$base/api/v1/search?q=$q&type=video&page=1"))
            array.objects().filter { it.optString("type") == "video" }.mapNotNull { o ->
                val id = o.optString("videoId")
                val title = o.optString("title")
                val seconds = o.optLong("lengthSeconds", 0)
                if (id.isEmpty() || title.isEmpty() || seconds <= 0) return@mapNotNull null
                // Thumbnail dibangun dari id: alamat instance bisa mati, alamat i.ytimg.com tidak.
                SearchItem(
                    id = id,
                    type = SearchType.SONG,
                    title = title,
                    subtitle = o.optString("author"),
                    thumbnailUrl = "https://i.ytimg.com/vi/$id/mqdefault.jpg",
                    durationText = formatDuration(seconds)
                )
            }
        }
    }
}
