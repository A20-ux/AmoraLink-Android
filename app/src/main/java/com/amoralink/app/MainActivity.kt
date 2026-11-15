package com.amoralink.app

import android.app.Activity
import android.app.usage.UsageStatsManager
import android.os.Bundle
import android.content.Intent
import android.provider.Settings
import android.graphics.Color
import android.view.View
import android.widget.LinearLayout
import android.widget.TextView
import android.widget.Button
import android.widget.ScrollView

class MainActivity : Activity() {

    private val backgroundColor = Color.rgb(20, 20, 35)
    private val cardColor = Color.rgb(38, 38, 58)
    private val accentColor = Color.rgb(220, 120, 170)
    private lateinit var resultText: TextView

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

        addCard(content, "AI Companions", "Manage your connected AI companions")

        addCard(content, "Usage Access", "Open app usage permission settings") {
            startActivity(Intent(Settings.ACTION_USAGE_ACCESS_SETTINGS))
        }

        addCard(content, "Smart Reminders", "Receive helpful interaction reminders")
        addCard(content, "App Management", "Manage your companion app preferences")

        content.addView(space(16))

        content.addView(Button(this).apply {
            text = "读取最近 24 小时使用记录"
            setOnClickListener { readUsageRecords() }
        })

        content.addView(space(12))
        resultText = text("点击按钮查看手机应用使用时间。", 14f, Color.WHITE)
        content.addView(resultText)

        val scroll = ScrollView(this).apply {
            addView(content)
        }
        setContentView(scroll)
    }

    private fun readUsageRecords() {
        try {
            val manager = getSystemService(USAGE_STATS_SERVICE) as UsageStatsManager
            val endTime = System.currentTimeMillis()
            val startTime = endTime - 24L * 60L * 60L * 1000L

            val records = manager.queryUsageStats(
                UsageStatsManager.INTERVAL_DAILY,
                startTime,
                endTime
            )

            if (records.isNullOrEmpty()) {
                resultText.text = "系统没有返回使用记录。请检查 Amora Link 的使用情况访问权限。"
                return
            }

            val lines = records
                .filter { it.totalTimeInForeground > 0 }
                .sortedByDescending { it.totalTimeInForeground }
                .map { record ->
                    val appName = try {
                        packageManager.getApplicationLabel(
                            packageManager.getApplicationInfo(record.packageName, 0)
                        ).toString()
                    } catch (_: Exception) {
                        record.packageName
                    }

                    val minutes = record.totalTimeInForeground / 60000
                    val hours = minutes / 60
                    val remainingMinutes = minutes % 60

                    val duration = if (hours > 0) {
                        "${hours}小时${remainingMinutes}分钟"
                    } else {
                        "${minutes}分钟"
                    }

                    "$appName\n使用时间：$duration"
                }

            resultText.text = if (lines.isEmpty()) {
                "最近 24 小时没有可显示的应用使用记录。"
            } else {
                "最近 24 小时应用使用记录：\n\n" +
                    lines.joinToString("\n\n")
            }
        } catch (e: Exception) {
            resultText.text = "读取失败：${e.message ?: "未知错误"}"
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
