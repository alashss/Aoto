package com.jimz.jimztune.data.history

import android.content.Context
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.setValue
import com.jimz.jimztune.AppGraph
import com.jimz.jimztune.data.library.toJson
import com.jimz.jimztune.data.library.toSong
import com.jimz.jimztune.data.model.SearchItem
import org.json.JSONArray

/** Riwayat lagu yang terakhir diputar (maksimal 50). Disimpan di perangkat dan disinkronkan ke akun. */
class HistoryStore(context: Context) {
    private val prefs = context.getSharedPreferences("jimztune_history", Context.MODE_PRIVATE)

    /** State Compose: layar yang membacanya otomatis ikut berubah (termasuk saat data cloud masuk). */
    var items by mutableStateOf(read())
        private set

    fun load(): List<SearchItem> = items

    fun add(item: SearchItem) {
        items = (listOf(item) + items.filter { it.id != item.id }).take(MAX)
        persist()
        AppGraph.cloud.markDirty()
    }

    fun clear() {
        items = emptyList()
        prefs.edit().remove(KEY).apply()
        AppGraph.cloud.markDirty()
    }

    fun exportJson(): JSONArray = JSONArray().also { a -> items.forEach { a.put(it.toJson()) } }

    /** Menerapkan data dari cloud (tidak memicu unggahan balik). */
    fun applyCloud(array: JSONArray?, merge: Boolean) {
        val cloud = parse(array)
        items = (if (merge) (items + cloud) else cloud).distinctBy { it.id }.take(MAX)
        persist()
    }

    private fun persist() {
        prefs.edit().putString(KEY, exportJson().toString()).apply()
    }

    private fun read(): List<SearchItem> {
        val raw = prefs.getString(KEY, null) ?: return emptyList()
        return try { parse(JSONArray(raw)) } catch (_: Exception) { emptyList() }
    }

    private fun parse(array: JSONArray?): List<SearchItem> {
        if (array == null) return emptyList()
        return (0 until array.length()).mapNotNull { array.optJSONObject(it) }.mapNotNull {
            try { it.toSong() } catch (_: Exception) { null }
        }
    }

    private companion object {
        const val KEY = "recent"
        const val MAX = 50
    }
}
