package com.amoralink.app

import android.app.Activity
import android.app.AlertDialog
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
import android.content.pm.ResolveInfo

class MainActivity : Activity() {

    private val backgroundColor = Color.rgb(20, 20, 35)
    private val cardColor = Color.rgb(38, 38, 58)
    private val accentColor = Color.rgb(220, 120, 170)
    private val preferenceName = "AmoraLinkSettings"
    private val selectedKey = "selected_companion_apps"

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

        addCard(content, "AI Companions", "选择你使用的 AI 伴侣应用") {
            chooseCompanionApps()
        }

        addCard(content, "Usage Access", "打开应用使用情况权限设置") {
            startActivity(Intent(Settings.ACTION_USAGE_ACCESS_SETTINGS))
        }

        addCard(content, "Smart Reminders", "智能互动提醒（后续开发）")
        addCard(content, "App Management", "应用管理功能（后续开发）")

        content.addView(space(16))

        content.addView(Button(this).apply {
            text = "查看伴侣应用使用时间"
            setOnClickListener { showCompanionUsage() }
        })

        content.addView(space(12))

        resultText = text("先选择你使用的 AI 伴侣应用。", 14f, Color.WHITE)
        content.addView(resultText)

        setContentView(ScrollView(this).apply {
            addView(content)
        })
    }

    private fun chooseCompanionApps() {
        val intent = Intent(Intent.ACTION_MAIN).addCategory(Intent.CATEGORY_LAUNCHER)

        val apps: List<ResolveInfo> = packageManager
            .queryIntentActivities(intent, 0)
            .filter { it.activityInfo.packageName != packageName }
            .distinctBy { it.activityInfo.packageName }
            .sortedBy { it.loadLabel(packageManager).toString().lowercase() }

        if (apps.isEmpty()) {
            resultText.text = "没有找到可选择的应用。"
            return
        }

        val labels = apps.map {
            it.loadLabel(packageManager).toString()
        }.toTypedArray()

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
                if (isChecked) {
                    selected.add(packageId)
                } else {
                    selected.remove(packageId)
                }
            }
            .setPositiveButton("保存选择") { _, _ ->
                getSharedPreferences(preferenceName, MODE_PRIVATE)
                    .edit()
                    .putStringSet(selectedKey, selected.toSet())
                    .apply()

                resultText.text = "已选择 ${selected.size} 个伴侣应用。\n点击下方按钮查看使用时间。"
            }
            .setNegativeButton("取消", null)
            .show()
    }

    private fun showCompanionUsage() {
        try {
            val saved = getSharedPreferences(preferenceName, MODE_PRIVATE)
                .getStringSet(selectedKey, emptySet()) ?: emptySet()

            if (saved.isEmpty()) {
                resultText.text = "你还没有选择 AI 伴侣应用。请先点击“AI Companions”进行选择。"
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
                resultText.text = "没有读取到使用记录，请检查使用情况访问权限。"
                return
            }

            val usageMap = records
                .filter { saved.contains(it.packageName) }
                .groupBy { it.packageName }
                .mapValues { entry ->
                    entry.value.sumOf { it.totalTimeInForeground }
                }

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

                Pair(minutes, "$appName\n使用时间：$duration")
            }.sortedByDescending { it.first }

            resultText.text =
                "已选伴侣应用最近 24 小时使用时间：\n\n" +
                lines.joinToString("\n\n") { it.second }

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
