package com.example.astrowall

import android.content.Context
import android.graphics.*
import android.view.View
import kotlin.math.min

/** Açılış animasyonu: silik gelir, geçmişten bugüne dönerek yerine oturur, sonra silinerek gider. */
class GlobeView(ctx: Context, private val moon: Boolean, private val onDone: () -> Unit) : View(ctx) {
    private val s = 480
    private val out = IntArray(s * s)
    private val bmp = Bitmap.createBitmap(s, s, Bitmap.Config.ARGB_8888)
    private val lock = Any()
    private val paint = Paint(Paint.FILTER_BITMAP_FLAG)
    @Volatile private var running = true
    @Volatile private var alpha = 0f
    @Volatile private var scale = 0.9f
    private var finished = false
    private val total = 5400L
    private val spinDur = 3900f

    init {
        setBackgroundColor(Color.BLACK)
        setOnClickListener { finish() }
        Thread {
            val tex = Astro.loadTex(context, if (moon) "moon.jpg" else "earth.jpg", 2048)
            val now = System.currentTimeMillis()
            val t0 = System.nanoTime()
            while (running) {
                val e = (System.nanoTime() - t0) / 1_000_000L
                if (e >= total) break
                val x = min(1f, e / spinDur)
                val p = 1f - (1f - x) * (1f - x) * (1f - x) // easeOutCubic
                val back = if (moon) 14.7 * Astro.DAY else Astro.DAY
                Astro.disc(tex, moon, s, now - (back * (1 - p)).toLong(), now, out)
                synchronized(lock) { bmp.setPixels(out, 0, s, 0, 0, s, s) }
                val fin = (e / 1300f).coerceIn(0f, 1f)
                val fout = ((e - 3900f) / 1500f).coerceIn(0f, 1f)
                val easeIn = 1f - (1f - fin) * (1f - fin)
                alpha = easeIn * (1f - fout * fout)
                scale = (0.9f + 0.1f * easeIn) * (1f + 0.08f * fout)
                postInvalidateOnAnimation()
            }
            if (running) post { finish() }
        }.start()
    }

    private fun finish() {
        if (finished) return
        finished = true
        running = false
        onDone()
    }

    override fun onDetachedFromWindow() { running = false; super.onDetachedFromWindow() }

    override fun onDraw(c: Canvas) {
        val w = width; val h = height
        val a = (alpha * 255).toInt().coerceIn(0, 255)
        Astro.stars(c, w, h, a)
        val cx = w / 2f; val cy = h * 0.42f
        val size = min(w, h) * 0.42f * 2f * 1.2f
        synchronized(lock) {
            paint.alpha = a
            c.save()
            c.scale(scale, scale, cx, cy)
            c.drawBitmap(bmp, null, RectF(cx - size / 2, cy - size / 2, cx + size / 2, cy + size / 2), paint)
            c.restore()
        }
    }
}
