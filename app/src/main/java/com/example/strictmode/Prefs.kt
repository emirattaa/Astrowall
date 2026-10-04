package com.example.strictmode

import android.app.usage.UsageStatsManager
import android.content.Context
import java.util.Calendar

object Prefs {
    const val OATH = "Önemli bir nedenim var, yemin ederim."
    const val BYPASS_MIN = 10

    private fun p(c: Context) = c.getSharedPreferences("strict", 0)

    fun limits(c: Context): Map<String, Int> =
        p(c).all.filter { it.key.startsWith("lim:") && it.value is Int }
            .map { it.key.removePrefix("lim:") to it.value as Int }.toMap()

    fun setLimit(c: Context, pkg: String, minutes: Int) {
        val e = p(c).edit()
        if (minutes <= 0) e.remove("lim:$pkg") else e.putInt("lim:$pkg", minutes)
        e.apply()
    }

    fun grantBypass(c: Context, pkg: String) =
        p(c).edit().putLong("by:$pkg", System.currentTimeMillis() + BYPASS_MIN * 60_000L).apply()

    private fun usedMs(c: Context, pkg: String): Long {
        val start = Calendar.getInstance().apply {
            set(Calendar.HOUR_OF_DAY, 0); set(Calendar.MINUTE, 0); set(Calendar.SECOND, 0)
        }.timeInMillis
        val usm = c.getSystemService(Context.USAGE_STATS_SERVICE) as UsageStatsManager
        return usm.queryAndAggregateUsageStats(start, System.currentTimeMillis())[pkg]
            ?.totalTimeInForeground ?: 0L
    }

    fun overLimit(c: Context, pkg: String): Boolean {
        val lim = limits(c)[pkg] ?: return false
        return usedMs(c, pkg) >= lim * 60_000L
    }

    /** Süre doldu ve yemin sonrası geçici izin de yok. */
    fun blocked(c: Context, pkg: String) =
        overLimit(c, pkg) && System.currentTimeMillis() > p(c).getLong("by:$pkg", 0L)
}
