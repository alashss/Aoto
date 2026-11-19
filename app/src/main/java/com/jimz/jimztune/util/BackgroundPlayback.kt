package com.jimz.jimztune.util

import android.annotation.SuppressLint
import android.app.ActivityManager
import android.content.ComponentName
import android.content.Context
import android.content.Intent
import android.net.Uri
import android.os.PowerManager
import android.provider.Settings

/**
 * Banyak HP (Oppo/Realme/Vivo/Xiaomi, dll) menghentikan jaringan aplikasi musik saat layar mati, sehingga lagu
 * berikutnya tidak mau jalan sampai aplikasi dibuka lagi. Izin "tanpa batasan baterai" mencegah itu.
 */
object BackgroundPlayback {
    private const val PREFS = "jimztune_background"

    fun isExempt(context: Context): Boolean = try {
        context.getSystemService(PowerManager::class.java)?.isIgnoringBatteryOptimizations(context.packageName) == true
    } catch (_: Exception) {
        true
    }

    @SuppressLint("BatteryLife")
    fun request(context: Context) {
        val direct = Intent(Settings.ACTION_REQUEST_IGNORE_BATTERY_OPTIMIZATIONS)
            .setData(Uri.parse("package:${context.packageName}"))
            .addFlags(Intent.FLAG_ACTIVITY_NEW_TASK)
        try {
            context.startActivity(direct)
        } catch (_: Exception) {
            try {
                context.startActivity(
                    Intent(Settings.ACTION_IGNORE_BATTERY_OPTIMIZATION_SETTINGS).addFlags(Intent.FLAG_ACTIVITY_NEW_TASK)
                )
            } catch (_: Exception) {
            }
        }
    }

    /** True kalau sistem menandai aplikasi ini "dibatasi di latar belakang" (Android 9+). Ini memutus jaringan saat layar mati. */
    fun isBackgroundRestricted(context: Context): Boolean = try {
        android.os.Build.VERSION.SDK_INT >= 28 &&
            context.getSystemService(ActivityManager::class.java)?.isBackgroundRestricted == true
    } catch (_: Exception) {
        false
    }

    /** Halaman "Mulai otomatis / aktivitas latar" milik tiap pabrikan. Semuanya dicoba sampai ada yang terbuka. */
    private val oemScreens = listOf(
        "com.coloros.safecenter" to "com.coloros.safecenter.permission.startup.StartupAppListActivity",
        "com.coloros.safecenter" to "com.coloros.safecenter.startupapp.StartupAppListActivity",
        "com.oplus.battery" to "com.oplus.powermanager.fuelgaue.PowerUsageModelActivity",
        "com.oppo.safe" to "com.oppo.safe.permission.startup.StartupAppListActivity",
        "com.miui.securitycenter" to "com.miui.permcenter.autostart.AutoStartManagementActivity",
        "com.vivo.permissionmanager" to "com.vivo.permissionmanager.activity.BgStartUpManagerActivity",
        "com.iqoo.secure" to "com.iqoo.secure.ui.phoneoptimize.AddWhiteListActivity",
        "com.huawei.systemmanager" to "com.huawei.systemmanager.startupmgr.ui.StartupNormalAppListActivity",
        "com.huawei.systemmanager" to "com.huawei.systemmanager.optimize.process.ProtectActivity",
        "com.transsion.phonemaster" to "com.cloud.sdk.commonutil.activity.AutoStartActivity",
        "com.asus.mobilemanager" to "com.asus.mobilemanager.autostart.AutoStartActivity"
    )

    /** Membuka pengaturan mulai-otomatis bawaan HP; kalau tidak ada, buka halaman info aplikasi. */
    fun openAutoStart(context: Context) {
        for ((pkg, cls) in oemScreens) {
            try {
                context.startActivity(Intent().setComponent(ComponentName(pkg, cls)).addFlags(Intent.FLAG_ACTIVITY_NEW_TASK))
                return
            } catch (_: Exception) {
            }
        }
        openAppInfo(context)
    }

    fun openAppInfo(context: Context) {
        try {
            context.startActivity(
                Intent(Settings.ACTION_APPLICATION_DETAILS_SETTINGS, Uri.parse("package:${context.packageName}"))
                    .addFlags(Intent.FLAG_ACTIVITY_NEW_TASK)
            )
        } catch (_: Exception) {
        }
    }

    fun promptSeen(context: Context): Boolean =
        context.getSharedPreferences(PREFS, Context.MODE_PRIVATE).getBoolean("prompt_seen", false)

    fun markPromptSeen(context: Context) {
        context.getSharedPreferences(PREFS, Context.MODE_PRIVATE).edit().putBoolean("prompt_seen", true).apply()
    }
}
