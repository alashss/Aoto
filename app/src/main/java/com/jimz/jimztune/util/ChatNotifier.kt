package com.jimz.jimztune.util

import android.Manifest
import android.app.NotificationChannel
import android.app.NotificationManager
import android.app.PendingIntent
import android.content.Context
import android.content.Intent
import android.content.pm.PackageManager
import android.os.Build
import androidx.core.app.NotificationCompat
import androidx.core.app.NotificationManagerCompat
import androidx.core.content.ContextCompat
import com.jimz.jimztune.AppGraph
import com.jimz.jimztune.MainActivity
import com.jimz.jimztune.data.social.SocialApi
import kotlinx.coroutines.CoroutineScope
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.Job
import kotlinx.coroutines.SupervisorJob
import kotlinx.coroutines.delay
import kotlinx.coroutines.isActive
import kotlinx.coroutines.launch
import kotlinx.coroutines.sync.withLock

/**
 * Notifikasi pesan teman. Dicek tiap ±8 detik selama proses aplikasi hidup (mis. musik berjalan di latar) dan tiap
 * ±15 menit oleh [ChatCheckWorker] walau aplikasi sudah ditutup. Notifikasi tidak muncul saat aplikasi sedang terlihat
 * (di sana sudah ada lencana), dan tiap pesan hanya dinotifikasi sekali.
 */
object ChatNotifier {
    private const val CHANNEL = "chat_messages"
    const val EXTRA_USER = "open_dm_user"
    const val EXTRA_NAME = "open_dm_name"
    private var job: Job? = null
    private val scope = CoroutineScope(SupervisorJob() + Dispatchers.IO)

    fun startLoop() {
        if (job?.isActive == true) return
        job = scope.launch {
            while (isActive) {
                try { check(AppGraph.appContext) } catch (_: Exception) { }
                try { PushRegistrar.ensure(AppGraph.appContext) } catch (_: Throwable) { }
                delay(8_000)
            }
        }
    }

    private val lock = kotlinx.coroutines.sync.Mutex()

    suspend fun check(context: Context) = lock.withLock {
        checkLocked(context)
    }

    private suspend fun checkLocked(context: Context) {
        val app = context.applicationContext
        if (!AppGraph.auth.isLoggedIn) return
        val rows = SocialApi.friends().value ?: return
        val prefs = app.getSharedPreferences("chat_notified", Context.MODE_PRIVATE)
        val seeded = prefs.getBoolean("seeded", false)
        val ed = prefs.edit()
        for (r in rows) {
            val at = r.lastAt ?: continue
            if (r.unread <= 0 || r.lastMine) continue
            if (prefs.getString("u_" + r.userId, null) == at) continue
            ed.putString("u_" + r.userId, at)
            if (seeded && !AppGraph.appVisible) show(app, r.userId, r.name, preview(r.lastBody.orEmpty()), r.unread)
        }
        ed.putBoolean("seeded", true).apply()
    }

    /** Teks pratinjau tanpa penanda kutipan / balasan. */
    private fun preview(b: String): String {
        if (b.startsWith("💬 “") || b.startsWith("↩ “")) {
            val reply = b.substringAfter("”‖", "").substringAfter("‖", "").trim()
            val head = if (b.startsWith("💬")) "Membalas catatan: " else ""
            if (reply.isNotBlank()) return head + reply
            return b.substringAfter("”", "").trim().ifBlank { "Pesan baru" }
        }
        return b.ifBlank { "Pesan baru" }
    }

    /** Dari push FCM. */
    fun showPush(app: Context, userId: String, name: String, text: String) = show(app, userId, name, text, 1)

    private fun show(app: Context, userId: String, name: String, text: String, unread: Int) {
        if (Build.VERSION.SDK_INT >= 33 &&
            ContextCompat.checkSelfPermission(app, Manifest.permission.POST_NOTIFICATIONS) != PackageManager.PERMISSION_GRANTED
        ) return
        try {
            if (Build.VERSION.SDK_INT >= 26) {
                val nm = app.getSystemService(Context.NOTIFICATION_SERVICE) as NotificationManager
                nm.createNotificationChannel(NotificationChannel(CHANNEL, "Pesan teman", NotificationManager.IMPORTANCE_HIGH))
            }
            // Push dan pengecekan berkala bisa membawa pesan yang sama: yang kedua hanya memperbarui isi, tanpa bunyi lagi.
            val sp = app.getSharedPreferences("chat_notified", Context.MODE_PRIVATE)
            val nowMs = System.currentTimeMillis()
            val quiet = nowMs - sp.getLong("shown_$userId", 0L) < 15_000
            sp.edit().putLong("shown_$userId", nowMs).apply()
            val open = PendingIntent.getActivity(
                app, userId.hashCode(),
                Intent(app, MainActivity::class.java)
                    .addFlags(Intent.FLAG_ACTIVITY_NEW_TASK or Intent.FLAG_ACTIVITY_SINGLE_TOP)
                    .putExtra(EXTRA_USER, userId).putExtra(EXTRA_NAME, name),
                PendingIntent.FLAG_UPDATE_CURRENT or PendingIntent.FLAG_IMMUTABLE
            )
            val n = NotificationCompat.Builder(app, CHANNEL)
                .setSmallIcon(com.jimz.jimztune.R.drawable.ic_stat_aoto)
                .setContentTitle(name)
                .setContentText(text)
                .setStyle(NotificationCompat.BigTextStyle().bigText(text))
                .setNumber(unread)
                .setCategory(NotificationCompat.CATEGORY_MESSAGE)
                .setPriority(NotificationCompat.PRIORITY_HIGH)
                .setAutoCancel(true)
                .setSilent(quiet)
                .setContentIntent(open)
                .build()
            NotificationManagerCompat.from(app).notify(("dm:" + userId).hashCode(), n)
        } catch (_: SecurityException) {
        } catch (_: Exception) {
        }
    }

    /** Hapus notifikasi percakapan ini (dipanggil saat chat-nya dibuka). */
    fun clear(context: Context, userId: String) {
        try { NotificationManagerCompat.from(context.applicationContext).cancel(("dm:" + userId).hashCode()) } catch (_: Exception) { }
    }
}
