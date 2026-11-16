package com.amoralink.app

import android.app.Service
import android.content.Intent
import android.os.Handler
import android.os.IBinder
import android.os.Looper
import org.json.JSONObject

/**
 * 云端中继轮询服务：每 30 秒拉取一次待执行指令，
 * 交给 AiControlManager 做完整安全校验后执行，再回执移出队列。
 *
 * 注意：这是普通后台服务，进程被系统回收后需下次打开 App 或开机后重新拉起；
 * 高可靠的实时下发后续应接 FCM / 厂商推送。
 */
class RelayPollService : Service() {

    private val handler = Handler(Looper.getMainLooper())
    private var stopped = false
    private val intervalMs = 30_000L

    private val pollTask = object : Runnable {
        override fun run() {
            poll()
            if (!stopped) handler.postDelayed(this, intervalMs)
        }
    }

    override fun onCreate() {
        super.onCreate()
        stopped = false
        handler.post(pollTask)
    }

    override fun onStartCommand(intent: Intent?, flags: Int, startId: Int): Int {
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
