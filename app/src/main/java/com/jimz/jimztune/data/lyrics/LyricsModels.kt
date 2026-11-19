package com.jimz.jimztune.data.lyrics

/** Satu kata (atau suku kata) dengan waktu nyanyinya, dan posisinya di dalam teks baris. */
data class LyricWord(val startMs: Long, val endMs: Long, val charStart: Int, val charEnd: Int)

/** [words] kosong = sumber hanya punya waktu per baris (LRC biasa); waktu per kata nanti diperkirakan. */
data class LyricLine(val timeMs: Long, val text: String, val words: List<LyricWord> = emptyList())

data class Lyrics(val lines: List<LyricLine>, val synced: Boolean, val source: String) {
    /** True kalau sumber memberi waktu tiap kata, jadi isian karaoke bisa persis mengikuti penyanyi. */
    val wordSynced: Boolean get() = lines.any { it.words.isNotEmpty() }
}

sealed interface LyricsState {
    data object Idle : LyricsState
    data object Loading : LyricsState

    /**
     * [optionCount] > 1 berarti ada beberapa sumber lirik yang cocok; user bisa berganti sumber kalau yang tampil
     * tidak pas dengan versi lagunya. [songId] dipakai untuk menyimpan geseran waktu per lagu.
     */
    data class Ready(
        val lyrics: Lyrics,
        val songId: String = "",
        val optionCount: Int = 1,
        val optionIndex: Int = 0,
        /** Keterangan tiap sumber (nama + jenis sinkron), untuk daftar pilihan sumber. */
        val optionInfos: List<String> = emptyList()
    ) : LyricsState

    data object NotFound : LyricsState

    /** Lirik sengaja tidak dicari (mis. video loop berjam-jam yang tidak mungkin cocok dengan lirik). */
    data class Unavailable(val reason: String) : LyricsState
}

/** Seberapa bisa dipercaya kecocokan sebuah hasil dengan lagu yang sedang diputar. */
enum class Trust {
    /** Dicocokkan lewat judul/artis/durasi, jadi harus lolos pemeriksaan kemiripan. */
    BY_METADATA,

    /** Dicari lewat id video YouTube yang sama persis. */
    EXACT_ID,

    /** Sumber mencocokkan sendiri di servernya dan tidak mengirim judul (mis. BetterLyrics). */
    BLIND
}

/** Satu hasil dari satu sumber, lengkap dengan data lagu aslinya supaya bisa diperiksa kecocokannya. */
data class LyricsCandidate(
    val lyrics: Lyrics,
    val title: String,
    val artist: String,
    val durationSec: Int,
    val trust: Trust = Trust.BY_METADATA
)

data class LyricsQuery(
    val videoId: String,
    val title: String,
    val artist: String,
    val durationSec: Int
)

/**
 * Format lirik per kata (YRC, dipakai NetEase): [mulaiBaris,durasiBaris](mulai,durasi,0)kata(mulai,durasi,0)kata...
 * Semua waktu dalam milidetik absolut dari awal lagu.
 */
object YrcParser {
    private val lineTag = Regex("^\\[(\\d+),(\\d+)\\](.*)$")
    private val wordTag = Regex("\\((\\d+),(\\d+),-?\\d+\\)")

    fun parse(raw: String, source: String): Lyrics? {
        val out = mutableListOf<LyricLine>()
        raw.lineSequence().forEach { rawLine ->
            val m = lineTag.find(rawLine.trim()) ?: return@forEach
            val body = m.groupValues[3]
            val tags = wordTag.findAll(body).toList()
            if (tags.isEmpty()) return@forEach

            val sb = StringBuilder()
            val spans = ArrayList<LongArray>() // [mulai, akhir, charStart, charEnd]
            tags.forEachIndexed { i, t ->
                val start = t.groupValues[1].toLong()
                val dur = t.groupValues[2].toLong()
                val textEnd = if (i + 1 < tags.size) tags[i + 1].range.first else body.length
                val piece = body.substring(t.range.last + 1, textEnd)
                val lead = piece.length - piece.trimStart().length
                val trimmed = piece.trim()
                val offset = sb.length
                sb.append(piece)
                if (trimmed.isNotEmpty()) {
                    spans += longArrayOf(start, start + dur, (offset + lead).toLong(), (offset + lead + trimmed.length).toLong())
                }
            }
            val full = sb.toString()
            val shift = full.length - full.trimStart().length
            val text = full.trim()
            if (text.isEmpty() || spans.isEmpty()) return@forEach

            val words = spans.map {
                LyricWord(it[0], it[1], (it[2] - shift).toInt(), (it[3] - shift).toInt())
            }
            out += LyricLine(words.first().startMs, text, words)
        }
        if (out.isEmpty()) return null
        return Lyrics(out.sortedBy { it.timeMs }, synced = true, source = source)
    }
}

object LrcParser {
    private val tag = Regex("\\[(\\d{1,3}):(\\d{2})(?:[.:](\\d{1,3}))?]")

    /** Mengurai format LRC bertimestamp. Mengembalikan null kalau tidak ada baris bertimestamp. */
    fun parse(raw: String, source: String): Lyrics? {
        val lines = mutableListOf<LyricLine>()
        raw.lineSequence().forEach { line ->
            val matches = tag.findAll(line).toList()
            if (matches.isEmpty()) return@forEach
            val text = line.substring(matches.last().range.last + 1).trim()
            matches.forEach { m ->
                val minutes = m.groupValues[1].toLong()
                val seconds = m.groupValues[2].toLong()
                val frac = m.groupValues[3]
                val ms = when (frac.length) {
                    0 -> 0L
                    1 -> frac.toLong() * 100
                    2 -> frac.toLong() * 10
                    else -> frac.take(3).toLong()
                }
                lines += LyricLine(minutes * 60_000 + seconds * 1000 + ms, text)
            }
        }
        if (lines.isEmpty()) return null
        return Lyrics(lines.sortedBy { it.timeMs }, synced = true, source = source)
    }

    /** Lirik biasa tanpa timestamp. */
    fun plain(raw: String, source: String): Lyrics? {
        val lines = raw.replace("\r", "").lines().map { it.trim() }
        if (lines.all { it.isEmpty() }) return null
        return Lyrics(lines.map { LyricLine(-1L, it) }, synced = false, source = source)
    }
}

private val noiseWords =
    "official|video|audio|lyric|lyrics|lirik|mv|m/v|hd|hq|4k|visuali[sz]er|remaster(?:ed)?|music video|clip officiel|full|performance video"

private val noiseInBrackets = Regex(
    "\\s*[\\(\\[\\{][^\\)\\]\\}]*\\b($noiseWords)\\b[^\\)\\]\\}]*[\\)\\]\\}]",
    RegexOption.IGNORE_CASE
)
private val trailingNoise = Regex("\\s*[-–|]\\s*(official|lyrics?|lirik|audio|video).*$", RegexOption.IGNORE_CASE)
private val trailingChannel = Regex("\\s*\\|\\s*[^|]+$")

fun cleanTitle(raw: String): String =
    raw.replace(noiseInBrackets, "").replace(trailingNoise, "").replace(trailingChannel, "")
        .replace(Regex("\\s{2,}"), " ").trim()

fun cleanArtist(raw: String): String =
    raw.substringBefore(" • ")
        .replace(Regex("\\s*-\\s*Topic$", RegexOption.IGNORE_CASE), "")
        .replace(Regex("VEVO$", RegexOption.IGNORE_CASE), "")
        .trim()
