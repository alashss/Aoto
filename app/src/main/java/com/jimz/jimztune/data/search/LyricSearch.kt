package com.jimz.jimztune.data.search

import java.net.URLEncoder
import org.json.JSONObject

/**
 * Mencari lagu dari potongan lirik. NetEase menyediakan pencarian isi lirik (type 1006); hasilnya hanya judul + artis,
 * yang lalu dicocokkan ke lagu yang bisa diputar lewat pencarian biasa (lihat SearchViewModel).
 */
object LyricSearch {
    class Hit(val title: String, val artist: String)

    private val headers = mapOf(
        "Referer" to "https://music.163.com/",
        "Cookie" to "os=pc; appver=2.9.7"
    )

    suspend fun find(query: String): List<Hit> {
        val kw = URLEncoder.encode(query.trim(), "UTF-8")
        val json = JSONObject(
            Http.get("https://music.163.com/api/search/get/web?csrf_token=&s=$kw&type=1006&offset=0&limit=10", headers)
        )
        val songs = json.obj("result")?.arr("songs")?.objects().orEmpty()
        return songs.mapNotNull { s ->
            val name = s.str("name")
            if (name.isBlank()) return@mapNotNull null
            val artist = s.arr("artists").objects().map { it.str("name") }.filter { it.isNotBlank() }.firstOrNull().orEmpty()
            Hit(name, artist)
        }.distinctBy { (it.title + "|" + it.artist).lowercase() }.take(8)
    }
}
