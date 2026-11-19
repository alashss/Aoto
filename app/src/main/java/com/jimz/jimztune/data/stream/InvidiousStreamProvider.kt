package com.jimz.jimztune.data.stream

import com.jimz.jimztune.data.search.Http
import com.jimz.jimztune.data.search.PublicInstances
import com.jimz.jimztune.data.search.arr
import com.jimz.jimztune.data.search.objects
import kotlinx.coroutines.CancellationException
import org.json.JSONObject
import java.io.IOException
import com.jimz.jimztune.AppGraph

class InvidiousStreamProvider(
    private val instances: List<String> = PublicInstances.invidious
) : StreamProvider {
    override val name = "Invidious"

    override suspend fun audioUrl(videoId: String): String {
        var last: Exception? = null
        for (base in instances.shuffled()) {
            try {
                val json = JSONObject(Http.get("$base/api/v1/videos/$videoId?local=true"))
                val best = QualityPicker.pick(
                    json.arr("adaptiveFormats").objects()
                        .filter { it.optString("type").startsWith("audio/") && it.optString("url").isNotEmpty() },
                    AppGraph.settings.quality
                ) { it.optString("bitrate").toIntOrNull() ?: 0 } ?: throw IOException("Tidak ada stream audio")
                val url = best.getString("url")
                return if (url.startsWith("/")) base + url else url
            } catch (e: CancellationException) {
                throw e
            } catch (e: Exception) {
                last = e
            }
        }
        throw last ?: IOException("Tidak ada instance Invidious")
    }
}
