package com.jimz.jimztune

import android.content.Context
import com.jimz.jimztune.data.detail.DetailRepository
import com.jimz.jimztune.data.download.Downloader
import com.jimz.jimztune.data.activity.ActivityTracker
import com.jimz.jimztune.data.auth.AuthManager
import com.jimz.jimztune.data.owner.AccountStore
import com.jimz.jimztune.data.realtime.RealtimeHub
import com.jimz.jimztune.data.cloud.CloudSync
import com.jimz.jimztune.data.history.HistoryStore
import com.jimz.jimztune.data.lyrics.LyricsPrefs
import com.jimz.jimztune.data.history.SearchHistoryStore
import com.jimz.jimztune.data.profile.ProfileStore
import com.jimz.jimztune.data.library.LibraryStore
import com.jimz.jimztune.data.remote.RemoteConfigRepository
import com.jimz.jimztune.data.search.SearchRepository
import com.jimz.jimztune.data.settings.SettingsStore

/**
 * Objek bersama untuk seluruh aplikasi (UI dan PlaybackService berjalan di proses yang sama).
 * WAJIB memanggil [init] lebih dulu: dilakukan di MainActivity.onCreate dan PlaybackService.onCreate.
 */
object AppGraph {
    private lateinit var app: Context

    fun init(context: Context) {
        if (!::app.isInitialized) app = context.applicationContext
        com.jimz.jimztune.player.fx.AudioFx.init(app)
        com.jimz.jimztune.ui.theme.AppTheme.init(app)
        com.jimz.jimztune.data.stats.PlayLog.init(app)
        com.jimz.jimztune.data.stats.Streak.init(app)
        com.jimz.jimztune.island.IslandPrefs.init(app)
        com.jimz.jimztune.player.SponsorPrefs.init(app)
        com.jimz.jimztune.util.ChatNotifier.startLoop()
    }

    val appContext: Context get() = app

    /** True selama layar aplikasi terlihat. Diatur MainActivity (onStart / onStop). */
    @Volatile var appVisible: Boolean = false
        private set

    /** True selama musik sedang diputar. Diatur PlayerViewModel. */
    @Volatile var playing: Boolean = false
        private set

    /** Dipanggil (di thread utama) setiap aplikasi terlihat/tersembunyi. Dipakai Dynamic Island. */
    @Volatile var onVisibilityChanged: ((Boolean) -> Unit)? = null

    fun setAppVisible(value: Boolean) {
        appVisible = value
        if (value) com.jimz.jimztune.data.stream.StreamResolver.warmUp()
        onVisibilityChanged?.invoke(value)
        updateBackgroundWork()
    }

    fun setPlaying(value: Boolean) {
        if (playing == value) return
        playing = value
        listen.onPlayingChanged(value)
        updateBackgroundWork()
    }

    /**
     * Koneksi realtime + detak "online" hanya berjalan selama aplikasi terlihat ATAU musik sedang diputar
     * (layanan pemutar menjaga proses tetap hidup), jadi tidak menguras baterai saat aplikasi benar-benar menganggur.
     */
    private fun updateBackgroundWork() {
        if (appVisible || playing) {
            realtime.start()
            activity.start()
        } else {
            realtime.stop()
            activity.stop()
        }
    }

    val searchRepository: SearchRepository by lazy { SearchRepository() }
    val searchHistory: SearchHistoryStore by lazy { SearchHistoryStore(app) }
    val detail: DetailRepository by lazy { DetailRepository() }
    val settings: SettingsStore by lazy { SettingsStore(app) }
    val library: LibraryStore by lazy { LibraryStore(app) }
    val downloads: Downloader by lazy { Downloader(app) }
    val remote: RemoteConfigRepository by lazy { RemoteConfigRepository(app) }
    val history: HistoryStore by lazy { HistoryStore(app) }
    val homeCache: com.jimz.jimztune.data.home.HomeCache by lazy { com.jimz.jimztune.data.home.HomeCache(app) }
    val lyricsPrefs: LyricsPrefs by lazy { LyricsPrefs(app) }
    /** Satu pencari lirik untuk pemutar dan widget lirik, supaya hasilnya berbagi simpanan dan tidak diunduh dobel. */
    val lyricsRepo: com.jimz.jimztune.data.lyrics.LyricsRepository by lazy { com.jimz.jimztune.data.lyrics.LyricsRepository(app) }
    val auth: AuthManager by lazy { AuthManager(app) }
    val profile: ProfileStore by lazy { ProfileStore(app) }
    val cloud: CloudSync by lazy { CloudSync(app) }
    val account: AccountStore by lazy { AccountStore(app) }
    val activity: ActivityTracker by lazy { ActivityTracker() }
    val realtime: RealtimeHub by lazy { RealtimeHub() }
    val listen: com.jimz.jimztune.data.stats.ListenTracker by lazy { com.jimz.jimztune.data.stats.ListenTracker(app) }
    val ranking: com.jimz.jimztune.data.stats.RankingStore by lazy { com.jimz.jimztune.data.stats.RankingStore() }
}
