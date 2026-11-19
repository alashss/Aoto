package com.jimz.jimztune.data.auth

import com.jimz.jimztune.AppConfig

/**
 * Web Client ID disuntikkan saat build (secret GOOGLE_WEB_CLIENT_ID di GitHub, atau local.properties).
 * Lihat AppConfig.kt dan README.
 */
object GoogleConfig {
    val WEB_CLIENT_ID: String get() = AppConfig.GOOGLE_WEB_CLIENT_ID

    val configured: Boolean get() = WEB_CLIENT_ID.isNotBlank() && !WEB_CLIENT_ID.contains("GANTI_")
}
