package com.netmonitor.app.util

import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.withContext
import java.net.InetAddress
import java.util.concurrent.ConcurrentHashMap

object IpResolver {

    private val cache = ConcurrentHashMap<String, String>()

    suspend fun resolve(ip: String): String =
        withContext(Dispatchers.IO) {
            if (ip == "0.0.0.0" || ip == "127.0.0.1"
                || ip == "::1" || ip == "::0"
            ) return@withContext "localhost"

            cache[ip]?.let { return@withContext it }

            try {
                val addr = InetAddress.getByName(ip)
                val hostname = addr.canonicalHostName
                if (hostname != ip) {
                    cache[ip] = hostname
                    hostname
                } else {
                    ip
                }
            } catch (_: Exception) {
                ip
            }
        }

    fun getCached(ip: String): String? = cache[ip]

    fun clearCache() = cache.clear()
}