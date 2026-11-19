package com.jimz.jimztune.player.fx

import android.media.audiofx.BassBoost
import android.media.audiofx.Equalizer
import android.media.audiofx.LoudnessEnhancer
import android.media.audiofx.Virtualizer
import android.os.Handler
import android.os.Looper
import androidx.media3.common.C
import androidx.media3.common.MediaItem
import androidx.media3.common.PlaybackParameters
import androidx.media3.common.Player
import androidx.media3.common.util.UnstableApi
import androidx.media3.datasource.DataSource
import androidx.media3.exoplayer.ExoPlayer
import com.jimz.jimztune.data.stream.LoudnessStore

/**
 * Menerapkan [AudioFx] ke ExoPlayer milik PlaybackService: skip silence, tempo/pitch, normalisasi volume,
 * equalizer + bass + virtualizer (efek bawaan Android), efek buatan sendiri ([SoundFxProcessor]), dan crossfade.
 * Semua efek dibungkus try/catch: kalau perangkat tidak mendukung, pemutaran tetap jalan.
 */
@UnstableApi
class AudioFxController(
    private val context: android.content.Context,
    private val player: ExoPlayer,
    private val processor: SoundFxProcessor,
    dataSources: DataSource.Factory
) {
    private val main = Handler(Looper.getMainLooper())
    private val crossfade = CrossfadeController(context, player, dataSources)

    private var eq: Equalizer? = null
    private var bassBoost: BassBoost? = null
    private var virtualizer: Virtualizer? = null
    private var loudness: LoudnessEnhancer? = null
    private var session = C.AUDIO_SESSION_ID_UNSET

    private val fxListener: (FxState) -> Unit = { s -> main.post { apply(s) } }

    private val playerListener = object : Player.Listener {
        override fun onAudioSessionIdChanged(audioSessionId: Int) {
            attachEffects(audioSessionId)
        }

        override fun onMediaItemTransition(mediaItem: MediaItem?, reason: Int) {
            applyNormalization(AudioFx.state)
        }

        override fun onPlaybackStateChanged(playbackState: Int) {
            // Alamat audio baru ketahuan setelah siap diputar, saat itu data kenyaringan lagu sudah tercatat.
            if (playbackState == Player.STATE_READY) applyNormalization(AudioFx.state)
        }
    }

    fun start() {
        VolumeMixer.player = player
        VolumeMixer.fade = 1f
        player.addListener(playerListener)
        AudioFx.addListener(fxListener)
        attachEffects(player.audioSessionId)
        apply(AudioFx.state)
        crossfade.start()
    }

    fun release() {
        AudioFx.removeListener(fxListener)
        player.removeListener(playerListener)
        crossfade.release()
        releaseEffects()
        if (VolumeMixer.player === player) VolumeMixer.player = null
    }

    private fun apply(s: FxState) {
        try {
            player.setSkipSilenceEnabled(s.skipSilence)
            val speed = s.speed.coerceIn(0.5f, 2f)
            val pitch = s.effectivePitch.coerceIn(0.5f, 2f)
            val cur = player.playbackParameters
            if (cur.speed != speed || cur.pitch != pitch) player.playbackParameters = PlaybackParameters(speed, pitch)
        } catch (_: Throwable) {
        }
        processor.reverb = s.reverb
        processor.pan8d = s.pan8d
        processor.warm = s.warm
        applyEq(s)
        try {
            bassBoost?.let {
                if (it.strengthSupported) it.setStrength((s.bass * 10).coerceIn(0, 1000).toShort())
                it.setEnabled(s.bass > 0)
            }
            virtualizer?.let {
                if (it.strengthSupported) it.setStrength((s.virtualizer * 10).coerceIn(0, 1000).toShort())
                it.setEnabled(s.virtualizer > 0)
            }
        } catch (_: Throwable) {
        }
        applyNormalization(s)
        crossfade.refresh()
    }

    private fun applyEq(s: FxState) {
        val e = eq ?: return
        try {
            if (!s.eqOn) {
                e.setEnabled(false)
                return
            }
            val range = e.bandLevelRange
            val min = range[0].toInt()
            val max = range[1].toInt()
            val gains = AudioFx.eqGainsOf(s)
            for (b in 0 until e.numberOfBands.toInt()) {
                val hz = e.getCenterFreq(b.toShort()) / 1000f
                val mb = (EqPresets.gainAt(hz, gains) * 100f).toInt().coerceIn(min, max)
                e.setBandLevel(b.toShort(), mb.toShort())
            }
            e.setEnabled(true)
        } catch (_: Throwable) {
        }
    }

    /**
     * Normalisasi: YouTube memberi tahu seberapa keras tiap lagu (loudnessDb). Lagu yang lebih keras dari standar
     * diturunkan lewat volume pemutar; lagu yang lebih pelan dinaikkan sedikit (maks +6 dB) lewat LoudnessEnhancer.
     * Lagu tanpa data kenyaringan tidak diubah.
     */
    private fun applyNormalization(s: FxState) {
        val id = player.currentMediaItem?.mediaId
        var gainDb = 0f
        if (s.normalize && id != null) {
            LoudnessStore.get(id)?.let { gainDb = (-it).coerceIn(-12f, 6f) }
        }
        VolumeMixer.norm = if (gainDb < 0f) Math.pow(10.0, gainDb / 20.0).toFloat() else 1f
        VolumeMixer.apply()
        try {
            loudness?.let {
                if (gainDb > 0f) {
                    it.setTargetGain((gainDb * 100).toInt())
                    it.setEnabled(true)
                } else {
                    it.setEnabled(false)
                }
            }
        } catch (_: Throwable) {
        }
    }

    private fun attachEffects(sessionId: Int) {
        if (sessionId <= 0 || sessionId == session) return
        releaseEffects()
        session = sessionId
        eq = runCatching { Equalizer(0, sessionId) }.getOrNull()
        bassBoost = runCatching { BassBoost(0, sessionId) }.getOrNull()
        virtualizer = runCatching { Virtualizer(0, sessionId) }.getOrNull()
        loudness = runCatching { LoudnessEnhancer(sessionId) }.getOrNull()
        apply(AudioFx.state)
    }

    private fun releaseEffects() {
        runCatching { eq?.release() }
        runCatching { bassBoost?.release() }
        runCatching { virtualizer?.release() }
        runCatching { loudness?.release() }
        eq = null
        bassBoost = null
        virtualizer = null
        loudness = null
        session = C.AUDIO_SESSION_ID_UNSET
    }
}
