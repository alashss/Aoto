package com.jimz.jimztune.island

import android.animation.Animator
import android.animation.AnimatorListenerAdapter
import android.animation.ObjectAnimator
import android.animation.ValueAnimator
import android.content.Context
import android.content.Intent
import android.graphics.Bitmap
import android.graphics.Color
import android.graphics.Outline
import android.graphics.PixelFormat
import android.graphics.Typeface
import android.graphics.drawable.GradientDrawable
import android.os.Build
import android.os.Handler
import android.os.Looper
import android.provider.Settings
import android.text.TextUtils
import android.view.GestureDetector
import android.view.Gravity
import android.view.MotionEvent
import android.view.View
import android.view.ViewGroup
import android.view.ViewOutlineProvider
import android.view.WindowManager
import android.view.animation.DecelerateInterpolator
import android.view.animation.LinearInterpolator
import android.view.ViewConfiguration
import android.view.animation.OvershootInterpolator
import android.widget.FrameLayout
import android.widget.ImageView
import android.widget.LinearLayout
import android.widget.TextView
import androidx.core.graphics.drawable.toBitmap
import androidx.media3.common.C
import androidx.media3.common.MediaItem
import androidx.media3.common.MediaMetadata
import androidx.media3.common.Player
import coil.imageLoader
import coil.request.ImageRequest
import coil.request.SuccessResult
import com.jimz.jimztune.AppGraph
import com.jimz.jimztune.MainActivity
import com.jimz.jimztune.widget.WidgetArt
import kotlinx.coroutines.CoroutineScope
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.Job
import kotlinx.coroutines.SupervisorJob
import kotlinx.coroutines.cancel
import kotlinx.coroutines.launch
import kotlinx.coroutines.withContext
import kotlin.math.abs
import kotlin.math.roundToInt

/**
 * Dynamic Island: pil musik di atas layar (menutupi area kamera) yang muncul saat musik diputar dan Aoto tidak
 * sedang dibuka. Dua bentuk yang berganti dengan animasi membal:
 *  - Ringkas: sampul bulat yang berputar, baris lirik / judul, batang equalizer, dan garis progres tipis.
 *  - Terbuka (besar): sampul, judul, artis, lirik, progres yang bisa digeser, dan tombol kontrol; isinya muncul bertahap.
 * Gestur: ketuk = buka/tutup, geser kiri/kanan (ringkas) = lagu berikutnya/sebelumnya, geser atas = tutup,
 * geser bawah = buka, tahan = buka Aoto. Area sentuh sengaja dibuat lebih luas dari pilnya.
 * Butuh izin "tampil di atas aplikasi lain".
 */
class IslandController(private val context: Context, private val player: Player) {
    private class Ui(
        val root: FrameLayout,
        val pill: FrameLayout,
        val bg: GradientDrawable,
        val compact: FrameLayout,
        val compactCover: ImageView,
        val compactText: TextView,
        val compactEq: IslandEqView,
        val thin: IslandThinProgress,
        val miniPlay: IslandGlyphView,
        val full: LinearLayout,
        val coverBox: FrameLayout,
        val cover: ImageView,
        val texts: LinearLayout,
        val title: TextView,
        val artist: TextView,
        val fullEq: IslandEqView,
        val lyric: TextView,
        val progressRow: LinearLayout,
        val progress: IslandProgressView,
        val timeLeft: TextView,
        val timeRight: TextView,
        val controls: LinearLayout,
        val prev: IslandGlyphView,
        val play: IslandGlyphView,
        val next: IslandGlyphView
    )

    private val wm = context.getSystemService(Context.WINDOW_SERVICE) as WindowManager
    private val main = Handler(Looper.getMainLooper())
    private val scope = CoroutineScope(SupervisorJob() + Dispatchers.Main)
    private var coverJob: Job? = null

    private var ui: Ui? = null
    private var shownStyle = -1
    private var expanded = false
    private var wideCompact = false
    private var lyricSeen = false
    private var lyricShownInFull = false
    private var coverId: String? = null
    private var coverColor: Int = WidgetArt.DEFAULT_COLOR
    private var lastCompactText: String? = null

    // Jeda: pil tidak hilang, tapi mengecil jadi "mini" (sampul + ikon putar). Ketuk = lanjut main.
    private var paused = false
    private var idleHidden = false
    private val pauseHide = Runnable {
        idleHidden = true
        refresh()
    }

    private var curW = 0f
    private var curH = 0f
    private var radius = 0f
    private var anim: ValueAnimator? = null
    private var spin: ObjectAnimator? = null
    private var glow: ValueAnimator? = null
    private var glowAlpha = 0.25f

    private val collapse = Runnable { setExpanded(false) }
    private val tick = object : Runnable {
        override fun run() {
            if (ui == null) return
            update()
            main.postDelayed(this, 250L)
        }
    }

    private val listener = object : Player.Listener {
        override fun onIsPlayingChanged(isPlaying: Boolean) = refresh()
        override fun onPlayWhenReadyChanged(playWhenReady: Boolean, reason: Int) {
            trackIdle(playWhenReady)
            refresh()
        }
        override fun onMediaItemTransition(mediaItem: MediaItem?, reason: Int) {
            refresh()
            if (ui != null && mediaItem != null && IslandPrefs.autoExpand) peek()
        }
        override fun onMediaMetadataChanged(mediaMetadata: MediaMetadata) = refresh()
    }

    fun start() {
        player.addListener(listener)
        AppGraph.onVisibilityChanged = { main.post { refresh() } }
        IslandPrefs.onChange = { main.post { refresh() } }
        trackIdle(player.playWhenReady)
        refresh()
    }

    /** Terlalu lama dijeda (10 menit): pil mini ikut hilang supaya tidak menempel selamanya. */
    private fun trackIdle(playWhenReady: Boolean) {
        main.removeCallbacks(pauseHide)
        if (playWhenReady) idleHidden = false else main.postDelayed(pauseHide, 10 * 60_000L)
    }

    fun release() {
        player.removeListener(listener)
        if (AppGraph.onVisibilityChanged != null) AppGraph.onVisibilityChanged = null
        IslandPrefs.onChange = null
        com.jimz.jimztune.widget.LyricsWidgetDriver.needLyrics = false
        main.removeCallbacks(pauseHide)
        hide()
        scope.cancel()
    }

    private fun canDraw(): Boolean = try {
        Settings.canDrawOverlays(context)
    } catch (_: Exception) {
        false
    }

    private fun refresh() {
        // Lirik dihitung oleh mesin widget lirik; kita hanya meminta ia tetap berjalan.
        val need = IslandPrefs.enabled && IslandPrefs.showLyrics
        if (need != com.jimz.jimztune.widget.LyricsWidgetDriver.needLyrics) {
            com.jimz.jimztune.widget.LyricsWidgetDriver.needLyrics = need
            com.jimz.jimztune.widget.LyricsWidgetDriver.instance?.onWidgetsChanged()
        }
        val alive = player.playWhenReady || (IslandPrefs.keepPaused && !idleHidden)
        val want = IslandPrefs.enabled && canDraw() && !AppGraph.appVisible &&
            player.currentMediaItem != null && alive
        if (!want) {
            hide()
            return
        }
        if (ui == null || shownStyle != IslandPrefs.style) {
            val first = ui == null
            hide()
            show()
            if (first && ui != null && IslandPrefs.autoExpand && player.playWhenReady) peek()
        }
        update()
    }

    // ---------- ukuran ----------

    private fun dp(v: Int) = (v * context.resources.displayMetrics.density).toInt()
    private fun dpf(v: Float) = v * context.resources.displayMetrics.density

    private val compactH get() = dp(38)
    private val narrowW get() = dp(124)
    private val wideW get() = dp(236)
    private val miniW get() = dp(82)
    private val miniH get() = dp(34)
    private val fullW get() = minOf(dp(404), context.resources.displayMetrics.widthPixels - dp(16))

    /** Ruang sentuh ekstra di sekeliling pil, supaya pil kecil mudah diketuk. */
    private val touchPad get() = dp(10)

    private fun fullHeight(u: Ui): Int {
        u.full.measure(
            View.MeasureSpec.makeMeasureSpec(fullW, View.MeasureSpec.EXACTLY),
            View.MeasureSpec.makeMeasureSpec(0, View.MeasureSpec.UNSPECIFIED)
        )
        return u.full.measuredHeight
    }

    private fun targetSize(u: Ui): Pair<Int, Int> =
        when {
            expanded -> fullW to fullHeight(u)
            paused -> miniW to miniH
            else -> (if (wideCompact) wideW else narrowW) to compactH
        }

    private fun applySize(w: Float, h: Float) {
        val u = ui ?: return
        curW = w
        curH = h
        val lp = u.pill.layoutParams
        lp.width = w.roundToInt()
        lp.height = h.roundToInt()
        u.pill.layoutParams = lp
        radius = minOf(h / 2f, dpf(36f))
        u.bg.cornerRadius = radius
        u.pill.invalidateOutline()
    }

    private fun applyLayerState(u: Ui, expandedFinal: Boolean) {
        u.full.alpha = if (expandedFinal) 1f else 0f
        u.compact.alpha = if (expandedFinal) 0f else 1f
        syncVisibility(u)
        if (!expandedFinal) resetStagger(u)
    }

    private fun syncVisibility(u: Ui) {
        u.full.visibility = if (u.full.alpha > 0.01f) View.VISIBLE else View.INVISIBLE
        u.compact.visibility = if (u.compact.alpha > 0.01f) View.VISIBLE else View.INVISIBLE
        syncSpin()
    }

    /** Isi pil terbuka muncul satu per satu dari bawah, sampulnya membesar. */
    private fun staggerIn(u: Ui) {
        val parts = listOf<View>(u.coverBox, u.texts, u.lyric, u.progressRow, u.controls)
        parts.forEachIndexed { i, v ->
            v.animate().cancel()
            v.alpha = 0f
            v.translationY = dpf(16f)
            v.animate().alpha(1f).translationY(0f)
                .setStartDelay(110L + i * 60L).setDuration(320L)
                .setInterpolator(DecelerateInterpolator(1.6f)).start()
        }
        u.coverBox.scaleX = 0.6f
        u.coverBox.scaleY = 0.6f
        u.coverBox.animate().scaleX(1f).scaleY(1f).setStartDelay(90L).setDuration(460L)
            .setInterpolator(OvershootInterpolator(1.6f)).start()
        u.play.scaleX = 0.7f
        u.play.scaleY = 0.7f
        u.play.animate().scaleX(1f).scaleY(1f).setStartDelay(330L).setDuration(420L)
            .setInterpolator(OvershootInterpolator(2.2f)).start()
    }

    private fun resetStagger(u: Ui) {
        listOf<View>(u.coverBox, u.texts, u.lyric, u.progressRow, u.controls, u.play).forEach {
            it.animate().cancel()
            it.alpha = 1f
            it.translationY = 0f
            it.scaleX = 1f
            it.scaleY = 1f
        }
    }

    /** Animasikan pil ke ukuran target. [stateChanged] = berganti antara ringkas dan terbuka. */
    private fun transition(stateChanged: Boolean, animate: Boolean = true) {
        val u = ui ?: return
        val (tw, th) = targetSize(u)
        val old = anim
        anim = null
        old?.cancel()
        if (!animate || curW <= 0f) {
            applySize(tw.toFloat(), th.toFloat())
            applyLayerState(u, expanded)
            return
        }
        val w0 = curW
        val h0 = curH
        val opening = expanded
        if (stateChanged && opening) staggerIn(u)
        val a = ValueAnimator.ofFloat(0f, 1f)
        a.duration = if (stateChanged) (if (opening) 560L else 340L) else 280L
        a.interpolator = when {
            !stateChanged -> DecelerateInterpolator()
            opening -> OvershootInterpolator(1.05f)
            else -> DecelerateInterpolator(1.7f)
        }
        a.addUpdateListener {
            val cu = ui ?: return@addUpdateListener
            val v = it.animatedValue as Float
            applySize(w0 + (tw - w0) * v, h0 + (th - h0) * v)
            if (stateChanged) {
                val vc = v.coerceIn(0f, 1f)
                if (opening) {
                    cu.compact.alpha = (1f - vc / 0.25f).coerceIn(0f, 1f)
                    cu.full.alpha = ((vc - 0.12f) / 0.4f).coerceIn(0f, 1f)
                } else {
                    cu.full.alpha = (1f - vc / 0.3f).coerceIn(0f, 1f)
                    cu.compact.alpha = ((vc - 0.4f) / 0.5f).coerceIn(0f, 1f)
                }
                syncVisibility(cu)
            }
        }
        a.addListener(object : AnimatorListenerAdapter() {
            override fun onAnimationEnd(animation: Animator) {
                val cu = ui ?: return
                if (anim !== a) return
                anim = null
                applyLayerState(cu, expanded)
            }
        })
        anim = a
        a.start()
    }

    // ---------- tampilan ----------

    private class Pal(val fg: Int, val soft: Int, val track: Int, val btn: Int, val btnFg: Int, val stroke: Int)

    private fun palette(style: Int): Pal = if (IslandPrefs.theme(style).light) {
        Pal(0xFF14172B.toInt(), 0x9914172B.toInt(), 0x2214172B, 0xFF14172B.toInt(), Color.WHITE, IslandPrefs.theme(style).stroke)
    } else {
        Pal(Color.WHITE, 0xB3FFFFFF.toInt(), 0x33FFFFFF, Color.WHITE, 0xFF0B0B10.toInt(), IslandPrefs.theme(style).stroke)
    }

    /** Warna latar: 1 warna = polos, lebih dari 1 = gradasi. Tema "Warna sampul" diturunkan dari sampul lagu. */
    private fun bgColors(style: Int): IntArray {
        val t = IslandPrefs.theme(style)
        t.colors?.let { return it }
        val hsv = FloatArray(3)
        Color.colorToHSV(coverColor, hsv)
        hsv[1] = minOf(hsv[1], 0.85f)
        val top = hsv.copyOf().also { it[2] = (it[2] * 0.55f).coerceIn(0.14f, 0.38f) }
        val bottom = hsv.copyOf().also {
            it[0] = (it[0] + 12f) % 360f
            it[2] = (it[2] * 0.28f).coerceIn(0.06f, 0.16f)
        }
        return intArrayOf(Color.HSVToColor(top), Color.HSVToColor(bottom))
    }

    private fun applyBg(bg: GradientDrawable, style: Int) {
        val cols = bgColors(style)
        if (cols.size == 1) {
            bg.setColor(cols[0])
        } else {
            bg.setOrientation(GradientDrawable.Orientation.TL_BR)
            bg.setColors(cols)
        }
    }

    /** Warna aksen untuk equalizer dan progres: tetap per tema, atau diturunkan dari sampul. */
    private fun accent(style: Int): Int {
        val t = IslandPrefs.theme(style)
        if (t.accent != 0) return t.accent
        val hsv = FloatArray(3)
        Color.colorToHSV(coverColor, hsv)
        if (hsv[1] >= 0.15f) hsv[1] = hsv[1].coerceAtLeast(0.5f)
        hsv[2] = if (t.light) 0.55f else 0.97f
        return Color.HSVToColor(hsv)
    }

    private fun roundOutline(r: Int) = object : ViewOutlineProvider() {
        override fun getOutline(view: View, outline: Outline) {
            outline.setRoundRect(0, 0, view.width, view.height, r.toFloat())
        }
    }

    private fun ovalOutline() = object : ViewOutlineProvider() {
        override fun getOutline(view: View, outline: Outline) {
            outline.setOval(0, 0, view.width, view.height)
        }
    }

    private fun coverView(cornerDp: Int, round: Boolean = false): ImageView = ImageView(context).apply {
        scaleType = ImageView.ScaleType.CENTER_CROP
        setBackgroundColor(0x33808080)
        clipToOutline = true
        outlineProvider = if (round) ovalOutline() else roundOutline(dp(cornerDp))
    }

    private fun scrollText(color: Int, sp: Float, bold: Boolean = false): TextView = ScrollTextView(context).apply {
        setTextColor(color)
        textSize = sp
        typeface = Typeface.create(if (bold) "sans-serif-medium" else "sans-serif", if (bold) Typeface.BOLD else Typeface.NORMAL)
        includeFontPadding = false
    }

    private fun text(color: Int, sp: Float, bold: Boolean = false): TextView = TextView(context).apply {
        setTextColor(color)
        textSize = sp
        typeface = Typeface.create(if (bold) "sans-serif-medium" else "sans-serif", if (bold) Typeface.BOLD else Typeface.NORMAL)
        includeFontPadding = false
    }

    private fun show() {
        val style = IslandPrefs.style
        shownStyle = style
        val pal = palette(style)
        val minimal = IslandPrefs.theme(style).minimal

        val bg = GradientDrawable().apply {
            cornerRadius = compactH / 2f
            setStroke(dp(1), pal.stroke)
        }
        applyBg(bg, style)
        val pill = FrameLayout(context).apply {
            background = bg
            clipToOutline = true
            outlineProvider = object : ViewOutlineProvider() {
                override fun getOutline(view: View, outline: Outline) {
                    outline.setRoundRect(0, 0, view.width, view.height, radius)
                }
            }
            elevation = dpf(8f)
        }

        // --- bentuk ringkas ---
        val compact = FrameLayout(context)
        val compactCover = coverView(0, round = true)
        compact.addView(
            compactCover,
            FrameLayout.LayoutParams(dp(28), dp(28), Gravity.START or Gravity.CENTER_VERTICAL).apply { marginStart = dp(5) }
        )
        // Lirik yang kepanjangan bergeser pelan otomatis (marquee) alih-alih terpotong.
        val compactText = scrollText(pal.fg, 12f, bold = true).apply {
            gravity = Gravity.CENTER_VERTICAL
            visibility = View.GONE
        }
        compact.addView(
            compactText,
            FrameLayout.LayoutParams(ViewGroup.LayoutParams.MATCH_PARENT, ViewGroup.LayoutParams.WRAP_CONTENT, Gravity.CENTER_VERTICAL)
                .apply {
                    marginStart = dp(41)
                    marginEnd = dp(34)
                }
        )
        val compactEq = IslandEqView(context)
        compact.addView(
            compactEq,
            FrameLayout.LayoutParams(dp(15), dp(15), Gravity.END or Gravity.CENTER_VERTICAL).apply { marginEnd = dp(13) }
        )
        val thin = IslandThinProgress(context).apply { trackColor = pal.track }
        compact.addView(
            thin,
            FrameLayout.LayoutParams(ViewGroup.LayoutParams.MATCH_PARENT, dp(2), Gravity.BOTTOM).apply {
                marginStart = dp(16)
                marginEnd = dp(16)
                bottomMargin = dp(3)
            }
        )

        // Ikon putar kecil: hanya tampil saat jeda (bentuk mini). Tidak menangkap sentuhan; ketukan ditangani pil.
        val miniPlay = IslandGlyphView(context, IslandGlyphView.PLAY).apply {
            tint = pal.fg
            circleColor = 0
            touchable = false
            isClickable = false
            visibility = View.GONE
        }
        compact.addView(
            miniPlay,
            FrameLayout.LayoutParams(dp(22), dp(22), Gravity.END or Gravity.CENTER_VERTICAL).apply { marginEnd = dp(12) }
        )

        // --- bentuk terbuka (besar) ---
        val full = LinearLayout(context).apply {
            orientation = LinearLayout.VERTICAL
            setPadding(dp(20), dp(18), dp(20), dp(12))
            alpha = 0f
            visibility = View.INVISIBLE
        }
        val row1 = LinearLayout(context).apply {
            orientation = LinearLayout.HORIZONTAL
            gravity = Gravity.CENTER_VERTICAL
        }
        val cover = coverView(20)
        val coverBox = FrameLayout(context).apply {
            addView(cover, FrameLayout.LayoutParams(dp(76), dp(76)))
            setOnClickListener { openMain() }
        }
        val texts = LinearLayout(context).apply { orientation = LinearLayout.VERTICAL }
        val title = scrollText(pal.fg, 18f, bold = true)
        val artist = scrollText(pal.soft, 14f)
        texts.addView(title)
        texts.addView(artist, LinearLayout.LayoutParams(ViewGroup.LayoutParams.MATCH_PARENT, ViewGroup.LayoutParams.WRAP_CONTENT).apply { topMargin = dp(5) })
        val fullEq = IslandEqView(context)
        row1.addView(coverBox, LinearLayout.LayoutParams(dp(76), dp(76)))
        row1.addView(texts, LinearLayout.LayoutParams(0, ViewGroup.LayoutParams.WRAP_CONTENT, 1f).apply {
            marginStart = dp(16)
            marginEnd = dp(12)
        })
        row1.addView(fullEq, LinearLayout.LayoutParams(dp(22), dp(22)))
        full.addView(row1, LinearLayout.LayoutParams(ViewGroup.LayoutParams.MATCH_PARENT, ViewGroup.LayoutParams.WRAP_CONTENT))

        val lyric = text(pal.fg, 15f, bold = true).apply {
            maxLines = 3
            setLineSpacing(0f, 1.08f)
            ellipsize = TextUtils.TruncateAt.END
            visibility = View.GONE
        }
        full.addView(lyric, LinearLayout.LayoutParams(ViewGroup.LayoutParams.MATCH_PARENT, ViewGroup.LayoutParams.WRAP_CONTENT).apply { topMargin = dp(16) })

        val progressRow = LinearLayout(context).apply {
            orientation = LinearLayout.HORIZONTAL
            gravity = Gravity.CENTER_VERTICAL
        }
        val timeLeft = text(pal.soft, 12f).apply { minWidth = dp(36) }
        val timeRight = text(pal.soft, 12f).apply {
            minWidth = dp(36)
            gravity = Gravity.END
        }
        val progress = IslandProgressView(context).apply {
            trackColor = pal.track
            fillColor = pal.fg
            onTouched = { keepOpen() }
            onSeek = { f ->
                val d = player.duration
                if (d > 0 && d != C.TIME_UNSET) player.seekTo((d * f).toLong())
                keepOpen()
            }
        }
        progressRow.addView(timeLeft)
        progressRow.addView(progress, LinearLayout.LayoutParams(0, dp(32), 1f).apply {
            marginStart = dp(8)
            marginEnd = dp(8)
        })
        progressRow.addView(timeRight)
        full.addView(progressRow, LinearLayout.LayoutParams(ViewGroup.LayoutParams.MATCH_PARENT, ViewGroup.LayoutParams.WRAP_CONTENT).apply { topMargin = dp(10) })

        val prev = IslandGlyphView(context, IslandGlyphView.PREV).apply {
            tint = pal.fg
            contentDescription = "Sebelumnya"
            setOnClickListener {
                pop(this)
                player.seekToPrevious()
                keepOpen()
            }
        }
        val play = IslandGlyphView(context, IslandGlyphView.PAUSE).apply {
            tint = pal.btnFg
            circleColor = pal.btn
            contentDescription = "Putar atau jeda"
            setOnClickListener {
                pop(this)
                togglePlay()
            }
        }
        val next = IslandGlyphView(context, IslandGlyphView.NEXT).apply {
            tint = pal.fg
            contentDescription = "Berikutnya"
            setOnClickListener {
                pop(this)
                player.seekToNext()
                keepOpen()
            }
        }
        val controls = LinearLayout(context).apply {
            orientation = LinearLayout.HORIZONTAL
            gravity = Gravity.CENTER
        }
        controls.addView(prev, LinearLayout.LayoutParams(dp(68), dp(56)))
        controls.addView(play, LinearLayout.LayoutParams(dp(66), dp(66)).apply {
            marginStart = dp(18)
            marginEnd = dp(18)
        })
        controls.addView(next, LinearLayout.LayoutParams(dp(68), dp(56)))
        full.addView(controls, LinearLayout.LayoutParams(ViewGroup.LayoutParams.MATCH_PARENT, ViewGroup.LayoutParams.WRAP_CONTENT).apply { topMargin = dp(6) })

        pill.addView(compact, FrameLayout.LayoutParams(narrowW, compactH, Gravity.CENTER))
        pill.addView(full, FrameLayout.LayoutParams(fullW, ViewGroup.LayoutParams.WRAP_CONTENT, Gravity.TOP or Gravity.CENTER_HORIZONTAL))

        // Jendela sedikit lebih besar dari pil (touchPad) supaya pil kecil gampang diketuk.
        val root = FrameLayout(context)
        root.setPadding(touchPad, touchPad, touchPad, touchPad)
        root.clipChildren = false
        root.clipToPadding = false
        root.addView(pill, FrameLayout.LayoutParams(narrowW, compactH, Gravity.TOP or Gravity.CENTER_HORIZONTAL))
        root.setOnTouchListener(touchListener(pill))

        val type = if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.O) {
            WindowManager.LayoutParams.TYPE_APPLICATION_OVERLAY
        } else {
            @Suppress("DEPRECATION") WindowManager.LayoutParams.TYPE_PHONE
        }
        val sbId = context.resources.getIdentifier("status_bar_height", "dimen", "android")
        val statusBar = if (sbId > 0) context.resources.getDimensionPixelSize(sbId) else dp(24)
        val lp = WindowManager.LayoutParams(
            WindowManager.LayoutParams.WRAP_CONTENT,
            WindowManager.LayoutParams.WRAP_CONTENT,
            type,
            WindowManager.LayoutParams.FLAG_NOT_FOCUSABLE or
                WindowManager.LayoutParams.FLAG_NOT_TOUCH_MODAL or
                WindowManager.LayoutParams.FLAG_LAYOUT_IN_SCREEN or
                WindowManager.LayoutParams.FLAG_LAYOUT_NO_LIMITS,
            PixelFormat.TRANSLUCENT
        ).apply {
            gravity = Gravity.TOP or Gravity.CENTER_HORIZONTAL
            val pillTop = ((statusBar - compactH) / 2).coerceAtLeast(dp(6))
            y = (pillTop - touchPad).coerceAtLeast(0)
            if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.P) {
                layoutInDisplayCutoutMode = WindowManager.LayoutParams.LAYOUT_IN_DISPLAY_CUTOUT_MODE_SHORT_EDGES
            }
        }
        try {
            wm.addView(root, lp)
        } catch (_: Exception) {
            return
        }
        ui = Ui(
            root, pill, bg, compact, compactCover, compactText, compactEq, thin, miniPlay,
            full, coverBox, cover, texts, title, artist, fullEq, lyric, progressRow, progress, timeLeft, timeRight,
            controls, prev, play, next
        )
        expanded = false
        wideCompact = false
        lyricSeen = false
        lyricShownInFull = false
        lastFullLyric = null
        lastCompactText = null
        coverId = null
        paused = !player.playWhenReady
        val startW = if (paused) miniW else narrowW
        val startH = if (paused) miniH else compactH
        curW = startW.toFloat()
        curH = startH.toFloat()
        radius = startH / 2f
        compact.layoutParams = FrameLayout.LayoutParams(startW, startH, Gravity.CENTER)
        pill.layoutParams = FrameLayout.LayoutParams(startW, startH, Gravity.TOP or Gravity.CENTER_HORIZONTAL)
        if (minimal) compactText.visibility = View.GONE
        syncCompactParts(ui!!)
        applyAccent()
        startGlow()
        enterAnimation(pill)
        main.removeCallbacks(tick)
        main.postDelayed(tick, 250L)
    }

    /**
     * Isi bentuk ringkas menurut keadaan: jalan (lirik / equalizer / progres tipis) atau jeda
     * (hanya sampul redup + ikon putar, lebih kecil).
     */
    private fun syncCompactParts(u: Ui) {
        val minimal = IslandPrefs.theme(shownStyle).minimal
        u.compactText.visibility = if (wideCompact && !paused && !minimal) View.VISIBLE else View.GONE
        u.compactEq.visibility = if (paused) View.GONE else View.VISIBLE
        u.thin.visibility = if (paused) View.INVISIBLE else View.VISIBLE
        u.miniPlay.visibility = if (paused) View.VISIBLE else View.GONE
        u.compactCover.alpha = if (paused) 0.72f else 1f
        val lp = u.compact.layoutParams
        lp.width = when {
            paused -> miniW
            wideCompact -> wideW
            else -> narrowW
        }
        lp.height = if (paused) miniH else compactH
        u.compact.layoutParams = lp
    }

    /** Muncul dari atas dengan membal kecil. */
    private fun enterAnimation(pill: View) {
        pill.alpha = 0f
        pill.scaleX = 0.55f
        pill.scaleY = 0.55f
        pill.translationY = -dpf(20f)
        pill.pivotY = 0f
        pill.post { pill.pivotX = pill.width / 2f }
        pill.animate().alpha(1f).scaleX(1f).scaleY(1f).translationY(0f)
            .setDuration(480L).setInterpolator(OvershootInterpolator(1.6f)).start()
    }

    /** Tombol "membal" kecil saat disentuh. */
    private fun pop(v: View) {
        v.animate().cancel()
        v.scaleX = 0.82f
        v.scaleY = 0.82f
        v.animate().scaleX(1f).scaleY(1f).setDuration(300L).setInterpolator(OvershootInterpolator(3f)).start()
    }

    /**
     * Penanganan sentuhan sendiri (bukan GestureDetector) supaya ketukan tidak hilang kalau jari bergeser sedikit.
     * Ketuk = jarak kecil dan singkat. Pil mengecil sedikit saat ditekan.
     */
    private fun touchListener(pill: View): View.OnTouchListener {
        val slop = ViewConfiguration.get(context).scaledTouchSlop * 2
        var downX = 0f
        var downY = 0f
        var downT = 0L
        var longDone = false
        val longPress = Runnable {
            longDone = true
            openMain()
        }
        fun release() {
            main.removeCallbacks(longPress)
            pill.animate().scaleX(1f).scaleY(1f).setDuration(260L).setInterpolator(OvershootInterpolator(2.4f)).start()
        }
        return View.OnTouchListener { _, ev ->
            when (ev.actionMasked) {
                MotionEvent.ACTION_DOWN -> {
                    downX = ev.rawX
                    downY = ev.rawY
                    downT = ev.eventTime
                    longDone = false
                    pill.pivotX = pill.width / 2f
                    pill.pivotY = 0f
                    pill.animate().scaleX(0.95f).scaleY(0.95f).setDuration(110L).setInterpolator(DecelerateInterpolator()).start()
                    main.postDelayed(longPress, 550L)
                    true
                }
                MotionEvent.ACTION_MOVE -> {
                    if (abs(ev.rawX - downX) > slop * 2 || abs(ev.rawY - downY) > slop * 2) main.removeCallbacks(longPress)
                    true
                }
                MotionEvent.ACTION_UP -> {
                    release()
                    if (!longDone) {
                        val dx = ev.rawX - downX
                        val dy = ev.rawY - downY
                        val min = dpf(24f)
                        when {
                            abs(dx) < slop && abs(dy) < slop -> {
                                if (!expanded && !player.playWhenReady) resumeFromMini(pill) else setExpanded(!expanded)
                            }
                            abs(dy) > abs(dx) && abs(dy) > min -> setExpanded(dy > 0)
                            abs(dx) > min && !expanded -> {
                                pop(pill)
                                if (dx < 0) player.seekToNext() else player.seekToPrevious()
                            }
                            else -> setExpanded(!expanded)
                        }
                    }
                    true
                }
                MotionEvent.ACTION_CANCEL -> {
                    release()
                    true
                }
                else -> true
            }
        }
    }

    /** Garis tepi pil berdenyut halus selama musik diputar. */
    private fun startGlow() {
        glow?.cancel()
        glow = ValueAnimator.ofFloat(0.12f, 0.55f).apply {
            duration = 1500L
            repeatMode = ValueAnimator.REVERSE
            repeatCount = ValueAnimator.INFINITE
            interpolator = DecelerateInterpolator()
            addUpdateListener {
                val u = ui ?: return@addUpdateListener
                glowAlpha = it.animatedValue as Float
                if (IslandPrefs.theme(shownStyle).light && IslandPrefs.theme(shownStyle).accent == 0) return@addUpdateListener
                if (!IslandPrefs.glowEdge) return@addUpdateListener
                val a = accent(shownStyle)
                val col = (a and 0x00FFFFFF) or ((glowAlpha * if (player.isPlaying) 255 else 60).toInt() shl 24)
                u.bg.setStroke(dp(2), col)
            }
            start()
        }
    }

    /** Sampul ringkas berputar seperti piringan hitam, hanya saat musik main dan bentuk ringkas terlihat. */
    private fun syncSpin() {
        val u = ui ?: return
        val should = IslandPrefs.spinCover && player.isPlaying && u.compact.visibility == View.VISIBLE
        if (should) {
            if (spin == null) {
                spin = ObjectAnimator.ofFloat(u.compactCover, View.ROTATION, u.compactCover.rotation, u.compactCover.rotation + 360f).apply {
                    duration = 9000L
                    interpolator = LinearInterpolator()
                    repeatCount = ValueAnimator.INFINITE
                    start()
                }
            }
        } else {
            spin?.cancel()
            spin = null
        }
    }

    private fun applyAccent() {
        val u = ui ?: return
        val a = accent(shownStyle)
        u.compactEq.color = a
        u.fullEq.color = a
        u.progress.fillColor = a
        u.thin.fillColor = a
        if (IslandPrefs.theme(shownStyle).colors == null) applyBg(u.bg, shownStyle)
    }

    // ---------- isi ----------

    private fun update() {
        val u = ui ?: return
        val item = player.currentMediaItem ?: return
        val titleText = item.mediaMetadata.title?.toString().orEmpty()
        val artistText = item.mediaMetadata.artist?.toString().orEmpty()
        val minimal = shownStyle == 3
        val id = item.mediaId
        if (id != coverId) {
            val firstSong = coverId == null
            coverId = id
            lyricSeen = false
            loadCover(item.mediaMetadata.artworkUri?.toString())
            if (!firstSong) {
                // Lagu berganti: sampul ringkas membal dan teks judul meluncur masuk.
                u.compactCover.animate().cancel()
                u.compactCover.scaleX = 0.6f
                u.compactCover.scaleY = 0.6f
                u.compactCover.animate().scaleX(1f).scaleY(1f).setDuration(420L).setInterpolator(OvershootInterpolator(2.4f)).start()
            }
        }

        val lyric = if (IslandPrefs.showLyrics) currentLyric() else null
        if (lyric != null) lyricSeen = true
        val wantWide = lyricSeen && IslandPrefs.showLyrics && !minimal
        val nowPaused = !player.playWhenReady
        if (wantWide != wideCompact || nowPaused != paused) {
            val wasPaused = paused
            wideCompact = wantWide
            paused = nowPaused
            syncCompactParts(u)
            if (!expanded) transition(stateChanged = false)
            // Lanjut main dari mini: pil membal kecil supaya terasa "hidup lagi".
            if (wasPaused && !nowPaused && !expanded) pop(u.pill)
        }

        // Ringkas: baris lirik (atau judul saat jeda lirik). Dilunakkan saat teks berganti.
        if (wideCompact) setCompactText(u, lyric ?: titleText)

        u.title.text = titleText
        u.artist.text = artistText
        u.artist.visibility = if (artistText.isBlank()) View.GONE else View.VISIBLE
        val hasLyric = lyric != null
        setFullLyric(u, lyric)
        u.lyric.visibility = if (hasLyric) View.VISIBLE else View.GONE
        if (hasLyric != lyricShownInFull) {
            lyricShownInFull = hasLyric
            if (expanded) transition(stateChanged = false)
        }

        val playing = player.isPlaying
        u.compactEq.active = playing
        u.fullEq.active = playing
        val kind = if (player.playWhenReady) IslandGlyphView.PAUSE else IslandGlyphView.PLAY
        if (u.play.kind != kind) u.play.kind = kind
        syncSpin()

        val pos = player.currentPosition.coerceAtLeast(0L)
        val dur = player.duration
        val known = dur > 0 && dur != C.TIME_UNSET
        val frac = if (known) pos.toFloat() / dur else 0f
        u.thin.fraction = frac
        if (expanded) {
            u.progress.setFraction(frac)
            u.timeLeft.text = fmt(pos)
            u.timeRight.text = if (known) fmt(dur) else "--:--"
        }
    }

    private var lastFullLyric: String? = null

    /** Lirik di bentuk terbuka: baris lama meluncur naik dan pudar, baris baru naik dari bawah. */
    private fun setFullLyric(u: Ui, value: String?) {
        val t = value.orEmpty()
        if (t == lastFullLyric) return
        val hadText = !lastFullLyric.isNullOrEmpty()
        lastFullLyric = t
        u.lyric.animate().cancel()
        if (!expanded || !hadText || t.isEmpty()) {
            u.lyric.alpha = 1f
            u.lyric.translationY = 0f
            u.lyric.text = t
            return
        }
        u.lyric.animate().alpha(0f).translationY(-dpf(10f)).setDuration(130L).withEndAction {
            val cu = ui ?: return@withEndAction
            cu.lyric.text = t
            cu.lyric.translationY = dpf(14f)
            // Baris baru bisa lebih panjang (2-3 baris): ukuran pil menyesuaikan dengan animasi.
            transition(stateChanged = false)
            cu.lyric.animate().alpha(1f).translationY(0f).setDuration(300L)
                .setInterpolator(DecelerateInterpolator(1.5f)).start()
        }.start()
    }

    private fun setCompactText(u: Ui, value: String) {
        if (value == lastCompactText) return
        val first = lastCompactText == null
        lastCompactText = value
        if (first || expanded) {
            u.compactText.text = value
            return
        }
        u.compactText.animate().alpha(0f).translationY(-dpf(6f)).setDuration(110L).withEndAction {
            val cu = ui ?: return@withEndAction
            cu.compactText.text = value
            cu.compactText.translationY = dpf(6f)
            cu.compactText.animate().alpha(1f).translationY(0f).setDuration(220L).start()
        }.start()
    }

    private fun fmt(ms: Long): String {
        val s = (ms / 1000).coerceAtLeast(0)
        return "%d:%02d".format(s / 60, s % 60)
    }

    /** Baris lirik yang sedang dinyanyikan, atau null kalau lagunya tidak punya lirik bersinkron. */
    private fun currentLyric(): String? {
        val snap = com.jimz.jimztune.widget.LyricsWidgetDriver.instance?.currentSnapshot() ?: return null
        val line = snap.line.trim()
        if (line.isEmpty() || line == "…" || line == com.jimz.jimztune.widget.WidgetSnapshot.IDLE_LINE) return null
        if (line.startsWith("Lirik lagu ini tidak")) return null
        return line
    }

    private fun loadCover(url: String?) {
        coverJob?.cancel()
        if (url.isNullOrBlank()) return
        coverJob = scope.launch {
            val bmp: Bitmap? = withContext(Dispatchers.IO) {
                try {
                    val req = ImageRequest.Builder(context).data(url).allowHardware(false).size(220).build()
                    (context.imageLoader.execute(req) as? SuccessResult)?.drawable?.toBitmap(220, 220)
                } catch (_: Exception) {
                    null
                }
            }
            val u = ui
            if (bmp != null && u != null) {
                u.compactCover.setImageBitmap(bmp)
                u.cover.setImageBitmap(bmp)
                coverColor = WidgetArt.dominantColor(bmp)
                applyAccent()
            }
        }
    }

    // ---------- interaksi ----------

    private fun setExpanded(value: Boolean, hold: Long = 7_000L) {
        if (ui == null) return
        main.removeCallbacks(collapse)
        if (expanded == value) {
            if (value) main.postDelayed(collapse, hold)
            return
        }
        expanded = value
        update()
        transition(stateChanged = true)
        if (value) main.postDelayed(collapse, hold)
    }

    /** Buka sebentar lalu menutup sendiri (dipakai saat lagu berganti). */
    private fun peek() {
        setExpanded(true, hold = 4_500L)
    }

    private fun keepOpen() {
        main.removeCallbacks(collapse)
        if (expanded) main.postDelayed(collapse, 7_000L)
    }

    private fun togglePlay() {
        val wasPlaying = player.playWhenReady
        if (wasPlaying) player.pause() else player.play()
        keepOpen()
        main.postDelayed({
            update()
            // Dijeda dari bentuk besar: tutup pelan-pelan jadi mini, jangan hilang.
            if (wasPlaying && !player.playWhenReady && IslandPrefs.keepPaused) setExpanded(false)
        }, 420L)
    }

    /** Ketuk pil mini (jeda) = langsung lanjut memutar. */
    private fun resumeFromMini(pill: View) {
        pop(pill)
        player.play()
        main.postDelayed({ update() }, 120L)
    }

    private fun openMain() {
        try {
            context.startActivity(
                Intent(context, MainActivity::class.java).addFlags(Intent.FLAG_ACTIVITY_NEW_TASK or Intent.FLAG_ACTIVITY_SINGLE_TOP)
            )
        } catch (_: Exception) {
        }
    }

    private fun hide() {
        main.removeCallbacks(collapse)
        main.removeCallbacks(tick)
        val old = anim
        anim = null
        old?.cancel()
        spin?.cancel()
        spin = null
        glow?.cancel()
        glow = null
        coverJob?.cancel()
        ui?.let {
            try {
                wm.removeView(it.root)
            } catch (_: Exception) {
            }
        }
        ui = null
        expanded = false
        wideCompact = false
        lyricSeen = false
        lyricShownInFull = false
        lastFullLyric = null
        lastCompactText = null
    }
}
