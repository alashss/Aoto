package com.jimz.jimztune.util

import android.app.Activity
import android.content.Context
import android.content.ContextWrapper
import android.view.WindowManager

/** Blokir tangkapan layar dan pratinjau Recents (FLAG_SECURE). Pilihan disimpan di perangkat. */
object ScreenGuard {
    private const val PREFS = "jimztune_settings"
    private const val KEY = "block_screenshots"

    fun enabled(context: Context): Boolean =
        context.getSharedPreferences(PREFS, Context.MODE_PRIVATE).getBoolean(KEY, false)

    fun set(context: Context, on: Boolean) {
        context.getSharedPreferences(PREFS, Context.MODE_PRIVATE).edit().putBoolean(KEY, on).apply()
        activityOf(context)?.let { apply(it) }
    }

    fun apply(activity: Activity) {
        if (enabled(activity)) activity.window.addFlags(WindowManager.LayoutParams.FLAG_SECURE)
        else activity.window.clearFlags(WindowManager.LayoutParams.FLAG_SECURE)
    }

    private fun activityOf(c: Context): Activity? {
        var x: Context? = c
        while (x is ContextWrapper) {
            if (x is Activity) return x
            x = x.baseContext
        }
        return null
    }
}
