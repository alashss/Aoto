package com.jimz.jimztune.ui.social

import androidx.compose.animation.AnimatedContent
import androidx.compose.animation.fadeIn
import androidx.compose.animation.fadeOut
import androidx.compose.animation.slideInHorizontally
import androidx.compose.animation.slideOutHorizontally
import androidx.compose.animation.togetherWith
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.background
import androidx.compose.runtime.Composable
import androidx.compose.runtime.LaunchedEffect
import androidx.compose.ui.Modifier
import androidx.compose.ui.graphics.Color
import com.jimz.jimztune.data.social.SocialNav
import com.jimz.jimztune.data.social.SocialPage

/** Lapisan halaman sosial di atas seluruh aplikasi. Kosong = tidak menggambar apa pun. */
@Composable
fun SocialHost() {
    LaunchedEffect(Unit) { SocialNav.startBadgePolling() }
    val context = androidx.compose.ui.platform.LocalContext.current
    val msg = SocialNav.message
    LaunchedEffect(msg) {
        if (msg != null) {
            android.widget.Toast.makeText(context, msg, android.widget.Toast.LENGTH_SHORT).show()
            SocialNav.message = null
        }
    }
    val top = SocialNav.stack.lastOrNull() ?: return
    Box(Modifier.fillMaxSize().background(Color(0xFF0B0E1F))) {
        AnimatedContent(
            targetState = top,
            transitionSpec = { (slideInHorizontally { it / 4 } + fadeIn()) togetherWith (fadeOut()) },
            label = "social"
        ) { page ->
            val back = { SocialNav.back() }
            when (page) {
                is SocialPage.Profile -> ProfileViewScreen(page.userId, back)
                SocialPage.Inbox -> InboxScreen(back)
                SocialPage.Friends -> FriendsScreen(back)
                SocialPage.GlobalChat -> GlobalChatScreen(back)
                SocialPage.Privacy -> PrivacyScreen(back)
                is SocialPage.Dm -> DmScreen(page.userId, page.name, page.avatar, back)
            }
        }
    }
}
