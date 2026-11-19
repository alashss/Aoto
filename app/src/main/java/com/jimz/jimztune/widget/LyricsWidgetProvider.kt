package com.jimz.jimztune.widget

import android.app.PendingIntent
import android.appwidget.AppWidgetManager
import android.appwidget.AppWidgetProvider
import android.content.ComponentName
import android.content.Context
import android.content.Intent
import android.os.Handler
import android.os.Looper
import android.widget.RemoteViews
import android.widget.Toast
import androidx.core.content.ContextCompat
import androidx.media3.session.MediaController
import androidx.media3.session.SessionToken
import com.jimz.jimztune.MainActivity
import com.jimz.jimztune.R
import com.jimz.jimztune.player.PlaybackService

/**
 * Widget layar utama: baris lirik yang sedang dinyanyikan (dan baris berikutnya), judul lagu,
 * serta tombol sebelumnya / putar-jeda / berikutnya. Isinya diperbarui oleh [LyricsWidgetDriver] di PlaybackService.
 */
class LyricsWidgetProvider : AppWidgetProvider() {

    override fun onUpdate(context: Context, manager: AppWidgetManager, appWidgetIds: IntArray) {
        val snap = LyricsWidgetStore.load(context)
        appWidgetIds.forEach {
            forget(it)
            manager.updateAppWidget(it, build(context, snap, sizeFor(context, manager, it)))
        }
        LyricsWidgetDriver.instance?.onWidgetsChanged()
    }

    /** Widget diubah ukurannya: gambar ulang latar supaya sudutnya tetap rapi. */
    override fun onAppWidgetOptionsChanged(context: Context, manager: AppWidgetManager, appWidgetId: Int, newOptions: android.os.Bundle?) {
        val snap = LyricsWidgetDriver.instance?.currentSnapshot() ?: LyricsWidgetStore.load(context)
        forget(appWidgetId)
        manager.updateAppWidget(appWidgetId, build(context, snap, sizeFor(context, manager, appWidgetId)))
    }

    override fun onDeleted(context: Context, appWidgetIds: IntArray) {
        appWidgetIds.forEach { forget(it) }
        LyricsWidgetDriver.instance?.onWidgetsChanged()
    }

    override fun onEnabled(context: Context) {
        LyricsWidgetDriver.instance?.onWidgetsChanged()
    }

    override fun onReceive(context: Context, intent: Intent) {
        when (intent.action) {
            ACTION_PLAY_PAUSE, ACTION_NEXT, ACTION_PREV, ACTION_SEEK ->
                control(context, intent.action.orEmpty(), intent.getIntExtra(EXTRA_PERMILLE, -1))
            else -> super.onReceive(context, intent)
        }
    }

    /** Menyambung ke pemutar sebentar, kirim perintahnya, lalu lepas lagi. */
    private fun control(context: Context, action: String, permille: Int = -1) {
        val pending = goAsync()
        val app = context.applicationContext
        val main = Handler(Looper.getMainLooper())
        try {
            val token = SessionToken(app, ComponentName(app, PlaybackService::class.java))
            val future = MediaController.Builder(app, token).buildAsync()
            future.addListener({
                try {
                    val c = future.get()
                    when (action) {
                        ACTION_PLAY_PAUSE -> if (c.isPlaying) c.pause() else c.play()
                        ACTION_NEXT -> c.seekToNext()
                        ACTION_PREV -> c.seekToPrevious()
                        ACTION_SEEK -> {
                            val d = c.duration
                            if (permille in 0..1000 && d > 0 && d != androidx.media3.common.C.TIME_UNSET) c.seekTo(d * permille / 1000L)
                        }
                    }
                } catch (_: Exception) {
                }
                // Beri waktu perintah sampai ke pemutar sebelum controller dilepas.
                main.postDelayed({
                    try {
                        MediaController.releaseFuture(future)
                    } catch (_: Exception) {
                    }
                    pending.finish()
                }, 600L)
            }, ContextCompat.getMainExecutor(app))
        } catch (_: Throwable) {
            pending.finish()
        }
    }

    companion object {
        const val ACTION_PLAY_PAUSE = "com.jimz.jimztune.widget.PLAY_PAUSE"
        const val ACTION_NEXT = "com.jimz.jimztune.widget.NEXT"
        const val ACTION_PREV = "com.jimz.jimztune.widget.PREV"
        const val ACTION_SEEK = "com.jimz.jimztune.widget.SEEK"
        const val EXTRA_PERMILLE = "permille"
        private const val SEGMENTS = 10

        private fun component(context: Context) = ComponentName(context, LyricsWidgetProvider::class.java)

        fun hasWidgets(context: Context): Boolean = try {
            AppWidgetManager.getInstance(context).getAppWidgetIds(component(context)).isNotEmpty()
        } catch (_: Exception) {
            false
        }

        /** Minta peluncur memasang widget langsung ke layar utama; kalau tidak didukung, tampilkan petunjuk manual. */
        fun requestPin(context: Context) {
            val hint = "Tekan lama layar utama → Widget → Aoto"
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

        /** Yang terakhir digambar penuh untuk tiap widget; kalau masih sama, cukup perbarui bagian yang berubah. */
        private class FullState(val size: String, val coverVersion: Int, val color: Int, val config: String) {
            override fun equals(other: Any?) = other is FullState && other.size == size &&
                other.coverVersion == coverVersion && other.color == color && other.config == config
            override fun hashCode() = size.hashCode() * 31 + coverVersion
        }

        private val fullState = HashMap<Int, FullState>()
        private var lastSnap: WidgetSnapshot? = null

        @Synchronized
        private fun forget(id: Int) {
            fullState.remove(id)
        }

        /** Paksa gambar penuh pada pembaruan berikutnya (mis. setelah layar menyala). */
        @Synchronized
        fun invalidate() {
            fullState.clear()
            lastSnap = null
        }

        /**
         * Mendorong [snap] ke semua widget yang terpasang. Penggambaran penuh hanya kalau perlu (widget baru, ukuran / tema /
         * sampul berubah). Selain itu hanya bagian yang berubah (baris lirik, progres, tombol) yang dikirim lewat
         * partiallyUpdateAppWidget, sehingga latar dan sampul tidak digambar ulang dan widget tidak berkedip.
         */
        @Synchronized
        fun push(context: Context, snap: WidgetSnapshot) {
            try {
                val manager = AppWidgetManager.getInstance(context)
                val ids = manager.getAppWidgetIds(component(context))
                if (ids.isEmpty()) {
                    fullState.clear()
                    return
                }
                val config = LyricsWidgetStore.configKey(context)
                val prev = lastSnap
                val partialIds = ArrayList<Int>()
                for (id in ids) {
                    val size = sizeFor(context, manager, id)
                    val st = FullState(size.joinToString(","), snap.coverVersion, snap.color, config)
                    if (prev != null && fullState[id] == st) {
                        partialIds.add(id)
                    } else {
                        manager.updateAppWidget(id, build(context, snap, size))
                        fullState[id] = st
                    }
                }
                if (partialIds.isNotEmpty() && prev != null) {
                    val pv = buildPartial(context, prev, snap)
                    if (pv != null) manager.partiallyUpdateAppWidget(partialIds.toIntArray(), pv)
                }
                lastSnap = snap
            } catch (_: Exception) {
            }
        }

        private fun broadcast(context: Context, action: String, code: Int): PendingIntent =
            PendingIntent.getBroadcast(
                context,
                code,
                Intent(context, LyricsWidgetProvider::class.java).setAction(action),
                PendingIntent.FLAG_IMMUTABLE or PendingIntent.FLAG_UPDATE_CURRENT
            )

        /** Ukuran widget dalam piksel (dibatasi supaya bitmap latar tidak kebesaran untuk RemoteViews). */
        private fun sizeFor(context: Context, manager: AppWidgetManager, id: Int): IntArray {
            val density = context.resources.displayMetrics.density
            var wDp = 250
            var hDp = 120
            try {
                val o = manager.getAppWidgetOptions(id)
                val portrait = context.resources.configuration.orientation == android.content.res.Configuration.ORIENTATION_PORTRAIT
                wDp = o.getInt(AppWidgetManager.OPTION_APPWIDGET_MIN_WIDTH, 0).takeIf { it > 0 } ?: 250
                hDp = o.getInt(AppWidgetManager.OPTION_APPWIDGET_MIN_HEIGHT, 0).takeIf { it > 0 } ?: 120
                if (!portrait) {
                    wDp = o.getInt(AppWidgetManager.OPTION_APPWIDGET_MAX_WIDTH, 0).takeIf { it > 0 } ?: wDp
                } else {
                    hDp = o.getInt(AppWidgetManager.OPTION_APPWIDGET_MAX_HEIGHT, 0).takeIf { it > 0 } ?: hDp
                }
            } catch (_: Exception) {
            }
            var w = (wDp * density).toInt().coerceAtLeast(120)
            var h = (hDp * density).toInt().coerceAtLeast(90)
            val maxArea = 420_000.0
            val area = w.toDouble() * h
            val scale = if (area > maxArea) Math.sqrt(maxArea / area) else 1.0
            w = (w * scale).toInt()
            h = (h * scale).toInt()
            val baseDp = when (LyricsWidgetStore.corner(context)) {
                0 -> 6f
                2 -> 40f
                else -> 24f
            }
            val radius = baseDp * density * scale.toFloat()
            return intArrayOf(w, h, radius.toInt())
        }

        private val bgCache = object : LinkedHashMap<String, android.graphics.Bitmap>(8, 0.75f, true) {
            override fun removeEldestEntry(eldest: MutableMap.MutableEntry<String, android.graphics.Bitmap>?) = size > 4
        }
        private var coverVer = -1
        private var coverBmp: android.graphics.Bitmap? = null

        @Synchronized
        private fun background(color: Int, size: IntArray, theme: WidgetTheme, opacity: Int): android.graphics.Bitmap {
            // Tema tetap tidak bergantung pada warna sampul, jadi tidak perlu digambar ulang tiap lagu.
            val c = if (theme.colors == null) color else 0
            val key = "$c-${size[0]}-${size[1]}-${size[2]}-${theme.id}-$opacity"
            return bgCache.getOrPut(key) { WidgetArt.background(color, size[0], size[1], size[2].toFloat(), theme, opacity * 255 / 100) }
        }

        @Synchronized
        private fun coverBitmap(context: Context, version: Int): android.graphics.Bitmap? {
            if (coverVer != version || coverBmp == null) {
                coverBmp = WidgetArt.loadCover(context)
                coverVer = version
            }
            return coverBmp
        }

        private fun seekIntent(context: Context, index: Int, permille: Int): PendingIntent =
            PendingIntent.getBroadcast(
                context,
                100 + index,
                Intent(context, LyricsWidgetProvider::class.java).setAction(ACTION_SEEK).putExtra(EXTRA_PERMILLE, permille),
                PendingIntent.FLAG_IMMUTABLE or PendingIntent.FLAG_UPDATE_CURRENT
            )

        private val progressCache = object : LinkedHashMap<String, android.graphics.Bitmap>(8, 0.75f, true) {
            override fun removeEldestEntry(eldest: MutableMap.MutableEntry<String, android.graphics.Bitmap>?) = size > 6
        }

        /** Bar progres kecil (bulat di ujung) sebagai gambar: lebih fleksibel soal warna daripada ProgressBar. */
        @Synchronized
        private fun progressBitmap(steps: Int, fill: Int, track: Int): android.graphics.Bitmap =
            progressCache.getOrPut("$steps-$fill-$track") {
                val w = 500
                val h = 14
                val bmp = android.graphics.Bitmap.createBitmap(w, h, android.graphics.Bitmap.Config.ARGB_8888)
                val c = android.graphics.Canvas(bmp)
                val p = android.graphics.Paint(android.graphics.Paint.ANTI_ALIAS_FLAG)
                val r = h / 2f
                p.color = track
                c.drawRoundRect(0f, 0f, w.toFloat(), h.toFloat(), r, r, p)
                if (steps > 0) {
                    p.color = fill
                    c.drawRoundRect(0f, 0f, maxOf(h.toFloat(), w * steps / 50f), h.toFloat(), r, r, p)
                }
                bmp
            }

        private fun fmtTime(ms: Long): String {
            val s = (ms / 1000).coerceAtLeast(0)
            return "%d:%02d".format(s / 60, s % 60)
        }

        /** Gambar penuh. Dipakai saat widget baru, ukuran / tema berubah, atau sampul berganti. */
        fun build(context: Context, snap: WidgetSnapshot, size: IntArray): RemoteViews {
            val views = RemoteViews(context.packageName, R.layout.widget_lyrics)
            val theme = WidgetThemes.get(LyricsWidgetStore.style(context))
            val main = theme.main
            val soft = theme.soft
            views.setTextViewText(R.id.widget_title, snap.title)
            views.setTextViewText(R.id.widget_artist, snap.artist)
            // Baris baru ditulis ke slot yang akan ditampilkan; ViewFlipper menggesernya masuk dengan animasi.
            val slotA = snap.slot == 0
            views.setTextViewText(if (slotA) R.id.widget_line_a else R.id.widget_line_b, snap.line)
            views.setTextViewText(if (slotA) R.id.widget_next_a else R.id.widget_next_b, snap.next)
            views.setDisplayedChild(R.id.widget_flipper, if (slotA) 0 else 1)
            views.setImageViewBitmap(R.id.widget_bg_image, background(snap.color, size, theme, LyricsWidgetStore.opacity(context)))
            views.setTextColor(R.id.widget_title, main)
            views.setTextColor(R.id.widget_artist, soft)
            views.setTextColor(R.id.widget_line_a, main)
            views.setTextColor(R.id.widget_line_b, main)
            views.setTextColor(R.id.widget_next_a, soft)
            views.setTextColor(R.id.widget_next_b, soft)
            for (id in intArrayOf(R.id.widget_prev, R.id.widget_play, R.id.widget_next)) {
                views.setInt(id, "setColorFilter", main)
            }
            // Pilihan tampilan: sampul, baris berikutnya, progres.
            val showCover = LyricsWidgetStore.showCover(context)
            val showNext = LyricsWidgetStore.showNext(context)
            val showProgress = LyricsWidgetStore.showProgress(context)
            views.setViewVisibility(R.id.widget_cover, if (showCover) android.view.View.VISIBLE else android.view.View.GONE)
            for (id in intArrayOf(R.id.widget_next_a, R.id.widget_next_b)) {
                views.setViewVisibility(id, if (showNext) android.view.View.VISIBLE else android.view.View.GONE)
            }
            views.setViewVisibility(R.id.widget_progress_row, if (showProgress) android.view.View.VISIBLE else android.view.View.GONE)

            val cover = coverBitmap(context, snap.coverVersion)
            if (cover != null) views.setImageViewBitmap(R.id.widget_cover, cover)
            else views.setImageViewResource(R.id.widget_cover, R.drawable.widget_cover_placeholder)
            views.setImageViewResource(
                R.id.widget_play,
                if (snap.playing) android.R.drawable.ic_media_pause else android.R.drawable.ic_media_play
            )
            views.setOnClickPendingIntent(R.id.widget_prev, broadcast(context, ACTION_PREV, 1))
            views.setOnClickPendingIntent(R.id.widget_play, broadcast(context, ACTION_PLAY_PAUSE, 2))
            views.setOnClickPendingIntent(R.id.widget_next, broadcast(context, ACTION_NEXT, 3))
            val open = PendingIntent.getActivity(
                context,
                0,
                Intent(context, MainActivity::class.java).addFlags(Intent.FLAG_ACTIVITY_NEW_TASK or Intent.FLAG_ACTIVITY_SINGLE_TOP),
                PendingIntent.FLAG_IMMUTABLE or PendingIntent.FLAG_UPDATE_CURRENT
            )
            views.setOnClickPendingIntent(R.id.widget_root, open)

            // Progres + waktu. Ketuk bagian mana pun di bar untuk loncat ke sana (10 bagian, tiap bagian = 10% lagu).
            applyProgress(views, snap, theme)
            val seekIds = intArrayOf(
                R.id.widget_seek_0, R.id.widget_seek_1, R.id.widget_seek_2, R.id.widget_seek_3, R.id.widget_seek_4,
                R.id.widget_seek_5, R.id.widget_seek_6, R.id.widget_seek_7, R.id.widget_seek_8, R.id.widget_seek_9
            )
            for (i in 0 until SEGMENTS) {
                // Tengah segmen: ketuk segmen ke-i meloncat ke (i + 0,5) / 10 lagu.
                val permille = ((i * 2 + 1) * 1000) / (SEGMENTS * 2)
                views.setOnClickPendingIntent(seekIds[i], seekIntent(context, i, permille))
            }
            return views
        }

        private fun applyProgress(views: RemoteViews, snap: WidgetSnapshot, theme: WidgetTheme) {
            val steps = snap.progress.coerceAtLeast(0)
            views.setImageViewBitmap(R.id.widget_progress_img, progressBitmap(steps, theme.main, theme.track))
            val dur = snap.durationMs
            views.setTextViewText(R.id.widget_time_left, if (dur > 0) fmtTime(dur * steps / 50L) else "0:00")
            views.setTextViewText(R.id.widget_time_right, if (dur > 0) fmtTime(dur) else "--:--")
            views.setTextColor(R.id.widget_time_left, theme.soft)
            views.setTextColor(R.id.widget_time_right, theme.soft)
        }

        /**
         * Hanya bagian yang berubah dari [prev] ke [snap]. Mengembalikan null kalau tidak ada yang perlu dikirim.
         * Latar, sampul, warna, dan aksi ketuk tidak disentuh sehingga tetap diam di layar.
         */
        private fun buildPartial(context: Context, prev: WidgetSnapshot, snap: WidgetSnapshot): RemoteViews? {
            // Selalu kirim SELURUH bagian yang berubah-ubah (bukan selisih): kalau satu pembaruan sebelumnya dibuang peluncur,
            // pembaruan berikutnya otomatis memulihkan keadaan yang benar. Teks yang sama tidak menimbulkan kedip / animasi.
            if (snap == prev) return null
            val views = RemoteViews(context.packageName, R.layout.widget_lyrics)
            val theme = WidgetThemes.get(LyricsWidgetStore.style(context))
            views.setTextViewText(R.id.widget_title, snap.title)
            views.setTextViewText(R.id.widget_artist, snap.artist)
            val slotA = snap.slot == 0
            views.setTextViewText(if (slotA) R.id.widget_line_a else R.id.widget_line_b, snap.line)
            views.setTextViewText(if (slotA) R.id.widget_next_a else R.id.widget_next_b, snap.next)
            if (snap.slot != prev.slot) views.setDisplayedChild(R.id.widget_flipper, if (slotA) 0 else 1)
            views.setImageViewResource(
                R.id.widget_play,
                if (snap.playing) android.R.drawable.ic_media_pause else android.R.drawable.ic_media_play
            )
            if (snap.progress != prev.progress || snap.durationMs != prev.durationMs) applyProgress(views, snap, theme)
            return views
        }
    }
}
