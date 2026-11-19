package com.jimz.jimztune.data.stream

import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.withContext
import okhttp3.OkHttpClient
import org.schabi.newpipe.extractor.NewPipe
import org.schabi.newpipe.extractor.ServiceList
import org.schabi.newpipe.extractor.localization.ContentCountry
import org.schabi.newpipe.extractor.localization.Localization
import org.schabi.newpipe.extractor.stream.DeliveryMethod
import org.schabi.newpipe.extractor.stream.StreamInfo
import java.io.IOException
import java.util.concurrent.TimeUnit
import com.jimz.jimztune.AppGraph

/** Sumber audio utama: NewPipeExtractor (v0.26.5 sudah berisi workaround SABR dari YouTube). */
class NewPipeStreamProvider : StreamProvider {
    override val name = "NewPipe"

    override suspend fun audioUrl(videoId: String): String = withContext(Dispatchers.IO) {
        ensureInit()
        val info = StreamInfo.getInfo(ServiceList.YouTube, "https://www.youtube.com/watch?v=$videoId")

        val audio = QualityPicker.pick(
            info.audioStreams.filter { it.deliveryMethod == DeliveryMethod.PROGRESSIVE_HTTP && it.isUrl },
            AppGraph.settings.quality
        ) { QualityPicker.normalize(it.averageBitrate) }
        if (audio != null) return@withContext audio.content

        // Beberapa video hanya punya stream gabungan (video + audio): ambil yang paling kecil, gambarnya diabaikan.
        val combined = info.videoStreams
            .filter { !it.isVideoOnly && it.deliveryMethod == DeliveryMethod.PROGRESSIVE_HTTP && it.isUrl }
            .minByOrNull { it.height }
        combined?.content ?: throw IOException("Tidak ada stream yang bisa diputar")
    }

    companion object {
        @Volatile
        private var ready = false

        @Synchronized
        fun ensureInit() {
            if (ready) return
            val client = OkHttpClient.Builder()
                .connectTimeout(10, TimeUnit.SECONDS)
                .readTimeout(15, TimeUnit.SECONDS)
                .build()
            NewPipe.init(NewPipeDownloader(client), Localization("id", "ID"), ContentCountry("ID"))
            ready = true
        }
    }
}
