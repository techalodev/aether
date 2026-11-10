package dev.aether.vpn.core

import org.snakeyaml.engine.v2.api.Load
import org.snakeyaml.engine.v2.api.LoadSettings

/** Reads the `proxies:` list of a Clash / Mihomo config into [Node]s. */
object ClashParser {

    fun looksLikeClash(text: String) = Regex("(?m)^proxies:\\s*$").containsMatchIn(text)

    fun parse(text: String): List<Node> {
        val settings = LoadSettings.builder()
            .setMaxAliasesForCollections(100_000)
            .setCodePointLimit(64 * 1024 * 1024)
            .build()
        val root = runCatching { Load(settings).loadFromString(escapeAstral(text)) as? Map<*, *> }.getOrNull() ?: return emptyList()
        val proxies = root["proxies"] as? List<*> ?: return emptyList()
        return proxies.mapNotNull { (it as? Map<*, *>)?.let(::toNode) }.map { it.copy(name = unescapeAstral(it.name)) }
    }

    // snakeyaml-engine's StreamReader can fail when a surrogate pair (emoji, flags) straddles its
    // internal buffer boundary. Encode astral code points as BMP private-use markers while parsing.
    private const val OPEN = ''
    private const val CLOSE = ''

    private fun escapeAstral(text: String): String {
        if (text.none { it.isSurrogate() }) return text
        val sb = StringBuilder(text.length)
        var i = 0
        while (i < text.length) {
            val cp = text.codePointAt(i)
            if (cp > 0xFFFF) sb.append(OPEN).append(cp.toString(16)).append(CLOSE) else sb.append(cp.toChar())
            i += Character.charCount(cp)
        }
        return sb.toString()
    }

    private fun unescapeAstral(text: String): String =
        Regex("$OPEN([0-9a-f]+)$CLOSE").replace(text) { m -> String(Character.toChars(m.groupValues[1].toInt(16))) }

    private fun toNode(p: Map<*, *>): Node? = runCatching {
        val type = p.str("type")?.lowercase() ?: return null
        val protocol = when (type) {
            "vless" -> Protocol.VLESS
            "vmess" -> Protocol.VMESS
            "trojan" -> Protocol.TROJAN
            "ss" -> Protocol.SHADOWSOCKS
            "hysteria2", "hy2" -> Protocol.HYSTERIA2
            else -> return null
        }
        val server = p.str("server") ?: return null
        val port = p.str("port")?.toIntOrNull() ?: return null
        val network = p.str("network")?.lowercase() ?: "tcp"
        val reality = p["reality-opts"] as? Map<*, *>
        val ws = p["ws-opts"] as? Map<*, *>
        val grpc = p["grpc-opts"] as? Map<*, *>
        val h2 = p["h2-opts"] as? Map<*, *>
        val tls = p.str("tls") == "true"
        val alpn = (p["alpn"] as? List<*>)?.joinToString(",")
        val sni = p.str("servername") ?: p.str("sni")
        val wsHost = (ws?.get("headers") as? Map<*, *>)?.str("Host") ?: (ws?.get("headers") as? Map<*, *>)?.str("host")

        Node(
            name = p.str("name")?.trim() ?: "$server:$port",
            protocol = protocol,
            address = server,
            port = port,
            secret = when (protocol) {
                Protocol.VLESS, Protocol.VMESS -> p.str("uuid")
                else -> p.str("password")
            } ?: return null,
            method = when (protocol) {
                Protocol.VMESS -> p.str("cipher") ?: "auto"
                Protocol.SHADOWSOCKS -> p.str("cipher")
                Protocol.VLESS -> p.str("encryption")?.ifBlank { null } ?: "none"
                else -> null
            },
            flow = p.str("flow"),
            network = if (protocol == Protocol.HYSTERIA2) "hysteria" else network,
            host = wsHost ?: (h2?.get("host") as? List<*>)?.firstOrNull()?.toString(),
            path = ws?.str("path") ?: h2?.str("path"),
            serviceName = grpc?.str("grpc-service-name"),
            security = when {
                protocol == Protocol.HYSTERIA2 -> "tls"
                reality != null -> "reality"
                tls || protocol == Protocol.TROJAN -> "tls"
                else -> null
            },
            sni = sni,
            fingerprint = p.str("client-fingerprint"),
            alpn = alpn,
            publicKey = reality?.str("public-key"),
            shortId = reality?.str("short-id"),
            insecure = p.str("skip-cert-verify") == "true",
            obfsPassword = if (p.str("obfs") == "salamander") p.str("obfs-password") else null,
            portHopping = p.str("ports"),
        )
    }.getOrNull()

    private fun Map<*, *>.str(key: String): String? = this[key]?.toString()?.takeIf { it.isNotBlank() }
}
