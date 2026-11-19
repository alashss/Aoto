package com.jimz.jimztune.data.detail

import com.jimz.jimztune.data.model.AlbumPage
import com.jimz.jimztune.data.model.ArtistPage
import com.jimz.jimztune.data.model.CollectionSection
import com.jimz.jimztune.data.model.SearchItem
import com.jimz.jimztune.data.model.SearchType
import com.jimz.jimztune.data.search.Http
import com.jimz.jimztune.data.search.PublicInstances
import com.jimz.jimztune.data.search.Ytm
import com.jimz.jimztune.data.search.YtmParse
import com.jimz.jimztune.data.search.arr
import com.jimz.jimztune.data.search.findAll
import com.jimz.jimztune.data.search.formatDuration
import com.jimz.jimztune.data.search.obj
import com.jimz.jimztune.data.search.objects
import com.jimz.jimztune.data.search.raceInstances
import kotlinx.coroutines.CancellationException
import org.json.JSONObject
import java.io.IOException
import java.util.concurrent.ConcurrentHashMap

/**
 * Mengambil halaman artis dan album dari YouTube Music (browse). Kalau gagal, jatuh ke instance Piped.
 * Hasil disimpan sebentar di memori supaya membuka ulang halaman terasa instan.
 */
class DetailRepository {
    private class Entry(val value: Any, val at: Long)

    private val cache = ConcurrentHashMap<String, Entry>()

    @Suppress("UNCHECKED_CAST")
    private suspend fun <T : Any> cached(key: String, load: suspend () -> T): T {
        val now = System.currentTimeMillis()
        cache[key]?.takeIf { now - it.at < TTL_MS }?.let { return it.value as T }
        val value = load()
        if (cache.size > 40) cache.clear()
        cache[key] = Entry(value, System.currentTimeMillis())
        return value
    }

    suspend fun artist(id: String): ArtistPage = cached("artist:$id") {
        try {
            ytmArtist(id)
        } catch (e: CancellationException) {
            throw e
        } catch (e: Exception) {
            pipedArtist(id)
        }
    }

    suspend fun album(id: String): AlbumPage = cached("album:$id") {
        // Id dari Piped berupa daftar putar (OLAK…/PL…): YouTube Music membukanya dengan awalan "VL".
        val browseId = if (id.startsWith("OLAK") || id.startsWith("PL") || id.startsWith("RDCLAK")) "VL$id" else id
        try {
            ytmAlbum(id, browseId)
        } catch (e: CancellationException) {
            throw e
        } catch (e: Exception) {
            pipedAlbum(id)
        }
    }

    /**
     * Seluruh isi sebuah playlist YouTube / YouTube Music, TANPA batas jumlah lagu (dipakai impor playlist).
     * Tidak di-cache dan tidak jatuh ke Piped: Piped hanya memberi sebagian, jadi lebih baik gagal jelas.
     */
    suspend fun playlistAll(id: String, onProgress: (loaded: Int) -> Unit = {}): AlbumPage {
        val browseId = if (id.startsWith("VL")) id else "VL$id"
        return ytmAlbum(id, browseId, maxPages = FULL_PAGES, onProgress = onProgress)
    }

    /** Semua lagu satu artis (rak "Lagu" lengkap), dipakai tombol "Tampilkan semua". */
    suspend fun allSongs(browseId: String, artistName: String?): List<SearchItem> = cached("songs:$browseId") {
        val json = Ytm.browse(browseId)
        val shelf = (json.findAll("musicPlaylistShelfRenderer") + json.findAll("musicShelfRenderer")).firstOrNull()
            ?: throw IOException("Daftar lagu tidak ditemukan")
        shelf.arr("contents").objects().mapNotNull {
            it.optJSONObject("musicResponsiveListItemRenderer")
                ?.let { r -> YtmParse.parseListItem(r, SearchType.SONG, artistName) }
        }.ifEmpty { throw IOException("Daftar lagu kosong") }
    }

    // ---------------------------------------------------------------- YouTube Music

    private suspend fun ytmArtist(id: String): ArtistPage {
        val json = Ytm.browse(id)
        val wrapper = json.optJSONObject("header")
        val header = wrapper?.keys()?.asSequence()?.mapNotNull { wrapper.optJSONObject(it) }?.firstOrNull()
            ?: throw IOException("Halaman artis tidak ditemukan")

        val name = YtmParse.runsText(header.optJSONObject("title")).ifBlank { throw IOException("Nama artis kosong") }
        val thumb = YtmParse.thumb(header.optJSONObject("thumbnail"), 800)
        val description = YtmParse.runsText(header.optJSONObject("description")).takeIf { it.isNotBlank() }
        val subs = header.findAll("subscribeButtonRenderer").firstOrNull()
            ?.let { YtmParse.runsText(it.optJSONObject("subscriberCountText")) }?.takeIf { it.isNotBlank() }

        // Rak lagu (daftar baris).
        val songShelf = json.findAll("musicShelfRenderer").firstOrNull()
        val topSongs = songShelf?.arr("contents")?.objects().orEmpty().mapNotNull {
            it.optJSONObject("musicResponsiveListItemRenderer")
                ?.let { r -> YtmParse.parseListItem(r, SearchType.SONG, name, thumb) }
        }
        val allSongsId = songShelf?.obj("bottomEndpoint", "browseEndpoint")?.optString("browseId")?.takeIf { it.isNotEmpty() }
            ?: songShelf?.obj("title")?.arr("runs")?.objects()?.firstOrNull()
                ?.obj("navigationEndpoint", "browseEndpoint")?.optString("browseId")?.takeIf { it.isNotEmpty() }

        // Rak karusel: album, single, video, artis serupa, dll.
        val sections = json.findAll("musicCarouselShelfRenderer").mapNotNull { shelf ->
            val title = shelf.obj("header", "musicCarouselShelfBasicHeaderRenderer")
                ?.let { YtmParse.runsText(it.optJSONObject("title")) }.orEmpty()
            val items = shelf.arr("contents").objects().mapNotNull { c ->
                c.optJSONObject("musicTwoRowItemRenderer")?.let { YtmParse.parseTwoRow(it) }
                    ?: c.optJSONObject("musicResponsiveListItemRenderer")
                        ?.let { YtmParse.parseListItem(it, SearchType.SONG, name) }
            }
            if (title.isBlank() || items.isEmpty()) null else CollectionSection(title, items)
        }

        if (topSongs.isEmpty() && sections.isEmpty()) throw IOException("Halaman artis kosong")
        return ArtistPage(id, name, thumb, description, subs, topSongs, allSongsId, sections)
    }

    private suspend fun ytmAlbum(
        id: String,
        browseId: String,
        maxPages: Int = 5,
        onProgress: (Int) -> Unit = {}
    ): AlbumPage {
        val json = Ytm.browse(browseId)
        val header = json.findAll("musicResponsiveHeaderRenderer").firstOrNull()
            ?: json.optJSONObject("header")?.let { w ->
                w.keys().asSequence().mapNotNull { w.optJSONObject(it) }.firstOrNull()
            }
            ?: throw IOException("Halaman album tidak ditemukan")

        val title = YtmParse.runsText(header.optJSONObject("title")).ifBlank { throw IOException("Judul album kosong") }
        val thumb = YtmParse.thumb(header.optJSONObject("thumbnail"), 600)

        val strap = header.obj("straplineTextOne")?.arr("runs")?.objects().orEmpty()
        val sub = header.obj("subtitle")?.arr("runs")?.objects().orEmpty()
        val artistRun = (strap + sub).firstOrNull {
            it.obj("navigationEndpoint", "browseEndpoint")?.optString("browseId").orEmpty().startsWith("UC")
        }
        val artistName = artistRun?.optString("text")?.takeIf { it.isNotBlank() }
            ?: strap.joinToString("") { it.optString("text") }.trim().ifBlank { "" }
        val artistId = artistRun?.obj("navigationEndpoint", "browseEndpoint")?.optString("browseId")

        val subtitle = sub.map { it.optString("text") }
            .filter { it.isNotBlank() && it.trim() != "•" && it != artistName }.joinToString(" • ")
        val second = YtmParse.runsText(header.optJSONObject("secondSubtitle"))
        val info = listOf(subtitle, second).filter { it.isNotBlank() }.joinToString(" • ")

        val shelf = (json.findAll("musicShelfRenderer") + json.findAll("musicPlaylistShelfRenderer")).firstOrNull()
            ?: throw IOException("Daftar lagu album tidak ditemukan")
        val tracks = shelf.arr("contents").objects().mapNotNull {
            it.optJSONObject("musicResponsiveListItemRenderer")?.let { r ->
                YtmParse.parseListItem(r, SearchType.SONG, artistName.ifBlank { null }, thumb)
            }
        }.map { it.copy(albumId = id) }
        if (tracks.isEmpty()) throw IOException("Album kosong")

        // Playlist panjang (lebih dari ~100 lagu) dikirim bertahap: ambil lanjutannya, gagal pun tidak apa-apa.
        onProgress(tracks.size)
        val more = if (browseId.startsWith("VL")) {
            moreTracks(shelf, artistName.ifBlank { null }, thumb, maxPages, tracks.size, onProgress).map { it.copy(albumId = id) }
        } else {
            emptyList()
        }
        return AlbumPage(id, title, artistName, artistId, info, thumb, (tracks + more).distinctBy { it.id })
    }

    private fun continuationToken(container: JSONObject?, entries: List<JSONObject>): String? =
        container?.arr("continuations")?.objects()?.firstOrNull()
            ?.optJSONObject("nextContinuationData")?.optString("continuation")?.takeIf { it.isNotEmpty() }
            ?: entries.firstNotNullOfOrNull {
                it.optJSONObject("continuationItemRenderer")?.obj("continuationEndpoint", "continuationCommand")
                    ?.optString("token")?.takeIf { t -> t.isNotEmpty() }
            }

    /**
     * Halaman lanjutan sebuah playlist, sampai habis (atau [maxPages] halaman). Tiap halaman dicoba ulang
     * beberapa kali: satu halaman gagal sesaat tidak boleh memotong playlist 500 lagu jadi 200.
     */
    private suspend fun moreTracks(
        shelf: JSONObject,
        artistName: String?,
        thumb: String?,
        maxPages: Int,
        alreadyLoaded: Int,
        onProgress: (Int) -> Unit
    ): List<SearchItem> {
        var token = continuationToken(shelf, shelf.arr("contents").objects())
        val out = ArrayList<SearchItem>()
        var pages = 0
        while (token != null && pages < maxPages) {
            var entries: List<JSONObject>? = null
            var classic: JSONObject? = null
            var attempt = 0
            while (entries == null && attempt < 3) {
                attempt++
                try {
                    val body = JSONObject().put("context", Ytm.context()).put("continuation", token).toString()
                    val json = JSONObject(
                        Http.postJson("${Ytm.BASE}/browse?ctoken=$token&continuation=$token&type=next&prettyPrint=false", body, Ytm.headers)
                    )
                    classic = json.findAll("musicPlaylistShelfContinuation").firstOrNull()
                    entries = classic?.arr("contents")?.objects()
                        ?: json.optJSONArray("onResponseReceivedActions")?.optJSONObject(0)
                            ?.optJSONObject("appendContinuationItemsAction")?.optJSONArray("continuationItems")?.objects()
                } catch (e: CancellationException) {
                    throw e
                } catch (_: Exception) {
                    kotlinx.coroutines.delay(400L * attempt)
                }
            }
            val got = entries ?: break
            got.forEach { e ->
                e.optJSONObject("musicResponsiveListItemRenderer")
                    ?.let { r -> YtmParse.parseListItem(r, SearchType.SONG, artistName, thumb) }
                    ?.let { out += it }
            }
            onProgress(alreadyLoaded + out.size)
            token = continuationToken(classic, got)
            pages++
        }
        return out
    }

    // ---------------------------------------------------------------- Piped (cadangan)

    private fun pipedSong(o: JSONObject, fallbackArtist: String): SearchItem? {
        val vid = o.optString("url").substringAfter("v=", "").substringBefore("&")
        val title = o.optString("title")
        if (vid.isEmpty() || title.isEmpty()) return null
        return SearchItem(
            id = vid,
            type = SearchType.SONG,
            title = title,
            subtitle = o.optString("uploaderName").ifBlank { fallbackArtist },
            thumbnailUrl = o.optString("thumbnail").ifBlank { null },
            durationText = formatDuration(o.optLong("duration", 0))
        )
    }

    private suspend fun pipedArtist(id: String): ArtistPage {
        val json = raceInstances(PublicInstances.piped) { base -> JSONObject(Http.get("$base/channel/$id")) }
        val name = json.optString("name").ifBlank { throw IOException("Artis tidak ditemukan") }
        val songs = json.arr("relatedStreams").objects().mapNotNull { pipedSong(it, name) }
        if (songs.isEmpty()) throw IOException("Tidak ada lagu untuk artis ini")
        val subs = json.optLong("subscriberCount", -1).takeIf { it > 0 }?.let { "%,d pelanggan".format(it) }
        return ArtistPage(
            id = id,
            name = name,
            thumbnailUrl = json.optString("avatarUrl").ifBlank { null },
            description = json.optString("description").takeIf { it.isNotBlank() && it != "null" },
            subscribers = subs,
            topSongs = songs,
            allSongsId = null,
            sections = emptyList()
        )
    }

    private suspend fun pipedAlbum(id: String): AlbumPage {
        val json = raceInstances(PublicInstances.piped) { base -> JSONObject(Http.get("$base/playlists/$id")) }
        val title = json.optString("name").ifBlank { throw IOException("Album tidak ditemukan") }
        val artist = json.optString("uploader")
        val tracks = json.arr("relatedStreams").objects().mapNotNull { pipedSong(it, artist) }
        if (tracks.isEmpty()) throw IOException("Album kosong")
        val thumb = json.optString("thumbnailUrl").ifBlank { null }
        return AlbumPage(id, title, artist, null, "${tracks.size} lagu", thumb, tracks.map { it.copy(albumId = id) })
    }

    private companion object {
        const val TTL_MS = 10 * 60_000L
        /** Batas pengaman saja (± 20.000 lagu), bukan batas fitur. */
        const val FULL_PAGES = 200
    }
}
