package com.jimz.jimztune.data.stats

import android.content.Context
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.setValue
import java.time.LocalDate

data class FlameTier(val level: Int, val name: String, val emoji: String)

data class StreakInfo(
    val current: Int,
    val best: Int,
    val listenedToday: Boolean,
    /** Kemarin terlewat dan masih bisa dipulihkan. */
    val canRecover: Boolean,
    val recoveriesLeft: Int,
    val tier: FlameTier,
    /** Detik mendengar hari ini. */
    val todaySeconds: Int = 0,
    /** Berapa dari 7 hari terakhir (termasuk hari ini) yang dihitung. */
    val weekCount: Int = 0,
    /** Sisa jam sebelum hari ini berganti; dipakai untuk peringatan "streak hampir putus". */
    val hoursLeft: Int = 24
) {
    /** 0..1: kemajuan menuju menit minimal hari ini. */
    val todayProgress: Float get() = (todaySeconds / 60f).coerceIn(0f, 1f)

    /** Streak aktif tapi hari ini belum tercatat dan sisa waktunya tinggal sedikit. */
    val atRisk: Boolean get() = current > 0 && !listenedToday && hoursLeft <= 6
}

/**
 * Streak: berapa hari berturut-turut kamu mendengarkan (minimal 1 menit per hari).
 * Satu hari yang terlewat bisa dipulihkan, maksimal [MAX_RECOVER] kali per bulan.
 */
object Streak {
    private const val MIN_SECONDS = 60
    private const val MAX_RECOVER = 2
    private lateinit var appCtx: Context
    private val prefs by lazy { appCtx.getSharedPreferences("jimztune_streak", Context.MODE_PRIVATE) }

    /** Dipanggil AppGraph dan widget streak (yang bisa berjalan tanpa aplikasi terbuka). */
    fun init(context: Context) {
        if (!::appCtx.isInitialized) appCtx = context.applicationContext
    }

    var enabled by mutableStateOf(true)
        private set
    private var loaded = false

    private fun ensure() {
        if (loaded) return
        loaded = true
        enabled = prefs.getBoolean("enabled", true)
    }

    fun isEnabled(): Boolean {
        ensure()
        return enabled
    }

    fun updateEnabled(v: Boolean) {
        ensure()
        enabled = v
        prefs.edit().putBoolean("enabled", v).apply()
        com.jimz.jimztune.AppGraph.cloud.markStatsDirty()
    }

    /** Hari yang dipulihkan, dalam bentuk yang dikirim ke cloud. */
    fun recoveredList(): List<String> = recovered().toList()

    /** Menerapkan keadaan dari cloud: pemulihan digabung (tidak pernah dikurangi), saklar hanya kalau [applyEnabled]. */
    fun mergeState(cloudRecovered: Collection<String>, cloudEnabled: Boolean?, applyEnabled: Boolean) {
        ensure()
        val valid = cloudRecovered.filter { runCatching { LocalDate.parse(it) }.isSuccess }
        val merged = recovered() + valid
        val e = prefs.edit()
        if (merged.size != recovered().size) e.putStringSet("recovered", merged)
        if (applyEnabled && cloudEnabled != null && cloudEnabled != enabled) {
            enabled = cloudEnabled
            e.putBoolean("enabled", cloudEnabled)
        }
        e.apply()
        PlayLog.touch()
    }

    /** Keluar akun: kembalikan ke keadaan awal. */
    fun clear() {
        ensure()
        enabled = true
        prefs.edit().clear().apply()
        PlayLog.touch()
    }

    private fun recovered(): Set<String> = prefs.getStringSet("recovered", emptySet()) ?: emptySet()

    private fun qualifies(d: LocalDate, rec: Set<String>) = PlayLog.secondsOn(d) >= MIN_SECONDS || d.toString() in rec

    fun tierOf(n: Int): FlameTier = when {
        n >= 100 -> FlameTier(6, "Api biru", "🔵🔥")
        n >= 30 -> FlameTier(5, "Berkobar", "🔥")
        n >= 14 -> FlameTier(4, "Besar", "🔥")
        n >= 7 -> FlameTier(3, "Menyala", "🔥")
        n >= 3 -> FlameTier(2, "Kecil", "🔥")
        n >= 1 -> FlameTier(1, "Bara", "✨")
        else -> FlameTier(0, "Padam", "🕯️")
    }

    fun info(): StreakInfo {
        // Membaca version supaya komposisi yang memanggil ini ikut diperbarui saat ada catatan baru.
        @Suppress("UNUSED_VARIABLE") val v = PlayLog.version
        val rec = recovered()
        val today = LocalDate.now()
        val listenedToday = qualifies(today, rec)
        var d = if (listenedToday) today else today.minusDays(1)
        var n = 0
        while (qualifies(d, rec)) {
            n++
            d = d.minusDays(1)
        }
        val usedThisMonth = rec.count { it.startsWith(today.toString().substring(0, 7)) }
        val left = (MAX_RECOVER - usedThisMonth).coerceAtLeast(0)
        val canRecover = !listenedToday && !qualifies(today.minusDays(1), rec) &&
            qualifies(today.minusDays(2), rec) && left > 0

        // Streak terbaik dari seluruh catatan.
        val all = (PlayLog.listenedDays() + rec).mapNotNull { runCatching { LocalDate.parse(it) }.getOrNull() }
            .filter { qualifies(it, rec) }.sorted()
        var best = 0
        var run = 0
        var prev: LocalDate? = null
        for (x in all) {
            run = if (prev != null && prev.plusDays(1) == x) run + 1 else 1
            best = maxOf(best, run)
            prev = x
        }
        best = maxOf(best, n)
        val now = java.time.LocalDateTime.now()
        val hoursLeft = java.time.Duration.between(now, now.toLocalDate().plusDays(1).atStartOfDay()).toHours().toInt()
        val week = (0..6).count { qualifies(today.minusDays(it.toLong()), rec) }
        return StreakInfo(
            n, best, listenedToday, canRecover, left, tierOf(n),
            todaySeconds = PlayLog.secondsOn(today), weekCount = week, hoursLeft = hoursLeft
        )
    }

    /** Apakah hari [d] dihitung dalam streak (cukup mendengarkan, atau sudah dipulihkan). */
    fun counted(d: LocalDate): Boolean = qualifies(d, recovered())

    private val MILESTONES = intArrayOf(1, 3, 7, 14, 30, 100)

    /** Jumlah hari untuk naik ke tingkat api berikutnya, atau null kalau sudah di puncak. */
    fun nextMilestone(n: Int): Int? = MILESTONES.firstOrNull { it > n }

    /** Batas bawah tingkat api yang sedang dipakai (0 kalau belum mulai). */
    fun currentMilestone(n: Int): Int = MILESTONES.lastOrNull { it <= n } ?: 0

    /** Memulihkan kemarin. True kalau berhasil. */
    fun recoverYesterday(): Boolean {
        val i = info()
        if (!i.canRecover) return false
        val set = recovered().toMutableSet()
        set.add(LocalDate.now().minusDays(1).toString())
        prefs.edit().putStringSet("recovered", set).apply()
        PlayLog.touch()
        com.jimz.jimztune.AppGraph.cloud.markStatsDirty(urgent = true)
        return true
    }
}
