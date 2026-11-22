package com.jimz.jimztune.data.realtime

import com.jimz.jimztune.AppConfig
import com.jimz.jimztune.AppGraph
import com.jimz.jimztune.data.cloud.CloudApi
import com.jimz.jimztune.data.owner.OwnerApi
import com.jimz.jimztune.util.Notifier
import kotlinx.coroutines.CompletableDeferred
import kotlinx.coroutines.CoroutineScope
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.Job
import kotlinx.coroutines.SupervisorJob
import kotlinx.coroutines.delay
import kotlinx.coroutines.isActive
import kotlinx.coroutines.launch
import kotlinx.coroutines.withContext
import okhttp3.OkHttpClient
import okhttp3.Request
import okhttp3.Response
import okhttp3.WebSocket
import okhttp3.WebSocketListener
import org.json.JSONArray
import org.json.JSONObject
import java.util.concurrent.TimeUnit

/**
 * Koneksi realtime ke server (WebSocket). Saat owner mengubah pengumuman, banner, popup donasi, maintenance,
 * atau status akun seorang user, perubahan langsung sampai ke aplikasi tanpa menunggu aplikasi dibuka ulang.
 *
 * Yang dikirim hanyalah sinyal "ada perubahan"; isinya diambil ulang lewat fungsi get_app_config / my_account.
 * Jadi keamanan tetap ditentukan database (RLS): user biasa tidak bisa mengubah apa pun lewat jalur ini.
 */
class RealtimeHub {
    private val scope = CoroutineScope(SupervisorJob() + Dispatchers.IO)
    private val client = OkHttpClient.Builder()
        .connectTimeout(12, TimeUnit.SECONDS)
        .readTimeout(0, TimeUnit.MILLISECONDS) // soket panjang: tidak boleh ada batas baca
        .build()

    @Volatile private var wanted = false
    private var loop: Job? = null
    private var socket: WebSocket? = null
    private var refCounter = 0
    private var pendingRefresh: Job? = null
    private val notified = HashSet<String>()
    private var pollJob: Job? = null

    /** Dinyalakan kalau langganan gagal selama login (mis. filter user_moderation ditolak), supaya sisanya tetap jalan. */
    @Volatile private var skipModeration = false

    private val topic = "realtime:jimztune"
    private companion object { const val POLL_MS = 15_000L }

    @Synchronized
    fun start() {
        if (wanted || !CloudApi.configured) return
        wanted = true
        loop = scope.launch { runLoop() }
        // Jaring pengaman: kalau soket realtime diam-diam mati / gagal berlangganan, perubahan tetap sampai
        // dalam hitungan detik (selama aplikasi terlihat) lewat pengambilan ulang berkala yang ringan.
        pollJob = scope.launch {
            while (isActive && wanted) {
                delay(POLL_MS)
                // Cadangan notifikasi donasi: kalau soket realtime diam, donasi baru tetap ketahuan dalam ±15 detik.
                try { pollDonations() } catch (_: Exception) {}
                if (AppGraph.appVisible) {
                    withContext(Dispatchers.Main) { AppGraph.remote.refresh(force = true) }
                }
            }
        }
    }

    @Synchronized
    fun stop() {
        wanted = false
        loop?.cancel()
        loop = null
        pollJob?.cancel()
        pollJob = null
        try { socket?.close(1000, "bye") } catch (_: Exception) {}
        socket = null
    }

    /** Dipanggil saat login / logout: sambungkan ulang supaya langganan (status akun) ikut berubah. */
    fun restart() {
        if (!wanted) return
        skipModeration = false
        stop()
        start()
    }

    private suspend fun runLoop() {
        var attempt = 0
        while (scope.isActive && wanted) {
            val closed = CompletableDeferred<Boolean>() // true = sempat tersambung baik-baik
            val token = AppGraph.auth.validToken() ?: AppConfig.CLOUD_KEY
            val wsUrl = CloudApi.url.replaceFirst("https://", "wss://").replaceFirst("http://", "ws://") +
                "/realtime/v1/websocket?apikey=${AppConfig.CLOUD_KEY}&vsn=1.0.0"
            val ws = client.newWebSocket(Request.Builder().url(wsUrl).build(), Listener(token, closed))
            socket = ws
            val wasHealthy = closed.await()
            try { ws.cancel() } catch (_: Exception) {}
            if (!wanted) break
            attempt = if (wasHealthy) 1 else attempt + 1
            delay((2_000L * attempt).coerceAtMost(15_000L))
        }
    }

    private fun nextRef(): String = (++refCounter).toString()

    private fun send(ws: WebSocket, topic: String, event: String, payload: JSONObject) {
        val msg = JSONObject().put("topic", topic).put("event", event).put("payload", payload).put("ref", nextRef())
        try { ws.send(msg.toString()) } catch (_: Exception) {}
    }

    private inner class Listener(private val initialToken: String, private val closed: CompletableDeferred<Boolean>) : WebSocketListener() {
        private var heartbeat: Job? = null
        private var joined = false
        private var sentToken = initialToken

        override fun onOpen(webSocket: WebSocket, response: Response) {
            webSocket0 = webSocket
            val changes = JSONArray()
            for (table in listOf("app_config", "announcements", "banners", "donation_links")) {
                changes.put(JSONObject().put("event", "*").put("schema", "public").put("table", table))
            }
            val uid = AppGraph.auth.session?.userId
            // Donasi masuk: hanya owner yang berlangganan (RLS di server juga hanya mengirim ke owner).
            if (uid != null && AppGraph.account.isOwner) {
                changes.put(JSONObject().put("event", "INSERT").put("schema", "public").put("table", "donations"))
            }
            if (uid != null && !skipModeration) {
                changes.put(
                    JSONObject().put("event", "*").put("schema", "public").put("table", "user_moderation")
                        .put("filter", "user_id=eq.$uid")
                )
                // Pesan pribadi dari owner (khusus akun ini; RLS server juga hanya mengirim ke pemiliknya).
                changes.put(
                    JSONObject().put("event", "INSERT").put("schema", "public").put("table", "user_messages")
                        .put("filter", "user_id=eq.$uid")
                )
            }
            val config = JSONObject()
                .put("broadcast", JSONObject().put("self", false))
                .put("presence", JSONObject().put("key", ""))
                .put("postgres_changes", changes)
            send(webSocket, topic, "phx_join", JSONObject().put("config", config).put("access_token", initialToken))

            heartbeat = scope.launch {
                while (isActive) {
                    delay(25_000)
                    send(webSocket, "phoenix", "heartbeat", JSONObject())
                    // Token login berumur ±1 jam: kirim yang baru supaya langganan tidak diputus server.
                    val fresh = AppGraph.auth.validToken() ?: AppConfig.CLOUD_KEY
                    if (fresh != sentToken) {
                        sentToken = fresh
                        send(webSocket, topic, "access_token", JSONObject().put("access_token", fresh))
                    }
                }
            }
        }

        override fun onMessage(webSocket: WebSocket, text: String) {
            try {
                val o = JSONObject(text)
                when (o.optString("event")) {
                    "phx_reply" -> if (o.optString("topic") == topic) {
                        val ok = o.optJSONObject("payload")?.optString("status") == "ok"
                        if (ok) {
                            if (!joined) {
                                joined = true
                                // Baru tersambung (atau tersambung ulang): ambil config terbaru supaya
                                // perubahan yang terjadi selagi soket putus tidak terlewat.
                                onChange("app_config")
                            }
                        } else failJoin()
                    }
                    "system" -> {
                        // Server menolak langganan (tabel belum didaftarkan, izin kurang, token salah, dst).
                        if (o.optString("topic") == topic &&
                            o.optJSONObject("payload")?.optString("status") == "error"
                        ) failJoin()
                    }
                    "postgres_changes" -> {
                        val data = o.optJSONObject("payload")?.optJSONObject("data")
                        val table = data?.optString("table").orEmpty()
                        if (table == "donations") onDonation(data?.optJSONObject("record")) else onChange(table)
                    }
                    "phx_error", "phx_close" -> if (o.optString("topic") == topic) closed.complete(joined)
                }
            } catch (_: Exception) {
            }
        }

        /** Langganan gagal: tutup soket supaya runLoop menyambung ulang, bukan menggantung tanpa langganan. */
        private fun failJoin() {
            if (AppGraph.auth.session?.userId != null) skipModeration = true
            try { webSocket0?.cancel() } catch (_: Exception) {}
            closed.complete(false)
        }

        private var webSocket0: WebSocket? = null

        override fun onClosed(webSocket: WebSocket, code: Int, reason: String) {
            heartbeat?.cancel()
            closed.complete(joined)
        }

        override fun onFailure(webSocket: WebSocket, t: Throwable, response: Response?) {
            heartbeat?.cancel()
            closed.complete(joined)
        }
    }

    private suspend fun pollDonations() {
        if (!AppGraph.account.isOwner || AppGraph.auth.session == null) return
        val list = OwnerApi.donations().value ?: return
        val maxId = list.maxOfOrNull { it.id } ?: return
        val prefs = AppGraph.appContext.getSharedPreferences("jimztune_donasi", android.content.Context.MODE_PRIVATE)
        val last = prefs.getLong("last_id", -1L)
        if (last < 0L) { // pertama kali: jadikan patokan, jangan membanjiri notifikasi donasi lama
            prefs.edit().putLong("last_id", maxId).apply()
            return
        }
        list.filter { it.id > last }.sortedBy { it.id }.forEach { d ->
            if (notified.add("don:${d.id}")) Notifier.donation(AppGraph.appContext, d.id.toString(), d.name, d.amount, d.note)
        }
        if (maxId > last) prefs.edit().putLong("last_id", maxId).apply()
    }

    private fun onDonation(rec: JSONObject?) {
        if (rec == null || !AppGraph.account.isOwner) return
        val id = rec.opt("id")?.toString() ?: return
        if (!notified.add("don:$id")) return
        val name = rec.optString("donor_name").ifBlank { "Anonim" }
        val amount = rec.optLong("amount")
        val note = rec.optString("note")
        Notifier.donation(AppGraph.appContext, id, name, amount, note)
    }

    private fun onChange(table: String) {
        if (table == "user_moderation") {
            scope.launch(Dispatchers.Main) { AppGraph.account.refresh() }
            return
        }
        // Beberapa perubahan beruntun (mis. banner + config) digabung jadi satu pengambilan ulang.
        pendingRefresh?.cancel()
        pendingRefresh = scope.launch(Dispatchers.Main) {
            delay(250)
            AppGraph.remote.refresh(force = true)
            notifyIfBackground()
        }
    }

    /** Aplikasi tidak sedang terlihat (mis. musik jalan di latar): pengumuman baru dikirim sebagai notifikasi sistem. */
    private fun notifyIfBackground() {
        if (AppGraph.appVisible) return
        val a = AppGraph.remote.pendingAnnouncement() ?: return
        if (!notified.add(a.id)) return
        Notifier.announcement(AppGraph.appContext, a.id, a.title, a.message)
    }
}
