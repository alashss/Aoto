package com.jimz.jimztune.ui.search

import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.setValue
import androidx.lifecycle.ViewModel
import androidx.lifecycle.viewModelScope
import com.jimz.jimztune.AppGraph
import com.jimz.jimztune.data.model.SearchItem
import com.jimz.jimztune.data.model.SearchType
import com.jimz.jimztune.data.model.isLongVersion
import kotlinx.coroutines.CancellationException
import kotlinx.coroutines.Job
import kotlinx.coroutines.async
import kotlinx.coroutines.delay
import kotlinx.coroutines.launch

sealed interface SearchUiState {
    data object Idle : SearchUiState
    data object Loading : SearchUiState
    data class Success(val items: List<SearchItem>, val source: String) : SearchUiState
    data class Error(val message: String) : SearchUiState
}

/** Satu baris di daftar saran: dari riwayat pencarian (ikon jam) atau dari saran YouTube. */
data class SuggestionRow(val text: String, val fromHistory: Boolean)

class SearchViewModel : ViewModel() {
    private val repo = AppGraph.searchRepository
    private val store = AppGraph.searchHistory

    var history by mutableStateOf(store.load())
        private set
    var focused by mutableStateOf(false)
        private set

    var query by mutableStateOf("")
        private set
    var type by mutableStateOf(SearchType.SONG)
        private set
    var state by mutableStateOf<SearchUiState>(SearchUiState.Idle)
        private set
    /** Mode "Cari lewat lirik": ketikan dianggap potongan lirik, bukan judul. */
    var lyricMode by mutableStateOf(false)
        private set
    var suggestions by mutableStateOf<List<String>>(emptyList())
        private set
    var showSuggestions by mutableStateOf(false)
        private set

    /** Riwayat yang cocok dengan ketikan (atau semua riwayat kalau kolom masih kosong), lalu saran dari YouTube. */
    val suggestionRows: List<SuggestionRow>
        get() {
            val q = query.trim()
            val fromHistory = if (q.isEmpty()) history.take(12)
            else history.filter { it.contains(q, ignoreCase = true) && !it.equals(q, ignoreCase = true) }.take(3)
            val remote = suggestions.filter { s -> fromHistory.none { it.equals(s, ignoreCase = true) } }
            return fromHistory.map { SuggestionRow(it, true) } + remote.map { SuggestionRow(it, false) }
        }

    fun onFocusChange(isFocused: Boolean) {
        focused = isFocused
        if (isFocused) showSuggestions = true
    }

    fun removeHistory(text: String) {
        history = store.remove(text)
    }

    fun clearHistory() {
        store.clear()
        history = emptyList()
    }

    /**
     * Sampul contoh lagu untuk tiap card genre (kunci = kata kunci genre). Dimuat satu-dua per waktu, menunggu kalau
     * pengguna sedang mencari, dan disimpan di perangkat. Dulu ~20 permintaan jalan sekaligus dan bersaing dengan pencarian
     * sungguhan, sehingga hasil/sampul sering telat atau tidak muncul.
     */
    var genreCovers by mutableStateOf<Map<String, String>>(loadCoverCache())
        private set
    private val coverRequested = HashSet<String>()
    private val coverGate = kotlinx.coroutines.sync.Semaphore(2)

    private fun loadCoverCache(): Map<String, String> = try {
        val o = org.json.JSONObject(
            AppGraph.appContext.getSharedPreferences("jimztune_genre_covers", android.content.Context.MODE_PRIVATE).getString("map", "{}") ?: "{}"
        )
        o.keys().asSequence().associateWith { o.getString(it) }
    } catch (_: Exception) {
        emptyMap()
    }

    private fun saveCoverCache() {
        try {
            val o = org.json.JSONObject()
            genreCovers.forEach { (k, v) -> o.put(k, v) }
            AppGraph.appContext.getSharedPreferences("jimztune_genre_covers", android.content.Context.MODE_PRIVATE)
                .edit().putString("map", o.toString()).apply()
        } catch (_: Exception) {
        }
    }

    fun loadGenreCover(query: String) {
        if (genreCovers.containsKey(query) || !coverRequested.add(query)) return
        viewModelScope.launch {
            try {
                coverGate.acquire()
                try {
                    while (state is SearchUiState.Loading) delay(400)
                    val cover = repo.search(query, SearchType.SONG, quick = true).items
                        .firstOrNull { it.type == SearchType.SONG && !it.thumbnailUrl.isNullOrBlank() }
                        ?.thumbnailUrl
                    if (cover != null) {
                        genreCovers = genreCovers + (query to cover)
                        saveCoverCache()
                    }
                } finally {
                    coverGate.release()
                }
            } catch (e: CancellationException) {
                coverRequested.remove(query)
                throw e
            } catch (e: Exception) {
                coverRequested.remove(query) // boleh dicoba lagi nanti
            }
        }
    }

    private var searchJob: Job? = null
    private var suggestJob: Job? = null

    fun onQueryChange(q: String) {
        query = q
        showSuggestions = true
        suggestJob?.cancel()
        val t = q.trim()
        if (t.length < 2) {
            suggestions = emptyList()
            return
        }
        suggestJob = viewModelScope.launch {
            delay(250)
            suggestions = repo.suggestions(t)
        }
    }

    fun submit(text: String = query) {
        val t = text.trim()
        if (t.isEmpty()) return
        query = t
        showSuggestions = false
        suggestJob?.cancel()
        suggestions = emptyList()
        history = store.add(t)
        runSearch(t)
    }

    fun selectType(newType: SearchType) {
        if (newType == type) return
        type = newType
        if (state !is SearchUiState.Idle) {
            query.trim().takeIf { it.isNotEmpty() }?.let { runSearch(it) }
        }
    }

    fun toggleLyricMode() {
        lyricMode = !lyricMode
        if (state !is SearchUiState.Idle) {
            query.trim().takeIf { it.isNotEmpty() }?.let { runSearch(it) }
        }
    }

    fun retry() {
        query.trim().takeIf { it.isNotEmpty() }?.let { runSearch(it) }
    }

    fun clear() {
        searchJob?.cancel()
        suggestJob?.cancel()
        query = ""
        suggestions = emptyList()
        showSuggestions = focused
        state = SearchUiState.Idle
    }

    private fun runSearch(q: String) {
        searchJob?.cancel()
        state = SearchUiState.Loading
        searchJob = viewModelScope.launch {
            state = try {
                if (lyricMode) {
                    SearchUiState.Success(lyricResults(q), "Lirik")
                } else {
                    val result = repo.search(q, type)
                    SearchUiState.Success(result.items, result.source)
                }
            } catch (e: CancellationException) {
                throw e
            } catch (e: Exception) {
                SearchUiState.Error("Semua sumber sedang bermasalah. Coba lagi sebentar lagi.")
            }
        }
    }

    /** Lagu yang liriknya mengandung [q]: cari di NetEase, cocokkan ke lagu yang bisa diputar, lalu lengkapi dengan pencarian biasa. */
    private suspend fun lyricResults(q: String): List<SearchItem> = kotlinx.coroutines.coroutineScope {
        val hits = try {
            com.jimz.jimztune.data.search.LyricSearch.find(q)
        } catch (e: CancellationException) {
            throw e
        } catch (_: Exception) {
            emptyList()
        }
        val gate = kotlinx.coroutines.sync.Semaphore(3)
        val resolved = hits.map { h ->
            async {
                gate.acquire()
                try {
                    repo.search("${h.artist} ${h.title}".trim(), SearchType.SONG, quick = true).items
                        .firstOrNull { it.type == SearchType.SONG && !it.isLongVersion }
                } catch (e: CancellationException) {
                    throw e
                } catch (_: Exception) {
                    null
                } finally {
                    gate.release()
                }
            }
        }.map { it.await() }.filterNotNull()
        val extra = if (resolved.size >= 3) emptyList() else try {
            repo.search("$q lirik", SearchType.SONG).items.filter { it.type == SearchType.SONG && !it.isLongVersion }
        } catch (e: CancellationException) {
            throw e
        } catch (_: Exception) {
            emptyList()
        }
        (resolved + extra).distinctBy { it.id }
    }
}
