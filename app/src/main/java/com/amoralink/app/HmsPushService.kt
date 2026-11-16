package com.amoralink.app

import android.content.Intent
import android.os.Bundle
import com.huawei.hms.push.HmsMessageService
import com.huawei.hms.push.RemoteMessage

/**
 * 华为 Push Kit 消息服务。
 *
 * - onNewToken：HMS 下发/刷新推送 token，保存到本地并上报 Worker 绑定 device_id。
 * - onMessageReceived：收到华为透传/通知消息后，立即拉起中继服务拉取一次待执行指令；
 *   拉取与执行仍走 RelayPollService → AiControlManager 的完整校验链路，轮询作为兜底。
 */
class HmsPushService : HmsMessageService() {

    override fun onNewToken(token: String, bundle: Bundle?) {
        super.onNewToken(token, bundle)
        if (token.isBlank()) return

        PushRegistrationStore.save(applicationContext, "huawei", token)
        RelayStore.savePushToken(applicationContext, token)

        val deviceId = RelayStore.getDeviceId(this)
        val secret = RelayStore.getSecret(this)
        if (deviceId.isNotBlank() && secret.isNotBlank()) {
            CloudflareRelayClient.reportPushToken(deviceId, secret, token) { _ -> }
        }
    }

    override fun onMessageReceived(message: RemoteMessage) {
        super.onMessageReceived(message)
        wakeRelayPoll()
    }

    private fun wakeRelayPoll() {
        try {
            startService(Intent(applicationContext, RelayPollService::class.java))
        } catch (_: Exception) {
            // 后台启动受限等情况忽略，轮询兜底仍会按周期拉取。
        }
    }
}
