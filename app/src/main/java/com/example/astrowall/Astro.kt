package com.example.astrowall

import android.content.Context
import android.graphics.*
import java.util.Random
import java.util.TimeZone
import kotlin.math.*

class Tex(val px: IntArray, val w: Int, val h: Int)

object Astro {
    const val DAY = 86400000.0
    private fun jd(ms: Long) = ms / DAY + 2440587.5

    /** (declination, subsolar longitude) in radians */
    private fun sun(ms: Long): Pair<Double, Double> {
        val n = jd(ms) - 2451545.0
        val l = Math.toRadians((280.460 + 0.9856474 * n) % 360)
        val g = Math.toRadians((357.528 + 0.9856003 * n) % 360)
        val lam = l + Math.toRadians(1.915) * sin(g) + Math.toRadians(0.020) * sin(2 * g)
        val eps = Math.toRadians(23.439 - 0.0000004 * n)
        val decl = asin(sin(eps) * sin(lam))
        val ra = atan2(cos(eps) * sin(lam), cos(lam))
        val gmst = Math.toRadians((280.46061837 + 360.98564736629 * n) % 360)
        val lon = ra - gmst
        return decl to atan2(sin(lon), cos(lon))
    }

    fun loadTex(ctx: Context, name: String, maxW: Int): Tex? = try {
        val o = BitmapFactory.Options().apply { inJustDecodeBounds = true }
        ctx.assets.open(name).use { BitmapFactory.decodeStream(it, null, o) }
        var s = 1
        while (o.outWidth / s > maxW) s *= 2
        val o2 = BitmapFactory.Options().apply { inSampleSize = s }
        val b = ctx.assets.open(name).use { BitmapFactory.decodeStream(it, null, o2) }!!
        val a = IntArray(b.width * b.height)
        b.getPixels(a, 0, b.width, 0, 0, b.width, b.height)
        val t = Tex(a, b.width, b.height)
        b.recycle()
        t
    } catch (e: Exception) { null }

    private fun lerpC(a: Int, b: Int, f: Float): Int {
        val r = ((a shr 16 and 255) * (1 - f) + (b shr 16 and 255) * f).toInt()
        val g = ((a shr 8 and 255) * (1 - f) + (b shr 8 and 255) * f).toInt()
        val bl = ((a and 255) * (1 - f) + (b and 255) * f).toInt()
        return (r shl 16) or (g shl 8) or bl
    }

    /** bilinear texture sampling, u wraps horizontally */
    fun sample(t: Tex, u: Double, v: Double): Int {
        val fx = u * t.w - 0.5
        val fy = (v * t.h - 0.5).coerceIn(0.0, t.h - 1.0)
        val x0 = floor(fx).toInt()
        val xf = (fx - x0).toFloat()
        val xa = Math.floorMod(x0, t.w)
        val xb = (xa + 1) % t.w
        val y0 = min(fy.toInt(), t.h - 1)
        val y1 = min(y0 + 1, t.h - 1)
        val yf = (fy - y0).toFloat()
        val top = lerpC(t.px[y0 * t.w + xa], t.px[y0 * t.w + xb], xf)
        val bot = lerpC(t.px[y1 * t.w + xa], t.px[y1 * t.w + xb], xf)
        return lerpC(top, bot, yf)
    }

    private fun smooth(x: Float): Float { val t = x.coerceIn(0f, 1f); return t * t * (3 - 2 * t) }

    fun stars(cv: Canvas, w: Int, h: Int, alpha: Int = 255) {
        val rnd = Random(42)
        val p = Paint(Paint.ANTI_ALIAS_FLAG)
        repeat(w * h / 5000) {
            val x = rnd.nextFloat() * w
            val y = rnd.nextFloat() * h
            val b = 70 + rnd.nextInt(185)
            p.color = Color.argb(alpha * b / 255, 255, 255, 255)
            cv.drawCircle(x, y, 0.5f + rnd.nextFloat(), p)
        }
    }

    /** Full wallpaper frame: gerçek NASA görüntüsü (indirilebilirse), yoksa hesaplanan çizim. */
    fun render(ctx: Context, moon: Boolean, w: Int, h: Int): Bitmap {
        val now = System.currentTimeMillis()
        val bmp = Bitmap.createBitmap(w, h, Bitmap.Config.ARGB_8888)
        val cv = Canvas(bmp)
        cv.drawColor(Color.BLACK)
        stars(cv, w, h)
        val r = min(w, h) * 0.42f
        val s = (r * 2f * 1.2f).toInt()
        val real = try { Real.fetch(ctx, moon) } catch (e: Exception) { null }
        val out: IntArray
        if (real != null) {
            out = if (moon) Real.moonLayer(real, s) else Real.earthLayer(real, s)
            real.recycle()
        } else {
            out = IntArray(s * s)
            disc(loadTex(ctx, if (moon) "moon.jpg" else "earth.jpg", 2048), moon, s, now, now, out)
        }
        val d = Bitmap.createBitmap(out, s, s, Bitmap.Config.ARGB_8888)
        cv.drawBitmap(d, w / 2f - s / 2f, h * 0.42f - s / 2f, Paint(Paint.FILTER_BITMAP_FLAG))
        d.recycle()
        return bmp
    }

    /** Renders a transparent square s*s containing the globe (disc radius = s/2.4) plus atmosphere halo. */
    fun disc(tex: Tex?, moon: Boolean, s: Int, ms: Long, nowMs: Long, out: IntArray,
             latDeg: Double = 23.0, lonDeg: Double = TimeZone.getDefault().rawOffset / 3600000.0 * 15.0) {
        val c = s / 2f
        val r = c / 1.2f
        if (moon) discMoon(tex, s, c, r, ms, out) else discEarth(tex, s, c, r, ms, nowMs, out, latDeg, lonDeg)
    }

    private fun discEarth(tex: Tex?, s: Int, c: Float, r: Float, ms: Long, nowMs: Long, out: IntArray, latDeg: Double, lonDeg: Double) {
        val (decl, sunLon) = sun(ms)
        val lat0 = Math.toRadians(latDeg)
        val lon0 = Math.toRadians(lonDeg) +
                (nowMs - ms) / DAY * 2 * PI // dünya döndükçe görünüm kayar
        val sl0 = sin(lat0); val cl0 = cos(lat0); val so = sin(lon0); val co = cos(lon0)
        val ex = (-so).toFloat(); val ey = co.toFloat()
        val ux = (-sl0 * co).toFloat(); val uy = (-sl0 * so).toFloat(); val uz = cl0.toFloat()
        val vx = (cl0 * co).toFloat(); val vy = (cl0 * so).toFloat(); val vz = sl0.toFloat()
        val cd = cos(decl)
        val s0x = cd * cos(sunLon); val s0y = cd * sin(sunLon); val s0z = sin(decl)
        val sx = (s0x * ex + s0y * ey).toFloat()
        val sy = (s0x * ux + s0y * uy + s0z * uz).toFloat()
        val sz = (s0x * vx + s0y * vy + s0z * vz).toFloat()
        val twoPi = (2 * PI).toFloat()
        for (y in 0 until s) {
            for (x in 0 until s) {
                var nx = (x + 0.5f - c) / r
                var ny = (c - y - 0.5f) / r
                val d = sqrt(nx * nx + ny * ny)
                if (d >= 1.2f) { out[y * s + x] = 0; continue }
                val cov = ((1f - d) * r + 0.5f).coerceIn(0f, 1f)
                var nz = 0f
                if (d <= 1f) nz = sqrt(1f - d * d) else { nx /= d; ny /= d }
                var ha = 0f
                if (d > 1f) {
                    val lh = (0.5f + 0.5f * (nx * sx + ny * sy)).coerceIn(0f, 1f)
                    val k = (1.2f - d) / 0.2f
                    ha = k * k * k * 0.6f * (0.12f + 0.88f * lh)
                }
                var cr = 0f; var cg = 0f; var cb = 0f
                if (cov > 0f) {
                    val px = nx * ex + ny * ux + nz * vx
                    val py = nx * ey + ny * uy + nz * vy
                    val pz = ny * uz + nz * vz
                    val lat = asin(pz.coerceIn(-1f, 1f))
                    val lon = atan2(py, px)
                    val col: Int = if (tex != null) {
                        sample(tex, (lon / twoPi + 0.5f).toDouble(), (0.5f - lat / PI.toFloat()).toDouble())
                    } else {
                        val n = sin(lon * 3) * cos(lat * 4) + sin(lon * 7 + lat * 5) * 0.5f
                        if (n > 0.7f) Color.rgb(60, 120, 60) else Color.rgb(20, 60, 130)
                    }
                    cr = (col shr 16 and 255).toFloat(); cg = (col shr 8 and 255).toFloat(); cb = (col and 255).toFloat()
                    val cosPhi = nx * sx + ny * sy + nz * sz
                    val t = smooth((cosPhi + 0.05f) / 0.15f)
                    val f = 0.06f + 0.94f * t
                    cr *= f; cg *= f; cb *= f
                    val q = cosPhi / 0.1f
                    val tw = exp(-q * q) * 0.6f
                    cr += 70f * tw; cg += 25f * tw
                    val rim = d.pow(6) * 0.6f * (0.2f + 0.8f * t)
                    cr = cr * (1 - rim) + 120f * rim
                    cg = cg * (1 - rim) + 175f * rim
                    cb = cb * (1 - rim) + 255f * rim
                }
                val a = cov + ha * (1 - cov)
                if (a <= 0.003f) { out[y * s + x] = 0; continue }
                val kd = cov / a; val kh = ha * (1 - cov) / a
                val fr = (cr * kd + 110f * kh).toInt().coerceIn(0, 255)
                val fg = (cg * kd + 170f * kh).toInt().coerceIn(0, 255)
                val fb = (cb * kd + 255f * kh).toInt().coerceIn(0, 255)
                out[y * s + x] = ((a * 255f).toInt().coerceIn(0, 255) shl 24) or (fr shl 16) or (fg shl 8) or fb
            }
        }
    }

    private fun discMoon(tex: Tex?, s: Int, c: Float, r: Float, ms: Long, out: IntArray) {
        val age = (jd(ms) - 2451550.1).mod(29.530588853)
        val ph = age / 29.530588853 * 2 * PI // 0 = yeni ay, PI = dolunay
        val lx = sin(ph).toFloat(); val lz = (-cos(ph)).toFloat()
        val twoPi = (2 * PI).toFloat()
        for (y in 0 until s) {
            for (x in 0 until s) {
                var nx = (x + 0.5f - c) / r
                var ny = (c - y - 0.5f) / r
                val d = sqrt(nx * nx + ny * ny)
                val cov = ((1f - d) * r + 0.5f).coerceIn(0f, 1f)
                if (cov <= 0f) { out[y * s + x] = 0; continue }
                var nz = 0f
                if (d <= 1f) nz = sqrt(1f - d * d) else { nx /= d; ny /= d }
                val lon = atan2(nx, nz)
                val lat = asin(ny.coerceIn(-1f, 1f))
                val col: Int = if (tex != null) {
                    sample(tex, (lon / twoPi + 0.5f).toDouble(), (0.5f - lat / PI.toFloat()).toDouble())
                } else {
                    val g = (150 + 25 * sin(nx * 13) * sin(ny * 11 + 2) + 15 * sin(nx * 31 + ny * 17)).toInt()
                    Color.rgb(g, g, g)
                }
                val lit = nx * lx + nz * lz
                val t = smooth((lit + 0.04f) / 0.1f)
                val f = 0.035f + 0.965f * t * (0.55f + 0.45f * lit.coerceIn(0f, 1f))
                val fr = ((col shr 16 and 255) * f * 1.1f).toInt().coerceIn(0, 255)
                val fg = ((col shr 8 and 255) * f * 1.1f).toInt().coerceIn(0, 255)
                val fb = ((col and 255) * f * 1.1f).toInt().coerceIn(0, 255)
                out[y * s + x] = ((cov * 255f).toInt() shl 24) or (fr shl 16) or (fg shl 8) or fb
            }
        }
    }
}
