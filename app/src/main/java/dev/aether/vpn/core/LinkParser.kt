package dev.aether.vpn.core

import org.json.JSONObject
import java.net.URLDecoder
import java.util.Base64

/** Normalised proxy description parsed from a share link. */
data class Node(
    val name: String,
    val protocol: Protocol,
    val address: String,
    val port: Int,
    /** UUID (vless/vmess) or password (trojan/ss/hysteria2). */
    val secret: String,
    /** vless encryption, vmess security, or shadowsocks method. */
    val method: String? = null,
    val flow: String? = null,
    val network: String = "tcp",
    val headerType: String? = null,
    val host: String? = null,
    val path: String? = null,
    val serviceName: String? = null,
    val authority: String? = null,
    val mode: String? = null,
    val xhttpExtra: String? = null,
    val seed: String? = null,
    val security: String? = null,
    val sni: String? = null,
    val fingerprint: String? = null,
    val alpn: String? = null,
    val publicKey: String? = null,
    val shortId: String? = null,
    val spiderX: String? = null,
    val mldsa65Verify: String? = null,
    val ech: String? = null,
    val pinnedCertSha256: String? = null,
    val insecure: Boolean = false,
    val obfsPassword: String? = null,
    val portHopping: String? = null,
    /** Raw Xray `finalmask` JSON (`fm` link parameter). */
    val finalMask: String? = null,
)

enum class Protocol(val label: String) {
    VLESS("VLESS"), VMESS("VMess"), TROJAN("Trojan"), SHADOWSOCKS("Shadowsocks"), HYSTERIA2("Hysteria2")
}

object LinkParser {

    fun parse(raw: String): Node? = runCatching {
        val link = raw.trim()
        when (link.substringBefore("://").lowercase()) {
            "vless" -> parseStandard(link, Protocol.VLESS)
            "trojan" -> parseStandard(link, Protocol.TROJAN)
            "hysteria2", "hy2" -> parseStandard(link, Protocol.HYSTERIA2)
            "vmess" -> parseVmess(link)
            "ss" -> parseShadowsocks(link)
            else -> null
        }
    }.getOrNull()

    /** Splits a text blob (possibly base64) into individual links. */
    fun splitLinks(text: String): List<String> {
        val body = text.trim()
        val decoded = if (!body.contains("://")) decodeBase64(body) ?: body else body
        return decoded.lineSequence().map { it.trim() }.filter { it.contains("://") }.toList()
    }

    // ---- scheme://userinfo@host:port?query#name -------------------------------------------------

    private class Parts(
        val user: String,
        val host: String,
        val port: Int,
        val query: Map<String, String>,
        val name: String,
    )

    private fun split(link: String): Parts {
        var rest = link.substringAfter("://")
        val name = if (rest.contains('#')) decode(rest.substringAfter('#')) else ""
        rest = rest.substringBefore('#')
        val query = if (rest.contains('?')) parseQuery(rest.substringAfter('?')) else emptyMap()
        rest = rest.substringBefore('?').trimEnd('/')
        val user = if (rest.contains('@')) decode(rest.substringBeforeLast('@')) else ""
        val hostPort = rest.substringAfterLast('@')
        val (host, port) = splitHostPort(hostPort)
        return Parts(user, host, port, query, name)
    }

    private fun splitHostPort(hostPort: String): Pair<String, Int> {
        return if (hostPort.startsWith("[")) {
            val host = hostPort.substring(1, hostPort.indexOf(']'))
            val port = hostPort.substringAfter("]:", "").takeWhile { it.isDigit() }.toIntOrNull() ?: 443
            host to port
        } else {
            val host = hostPort.substringBeforeLast(':')
            // Hysteria2 may carry a port range ("443,5000-6000"); the first port is the main one
            val port = hostPort.substringAfterLast(':', "").takeWhile { it.isDigit() }.toIntOrNull() ?: 443
            host to port
        }
    }

    private fun parseStandard(link: String, protocol: Protocol): Node? {
        val p = split(link)
        if (p.host.isBlank() || p.user.isBlank()) return null
        val q = p.query
        val network = (q["type"] ?: q["net"])?.lowercase()?.ifBlank { null } ?: "tcp"
        val security = when (protocol) {
            Protocol.TROJAN -> q["security"]?.ifBlank { null } ?: "tls"
            Protocol.HYSTERIA2 -> "tls"
            else -> q["security"]?.ifBlank { null }?.takeIf { it != "none" }
        }
        // hysteria2 port hopping: explicit mport, or a range in the authority part
        val hopping = q["mport"] ?: link.substringAfter("://").substringBefore('?').substringBefore('#')
            .substringAfterLast(':', "").takeIf { it.contains(',') || it.contains('-') }
        return Node(
            name = p.name.ifBlank { "${p.host}:${p.port}" },
            protocol = protocol,
            address = p.host,
            port = p.port,
            secret = p.user,
            method = q["encryption"],
            flow = q["flow"],
            network = if (protocol == Protocol.HYSTERIA2) "hysteria" else network,
            headerType = q["headerType"],
            host = q["host"],
            path = q["path"],
            serviceName = q["serviceName"],
            authority = q["authority"],
            mode = q["mode"],
            xhttpExtra = q["extra"],
            seed = q["seed"],
            security = security,
            sni = q["sni"] ?: q["peer"],
            fingerprint = q["fp"],
            alpn = q["alpn"],
            publicKey = q["pbk"],
            shortId = q["sid"],
            spiderX = q["spx"],
            mldsa65Verify = q["pqv"],
            ech = q["ech"],
            pinnedCertSha256 = q["pcs"] ?: q["pinSHA256"],
            insecure = q["allowInsecure"].isTruthy() || q["insecure"].isTruthy(),
            obfsPassword = if (q["obfs"] == "salamander") q["obfs-password"] else null,
            portHopping = hopping?.ifBlank { null },
            finalMask = q["fm"]?.ifBlank { null },
        )
    }

    // ---- vmess://base64(json) -------------------------------------------------------------------

    private fun parseVmess(link: String): Node? {
        val payload = link.substringAfter("://").substringBefore('#')
        val json = JSONObject(decodeBase64(payload) ?: return null)
        val address = json.optString("add").ifBlank { return null }
        val network = json.optString("net").ifBlank { "tcp" }.lowercase()
        val tls = json.optString("tls").lowercase().takeIf { it == "tls" || it == "reality" }
        val path = json.optString("path").ifBlank { null }
        return Node(
            name = json.optString("ps").ifBlank { "$address:${json.optString("port")}" },
            protocol = Protocol.VMESS,
            address = address,
            port = json.optString("port").toIntOrNull() ?: 443,
            secret = json.optString("id"),
            method = json.optString("scy").ifBlank { "auto" },
            network = network,
            headerType = json.optString("type").ifBlank { null },
            host = json.optString("host").ifBlank { null },
            path = path,
            serviceName = if (network == "grpc") path else null,
            mode = json.optString("mode").ifBlank { null },
            security = tls,
            sni = json.optString("sni").ifBlank { null },
            fingerprint = json.optString("fp").ifBlank { null },
            alpn = json.optString("alpn").ifBlank { null },
            insecure = json.optString("allowInsecure").isTruthy(),
        )
    }

    // ---- ss://base64(method:pass)@host:port#name  |  ss://base64(method:pass@host:port)#name -----

    private fun parseShadowsocks(link: String): Node? {
        var body = link.substringAfter("://")
        val name = if (body.contains('#')) decode(body.substringAfter('#')) else ""
        body = body.substringBefore('#').substringBefore('?').trimEnd('/')
        if (!body.contains('@')) body = decodeBase64(body) ?: return null
        val userInfo = body.substringBeforeLast('@')
        val creds = if (userInfo.contains(':')) decode(userInfo) else decodeBase64(decode(userInfo)) ?: return null
        val (host, port) = splitHostPort(body.substringAfterLast('@'))
        return Node(
            name = name.ifBlank { "$host:$port" },
            protocol = Protocol.SHADOWSOCKS,
            address = host,
            port = port,
            secret = creds.substringAfter(':'),
            method = creds.substringBefore(':'),
        )
    }

    // ---- helpers --------------------------------------------------------------------------------

    private fun parseQuery(query: String): Map<String, String> =
        query.split('&').filter { it.isNotBlank() }.associate {
            decode(it.substringBefore('=')) to decode(it.substringAfter('=', ""))
        }

    private fun decode(s: String): String =
        runCatching { URLDecoder.decode(s.replace("+", "%2B"), "UTF-8") }.getOrDefault(s)

    private fun String?.isTruthy() = this == "1" || this.equals("true", ignoreCase = true)

    fun decodeBase64(text: String): String? {
        val clean = text.trim().replace("\\s".toRegex(), "")
        if (clean.isEmpty()) return null
        val padded = clean.padEnd((clean.length + 3) / 4 * 4, '=')
        return runCatching { String(Base64.getDecoder().decode(padded)) }
            .recoverCatching { String(Base64.getUrlDecoder().decode(padded)) }
            .getOrNull()
    }
}
