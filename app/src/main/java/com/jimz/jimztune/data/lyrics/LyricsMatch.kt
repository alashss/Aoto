package com.jimz.jimztune.data.lyrics

import java.text.Normalizer
import kotlin.math.abs
import kotlin.math.max
import kotlin.math.min

/**
 * Memeriksa apakah sebuah hasil lirik benar-benar milik lagu yang diputar. Dulu hasil pencarian hanya dipilih
 * dari kemiripan durasi, sehingga lagu lain yang kebetulan panjangnya sama bisa terpilih (lirik tidak nyambung).
 * Sekarang judul, versi (live/remix/akustik/...), artis, durasi, dan isi lirik ikut diperiksa.
 */
object LyricsMatch {
    private val diacritics = Regex("\\p{InCombiningDiacriticalMarks}+")
    private val brackets = Regex("[\\(\\[\\{][^\\)\\]\\}]*[\\)\\]\\}]")
    private val featTail = Regex("\\b(feat|ft|featuring)\\b\\.?.*$")
    private val nonWord = Regex("[^\\p{L}\\p{N}]+")
    private val artistSplit = Regex("\\s*(,|&|;|/|\\bfeat\\b\\.?|\\bft\\b\\.?|\\bx\\b|\\band\\b|\\bdan\\b|•)\\s*", RegexOption.IGNORE_CASE)

    private val versionWords = listOf(
        "live", "remix", "acoustic", "akustik", "instrumental", "karaoke", "cover", "demo", "interlude",
        "slowed", "reverb", "nightcore", "sped", "8d", "extended", "radio", "unplugged", "reprise", "intro", "outro"
    )

    fun normalize(s: String): String {
        var t = Normalizer.normalize(s, Normalizer.Form.NFD).replace(diacritics, "").lowercase()
        t = t.replace(brackets, " ")
        t = t.replace(featTail, " ")
        return t.replace(nonWord, " ").trim()
    }

    /** Judul berakhiran titik-titik ("Nak...", "Nak…"): lagu yang berbeda dari "Nak" biasa. */
    private val trailingEllipsis = Regex("(\\.{2,}|…|\\.\\s\\.\\s\\.)\\s*$")

    fun hasEllipsis(title: String): Boolean = trailingEllipsis.containsMatchIn(title.replace(brackets, " ").trim())

    /**
     * Penanda versi yang ada di judul (termasuk di dalam kurung). Dua judul harus punya penanda yang sama.
     * Titik-titik di ujung judul juga dihitung sebagai penanda, karena `normalize` membuangnya
     * sehingga "Nak" dan "Nak..." (dua lagu Iwan Fals yang berbeda) dulu dianggap judul yang sama.
     */
    fun versionTags(title: String): Set<String> {
        val t = Normalizer.normalize(title, Normalizer.Form.NFD).replace(diacritics, "").lowercase()
            .replace(nonWord, " ")
        val words = t.split(' ').toSet()
        val tags = versionWords.filter { it in words }.toMutableSet()
        if (hasEllipsis(title)) tags += ELLIPSIS_TAG
        return tags
    }

    private const val ELLIPSIS_TAG = "..."

    private fun levenshtein(a: String, b: String): Int {
        if (a == b) return 0
        if (a.isEmpty()) return b.length
        if (b.isEmpty()) return a.length
        var prev = IntArray(b.length + 1) { it }
        var cur = IntArray(b.length + 1)
        for (i in 1..a.length) {
            cur[0] = i
            for (j in 1..b.length) {
                val cost = if (a[i - 1] == b[j - 1]) 0 else 1
                cur[j] = min(min(cur[j - 1] + 1, prev[j] + 1), prev[j - 1] + cost)
            }
            val tmp = prev; prev = cur; cur = tmp
        }
        return prev[b.length]
    }

    /** 0..1, 1 = sama persis (setelah dinormalkan). */
    fun similarity(a: String, b: String): Double {
        val x = normalize(a)
        val y = normalize(b)
        if (x.isEmpty() || y.isEmpty()) return 0.0
        if (x == y) return 1.0
        val lev = 1.0 - levenshtein(x, y).toDouble() / max(x.length, y.length)
        val tx = x.split(' ').toSet()
        val ty = y.split(' ').toSet()
        val jac = tx.intersect(ty).size.toDouble() / tx.union(ty).size
        return max(lev, jac)
    }

    /** -1 = tidak diketahui. Artis dianggap cocok kalau salah satu nama (dari daftar "A, B & C") mirip. */
    fun artistSimilarity(a: String, b: String): Double {
        if (a.isBlank() || b.isBlank()) return -1.0
        val xs = a.split(artistSplit).filter { it.isNotBlank() }.ifEmpty { listOf(a) }
        val ys = b.split(artistSplit).filter { it.isNotBlank() }.ifEmpty { listOf(b) }
        var best = 0.0
        for (x in xs) for (y in ys) {
            val nx = normalize(x)
            val ny = normalize(y)
            val sim = when {
                nx.isEmpty() || ny.isEmpty() -> 0.0
                nx == ny -> 1.0
                nx.length >= 4 && (ny.contains(nx) || nx.contains(ny)) -> 0.9
                else -> similarity(x, y)
            }
            best = max(best, sim)
        }
        return best
    }

    /** Kemungkinan judul sebenarnya: judul YouTube sering berbentuk "Artis - Judul". */
    fun titleVariants(title: String): List<String> {
        val out = linkedSetOf(title)
        val parts = title.split(" - ", " – ", " — ").map { it.trim() }.filter { it.isNotEmpty() }
        if (parts.size >= 2) {
            out += parts[0]
            out += parts[1]
            out += parts.drop(1).joinToString(" - ")
        }
        return out.filter { it.isNotBlank() }
    }

    /**
     * Memisahkan "Artis - Judul" dari judul video. Mengembalikan pasangan (judul, artis) yang dipakai untuk mencari.
     */
    fun splitTitleArtist(title: String, artist: String): Pair<String, String> {
        val parts = title.split(" - ", " – ", " — ", limit = 2).map { it.trim() }
        if (parts.size < 2 || parts[0].isEmpty() || parts[1].isEmpty()) return title to artist
        val left = parts[0]
        val right = parts[1]
        return when {
            artist.isBlank() -> right to left
            artistSimilarity(left, artist) >= 0.6 -> right to artist
            artistSimilarity(right, artist) >= 0.6 -> left to artist
            looksLikeChannel(artist) -> right to left
            else -> title to artist
        }
    }

    private fun looksLikeChannel(name: String): Boolean =
        Regex("(?i)(records|music|entertainment|official|label|channel|tv|media|studio|vevo|t-series|production)").containsMatchIn(name)

    /** Query untuk sumber lirik: judul tanpa "(feat. ...)" supaya cocok dengan katalog. */
    fun searchTitle(title: String): String =
        title.replace(Regex("(?i)\\s*[\\(\\[]\\s*(feat|ft|featuring)\\b[^\\)\\]]*[\\)\\]]"), "").trim()

    /**
     * Skor kecocokan 0..100+, atau null kalau hasil ini jelas bukan lagu yang diputar.
     * [durationSec] <= 0 berarti durasi tidak diketahui (pemeriksaan durasi dilewati).
     */
    fun score(
        q: LyricsQuery,
        title: String,
        artist: String,
        candidateDurationSec: Int,
        trust: Trust = Trust.BY_METADATA
    ): Double? {
        if (trust == Trust.BLIND) return 70.0

        val variants = titleVariants(q.title)
        if (trust == Trust.EXACT_ID) {
            // Pencocokan lewat id video bisa salah kalau ada dua lagu berjudul hampir sama ("Nak" vs "Nak...").
            // Kalau sumber mengirim judulnya dan jelas lagu lain karena titik-titik, tolak.
            if (title.isNotBlank() && ellipsisConflict(variants, title)) return null
            return 100.0
        }
        if (variants.isEmpty()) return null
        val tSim = variants.maxOf { similarity(it, title) }
        if (tSim < 0.75) return null

        // "Song" tidak boleh mengambil lirik "Song (Live)" atau sebaliknya. Hanya varian judul yang memang
        // mirip dengan kandidat yang dihitung (bukan nama artis dari "Artis - Judul").
        val candTags = versionTags(title)
        if (variants.filter { similarity(it, title) >= 0.75 }.none { versionTags(it) == candTags }) return null

        val aSim = artistSimilarity(q.artist, artist)
        val dd = if (q.durationSec > 0 && candidateDurationSec > 0) abs(q.durationSec - candidateDurationSec) else -1

        if (dd > 15) return null
        if (dd > 6 && !(tSim >= 0.9 && aSim >= 0.6)) return null
        if (aSim in 0.0..0.3 && tSim < 0.95) return null
        // Judul pendek/umum ("Home", "Intro") terlalu gampang kembar: wajib artis yang cocok.
        if (normalize(q.title).length <= 6 && aSim in 0.0..0.5) return null

        return tSim * 60 +
            (if (aSim >= 0) aSim * 25 else 10.0) +
            (if (dd >= 0) (15 - dd).coerceAtLeast(0).toDouble() else 8.0)
    }

    /** True kalau judul kandidat mirip salah satu varian judul, tetapi status titik-titiknya berbeda. */
    private fun ellipsisConflict(variants: List<String>, candidateTitle: String): Boolean {
        val similar = variants.filter { similarity(it, candidateTitle) >= 0.75 }
        if (similar.isEmpty()) return false
        val cand = hasEllipsis(candidateTitle)
        return similar.none { hasEllipsis(it) == cand }
    }

    /**
     * Pemeriksaan isi lirik: baris terakhir tidak boleh melewati panjang lagu (itu lirik versi lain yang lebih
     * panjang), dan lirik yang terlalu pendek untuk lagunya (cuma cuplikan) ditolak.
     */
    fun plausible(lyrics: Lyrics, durationSec: Int): Boolean {
        if (!lyrics.synced || durationSec <= 0) return true
        val timed = lyrics.lines.filter { it.timeMs >= 0 }
        if (timed.isEmpty()) return true
        val last = timed.maxOf { it.timeMs }
        if (last > (durationSec + 5) * 1000L) return false
        if (timed.size >= 8 && last < durationSec * 1000L * 0.35) return false
        return true
    }
}
