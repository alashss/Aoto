package com.jimz.jimztune.ui.home

import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.setValue
import androidx.lifecycle.ViewModel
import androidx.lifecycle.viewModelScope
import com.jimz.jimztune.AppGraph
import com.jimz.jimztune.data.model.SearchItem
import com.jimz.jimztune.data.model.SearchType
import com.jimz.jimztune.data.model.isLongVersion
import com.jimz.jimztune.data.model.isNormalSong
import com.jimz.jimztune.data.stream.StreamResolver
import kotlinx.coroutines.CancellationException
import kotlinx.coroutines.Job
import kotlinx.coroutines.delay
import kotlinx.coroutines.launch
import kotlinx.coroutines.sync.Semaphore
import kotlinx.coroutines.sync.withPermit
import kotlinx.coroutines.supervisorScope

data class Shelf(
    val title: String,
    val items: List<SearchItem> = emptyList(),
    val loading: Boolean = true,
    val failed: Boolean = false
)

/**
 * Beranda: tampil seketika dari simpanan terakhir, lalu diperbarui di belakang.
 * Rak dimuat paling banyak 2 sekaligus (dulu 5 rak × semua sumber ditembak bersamaan, jaringan seluler kewalahan
 * dan semuanya "Gagal memuat"), tiap rak dicoba ulang otomatis, dan rak yang gagal tidak menghapus isi lama.
 */
class HomeViewModel : ViewModel() {
    private val repo = AppGraph.searchRepository
    private val cache = AppGraph.homeCache
    private var loadJob: Job? = null

    var shelves by mutableStateOf(DEFAULT_SHELVES.map { Shelf(it.first) })
        private set

    /** Nama chip mood yang sedang dimuat (null = tidak ada). */
    var moodBusy by mutableStateOf<String?>(null)
        private set

    /** Cari lagu sesuai suasana lalu serahkan ke [onResult] untuk diputar. */
    fun playMood(label: String, query: String, onResult: (List<SearchItem>) -> Unit, onFail: () -> Unit) {
        if (moodBusy != null) return
        moodBusy = label
        viewModelScope.launch {
            val songs = try { fetchWithRetry(query) } catch (e: CancellationException) { moodBusy = null; throw e }
            moodBusy = null
            if (songs.isEmpty()) onFail() else onResult(songs.shuffled())
        }
    }

    init {
        val snapshot = cache.read()
        shelves = DEFAULT_SHELVES.map { (title, _) ->
            val saved = snapshot.shelves[title].orEmpty().filter { !it.isLongVersion }
            if (saved.isEmpty()) Shelf(title) else Shelf(title, saved, loading = false)
        }
        val fresh = snapshot.savedAt > 0 &&
            System.currentTimeMillis() - snapshot.savedAt < FRESH_MS &&
            shelves.none { it.items.isEmpty() }
        if (!fresh) load()
    }

    /** Dipanggil saat internet kembali: muat rak yang tadinya kosong atau gagal. */
    fun reloadIfNeeded() {
        if (shelves.any { it.items.isEmpty() || it.failed }) load()
    }

    /** Muat ulang semua rak (rak yang sudah punya isi tetap tampil sampai isi baru tiba). */
    fun load() {
        loadJob?.cancel()
        shelves = shelves.map { if (it.items.isEmpty()) Shelf(it.title) else it }
        loadJob = viewModelScope.launch {
            val gate = Semaphore(2)
            supervisorScope {
                DEFAULT_SHELVES.forEachIndexed { index, (title, query) ->
                    launch {
                        gate.withPermit {
                            val songs = fetchWithRetry(query)
                            val old = shelves[index]
                            val next = when {
                                songs.isNotEmpty() -> Shelf(title, songs, loading = false)
                                old.items.isNotEmpty() -> old.copy(loading = false, failed = false)
                                else -> Shelf(title, emptyList(), loading = false, failed = true)
                            }
                            shelves = shelves.toMutableList().also { it[index] = next }
                        }
                    }
                }
            }
            cache.write(shelves.associate { it.title to it.items })
            // Siapkan alamat audio 2 lagu teratas, jadi ketukan pertama langsung berbunyi.
            shelves.firstOrNull()?.items?.take(2)?.forEach { StreamResolver.prefetch(it.id) }
        }
    }

    private suspend fun fetchWithRetry(query: String): List<SearchItem> {
        repeat(ATTEMPTS) { attempt ->
            if (attempt > 0) delay(1_200L * attempt)
            try {
                val items = repo.search(query, SearchType.SONG, quick = true).items
                val songs = items.filter { it.isNormalSong }
                    .ifEmpty { items.filter { it.type == SearchType.SONG && !it.isLongVersion } }
                if (songs.isNotEmpty()) return songs.take(15)
            } catch (e: CancellationException) {
                throw e
            } catch (_: Exception) {
            }
        }
        return emptyList()
    }

    private companion object {
        const val ATTEMPTS = 3
        const val FRESH_MS = 20 * 60_000L

        val DEFAULT_SHELVES = listOf(
            "Lagu populer Indonesia" to "lagu indonesia populer 2026",
            "Hits dunia" to "top hits 2026",
            "Anime & J-Pop" to "anime opening songs best",
            "Santai & lofi" to "lofi chill songs",
            "Nostalgia 2000-an" to "lagu nostalgia 2000an indonesia"
        )
    }
}
