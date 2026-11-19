package com.jimz.jimztune.data.cloud

import com.jimz.jimztune.AppConfig
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.withContext
import okhttp3.MediaType.Companion.toMediaType
import okhttp3.OkHttpClient
import okhttp3.Request
import okhttp3.RequestBody
import okhttp3.RequestBody.Companion.toRequestBody
import org.json.JSONObject
import java.io.IOException
import java.util.concurrent.TimeUnit

/**
 * Klien REST server untuk Auth, database (PostgREST), dan Storage.
 * Sengaja terpisah dari [com.jimz.jimztune.data.search.Http]: timeout lebih longgar (unggah gambar)
 * dan isi pesan error dari server tetap terbaca (mis. "Invalid login credentials").
 */
object CloudApi {
    val url: String get() = AppConfig.CLOUD_URL.trimEnd('/')
    private val anon: String get() = AppConfig.CLOUD_KEY

    val configured: Boolean
        get() = !AppConfig.CLOUD_URL.contains("GANTI_") && !AppConfig.CLOUD_KEY.contains("GANTI_")

    class Resp(val code: Int, val body: String) {
        val ok: Boolean get() = code in 200..299
        /** -1 = gagal menghubungi server (offline, timeout, dll). */
        val offline: Boolean get() = code == -1
    }

    private val client = OkHttpClient.Builder()
        .connectTimeout(12, TimeUnit.SECONDS)
        .readTimeout(25, TimeUnit.SECONDS)
        .writeTimeout(40, TimeUnit.SECONDS)
        .callTimeout(60, TimeUnit.SECONDS)
        .build()

    private val JSON = "application/json; charset=utf-8".toMediaType()

    suspend fun json(
        method: String,
        path: String,
        body: String? = null,
        token: String? = null,
        headers: Map<String, String> = emptyMap()
    ): Resp {
        val needsBody = method == "POST" || method == "PUT" || method == "PATCH"
        val reqBody: RequestBody? = when {
            body != null -> body.toRequestBody(JSON)
            needsBody -> "".toRequestBody(JSON)
            else -> null
        }
        return send(method, path, reqBody, token, headers)
    }

    suspend fun bytes(
        path: String,
        data: ByteArray,
        contentType: String,
        token: String
    ): Resp = send("POST", path, data.toRequestBody(contentType.toMediaType()), token, emptyMap())

    private suspend fun send(
        method: String,
        path: String,
        body: RequestBody?,
        token: String?,
        headers: Map<String, String>
    ): Resp = withContext(Dispatchers.IO) {
        // Server belum dikonfigurasi (build tanpa CLOUD_URL / CLOUD_KEY): jangan membuat permintaan sama sekali.
        // Dulu alamat "GANTI_CLOUD_URL" membuat OkHttp melempar IllegalArgumentException (bukan IOException) dan aplikasi crash
        // begitu ada sesi login lama yang tersimpan.
        if (!configured) return@withContext Resp(-1, "Server belum dikonfigurasi")
        try {
            val builder = Request.Builder()
                .url(url + path)
                .header("apikey", anon)
                .header("Authorization", "Bearer " + (token ?: anon))
                .method(method, body)
            headers.forEach { (k, v) -> builder.header(k, v) }
            client.newCall(builder.build()).execute().use { r ->
                Resp(r.code, r.body?.string().orEmpty())
            }
        } catch (e: IOException) {
            com.jimz.jimztune.util.UserError.log("Jaringan", "${e.javaClass.simpleName}: ${e.message}")
            Resp(-1, "Tidak bisa menghubungi server")
        } catch (e: IllegalArgumentException) {
            com.jimz.jimztune.util.UserError.log("Jaringan", "Alamat server tidak valid: ${e.message}")
            Resp(-1, "Alamat server tidak valid")
        }
    }

    fun publicUrl(bucket: String, path: String): String = "$url/storage/v1/object/public/$bucket/$path"

    /** Mengambil pesan error yang enak dibaca dari berbagai bentuk respons error server. */
    fun errorText(resp: Resp): String {
        if (resp.offline) return "Tidak bisa menghubungi server. Cek internetmu."
        if (resp.code >= 500) {
            return com.jimz.jimztune.util.UserError.report("Server", "Server sedang bermasalah. Coba lagi sebentar lagi.", "HTTP ${resp.code}: ${resp.body.take(160)}")
        }
        return try {
            val o = JSONObject(resp.body)
            listOf("msg", "error_description", "message", "error")
                .firstNotNullOfOrNull { k -> o.optString(k).takeIf { it.isNotBlank() } }
                ?: "Terjadi kesalahan (${resp.code})"
        } catch (_: Exception) {
            "Terjadi kesalahan (${resp.code})"
        }
    }
}
