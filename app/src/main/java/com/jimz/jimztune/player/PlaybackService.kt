package com.jimz.jimztune.player

import android.net.Uri
import android.util.Log
import androidx.annotation.OptIn
import androidx.media3.common.AudioAttributes
import androidx.media3.common.C
import androidx.media3.common.MediaItem
import androidx.media3.common.PlaybackException
import androidx.media3.common.Player
import androidx.media3.common.util.UnstableApi
import androidx.media3.datasource.DefaultDataSource
import androidx.media3.datasource.DefaultHttpDataSource
import androidx.media3.database.StandaloneDatabaseProvider
import androidx.media3.datasource.ResolvingDataSource
import androidx.media3.datasource.cache.CacheDataSource
import androidx.media3.datasource.cache.LeastRecentlyUsedCacheEvictor
import androidx.media3.datasource.cache.SimpleCache
import androidx.media3.exoplayer.DefaultLoadControl
import androidx.media3.exoplayer.DefaultRenderersFactory
import androidx.media3.exoplayer.ExoPlayer
import androidx.media3.exoplayer.audio.AudioSink
import androidx.media3.exoplayer.audio.DefaultAudioSink
import com.jimz.jimztune.player.fx.AudioFxController
import com.jimz.jimztune.player.fx.SoundFxProcessor
import androidx.media3.exoplayer.source.DefaultMediaSourceFactory
import androidx.media3.session.MediaSession
import androidx.media3.session.MediaSessionService
import com.google.common.util.concurrent.Futures
import com.google.common.util.concurrent.ListenableFuture
import com.jimz.jimztune.data.stream.StreamResolver
import android.content.BroadcastReceiver
import android.content.Context
import android.content.Intent
import android.content.IntentFilter
import android.net.ConnectivityManager
import android.net.Network
import android.os.Handler
import android.os.Looper
import android.os.SystemClock
import android.widget.Toast
import com.jimz.jimztune.util.UserError
import kotlinx.coroutines.CancellationException
import kotlinx.coroutines.TimeoutCancellationException
import kotlinx.coroutines.runBlocking
import kotlinx.coroutines.withTimeout
import java.io.File
import java.io.IOException
import com.jimz.jimztune.AppGraph

/**
 * Layanan pemutar di latar belakang: notifikasi media, kontrol di lockscreen,
 * dan pemutaran tetap jalan saat aplikasi ditutup.
 */
@OptIn(UnstableApi::class)
class PlaybackService : MediaSessionService() {
    private var session: MediaSession? = null
    private var audioCache: SimpleCache? = null
    private var retryId: String? = null
    private var retries = 0
    private var skipStreak = 0
    private var retryRunnable: Runnable? = null
    private val repaired = mutableSetOf<String>()

    private val handler = Handler(Looper.getMainLooper())
    private var stuckSince = 0L
    private var stuckFires = 0
    private var watchdogRunning = false
    private var networkCallback: ConnectivityManager.NetworkCallback? = null
    private var lastNetworkKick = 0L
    private var widgetDriver: com.jimz.jimztune.widget.LyricsWidgetDriver? = null
    private var fxController: AudioFxController? = null
    private var listenLogger: ListenLogger? = null
    private var island: com.jimz.jimztune.island.IslandController? = null
    private var sponsorBlock: SponsorBlock? = null

    private var btCallback: android.media.AudioDeviceCallback? = null
    private var lastPlayingAt = 0L

    /**
     * Lanjut memutar saat perangkat Bluetooth audio tersambung, kalau fitur aktif dan lagu baru saja dijeda.
     * Memakai AudioDeviceCallback supaya tidak perlu izin Bluetooth.
     */
    private fun registerBtResume() {
        val am = getSystemService(Context.AUDIO_SERVICE) as? android.media.AudioManager ?: return
        val cb = object : android.media.AudioDeviceCallback() {
            override fun onAudioDevicesAdded(added: Array<out android.media.AudioDeviceInfo>) {
                val bt = added.any {
                    it.type == android.media.AudioDeviceInfo.TYPE_BLUETOOTH_A2DP ||
                        it.type == android.media.AudioDeviceInfo.TYPE_BLUETOOTH_SCO ||
                        it.type == android.media.AudioDeviceInfo.TYPE_HEARING_AID ||
                        (android.os.Build.VERSION.SDK_INT >= 31 && it.type == android.media.AudioDeviceInfo.TYPE_BLE_HEADSET)
                }
                if (!bt || !com.jimz.jimztune.util.PrivacyPrefs.btResume(applicationContext)) return
                val p = session?.player ?: return
                if (p.mediaItemCount > 0 && !p.isPlaying && System.currentTimeMillis() - lastPlayingAt < 10 * 60_000L) p.play()
            }
        }
        am.registerAudioDeviceCallback(cb, handler)
        btCallback = cb
    }

    private var screenReceiver: BroadcastReceiver? = null

    override fun onCreate() {
        super.onCreate()
        AppGraph.init(applicationContext)
        val sr = com.jimz.jimztune.util.LockScreenLyrics.ScreenReceiver { session?.player?.isPlaying == true }
        screenReceiver = sr
        androidx.core.content.ContextCompat.registerReceiver(this, sr, IntentFilter(Intent.ACTION_SCREEN_ON), androidx.core.content.ContextCompat.RECEIVER_NOT_EXPORTED)

        val http = DefaultHttpDataSource.Factory()
            .setUserAgent("Mozilla/5.0 (Windows NT 10.0; rv:128.0) Gecko/20100101 Firefox/128.0")
            .setConnectTimeoutMs(10_000)
            .setReadTimeoutMs(20_000)
            .setAllowCrossProtocolRedirects(true)

        // Simpan audio yang sudah dimuat (maks 300 MB): ulang lagu, geser posisi, dan kembali ke lagu sebelumnya
        // langsung jalan tanpa mengunduh lagi.
        //
        // PENTING: cache berada DI DALAM resolver, dan kuncinya memuat format file (itag + ukuran, lihat [cacheKeyFor]).
        // Dulu kuncinya cuma ID lagu, padahal tiap sumber (NewPipe, iOS, Android, Music, Piped...) bisa memberi
        // file berbeda (webm/opus vs mp4/m4a). Potongan awal dari satu sumber lalu sisanya dari sumber lain
        // tergabung jadi satu file rusak -> macet di tengah lagu dengan ERROR_CODE_PARSING_CONTAINER_UNSUPPORTED.
        val cache = SimpleCache(
            File(cacheDir, "audio_v2"),
            LeastRecentlyUsedCacheEvictor(300L * 1024 * 1024),
            StandaloneDatabaseProvider(this)
        )
        audioCache = cache
        // Cache lama (kunci cuma ID lagu) bisa berisi potongan campuran: buang.
        // Di thread terpisah: menghapus folder besar di thread utama menahan service saat pertama kali dibuat.
        Thread {
            try {
                File(cacheDir, "audio").takeIf { it.exists() }?.deleteRecursively()
            } catch (_: Exception) {
            }
        }.apply { isDaemon = true }.start()
        val cachedFactory = CacheDataSource.Factory()
            .setCache(cache)
            .setUpstreamDataSourceFactory(
                ChunkedDataSource.Factory(DefaultDataSource.Factory(this, http))
            )
            .setFlags(CacheDataSource.FLAG_IGNORE_CACHE_ON_ERROR)

        val resolving = ResolvingDataSource.Factory(
            cachedFactory,
            ResolvingDataSource.Resolver { dataSpec ->
                val id = dataSpec.uri.lastPathSegment ?: dataSpec.key?.substringBefore('|')
                    ?: throw IOException("ID lagu tidak ditemukan")
                // Batas waktu wajib: tanpa ini, pencarian yang menggantung membuat pemutar macet "memuat" selamanya
                // (tombol putar di notifikasi berputar terus), terutama saat aplikasi di latar belakang.
                val t0 = SystemClock.elapsedRealtime()
                val url = try {
                    runBlocking { withTimeout(RESOLVE_TIMEOUT_MS) { StreamResolver.resolve(id) } }
                        .also { PlaybackDiagnostics.add("Alamat audio siap (${SystemClock.elapsedRealtime() - t0} ms)") }
                } catch (e: TimeoutCancellationException) {
                    throw IOException("Pencarian audio terlalu lama")
                } catch (e: CancellationException) {
                    throw IOException("Pencarian audio dibatalkan")
                }
                var spec = dataSpec.buildUpon()
                    .setUri(Uri.parse(url))
                    .setKey(cacheKeyFor(id, url))
                    .build()
                StreamResolver.userAgentFor(url)?.let { ua ->
                    spec = spec.withAdditionalHeaders(mapOf("User-Agent" to ua))
                }
                spec
            }
        )

        // Mulai memutar setelah 0,6 detik audio terisi (bawaan 2,5 detik), dan 2,5 detik setelah macet (bawaan 5 detik).
        val loadControl = DefaultLoadControl.Builder()
            .setBufferDurationsMs(25_000, 60_000, 350, 2_000)
            .setPrioritizeTimeOverSizeThresholds(true)
            .build()

        // Jalur audio dengan efek buatan sendiri (reverb, 8D, lo-fi). Tempo/pitch/skip silence ditangani bawaan ExoPlayer.
        val fxProcessor = SoundFxProcessor()
        val renderersFactory = object : DefaultRenderersFactory(this) {
            override fun buildAudioSink(
                context: Context,
                enableFloatOutput: Boolean,
                enableAudioTrackPlaybackParams: Boolean
            ): AudioSink = DefaultAudioSink.Builder(context)
                .setEnableFloatOutput(enableFloatOutput)
                .setEnableAudioTrackPlaybackParams(enableAudioTrackPlaybackParams)
                .setAudioProcessors(arrayOf(fxProcessor))
                .build()
        }

        val player = ExoPlayer.Builder(this, renderersFactory)
            .setLoadControl(loadControl)
            .setMediaSourceFactory(DefaultMediaSourceFactory(resolving))
            .setAudioAttributes(
                AudioAttributes.Builder()
                    .setUsage(C.USAGE_MEDIA)
                    .setContentType(C.AUDIO_CONTENT_TYPE_MUSIC)
                    .build(),
                true
            )
            .setHandleAudioBecomingNoisy(true)
            .setWakeMode(C.WAKE_MODE_NETWORK)
            .build()

        // Antrean diulang dari awal terus-menerus setelah lagu terakhir (bisa diubah lewat tombol ulangi).
        player.repeatMode = Player.REPEAT_MODE_ALL
        // Siapkan lagu berikutnya (cari URL + muat ±10 detik pertama) selagi lagu sekarang masih diputar,
        // jadi pindah lagu hampir instan, termasuk saat layar mati.
        player.preloadConfiguration = ExoPlayer.PreloadConfiguration(10_000_000L)
        SleepTimer.attach(player)
        fxController = AudioFxController(this, player, fxProcessor, resolving).also { it.start() }
        listenLogger = ListenLogger(this, player).also { it.start() }
        island = com.jimz.jimztune.island.IslandController(this, player).also { it.start() }
        SponsorPrefs.init(this)
        sponsorBlock = SponsorBlock(this, player).also { it.start() }

        player.trackSelectionParameters = player.trackSelectionParameters.buildUpon()
            .setTrackTypeDisabled(C.TRACK_TYPE_VIDEO, true)
            .build()

        player.addListener(object : Player.Listener {
            override fun onPlayerError(error: PlaybackException) {
                Log.e("Aoto", "Kesalahan pemutar", error)
                PlaybackDiagnostics.add("Pemutar error: ${error.errorCodeName}")
                val id = player.currentMediaItem?.mediaId ?: return
                val title = player.currentMediaItem?.mediaMetadata?.title?.toString() ?: id
                UserError.log("Pemutar", "$title: ${error.errorCodeName}")
                com.jimz.jimztune.util.FailedSongs.record(id, title, error.errorCodeName)
                StreamResolver.invalidate(id)
                // Error baca/parsing file sering berarti data di cache rusak: buang supaya percobaan ulang mengunduh bersih.
                if (error.errorCode in PARSING_ERRORS) purgeCache(id)
                // Lagu yang diputar dari unduhan lalu rusak: hapus file itu, putar online, unduh ulang di latar belakang.
                if (error.errorCode in LOCAL_FILE_ERRORS && AppGraph.downloads.localFile(id) != null && repaired.add(id)) {
                    PlaybackDiagnostics.add("Unduhan rusak, diputar online dan diunduh ulang")
                    AppGraph.downloads.repair(id)
                    retries = 0
                }
                // Ditolak server (403) di tengah lagu: langsung ke NewPipe, jangan coba klien langsung satu per satu.
                if (error.errorCode == PlaybackException.ERROR_CODE_IO_BAD_HTTP_STATUS) StreamResolver.skipDirectClients(id)
                if (retryId != id) {
                    retryId = id
                    retries = 0
                }
                // IO_UNSPECIFIED / koneksi putus / timeout: biasanya URL klien langsung sudah mati atau dibatasi
                // (throttle) atau potongan cache setengah jadi. Percobaan ulang ke-2 dst: buang cache lagu itu dan
                // pindah ke NewPipe, yang URL-nya jauh lebih jarang ditolak.
                if (error.errorCode in IO_SOFT_ERRORS) {
                    if (retries >= 1) {
                        StreamResolver.skipDirectClients(id)
                        purgeCache(id)
                        PlaybackDiagnostics.add("Error IO berulang: cache dibuang, pindah ke NewPipe")
                    }
                }
                if (retries < MAX_RETRIES) {
                    // Coba lagi dengan sumber audio berikutnya, dengan jeda yang makin lama (backoff).
                    val wait = BACKOFF_MS[retries.coerceAtMost(BACKOFF_MS.lastIndex)]
                    retries++
                    scheduleRetry(player, id, wait)
                } else if (player.hasNextMediaItem() && player.mediaItemCount > 1) {
                    retries = 0
                    skipToNext(player, "$title: ${error.errorCodeName}")
                } else {
                    // Satu-satunya lagu di antrean dan semua sumber gagal: berhenti, jangan berputar selamanya.
                    player.pause()
                    notice("Lagu belum bisa diputar. Coba lagi nanti.", "$title: ${error.errorCodeName}")
                }
            }

            // Cari URL lagu berikutnya lebih dulu, supaya pindah lagu (juga saat layar mati) langsung jalan.
            override fun onMediaItemTransition(mediaItem: MediaItem?, reason: Int) {
                cancelRetry()
                stuckSince = 0L
                stuckFires = 0
                prefetchAhead(player)
                if (reason == Player.MEDIA_ITEM_TRANSITION_REASON_AUTO ||
                    reason == Player.MEDIA_ITEM_TRANSITION_REASON_REPEAT
                ) SleepTimer.onTrackFinished()
            }

            override fun onPlaybackStateChanged(playbackState: Int) {
                if (playbackState == Player.STATE_READY) prefetchAhead(player)
                startWatchdog()
            }

            override fun onPlayWhenReadyChanged(playWhenReady: Boolean, reason: Int) {
                if (playWhenReady) startWatchdog()
            }

            override fun onIsPlayingChanged(isPlaying: Boolean) {
                lastPlayingAt = System.currentTimeMillis()
                if (isPlaying) {
                    retries = 0
                    skipStreak = 0
                    stuckSince = 0L
                    stuckFires = 0
                    PlaybackDiagnostics.finish()
                }
            }
        })

        registerBtResume()
        setMediaNotificationProvider(
            androidx.media3.session.DefaultMediaNotificationProvider(this).also { it.setSmallIcon(com.jimz.jimztune.R.drawable.ic_stat_aoto) }
        )
        session = MediaSession.Builder(this, player)
            .setCallback(object : MediaSession.Callback {
                /**
                 * KEAMANAN: layanan ini harus bisa dihubungi sistem (notifikasi, Bluetooth, layar kunci), tapi aplikasi
                 * sembarangan tidak perlu boleh mengendalikan pemutar atau membaca lagu yang sedang diputar.
                 * Yang diterima: sistem/terpercaya, aplikasi ini sendiri, dan beberapa pengendali media yang dikenal.
                 */
                override fun onConnect(
                    mediaSession: MediaSession,
                    controller: MediaSession.ControllerInfo
                ): MediaSession.ConnectionResult {
                    val pkg = controller.packageName
                    val ok = controller.isTrusted || pkg == packageName || pkg in TRUSTED_CONTROLLERS
                    return if (ok) MediaSession.ConnectionResult.AcceptedResultBuilder(mediaSession).build()
                    else MediaSession.ConnectionResult.reject()
                }

                // Pastikan setiap item selalu punya URI dan kunci yang dikenali resolver.
                override fun onAddMediaItems(
                    mediaSession: MediaSession,
                    controller: MediaSession.ControllerInfo,
                    mediaItems: MutableList<MediaItem>
                ): ListenableFuture<MutableList<MediaItem>> {
                    val updated = mediaItems.map {
                        it.buildUpon()
                            .setUri("jimztune://song/${it.mediaId}")
                            .setCustomCacheKey(it.mediaId)
                            .build()
                    }.toMutableList()
                    return Futures.immediateFuture(updated)
                }
            })
            .build()
        registerNetworkCallback()
        // Widget lirik di layar utama: berjalan di sini supaya tetap hidup selama musik diputar di latar belakang.
        widgetDriver = com.jimz.jimztune.widget.LyricsWidgetDriver(applicationContext, player).also { it.start() }
    }

    /**
     * Pengaman: kalau pemutar menunggu (buffering) lebih dari [STUCK_MS] padahal seharusnya berbunyi, buang alamat audio
     * yang bermasalah dan coba lagi dari awal. Setelah dua kali gagal, lompat ke lagu berikutnya.
     * Berjalan hanya selama pemutar diminta memutar, jadi tidak menghabiskan baterai saat diam.
     */
    private fun startWatchdog() {
        if (watchdogRunning) return
        watchdogRunning = true
        handler.postDelayed(watchdog, 2_000L)
    }

    private val watchdog = object : Runnable {
        override fun run() {
            val player = session?.player
            if (player == null || !player.playWhenReady ||
                player.playbackState == Player.STATE_IDLE || player.playbackState == Player.STATE_ENDED
            ) {
                watchdogRunning = false
                stuckSince = 0L
                return
            }
            if (player.playbackState == Player.STATE_BUFFERING) {
                val now = SystemClock.elapsedRealtime()
                if (stuckSince == 0L) stuckSince = now
                if (now - stuckSince >= STUCK_MS) {
                    stuckSince = 0L
                    recoverStuck(player)
                }
            } else {
                stuckSince = 0L
            }
            handler.postDelayed(this, 2_000L)
        }
    }

    private fun recoverStuck(player: Player) {
        val id = player.currentMediaItem?.mediaId ?: return
        stuckFires++
        PlaybackDiagnostics.add("Macet memuat, percobaan ulang $stuckFires")
        StreamResolver.cancelInflight(id)
        StreamResolver.invalidate(id)
        if (stuckFires <= 2 || !player.hasNextMediaItem() || player.mediaItemCount < 2) {
            player.prepare()
            player.play()
        } else {
            stuckFires = 0
            skipToNext(player, "macet memuat")
        }
    }

    /** Percobaan ulang setelah [waitMs]; dibatalkan kalau lagunya sudah berganti. */
    private fun scheduleRetry(player: Player, id: String, waitMs: Long) {
        cancelRetry()
        val r = Runnable {
            retryRunnable = null
            if (player.currentMediaItem?.mediaId == id) {
                player.prepare()
                player.play()
            }
        }
        retryRunnable = r
        if (waitMs <= 0L) r.run() else handler.postDelayed(r, waitMs)
    }

    private fun cancelRetry() {
        retryRunnable?.let { handler.removeCallbacks(it) }
        retryRunnable = null
    }

    /**
     * Melewati lagu yang gagal dan lanjut ke berikutnya. Kalau beberapa lagu berturut-turut gagal
     * (biasanya karena internet mati), berhenti saja supaya antrean tidak berputar terus.
     */
    private fun skipToNext(player: Player, detail: String) {
        skipStreak++
        if (skipStreak > MAX_SKIPS_IN_ROW) {
            skipStreak = 0
            player.pause()
            notice("Beberapa lagu gagal diputar. Cek koneksi internet lalu coba lagi.", detail)
            return
        }
        notice("Lagu dilewati karena gagal diputar.", detail)
        player.seekToNextMediaItem()
        player.prepare()
        player.play()
    }

    /** Pesan umum untuk user (owner juga melihat rincian). Rincian selalu masuk log. */
    private fun notice(generic: String, detail: String) {
        UserError.log("Pemutar", detail)
        handler.post {
            try {
                Toast.makeText(applicationContext, UserError.text(generic, detail), Toast.LENGTH_SHORT).show()
            } catch (_: Exception) {
            }
        }
    }

    /** Begitu internet kembali (mis. sinyal sempat hilang saat layar mati), langsung lanjutkan lagu yang tertahan. */
    private fun registerNetworkCallback() {
        try {
            val cm = getSystemService(Context.CONNECTIVITY_SERVICE) as ConnectivityManager
            val callback = object : ConnectivityManager.NetworkCallback() {
                override fun onAvailable(network: Network) {
                    handler.post {
                        val player = session?.player ?: return@post
                        val now = SystemClock.elapsedRealtime()
                        if (now - lastNetworkKick < 4_000L) return@post
                        val waiting = player.playWhenReady &&
                            (player.playbackState == Player.STATE_BUFFERING ||
                                (player.playbackState == Player.STATE_IDLE && player.playerError != null))
                        if (waiting && player.mediaItemCount > 0) {
                            lastNetworkKick = now
                            player.currentMediaItem?.mediaId?.let {
                                StreamResolver.cancelInflight(it)
                                StreamResolver.invalidate(it)
                            }
                            player.prepare()
                            player.play()
                        }
                    }
                }
            }
            cm.registerDefaultNetworkCallback(callback)
            networkCallback = callback
        } catch (_: Exception) {
        }
    }

    /** Membuang semua cache lagu ini (semua format) supaya diunduh ulang dari nol. */
    private fun purgeCache(id: String) {
        val c = audioCache ?: return
        try {
            c.keys.filter { it == id || it.startsWith("$id|") }.forEach { c.removeResource(it) }
            PlaybackDiagnostics.add("Cache lagu dibersihkan")
        } catch (_: Exception) {
        }
    }

    private fun prefetchAhead(player: Player) {
        val count = player.mediaItemCount
        if (count == 0) return
        val next = player.nextMediaItemIndex
        if (next in 0 until count) StreamResolver.prefetch(player.getMediaItemAt(next).mediaId)
        val after = player.currentMediaItemIndex + 2
        if (after in 0 until count) StreamResolver.prefetch(player.getMediaItemAt(after).mediaId)
    }

    private companion object {
        val TRUSTED_CONTROLLERS = setOf(
            "com.android.systemui", "com.android.bluetooth", "com.google.android.projection.gearhead",
            "com.google.android.googlequicksearchbox", "com.google.android.wearable.app",
            "com.google.android.apps.wear.companion", "com.google.android.gms", "android"
        )
        val IO_SOFT_ERRORS = setOf(
            PlaybackException.ERROR_CODE_IO_UNSPECIFIED,
            PlaybackException.ERROR_CODE_IO_NETWORK_CONNECTION_FAILED,
            PlaybackException.ERROR_CODE_IO_NETWORK_CONNECTION_TIMEOUT,
            PlaybackException.ERROR_CODE_IO_BAD_HTTP_STATUS,
            PlaybackException.ERROR_CODE_IO_READ_POSITION_OUT_OF_RANGE
        )
        val PARSING_ERRORS = setOf(
            PlaybackException.ERROR_CODE_PARSING_CONTAINER_MALFORMED,
            PlaybackException.ERROR_CODE_PARSING_MANIFEST_MALFORMED,
            PlaybackException.ERROR_CODE_PARSING_CONTAINER_UNSUPPORTED,
            PlaybackException.ERROR_CODE_PARSING_MANIFEST_UNSUPPORTED,
            PlaybackException.ERROR_CODE_DECODING_FAILED
        )

        /**
         * Kunci cache = ID lagu + format file (itag) + ukuran (clen). Dua sumber yang memberi file identik
         * (itag & ukuran sama) berbagi cache; sumber yang memberi format lain dapat cache sendiri, tidak tercampur.
         */
        fun cacheKeyFor(id: String, url: String): String {
            val uri = Uri.parse(url)
            if (uri.scheme == "file") return "$id|local"
            val itag = uri.getQueryParameter("itag") ?: "x"
            val clen = uri.getQueryParameter("clen") ?: "x"
            val host = if (itag == "x" && clen == "x") "|" + (uri.host ?: "") else ""
            return "$id|$itag|$clen$host"
        }

        /** Berapa kali mencoba ulang satu lagu (tiap percobaan memakai sumber audio berbeda). */
        const val MAX_RETRIES = 5
        const val MAX_SKIPS_IN_ROW = 5

        /** Jeda sebelum percobaan ulang ke-1..4. Pertama langsung (sumber sudah dirotasi), lalu makin lama. */
        val BACKOFF_MS = longArrayOf(0L, 800L, 2_000L, 4_000L, 7_000L)

        /** Error yang berarti file unduhan lokal rusak atau hilang. */
        val LOCAL_FILE_ERRORS = setOf(
            PlaybackException.ERROR_CODE_PARSING_CONTAINER_MALFORMED,
            PlaybackException.ERROR_CODE_PARSING_CONTAINER_UNSUPPORTED,
            PlaybackException.ERROR_CODE_DECODING_FAILED,
            PlaybackException.ERROR_CODE_IO_FILE_NOT_FOUND,
            PlaybackException.ERROR_CODE_IO_READ_POSITION_OUT_OF_RANGE
        )

        /** Batas pencarian URL audio untuk satu lagu (grup 1 maks 7 dtk + grup 2 maks 8 dtk). */
        const val RESOLVE_TIMEOUT_MS = 20_000L

        /** Lama menunggu buffering sebelum dianggap macet. */
        const val STUCK_MS = 14_000L
    }

    override fun onGetSession(controllerInfo: MediaSession.ControllerInfo): MediaSession? = session

    override fun onTaskRemoved(rootIntent: android.content.Intent?) {
        val player = session?.player
        if (player == null || !player.playWhenReady || player.mediaItemCount == 0) stopSelf()
    }

    override fun onDestroy() {
        screenReceiver?.let { try { unregisterReceiver(it) } catch (_: Exception) { } }
        screenReceiver = null
        btCallback?.let { (getSystemService(Context.AUDIO_SERVICE) as? android.media.AudioManager)?.unregisterAudioDeviceCallback(it) }
        btCallback = null
        cancelRetry()
        fxController?.release()
        fxController = null
        listenLogger?.release()
        listenLogger = null
        island?.release()
        island = null
        sponsorBlock?.release()
        sponsorBlock = null
        handler.removeCallbacksAndMessages(null)
        networkCallback?.let {
            try {
                (getSystemService(Context.CONNECTIVITY_SERVICE) as ConnectivityManager).unregisterNetworkCallback(it)
            } catch (_: Exception) {
            }
        }
        networkCallback = null
        widgetDriver?.stop()
        widgetDriver = null
        SleepTimer.detach()
        session?.run {
            player.release()
            release()
        }
        session = null
        audioCache?.release()
        audioCache = null
        super.onDestroy()
    }
}
