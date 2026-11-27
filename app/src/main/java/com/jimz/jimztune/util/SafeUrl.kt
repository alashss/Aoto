package com.jimz.jimztune.util

import android.net.Uri

/**
 * Gambar yang URL-nya ditentukan pengguna lain (foto profil, banner, sampul) hanya dimuat dari host tepercaya.
 * Tanpa ini, seseorang bisa mengisi URL ke servernya sendiri dan melihat alamat IP setiap orang yang membuka profilnya.
 */
object SafeUrl {
    private val hosts = listOf(
        "supabase.co", "googleusercontent.com", "ggpht.com", "gstatic.com", "ytimg.com", "youtube.com"
    )

    fun image(url: String?): String? {
        if (url.isNullOrBlank() || url.length > 600) return null
        val u = try { Uri.parse(url.trim()) } catch (_: Exception) { return null }
        if (u.scheme != "https") return null
        val host = u.host?.lowercase() ?: return null
        if (u.userInfo != null) return null
        val own = try { Uri.parse(com.jimz.jimztune.AppConfig.CLOUD_URL).host?.lowercase() } catch (_: Exception) { null }
        return if (host == own || hosts.any { host == it || host.endsWith(".$it") }) url.trim() else null
    }
}
