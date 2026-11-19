package com.jimz.jimztune.data.download

import android.content.Context
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateMapOf
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.setValue
import com.jimz.jimztune.data.library.toJson
import com.jimz.jimztune.data.library.toSong
import com.jimz.jimztune.data.model.SearchItem
import com.jimz.jimztune.data.stream.StreamResolver
import kotlinx.coroutines.CancellationException
import kotlinx.coroutines.CoroutineScope
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.Job
import kotlinx.coroutines.SupervisorJob
import kotlinx.coroutines.currentCoroutineContext
import kotlinx.coroutines.ensureActive
import kotlinx.coroutines.launch
import kotlinx.coroutines.sync.Semaphore
import kotlinx.coroutines.sync.withPermit
import okhttp3.OkHttpClient
import okhttp3.Request
import org.json.JSONArray
import java.io.File
import java.io.IOException
import java.util.concurrent.ConcurrentHashMap
import java.util.concurrent.TimeUnit

data class ActiveDownload(val song: SearchItem, val progress: Float = -1f, val error: String? = null)

/**
 * Unduhan untuk didengar offline. Audio disimpan di filesDir/downloads, dan [StreamResolver]
 * otomatis memakai file lokal itu kalau ada, sehingga lagu yang diunduh bisa diputar tanpa internet.
 * Proses unduh berjalan selama aplikasi hidup (misalnya sambil musik diputar).
 */
class Downloader(context: Context) {
    private val dir = File(context.filesDir, "downloads").apply { mkdirs() }
    private val indexFile = File(dir, "index.json")
    private val scope = CoroutineScope(SupervisorJob() + Dispatchers.IO)
    private val gate = Semaphore(2)
    private val jobs = ConcurrentHashMap<String, Job>()
    private val client = OkHttpClient.Builder()
        .connectTimeout(15, TimeUnit.SECONDS)
        .readTimeout(30, TimeUnit.SECONDS)
        .build()

    var songs by mutableStateOf<List<SearchItem>>(emptyList())
        private set
    val active = mutableStateMapOf<String, ActiveDownload>()

    init {
        dir.listFiles()?.filter { it.extension == "part" }?.forEach { it.delete() }
        try {
            if (indexFile.exists()) {
                val arr = JSONArray(indexFile.readText())
                // Hanya simpan yang filenya masih ada.
                songs = (0 until arr.length()).mapNotNull { arr.optJSONObject(it) }
                    .mapNotNull { runCatching { it.toSong() }.getOrNull() }
                    .filter { localFile(it.id) != null }
            }
        } catch (_: Exception) {
        }
    }

    fun isDownloaded(id: String): Boolean = songs.any { it.id == id }

    /** File audio lokal untuk lagu ini, atau null kalau belum diunduh. */
    fun localFile(id: String): File? {
        val safe = safeName(id)
        return dir.listFiles()?.firstOrNull {
            it.nameWithoutExtension == safe && it.extension != "part" && it.extension != "json" && it.length() > 0
        }
    }

    fun totalBytes(): Long = dir.listFiles()?.filter { it.extension != "json" }?.sumOf { it.length() } ?: 0L

    fun download(song: SearchItem) {
        if (!com.jimz.jimztune.AppGraph.remote.featureOn("downloads")) return
        if (isDownloaded(song.id) || jobs.containsKey(song.id)) return
        active[song.id] = ActiveDownload(song)
        jobs[song.id] = scope.launch {
            try {
                gate.withPermit { run(song) }
                songs = listOf(song) + songs.filter { it.id != song.id }
                saveIndex()
                active.remove(song.id)
            } catch (e: CancellationException) {
                File(dir, safeName(song.id) + ".part").delete()
                active.remove(song.id)
                throw e
            } catch (e: Throwable) {
                File(dir, safeName(song.id) + ".part").delete()
                val detail = (e.message ?: e.javaClass.simpleName).take(120)
                com.jimz.jimztune.util.UserError.log("Unduh", "${song.title}: $detail")
                active[song.id] = ActiveDownload(song, -1f, detail)
            } finally {
                jobs.remove(song.id)
            }
        }
    }

    /** Mengantre semua lagu yang belum terunduh (maksimal 2 berjalan bersamaan). Mengembalikan jumlah yang diantre. */
    fun downloadAll(list: List<SearchItem>): Int {
        var queued = 0
        list.distinctBy { it.id }.forEach {
            if (!isDownloaded(it.id) && !jobs.containsKey(it.id)) {
                download(it)
                queued++
            }
        }
        return queued
    }

    /** Membatalkan unduhan yang masih antre/berjalan (yang sudah selesai tetap tersimpan). */
    fun cancelAll(list: List<SearchItem>) {
        list.forEach { if (active.containsKey(it.id)) cancel(it.id) }
    }

    fun cancel(id: String) {
        jobs[id]?.cancel()
        active.remove(id)
    }

    fun delete(id: String) {
        cancel(id)
        localFile(id)?.delete()
        songs = songs.filter { it.id != id }
        saveIndex()
    }

    /**
     * Dipanggil saat file unduhan ternyata rusak: hapus file itu (lagu langsung diputar online),
     * lalu unduh ulang di latar belakang.
     */
    fun repair(id: String) {
        val song = songs.firstOrNull { it.id == id } ?: return
        delete(id)
        com.jimz.jimztune.util.UserError.log("Unduh", "${song.title}: file rusak, diunduh ulang")
        download(song)
    }

    fun deleteAll() {
        jobs.keys.toList().forEach { cancel(it) }
        dir.listFiles()?.forEach { it.delete() }
        songs = emptyList()
    }

    private suspend fun run(song: SearchItem) {
        var last: Exception? = null
        repeat(3) { attempt ->
            try {
                if (attempt > 0) kotlinx.coroutines.delay(1_000L * attempt) // jeda makin lama antar percobaan
                val url = StreamResolver.resolve(song.id)
                fetch(song.id, url) { active[song.id] = ActiveDownload(song, it) }
                return
            } catch (e: CancellationException) {
                throw e
            } catch (e: Exception) {
                last = e
                StreamResolver.invalidate(song.id) // URL mungkin kedaluwarsa, cari yang baru
            }
        }
        throw last ?: IOException("Unduhan gagal")
    }

    /** Mengunduh per potongan 1 MB (Range) karena server video sering menolak unduhan satu aliran penuh. */
    private suspend fun fetch(id: String, url: String, onProgress: (Float) -> Unit) {
        val safe = safeName(id)
        val part = File(dir, "$safe.part")
        part.delete()
        val ua = StreamResolver.userAgentFor(url)
            ?: "Mozilla/5.0 (Windows NT 10.0; rv:128.0) Gecko/20100101 Firefox/128.0"
        var start = 0L
        var total = -1L
        var ext = "m4a"
        var first = true

        while (true) {
            currentCoroutineContext().ensureActive()
            val request = Request.Builder().url(url)
                .header("User-Agent", ua)
                .header("Range", "bytes=$start-${start + CHUNK - 1}")
                .build()
            var wholeFile = false
            client.newCall(request).execute().use { resp ->
                if (first) {
                    val type = resp.header("Content-Type").orEmpty()
                    ext = if (type.contains("webm")) "webm" else "m4a"
                    first = false
                }
                val body = resp.body ?: throw IOException("Respons kosong")
                when (resp.code) {
                    206 -> {
                        total = resp.header("Content-Range")?.substringAfter('/')?.toLongOrNull() ?: total
                        part.appendBytes(body.bytes())
                        start = part.length()
                    }
                    200 -> {
                        // Server mengabaikan Range dan mengirim file utuh.
                        total = body.contentLength()
                        part.outputStream().use { out -> body.byteStream().copyTo(out) }
                        start = part.length()
                        wholeFile = true
                    }
                    416 -> {
                        // Sudah melewati akhir file: berarti semua potongan sudah terunduh.
                        if (start == 0L) throw IOException("HTTP 416")
                        wholeFile = true
                    }
                    else -> throw IOException("HTTP ${resp.code}")
                }
            }
            if (total > 0) onProgress((start.toFloat() / total).coerceIn(0f, 1f))
            if (wholeFile || (total > 0 && start >= total)) break
            if (total <= 0 && start == 0L) throw IOException("Ukuran file tidak diketahui")
        }
        if (part.length() == 0L) throw IOException("File kosong")
        val target = File(dir, "$safe.$ext")
        target.delete()
        if (!part.renameTo(target)) throw IOException("Gagal menyimpan file")
    }

    private fun saveIndex() {
        try {
            val arr = JSONArray()
            songs.forEach { arr.put(it.toJson()) }
            indexFile.writeText(arr.toString())
        } catch (_: Exception) {
        }
    }

    private fun safeName(id: String) = id.replace(Regex("[^A-Za-z0-9_-]"), "_")

    private companion object {
        const val CHUNK = 1_048_576L
    }
}
