package com.jimz.jimztune.data.lyrics

interface LyricsProvider {
    val name: String

    /** Mengembalikan semua kandidat yang masuk akal dari sumber ini (kosong kalau tidak ada). Pemilihan dilakukan di repository. */
    suspend fun fetch(q: LyricsQuery): List<LyricsCandidate>
}
