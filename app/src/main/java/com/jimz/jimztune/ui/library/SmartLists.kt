package com.jimz.jimztune.ui.library

import com.jimz.jimztune.AppGraph
import com.jimz.jimztune.data.model.SearchItem
import com.jimz.jimztune.data.model.SearchType
import com.jimz.jimztune.data.stats.PlayLog
import java.time.LocalDate

/** Playlist cerdas: dibuat otomatis dari riwayat putar, tidak disimpan dan tidak bisa diubah. */
object SmartLists {
    class Smart(val id: String, val title: String, val hint: String, val songs: List<SearchItem>)

    private const val OLD_DAYS = 45L

    fun build(): List<Smart> {
        val today = LocalDate.now()
        val library = AppGraph.library
        val recent = AppGraph.history.items.filter { it.type == SearchType.SONG }.distinctBy { it.id }.take(50)
        val month = PlayLog.topSongs(today.minusDays(30), today, 50).map {
            SearchItem(it.id, SearchType.SONG, it.title, it.artist, it.art)
        }
        val playedRecently = PlayLog.topSongs(today.minusDays(OLD_DAYS), today, 5000).map { it.id }.toSet()
        val pool = (library.favorites + library.playlists.flatMap { it.songs })
            .filter { it.type == SearchType.SONG }
            .distinctBy { it.id }
        val old = pool.filter { it.id !in playedRecently }.take(100)
        return listOf(
            Smart("new", "Baru kamu dengar", "Lagu yang terakhir diputar", recent),
            Smart("month", "Favorit bulan ini", "Paling sering diputar 30 hari terakhir", month),
            Smart("old", "Lama nggak diputar", "Favorit dan isi playlist yang belum diputar $OLD_DAYS hari", old)
        )
    }
}
