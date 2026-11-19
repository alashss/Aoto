package com.jimz.jimztune.util

import java.time.OffsetDateTime
import java.time.ZoneId
import java.time.format.DateTimeFormatter
import java.util.Locale

/** Pembantu waktu untuk panel owner & status akun. Waktu dari server berbentuk ISO-8601 (UTC). */
object TimeUtil {
    private val local = DateTimeFormatter.ofPattern("d MMM yyyy, HH:mm", Locale("id", "ID"))
    private val hourOnly = DateTimeFormatter.ofPattern("HH:mm", Locale("id", "ID"))

    /** ISO-8601 -> milidetik sejak epoch. Null kalau kosong / tidak terbaca. */
    fun parse(iso: String?): Long? {
        if (iso.isNullOrBlank() || iso == "null") return null
        return try {
            OffsetDateTime.parse(iso).toInstant().toEpochMilli()
        } catch (_: Exception) {
            null
        }
    }

    fun format(ms: Long): String =
        java.time.Instant.ofEpochMilli(ms).atZone(ZoneId.systemDefault()).format(local)

    fun formatHour(ms: Long): String =
        java.time.Instant.ofEpochMilli(ms).atZone(ZoneId.systemDefault()).format(hourOnly)

    fun format(iso: String?): String = parse(iso)?.let { format(it) } ?: "-"

    /** "baru saja", "5 menit lalu", "3 jam lalu", "2 hari lalu". */
    fun ago(ms: Long, now: Long = System.currentTimeMillis()): String {
        val s = ((now - ms) / 1000).coerceAtLeast(0)
        return when {
            s < 60 -> "baru saja"
            s < 3600 -> "${s / 60} menit lalu"
            s < 86400 -> "${s / 3600} jam lalu"
            else -> "${s / 86400} hari lalu"
        }
    }

    /** Milidetik -> ISO-8601 UTC, untuk dikirim ke server. */
    fun toIso(ms: Long): String = java.time.Instant.ofEpochMilli(ms).toString()
}
