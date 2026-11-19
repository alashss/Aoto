package com.jimz.jimztune.data.history

import android.content.Context
import com.jimz.jimztune.AppGraph
import org.json.JSONArray

/** Riwayat kata kunci pencarian (terbaru di depan, maksimal 20), disimpan di perangkat. */
class SearchHistoryStore(context: Context) {
    private val prefs = context.getSharedPreferences("jimztune_search_history", Context.MODE_PRIVATE)

    fun load(): List<String> {
        val raw = prefs.getString(KEY, null) ?: return emptyList()
        return try {
            val array = JSONArray(raw)
            (0 until array.length()).map { array.getString(it) }.filter { it.isNotBlank() }
        } catch (_: Exception) {
            emptyList()
        }
    }

    fun add(query: String): List<String> {
        val q = query.trim()
        if (q.isEmpty()) return load()
        val list = (listOf(q) + load().filterNot { it.equals(q, ignoreCase = true) }).take(MAX)
        save(list)
        return list
    }

    fun remove(query: String): List<String> {
        val list = load().filterNot { it.equals(query, ignoreCase = true) }
        save(list)
        return list
    }

    fun clear() {
        prefs.edit().remove(KEY).apply()
        AppGraph.cloud.markDirty()
    }

    fun exportJson(): JSONArray = JSONArray().also { a -> load().forEach { a.put(it) } }

    /** Menerapkan data dari cloud (tidak memicu unggahan balik). */
    fun applyCloud(array: JSONArray?, merge: Boolean) {
        val cloud = if (array == null) emptyList() else (0 until array.length()).map { array.optString(it) }.filter { it.isNotBlank() }
        val all = if (merge) load() + cloud else cloud
        val out = ArrayList<String>()
        all.forEach { q -> if (out.none { it.equals(q, ignoreCase = true) }) out.add(q) }
        write(out.take(MAX))
    }

    private fun save(list: List<String>) {
        write(list)
        AppGraph.cloud.markDirty()
    }

    private fun write(list: List<String>) {
        val array = JSONArray()
        list.forEach { array.put(it) }
        prefs.edit().putString(KEY, array.toString()).apply()
    }

    private companion object {
        const val KEY = "queries"
        const val MAX = 20
    }
}
