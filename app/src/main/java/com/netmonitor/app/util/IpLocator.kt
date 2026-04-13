package com.netmonitor.app.util

import org.json.JSONObject
import java.net.HttpURLConnection
import java.net.URL

object IpLocator {

    data class IpInfo(
        val ip: String,
        val country: String,
        val region: String,
        val city: String,
        val isp: String,
        val org: String,
        val asNumber: String,
        val isLocal: Boolean = false,
        val error: String? = null
    ) {
        fun format(): String {
            if (error != null) return "查询失败: " + error
            if (isLocal) return ip + "\n\n本机 / 局域网地址\n无需查询"

            val sb = StringBuilder()
            sb.appendLine("IP: " + ip)
            sb.appendLine("国家: " + country)
            sb.appendLine("地区: " + region)
            sb.appendLine("城市: " + city)
            sb.appendLine("运营商: " + isp)
            if (org.isNotBlank() && org != isp) {
                sb.appendLine("组织: " + org)
            }
            sb.appendLine("ASN: " + asNumber)
            return sb.toString().trim()
        }

        fun oneLine(): String {
            if (isLocal) return "本机/局域网"
            if (error != null) return "查询失败"
            val parts = mutableListOf<String>()
            if (country.isNotBlank()) parts.add(country)
            if (region.isNotBlank()) parts.add(region)
            if (city.isNotBlank() && city != region) parts.add(city)
            if (isp.isNotBlank()) parts.add(isp)
            return parts.joinToString(" ")
        }
    }

    fun isLocalIp(ip: String): Boolean {
        val clean = ip.replace("::ffff:", "").trim()
        if (clean.startsWith("127.")) return true
        if (clean.startsWith("10.")) return true
        if (clean.startsWith("192.168.")) return true
        if (clean == "0.0.0.0" || clean == "0:0:0:0:0:0:0:0") return true
        if (clean == "::1" || clean == "::0" || clean == ":::0") return true
        if (clean.startsWith("::") && clean.length < 6) return true
        if (clean.startsWith("0:0:")) return true
        if (clean.startsWith("172.")) {
            val parts = clean.split(".")
            if (parts.size >= 2) {
                val second = parts[1].toIntOrNull() ?: 0
                if (second in 16..31) return true
            }
        }
        if (clean.startsWith("fe80:")) return true
        if (clean.startsWith("fc") || clean.startsWith("fd")) return true
        return false
    }

    fun lookup(ip: String): IpInfo {
        if (isLocalIp(ip)) {
            return IpInfo(
                ip = ip, country = "", region = "",
                city = "", isp = "", org = "",
                asNumber = "", isLocal = true
            )
        }

        return try {
            AppLogger.d("IpLocator", "Looking up: " + ip)
            val cleanIp = ip.replace("::ffff:", "")
            val apiUrl = "http://ip-api.com/json/" + cleanIp +
                "?lang=zh-CN&fields=status,message,country,regionName,city,isp,org,as,query"
            val url = URL(apiUrl)
            val conn = url.openConnection() as HttpURLConnection
            conn.connectTimeout = 8000
            conn.readTimeout = 8000
            conn.requestMethod = "GET"

            val code = conn.responseCode
            if (code != 200) {
                conn.disconnect()
                return IpInfo(
                    ip = ip, country = "", region = "",
                    city = "", isp = "", org = "",
                    asNumber = "", error = "HTTP " + code
                )
            }

            val response = conn.inputStream.bufferedReader().readText()
            conn.disconnect()

            val json = JSONObject(response)
            val status = json.optString("status", "fail")

            if (status != "success") {
                val msg = json.optString("message", "unknown error")
                AppLogger.w("IpLocator", "API returned fail: " + msg)
                return IpInfo(
                    ip = ip, country = "", region = "",
                    city = "", isp = "", org = "",
                    asNumber = "", error = msg
                )
            }

            val info = IpInfo(
                ip = json.optString("query", ip),
                country = json.optString("country", ""),
                region = json.optString("regionName", ""),
                city = json.optString("city", ""),
                isp = json.optString("isp", ""),
                org = json.optString("org", ""),
                asNumber = json.optString("as", "")
            )
            AppLogger.i("IpLocator", "Result: " + info.oneLine())
            info
        } catch (e: Exception) {
            AppLogger.e("IpLocator", "Lookup failed: " + e.message)
            IpInfo(
                ip = ip, country = "", region = "",
                city = "", isp = "", org = "",
                asNumber = "", error = e.message ?: "Unknown error"
            )
        }
    }
}