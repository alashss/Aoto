package com.jimz.jimztune.data.auth

import android.content.Context
import android.net.Uri
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.setValue
import com.jimz.jimztune.AppGraph
import com.jimz.jimztune.data.cloud.CloudApi
import kotlinx.coroutines.sync.Mutex
import kotlinx.coroutines.sync.withLock
import org.json.JSONObject
import java.net.URLEncoder
import java.security.MessageDigest
import java.security.SecureRandom
import android.util.Base64
import com.jimz.jimztune.util.SecureStore
import androidx.credentials.exceptions.GetCredentialCancellationException
import androidx.credentials.exceptions.GetCredentialException
import androidx.credentials.exceptions.NoCredentialException
import kotlinx.coroutines.CancellationException

data class Session(
    val accessToken: String,
    val refreshToken: String,
    /** Detik sejak epoch. */
    val expiresAt: Long,
    val userId: String,
    val email: String
)

/** [needsCode] = akun butuh verifikasi kode OTP dari email (layar login pindah ke kolom kode). */
data class AuthResult(val ok: Boolean, val message: String = "", val needsCode: Boolean = false)

/**
 * Login lewat REST (tanpa library tambahan): email+password (verifikasi kode OTP) dan Google native.
 * Sesi disimpan di perangkat dan diperbarui otomatis, jadi user tidak perlu login ulang tiap buka aplikasi.
 */
class AuthManager(context: Context) {
    private val prefs = context.applicationContext.getSharedPreferences("jimztune_auth", Context.MODE_PRIVATE)
    private val refreshLock = Mutex()

    var session by mutableStateOf(loadSession())
        private set
    var busy by mutableStateOf(false)
    /** Pesan untuk layar login (error / info), diisi saat kembali dari browser atau tautan email. */
    var message by mutableStateOf<String?>(null)
    /** True setelah user membuka tautan "lupa password" dari email: minta password baru. */
    var recoveryMode by mutableStateOf(false)
    var loginRequested by mutableStateOf(false)
        private set
    private var onboardingDone by mutableStateOf(prefs.getBoolean("onboarding_done", false))

    val isLoggedIn: Boolean get() = session != null
    val canSkip: Boolean get() = !REQUIRE_LOGIN

    /** Layar login tampil: dibuka manual dari Setelan, atau otomatis saat pertama kali memakai aplikasi. */
    val loginVisible: Boolean
        get() = CloudApi.configured && !isLoggedIn && (loginRequested || REQUIRE_LOGIN || !onboardingDone)

    fun openLogin() {
        message = null
        loginRequested = true
    }

    fun skipLogin() {
        onboardingDone = true
        loginRequested = false
        prefs.edit().putBoolean("onboarding_done", true).apply()
    }

    // ---------------------------------------------------------------- email

    suspend fun signIn(email: String, password: String): AuthResult {
        // Akun lama mungkin punya password pendek, jadi panjang minimum hanya diperiksa saat daftar / ganti password.
        if (!email.contains("@") || !email.contains(".")) return AuthResult(false, "Format email belum benar")
        if (password.isEmpty()) return AuthResult(false, "Isi passwordnya dulu")
        busy = true
        try {
            val r = CloudApi.json(
                "POST", "/auth/v1/token?grant_type=password",
                JSONObject().put("email", email.trim()).put("password", password).toString()
            )
            if (!r.ok) {
                if ("email not confirmed" in CloudApi.errorText(r).lowercase()) {
                    resendSignupCode(email, manageBusy = false)
                    return AuthResult(true, "Email belum diverifikasi. Kode baru dikirim ke emailmu.", needsCode = true)
                }
                return AuthResult(false, translate(CloudApi.errorText(r)))
            }
            return if (applyTokenResponse(r.body)) AuthResult(true) else AuthResult(false, "Respons login tidak dikenali")
        } finally {
            busy = false
        }
    }

    /** Daftar: Server mengirim kode OTP ke email (template "Confirm signup" harus memakai {{ .Token }}). */
    suspend fun signUp(email: String, password: String): AuthResult {
        if (!AppGraph.remote.featureOn("signup")) {
            return AuthResult(false, "Pendaftaran akun baru sedang ditutup sementara. Coba lagi nanti ya.")
        }
        checkInput(email, password)?.let { return it }
        busy = true
        try {
            val r = CloudApi.json(
                "POST", "/auth/v1/signup",
                JSONObject().put("email", email.trim()).put("password", password).toString()
            )
            if (!r.ok) return AuthResult(false, translate(CloudApi.errorText(r)))
            // "Confirm email" dimatikan di server -> langsung dapat sesi.
            if (applyTokenResponse(r.body)) return AuthResult(true)
            return AuthResult(true, "Kode 6 digit dikirim ke ${email.trim()}. Cek kotak masuk / spam.", needsCode = true)
        } finally {
            busy = false
        }
    }

    /** Memasukkan kode OTP pendaftaran. */
    suspend fun verifySignupCode(email: String, code: String): AuthResult =
        verifyCode(email, code, "signup")

    suspend fun resendSignupCode(email: String, manageBusy: Boolean = true): AuthResult {
        if (manageBusy) busy = true
        try {
            val r = CloudApi.json(
                "POST", "/auth/v1/resend",
                JSONObject().put("type", "signup").put("email", email.trim()).toString()
            )
            return if (r.ok) AuthResult(true, "Kode baru dikirim.") else AuthResult(false, translate(CloudApi.errorText(r)))
        } finally {
            if (manageBusy) busy = false
        }
    }

    /** Lupa password: kirim kode OTP ke email (template "Reset password" harus memakai {{ .Token }}). */
    suspend fun sendPasswordReset(email: String): AuthResult {
        if (!email.contains("@")) return AuthResult(false, "Isi emailnya dulu di kolom email")
        busy = true
        try {
            val r = CloudApi.json(
                "POST", "/auth/v1/recover",
                JSONObject().put("email", email.trim()).toString()
            )
            return if (r.ok) AuthResult(true, "Kode 6 digit dikirim ke ${email.trim()}.", needsCode = true)
            else AuthResult(false, translate(CloudApi.errorText(r)))
        } finally {
            busy = false
        }
    }

    /** Memasukkan kode OTP reset password. Berhasil = otomatis masuk, lalu diminta password baru. */
    suspend fun verifyRecoveryCode(email: String, code: String): AuthResult {
        val r = verifyCode(email, code, "recovery")
        if (r.ok) recoveryMode = true
        return r
    }

    private suspend fun verifyCode(email: String, code: String, type: String): AuthResult {
        val clean = code.filter { it.isDigit() }
        if (clean.length < 6) return AuthResult(false, "Masukkan kode 6 digit dari email")
        busy = true
        try {
            val r = CloudApi.json(
                "POST", "/auth/v1/verify",
                JSONObject().put("type", type).put("email", email.trim()).put("token", clean).toString()
            )
            if (!r.ok) return AuthResult(false, translate(CloudApi.errorText(r)))
            return if (applyTokenResponse(r.body)) AuthResult(true) else AuthResult(false, "Respons verifikasi tidak dikenali")
        } finally {
            busy = false
        }
    }

    suspend fun updatePassword(newPassword: String): AuthResult {
        if (newPassword.length < MIN_PASSWORD) return AuthResult(false, "Password minimal $MIN_PASSWORD karakter")
        val token = validToken() ?: return AuthResult(false, "Sesi habis, masuk lagi ya")
        busy = true
        try {
            val r = CloudApi.json("PUT", "/auth/v1/user", JSONObject().put("password", newPassword).toString(), token)
            if (r.ok) recoveryMode = false
            return if (r.ok) AuthResult(true, "Password diperbarui") else AuthResult(false, translate(CloudApi.errorText(r)))
        } finally {
            busy = false
        }
    }

    // ---------------------------------------------------------------- google

    /**
     * Login Google di dalam aplikasi (tanpa browser): ID token dari Google dikirim ke server.
     * Hasil dengan pesan kosong = pengguna membatalkan sendiri (tidak perlu menampilkan error).
     */
    suspend fun signInWithGoogleNative(context: Context): AuthResult {
        if (busy) return AuthResult(false)
        busy = true
        message = null
        try {
            val rawNonce = b64(ByteArray(32).also { SecureRandom().nextBytes(it) })
            val hashed = MessageDigest.getInstance("SHA-256").digest(rawNonce.toByteArray(Charsets.UTF_8))
                .joinToString("") { "%02x".format(it) }
            val idToken = try {
                GoogleSignIn.idToken(context, hashed)
            } catch (e: CancellationException) {
                throw e
            } catch (_: GetCredentialCancellationException) {
                return AuthResult(false)
            } catch (_: NoCredentialException) {
                return AuthResult(false, "Tidak ada akun Google di HP ini. Tambahkan dulu di Setelan HP.")
            } catch (e: GetCredentialException) {
                return AuthResult(false, com.jimz.jimztune.util.UserError.report("Login Google", "Login Google gagal. Coba lagi.", e))
            } catch (e: Exception) {
                return AuthResult(false, com.jimz.jimztune.util.UserError.report("Login Google", "Login Google gagal. Coba lagi.", e))
            }
            val r = CloudApi.json(
                "POST", "/auth/v1/token?grant_type=id_token",
                JSONObject().put("provider", "google").put("id_token", idToken).put("nonce", rawNonce).toString()
            )
            return if (r.ok && applyTokenResponse(r.body)) AuthResult(true)
            else AuthResult(false, translate(CloudApi.errorText(r)))
        } finally {
            busy = false
        }
    }

    /**
     * Dipanggil MainActivity saat aplikasi dibuka lewat jimztune://auth.
     * KEAMANAN: tautan ini TIDAK PERNAH lagi dipakai untuk masuk. Skema kustom bisa dipicu aplikasi lain atau halaman web
     * mana pun; kalau menerima access_token / kode dari sana, penyerang bisa memasukkan HP korban ke akun milik penyerang
     * lalu menyedot favorit, playlist, dan riwayat korban lewat sinkronisasi. Login hanya lewat kode OTP email dan Google native.
     */
    suspend fun handleCallback(uri: Uri) {
        if (uri.scheme != "jimztune" || uri.host != "auth") return
        message = "Masuk lewat tautan sudah tidak dipakai. Gunakan kode 6 digit yang dikirim ke emailmu."
    }

    // ---------------------------------------------------------------- sesi

    /** Token yang masih berlaku (diperbarui otomatis kalau hampir habis). Null kalau belum login / gagal. */
    suspend fun validToken(): String? {
        val s = session ?: return null
        if (s.expiresAt - System.currentTimeMillis() / 1000 > 60) return s.accessToken
        return refresh()?.accessToken
    }

    private suspend fun refresh(): Session? = refreshLock.withLock {
        val current = session ?: return@withLock null
        if (current.expiresAt - System.currentTimeMillis() / 1000 > 60) return@withLock current
        val r = CloudApi.json(
            "POST", "/auth/v1/token?grant_type=refresh_token",
            JSONObject().put("refresh_token", current.refreshToken).toString()
        )
        when {
            r.ok -> if (applyTokenResponse(r.body, notify = false)) session else null
            r.offline || r.code >= 500 -> null
            else -> {
                clearSession()
                null
            }
        }
    }

    suspend fun signOut(): AuthResult {
        val token = session?.accessToken ?: return AuthResult(true)
        // Akun yang sedang diblokir tidak bisa menyimpan ke cloud, tapi tetap harus bisa keluar.
        if (!AppGraph.account.blocked && !AppGraph.cloud.syncNow()) {
            return AuthResult(false, "Data belum bisa disimpan ke cloud. Sambungkan internet lalu coba keluar lagi.")
        }
        CloudApi.json("POST", "/auth/v1/logout", token = token)
        clearSession()
        AppGraph.cloud.onLogout()
        return AuthResult(true)
    }

    private fun applyTokenResponse(text: String, notify: Boolean = true): Boolean {
        val o = try { JSONObject(text) } catch (_: Exception) { return false }
        val access = o.optString("access_token")
        if (access.isEmpty()) return false
        val user = o.optJSONObject("user")
        val expires = if (o.has("expires_at")) o.optLong("expires_at")
        else System.currentTimeMillis() / 1000 + o.optLong("expires_in", 3600)
        val prev = session
        val s = Session(
            accessToken = access,
            refreshToken = o.optString("refresh_token"),
            expiresAt = expires,
            userId = user?.optString("id").orEmpty().ifEmpty { prev?.userId.orEmpty() },
            email = user?.optString("email").orEmpty().ifEmpty { prev?.email.orEmpty() }
        )
        if (notify) applySession(s) else saveSession(s)  // ← fix
        return true
    }

    private fun applySession(s: Session) {  // ← fix: renamed dari setSession
        saveSession(s)
        loginRequested = false
        message = null
        onboardingDone = true
        prefs.edit().putBoolean("onboarding_done", true).apply()
        AppGraph.cloud.onLogin()
    }

    private fun saveSession(s: Session) {
        session = s
        val json = JSONObject()
            .put("access", s.accessToken).put("refresh", s.refreshToken)
            .put("expires", s.expiresAt).put("uid", s.userId).put("email", s.email)
            .toString()
        // Disimpan terenkripsi (Android Keystore). Kalau enkripsi gagal, sesi hanya hidup di memori (login ulang saat aplikasi dibuka lagi).
        val enc = SecureStore.encrypt(json)
        val e = prefs.edit().remove("access").remove("refresh").remove("expires").remove("uid").remove("email")
        if (enc != null) e.putString("sess", enc) else e.remove("sess")
        e.apply()
    }

    private fun clearSession() {
        session = null
        recoveryMode = false
        prefs.edit().remove("sess").remove("access").remove("refresh").remove("expires").remove("uid").remove("email").apply()
    }

    private fun loadSession(): Session? {
        prefs.getString("sess", null)?.let { blob ->
            val o = try { SecureStore.decrypt(blob)?.let { JSONObject(it) } } catch (_: Exception) { null }
            if (o == null) {
                prefs.edit().remove("sess").apply()
                return null
            }
            return Session(o.optString("access"), o.optString("refresh"), o.optLong("expires"), o.optString("uid"), o.optString("email"))
        }
        // Migrasi dari versi lama (token polos): enkripsi lalu hapus versi polosnya.
        val access = prefs.getString("access", null) ?: return null
        val refresh = prefs.getString("refresh", null) ?: return null
        val s = Session(
            access, refresh, prefs.getLong("expires", 0),
            prefs.getString("uid", "").orEmpty(), prefs.getString("email", "").orEmpty()
        )
        val enc = SecureStore.encrypt(
            JSONObject().put("access", s.accessToken).put("refresh", s.refreshToken)
                .put("expires", s.expiresAt).put("uid", s.userId).put("email", s.email).toString()
        )
        prefs.edit().remove("access").remove("refresh").remove("expires").remove("uid").remove("email")
            .also { if (enc != null) it.putString("sess", enc) }.apply()
        return s
    }

    // ---------------------------------------------------------------- util

    private fun checkInput(email: String, password: String): AuthResult? = when {
        !email.contains("@") || !email.contains(".") -> AuthResult(false, "Format email belum benar")
        password.length < MIN_PASSWORD -> AuthResult(false, "Password minimal $MIN_PASSWORD karakter")
        else -> null
    }

    private fun translate(raw: String): String {
        val t = raw.lowercase()
        return when {
            "invalid login credentials" in t -> "Email atau password salah"
            "email not confirmed" in t -> "Email belum dikonfirmasi. Cek kotak masukmu."
            "already registered" in t || "already been registered" in t -> "Email ini sudah terdaftar, silakan masuk"
            "rate limit" in t || "too many" in t -> "Terlalu sering mencoba, tunggu sebentar ya"
            "token has expired" in t || "otp_expired" in t || "invalid" in t && "token" in t -> "Kode salah atau sudah kedaluwarsa"
            "after" in t && "second" in t -> "Tunggu sekitar semenit sebelum minta kode lagi"
            "password should be" in t -> "Password terlalu lemah"
            "unable to validate email" in t || "invalid email" in t -> "Format email belum benar"
            "provider is not enabled" in t || "unsupported provider" in t -> "Login Google belum diaktifkan di server"
            else -> raw
        }
    }

    private fun parseParams(raw: String?): Map<String, String> {
        if (raw.isNullOrEmpty()) return emptyMap()
        return raw.split("&").mapNotNull {
            val i = it.indexOf('=')
            if (i <= 0) null else Uri.decode(it.substring(0, i)) to Uri.decode(it.substring(i + 1).replace("+", " "))
        }.toMap()
    }

    private fun enc(s: String) = URLEncoder.encode(s, "UTF-8")
    private fun b64(b: ByteArray) = Base64.encodeToString(b, Base64.URL_SAFE or Base64.NO_PADDING or Base64.NO_WRAP)

    companion object {
        const val REDIRECT = "jimztune://auth"
        const val MIN_PASSWORD = 8
        const val REQUIRE_LOGIN = false
    }
}
