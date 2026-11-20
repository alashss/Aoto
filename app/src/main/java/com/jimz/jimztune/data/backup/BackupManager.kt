package com.jimz.jimztune.data.backup

import android.content.Context
import android.net.Uri
import org.json.JSONArray
import org.json.JSONObject

/**
 * Cadangan lokal: menyimpan pengaturan (tema, gaya, lirik, Dynamic Island, widget, streak, riwayat) ke satu berkas JSON
 * yang dipilih pengguna, dan memulihkannya. Data akun, token, dan pengaturan server SENGAJA tidak ikut.
 */
object BackupManager {
    private const val FORMAT = 1
    private const val MAX_BYTES = 2 * 1024 * 1024
    private const val MAX_ENTRIES = 5000
    private const val MAX_STRING = 200_000
    private val FILES = listOf(
        "jimztune_settings", "jimztune_theme", "jimztune_lyrics", "jimztune_island", "jimztune_widget",
        "jimztune_streak", "jimztune_history", "jimztune_search_history", "jimztune_listen"
    )

    fun export(context: Context, uri: Uri): Result<Int> = runCatching {
        val root = JSONObject().put("app", "aoto").put("format", FORMAT)
        val all = JSONObject()
        var count = 0
        for (name in FILES) {
            val prefs = context.getSharedPreferences(name, Context.MODE_PRIVATE)
            val o = JSONObject()
            for ((k, v) in prefs.all) {
                val e = JSONObject()
                when (v) {
                    is Boolean -> e.put("t", "b").put("v", v)
                    is Int -> e.put("t", "i").put("v", v)
                    is Long -> e.put("t", "l").put("v", v)
                    is Float -> e.put("t", "f").put("v", v.toDouble())
                    is String -> e.put("t", "s").put("v", v)
                    is Set<*> -> e.put("t", "S").put("v", JSONArray(v.map { it.toString() }))
                    else -> continue
                }
                o.put(k, e)
                count++
            }
            all.put(name, o)
        }
        root.put("prefs", all)
        context.contentResolver.openOutputStream(uri, "wt")!!.use { it.write(root.toString().toByteArray()) }
        count
    }

    fun restore(context: Context, uri: Uri): Result<Int> = runCatching {
        val text = context.contentResolver.openInputStream(uri)!!.use { ins ->
            readCapped(ins, MAX_BYTES).toString(Charsets.UTF_8)
        }
        val root = JSONObject(text)
        require(root.optString("app") == "aoto") { "Bukan berkas cadangan Aoto" }
        val all = root.getJSONObject("prefs")
        var count = 0
        for (name in FILES) {
            val o = all.optJSONObject(name) ?: continue
            val prefs = context.getSharedPreferences(name, Context.MODE_PRIVATE)
            val existing = prefs.all
            val ed = prefs.edit().clear()
            for (k in o.keys()) {
                if (count >= MAX_ENTRIES || k.length > 120) continue
                val e = o.getJSONObject(k)
                val t = e.getString("t")
                // Tipe harus sama dengan nilai yang sudah ada, supaya berkas rusak/jahat tidak bisa membuat aplikasi crash saat dibuka.
                val old = existing[k]
                if (old != null) {
                    val same = when (t) {
                        "b" -> old is Boolean; "i" -> old is Int; "l" -> old is Long; "f" -> old is Float
                        "s" -> old is String; "S" -> old is Set<*>; else -> false
                    }
                    if (!same) continue
                }
                if (t == "s" && e.getString("v").length > MAX_STRING) continue
                when (t) {
                    "b" -> ed.putBoolean(k, e.getBoolean("v"))
                    "i" -> ed.putInt(k, e.getInt("v"))
                    "l" -> ed.putLong(k, e.getLong("v"))
                    "f" -> ed.putFloat(k, e.getDouble("v").toFloat())
                    "s" -> ed.putString(k, e.getString("v"))
                    "S" -> {
                        val a = e.getJSONArray("v")
                        ed.putStringSet(k, (0 until a.length()).map { a.getString(it) }.toSet())
                    }
                    else -> continue
                }
                count++
            }
            ed.apply()
        }
        count
    }

    /** Membaca paling banyak [max] byte; lebih dari itu dianggap berkas tidak wajar. */
    fun readCapped(ins: java.io.InputStream, max: Int): ByteArray {
        val out = java.io.ByteArrayOutputStream()
        val buf = ByteArray(8192)
        var total = 0
        while (true) {
            val n = ins.read(buf)
            if (n < 0) break
            total += n
            require(total <= max) { "Berkas terlalu besar" }
            out.write(buf, 0, n)
        }
        return out.toByteArray()
    }
}
