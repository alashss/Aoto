package com.jimz.jimztune.data.search

import com.jimz.jimztune.data.model.SearchItem
import com.jimz.jimztune.data.model.SearchType
import org.json.JSONArray
import org.json.JSONObject
import java.text.SimpleDateFormat
import java.util.Date
import java.util.Locale

/** Cari semua objek dengan kunci tertentu di seluruh pohon JSON (struktur YouTube Music sering berubah-ubah). */
internal fun JSONObject.findAll(key: String, out: MutableList<JSONObject> = mutableListOf()): MutableList<JSONObject> {
    val it = keys()
    while (it.hasNext()) {
        val k = it.next()
        val v = opt(k)
        if (k == key && v is JSONObject) out.add(v)
        when (v) {
            is JSONObject -> v.findAll(key, out)
            is JSONArray -> for (i in 0 until v.length()) v.optJSONObject(i)?.findAll(key, out)
            else -> {}
        }
    }
    return out
}

/** Klien dan pemanggilan dasar YouTube Music (dipakai pencarian dan halaman artis/album). */
internal object Ytm {
    const val BASE = "https://music.youtube.com/youtubei/v1"

    val headers = mapOf(
        "Origin" to "https://music.youtube.com",
        "Referer" to "https://music.youtube.com/",
        "X-Goog-Api-Format-Version" to "1",
        "Cookie" to "SOCS=CAI"
    )

    fun context(): JSONObject {
        val version = "1." + SimpleDateFormat("yyyyMMdd", Locale.US).format(Date()) + ".01.00"
        return JSONObject().put(
            "client",
            JSONObject()
                .put("clientName", "WEB_REMIX")
                .put("clientVersion", version)
                .put("hl", "id")
                .put("gl", "ID")
        )
    }

    suspend fun browse(browseId: String): JSONObject {
        val body = JSONObject().put("context", context()).put("browseId", browseId).toString()
        return JSONObject(Http.postJson("$BASE/browse?prettyPrint=false", body, headers))
    }
}

internal object YtmParse {
    private val durationRegex = Regex("^\\d{1,3}[:.]\\d{2}([:.]\\d{2})?$")
    private val sizeRegex = Regex("=w\\d+-h\\d+")
    private val labels = setOf("lagu", "song", "video", "single", "album", "ep", "artis", "artist", "playlist")

    fun runsText(o: JSONObject?): String =
        o?.arr("runs")?.objects()?.joinToString("") { it.optString("text") }?.trim().orEmpty()

    /** Ambil url thumbnail terbesar di mana pun letaknya di dalam objek, lalu ubah ukurannya. */
    fun thumb(o: JSONObject?, px: Int = 226): String? {
        if (o == null) return null
        val array = deepArray(o, "thumbnails") ?: return null
        val url = array.objects().lastOrNull()?.optString("url")?.takeIf { it.isNotEmpty() } ?: return null
        val fixed = if (url.startsWith("//")) "https:$url" else url
        return fixed.replace(sizeRegex, "=w$px-h$px")
    }

    private fun deepArray(o: JSONObject, key: String): JSONArray? {
        val arr = o.optJSONArray(key)
        if (arr != null && arr.length() > 0) return arr
        val it = o.keys()
        while (it.hasNext()) {
            val v = o.opt(it.next())
            val hit = when (v) {
                is JSONObject -> deepArray(v, key)
                is JSONArray -> (0 until v.length()).firstNotNullOfOrNull { i -> v.optJSONObject(i)?.let { deepArray(it, key) } }
                else -> null
            }
            if (hit != null) return hit
        }
        return null
    }

    private fun browseIdOf(run: JSONObject): String? =
        run.obj("navigationEndpoint", "browseEndpoint")?.optString("browseId")?.takeIf { it.isNotEmpty() }

    /** Parser baris daftar (hasil pencarian, lagu populer artis, trek album). */
    fun parseListItem(
        r: JSONObject,
        type: SearchType,
        fallbackArtist: String? = null,
        fallbackThumb: String? = null
    ): SearchItem? {
        val cols = r.arr("flexColumns").objects()
        fun runs(i: Int): List<JSONObject> =
            cols.getOrNull(i)
                ?.obj("musicResponsiveListItemFlexColumnRenderer", "text")
                ?.arr("runs")?.objects()
                .orEmpty()

        val title = runs(0).joinToString("") { it.optString("text") }.trim().takeIf { it.isNotBlank() } ?: return null
        val subRuns = runs(1)
        var parts = subRuns.map { it.optString("text") }.filter { it.isNotBlank() && it.trim() != "•" }

        // Hasil video diawali label seperti "Video"; buang supaya yang tampil nama artisnya.
        if ((type == SearchType.SONG || type == SearchType.PLAYLIST) && parts.size >= 2 && parts.first().trim().lowercase() in labels) parts = parts.drop(1)

        var duration = parts.lastOrNull()?.takeIf { durationRegex.matches(it.trim()) }?.trim()?.replace('.', ':')
        if (duration == null) {
            // Trek album menaruh durasi di kolom tetap.
            duration = r.arr("fixedColumns").objects().firstOrNull()
                ?.obj("musicResponsiveListItemFixedColumnRenderer", "text")
                ?.arr("runs")?.objects()?.firstOrNull()?.optString("text")
                ?.takeIf { durationRegex.matches(it.trim()) }?.trim()?.replace('.', ':')
        }
        var subtitle = parts.filter { it.trim().replace('.', ':') != duration || !durationRegex.matches(it.trim()) }.joinToString(" • ")
        if (fallbackArtist != null && (subtitle.isBlank() || subtitle.any { it.isDigit() })) subtitle = fallbackArtist

        val id = when (type) {
            SearchType.SONG ->
                r.optJSONObject("playlistItemData")?.optString("videoId")?.takeIf { it.isNotEmpty() }
                    ?: r.obj(
                        "overlay", "musicItemThumbnailOverlayRenderer", "content",
                        "musicPlayButtonRenderer", "playNavigationEndpoint", "watchEndpoint"
                    )?.optString("videoId")?.takeIf { it.isNotEmpty() }
            else ->
                r.obj("navigationEndpoint", "browseEndpoint")?.optString("browseId")?.takeIf { it.isNotEmpty() }
        } ?: return null

        val artistId = subRuns.mapNotNull { browseIdOf(it) }.firstOrNull { it.startsWith("UC") }
        val albumId = subRuns.mapNotNull { browseIdOf(it) }.firstOrNull { it.startsWith("MPRE") }
            ?: runs(2).mapNotNull { browseIdOf(it) }.firstOrNull { it.startsWith("MPRE") }

        val thumb = thumb(r.optJSONObject("thumbnail")) ?: fallbackThumb
        return SearchItem(id, type, title, subtitle, thumb, duration, artistId, albumId)
    }

    /** Parser kartu (karusel di halaman artis): album, single, video, artis serupa. */
    fun parseTwoRow(r: JSONObject): SearchItem? {
        val title = runsText(r.optJSONObject("title")).takeIf { it.isNotBlank() } ?: return null
        val subRuns = r.obj("subtitle")?.arr("runs")?.objects().orEmpty()
        val subtitle = subRuns.map { it.optString("text") }.filter { it.isNotBlank() && it.trim() != "•" }.joinToString(" • ")
        val nav = r.optJSONObject("navigationEndpoint")
        val videoId = nav?.optJSONObject("watchEndpoint")?.optString("videoId")?.takeIf { it.isNotEmpty() }
        val browseId = nav?.optJSONObject("browseEndpoint")?.optString("browseId")?.takeIf { it.isNotEmpty() }
        val thumb = thumb(r.optJSONObject("thumbnailRenderer"), 300)
        val artistId = subRuns.mapNotNull { browseIdOf(it) }.firstOrNull { it.startsWith("UC") }
        return when {
            videoId != null -> SearchItem(videoId, SearchType.SONG, title, subtitle, thumb, null, artistId)
            browseId != null && browseId.startsWith("UC") ->
                SearchItem(browseId, SearchType.ARTIST, title, subtitle, thumb)
            browseId != null -> SearchItem(browseId, SearchType.ALBUM, title, subtitle, thumb, null, artistId)
            else -> null
        }
    }
}
