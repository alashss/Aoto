package com.jimz.jimztune.util

import com.jimz.jimztune.AppGraph
import java.text.SimpleDateFormat
import java.util.Date
import java.util.Locale

/**
 * Pusat pesan error untuk layar.
 *
 * Aturan: user biasa hanya melihat kalimat umum yang ramah ([text] tanpa rincian teknis).
 * Akun owner melihat kalimat yang sama ditambah rincian di dalam kurung siku.
 * Rincian selalu dicatat di [log] supaya owner bisa membacanya di Setelan -> Log pemutar.
 */
object UserError {
    private val clock = SimpleDateFormat("HH:mm:ss", Locale.US)
    private val entries = ArrayDeque<String>()

    /** Mencatat rincian teknis. Tidak pernah ditampilkan ke user biasa. */
    @Synchronized
    fun log(tag: String, detail: String?) {
        if (detail.isNullOrBlank()) return
        entries.addLast("${clock.format(Date())}  [$tag] ${detail.take(300)}")
        while (entries.size > 80) entries.removeFirst()
    }

    /** Mencatat sekaligus mengembalikan teks layar yang sesuai dengan jenis akun. */
    fun report(tag: String, generic: String, detail: String?): String {
        log(tag, detail)
        return text(generic, detail)
    }

    fun report(tag: String, generic: String, t: Throwable): String =
        report(tag, generic, "${t.javaClass.simpleName}: ${t.message}")

    /** User biasa: [generic]. Owner: [generic] + rincian. */
    fun text(generic: String, detail: String?): String =
        if (isOwner() && !detail.isNullOrBlank()) "$generic\n[${detail.take(160)}]" else generic

    private fun isOwner(): Boolean = try {
        AppGraph.account.isOwner
    } catch (_: Throwable) {
        false
    }

    @Synchronized
    fun dump(): String = entries.joinToString("\n")
}
