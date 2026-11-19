package com.jimz.jimztune.player

import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.setValue
import androidx.media3.common.Player
import com.jimz.jimztune.player.fx.VolumeMixer
import kotlinx.coroutines.CoroutineScope
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.Job
import kotlinx.coroutines.SupervisorJob
import kotlinx.coroutines.delay
import kotlinx.coroutines.launch

/**
 * Timer tidur. Hidup di tingkat aplikasi (bukan di layar pemutar), jadi tetap jalan walau aplikasi ditutup
 * selama layanan pemutar masih hidup. Pemutar dipasang oleh PlaybackService lewat [attach].
 *
 * Dua mode:
 *  - hitung mundur menit (15/30/45/60/...): musik mengecil pelan di 20 detik terakhir lalu berhenti.
 *  - "sampai lagu ini selesai": berhenti begitu lagu yang sedang diputar habis.
 */
object SleepTimer {
    private val scope = CoroutineScope(SupervisorJob() + Dispatchers.Main.immediate)
    private var job: Job? = null
    private var player: Player? = null

    /** Waktu (ms, jam sistem) timer berakhir. 0 = tidak ada hitung mundur. */
    var endAtMs by mutableStateOf(0L)
        private set

    /** True = berhenti setelah lagu yang sedang diputar selesai. */
    var endOfSong by mutableStateOf(false)
        private set

    val active: Boolean get() = endAtMs > 0L || endOfSong

    fun attach(p: Player) {
        player = p
    }

    fun detach() {
        cancel()
        player = null
    }

    fun startMinutes(minutes: Int) {
        cancel()
        val total = minutes * 60_000L
        endAtMs = System.currentTimeMillis() + total
        job = scope.launch {
            while (true) {
                val left = endAtMs - System.currentTimeMillis()
                if (left <= 0) break
                // Mengecilkan volume pelan-pelan di 20 detik terakhir.
                VolumeMixer.sleep = if (left < FADE_MS) (left.toFloat() / FADE_MS).coerceIn(0f, 1f) else 1f
                VolumeMixer.apply()
                delay(if (left < FADE_MS) 250L else 1_000L)
            }
            fire()
        }
    }

    fun startEndOfSong() {
        cancel()
        endOfSong = true
    }

    fun cancel() {
        job?.cancel()
        job = null
        endAtMs = 0L
        endOfSong = false
        VolumeMixer.sleep = 1f
        VolumeMixer.apply()
    }

    /** Dipanggil PlaybackService saat lagu berganti sendiri (habis). */
    fun onTrackFinished() {
        if (endOfSong) fire()
    }

    private fun fire() {
        player?.pause()
        cancel() // mengembalikan volume ke 100% supaya lagu berikutnya normal
    }

    private const val FADE_MS = 20_000L
}
