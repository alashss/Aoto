package com.jimz.jimztune.data.stream

import com.jimz.jimztune.AppGraph
import com.jimz.jimztune.data.search.Http
import com.jimz.jimztune.data.search.arr
import com.jimz.jimztune.data.search.obj
import com.jimz.jimztune.data.search.objects
import org.json.JSONObject
import java.io.IOException

/**
 * Mengambil URL audio dari API player YouTube memakai klien resmi tertentu (iOS, Android, YouTube Music).
 * Tiap klien punya perilaku berbeda, jadi kalau satu ditolak YouTube yang lain sering masih jalan.
 * Hanya URL langsung (tanpa enkripsi tanda tangan) yang dipakai.
 */
class InnerTubeClientStreamProvider(private val client: Client) : StreamProvider {
    override val name = client.label

    enum class Client(
        val label: String,
        val clientName: String,
        val clientNumber: String,
        val version: String,
        val userAgent: String,
        val host: String,
        val extra: Map<String, Any>
    ) {
        IOS(
            "YouTube iOS", "IOS", "5", "20.10.4",
            "com.google.ios.youtube/20.10.4 (iPhone16,2; U; CPU iOS 18_3_2 like Mac OS X;)",
            "https://www.youtube.com",
            mapOf("deviceMake" to "Apple", "deviceModel" to "iPhone16,2", "osName" to "iPhone", "osVersion" to "18.3.2.22D82")
        ),
        ANDROID(
            "YouTube Android", "ANDROID", "3", "20.10.38",
            "com.google.android.youtube/20.10.38 (Linux; U; Android 11) gzip",
            "https://www.youtube.com",
            mapOf("osName" to "Android", "osVersion" to "11", "androidSdkVersion" to 30)
        ),
        MUSIC(
            "YouTube Music", "ANDROID_MUSIC", "21", "7.27.52",
            "com.google.android.apps.youtube.music/7.27.52 (Linux; U; Android 11) gzip",
            "https://music.youtube.com",
            mapOf("osName" to "Android", "osVersion" to "11", "androidSdkVersion" to 30)
        )
    }

    override suspend fun audioUrl(videoId: String): String {
        val c = JSONObject()
            .put("clientName", client.clientName)
            .put("clientVersion", client.version)
            .put("hl", "en")
            .put("gl", "US")
        client.extra.forEach { (k, v) -> c.put(k, v) }
        val body = JSONObject()
            .put("context", JSONObject().put("client", c))
            .put("videoId", videoId)
            .put("contentCheckOk", true)
            .put("racyCheckOk", true)
            .toString()

        val json = JSONObject(
            Http.postJson(
                "${client.host}/youtubei/v1/player?prettyPrint=false",
                body,
                mapOf(
                    "User-Agent" to client.userAgent,
                    "X-YouTube-Client-Name" to client.clientNumber,
                    "X-YouTube-Client-Version" to client.version,
                    "Origin" to client.host
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
}
