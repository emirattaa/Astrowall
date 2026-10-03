package com.example.astrowall

import android.app.Activity
import android.graphics.Color
import android.os.Bundle
import android.view.Gravity
import android.widget.*
import androidx.work.ExistingPeriodicWorkPolicy
import androidx.work.PeriodicWorkRequest
import androidx.work.WorkManager
import java.util.concurrent.TimeUnit

class MainActivity : Activity() {
    private lateinit var globe: GlobeView

    override fun onCreate(s: Bundle?) {
        super.onCreate(s)
        window.statusBarColor = Color.BLACK
        window.navigationBarColor = Color.BLACK
        val prefs = getSharedPreferences("p", 0)
        val menu = LinearLayout(this).apply {
            orientation = LinearLayout.VERTICAL
            gravity = Gravity.CENTER
            setPadding(48, 48, 48, 48)
            setBackgroundColor(Color.rgb(6, 8, 18))
            alpha = 0f
        }
        menu.addView(TextView(this).apply {
            text = "Astro Duvar Kağıdı\nHer saat güncellenir"
            textSize = 20f; gravity = Gravity.CENTER; setTextColor(Color.WHITE)
        })
        val lock = CheckBox(this).apply {
            text = "Kilit ekranına da uygula"; setTextColor(Color.WHITE)
            isChecked = prefs.getBoolean("lock", true)
        }
        fun go(mode: String) {
            prefs.edit().putString("mode", mode).putBoolean("lock", lock.isChecked).apply()
            WorkManager.getInstance(this).enqueueUniquePeriodicWork(
                "wall", ExistingPeriodicWorkPolicy.UPDATE,
                PeriodicWorkRequest.Builder(WallWorker::class.java, 1, TimeUnit.HOURS).build())
            Toast.makeText(this, "Uygulanıyor...", Toast.LENGTH_SHORT).show()
            Thread {
                try { Apply.now(this) } catch (e: Exception) {
                    runOnUiThread { Toast.makeText(this, "Hata: ${e.message}", Toast.LENGTH_LONG).show() }
                }
            }.start()
        }
        listOf("Dünya" to "earth", "Ay" to "moon", "Ana ekran Dünya + Kilit Ay" to "both").forEach { (t, m) ->
            menu.addView(Button(this).apply { text = t; setOnClickListener { go(m) } })
        }
        menu.addView(lock)

        val frame = FrameLayout(this)
        frame.addView(menu)
        globe = GlobeView(this, prefs.getString("mode", "earth") == "moon") {
            frame.removeView(globe)
            menu.animate().alpha(1f).setDuration(600).start()
        }
        frame.addView(globe)
        setContentView(frame)
    }
}
