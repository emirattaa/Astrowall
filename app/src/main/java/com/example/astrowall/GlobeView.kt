package com.example.astrowall

import android.content.Context
import android.graphics.*
import android.view.View
import kotlin.math.min

/** Açılış: silik gelir, geçmişten bugüne dönerek yerine oturur, gerçek NASA görüntüsüne dönüşür, sonra silinip gider. */
class GlobeView(ctx: Context, private val moon: Boolean, private val onDone: () -> Unit) : View(ctx) {
    private val s = 480
    private val out = IntArray(s * s)
    private val bmp = Bitmap.createBitmap(s, s, Bitmap.Config.ARGB_8888)
    private val bmp2 = Bitmap.createBitmap(s, s, Bitmap.Config.ARGB_8888)
    private val lock = Any()
    private val paint = Paint(Paint.FILTER_BITMAP_FLAG)
    @Volatile private var running = true
    @Volatile private var alpha = 0f
    @Volatile private var scale = 0.9f
    @Volatile private var mix = 0f
    private var finished = false
    private val total = 5600L
    private val spinDur = 3600f

    init {
        setBackgroundColor(Color.BLACK)
        setOnClickListener { finish() }
        Thread {
            val tex = Astro.loadTex(context, if (moon) "moon.jpg" else "earth.jpg", 2048)
            val photo = Real.cached(context, moon)
            var hasPhoto = false
            if (photo != null) {
                val layer = if (moon) Real.moonLayer(photo, s) else Real.earthLayer(photo, s)
                photo.recycle()
                bmp2.setPixels(layer, 0, s, 0, 0, s, s)
                hasPhoto = true
            }
            val meta = context.getSharedPreferences("meta", 0)
            val epicMs = meta.getLong("epic_ms", 0L)
            val useMeta = !moon && hasPhoto && epicMs > 0
            val endMs = if (useMeta) epicMs else System.currentTimeMillis()
            val lat = meta.getFloat("epic_lat", 23f).toDouble()
            val lon = meta.getFloat("epic_lon", 0f).toDouble()
            val t0 = System.nanoTime()
            while (running) {
                val e = (System.nanoTime() - t0) / 1_000_000L
                if (e >= total) break
                val x = min(1f, e / spinDur)
                val p = 1f - (1f - x) * (1f - x) * (1f - x)
                val back = if (moon) 14.7 * Astro.DAY else Astro.DAY
                val ms = endMs - (back * (1 - p)).toLong()
                if (useMeta) Astro.disc(tex, moon, s, ms, endMs, out, lat, lon)
                else Astro.disc(tex, moon, s, ms, endMs, out)
                synchronized(lock) { bmp.setPixels(out, 0, s, 0, 0, s, s) }
                val fin = (e / 1300f).coerceIn(0f, 1f)
                val fout = ((e - 4100f) / 1500f).coerceIn(0f, 1f)
                val easeIn = 1f - (1f - fin) * (1f - fin)
                alpha = easeIn * (1f - fout * fout)
                scale = (0.9f + 0.1f * easeIn) * (1f + 0.08f * fout)
                mix = if (hasPhoto) ((e - 3300f) / 1000f).coerceIn(0f, 1f) else 0f
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
        val dst = RectF(cx - size / 2, cy - size / 2, cx + size / 2, cy + size / 2)
        synchronized(lock) {
            c.save()
            c.scale(scale, scale, cx, cy)
            paint.alpha = (a * (1f - mix)).toInt()
            c.drawBitmap(bmp, null, dst, paint)
            if (mix > 0f) { paint.alpha = (a * mix).toInt(); c.drawBitmap(bmp2, null, dst, paint) }
            c.restore()
        }
    }
}
