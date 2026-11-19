package com.jimz.jimztune.data.search

import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.suspendCancellableCoroutine
import kotlinx.coroutines.withContext
import okhttp3.Call
import okhttp3.Callback
import okhttp3.Dispatcher
import okhttp3.MediaType.Companion.toMediaType
import okhttp3.OkHttpClient
import okhttp3.Request
import okhttp3.RequestBody.Companion.toRequestBody
import okhttp3.Response
import java.io.IOException
import java.util.concurrent.TimeUnit
import kotlin.coroutines.resume
import kotlin.coroutines.resumeWithException

object Http {
    private const val UA =
        "Mozilla/5.0 (Windows NT 10.0; Win64; x64) AppleWebKit/537.36 (KHTML, like Gecko) Chrome/124.0 Safari/537.36"

    // Bawaan OkHttp: maks 5 permintaan per host. Beberapa klien YouTube + prefetch lagu lain ke host yang sama
    // jadi antre, dan lagu yang sedang ditunggu ikut tertahan di belakangnya.
    private val dispatcher = Dispatcher().apply {
        maxRequests = 64
        maxRequestsPerHost = 24
    }

    private val client = OkHttpClient.Builder()
        .dispatcher(dispatcher)
        .connectTimeout(4, TimeUnit.SECONDS)
        .readTimeout(6, TimeUnit.SECONDS)
        .callTimeout(8, TimeUnit.SECONDS)
        .build()

    suspend fun get(url: String, headers: Map<String, String> = emptyMap()): String =
        execute(Request.Builder().url(url), headers)

    /** Membuka sambungan (DNS + TLS) ke host lebih awal; hasil dan error diabaikan. Sambungan dipakai ulang oleh permintaan berikutnya. */
    suspend fun preconnect(url: String) {
        try {
            execute(Request.Builder().url(url), emptyMap())
        } catch (e: kotlinx.coroutines.CancellationException) {
            throw e
        } catch (_: Throwable) {
        }
    }

    /** Alamat akhir setelah semua pengalihan (tautan pendek seperti spotify.link). Gagal = alamat semula. */
    suspend fun finalUrl(url: String): String = withContext(Dispatchers.IO) {
        try {
            client.newCall(Request.Builder().url(url).header("User-Agent", UA).build()).execute()
                .use { it.request.url.toString() }
        } catch (_: Exception) {
            url
        }
    }

    suspend fun postJson(url: String, body: String, headers: Map<String, String> = emptyMap()): String =
        execute(
            Request.Builder().url(url)
                .post(body.toRequestBody("application/json; charset=utf-8".toMediaType())),
            headers
        )

    /** POST berbentuk formulir (application/x-www-form-urlencoded); [form] sudah di-encode, mis. "q=halo&x=1". */
    suspend fun postForm(url: String, form: String, headers: Map<String, String> = emptyMap()): String =
        execute(
            Request.Builder().url(url)
                .post(form.toRequestBody("application/x-www-form-urlencoded; charset=utf-8".toMediaType())),
            headers
        )

    // Memakai kolam sambungan yang sama dengan [client], jadi hasil preconnect (DNS + TLS) ikut terpakai dan
    // pengecekan URL tidak membayar jabat tangan baru.
    private val probeClient = client.newBuilder()
        .connectTimeout(3, TimeUnit.SECONDS)
        .readTimeout(3, TimeUnit.SECONDS)
        .callTimeout(4, TimeUnit.SECONDS)
        .followRedirects(true)
        .build()

    /**
     * Mengecek cepat (1–2 byte pertama) apakah URL audio benar-benar bisa diunduh.
     * Mengembalikan false HANYA kalau server menolak (403/404/dst). Kalau jaringan yang bermasalah,
     * kembalikan true dan biarkan pemutar yang memutuskan, supaya pengecekan ini tidak malah memperlambat.
     */
    suspend fun probe(url: String, userAgent: String?): Boolean =
        suspendCancellableCoroutine { cont ->
            // Cek di bagian DALAM file, bukan awalnya: URL klien iOS/Android tanpa PO token sering lancar di awal
            // lalu ditolak (403) di tengah lagu. Posisi dipilih ~60% panjang file (maks 6 MB) supaya terdeteksi
            // sebelum lagu diputar, bukan saat sudah jalan setengah.
            val clen = Regex("[?&]clen=(\\d+)").find(url)?.groupValues?.get(1)?.toLongOrNull()
            val at = if (clen != null && clen > 2_000_000L) minOf((clen * 6) / 10, 6_000_000L) else 0L
            val b = Request.Builder().url(url).header("Range", "bytes=$at-${at + 1}")
            b.header("User-Agent", userAgent ?: "Mozilla/5.0 (Windows NT 10.0; rv:128.0) Gecko/20100101 Firefox/128.0")
            val call = probeClient.newCall(b.build())
            cont.invokeOnCancellation { call.cancel() }
            call.enqueue(object : Callback {
                override fun onFailure(call: Call, e: IOException) {
                    if (cont.isActive) cont.resume(true)
                }

                override fun onResponse(call: Call, response: Response) {
                    // 416 = posisi di luar file (file lebih pendek dari perkiraan), bukan penolakan.
                    val ok = response.use { it.isSuccessful || it.code == 416 }
                    if (cont.isActive) cont.resume(ok)
                }
            })
        }

    /** Permintaan jaringan yang langsung berhenti saat coroutine-nya dibatalkan. */
    private suspend fun execute(builder: Request.Builder, headers: Map<String, String>): String =
        suspendCancellableCoroutine<String> { cont ->
            builder.header("User-Agent", UA)
            headers.forEach { (k, v) -> builder.header(k, v) }
            val call = client.newCall(builder.build())
            cont.invokeOnCancellation { call.cancel() }
            call.enqueue(object : Callback {
                override fun onFailure(call: Call, e: IOException) {
                    if (cont.isActive) cont.resumeWithException(e)
                }

                override fun onResponse(call: Call, response: Response) {
                    response.use { resp ->
                        try {
                            if (!resp.isSuccessful) throw IOException("HTTP ${resp.code}")
                            val text = resp.body?.string() ?: throw IOException("Respons kosong")
                            if (cont.isActive) cont.resume(text)
                        } catch (e: Throwable) {
                            // Throwable apa pun harus ditangkap di sini: OkHttp melempar ulang error dari callback
                            // dan itu akan mematikan seluruh aplikasi.
                            val error = e as? IOException ?: IOException(e.javaClass.simpleName + ": " + e.message)
                            if (cont.isActive) cont.resumeWithException(error)
                        }
                    }
                }
            })
        }
}
