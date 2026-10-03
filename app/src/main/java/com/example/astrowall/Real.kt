package com.example.astrowall

import android.content.Context
import android.graphics.*
import org.json.JSONArray
import java.io.File
import java.net.HttpURLConnection
import java.net.URL
import java.text.SimpleDateFormat
import java.util.Locale
import java.util.TimeZone
import kotlin.math.*

/** Gerçek NASA görüntüleri: Dünya = DSCOVR/EPIC uydu fotoğrafı, Ay = NASA SVS saatlik Ay karesi (LRO verisi). */
object Real {
    private class Meta(val url: String, val ms: Long, val lat: Double, val lon: Double)

    private fun get(url: String): ByteArray? = try {
        val c = URL(url).openConnection() as HttpURLConnection
        c.connectTimeout = 15000; c.readTimeout = 30000
        c.setRequestProperty("User-Agent", "AstroWall/1.0")
        if (c.responseCode == 200) c.inputStream.use { it.readBytes() } else null
    } catch (e: Exception) { null }

    private fun earthMeta(): Meta? = try {
        val txt = get("https://epic.gsfc.nasa.gov/api/natural")?.toString(Charsets.UTF_8)
        val arr = JSONArray(txt)
        val o = arr.getJSONObject(arr.length() - 1)
        val date = o.getString("date") // "2026-09-30 23:44:00"
        val f = SimpleDateFormat("yyyy-MM-dd HH:mm:ss", Locale.US).apply { timeZone = TimeZone.getTimeZone("UTC") }
        val ms = f.parse(date)!!.time
        val (y, m, d) = date.substring(0, 10).split("-")
        val cc = o.optJSONObject("centroid_coordinates")
        Meta("https://epic.gsfc.nasa.gov/archive/natural/$y/$m/$d/jpg/${o.getString("image")}.jpg",
            ms, cc?.optDouble("lat", 0.0) ?: 0.0, cc?.optDouble("lon", 0.0) ?: 0.0)
    } catch (e: Exception) { null }

    private fun moonUrl(): String? {
        val y = java.util.Calendar.getInstance(TimeZone.getTimeZone("UTC")).get(java.util.Calendar.YEAR)
        if (y != 2026) return null // yalnızca 2026 karelerinin adresi doğrulandı
        val n = (((System.currentTimeMillis() - 1767225600000L) / 3600000L).toInt() + 1).coerceIn(1, 8760)
        return "https://svs.gsfc.nasa.gov/vis/a000000/a005500/a005587/frames/730x730_1x1_30p/moon.%04d.jpg".format(n)
    }

    fun refresh(ctx: Context, moon: Boolean) {
        val f = File(ctx.cacheDir, if (moon) "moon_real.jpg" else "earth_real.jpg")
        if (moon) {
            val b = moonUrl()?.let { get(it) }
            if (b != null && b.size > 5000) f.writeBytes(b)
        } else {
            val m = earthMeta() ?: return
            val b = get(m.url)
            if (b != null && b.size > 20000) {
                f.writeBytes(b)
                ctx.getSharedPreferences("meta", 0).edit()
                    .putLong("epic_ms", m.ms).putFloat("epic_lat", m.lat.toFloat()).putFloat("epic_lon", m.lon.toFloat()).apply()
            }
        }
    }

    fun cached(ctx: Context, moon: Boolean): Bitmap? {
        val f = File(ctx.cacheDir, if (moon) "moon_real.jpg" else "earth_real.jpg")
        if (!f.exists()) return null
        return BitmapFactory.decodeFile(f.path, BitmapFactory.Options().apply { inSampleSize = if (moon) 1 else 2 })
    }

    fun fetch(ctx: Context, moon: Boolean): Bitmap? { refresh(ctx, moon); return cached(ctx, moon) }

    private fun pack(a: Float, r: Float, g: Float, b: Float) =
        ((a * 255f).toInt().coerceIn(0, 255) shl 24) or (r.toInt().coerceIn(0, 255) shl 16) or
                (g.toInt().coerceIn(0, 255) shl 8) or b.toInt().coerceIn(0, 255)

    /** EPIC tam disk fotoğrafı -> s*s şeffaf kare (disk + atmosfer parıltısı). */
    fun earthLayer(b: Bitmap, s: Int): IntArray {
        val w = b.width; val h = b.height
        val px = IntArray(w * h); b.getPixels(px, 0, w, 0, 0, w, h)
        var x0 = w; var x1 = 0; var y0 = h; var y1 = 0
        var y = 0
        while (y < h) {
            var x = 0
            while (x < w) {
                val c = px[y * w + x]
                if (max(c shr 16 and 255, max(c shr 8 and 255, c and 255)) > 28) {
                    x0 = min(x0, x); x1 = max(x1, x); y0 = min(y0, y); y1 = max(y1, y)
                }
                x += 3
            }
            y += 3
        }
        if (x1 <= x0 || y1 <= y0) { x0 = 0; x1 = w - 1; y0 = 0; y1 = h - 1 }
        val cx = (x0 + x1) / 2f; val cy = (y0 + y1) / 2f
        val rad = max(x1 - x0, y1 - y0) / 2f * 0.985f
        val tex = Tex(px, w, h)
        val lut = IntArray(256) { (255.0 * (it / 255.0).pow(0.85)).toInt() }
        val out = IntArray(s * s)
        val c0 = s / 2f; val r = c0 / 1.2f
        for (yy in 0 until s) for (xx in 0 until s) {
            var nx = (xx + 0.5f - c0) / r
            var ny = (c0 - yy - 0.5f) / r
            val d = sqrt(nx * nx + ny * ny)
            if (d >= 1.2f) continue
            val cov = ((1f - d) * r + 0.5f).coerceIn(0f, 1f)
            if (d > 1f) { nx /= d; ny /= d }
            var ha = 0f
            if (d > 1f) { val k = (1.2f - d) / 0.2f; ha = k * k * k * 0.45f }
            var cr = 0f; var cg = 0f; var cb = 0f
            if (cov > 0f) {
                val col = Astro.sample(tex, ((cx + nx * rad) / w).toDouble(), ((cy - ny * rad) / h).toDouble())
                cr = lut[col shr 16 and 255].toFloat(); cg = lut[col shr 8 and 255].toFloat(); cb = lut[col and 255].toFloat()
                val rim = min(d, 1f).pow(6) * 0.3f
                cr = cr * (1 - rim) + 120f * rim; cg = cg * (1 - rim) + 175f * rim; cb = cb * (1 - rim) + 255f * rim
            }
            val a = cov + ha * (1 - cov)
            if (a <= 0.003f) continue
            val kd = cov / a; val kh = ha * (1 - cov) / a
            out[yy * s + xx] = pack(a, cr * kd + 110f * kh, cg * kd + 170f * kh, cb * kd + 255f * kh)
        }
        return out
    }

    /** NASA Ay karesi (siyah arka plan) -> s*s kare, parlaklık şeffaflığa çevrilir. */
    fun moonLayer(b: Bitmap, s: Int): IntArray {
        val size = (s / 2.4f * 2f * 1.04f)
        val tmp = Bitmap.createBitmap(s, s, Bitmap.Config.ARGB_8888)
        Canvas(tmp).drawBitmap(b, null,
            RectF(s / 2f - size / 2, s / 2f - size / 2, s / 2f + size / 2, s / 2f + size / 2),
            Paint(Paint.FILTER_BITMAP_FLAG))
        val a = IntArray(s * s); tmp.getPixels(a, 0, s, 0, 0, s, s); tmp.recycle()
        for (i in a.indices) {
            val c = a[i]
            val rr = c shr 16 and 255; val gg = c shr 8 and 255; val bb = c and 255
            val l = max(rr, max(gg, bb))
            a[i] = if (l == 0) 0 else (l shl 24) or ((rr * 255 / l) shl 16) or ((gg * 255 / l) shl 8) or (bb * 255 / l)
        }
        return a
    }
}
