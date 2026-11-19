package com.jimz.jimztune.util

import android.content.Context
import android.content.Intent
import android.widget.Toast
import com.jimz.jimztune.data.model.SearchItem

/** Membuka lembar bagikan Android (WhatsApp, Telegram, salin, dst) dengan [text]. */
fun Context.shareText(subject: String, text: String) {
    try {
        val send = Intent(Intent.ACTION_SEND)
            .setType("text/plain")
            .putExtra(Intent.EXTRA_SUBJECT, subject)
            .putExtra(Intent.EXTRA_TEXT, text)
        startActivity(Intent.createChooser(send, "Bagikan").addFlags(Intent.FLAG_ACTIVITY_NEW_TASK))
    } catch (_: Exception) {
        Toast.makeText(this, "Tidak bisa membuka menu bagikan", Toast.LENGTH_SHORT).show()
    }
}

/** Tautan playlist YouTube Music. Awalan "VL" (id halaman browse) dibuang supaya jadi id daftar putar. */
fun ytPlaylistUrl(id: String): String = "https://music.youtube.com/playlist?list=" + id.removePrefix("VL")

/** Teks bagikan untuk playlist dari pencarian: nama + tautan yang bisa dibuka siapa pun. */
fun searchPlaylistShareText(title: String, id: String): String =
    "$title\n${ytPlaylistUrl(id)}\n\nDibagikan lewat Aoto"

/**
 * Teks bagikan untuk playlist buatan sendiri: satu lagu per baris ("Artis - Judul").
 * Penerima bisa menempelnya di Koleksi → Impor Playlist → Tempel daftar lagu (baris "#" diabaikan).
 */
fun libraryPlaylistShareText(name: String, songs: List<SearchItem>): String = buildString {
    append("# ").append(name).append(" (").append(songs.size).append(" lagu) · dibagikan lewat Aoto\n")
    songs.forEachIndexed { i, s ->
        val artist = s.subtitle.substringBefore(" • ").trim()
        append(i + 1).append(". ")
        if (artist.isNotEmpty()) append(artist).append(" - ")
        append(s.title).append('\n')
    }
}

/** Teks bagikan untuk satu lagu: judul, artis, dan tautan YouTube Music. */
fun songShareText(song: SearchItem): String {
    val artist = song.subtitle.substringBefore(" • ").trim()
    val head = if (artist.isNotEmpty()) "${song.title} - $artist" else song.title
    return "$head\nhttps://music.youtube.com/watch?v=${song.id}\n\nDibagikan lewat Aoto"
}
