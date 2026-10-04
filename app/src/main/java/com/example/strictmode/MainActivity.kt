package com.example.strictmode

import android.content.Intent
import android.os.Bundle
import android.provider.Settings
import android.text.InputType
import android.widget.*
import androidx.appcompat.app.AlertDialog
import androidx.appcompat.app.AppCompatActivity

class MainActivity : AppCompatActivity() {
    private lateinit var list: ListView

    override fun onCreate(b: Bundle?) {
        super.onCreate(b)
        fun btn(t: String, a: String) = Button(this).apply {
            text = t; setOnClickListener { startActivity(Intent(a)) }
        }
        list = ListView(this)
        setContentView(LinearLayout(this).apply {
            orientation = LinearLayout.VERTICAL
            addView(btn("1) Kullanım erişimi izni", Settings.ACTION_USAGE_ACCESS_SETTINGS))
            addView(btn("2) Erişilebilirlik → Katı Mod'u aç", Settings.ACTION_ACCESSIBILITY_SETTINGS))
            addView(btn("3) Bildirim erişimi → Katı Mod'u aç", Settings.ACTION_NOTIFICATION_LISTENER_SETTINGS))
            addView(list, LinearLayout.LayoutParams(-1, 0, 1f))
        })
    }

    override fun onResume() { super.onResume(); refresh() }

    private fun refresh() {
        val launch = Intent(Intent.ACTION_MAIN).addCategory(Intent.CATEGORY_LAUNCHER)
        val apps = packageManager.queryIntentActivities(launch, 0)
            .map { it.activityInfo.packageName to it.loadLabel(packageManager).toString() }
            .filter { it.first != packageName }.sortedBy { it.second.lowercase() }
        val limits = Prefs.limits(this)
        list.adapter = ArrayAdapter(this, android.R.layout.simple_list_item_1, apps.map { (p, n) ->
            n + (limits[p]?.let { "  —  günde $it dk" } ?: "")
        })
        list.setOnItemClickListener { _, _, i, _ ->
            val (pkg, name) = apps[i]
            val et = EditText(this).apply {
                inputType = InputType.TYPE_CLASS_NUMBER; hint = "Günlük dakika (0 = sınırı kaldır)"
            }
            AlertDialog.Builder(this).setTitle(name).setView(et)
                .setPositiveButton("Kaydet") { _, _ ->
                    Prefs.setLimit(this, pkg, et.text.toString().toIntOrNull() ?: 0); refresh()
                }.setNegativeButton("İptal", null).show()
        }
    }
}
