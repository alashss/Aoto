package com.jimz.jimztune.player.fx

import android.content.Context
import android.os.Handler
import android.os.Looper
import android.os.SystemClock
import androidx.media3.common.AudioAttributes
import androidx.media3.common.C
import androidx.media3.common.MediaItem
import androidx.media3.common.Player
import androidx.media3.common.util.UnstableApi
import androidx.media3.datasource.DataSource
import androidx.media3.exoplayer.ExoPlayer
import androidx.media3.exoplayer.source.DefaultMediaSourceFactory
import com.jimz.jimztune.player.SleepTimer
import com.jimz.jimztune.player.PlaybackDiagnostics

/**
 * Crossfade dengan dua pemutar. Beberapa detik sebelum waktunya, pemutar kedua menyiapkan lagu yang SAMA
 * di posisi awal crossfade (data sudah ada di cache). Saat waktunya: pemutar utama pindah ke lagu berikutnya
 * dengan volume naik dari 0, sementara pemutar kedua memutar sisa lagu lama dengan volume turun ke 0.
 *
 * Aman terhadap kegagalan: kalau pemutar kedua belum siap, tidak ada crossfade dan lagu berganti seperti biasa (gapless).
 * Tidak dipakai saat ulangi-satu-lagu, antrean cuma satu lagu, lagu terakhir tanpa ulang, atau timer "sampai lagu selesai".
 */
@UnstableApi
class CrossfadeController(
    private val context: Context,
    private val main: ExoPlayer,
    private val dataSources: DataSource.Factory
) {
    private val handler = Handler(Looper.getMainLooper())
    private var secondary: ExoPlayer? = null
    private var preparedFor: String? = null
    private var gaveUpFor: String? = null
    private var firing = false
    private var expectTransition = false
    private var looping = false
    private var rampStart = 0L
    private var rampMs = 1L
    private var outgoingNorm = 1f

    private val listener = object : Player.Listener {
        override fun onMediaItemTransition(mediaItem: MediaItem?, reason: Int) {
            if (firing && expectTransition) {
                expectTransition = false // perpindahan yang kita picu sendiri
            } else {
                // Lompat lagu manual / ganti antrean: batalkan semuanya.
                cancelAll()
                gaveUpFor = null
            }
        }

        override fun onPlayWhenReadyChanged(playWhenReady: Boolean, reason: Int) {
            if (firing && !playWhenReady) cancelAll()
            refresh()
        }

        override fun onIsPlayingChanged(isPlaying: Boolean) {
            refresh()
        }
    }

    private val tick = object : Runnable {
        override fun run() {
            if (!firing && !(AudioFx.state.crossfadeSec > 0 && main.isPlaying)) {
                looping = false
                dropSecondary()
                return
            }
            try {
                onTick()
            } catch (t: Throwable) {
                PlaybackDiagnostics.add("Crossfade dibatalkan: ${t.javaClass.simpleName}")
                cancelAll()
            }
            handler.postDelayed(this, if (firing) 50L else 250L)
        }
    }

    fun start() {
        main.addListener(listener)
        refresh()
    }

    /** Menyalakan loop pemantau kalau perlu (dipanggil saat pengaturan atau status putar berubah). */
    fun refresh() {
        if (looping) return
        if (AudioFx.state.crossfadeSec > 0 && main.isPlaying) {
            looping = true
            handler.post(tick)
        }
    }

    fun release() {
        handler.removeCallbacks(tick)
        looping = false
        main.removeListener(listener)
        cancelAll()
    }

    private fun onTick() {
        if (firing) {
            ramp()
            return
        }
        val cf = AudioFx.state.crossfadeSec * 1000L
        if (cf <= 0L) return
        if (main.mediaItemCount < 2 || !main.hasNextMediaItem() || main.repeatMode == Player.REPEAT_MODE_ONE) {
            dropSecondary()
            return
        }
        val dur = main.duration
        if (dur == C.TIME_UNSET || dur <= 0L || dur < cf * 3) return
        val item = main.currentMediaItem ?: return
        val id = item.mediaId
        val remaining = dur - main.currentPosition

        when {
            remaining <= cf -> {
                val sec = secondary
                if (preparedFor == id && sec != null && sec.playbackState == Player.STATE_READY) {
                    if (SleepTimer.endOfSong) {
                        dropSecondary()
                        gaveUpFor = id
                    } else {
                        fire(cf)
                    }
                } else if (remaining < cf / 2) {
                    // Pemutar kedua tak kunjung siap: menyerah, lagu berganti biasa.
                    dropSecondary()
                    gaveUpFor = id
                }
            }
            remaining <= cf + PREPARE_LEAD_MS -> {
                if (preparedFor != id && gaveUpFor != id) prepare(item, dur - cf)
            }
            else -> {
                dropSecondary()
                gaveUpFor = null
            }
        }
    }

    private fun prepare(item: MediaItem, startMs: Long) {
        val sec = secondary ?: ExoPlayer.Builder(context)
            .setMediaSourceFactory(DefaultMediaSourceFactory(dataSources))
            .setAudioAttributes(
                AudioAttributes.Builder()
                    .setUsage(C.USAGE_MEDIA)
                    .setContentType(C.AUDIO_CONTENT_TYPE_MUSIC)
                    .build(),
                false // fokus audio tetap dipegang pemutar utama
            )
            .build()
            .also { secondary = it }
        sec.volume = 0f
        sec.playbackParameters = main.playbackParameters
        sec.setMediaItem(item, startMs.coerceAtLeast(0L))
        sec.playWhenReady = false
        sec.prepare()
        preparedFor = item.mediaId
    }

    private fun fire(cf: Long) {
        val sec = secondary ?: return
        val speed = main.playbackParameters.speed.coerceAtLeast(0.5f)
        rampMs = (cf / speed).toLong().coerceAtLeast(500L)
        rampStart = SystemClock.elapsedRealtime()
        outgoingNorm = VolumeMixer.norm
        firing = true
        expectTransition = true
        VolumeMixer.fade = 0f
        VolumeMixer.apply()
        sec.volume = VolumeMixer.secondaryVolume(outgoingNorm, 1f)
        sec.play()
        main.seekToNextMediaItem()
        PlaybackDiagnostics.add("Crossfade dimulai")
    }

    private fun ramp() {
        val t = ((SystemClock.elapsedRealtime() - rampStart).toFloat() / rampMs).coerceIn(0f, 1f)
        VolumeMixer.fade = Math.sin(t * Math.PI / 2).toFloat() // lagu baru naik
        VolumeMixer.apply()
        secondary?.volume = VolumeMixer.secondaryVolume(outgoingNorm, Math.cos(t * Math.PI / 2).toFloat()) // lagu lama turun
        if (t >= 1f) cancelAll()
    }

    /** Menghentikan crossfade (selesai atau dibatalkan): lepas pemutar kedua, volume utama kembali normal. */
    private fun cancelAll() {
        firing = false
        expectTransition = false
        dropSecondary()
        VolumeMixer.fade = 1f
        VolumeMixer.apply()
    }

    private fun dropSecondary() {
        secondary?.let { runCatching { it.release() } }
        secondary = null
        preparedFor = null
    }

    private companion object {
        /** Pemutar kedua mulai disiapkan sekian ms sebelum crossfade. */
        const val PREPARE_LEAD_MS = 6_000L
    }
}
