package com.example.astrowall

import android.app.Activity
import android.os.Bundle
import android.view.Gravity
import android.widget.*
import androidx.work.ExistingPeriodicWorkPolicy
import androidx.work.PeriodicWorkRequest
import androidx.work.WorkManager
import java.util.concurrent.TimeUnit

class MainActivity : Activity() {
    override fun onCreate(s: Bundle?) {
        super.onCreate(s)
        val prefs = getSharedPreferences("p", 0)
        val root = LinearLayout(this).apply {
            orientation = LinearLayout.VERTICAL
            gravity = Gravity.CENTER
            setPadding(48, 48, 48, 48)
        }
        root.addView(TextView(this).apply {
            text = "Astro Duvar Kağıdı\nHer saat güncellenir"
            textSize = 20f; gravity = Gravity.CENTER
        })
        val lock = CheckBox(this).apply {
            text = "Kilit ekranına da uygula"; isChecked = prefs.getBoolean("lock", true)
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
            root.addView(Button(this).apply { text = t; setOnClickListener { go(m) } })
        }
        root.addView(lock)
        setContentView(root)
    }
}
