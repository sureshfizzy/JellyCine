package com.jellycine.data.network

import okhttp3.Dns
import okhttp3.OkHttpClient
import okhttp3.Request
import org.json.JSONObject
import java.net.InetAddress
import java.util.concurrent.ConcurrentHashMap
import java.util.concurrent.TimeUnit

/**
 * Resolves hostnames over DNS-over-HTTPS (Cloudflare, by IP so no bootstrap lookup is needed),
 * falling back to the system resolver. Some ISPs (e.g. Jio in India) poison system DNS for
 * api.themoviedb.org, which makes requests hang until they time out.
 */
internal class DohDns(
    private val fallback: Dns = Dns.SYSTEM
) : Dns {
    private data class Entry(val addresses: List<InetAddress>, val expiresAtMs: Long)

    private val cache = ConcurrentHashMap<String, Entry>()
    private val client = OkHttpClient.Builder()
        .connectTimeout(4, TimeUnit.SECONDS)
        .readTimeout(4, TimeUnit.SECONDS)
        .callTimeout(6, TimeUnit.SECONDS)
        .build()

    override fun lookup(hostname: String): List<InetAddress> {
        cache[hostname]?.takeIf { it.expiresAtMs > System.currentTimeMillis() }?.let { return it.addresses }
        val resolved = runCatching { resolveOverHttps(hostname) }.getOrNull().orEmpty()
        if (resolved.isNotEmpty()) {
            cache[hostname] = Entry(resolved, System.currentTimeMillis() + CACHE_TTL_MS)
            return resolved
        }
        return fallback.lookup(hostname)
    }

    private fun resolveOverHttps(hostname: String): List<InetAddress> {
        val request = Request.Builder()
            .url("https://1.1.1.1/dns-query?name=$hostname&type=A")
            .header("Accept", "application/dns-json")
            .build()
        client.newCall(request).execute().use { response ->
            if (!response.isSuccessful) return emptyList()
            val answers = JSONObject(response.body?.string().orEmpty()).optJSONArray("Answer")
                ?: return emptyList()
            return (0 until answers.length())
                .map { answers.getJSONObject(it) }
                .filter { it.optInt("type") == TYPE_A }
                .map { InetAddress.getByName(it.getString("data")) }
        }
    }

    private companion object {
        const val TYPE_A = 1
        const val CACHE_TTL_MS = 10 * 60 * 1000L
    }
}