package com.jimz.jimztune.data.lyrics

import com.atilika.kuromoji.ipadic.Token
import com.atilika.kuromoji.ipadic.Tokenizer

/**
 * Romanisasi lirik (huruf Latin).
 *
 * - Jepang: kuromoji membaca kanji dan kana per kata (bacaan kamus, jadi 今日 = kyō, bukan pinyin), lalu diubah ke
 *   Hepburn: partikel は/へ/を jadi wa/e/o, sokuon (っ) menggandakan konsonan, ー jadi tanda panjang, dan kata kerja
 *   serta akhirannya dirapatkan ("tabemashita"). Mode Jepang aktif kalau lagu punya kana; lagu kanji saja (Mandarin)
 *   tetap lewat ICU (pinyin).
 * - Bahasa lain (Korea, Kiril, Arab, Thai, dst): ICU bawaan Android.
 */
object Romanizer {
    private val icu by lazy {
        try {
            android.icu.text.Transliterator.getInstance("Any-Latin; NFD; [:Nonspacing Mark:] Remove; NFC")
        } catch (_: Throwable) {
            null
        }
    }

    /** Memuat kamus butuh waktu dan memori sekali saja; dipanggil dari thread latar. */
    private val kuromoji: Tokenizer? by lazy {
        try {
            Tokenizer()
        } catch (_: Throwable) {
            null
        }
    }

    /** Ada huruf non-Latin di teks ini? */
    fun needs(text: String): Boolean = text.any { it.code > 0x24F && it.isLetter() }

    private fun isKana(c: Char) = c.code in 0x3040..0x30FF && c != '・'
    private fun isHan(c: Char) = c.code in 0x4E00..0x9FFF || c.code in 0x3400..0x4DBF || c == '々'
    private fun hasKana(s: String) = s.any { isKana(it) }

    /** Hasil romanisasi per baris; null untuk baris yang sudah Latin atau gagal. */
    fun lines(texts: List<String>): List<String?> {
        val japanese = texts.any { hasKana(it) } && kuromoji != null
        return texts.map { line ->
            if (!needs(line)) return@map null
            val out = try {
                if (japanese && line.any { isKana(it) || isHan(it) }) japanese(line) else icuLine(line)
            } catch (_: Throwable) {
                null
            }
            out?.trim()?.takeIf { it.isNotBlank() && it != line }
        }
    }

    private fun icuLine(line: String): String? = icu?.transliterate(line)

    // ------------------------------------------------------------ Jepang

    private fun japanese(line: String): String? {
        val tk = kuromoji ?: return icuLine(line)
        val sb = StringBuilder()
        var afterOpen = false
        for (t in tk.tokenize(line)) {
            val surface = t.surface
            if (surface.isBlank()) continue
            val kana = readingOf(t)
            val piece = if (kana != null) toRomaji(kana) else punct(surface)
            if (piece.isEmpty()) continue
            val isPunct = kana == null && piece.none { it.isLetterOrDigit() }
            val attach = kana != null && shouldAttach(t)
            if (sb.isNotEmpty() && sb.last() != ' ' && !isPunct && !attach && !afterOpen) sb.append(' ')
            sb.append(piece)
            afterOpen = isPunct && piece == "("
        }
        val r = sb.toString().replace(Regex(" +"), " ").trim()
        return r.replaceFirstChar { it.uppercase() }
    }

    private fun isKanaOnly(s: String) = s.isNotEmpty() && s.all { isKana(it) }

    /** Bacaan katakana token (pelafalan: は→ワ, へ→エ, を→オ), atau null kalau bukan kata Jepang. */
    private fun readingOf(t: Token): String? {
        val s = t.surface
        val pr = t.pronunciation
        val rd = t.reading
        if (t.isKnown && pr != null && isKanaOnly(pr)) return pr
        if (isKanaOnly(s)) return toKatakana(s)
        if (t.isKnown && rd != null && isKanaOnly(rd)) return rd
        return null
    }

    /** Kata bantu menempel ke kata sebelumnya: 食べ+ました+た → tabemashita. */
    private fun shouldAttach(t: Token): Boolean {
        val p1 = t.partOfSpeechLevel1
        val p2 = t.partOfSpeechLevel2
        return p1 == "助動詞" || p1 == "接尾" ||
            (p1 == "動詞" && p2 == "非自立") ||
            (p1 == "助詞" && p2 == "接続助詞" && (t.surface == "て" || t.surface == "で"))
    }

    private fun punct(s: String): String {
        val sb = StringBuilder()
        for (c in s) {
            sb.append(
                when (c) {
                    '、', '，' -> ","
                    '。', '．' -> "."
                    '！' -> "!"
                    '？' -> "?"
                    '「', '『', '（' -> "("
                    '」', '』', '）' -> ")"
                    '〜', '～' -> "~"
                    '・' -> " "
                    '　' -> " "
                    else -> if (isHan(c)) (icu?.transliterate(c.toString()) ?: "") else c.toString()
                }
            )
        }
        return sb.toString()
    }

    private fun toKatakana(s: String): String = buildString {
        for (c in s) append(if (c.code in 0x3041..0x3096) (c.code + 0x60).toChar() else c)
    }

    // ------------------------------------------------------------ Hepburn

    private val base: Map<String, String> = buildMap {
        val rows = listOf(
            "アaイiウuエeオo", "カkaキkiクkuケkeコko", "ガgaギgiグguゲgeゴgo", "サsaシshiスsuセseソso",
            "ザzaジjiズzuゼzeゾzo", "タtaチchiツtsuテteトto", "ダdaヂjiヅzuデdeドdo", "ナnaニniヌnuネneノno",
            "ハhaヒhiフfuヘheホho", "バbaビbiブbuベbeボbo", "パpaピpiプpuペpeポpo", "マmaミmiムmuメmeモmo",
            "ヤyaユyuヨyo", "ラraリriルruレreロro", "ワwaヲoンn", "ヴvu"
        )
        for (row in rows) {
            var i = 0
            while (i < row.length) {
                val k = row[i]
                var j = i + 1
                while (j < row.length && row[j].code < 0x3000) j++
                put(k.toString(), row.substring(i + 1, j))
                i = j
            }
        }
        put("ァ", "a"); put("ィ", "i"); put("ゥ", "u"); put("ェ", "e"); put("ォ", "o"); put("ヮ", "wa")
        put("ャ", "ya"); put("ュ", "yu"); put("ョ", "yo")
        // Gabungan dengan ャュョ
        val y = mapOf(
            'キ' to "ky", 'ギ' to "gy", 'ニ' to "ny", 'ヒ' to "hy", 'ビ' to "by", 'ピ' to "py", 'ミ' to "my", 'リ' to "ry",
            'シ' to "sh", 'ジ' to "j", 'チ' to "ch", 'ヂ' to "j"
        )
        for ((k, stem) in y) {
            put("${k}ャ", stem + "a"); put("${k}ュ", stem + "u"); put("${k}ョ", stem + "o")
        }
        // Bunyi serapan
        val ext = mapOf(
            "シェ" to "she", "ジェ" to "je", "チェ" to "che", "ファ" to "fa", "フィ" to "fi", "フェ" to "fe", "フォ" to "fo",
            "ウィ" to "wi", "ウェ" to "we", "ウォ" to "wo", "ティ" to "ti", "ディ" to "di", "トゥ" to "tu", "ドゥ" to "du",
            "テュ" to "tyu", "デュ" to "dyu", "ツァ" to "tsa", "ツィ" to "tsi", "ツェ" to "tse", "ツォ" to "tso",
            "ヴァ" to "va", "ヴィ" to "vi", "ヴェ" to "ve", "ヴォ" to "vo", "イェ" to "ye", "クァ" to "kwa", "クィ" to "kwi",
            "クェ" to "kwe", "クォ" to "kwo", "グァ" to "gwa", "フュ" to "fyu"
        )
        putAll(ext)
    }

    private val macron = mapOf('a' to 'ā', 'i' to 'ī', 'u' to 'ū', 'e' to 'ē', 'o' to 'ō')

    private fun toRomaji(kata: String): String {
        val sb = StringBuilder()
        var i = 0
        var geminate = false
        while (i < kata.length) {
            val c = kata[i]
            when {
                c == 'ッ' -> {
                    geminate = true
                    i++
                    continue
                }
                c == 'ー' -> {
                    if (sb.isNotEmpty()) macron[sb.last()]?.let { sb.setCharAt(sb.length - 1, it) }
                    i++
                    continue
                }
                else -> {
                    var r: String? = null
                    var len = 1
                    if (i + 1 < kata.length) {
                        val two = kata.substring(i, i + 2)
                        base[two]?.let { r = it; len = 2 }
                    }
                    if (r == null) r = base[c.toString()]
                    if (r == null) {
                        sb.append(c)
                        i++
                        geminate = false
                        continue
                    }
                    var out = r!!
                    if (geminate) {
                        out = if (out.startsWith("ch")) "t$out" else out[0] + out
                        geminate = false
                    }
                    if (out == "n" && i + len < kata.length) {
                        val nx = base[kata[i + len].toString()]
                        if (nx != null && (nx[0] in "aiueoy")) out = "n'"
                    }
                    sb.append(out)
                    i += len
                }
            }
        }
        return sb.toString()
    }
}
