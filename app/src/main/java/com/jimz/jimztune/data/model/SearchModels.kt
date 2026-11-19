package com.jimz.jimztune.data.model

enum class SearchType(val label: String) {
    SONG("Lagu"),
    PLAYLIST("Playlist"),
    ALBUM("Album"),
    ARTIST("Artis")
}

data class SearchItem(
    val id: String,
    val type: SearchType,
    val title: String,
    val subtitle: String,
    val thumbnailUrl: String?,
    val durationText: String? = null,
    /** Id halaman artis (browseId YouTube Music), kalau sumbernya menyediakan. */
    val artistId: String? = null,
    /** Id halaman album, kalau sumbernya menyediakan. */
    val albumId: String? = null
)

data class SearchResult(val items: List<SearchItem>, val source: String)

private val durationShape = Regex("^\\d{1,3}([:.]\\d{1,2}){1,2}$")

/** Apakah teks ini berbentuk durasi: "3:45", "1:02:10", atau "1.23.42" (YouTube berbahasa Indonesia memakai titik). */
fun looksLikeDuration(text: String?): Boolean = text != null && durationShape.matches(text.trim())

/** Menyeragamkan "1.23.42" menjadi "1:23:42". Teks lain dikembalikan apa adanya. */
fun normalizeDuration(text: String): String = if (looksLikeDuration(text)) text.trim().replace('.', ':') else text

/** Durasi dalam detik dari teks "3:45" / "1:02:10" / "1.02.10". 0 kalau tidak diketahui. */
val SearchItem.durationSeconds: Int
    get() {
        val text = durationText?.trim()
        if (!looksLikeDuration(text)) return 0
        val parts = text!!.split(':', '.').mapNotNull { it.toIntOrNull() }
        return if (parts.isEmpty()) 0 else parts.fold(0) { acc, v -> acc * 60 + v }
    }

/** Kata di judul yang menandakan video kumpulan / mix / loop (bukan satu lagu). */
private val longVersionTitle = Regex(
    "(?i)\\b\\d+\\s*(hours?|hrs?|jam)\\b|" +
        "\\b(loop|looped|looping|nonstop|non-stop|full album|compilation|kumpulan|playlist|megamix|mixtape|mix|" +
        "terbaik|best of|top\\s*\\d{2,}|\\d{2,}\\s*(lagu|songs|tracks))\\b|" +
        // Judul khas kumpulan: "Lagu Santai Viral TikTok 2026", "Lagu-lagu ...", "Paling enak didengar", dst.
        "\\blagu[- ]lagu\\b|\\b(viral|terpopuler|terbaru|populer|hits)\\b.*\\b20\\d{2}\\b|\\b20\\d{2}\\b.*\\b(viral|terpopuler|terbaru|hits)\\b|" +
        "\\b(enak|cocok)\\s+(di)?(dengar|didengar|buat|untuk)\\b|\\b(teman|pengantar)\\s+(kerja|santai|belajar|tidur|perjalanan|ngopi)\\b|" +
        "\\b(tanpa iklan|no ads|full bass|slowed\\s*\\+?\\s*reverb\\s*mix)\\b|\\b(kompilasi)\\b"
)

/** Kata di KATA KUNCI pencarian yang berarti pengguna memang mencari kumpulan / versi panjang. */
private val longQuery = Regex(
    "(?i)\\b(\\d+\\s*(hours?|hrs?|jam)|loop|nonstop|non-stop|full album|compilation|kumpulan|playlist|megamix|mixtape|mix|jam|hours?)\\b"
)

fun queryWantsLongVersion(query: String): Boolean = longQuery.containsMatchIn(query)

/**
 * Video satu lagu yang diulang berjam-jam, atau mix / kumpulan lagu. Dipisah dari lagu biasa di hasil pencarian,
 * dan tidak dicarikan lirik (liriknya tidak mungkin pas dengan video seperti itu).
 * Durasi tidak diketahui + judul mencurigakan juga dianggap kumpulan.
 */
val SearchItem.isLongVersion: Boolean
    get() {
        if (type != SearchType.SONG) return false
        val seconds = durationSeconds
        if (seconds >= 10 * 60) return true
        // Kata seperti "mix" / "loop" bisa saja bagian judul lagu biasa, jadi hanya dihitung kalau durasinya
        // tidak diketahui atau sudah cukup panjang untuk sebuah kumpulan.
        // Kumpulan lagu hampir selalu lebih dari 5 menit. Durasi pendek dengan judul "viral 2026" tetap dianggap lagu.
        return longVersionTitle.containsMatchIn(title) && (seconds == 0 || seconds >= 5 * 60)
    }

/** Satu lagu biasa yang layak muncul di Beranda: durasi diketahui dan wajar (1–10 menit). */
val SearchItem.isNormalSong: Boolean
    get() = type == SearchType.SONG && durationSeconds in 60..(10 * 60) && !isLongVersion
