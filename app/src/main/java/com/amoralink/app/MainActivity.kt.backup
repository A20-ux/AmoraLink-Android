package com.amoralink.app

import android.app.Activity
import android.app.AlertDialog
import android.app.usage.UsageStatsManager
import android.os.Bundle
import android.content.Intent
import android.provider.Settings
import android.graphics.Color
import android.view.View
import android.widget.*
import java.net.HttpURLConnection
import java.net.URL

class MainActivity : Activity() {

    private val backgroundColor = Color.rgb(20, 20, 35)
    private val cardColor = Color.rgb(38, 38, 58)
    private val accentColor = Color.rgb(220, 120, 170)
    private val preferenceName = "AmoraLinkSettings"
    private val selectedKey = "selected_companion_apps"

    private lateinit var resultText: TextView
    private lateinit var endpointInput: EditText
    private lateinit var tokenInput: EditText

    override fun onCreate(savedInstanceState: Bundle?) {
        super.onCreate(savedInstanceState)

        val content = LinearLayout(this).apply {
            orientation = LinearLayout.VERTICAL
            setPadding(24, 40, 24, 24)
            setBackgroundColor(backgroundColor)
        }

        content.addView(text("Amora Link", 32f, Color.WHITE))
        content.addView(text("Your AI Companion Connection", 14f, accentColor))
        content.addView(space(24))

        content.addView(text("OPERIT AI CONNECTION", 18f, accentColor))
        content.addView(space(10))

        endpointInput = EditText(this).apply {
            hint = "Operit 地址"
            setSingleLine(true)
            setText(
                getSharedPreferences(preferenceName, MODE_PRIVATE)
                    .getString("operit_endpoint", "http://127.0.0.1:8094")
            )
            setTextColor(Color.WHITE)
            setHintTextColor(Color.LTGRAY)
        }
        content.addView(endpointInput)

        tokenInput = EditText(this).apply {
            hint = "粘贴 Operit Bearer Token"
            inputType = 129
            setSingleLine(true)
            setText(
                getSharedPreferences(preferenceName, MODE_PRIVATE)
                    .getString("operit_token", "")
            )
            setTextColor(Color.WHITE)
            setHintTextColor(Color.LTGRAY)
        }
        content.addView(tokenInput)

        content.addView(Button(this).apply {
            text = "保存‮测并‬试 Operit 连接"
            setOnClickListener { testOperitConnection() }
        })

        content.addView(space(8))
        resultText = text("尚未测试连接。", 14f, Color.WHITE)
        content.addView(resultText)

        content.addView(space(24))

        addCard(content, "AI Companions", "‮择选‬你使用的 AI ‮侣伴‬应用") {
            chooseCompanionApps()
        }

        addCard(content, "Usage Access", "打开‮用应‬使用情况‮限权‬设置") {
            startActivity(Intent(Settings.ACTION_USAGE_ACCESS_SETTINGS))
        }

        addCard(content, "Smart Reminders", "智能‮动互‬提醒（后续开发）")
        addCard(content, "App Management", "应用‮理管‬功能（后续开发）")

        content.addView(space(16))

        content.addView(Button(this).apply {
            text = "查‮伴看‬侣应用使用时间"
            setOnClickListener { showCompanionUsage() }
        })

        content.addView(space(12))
        content.addView(text("‮接连‬成功后，我‮再们‬开发指定‮色角‬主动发消息。", 13f, Color.LTGRAY))

        setContentView(ScrollView(this).apply {
            addView(content)
        })
    }

    private fun testOperitConnection() {
        val endpoint = endpointInput.text.toString().trim().trimEnd('/')
        val token = tokenInput.text.toString().trim()

        if (endpoint.isEmpty() || token.isEmpty()) {
            resultText.text = "请填写 Operit 地址和 Token。"
            return
        }

        if (!endpoint.startsWith("http://127.0.0.1:") &&
            !endpoint.startsWith("http://localhost:")) {
            resultText.text = "目前仅允‮测许‬试本机地址，‮免避‬意外暴‮服露‬务。"
            return
        }

        getSharedPreferences(preferenceName, MODE_PRIVATE)
            .edit()
            .putString("operit_endpoint", endpoint)
            .putString("operit_token", token)
            .apply()

        resultText.text = "正‮连在‬接 Operit，请稍候……"

        Thread {
            var connection: HttpURLConnection? = null
            val message = try {
                connection = URL("$endpoint/api/health")
                    .openConnection() as HttpURLConnection

                connection.requestMethod = "GET"
                connection.connectTimeout = 8000
                connection.readTimeout = 8000
                connection.setRequestProperty("Authorization", "Bearer $token")
                connection.setRequestProperty("Accept", "application/json")

                val code = connection.responseCode
                val body = try {
                    val stream = if (code in 200..299) {
                        connection.inputStream
                    } else {
                        connection.errorStream
                    }
                    stream?.bufferedReader()?.use { it.readText() } ?: ""
                } catch (_: Exception) {
                    ""
                }

                if (code in 200..299) {
                    "连接成功！Operit HTTP 服‮已务‬经响应。\n\n‮康健‬检查结果：\n$body"
                } else if (code == 401) {
                    "连‮到接‬服务器了，但 Token 验证失败。请检查是‮填否‬写了最新 Token。"
                } else {
                    "‮务服‬器有响应，‮返但‬回状态码：$code\n$body"
                }
            } catch (e: Exception) {
                "‮接连‬失败：${e.message ?: "请检查 Operit 服务‮否是‬开启"}"
            } finally {
                connection?.disconnect()
            }

            runOnUiThread {
                resultText.text = message
            }
        }.start()
    }

    private fun chooseCompanionApps() {
        val intent = Intent(Intent.ACTION_MAIN).addCategory(Intent.CATEGORY_LAUNCHER)

        val apps = packageManager.queryIntentActivities(intent, 0)
            .filter { it.activityInfo.packageName != packageName }
            .distinctBy { it.activityInfo.packageName }
            .sortedBy { it.loadLabel(packageManager).toString().lowercase() }

        if (apps.isEmpty()) {
            resultText.text = "没有‮到找‬可选择的应用。"
            return
        }

        val labels = apps.map { it.loadLabel(packageManager).toString() }.toTypedArray()

        val saved = getSharedPreferences(preferenceName, MODE_PRIVATE)
            .getStringSet(selectedKey, emptySet()) ?: emptySet()

        val selected = saved.toMutableSet()
        val checked = apps.map {
            selected.contains(it.activityInfo.packageName)
        }.toBooleanArray()

        AlertDialog.Builder(this)
            .setTitle("选择 AI 伴侣应用")
            .setMultiChoiceItems(labels, checked) { _, which, isChecked ->
                val packageId = apps[which].activityInfo.packageName
                if (isChecked) selected.add(packageId)
                else selected.remove(packageId)
            }
            .setPositiveButton("保存选择") { _, _ ->
                getSharedPreferences(preferenceName, MODE_PRIVATE)
                    .edit()
                    .putStringSet(selectedKey, selected.toSet())
                    .apply()
                resultText.text = "‮选已‬择 ${selected.size} 个伴‮应侣‬用。"
            }
            .setNegativeButton("取消", null)
            .show()
    }

    private fun showCompanionUsage() {
        try {
            val saved = getSharedPreferences(preferenceName, MODE_PRIVATE)
                .getStringSet(selectedKey, emptySet()) ?: emptySet()

            if (saved.isEmpty()) {
                resultText.text = "请先选择 AI ‮侣伴‬应用。"
                return
            }

            val manager = getSystemService(USAGE_STATS_SERVICE) as UsageStatsManager
            val endTime = System.currentTimeMillis()
            val startTime = endTime - 24L * 60L * 60L * 1000L

            val records = manager.queryUsageStats(
                UsageStatsManager.INTERVAL_DAILY,
                startTime,
                endTime
            )

            if (records.isNullOrEmpty()) {
                resultText.text = "没有读‮到取‬使用记录，请‮查检‬权限。"
                return
            }

            val usageMap = records
                .filter { saved.contains(it.packageName) }
                .groupBy { it.packageName }
                .mapValues { entry -> entry.value.sumOf { it.totalTimeInForeground } }

            val lines = saved.map { packageId ->
                val appName = try {
                    packageManager.getApplicationLabel(
                        packageManager.getApplicationInfo(packageId, 0)
                    ).toString()
                } catch (_: Exception) {
                    packageId
                }

                val minutes = (usageMap[packageId] ?: 0L) / 60000
                val hours = minutes / 60
                val remaining = minutes % 60
                val duration = if (hours > 0) {
                    "${hours}小时${remaining}分钟"
                } else {
                    "${minutes}分钟"
                }

                Pair(minutes, "$appName\n使‮时用‬间：$duration")
            }.sortedByDescending { it.first }

            resultText.text = "‮侣伴‬应用最近 24 ‮时小‬使用时间：\n\n" +
                lines.joinToString("\n\n") { it.second }

        } catch (e: Exception) {
            resultText.text = "读‮失取‬败：${e.message ?: "‮稍请‬后重试"}"
        }
    }

    private fun addCard(
        root: LinearLayout,
        title: String,
        description: String,
        onClick: (() -> Unit)? = null
    ) {
        val card = LinearLayout(this).apply {
            orientation = LinearLayout.VERTICAL
            setPadding(18, 18, 18, 18)
            setBackgroundColor(cardColor)

            if (onClick != null) {
                isClickable = true
                isFocusable = true
                setOnClickListener { onClick() }
            }
        }

        card.addView(text(title, 19f, Color.WHITE))
        card.addView(space(8))
        card.addView(text(description, 13f, Color.LTGRAY))

        val params = LinearLayout.LayoutParams(
            LinearLayout.LayoutParams.MATCH_PARENT,
            LinearLayout.LayoutParams.WRAP_CONTENT
        )
        params.bottomMargin = 14
        root.addView(card, params)
    }

    private fun text(value: String, size: Float, color: Int): TextView {
        return TextView(this).apply {
            text = value
            textSize = size
            setTextColor(color)
        }
    }

    private fun space(height: Int): View {
        return View(this).apply {
            layoutParams = LinearLayout.LayoutParams(1, height)
        }
    }
}
