package com.jimz.jimztune.data.home

import android.content.Context
import com.jimz.jimztune.data.library.toJson
import com.jimz.jimztune.data.library.toSong
import com.jimz.jimztune.data.model.SearchItem
import org.json.JSONArray
import org.json.JSONObject

/**
 * Simpanan rak Beranda di perangkat. Beranda langsung tampil dari sini saat aplikasi dibuka (tanpa menunggu jaringan),
 * lalu diperbarui diam-diam di belakang. Kalau jaringan sedang buruk, rak lama tetap tampil, bukan "Gagal memuat".
 */
class HomeCache(context: Context) {
    private val prefs = context.applicationContext.getSharedPreferences("jimztune_home", Context.MODE_PRIVATE)

    class Snapshot(val shelves: Map<String, List<SearchItem>>, val savedAt: Long)

    fun read(): Snapshot {
        val raw = prefs.getString(KEY, null) ?: return Snapshot(emptyMap(), 0L)
        return try {
            val root = JSONObject(raw)
            val shelves = root.getJSONObject("shelves")
            val map = HashMap<String, List<SearchItem>>()
            for (title in shelves.keys()) {
                val array = shelves.getJSONArray(title)
                map[title] = (0 until array.length()).mapNotNull { array.optJSONObject(it) }.mapNotNull {
                    try { it.toSong() } catch (_: Exception) { null }
                }
            }
            Snapshot(map, root.optLong("savedAt", 0L))
        } catch (_: Exception) {
            Snapshot(emptyMap(), 0L)
        }
    }

    fun write(shelves: Map<String, List<SearchItem>>) {
        try {
            val obj = JSONObject()
            shelves.forEach { (title, items) ->
                if (items.isNotEmpty()) obj.put(title, JSONArray().also { a -> items.forEach { a.put(it.toJson()) } })
            }
            prefs.edit().putString(KEY, JSONObject().put("savedAt", System.currentTimeMillis()).put("shelves", obj).toString()).apply()
        } catch (_: Exception) {
        }
    }

    private companion object {
        const val KEY = "shelves_v1"
    }
}
