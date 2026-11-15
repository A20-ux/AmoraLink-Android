package com.amoralink.app

import android.app.Activity
import android.app.AlertDialog
import android.app.usage.UsageStatsManager
import android.app.usage.UsageEvents
import android.content.ContentValues
import android.content.Intent
import android.database.sqlite.SQLiteDatabase
import android.database.sqlite.SQLiteOpenHelper
import android.graphics.Color
import android.os.Bundle
import android.provider.Settings
import android.view.View
import android.widget.*
import java.text.SimpleDateFormat
import java.util.Date
import java.util.Locale

class MainActivity : Activity() {

    private val prefsName = "AmoraLinkSettings"
    private val selectedKey = "selected_companion_apps"
    private lateinit var resultText: TextView
    private lateinit var database: UsageDatabase

    private val bg = Color.rgb(20, 20, 35)
    private val accent = Color.rgb(220, 120, 170)

    override fun onCreate(savedInstanceState: Bundle?) {
        super.onCreate(savedInstanceState)
        database = UsageDatabase(this)

        val root = LinearLayout(this).apply {
            orientation = LinearLayout.VERTICAL
            setPadding(24, 36, 24, 24)
            setBackgroundColor(bg)
        }

        root.addView(label("Amora Link", 30f, Color.WHITE))
        root.addView(label("陪伴应用 · 使‮记用‬录 · ‮忆记‬管理", 15f, accent))
        root.addView(space(18))

        addButton(root, "选择 AI 伴侣应用") { chooseApps() }
        addButton(root, "‮启开‬使用情况访问权限") {
            startActivity(Intent(Settings.ACTION_USAGE_ACCESS_SETTINGS))
        }
        addButton(root, "刷‮使新‬用记录") {
            scanUsageEvents()
            showUsageReport()
        }
        addButton(root, "查看‮动互‬记录") {
            scanUsageEvents()
            showInteractionHistory()
        }

        root.addView(space(12))
        root.addView(label("专属记忆", 21f, Color.WHITE))
        addButton(root, "添加‮条一‬记忆") { showMemoryEditor(null) }
        addButton(root, "查看 / 编辑 / 删除记忆") { showMemoryList() }

        root.addView(space(12))
        resultText = label(
            "先选‮陪择‬伴应用，再‮加添‬属于‮的它‬记忆。",
            14f,
            Color.WHITE
        )

addButton(root, "测试云端连接") {
    resultText.text = "正在连接云端……"
    CloudflareRelayClient.checkHealth { response ->
        resultText.text = response
    }
}
root.addView(resultText)

        setContentView(ScrollView(this).apply { addView(root) })
    }

    private fun chooseApps() {
        val intent = Intent(Intent.ACTION_MAIN)
            .addCategory(Intent.CATEGORY_LAUNCHER)

        val apps = packageManager.queryIntentActivities(intent, 0)
            .filter { it.activityInfo.packageName != packageName }
            .distinctBy { it.activityInfo.packageName }
            .sortedBy { it.loadLabel(packageManager).toString().lowercase() }

        if (apps.isEmpty()) {
            resultText.text = "‮有没‬找到可选择的应用。"
            return
        }

        val selected = getSelectedApps().toMutableSet()
        val names = apps.map {
            it.loadLabel(packageManager).toString()
        }.toTypedArray()

        val checked = apps.map {
            selected.contains(it.activityInfo.packageName)
        }.toBooleanArray()

        AlertDialog.Builder(this)
            .setTitle("选择要‮测监‬的陪‮应伴‬用")
            .setMultiChoiceItems(names, checked) { _, index, isChecked ->
                val pkg = apps[index].activityInfo.packageName
                if (isChecked) selected.add(pkg) else selected.remove(pkg)
            }
            .setPositiveButton("保‮选存‬择") { _, _ ->
                getSharedPreferences(prefsName, MODE_PRIVATE)
                    .edit()
                    .putStringSet(selectedKey, selected.toSet())
                    .apply()
                resultText.text = "已保存 ${selected.size} 个应用。"
            }
            .setNegativeButton("取消", null)
            .show()
    }

    private fun scanUsageEvents() {
        val selected = getSelectedApps()
        if (selected.isEmpty()) {
            resultText.text = "请先‮择选‬至少一‮陪个‬伴应用。"
            return
        }

        try {
            val manager = getSystemService(USAGE_STATS_SERVICE) as UsageStatsManager
            val end = System.currentTimeMillis()
            val start = end - 30L * 24 * 60 * 60 * 1000
            val events = manager.queryEvents(start, end)
            val event = UsageEvents.Event()
            val openSessions = mutableMapOf<String, Long>()
            var savedCount = 0

            while (events.hasNextEvent()) {
                events.getNextEvent(event)
                val pkg = event.packageName ?: continue
                if (!selected.contains(pkg)) continue

                when (event.eventType) {
                    UsageEvents.Event.MOVE_TO_FOREGROUND,
                    UsageEvents.Event.ACTIVITY_RESUMED -> {
                        openSessions[pkg] = event.timeStamp
                    }
                    UsageEvents.Event.MOVE_TO_BACKGROUND,
                    UsageEvents.Event.ACTIVITY_PAUSED -> {
                        val started = openSessions.remove(pkg)
                        if (started != null && event.timeStamp > started) {
                            database.saveSession(pkg, started, event.timeStamp)
                            savedCount++
                        }
                    }
                }
            }

            resultText.text = "系‮事统‬件读取完成，本次记录 $savedCount ‮使个‬用时段。"
        } catch (e: Exception) {
            resultText.text = "读取失败：${e.message ?: "请检查使‮情用‬况访‮权问‬限"}"
        }
    }

    private fun showUsageReport() {
        val selected = getSelectedApps()
        if (selected.isEmpty()) {
            resultText.text = "请先‮择选‬陪伴应用。"
            return
        }

        try {
            val manager = getSystemService(USAGE_STATS_SERVICE) as UsageStatsManager
            val now = System.currentTimeMillis()
            val stats = manager.queryUsageStats(
                UsageStatsManager.INTERVAL_DAILY,
                now - 24L * 60 * 60 * 1000,
                now
            ) ?: emptyList()

            val lines = selected.map { pkg ->
                val total = stats.filter { it.packageName == pkg }
                    .sumOf { it.totalTimeInForeground }
                val last = stats.filter { it.packageName == pkg }
                    .maxOfOrNull { it.lastTimeUsed } ?: 0L

                val minutes = total / 60000
                "${appName(pkg)}\n最近24小时：${minutes / 60}小时${minutes % 60}分钟" +
                    "\n最‮使近‬用：${if (last > 0) formatTime(last) else "系‮暂统‬未提供"}"
            }

            resultText.text = "最近24小时使‮情用‬况\n\n" + lines.joinToString("\n\n")
        } catch (_: Exception) {
            resultText.text = "读取失败，‮确请‬认已开启‮用使‬情况访‮权问‬限。"
        }
    }

    private fun showInteractionHistory() {
        val records = database.getRecentSessions(getSelectedApps(), 30)
        if (records.isEmpty()) {
            resultText.text = "暂时‮有没‬已完成的使用记录。"
            return
        }

        resultText.text = "最近‮动互‬时段\n\n" + records.joinToString("\n\n") {
            "${appName(it.packageName)}\n开始：${formatTime(it.started)}" +
                "\n结束：${formatTime(it.ended)}\n时长：${it.duration / 60000} 分钟"
        }
    }

    private fun showMemoryEditor(memory: MemoryRow?) {
        val selected = getSelectedApps().toList().sorted()
        if (selected.isEmpty()) {
            resultText.text = "请先选择 AI 伴‮应侣‬用，再‮它为‬添加记忆。"
            return
        }

        val form = LinearLayout(this).apply {
            orientation = LinearLayout.VERTICAL
            setPadding(24, 12, 24, 0)
        }

        val appNames = selected.map { appName(it) }
        val spinner = Spinner(this)
        spinner.adapter = ArrayAdapter(
            this,
            android.R.layout.simple_spinner_dropdown_item,
            appNames
        )
        val currentIndex = memory?.let { selected.indexOf(it.packageName) } ?: 0
        spinner.setSelection(if (currentIndex >= 0) currentIndex else 0)
        form.addView(label("‮联关‬的 AI 伴侣", 14f, Color.DKGRAY))
        form.addView(spinner)

        val titleInput = EditText(this).apply {
            hint = "记忆标题，例如：‮欢喜‬的称呼"
            setSingleLine(true)
            setText(memory?.title ?: "")
        }
        form.addView(titleInput)

        val contentInput = EditText(this).apply {
            hint = "记忆内容，例如：喜欢被称‮小为‬星星"
            minLines = 4
            gravity = android.view.Gravity.TOP
            setText(memory?.content ?: "")
        }
        form.addView(contentInput)

        AlertDialog.Builder(this)
            .setTitle(if (memory == null) "添‮记加‬忆" else "编辑记忆")
            .setView(form)
            .setPositiveButton("保存") { _, _ ->
                val title = titleInput.text.toString().trim()
                val content = contentInput.text.toString().trim()
                if (title.isEmpty() || content.isEmpty()) {
                    resultText.text = "标‮和题‬内容都不‮为能‬空。"
                    return@setPositiveButton
                }

                val pkg = selected[spinner.selectedItemPosition]
                database.saveMemory(
                    memory?.id,
                    pkg,
                    title,
                    content
                )
                resultText.text = "记忆已‮存保‬到手机本地。"
            }
            .setNegativeButton("取消", null)
            .show()
    }

    private fun showMemoryList() {
        val memories = database.getMemories(getSelectedApps())
        if (memories.isEmpty()) {
            resultText.text = "还没有记忆。点击“添加‮条一‬记忆”‮始开‬建立专属记忆。"
            return
        }

        val labels = memories.map {
            "${appName(it.packageName)} · ${it.title}"
        }.toTypedArray()

        AlertDialog.Builder(this)
            .setTitle("本‮记地‬忆（点击编辑，长按删除）")
            .setItems(labels) { _, index ->
                showMemoryActions(memories[index])
            }
            .setNegativeButton("关闭", null)
            .show()
    }

    private fun showMemoryActions(memory: MemoryRow) {
        AlertDialog.Builder(this)
            .setTitle(memory.title)
            .setMessage(
                "${appName(memory.packageName)}\n\n${memory.content}\n\n" +
                    "创建：${formatTime(memory.createdAt)}\n" +
                    "修改：${formatTime(memory.updatedAt)}"
            )
            .setPositiveButton("编辑") { _, _ ->
                showMemoryEditor(memory)
            }
            .setNeutralButton("删除") { _, _ ->
                AlertDialog.Builder(this)
                    .setMessage("‮定确‬删除这条记忆吗？删除后‮法无‬恢复。")
                    .setPositiveButton("删除") { _, _ ->
                        database.deleteMemory(memory.id)
                        resultText.text = "‮忆记‬已删除。"
                    }
                    .setNegativeButton("取消", null)
                    .show()
            }
            .setNegativeButton("关闭", null)
            .show()
    }

    private fun getSelectedApps(): Set<String> {
        return getSharedPreferences(prefsName, MODE_PRIVATE)
            .getStringSet(selectedKey, emptySet()) ?: emptySet()
    }

    private fun appName(pkg: String): String {
        return try {
            packageManager.getApplicationLabel(
                packageManager.getApplicationInfo(pkg, 0)
            ).toString()
        } catch (_: Exception) {
            pkg
        }
    }

    private fun formatTime(time: Long): String {
        return if (time <= 0) "未知" else SimpleDateFormat(
            "yyyy-MM-dd HH:mm",
            Locale.getDefault()
        ).format(Date(time))
    }

    private fun addButton(root: LinearLayout, title: String, action: () -> Unit) {
        val button = Button(this).apply {
            text = title
            setOnClickListener { action() }
        }
        val params = LinearLayout.LayoutParams(
            LinearLayout.LayoutParams.MATCH_PARENT,
            LinearLayout.LayoutParams.WRAP_CONTENT
        )
        params.bottomMargin = 8
        root.addView(button, params)
    }

    private fun label(value: String, size: Float, color: Int): TextView {
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

private class UsageDatabase(context: android.content.Context) :
    SQLiteOpenHelper(context, "amoralink_usage.db", null, 2) {

    override fun onCreate(db: SQLiteDatabase) {
        db.execSQL(
            """
            CREATE TABLE sessions (
                id INTEGER PRIMARY KEY AUTOINCREMENT,
                package_name TEXT NOT NULL,
                started INTEGER NOT NULL,
                ended INTEGER NOT NULL,
                duration INTEGER NOT NULL,
                UNIQUE(package_name, started)
            )
            """.trimIndent()
        )
        createMemoryTable(db)
    }

    private fun createMemoryTable(db: SQLiteDatabase) {
        db.execSQL(
            """
            CREATE TABLE IF NOT EXISTS memories (
                id INTEGER PRIMARY KEY AUTOINCREMENT,
                package_name TEXT NOT NULL,
                title TEXT NOT NULL,
                content TEXT NOT NULL,
                created_at INTEGER NOT NULL,
                updated_at INTEGER NOT NULL
            )
            """.trimIndent()
        )
    }

    override fun onUpgrade(db: SQLiteDatabase, oldVersion: Int, newVersion: Int) {
        if (oldVersion < 2) createMemoryTable(db)
    }

    fun saveSession(pkg: String, start: Long, end: Long) {
        if (end <= start) return
        val values = ContentValues().apply {
            put("package_name", pkg)
            put("started", start)
            put("ended", end)
            put("duration", end - start)
        }
        writableDatabase.insertWithOnConflict(
            "sessions", null, values, SQLiteDatabase.CONFLICT_REPLACE
        )
    }

    fun getRecentSessions(packages: Set<String>, limit: Int): List<SessionRow> {
        if (packages.isEmpty()) return emptyList()
        val marks = packages.joinToString(",") { "?" }
        val cursor = readableDatabase.rawQuery(
            """
            SELECT package_name, started, ended, duration FROM sessions
            WHERE package_name IN ($marks) ORDER BY started DESC LIMIT ?
            """.trimIndent(),
            packages.toTypedArray() + limit.toString()
        )
        val rows = mutableListOf<SessionRow>()
        cursor.use {
            while (it.moveToNext()) {
                rows.add(SessionRow(it.getString(0), it.getLong(1), it.getLong(2), it.getLong(3)))
            }
        }
        return rows
    }

    fun saveMemory(id: Long?, pkg: String, title: String, content: String) {
        val now = System.currentTimeMillis()
        val values = ContentValues().apply {
            put("package_name", pkg)
            put("title", title)
            put("content", content)
            put("updated_at", now)
        }

        if (id == null) {
            values.put("created_at", now)
            writableDatabase.insert("memories", null, values)
        } else {
            writableDatabase.update("memories", values, "id = ?", arrayOf(id.toString()))
        }
    }

    fun getMemories(packages: Set<String>): List<MemoryRow> {
        if (packages.isEmpty()) return emptyList()
        val marks = packages.joinToString(",") { "?" }
        val cursor = readableDatabase.rawQuery(
            """
            SELECT id, package_name, title, content, created_at, updated_at
            FROM memories WHERE package_name IN ($marks)
            ORDER BY updated_at DESC
            """.trimIndent(),
            packages.toTypedArray()
        )
        val rows = mutableListOf<MemoryRow>()
        cursor.use {
            while (it.moveToNext()) {
                rows.add(
                    MemoryRow(
                        it.getLong(0), it.getString(1), it.getString(2),
                        it.getString(3), it.getLong(4), it.getLong(5)
                    )
                )
            }
        }
        return rows
    }

    fun deleteMemory(id: Long) {
        writableDatabase.delete("memories", "id = ?", arrayOf(id.toString()))
    }
}

private data class SessionRow(
    val packageName: String,
    val started: Long,
    val ended: Long,
    val duration: Long
)

private data class MemoryRow(
    val id: Long,
    val packageName: String,
    val title: String,
    val content: String,
    val createdAt: Long,
    val updatedAt: Long
)
