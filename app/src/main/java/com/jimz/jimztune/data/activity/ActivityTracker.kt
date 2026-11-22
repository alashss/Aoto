package com.jimz.jimztune.data.activity

import com.jimz.jimztune.AppGraph
import com.jimz.jimztune.data.cloud.CloudApi
import com.jimz.jimztune.data.model.SearchItem
import kotlinx.coroutines.CoroutineScope
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.Job
import kotlinx.coroutines.SupervisorJob
import kotlinx.coroutines.delay
import kotlinx.coroutines.isActive
import kotlinx.coroutines.launch
import org.json.JSONObject
import java.util.UUID

/**
 * Mencatat aktivitas user yang sudah login untuk panel owner:
 *  - "detak" tiap ±60 detik selama aplikasi terbuka / musik diputar (dipakai untuk status online & kapan terakhir aktif)
 *  - lagu apa yang diputar dan jam berapa
 * Semuanya best-effort: gagal / offline tidak mengganggu pemakaian aplikasi.
 */
class ActivityTracker {
    private val scope = CoroutineScope(SupervisorJob() + Dispatchers.IO)
    private val sessionId = UUID.randomUUID().toString()
    private var beat: Job? = null
    private var lastSong: String? = null
    private var lastSongAt = 0L

    fun start() {
        if (beat?.isActive == true) return
        beat = scope.launch {
            while (isActive) {
                touch()
                delay(BEAT_MS)
            }
        }
    }

    fun stop() {
        finishCurrent()
        beat?.cancel()
        beat = null
    }

    private suspend fun touch() {
        if (!AppGraph.auth.isLoggedIn || AppGraph.account.blocked) return
        val token = AppGraph.auth.validToken() ?: return
        CloudApi.json(
            "POST", "/rest/v1/rpc/touch_session",
            JSONObject().put("p_id", sessionId).put("p_version", AppGraph.remote.versionName).toString(),
            token
        )
        pushPresence()
    }

    // Kehadiran untuk teman: lagu yang sedang didengar + status online. Best-effort, gagal diabaikan.
    private var curId: String? = null
    private var curTitle: String? = null
    private var curArtist: String? = null
    private var curThumb: String? = null
    private var curPlaying = false

    private suspend fun pushPresence() {
        try {
            com.jimz.jimztune.data.social.SocialApi.setPresence(curId, curTitle, curArtist, curThumb, curPlaying)
        } catch (_: Exception) {
        }
    }

    /** Dipanggil saat tombol putar/jeda berubah supaya status "sedang mendengarkan" ikut berubah. */
    fun setPlaying(playing: Boolean) {
        if (curPlaying == playing) return
        curPlaying = playing
        if (!AppGraph.auth.isLoggedIn) return
        scope.launch { pushPresence() }
    }

    /** Id putaran di server untuk lagu yang sedang berjalan, dan detik dengar saat mulai (untuk menghitung lamanya). */
    private var currentEventId = 0L
    private var currentStartListen = 0L

    /** Dipanggil setiap lagu mulai diputar. Menutup putaran sebelumnya (mengisi lama didengar) lalu membuka yang baru. */
    fun logPlay(song: SearchItem) {
        val session = AppGraph.auth.session ?: return
        if (AppGraph.account.blocked) return
        val now = System.currentTimeMillis()
        // Lagu yang sama dalam 5 detik dianggap satu kali putar (event pemutar kadang muncul dua kali).
        if (song.id == lastSong && now - lastSongAt < 5_000) return
        lastSong = song.id
        lastSongAt = now
        curId = song.id
        curTitle = song.title
        curArtist = song.subtitle.substringBefore(" • ")
        curThumb = song.thumbnailUrl
        curPlaying = true
        scope.launch { pushPresence() }
        val closeId = currentEventId
        val closeSec = (AppGraph.listen.totalSeconds - currentStartListen).coerceAtLeast(0)
        currentEventId = 0L
        currentStartListen = AppGraph.listen.totalSeconds
        scope.launch {
            val token = AppGraph.auth.validToken() ?: return@launch
            if (closeId > 0 && closeSec > 0) finish(token, closeId, closeSec)
            val body = JSONObject()
                .put("p_song_id", song.id)
                .put("p_title", song.title)
                .put("p_artist", song.subtitle.substringBefore(" • "))
                .put("p_thumbnail", song.thumbnailUrl ?: JSONObject.NULL)
            val r = CloudApi.json("POST", "/rest/v1/rpc/log_play", body.toString(), token)
            if (r.ok) currentEventId = r.body.trim().toLongOrNull() ?: 0L
            else if (r.code == 404) {
                // Server belum menjalankan tahap23_riwayat_rapi.sql: pakai cara lama supaya riwayat tetap tercatat.
                val legacy = JSONObject()
                    .put("user_id", session.userId)
                    .put("song_id", song.id)
                    .put("title", song.title)
                    .put("artist", song.subtitle.substringBefore(" • "))
                    .put("thumbnail", song.thumbnailUrl ?: JSONObject.NULL)
                CloudApi.json("POST", "/rest/v1/play_events", legacy.toString(), token, mapOf("Prefer" to "return=minimal"))
            }
        }
    }

    /** Menutup putaran yang sedang berjalan (aplikasi ditutup / keluar akun). */
    fun finishCurrent() {
        val id = currentEventId
        val sec = (AppGraph.listen.totalSeconds - currentStartListen).coerceAtLeast(0)
        currentEventId = 0L
        if (id <= 0 || sec <= 0) return
        scope.launch {
            val token = AppGraph.auth.validToken() ?: return@launch
            finish(token, id, sec)
        }
    }

    private suspend fun finish(token: String, id: Long, seconds: Long) {
        CloudApi.json(
            "POST", "/rest/v1/rpc/finish_play",
            JSONObject().put("p_id", id).put("p_seconds", seconds.toInt()).toString(), token
        )
    }

    private companion object {
        const val BEAT_MS = 60_000L
    }
}
