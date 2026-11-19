package com.jimz.jimztune.player.fx

import androidx.media3.common.C
import androidx.media3.common.audio.AudioProcessor
import androidx.media3.common.audio.BaseAudioProcessor
import androidx.media3.common.util.UnstableApi
import java.nio.ByteBuffer

/**
 * Efek suara buatan sendiri (tanpa bergantung pada efek bawaan perangkat, jadi hasilnya sama di semua HP):
 *  - reverb (gema ruangan)
 *  - 8D (suara berputar kiri-kanan)
 *  - warm (peredam nada tinggi untuk kesan lo-fi)
 * Hanya memproses PCM 16-bit stereo; format lain dilewati apa adanya.
 */
@UnstableApi
class SoundFxProcessor : BaseAudioProcessor() {
    @Volatile var reverb: Float = 0f
    @Volatile var pan8d: Boolean = false
    @Volatile var warm: Boolean = false

    private class Comb(size: Int) {
        private val buf = FloatArray(size)
        private var i = 0
        private var store = 0f
        fun process(input: Float, feedback: Float, damp: Float): Float {
            val out = buf[i]
            store = out * (1f - damp) + store * damp
            buf[i] = input + store * feedback
            if (++i >= buf.size) i = 0
            return out
        }
    }

    private class Allpass(size: Int) {
        private val buf = FloatArray(size)
        private var i = 0
        fun process(input: Float): Float {
            val b = buf[i]
            val out = -input + b
            buf[i] = input + b * 0.5f
            if (++i >= buf.size) i = 0
            return out
        }
    }

    private var combsL: List<Comb> = emptyList()
    private var combsR: List<Comb> = emptyList()
    private var allL: List<Allpass> = emptyList()
    private var allR: List<Allpass> = emptyList()
    private var lpL = 0f
    private var lpR = 0f
    private var lpAlpha = 0.3f
    private var phase = 0.0
    private var phaseStep = 0.0
    private var gainL = 1f
    private var gainR = 1f
    private var frameCounter = 0

    override fun onConfigure(inputAudioFormat: AudioProcessor.AudioFormat): AudioProcessor.AudioFormat {
        // Format lain: nonaktif (dilewati), bukan error, supaya pemutaran tidak pernah gagal karena efek.
        if (inputAudioFormat.encoding != C.ENCODING_PCM_16BIT || inputAudioFormat.channelCount != 2) {
            return AudioProcessor.AudioFormat.NOT_SET
        }
        return inputAudioFormat
    }

    override fun onFlush() {
        val rate = inputAudioFormat.sampleRate.coerceAtLeast(8000)
        val k = rate / 44100f
        fun d(n: Int) = (n * k).toInt().coerceAtLeast(1)
        combsL = listOf(1116, 1188, 1277, 1356).map { Comb(d(it)) }
        combsR = listOf(1116, 1188, 1277, 1356).map { Comb(d(it + 23)) }
        allL = listOf(556, 441).map { Allpass(d(it)) }
        allR = listOf(556, 441).map { Allpass(d(it + 23)) }
        lpL = 0f
        lpR = 0f
        // Low-pass satu kutub di sekitar 3,5 kHz.
        lpAlpha = (1.0 - Math.exp(-2.0 * Math.PI * 3500.0 / rate)).toFloat()
        // Satu putaran 8D tiap 8 detik.
        phaseStep = 2.0 * Math.PI / (rate * 8.0)
        frameCounter = 0
    }

    override fun onReset() {
        combsL = emptyList()
        combsR = emptyList()
        allL = emptyList()
        allR = emptyList()
    }

    override fun queueInput(inputBuffer: ByteBuffer) {
        val size = inputBuffer.remaining()
        if (size == 0) return
        val out = replaceOutputBuffer(size)
        val mix = reverb
        val do8d = pan8d
        val doWarm = warm
        val doReverb = mix > 0.001f && combsL.isNotEmpty()
        if (!doReverb && !do8d && !doWarm) {
            out.put(inputBuffer)
            out.flip()
            return
        }

        val frames = size / 4
        val dry = 1f - mix * 0.35f
        val wet = mix * 3f
        repeat(frames) {
            var l = inputBuffer.getShort() / 32768f
            var r = inputBuffer.getShort() / 32768f

            if (doWarm) {
                lpL += lpAlpha * (l - lpL)
                lpR += lpAlpha * (r - lpR)
                l = lpL
                r = lpR
            }
            if (doReverb) {
                val inp = (l + r) * 0.03f
                var wl = 0f
                var wr = 0f
                for (c in combsL) wl += c.process(inp, 0.84f, 0.25f)
                for (c in combsR) wr += c.process(inp, 0.84f, 0.25f)
                for (a in allL) wl = a.process(wl)
                for (a in allR) wr = a.process(wr)
                l = l * dry + wl * wet
                r = r * dry + wr * wet
            }
            if (do8d) {
                // Gain diperbarui tiap 16 frame (hemat CPU); batas ±0,85 supaya satu sisi tidak pernah hilang total.
                if (frameCounter++ % 16 == 0) {
                    val pan = 0.85 * Math.sin(phase)
                    val angle = (pan + 1.0) * Math.PI / 4.0
                    val depth = 0.85 + 0.15 * Math.cos(phase)
                    gainL = (Math.cos(angle) * 1.4142 * depth).toFloat()
                    gainR = (Math.sin(angle) * 1.4142 * depth).toFloat()
                }
                phase += phaseStep
                if (phase > 2.0 * Math.PI) phase -= 2.0 * Math.PI
                l *= gainL
                r *= gainR
            }
            out.putShort(toShort(l))
            out.putShort(toShort(r))
        }
        // Sisa byte (kalau ukuran tidak kelipatan 4) disalin apa adanya.
        while (inputBuffer.hasRemaining()) out.put(inputBuffer.get())
        out.flip()
    }

    private fun toShort(v: Float): Short {
        val s = (v * 32768f).toInt()
        return s.coerceIn(-32768, 32767).toShort()
    }
}
