package com.jimz.jimztune.data.library

import android.net.Uri
import com.jimz.jimztune.AppGraph
import com.jimz.jimztune.data.model.SearchItem
import com.jimz.jimztune.data.model.SearchType
import com.jimz.jimztune.data.model.durationSeconds
import com.jimz.jimztune.data.model.isLongVersion
import com.jimz.jimztune.data.search.Http
import com.jimz.jimztune.data.search.arr
import com.jimz.jimztune.data.search.findAll
import com.jimz.jimztune.data.search.obj
import com.jimz.jimztune.data.search.objects
import com.jimz.jimztune.data.search.str
import kotlinx.coroutines.CancellationException
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.async
import kotlinx.coroutines.awaitAll
import kotlinx.coroutines.coroutineScope
import kotlinx.coroutines.delay
import kotlinx.coroutines.sync.Semaphore
import kotlinx.coroutines.sync.withPermit
import org.json.JSONArray
import org.json.JSONObject
import java.io.IOException
import java.util.concurrent.atomic.AtomicInteger

/** Satu lagu dari layanan lain (belum dicocokkan dengan YouTube). */
data class ImportedTrack(val title: String, val artist: String, val durationSec: Int = 0)

/**
 * Isi playlist yang berhasil dibaca dari tautan. [tracks] masih harus dicocokkan ke YouTube Music;
 * [ready] sudah berupa lagu siap putar (playlist YouTube / YouTube Music), jadi tidak perlu dicocokkan.
 */
class Fetched(
    val source: String,
    val name: String,
    val tracks: List<ImportedTrack>,
    val ready: List<SearchItem> = emptyList(),
    val note: String? = null
)

class MatchResult(val matched: List<SearchItem>, val missing: List<ImportedTrack>)

/** Pesan di sini sudah berbahasa pengguna, aman langsung ditampilkan. */
class ImportException(message: String, val detail: String? = null) : IOException(message)

/**
 * Impor playlist dari tautan layanan lain, lalu mencocokkan tiap lagunya ke YouTube Music (sumber putar aplikasi ini).
 *
 *  Tidak ada batas jumlah lagu: playlist 500 lagu ya masuk 500.
 *
 *  - Spotify: halaman embed publik (tanpa login) memberi 100 lagu pertama + token anonim; token itu dipakai
 *    untuk membaca sisanya halaman demi halaman lewat API Spotify. Kalau jalur lengkap gagal, 100 lagu pertama tetap dipakai.
 *  - YouTube / YouTube Music: langsung memakai daftar lagunya (semua halaman lanjutan), tanpa pencocokan.
 *  - Deezer: API publik, semua halaman.
 *  - Apple Music: membaca data terstruktur di halaman playlist (bisa gagal kalau Apple mengubah halamannya).
 *  - Teks: satu lagu per baris ("Artis - Judul").
 */
object PlaylistImporter {
    private const val ACCEPT_SCORE = 55
    private const val EMBED_LIMIT = 100

    /** Skor minimum untuk hasil yang judulnya persis sama tapi nama artisnya beda penulisan. */
    private const val LOOSE_SCORE = 45
    private const val MATCH_PARALLEL = 12

    private val URL_REGEX = Regex("https?://[^\\s]+")
    private val SPOTIFY = Regex("/(playlist|album)/([A-Za-z0-9]{10,})")
    private val DEEZER = Regex("/(playlist|album)/(\\d+)")
    private val SHORT_HOSTS = listOf("spotify.link", "spotify.app.link", "link.deezer.com", "deezer.page.link", "on.soundcloud.com")

    // ------------------------------------------------------------------ membaca tautan

    suspend fun fetch(input: String, onStatus: (String) -> Unit = {}): Fetched {
        val raw = URL_REGEX.find(input)?.value?.trimEnd(')', ']', '.', ',', '"')
            ?: throw ImportException("Tempel tautan playlist yang diawali https://")
        var url = raw
        val firstHost = hostOf(url)
        if (SHORT_HOSTS.any { firstHost == it || firstHost.endsWith(".$it") }) url = Http.finalUrl(url)
        val host = hostOf(url)
        try {
            return when {
                host.endsWith("spotify.com") -> {
                    val m = SPOTIFY.find(url)
                        ?: throw ImportException("Tautan Spotify ini bukan playlist atau album")
                    spotify(m.groupValues[1], m.groupValues[2], onStatus)
                }
                host.endsWith("youtube.com") || host == "youtu.be" -> youtube(url, onStatus)
                host.endsWith("deezer.com") -> {
                    val m = DEEZER.find(url) ?: throw ImportException("Tautan Deezer ini bukan playlist atau album")
                    deezer(m.groupValues[1], m.groupValues[2], onStatus)
                }
                host.endsWith("apple.com") -> appleMusic(url)
                else -> throw ImportException(
                    "Tautan ini belum didukung. Coba Spotify, YouTube Music, Deezer, atau Apple Music, " +
                        "atau pakai tab \"Tempel daftar\"."
                )
            }
        } catch (e: ImportException) {
            throw e
        } catch (e: CancellationException) {
            throw e
        } catch (e: Exception) {
            throw ImportException("Playlist tidak bisa dibaca. Pastikan playlist bersifat publik dan koneksi lancar.", "${e.javaClass.simpleName}: ${e.message?.take(80)}")
        }
    }

    private fun hostOf(url: String): String = try {
        Uri.parse(url).host.orEmpty().lowercase()
    } catch (_: Exception) {
        ""
    }

    // ---------------------------------------------------------------- Spotify

    private class SpotifyApiResult(val tracks: List<ImportedTrack>, val complete: Boolean)

    private suspend fun spotify(kind: String, id: String, onStatus: (String) -> Unit): Fetched {
        val html = getRetry("https://open.spotify.com/embed/$kind/$id", mapOf("Accept-Language" to "en"))
        val json = Regex("<script id=\"__NEXT_DATA__\" type=\"application/json\">(.*?)</script>", RegexOption.DOT_MATCHES_ALL)
            .find(html)?.groupValues?.get(1)
            ?: throw ImportException("Impor dari Spotify belum bisa saat ini. Pakai tab \"Tempel daftar\" dulu.", "Spotify embed: __NEXT_DATA__ tidak ditemukan (format halaman berubah)")
        val root = JSONObject(json)
        val entity = root.findAll("entity").firstOrNull { it.has("trackList") }
            ?: throw ImportException("Daftar lagu tidak ditemukan. Pastikan playlist bersifat publik.")
        val list = entity.optJSONArray("trackList") ?: JSONArray()
        val embedTracks = ArrayList<ImportedTrack>()
        for (i in 0 until list.length()) {
            val o = list.optJSONObject(i) ?: continue
            val title = o.str("title").trim()
            if (title.isEmpty()) continue
            embedTracks += ImportedTrack(
                title = title,
                artist = o.str("subtitle").replace('\u00a0', ' ').trim(),
                durationSec = (o.optLong("duration") / 1000L).toInt()
            )
        }
        if (embedTracks.isEmpty()) throw ImportException("Playlist kosong atau tidak bisa dibaca (mungkin privat).")
        val name = entity.str("name").ifBlank { entity.str("title") }.ifBlank { "Playlist Spotify" }
        onStatus("Membaca lagu dari Spotify… ${embedTracks.size}")

        // Halaman embed berhenti di 100 lagu. Di bawah itu berarti playlistnya memang sudah terbaca semua.
        if (embedTracks.size < EMBED_LIMIT) return Fetched("Spotify", name, embedTracks)

        val token = findString(root, "accessToken")
        val api = if (token == null) null else try {
            spotifyApi(kind, id, token, onStatus)
        } catch (e: CancellationException) {
            throw e
        } catch (_: Exception) {
            null
        }
        return when {
            api != null && api.tracks.size > embedTracks.size && api.complete ->
                Fetched("Spotify", name, api.tracks)
            api != null && api.tracks.size > embedTracks.size ->
                Fetched(
                    "Spotify", name, api.tracks,
                    note = "Koneksi ke Spotify terputus di tengah jalan: baru ${api.tracks.size} lagu yang terbaca. " +
                        "Coba impor ulang untuk mendapat sisanya."
                )
            else ->
                Fetched(
                    "Spotify", name, embedTracks,
                    note = "Spotify hanya memberi ${embedTracks.size} lagu pertama kali ini (jalur lengkapnya gagal). " +
                        "Coba impor ulang sebentar lagi, atau salin daftarnya lalu pakai tab \"Tempel daftar\"."
                )
        }
    }

    /**
     * Membaca seluruh isi playlist / album lewat API Spotify dengan token anonim dari halaman embed.
     * Ukuran halaman otomatis diturunkan kalau Spotify menolak (HTTP 400), halaman berikutnya diambil lewat
     * "next" ATAU offset, dan hasil dibandingkan dengan "total" dari Spotify supaya tahu lengkap atau tidak.
     */
    private suspend fun spotifyApi(kind: String, id: String, token: String, onStatus: (String) -> Unit): SpotifyApiResult {
        val headers = mapOf("Authorization" to "Bearer $token", "Accept" to "application/json", "Accept-Language" to "en")
        // Spotify sedang mengganti nama endpoint playlist (tracks -> items): coba bentuk baru dulu, lalu yang lama.
        val paths = if (kind == "playlist") listOf("items", "tracks") else listOf("tracks")
        val sizes = if (kind == "playlist") listOf(100, 50, 20, 10) else listOf(50, 20, 10)
        var best: SpotifyApiResult? = null
        var lastError: Exception? = null
        for (path in paths) {
            for (size in sizes) {
                val out = ArrayList<ImportedTrack>()
                var offset = 0
                var total = -1
                var pages = 0
                var badRequest = false
                try {
                    while (pages < 2000) {
                        val url = "https://api.spotify.com/v1/${kind}s/$id/$path?limit=$size&offset=$offset&additional_types=track"
                        val page = JSONObject(getRetry(url, headers))
                        if (page.has("total")) total = page.optInt("total", total)
                        val items = page.optJSONArray("items") ?: JSONArray()
                        for (i in 0 until items.length()) {
                            val entry = items.optJSONObject(i) ?: continue
                            // Playlist membungkus lagu di "track" (atau "item"); album langsung berisi lagu.
                            val t = entry.optJSONObject("track") ?: entry.optJSONObject("item") ?: entry
                            if (t.str("type") == "episode") continue
                            val title = t.str("name").trim()
                            if (title.isEmpty()) continue
                            val artists = t.optJSONArray("artists").let { a ->
                                if (a == null) "" else (0 until a.length())
                                    .mapNotNull { k -> a.optJSONObject(k)?.str("name")?.takeIf { n -> n.isNotBlank() } }
                                    .joinToString(", ")
                            }
                            out += ImportedTrack(title, artists, (t.optLong("duration_ms") / 1000L).toInt())
                        }
                        onStatus("Membaca lagu dari Spotify… ${out.size}" + if (total > 0) " dari $total" else "")
                        // Offset dihitung dari jumlah item MENTAH di halaman (lagu kosong/dihapus tetap memakai slot).
                        offset += items.length()
                        val hasNext = page.str("next").isNotBlank()
                        val reachedTotal = total in 0..offset
                        if (items.length() == 0 || (!hasNext && reachedTotal) || (!hasNext && total < 0)) break
                        pages++
                    }
                    val complete = total < 0 || offset >= total
                    if (out.isNotEmpty()) {
                        val r = SpotifyApiResult(out, complete)
                        if (complete) return r
                        if (best == null || r.tracks.size > best.tracks.size) best = r
                    }
                    break // ukuran halaman ini jalan; tidak perlu coba ukuran lain
                } catch (e: CancellationException) {
                    throw e
                } catch (e: Exception) {
                    lastError = e
                    if (out.isNotEmpty()) {
                        val r = SpotifyApiResult(out, false)
                        if (best == null || r.tracks.size > best.tracks.size) best = r
                    }
                    badRequest = e.message.orEmpty().contains("HTTP 400")
                    // Hanya 400 (ukuran halaman ditolak) yang layak dicoba ulang dengan halaman lebih kecil.
                    if (!badRequest) break
                }
            }
        }
        return best ?: throw (lastError ?: IOException("Spotify tidak memberi daftar lagu"))
    }

    /** Mencari nilai string pertama dengan kunci [key] di mana pun di dalam JSON. */
    private fun findString(node: Any?, key: String): String? {
        when (node) {
            is JSONObject -> {
                val direct = node.opt(key)
                if (direct is String && direct.isNotBlank()) return direct
                val keys = node.keys()
                while (keys.hasNext()) {
                    val found = findString(node.opt(keys.next()), key)
                    if (found != null) return found
                }
            }
            is JSONArray -> for (i in 0 until node.length()) {
                val found = findString(node.opt(i), key)
                if (found != null) return found
            }
        }
        return null
    }

    /** GET dengan beberapa kali percobaan ulang (jaringan seluler sering putus sesaat, atau Spotify membalas 429). */
    private suspend fun getRetry(url: String, headers: Map<String, String> = emptyMap(), tries: Int = 5): String {
        var last: Exception? = null
        for (i in 1..tries) {
            try {
                return Http.get(url, headers)
            } catch (e: CancellationException) {
                throw e
            } catch (e: Exception) {
                last = e
                // 401/403/404 tidak akan membaik kalau diulang.
                val m = e.message.orEmpty()
                if (m.contains("HTTP 401") || m.contains("HTTP 403") || m.contains("HTTP 404")) break
                // 429 = kena pembatasan laju: tunggu lebih lama supaya halaman berikutnya tidak ikut terblokir.
                delay(if (m.contains("HTTP 429")) 2500L * i else 700L * i)
            }
        }
        throw last ?: IOException("Gagal")
    }

    // ---------------------------------------------------------------- YouTube / YouTube Music

    private suspend fun youtube(url: String, onStatus: (String) -> Unit): Fetched {
        val list = try {
            Uri.parse(url).getQueryParameter("list")
        } catch (_: Exception) {
            null
        }?.takeIf { it.isNotBlank() }
            ?: throw ImportException("Tautan ini video tunggal, bukan playlist (tidak ada parameter list=)")
        val id = if (list.startsWith("PL") || list.startsWith("OLAK") || list.startsWith("RDCLAK") || list.startsWith("VL")) list else "VL$list"
        onStatus("Membaca playlist YouTube…")
        var note: String? = null
        var page: com.jimz.jimztune.data.model.AlbumPage? = null
        var lastError: Exception? = null
        // Halaman lanjutan yang gagal membuat playlist terpotong diam-diam: ulangi sampai jumlahnya sesuai
        // yang dilaporkan YouTube (maks 3 kali), lalu pakai hasil terbanyak.
        for (attempt in 1..3) {
            try {
                val p = AppGraph.detail.playlistAll(id) { n -> onStatus("Membaca lagu dari YouTube… $n") }
                if (page == null || p.tracks.size > page.tracks.size) page = p
                val expected = expectedCount(p.info)
                if (expected == null || p.tracks.size >= expected) break
                if (attempt < 3) {
                    onStatus("Baru ${p.tracks.size} dari $expected lagu, mengulang…")
                    delay(1200L * attempt)
                }
            } catch (e: CancellationException) {
                throw e
            } catch (e: Exception) {
                lastError = e
                delay(1200L * attempt)
            }
        }
        if (page == null) {
            // Jalur lengkap gagal total: pakai jalur cadangan (bisa hanya sebagian isi playlist).
            note = "Playlist mungkin belum terbaca seluruhnya. Coba impor ulang kalau jumlah lagunya kurang."
            page = try {
                AppGraph.detail.album(id)
            } catch (e: CancellationException) {
                throw e
            } catch (e: Exception) {
                throw lastError ?: e
            }
        }
        val songs = page.tracks.filter { it.type == SearchType.SONG }
        if (songs.isEmpty()) throw ImportException("Playlist YouTube kosong atau tidak bisa dibaca (mungkin privat).")
        val expected = expectedCount(page.info)
        if (note == null && expected != null && songs.size < expected) {
            note = "YouTube melaporkan $expected lagu, yang terbaca ${songs.size}. Selisihnya biasanya lagu yang sudah " +
                "dihapus / tidak tersedia. Kalau merasa kurang banyak, coba impor ulang."
        }
        return Fetched("YouTube Music", page.title, emptyList(), ready = songs, note = note)
    }

    /** Jumlah lagu yang dilaporkan header playlist, mis. "500 songs" / "1.234 lagu". Null kalau tidak terbaca. */
    private fun expectedCount(info: String): Int? =
        Regex("([\\d.,]+)\\s*(songs?|lagu|tracks?|video|videos)", RegexOption.IGNORE_CASE).find(info)
            ?.groupValues?.get(1)?.filter { it.isDigit() }?.toIntOrNull()?.takeIf { it > 0 }

    // ---------------------------------------------------------------- Deezer

    private suspend fun deezer(kind: String, id: String, onStatus: (String) -> Unit): Fetched {
        val meta = JSONObject(Http.get("https://api.deezer.com/$kind/$id"))
        if (meta.has("error")) throw ImportException("Playlist Deezer tidak ditemukan atau privat.")
        val name = meta.str("title").ifBlank { "Playlist Deezer" }
        val out = ArrayList<ImportedTrack>()
        var next: String? = "https://api.deezer.com/$kind/$id/tracks?limit=100"
        var pages = 0
        while (next != null && pages < 500) {
            val page = JSONObject(getRetry(next))
            for (o in page.arr("data").objects()) {
                val title = o.str("title").trim()
                if (title.isEmpty()) continue
                out += ImportedTrack(title, o.obj("artist")?.str("name").orEmpty(), o.optInt("duration"))
            }
            onStatus("Membaca lagu dari Deezer… ${out.size}")
            next = page.str("next").ifBlank { null }
            pages++
        }
        if (out.isEmpty()) throw ImportException("Playlist kosong atau tidak bisa dibaca.")
        return Fetched("Deezer", name, out)
    }

    // ---------------------------------------------------------------- Apple Music

    private suspend fun appleMusic(url: String): Fetched {
        val html = Http.get(url, mapOf("Accept-Language" to "en"))
        val blocks = Regex("<script[^>]*type=\"application/ld\\+json\"[^>]*>(.*?)</script>", RegexOption.DOT_MATCHES_ALL)
            .findAll(html).map { it.groupValues[1] }.toList()
        for (b in blocks) {
            val root = try { JSONObject(b) } catch (_: Exception) { continue }
            val arr = root.optJSONArray("track") ?: continue
            val albumArtist = nameOf(root.opt("byArtist"))
            val tracks = ArrayList<ImportedTrack>()
            for (i in 0 until arr.length()) {
                val t = arr.optJSONObject(i) ?: continue
                val title = t.str("name").trim()
                if (title.isEmpty()) continue
                tracks += ImportedTrack(title, nameOf(t.opt("byArtist")).ifBlank { albumArtist }, isoSeconds(t.str("duration")))
            }
            if (tracks.isNotEmpty()) {
                return Fetched("Apple Music", root.str("name").ifBlank { "Playlist Apple Music" }, tracks)
            }
        }
        throw ImportException("Impor dari Apple Music belum bisa saat ini. Pakai tab \"Tempel daftar\".", "Apple Music: JSON-LD daftar lagu tidak ditemukan")
    }

    private fun nameOf(v: Any?): String = when (v) {
        is JSONObject -> v.str("name")
        is JSONArray -> (0 until v.length()).mapNotNull { v.optJSONObject(it)?.str("name")?.takeIf { n -> n.isNotBlank() } }.joinToString(", ")
        is String -> v
        else -> ""
    }

    /** "PT3M45S" -> 225 */
    private fun isoSeconds(iso: String): Int {
        val m = Regex("PT(?:(\\d+)H)?(?:(\\d+)M)?(?:(\\d+)S)?").matchEntire(iso.trim()) ?: return 0
        val (h, mi, s) = m.destructured
        return (h.toIntOrNull() ?: 0) * 3600 + (mi.toIntOrNull() ?: 0) * 60 + (s.toIntOrNull() ?: 0)
    }

    // ---------------------------------------------------------------- teks tempelan

    /** Satu lagu per baris: "Artis - Judul" atau cukup "Judul". Nomor urut di depan baris diabaikan. */
    fun parseText(text: String): List<ImportedTrack> =
        text.lineSequence()
            .map { it.trim() }
            // Baris "# judul" (dari tombol Bagikan) dan tautan bukan lagu.
            .filter { !it.startsWith("#") && !it.startsWith("http") }
            .map { it.replace(Regex("^\\d+[.)]\\s+"), "") }
            .filter { it.isNotBlank() }
            .map { line ->
                val parts = line.split(Regex("\\s+[-\u2013\u2014]\\s+"), limit = 2)
                if (parts.size == 2) ImportedTrack(title = parts[1].trim(), artist = parts[0].trim())
                else ImportedTrack(title = line, artist = "")
            }
            .toList()

    /**
     * Membaca CSV ekspor playlist (Exportify, TuneMyMusic, dsb.). Kolom dikenali dari baris judul
     * ("Track Name", "Title", "Artist Name(s)", ...); tanpa baris judul dipakai kolom 1 = judul, kolom 2 = artis.
     * Maksimal 5000 lagu.
     */
    fun parseCsv(text: String): List<ImportedTrack> {
        val rows = csvRows(text.removePrefix("\uFEFF"))
        if (rows.isEmpty()) return emptyList()
        val head = rows[0].map { it.trim().lowercase() }
        val titleKeys = listOf("track name", "track", "title", "song", "song name", "name", "judul", "lagu")
        val artistKeys = listOf("artist name(s)", "artist name", "artist(s)", "artists", "artist", "artis", "penyanyi")
        val ti = titleKeys.firstNotNullOfOrNull { k -> head.indexOf(k).takeIf { it >= 0 } }
        val ai = artistKeys.firstNotNullOfOrNull { k -> head.indexOf(k).takeIf { it >= 0 } }
        val hasHeader = ti != null
        val tCol = ti ?: 0
        val aCol = if (hasHeader) ai ?: -1 else 1
        return rows.drop(if (hasHeader) 1 else 0).mapNotNull { r ->
            val title = r.getOrNull(tCol)?.trim().orEmpty()
            if (title.isEmpty()) return@mapNotNull null
            // Spotify memisahkan beberapa artis dengan ";": ambil yang pertama supaya pencarian lebih akurat.
            val artist = r.getOrNull(aCol)?.trim().orEmpty().substringBefore(';').trim()
            ImportedTrack(title = title, artist = artist)
        }.take(5000)
    }

    /** Pemisah baris CSV sederhana: mendukung tanda kutip ganda, koma di dalam kutip, dan "" sebagai kutip. */
    private fun csvRows(text: String): List<List<String>> {
        val delim = if (text.lineSequence().firstOrNull().orEmpty().count { it == ';' } > text.lineSequence().firstOrNull().orEmpty().count { it == ',' }) ';' else ','
        val out = ArrayList<List<String>>()
        var row = ArrayList<String>()
        val cell = StringBuilder()
        var quoted = false
        var i = 0
        fun endCell() { row.add(cell.toString()); cell.setLength(0) }
        fun endRow() { endCell(); if (row.any { it.isNotBlank() }) out.add(row); row = ArrayList() }
        while (i < text.length && out.size <= 20000) {
            val ch = text[i]
            when {
                quoted && ch == '"' && text.getOrNull(i + 1) == '"' -> { cell.append('"'); i++ }
                ch == '"' -> quoted = !quoted
                !quoted && ch == delim -> endCell()
                !quoted && ch == '\n' -> endRow()
                !quoted && ch == '\r' -> {}
                else -> cell.append(ch)
            }
            i++
        }
        if (cell.isNotEmpty() || row.isNotEmpty()) endRow()
        return out
    }

    // ---------------------------------------------------------------- pencocokan ke YouTube Music

    /**
     * Mencari tiap lagu di YouTube Music (beberapa sekaligus) dan memilih hasil yang paling mirip.
     * Lagu yang tidak ketemu di putaran pertama (sering karena pembatasan sesaat) dicoba lagi pelan-pelan
     * lewat SEMUA sumber pencarian sebelum dinyatakan "tidak ditemukan".
     */
    suspend fun match(
        tracks: List<ImportedTrack>,
        onPhase: (String) -> Unit = {},
        onProgress: (done: Int, found: Int) -> Unit
    ): MatchResult = coroutineScope {
        val done = AtomicInteger()
        val found = AtomicInteger()
        val failures = AtomicInteger()
        val gate = Semaphore(MATCH_PARALLEL)
        val hits = arrayOfNulls<SearchItem>(tracks.size)
        tracks.mapIndexed { i, t ->
            async(Dispatchers.IO) {
                gate.withPermit {
                    val hit = try {
                        findWithRetry(t, quick = true, fastest = true)
                    } catch (e: CancellationException) {
                        throw e
                    } catch (_: Exception) {
                        failures.incrementAndGet()
                        null
                    }
                    hits[i] = hit
                    if (hit != null) found.incrementAndGet()
                    onProgress(done.incrementAndGet(), found.get())
                }
            }
        }.awaitAll()
        if (tracks.isNotEmpty() && failures.get() >= tracks.size) {
            throw ImportException("Tidak bisa mencari lagu. Periksa koneksi internet lalu coba lagi.")
        }

        // Putaran kedua: hanya yang belum ketemu, lebih pelan (3 sekaligus) tapi memakai semua sumber.
        val retryIdx = hits.indices.filter { hits[it] == null }
        if (retryIdx.isNotEmpty()) {
            onPhase("Mencoba lagi ${retryIdx.size} lagu yang belum ketemu…")
            delay(1500)
            val gate2 = Semaphore(3)
            retryIdx.map { i ->
                async(Dispatchers.IO) {
                    gate2.withPermit {
                        val hit = try {
                            findWithRetry(tracks[i], quick = false, fastest = false)
                        } catch (e: CancellationException) {
                            throw e
                        } catch (_: Exception) {
                            null
                        }
                        if (hit != null) {
                            hits[i] = hit
                            onProgress(tracks.size, found.incrementAndGet())
                        }
                    }
                }
            }.awaitAll()
        }

        val matched = ArrayList<SearchItem>()
        val missing = ArrayList<ImportedTrack>()
        hits.forEachIndexed { i, h -> if (h != null) matched += h else missing += tracks[i] }
        MatchResult(matched.distinctBy { it.id }, missing)
    }

    private val artistSplit = Regex("\\s*(,|&|;|\\bfeat\\.?|\\bft\\.?|\\s+x\\s+)\\s*", RegexOption.IGNORE_CASE)

    /** Pencarian yang gagal karena jaringan / pembatasan sesaat diulang sebelum lagunya dianggap tidak ada. */
    private suspend fun findWithRetry(t: ImportedTrack, quick: Boolean, fastest: Boolean): SearchItem? {
        var last: Exception? = null
        for (i in 1..3) {
            try {
                return findBest(t, quick, fastest)
            } catch (e: CancellationException) {
                throw e
            } catch (e: Exception) {
                last = e
                delay(500L * i)
            }
        }
        throw last ?: IOException("Gagal mencari")
    }

    private suspend fun findBest(t: ImportedTrack, quick: Boolean, fastest: Boolean): SearchItem? {
        val artists = t.artist.split(artistSplit).map { it.trim() }.filter { it.isNotEmpty() }
        val first = artists.firstOrNull().orEmpty()
        val queries = listOf(
            "${t.title} $first",
            "${cleanTitle(t.title)} $first",
            "${cleanTitle(t.title)} ${artists.joinToString(" ")}"
        ).map { it.trim() }.filter { it.isNotEmpty() }.distinct()
        var fallback: SearchItem? = null
        var fallbackScore = Int.MIN_VALUE
        for (q in queries) {
            val items = AppGraph.searchRepository.search(q, SearchType.SONG, quick = quick, fastest = fastest).items
                .filter { it.type == SearchType.SONG }
                .take(8)
            val best = items.maxByOrNull { score(t, artists, it) } ?: continue
            val sc = score(t, artists, best)
            if (sc >= ACCEPT_SCORE) return best
            if (sc > fallbackScore) {
                fallback = best
                fallbackScore = sc
            }
        }
        // Tidak ada yang meyakinkan: terima yang judulnya persis sama (artis bisa beda penulisan) daripada lagunya hilang.
        return if (fallbackScore >= LOOSE_SCORE) fallback else null
    }

    /** Membuang "(feat. ...)", "[Remastered]", dan " - Remastered 2011" dari judul supaya pencarian lebih longgar. */
    private fun cleanTitle(title: String): String =
        title.replace(Regex("\\(.*?\\)|\\[.*?\\]"), " ").substringBefore(" - ").replace(Regex("\\s+"), " ").trim()
            .ifEmpty { title }

    private fun norm(s: String): String = s.lowercase().filter { it.isLetterOrDigit() }

    private fun tokens(s: String): List<String> = s.lowercase().split(Regex("[^\\p{L}\\p{N}]+")).filter { it.length >= 2 }

    private val junkWords = setOf("live", "cover", "karaoke", "instrumental", "remix", "nightcore", "sped", "slowed", "reverb", "8d", "tribute")

    /** Skor kemiripan (makin tinggi makin cocok): judul, artis, durasi, dikurangi kata penanda versi lain. */
    private fun score(t: ImportedTrack, artists: List<String>, c: SearchItem): Int {
        val wantTitle = norm(t.title)
        val candTitle = norm(c.title)
        val hay = tokens(c.title + " " + c.subtitle).toSet()
        val wantTokens = tokens(t.title)
        val frac = if (wantTokens.isEmpty()) 0f else wantTokens.count { it in hay }.toFloat() / wantTokens.size
        var s = when {
            wantTitle.isNotEmpty() && candTitle == wantTitle -> 50
            wantTitle.isNotEmpty() && (candTitle.contains(wantTitle) || (candTitle.length >= 4 && wantTitle.contains(candTitle))) -> 42
            else -> (frac * 38).toInt()
        }
        s += if (artists.isEmpty()) {
            12
        } else {
            val h = norm(c.subtitle + " " + c.title)
            val hit = artists.count { norm(it).isNotEmpty() && h.contains(norm(it)) }
            when {
                hit == 0 -> 0
                hit == artists.size -> 25
                else -> 18
            }
        }
        val want = t.durationSec
        val got = c.durationSeconds
        if (want > 0 && got > 0) {
            val diff = kotlin.math.abs(want - got)
            s += when {
                diff <= 3 -> 25
                diff <= 8 -> 15
                diff <= 15 -> 5
                diff > 30 -> -30
                else -> 0
            }
        }
        if (c.isLongVersion) s -= 60
        val wantSet = wantTokens.toSet()
        val candTokens = tokens(c.title)
        s -= 25 * candTokens.count { it in junkWords && it !in wantSet }.coerceAtMost(2)
        return s
    }
}
