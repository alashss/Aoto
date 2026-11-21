package com.jimz.jimztune.util

import android.content.Context
import org.json.JSONArray
import org.json.JSONObject

/**
 * Catatan lagu yang gagal diputar di perangkat ini (maksimal 60, yang terbaru di atas). Disimpan lokal dan hanya
 * dilihat lewat Panel Owner; tidak dikirim ke mana pun.
 */
object FailedSongs {
    data class Entry(val id: String, val title: String, val code: String, val count: Int, val lastMs: Long)

    private const val MAX = 60
    private const val PREF = "jimztune_failed_songs"
    private lateinit var appContext: Context

    fun init(context: Context) {
        appContext = context.applicationContext
    }

    private fun prefs() = appContext.getSharedPreferences(PREF, Context.MODE_PRIVATE)

    @Synchronized
    fun load(): List<Entry> {
        if (!::appContext.isInitialized) return emptyList()
        return try {
            val a = JSONArray(prefs().getString("list", "[]") ?: "[]")
            (0 until a.length()).map { i ->
                val o = a.getJSONObject(i)
                Entry(o.getString("id"), o.optString("title"), o.optString("code"), o.optInt("count", 1), o.optLong("t"))
            }
        } catch (_: Exception) {
            emptyList()
        }
    }

    @Synchronized
    fun record(id: String, title: String, code: String) {
        if (!::appContext.isInitialized) return
        val old = load()
        val prev = old.firstOrNull { it.id == id }
        val entry = Entry(id, title.take(120), code.take(60), (prev?.count ?: 0) + 1, System.currentTimeMillis())
        save((listOf(entry) + old.filter { it.id != id }).take(MAX))
    }

    @Synchronized
    fun clear() {
        if (::appContext.isInitialized) prefs().edit().remove("list").apply()
    }

    private fun save(list: List<Entry>) {
        val a = JSONArray()
        list.forEach {
            a.put(JSONObject().put("id", it.id).put("title", it.title).put("code", it.code).put("count", it.count).put("t", it.lastMs))
        }
        prefs().edit().putString("list", a.toString()).apply()
    }
}
