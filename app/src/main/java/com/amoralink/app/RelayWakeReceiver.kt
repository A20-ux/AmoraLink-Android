package com.amoralink.app

import android.app.AlarmManager
import android.app.PendingIntent
import android.content.BroadcastReceiver
import android.content.Context
import android.content.Intent

/**
 * 中继进程被回收后的闹钟唤醒兜底。
 *
 * [RelayWakeScheduler] 每 60 秒排一次允许在 Doze 下触发的闹钟；
 * [RelayWakeReceiver] 触发时若仍已注册，就重新拉起 [RelayPollService]，再排下一次。
 * 存活期间的 15/30 秒轮询由服务内 Handler 负责，这里只负责把死掉的进程叫回来。
 */
object RelayWakeScheduler {
    private const val REQUEST_CODE = 8302
    private const val WAKE_INTERVAL_MS = 60_000L

    fun schedule(context: Context) {
        val intent = Intent(context, RelayWakeReceiver::class.java)
        val pending = PendingIntent.getBroadcast(
            context, REQUEST_CODE, intent,
            PendingIntent.FLAG_UPDATE_CURRENT or PendingIntent.FLAG_IMMUTABLE
        )

        val alarm = context.getSystemService(Context.ALARM_SERVICE) as AlarmManager
        alarm.setAndAllowWhileIdle(
            AlarmManager.RTC_WAKEUP,
            System.currentTimeMillis() + WAKE_INTERVAL_MS,
            pending
        )
    }

    fun cancel(context: Context) {
        val intent = Intent(context, RelayWakeReceiver::class.java)
        val pending = PendingIntent.getBroadcast(
            context, REQUEST_CODE, intent,
            PendingIntent.FLAG_UPDATE_CURRENT or PendingIntent.FLAG_IMMUTABLE
        )
        val alarm = context.getSystemService(Context.ALARM_SERVICE) as AlarmManager
        alarm.cancel(pending)
    }
}

class RelayWakeReceiver : BroadcastReceiver() {

    override fun onReceive(context: Context, intent: Intent?) {
        if (!RelayStore.isRegistered(context)) return

        try {
            context.startForegroundService(Intent(context, RelayPollService::class.java))
        } catch (_: Exception) {
            try {
                context.startService(Intent(context, RelayPollService::class.java))
            } catch (_: Exception) {
                // 后台启动受限，等待下一次闹钟或打开 App。
            }
        }

        RelayWakeScheduler.schedule(context)
    }
}
