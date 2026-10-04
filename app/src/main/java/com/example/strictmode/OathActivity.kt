package com.example.strictmode

import android.os.Bundle
import android.text.Editable
import android.text.TextWatcher
import android.view.Gravity
import android.widget.*
import androidx.appcompat.app.AppCompatActivity

class OathActivity : AppCompatActivity() {
    override fun onCreate(b: Bundle?) {
        super.onCreate(b)
        val pkg = intent.getStringExtra("pkg") ?: return finish()
        val pad = (24 * resources.displayMetrics.density).toInt()

        val info = TextView(this).apply {
            textSize = 18f
            text = "Bu uygulama için bugünkü süren doldu.\n\n" +
                "Yine de devam etmek istiyorsan aşağıdaki cümleyi aynen yaz:\n\n“${Prefs.OATH}”"
        }
        val input = EditText(this).apply { hint = "Yemini buraya yaz" }
        val go = Button(this).apply { text = "Yemin ediyorum, ${Prefs.BYPASS_MIN} dk ver"; isEnabled = false }
        val quit = Button(this).apply { text = "Vazgeç" }

        input.addTextChangedListener(object : TextWatcher {
            override fun afterTextChanged(s: Editable?) {
                go.isEnabled = s.toString().trim().equals(Prefs.OATH, ignoreCase = true)
            }
            override fun beforeTextChanged(s: CharSequence?, a: Int, b: Int, c: Int) {}
            override fun onTextChanged(s: CharSequence?, a: Int, b: Int, c: Int) {}
        })
        go.setOnClickListener {
            Prefs.grantBypass(this, pkg)
            packageManager.getLaunchIntentForPackage(pkg)?.let { startActivity(it) }
            finish()
        }
        quit.setOnClickListener { finish() }

        setContentView(LinearLayout(this).apply {
            orientation = LinearLayout.VERTICAL; gravity = Gravity.CENTER; setPadding(pad, pad, pad, pad)
            addView(info); addView(input); addView(go); addView(quit)
        })
    }
}
