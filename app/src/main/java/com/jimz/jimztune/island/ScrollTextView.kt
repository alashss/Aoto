package com.jimz.jimztune.island

import android.content.Context
import android.os.SystemClock
import android.widget.TextView

/**
 * Teks satu baris yang bergeser pelan (kiri, jeda, ulang) kalau terlalu panjang. Tidak memakai marquee bawaan TextView,
 * karena marquee itu bergantung pada fokus jendela dan tidak jalan di jendela overlay seperti Dynamic Island.
 */
class ScrollTextView(context: Context) : TextView(context) {
    private var startAt = SystemClock.uptimeMillis()
    private var running = false
    private val density = context.resources.displayMetrics.density

    init {
        setSingleLine()
        setHorizontallyScrolling(true)
        ellipsize = null
    }

    override fun setText(text: CharSequence?, type: BufferType?) {
        super.setText(text, type)
        startAt = SystemClock.uptimeMillis()
        scrollTo(0, 0)
    }

    override fun onAttachedToWindow() {
        super.onAttachedToWindow()
        running = true
        postOnAnimation(tick)
    }

    override fun onDetachedFromWindow() {
        running = false
        removeCallbacks(tick)
        super.onDetachedFromWindow()
    }

    private val tick = object : Runnable {
        override fun run() {
            if (!running) return
            val avail = width - compoundPaddingLeft - compoundPaddingRight
            val text = text?.toString().orEmpty()
            val tw = if (text.isEmpty()) 0f else paint.measureText(text)
            if (avail <= 0 || tw <= avail + 1f || visibility != VISIBLE) {
                if (scrollX != 0) scrollTo(0, 0)
            } else {
                val maxScroll = tw - avail + 8f * density
                val speed = 38f * density // piksel per detik
                val hold = 1_600L
                val travel = (maxScroll / speed * 1000f).toLong()
                val cycle = hold + travel + hold
                val t = (SystemClock.uptimeMillis() - startAt) % cycle
                val x = when {
                    t < hold -> 0f
                    t < hold + travel -> maxScroll * (t - hold) / travel.toFloat()
                    else -> maxScroll
                }
                val xi = x.toInt()
                if (xi != scrollX) scrollTo(xi, 0)
            }
            postOnAnimation(this)
        }
    }
}
