package com.netmonitor.app.util

import android.util.Log
import java.io.BufferedReader
import java.io.DataOutputStream
import java.io.InputStreamReader
import java.util.concurrent.TimeUnit

object RootShell {

    private const val TAG = "RootShell"
    private const val TIMEOUT_SECONDS = 5L

    data class CommandResult(
        val exitCode: Int,
        val output: String,
        val error: String
    ) {
        val isSuccess get() = exitCode == 0
    }

    fun isRootAvailable(): Boolean {
        return try {
            AppLogger.d(TAG, "Checking root availability...")
            val process = Runtime.getRuntime().exec("su")
            val os = DataOutputStream(process.outputStream)
            os.writeBytes("id\n")
            os.writeBytes("exit\n")
            os.flush()
            os.close()

            val finished = process.waitFor(TIMEOUT_SECONDS, TimeUnit.SECONDS)
            if (!finished) {
                AppLogger.w(TAG, "Root check timed out after " + TIMEOUT_SECONDS + "s")
                process.destroyForcibly()
                return false
            }

            val reader = BufferedReader(InputStreamReader(process.inputStream))
            val output = reader.readText()
            reader.close()

            val hasRoot = process.exitValue() == 0 && output.contains("uid=0")
            AppLogger.i(TAG, "Root check result: " + hasRoot)
            hasRoot
        } catch (e: Exception) {
            AppLogger.w(TAG, "Root not available: " + e.message)
            false
        }
    }

    fun execute(command: String): CommandResult {
        return try {
            AppLogger.d(TAG, "Exec: " + command.take(80))
            val process = Runtime.getRuntime().exec("su")
            val os = DataOutputStream(process.outputStream)
            os.writeBytes(command + "\n")
            os.writeBytes("exit\n")
            os.flush()
            os.close()

            val finished = process.waitFor(TIMEOUT_SECONDS, TimeUnit.SECONDS)
            if (!finished) {
                AppLogger.w(TAG, "Command timed out: " + command.take(50))
                process.destroyForcibly()
                return CommandResult(-1, "", "Timeout after " + TIMEOUT_SECONDS + "s")
            }

            val stdout = BufferedReader(InputStreamReader(process.inputStream))
            val stderr = BufferedReader(InputStreamReader(process.errorStream))

            val output = stdout.readText()
            val error = stderr.readText()
            val exitCode = process.exitValue()

            stdout.close()
            stderr.close()

            AppLogger.d(TAG, "Result: exit=" + exitCode + " out=" + output.length + " chars")
            CommandResult(exitCode, output.trim(), error.trim())
        } catch (e: Exception) {
            AppLogger.e(TAG, "Execute failed: " + e.message)
            CommandResult(-1, "", e.message ?: "Unknown error")
        }
    }

    fun executeMultiple(commands: List<String>): CommandResult {
        return try {
            val process = Runtime.getRuntime().exec("su")
            val os = DataOutputStream(process.outputStream)
            for (cmd in commands) {
                os.writeBytes(cmd + "\n")
            }
            os.writeBytes("exit\n")
            os.flush()
            os.close()

            val finished = process.waitFor(TIMEOUT_SECONDS * 2, TimeUnit.SECONDS)
            if (!finished) {
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
            CommandResult(-1, "", e.message ?: "Unknown error")
        }
    }

    fun readFileAsRoot(path: String): String? {
        val result = execute("cat " + path)
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

    fun getProcessNetwork(pid: Int): String? {
        val result = execute("ls -la /proc/" + pid + "/fd 2>/dev/null | grep socket && cat /proc/" + pid + "/net/tcp 2>/dev/null")
        return if (result.isSuccess) result.output else null
    }

    fun getTcpdumpCapture(iface: String = "any", count: Int = 100): String? {
        val result = execute("tcpdump -i " + iface + " -c " + count + " -nn -q 2>/dev/null")
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

    fun grantPermission(packageName: String, permission: String): Boolean {
        val result = execute("pm grant " + packageName + " " + permission)
        return result.isSuccess
    }
}