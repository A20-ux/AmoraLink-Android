package com.amoralink.app

import android.app.AlarmManager
import android.app.NotificationChannel
import android.app.NotificationManager
import android.app.PendingIntent
import android.content.BroadcastReceiver
import android.content.Context
import android.content.Intent
import android.os.Build
import android.app.usage.UsageStatsManager
import android.provider.Settings

object ReminderScheduler {
    private const val REQUEST_CODE = 8301

    fun schedule(context: Context) {
        val intent = Intent(context, ProactiveReminderReceiver::class.java)
        val pending = PendingIntent.getBroadcast(
            context, REQUEST_CODE, intent,
            PendingIntent.FLAG_UPDATE_CURRENT or PendingIntent.FLAG_IMMUTABLE
        )

        val prefs = context.getSharedPreferences("AmoraLinkSettings", Context.MODE_PRIVATE)
        val minutes = prefs.getInt("reminder_minutes", 30).coerceAtLeast(15)
        val trigger = System.currentTimeMillis() + minutes * 60_000L

        val alarm = context.getSystemService(Context.ALARM_SERVICE) as AlarmManager
        alarm.setAndAllowWhileIdle(AlarmManager.RTC_WAKEUP, trigger, pending)
    }

    fun cancel(context: Context) {
        val intent = Intent(context, ProactiveReminderReceiver::class.java)
        val pending = PendingIntent.getBroadcast(
            context, REQUEST_CODE, intent,
            PendingIntent.FLAG_UPDATE_CURRENT or PendingIntent.FLAG_IMMUTABLE
        )
        val alarm = context.getSystemService(Context.ALARM_SERVICE) as AlarmManager
        alarm.cancel(pending)
    }
}

class ProactiveReminderReceiver : BroadcastReceiver() {

    override fun onReceive(context: Context, intent: Intent?) {
        val prefs = context.getSharedPreferences("AmoraLinkSettings", Context.MODE_PRIVATE)
        if (!prefs.getBoolean("proactive_enabled", false)) return

        val selected = prefs.getStringSet(
            "selected_companion_apps", emptySet()
        ) ?: emptySet()

        val minutes = prefs.getInt("reminder_minutes", 30).coerceAtLeast(15)
        val threshold = minutes * 60_000L
        val now = System.currentTimeMillis()

        try {
            val usage = context.getSystemService(Context.USAGE_STATS_SERVICE)
                as UsageStatsManager

            val stats = usage.queryUsageStats(
                UsageStatsManager.INTERVAL_DAILY,
                now - 24L * 60 * 60 * 1000,
                now
            ) ?: emptyList()

            val latest = stats
                .filter { selected.contains(it.packageName) && it.lastTimeUsed > 0 }
                .maxByOrNull { it.lastTimeUsed }

            if (latest != null && now - latest.lastTimeUsed >= threshold) {
                showNotification(
                    context,
                    latest.packageName,
                    (now - latest.lastTimeUsed) / 60_000L
                )
            }
        } catch (_: Exception) {
        }

        ReminderScheduler.schedule(context)
    }

    private fun showNotification(context: Context, pkg: String, inactive: Long) {
        val manager = context.getSystemService(Context.NOTIFICATION_SERVICE)
            as NotificationManager

        val channelId = "amoralink_proactive"
        if (Build.VERSION.SDK_INT >= 26) {
            manager.createNotificationChannel(
                NotificationChannel(
                    channelId,
                    "AI伴侣主动提醒",
                    NotificationManager.IMPORTANCE_DEFAULT
                ).apply {
                    description = "陪伴‮用应‬长时‮未间‬使用‮的时‬温馨提醒"
                }
            )
        }

        val appName = try {
            context.packageManager.getApplicationLabel(
                context.packageManager.getApplicationInfo(pkg, 0)
            ).toString()
        } catch (_: Exception) {
            "你的 AI 伴侣"
        }

        val memory = latestMemory(context, pkg)
        val message = if (memory.isBlank()) {
            "已经‮一有‬段时‮没间‬有打开 $appName 了。想‮去回‬看看吗？"
        } else {
            "‮记还‬得吗：$memory"
        }

        val launch = context.packageManager.getLaunchIntentForPackage(pkg)
        val contentIntent = if (launch != null) {
            PendingIntent.getActivity(
                context, pkg.hashCode(), launch,
                PendingIntent.FLAG_UPDATE_CURRENT or PendingIntent.FLAG_IMMUTABLE
            )
        } else {
            null
        }

        val builder = if (Build.VERSION.SDK_INT >= 26) {
            android.app.Notification.Builder(context, channelId)
        } else {
            @Suppress("DEPRECATION")
            android.app.Notification.Builder(context)
        }

        val notification = builder
            .setSmallIcon(android.R.drawable.ic_dialog_info)
            .setContentTitle("$appName ‮念想‬时光")
            .setContentText(message)
            .setStyle(android.app.Notification.BigTextStyle().bigText(message))
            .setAutoCancel(true)
            .apply {
                if (contentIntent != null) setContentIntent(contentIntent)
            }
            .build()

        if (Build.VERSION.SDK_INT < 33 ||
            context.checkSelfPermission(android.Manifest.permission.POST_NOTIFICATIONS)
                == android.content.pm.PackageManager.PERMISSION_GRANTED
        ) {
            manager.notify(pkg.hashCode(), notification)
        }
    }

    private fun latestMemory(context: Context, pkg: String): String {
        return try {
            val db = context.openOrCreateDatabase(
                "amoralink_usage.db", Context.MODE_PRIVATE, null
            )
            val cursor = db.rawQuery(
                "SELECT title, content FROM memories WHERE package_name=? ORDER BY updated_at DESC LIMIT 1",
                arrayOf(pkg)
            )
            var result = ""
            cursor.use {
                if (it.moveToFirst()) {
                    result = it.getString(0) + "：" + it.getString(1)
                }
            }
            db.close()
            result
        } catch (_: Exception) {
            ""
        }
    }
}
