package com.jimz.jimztune.data.stream

import com.jimz.jimztune.AppGraph
import com.jimz.jimztune.data.search.obj
import org.json.JSONObject

/**
 * Menyimpan data kenyaringan (loudnessDb) per lagu yang dikirim YouTube di respons pemutarnya.
 * Dipakai untuk normalisasi volume antar lagu. Disimpan permanen supaya lagu unduhan (offline) juga ternormalisasi.
 */
object LoudnessStore {
    private const val MAX = 3000
    private val map = object : LinkedHashMap<String, Float>(256, 0.75f, true) {
        override fun removeEldestEntry(eldest: MutableMap.MutableEntry<String, Float>?): Boolean = size > MAX
    }
    private var loaded = false

    private val prefs by lazy { AppGraph.appContext.getSharedPreferences("jimztune_loudness", 0) }

    @Synchronized
    private fun ensureLoaded() {
        if (loaded) return
        loaded = true
        try {
            prefs.getString("map", "")?.split(',')?.forEach {
                val i = it.indexOf(':')
                if (i > 0) it.substring(i + 1).toFloatOrNull()?.let { v -> map[it.substring(0, i)] = v }
            }
        } catch (_: Exception) {
        }
    }

    @Synchronized
    fun get(videoId: String): Float? {
        ensureLoaded()
        return map[videoId]
    }

    @Synchronized
    fun put(videoId: String, db: Float) {
        ensureLoaded()
        if (map[videoId] == db) return
        map[videoId] = db
        try {
            prefs.edit().putString("map", map.entries.joinToString(",") { "${it.key}:${it.value}" }).apply()
        } catch (_: Exception) {
        }
    }

    /** Membaca loudnessDb dari respons /player YouTube (kalau ada). Tidak pernah melempar error. */
    fun capture(videoId: String, playerResponse: JSONObject) {
        try {
            val db = playerResponse.obj("playerConfig", "audioConfig")?.optDouble("loudnessDb", Double.NaN)
            if (db != null && !db.isNaN()) put(videoId, db.toFloat())
        } catch (_: Exception) {
        }
    }
}
