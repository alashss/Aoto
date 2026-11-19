package com.jimz.jimztune.data.search

import com.jimz.jimztune.data.model.SearchItem
import com.jimz.jimztune.data.model.SearchType
import org.json.JSONObject
import java.net.URLEncoder

/**
 * Instance Piped publik. Mode [videos] memakai filter "videos" (bukan rak musik), jadi remix/DJ
 * yang tidak masuk katalog YouTube Music ikut ketemu.
 */
class PipedProvider(
    private val videos: Boolean = false,
    private val instances: List<String> = PublicInstances.piped
) : SearchProvider {
    override val name = if (videos) "Piped Video" else "Piped"

    override suspend fun search(query: String, type: SearchType): List<SearchItem> {
        if (videos && type != SearchType.SONG) throw UnsupportedTypeException("Filter video hanya untuk lagu")
        val filter = when {
            videos -> "videos"
            type == SearchType.SONG -> "music_songs"
            type == SearchType.ALBUM -> "music_albums"
            type == SearchType.PLAYLIST -> "playlists"
            else -> "music_artists"
        }
        val q = URLEncoder.encode(query, "UTF-8")
        return raceInstances(instances) { base ->
            val json = JSONObject(Http.get("$base/search?q=$q&filter=$filter"))
            json.arr("items").objects().mapNotNull { toItem(it, type) }
        }
    }

    private fun toItem(o: JSONObject, type: SearchType): SearchItem? {
        val url = o.optString("url")
        val id = when (type) {
            SearchType.SONG -> url.substringAfter("v=", "")
            SearchType.ALBUM, SearchType.PLAYLIST -> url.substringAfter("list=", "")
            SearchType.ARTIST -> url.substringAfterLast("/", "")
        }
        if (id.isEmpty()) return null
        val title = o.optString("title").ifEmpty { o.optString("name") }
        if (title.isEmpty()) return null
        val seconds = o.optLong("duration", 0)
        // Siaran langsung (durasi -1) dan Shorts tidak cocok untuk pemutar musik.
        if (type == SearchType.SONG && videos && seconds <= 0) return null
        return SearchItem(
            id = id,
            type = type,
            title = title,
            subtitle = o.optString("uploaderName"),
            thumbnailUrl = o.optString("thumbnail").ifEmpty { null },
            durationText = formatDuration(seconds)
        )
    }
}
