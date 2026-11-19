package com.jimz.jimztune.ui.player

import androidx.compose.ui.text.TextLayoutResult
import kotlin.math.max
import kotlin.math.min

private fun mix(a: Float, b: Float, t: Float): Float = a + (b - a) * t

/** Bagian slot waktu sebuah kata yang dipakai untuk mengisi kata itu (perkiraan). */
private const val SNAP = 0.8f

/**
 * Pewaktuan karaoke per kata untuk lirik yang hanya punya waktu per baris (format LRC biasa).
 *
 * Waktu satu baris dibagi ke tiap kata menurut jumlah suku katanya, jadi kata panjang ("perasaan") disapu
 * lebih lama daripada kata pendek ("aku"). Kata terakhir diberi bobot lebih karena di akhir baris nada
 * biasanya ditahan. Di antara kata ada jeda singkat. Di dalam kata, isian melambat di awal dan akhir
 * (easing), tidak meluncur lurus dari kiri ke kanan.
 */
internal object LyricTiming {
    /** Satu kata: rentang karakter dan bobot waktunya (kira-kira jumlah suku kata). */
    class Word(val start: Int, val end: Int, val weight: Float)

    /** Jeda antar kata, dalam satuan "suku kata". */
    private const val WORD_GAP = 0.30f

    /** Kata terakhir ditahan lebih lama. */
    private const val LAST_WORD_HOLD = 1.20f

    /** Di bawah jarak ini per suku kata, baris dianggap padat: dinyanyikan nyaris tanpa jeda. */
    private const val DENSE_MS_PER_SYLLABLE = 420f

    private val vowels = "aeiouyàáâãäåèéêëìíîïòóôõöøùúûüAEIOUYÀÁÂÃÄÅÈÉÊËÌÍÎÏÒÓÔÕÖØÙÚÛÜ"

    private fun isVowel(c: Char) = vowels.indexOf(c) >= 0

    /** Aksara tanpa spasi antar kata: tiap karakter dihitung sebagai satu kata. */
    private fun isCjk(c: Char): Boolean {
        val block = Character.UnicodeBlock.of(c)
        return block == Character.UnicodeBlock.HIRAGANA ||
            block == Character.UnicodeBlock.KATAKANA ||
            block == Character.UnicodeBlock.HANGUL_SYLLABLES ||
            block == Character.UnicodeBlock.CJK_UNIFIED_IDEOGRAPHS
    }

    /** Perkiraan jumlah suku kata: banyaknya kelompok huruf vokal (cukup akurat untuk Indonesia dan Inggris). */
    private fun syllables(token: String): Float {
        val letters = token.filter { it.isLetterOrDigit() }
        if (letters.isEmpty()) return 0.3f // tanda baca berdiri sendiri, misalnya "-"
        var groups = 0
        var prevVowel = false
        for (c in letters) {
            val v = isVowel(c)
            if (v && !prevVowel) groups++
            prevVowel = v
        }
        // "love", "time": huruf e di ujung biasanya tidak dibunyikan.
        if (groups > 1 && letters.length >= 2 &&
            (letters.last() == 'e' || letters.last() == 'E') && !isVowel(letters[letters.length - 2])
        ) {
            groups--
        }
        return max(1, groups) + 0.06f * letters.length
    }

    fun words(text: String): List<Word> {
        val out = ArrayList<Word>()
        var i = 0
        while (i < text.length) {
            val c = text[i]
            when {
                c.isWhitespace() -> i++
                isCjk(c) -> {
                    out += Word(i, i + 1, 1f)
                    i++
                }
                else -> {
                    var j = i
                    while (j < text.length && !text[j].isWhitespace() && !isCjk(text[j])) j++
                    out += Word(i, j, syllables(text.substring(i, j)))
                    i = j
                }
            }
        }
        return out
    }

    /** Bobot waktu tiap kata, kata terakhir diperpanjang. */
    private fun timeWeights(words: List<Word>): FloatArray =
        FloatArray(words.size) { k -> words[k].weight * (if (k == words.lastIndex) LAST_WORD_HOLD else 1f) }

    /**
     * Berapa lama (ms) sapuan satu baris (perkiraan, untuk sumber tanpa waktu per kata).
     *
     * Kecepatan dinyanyikan diturunkan dari jarak ke baris berikutnya: kalau barisnya padat (jarak per suku
     * kata pendek, misalnya "wo oh yao ya bongkar"), sapuan memakai hampir seluruh jarak itu dan kata-katanya
     * ikut cepat. Kalau barisnya jarang (balada, banyak jeda), sapuan hanya memakai sebagian jarak dan sisanya
     * jadi jeda. Dulu ada minimum 1,6 detik dan pengali 2,4x yang membuat baris cepat terisi terlalu lambat.
     */
    fun sweepMs(text: String, gapToNextMs: Long): Long {
        val syl = max(words(text).sumOf { it.weight.toDouble() }.toFloat(), 1f)
        val gap = max(gapToNextMs, 400L).toFloat()
        val cap = gap * 0.92f
        val perSyllable = gap / syl
        val sung = if (perSyllable <= DENSE_MS_PER_SYLLABLE) {
            cap
        } else {
            syl * (DENSE_MS_PER_SYLLABLE + (perSyllable - DENSE_MS_PER_SYLLABLE) * 0.35f)
        }
        return min(cap, sung).toLong().coerceAtLeast(300L)
    }

    /** Lama sapuan untuk baris yang punya waktu per kata asli: dari awal kata pertama sampai akhir kata terakhir. */
    fun wordSweepMs(line: com.jimz.jimztune.data.lyrics.LyricLine): Long {
        val last = line.words.lastOrNull() ?: return 300L
        return max(last.endMs - line.timeMs, 300L)
    }

    /**
     * Pemetaan dari kemajuan waktu baris (0..1) ke posisi tepi isian (piksel, sepanjang gabungan semua
     * baris visual teks). Dibuat sekali per tata letak teks.
     */
    class SweepMap(
        private val tStart: FloatArray,
        private val tEnd: FloatArray,
        private val xStart: FloatArray,
        private val xEnd: FloatArray,
        /** True = waktu kata asli dari sumber (isian linear sepanjang kata). False = perkiraan (isian rapat di awal kata). */
        private val exact: Boolean
    ) {
        fun xAt(p: Float): Float {
            val n = tStart.size
            if (n == 0) return 0f
            if (p <= 0f) return xStart[0]
            if (p >= 1f) return xEnd[n - 1]
            for (k in 0 until n) {
                if (p < tEnd[k]) {
                    if (p < tStart[k]) {
                        // Di jeda sebelum kata k: tepi bergerak lurus melewati spasi.
                        val prevEnd = if (k > 0) tEnd[k - 1] else 0f
                        val local = (p - prevEnd) / max(tStart[k] - prevEnd, 0.0001f)
                        val fromX = if (k > 0) xEnd[k - 1] else xStart[0]
                        return mix(fromX, xStart[k], local)
                    }
                    val local = (p - tStart[k]) / max(tEnd[k] - tStart[k], 0.0001f)
                    // Perkiraan: kata terisi di 80% awal slot waktunya lalu menunggu kata berikutnya, jadi terasa
                    // tegas per kata dan tidak meluncur. Waktu asli: linear mengikuti durasi kata sebenarnya.
                    val f = if (exact) local else (local / SNAP).coerceAtMost(1f)
                    return mix(xStart[k], xEnd[k], f)
                }
            }
            return xEnd[n - 1]
        }
    }

    /** Membangun [SweepMap] dari tata letak teks: posisi tiap kata dihitung dari kotak huruf pertama dan terakhirnya. */
    fun buildSweepMap(layout: TextLayoutResult, text: String): SweepMap? {
        val words = words(text)
        if (words.isEmpty() || layout.layoutInput.text.length < text.length) return null

        // Posisi awal tiap baris visual pada koordinat gabungan (baris 2 dianggap lanjutan baris 1).
        val lineCount = layout.lineCount
        val cum = FloatArray(lineCount)
        var acc = 0f
        for (i in 0 until lineCount) {
            cum[i] = acc
            acc += layout.getLineRight(i) - layout.getLineLeft(i)
        }

        val n = words.size
        val weights = timeWeights(words)
        val total = weights.sum() + WORD_GAP * (n - 1)

        val tStart = FloatArray(n)
        val tEnd = FloatArray(n)
        val xStart = FloatArray(n)
        val xEnd = FloatArray(n)
        var t = 0f
        for (k in 0 until n) {
            val w = words[k]
            tStart[k] = t / total
            t += weights[k]
            tEnd[k] = t / total
            t += WORD_GAP

            val first = layout.getBoundingBox(w.start)
            val last = layout.getBoundingBox(w.end - 1)
            val firstLine = layout.getLineForOffset(w.start)
            val lastLine = layout.getLineForOffset(w.end - 1)
            xStart[k] = cum[firstLine] + (min(first.left, first.right) - layout.getLineLeft(firstLine))
            xEnd[k] = cum[lastLine] + (max(last.left, last.right) - layout.getLineLeft(lastLine))
        }
        return SweepMap(tStart, tEnd, xStart, xEnd, exact = false)
    }

    /**
     * SweepMap dari waktu kata asli (mis. NetEase YRC). Kemajuan 0..1 dihitung terhadap [wordSweepMs],
     * jadi tiap kata terisi tepat di detik penyanyi mengucapkannya, secepat atau selambat apa pun.
     */
    fun buildSweepMapFromWords(
        layout: TextLayoutResult,
        text: String,
        words: List<com.jimz.jimztune.data.lyrics.LyricWord>,
        lineStartMs: Long
    ): SweepMap? {
        if (words.isEmpty() || layout.layoutInput.text.length < text.length) return null
        val usable = words.filter { it.charStart >= 0 && it.charEnd <= text.length && it.charEnd > it.charStart }
        if (usable.isEmpty()) return null

        val lineCount = layout.lineCount
        val cum = FloatArray(lineCount)
        var acc = 0f
        for (i in 0 until lineCount) {
            cum[i] = acc
            acc += layout.getLineRight(i) - layout.getLineLeft(i)
        }

        val total = max(usable.last().endMs - lineStartMs, 300L).toFloat()
        val n = usable.size
        val tStart = FloatArray(n)
        val tEnd = FloatArray(n)
        val xStart = FloatArray(n)
        val xEnd = FloatArray(n)
        var prevEnd = 0f
        for (k in 0 until n) {
            val w = usable[k]
            // Dijaga tidak mundur dan tidak tumpang tindih, supaya pemetaan selalu naik.
            val s = max(((w.startMs - lineStartMs) / total).coerceIn(0f, 1f), prevEnd)
            val e = max(((w.endMs - lineStartMs) / total).coerceIn(0f, 1f), s + 0.0005f)
            tStart[k] = s
            tEnd[k] = e
            prevEnd = e

            val first = layout.getBoundingBox(w.charStart)
            val last = layout.getBoundingBox(w.charEnd - 1)
            val firstLine = layout.getLineForOffset(w.charStart)
            val lastLine = layout.getLineForOffset(w.charEnd - 1)
            xStart[k] = cum[firstLine] + (min(first.left, first.right) - layout.getLineLeft(firstLine))
            xEnd[k] = cum[lastLine] + (max(last.left, last.right) - layout.getLineLeft(lastLine))
        }
        return SweepMap(tStart, tEnd, xStart, xEnd, exact = true)
    }
}
