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

class MainActivity : Activity() {

    private val backgroundColor = Color.rgb(20, 20, 35)
    private val cardColor = Color.rgb(38, 38, 58)
    private val accentColor = Color.rgb(220, 120, 170)
    private lateinit var resultText: TextView

    override fun onCreate(savedInstanceState: Bundle?) {
        super.onCreate(savedInstanceState)

        val root = LinearLayout(this).apply {
            orientation = LinearLayout.VERTICAL
            setPadding(24, 40, 24, 24)
            setBackgroundColor(backgroundColor)
        }

        root.addView(text("Amora Link", 32f, Color.WHITE))
        root.addView(text("Your AI Companion Connection", 14f, accentColor))
        root.addView(space(28))

        addCard(root, "AI Companions", "Manage your connected AI companions")

        addCard(root, "Usage Access", "Tap to open app usage permission settings") {
            startActivity(Intent(Settings.ACTION_USAGE_ACCESS_SETTINGS))
        }

        addCard(root, "Smart Reminders", "Receive helpful interaction reminders")
        addCard(root, "App Management", "Manage your companion app preferences")

        root.addView(space(18))

        val readButton = Button(this).apply {
            text = "读取最近 24 小时使用记录"
            setOnClickListener { readUsageRecords() }
        }
        root.addView(readButton)

        root.addView(space(12))
        resultText = text("点击上方按钮，查看手机应用使用记录。", 14f, Color.WHITE)
        root.addView(resultText)

        root.addView(space(24))
        root.addView(text("Your connections. Your choices. Your control.", 13f, Color.LTGRAY))

        setContentView(root)
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
                resultText.text = "暂时没有读取到记录。请确认 Amora Link 的“使用情况访问”权限已开启，然后再试一次。"
                return
            }

            val lines = records
                .filter { it.totalTimeInForeground > 0 }
                .sortedByDescending { it.totalTimeInForeground }
                .take(15)
                .mapNotNull { record ->
                    try {
                        val appName = packageManager.getApplicationLabel(
                            packageManager.getApplicationInfo(record.packageName, 0)
                        )
                        val minutes = record.totalTimeInForeground / 60000
                        "$appName：约 $minutes 分钟"
                    } catch (_: Exception) {
                        null
                    }
                }

            resultText.text = if (lines.isEmpty()) {
                "最近 24 小时暂时没有可显示的应用记录。"
            } else {
                "最近 24 小时应用使用记录：\n\n" + lines.joinToString("\n")
            }
        } catch (e: Exception) {
            resultText.text = "读取失败：${e.message ?: "请稍后重试"}"
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
