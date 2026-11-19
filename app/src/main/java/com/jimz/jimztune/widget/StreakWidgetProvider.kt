package com.jimz.jimztune.widget

import android.app.PendingIntent
import android.appwidget.AppWidgetManager
import android.appwidget.AppWidgetProvider
import android.content.ComponentName
import android.content.Context
import android.content.Intent
import android.graphics.Bitmap
import android.graphics.Canvas
import android.graphics.Color
import android.graphics.LinearGradient
import android.graphics.Paint
import android.graphics.Path
import android.graphics.RadialGradient
import android.graphics.RectF
import android.graphics.Shader
import android.graphics.Typeface
import android.os.SystemClock
import android.widget.RemoteViews
import android.widget.Toast
import com.jimz.jimztune.MainActivity
import com.jimz.jimztune.R
import com.jimz.jimztune.data.stats.Streak
import com.jimz.jimztune.data.stats.StreakInfo
import java.time.LocalDate
import java.time.format.TextStyle
import java.util.Locale

/**
 * Widget streak: api, jumlah hari berturut-turut, dan tujuh hari terakhir. Seluruh widget digambar sebagai satu gambar
 * (api, angka, titik hari) supaya tampilannya sama di semua peluncur. Diperbarui saat ada catatan dengar baru
 * (dibatasi tiap 20 detik) dan berkala oleh sistem untuk pergantian hari.
 */
class StreakWidgetProvider : AppWidgetProvider() {

    override fun onUpdate(context: Context, manager: AppWidgetManager, appWidgetIds: IntArray) {
        appWidgetIds.forEach { manager.updateAppWidget(it, build(context, manager, it)) }
    }

    override fun onAppWidgetOptionsChanged(context: Context, manager: AppWidgetManager, appWidgetId: Int, newOptions: android.os.Bundle?) {
        manager.updateAppWidget(appWidgetId, build(context, manager, appWidgetId))
    }

    companion object {
        private var lastRefresh = 0L

        private fun component(context: Context) = ComponentName(context, StreakWidgetProvider::class.java)

        fun hasWidgets(context: Context): Boolean = try {
            AppWidgetManager.getInstance(context).getAppWidgetIds(component(context)).isNotEmpty()
        } catch (_: Exception) {
            false
        }

        fun requestPin(context: Context) {
            val hint = "Tekan lama layar utama → Widget → Streak Aoto"
            try {
                val manager = AppWidgetManager.getInstance(context)
                if (manager.isRequestPinAppWidgetSupported) {
                    if (!manager.requestPinAppWidget(component(context), null, null)) {
                        Toast.makeText(context, hint, Toast.LENGTH_LONG).show()
                    }
                } else {
                    Toast.makeText(context, hint, Toast.LENGTH_LONG).show()
                }
            } catch (_: Exception) {
                Toast.makeText(context, hint, Toast.LENGTH_LONG).show()
            }
        }

        /** Dipanggil setiap ada catatan dengar baru. Aman dipanggil sering: dibatasi sekali per 20 detik. */
        fun refreshThrottled(context: Context) {
            val now = SystemClock.elapsedRealtime()
            if (now - lastRefresh < 20_000L) return
            lastRefresh = now
            refresh(context)
        }

        fun refresh(context: Context) {
            try {
                val app = context.applicationContext
                val manager = AppWidgetManager.getInstance(app)
                val ids = manager.getAppWidgetIds(component(app))
                ids.forEach { manager.updateAppWidget(it, build(app, manager, it)) }
            } catch (_: Exception) {
            }
        }

        private fun build(context: Context, manager: AppWidgetManager, id: Int): RemoteViews {
            Streak.init(context)
            val density = context.resources.displayMetrics.density
            var wDp = 160
            var hDp = 160
            try {
                val o = manager.getAppWidgetOptions(id)
                wDp = o.getInt(AppWidgetManager.OPTION_APPWIDGET_MIN_WIDTH, 0).takeIf { it > 0 } ?: wDp
                hDp = o.getInt(AppWidgetManager.OPTION_APPWIDGET_MIN_HEIGHT, 0).takeIf { it > 0 } ?: hDp
            } catch (_: Exception) {
            }
            var w = (wDp * density).toInt().coerceAtLeast(160)
            var h = (hDp * density).toInt().coerceAtLeast(160)
            // Batas ukuran supaya bitmap tidak melewati batas RemoteViews.
            val scale = Math.sqrt(minOf(1.0, 380_000.0 / (w.toDouble() * h)))
            w = (w * scale).toInt()
            h = (h * scale).toInt()

            val info = try {
                Streak.info()
            } catch (_: Exception) {
                null
            }
            val views = RemoteViews(context.packageName, R.layout.widget_streak)
            views.setImageViewBitmap(R.id.widget_streak_img, draw(w, h, info, density * scale.toFloat()))
            val open = PendingIntent.getActivity(
                context, 10,
                Intent(context, MainActivity::class.java).addFlags(Intent.FLAG_ACTIVITY_NEW_TASK or Intent.FLAG_ACTIVITY_SINGLE_TOP),
                PendingIntent.FLAG_IMMUTABLE or PendingIntent.FLAG_UPDATE_CURRENT
            )
            views.setOnClickPendingIntent(R.id.widget_streak_root, open)
            return views
        }

        private fun palette(level: Int): IntArray = when {
            level >= 6 -> intArrayOf(0xFF2F6BFF.toInt(), 0xFF7FD6FF.toInt(), 0xFFEAFBFF.toInt(), 0xFF4DA3FF.toInt())
            level >= 3 -> intArrayOf(0xFFFF3D2E.toInt(), 0xFFFFB02E.toInt(), 0xFFFFF3B0.toInt(), 0xFFFF7A2E.toInt())
            level >= 1 -> intArrayOf(0xFFFF7A1A.toInt(), 0xFFFFCB47.toInt(), 0xFFFFF6CC.toInt(), 0xFFFFA62E.toInt())
            else -> intArrayOf(0xFF6B7391.toInt(), 0xFF8E96B5.toInt(), 0xFFB9BFD6.toInt(), 0xFF6B7391.toInt())
        }

        /** Bentuk api yang sama dengan FlameIcon di aplikasi, dalam kotak [size] di posisi ([ox], [oy]). */
        private fun flamePath(ox: Float, oy: Float, size: Float, k: Float): Path {
            fun x(v: Float) = ox + (0.5f + (v - 0.5f) * k) * size
            fun y(v: Float) = oy + (0.98f - (0.98f - v) * k) * size
            return Path().apply {
                moveTo(x(0.5f), y(0.02f))
                cubicTo(x(0.58f), y(0.22f), x(0.93f), y(0.38f), x(0.9f), y(0.66f))
                cubicTo(x(0.88f), y(0.88f), x(0.7f), y(0.98f), x(0.5f), y(0.98f))
                cubicTo(x(0.3f), y(0.98f), x(0.12f), y(0.88f), x(0.1f), y(0.66f))
                cubicTo(x(0.09f), y(0.5f), x(0.2f), y(0.4f), x(0.3f), y(0.3f))
                cubicTo(x(0.32f), y(0.4f), x(0.36f), y(0.44f), x(0.42f), y(0.46f))
                cubicTo(x(0.4f), y(0.3f), x(0.44f), y(0.14f), x(0.5f), y(0.02f))
                close()
            }
        }

        private fun draw(w: Int, h: Int, info: StreakInfo?, d: Float): Bitmap {
            val bmp = Bitmap.createBitmap(w, h, Bitmap.Config.ARGB_8888)
            val c = Canvas(bmp)
            val level = info?.tier?.level ?: 0
            val pal = palette(level)
            val lit = level > 0
            val radius = 24f * d
            val rect = RectF(0f, 0f, w.toFloat(), h.toFloat())
            val p = Paint(Paint.ANTI_ALIAS_FLAG)

            // Latar gelap dengan semburat warna api.
            p.shader = LinearGradient(0f, 0f, w.toFloat(), h.toFloat(), 0xFF181B2B.toInt(), 0xFF0A0C16.toInt(), Shader.TileMode.CLAMP)
            c.drawRoundRect(rect, radius, radius, p)
            p.shader = RadialGradient(
                w * 0.5f, h * 0.38f, maxOf(w, h) * 0.7f,
                intArrayOf(Color.argb(if (lit) 90 else 30, Color.red(pal[3]), Color.green(pal[3]), Color.blue(pal[3])), Color.TRANSPARENT),
                null, Shader.TileMode.CLAMP
            )
            c.drawRoundRect(rect, radius, radius, p)
            p.shader = null

            // Api.
            val flame = minOf(w * 0.34f, h * 0.36f)
            val fx = (w - flame) / 2f
            val fy = h * 0.07f
            val outer = flamePath(fx, fy, flame, 1f)
            if (lit) {
                p.shader = LinearGradient(0f, fy, 0f, fy + flame, pal[0], pal[1], Shader.TileMode.CLAMP)
                c.drawPath(outer, p)
                p.shader = LinearGradient(0f, fy + flame * 0.4f, 0f, fy + flame, pal[1], pal[2], Shader.TileMode.CLAMP)
                c.drawPath(flamePath(fx, fy, flame, 0.52f), p)
                p.shader = null
            } else {
                p.style = Paint.Style.STROKE
                p.strokeWidth = flame * 0.045f
                p.color = Color.argb(140, Color.red(pal[1]), Color.green(pal[1]), Color.blue(pal[1]))
                c.drawPath(outer, p)
                p.style = Paint.Style.FILL
            }

            // Angka + keterangan.
            val num = (info?.current ?: 0).toString()
            val tp = Paint(Paint.ANTI_ALIAS_FLAG)
            tp.color = Color.WHITE
            tp.textAlign = Paint.Align.CENTER
            tp.typeface = Typeface.create(Typeface.DEFAULT, Typeface.BOLD)
            tp.textSize = h * 0.2f
            val numY = fy + flame + h * 0.17f
            c.drawText(num, w / 2f, numY, tp)
            tp.typeface = Typeface.DEFAULT
            tp.textSize = h * 0.075f
            tp.color = Color.argb(170, 255, 255, 255)
            val caption = when {
                info == null -> "Streak Aoto"
                info.current == 0 -> "Dengarkan 1 menit untuk mulai"
                info.listenedToday -> "hari berturut-turut"
                info.atRisk -> "Hampir putus! Sisa ${info.hoursLeft.coerceAtLeast(1)} jam"
                else -> "hari · dengarkan hari ini"
            }
            if (info != null && info.atRisk) tp.color = pal[1]
            c.drawText(caption, w / 2f, numY + h * 0.095f, tp)

            // Tujuh hari terakhir.
            val today = LocalDate.now()
            val dotR = minOf(w / 7f * 0.26f, h * 0.05f)
            val cy = h * 0.88f
            val step = (w - 2 * radius * 0.6f) / 7f
            val startX = (w - step * 6f) / 2f
            val lp = Paint(Paint.ANTI_ALIAS_FLAG)
            lp.textAlign = Paint.Align.CENTER
            lp.textSize = h * 0.05f
            for (i in 0..6) {
                val day = today.minusDays((6 - i).toLong())
                val on = try { Streak.counted(day) } catch (_: Exception) { false }
                val cx = startX + step * i
                p.color = if (on) pal[1] else Color.argb(36, 255, 255, 255)
                c.drawCircle(cx, cy, dotR, p)
                if (day == today && !on) {
                    val ring = Paint(Paint.ANTI_ALIAS_FLAG)
                    ring.style = Paint.Style.STROKE
                    ring.strokeWidth = d * 1.5f
                    ring.color = pal[1]
                    c.drawCircle(cx, cy, dotR, ring)
                }
                lp.color = Color.argb(if (day == today) 230 else 110, 255, 255, 255)
                lp.typeface = if (day == today) Typeface.DEFAULT_BOLD else Typeface.DEFAULT
                c.drawText(
                    day.dayOfWeek.getDisplayName(TextStyle.NARROW, Locale("id", "ID")).uppercase(),
                    cx, cy + dotR + h * 0.055f, lp
                )
            }
            return bmp
        }
    }
}
