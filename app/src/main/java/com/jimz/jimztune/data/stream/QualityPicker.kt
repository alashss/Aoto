package com.jimz.jimztune.data.stream

import com.jimz.jimztune.data.settings.AudioQuality

/** Memilih stream audio sesuai kualitas yang dipilih pengguna di Setelan. */
object QualityPicker {
    /** Beberapa sumber memberi kbps (128), yang lain bps (128000). Samakan ke bps. */
    fun normalize(raw: Int): Int = if (raw in 1..999) raw * 1000 else raw

    fun <T> pick(items: List<T>, quality: AudioQuality, bitrateBps: (T) -> Int): T? {
        if (items.isEmpty()) return null
        val sorted = items.sortedBy(bitrateBps)
        if (quality == AudioQuality.HIGH) return sorted.last()
        return sorted.lastOrNull { bitrateBps(it) <= quality.maxBitrate } ?: sorted.first()
    }
}
