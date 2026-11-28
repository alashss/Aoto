package com.jimz.jimztune.util

import android.content.BroadcastReceiver
import android.content.Context
import android.content.Intent
import androidx.core.app.RemoteInput
import com.jimz.jimztune.AppGraph
import com.jimz.jimztune.data.social.SocialApi
import kotlinx.coroutines.CoroutineScope
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.launch

/** Aksi di notifikasi chat: balas langsung (seperti WhatsApp) dan tandai dibaca. */
class ChatReplyReceiver : BroadcastReceiver() {
    override fun onReceive(context: Context, intent: Intent) {
        val uid = intent.getStringExtra(ChatNotifier.EXTRA_USER) ?: return
        val name = intent.getStringExtra(ChatNotifier.EXTRA_NAME).orEmpty()
        val last = intent.getStringExtra(ChatNotifier.EXTRA_LAST).orEmpty()
        val action = intent.action
        val text = RemoteInput.getResultsFromIntent(intent)?.getCharSequence(ChatNotifier.KEY_REPLY)?.toString()?.trim().orEmpty()
        val app = context.applicationContext
        val pending = goAsync()
        CoroutineScope(Dispatchers.IO).launch {
            try {
                AppGraph.init(app)
                if (action == ChatNotifier.ACTION_READ || text.isBlank()) {
                    SocialApi.dmMarkRead(uid)
                    ChatNotifier.clear(app, uid)
                } else {
                    val err = SocialApi.dmSend(uid, text)
                    if (err == null) SocialApi.dmMarkRead(uid)
                    ChatNotifier.afterReply(app, uid, name, last, text, err)
                }
            } catch (e: Exception) {
                if (text.isNotBlank()) ChatNotifier.afterReply(app, uid, name, last, text, "periksa koneksi")
            } finally {
                pending.finish()
            }
        }
    }
}
