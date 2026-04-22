package com.netmonitor.app.util

import android.content.Context
import com.netmonitor.app.Constants
import com.netmonitor.app.model.ConnectionInfo
import com.netmonitor.app.model.ExposureRecord
import kotlinx.coroutines.*
import org.json.JSONArray
import java.io.File
import java.text.SimpleDateFormat
import java.util.Date
import java.util.Locale
import java.util.concurrent.ConcurrentHashMap
import java.util.concurrent.CopyOnWriteArrayList

/**
 * 暴露记录管理器
 *
 * 改进点:
 * - seenKeys 改用 ConcurrentHashMap.newKeySet()，解决多线程竞争
 * - save() 改为延迟批量写入（防抖），避免每条记录都触发全量磁盘 I/O
 * - 常量统一引用 Constants
 */
object ExposureLogManager {

    private const val TAG = "ExposureLog"

    private val records = CopyOnWriteArrayList<ExposureRecord>()

    /** 线程安全的去重集合 —— 修复原来的 mutableSetOf 并发问题 */
    private val seenKeys: MutableSet<String> = ConcurrentHashMap.newKeySet()

    private var initialized = false

    // ── 延迟写入防抖 ──
    private var saveJob: Job? = null
    private val saveScope = CoroutineScope(Dispatchers.IO + SupervisorJob())

    // ── 初始化 ──

    @Synchronized
    fun init(context: Context) {
        if (initialized) return
        val file = File(context.filesDir, Constants.EXPOSURE_LOG_FILE)
        if (file.exists()) {
            try {
                val jsonArray = JSONArray(file.readText())
                for (i in 0 until jsonArray.length()) {
                    val record = ExposureRecord.fromJson(jsonArray.getJSONObject(i))
                    if (record != null) {
                        records.add(record)
                    }
                }
                AppLogger.i(TAG, "加载了 ${records.size} 条暴露记录")
            } catch (e: Exception) {
                AppLogger.e(TAG, "加载暴露记录失败: ${e.message}")
            }
        }
        initialized = true
    }

    // ── 会话管理 ──

    fun resetSession() {
        seenKeys.clear()
        AppLogger.i(TAG, "会话去重已重置")
    }

    // ── 扫描与记录 ──

    /**
     * 扫描连接列表，自动记录新暴露
     * @return 本次新增的记录数
     */
    fun scanAndLog(context: Context, connections: List<ConnectionInfo>): Int {
        if (!initialized) init(context)
        var newCount = 0

        for (conn in connections) {
            if (conn.isRealIpExposed) {
                val record = ExposureRecord.fromConnection(conn, "ip_leak")
                if (seenKeys.add(record.deduplicationKey)) {
                    addRecord(context, record)
                    newCount++
                }
            }
            if (conn.isExposedListener) {
                val record = ExposureRecord.fromConnection(conn, "port_exposed")
                if (seenKeys.add(record.deduplicationKey)) {
                    addRecord(context, record)
                    newCount++
                }
            }
        }
        return newCount
    }

    private fun addRecord(context: Context, record: ExposureRecord) {
        records.add(0, record)

        // 超过上限，删除最旧的
        while (records.size > Constants.MAX_EXPOSURE_RECORDS) {
            records.removeAt(records.lastIndex)
        }

        // 延迟批量写入（防抖），避免每条记录都触发全量 I/O
        scheduleSave(context)

        AppLogger.i(TAG, "记录暴露: ${record.displayType} | ${record.appName} | " +
                "${record.localIp}:${record.localPort}")
    }

    /**
     * 防抖写入：在最后一次 addRecord 后延迟 EXPOSURE_DEBOUNCE_SAVE_MS 再执行写入，
     * 连续添加多条记录只会触发一次磁盘写入
     */
    private fun scheduleSave(context: Context) {
        saveJob?.cancel()
        saveJob = saveScope.launch {
            delay(Constants.EXPOSURE_DEBOUNCE_SAVE_MS)
            saveNow(context)
        }
    }

    /** 立即写入（用于 clear / App 退出等场景） */
    fun forceSave(context: Context) {
        saveJob?.cancel()
        saveNow(context)
    }

    private fun saveNow(context: Context) {
        try {
            val jsonArray = JSONArray()
            for (record in records) {
                jsonArray.put(record.toJson())
            }
            val file = File(context.filesDir, Constants.EXPOSURE_LOG_FILE)
            file.writeText(jsonArray.toString())
        } catch (e: Exception) {
            AppLogger.e(TAG, "保存暴露记录失败: ${e.message}")
        }
    }

    // ── 查询 ──

    fun getRecords(): List<ExposureRecord> = records.toList()

    fun getRecordsByType(type: String): List<ExposureRecord> {
        return records.filter { it.type == type }
    }

    fun getCount(): Int = records.size

    // ── 清空 ──

    fun clear(context: Context) {
        records.clear()
        seenKeys.clear()
        forceSave(context)
        AppLogger.i(TAG, "暴露记录已清空")
    }

    // ── 导出 ──

    fun exportText(): String {
        if (records.isEmpty()) return "无暴露记录"

        val sb = StringBuilder()
        sb.appendLine("===== NetMonitor 暴露记录 =====")
        sb.appendLine("导出时间: " + SimpleDateFormat(
            "yyyy-MM-dd HH:mm:ss", Locale.getDefault()
        ).format(Date()))
        sb.appendLine("总记录数: ${records.size}")
        sb.appendLine()

        val ipLeaks = records.filter { it.type == "ip_leak" }
        val portExposed = records.filter { it.type == "port_exposed" }

        sb.appendLine("--- 真实IP暴露: ${ipLeaks.size} 条 ---")
        for (r in ipLeaks) {
            sb.appendLine(r.format())
        }

        sb.appendLine("--- 端口暴露: ${portExposed.size} 条 ---")
        for (r in portExposed) {
            sb.appendLine(r.format())
        }

        sb.appendLine("===== End =====")
        return sb.toString()
    }

    /** 释放资源（App 退出时调用） */
    fun destroy() {
        saveJob?.cancel()
        saveScope.cancel()
    }
}
