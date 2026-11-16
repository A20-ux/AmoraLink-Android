package com.amoralink.app

import android.os.Handler
import android.os.Looper
import org.json.JSONObject
import java.net.HttpURLConnection
import java.net.URL

/**
 * Cloudflare Worker 中继客户端。
 *
 * - checkHealth：健康检查。
 * - register：用配对令牌换发设备凭证（device_id + secret）。
 * - poll：凭设备凭证拉取待执行指令。
 * - ack：执行完成后按 command_id 回执，移出队列。
 *
 * 所有请求都在后台线程发起，回调切回主线程；不在日志里打印令牌或 secret。
 */
object CloudflareRelayClient {
    private const val BASE_URL =
        "https://dry-unit-113e.3557419867.workers.dev"
    private const val TIMEOUT = 10_000

    fun checkHealth(callback: (String) -> Unit) {
        Thread {
            val result = try {
                val connection =
                    URL("$BASE_URL/health").openConnection() as HttpURLConnection
                connection.requestMethod = "GET"
                connection.connectTimeout = TIMEOUT
                connection.readTimeout = TIMEOUT
                val response = connection.inputStream
                    .bufferedReader()
                    .use { it.readText() }
                connection.disconnect()
                response
            } catch (e: Exception) {
                "连接失败：${e.message}"
            }

            Handler(Looper.getMainLooper()).post { callback(result) }
        }.start()
    }

    /** 注册设备。回调参数：是否成功、device_id、secret、错误信息。 */
    fun register(
        token: String,
        deviceName: String,
        callback: (Boolean, String, String, String?) -> Unit
    ) {
        val body = JSONObject()
            .put("token", token)
            .put("device_name", deviceName)
            .toString()

        post("/v1/register", body) { ok, text ->
            if (!ok) {
                callback(false, "", "", text)
                return@post
            }
            try {
                val json = JSONObject(text)
                callback(
                    true,
                    json.optString("device_id", ""),
                    json.optString("secret", ""),
                    null
                )
            } catch (e: Exception) {
                callback(false, "", "", e.message)
            }
        }
    }

    /** 轮询待执行指令。回调参数：是否成功、指令数组原始 JSON、错误信息。 */
    fun poll(
        deviceId: String,
        secret: String,
        callback: (Boolean, String, String?) -> Unit
    ) {
        val body = JSONObject()
            .put("device_id", deviceId)
            .put("secret", secret)
            .toString()

        post("/v1/poll", body) { ok, text ->
            if (ok) callback(true, text, null) else callback(false, "", text)
        }
    }

    /** 回执单条指令。 */
    fun ack(
        deviceId: String,
        secret: String,
        commandId: String,
        callback: (Boolean) -> Unit
    ) {
        val body = JSONObject()
            .put("device_id", deviceId)
            .put("secret", secret)
            .put("command_id", commandId)
            .toString()

        post("/v1/ack", body) { ok, _ -> callback(ok) }
    }

    private fun post(
        path: String,
        body: String,
        callback: (Boolean, String) -> Unit
    ) {
        Thread {
            var connection: HttpURLConnection? = null
            val result = try {
                connection = URL(BASE_URL + path).openConnection() as HttpURLConnection
                connection.requestMethod = "POST"
                connection.connectTimeout = TIMEOUT
                connection.readTimeout = TIMEOUT
                connection.doOutput = true
                connection.setRequestProperty("Content-Type", "application/json")

                connection.outputStream.use { it.write(body.toByteArray(Charsets.UTF_8)) }

                val code = connection.responseCode
                val stream = if (code in 200..299) connection.inputStream else connection.errorStream
                val text = stream?.bufferedReader()?.use { it.readText() } ?: ""
                Pair(code in 200..299, text)
            } catch (e: Exception) {
                Pair(false, e.message ?: "网络错误")
            } finally {
                connection?.disconnect()
            }

            Handler(Looper.getMainLooper()).post { callback(result.first, result.second) }
        }.start()
    }
}
