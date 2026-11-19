package com.jimz.jimztune.data.lyrics

import android.content.Context
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableIntStateOf
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.setValue

/** Pengaturan tampilan lirik (rata teks, ukuran) dan penyesuaian per lagu (geseran waktu, sumber pilihan). */
class LyricsPrefs(context: Context) {
    private val prefs = context.applicationContext.getSharedPreferences("jimztune_lyrics", Context.MODE_PRIVATE)

    /** 0 = rata kiri, 1 = tengah. */
    var align by mutableIntStateOf(prefs.getInt("align", 0))
        private set

    /** Indeks ke [SIZES]. */
    var sizeLevel by mutableIntStateOf(prefs.getInt("size", 1))
        private set

    val sizeScale: Float get() = SIZES[sizeLevel.coerceIn(0, SIZES.lastIndex)]

    /** Terjemahan lirik (di bawah tiap baris, hanya di halaman lirik penuh). */
    var translateEnabled by mutableStateOf(prefs.getBoolean("translate", true))
        private set

    /** Lagu berbahasa Inggris ikut diterjemahkan. Matikan kalau mau hemat permintaan. */
    var translateEnglish by mutableStateOf(prefs.getBoolean("translate_en", true))
        private set

    /** Gaya animasi baris aktif: 0 Karaoke, 1 Fade, 2 Glow, 3 Slide. */
    var animStyle by mutableIntStateOf(prefs.getInt("anim", 0))
        private set

    /** Baris yang bukan sedang dinyanyikan diburamkan. */
    var blurOthers by mutableStateOf(prefs.getBoolean("blur", true))
        private set

    /** Tampilkan romanisasi (huruf Latin) di bawah lirik non-Latin. */
    var romanize by mutableStateOf(prefs.getBoolean("roman", false))
        private set

    /** Pratinjau lirik di bawah sampul: 0 mati, 1 satu baris, 2 dua baris. */
    var preview by mutableIntStateOf(prefs.getInt("preview", 1))
        private set

    /** Naik setiap geseran waktu berubah, supaya tampilan lirik ikut menyegarkan. */
    var offsetVersion by mutableIntStateOf(0)
        private set

    fun updateAnimStyle(v: Int) {
        animStyle = v.coerceIn(0, ANIM_NAMES.lastIndex)
        prefs.edit().putInt("anim", animStyle).apply()
    }

    fun updateBlurOthers(v: Boolean) {
        blurOthers = v
        prefs.edit().putBoolean("blur", v).apply()
    }

    fun updateRomanize(v: Boolean) {
        romanize = v
        prefs.edit().putBoolean("roman", v).apply()
    }

    fun updatePreview(v: Int) {
        preview = v.coerceIn(0, 2)
        prefs.edit().putInt("preview", preview).apply()
    }

    fun updateTranslateEnabled(value: Boolean) {
        translateEnabled = value
        prefs.edit().putBoolean("translate", value).apply()
    }

    fun updateTranslateEnglish(value: Boolean) {
        translateEnglish = value
        prefs.edit().putBoolean("translate_en", value).apply()
    }

    fun toggleAlign() {
        align = if (align == 0) 1 else 0
        prefs.edit().putInt("align", align).apply()
    }

    fun sizeUp() = setSize(sizeLevel + 1)
    fun sizeDown() = setSize(sizeLevel - 1)

    private fun setSize(level: Int) {
        sizeLevel = level.coerceIn(0, SIZES.lastIndex)
        prefs.edit().putInt("size", sizeLevel).apply()
    }

    /** Positif = lirik muncul lebih cepat, negatif = lebih lambat. */
    fun offsetMs(songId: String): Long = prefs.getLong("off_$songId", 0L)

    fun setOffsetMs(songId: String, ms: Long) {
        prefs.edit().apply { if (ms == 0L) remove("off_$songId") else putLong("off_$songId", ms) }.apply()
        offsetVersion++
    }

    fun choice(songId: String): Int = prefs.getInt("src_$songId", 0)

    fun setChoice(songId: String, index: Int) {
        prefs.edit().putInt("src_$songId", index).apply()
    }

    companion object {
        val SIZES = floatArrayOf(0.85f, 1f, 1.15f, 1.3f)
        val ANIM_NAMES = listOf("Karaoke", "Fade", "Glow", "Slide")
        val PREVIEW_NAMES = listOf("Mati", "Satu baris", "Dua baris")
    }
}
