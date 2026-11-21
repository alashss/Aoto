package com.jimz.jimztune.util

import android.content.Context
import android.view.WindowManager
import android.app.Activity

/** Pengaturan kecil lokal: jeda riwayat dengar dan refresh rate tinggi. */
object PrivacyPrefs {
    private const val PREFS = "jimztune_settings"
    private fun p(c: Context) = c.applicationContext.getSharedPreferences(PREFS, Context.MODE_PRIVATE)

    fun btResume(c: Context): Boolean = p(c).getBoolean("bt_resume", false)
    fun setBtResume(c: Context, v: Boolean) = p(c).edit().putBoolean("bt_resume", v).apply()

    fun highRefresh(c: Context): Boolean = p(c).getBoolean("high_refresh", false)
    fun setHighRefresh(c: Context, v: Boolean) = p(c).edit().putBoolean("high_refresh", v).apply()

    /** Meminta layar memakai refresh rate tertinggi (atau mengembalikan ke bawaan sistem). */
    fun applyRefresh(a: Activity) {
        val lp: WindowManager.LayoutParams = a.window.attributes
        lp.preferredRefreshRate = if (highRefresh(a) && !com.jimz.jimztune.ui.theme.AppTheme.saver) 120f else 0f
        a.window.attributes = lp
    }
}
