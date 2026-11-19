package com.jimz.jimztune.data.lyrics

import android.util.Base64
import org.w3c.dom.Element
import org.w3c.dom.Node
import org.xml.sax.InputSource
import java.io.ByteArrayOutputStream
import java.io.StringReader
import java.util.zip.Inflater
import javax.xml.parsers.DocumentBuilderFactory
import kotlin.math.roundToLong

/** Membuka format KRC milik KuGou: buang 4 byte awal, XOR dengan kunci tetap, lalu dekompresi zlib. */
object KrcDecoder {
    private val key = intArrayOf(0x40, 0x47, 0x61, 0x77, 0x5e, 0x32, 0x74, 0x47, 0x51, 0x36, 0x31, 0x2d, 0xce, 0xd2, 0x6e, 0x69)

    fun decode(base64: String): String? {
        val raw = Base64.decode(base64, Base64.DEFAULT)
        if (raw.size <= 4) return null
        val data = ByteArray(raw.size - 4) { i -> (raw[i + 4].toInt() xor key[i % key.size]).toByte() }
        val inflater = Inflater()
        return try {
            inflater.setInput(data)
            val out = ByteArrayOutputStream()
            val buf = ByteArray(4096)
            while (!inflater.finished()) {
                val n = inflater.inflate(buf)
                if (n == 0 && (inflater.needsInput() || inflater.needsDictionary())) break
                out.write(buf, 0, n)
            }
            out.toString("UTF-8").removePrefix("\uFEFF")
        } catch (_: Exception) {
            null
        } finally {
            inflater.end()
        }
    }
}

/**
 * Lirik per kata KuGou (KRC): [mulaiBaris,durasiBaris]<offset,durasi,0>kata<offset,durasi,0>kata ...
 * Offset kata dihitung dari awal baris (bukan dari awal lagu).
 */
object KrcParser {
    private val lineTag = Regex("^\\[(\\d+),(\\d+)](.*)$")
    private val wordTag = Regex("<(\\d+),(\\d+),-?\\d+>")

    fun parse(raw: String, source: String): Lyrics? {
        val out = mutableListOf<LyricLine>()
        raw.lineSequence().forEach { rawLine ->
            val m = lineTag.find(rawLine.trim()) ?: return@forEach
            val lineStart = m.groupValues[1].toLong()
            val body = m.groupValues[3]
            val tags = wordTag.findAll(body).toList()
            if (tags.isEmpty()) return@forEach

            val sb = StringBuilder()
            val spans = ArrayList<LongArray>() // [mulai, akhir, charStart, charEnd]
            tags.forEachIndexed { i, t ->
                val start = lineStart + t.groupValues[1].toLong()
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
            val words = spans.map { LyricWord(it[0], it[1], (it[2] - shift).toInt(), (it[3] - shift).toInt()) }
            out += LyricLine(words.first().startMs, text, words)
        }
        if (out.size < 2) return null
        return Lyrics(out.sortedBy { it.timeMs }, synced = true, source = source)
    }
}

/**
 * TTML (format Apple Music, dipakai BetterLyrics): <p begin end> berisi <span begin end> per kata/suku kata.
 * Suara latar (x-bg) digabung ke baris yang sama; terjemahan/transliterasi dilewati.
 */
object TtmlParser {
    private val spaces = Regex("\\s+")

    fun parseTime(raw: String?): Long? {
        val t = raw?.trim()?.removeSuffix("s")?.takeIf { it.isNotEmpty() } ?: return null
        return try {
            var sec = 0.0
            t.split(":").forEach { sec = sec * 60 + it.toDouble() }
            (sec * 1000).roundToLong()
        } catch (_: NumberFormatException) {
            null
        }
    }

    fun parse(xml: String, source: String): Lyrics? {
        if (xml.isBlank()) return null
        val doc = try {
            DocumentBuilderFactory.newInstance().newDocumentBuilder().parse(InputSource(StringReader(xml)))
        } catch (_: Exception) {
            return null
        }
        val paragraphs = doc.getElementsByTagName("p")
        val out = mutableListOf<LyricLine>()
        for (i in 0 until paragraphs.length) {
            val p = paragraphs.item(i) as? Element ?: continue
            val sb = StringBuilder()
            val words = ArrayList<LyricWord>()
            collect(p, sb, words)

            val full = sb.toString()
            val shift = full.length - full.trimStart().length
            val text = full.trim()
            if (text.isEmpty()) continue
            val fixed = words.map { LyricWord(it.startMs, it.endMs, it.charStart - shift, it.charEnd - shift) }
                .filter { it.charStart >= 0 && it.charEnd <= text.length && it.charEnd > it.charStart }

            val start = parseTime(p.getAttribute("begin")) ?: fixed.firstOrNull()?.startMs ?: continue
            out += LyricLine(start, text, fixed)
        }
        if (out.size < 2) return null
        return Lyrics(out.sortedBy { it.timeMs }, synced = true, source = source)
    }

    private fun collect(node: Node, sb: StringBuilder, words: MutableList<LyricWord>) {
        val kids = node.childNodes
        for (i in 0 until kids.length) {
            val n = kids.item(i)
            when (n.nodeType) {
                Node.TEXT_NODE -> {
                    val t = n.textContent.replace(spaces, " ")
                    if (t.isBlank()) {
                        if (sb.isNotEmpty() && !sb.endsWith(" ")) sb.append(' ')
                    } else {
                        sb.append(if (sb.isEmpty()) t.trimStart() else t)
                    }
                }
                Node.ELEMENT_NODE -> {
                    val el = n as Element
                    val role = el.getAttribute("ttm:role")
                    if (role == "x-translation" || role == "x-roman") continue
                    val begin = parseTime(el.getAttribute("begin"))
                    val end = parseTime(el.getAttribute("end"))
                    val isLeaf = el.tagName == "span" && el.getElementsByTagName("span").length == 0
                    if (isLeaf && begin != null && end != null) {
                        val raw = el.textContent.replace(spaces, " ")
                        val lead = raw.length - raw.trimStart().length
                        val trimmed = raw.trim()
                        if (trimmed.isNotEmpty()) {
                            val base = sb.length
                            sb.append(raw)
                            words += LyricWord(begin, maxOf(end, begin + 1), base + lead, base + lead + trimmed.length)
                        }
                    } else {
                        collect(el, sb, words)
                    }
                }
            }
        }
    }
}
