package com.jimz.jimztune.data.search

import com.jimz.jimztune.data.model.SearchItem
import com.jimz.jimztune.data.model.SearchType
import com.jimz.jimztune.data.stream.NewPipeStreamProvider
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.withContext
import org.schabi.newpipe.extractor.ServiceList
import org.schabi.newpipe.extractor.search.SearchInfo
import org.schabi.newpipe.extractor.stream.StreamInfoItem

/** Pencarian lewat NewPipeExtractor (pustaka yang sama dengan pemutar). Mencari di semua video YouTube. */
class NewPipeSearchProvider : SearchProvider {
    override val name = "NewPipe"

    override suspend fun search(query: String, type: SearchType): List<SearchItem> {
        if (type != SearchType.SONG) throw UnsupportedTypeException("NewPipe hanya untuk lagu")
        return withContext(Dispatchers.IO) {
            NewPipeStreamProvider.ensureInit()
            val service = ServiceList.YouTube
            val handler = service.searchQHFactory.fromQuery(query, listOf("videos"), "")
            val info = SearchInfo.getInfo(service, handler)
            info.relatedItems.filterIsInstance<StreamInfoItem>().mapNotNull { s ->
                val id = s.url.substringAfter("v=", "").substringBefore("&")
                if (id.isEmpty() || s.duration <= 0) return@mapNotNull null
                SearchItem(
                    id = id,
                    type = SearchType.SONG,
                    title = s.name,
                    subtitle = s.uploaderName.orEmpty(),
                    thumbnailUrl = s.thumbnails.lastOrNull()?.url ?: "https://i.ytimg.com/vi/$id/mqdefault.jpg",
                    durationText = formatDuration(s.duration)
                )
            }
        }
    }
}
