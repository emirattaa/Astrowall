package com.example.astrowall

import android.app.WallpaperManager
import android.content.Context
import androidx.work.Worker
import androidx.work.WorkerParameters

object Apply {
    fun now(ctx: Context) {
        val p = ctx.getSharedPreferences("p", 0)
        val mode = p.getString("mode", "earth")
        val lock = p.getBoolean("lock", true)
        val dm = ctx.resources.displayMetrics
        val wm = WallpaperManager.getInstance(ctx)
        fun set(moon: Boolean, flags: Int) {
            val b = Astro.render(ctx, moon, dm.widthPixels, dm.heightPixels)
            wm.setBitmap(b, null, true, flags)
            b.recycle()
        }
        when (mode) {
            "both" -> {
                set(false, WallpaperManager.FLAG_SYSTEM)
                set(true, WallpaperManager.FLAG_LOCK)
            }
            else -> set(mode == "moon",
                WallpaperManager.FLAG_SYSTEM or (if (lock) WallpaperManager.FLAG_LOCK else 0))
        }
    }
}

class WallWorker(c: Context, p: WorkerParameters) : Worker(c, p) {
    override fun doWork(): Result {
        return try { Apply.now(applicationContext); Result.success() } catch (e: Exception) { Result.retry() }
    }
}
