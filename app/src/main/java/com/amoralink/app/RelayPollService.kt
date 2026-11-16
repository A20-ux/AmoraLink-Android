package com.amoralink.app

import android.app.Notification
import android.app.NotificationChannel
import android.app.NotificationManager
import android.app.Service
import android.content.Intent
import android.content.pm.ServiceInfo
import android.os.Build
import android.os.Handler
import android.os.IBinder
import android.os.Looper
import org.json.JSONObject

/**
 * 云端中继轮询服务（前台服务 + 闹钟唤醒兜底）。
 *
 * - 作为前台服务常驻，降低被系统后台回收的概率；通知为最低优先级、静默、不显示角标。
 * - 存活期间按设置周期轮询：仅轮询 30 秒，推送优先模式 15 秒。
 * - 进程若被回收，[RelayWakeReceiver] 会经 AlarmManager 按兜底间隔重新拉起本服务。
 * - 拉到的指令交给 AiControlManager 做完整安全校验后执行，再回执移出队列。
 */
class RelayPollService : Service() {

    private val handler = Handler(Looper.getMainLooper())
    private var stopped = false

    private fun intervalMs(): Long {
        val mode = getSharedPreferences("AmoraLinkSettings", MODE_PRIVATE)
            .getString("push_mode", "poll")
        return if (mode == "push") 15_000L else 30_000L
    }

    private val pollTask = object : Runnable {
        override fun run() {
            poll()
            if (!stopped) handler.postDelayed(this, intervalMs())
        }
    }

    override fun onCreate() {
        super.onCreate()
        ensureChannel()
        startAsForeground()
        stopped = false
        handler.post(pollTask)
    }

    override fun onStartCommand(intent: Intent?, flags: Int, startId: Int): Int {
        // 被闹钟或界面拉起时立即拉取一次；同时排好下一次兜底唤醒。
        poll()
        RelayWakeScheduler.schedule(this)
        return START_STICKY
    }

    private fun startAsForeground() {
        val notification = buildNotification()
        try {
            if (Build.VERSION.SDK_INT >= 34) {
                startForeground(
                    NOTIF_ID,
                    notification,
                    ServiceInfo.FOREGROUND_SERVICE_TYPE_DATA_SYNC
                )
            } else {
                startForeground(NOTIF_ID, notification)
            }
        } catch (_: Exception) {
            // 通知权限受限等情况不阻塞服务启动，轮询逻辑继续。
        }
    }

    private fun ensureChannel() {
        if (Build.VERSION.SDK_INT < 26) return
        val manager = getSystemService(NOTIFICATION_SERVICE) as NotificationManager
        val channel = NotificationChannel(
            CHANNEL_ID,
            "中继后台同步",
            NotificationManager.IMPORTANCE_MIN
        ).apply {
            description = "保持云端指令中继在后台同步，无声音、不弹出提醒。"
            setShowBadge(false)
            setSound(null, null)
        }
        manager.createNotificationChannel(channel)
    }

    private fun buildNotification(): Notification {
        val builder = if (Build.VERSION.SDK_INT >= 26) {
            Notification.Builder(this, CHANNEL_ID)
        } else {
            @Suppress("DEPRECATION")
            Notification.Builder(this)
        }
        return builder
            .setSmallIcon(android.R.drawable.ic_menu_recent_history)
            .setContentTitle("Amora Link")
            .setContentText("后台同步远程指令")
            .setOngoing(true)
            .setPriority(Notification.PRIORITY_MIN)
            .build()
    }

    private fun poll() {
        val deviceId = RelayStore.getDeviceId(this)
        val secret = RelayStore.getSecret(this)
        if (deviceId.isBlank() || secret.isBlank()) {
            stopSelf()
            return
        }

        CloudflareRelayClient.poll(deviceId, secret) { ok, body, _ ->
            if (!ok) {
                RelayStore.setLastPoll(this, false, body)
                return@poll
            }

            RelayStore.setLastPoll(this, true, "")
            try {
                val root = JSONObject(body)
                val commands = root.optJSONArray("commands") ?: return@poll

                for (i in 0 until commands.length()) {
                    val item = commands.getJSONObject(i)
                    val commandId = item.optString("command_id", "")
                    val payload = item.optJSONObject("payload") ?: continue

                    AiControlManager.handleCommand(applicationContext, payload.toString())

                    if (commandId.isNotBlank()) {
                        CloudflareRelayClient.ack(deviceId, secret, commandId) { _ -> }
                    }
                }
            } catch (_: Exception) {
            }
        }
    }

    override fun onDestroy() {
        stopped = true
        handler.removeCallbacks(pollTask)
        stopForeground(STOP_FOREGROUND_REMOVE)
        super.onDestroy()
    }

    override fun onBind(intent: Intent?): IBinder? = null

    companion object {
        private const val NOTIF_ID = 8401
        private const val CHANNEL_ID = "amoralink_relay"
    }
}
