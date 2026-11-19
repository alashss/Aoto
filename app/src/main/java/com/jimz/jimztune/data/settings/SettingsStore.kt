package com.jimz.jimztune.data.settings

import android.content.Context
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.setValue
import com.jimz.jimztune.AppGraph
import com.jimz.jimztune.data.stream.StreamResolver

enum class AudioQuality(val label: String, val hint: String, val maxBitrate: Int) {
    LOW("Hemat", "Sekitar 48–64 kbps, hemat kuota", 70_000),
    MEDIUM("Seimbang", "Sekitar 128 kbps", 140_000),
    HIGH("Terbaik", "Bitrate tertinggi yang tersedia", Int.MAX_VALUE)
}

/** Pengaturan pengguna. State-nya Compose, jadi layar langsung ikut berubah saat nilainya diganti. */
class SettingsStore(context: Context) {
    private val prefs = context.getSharedPreferences("jimztune_settings", Context.MODE_PRIVATE)

    init {
        // Pilihan sumber audio sudah dihapus: semua sumber selalu aktif. Buang sisa pengaturan lama.
        prefs.edit().remove("off_providers").remove("canvas_url").apply()
    }

    var quality by mutableStateOf(
        runCatching { AudioQuality.valueOf(prefs.getString("quality", null) ?: "") }.getOrDefault(AudioQuality.HIGH)
    )
        private set

    // Jangan dinamai setQuality: bentrok dengan setter otomatis property `quality` di JVM.
    fun updateQuality(value: AudioQuality) {
        quality = value
        prefs.edit().putString("quality", value.name).apply()
        StreamResolver.clearCache() // URL lama masih kualitas lama
        AppGraph.cloud.markDirty()
    }

    /** Menerapkan kualitas dari cloud (tidak memicu unggahan balik). */
    fun applyCloudQuality(name: String?) {
        val q = runCatching { AudioQuality.valueOf(name ?: "") }.getOrNull() ?: return
        if (q == quality) return
        quality = q
        prefs.edit().putString("quality", q.name).apply()
        StreamResolver.clearCache()
    }
}
