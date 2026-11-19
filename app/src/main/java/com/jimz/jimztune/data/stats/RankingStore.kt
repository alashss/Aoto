package com.jimz.jimztune.data.stats

import androidx.compose.runtime.mutableStateMapOf
import com.jimz.jimztune.AppGraph
import com.jimz.jimztune.data.cloud.CloudApi
import kotlinx.coroutines.CoroutineScope
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.SupervisorJob
import kotlinx.coroutines.launch
import org.json.JSONArray
import org.json.JSONObject
import java.util.Locale

enum class RankKind(val key: String, val rpc: String, val title: String, val cardTitle: String) {
    LISTEN("listen", "leaderboard_listen", "Peringkat Mendengar", "TOP PENDENGAR"),
    DONATE("donate", "leaderboard_donations", "Peringkat Donasi", "TOP DONATUR");

    companion object {
        fun fromKey(key: String?): RankKind = entries.firstOrNull { it.key == key } ?: LISTEN
    }
}

enum class RankPeriod(val key: String, val label: String, val short: String) {
    ALL("all", "Semua waktu", "ALL"),
    D30("30d", "30 hari", "30D"),
    D7("7d", "7 hari", "7D");

    companion object {
        fun fromKey(key: String?): RankPeriod = entries.firstOrNull { it.key == key } ?: ALL
    }
}

data class RankRow(
    val rank: Int,
    val userId: String?,
    val name: String,
    val username: String?,
    val avatarUrl: String?,
    val value: Long
)

data class RankBoard(val rows: List<RankRow>, val meRank: Int?, val meValue: Long?)

/** Memformat nilai peringkat: detik -> "12j 40m", donasi -> "Rp 1.250.000". */
fun RankKind.format(value: Long): String = when (this) {
    RankKind.LISTEN -> formatListen(value)
    RankKind.DONATE -> formatRupiah(value)
}

fun formatRupiah(v: Long): String = "Rp " + String.format(Locale("id", "ID"), "%,d", v)

/** Versi pendek untuk kartu kecil: Rp50rb, Rp1,2jt. */
fun formatRupiahShort(v: Long): String = when {
    v >= 1_000_000_000 -> "Rp" + trim1(v / 1_000_000_000.0) + "M"
    v >= 1_000_000 -> "Rp" + trim1(v / 1_000_000.0) + "jt"
    v >= 1_000 -> "Rp" + (v / 1_000) + "rb"
    else -> "Rp$v"
}

private fun trim1(d: Double): String {
    val s = String.format(Locale("id", "ID"), "%.1f", d)
    return if (s.endsWith(",0")) s.dropLast(2) else s
}

fun RankKind.formatShort(value: Long): String = when (this) {
    RankKind.LISTEN -> formatListen(value)
    RankKind.DONATE -> formatRupiahShort(value)
}

/**
 * Pengambil papan peringkat. Hasil terakhir tiap (jenis, periode) disimpan di memori supaya kartu di Beranda
 * dan halaman peringkat langsung tampil, lalu diperbarui di belakang.
 */
class RankingStore {
    private val scope = CoroutineScope(SupervisorJob() + Dispatchers.Main)

    val boards = mutableStateMapOf<String, RankBoard>()
    val failed = mutableStateMapOf<String, Boolean>()
    val loading = mutableStateMapOf<String, Boolean>()
    private val lastLoad = HashMap<String, Long>()

    private fun id(kind: RankKind, period: RankPeriod) = kind.key + ":" + period.key

    fun board(kind: RankKind, period: RankPeriod): RankBoard? = boards[id(kind, period)]
    fun isLoading(kind: RankKind, period: RankPeriod): Boolean = loading[id(kind, period)] == true
    fun hasFailed(kind: RankKind, period: RankPeriod): Boolean = failed[id(kind, period)] == true

    /** [force] = abaikan jeda 30 detik antar pengambilan. */
    fun load(kind: RankKind, period: RankPeriod, force: Boolean = false) {
        val k = id(kind, period)
        val now = System.currentTimeMillis()
        if (loading[k] == true) return
        if (!force && boards[k] != null && now - (lastLoad[k] ?: 0L) < 30_000L) return
        if (!CloudApi.configured) return
        loading[k] = true
        scope.launch {
            try {
                val token = AppGraph.auth.validToken()
                val r = CloudApi.json(
                    "POST", "/rest/v1/rpc/${kind.rpc}",
                    JSONObject().put("p_period", period.key).put("p_limit", 50).toString(), token
                )
                if (r.ok) {
                    boards[k] = parse(r.body)
                    failed[k] = false
                    lastLoad[k] = now
                } else {
                    failed[k] = true
                }
            } catch (_: Exception) {
                failed[k] = true
            } finally {
                loading[k] = false
            }
        }
    }

    private fun JSONObject.s(key: String): String? = if (isNull(key)) null else optString(key).takeIf { it.isNotBlank() }

    private fun parse(text: String): RankBoard {
        val o = JSONObject(text)
        val arr: JSONArray = o.optJSONArray("rows") ?: JSONArray()
        val rows = (0 until arr.length()).mapNotNull { arr.optJSONObject(it) }.map {
            RankRow(
                rank = it.optInt("rank"),
                userId = it.s("user_id"),
                name = (it.s("name") ?: "Pengguna").substringBefore("@"),
                username = it.s("username"),
                avatarUrl = it.s("avatar_url"),
                value = it.optLong("value")
            )
        }
        val me = o.optJSONObject("me")
        return RankBoard(rows, me?.optInt("rank"), me?.optLong("value"))
    }
}
