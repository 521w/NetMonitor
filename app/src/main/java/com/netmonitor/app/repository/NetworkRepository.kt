package com.netmonitor.app.repository

import android.content.Context
import com.netmonitor.app.model.ConnectionInfo
import com.netmonitor.app.model.ExposureRecord
import com.netmonitor.app.util.ExposureLogManager
import com.netmonitor.app.util.NetworkParser
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.withContext

/**
 * 网络数据仓库层
 *
 * 职责：统一管理所有网络数据的获取和暴露检测逻辑，
 * 将 ViewModel 与具体数据源（NetworkParser / ExposureLogManager）解耦。
 *
 * ViewModel 不再直接调用 NetworkParser 和 ExposureLogManager，
 * 所有数据操作都通过 Repository 中转。
 */
class NetworkRepository(private val context: Context) {

    /**
     * 获取所有当前网络连接（IO 线程执行）
     */
    suspend fun getConnections(): List<ConnectionInfo> = withContext(Dispatchers.IO) {
        NetworkParser.parseAllConnections(context)
    }

    /**
     * 扫描连接列表中的暴露风险并记录（IO 线程执行）
     * @return 本次新增的暴露记录数
     */
    suspend fun scanExposures(connections: List<ConnectionInfo>): Int = withContext(Dispatchers.IO) {
        ExposureLogManager.scanAndLog(context, connections)
    }

    /**
     * 获取所有暴露记录
     */
    fun getExposureRecords(): List<ExposureRecord> {
        return ExposureLogManager.getRecords()
    }

    /**
     * 按类型获取暴露记录
     */
    fun getExposureRecordsByType(type: String): List<ExposureRecord> {
        return ExposureLogManager.getRecordsByType(type)
    }

    /**
     * 获取暴露记录数量
     */
    fun getExposureCount(): Int {
        return ExposureLogManager.getCount()
    }

    /**
     * 清空暴露记录
     */
    fun clearExposures() {
        ExposureLogManager.clear(context)
    }

    /**
     * 导出暴露记录为文本
     */
    fun exportExposureText(): String {
        return ExposureLogManager.exportText()
    }

    /**
     * 重置暴露检测会话（新一轮监控开始时调用）
     */
    fun resetExposureSession() {
        ExposureLogManager.resetSession()
    }

    /**
     * 强制保存暴露记录到磁盘
     */
    fun forceSaveExposures() {
        ExposureLogManager.forceSave(context)
    }
}
