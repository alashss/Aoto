package com.jimz.jimztune.data.model

/** Satu kelompok di halaman artis (Album, Single, Video, Artis serupa, dst). Judul diambil apa adanya dari sumber. */
data class CollectionSection(val title: String, val items: List<SearchItem>)

data class ArtistPage(
    val id: String,
    val name: String,
    val thumbnailUrl: String?,
    val description: String?,
    val subscribers: String?,
    val topSongs: List<SearchItem>,
    /** Id halaman "semua lagu" artis ini, kalau tersedia. */
    val allSongsId: String?,
    val sections: List<CollectionSection>
)

data class AlbumPage(
    val id: String,
    val title: String,
    val artistName: String,
    val artistId: String?,
    val info: String,
    val thumbnailUrl: String?,
    val tracks: List<SearchItem>
)
