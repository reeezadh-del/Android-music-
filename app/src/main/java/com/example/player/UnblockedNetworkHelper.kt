package com.example.player

import java.net.InetAddress
import java.net.UnknownHostException
import java.util.concurrent.ConcurrentHashMap
import java.util.concurrent.TimeUnit
import kotlinx.serialization.json.Json
import kotlinx.serialization.json.JsonArray
import kotlinx.serialization.json.JsonObject
import kotlinx.serialization.json.contentOrNull
import kotlinx.serialization.json.intOrNull
import kotlinx.serialization.json.jsonObject
import kotlinx.serialization.json.jsonPrimitive
import okhttp3.Dns
import okhttp3.OkHttpClient
import okhttp3.Request

object UnblockedNetworkHelper {

    private val jsonParser = Json { ignoreUnknownKeys = true }

    private val DOH_ENDPOINTS = listOf(
        "https://1.1.1.1/dns-query",
        "https://dns.quad9.net:5053/dns-query",
        "https://dns.google/resolve"
    )

    private val DIRECT_YOUTUBE_HOSTS = listOf(
        "youtube.com",
        "youtu.be",
        "googlevideo.com",
        "ytimg.com",
        "ggpht.com",
        "googleusercontent.com"
    )

    fun isSinkholeIp(hostAddress: String): Boolean {
        val clean = hostAddress.trim()
        // Iranian telecom/filtering DNS poisoning returns 10.10.34.* / 10.10.35.*
        if (clean.startsWith("10.10.34.") || clean.startsWith("10.10.35.")) return true
        // Standard private non-routable sinkhole used on some local ISPs when resolving public domains
        if (clean == "0.0.0.0" || clean == "::") return true
        return false
    }

    fun isDirectYoutubeUrl(url: String): Boolean {
        val lower = url.lowercase()
        return DIRECT_YOUTUBE_HOSTS.any { host -> lower.contains(host) }
    }

    fun isProxiedNonGoogleVideoUrl(url: String): Boolean {
        val clean = url.trim()
        if (!clean.startsWith("http", ignoreCase = true)) return false
        return !clean.contains("googlevideo.com", ignoreCase = true)
    }

    fun parseDohJsonAddresses(rawJson: String): List<String> = runCatching {
        val root = jsonParser.parseToJsonElement(rawJson).jsonObject
        val answers = root["Answer"] as? JsonArray ?: return@runCatching emptyList()
        answers.mapNotNull { el ->
            val obj = el as? JsonObject ?: return@mapNotNull null
            val type = obj["type"]?.jsonPrimitive?.intOrNull ?: 0
            val data = obj["data"]?.jsonPrimitive?.contentOrNull?.trim().orEmpty()
            if (type == 1 && data.isNotBlank() && !isSinkholeIp(data)) {
                data
            } else {
                null
            }
        }
    }.getOrDefault(emptyList())

    fun createUnblockedOkHttpClient(
        connectTimeoutSec: Long = 6L,
        readTimeoutSec: Long = 12L
    ): OkHttpClient {
        return OkHttpClient.Builder()
            .connectTimeout(connectTimeoutSec, TimeUnit.SECONDS)
            .readTimeout(readTimeoutSec, TimeUnit.SECONDS)
            .followRedirects(true)
            .followSslRedirects(true)
            .dns(SmartIranBypassDns())
            .build()
    }

    private class SmartIranBypassDns : Dns {
        private data class CachedEntry(
            val addresses: List<InetAddress>,
            val expiresAtMs: Long
        )

        private val cache = ConcurrentHashMap<String, CachedEntry>()

        private val dohClient by lazy {
            OkHttpClient.Builder()
                .connectTimeout(2500, TimeUnit.MILLISECONDS)
                .readTimeout(2500, TimeUnit.MILLISECONDS)
                .build()
        }

        override fun lookup(hostname: String): List<InetAddress> {
            val lower = hostname.lowercase()
            if (lower == "localhost" || lower == "127.0.0.1" || lower == "1.1.1.1" || lower == "8.8.8.8") {
                return Dns.SYSTEM.lookup(hostname)
            }

            val now = System.currentTimeMillis()
            val cached = cache[lower]
            if (cached != null && cached.expiresAtMs > now && cached.addresses.isNotEmpty()) {
                return cached.addresses
            }

            val systemAddrs = runCatching { Dns.SYSTEM.lookup(hostname) }.getOrNull().orEmpty()
            val cleanSystemAddrs = systemAddrs.filterNot { addr ->
                val ip = addr.hostAddress.orEmpty()
                isSinkholeIp(ip)
            }

            if (cleanSystemAddrs.isNotEmpty()) {
                cache[lower] = CachedEntry(cleanSystemAddrs, now + CACHE_TTL_MS)
                return cleanSystemAddrs
            }

            // System DNS either failed or returned an Iranian 10.10.34.x filtering sinkhole IP -> query DoH
            val dohAddrs = resolveViaDoh(hostname)
            if (dohAddrs.isNotEmpty()) {
                cache[lower] = CachedEntry(dohAddrs, now + CACHE_TTL_MS)
                return dohAddrs
            }

            if (systemAddrs.isNotEmpty()) {
                // Reject 10.10.34.x sinkhole quickly so blocked hosts fail immediately instead of hanging TCP
                throw UnknownHostException("Blocked DNS sinkhole detected for $hostname")
            }

            throw UnknownHostException("Unable to resolve host $hostname")
        }

        private fun resolveViaDoh(hostname: String): List<InetAddress> {
            for (endpoint in DOH_ENDPOINTS) {
                val url = "$endpoint?name=$hostname&type=A"
                val rawJson = runCatching {
                    val req = Request.Builder()
                        .url(url)
                        .header("Accept", "application/dns-json")
                        .build()
                    dohClient.newCall(req).execute().use { res ->
                        if (!res.isSuccessful) return@use null
                        res.body?.string()
                    }
                }.getOrNull() ?: continue

                val ips = parseDohJsonAddresses(rawJson)
                if (ips.isNotEmpty()) {
                    return ips.mapNotNull { ip -> runCatching { InetAddress.getByName(ip) }.getOrNull() }
                }
            }
            return emptyList()
        }

        companion object {
            private const val CACHE_TTL_MS = 10 * 60 * 1000L
        }
    }
}
