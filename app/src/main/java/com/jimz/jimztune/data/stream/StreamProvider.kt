package com.jimz.jimztune.data.stream

interface StreamProvider {
    val name: String

    /** Mengembalikan URL audio yang bisa langsung diputar untuk video YouTube dengan id tertentu. */
    suspend fun audioUrl(videoId: String): String
}
