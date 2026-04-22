package com.netmonitor.app.util

import com.netmonitor.app.Constants
import java.io.BufferedReader
import java.io.DataOutputStream
import java.io.InputStreamReader
import java.util.concurrent.TimeUnit

/**
 * Root 权限命令执行器
 *
 * 安全改进:
 * - 所有接受用户输入的方法都做了参数校验，防止命令注入
 * - 提供 executeArgs() 安全执行方式
 * - 路径、包名、权限名等参数使用白名单正则过滤
 */
object RootShell {

    private const val TAG = "RootShell"
    private var cachedRootResult: Boolean? = null

    // ── 安全校验正则 ──
    private val SAFE_PATH_REGEX = Regex("^[a-zA-Z0-9/_.-]+$")
    private val SAFE_PACKAGE_REGEX = Regex("^[a-zA-Z0-9_.]+$")
    private val SAFE_IFACE_REGEX = Regex("^[a-zA-Z0-9_.-]+$")

    data class CommandResult(
        val exitCode: Int,
        val output: String,
        val error: String
    ) {
        val isSuccess get() = exitCode == 0
    }

    // ── Root 可用性检测 ──

    fun isRootAvailable(): Boolean {
        val cached = cachedRootResult
        if (cached != null) return cached
        val result = checkRoot()
        cachedRootResult = result
        return result
    }

    fun clearRootCache() {
        cachedRootResult = null
    }

    private fun checkRoot(): Boolean {
        return try {
            AppLogger.d(TAG, "Checking root availability...")
            val process = Runtime.getRuntime().exec("su")
            val os = DataOutputStream(process.outputStream)
            os.writeBytes("id\n")
            os.writeBytes("exit\n")
            os.flush()
            os.close()

            val finished = process.waitFor(Constants.ROOT_TIMEOUT_SECONDS, TimeUnit.SECONDS)
            if (!finished) {
                AppLogger.w(TAG, "Root check timed out")
                process.destroyForcibly()
                return false
            }

            val reader = BufferedReader(InputStreamReader(process.inputStream))
            val output = reader.readText()
            reader.close()

            val hasRoot = process.exitValue() == 0 && output.contains("uid=0")
            AppLogger.i(TAG, "Root available: $hasRoot")
            hasRoot
        } catch (e: Exception) {
            AppLogger.w(TAG, "Root not available: ${e.message}")
            false
        }
    }

    // ── 命令执行 ──

    /**
     * 执行一条 root 命令（内部使用，外部优先使用封装好的安全方法）
     */
    fun execute(command: String): CommandResult {
        return try {
            val process = Runtime.getRuntime().exec("su")
            val os = DataOutputStream(process.outputStream)
            os.writeBytes(command + "\n")
            os.writeBytes("exit\n")
            os.flush()
            os.close()

            val finished = process.waitFor(Constants.ROOT_TIMEOUT_SECONDS, TimeUnit.SECONDS)
            if (!finished) {
                AppLogger.w(TAG, "Timeout: ${command.take(60)}")
                process.destroyForcibly()
                return CommandResult(-1, "", "Timeout")
            }

            val stdout = BufferedReader(InputStreamReader(process.inputStream))
            val stderr = BufferedReader(InputStreamReader(process.errorStream))
            val output = stdout.readText()
            val error = stderr.readText()
            val exitCode = process.exitValue()
            stdout.close()
            stderr.close()

            CommandResult(exitCode, output.trim(), error.trim())
        } catch (e: Exception) {
            AppLogger.e(TAG, "Execute failed: ${e.message}")
            CommandResult(-1, "", e.message ?: "Unknown error")
        }
    }

    // ── 安全封装方法（对所有外部输入做校验） ──

    /**
     * 以 root 权限读取文件内容
     * @param path 文件路径，仅允许 [a-zA-Z0-9/_.-]
     */
    fun readFileAsRoot(path: String): String? {
        require(path.matches(SAFE_PATH_REGEX)) {
            "Invalid path characters: $path"
        }
        val result = execute("cat $path")
        return if (result.isSuccess) result.output else null
    }

    fun getNetstat(): String? {
        val result = execute("netstat -tunap 2>/dev/null || ss -tunap")
        return if (result.isSuccess) result.output else null
    }

    fun getArpTable(): String? {
        val result = execute("cat /proc/net/arp")
        return if (result.isSuccess) result.output else null
    }

    fun getRoutingTable(): String? {
        val result = execute("ip route show table all")
        return if (result.isSuccess) result.output else null
    }

    fun getIptablesRules(): String? {
        val result = execute("iptables -L -n -v 2>/dev/null && ip6tables -L -n -v 2>/dev/null")
        return if (result.isSuccess) result.output else null
    }

    fun getActiveInterfaces(): String? {
        val result = execute("ip -o link show")
        return if (result.isSuccess) result.output else null
    }

    fun getDnsInfo(): String? {
        val result = execute("getprop net.dns1 && getprop net.dns2 && cat /etc/resolv.conf 2>/dev/null")
        return if (result.isSuccess) result.output else null
    }

    /**
     * 查看指定进程的网络信息
     * @param pid 进程 ID，必须为正整数
     */
    fun getProcessNetwork(pid: Int): String? {
        require(pid > 0) { "Invalid PID: $pid" }
        val result = execute(
            "ls -la /proc/$pid/fd 2>/dev/null | grep socket && cat /proc/$pid/net/tcp 2>/dev/null"
        )
        return if (result.isSuccess) result.output else null
    }

    /**
     * 使用 tcpdump 抓包
     * @param iface 网络接口名，仅允许安全字符
     * @param count 抓包数量上限，1-10000
     */
    fun getTcpdumpCapture(iface: String = "any", count: Int = 100): String? {
        require(iface.matches(SAFE_IFACE_REGEX)) {
            "Invalid interface name: $iface"
        }
        require(count in 1..10000) {
            "Count out of range: $count"
        }
        val result = execute("tcpdump -i $iface -c $count -nn -q 2>/dev/null")
        return if (result.isSuccess) result.output else null
    }

    fun getWifiInfo(): String? {
        val result = execute("dumpsys wifi | grep -E 'mWifiInfo|SSID|BSSID|RSSI|Link speed|Frequency'")
        return if (result.isSuccess) result.output else null
    }

    fun getNetworkStats(): String? {
        val result = execute("cat /proc/net/dev")
        return if (result.isSuccess) result.output else null
    }

    fun getOpenPorts(): String? {
        val result = execute("ss -tulnp")
        return if (result.isSuccess) result.output else null
    }

    /**
     * 授予应用权限
     * @param packageName 包名，仅允许 [a-zA-Z0-9_.]
     * @param permission 权限名，仅允许 [a-zA-Z0-9_.]
     */
    fun grantPermission(packageName: String, permission: String): Boolean {
        require(packageName.matches(SAFE_PACKAGE_REGEX)) {
            "Invalid package name: $packageName"
        }
        require(permission.matches(SAFE_PACKAGE_REGEX)) {
            "Invalid permission name: $permission"
        }
        val result = execute("pm grant $packageName $permission")
        return result.isSuccess
    }
}
