package com.jimz.jimztune.util

import android.content.ComponentName
import android.content.Context
import android.content.pm.PackageManager

/** Ikon launcher alternatif lewat activity-alias. Mengganti ikon bisa menutup aplikasi sebentar di sebagian launcher. */
object AppIcons {
    class Option(val id: String, val label: String, val start: Long, val end: Long)

    val options = listOf(
        Option("LauncherDefault", "Biru", 0xFF0A1330, 0xFF0B2A6E),
        Option("LauncherNeon", "Neon", 0xFF00C2FF, 0xFF7C4DFF),
        Option("LauncherRuby", "Ruby", 0xFFE0607E, 0xFF6E0F2D),
        Option("LauncherForest", "Hutan", 0xFF2E9E6B, 0xFF0B3D2A),
        Option("LauncherSenja", "Senja", 0xFFFF8A3D, 0xFF8E2DE2),
        Option("LauncherMono", "Mono", 0xFF2B2B2B, 0xFF0B0B0B)
    )

    private fun cn(c: Context, id: String) = ComponentName(c.packageName, "com.jimz.jimztune.$id")

    fun current(c: Context): String {
        val pm = c.packageManager
        for (o in options) {
            val st = pm.getComponentEnabledSetting(cn(c, o.id))
            if (st == PackageManager.COMPONENT_ENABLED_STATE_ENABLED) return o.id
        }
        return options[0].id
    }

    fun set(c: Context, id: String) {
        val pm = c.packageManager
        pm.setComponentEnabledSetting(cn(c, id), PackageManager.COMPONENT_ENABLED_STATE_ENABLED, PackageManager.DONT_KILL_APP)
        for (o in options) if (o.id != id) {
            pm.setComponentEnabledSetting(cn(c, o.id), PackageManager.COMPONENT_ENABLED_STATE_DISABLED, PackageManager.DONT_KILL_APP)
        }
    }
}
