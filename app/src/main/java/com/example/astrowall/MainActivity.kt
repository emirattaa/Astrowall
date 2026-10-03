package com.example.astrowall

import android.app.Activity
import android.content.res.ColorStateList
import android.graphics.*
import android.graphics.drawable.GradientDrawable
import android.os.Bundle
import android.view.Gravity
import android.view.View
import android.view.ViewGroup
import android.widget.*
import androidx.work.ExistingPeriodicWorkPolicy
import androidx.work.PeriodicWorkRequest
import androidx.work.WorkManager
import java.text.SimpleDateFormat
import java.util.Date
import java.util.Locale
import java.util.concurrent.TimeUnit
import kotlin.math.roundToInt

class MainActivity : Activity() {
    private lateinit var globe: GlobeView
    private var mode = "earth"
    private val accent = Color.rgb(120, 175, 255)
    private val matchP = ViewGroup.LayoutParams.MATCH_PARENT
    private val wrapP = ViewGroup.LayoutParams.WRAP_CONTENT

    private class CardUi(val key: String, val root: LinearLayout, val img: ImageView, val check: TextView)
    private val cards = ArrayList<CardUi>()

    private fun dp(v: Int) = (v * resources.displayMetrics.density + 0.5f).toInt()

    override fun onCreate(s: Bundle?) {
        super.onCreate(s)
        window.statusBarColor = Color.BLACK
        window.navigationBarColor = Color.BLACK
        val prefs = getSharedPreferences("p", 0)
        mode = prefs.getString("mode", "earth") ?: "earth"
        Thread { try { Real.refresh(this, false); Real.refresh(this, true) } catch (e: Exception) {} }.start()

        val space = SpaceView(this)
        space.alpha = 0f

        // ---- içerik ----
        val col = LinearLayout(this)
        col.orientation = LinearLayout.VERTICAL
        col.setPadding(dp(20), dp(28), dp(20), dp(32))

        col.addView(label("ASTRO WALL", 12f, Color.rgb(130, 180, 255), 0.4f))
        val title = label("Astro Duvar Kağıdı", 32f, Color.WHITE, 0f)
        title.typeface = Typeface.create("sans-serif-light", Typeface.NORMAL)
        title.setPadding(0, dp(6), 0, 0)
        col.addView(title)
        val subtitle = label("Gerçek NASA uydu görüntüleriyle duvar kağıdın her saat başı kendini yeniler.",
            14f, Color.argb(170, 255, 255, 255), 0f)
        subtitle.setPadding(0, dp(8), 0, 0)
        col.addView(subtitle)

        val mi = Astro.moonInfo(System.currentTimeMillis())
        val pill = label("${mi.emoji}  Şu an Ay: ${mi.name} · %${(mi.illum * 100).roundToInt()} aydınlık",
            13f, Color.argb(225, 255, 255, 255), 0f)
        pill.setPadding(dp(14), dp(8), dp(14), dp(8))
        pill.background = box(18, Color.argb(60, 120, 160, 255), 1, Color.argb(50, 255, 255, 255))
        col.addView(pill, LinearLayout.LayoutParams(wrapP, wrapP).apply { topMargin = dp(18) })

        // ---- seçenekler (önce oluştur; kartlar bunlara bağlı) ----
        val lock = makeSwitch("Kilit ekranına da uygula", prefs.getBoolean("lock", true))
        val info = makeSwitch("Görüntü üzerinde bilgi yazısı", prefs.getBoolean("info", true))

        fun select(key: String) {
            mode = key
            styleCards()
            val on = key != "both"
            lock.isEnabled = on
            lock.alpha = if (on) 1f else 0.4f
        }

        // ---- görünüm kartları ----
        col.addView(section("GÖRÜNÜM"), sectionLp())
        val defs = listOf(
            Triple("earth", "Dünya", "Uzaydan canlı Dünya fotoğrafı (NASA EPIC)"),
            Triple("moon", "Ay", "Bugünün Ay evresi (NASA SVS)"),
            Triple("both", "Dünya + Ay", "Ana ekran Dünya, kilit ekranı Ay"))
        for ((key, name, desc) in defs) {
            val ui = makeCard(key, name, desc)
            cards.add(ui)
            ui.root.setOnClickListener { select(key) }
            col.addView(ui.root, LinearLayout.LayoutParams(matchP, wrapP).apply { bottomMargin = dp(12) })
        }

        // ---- ayarlar ----
        col.addView(section("SEÇENEKLER"), sectionLp())
        val opts = LinearLayout(this)
        opts.orientation = LinearLayout.VERTICAL
        opts.setPadding(dp(18), dp(4), dp(18), dp(4))
        opts.background = box(22, Color.argb(165, 12, 16, 34), 1, Color.argb(45, 255, 255, 255))
        opts.addView(lock, LinearLayout.LayoutParams(matchP, dp(56)))
        opts.addView(info, LinearLayout.LayoutParams(matchP, dp(56)))
        col.addView(opts, LinearLayout.LayoutParams(matchP, wrapP))

        // ---- uygula düğmesi ----
        val btn = Button(this)
        btn.text = "Duvar kağıdına uygula"
        btn.textSize = 16f
        btn.isAllCaps = false
        btn.setTextColor(Color.WHITE)
        btn.typeface = Typeface.create("sans-serif-medium", Typeface.NORMAL)
        btn.stateListAnimator = null
        val grad = GradientDrawable(GradientDrawable.Orientation.LEFT_RIGHT,
            intArrayOf(Color.rgb(64, 128, 255), Color.rgb(128, 88, 255)))
        grad.cornerRadius = dp(30).toFloat()
        btn.background = grad
        col.addView(btn, LinearLayout.LayoutParams(matchP, dp(60)).apply { topMargin = dp(26) })

        // ---- alt bilgi ----
        val foot = label("", 12f, Color.argb(120, 255, 255, 255), 0f)
        foot.gravity = Gravity.CENTER
        foot.setPadding(0, dp(18), 0, 0)
        col.addView(foot, LinearLayout.LayoutParams(matchP, wrapP))

        fun refreshFoot() {
            val last = prefs.getLong("last", 0L)
            val fmt = SimpleDateFormat("d MMM HH:mm", Locale("tr", "TR"))
            val lastTxt = if (last > 0L) "Son uygulama: ${fmt.format(Date(last))}" else "Henüz uygulanmadı"
            foot.text = "Dünya: NASA EPIC (DSCOVR) · Ay: NASA SVS (LRO)\n$lastTxt"
        }
        refreshFoot()
        select(mode)

        btn.setOnClickListener {
            prefs.edit().putString("mode", mode)
                .putBoolean("lock", lock.isChecked).putBoolean("info", info.isChecked).apply()
            WorkManager.getInstance(this).enqueueUniquePeriodicWork(
                "wall", ExistingPeriodicWorkPolicy.UPDATE,
                PeriodicWorkRequest.Builder(WallWorker::class.java, 1, TimeUnit.HOURS).build())
            btn.isEnabled = false
            btn.text = "Uygulanıyor…"
            Thread {
                var err: String? = null
                try { Apply.now(this) } catch (e: Throwable) { err = e.message ?: e.toString() }
                val msg = err
                runOnUiThread {
                    btn.isEnabled = true
                    btn.text = "Duvar kağıdına uygula"
                    refreshFoot()
                    Toast.makeText(this,
                        if (msg == null) "Duvar kağıdı güncellendi ✓" else "Hata: $msg",
                        Toast.LENGTH_LONG).show()
                }
            }.start()
        }

        // ---- kart önizlemeleri (arka planda hazırlanır) ----
        Thread {
            try {
                val px = dp(88)
                val e = Astro.preview(this, false, px)
                val m = Astro.preview(this, true, px)
                val both = Bitmap.createBitmap(px, px, Bitmap.Config.ARGB_8888)
                val cv = Canvas(both)
                val pt = Paint(Paint.FILTER_BITMAP_FLAG)
                cv.drawBitmap(e, null, RectF(0f, 0f, px * 0.74f, px * 0.74f), pt)
                cv.drawBitmap(m, null, RectF(px * 0.36f, px * 0.36f, px * 1f, px * 1f), pt)
                runOnUiThread {
                    for (c in cards) {
                        c.img.setImageBitmap(when (c.key) { "earth" -> e; "moon" -> m; else -> both })
                    }
                }
            } catch (ex: Throwable) { }
        }.start()

        // ---- yerleşim + açılış animasyonu ----
        val scroll = ScrollView(this)
        scroll.isFillViewport = true
        scroll.isVerticalScrollBarEnabled = false
        scroll.overScrollMode = View.OVER_SCROLL_NEVER
        scroll.alpha = 0f
        scroll.addView(col)

        val frame = FrameLayout(this)
        frame.addView(space, FrameLayout.LayoutParams(matchP, matchP))
        frame.addView(scroll, FrameLayout.LayoutParams(matchP, matchP))
        globe = GlobeView(this, mode == "moon") {
            frame.removeView(globe)
            space.animate().alpha(1f).setDuration(700).start()
            scroll.animate().alpha(1f).setDuration(700).start()
        }
        frame.addView(globe)
        setContentView(frame)
    }

    // ---------- yardımcılar ----------

    private fun label(t: String, size: Float, color: Int, spacing: Float): TextView {
        val tv = TextView(this)
        tv.text = t
        tv.textSize = size
        tv.setTextColor(color)
        tv.letterSpacing = spacing
        return tv
    }

    private fun section(t: String): TextView = label(t, 12f, Color.argb(150, 255, 255, 255), 0.3f)

    private fun sectionLp() = LinearLayout.LayoutParams(wrapP, wrapP).apply {
        topMargin = dp(26); bottomMargin = dp(10); leftMargin = dp(4)
    }

    private fun box(radiusDp: Int, fill: Int, strokeDp: Int, stroke: Int): GradientDrawable {
        val g = GradientDrawable()
        g.cornerRadius = dp(radiusDp).toFloat()
        g.setColor(fill)
        g.setStroke(dp(strokeDp), stroke)
        return g
    }

    private fun makeSwitch(text: String, checked: Boolean): Switch {
        val sw = Switch(this)
        sw.text = text
        sw.textSize = 15f
        sw.setTextColor(Color.WHITE)
        sw.isChecked = checked
        val st = arrayOf(intArrayOf(android.R.attr.state_checked), intArrayOf())
        sw.thumbTintList = ColorStateList(st, intArrayOf(Color.rgb(150, 195, 255), Color.rgb(165, 170, 185)))
        sw.trackTintList = ColorStateList(st, intArrayOf(Color.argb(150, 80, 140, 255), Color.argb(90, 130, 135, 150)))
        return sw
    }

    private fun makeCard(key: String, name: String, desc: String): CardUi {
        val img = ImageView(this)
        img.scaleType = ImageView.ScaleType.FIT_CENTER
        val nameTv = label(name, 18f, Color.WHITE, 0f)
        nameTv.typeface = Typeface.create("sans-serif-medium", Typeface.NORMAL)
        val descTv = label(desc, 13f, Color.argb(165, 255, 255, 255), 0f)
        descTv.setPadding(0, dp(3), 0, 0)
        val tx = LinearLayout(this)
        tx.orientation = LinearLayout.VERTICAL
        tx.addView(nameTv)
        tx.addView(descTv)
        val check = label("✓", 20f, accent, 0f)
        val row = LinearLayout(this)
        row.orientation = LinearLayout.HORIZONTAL
        row.gravity = Gravity.CENTER_VERTICAL
        row.setPadding(dp(14), dp(14), dp(18), dp(14))
        row.addView(img, LinearLayout.LayoutParams(dp(80), dp(80)))
        row.addView(tx, LinearLayout.LayoutParams(0, wrapP, 1f).apply { leftMargin = dp(14) })
        row.addView(check)
        return CardUi(key, row, img, check)
    }

    private fun styleCards() {
        for (c in cards) {
            val sel = c.key == mode
            c.root.background = if (sel)
                box(22, Color.argb(205, 22, 42, 84), 2, accent)
            else
                box(22, Color.argb(165, 12, 16, 34), 1, Color.argb(45, 255, 255, 255))
            c.check.visibility = if (sel) View.VISIBLE else View.INVISIBLE
        }
    }
}
