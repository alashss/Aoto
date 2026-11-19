package com.jimz.jimztune.data.stats

import android.content.Context
import com.jimz.jimztune.AppGraph
import com.jimz.jimztune.data.model.SearchType
import kotlinx.coroutines.CancellationException
import java.util.concurrent.ConcurrentHashMap

/**
 * Foto artis untuk Rekap. Catatan dengar hanya menyimpan nama artis, jadi fotonya dicari lewat pencarian artis
 * (hasil teratas) lalu diingat di perangkat. Artis yang fotonya tidak ketemu dicatat juga (sementara) supaya tidak dicari berulang.
 */
object ArtistPhotos {
    private const val PREFS = "jimztune_artist_photos"
    private val memory = ConcurrentHashMap<String, String>()
    private val missed = ConcurrentHashMap<String, Long>()
    private const val RETRY_MS = 6 * 60 * 60 * 1000L

    private fun prefs() = AppGraph.appContext.getSharedPreferences(PREFS, Context.MODE_PRIVATE)
    private fun key(name: String) = name.trim().lowercase()

    /** Foto yang sudah diingat, tanpa jaringan. */
    fun cached(name: String): String? {
        val k = key(name)
        memory[k]?.let { return it }
        val saved = try {
            prefs().getString(k, null)
        } catch (_: Exception) {
            null
        }
        if (!saved.isNullOrBlank()) memory[k] = saved
        return saved?.takeIf { it.isNotBlank() }
    }

    /** Cari foto artis lewat jaringan (kalau belum diingat). Mengembalikan null kalau tidak ditemukan. */
    suspend fun fetch(name: String): String? {
        cached(name)?.let { return it }
        val k = key(name)
        val lastMiss = missed[k]
        if (lastMiss != null && System.currentTimeMillis() - lastMiss < RETRY_MS) return null
        val url = try {
            val items = AppGraph.searchRepository.search(name, SearchType.ARTIST, quick = true).items
            // Utamakan artis yang namanya sama persis; kalau tidak ada, hasil artis teratas.
            val artists = items.filter { it.type == SearchType.ARTIST }
            (artists.firstOrNull { key(it.title) == k } ?: artists.firstOrNull())?.thumbnailUrl
        } catch (e: CancellationException) {
            throw e
        } catch (_: Exception) {
            null
        }
        if (url.isNullOrBlank()) {
            missed[k] = System.currentTimeMillis()
            return null
        }
        memory[k] = url
        try {
            prefs().edit().putString(k, url).apply()
        } catch (_: Exception) {
        }
        return url
    }
}
