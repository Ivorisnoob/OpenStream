package com.ivor.openstream.data.settings

import okhttp3.Dns
import okhttp3.HttpUrl.Companion.toHttpUrl
import okhttp3.OkHttpClient
import okhttp3.dnsoverhttps.DnsOverHttps
import java.io.IOException
import java.net.InetAddress
import java.util.concurrent.ConcurrentHashMap
import java.util.concurrent.TimeUnit
import javax.inject.Inject
import javax.inject.Singleton

/**
 * The resolver every OkHttp client in the app uses. It follows the DNS setting on each lookup, so
 * a change applies to new connections straight away. If the DNS-over-HTTPS server can't be
 * reached, it falls back to the network's DNS rather than failing the request.
 */
@Singleton
class AppDns @Inject constructor(
    private val settings: AppSettingsStore
) : Dns {

    private val bootstrapClient = OkHttpClient.Builder()
        .connectTimeout(10, TimeUnit.SECONDS)
        .readTimeout(10, TimeUnit.SECONDS)
        .build()

    private val resolvers = ConcurrentHashMap<DnsProvider, Dns>()
    private val cache = ConcurrentHashMap<String, CachedAnswer>()

    override fun lookup(hostname: String): List<InetAddress> {
        val provider = settings.current.dnsProvider
        val resolver = resolverFor(provider) ?: return Dns.SYSTEM.lookup(hostname)

        val key = "${provider.name}:$hostname"
        val now = System.currentTimeMillis()
        cache[key]?.takeIf { it.expiresAtMs > now }?.let { return it.addresses }

        return try {
            resolver.lookup(hostname).also { cache[key] = CachedAnswer(it, now + CACHE_TTL_MS) }
        } catch (e: IOException) {
            Dns.SYSTEM.lookup(hostname)
        }
    }

    private fun resolverFor(provider: DnsProvider): Dns? {
        val url = provider.url ?: return null
        return resolvers.getOrPut(provider) {
            DnsOverHttps.Builder()
                .client(bootstrapClient)
                .url(url.toHttpUrl())
                .bootstrapDnsHosts(provider.bootstrapHosts.map(InetAddress::getByName))
                // OkHttp 4 tries addresses one by one, so an unreachable IPv6 route would stall
                // every connection until it timed out.
                .includeIPv6(false)
                .build()
        }
    }

    private class CachedAnswer(val addresses: List<InetAddress>, val expiresAtMs: Long)

    private companion object {
        const val CACHE_TTL_MS = 5 * 60_000L
    }
}
