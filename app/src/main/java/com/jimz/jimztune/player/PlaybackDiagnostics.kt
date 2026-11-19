package com.jimz.jimztune.player

import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.setValue
import java.text.SimpleDateFormat
import java.util.Date
import java.util.Locale

/** Catatan singkat proses pencarian sumber audio, ditampilkan di pemutar dan di Setelan agar mudah dilaporkan. */
object PlaybackDiagnostics {
    var log by mutableStateOf<List<String>>(emptyList())
        private set

    /** True selama proses memuat lagu berlangsung (dimatikan saat lagu sudah berbunyi). */
    var active by mutableStateOf(false)
        private set

    private val clock = SimpleDateFormat("HH:mm:ss", Locale.US)

    @Synchronized
    fun add(message: String) {
        log = (log + "${clock.format(Date())}  $message").takeLast(60)
        active = true
    }

    fun finish() {
        active = false
    }

    /** Seluruh catatan (terbaru di bawah), untuk ditampilkan dan disalin dari Setelan. */
    fun dump(): String = log.joinToString("\n")

    fun recent(n: Int = 3): String = log.takeLast(n).joinToString("\n")
}
