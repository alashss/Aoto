package com.jimz.jimztune.data.lyrics

import android.content.Context
import com.jimz.jimztune.data.search.Http
import kotlinx.coroutines.CancellationException
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.async
import kotlinx.coroutines.awaitAll
import kotlinx.coroutines.coroutineScope
import kotlinx.coroutines.sync.Semaphore
import kotlinx.coroutines.sync.withPermit
import kotlinx.coroutines.withContext
import org.json.JSONArray
import org.json.JSONObject
import java.io.File
import java.net.URLEncoder
import java.util.Locale

/** Keadaan terjemahan lirik untuk lagu yang sedang tampil. */
sealed interface TranslationState {
    /** Dimatikan di pengaturan, atau halaman lirik belum dibuka. */
    data object Off : TranslationState
    data object Loading : TranslationState

    /** Lagu sudah berbahasa Indonesia (atau bahasa Inggris dan opsinya dimatikan): tidak ada yang perlu ditampilkan. */
    data object NotNeeded : TranslationState

    /** Layanan terjemahan tidak bisa dihubungi. Lirik asli tetap tampil normal. */
    data object Failed : TranslationState

    /** [lines] sejajar dengan Lyrics.lines; null = baris itu tidak punya terjemahan (kosong / sama dengan aslinya). */
    data class Ready(val language: String, val lines: List<String?>) : TranslationState {
        /** Nama bahasa asal dalam bahasa Indonesia, mis. "Jepang" atau "Jawa & Inggris" untuk lagu campuran. */
        val languageName: String
            get() = language.split('+').filter { it.isNotBlank() }.joinToString(" & ") { code ->
                try {
                    // Google memakai kode lama "jw" untuk bahasa Jawa; Java/Android mengenalnya sebagai "jv".
                    val tag = if (code == "jw") "jv" else code
                    Locale.forLanguageTag(tag).getDisplayLanguage(Locale("id")).ifBlank { code }
                } catch (_: Exception) {
                    code
                }
            }.ifBlank { language }
    }
}

/** Mendeteksi apakah lirik perlu diterjemahkan, tanpa menghubungi internet. */
internal object LyricLanguage {
    enum class Guess { INDONESIAN, ENGLISH, FOREIGN }

    private val indonesian = setOf(
        "yang", "dan", "di", "ke", "dari", "ini", "itu", "aku", "kamu", "dia", "kita", "kami", "mereka", "tidak", "bukan",
        "ada", "untuk", "dengan", "pada", "akan", "sudah", "belum", "masih", "hanya", "saja", "juga", "cinta", "hati",
        "rindu", "sayang", "jangan", "bisa", "mau", "tak", "tahu", "apa", "kau", "lagi", "saat", "bila", "jika", "karena",
        "tapi", "atau", "seperti", "dalam", "pergi", "datang", "malam", "hari", "kini", "ku", "mu", "nya", "dunia"
    )
    private val english = setOf(
        "the", "you", "and", "i", "to", "a", "of", "in", "is", "it", "my", "me", "your", "we", "be", "that", "for", "on",
        "with", "this", "are", "was", "but", "not", "love", "just", "oh", "baby", "like", "all", "know", "can", "don't",
        "i'm", "it's", "got", "get", "when", "what", "so", "no", "yeah", "she", "he", "they", "from", "have", "do",
        "will", "always", "forever", "heart", "never", "want", "need", "girl", "boy", "tonight", "feel", "see", "say",
        "down", "up", "away", "time", "life", "only", "ever", "if", "how", "why", "who", "now", "here", "there", "give",
        "take", "come", "go", "make", "one", "together", "mine", "us", "our", "her", "his", "them", "been", "were",
        "would", "could", "should", "let", "till", "cause", "gonna", "wanna", "ain't", "can't", "won't", "i'll", "you're",
        "i've", "let's", "dream", "dreams", "night", "day", "world", "everything", "nothing", "without", "again", "stay",
        "tell", "think", "cry", "hold", "run", "fall", "fly", "light", "face", "eyes", "hand", "hands", "home"
    )

    /**
     * Kata khas bahasa Jawa (yang TIDAK dipakai dalam bahasa Indonesia baku/gaul), untuk mengenali baris berbahasa Jawa.
     * Kata yang sama persis dengan bahasa Indonesia ("aku", "tak", "ati-ati") sengaja tidak dimasukkan.
     */
    private val javanese = setOf(
        "ora", "ra", "wis", "wes", "uwis", "kowe", "koe", "sliramu", "nggo", "kanggo", "sing", "iki", "kuwi", "kui",
        "opo", "piye", "mung", "wae", "tenan", "dadi", "nang", "ning", "neng", "karo", "ojo", "saiki", "mbok",
        "mulih", "lungo", "tresno", "bojo", "atiku", "uripku", "rasane", "wong", "sampeyan", "mboten", "nggih",
        "sampun", "dereng", "kulo", "kula", "sinten", "pripun", "ndi", "endi", "pancen", "nek", "yen", "mergo", "merga",
        "amarga", "ambek", "isih", "durung", "apik", "lara", "loro", "seneng", "bareng", "kabeh", "sopo", "ono",
        "kudu", "ngerti", "ngertio", "nyawang", "ngopo", "ngene", "ngono", "ngomong", "mikir", "ndelok", "nelongso",
        "kelingan", "ilang", "gelem", "wedi", "ndang", "alon", "jenengmu", "lungguh", "gawe", "gawa", "nyang", "nggone",
        "duwe", "ndue", "bakal", "arep", "iso", "isoh", "lali", "dolan", "mlaku", "turu", "tangi", "enteni", "tekamu",
        "kangene", "dewean", "mesakke", "koyo", "kados", "mugi", "ndherek", "matur", "nuwun", "sugeng", "dalu", "enjing",
        "wengi", "esuk", "surup"
    )

    enum class Line { INDONESIAN, ENGLISH, JAVANESE, OTHER, NONE }

    /** [idHits] = jumlah kata Indonesia di baris itu (dipakai untuk memastikan baris benar-benar Indonesia). */
    class LineGuess(val lang: Line, val idHits: Int)

    /** Menebak bahasa SATU baris, tanpa internet. Dipakai supaya bagian Inggris/Jawa di lagu Indonesia ikut diterjemahkan. */
    fun classifyLine(text: String): LineGuess {
        var letters = 0
        var nonLatin = 0
        val words = ArrayList<String>()
        val current = StringBuilder()
        var i = 0
        while (i < text.length) {
            val cp = text.codePointAt(i)
            i += Character.charCount(cp)
            if (Character.isLetter(cp)) {
                letters++
                if (Character.UnicodeScript.of(cp) != Character.UnicodeScript.LATIN) nonLatin++
                current.appendCodePoint(Character.toLowerCase(cp))
            } else if (cp == '\''.code || cp == '’'.code) {
                current.append('\'')
            } else if (current.isNotEmpty()) {
                words += current.toString()
                current.clear()
            }
        }
        if (current.isNotEmpty()) words += current.toString()
        if (letters == 0 || words.isEmpty()) return LineGuess(Line.NONE, 0)
        if (nonLatin.toFloat() / letters >= 0.30f) return LineGuess(Line.OTHER, 0)

        val id = words.count { it in indonesian }
        val en = words.count { it in english }
        val jv = words.count { it in javanese }
        return when {
            jv >= 1 && jv >= id && jv >= en -> LineGuess(Line.JAVANESE, id)
            en >= 2 && en > id && en > jv -> LineGuess(Line.ENGLISH, id)
            id >= 1 && id >= en -> LineGuess(Line.INDONESIAN, id)
            else -> LineGuess(Line.OTHER, id)
        }
    }

    fun guess(lines: List<String>): Guess {
        var letters = 0
        var nonLatin = 0
        val words = ArrayList<String>()
        val current = StringBuilder()
        for (line in lines) {
            var i = 0
            while (i < line.length) {
                val cp = line.codePointAt(i)
                i += Character.charCount(cp)
                if (Character.isLetter(cp)) {
                    letters++
                    val script = Character.UnicodeScript.of(cp)
                    if (script != Character.UnicodeScript.LATIN) nonLatin++
                    current.appendCodePoint(Character.toLowerCase(cp))
                } else if (cp == '\''.code || cp == '’'.code) {
                    current.append('\'')
                } else if (current.isNotEmpty()) {
                    words += current.toString()
                    current.clear()
                }
            }
            if (current.isNotEmpty()) {
                words += current.toString()
                current.clear()
            }
        }
        if (letters == 0) return Guess.INDONESIAN // tidak ada teks: tidak ada yang perlu diterjemahkan
        if (nonLatin.toFloat() / letters >= 0.30f) return Guess.FOREIGN

        val id = words.count { it in indonesian }
        val en = words.count { it in english }
        return when {
            id >= 3 && id >= en -> Guess.INDONESIAN
            en >= 3 && en > id -> Guess.ENGLISH
            else -> Guess.FOREIGN // Spanyol, Prancis, Jawa, dst.; kode bahasa dari server memastikan lagi
        }
    }
}

/**
 * Menerjemahkan lirik ke bahasa Indonesia dengan satu permintaan per lagu (dibagi beberapa kalau liriknya sangat panjang),
 * memakai layanan terjemahan Google yang tidak resmi (tanpa API key). Hasilnya disimpan di perangkat, jadi membuka
 * lagu yang sama lagi langsung muncul tanpa internet. Kalau layanannya gagal, lirik asli tetap tampil seperti biasa.
 */
class LyricTranslator(context: Context) {
    private val dir = File(context.applicationContext.filesDir, "lyrics_tr_v3").apply { mkdirs() }

    init {
        // Cache lama menganggap lagu Jawa / lagu Indonesia yang diselipi bahasa Inggris "tidak perlu diterjemahkan".
        File(context.applicationContext.filesDir, "lyrics_tr_v2").takeIf { it.exists() }?.deleteRecursively()
    }

    /**
     * Rencana terjemahan. [sourceByLine]: indeks baris -> kode bahasa asal ("en", "jw", atau "auto").
     * [wholeSongAuto] = seluruh lagu dikirim dengan deteksi otomatis (lagu satu bahasa asing, seperti dulu).
     */
    private class Plan(val sourceByLine: Map<Int, String>, val wholeSongAuto: Boolean)

    /**
     * Menentukan baris mana yang perlu diterjemahkan:
     *  - lagu satu bahasa asing (Jepang, Korea, Spanyol, ...) atau lagu Inggris: semua baris, deteksi otomatis;
     *  - lagu Indonesia yang diselipi bagian Inggris, atau lagu Jawa: per baris. Baris Inggris dikirim sebagai
     *    Inggris, baris Jawa sebagai Jawa, baris Indonesia dilewati. (Dulu lagu begini dianggap "sudah Indonesia"
     *    karena banyak kata yang sama, jadi bagian Jawa/Inggris-nya tidak pernah diterjemahkan.)
     * Mengembalikan null kalau tidak ada yang perlu diterjemahkan.
     */
    private fun plan(texts: List<String>, includeEnglish: Boolean): Plan? {
        val guess = LyricLanguage.guess(texts)
        val lines = texts.map { if (it.isEmpty()) null else LyricLanguage.classifyLine(it) }
        val content = lines.count { it != null && it.lang != LyricLanguage.Line.NONE }
        val jv = lines.count { it?.lang == LyricLanguage.Line.JAVANESE }
        val javanese = content > 0 && jv >= 2 && jv * 100 >= content * 15

        if (!javanese) {
            when (guess) {
                LyricLanguage.Guess.ENGLISH ->
                    return if (includeEnglish) Plan(texts.indices.filter { texts[it].isNotEmpty() }.associateWith { "auto" }, true) else null
                LyricLanguage.Guess.FOREIGN ->
                    return Plan(texts.indices.filter { texts[it].isNotEmpty() }.associateWith { "auto" }, true)
                LyricLanguage.Guess.INDONESIAN -> Unit
            }
        }

        val map = LinkedHashMap<Int, String>()
        lines.forEachIndexed { i, info ->
            if (info == null) return@forEachIndexed
            val source = when (info.lang) {
                LyricLanguage.Line.JAVANESE -> "jw"
                LyricLanguage.Line.ENGLISH -> "en" // bagian Inggris di lagu Indonesia/Jawa selalu diterjemahkan
                // Di lagu Jawa, baris tanpa kata khas pun biasanya Jawa; baris yang jelas Indonesia dilewati.
                LyricLanguage.Line.INDONESIAN -> if (javanese && info.idHits < 2) "jw" else null
                LyricLanguage.Line.OTHER -> if (javanese) "jw" else null
                LyricLanguage.Line.NONE -> null
            }
            if (source != null) map[i] = source
        }
        return if (map.isEmpty()) null else Plan(map, false)
    }

    suspend fun translate(songId: String, lyrics: Lyrics, includeEnglish: Boolean): TranslationState {
        val texts = lyrics.lines.map { it.text.trim() }
        if (texts.count { it.isNotEmpty() } < 2) return TranslationState.NotNeeded

        val plan = plan(texts, includeEnglish) ?: return TranslationState.NotNeeded

        val file = fileFor(songId, texts)
        readCache(file)?.let { return it }

        val result = try {
            fetch(texts, plan)
        } catch (e: CancellationException) {
            throw e
        } catch (_: Exception) {
            return TranslationState.Failed
        }
        if (plan.wholeSongAuto) {
            if (result.language.startsWith("id") || result.language == "ms") {
                writeCache(file, result.language, null)
                return TranslationState.NotNeeded
            }
            if (result.language == "en" && !includeEnglish) return TranslationState.NotNeeded
            if (result.lines.none { it != null }) return TranslationState.Failed
        } else if (result.lines.none { it != null }) {
            // Baris Jawa/Inggris yang ditebak ternyata sama dengan hasilnya: memang tidak ada yang perlu ditampilkan.
            writeCache(file, result.language, null)
            return TranslationState.NotNeeded
        }

        writeCache(file, result.language, result.lines)
        return TranslationState.Ready(result.language, result.lines)
    }

    /** [language]: kode bahasa asal; lagu campuran digabung dengan "+", mis. "jw+en". */
    private class Fetched(val language: String, val lines: List<String?>)

    private suspend fun fetch(texts: List<String>, plan: Plan): Fetched {
        val out = arrayOfNulls<String>(texts.size)
        val languages = LinkedHashSet<String>()

        // Satu kelompok per bahasa asal, supaya tiap baris diterjemahkan dengan bahasa yang benar.
        val groups = plan.sourceByLine.entries.groupBy({ it.value }, { it.key })
        for ((source, indices) in groups) {
            val sendable = indices.map { texts[it].replace('\n', ' ') }
            val translated = arrayOfNulls<String>(sendable.size)
            var detected = ""
            for (chunk in chunks(sendable)) {
                val (lang, lines) = requestChunk(sendable.subList(chunk.first, chunk.last + 1), source)
                if (detected.isEmpty()) detected = lang
                lines.forEachIndexed { k, t -> translated[chunk.first + k] = t }
            }
            indices.forEachIndexed { k, original ->
                val t = translated[k]?.trim().orEmpty()
                // "oh oh oh", "la la la" dan sejenisnya: sama dengan aslinya, tidak perlu diulang.
                if (t.isNotEmpty() && !t.equals(texts[original], ignoreCase = true)) out[original] = t
            }
            val code = if (source == "auto") detected.ifEmpty { "und" } else source
            // Hanya cantumkan bahasa yang memang menghasilkan terjemahan, supaya label tidak menyesatkan.
            if (source == "auto" || indices.any { out[it] != null }) languages += code
        }
        return Fetched(languages.joinToString("+").ifEmpty { "und" }, out.toList())
    }

    /** Membagi baris menjadi kelompok (rentang indeks) yang masing-masing tidak lebih dari ±3.800 karakter. */
    private fun chunks(lines: List<String>): List<IntRange> {
        val result = ArrayList<IntRange>()
        var start = 0
        var size = 0
        lines.forEachIndexed { i, l ->
            if (size + l.length + 1 > MAX_CHARS && i > start) {
                result += start until i
                start = i
                size = 0
            }
            size += l.length + 1
        }
        if (start < lines.size) result += start until lines.size
        return result
    }

    /** Satu permintaan untuk satu kelompok baris. Mengembalikan (kode bahasa asal, terjemahan per baris). */
    private suspend fun requestChunk(lines: List<String>, source: String): Pair<String, List<String?>> {
        val endpoint = endpointFor(source)
        val joined = lines.joinToString("\n")
        val json = JSONArray(Http.postForm(endpoint, "q=" + URLEncoder.encode(joined, "UTF-8")))
        val segments = json.getJSONArray(0)
        val language = json.optString(2, "").takeIf { it.isNotBlank() && it != "null" } ?: ""

        val originals = ArrayList<String>()
        val translations = ArrayList<String>()
        for (i in 0 until segments.length()) {
            val seg = segments.optJSONArray(i) ?: continue
            if (seg.isNull(0)) continue
            translations += seg.optString(0)
            originals += seg.optString(1)
        }

        // Cara 1 (paling umum): Google sering menggabung beberapa baris dalam SATU potongan yang berisi "\n".
        // Gabungkan semuanya lalu pecah per baris; kalau jumlahnya sama dengan yang dikirim, petakan langsung.
        val split = translations.joinToString("").trim('\n', '\r').split('\n').map { it.trim() }
        if (split.size == lines.size) return language to split.map { it.ifBlank { null } }

        // Cara 2: tiap potongan tepat satu baris (tanpa "\n" di dalamnya).
        if (translations.size == lines.size && translations.none { it.contains('\n') }) {
            return language to translations.map { it.trim().ifBlank { null } }
        }

        // Cara 2b: petakan lewat posisi di teks asli; potongan yang melintasi beberapa baris dipecah di "\n".
        mapByOffsets(lines, joined, originals, translations)?.let { return language to it }

        // Cara 3 (jarang): terjemahkan baris demi baris, 4 sekaligus.
        if (lines.size > MAX_SINGLE_LINES) throw IllegalStateException("Hasil terjemahan tidak sejajar")
        val gate = Semaphore(4)
        val each = coroutineScope {
            lines.map { line ->
                async(Dispatchers.IO) {
                    gate.withPermit {
                        val one = JSONArray(Http.postForm(endpoint, "q=" + URLEncoder.encode(line, "UTF-8")))
                        val segs = one.getJSONArray(0)
                        (0 until segs.length()).mapNotNull { segs.optJSONArray(it)?.optString(0) }.joinToString("").trim()
                    }
                }
            }.awaitAll()
        }
        return language to each.map { it.ifBlank { null } }
    }

    /** Memetakan potongan terjemahan ke baris lewat posisinya di teks asli. null kalau tidak bisa dipetakan dengan yakin. */
    private fun mapByOffsets(
        lines: List<String>,
        joined: String,
        originals: List<String>,
        translations: List<String>
    ): List<String?>? {
        if (originals.sumOf { it.length } != joined.length) return null
        val starts = IntArray(lines.size)
        var pos = 0
        lines.forEachIndexed { i, l ->
            starts[i] = pos
            pos += l.length + 1
        }
        fun lineAt(index: Int) = starts.indexOfLast { it <= index }.coerceIn(0, lines.lastIndex)

        val buckets = Array(lines.size) { StringBuilder() }
        var offset = 0
        for (k in originals.indices) {
            val orig = originals[k]
            val first = orig.indexOfFirst { it != '\n' }
            val last = orig.indexOfLast { it != '\n' }
            if (first >= 0) {
                val a = lineAt(offset + first)
                val b = lineAt(offset + last)
                val pieces = translations[k].trim('\n', '\r').split('\n').map { it.trim() }
                when {
                    a == b -> {
                        val text = pieces.joinToString(" ").trim()
                        if (text.isNotEmpty()) {
                            if (buckets[a].isNotEmpty()) buckets[a].append(' ')
                            buckets[a].append(text)
                        }
                    }
                    pieces.size == b - a + 1 -> pieces.forEachIndexed { n, t ->
                        if (t.isNotEmpty()) {
                            if (buckets[a + n].isNotEmpty()) buckets[a + n].append(' ')
                            buckets[a + n].append(t)
                        }
                    }
                    else -> return null
                }
            }
            offset += orig.length
        }
        return buckets.map { it.toString().ifBlank { null } }
    }

    // ------------------------------------------------------------------ simpanan

    private fun fileFor(songId: String, texts: List<String>): File {
        val safe = songId.replace(Regex("[^A-Za-z0-9_-]"), "_")
        return File(dir, safe + "_" + texts.joinToString("\n").hashCode().toUInt().toString(16) + ".json")
    }

    private suspend fun readCache(file: File): TranslationState? = withContext(Dispatchers.IO) {
        if (!file.exists()) return@withContext null
        try {
            val root = JSONObject(file.readText())
            if (root.optBoolean("none")) return@withContext TranslationState.NotNeeded
            val arr = root.getJSONArray("lines")
            TranslationState.Ready(
                root.optString("lang", "und"),
                (0 until arr.length()).map { if (arr.isNull(it)) null else arr.getString(it) }
            )
        } catch (_: Exception) {
            null
        }
    }

    private suspend fun writeCache(file: File, language: String, lines: List<String?>?) = withContext(Dispatchers.IO) {
        try {
            val root = JSONObject().put("lang", language)
            if (lines == null) root.put("none", true)
            else root.put("lines", JSONArray().also { a -> lines.forEach { a.put(it ?: JSONObject.NULL) } })
            file.writeText(root.toString())
        } catch (_: Exception) {
        }
    }

    private companion object {
        /** [source]: "auto", "en", "jw" (Jawa), dst. Terjemahannya selalu ke bahasa Indonesia. */
        fun endpointFor(source: String) =
            "https://translate.googleapis.com/translate_a/single?client=gtx&sl=$source&tl=id&dt=t"
        const val MAX_CHARS = 3_800
        const val MAX_SINGLE_LINES = 120
    }
}
