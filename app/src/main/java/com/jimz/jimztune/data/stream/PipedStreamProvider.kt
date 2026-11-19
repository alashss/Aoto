package com.jimz.jimztune.data.stream

import com.jimz.jimztune.data.search.Http
import com.jimz.jimztune.data.search.PublicInstances
import com.jimz.jimztune.data.search.arr
import com.jimz.jimztune.data.search.objects
import kotlinx.coroutines.CancellationException
import org.json.JSONObject
import java.io.IOException
import com.jimz.jimztune.AppGraph

class PipedStreamProvider(
    private val instances: List<String> = PublicInstances.piped
) : StreamProvider {
    override val name = "Piped"

    override suspend fun audioUrl(videoId: String): String {
        var last: Exception? = null
        for (base in instances.shuffled()) {
            try {
                val json = JSONObject(Http.get("$base/streams/$videoId"))
                val best = QualityPicker.pick(
                    json.arr("audioStreams").objects().filter { it.optString("url").isNotEmpty() },
                    AppGraph.settings.quality
                ) { it.optInt("bitrate", 0) } ?: throw IOException("Tidak ada stream audio")
                return best.getString("url")
            } catch (e: CancellationException) {
                throw e
            } catch (e: Exception) {
                last = e
            }
        }
        throw last ?: IOException("Tidak ada instance Piped")
    }
}
