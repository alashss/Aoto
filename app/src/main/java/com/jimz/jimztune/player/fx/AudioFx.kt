package com.jimz.jimztune.player.fx

import android.content.Context
import android.content.SharedPreferences
import android.os.Handler
import android.os.Looper
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.setValue
import androidx.media3.common.Player
import java.util.concurrent.CopyOnWriteArrayList

/** Semua pilihan kontrol audio dalam satu objek tak-berubah. */
data class FxState(
    val skipSilence: Boolean = false,
    val speed: Float = 1f,
    val pitch: Float = 1f,
    /** True = nada ikut kecepatan (seperti memutar piringan hitam lebih cepat/lambat). */
    val varispeed: Boolean = false,
    val normalize: Boolean = false,
    val eqOn: Boolean = false,
    /** Nama preset EQ, atau "Custom" kalau slider digeser manual. */
    val eqPreset: String = "Flat",
    /** Penguatan dB di 5 titik tetap ([EqPresets.FREQS]); dipetakan ke band milik perangkat. */
    val eqGains: List<Float> = List(5) { 0f },
    /** 0..100 */
    val bass: Int = 0,
    /** 0..100 */
    val virtualizer: Int = 0,
    /** 0..1 */
    val reverb: Float = 0f,
    val pan8d: Boolean = false,
    val warm: Boolean = false,
    /** 0 = mati (gapless). */
    val crossfadeSec: Int = 0,
    val style: String = "Normal"
) {
    val effectivePitch: Float get() = if (varispeed) speed else pitch
}

/**
 * Pusat pengaturan audio. Hidup di tingkat aplikasi (UI dan PlaybackService satu proses):
 * UI mengubah [state], PlaybackService mendengarkan lewat [addListener] dan menerapkannya ke pemutar.
 */
object AudioFx {
    private lateinit var prefs: SharedPreferences
    private val main = Handler(Looper.getMainLooper())
    private val listeners = CopyOnWriteArrayList<(FxState) -> Unit>()
    private val saveTask = Runnable { save() }

    var state by mutableStateOf(FxState())
        private set

    fun init(context: Context) {
        if (::prefs.isInitialized) return
        prefs = context.applicationContext.getSharedPreferences("jimztune_audiofx", Context.MODE_PRIVATE)
        state = load()
    }

    fun addListener(l: (FxState) -> Unit) {
        listeners.add(l)
    }

    fun removeListener(l: (FxState) -> Unit) {
        listeners.remove(l)
    }

    /** Mengubah satu atau beberapa pilihan. Kalau [markCustom], label gaya suara menjadi "Custom". */
    fun update(markCustom: Boolean = true, block: FxState.() -> FxState) {
        var next = state.block()
        if (markCustom && next.style != "Custom") next = next.copy(style = "Custom")
        commit(next)
    }

    /** Memilih preset gaya suara (lihat [SoundStyles]). */
    fun applyStyle(style: SoundStyle) {
        commit(style.apply(state).copy(style = style.name))
    }

    fun resetAll() {
        commit(FxState(skipSilence = state.skipSilence, normalize = state.normalize, crossfadeSec = state.crossfadeSec))
    }

    /** Penguatan EQ yang sedang berlaku (preset atau custom). */
    fun eqGainsOf(s: FxState): List<Float> =
        if (s.eqPreset == "Custom") s.eqGains else (EqPresets.all[s.eqPreset] ?: EqPresets.FLAT)

    private fun commit(next: FxState) {
        state = next
        main.removeCallbacks(saveTask)
        main.postDelayed(saveTask, 400L) // digeser terus-menerus: simpan setelah berhenti
        listeners.forEach { it(next) }
    }

    private fun save() {
        val s = state
        prefs.edit()
            .putBoolean("skip", s.skipSilence)
            .putFloat("speed", s.speed)
            .putFloat("pitch", s.pitch)
            .putBoolean("vari", s.varispeed)
            .putBoolean("norm", s.normalize)
            .putBoolean("eq_on", s.eqOn)
            .putString("eq_preset", s.eqPreset)
            .putString("eq_gains", s.eqGains.joinToString(","))
            .putInt("bass", s.bass)
            .putInt("virt", s.virtualizer)
            .putFloat("reverb", s.reverb)
            .putBoolean("pan8d", s.pan8d)
            .putBoolean("warm", s.warm)
            .putInt("xfade", s.crossfadeSec)
            .putString("style", s.style)
            .apply()
    }

    private fun load(): FxState {
        val gains = prefs.getString("eq_gains", null)?.split(",")?.mapNotNull { it.toFloatOrNull() }
            ?.takeIf { it.size == 5 } ?: List(5) { 0f }
        return FxState(
            skipSilence = prefs.getBoolean("skip", false),
            speed = prefs.getFloat("speed", 1f).coerceIn(0.5f, 2f),
            pitch = prefs.getFloat("pitch", 1f).coerceIn(0.5f, 2f),
            varispeed = prefs.getBoolean("vari", false),
            normalize = prefs.getBoolean("norm", false),
            eqOn = prefs.getBoolean("eq_on", false),
            eqPreset = prefs.getString("eq_preset", "Flat") ?: "Flat",
            eqGains = gains,
            bass = prefs.getInt("bass", 0).coerceIn(0, 100),
            virtualizer = prefs.getInt("virt", 0).coerceIn(0, 100),
            reverb = prefs.getFloat("reverb", 0f).coerceIn(0f, 1f),
            pan8d = prefs.getBoolean("pan8d", false),
            warm = prefs.getBoolean("warm", false),
            crossfadeSec = prefs.getInt("xfade", 0).coerceIn(0, 12),
            style = prefs.getString("style", "Normal") ?: "Normal"
        )
    }
}

/** Preset equalizer. Lima nilai = penguatan dB di [FREQS]. */
object EqPresets {
    val FREQS = floatArrayOf(60f, 230f, 910f, 3600f, 14000f)
    val LABELS = listOf("60 Hz", "230 Hz", "910 Hz", "3,6 kHz", "14 kHz")
    val FLAT = listOf(0f, 0f, 0f, 0f, 0f)

    val all: LinkedHashMap<String, List<Float>> = linkedMapOf(
        "Flat" to FLAT,
        "Bass" to listOf(6f, 4f, 0f, 0f, 0f),
        "Treble" to listOf(0f, 0f, 0f, 3f, 6f),
        "Vokal" to listOf(-3f, -1f, 3f, 4f, 1f),
        "Rock" to listOf(5f, 3f, -1f, 3f, 5f),
        "Pop" to listOf(-1f, 2f, 4f, 2f, -1f),
        "Jazz" to listOf(3f, 2f, -2f, 2f, 4f),
        "Klasik" to listOf(4f, 3f, -2f, 3f, 4f),
        "Elektronik" to listOf(6f, 4f, 0f, -2f, 4f),
        "Hangat" to listOf(3f, 2f, 0f, -3f, -8f)
    )

    /** Penguatan dB pada frekuensi [hz], diinterpolasi secara logaritmik antar titik. */
    fun gainAt(hz: Float, gains: List<Float>): Float {
        if (hz <= FREQS.first()) return gains.first()
        if (hz >= FREQS.last()) return gains.last()
        for (i in 0 until FREQS.size - 1) {
            if (hz <= FREQS[i + 1]) {
                val a = Math.log(FREQS[i].toDouble())
                val b = Math.log(FREQS[i + 1].toDouble())
                val t = ((Math.log(hz.toDouble()) - a) / (b - a)).toFloat()
                return gains[i] + (gains[i + 1] - gains[i]) * t
            }
        }
        return gains.last()
    }
}

/** Satu preset suara (#10): sekali ketuk mengatur tempo, nada, reverb, EQ, dll. */
class SoundStyle(val name: String, val hint: String, val apply: (FxState) -> FxState)

object SoundStyles {
    private fun FxState.base() = copy(
        speed = 1f, pitch = 1f, varispeed = false, reverb = 0f, pan8d = false, warm = false,
        eqOn = false, eqPreset = "Flat", bass = 0, virtualizer = 0
    )

    val all: List<SoundStyle> = listOf(
        SoundStyle("Normal", "Suara asli") { it.base() },
        SoundStyle("Sped up", "Lebih cepat, nada naik") { it.base().copy(speed = 1.25f, varispeed = true) },
        SoundStyle("Slowed + reverb", "Pelan, nada turun, bergema") {
            it.base().copy(speed = 0.85f, varispeed = true, reverb = 0.45f, bass = 25)
        },
        SoundStyle("Nightcore", "Cepat dan nada tinggi") { it.base().copy(speed = 1.3f, varispeed = true) },
        SoundStyle("Lo-fi", "Hangat, sedikit pelan") {
            it.base().copy(speed = 0.95f, varispeed = true, warm = true, reverb = 0.12f, eqOn = true, eqPreset = "Hangat")
        },
        SoundStyle("8D", "Suara berputar di sekeliling kepala") { it.base().copy(pan8d = true, reverb = 0.2f) },
        SoundStyle("Concert", "Luas seperti di aula") {
            it.base().copy(reverb = 0.5f, virtualizer = 60, bass = 20, eqOn = true, eqPreset = "Vokal")
        }
    )

    fun byName(name: String): SoundStyle? = all.firstOrNull { it.name == name }
}

/**
 * Menggabungkan semua pengubah volume pemutar utama (normalisasi, fade crossfade, fade timer tidur)
 * supaya tidak saling menimpa. Pemutar dipasang oleh [AudioFxController].
 */
object VolumeMixer {
    @Volatile var player: Player? = null
    @Volatile var norm: Float = 1f
    @Volatile var fade: Float = 1f
    @Volatile var sleep: Float = 1f

    fun apply() {
        player?.volume = (norm * fade * sleep).coerceIn(0f, 1f)
    }

    /** Volume untuk pemutar kedua (lagu yang memudar keluar saat crossfade). */
    fun secondaryVolume(outgoingNorm: Float, factor: Float): Float = (outgoingNorm * sleep * factor).coerceIn(0f, 1f)
}
