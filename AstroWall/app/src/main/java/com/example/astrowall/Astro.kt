package com.example.astrowall

import android.content.Context
import android.graphics.Bitmap
import android.graphics.BitmapFactory
import android.graphics.Color
import java.util.Random
import java.util.TimeZone
import kotlin.math.*

class Tex(val px: IntArray, val w: Int, val h: Int)

object Astro {
    private fun jd(ms: Long) = ms / 86400000.0 + 2440587.5

    /** returns (declination, subsolar longitude) in radians */
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

    private fun loadTex(ctx: Context, name: String, maxW: Int): Tex? = try {
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

    private fun stars(px: IntArray, w: Int, h: Int) {
        val r = Random(42)
        repeat(w * h / 3500) {
            val v = 90 + r.nextInt(165)
            px[r.nextInt(w * h)] = Color.rgb(v, v, v)
        }
    }

    private fun scale(c: Int, f: Float) = Color.rgb(
        (Color.red(c) * f).toInt().coerceIn(0, 255),
        (Color.green(c) * f).toInt().coerceIn(0, 255),
        (Color.blue(c) * f).toInt().coerceIn(0, 255)
    )

    fun render(ctx: Context, moon: Boolean, w: Int, h: Int): Bitmap {
        val ms = System.currentTimeMillis()
        val px = IntArray(w * h) { Color.BLACK }
        stars(px, w, h)
        val r = min(w, h) * 0.42f
        val cx = w / 2f
        val cy = h * 0.42f
        if (moon) drawMoon(ctx, px, w, h, cx, cy, r, ms) else drawEarth(ctx, px, w, h, cx, cy, r, ms)
        val bmp = Bitmap.createBitmap(w, h, Bitmap.Config.ARGB_8888)
        bmp.setPixels(px, 0, w, 0, 0, w, h)
        return bmp
    }

    private fun drawEarth(ctx: Context, px: IntArray, w: Int, h: Int, cx: Float, cy: Float, r: Float, ms: Long) {
        val tex = loadTex(ctx, "earth.jpg", 2048)
        val (decl, sunLon) = sun(ms)
        val lat0 = Math.toRadians(25.0)
        val lon0 = Math.toRadians(TimeZone.getDefault().rawOffset / 3600000.0 * 15.0)
        val sl0 = sin(lat0); val cl0 = cos(lat0)
        val sd = sin(decl); val cd = cos(decl)
        for (y in max(0, (cy - r).toInt())..min(h - 1, (cy + r).toInt())) {
            for (x in max(0, (cx - r).toInt())..min(w - 1, (cx + r).toInt())) {
                val nx = (x - cx) / r; val ny = (cy - y) / r
                val d2 = nx * nx + ny * ny
                if (d2 > 1f) continue
                val nz = sqrt(1f - d2)
                val lat = asin((nz * sl0 + ny * cl0).toDouble().coerceIn(-1.0, 1.0))
                val lon = lon0 + atan2(nx.toDouble(), nz * cl0 - ny * sl0)
                var c: Int
                if (tex != null) {
                    var u = (lon / (2 * PI) + 0.5) % 1.0; if (u < 0) u += 1.0
                    val v = (0.5 - lat / PI).coerceIn(0.0, 0.9999)
                    c = tex.px[(v * tex.h).toInt() * tex.w + (u * tex.w).toInt().coerceIn(0, tex.w - 1)]
                } else {
                    val n = sin(lon * 3) * cos(lat * 4) + sin(lon * 7 + lat * 5) * 0.5
                    c = if (n > 0.7) Color.rgb(60, 120, 60) else Color.rgb(20, 60, 130)
                }
                val cosPhi = sin(lat) * sd + cos(lat) * cd * cos(lon - sunLon)
                val t = ((cosPhi + 0.08) / 0.16).coerceIn(0.0, 1.0).toFloat()
                c = scale(c, 0.12f + 0.88f * t)
                val rim = d2.pow(8) * 0.45f
                px[y * w + x] = Color.rgb(
                    (Color.red(c) * (1 - rim) + 90 * rim * t).toInt(),
                    (Color.green(c) * (1 - rim) + 150 * rim * t).toInt(),
                    (Color.blue(c) * (1 - rim) + 255 * rim * t).toInt()
                )
            }
        }
    }

    private fun drawMoon(ctx: Context, px: IntArray, w: Int, h: Int, cx: Float, cy: Float, r: Float, ms: Long) {
        val tex = loadTex(ctx, "moon.jpg", 2048)
        val age = (jd(ms) - 2451550.1).mod(29.530588853)
        val ph = age / 29.530588853 * 2 * PI // 0 = new, PI = full
        val lx = sin(ph).toFloat(); val lz = (-cos(ph)).toFloat()
        for (y in max(0, (cy - r).toInt())..min(h - 1, (cy + r).toInt())) {
            for (x in max(0, (cx - r).toInt())..min(w - 1, (cx + r).toInt())) {
                val nx = (x - cx) / r; val ny = (cy - y) / r
                val d2 = nx * nx + ny * ny
                if (d2 > 1f) continue
                val nz = sqrt(1f - d2)
                val base: Int
                if (tex != null) {
                    val lon = atan2(nx.toDouble(), nz.toDouble())
                    val lat = asin(ny.toDouble())
                    val u = (lon / (2 * PI) + 0.5).coerceIn(0.0, 0.9999)
                    val v = (0.5 - lat / PI).coerceIn(0.0, 0.9999)
                    base = tex.px[(v * tex.h).toInt() * tex.w + (u * tex.w).toInt()]
                } else {
                    val g = (150 + 25 * sin(nx * 13) * sin(ny * 11 + 2) + 15 * sin(nx * 31 + ny * 17)).toInt()
                    base = Color.rgb(g, g, g)
                }
                val lit = max(0f, nx * lx + nz * lz)
                px[y * w + x] = scale(base, 0.03f + 0.97f * lit)
            }
        }
    }
}
