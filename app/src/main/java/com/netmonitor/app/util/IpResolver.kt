package com.netmonitor.app.util

import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.withContext
import java.net.InetAddress
import java.util.concurrent.ConcurrentHashMap

object IpResolver {

    private const val MAX_CACHE_SIZE = 2000      // ← 新增
    private val cache = ConcurrentHashMap<String, String>()

    suspend fun resolve(ip: String): String =
        withContext(Dispatchers.IO) {
            if (ip == "0.0.0.0" || ip == "127.0.0.1" ||
                ip == "::1" || ip == "::0"
            ) return@withContext "localhost"

            cache[ip]?.let { return@withContext it }

            try {
                val addr = InetAddress.getByName(ip)
                val hostname = addr.canonicalHostName
                if (hostname != ip) {
                    trimCacheIfNeeded()          // ← 新增
                    cache[ip] = hostname
                    hostname
                } else {
                    ip
                }
            } catch (_: Exception) {
                ip
            }
        }

    // ← 新增：缓存淘汰
    private fun trimCacheIfNeeded() {
        if (cache.size >= MAX_CACHE_SIZE) {
            val keysToRemove = cache.keys()
                .toList()
                .take(MAX_CACHE_SIZE / 4)
            keysToRemove.forEach { cache.remove(it) }
        }
    }

    fun getCached(ip: String): String? = cache[ip]

    fun clearCache() = cache.clear()
}
