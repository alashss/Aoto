package com.jimz.jimztune.data.search

import com.jimz.jimztune.data.model.SearchItem
import com.jimz.jimztune.data.model.SearchResult
import com.jimz.jimztune.data.model.SearchType
import com.jimz.jimztune.data.model.isLongVersion
import com.jimz.jimztune.data.model.queryWantsLongVersion
import kotlinx.coroutines.CancellationException
import kotlinx.coroutines.CoroutineScope
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.SupervisorJob
import kotlinx.coroutines.channels.Channel
import kotlinx.coroutines.launch
import kotlinx.coroutines.withTimeoutOrNull
import org.json.JSONArray
import java.net.URLEncoder

class AllProvidersFailedException(val details: List<String>) :
    Exception("Semua sumber gagal: " + details.joinToString("; "))

/**
 * Menjalankan pencarian di banyak sumber SEKALIGUS lalu menggabungkan hasilnya (tanpa duplikat),
 * diurutkan dari yang paling cocok dengan kata kunci. Sumber lambat tidak menahan hasil: setelah sumber
 * pertama menjawab, sumber lain hanya ditunggu sebentar. Sumber yang gagal berulang kali diistirahatkan.
 */
class SearchRepository(
    private val innerTube: InnerTubeProvider = InnerTubeProvider(),
    // Urutan = prioritas saat skor kecocokan sama. Rilisan resmi (YouTube Music) selalu di depan.
    private val providers: List<SearchProvider> = listOf(
        innerTube,
        InnerTubeProvider(videosOnly = true),
        YouTubeWebProvider(),
        NewPipeSearchProvider(),
        PipedProvider(),
        PipedProvider(videos = true),
        InvidiousProvider()
    )
) {
    private class Health(var failures: Int = 0, var restUntil: Long = 0L)

    private class Outcome(
        val index: Int,
        val provider: SearchProvider,
        val items: List<SearchItem>?,
        val error: Throwable?
    )

    private val health = providers.associate { it.name to Health() }
    private val scope = CoroutineScope(SupervisorJob() + Dispatchers.IO)

    /**
     * [quick] = hanya sumber utama (dipakai rak Beranda supaya tidak menembak semua sumber sekaligus).
     */
    private class Memo(val at: Long, val result: SearchResult)
    private val memo = java.util.concurrent.ConcurrentHashMap<String, Memo>()

    suspend fun search(query: String, type: SearchType, quick: Boolean = false, fastest: Boolean = false): SearchResult {
        // Pencarian yang sama dalam 5 menit langsung dijawab dari ingatan.
        val key = "${type.name}|${quick}|${fastest}|${query.trim().lowercase()}"
        memo[key]?.takeIf { System.currentTimeMillis() - it.at < 5 * 60_000L }?.let { return it.result }
        val r = searchNet(query, type, quick, fastest)
        if (r.items.isNotEmpty()) {
            if (memo.size > 60) memo.clear()
            memo[key] = Memo(System.currentTimeMillis(), r)
        }
        return r
    }

    private suspend fun searchNet(query: String, type: SearchType, quick: Boolean, fastest: Boolean): SearchResult {
        val now = System.currentTimeMillis()
        // [fastest] = hanya YouTube Music dan langsung kembali begitu ada hasil (tanpa menunggu sumber lain).
        // Dipakai impor playlist: ratusan lagu dicari berturut-turut, jadi menunggu 2 detik per lagu sangat terasa.
        val quick = quick || fastest
        val pool = providers.withIndex().filter {
            if (fastest) it.value.name == FASTEST_SOURCE else !quick || it.value.name in QUICK_SOURCES
        }
        // Rak Beranda (quick) mengabaikan masa istirahat: kegagalan sesaat saat aplikasi baru dibuka (jaringan belum siap)
        // tidak boleh membuat sumber utama "dihukum" dan Beranda kosong beberapa menit.
        val active = if (quick) pool else pool
            .filter { health.getValue(it.value.name).restUntil <= now }
            .ifEmpty { pool }

        val channel = Channel<Outcome>(active.size)
        val jobs = active.map { (index, p) ->
            scope.launch {
                val outcome = try {
                    Outcome(index, p, p.search(query, type), null)
                } catch (e: CancellationException) {
                    throw e
                } catch (e: Throwable) {
                    Outcome(index, p, null, e)
                }
                channel.trySend(outcome)
            }
        }

        val results = mutableListOf<Outcome>()
        val errors = mutableListOf<String>()
        var anyOk = false
        try {
            val hardEnd = System.currentTimeMillis() + HARD_LIMIT_MS
            var graceEnd = Long.MAX_VALUE
            var pending = active.size
            while (pending > 0) {
                val remaining = minOf(hardEnd, graceEnd) - System.currentTimeMillis()
                if (remaining <= 0) break
                val o = withTimeoutOrNull(remaining) { channel.receive() } ?: break
                pending--
                val h = health.getValue(o.provider.name)
                when {
                    o.items != null -> {
                        anyOk = true
                        h.failures = 0
                        h.restUntil = 0L
                        if (o.items.isNotEmpty()) {
                            results += o
                            if (graceEnd == Long.MAX_VALUE) graceEnd = System.currentTimeMillis() + if (fastest) 0L else GRACE_MS
                        }
                    }
                    o.error is UnsupportedTypeException -> Unit
                    quick -> errors += "${o.provider.name}: ${o.error?.message}"
                    else -> {
                        h.failures++
                        if (h.failures >= 2) h.restUntil = System.currentTimeMillis() + minOf(h.failures, 5) * 60_000L
                        errors += "${o.provider.name}: ${o.error?.message}"
                    }
                }
            }
        } finally {
            jobs.forEach { it.cancel() }
        }

        if (results.isEmpty()) {
            if (anyOk) return SearchResult(emptyList(), providers.first().name)
            throw AllProvidersFailedException(errors)
        }
        val merged = merge(query, type, results.sortedBy { it.index })
        val names = results.sortedBy { it.index }.map { it.provider.name }
        val label = if (names.size <= 2) names.joinToString(" + ") else "${names.first()} +${names.size - 1} sumber"
        return SearchResult(merged, label)
    }

    private fun merge(query: String, type: SearchType, results: List<Outcome>): List<SearchItem> {
        val tokens = tokenize(query)
        val wantsLong = queryWantsLongVersion(query)
        class Ranked(val item: SearchItem, val score: Int, val order: Int)

        val seenIds = HashSet<String>()
        val seenKeys = HashSet<String>()
        val ranked = ArrayList<Ranked>()
        for (o in results) {
            o.items.orEmpty().forEachIndexed { i, item ->
                if (!seenIds.add(item.id)) return@forEachIndexed
                val key = norm(item.title) + "|" + norm(item.subtitle.substringBefore(" • "))
                if (!seenKeys.add(key)) return@forEachIndexed

                val titleN = norm(item.title)
                val haystack = norm(item.title + " " + item.subtitle)
                val inTitle = if (tokens.isEmpty()) 0 else tokens.count { titleN.contains(it) }
                val hits = if (tokens.isEmpty()) 0 else tokens.count { haystack.contains(it) }
                // Kata kunci yang ada di JUDUL lebih berharga daripada yang cuma cocok dengan nama artis
                // (cari "december" jangan memenangkan lagu-lagu milik artis bernama December).
                var score = when {
                    tokens.isEmpty() -> 2
                    inTitle == tokens.size -> 3
                    hits == tokens.size -> 2
                    hits * 2 >= tokens.size -> 1
                    else -> 0
                }
                val official = Regex("(?i)(- topic|vevo|official|records|recordings|music)\\s*$").containsMatchIn(item.subtitle.substringBefore(" • ")) ||
                    item.subtitle.contains(" • ")
                val officialBonus = if (official && score >= 2) 1 else 0
                var order = o.index * 1000 + i
                // Mix / kumpulan lagu jangan mengalahkan lagu biasa, kecuali kata kuncinya memang meminta itu
                // ("mix", "jam", "loop", "full album", ...).
                if (type == SearchType.SONG && item.isLongVersion && !wantsLong) {
                    score = -1
                    order += 100_000
                }
                ranked += Ranked(item, score * 2 + officialBonus, order)
            }
        }
        return ranked.sortedWith(compareByDescending<Ranked> { it.score }.thenBy { it.order }).map { it.item }
    }

    private fun tokenize(q: String): List<String> =
        q.lowercase().split(Regex("[^a-z0-9\\p{L}]+")).filter { it.length >= 2 }

    private fun norm(s: String): String = s.lowercase().filter { it.isLetterOrDigit() }

    private fun secondsOf(text: String?): Int {
        if (text.isNullOrBlank()) return 0
        return text.trim().split(':', '.').mapNotNull { it.toIntOrNull() }.fold(0) { acc, v -> acc * 60 + v }
    }

    suspend fun suggestions(query: String): List<String> {
        try {
            val result = innerTube.suggestions(query)
            if (result.isNotEmpty()) return result.take(8)
        } catch (e: CancellationException) {
            throw e
        } catch (_: Exception) {
        }
        return try {
            val q = URLEncoder.encode(query, "UTF-8")
            val array = JSONArray(Http.get("https://suggestqueries.google.com/complete/search?client=firefox&ds=yt&q=$q"))
            val list = array.getJSONArray(1)
            (0 until list.length()).map { list.getString(it) }.take(8)
        } catch (e: CancellationException) {
            throw e
        } catch (_: Exception) {
            emptyList()
        }
    }

    private companion object {
        val QUICK_SOURCES = setOf("YouTube Music", "YouTube")
        const val FASTEST_SOURCE = "YouTube Music"
        const val HARD_LIMIT_MS = 6_000L
        const val GRACE_MS = 700L
    }
}
