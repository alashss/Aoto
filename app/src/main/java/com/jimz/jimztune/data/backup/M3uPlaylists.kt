package com.jimz.jimztune.data.backup

import android.content.Context
import android.net.Uri
import com.jimz.jimztune.AppGraph
import com.jimz.jimztune.data.library.Playlist
import com.jimz.jimztune.data.model.SearchItem
import com.jimz.jimztune.data.model.SearchType

/** Ekspor/impor playlist ke berkas M3U biasa (teks). Satu berkas bisa memuat banyak playlist lewat baris #PLAYLIST:. */
object M3uPlaylists {
    private val idRegex = Regex("(?:v=|youtu\\.be/|/watch/)([A-Za-z0-9_-]{11})")

    fun export(context: Context, uri: Uri, playlists: List<Playlist>): Result<Int> = runCatching {
        val sb = StringBuilder("#EXTM3U\n")
        var n = 0
        for (p in playlists) {
            sb.append("#PLAYLIST:").append(p.name.replace('\n', ' ')).append('\n')
            for (s in p.songs) {
                if (s.type != SearchType.SONG) continue
                val line = if (s.subtitle.isBlank()) s.title else "${s.subtitle} - ${s.title}"
                sb.append("#EXTINF:-1,").append(line.replace('\n', ' ')).append('\n')
                sb.append("https://music.youtube.com/watch?v=").append(s.id).append('\n')
                n++
            }
        }
        context.contentResolver.openOutputStream(uri, "wt")!!.use { it.write(sb.toString().toByteArray()) }
        n
    }

    /** Mengembalikan jumlah lagu yang berhasil diimpor. */
    fun import(context: Context, uri: Uri, fallbackName: String = "Impor M3U"): Result<Int> = runCatching {
        val text = context.contentResolver.openInputStream(uri)!!.use { ins ->
            BackupManager.readCapped(ins, 5 * 1024 * 1024).toString(Charsets.UTF_8)
        }
        val groups = LinkedHashMap<String, MutableList<SearchItem>>()
        var name = fallbackName
        var pendingTitle = ""
        var pendingArtist = ""
        for (raw in text.lineSequence()) {
            val line = raw.trim()
            when {
                line.isEmpty() -> {}
                line.startsWith("#PLAYLIST:") -> name = line.removePrefix("#PLAYLIST:").trim().ifEmpty { fallbackName }
                line.startsWith("#EXTINF:") -> {
                    val label = line.substringAfter(',', "").trim()
                    if (" - " in label) {
                        pendingArtist = label.substringBefore(" - ").trim()
                        pendingTitle = label.substringAfter(" - ").trim()
                    } else {
                        pendingArtist = ""
                        pendingTitle = label
                    }
                }
                line.startsWith("#") -> {}
                else -> {
                    val id = idRegex.find(line)?.groupValues?.get(1)
                    if (id != null && groups.values.sumOf { it.size } < 5000) {
                        groups.getOrPut(name) { mutableListOf() }.add(
                            SearchItem(
                                id = id,
                                type = SearchType.SONG,
                                title = pendingTitle.ifEmpty { id },
                                subtitle = pendingArtist,
                                thumbnailUrl = "https://i.ytimg.com/vi/$id/mqdefault.jpg"
                            )
                        )
                    }
                    pendingTitle = ""
                    pendingArtist = ""
                }
            }
        }
        require(groups.isNotEmpty()) { "Tidak ada lagu YouTube di berkas ini" }
        var total = 0
        for ((n, songs) in groups) {
            AppGraph.library.importPlaylist(n, songs)
            total += songs.distinctBy { it.id }.size
        }
        total
    }
}
