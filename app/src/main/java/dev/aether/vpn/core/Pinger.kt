package dev.aether.vpn.core

import android.content.Context
import android.os.SystemClock
import dev.aether.vpn.data.Profile
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.withContext
import libv2ray.CoreCallbackHandler
import libv2ray.CoreController
import libv2ray.Libv2ray
import org.json.JSONObject
import java.net.HttpURLConnection
import java.net.InetSocketAddress
import java.net.Proxy
import java.net.ServerSocket
import java.net.URL
import java.util.Locale

/**
 * libv2ray passes the TUN fd through a process-wide environment variable inside startLoop,
 * so every core start must be serialised.
 */
object CoreLock

private object NoopCallback : CoreCallbackHandler {
    override fun startup(): Long = 0
    override fun shutdown(): Long = 0
    override fun onEmitStatus(p0: Long, p1: String?): Long = 0
}

fun startCore(core: CoreController, config: String, tunFd: Int) {
    synchronized(CoreLock) { core.startLoop(config, tunFd) }
}

data class Country(val code: String, val name: String) {
    val flag: String
        get() = code.uppercase().takeIf { it.length == 2 && it.all { c -> c in 'A'..'Z' } }
            ?.map { String(Character.toChars(0x1F1E6 + (it - 'A'))) }?.joinToString("") ?: ""
}

/** Runs requests through a short-lived core instance with a local SOCKS inbound. */
object ProxyProbe {

    private suspend fun <T> withProxy(context: Context, profile: Profile, block: (Proxy) -> T): T =
        withContext(Dispatchers.IO) {
            val port = ServerSocket(0).use { it.localPort }
            val config = XrayConfig.forProbe(profile, port, GeoData.categories(context))
            val core = Libv2ray.newCoreController(NoopCallback)
            startCore(core, config, 0)
            try {
                block(Proxy(Proxy.Type.SOCKS, InetSocketAddress("127.0.0.1", port)))
            } finally {
                runCatching { core.stopLoop() }
            }
        }

    private fun open(url: String, proxy: Proxy, timeout: Int = 10_000) =
        (URL(url).openConnection(proxy) as HttpURLConnection).apply {
            connectTimeout = timeout
            readTimeout = timeout
            useCaches = false
            instanceFollowRedirects = false
            setRequestProperty("Connection", "close")
        }

    /**
     * Proxy GET: time from opening a fresh connection through the proxy until the response
     * headers arrive — includes the proxy handshake and TLS, like real browsing. -1 on failure.
     */
    suspend fun ping(context: Context, profile: Profile, url: String): Int = runCatching {
        withProxy(context, profile) { proxy ->
            val start = SystemClock.elapsedRealtime()
            val conn = open(url, proxy)
            try {
                val code = conn.responseCode
                if (code !in 200..399) error("HTTP $code")
                (SystemClock.elapsedRealtime() - start).toInt().coerceAtLeast(1)
            } finally {
                conn.disconnect()
            }
        }
    }.getOrDefault(-1)

    /** Exit country as seen by the internet through this profile. */
    suspend fun country(context: Context, profile: Profile): Country? = runCatching {
        withProxy(context, profile) { proxy ->
            sequenceOf(
                { lookup(proxy, "https://ipwho.is/?fields=country,country_code", "country_code", "country") },
                { lookup(proxy, "http://ip-api.com/json/?fields=countryCode,country", "countryCode", "country") },
            ).firstNotNullOfOrNull { runCatching(it).getOrNull() }
        }
    }.getOrNull()

    private fun lookup(proxy: Proxy, url: String, codeKey: String, nameKey: String): Country? {
        val conn = open(url, proxy, 8_000)
        try {
            if (conn.responseCode != 200) return null
            val json = JSONObject(conn.inputStream.use { it.readBytes().decodeToString() })
            val code = json.optString(codeKey).uppercase().takeIf { it.length == 2 } ?: return null
            val name = Locale("", code).getDisplayCountry(Locale.ENGLISH).ifBlank { json.optString(nameKey) }
            return Country(code, name)
        } finally {
            conn.disconnect()
        }
    }
}
