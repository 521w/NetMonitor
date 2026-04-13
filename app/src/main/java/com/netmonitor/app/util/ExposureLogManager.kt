package com.netmonitor.app.util

import android.content.Context
import com.netmonitor.app.model.ConnectionInfo
import com.netmonitor.app.model.ExposureRecord
import org.json.JSONArray
import java.io.File
import java.text.SimpleDateFormat
import java.util.Date
import java.util.Locale
import java.util.concurrent.CopyOnWriteArrayList

/**
 * 暴露记录管理器
 * - JSON 文件持久化存储到 App 内部存储
 * - 会话级去重，同一连接不会重复记录
 * - 最多保留 MAX_RECORDS 条记录
 */
object ExposureLogManager {

    private const val FILE_NAME = "exposure_log.json"
    private const val MAX_RECORDS = 500

    private val records = CopyOnWriteArrayList<ExposureRecord>()
    private val seenKeys = mutableSetOf<String>()
    private var initialized = false

    /**
     * 初始化：从文件加载历史记录
     */
    @Synchronized
    fun init(context: Context) {
        if (initialized) return
        val file = File(context.filesDir, FILE_NAME)
        if (file.exists()) {
            try {
                val jsonArray = JSONArray(file.readText())
                for (i in 0 until jsonArray.length()) {
                    val record = ExposureRecord.fromJson(jsonArray.getJSONObject(i))
                    if (record != null) {
                        records.add(record)
                    }
                }
                AppLogger.i("ExposureLog", "\u52a0\u8f7d\u4e86 ${records.size} \u6761\u66b4\u9732\u8bb0\u5f55")
            } catch (e: Exception) {
                AppLogger.e("ExposureLog", "\u52a0\u8f7d\u66b4\u9732\u8bb0\u5f55\u5931\u8d25: ${e.message}")
            }
        }
        initialized = true
    }

    /**
     * 重置会话去重（监控启动时调用）
     */
    fun resetSession() {
        seenKeys.clear()
        AppLogger.i("ExposureLog", "\u4f1a\u8bdd\u53bb\u91cd\u5df2\u91cd\u7f6e")
    }

    /**
     * 扫描连接列表，自动记录新的暴露
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
        while (records.size > MAX_RECORDS) {
            records.removeAt(records.lastIndex)
        }

        save(context)
        AppLogger.i("ExposureLog",
            "\u8bb0\u5f55\u66b4\u9732: ${record.displayType} | ${record.appName} | ${record.localIp}:${record.localPort}")
    }

    /**
     * 获取所有记录
     */
    fun getRecords(): List<ExposureRecord> = records.toList()

    /**
     * 按类型筛选记录
     */
    fun getRecordsByType(type: String): List<ExposureRecord> {
        return records.filter { it.type == type }
    }

    /**
     * 获取记录数量
     */
    fun getCount(): Int = records.size

    /**
     * 清空所有记录
     */
    fun clear(context: Context) {
        records.clear()
        seenKeys.clear()
        save(context)
        AppLogger.i("ExposureLog", "\u66b4\u9732\u8bb0\u5f55\u5df2\u6e05\u7a7a")
    }

    /**
     * 导出为文本
     */
    fun exportText(): String {
        if (records.isEmpty()) return "\u65e0\u66b4\u9732\u8bb0\u5f55"

        val sb = StringBuilder()
        sb.appendLine("===== NetMonitor \u66b4\u9732\u8bb0\u5f55 =====")
        sb.appendLine("\u5bfc\u51fa\u65f6\u95f4: " + SimpleDateFormat(
            "yyyy-MM-dd HH:mm:ss", Locale.getDefault()
        ).format(Date()))
        sb.appendLine("\u603b\u8bb0\u5f55\u6570: ${records.size}")
        sb.appendLine()

        val ipLeaks = records.filter { it.type == "ip_leak" }
        val portExposed = records.filter { it.type == "port_exposed" }

        sb.appendLine("--- \u771f\u5b9eIP\u66b4\u9732: ${ipLeaks.size} \u6761 ---")
        for (r in ipLeaks) {
            sb.appendLine(r.format())
        }

        sb.appendLine("--- \u7aef\u53e3\u66b4\u9732: ${portExposed.size} \u6761 ---")
        for (r in portExposed) {
            sb.appendLine(r.format())
        }

        sb.appendLine("===== End =====")
        return sb.toString()
    }

    /**
     * 保存到文件
     */
    private fun save(context: Context) {
        try {
            val jsonArray = JSONArray()
            for (record in records) {
                jsonArray.put(record.toJson())
            }
            val file = File(context.filesDir, FILE_NAME)
            file.writeText(jsonArray.toString())
        } catch (e: Exception) {
            AppLogger.e("ExposureLog", "\u4fdd\u5b58\u66b4\u9732\u8bb0\u5f55\u5931\u8d25: ${e.message}")
        }
    }
}
