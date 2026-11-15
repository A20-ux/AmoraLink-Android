package com.amoralink.app

import android.app.Service
import android.app.usage.UsageStatsManager
import android.content.Context
import android.content.Intent
import android.graphics.Color
import android.graphics.PixelFormat
import android.graphics.drawable.GradientDrawable
import android.os.Handler
import android.os.IBinder
import android.os.Looper
import android.provider.Settings
import android.view.Gravity
import android.view.WindowManager
import android.widget.TextView

class ReminderOverlayService : Service() {

    private val handler = Handler(Looper.getMainLooper())
    private var windowManager: WindowManager? = null
    private var bubble: TextView? = null
    private var targetPackage: String? = null

    private val checker = object : Runnable {
        override fun run() {
            checkInactiveApps()
            handler.postDelayed(this, 60_000)
        }
    }

    override fun onCreate() {
        super.onCreate()
        windowManager = getSystemService(WINDOW_SERVICE) as WindowManager
        handler.post(checker)
    }

    override fun onStartCommand(intent: Intent?, flags: Int, startId: Int): Int {
        if (!Settings.canDrawOverlays(this)) {
            stopSelf()
            return START_NOT_STICKY
        }
        checkInactiveApps()
        return START_STICKY
    }

    private fun checkInactiveApps() {
        if (!Settings.canDrawOverlays(this)) return

        val prefs = getSharedPreferences("AmoraLinkSettings", MODE_PRIVATE)
        val selected = prefs.getStringSet(
            "selected_companion_apps",
            emptySet()
        ) ?: emptySet()

        if (selected.isEmpty()) {
            removeBubble()
            return
        }

        val minutes = prefs.getInt("reminder_minutes", 30)
        val threshold = minutes * 60_000L

        try {
            val manager = getSystemService(Context.USAGE_STATS_SERVICE)
                as UsageStatsManager

            val now = System.currentTimeMillis()
            val stats = manager.queryUsageStats(
                UsageStatsManager.INTERVAL_DAILY,
                now - 24L * 60 * 60 * 1000,
                now
            ) ?: emptyList()

            val latest = stats
                .filter { selected.contains(it.packageName) }
                .maxByOrNull { it.lastTimeUsed }

            if (latest == null || latest.lastTimeUsed <= 0) {
                removeBubble()
                return
            }

            val inactive = now - latest.lastTimeUsed
            if (inactive >= threshold) {
                targetPackage = latest.packageName
                showBubble(
                    latest.packageName,
                    inactive / 60_000L
                )
            } else {
                removeBubble()
            }
        } catch (_: Exception) {
            removeBubble()
        }
    }

    private fun showBubble(pkg: String, inactiveMinutes: Long) {
        if (bubble != null) {
            bubble?.text = "♡ ${appName(pkg)} 已有 ${inactiveMinutes} 分钟‮使未‬用 · 点我返回"
            return
        }

        val view = TextView(this).apply {
            text = "♡ ${appName(pkg)} 已有 ${inactiveMinutes} 分钟‮使未‬用 · 点我返回"
            textSize = 13f
            setTextColor(Color.WHITE)
            setPadding(24, 16, 24, 16)
            background = GradientDrawable().apply {
                setColor(Color.rgb(90, 45, 80))
                cornerRadius = 48f
            }
            elevation = 12f
            setOnClickListener {
                val target = targetPackage ?: return@setOnClickListener
                val launch = packageManager.getLaunchIntentForPackage(target)
                if (launch != null) {
                    launch.addFlags(Intent.FLAG_ACTIVITY_NEW_TASK)
                    startActivity(launch)
                }
                removeBubble()
            }
            setOnLongClickListener {
                removeBubble()
                true
            }
        }

        val params = WindowManager.LayoutParams(
            WindowManager.LayoutParams.WRAP_CONTENT,
            WindowManager.LayoutParams.WRAP_CONTENT,
            WindowManager.LayoutParams.TYPE_APPLICATION_OVERLAY,
            WindowManager.LayoutParams.FLAG_NOT_FOCUSABLE or
                WindowManager.LayoutParams.FLAG_LAYOUT_IN_SCREEN,
            PixelFormat.TRANSLUCENT
        ).apply {
            gravity = Gravity.BOTTOM or Gravity.CENTER_HORIZONTAL
            y = 100
        }

        try {
            windowManager?.addView(view, params)
            bubble = view
        } catch (_: Exception) {
            bubble = null
        }
    }

    private fun removeBubble() {
        val view = bubble ?: return
        try {
            windowManager?.removeView(view)
        } catch (_: Exception) {
        }
        bubble = null
    }

    private fun appName(pkg: String): String {
        return try {
            packageManager.getApplicationLabel(
                packageManager.getApplicationInfo(pkg, 0)
            ).toString()
        } catch (_: Exception) {
            pkg
        }
    }

    override fun onDestroy() {
        handler.removeCallbacks(checker)
        removeBubble()
        super.onDestroy()
    }

    override fun onBind(intent: Intent?): IBinder? = null
}
