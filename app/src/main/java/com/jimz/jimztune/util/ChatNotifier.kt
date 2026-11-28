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
    const val EXTRA_LAST = "dm_last_text"
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

    const val KEY_REPLY = "reply_text"
    const val ACTION_REPLY = "com.jimz.jimztune.DM_REPLY"
    const val ACTION_READ = "com.jimz.jimztune.DM_READ"

    private fun show(app: Context, userId: String, name: String, text: String, unread: Int) {
        if (Build.VERSION.SDK_INT >= 33 &&
            ContextCompat.checkSelfPermission(app, Manifest.permission.POST_NOTIFICATIONS) != PackageManager.PERMISSION_GRANTED
        ) return
        try {
            // Push dan pengecekan berkala bisa membawa pesan yang sama: yang kedua hanya memperbarui isi, tanpa bunyi lagi.
            val sp = app.getSharedPreferences("chat_notified", Context.MODE_PRIVATE)
            val nowMs = System.currentTimeMillis()
            val quiet = nowMs - sp.getLong("shown_$userId", 0L) < 15_000
            sp.edit().putLong("shown_$userId", nowMs).apply()
            post(app, userId, name, text, unread, quiet, mine = null, error = null)
        } catch (_: SecurityException) {
        } catch (_: Exception) {
        }
    }

    /** Dipanggil setelah user membalas dari notifikasi: tampilkan balasannya di percakapan notifikasi (atau pesan gagal). */
    fun afterReply(app: Context, userId: String, name: String, last: String, mine: String, error: String?) {
        try { post(app, userId, name, last, 1, true, mine = mine, error = error) } catch (_: Exception) { }
    }

    private fun post(app: Context, userId: String, name: String, text: String, unread: Int, quiet: Boolean, mine: String?, error: String?) {
        if (Build.VERSION.SDK_INT >= 26) {
            val nm = app.getSystemService(Context.NOTIFICATION_SERVICE) as NotificationManager
            nm.createNotificationChannel(NotificationChannel(CHANNEL, "Pesan teman", NotificationManager.IMPORTANCE_HIGH))
        }
        val open = PendingIntent.getActivity(
            app, userId.hashCode(),
            Intent(app, MainActivity::class.java)
                .addFlags(Intent.FLAG_ACTIVITY_NEW_TASK or Intent.FLAG_ACTIVITY_SINGLE_TOP)
                .putExtra(EXTRA_USER, userId).putExtra(EXTRA_NAME, name),
            PendingIntent.FLAG_UPDATE_CURRENT or PendingIntent.FLAG_IMMUTABLE
        )
        fun receiverIntent(action: String, code: Int, mutable: Boolean): PendingIntent = PendingIntent.getBroadcast(
            app, userId.hashCode() + code,
            Intent(app, ChatReplyReceiver::class.java).setAction(action)
                .putExtra(EXTRA_USER, userId).putExtra(EXTRA_NAME, name).putExtra(EXTRA_LAST, text),
            PendingIntent.FLAG_UPDATE_CURRENT or (if (mutable && Build.VERSION.SDK_INT >= 31) PendingIntent.FLAG_MUTABLE else PendingIntent.FLAG_IMMUTABLE)
        )
        val remote = androidx.core.app.RemoteInput.Builder(KEY_REPLY).setLabel("Balas ke $name").build()
        val replyAction = NotificationCompat.Action.Builder(
            com.jimz.jimztune.R.drawable.ic_stat_aoto, "Balas", receiverIntent(ACTION_REPLY, 1, true)
        ).addRemoteInput(remote).setSemanticAction(NotificationCompat.Action.SEMANTIC_ACTION_REPLY).setAllowGeneratedReplies(true).build()
        val readAction = NotificationCompat.Action.Builder(
            com.jimz.jimztune.R.drawable.ic_stat_aoto, "Tandai dibaca", receiverIntent(ACTION_READ, 2, false)
        ).setSemanticAction(NotificationCompat.Action.SEMANTIC_ACTION_MARK_AS_READ).build()

        val me = androidx.core.app.Person.Builder().setName("Kamu").build()
        val friend = androidx.core.app.Person.Builder().setName(name).setKey(userId).build()
        val style = NotificationCompat.MessagingStyle(me)
            .addMessage(text, System.currentTimeMillis() - (if (mine != null) 2000 else 0), friend)
        if (mine != null) style.addMessage(if (error == null) mine else "Gagal terkirim: $error", System.currentTimeMillis(), null as androidx.core.app.Person?)

        val n = NotificationCompat.Builder(app, CHANNEL)
            .setSmallIcon(com.jimz.jimztune.R.drawable.ic_stat_aoto)
            .setContentTitle(name)
            .setContentText(text)
            .setStyle(style)
            .setNumber(unread)
            .setCategory(NotificationCompat.CATEGORY_MESSAGE)
            .setPriority(NotificationCompat.PRIORITY_HIGH)
            .setAutoCancel(true)
            .setSilent(quiet)
            .setContentIntent(open)
            .addAction(replyAction)
            .addAction(readAction)
            .apply { if (mine != null && error == null) setTimeoutAfter(12_000) }
            .build()
        NotificationManagerCompat.from(app).notify(("dm:" + userId).hashCode(), n)
    }

    /** Hapus notifikasi percakapan ini (dipanggil saat chat-nya dibuka). */
    fun clear(context: Context, userId: String) {
        try { NotificationManagerCompat.from(context.applicationContext).cancel(("dm:" + userId).hashCode()) } catch (_: Exception) { }
    }
}
