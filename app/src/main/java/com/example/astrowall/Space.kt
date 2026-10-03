package com.example.astrowall

import android.content.Context
import android.graphics.*
import android.view.View
import java.util.Random
import java.util.concurrent.ConcurrentHashMap
import kotlin.math.*

/** Yıldızlı uzay arka planı: derin gradyan, nebula, Samanyolu, renkli ve parlak yıldızlar.
 *  Bir kez çizilir, boyuta göre önbellekte tutulur (animasyonlarda her karede yeniden çizilmez). */
object Space {
    private val cache = ConcurrentHashMap<Long, Bitmap>()
    private val buildLock = Any()
    private fun key(w: Int, h: Int) = w.toLong() * 100000L + h

    fun peek(w: Int, h: Int): Bitmap? = cache[key(w, h)]

    fun bitmap(w: Int, h: Int): Bitmap {
        cache[key(w, h)]?.let { return it }
        return synchronized(buildLock) {
            cache[key(w, h)] ?: run {
                if (cache.size >= 3) cache.clear()
                val b = build(w, h)
                cache[key(w, h)] = b
                b
            }
        }
    }

    /** Arka planı ayrı iş parçacığında hazırlar; hazır olunca done() çağrılır. */
    fun prepare(w: Int, h: Int, done: () -> Unit) {
        if (peek(w, h) != null) { done(); return }
        Thread {
            try { bitmap(w, h) } catch (e: Throwable) { }
            done()
        }.start()
    }

    fun drawCached(c: Canvas, w: Int, h: Int, alpha: Int) {
        val b = peek(w, h) ?: return
        val p = Paint(Paint.FILTER_BITMAP_FLAG)
        p.alpha = alpha.coerceIn(0, 255)
        c.drawBitmap(b, 0f, 0f, p)
    }

    private fun glow(cv: Canvas, p: Paint, x: Float, y: Float, rad: Float, r: Int, g: Int, b: Int, a: Int) {
        p.shader = RadialGradient(x, y, rad, Color.argb(a, r, g, b), Color.argb(0, r, g, b), Shader.TileMode.CLAMP)
        cv.drawCircle(x, y, rad, p)
        p.shader = null
    }

    private fun build(w: Int, h: Int): Bitmap {
        val bmp = Bitmap.createBitmap(w, h, Bitmap.Config.ARGB_8888)
        val cv = Canvas(bmp)
        val wf = w.toFloat(); val hf = h.toFloat()
        val m = max(w, h).toFloat()
        val k = (min(w, h) / 1080f).coerceIn(0.5f, 2.5f)
        val rnd = Random(20261003L)
        val p = Paint(Paint.ANTI_ALIAS_FLAG)

        // 1) derin uzay gradyanı
        p.shader = LinearGradient(0f, 0f, 0f, hf,
            intArrayOf(Color.rgb(4, 6, 18), Color.rgb(1, 2, 8), Color.rgb(5, 3, 14)),
            floatArrayOf(0f, 0.55f, 1f), Shader.TileMode.CLAMP)
        cv.drawRect(0f, 0f, wf, hf, p)
        p.shader = null

        // 2) nebula bulutları
        glow(cv, p, wf * 0.16f, hf * 0.22f, wf * 0.95f, 70, 45, 150, 40)
        glow(cv, p, wf * 0.90f, hf * 0.70f, wf * 0.85f, 20, 95, 155, 32)
        glow(cv, p, wf * 0.30f, hf * 0.94f, wf * 0.75f, 125, 40, 115, 28)
        glow(cv, p, wf * 0.78f, hf * 0.06f, wf * 0.65f, 30, 65, 135, 24)

        // 3) Samanyolu bandı (köşegen)
        val cy = hf * 0.6f
        cv.save()
        cv.rotate(-32f, wf * 0.5f, cy)
        for (i in 0 until 36) {
            val t = i / 35f
            val x = wf * 0.5f + (t - 0.5f) * m * 1.7f
            val y = cy + (rnd.nextFloat() - 0.5f) * m * 0.05f
            glow(cv, p, x, y, m * (0.15f + 0.14f * rnd.nextFloat()), 150, 165, 215, 9)
        }
        for (i in 0 until 16) {
            val t = i / 15f
            val x = wf * 0.5f + (t - 0.5f) * m * 1.2f
            val y = cy + (rnd.nextFloat() - 0.5f) * m * 0.03f
            glow(cv, p, x, y, m * (0.07f + 0.06f * rnd.nextFloat()), 215, 200, 225, 10)
        }
        for (i in 0 until 9) {   // koyu toz şeritleri
            val x = wf * 0.5f + (rnd.nextFloat() - 0.5f) * m * 1.4f
            val y = cy + (rnd.nextFloat() - 0.5f) * m * 0.05f
            glow(cv, p, x, y, m * (0.03f + 0.03f * rnd.nextFloat()), 0, 0, 3, 70)
        }
        repeat(1700) {
            val x = wf * 0.5f + (rnd.nextFloat() - 0.5f) * m * 1.7f
            val y = cy + rnd.nextGaussian().toFloat() * m * 0.075f
            p.color = Color.argb(40 + rnd.nextInt(140), 235, 235, 255)
            cv.drawCircle(x, y, (0.35f + rnd.nextFloat() * 0.5f) * k, p)
        }
        cv.restore()

        // 4) kenar karartma (vinyet)
        p.shader = RadialGradient(wf / 2f, hf * 0.45f, hypot(wf, hf) * 0.6f,
            intArrayOf(Color.TRANSPARENT, Color.argb(120, 0, 0, 0)),
            floatArrayOf(0.5f, 1f), Shader.TileMode.CLAMP)
        cv.drawRect(0f, 0f, wf, hf, p)
        p.shader = null

        // 5) renkli yıldızlar
        val count = (w.toLong() * h / 3800L).toInt()
        repeat(count) {
            val x = rnd.nextFloat() * wf
            val y = rnd.nextFloat() * hf
            val s = rnd.nextFloat()
            val size = (0.35f + s * s * s * 1.5f) * k
            val a = (70 + (s * 150).toInt() + rnd.nextInt(36)).coerceAtMost(255)
            val tp = rnd.nextInt(100)
            p.color = when {
                tp < 58 -> Color.argb(a, 255, 255, 255)
                tp < 76 -> Color.argb(a, 170, 200, 255)
                tp < 90 -> Color.argb(a, 255, 238, 190)
                else -> Color.argb(a, 255, 190, 150)
            }
            cv.drawCircle(x, y, size, p)
        }

        // 6) parlak yıldızlar: hale + çapraz ışın
        repeat(9) {
            val x = (0.08f + rnd.nextFloat() * 0.84f) * wf
            val y = (0.04f + rnd.nextFloat() * 0.90f) * hf
            val kind = rnd.nextInt(3)
            val r = if (kind == 0) 200 else 255
            val g = if (kind == 0) 220 else if (kind == 1) 245 else 210
            val b = if (kind == 0) 255 else if (kind == 1) 215 else 180
            val rad = (14f + rnd.nextFloat() * 14f) * k
            glow(cv, p, x, y, rad, r, g, b, 80)
            p.color = Color.argb(255, 255, 255, 255)
            cv.drawCircle(x, y, 1.6f * k, p)
            val len = rad * (1.4f + rnd.nextFloat())
            p.color = Color.argb(110, r, g, b)
            p.strokeWidth = k.coerceAtLeast(1f)
            cv.drawLine(x - len, y, x + len, y, p)
            cv.drawLine(x, y - len, x, y + len, p)
        }
        return bmp
    }
}

/** Menü arkasındaki yıldızlı uzay. */
class SpaceView(ctx: Context) : View(ctx) {
    override fun onSizeChanged(w: Int, h: Int, ow: Int, oh: Int) {
        super.onSizeChanged(w, h, ow, oh)
        if (w > 0 && h > 0) Space.prepare(w, h) { postInvalidate() }
    }

    override fun onDraw(c: Canvas) {
        Space.drawCached(c, width, height, 255)
    }
}
