package dev.aether.vpn.core

import dev.aether.vpn.data.Profile
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.withContext
import org.json.JSONArray
import org.json.JSONObject
import java.net.HttpURLConnection
import java.net.URL
import java.util.UUID

data class FetchResult(
    val profiles: List<Profile>,
    val title: String?,
    val upload: Long,
    val download: Long,
    val total: Long,
    val expire: Long,
    val supportUrl: String?,
    val description: String?,
    val updateIntervalHours: Int?,
)

object SubscriptionFetcher {

    suspend fun fetch(
        url: String,
        subId: String,
        userAgent: String,
        extraHeaders: Map<String, String>,
    ): FetchResult = withContext(Dispatchers.IO) {
        val conn = (URL(url).openConnection() as HttpURLConnection).apply {
            connectTimeout = 15_000
            readTimeout = 20_000
            instanceFollowRedirects = true
            setRequestProperty("User-Agent", userAgent)
            setRequestProperty("Accept", "*/*")
            extraHeaders.forEach { (k, v) -> setRequestProperty(k, v) }
        }
        try {
            val code = conn.responseCode
            if (code !in 200..299) error("HTTP $code")
            val body = conn.inputStream.use { it.readBytes().decodeToString() }

            val info = parseUserInfo(conn.getHeaderField("subscription-userinfo"))
            val title = conn.getHeaderField("profile-title")?.let(::decodeTitle)
                ?: conn.getHeaderField("content-disposition")
                    ?.substringAfter("filename=", "")?.trim('"', ' ')?.ifBlank { null }

            val profiles = parseBody(body, subId)
            if (profiles.isEmpty()) error("No supported configs")

            FetchResult(
                profiles = profiles,
                title = title,
                upload = info["upload"] ?: 0,
                download = info["download"] ?: 0,
                total = info["total"] ?: 0,
                expire = info["expire"] ?: 0,
                supportUrl = conn.getHeaderField("support-url"),
                description = conn.getHeaderField("announce")?.let(::decodeTitle),
                updateIntervalHours = conn.getHeaderField("profile-update-interval")?.trim()?.toIntOrNull(),
            )
        } finally {
            conn.disconnect()
        }
    }

    /** Parses share links (plain or base64), Xray JSON config(s) or a Clash/Mihomo YAML config. */
    fun parseBody(body: String, subId: String?): List<Profile> {
        val text = body.trim()
        if (text.startsWith("[") || text.startsWith("{")) {
            val configs = runCatching {
                if (text.startsWith("[")) JSONArray(text).let { a -> (0 until a.length()).map { a.getJSONObject(it) } }
                else listOf(JSONObject(text))
            }.getOrDefault(emptyList())
            return configs.mapNotNull { fromJson(it, subId) }
        }
        if (ClashParser.looksLikeClash(text)) {
            return ClashParser.parse(text).map { fromNode(it, LinkWriter.write(it), subId, null) }
        }
        return LinkParser.splitLinks(text).mapNotNull { fromLink(it, subId) }
    }

    fun fromLink(link: String, subId: String?): Profile? {
        val node = LinkParser.parse(link) ?: return null
        // Remnawave adds `serverDescription` (often base64) to links for some clients
        val description = Regex("[?&]serverDescription=([^&#]*)").find(link)?.groupValues?.get(1)
            ?.let { runCatching { java.net.URLDecoder.decode(it, "UTF-8") }.getOrDefault(it) }
            ?.let { raw -> LinkParser.decodeBase64(raw)?.takeIf { d -> d.all { !it.isISOControl() } } ?: raw }
        return fromNode(node, link.trim(), subId, description)
    }

    private fun fromNode(node: Node, link: String, subId: String?, description: String?) = Profile(
        id = UUID.randomUUID().toString(),
        subId = subId,
        name = node.name,
        link = link,
        protocol = node.protocol.label,
        address = node.address,
        port = node.port,
        transport = transportLabel(node.network),
        security = securityLabel(node.security),
        description = description?.trim()?.ifBlank { null },
    )

    private fun fromJson(config: JSONObject, subId: String?): Profile? {
        val outbounds = config.optJSONArray("outbounds") ?: return null
        val first = (0 until outbounds.length()).map { outbounds.getJSONObject(it) }
            .firstOrNull { it.optString("protocol") !in setOf("freedom", "blackhole", "dns") } ?: return null
        val s = first.optJSONObject("settings")
        val server = s?.optJSONArray("vnext")?.optJSONObject(0)
            ?: s?.optJSONArray("servers")?.optJSONObject(0)
            ?: s
        val address = server?.optString("address").orEmpty()
        val stream = first.optJSONObject("streamSettings")
        val protocol = first.optString("protocol").lowercase()
        return Profile(
            id = UUID.randomUUID().toString(),
            subId = subId,
            name = config.optString("remarks").ifBlank { address.ifBlank { "Config" } },
            link = config.toString(),
            protocol = Protocol.entries.firstOrNull { it.name.lowercase() == protocol || it.label.lowercase() == protocol }?.label
                ?: if (protocol == "hysteria") "Hysteria2" else protocol.replaceFirstChar { it.uppercase() },
            address = address,
            port = server?.optInt("port") ?: 0,
            transport = transportLabel(stream?.optString("network")?.ifBlank { null } ?: "tcp"),
            security = securityLabel(stream?.optString("security")),
            description = config.optJSONObject("meta")?.optString("serverDescription")?.trim()?.ifBlank { null },
        )
    }

    fun transportLabel(network: String?): String? = when (network?.lowercase()) {
        null, "" -> null
        "tcp", "raw" -> "TCP"
        "ws" -> "WS"
        "grpc" -> "gRPC"
        "xhttp", "splithttp" -> "XHTTP"
        "httpupgrade" -> "HTTPUpgrade"
        "kcp", "mkcp" -> "mKCP"
        "h2", "http" -> "H2"
        "hysteria" -> "QUIC"
        else -> network.uppercase()
    }

    fun securityLabel(security: String?): String? = when (security?.lowercase()) {
        "reality" -> "REALITY"
        "tls" -> "TLS"
        else -> null
    }

    private fun parseUserInfo(header: String?): Map<String, Long> =
        header.orEmpty().split(';').mapNotNull {
            val k = it.substringBefore('=').trim().lowercase()
            val v = it.substringAfter('=', "").trim().toDoubleOrNull()?.toLong()
            if (k.isNotEmpty() && v != null) k to v else null
        }.toMap()

    private fun decodeTitle(raw: String): String? {
        val t = raw.trim()
        return if (t.startsWith("base64:")) LinkParser.decodeBase64(t.removePrefix("base64:")) else t.ifBlank { null }
    }

    fun newId(): String = UUID.randomUUID().toString()
}
