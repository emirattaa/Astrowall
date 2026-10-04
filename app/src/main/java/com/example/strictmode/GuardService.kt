package com.example.strictmode

import android.accessibilityservice.AccessibilityService
import android.content.Intent
import android.os.Handler
import android.os.Looper
import android.view.accessibility.AccessibilityEvent

class GuardService : AccessibilityService() {
    private val h = Handler(Looper.getMainLooper())
    private var current: String? = null

    private val tick = object : Runnable {
        override fun run() { current?.let { check(it) }; h.postDelayed(this, 20_000) }
    }

    override fun onServiceConnected() { h.post(tick) }

    override fun onAccessibilityEvent(e: AccessibilityEvent) {
        val pkg = e.packageName?.toString() ?: return
        if (pkg == packageName || pkg == "com.android.systemui") return
        current = pkg
        check(pkg)
    }

    private fun check(pkg: String) {
        if (!Prefs.blocked(this, pkg)) return
        performGlobalAction(GLOBAL_ACTION_HOME)
        startActivity(Intent(this, OathActivity::class.java)
            .putExtra("pkg", pkg).addFlags(Intent.FLAG_ACTIVITY_NEW_TASK))
    }

    override fun onInterrupt() {}
    override fun onDestroy() { h.removeCallbacksAndMessages(null); super.onDestroy() }
}
