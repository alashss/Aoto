package com.jimz.jimztune.data.stats

import android.content.Context
import android.os.Handler
import android.os.Looper
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableIntStateOf
import androidx.compose.runtime.setValue
import org.json.JSONObject
import java.io.File
import java.time.LocalDate

data class SongStat(val id: String, val title: String, val artist: String, val art: String?, val seconds: Int, val plays: Int)
data class ArtistStat(val name: String, val seconds: Int, val plays: Int)

/**
 * Catatan dengar di perangkat: per hari, per lagu (detik dan jumlah putar). Dipakai Rekap dan Streak.
 * Hanya disimpan lokal; mulai tercatat sejak versi ini (riwayat lama tidak punya tanggal).
 */
object PlayLog {
    private class Meta(val title: String, val artist: String, val art: String?)

    private val days = HashMap<String, HashMap<String, IntArray>>() // hari -> id -> [detik, putar]
    private val metas = HashMap<String, Meta>()
    private var file: File? = null
    private val main = Handler(Looper.getMainLooper())
    private val saveTask = Runnable { save() }

    /** Naik tiap ada catatan baru; layar yang membacanya ikut menggambar ulang. */
    var version by mutableIntStateOf(0)
        private set

    @Synchronized
    fun init(context: Context) {
        if (file != null) return
        file = File(context.applicationContext.filesDir, "playlog.json")
        try {
            val f = file!!
            if (!f.exists()) return
            val root = JSONObject(f.readText())
            val m = root.optJSONObject("meta")
            m?.keys()?.forEach { id ->
                val o = m.getJSONObject(id)
                metas[id] = Meta(o.optString("t"), o.optString("a"), o.optString("i").ifBlank { null })
            }
            val d = root.optJSONObject("days")
            d?.keys()?.forEach { day ->
                val o = d.getJSONObject(day)
                val map = HashMap<String, IntArray>()
                o.keys().forEach { id ->
                    val a = o.getJSONArray(id)
                    map[id] = intArrayOf(a.optInt(0), a.optInt(1))
                }
                days[day] = map
            }
        } catch (_: Exception) {
            days.clear()
            metas.clear()
        }
    }

    @Synchronized
    fun record(id: String, title: String, artist: String, art: String?, seconds: Int, newPlay: Boolean) {
        if (id.isBlank() || seconds <= 0 && !newPlay) return
        if (com.jimz.jimztune.util.PrivacyPrefs.historyPaused(com.jimz.jimztune.AppGraph.appContext)) return
        val day = LocalDate.now().toString()
        val e = days.getOrPut(day) { HashMap() }.getOrPut(id) { IntArray(2) }
        e[0] += seconds
        if (newPlay) e[1] += 1
        metas[id] = Meta(title, artist, art)
        main.post {
            version++
            main.removeCallbacks(saveTask)
            main.postDelayed(saveTask, 20_000L)
        }
        com.jimz.jimztune.AppGraph.cloud.markStatsDirty()
    }

    /**
     * Data hari-hari terakhir dalam bentuk yang dikirim ke server:
     * { "2026-11-17": { "<id>": {s, p, t, a, i} } }. [daysBack] null = semua hari yang tercatat.
     */
    @Synchronized
    fun exportDays(daysBack: Int?): JSONObject {
        val limit = if (daysBack == null) "" else LocalDate.now().minusDays(daysBack.toLong()).toString()
        val out = JSONObject()
        days.forEach { (day, map) ->
            if (day < limit) return@forEach
            val o = JSONObject()
            map.forEach { (id, v) ->
                if (v[0] <= 0 && v[1] <= 0) return@forEach
                val m = metas[id]
                o.put(
                    id,
                    JSONObject().put("s", v[0]).put("p", v[1])
                        .put("t", m?.title.orEmpty()).put("a", m?.artist.orEmpty()).put("i", m?.art.orEmpty())
                )
            }
            if (o.length() > 0) out.put(day, o)
        }
        return out
    }

    /** Menggabungkan data dari cloud: nilai terbesar per lagu per hari (aman dipanggil berulang). */
    fun mergeFrom(cloud: JSONObject?): Boolean {
        if (cloud == null || cloud.length() == 0) return false
        var changed = false
        synchronized(this) {
            val keep = LocalDate.now().minusDays(800).toString()
            cloud.keys().forEach { day ->
                if (day < keep) return@forEach
                val o = cloud.optJSONObject(day) ?: return@forEach
                val map = days.getOrPut(day) { HashMap() }
                o.keys().forEach { id ->
                    val e = o.optJSONObject(id) ?: return@forEach
                    val sec = e.optInt("s")
                    val pl = e.optInt("p")
                    val cur = map.getOrPut(id) { IntArray(2) }
                    if (sec > cur[0]) { cur[0] = sec; changed = true }
                    if (pl > cur[1]) { cur[1] = pl; changed = true }
                    val t = e.optString("t")
                    if (t.isNotBlank() && metas[id] == null) {
                        metas[id] = Meta(t, e.optString("a"), e.optString("i").ifBlank { null })
                        changed = true
                    }
                }
                if (map.isEmpty()) days.remove(day)
            }
        }
        if (changed) {
            save()
            touch()
        }
        return changed
    }

    /** Mengosongkan seluruh catatan (keluar akun / ganti akun supaya tidak bocor ke akun lain di HP ini). */
    fun clear() {
        synchronized(this) {
            days.clear()
            metas.clear()
            try { file?.delete() } catch (_: Exception) {}
        }
        main.removeCallbacks(saveTask)
        touch()
    }

    /** Memicu tampilan yang membaca [version] untuk menggambar ulang. */
    fun touch() {
        main.post { version++ }
        // Widget streak ikut diperbarui (dibatasi di dalamnya); gagal diam-diam kalau belum diinisialisasi.
        try {
            com.jimz.jimztune.widget.StreakWidgetProvider.refreshThrottled(com.jimz.jimztune.AppGraph.appContext)
        } catch (_: Throwable) {
        }
    }

    @Synchronized
    fun secondsOn(date: LocalDate): Int = days[date.toString()]?.values?.sumOf { it[0] } ?: 0

    @Synchronized
    fun listenedDays(): Set<String> = days.filter { e -> e.value.values.sumOf { it[0] } > 0 }.keys.toSet()

    @Synchronized
    fun totalSeconds(from: LocalDate, to: LocalDate): Int = inRange(from, to).sumOf { e -> e.value.values.sumOf { it[0] } }

    @Synchronized
    fun activeDays(from: LocalDate, to: LocalDate): Int = inRange(from, to).count { e -> e.value.values.sumOf { it[0] } >= 60 }

    /** Detik mendengar per hari (kunci "yyyy-MM-dd") dalam rentang, hanya hari yang ada catatannya. */
    @Synchronized
    fun secondsByDay(from: LocalDate, to: LocalDate): Map<String, Int> =
        inRange(from, to).associate { e -> e.key to e.value.values.sumOf { it[0] } }.filterValues { it > 0 }

    @Synchronized
    fun topSongs(from: LocalDate, to: LocalDate, n: Int): List<SongStat> {
        val sum = HashMap<String, IntArray>()
        inRange(from, to).forEach { e ->
            e.value.forEach { (id, v) ->
                val a = sum.getOrPut(id) { IntArray(2) }
                a[0] += v[0]
                a[1] += v[1]
            }
        }
        return sum.entries.sortedWith(compareByDescending<Map.Entry<String, IntArray>> { it.value[1] }.thenByDescending { it.value[0] })
            .take(n).map { (id, v) ->
                val m = metas[id]
                SongStat(id, m?.title ?: id, m?.artist.orEmpty(), m?.art, v[0], v[1])
            }
    }

    @Synchronized
    fun topArtists(from: LocalDate, to: LocalDate, n: Int): List<ArtistStat> {
        val sec = HashMap<String, Int>()
        val plays = HashMap<String, Int>()
        inRange(from, to).forEach { e ->
            e.value.forEach { (id, v) ->
                val artist = metas[id]?.artist?.takeIf { it.isNotBlank() } ?: return@forEach
                sec[artist] = (sec[artist] ?: 0) + v[0]
                plays[artist] = (plays[artist] ?: 0) + v[1]
            }
        }
        return sec.entries.sortedByDescending { it.value }.take(n).map { ArtistStat(it.key, it.value, plays[it.key] ?: 0) }
    }

    private fun inRange(from: LocalDate, to: LocalDate): List<Map.Entry<String, HashMap<String, IntArray>>> {
        val a = from.toString()
        val b = to.toString()
        return days.entries.filter { it.key >= a && it.key <= b } // format ISO bisa dibandingkan sebagai teks
    }

    @Synchronized
    private fun save() {
        val f = file ?: return
        try {
            // Buang catatan lebih dari 800 hari supaya berkas tidak membengkak.
            val limit = LocalDate.now().minusDays(800).toString()
            days.keys.removeAll { it < limit }
            val used = days.values.flatMap { it.keys }.toSet()
            val meta = JSONObject()
            used.forEach { id ->
                metas[id]?.let { meta.put(id, JSONObject().put("t", it.title).put("a", it.artist).put("i", it.art ?: "")) }
            }
            val d = JSONObject()
            days.forEach { (day, map) ->
                val o = JSONObject()
                map.forEach { (id, v) -> o.put(id, org.json.JSONArray().put(v[0]).put(v[1])) }
                d.put(day, o)
            }
            f.writeText(JSONObject().put("meta", meta).put("days", d).toString())
        } catch (_: Exception) {
        }
    }

    /** Simpan sekarang (dipanggil saat layanan pemutar berhenti). */
    fun flush() {
        main.removeCallbacks(saveTask)
        save()
    }
}
