package com.jimz.jimztune.data.stats

import android.content.Context
import android.os.SystemClock
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableLongStateOf
import androidx.compose.runtime.setValue
import com.jimz.jimztune.AppGraph
import com.jimz.jimztune.data.cloud.CloudApi
import kotlinx.coroutines.CoroutineScope
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.Job
import kotlinx.coroutines.SupervisorJob
import kotlinx.coroutines.delay
import kotlinx.coroutines.isActive
import kotlinx.coroutines.launch
import kotlinx.coroutines.sync.Mutex
import kotlinx.coroutines.sync.withLock
import org.json.JSONObject
import kotlin.math.sqrt

/**
 * Menghitung berapa lama user mendengarkan lagu (detik) selama musik benar-benar diputar.
 * Detik yang belum terkirim disimpan di perangkat dan dikirim berkala (RPC add_listen), jadi tidak hilang
 * walau offline atau aplikasi ditutup. Total yang tampil = total dari server + detik yang belum terkirim.
 */
class ListenTracker(context: Context) {
    private val prefs = context.applicationContext.getSharedPreferences("jimztune_listen", Context.MODE_PRIVATE)
    private val scope = CoroutineScope(SupervisorJob() + Dispatchers.IO)
    private val flushLock = Mutex()
    private var loop: Job? = null

    private var serverTotal = prefs.getLong("server_total", 0L)
    private var pending = prefs.getLong("pending", 0L)

    /** Total detik mendengar (sudah termasuk yang belum terkirim). Aman dibaca dari komposisi. */
    var totalSeconds by mutableLongStateOf(serverTotal + pending)
        private set

    /** Dipanggil AppGraph setiap status "sedang memutar" berubah. */
    fun onPlayingChanged(playing: Boolean) {
        if (playing) startLoop() else {
            loop?.cancel()
            loop = null
            scope.launch { flush(force = true) }
        }
    }

    private fun startLoop() {
        if (loop?.isActive == true) return
        loop = scope.launch {
            var last = SystemClock.elapsedRealtime()
            var sinceFlush = 0L
            var carryMs = 0L
            while (isActive) {
                delay(TICK_MS)
                val now = SystemClock.elapsedRealtime()
                val dt = (now - last).coerceIn(0L, 10_000L)
                last = now
                if (AppGraph.playing && AppGraph.auth.isLoggedIn && !AppGraph.account.blocked) {
                    carryMs += dt
                    val whole = carryMs / 1000
                    if (whole > 0) {
                        carryMs -= whole * 1000
                        addPending(whole)
                        sinceFlush += whole
                    }
                }
                if (sinceFlush >= FLUSH_EVERY_S) {
                    sinceFlush = 0
                    flush()
                }
            }
        }
    }

    @Synchronized
    private fun addPending(sec: Long) {
        pending += sec
        prefs.edit().putLong("pending", pending).apply()
        totalSeconds = serverTotal + pending
    }

    /** Mengirim detik yang tertunda. [force] = coba walau belum menumpuk banyak. */
    suspend fun flush(force: Boolean = false) {
        if (!AppGraph.auth.isLoggedIn) return
        if (pending <= 0L) return
        if (!force && pending < 10) return
        flushLock.withLock {
            val token = AppGraph.auth.validToken() ?: return
            val send = synchronized(this) { pending.coerceAtMost(3600L) }
            if (send <= 0L) return
            val r = CloudApi.json(
                "POST", "/rest/v1/rpc/add_listen",
                JSONObject().put("p_seconds", send.toInt()).toString(), token
            )
            if (r.ok) {
                val total = r.body.trim().toLongOrNull()
                synchronized(this) {
                    pending = (pending - send).coerceAtLeast(0L)
                    if (total != null) serverTotal = total
                    persist()
                }
            }
        }
    }

    /** Mengambil total terbaru dari server (dipakai saat login / Beranda dibuka). */
    suspend fun refresh() {
        if (!AppGraph.auth.isLoggedIn) return
        flush(force = true)
        val token = AppGraph.auth.validToken() ?: return
        val r = CloudApi.json("POST", "/rest/v1/rpc/my_listen_total", "{}", token)
        if (!r.ok) return
        val total = r.body.trim().toLongOrNull() ?: return
        synchronized(this) {
            serverTotal = total
            persist()
        }
    }

    /** Keluar akun: kosongkan, supaya detik akun lama tidak masuk ke akun berikutnya. */
    fun clear() {
        synchronized(this) {
            serverTotal = 0
            pending = 0
            prefs.edit().clear().apply()
            totalSeconds = 0
        }
    }

    @Synchronized
    private fun persist() {
        prefs.edit().putLong("server_total", serverTotal).putLong("pending", pending).apply()
        totalSeconds = serverTotal + pending
    }

    private companion object {
        const val TICK_MS = 1_000L
        const val FLUSH_EVERY_S = 45L
    }
}

/** Level dari lama mendengar: level 2 di 30 menit, 3 di 2 jam, 5 di 8 jam, 10 di ±40 jam, 20 di 180 jam. */
object ListenLevel {
    private const val UNIT = 1800.0 // detik

    fun level(totalSec: Long): Int = 1 + sqrt(totalSec.coerceAtLeast(0) / UNIT).toInt()

    /** 0f..1f menuju level berikutnya. */
    fun progress(totalSec: Long): Float {
        val l = level(totalSec)
        val from = UNIT * (l - 1) * (l - 1)
        val to = UNIT * l * l
        return (((totalSec - from) / (to - from)).toFloat()).coerceIn(0f, 1f)
    }

    /** Sisa detik menuju level berikutnya. */
    fun remaining(totalSec: Long): Long {
        val l = level(totalSec)
        return (UNIT * l * l - totalSec).toLong().coerceAtLeast(0)
    }
}

/** "12j 40m", "40m", "<1m". */
fun formatListen(sec: Long): String {
    val m = (sec / 60).toInt()
    val h = m / 60
    return when {
        h >= 100 -> "${h}j"
        h > 0 -> "${h}j ${m % 60}m"
        m > 0 -> "${m}m"
        else -> if (sec > 0) "<1m" else "0m"
    }
}
