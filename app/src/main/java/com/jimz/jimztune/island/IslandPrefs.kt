package com.jimz.jimztune.island

import android.content.Context
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableIntStateOf
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.setValue

/**
 * Tema pil. [colors] berisi 1 warna (polos) atau 2+ warna (gradasi diagonal); null = warna diturunkan dari sampul lagu.
 * [accent] = warna equalizer / progres / garis tepi yang berdenyut (0 = diturunkan dari sampul).
 */
class IslandTheme(
    val name: String,
    val colors: IntArray?,
    val light: Boolean = false,
    val accent: Int = 0,
    val stroke: Int = 0x22FFFFFF,
    val minimal: Boolean = false
)

/** Pengaturan Dynamic Island (pil pemutar yang melayang di atas aplikasi lain). */
object IslandPrefs {
    /** Urutan 0..3 tetap sama seperti versi lama supaya pilihan tersimpan tidak bergeser. */
    val THEMES: List<IslandTheme> = listOf(
        IslandTheme("Klasik", intArrayOf(0xFF000000.toInt())),
        IslandTheme("Warna sampul", null),
        IslandTheme("Terang", intArrayOf(0xFFF2F4FB.toInt()), light = true, stroke = 0x14000000),
        IslandTheme("Minimal", intArrayOf(0xF2000000.toInt()), minimal = true),
        IslandTheme("Samudra", intArrayOf(0xFF0E3A5B.toInt(), 0xFF0A2038.toInt()), accent = 0xFF5CE1E6.toInt()),
        IslandTheme("Senja", intArrayOf(0xFFC2410C.toInt(), 0xFF9D174D.toInt()), accent = 0xFFFFD27A.toInt()),
        IslandTheme("Aurora", intArrayOf(0xFF0F766E.toInt(), 0xFF4338CA.toInt()), accent = 0xFF7CF5C8.toInt()),
        IslandTheme("Galaksi", intArrayOf(0xFF4C1D95.toInt(), 0xFF0B0820.toInt()), accent = 0xFFC4B5FD.toInt()),
        IslandTheme("Hutan", intArrayOf(0xFF166534.toInt(), 0xFF052E2B.toInt()), accent = 0xFFA3E635.toInt()),
        IslandTheme("Sakura", intArrayOf(0xFFFFE4EC.toInt(), 0xFFFFC9DA.toInt()), light = true, accent = 0xFFD6336C.toInt(), stroke = 0x14000000),
        IslandTheme("Neon", intArrayOf(0xFF08080F.toInt()), accent = 0xFF21E6C1.toInt()),
        IslandTheme("Emas", intArrayOf(0xFF241A05.toInt(), 0xFF000000.toInt()), accent = 0xFFFFC94D.toInt()),
        IslandTheme("Kaca", intArrayOf(0xCC141824.toInt()))
    )
    val STYLES: List<String> = THEMES.map { it.name }

    fun theme(style: Int): IslandTheme = THEMES.getOrElse(style) { THEMES[0] }

    private var prefs: android.content.SharedPreferences? = null
    var enabled by mutableStateOf(false); private set
    var style by mutableIntStateOf(0); private set
    var showLyrics by mutableStateOf(true); private set
    var autoExpand by mutableStateOf(true); private set

    /** Saat jeda, pil tidak hilang tapi mengecil; ketuk untuk melanjutkan. */
    var keepPaused by mutableStateOf(true); private set
    var spinCover by mutableStateOf(true); private set
    var glowEdge by mutableStateOf(true); private set
    var onChange: (() -> Unit)? = null

    fun init(context: Context) {
        if (prefs != null) return
        val p = context.applicationContext.getSharedPreferences("jimztune_island", Context.MODE_PRIVATE)
        prefs = p
        enabled = p.getBoolean("enabled", false)
        style = p.getInt("style", 0).coerceIn(0, THEMES.lastIndex)
        showLyrics = p.getBoolean("lyrics", true)
        autoExpand = p.getBoolean("auto_expand", true)
        keepPaused = p.getBoolean("keep_paused", true)
        spinCover = p.getBoolean("spin_cover", true)
        glowEdge = p.getBoolean("glow_edge", true)
    }

    fun updateShowLyrics(v: Boolean) {
        showLyrics = v
        prefs?.edit()?.putBoolean("lyrics", v)?.apply()
        onChange?.invoke()
    }

    fun updateAutoExpand(v: Boolean) {
        autoExpand = v
        prefs?.edit()?.putBoolean("auto_expand", v)?.apply()
    }

    fun updateKeepPaused(v: Boolean) {
        keepPaused = v
        prefs?.edit()?.putBoolean("keep_paused", v)?.apply()
        onChange?.invoke()
    }

    fun updateSpinCover(v: Boolean) {
        spinCover = v
        prefs?.edit()?.putBoolean("spin_cover", v)?.apply()
        onChange?.invoke()
    }

    fun updateGlowEdge(v: Boolean) {
        glowEdge = v
        prefs?.edit()?.putBoolean("glow_edge", v)?.apply()
        onChange?.invoke()
    }

    fun updateEnabled(v: Boolean) {
        enabled = v
        prefs?.edit()?.putBoolean("enabled", v)?.apply()
        onChange?.invoke()
    }

    fun updateStyle(v: Int) {
        style = v.coerceIn(0, THEMES.lastIndex)
        prefs?.edit()?.putInt("style", style)?.apply()
        onChange?.invoke()
    }
}
