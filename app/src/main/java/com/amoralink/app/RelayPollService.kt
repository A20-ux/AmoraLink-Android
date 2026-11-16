package com.amoralink.app

import android.app.Service
import android.content.Intent
import android.os.Handler
import android.os.IBinder
import android.os.Looper
import org.json.JSONObject

/**
 * 云端中继轮询服务：按设置拉取待执行指令，
 * 交给 AiControlManager 做完整安全校验后执行，再回执移出队列。
 *
 * 兜底策略：仅轮询模式 30 秒一轮；优先实时推送模式在推送适配器接入前以 15 秒快速轮询兜底，
 * 真实推送（FCM / 厂商推送）到达后会立即触发一次拉取。
 * 注意：这是普通后台服务，进程被系统回收后需下次打开 App 或开机后重新拉起。
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
        stopped = false
        handler.post(pollTask)
    }

    override fun onStartCommand(intent: Intent?, flags: Int, startId: Int): Int {
        // 被推送消息或界面拉起时立即拉取一次；定时兜底由 pollTask 继续周期执行。
        poll()
        return START_STICKY
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
        super.onDestroy()
    }

    override fun onBind(intent: Intent?): IBinder? = null
}
