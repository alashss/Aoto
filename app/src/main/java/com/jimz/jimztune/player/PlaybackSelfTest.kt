package com.jimz.jimztune.player

import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.setValue
import com.jimz.jimztune.AppGraph
import com.jimz.jimztune.data.model.SearchItem
import com.jimz.jimztune.data.model.SearchType
import com.jimz.jimztune.data.stream.StreamResolver
import kotlinx.coroutines.CancellationException
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.delay
import kotlinx.coroutines.withContext
import kotlinx.coroutines.withTimeoutOrNull
import okhttp3.OkHttpClient
import okhttp3.Request
import java.util.concurrent.TimeUnit

/**
 * Uji pemutaran lagu langsung di HP: pencarian -> tiap sumber audio -> URL benar-benar bisa diunduh
 * -> ExoPlayer (lewat PlaybackService) benar-benar bunyi dan posisinya bergerak.
 */
object PlaybackSelfTest {
    data class Step(val name: String, val ok: Boolean?, val detail: String)

    var steps by mutableStateOf<List<Step>>(emptyList())
        private set
    var running by mutableStateOf(false)
        private set

    private val http = OkHttpClient.Builder()
        .connectTimeout(8, TimeUnit.SECONDS)
        .readTimeout(10, TimeUnit.SECONDS)
        .build()

    private fun add(name: String, ok: Boolean?, detail: String) {
        steps = steps + Step(name, ok, detail)
    }

    suspend fun run(player: PlayerViewModel) {
        if (running) return
        running = true
        steps = emptyList()
        try {
            val song = searchStep() ?: return
            val url = providerSteps(song) ?: return
            if (!downloadStep(url)) return
            playerStep(player, song)
        } catch (e: CancellationException) {
            throw e
        } catch (e: Throwable) {
            add("Uji berhenti", false, "${e.javaClass.simpleName}: ${e.message}".take(160))
        } finally {
            running = false
        }
    }

    private suspend fun searchStep(): SearchItem? {
        add("1. Cari lagu", null, "Mencari…")
        return try {
            val result = AppGraph.searchRepository.search("lofi hip hop chill", SearchType.SONG)
            val song = result.items.firstOrNull()
            steps = steps.dropLast(1)
            if (song == null) {
                add("1. Cari lagu", false, "Hasil kosong dari ${result.source}")
                null
            } else {
                add("1. Cari lagu", true, "${song.title.take(40)} (via ${result.source})")
                song
            }
        } catch (e: CancellationException) {
            throw e
        } catch (e: Exception) {
            steps = steps.dropLast(1)
            add("1. Cari lagu", false, (e.message ?: e.javaClass.simpleName).take(160))
            null
        }
    }

    /** Menguji tiap sumber audio satu per satu. Mengembalikan URL pertama yang berhasil. */
    private suspend fun providerSteps(song: SearchItem): String? {
        var firstUrl: String? = null
        for (provider in StreamResolver.allProviders) {
            val label = "2. Sumber ${provider.name}"
            add(label, null, "Mengecek…")
            val started = System.currentTimeMillis()
            val outcome: Result<String?> = try {
                Result.success(withTimeoutOrNull(16_000) { provider.audioUrl(song.id) })
            } catch (e: CancellationException) {
                throw e
            } catch (e: Throwable) {
                Result.failure(e)
            }
            val took = (System.currentTimeMillis() - started) / 1000.0
            steps = steps.dropLast(1)
            val url = outcome.getOrNull()
            when {
                url != null -> {
                    add(label, true, "OK dalam ${"%.1f".format(took)} dtk")
                    if (firstUrl == null) firstUrl = url
                }
                outcome.isFailure -> {
                    val e = outcome.exceptionOrNull()
                    add(label, false, "${e?.javaClass?.simpleName}: ${e?.message}".take(150))
                }
                else -> add(label, false, "Tidak menjawab dalam 16 detik")
            }
        }
        if (firstUrl == null) add("Kesimpulan", false, "Tidak ada satu pun sumber audio yang berhasil")
        return firstUrl
    }

    private suspend fun downloadStep(url: String): Boolean {
        add("3. Unduh potongan audio", null, "Mengecek…")
        val result: Result<String> = withContext(Dispatchers.IO) {
            try {
                val ua = StreamResolver.userAgentFor(url)
                    ?: "Mozilla/5.0 (Windows NT 10.0; rv:128.0) Gecko/20100101 Firefox/128.0"
                val request = Request.Builder().url(url)
                    .header("User-Agent", ua)
                    .header("Range", "bytes=0-65535")
                    .build()
                http.newCall(request).execute().use { r ->
                    val bytes = r.body?.bytes()?.size ?: 0
                    if (r.code in 200..299 && bytes > 0) Result.success("HTTP ${r.code}, $bytes byte")
                    else Result.failure(Exception("HTTP ${r.code}, $bytes byte"))
                }
            } catch (e: Exception) {
                Result.failure(e)
            }
        }
        steps = steps.dropLast(1)
        return if (result.isSuccess) {
            add("3. Unduh potongan audio", true, result.getOrThrow())
            true
        } else {
            add("3. Unduh potongan audio", false, (result.exceptionOrNull()?.message ?: "gagal").take(150))
            false
        }
    }

    private suspend fun playerStep(player: PlayerViewModel, song: SearchItem) {
        add("4. Putar lewat pemutar", null, "Memulai…")
        player.play(listOf(song), 0)

        var started = false
        for (i in 0 until 50) { // maksimal 25 detik
            delay(500)
            if (player.ui.isPlaying) { started = true; break }
        }
        if (!started) {
            steps = steps.dropLast(1)
            val why = player.ui.error ?: PlaybackDiagnostics.recent(2).ifBlank { "tidak ada keterangan" }
            add("4. Putar lewat pemutar", false, "Tidak bunyi dalam 25 detik: $why".take(200))
            return
        }
        val p0 = player.ui.positionMs
        delay(3_000)
        val p1 = player.ui.positionMs
        val moved = p1 - p0
        steps = steps.dropLast(1)
        if (moved >= 1_500) {
            add("4. Putar lewat pemutar", true, "Bunyi, posisi maju ${moved / 1000.0} dtk")
            add("Kesimpulan", true, "Pemutaran lagu BERHASIL ✅")
        } else {
            add("4. Putar lewat pemutar", false, "Status playing tapi posisi tidak bergerak ($moved ms)")
        }
        if (player.ui.isPlaying) player.togglePlay()
    }
}
