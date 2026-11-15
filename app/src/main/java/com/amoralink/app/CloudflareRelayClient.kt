package com.amoralink.app

import android.os.Handler
import android.os.Looper
import java.net.HttpURLConnection
import java.net.URL

object CloudflareRelayClient {
    private const val BASE_URL =
        "https://dry-unit-113e.3557419867.workers.dev"

    fun checkHealth(callback: (String) -> Unit) {
        Thread {
            val result = try {
                val connection =
                    URL("$BASE_URL/health").openConnection()
                        as HttpURLConnection

                connection.requestMethod = "GET"
                connection.connectTimeout = 10000
                connection.readTimeout = 10000

                val response = connection.inputStream
                    .bufferedReader()
                    .use { it.readText() }

                connection.disconnect()
                response
            } catch (e: Exception) {
                "连接失败：${e.message}"
            }

            Handler(Looper.getMainLooper()).post {
                callback(result)
            }
        }.start()
    }
}
