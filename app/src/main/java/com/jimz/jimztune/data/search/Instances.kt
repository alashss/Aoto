package com.jimz.jimztune.data.search

import kotlinx.coroutines.CancellationException
import kotlinx.coroutines.CoroutineScope
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.SupervisorJob
import kotlinx.coroutines.channels.Channel
import kotlinx.coroutines.launch
import kotlinx.coroutines.withTimeoutOrNull
import java.io.IOException

private val raceScope = CoroutineScope(SupervisorJob() + Dispatchers.IO)

/**
 * Mencoba beberapa instance publik sekaligus dan memakai yang pertama berhasil.
 * Instance mati tidak lagi membuat pencarian menunggu satu-satu.
 */
internal suspend fun <T> raceInstances(
    instances: List<String>,
    maxParallel: Int = 5,
    timeoutMs: Long = 9_000L,
    block: suspend (String) -> T
): T {
    val picks = instances.shuffled().take(maxParallel)
    if (picks.isEmpty()) throw IOException("Daftar instance kosong")
    val channel = Channel<Result<T>>(picks.size)
    val jobs = picks.map { base ->
        raceScope.launch {
            val result = try {
                Result.success(block(base))
            } catch (e: CancellationException) {
                throw e
            } catch (e: Throwable) {
                Result.failure(e)
            }
            channel.trySend(result)
        }
    }
    try {
        var last: Throwable? = null
        var pending = picks.size
        val deadline = System.currentTimeMillis() + timeoutMs
        while (pending > 0) {
            val remaining = deadline - System.currentTimeMillis()
            if (remaining <= 0) break
            val r = withTimeoutOrNull(remaining) { channel.receive() } ?: break
            pending--
            if (r.isSuccess) return r.getOrThrow()
            last = r.exceptionOrNull()
        }
        throw last ?: IOException("Tidak ada instance yang menjawab")
    } finally {
        jobs.forEach { it.cancel() }
    }
}
