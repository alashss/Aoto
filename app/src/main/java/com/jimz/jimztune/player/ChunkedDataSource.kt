package com.jimz.jimztune.player

import android.net.Uri
import android.os.SystemClock
import androidx.annotation.OptIn
import androidx.media3.common.C
import androidx.media3.common.util.UnstableApi
import androidx.media3.datasource.DataSource
import androidx.media3.datasource.DataSpec
import androidx.media3.datasource.TransferListener
import java.io.IOException
import java.io.InterruptedIOException
import java.util.concurrent.CountDownLatch
import java.util.concurrent.TimeUnit
import java.util.concurrent.atomic.AtomicInteger

/**
 * Membuat lagu YouTube cepat mulai, tanpa jeda menunggu di tengah lagu.
 *
 * Temuan dari uji di HP:
 *  - permintaan dengan batas jelas (`Range: bytes=awal-akhir`) dilayani googlevideo langsung dengan kecepatan penuh,
 *    sedangkan permintaan tanpa batas dari awal file lambat dimulai (tombol putar berputar lama);
 *  - memecah SELURUH lagu menjadi potongan-potongan membuat lagu macet di batas potongan;
 *  - parameter `&range=` di URL tidak dilayani sebagaimana mestinya.
 *
 * Cara kerjanya:
 *  1. [HEAD_BYTES] (4 MB) pertama diminta dengan Range ber-ujung (cepat). Kebanyakan lagu 3–5 menit selesai di sini,
 *     jadi cuma satu permintaan.
 *  2. Untuk lagu yang lebih panjang, permintaan untuk SISANYA (tanpa batas ujung, seperti perilaku asli) sudah dibuka
 *     di latar belakang sejak awal lagu. Waktu tunggu pembukaannya terbayar saat pendengar masih di awal lagu, bukan
 *     saat buffer hampir habis. Kalau sambungan siap itu gagal dipakai, sisa file dibuka ulang secara biasa.
 *
 * Dari sisi pemutar tidak ada yang berubah: [open] melaporkan sisa panjang file (dari `clen` di URL atau header
 * Content-Range), jadi geser posisi dan cache normal. Kalau ukuran file tidak bisa dipastikan, permintaan diulang
 * sebagai permintaan biasa. Sumber non-googlevideo (file unduhan, Piped, Invidious) langsung diteruskan.
 */
@OptIn(UnstableApi::class)
class ChunkedDataSource(
    private val factory: DataSource.Factory,
    private val enabled: () -> Boolean = { true }
) : DataSource {

    private enum class Mode { PASS, HEAD, TAIL }

    /** Sambungan untuk sisa file yang dibuka lebih awal di thread terpisah. */
    private class Ahead(val source: DataSource, val position: Long) {
        val state = AtomicInteger(RUNNING)
        val done = CountDownLatch(1)

        @Volatile var length = C.LENGTH_UNSET.toLong()

        @Volatile var error: IOException? = null

        /** Membatalkan / membuang sambungan ini dan memastikan koneksinya ditutup tepat satu kali oleh pemiliknya. */
        fun discard() {
            if (!state.compareAndSet(RUNNING, CANCELLED)) {
                try {
                    source.close()
                } catch (_: Exception) {
                }
            }
        }

        companion object {
            const val RUNNING = 0
            const val FINISHED = 1
            const val CANCELLED = 2
        }
    }

    private val delegate: DataSource = factory.createDataSource()
    private var current: DataSource = delegate
    private val listeners = ArrayList<TransferListener>()

    private var spec: DataSpec? = null
    private var mode = Mode.PASS
    private var nextPosition = 0L
    private var remaining = 0L      // sisa byte yang masih harus diberikan ke pemutar
    private var chunkRemaining = 0L // sisa byte di permintaan yang sedang dibaca
    private var ahead: Ahead? = null
    private var freshTail = false   // sambungan lanjutan belum pernah berhasil dibaca
    private var reopenFails = 0     // berapa kali sambungan dibuka ulang berturut-turut tanpa ada data masuk
    private var startPosition = 0L
    private var aheadPending = false // sisa file perlu sambungan lanjutan yang belum dibuka
    private var aheadPosition = 0L
    private var aheadLength = C.LENGTH_UNSET.toLong()

    override fun addTransferListener(transferListener: TransferListener) {
        listeners += transferListener
        delegate.addTransferListener(transferListener)
    }

    override fun open(dataSpec: DataSpec): Long {
        discardAhead()
        current = delegate
        spec = dataSpec
        mode = Mode.PASS
        freshTail = false
        reopenFails = 0
        aheadPending = false
        if (!enabled() || !isGoogleVideo(dataSpec)) return delegate.open(dataSpec)

        val unset = C.LENGTH_UNSET.toLong()
        val want = dataSpec.length
        // Permintaan kecil (mis. pengisian celah di cache) tidak perlu dua tahap.
        if (want != unset && want <= HEAD_BYTES) return delegate.open(dataSpec)

        val started = SystemClock.elapsedRealtime()
        delegate.open(dataSpec.buildUpon().setLength(HEAD_BYTES).build())
        val range = parseContentRange(delegate.responseHeaders)
        val total = dataSpec.uri.getQueryParameter("clen")?.toLongOrNull()?.takeIf { it > 0 } ?: range?.total

        if (range == null || total == null || total <= dataSpec.position) {
            // Ukuran file tidak bisa dipastikan: ulangi sebagai permintaan biasa (perilaku asli).
            PlaybackDiagnostics.add("Unduh bertahap: ukuran file tak diketahui, pakai cara biasa")
            delegate.close()
            return delegate.open(dataSpec)
        }

        val toEnd = total - dataSpec.position
        remaining = if (want != unset) minOf(want, toEnd) else toEnd
        chunkRemaining = minOf(range.end - range.start + 1, remaining)
        nextPosition = dataSpec.position
        mode = Mode.HEAD
        PlaybackDiagnostics.add("Unduh bertahap: potongan awal siap (${SystemClock.elapsedRealtime() - started} ms)")

        // Lagu lebih panjang dari potongan awal: sambungan untuk sisanya disiapkan di latar belakang, tapi BARU
        // setelah audio mulai mengalir (lihat read), supaya tidak mengganggu start.
        startPosition = nextPosition
        aheadPending = remaining > chunkRemaining
        aheadPosition = nextPosition + chunkRemaining
        aheadLength = if (want != unset) remaining - chunkRemaining else unset
        return remaining
    }

    override fun read(buffer: ByteArray, offset: Int, length: Int): Int {
        if (mode == Mode.PASS) return delegate.read(buffer, offset, length)
        if (length == 0) return 0
        if (remaining <= 0L) return C.RESULT_END_OF_INPUT

        if (chunkRemaining <= 0L) {
            if (mode == Mode.TAIL) return C.RESULT_END_OF_INPUT
            openTail()
        }

        val limit = minOf(length.toLong(), chunkRemaining, remaining).toInt()
        val n = readFromCurrent(buffer, offset, limit)
        if (n == C.RESULT_END_OF_INPUT) {
            chunkRemaining = 0L
            if (mode == Mode.HEAD && remaining > 0L) {
                // Potongan awal berakhir lebih cepat dari perkiraan: lanjutkan dengan sisa file.
                openTail()
                return read(buffer, offset, length)
            }
            return C.RESULT_END_OF_INPUT
        }
        nextPosition += n
        chunkRemaining -= n
        remaining -= n
        if (aheadPending && mode == Mode.HEAD && nextPosition - startPosition >= AHEAD_AFTER_BYTES) {
            aheadPending = false
            spec?.let { startAhead(it, aheadPosition, aheadLength) }
        }
        return n
    }

    /**
     * Membaca dari sambungan aktif. Kalau sambungan sisa-file putus di tengah lagu (server menutup sambungan yang
     * lama menganggur, sinyal goyang, dsb.), langsung buka ulang dari posisi terakhir di sini juga, tanpa
     * menunggu pemutar mengulang (yang butuh jeda beberapa detik = lagu terasa macet). Maksimal [MAX_REOPEN]
     * kali berturut-turut tanpa ada data yang masuk; sesudah itu error diteruskan ke pemutar seperti biasa.
     */
    private fun readFromCurrent(buffer: ByteArray, offset: Int, limit: Int): Int {
        try {
            val n = current.read(buffer, offset, limit)
            if (n > 0) {
                freshTail = false
                reopenFails = 0
            }
            return n
        } catch (e: IOException) {
            if (e is InterruptedIOException || mode != Mode.TAIL || reopenFails >= MAX_REOPEN) throw e
            reopenFails++
            freshTail = false
            PlaybackDiagnostics.add("Unduh bertahap: sambungan terputus, membuka ulang — ${e.message?.take(50)}")
            try {
                current.close()
            } catch (_: Exception) {
            }
            current = delegate
            openTailNow()
            return current.read(buffer, offset, minOf(limit.toLong(), chunkRemaining).toInt())
        }
    }

    // ---------------------------------------------------------------- tahap 2

    private fun startAhead(base: DataSpec, position: Long, length: Long) {
        val source = factory.createDataSource()
        listeners.forEach { source.addTransferListener(it) }
        val a = Ahead(source, position)
        ahead = a
        val request = base.buildUpon().setPosition(position).setLength(length).build()
        val t0 = SystemClock.elapsedRealtime()
        Thread({
            try {
                a.length = source.open(request)
            } catch (e: IOException) {
                a.error = e
            } catch (e: RuntimeException) {
                a.error = IOException(e)
            }
            if (!a.state.compareAndSet(Ahead.RUNNING, Ahead.FINISHED)) {
                try {
                    source.close()
                } catch (_: Exception) {
                }
            }
            if (a.error == null) PlaybackDiagnostics.add("Unduh bertahap: lanjutan siap di latar (${SystemClock.elapsedRealtime() - t0} ms)")
            a.done.countDown()
        }, "Aoto-lanjutan").apply { isDaemon = true }.start()
    }

    private fun discardAhead() {
        ahead?.discard()
        ahead = null
    }

    /** Beralih ke sambungan yang sudah disiapkan; kalau tidak ada / gagal, buka sisa file seperti biasa. */
    @Throws(IOException::class)
    private fun openTail() {
        val a = ahead
        ahead = null
        if (a != null) {
            val t0 = SystemClock.elapsedRealtime()
            try {
                a.done.await(AHEAD_WAIT_SECONDS, TimeUnit.SECONDS)
            } catch (e: InterruptedException) {
                a.discard()
                Thread.currentThread().interrupt()
                throw InterruptedIOException()
            }
            val ok = a.state.get() == Ahead.FINISHED && a.error == null && a.position == nextPosition && a.length != 0L
            if (ok) {
                try {
                    delegate.close()
                } catch (_: Exception) {
                }
                val unset = C.LENGTH_UNSET.toLong()
                current = a.source
                chunkRemaining = if (a.length == unset) remaining else minOf(a.length, remaining)
                mode = Mode.TAIL
                freshTail = true
                PlaybackDiagnostics.add("Unduh bertahap: beralih ke lanjutan, menunggu ${SystemClock.elapsedRealtime() - t0} ms")
                return
            }
            a.discard()
        }
        current = delegate
        openTailNow()
    }

    /** Membuka sisa file (tanpa batas ujung) lewat [delegate] dan menunggu sampai siap. */
    @Throws(IOException::class)
    private fun openTailNow() {
        val base = spec ?: throw IOException("Sumber data belum dibuka")
        val unset = C.LENGTH_UNSET.toLong()
        var failure: IOException? = null
        val started = SystemClock.elapsedRealtime()
        repeat(2) { attempt ->
            try {
                delegate.close()
                val length = if (base.length != unset) remaining else unset
                val got = delegate.open(base.buildUpon().setPosition(nextPosition).setLength(length).build())
                current = delegate
                mode = Mode.TAIL
                chunkRemaining = if (got == unset) remaining else minOf(got, remaining)
                if (chunkRemaining <= 0L) throw IOException("Sisa file kosong")
                PlaybackDiagnostics.add(
                    "Unduh bertahap: lanjutan dibuka langsung (${SystemClock.elapsedRealtime() - started} ms" +
                        if (attempt > 0) ", percobaan ke-2)" else ")"
                )
                return
            } catch (e: IOException) {
                failure = e
                PlaybackDiagnostics.add("Unduh bertahap: lanjutan gagal — ${e.message?.take(60)}")
            }
        }
        throw failure ?: IOException("Gagal membuka sisa file")
    }

    // ---------------------------------------------------------------- lain-lain

    override fun getUri(): Uri? = if (mode == Mode.PASS) delegate.uri else spec?.uri

    override fun getResponseHeaders(): Map<String, List<String>> = current.responseHeaders

    @Throws(IOException::class)
    override fun close() {
        discardAhead()
        try {
            current.close()
        } finally {
            try {
                if (current !== delegate) delegate.close()
            } finally {
                current = delegate
                spec = null
                mode = Mode.PASS
                chunkRemaining = 0L
                freshTail = false
            }
        }
    }

    private fun isGoogleVideo(dataSpec: DataSpec): Boolean {
        val scheme = dataSpec.uri.scheme
        val host = dataSpec.uri.host ?: return false
        return (scheme == "https" || scheme == "http") &&
            dataSpec.httpMethod == DataSpec.HTTP_METHOD_GET &&
            dataSpec.httpBody == null &&
            host.endsWith("googlevideo.com")
    }

    private class Range(val start: Long, val end: Long, val total: Long?)

    /** Membaca "Content-Range: bytes 0-1048575/3456789" (nama header dicari tanpa membedakan huruf besar/kecil). */
    private fun parseContentRange(headers: Map<String, List<String>>): Range? {
        val value = headers.entries.firstOrNull { it.key.equals("Content-Range", ignoreCase = true) }
            ?.value?.firstOrNull() ?: return null
        val m = RANGE.matchEntire(value.trim()) ?: return null
        val start = m.groupValues[1].toLongOrNull() ?: return null
        val end = m.groupValues[2].toLongOrNull() ?: return null
        return Range(start, end, m.groupValues[3].toLongOrNull())
    }

    class Factory(
        private val upstream: DataSource.Factory,
        private val enabled: () -> Boolean = { true }
    ) : DataSource.Factory {
        override fun createDataSource(): DataSource = ChunkedDataSource(upstream, enabled)
    }

    private companion object {
        const val HEAD_BYTES = 1L * 1024 * 1024

        /** Sambungan lanjutan dibuka begitu audio awal mulai mengalir (32 KB pertama terbaca): sisa lagu langsung disiapkan, tidak menunggu. */
        const val AHEAD_AFTER_BYTES = 32L * 1024
        const val AHEAD_WAIT_SECONDS = 35L
        const val MAX_REOPEN = 2
        val RANGE = Regex("bytes\\s+(\\d+)-(\\d+)/(\\d+|\\*)")
    }
}
