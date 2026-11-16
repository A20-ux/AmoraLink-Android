package com.amoralink.app

import android.content.BroadcastReceiver
import android.content.Context
import android.content.Intent

/**
 * 开机重启后恢复主动提醒闹钟。
 * 后台限制下不在开机广播里直接拉起悬浮服务；
 * 闹钟触发时由 ProactiveReminderReceiver 发通知，悬浮气泡在下次打开 App 时由界面启动。
 */
class AmoraBootReceiver : BroadcastReceiver() {

    override fun onReceive(context: Context, intent: Intent?) {
        if (intent?.action != Intent.ACTION_BOOT_COMPLETED) return

        val prefs = context.getSharedPreferences("AmoraLinkSettings", Context.MODE_PRIVATE)
        if (prefs.getBoolean("proactive_enabled", false)) {
            ReminderScheduler.schedule(context)
        }
    }
}
