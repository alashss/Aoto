package com.jimz.jimztune.widget

import android.content.Context
import androidx.media3.common.Player
import com.jimz.jimztune.AppGraph
import com.jimz.jimztune.data.lyrics.Lyrics
import com.jimz.jimztune.data.model.isLongVersion
import com.jimz.jimztune.player.parseDurationMs
import com.jimz.jimztune.player.toSong
import kotlinx.coroutines.CancellationException
import kotlinx.coroutines.CoroutineScope
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.Job
import kotlinx.coroutines.SupervisorJob
import kotlinx.coroutines.cancel
import kotlinx.coroutines.delay
import kotlinx.coroutines.withContext
import android.graphics.drawable.BitmapDrawable
import coil.imageLoader
import coil.request.ImageRequest
import kotlinx.coroutines.launch

/**
 * Menggerakkan widget lirik. Hidup di PlaybackService (jadi tetap jalan walau layar aplikasi ditutup):
 * mengikuti pemutar, memuat lirik lagu yang sedang diputar, dan memperbarui widget hanya saat barisnya berganti.
 * Tidak memuat lirik atau berputar sama sekali kalau belum ada widget yang dipasang.
 */
class LyricsWidgetDriver(private val context: Context, private val player: Player) {
    private val scope = CoroutineScope(SupervisorJob() + Dispatchers.Main.immediate)
    private var loadJob: Job? = null
    private var tickJob: Job? = null

    private var songId: String? = null
    private var lyrics: Lyrics? = null
    private var note: String = ""
    private var widgetsPresent = false
    private var last: WidgetSnapshot? = null
    private var coverJob: Job? = null
    private var coverId: String? = null
    private var color: Int = WidgetArt.DEFAULT_COLOR
    private var coverVersion = 0

    // Animasi: slot lirik bergantian dan jeda tick mengikuti waktu baris berikutnya.
    private var slot = 0
    private var shownColor: Int = WidgetArt.DEFAULT_COLOR
    private var nextChangeMs: Long = 400L
    private var lastFlipAt = 0L

    private val listener = object : Player.Listener {
        override fun onEvents(player: Player, events: Player.Events) = refresh()
    }

    /**
     * Layar menyala / dibuka kuncinya: peluncur bisa saja membuang pembaruan widget saat layar mati, sehingga isinya basi.
     * Gambar penuh sekali supaya widget kembali sinkron dengan pemutar.
     */
    private val screenReceiver = object : android.content.BroadcastReceiver() {
        override fun onReceive(c: Context?, intent: android.content.Intent?) {
            if (!widgetsPresent) return
            LyricsWidgetProvider.invalidate()
            last = null
            publish()
        }
    }

    fun start() {
        instance = this
        widgetsPresent = LyricsWidgetProvider.hasWidgets(context)
        player.addListener(listener)
        try {
            val f = android.content.IntentFilter(android.content.Intent.ACTION_SCREEN_ON)
            f.addAction(android.content.Intent.ACTION_USER_PRESENT)
            androidx.core.content.ContextCompat.registerReceiver(
                context, screenReceiver, f, androidx.core.content.ContextCompat.RECEIVER_NOT_EXPORTED
            )
        } catch (_: Exception) {
        }
        refresh()
    }

    fun stop() {
        player.removeListener(listener)
        try { context.unregisterReceiver(screenReceiver) } catch (_: Exception) {}
        scope.cancel()
        if (instance === this) instance = null
        // Pemutar berhenti total: kembalikan widget ke keadaan awal supaya tidak menampilkan lirik basi.
        WidgetArt.saveCover(context, null)
        val idle = WidgetSnapshot.IDLE.copy(coverVersion = coverVersion + 1)
        LyricsWidgetStore.save(context, idle)
        LyricsWidgetProvider.push(context, idle)
    }

    /** Dipanggil saat widget dipasang / dibuang. */
    fun onWidgetsChanged() {
        widgetsPresent = LyricsWidgetProvider.hasWidgets(context)
        last = null
        refresh()
    }

    private fun refresh() {
        if (!widgetsPresent && !needLyrics) {
            tickJob?.cancel()
            return
        }
        val item = player.currentMediaItem
        if (item == null) {
            val had = songId != null || coverId != null
            loadJob?.cancel()
            tickJob?.cancel()
            songId = null
            coverId = null
            coverJob?.cancel()
            lyrics = null
            note = ""
            color = WidgetArt.DEFAULT_COLOR
            shownColor = color
            if (had) {
                WidgetArt.saveCover(context, null)
                coverVersion++
            }
            publish()
            return
        }
        if (item.mediaId != coverId) loadCover(item)
        if (item.mediaId != songId) loadLyrics(item.mediaId, item)
        if (player.isPlaying) startTick() else {
            tickJob?.cancel()
            publish()
        }
    }

    /** Mengunduh sampul lagu, membuat versi membulat + warna dominannya, lalu menyimpannya untuk widget. */
    private fun loadCover(item: androidx.media3.common.MediaItem) {
        coverJob?.cancel()
        coverId = item.mediaId
        val id = item.mediaId
        val url = item.mediaMetadata.artworkUri?.toString()
        if (url == null) {
            applyCover(null)
            return
        }
        coverJob = scope.launch {
            val bmp = try {
                withContext(Dispatchers.IO) {
                    val req = ImageRequest.Builder(context).data(url).allowHardware(false).size(400, 400).build()
                    val res = context.imageLoader.execute(req)
                    (res.drawable as? BitmapDrawable)?.bitmap?.let { src ->
                        val rounded = WidgetArt.roundedCover(src)
                        rounded to WidgetArt.dominantColor(src)
                    }
                }
            } catch (e: CancellationException) {
                throw e
            } catch (_: Exception) {
                null
            }
            if (coverId != id) return@launch
            applyCover(bmp)
        }
    }

    private fun applyCover(result: Pair<android.graphics.Bitmap, Int>?) {
        WidgetArt.saveCover(context, result?.first)
        color = result?.second ?: WidgetArt.DEFAULT_COLOR
        // Sampul dan warna latar diganti sekaligus dalam satu gambar ulang (tanpa pergeseran warna bertahap yang membuat kedip).
        shownColor = color
        coverVersion++
        publish()
    }

    /** Isi widget saat ini (dipakai provider ketika widget diubah ukurannya). */
    fun currentSnapshot(): WidgetSnapshot = last ?: snapshot().copy(slot = slot)

    private fun loadLyrics(id: String, item: androidx.media3.common.MediaItem) {
        loadJob?.cancel()
        songId = id
        lyrics = null
        val song = item.toSong()
        when {
            !AppGraph.remote.featureOn("lyrics") -> {
                note = "Fitur lirik sedang dimatikan"
                return
            }
            song.isLongVersion -> {
                note = "Lirik tidak tersedia untuk video ini"
                return
            }
        }
        note = "Memuat lirik…"
        loadJob = scope.launch {
            // Beri jaringan ke pencarian audio dulu, sama seperti di pemutar.
            delay(900)
            fun durationSec(): Int =
                if (player.duration > 0) (player.duration / 1000).toInt() else (parseDurationMs(song.durationText) / 1000).toInt()
            var waited = 0
            while (durationSec() <= 0 && waited < 8) {
                delay(500)
                waited++
            }
            val options = try {
                AppGraph.lyricsRepo.get(song, durationSec())
            } catch (e: CancellationException) {
                throw e
            } catch (_: Exception) {
                emptyList()
            }
            if (songId != id) return@launch
            if (options.isEmpty()) {
                lyrics = null
                note = "Lirik tidak ditemukan"
            } else {
                lyrics = options[AppGraph.lyricsPrefs.choice(id).coerceIn(0, options.lastIndex)]
                note = ""
            }
            publish()
        }
    }

    private fun startTick() {
        if (tickJob?.isActive == true) return
        tickJob = scope.launch {
            var n = 0
            while (true) {
                // Cek berkala apakah widget masih ada; kalau sudah dibuang, berhenti berputar.
                if (n++ % 12 == 0) {
                    widgetsPresent = LyricsWidgetProvider.hasWidgets(context)
                    if (!widgetsPresent && !needLyrics) break
                }
                publish()
                delay(nextChangeMs)
            }
        }
    }

    private fun snapshot(): WidgetSnapshot {
        val item = player.currentMediaItem ?: return WidgetSnapshot.IDLE
        val title = item.mediaMetadata.title?.toString().orEmpty().ifBlank { "Aoto" }
        val artist = item.mediaMetadata.artist?.toString().orEmpty()
        val l = lyrics
        var line: String
        var next = ""
        var idx = -2
        nextChangeMs = 500L
        when {
            l == null -> line = note.ifBlank { "…" }
            !l.synced || l.lines.isEmpty() -> line = "Lirik lagu ini tidak bersinkron. Buka Aoto untuk membacanya."
            else -> {
                val pos = player.currentPosition + 180L + AppGraph.lyricsPrefs.offsetMs(item.mediaId)
                var lo = 0
                var hi = l.lines.lastIndex
                while (lo <= hi) {
                    val mid = (lo + hi) / 2
                    if (l.lines[mid].timeMs <= pos) {
                        idx = mid
                        lo = mid + 1
                    } else {
                        hi = mid - 1
                    }
                }
                line = if (idx < 0) "♪" else l.lines[idx].text.ifBlank { "♪" }
                next = l.lines.getOrNull(idx + 1)?.text.orEmpty()
                // Bangun lagi tepat saat baris berikutnya mulai (bukan menunggu tick 400 ms), supaya terasa mulus.
                val upcoming = l.lines.getOrNull(idx + 1)?.timeMs
                nextChangeMs = if (upcoming != null) (upcoming - pos + 8L).coerceIn(40L, 500L) else 500L
            }
        }
        val key = ((item.mediaId.hashCode() * 31 + idx) * 31) + line.hashCode()
        val dur = player.duration
        val known = dur > 0 && dur != androidx.media3.common.C.TIME_UNSET
        val progress = if (known) (player.currentPosition.coerceIn(0L, dur) * 50L / dur).toInt() else -1
        return WidgetSnapshot(
            title, artist, line, next, player.isPlaying, shownColor, coverVersion, slot, key,
            progress, if (known) dur else 0L
        )
    }

    private fun publish() {
        var snap = snapshot()
        val prev = last
        // Baris berganti -> pindah ke slot satunya, itulah yang memicu animasi masuk/keluar di widget.
        // Kalau baris berganti terlalu cepat (animasi sebelumnya belum selesai), teks diganti langsung di slot yang
        // sedang tampil tanpa animasi, supaya dua baris tidak saling menimpa / berkedip seperti sebelumnya.
        if (ANIMATE_LINES && LyricsWidgetStore.animate(context) && prev != null && snap.key != prev.key) {
            val now = android.os.SystemClock.elapsedRealtime()
            if (snap.title != prev.title || now - lastFlipAt >= MIN_FLIP_MS) {
                slot = 1 - slot
                lastFlipAt = now
            }
        }
        if (!ANIMATE_LINES || !LyricsWidgetStore.animate(context)) slot = 0
        snap = snap.copy(slot = slot)
        if (snap == prev) return
        last = snap
        LyricsWidgetStore.save(context, snap)
        LyricsWidgetProvider.push(context, snap)
    }

    companion object {
        /**
         * Animasi ganti baris dimatikan. Di sejumlah peluncur (mis. HyperOS/ColorOS) ViewFlipper pada widget kehilangan
         * animasi masuknya, sehingga baris lirik hilang (kosong) sekitar satu detik tiap berganti baris. Dengan false,
         * teks diganti langsung di slot yang sama dan selalu terlihat.
         */
        private const val ANIMATE_LINES = false

        /** Jarak minimal antar animasi ganti baris (anim keluar 140 ms + masuk 240 ms + sedikit napas). */
        private const val MIN_FLIP_MS = 560L

        @Volatile
        var instance: LyricsWidgetDriver? = null

        /** Dynamic Island meminta lirik walau tidak ada widget terpasang. */
        @Volatile
        var needLyrics: Boolean = false
    }
}
