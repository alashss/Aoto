package com.jimz.jimztune.widget

import android.content.Context

/** Isi widget lirik pada satu saat. Disimpan agar widget tetap menampilkan yang terakhir walau proses aplikasi dimatikan sistem. */
data class WidgetSnapshot(
    val title: String,
    val artist: String,
    val line: String,
    val next: String,
    val playing: Boolean,
    /** Warna dominan sampul (latar widget). */
    val color: Int = WidgetArt.DEFAULT_COLOR,
    /** Naik setiap sampul diganti, supaya perubahan sampul memicu pembaruan widget. */
    val coverVersion: Int = 0,
    /** Slot lirik yang sedang tampil (0 atau 1). Berganti setiap baris baru supaya ViewFlipper menganimasikannya. */
    val slot: Int = 0,
    /** Identitas baris yang tampil; berubah saat baris (atau lagu) berganti. */
    val key: Int = 0,
    /** Progres lagu dalam 50 langkah (0..50); -1 kalau durasi belum diketahui. Dibulatkan supaya widget tidak digambar ulang tiap detik. */
    val progress: Int = -1,
    /** Durasi lagu (ms), 0 kalau belum diketahui. */
    val durationMs: Long = 0
) {
    companion object {
        const val IDLE_LINE = "Putar lagu untuk melihat lirik"
        val IDLE = WidgetSnapshot("Aoto", "", IDLE_LINE, "", false)
    }
}

object LyricsWidgetStore {
    private fun prefs(context: Context) =
        context.applicationContext.getSharedPreferences("jimztune_widget", Context.MODE_PRIVATE)

    /** Tema widget: indeks ke [WidgetThemes.ALL] (0 = mengikuti warna sampul). */
    fun style(context: Context): Int = prefs(context).getInt("style", 0).coerceIn(0, WidgetThemes.ALL.lastIndex)

    fun setStyle(context: Context, style: Int) {
        prefs(context).edit().putInt("style", style.coerceIn(0, WidgetThemes.ALL.lastIndex)).apply()
    }

    /** Kepekatan latar dalam persen (30..100). */
    fun opacity(context: Context): Int = prefs(context).getInt("opacity", 100).coerceIn(30, 100)
    fun setOpacity(context: Context, v: Int) = prefs(context).edit().putInt("opacity", v.coerceIn(30, 100)).apply()

    /** Sudut: 0 = tajam, 1 = membulat (bawaan), 2 = sangat bulat. */
    fun corner(context: Context): Int = prefs(context).getInt("corner", 1).coerceIn(0, 2)
    fun setCorner(context: Context, v: Int) = prefs(context).edit().putInt("corner", v.coerceIn(0, 2)).apply()

    fun showCover(context: Context): Boolean = prefs(context).getBoolean("show_cover", true)
    fun setShowCover(context: Context, v: Boolean) = prefs(context).edit().putBoolean("show_cover", v).apply()

    fun showNext(context: Context): Boolean = prefs(context).getBoolean("show_next", true)
    fun setShowNext(context: Context, v: Boolean) = prefs(context).edit().putBoolean("show_next", v).apply()

    fun showProgress(context: Context): Boolean = prefs(context).getBoolean("show_progress", true)
    fun setShowProgress(context: Context, v: Boolean) = prefs(context).edit().putBoolean("show_progress", v).apply()

    /** Animasi pergantian baris. Mati secara bawaan: beberapa peluncur (mis. ColorOS) menggambar animasi widget dengan glitch. */
    fun animate(context: Context): Boolean = prefs(context).getBoolean("animate", false)
    fun setAnimate(context: Context, v: Boolean) = prefs(context).edit().putBoolean("animate", v).apply()

    /** Semua pilihan tampilan jadi satu teks: kalau berubah, widget digambar ulang penuh (bukan pembaruan parsial). */
    fun configKey(context: Context): String =
        "${style(context)}|${opacity(context)}|${corner(context)}|${showCover(context)}|${showNext(context)}|${showProgress(context)}|${animate(context)}"

    fun save(context: Context, s: WidgetSnapshot) {
        prefs(context).edit()
            .putString("title", s.title)
            .putString("artist", s.artist)
            .putString("line", s.line)
            .putString("next", s.next)
            .putBoolean("playing", s.playing)
            .putInt("color", s.color)
            .apply()
    }

    fun load(context: Context): WidgetSnapshot {
        val p = prefs(context)
        return WidgetSnapshot(
            title = p.getString("title", null) ?: WidgetSnapshot.IDLE.title,
            artist = p.getString("artist", null).orEmpty(),
            line = p.getString("line", null) ?: WidgetSnapshot.IDLE_LINE,
            next = p.getString("next", null).orEmpty(),
            // Proses baru saja dimulai: pemutar belum tentu berjalan, jadi jangan menampilkan ikon "jeda".
            playing = false,
            color = p.getInt("color", WidgetArt.DEFAULT_COLOR)
        )
    }
}
