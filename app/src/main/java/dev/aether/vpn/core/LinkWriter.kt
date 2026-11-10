package dev.aether.vpn.core

import org.json.JSONObject
import java.net.URLEncoder
import java.util.Base64

/** Serialises a [Node] back into a standard share link (inverse of [LinkParser]). */
object LinkWriter {

    fun write(n: Node): String = when (n.protocol) {
        Protocol.VMESS -> vmess(n)
        Protocol.SHADOWSOCKS -> shadowsocks(n)
        else -> standard(n)
    }

    private fun standard(n: Node): String {
        val scheme = when (n.protocol) {
            Protocol.VLESS -> "vless"
            Protocol.TROJAN -> "trojan"
            else -> "hysteria2"
        }
        val q = linkedMapOf<String, String?>()
        when (n.protocol) {
            Protocol.VLESS -> {
                q["encryption"] = n.method ?: "none"
                q["flow"] = n.flow
            }
            Protocol.TROJAN -> q["flow"] = n.flow
            else -> Unit
        }
        if (n.protocol != Protocol.HYSTERIA2) {
            q["type"] = n.network
            q["headerType"] = n.headerType
            q["host"] = n.host
            q["path"] = n.path
            q["serviceName"] = n.serviceName
            q["authority"] = n.authority
            q["mode"] = n.mode
            q["extra"] = n.xhttpExtra
            q["seed"] = n.seed
            q["security"] = n.security ?: "none"
        } else {
            q["obfs"] = n.obfsPassword?.let { "salamander" }
            q["obfs-password"] = n.obfsPassword
            q["mport"] = n.portHopping
        }
        q["sni"] = n.sni
        q["fp"] = n.fingerprint
        q["alpn"] = n.alpn
        q["pbk"] = n.publicKey
        q["sid"] = n.shortId
        q["spx"] = n.spiderX
        q["pqv"] = n.mldsa65Verify
        q["ech"] = n.ech
        q["pcs"] = n.pinnedCertSha256
        if (n.insecure) q["insecure"] = "1"
        q["fm"] = n.finalMask
        val query = q.filterValues { !it.isNullOrBlank() }
            .entries.joinToString("&") { (k, v) -> "$k=${enc(v!!)}" }
        return "$scheme://${enc(n.secret)}@${host(n.address)}:${n.port}" +
            (if (query.isNotEmpty()) "?$query" else "") + "#" + enc(n.name)
    }

    private fun vmess(n: Node): String {
        val json = JSONObject()
            .put("v", "2").put("ps", n.name).put("add", n.address).put("port", n.port.toString())
            .put("id", n.secret).put("aid", "0").put("scy", n.method ?: "auto")
            .put("net", n.network).put("type", n.headerType ?: "none")
            .put("host", n.host.orEmpty())
            .put("path", (if (n.network == "grpc") n.serviceName else n.path).orEmpty())
            .put("tls", n.security.orEmpty()).put("sni", n.sni.orEmpty())
            .put("alpn", n.alpn.orEmpty()).put("fp", n.fingerprint.orEmpty())
        return "vmess://" + Base64.getEncoder().encodeToString(json.toString().toByteArray())
    }

    private fun shadowsocks(n: Node): String {
        val user = Base64.getUrlEncoder().withoutPadding()
            .encodeToString("${n.method}:${n.secret}".toByteArray())
        return "ss://$user@${host(n.address)}:${n.port}#${enc(n.name)}"
    }

    private fun host(address: String) = if (address.contains(':')) "[$address]" else address

    private fun enc(s: String) = URLEncoder.encode(s, "UTF-8").replace("+", "%20")
}
