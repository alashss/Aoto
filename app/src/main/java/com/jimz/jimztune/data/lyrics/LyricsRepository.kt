package com.jimz.jimztune.data.lyrics

import android.content.Context
import com.jimz.jimztune.data.model.SearchItem
import com.jimz.jimztune.player.parseDurationMs
import kotlinx.coroutines.CancellationException
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.cancelChildren
import kotlinx.coroutines.channels.Channel
import kotlinx.coroutines.coroutineScope
import kotlinx.coroutines.launch
import kotlinx.coroutines.sync.withLock
import kotlinx.coroutines.withContext
import kotlinx.coroutines.withTimeoutOrNull
import org.json.JSONArray
import org.json.JSONObject
import java.io.File

/**
 * Mengambil lirik dari banyak sumber sekaligus, MEMERIKSA kecocokannya dengan lagu yang diputar (judul, versi,
 * artis, durasi, panjang lirik), lalu mengurutkan: lirik per kata > per baris > biasa, dan yang paling cocok dulu.
 * Daftar hasil (satu per sumber) disimpan di perangkat; user bisa berganti sumber kalau yang pertama kurang pas.
 */
class LyricsRepository(context: Context) {
    private val dir = File(context.filesDir, "lyrics_v6").apply { mkdirs() }

    init {
        // Cache lama tidak membedakan "Nak" dan "Nak..." (dan versi v1 tidak diperiksa sama sekali), jadi bisa
        // berisi lirik lagu yang salah: buang semuanya supaya dicari ulang dengan pemeriksaan yang baru.
        listOf("lyrics", "lyrics_v2", "lyrics_v3", "lyrics_v4", "lyrics_v5").forEach { File(context.filesDir, it).takeIf { f -> f.exists() }?.deleteRecursively() }
    }

    private val providers: List<LyricsProvider> = listOf(
        BetterLyricsProvider(),
        LrcLibProvider(),
        KuGouProvider(),
        SimpMusicProvider(),
        NetEaseProvider(),
        YouTubeMusicLyricsProvider(),
        LyricsOvhProvider()
    )

    private class Scored(val candidate: LyricsCandidate, val score: Double)

    /** Satu kunci per lagu: pemutar dan widget lirik yang meminta lagu sama bersamaan tidak mengunduh dua kali. */
    private val locks = java.util.concurrent.ConcurrentHashMap<String, kotlinx.coroutines.sync.Mutex>()

    /** Semua pilihan lirik untuk lagu ini, yang terbaik di indeks 0. Kosong = tidak ditemukan. */
    suspend fun get(song: SearchItem, durationSec: Int, refresh: Boolean = false): List<Lyrics> {
        if (locks.size > 200) locks.clear()
        val lock = locks.getOrPut(song.id) { kotlinx.coroutines.sync.Mutex() }
        return lock.withLock { load(song, durationSec, refresh) }
    }

    private suspend fun load(song: SearchItem, durationSec: Int, refresh: Boolean): List<Lyrics> {
        if (!refresh) readCache(song.id)?.let { return it }

        val (title, artist) = LyricsMatch.splitTitleArtist(cleanTitle(song.title), cleanArtist(song.subtitle))
        val duration = if (durationSec > 0) durationSec else (parseDurationMs(song.durationText) / 1000).toInt()
        val query = LyricsQuery(videoId = song.id, title = title, artist = artist, durationSec = duration)

        val all = ArrayList<LyricsCandidate>()
        coroutineScope {
            val channel = Channel<Pair<String, List<LyricsCandidate>>>(providers.size)
            providers.forEach { p ->
                launch {
                    val found = withTimeoutOrNull(PROVIDER_TIMEOUT_MS) {
                        try {
                            p.fetch(query)
                        } catch (e: CancellationException) {
                            throw e
                        } catch (_: Exception) {
                            emptyList()
                        }
                    }.orEmpty()
                    channel.trySend(p.name to found)
                }
            }

            var pending = providers.size
            val hardEnd = System.currentTimeMillis() + HARD_LIMIT_MS
            var graceEnd = Long.MAX_VALUE
            var primaryDone = false
            while (pending > 0) {
                // BetterLyrics adalah sumber utama: jangan berhenti sebelum BetterLyrics selesai menjawab (atau batas waktu habis).
                val limit = if (primaryDone || graceEnd != Long.MAX_VALUE) minOf(hardEnd, graceEnd) else hardEnd
                val remaining = limit - System.currentTimeMillis()
                if (remaining <= 0) break
                val (name, batch) = withTimeoutOrNull(remaining) { channel.receive() } ?: break
                pending--
                if (name == PRIMARY) primaryDone = true
                all += batch
                val best = rank(all, query).firstOrNull()
                if (best != null && graceEnd == Long.MAX_VALUE) {
                    val l = best.candidate.lyrics
                    val wait = when {
                        l.wordSynced -> 350L
                        l.synced -> 900L
                        else -> 2_500L
                    }
                    graceEnd = System.currentTimeMillis() + wait
                }
            }
            coroutineContext.cancelChildren()
        }

        val options = rank(all, query).map { it.candidate.lyrics }
        if (options.isNotEmpty()) writeCache(song.id, options)
        return options
    }

    /** Memeriksa setiap kandidat dan mengurutkannya. Hanya satu hasil terbaik per sumber. */
    private fun rank(all: List<LyricsCandidate>, q: LyricsQuery): List<Scored> =
        all.mapNotNull { c ->
            val base = LyricsMatch.score(q, c.title, c.artist, c.durationSec, c.trust) ?: return@mapNotNull null
            if (!LyricsMatch.plausible(c.lyrics, q.durationSec)) return@mapNotNull null
            // Sumber tanpa metadata: lirik tanpa waktu tidak bisa diverifikasi dengan durasi, jadi tidak dipakai.
            if (c.trust == Trust.BLIND && !c.lyrics.synced) return@mapNotNull null
            // BetterLyrics (bersinkron) selalu diutamakan. Lirik biasa tanpa waktu kalah dari yang bersinkron mana pun.
            val bonus = when {
                c.lyrics.source == PRIMARY && c.lyrics.synced -> 60.0
                c.lyrics.wordSynced -> 14.0
                c.lyrics.synced -> 7.0
                else -> -30.0
            }
            Scored(c, base + bonus)
        }
            .sortedByDescending { it.score }
            .distinctBy { it.candidate.lyrics.source }
            .take(MAX_OPTIONS)

    // ------------------------------------------------------------------ cache

    fun hasCache(id: String): Boolean = fileFor(id).exists()

    private fun fileFor(id: String) = File(dir, id.replace(Regex("[^A-Za-z0-9_-]"), "_") + ".json")

    private suspend fun readCache(id: String): List<Lyrics>? = withContext(Dispatchers.IO) {
        val f = fileFor(id)
        if (!f.exists()) return@withContext null
        try {
            val root = JSONObject(f.readText())
            val options = root.getJSONArray("options")
            val list = (0 until options.length()).map { parse(options.getJSONObject(it)) }
            // Kalau yang tersimpan hanya lirik biasa, coba lagi setelah 3 hari (mungkin sudah ada yang bersinkron).
            val stale = list.none { it.synced } && System.currentTimeMillis() - f.lastModified() > 3 * 86_400_000L
            if (list.isEmpty() || stale) null else list
        } catch (_: Exception) {
            null
        }
    }

    private suspend fun writeCache(id: String, options: List<Lyrics>) = withContext(Dispatchers.IO) {
        try {
            fileFor(id).writeText(JSONObject().put("options", JSONArray().also { a -> options.forEach { a.put(toJson(it)) } }).toString())
        } catch (_: Exception) {
        }
    }

    private fun parse(o: JSONObject): Lyrics {
        val arr = o.getJSONArray("lines")
        return Lyrics(
            lines = (0 until arr.length()).map {
                val a = arr.getJSONArray(it)
                val w = a.optJSONArray(2)
                val words = if (w == null) emptyList() else (0 until w.length()).map { k ->
                    val x = w.getJSONArray(k)
                    LyricWord(x.getLong(0), x.getLong(1), x.getInt(2), x.getInt(3))
                }
                LyricLine(a.getLong(0), a.getString(1), words)
            },
            synced = o.getBoolean("synced"),
            source = o.getString("source")
        )
    }

    private fun toJson(lyrics: Lyrics): JSONObject {
        val lines = JSONArray()
        lyrics.lines.forEach { line ->
            val entry = JSONArray().put(line.timeMs).put(line.text)
            if (line.words.isNotEmpty()) {
                val w = JSONArray()
                line.words.forEach { w.put(JSONArray().put(it.startMs).put(it.endMs).put(it.charStart).put(it.charEnd)) }
                entry.put(w)
            }
            lines.put(entry)
        }
        return JSONObject().put("synced", lyrics.synced).put("source", lyrics.source).put("lines", lines)
    }

    private companion object {
        const val PROVIDER_TIMEOUT_MS = 7_000L
        const val HARD_LIMIT_MS = 8_000L
        const val MAX_OPTIONS = 5
        const val PRIMARY = "BetterLyrics"
    }
}
