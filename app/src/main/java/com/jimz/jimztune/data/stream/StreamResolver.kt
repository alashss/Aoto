package com.jimz.jimztune.data.stream

import com.jimz.jimztune.player.PlaybackDiagnostics
import kotlinx.coroutines.CancellationException
import kotlinx.coroutines.CoroutineScope
import kotlinx.coroutines.CoroutineStart
import kotlinx.coroutines.Deferred
import kotlinx.coroutines.async
import kotlinx.coroutines.delay
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.SupervisorJob
import kotlinx.coroutines.channels.Channel
import kotlinx.coroutines.launch
import kotlinx.coroutines.withTimeoutOrNull
import java.io.IOException
import java.util.Collections
import java.util.concurrent.ConcurrentHashMap
import java.util.concurrent.atomic.AtomicLong
import com.jimz.jimztune.AppGraph
import com.jimz.jimztune.data.search.Http

/**
 * Mencari URL audio. Sumber dalam satu kelompok dicoba bersamaan (siapa cepat dia menang),
 * kelompok berikutnya baru dipakai kalau kelompok sebelumnya gagal total. Hasilnya tidak pernah menggantung:
 * setiap kelompok punya batas waktu.
 */
object StreamResolver {
    private class Cached(val url: String, val validUntil: Long)

    private val scope = CoroutineScope(SupervisorJob() + Dispatchers.IO)
    private val cache = ConcurrentHashMap<String, Cached>()
    private val lastProvider = ConcurrentHashMap<String, String>()
    private val failed = ConcurrentHashMap<String, MutableSet<String>>()
    private const val TTL_MS = 3 * 60 * 60_000L

    /** Pencarian yang sedang berjalan per lagu: dua permintaan untuk lagu yang sama berbagi satu pencarian. */
    private val inflight = ConcurrentHashMap<String, Deferred<String>>()

    /** Kapan sumber cepat (klien YouTube langsung) terakhir berhasil. NewPipe yang berat baru dinyalakan belakangan. */
    private val lastFastWin = AtomicLong(0L)
    private val expireParam = Regex("[?&]expire=(\\d+)")

    /**
     * Sumber langsung yang baru-baru ini ditolak server (403), nama -> sampai kapan dianggap lemah. Sumber lemah tidak dibuang
     * (bisa pulih), hanya diberi giliran belakangan; dan kalau SEMUA sumber langsung lemah, NewPipe langsung jalan tanpa menunggu 1,5 detik.
     */
    private val weakUntil = ConcurrentHashMap<String, Long>()
    private fun isWeak(name: String) = (weakUntil[name] ?: 0L) > System.currentTimeMillis()

    @Volatile private var lastWarm = 0L

    /** NewPipe gagal baru-baru ini: sampai kapan cadangan tidak perlu menunggu NewPipe. */
    @Volatile private var newPipeDownUntil = 0L
    private fun isNewPipeDown() = newPipeDownUntil > System.currentTimeMillis()
    private const val NEWPIPE_HEAD_START_MS = 2_500L

    /**
     * Menghangatkan sambungan (DNS + TLS) ke YouTube dan menyiapkan NewPipe di latar belakang, supaya pencarian lagu PERTAMA
     * tidak membayar biaya sambungan awal. Aman dipanggil berulang (dibatasi sekali per 4 menit).
     */
    fun warmUp() {
        val now = System.currentTimeMillis()
        if (now - lastWarm < 4 * 60_000L) return
        lastWarm = now
        scope.launch {
            launch { try { NewPipeStreamProvider.ensureInit() } catch (_: Throwable) {} }
            listOf(
                "https://www.youtube.com/generate_204",
                "https://music.youtube.com/generate_204",
                "https://youtubei.googleapis.com/generate_204"
            ).forEach { u ->
                launch { Http.preconnect(u) }
            }
        }
    }

    // Kelompok 1: langsung dari YouTube lewat beberapa klien (semua dicoba bersamaan, tercepat menang).
    // Kelompok 2: lewat instance publik, baru dipakai kalau kelompok 1 gagal total.
    private val groups: List<Pair<List<StreamProvider>, Long>> = listOf(
        listOf<StreamProvider>(
            NewPipeStreamProvider(),
            InnerTubeStreamProvider(),
            InnerTubeClientStreamProvider(InnerTubeClientStreamProvider.Client.IOS),
            InnerTubeClientStreamProvider(InnerTubeClientStreamProvider.Client.MUSIC),
            InnerTubeClientStreamProvider(InnerTubeClientStreamProvider.Client.ANDROID)
        ) to 7_000L,
        listOf<StreamProvider>(PipedStreamProvider(), InvidiousStreamProvider()) to 8_000L
    )

    val providerCount: Int get() = groups.sumOf { it.first.size }

    /** Semua sumber audio, dipakai oleh uji pemutaran di Setelan. */
    val allProviders: List<StreamProvider> get() = groups.flatMap { it.first }

    /** Dipanggil saat kualitas berubah, supaya URL lama tidak terpakai. */
    fun clearCache() {
        cache.clear()
        lastProvider.clear()
        failed.clear()
    }

    suspend fun resolve(videoId: String): String {
        // Lagu yang sudah diunduh diputar dari file lokal (bisa offline).
        AppGraph.downloads.localFile(videoId)?.let {
            PlaybackDiagnostics.add("Memutar dari unduhan (offline)")
            return android.net.Uri.fromFile(it).toString()
        }
        cached(videoId)?.let {
            PlaybackDiagnostics.add("Memakai URL tersimpan (${lastProvider[videoId] ?: "cache"})")
            return it
        }

        // Satu pencarian per lagu: pemutar sering membuka sumber data beberapa kali, dan prefetch bisa sedang berjalan.
        inflight[videoId]?.let { return it.await() }
        val job = scope.async(start = CoroutineStart.LAZY) { search(videoId) }
        val existing = inflight.putIfAbsent(videoId, job)
        if (existing != null) {
            job.cancel()
            return existing.await()
        }
        job.start()
        try {
            return job.await()
        } finally {
            inflight.remove(videoId, job)
        }
    }

    /**
     * Mencari URL di latar belakang sebelum lagunya dimainkan (lagu berikutnya di antrean), supaya saat tiba
     * gilirannya pemutaran langsung mulai tanpa menunggu pencarian.
     */
    fun prefetch(videoId: String) {
        if (videoId.isBlank() || cached(videoId) != null || inflight.containsKey(videoId)) return
        scope.launch {
            try {
                if (AppGraph.downloads.localFile(videoId) == null) resolve(videoId)
            } catch (e: CancellationException) {
                throw e
            } catch (_: Throwable) {
            }
        }
    }

    private fun cached(videoId: String): String? =
        cache[videoId]?.takeIf { System.currentTimeMillis() < it.validUntil }?.url

    private suspend fun search(videoId: String): String {
        PlaybackDiagnostics.add("Mencari sumber audio…")
        val skip = failed[videoId].orEmpty().toSet()
        val errors = mutableListOf<String>()

        for ((group, timeout) in groups) {
            val candidates = group.filter { it.name !in skip }.ifEmpty { group }
            val winner = race(candidates, videoId, errors, timeout)
            if (winner != null) {
                val now = System.currentTimeMillis()
                // URL YouTube memuat waktu kedaluwarsanya sendiri (parameter expire); pakai itu dengan sisa 10 menit.
                val expire = expireParam.find(winner.second)?.groupValues?.get(1)?.toLongOrNull()?.times(1000)
                val until = if (expire != null) minOf(expire - 10 * 60_000L, now + TTL_MS) else now + TTL_MS
                cache[videoId] = Cached(winner.second, until)
                lastProvider[videoId] = winner.first
                weakUntil.remove(winner.first)
                if (winner.first == "NewPipe") newPipeDownUntil = 0L
                if (winner.first != "NewPipe" && group === groups.first().first) lastFastWin.set(now)
                PlaybackDiagnostics.add("Sumber audio: ${winner.first}")
                return winner.second
            }
        }
        throw IOException("Semua sumber audio gagal: " + errors.joinToString("; "))
    }

    private suspend fun race(
        group: List<StreamProvider>,
        videoId: String,
        errors: MutableList<String>,
        timeoutMs: Long
    ): Pair<String, String>? {
        val channel = Channel<Pair<StreamProvider, Result<String>>>(group.size)
        val jobs = group.map { p ->
            scope.launch {
                // NewPipe adalah sumber UTAMA: langsung jalan tanpa menunggu. Klien YouTube langsung menjadi cadangan yang baru
                // ikut berlomba setelah NewPipe diberi waktu [NEWPIPE_HEAD_START_MS]. Kalau NewPipe baru saja gagal, cadangan
                // langsung jalan tanpa menunggu (supaya tidak ada jeda sia-sia). Klien yang baru ditolak server (lemah) menunggu lebih lama.
                if (p.name != "NewPipe" && group.size > 1) {
                    val wait = if (isNewPipeDown()) 0L else NEWPIPE_HEAD_START_MS
                    val extra = if (isWeak(p.name)) 600L else 0L
                    if (wait + extra > 0L) delay(wait + extra)
                }
                val result = try {
                    val url = p.audioUrl(videoId)
                    // Jangan terima URL yang ditolak server (403): dulu ini baru ketahuan setelah pemutar gagal,
                    // lalu mengulang dari awal dan memakan 5–15 detik.
                    val group2 = p.name == "Piped" || p.name == "Invidious"
                    if (!group2 && !Http.probe(url, userAgentFor(url))) {
                        if (p.name != "NewPipe") weakUntil[p.name] = System.currentTimeMillis() + 20 * 60_000L
                        throw IOException("URL ditolak server (403)")
                    }
                    Result.success(url)
                } catch (e: CancellationException) {
                    throw e
                } catch (e: Throwable) {
                    Result.failure(e)
                }
                channel.trySend(p to result)
            }
        }

        val deadline = System.currentTimeMillis() + timeoutMs
        var pending = group.size
        var winner: Pair<String, String>? = null
        while (pending > 0 && winner == null) {
            val remaining = deadline - System.currentTimeMillis()
            if (remaining <= 0) break
            val item = withTimeoutOrNull(remaining) { channel.receive() } ?: break
            pending--
            val (provider, result) = item
            val url = result.getOrNull()
            if (url != null) {
                winner = provider.name to url
            } else {
                val e = result.exceptionOrNull()
                val reason = "${e?.javaClass?.simpleName}: ${e?.message}".take(110)
                errors += "${provider.name}: $reason"
                if (provider.name == "NewPipe") newPipeDownUntil = System.currentTimeMillis() + 3 * 60_000L
                PlaybackDiagnostics.add("${provider.name} gagal — $reason")
            }
        }
        if (winner == null && pending > 0) {
            val note = "$pending sumber tidak menjawab dalam ${timeoutMs / 1000} detik"
            errors += note
            PlaybackDiagnostics.add(note)
        }
        jobs.forEach { it.cancel() }
        return winner
    }

    /** Membatalkan pencarian yang sedang berjalan untuk lagu ini (dipakai saat pemutar macet menunggu). */
    fun cancelInflight(videoId: String) {
        inflight.remove(videoId)?.cancel()
    }

    /** Dipanggil saat URL gagal diputar: buang dari cache dan tandai sumbernya agar dilewati. */
    fun invalidate(videoId: String) {
        cache.remove(videoId)
        lastProvider.remove(videoId)?.let {
            PlaybackDiagnostics.add("URL dari $it ditolak, mencoba sumber lain")
            failed.getOrPut(videoId) { Collections.synchronizedSet(mutableSetOf()) }.add(it)
        }
    }

    /**
     * Dipanggil saat sumber langsung YouTube (iOS/Android/Music/VR) ditolak server (HTTP 403) di tengah lagu.
     * Klien-klien itu memakai mekanisme yang sama, jadi kalau satu ditolak yang lain hampir pasti sama:
     * lewati semuanya dan langsung pakai NewPipe (tanpa menunggu), supaya jeda di tengah lagu sesingkat mungkin.
     */
    fun skipDirectClients(videoId: String) {
        val set = failed.getOrPut(videoId) { Collections.synchronizedSet(mutableSetOf()) }
        groups.first().first.filter { it.name != "NewPipe" }.forEach {
            set.add(it.name)
            weakUntil[it.name] = System.currentTimeMillis() + 10 * 60_000L
        }
    }

    /** User-Agent yang harus dipakai saat mengunduh URL tertentu (sesuai klien YouTube yang membuatnya). */
    fun userAgentFor(url: String): String? = when {
        url.contains("c=ANDROID_VR") -> InnerTubeStreamProvider.USER_AGENT
        url.contains("c=ANDROID_MUSIC") -> InnerTubeClientStreamProvider.Client.MUSIC.userAgent
        url.contains("c=ANDROID") -> InnerTubeClientStreamProvider.Client.ANDROID.userAgent
        url.contains("c=IOS") -> InnerTubeClientStreamProvider.Client.IOS.userAgent
        else -> null
    }
}
