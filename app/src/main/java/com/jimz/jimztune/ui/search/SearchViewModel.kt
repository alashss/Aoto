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
            delay(120)
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
                    val result = if (type == SearchType.SONG) withArtistOnTop(q) else repo.search(q, type)
                    // Siapkan alamat audio 3 hasil teratas: ketukan pertama langsung berbunyi.
                    result.items.filter { it.type == SearchType.SONG }.take(3).forEach { com.jimz.jimztune.data.stream.StreamResolver.prefetch(it.id) }
                    SearchUiState.Success(result.items, result.source)
                }
            } catch (e: CancellationException) {
                throw e
            } catch (e: Exception) {
                SearchUiState.Error("Semua sumber sedang bermasalah. Coba lagi sebentar lagi.")
            }
        }
    }

    /**
     * Kalau ketikan cocok dengan nama artis: kartu artis di paling atas, lalu lagu populernya dari halaman artis,
     * baru hasil pencarian biasa (lagu yang memang dibawakan artis itu didahulukan dari cover / lagu orang lain).
     */
    private suspend fun withArtistOnTop(q: String): com.jimz.jimztune.data.model.SearchResult = kotlinx.coroutines.coroutineScope {
        val songsD = async { repo.search(q, SearchType.SONG) }
        // Sumber resmi (YouTube Music): rilis dari artisnya sendiri, diurutkan menurut kepopuleran.
        val ytmD = async {
            try {
                kotlinx.coroutines.withTimeoutOrNull(3_000) { repo.search(q, SearchType.SONG, fastest = true).items }.orEmpty()
            } catch (e: CancellationException) { throw e } catch (_: Exception) { emptyList() }
        }
        val artistD = async {
            try {
                kotlinx.coroutines.withTimeoutOrNull(3_000) { repo.search(q, SearchType.ARTIST, quick = true).items.firstOrNull { it.type == SearchType.ARTIST } }
            } catch (e: CancellationException) { throw e } catch (_: Exception) { null }
        }
        val songs0 = songsD.await()
        val ytm = ytmD.await().filter { it.type == SearchType.SONG }
        val artist = artistD.await()
        fun norm(x: String) = x.lowercase().filter { it.isLetterOrDigit() }
        val qTokens = q.lowercase().split(Regex("[^\\p{L}\\p{N}]+")).filter { it.length >= 2 }
        // rilis resmi yang judul/artisnya cocok dengan ketikan naik ke depan; sisanya (hasil biasa) menyusul tanpa duplikat
        val official = ytm.filter { it0 -> val hay = norm(it0.title + " " + it0.subtitle); qTokens.isNotEmpty() && qTokens.all { tk -> hay.contains(tk) } }
        val seenKeys = HashSet<String>()
        fun keyOf(it0: SearchItem) = norm(it0.title) + "|" + norm(it0.subtitle.substringBefore(" • ").substringBefore(","))
        val songs = songs0.copy(items = (official + songs0.items).filter { seenKeys.add(it.id) && seenKeys.add(keyOf(it)) })
        fun n(x: String) = x.lowercase().filter { it.isLetterOrDigit() }
        val key = n(q); val an = artist?.let { n(it.title) }.orEmpty()
        // Tab Lagu hanya berisi lagu: artis (juga playlist/album) dibuang dari hasil.
        val onlySongs0 = songs.copy(items = songs.items.filter { it.type == SearchType.SONG })
        val onlySongs = if (official.isEmpty()) onlySongs0.copy(items = promoteOfficial(onlySongs0.items)) else onlySongs0
        // Kartu artis hanya untuk artis yang benar-benar terkenal (>= 300 rb subscriber); artis kecil cukup di tab Artis.
        if (artist == null || key.length < 3 || an.length < 3 || !(an == key || an.startsWith(key) || key.startsWith(an)) || subscribersOf(artist.subtitle) < 300_000L) return@coroutineScope onlySongs
        val top = try {
            kotlinx.coroutines.withTimeoutOrNull(3_500) { AppGraph.detail.artist(artist.id).topSongs }.orEmpty()
        } catch (e: CancellationException) { throw e } catch (_: Exception) { emptyList() }
        val rest = onlySongs.items
        val (own, other) = rest.partition { n(it.subtitle.substringBefore(" • ")).contains(an) }
        val seen = HashSet<String>()
        val merged = (listOf(artist) + top.filter { it.type == SearchType.SONG } + own + other)
            .filter { seen.add(it.id) }
        onlySongs.copy(items = merged)
    }

    /**
     * Hasil teratas sering unggahan label/kanal lain ("Neck Deep - December" dari Hopeless Records). Cari versi resmi
     * dari artisnya di YouTube Music dan taruh di paling atas kalau ada.
     */
    private suspend fun promoteOfficial(items: List<SearchItem>): List<SearchItem> {
        val top = items.firstOrNull { it.type == SearchType.SONG } ?: return items
        val (title, artist) = com.jimz.jimztune.data.lyrics.LyricsMatch.splitTitleArtist(top.title, top.subtitle.substringBefore(" • "))
        if (artist.isBlank() || title.isBlank()) return items
        val found = try {
            kotlinx.coroutines.withTimeoutOrNull(2_500) { repo.search("$artist $title", SearchType.SONG, fastest = true).items }
        } catch (e: CancellationException) { throw e } catch (_: Exception) { null } ?: return items
        fun n(x: String) = x.lowercase().filter { it.isLetterOrDigit() }
        val official = found.firstOrNull {
            it.type == SearchType.SONG && n(it.title) == n(title) &&
                com.jimz.jimztune.data.lyrics.LyricsMatch.artistSimilarity(artist, it.subtitle.substringBefore(" • ")) >= 0.8
        } ?: return items
        if (official.id == top.id) return items
        return listOf(official) + items.filter { it.id != official.id }
    }

    /** "10,9 rb subscriber" -> 10900; "1,2 jt" -> 1200000; "3.4M" -> 3400000. 0 kalau tidak terbaca. */
    private fun subscribersOf(text: String): Long {
        val m = Regex("(?i)([0-9]+(?:[.,][0-9]+)?)\\s*(rb|ribu|k|jt|juta|m|miliar|b)?\\b").find(text.substringAfter("•", text)) ?: return 0L
        val n = m.groupValues[1].replace(',', '.').toDoubleOrNull() ?: return 0L
        val mult = when (m.groupValues[2].lowercase()) { "rb", "ribu", "k" -> 1_000.0; "jt", "juta", "m" -> 1_000_000.0; "miliar", "b" -> 1_000_000_000.0; else -> 1.0 }
        return (n * mult).toLong()
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
