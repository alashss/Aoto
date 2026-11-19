package com.jimz.jimztune.player

import android.os.Handler
import android.os.Looper
import androidx.media3.common.MediaItem
import androidx.media3.common.Player
import com.jimz.jimztune.data.stats.PlayLog

/**
 * Mencatat lama dan jumlah putar tiap lagu ke [PlayLog] (untuk Rekap dan Streak).
 * Dihitung tiap 5 detik selama musik benar-benar terdengar. Satu "putar" dihitung kalau lagu didengar >= 30 detik.
 */
class ListenLogger(private val context: android.content.Context, private val player: Player) {
    private val handler = Handler(Looper.getMainLooper())
    private var heardMs = 0L
    private var counted = false

    private val tick = object : Runnable {
        override fun run() {
            if (player.isPlaying) log()
            handler.postDelayed(this, STEP_MS)
        }
    }

    private val listener = object : Player.Listener {
        override fun onMediaItemTransition(mediaItem: MediaItem?, reason: Int) {
            heardMs = 0L
            counted = false
        }
    }

    fun start() {
        player.addListener(listener)
        handler.postDelayed(tick, STEP_MS)
    }

    fun release() {
        handler.removeCallbacks(tick)
        player.removeListener(listener)
        PlayLog.flush()
        com.jimz.jimztune.AppGraph.cloud.flushStats()
    }

    private fun log() {
        val item = player.currentMediaItem ?: return
        heardMs += STEP_MS
        val newPlay = !counted && heardMs >= 30_000L
        if (newPlay) counted = true
        val song = item.toSong()
        PlayLog.record(
            id = song.id,
            title = song.title,
            artist = song.subtitle.substringBefore(" • ").trim(),
            art = song.thumbnailUrl,
            seconds = (STEP_MS / 1000).toInt(),
            newPlay = newPlay
        )
    }

    private companion object {
        const val STEP_MS = 5_000L
    }
}
