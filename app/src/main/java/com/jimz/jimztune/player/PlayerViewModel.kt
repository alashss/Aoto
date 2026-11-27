package com.jimz.jimztune.player

import android.app.Application
import android.content.ComponentName
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.setValue
import androidx.core.content.ContextCompat
import androidx.lifecycle.AndroidViewModel
import androidx.lifecycle.viewModelScope
import androidx.media3.common.PlaybackException
import androidx.media3.common.Player
import androidx.media3.session.MediaController
import androidx.media3.session.SessionToken
import com.jimz.jimztune.AppGraph
import com.jimz.jimztune.data.lyrics.Lyrics
import com.jimz.jimztune.data.lyrics.LyricsRepository
import com.jimz.jimztune.data.model.isLongVersion
import com.jimz.jimztune.data.stream.StreamResolver
import com.jimz.jimztune.data.lyrics.LyricsState
import com.jimz.jimztune.data.lyrics.LyricTranslator
import com.jimz.jimztune.data.lyrics.TranslationState
import com.jimz.jimztune.data.model.SearchItem
import kotlinx.coroutines.CancellationException
import kotlinx.coroutines.Job
import kotlinx.coroutines.delay
import kotlinx.coroutines.launch

class PlayerViewModel(app: Application) : AndroidViewModel(app) {
    private val history = AppGraph.history
    private val lyricsRepo = AppGraph.lyricsRepo
    private val translator = LyricTranslator(app)

    var ui by mutableStateOf(PlayerUiState())
        private set
    /** Riwayat diputar. Berasal dari store bersama, jadi ikut berubah saat data cloud masuk. */
    val recent: List<SearchItem> get() = history.items
    var lyrics by mutableStateOf<LyricsState>(LyricsState.Idle)
        private set

    private var controller: MediaController? = null
    private var pending: ((MediaController) -> Unit)? = null
    private var lyricsJob: Job? = null
    private var lyricsFor: String? = null

    private val listener = object : Player.Listener {
        override fun onEvents(player: Player, events: Player.Events) {
            val transition = events.contains(Player.EVENT_MEDIA_ITEM_TRANSITION)
            if (transition) recordCurrent(player)
            // Daftar antrean (bisa ratusan lagu) hanya disusun ulang kalau isinya berubah, bukan tiap 0,5 detik.
            val queueChanged = transition || events.contains(Player.EVENT_TIMELINE_CHANGED) ||
                events.contains(Player.EVENT_PLAYLIST_METADATA_CHANGED)
            sync(player, clearError = transition, rebuildQueue = queueChanged)
        }

        override fun onPlayerError(error: PlaybackException) {
            ui = ui.copy(error = "Gagal memutar: " + describe(error))
        }
    }

    init {
        connect(app.applicationContext)

        // Memperbarui posisi lagu setiap setengah detik saat sedang diputar.
        viewModelScope.launch {
            while (true) {
                delay(500)
                val c = controller ?: continue
                if (c.isPlaying) {
                    val duration = if (c.duration > 0) c.duration else ui.durationMs
                    ui = ui.copy(positionMs = c.currentPosition.coerceAtLeast(0), durationMs = duration)
                }
            }
        }
    }

    /** Menyambung ke PlaybackService. Kalau gagal (mis. service belum terdaftar di manifest), tampilkan pesan, jangan crash. */
    private fun connect(context: android.content.Context) {
        try {
            val token = SessionToken(context, ComponentName(context, PlaybackService::class.java))
            val future = MediaController.Builder(context, token).buildAsync()
            future.addListener({
                try {
                    val c = future.get()
                    controller = c
                    c.addListener(listener)
                    sync(c)
                    pending?.invoke(c)
                    pending = null
                    PlaybackDiagnostics.add("Pemutar tersambung")
                } catch (e: Exception) {
                    val msg = "Pemutar belum siap: " + describe(e)
                    PlaybackDiagnostics.add(msg)
                    ui = ui.copy(error = msg)
                }
            }, ContextCompat.getMainExecutor(context))
        } catch (e: Throwable) {
            val msg = "Layanan pemutar tidak bisa dibuka (cek <service> di AndroidManifest): " + describe(e)
            PlaybackDiagnostics.add(msg)
            ui = ui.copy(error = msg)
        }
    }

    /** Merangkum rantai penyebab error menjadi satu kalimat pendek agar mudah dilaporkan. */
    private fun describe(t: Throwable): String {
        val parts = mutableListOf<String>()
        var cur: Throwable? = t
        while (cur != null && parts.size < 3) {
            cur.message?.takeIf { it.isNotBlank() }?.let { parts += it }
            cur = cur.cause
        }
        return parts.distinct().joinToString(" ← ").take(260).ifEmpty { t.javaClass.simpleName }
    }

    private fun withController(block: (MediaController) -> Unit) {
        val c = controller
        if (c != null) block(c) else pending = block
    }

    private fun sync(p: Player, clearError: Boolean = false, rebuildQueue: Boolean = true) {
        val item = p.currentMediaItem
        val duration = if (p.duration > 0) p.duration
        else parseDurationMs(item?.mediaMetadata?.extras?.getString("duration"))
        val song = item?.toSong()
        if (p.isPlaying) PlaybackDiagnostics.finish()
        AppGraph.setPlaying(p.isPlaying)
        AppGraph.activity.setPlaying(p.isPlaying)
        ui = ui.copy(
            current = song,
            isPlaying = p.isPlaying,
            isBuffering = p.playbackState == Player.STATE_BUFFERING,
            positionMs = p.currentPosition.coerceAtLeast(0),
            durationMs = duration,
            hasNext = p.hasNextMediaItem(),
            hasPrevious = p.hasPreviousMediaItem(),
            shuffle = p.shuffleModeEnabled,
            repeat = p.repeatMode,
            queue = if (rebuildQueue || ui.queue.size != p.mediaItemCount) {
                (0 until p.mediaItemCount).map { p.getMediaItemAt(it).toSong() }
            } else {
                ui.queue
            },
            queueIndex = p.currentMediaItemIndex,
            error = if (clearError || p.isPlaying) null else ui.error
        )
        ensureLyrics(song, (duration / 1000).toInt())
    }

    /** Terjemahan lirik lagu yang sedang tampil. Hanya diminta saat halaman lirik penuh dibuka. */
    var translation by mutableStateOf<TranslationState>(TranslationState.Off)
        private set
    private var translationJob: Job? = null
    private var lyricsVisible = false

    /** Dipanggil layar pemutar: terjemahan baru diminta ke internet kalau halaman lirik benar-benar dibuka. */
    fun setLyricsVisible(visible: Boolean) {
        lyricsVisible = visible
        if (visible && translation == TranslationState.Off) refreshTranslation()
    }

    /** Mulai (atau ulangi) terjemahan untuk lirik yang sedang tampil; dipanggil juga saat saklar di pengaturan lirik berubah. */
    fun refreshTranslation() {
        translationJob?.cancel()
        val ready = lyrics as? LyricsState.Ready
        val prefs = AppGraph.lyricsPrefs
        if (ready == null || !prefs.translateEnabled || !lyricsVisible) {
            translation = TranslationState.Off
            return
        }
        translation = TranslationState.Loading
        translationJob = viewModelScope.launch {
            translation = try {
                translator.translate(ready.songId, ready.lyrics, prefs.translateEnglish)
            } catch (e: CancellationException) {
                throw e
            } catch (_: Exception) {
                TranslationState.Failed
            }
        }
    }

    private var lyricsOptions: List<Lyrics> = emptyList()
    private var lyricsIndex = 0

    private fun ensureLyrics(song: SearchItem?, durationSec: Int) {
        if (song == null) {
            lyricsJob?.cancel()
            lyricsFor = null
            lyrics = LyricsState.Idle
            translationJob?.cancel()
            translation = TranslationState.Off
            return
        }
        if (!AppGraph.remote.featureOn("lyrics")) {
            lyricsJob?.cancel()
            lyricsFor = song.id
            lyrics = LyricsState.Unavailable("Fitur lirik sedang dimatikan")
            return
        }
        if (song.id == lyricsFor) return
        // Durasi dibutuhkan untuk mencocokkan lirik dengan benar. Kalau pemutar belum tahu, pakai durasi dari katalog,
        // dan kalau dua-duanya belum ada, tunggu (jangan mencari dulu: hasilnya bisa lagu lain).
        val sec = if (durationSec > 0) durationSec else (parseDurationMs(song.durationText) / 1000).toInt()
        if (sec <= 0 && !song.isLongVersion) {
            lyricsJob?.cancel()
            lyricsFor = null
            lyrics = LyricsState.Loading
            return
        }
        lyricsFor = song.id
        loadLyrics(song, sec)
    }

    private val lyricsWarmed = HashSet<String>()
    /** Mengunduh lirik ke cache perangkat lebih awal, supaya saat dibuka langsung tampil. */
    fun prefetchLyrics(song: SearchItem) {
        if (song.isLongVersion || !lyricsWarmed.add(song.id) || lyricsRepo.hasCache(song.id)) return
        if (lyricsWarmed.size > 300) lyricsWarmed.clear()
        viewModelScope.launch {
            try { lyricsRepo.get(song, 0) } catch (e: CancellationException) { throw e } catch (_: Exception) { }
        }
    }

    private fun loadLyrics(song: SearchItem, durationSec: Int, refresh: Boolean = false) {
        lyricsJob?.cancel()
        translationJob?.cancel()
        translation = TranslationState.Off
        if (song.isLongVersion) {
            lyrics = LyricsState.Unavailable("Lirik tidak dicari untuk video loop / mix panjang")
            return
        }
        lyrics = LyricsState.Loading
        lyricsJob = viewModelScope.launch {
            // Beri jaringan ke pencarian audio dulu; lirik menyusul sebentar kemudian.
            if (!refresh && !lyricsRepo.hasCache(song.id)) delay(150)
            lyrics = try {
                val options = lyricsRepo.get(song, durationSec, refresh)
                if (options.isEmpty()) {
                    LyricsState.NotFound
                } else {
                    lyricsOptions = options
                    lyricsIndex = AppGraph.lyricsPrefs.choice(song.id).coerceIn(0, options.lastIndex)
                    readyState(song.id)
                }
            } catch (e: CancellationException) {
                throw e
            } catch (_: Exception) {
                LyricsState.NotFound
            }
            refreshTranslation()
        }
    }

    private fun readyState(songId: String) = LyricsState.Ready(
        lyrics = lyricsOptions[lyricsIndex],
        songId = songId,
        optionCount = lyricsOptions.size,
        optionIndex = lyricsIndex,
        optionInfos = lyricsOptions.map {
            val kind = when {
                it.wordSynced -> "per kata"
                it.synced -> "per baris"
                else -> "tanpa waktu"
            }
            "${it.source} · $kind"
        }
    )

    /** Memilih sumber lirik tertentu dari daftar. */
    fun pickLyricsSource(index: Int) {
        val id = lyricsFor ?: return
        if (lyrics !is LyricsState.Ready || index !in lyricsOptions.indices || index == lyricsIndex) return
        lyricsIndex = index
        AppGraph.lyricsPrefs.setChoice(id, lyricsIndex)
        lyrics = readyState(id)
        refreshTranslation()
    }

    /** Mencari ulang dari semua sumber (mengabaikan simpanan). */
    fun retryLyrics() {
        val song = ui.current ?: return
        lyricsFor = song.id
        val sec = if (ui.durationMs > 0) (ui.durationMs / 1000).toInt() else (parseDurationMs(song.durationText) / 1000).toInt()
        loadLyrics(song, sec, refresh = true)
    }

    /** Ganti ke sumber lirik berikutnya (kalau yang tampil kurang pas dengan versi lagunya). */
    fun nextLyricsSource() {
        val id = lyricsFor ?: return
        if (lyrics !is LyricsState.Ready || lyricsOptions.size < 2) return
        lyricsIndex = (lyricsIndex + 1) % lyricsOptions.size
        AppGraph.lyricsPrefs.setChoice(id, lyricsIndex)
        lyrics = readyState(id)
        refreshTranslation()
    }

    private fun recordCurrent(p: Player) {
        val item = p.currentMediaItem ?: return
        val song = item.toSong()
        history.add(song)
        // Dicatat untuk panel owner (lagu apa, jam berapa). Hanya untuk akun yang sudah login.
        AppGraph.activity.logPlay(song)
    }

    /**
     * Playlist yang sedang menjadi sumber antrean (null = antrean bebas). Selama terisi, perubahan isi playlist
     * langsung diterapkan ke antrean (lihat [syncWithPlaylist]). Mengubah antrean manual melepas hubungan ini.
     */
    var queueSourceId by mutableStateOf<String?>(null)
        private set

    fun play(items: List<SearchItem>, index: Int) {
        if (items.isEmpty()) return
        // Kalau daftar yang diputar persis sama dengan sebuah playlist (urutan sama), antrean ikut berubah saat playlist berubah.
        queueSourceId = AppGraph.library.playlists.firstOrNull { p ->
            p.songs.size == items.size && p.songs.indices.all { p.songs[it].id == items[it].id }
        }?.id
        // Mulai mencari URL audio sekarang juga (selagi layanan pemutar disambungkan), bukan setelah pemutar siap.
        val start = index.coerceIn(0, items.lastIndex)
        // Hanya lagu yang diketuk: prefetch lagu berikutnya bersamaan hanya merebut jaringan dari lagu ini.
        // Lagu berikutnya disiapkan oleh PlaybackService begitu lagu ini mulai berbunyi.
        StreamResolver.prefetch(items[start].id)
        // Lirik lagu ini dicari sejak ketukan (tanpa menunggu pemutar siap), dan lirik lagu berikutnya disiapkan.
        prefetchLyrics(items[start])
        items.getOrNull(start + 1)?.let { prefetchLyrics(it) }
        withController { c ->
            c.setMediaItems(items.map { it.toMediaItem() }, index.coerceIn(0, items.lastIndex), 0L)
            c.prepare()
            c.play()
        }
    }

    // ------------------------------------------------------------------ potongan lagu catatan

    private class NoteSnap(
        val items: List<androidx.media3.common.MediaItem>, val index: Int, val pos: Long, val playing: Boolean,
        val shuffle: Boolean, val repeat: Int, val source: String?
    )
    private var noteSnap: NoteSnap? = null
    private var noteDepth = 0

    /** Memutar lagu catatan teman dan mengingat apa yang sedang kita dengar, supaya bisa dilanjutkan setelahnya. */
    fun beginNoteClip(song: SearchItem, startMs: Long = 0L) = withController { c ->
        if (noteDepth == 0) {
            noteSnap = if (c.mediaItemCount > 0) NoteSnap(
                (0 until c.mediaItemCount).map { c.getMediaItemAt(it) }, c.currentMediaItemIndex,
                c.currentPosition.coerceAtLeast(0), c.isPlaying, c.shuffleModeEnabled, c.repeatMode, queueSourceId
            ) else null
        }
        noteDepth++
        if (c.currentMediaItem?.mediaId != song.id) {
            StreamResolver.prefetch(song.id)
            c.shuffleModeEnabled = false
            c.repeatMode = Player.REPEAT_MODE_OFF
            c.setMediaItems(listOf(song.toMediaItem()), 0, startMs)
            c.prepare()
        }
        c.play()
    }

    /** Selesai melihat catatan: kembalikan lagu / antrean / posisi kita seperti semula (atau hening bila tadinya tidak memutar). */
    fun endNoteClip(onDone: () -> Unit = {}) = withController { c ->
        noteDepth = (noteDepth - 1).coerceAtLeast(0)
        if (noteDepth > 0) { onDone(); return@withController }
        val s = noteSnap
        noteSnap = null
        if (s != null) {
            c.setMediaItems(s.items, s.index.coerceIn(0, s.items.lastIndex), s.pos)
            c.shuffleModeEnabled = s.shuffle
            c.repeatMode = s.repeat
            c.prepare()
            if (s.playing) c.play() else c.pause()
            queueSourceId = s.source
        } else {
            queueSourceId = null
            c.stop()
            c.clearMediaItems()
        }
        onDone()
    }

    /** Menghentikan pemutaran dan mengosongkan antrean (dipakai saat aplikasi dikunci dari jauh). */
    fun stop() = withController { c ->
        queueSourceId = null
        c.stop()
        c.clearMediaItems()
    }

    // ------------------------------------------------------------------ antrean

    /** Memutar [song] tepat setelah lagu yang sedang diputar. Kalau belum ada yang diputar, langsung diputar. */
    fun playNext(song: SearchItem) = withController { c ->
        queueSourceId = null
        if (c.mediaItemCount == 0) {
            c.setMediaItems(listOf(song.toMediaItem()), 0, 0L)
            c.prepare()
            c.play()
        } else {
            c.addMediaItem((c.currentMediaItemIndex + 1).coerceAtMost(c.mediaItemCount), song.toMediaItem())
        }
    }

    /** Menaruh [song] di akhir antrean. Kalau belum ada yang diputar, langsung diputar. */
    fun addToQueue(song: SearchItem) = withController { c ->
        queueSourceId = null
        if (c.mediaItemCount == 0) {
            c.setMediaItems(listOf(song.toMediaItem()), 0, 0L)
            c.prepare()
            c.play()
        } else {
            c.addMediaItem(song.toMediaItem())
        }
    }

    fun removeFromQueue(index: Int) = withController { c ->
        if (index !in 0 until c.mediaItemCount) return@withController
        queueSourceId = null
        c.removeMediaItem(index)
    }

    fun moveInQueue(from: Int, to: Int) = withController { c ->
        if (from !in 0 until c.mediaItemCount || to !in 0 until c.mediaItemCount || from == to) return@withController
        queueSourceId = null
        c.moveMediaItem(from, to)
    }

    /** Playlist sumber dihapus: antrean jalan terus apa adanya. */
    fun detachQueueSource() {
        queueSourceId = null
    }

    /**
     * Menyamakan antrean dengan isi terbaru playlist sumbernya TANPA memutus lagu yang sedang diputar:
     * lagu yang sedang jalan tetap jalan, lagu sebelum/sesudahnya diganti sesuai playlist.
     */
    fun syncWithPlaylist(songs: List<SearchItem>) = withController { c ->
        if (c.mediaItemCount == 0) return@withController
        val curId = c.currentMediaItem?.mediaId
        val pos = songs.indexOfFirst { it.id == curId }
        val currentIds = (0 until c.mediaItemCount).map { c.getMediaItemAt(it).mediaId }
        // Sudah sama persis: tidak perlu menyentuh pemutar.
        if (pos >= 0 && currentIds == songs.map { it.id }) return@withController
        val cur = c.currentMediaItemIndex
        val before = if (pos >= 0) songs.subList(0, pos) else emptyList()
        val after = if (pos >= 0) songs.subList(pos + 1, songs.size) else songs
        if (cur + 1 < c.mediaItemCount) c.removeMediaItems(cur + 1, c.mediaItemCount)
        if (cur > 0) c.removeMediaItems(0, cur)
        if (after.isNotEmpty()) c.addMediaItems(after.map { it.toMediaItem() })
        if (before.isNotEmpty()) c.addMediaItems(0, before.map { it.toMediaItem() })
    }

    fun clearHistory() {
        history.clear()
    }

    fun togglePlay() = withController { c ->
        if (c.isPlaying) {
            c.pause()
        } else {
            if (c.playbackState == Player.STATE_IDLE) c.prepare()
            c.play()
        }
    }

    fun next() = withController { it.seekToNextMediaItem() }

    fun previous() = withController { c ->
        if (c.currentPosition > 3000 || !c.hasPreviousMediaItem()) c.seekTo(0) else c.seekToPreviousMediaItem()
    }

    fun seekTo(ms: Long) = withController {
        it.seekTo(ms)
        ui = ui.copy(positionMs = ms)
    }

    fun toggleShuffle() = withController { it.shuffleModeEnabled = !it.shuffleModeEnabled }

    fun toggleRepeat() = withController {
        it.repeatMode = when (it.repeatMode) {
            Player.REPEAT_MODE_OFF -> Player.REPEAT_MODE_ALL
            Player.REPEAT_MODE_ALL -> Player.REPEAT_MODE_ONE
            else -> Player.REPEAT_MODE_OFF
        }
    }

    fun skipTo(index: Int) = withController {
        it.seekTo(index, 0L)
        it.play()
    }

    override fun onCleared() {
        controller?.removeListener(listener)
        controller?.release()
        controller = null
        super.onCleared()
    }
}
