package com.jimz.jimztune.data.social

import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateListOf
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.setValue
import com.jimz.jimztune.AppGraph
import kotlinx.coroutines.CoroutineScope
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.SupervisorJob
import kotlinx.coroutines.delay
import kotlinx.coroutines.isActive
import kotlinx.coroutines.launch

sealed interface SocialPage {
    data class Profile(val userId: String) : SocialPage
    data object Inbox : SocialPage
    data object Friends : SocialPage
    data object GlobalChat : SocialPage
    data object Privacy : SocialPage
    data object Cosmetics : SocialPage
    data class Dm(val userId: String, val name: String, val avatar: String?) : SocialPage
}

/**
 * Tumpukan halaman sosial yang tampil sebagai lapisan di atas seluruh aplikasi (lihat SocialHost).
 * Layar mana pun (peringkat, setelan) cukup memanggil [open] tanpa perlu mengoper callback.
 */
object SocialNav {
    val stack = mutableStateListOf<SocialPage>()
    var badges by mutableStateOf(SocialBadges())
        private set
    var message by mutableStateOf<String?>(null)

    val isOpen: Boolean get() = stack.isNotEmpty()

    fun open(page: SocialPage) {
        if (!AppGraph.auth.isLoggedIn) {
            message = "Masuk dulu untuk memakai fitur sosial"
            return
        }
        if (stack.lastOrNull() == page) return
        stack.add(page)
    }

    fun openProfile(userId: String?) {
        if (userId.isNullOrBlank()) return
        open(SocialPage.Profile(userId))
    }

    fun back() {
        if (stack.isNotEmpty()) stack.removeAt(stack.lastIndex)
    }

    fun closeAll() = stack.clear()

    private val scope = CoroutineScope(SupervisorJob() + Dispatchers.IO)
    private var polling = false

    /** Menyegarkan lencana (pesan belum dibaca, permintaan teman) tiap ±15 detik selama aplikasi terbuka. */
    fun startBadgePolling() {
        if (polling) return
        polling = true
        scope.launch {
            while (isActive) {
                if (AppGraph.auth.isLoggedIn) refreshBadges() else badges = SocialBadges()
                delay(15_000)
            }
        }
    }

    suspend fun refreshBadges() {
        val r = SocialApi.badges()
        r.value?.let { badges = it }
    }
}
