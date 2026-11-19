package com.jimz.jimztune.player

import android.content.Context
import android.os.Handler
import android.os.Looper
import android.widget.Toast
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.setValue
import androidx.media3.common.MediaItem
import androidx.media3.common.Player
import com.jimz.jimztune.data.search.Http
import kotlinx.coroutines.CoroutineScope
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.Job
import kotlinx.coroutines.SupervisorJob
import kotlinx.coroutines.cancel
import kotlinx.coroutines.launch
import org.json.JSONArray
import java.security.MessageDigest

object SponsorPrefs {
    private var prefs: android.content.SharedPreferences? = null
    var enabled by mutableStateOf(false); private set

    fun init(context: Context) {
        if (prefs != null) return
        val p = context.applicationContext.getSharedPreferences("jimztune_sponsor", Context.MODE_PRIVATE)
        prefs = p
        enabled = p.getBoolean("enabled", false)
    }

    fun update(v: Boolean) {
        enabled = v
        prefs?.edit()?.putBoolean("enabled", v)?.apply()
    }
}

/**
 * Melewati bagian non-musik di video YouTube (omongan, iklan sponsor, promosi diri) memakai data komunitas SponsorBlock
 * (sponsor.ajay.app). Hanya id lagu (dipotong jadi 4 karakter hash) yang dikirim, jadi privasi tetap terjaga.
 * Mati secara bawaan.
 */
class SponsorBlock(private val context: Context, private val player: Player) {
    private val handler = Handler(Looper.getMainLooper())
    private val scope = CoroutineScope(SupervisorJob() + Dispatchers.Main)
    private var job: Job? = null
    private var forId: String? = null
    private var segments: List<LongArray> = emptyList() // [mulaiMs, akhirMs]
    private var lastSkipEnd = -1L
    private var toasted = false

    private val tick = object : Runnable {
        override fun run() {
            check()
            handler.postDelayed(this, 500L)
        }
    }

    private val listener = object : Player.Listener {
        override fun onMediaItemTransition(mediaItem: MediaItem?, reason: Int) {
            segments = emptyList()
            forId = null
            lastSkipEnd = -1L
            toasted = false
            fetch(mediaItem?.mediaId)
        }
    }

    fun start() {
        player.addListener(listener)
        handler.postDelayed(tick, 500L)
        fetch(player.currentMediaItem?.mediaId)
    }

    fun release() {
        handler.removeCallbacks(tick)
        player.removeListener(listener)
        scope.cancel()
    }

    private fun fetch(id: String?) {
        job?.cancel()
        if (id.isNullOrBlank() || !SponsorPrefs.enabled || id == forId) return
        job = scope.launch {
            try {
                val hash = MessageDigest.getInstance("SHA-256").digest(id.toByteArray())
                    .joinToString("") { "%02x".format(it) }.take(4)
                val cats = java.net.URLEncoder.encode("[\"music_offtopic\",\"sponsor\",\"selfpromo\"]", "UTF-8")
                val body = Http.get("https://sponsor.ajay.app/api/skipSegments/$hash?categories=$cats")
                val arr = JSONArray(body)
                val out = mutableListOf<LongArray>()
                for (i in 0 until arr.length()) {
                    val v = arr.optJSONObject(i) ?: continue
                    if (v.optString("videoID") != id) continue
                    val segs = v.optJSONArray("segments") ?: continue
                    for (j in 0 until segs.length()) {
                        val pair = segs.optJSONObject(j)?.optJSONArray("segment") ?: continue
                        val a = (pair.optDouble(0) * 1000).toLong()
                        val b = (pair.optDouble(1) * 1000).toLong()
                        if (b > a) out += longArrayOf(a, b)
                    }
                }
                forId = id
                segments = out.sortedBy { it[0] }
            } catch (_: Exception) {
                // Tidak ada data untuk video ini (404) atau tidak ada internet: biarkan putar normal.
                forId = id
            }
        }
    }

    private fun check() {
        if (!SponsorPrefs.enabled || segments.isEmpty() || !player.isPlaying) return
        if (player.currentMediaItem?.mediaId != forId) return
        val pos = player.currentPosition
        val seg = segments.firstOrNull { pos >= it[0] && pos < it[1] - 400L } ?: return
        if (seg[1] == lastSkipEnd) return
        lastSkipEnd = seg[1]
        val dur = player.duration
        if (dur > 0 && seg[1] >= dur - 1000L) player.seekToNext() else player.seekTo(seg[1])
        if (!toasted) {
            toasted = true
            try {
                Toast.makeText(context, "Bagian non-musik dilewati", Toast.LENGTH_SHORT).show()
            } catch (_: Exception) {
            }
        }
    }
}
