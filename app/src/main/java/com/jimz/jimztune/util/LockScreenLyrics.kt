package com.jimz.jimztune.util

import android.app.Activity
import android.app.KeyguardManager
import android.content.BroadcastReceiver
import android.content.Context
import android.content.Intent
import android.net.Uri
import android.os.Build
import android.provider.Settings
import android.view.WindowManager
import com.jimz.jimztune.MainActivity

/**
 * Lirik di layar kunci asli. Saat layar menyala dalam keadaan terkunci dan musik sedang diputar,
 * Aoto membuka Mode Kunci Layar di atas layar kunci (tanpa membuka kunci). Butuh izin "tampil di atas aplikasi lain".
 */
object LockScreenLyrics {
    const val EXTRA = "aoto_lockscreen"
    private const val PREF = "lockscreen_lyrics"

    /** Diisi true selama mode kunci layar dibuka oleh layar kunci asli. */
    @Volatile var fromKeyguard: Boolean = false

    fun enabled(c: Context) = c.getSharedPreferences(PREF, Context.MODE_PRIVATE).getBoolean("on", false)
    fun set(c: Context, on: Boolean) { c.getSharedPreferences(PREF, Context.MODE_PRIVATE).edit().putBoolean("on", on).apply() }
    fun canDraw(c: Context) = Build.VERSION.SDK_INT < 23 || Settings.canDrawOverlays(c)
    fun overlaySettings(c: Context) = Intent(Settings.ACTION_MANAGE_OVERLAY_PERMISSION, Uri.parse("package:${c.packageName}"))
        .addFlags(Intent.FLAG_ACTIVITY_NEW_TASK)

    /** Dipasang di PlaybackService; [isPlaying] dibaca saat layar menyala. */
    class ScreenReceiver(private val isPlaying: () -> Boolean) : BroadcastReceiver() {
        override fun onReceive(context: Context, intent: Intent) {
            if (intent.action != Intent.ACTION_SCREEN_ON) return
            if (!enabled(context) || !canDraw(context) || !isPlaying()) return
            val km = context.getSystemService(Context.KEYGUARD_SERVICE) as? KeyguardManager ?: return
            if (!km.isKeyguardLocked) return
            try {
                context.startActivity(
                    Intent(context, MainActivity::class.java)
                        .addFlags(Intent.FLAG_ACTIVITY_NEW_TASK or Intent.FLAG_ACTIVITY_REORDER_TO_FRONT or Intent.FLAG_ACTIVITY_SINGLE_TOP)
                        .putExtra(EXTRA, true)
                )
            } catch (_: Exception) { }
        }
    }

    /** Dipanggil MainActivity: izinkan tampil di atas layar kunci. */
    @Suppress("DEPRECATION")
    fun showOverKeyguard(a: Activity) {
        fromKeyguard = true
        if (Build.VERSION.SDK_INT >= 27) { a.setShowWhenLocked(true); a.setTurnScreenOn(true) }
        else a.window.addFlags(WindowManager.LayoutParams.FLAG_SHOW_WHEN_LOCKED or WindowManager.LayoutParams.FLAG_TURN_SCREEN_ON)
    }

    /** Selesai: kembalikan ke biasa dan minta pengguna membuka kunci bila diperlukan. */
    @Suppress("DEPRECATION")
    fun leaveKeyguard(a: Activity, dismiss: Boolean) {
        if (!fromKeyguard) return
        fromKeyguard = false
        if (Build.VERSION.SDK_INT >= 27) { a.setShowWhenLocked(false); a.setTurnScreenOn(false) }
        else a.window.clearFlags(WindowManager.LayoutParams.FLAG_SHOW_WHEN_LOCKED or WindowManager.LayoutParams.FLAG_TURN_SCREEN_ON)
        if (dismiss && Build.VERSION.SDK_INT >= 26) {
            (a.getSystemService(Context.KEYGUARD_SERVICE) as? KeyguardManager)?.requestDismissKeyguard(a, null)
        }
    }
}
