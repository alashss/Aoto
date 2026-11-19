package com.jimz.jimztune.data.stream

import com.jimz.jimztune.data.search.Http
import com.jimz.jimztune.data.search.arr
import com.jimz.jimztune.data.search.obj
import com.jimz.jimztune.data.search.objects
import org.json.JSONObject
import java.io.IOException
import com.jimz.jimztune.AppGraph

/**
 * Mengambil URL audio langsung dari API player YouTube memakai klien ANDROID_VR.
 * Klien ini mengembalikan URL langsung (tanpa enkripsi tanda tangan), jadi tidak bergantung pada NewPipe.
 */
class InnerTubeStreamProvider : StreamProvider {
    override val name = "YouTube VR"

    override suspend fun audioUrl(videoId: String): String {
        val client = JSONObject()
            .put("clientName", "ANDROID_VR")
            .put("clientVersion", VERSION)
            .put("deviceMake", "Oculus")
            .put("deviceModel", "Quest 3")
            .put("osName", "Android")
            .put("osVersion", "12")
            .put("androidSdkVersion", 32)
            .put("hl", "en")
            .put("gl", "US")
        val body = JSONObject()
            .put("context", JSONObject().put("client", client))
            .put("videoId", videoId)
            .put("contentCheckOk", true)
            .put("racyCheckOk", true)
            .toString()

        val json = JSONObject(
            Http.postJson(
                "https://www.youtube.com/youtubei/v1/player?prettyPrint=false",
                body,
                mapOf(
                    "User-Agent" to USER_AGENT,
                    "X-YouTube-Client-Name" to "28",
                    "X-YouTube-Client-Version" to VERSION,
                    "Origin" to "https://www.youtube.com"
                )
            )
        )

        val playability = json.obj("playabilityStatus")
        val status = playability?.optString("status").orEmpty()
        if (status != "OK") {
            throw IOException("Tidak bisa diputar ($status ${playability?.optString("reason").orEmpty()})".trim())
        }

        LoudnessStore.capture(videoId, json)
        val audio = json.obj("streamingData")?.arr("adaptiveFormats")?.objects().orEmpty()
            .filter { it.optString("mimeType").startsWith("audio/") && it.optString("url").isNotEmpty() }
        val best = QualityPicker.pick(
            audio.filter { it.optString("mimeType").startsWith("audio/mp4") }.ifEmpty { audio },
            AppGraph.settings.quality
        ) { it.optInt("bitrate") } ?: throw IOException("Tidak ada stream audio langsung")
        return best.getString("url")
    }

    companion object {
        const val VERSION = "1.60.19"
        const val USER_AGENT =
            "com.google.android.apps.youtube.vr.oculus/1.60.19 (Linux; U; Android 12; en_US; Quest 3; Build/SQ3A.220605.009.A1; Cronet/132.0.6808.3)"
    }
}
