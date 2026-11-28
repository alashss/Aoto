package com.jimz.jimztune.util

import android.content.Context
import android.os.Handler
import android.os.Looper
import android.widget.Toast
import com.google.firebase.FirebaseApp
import com.google.firebase.messaging.FirebaseMessaging
import com.jimz.jimztune.AppGraph
import com.jimz.jimztune.data.social.SocialApi
import kotlin.coroutines.resume
import kotlinx.coroutines.suspendCancellableCoroutine

/** Mendaftarkan token FCM perangkat ke server (supaya server bisa mengirim push chat). Aman dipanggil berulang kali. */
object PushRegistrar {
    private const val PREFS = "push_state"
    private var lastTry = 0L

    private fun prefs(c: Context) = c.applicationContext.getSharedPreferences(PREFS, Context.MODE_PRIVATE)

    /** Firebase hanya ada kalau build memakai google-services.json. */
    private fun available(c: Context): Boolean =
        try { FirebaseApp.getApps(c.applicationContext).isNotEmpty() } catch (_: Throwable) { false }

    private suspend fun fetchToken(): String? = suspendCancellableCoroutine { cont ->
        try {
            FirebaseMessaging.getInstance().token.addOnCompleteListener { t ->
                if (cont.isActive) cont.resume(if (t.isSuccessful) t.result else null)
            }
        } catch (_: Throwable) {
            if (cont.isActive) cont.resume(null)
        }
    }

    /** Info diagnosis singkat (Toast) tiap kali status berubah, supaya penyebab push tidak jalan terlihat tanpa log. */
    private fun report(c: Context, status: String) {
        val p = prefs(c)
        if (p.getString("status", null) == status) return
        p.edit().putString("status", status).apply()
        Handler(Looper.getMainLooper()).post {
            try { Toast.makeText(c.applicationContext, "Push: $status", Toast.LENGTH_LONG).show() } catch (_: Exception) { }
        }
    }

    /** Daftarkan kalau token / akun berubah. */
    suspend fun ensure(context: Context) {
        val app = context.applicationContext
        if (!AppGraph.auth.isLoggedIn) return
        if (!available(app)) { report(app, "Firebase tidak aktif di APK ini (google-services.json tidak ikut build)"); return }
        val uid = AppGraph.auth.session?.userId.orEmpty()
        val p = prefs(app)
        var token = p.getString("token", null)
        if (token != null && p.getString("reg", null) == "$token|$uid") return
        // Batasi percobaan (jaringan mati / SQL belum dijalankan): paling sering sekali per menit.
        val now = System.currentTimeMillis()
        if (now - lastTry < 60_000) return
        lastTry = now
        if (token == null) {
            token = fetchToken()
            if (token == null) { report(app, "gagal mengambil token dari Firebase"); return }
            p.edit().putString("token", token).apply()
        }
        val err = SocialApi.pushRegister(token)
        if (err == null) {
            p.edit().putString("reg", "$token|$uid").apply()
            report(app, "aktif ✓")
        } else report(app, "gagal daftar ke server: ${err.take(80)}")
    }

    /** Token baru dari Firebase. */
    suspend fun onNewToken(context: Context, token: String) {
        prefs(context).edit().putString("token", token).remove("reg").apply()
        lastTry = 0L
        ensure(context)
    }

    /** Dipanggil sebelum keluar akun: hapus token perangkat ini dari akun tersebut. */
    suspend fun unregister(context: Context) {
        val app = context.applicationContext
        val p = prefs(app)
        val token = p.getString("token", null) ?: return
        try { SocialApi.pushUnregister(token) } catch (_: Exception) { }
        p.edit().remove("reg").apply()
    }
}
