package com.jimz.jimztune.util

import com.google.firebase.messaging.FirebaseMessagingService
import com.google.firebase.messaging.RemoteMessage
import com.jimz.jimztune.AppGraph
import kotlinx.coroutines.CoroutineScope
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.SupervisorJob
import kotlinx.coroutines.launch

/** Menerima push FCM (pesan chat teman) dan menampilkannya sebagai notifikasi AOTO. */
class PushService : FirebaseMessagingService() {
    private val scope = CoroutineScope(SupervisorJob() + Dispatchers.IO)

    override fun onNewToken(token: String) {
        AppGraph.init(applicationContext)
        scope.launch { try { PushRegistrar.onNewToken(applicationContext, token) } catch (_: Exception) { } }
    }

    override fun onMessageReceived(message: RemoteMessage) {
        val d = message.data
        if (d["type"] != "dm") return
        val sender = d["sender"].orEmpty()
        if (sender.isBlank()) return
        AppGraph.init(applicationContext)
        // Aplikasi sedang terlihat: lencana di dalam aplikasi sudah cukup.
        if (AppGraph.appVisible) return
        ChatNotifier.showPush(applicationContext, sender, d["name"].orEmpty().ifBlank { "Pesan baru" }, d["body"].orEmpty().ifBlank { "Pesan baru" })
    }
}
