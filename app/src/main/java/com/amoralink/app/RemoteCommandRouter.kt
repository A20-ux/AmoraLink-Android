package com.amoralink.app

import android.content.Context
import org.json.JSONObject

/**
 * 推送平台无关的远程指令处理入口。
 *
 * 未来 Huawei Push Kit、FCM 或其他消息适配器，
 * 收到消息后都可以调用 dispatch()。
 */
object RemoteCommandRouter {

    fun dispatch(
        context: Context,
        payload: Map<String, String>
    ): AiControlManager.CommandResult {

        if (payload.isEmpty() || payload.size > 16) {
            return AiControlManager.CommandResult(false, "消息内容无效")
        }

        for ((key, value) in payload) {
            if (key.length > 64 || value.length > 4096) {
                return AiControlManager.CommandResult(false, "消息字段超出限制")
            }
        }

        return try {
            val json = JSONObject()
            payload.forEach { (key, value) ->
                json.put(key, value)
            }

            AiControlManager.handleCommand(
                context.applicationContext,
                json.toString()
            )
        } catch (_: Exception) {
            AiControlManager.CommandResult(false, "远程指令处理失败")
        }
    }
}
