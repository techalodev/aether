package dev.aether.vpn.core

import dev.aether.vpn.data.Profile
import dev.aether.vpn.data.RuleAction
import dev.aether.vpn.data.Settings
import org.json.JSONArray
import org.json.JSONObject

/** Builds Xray-core JSON configs for the TUN service and for latency tests. */
object XrayConfig {

    const val TAG_PROXY = "proxy"
    private const val TAG_DIRECT = "direct"
    private const val TAG_BLOCK = "block"
    private const val TAG_DNS = "dns-out"
    private const val TAG_TUN = "tun"
    const val MTU = 1500

    val PRIVATE_CIDRS = listOf(
        "10.0.0.0/8", "100.64.0.0/10", "127.0.0.0/8", "169.254.0.0/16",
        "172.16.0.0/12", "192.168.0.0/16", "224.0.0.0/4", "fc00::/7", "fe80::/10",
    )

    /** .ru / .su / .рф (punycode) + Russian services and IPs → direct. */
    val RU_PRESET = listOf("domain:ru", "domain:su", "domain:xn--p1ai", "geosite:category-ru", "geoip:ru")

    /** Full config: TUN inbound → proxy, with DNS hijack, LAN bypass and routing rules. */
    fun forTun(profile: Profile, settings: Settings, geo: GeoData.Categories): String {
        val json = isJson(profile.link)
        val root = if (json) JSONObject(profile.link).also { sanitizeGeo(it, geo) } else baseConfig(profile)

        root.put("log", JSONObject().put("loglevel", "warning"))
        root.put("stats", JSONObject())
        root.put(
            "policy", JSONObject()
                .put("levels", JSONObject().put("8", JSONObject()
                    .put("handshake", 4).put("connIdle", 300)
                    .put("uplinkOnly", 1).put("downlinkOnly", 1)))
                .put("system", JSONObject()
                    .put("statsOutboundUplink", true)
                    .put("statsOutboundDownlink", true))
        )
        root.put("inbounds", JSONArray().put(tunInbound()))

        val outbounds = root.optJSONArray("outbounds") ?: JSONArray().also { root.put("outbounds", it) }
        if (outbounds.objects().none { it.optString("tag") == TAG_DNS }) {
            outbounds.put(JSONObject().put("tag", TAG_DNS).put("protocol", "dns"))
        }
        if (outbounds.objects().none { it.optString("tag") == TAG_DIRECT }) {
            outbounds.put(JSONObject().put("tag", TAG_DIRECT).put("protocol", "freedom"))
        }
        if (outbounds.objects().none { it.optString("tag") == TAG_BLOCK }) {
            outbounds.put(JSONObject().put("tag", TAG_BLOCK).put("protocol", "blackhole"))
        }
        // Rules pointing at "proxy" must hit the profile's main outbound, whatever its tag is
        val proxyTag = outbounds.objects().firstOrNull()?.optString("tag")?.ifBlank { null } ?: TAG_PROXY

        if (!json || !root.has("dns")) {
            root.put(
                "dns", JSONObject()
                    .put("servers", JSONArray(settings.remoteDns.split(',', ' ').map { it.trim() }.filter { it.isNotEmpty() }))
                    .put("queryStrategy", "UseIPv4")
            )
        }

        val routing = root.optJSONObject("routing") ?: JSONObject().put("domainStrategy", "AsIs")
        val rules = JSONArray()
        rules.put(JSONObject().put("inboundTag", JSONArray().put(TAG_TUN)).put("port", "53").put("outboundTag", TAG_DNS))
        if (settings.bypassLan) {
            rules.put(JSONObject().put("ip", JSONArray(PRIVATE_CIDRS)).put("outboundTag", TAG_DIRECT))
        }
        settings.rules.filter { it.enabled }.forEach { rule ->
            val tag = when (rule.action) {
                RuleAction.Proxy -> proxyTag
                RuleAction.Direct -> TAG_DIRECT
                RuleAction.Block -> TAG_BLOCK
            }
            ruleObjects(splitEntries(rule.values), tag, geo).forEach { rules.put(it) }
        }
        if (settings.ruDirect) {
            ruleObjects(RU_PRESET, TAG_DIRECT, geo).forEach { rules.put(it) }
        }
        routing.optJSONArray("rules")?.objects()?.forEach { rules.put(it) }
        routing.put("rules", rules)
        root.put("routing", routing)

        return root.toString()
    }

    /**
     * Config for a short-lived probe instance (latency test, exit-country lookup):
     * SOCKS on 127.0.0.1:[port] → the profile's outbound(s).
     */
    fun forProbe(profile: Profile, port: Int, geo: GeoData.Categories): String {
        val root = if (isJson(profile.link)) JSONObject(profile.link).also { sanitizeGeo(it, geo) } else baseConfig(profile)
        root.put("log", JSONObject().put("loglevel", "none"))
        root.remove("stats")
        root.remove("dns")
        root.put(
            "inbounds", JSONArray().put(
                JSONObject()
                    .put("tag", "probe")
                    .put("listen", "127.0.0.1")
                    .put("port", port)
                    .put("protocol", "socks")
                    .put("settings", JSONObject().put("auth", "noauth").put("udp", false))
            )
        )
        return root.toString()
    }

    fun isJson(link: String) = link.trimStart().startsWith("{")

    // ---- geo safety -----------------------------------------------------------------------------

    /** True if a geosite:/geoip:/ext: entry can be resolved with the installed files. */
    private fun geoAvailable(entry: String, geo: GeoData.Categories): Boolean {
        val v = entry.trim().lowercase()
        return when {
            v.startsWith("geosite:") -> v.removePrefix("geosite:").substringBefore('@') in geo.geosite
            v.startsWith("geoip:") -> v.removePrefix("geoip:").removePrefix("!") in geo.geoip
            v.startsWith("ext:") || v.startsWith("ext-domain:") || v.startsWith("ext-ip:") -> false
            else -> true
        }
    }

    /**
     * Provider JSON configs often reference geo categories. Xray refuses to start if one is
     * missing, so drop rules / DNS servers that would need unavailable categories.
     */
    private fun sanitizeGeo(root: JSONObject, geo: GeoData.Categories) {
        root.optJSONObject("routing")?.let { routing ->
            val kept = JSONArray()
            routing.optJSONArray("rules")?.objects()?.forEach { rule ->
                var drop = false
                for (key in listOf("domain", "domains", "ip", "source", "sourceIP")) {
                    val arr = rule.optJSONArray(key) ?: continue
                    val filtered = (0 until arr.length()).map { arr.optString(it) }.filter { geoAvailable(it, geo) }
                    if (filtered.isEmpty()) drop = true else rule.put(key, JSONArray(filtered))
                }
                if (!drop) kept.put(rule)
            }
            if (routing.has("rules")) routing.put("rules", kept)
        }
        root.optJSONObject("dns")?.let { dns ->
            dns.optJSONArray("servers")?.let { servers ->
                val kept = JSONArray()
                for (i in 0 until servers.length()) {
                    val server = servers.opt(i)
                    if (server !is JSONObject) { kept.put(server); continue }
                    val domains = server.optJSONArray("domains")
                    if (domains != null && domains.length() > 0) {
                        val filtered = (0 until domains.length()).map { domains.optString(it) }.filter { geoAvailable(it, geo) }
                        if (filtered.isEmpty()) continue
                        server.put("domains", JSONArray(filtered))
                    }
                    for (key in listOf("expectIPs", "expectedIPs", "unexpectedIPs")) {
                        val arr = server.optJSONArray(key) ?: continue
                        val filtered = (0 until arr.length()).map { arr.optString(it) }.filter { geoAvailable(it, geo) }
                        if (filtered.isEmpty()) server.remove(key) else server.put(key, JSONArray(filtered))
                    }
                    kept.put(server)
                }
                dns.put("servers", kept)
            }
            dns.optJSONObject("hosts")?.let { hosts ->
                hosts.keys().asSequence().toList().filterNot { geoAvailable(it, geo) }.forEach { hosts.remove(it) }
            }
        }
    }

    // ---- routing rules --------------------------------------------------------------------------

    private val DOMAIN_PREFIXES = listOf("domain:", "full:", "regexp:", "keyword:", "geosite:", "ext:", "dotless:")

    fun splitEntries(text: String): List<String> =
        text.split(',', '\n', ' ', ';').map { it.trim() }.filter { it.isNotEmpty() }

    private fun isIp(entry: String): Boolean =
        entry.startsWith("geoip:") ||
            entry.matches(Regex("^\\d{1,3}(\\.\\d{1,3}){3}(/\\d{1,2})?$")) ||
            (entry.contains(':') && entry.matches(Regex("^[0-9a-fA-F:]+(/\\d{1,3})?$")))

    private fun normalizeDomain(entry: String): String {
        val prefix = DOMAIN_PREFIXES.firstOrNull { entry.startsWith(it) }
        val (p, value) = if (prefix != null) prefix to entry.removePrefix(prefix) else "domain:" to entry
        if (p == "regexp:" || p == "geosite:" || p == "ext:") return p + value
        // Allow typing Cyrillic domains such as "рф" or "пример.рф"
        val ascii = runCatching { java.net.IDN.toASCII(value.trimStart('.')) }.getOrDefault(value)
        return p + ascii.lowercase()
    }

    /** Domain and IP matchers must be separate rules (a single rule ANDs its conditions). */
    private fun ruleObjects(entries: List<String>, tag: String, geo: GeoData.Categories): List<JSONObject> {
        val ips = entries.filter { isIp(it) }.filter { geoAvailable(it, geo) }
        val domains = entries.filterNot { isIp(it) }.map { normalizeDomain(it) }.filter { geoAvailable(it, geo) }
        return listOfNotNull(
            domains.takeIf { it.isNotEmpty() }?.let { JSONObject().put("domain", JSONArray(it)).put("outboundTag", tag) },
            ips.takeIf { it.isNotEmpty() }?.let { JSONObject().put("ip", JSONArray(it)).put("outboundTag", tag) },
        )
    }

    private fun baseConfig(profile: Profile): JSONObject {
        val node = LinkParser.parse(profile.link) ?: error("Unsupported config")
        return JSONObject()
            .put("outbounds", JSONArray()
                .put(outbound(node))
                .put(JSONObject().put("tag", TAG_DIRECT).put("protocol", "freedom"))
                .put(JSONObject().put("tag", TAG_BLOCK).put("protocol", "blackhole")))
            .put("routing", JSONObject().put("domainStrategy", "AsIs").put("rules", JSONArray()))
    }

    private fun tunInbound() = JSONObject()
        .put("tag", TAG_TUN)
        .put("protocol", "tun")
        .put("settings", JSONObject().put("name", "xray0").put("MTU", MTU).put("userLevel", 8))
        .put("sniffing", JSONObject()
            .put("enabled", true)
            .put("destOverride", JSONArray(listOf("http", "tls", "quic"))))

    // ---- outbound -------------------------------------------------------------------------------

    fun outbound(n: Node): JSONObject {
        val settings = JSONObject().put("address", n.address).put("port", n.port)
        val protocol = when (n.protocol) {
            Protocol.VLESS -> {
                settings.put("id", n.secret).put("encryption", n.method?.ifBlank { null } ?: "none").put("level", 8)
                n.flow?.ifBlank { null }?.let { settings.put("flow", it) }
                "vless"
            }
            Protocol.VMESS -> {
                settings.put("id", n.secret).put("security", n.method ?: "auto").put("level", 8)
                "vmess"
            }
            Protocol.TROJAN -> {
                settings.put("password", n.secret).put("level", 8)
                "trojan"
            }
            Protocol.SHADOWSOCKS -> {
                settings.put("method", n.method).put("password", n.secret).put("level", 8)
                "shadowsocks"
            }
            Protocol.HYSTERIA2 -> {
                settings.put("version", 2)
                "hysteria"
            }
        }
        val out = JSONObject().put("tag", TAG_PROXY).put("protocol", protocol).put("settings", settings)
        if (n.protocol != Protocol.SHADOWSOCKS || n.network != "tcp" || n.security != null) {
            out.put("streamSettings", streamSettings(n))
        }
        out.put("mux", JSONObject().put("enabled", false))
        return out
    }

    private fun streamSettings(n: Node): JSONObject {
        val s = JSONObject()
        var sniHint: String? = null
        val network = when (n.network) {
            "raw" -> "tcp"
            "splithttp" -> "xhttp"
            "http" -> "h2"
            else -> n.network
        }
        s.put("network", network)
        when (network) {
            "tcp" -> {
                val header = JSONObject()
                if (n.headerType == "http") {
                    header.put("type", "http").put(
                        "request", JSONObject()
                            .put("path", JSONArray(n.path.csv().ifEmpty { listOf("/") }))
                            .put("headers", JSONObject().put("Host", JSONArray(n.host.csv())))
                    )
                    sniHint = n.host.csv().firstOrNull()
                } else {
                    header.put("type", "none")
                    sniHint = n.host
                }
                s.put("tcpSettings", JSONObject().put("header", header))
            }
            "ws" -> {
                s.put("wsSettings", JSONObject().put("path", n.path ?: "/").put("host", n.host.orEmpty()))
                sniHint = n.host
            }
            "httpupgrade" -> {
                s.put("httpupgradeSettings", JSONObject().put("path", n.path ?: "/").put("host", n.host.orEmpty()))
                sniHint = n.host
            }
            "xhttp" -> {
                val x = JSONObject().put("path", n.path ?: "/").put("host", n.host.orEmpty())
                n.mode?.ifBlank { null }?.let { x.put("mode", it) }
                n.xhttpExtra?.let { runCatching { JSONObject(it) }.getOrNull() }?.let { x.put("extra", it) }
                s.put("xhttpSettings", x)
                sniHint = n.host
            }
            "h2" -> {
                s.put("httpSettings", JSONObject().put("host", JSONArray(n.host.csv())).put("path", n.path ?: "/"))
                sniHint = n.host.csv().firstOrNull()
            }
            "grpc" -> {
                s.put(
                    "grpcSettings", JSONObject()
                        .put("serviceName", n.serviceName.orEmpty())
                        .put("authority", n.authority.orEmpty())
                        .put("multiMode", n.mode == "multi")
                        .put("idle_timeout", 60)
                        .put("health_check_timeout", 20)
                )
                sniHint = n.authority
            }
            "kcp" -> {
                s.put("kcpSettings", JSONObject())
                val mask = JSONObject().put("type", "mkcp-legacy")
                n.seed?.ifBlank { null }?.let { mask.put("settings", JSONObject().put("value", it)) }
                s.put("finalmask", JSONObject().put("udp", JSONArray().put(mask)))
            }
            "hysteria" -> {
                s.put("hysteriaSettings", JSONObject().put("version", 2).put("auth", n.secret))
                val masks = JSONArray()
                n.obfsPassword?.ifBlank { null }?.let {
                    masks.put(JSONObject().put("type", "salamander").put("settings", JSONObject().put("password", it)))
                }
                n.portHopping?.let {
                    masks.put(JSONObject().put("type", "udphop").put("settings", JSONObject()
                        .put("mode", "intervallocal,intervalremote")
                        .put("interval", "30")
                        .put("remotePorts", it)))
                }
                if (masks.length() > 0) s.put("finalmask", JSONObject().put("udp", masks))
            }
        }
        // An explicit finalmask from the link wins over the generated one
        n.finalMask?.let { runCatching { JSONObject(it) }.getOrNull() }?.let { s.put("finalmask", it) }

        val security = n.security?.takeIf { it != "none" } ?: return s
        val sni = n.sni?.ifBlank { null }
            ?: sniHint?.takeIf { it.isDomain() }
            ?: n.address.takeIf { it.isDomain() }
        val tls = JSONObject()
        sni?.let { tls.put("serverName", it) }
        tls.put("fingerprint", n.fingerprint?.ifBlank { null } ?: "chrome")
        when (security) {
            "reality" -> {
                tls.put("publicKey", n.publicKey.orEmpty())
                n.shortId?.let { tls.put("shortId", it) }
                n.spiderX?.let { tls.put("spiderX", it) }
                n.mldsa65Verify?.ifBlank { null }?.let { tls.put("mldsa65Verify", it) }
                s.put("security", "reality").put("realitySettings", tls)
            }
            else -> {
                val alpn = if (network == "hysteria") listOf("h3") else n.alpn.csv()
                if (alpn.isNotEmpty()) tls.put("alpn", JSONArray(alpn))
                n.ech?.ifBlank { null }?.let { tls.put("echConfigList", it) }
                val pin = n.pinnedCertSha256?.ifBlank { null }
                if (pin != null) tls.put("pinnedPeerCertSha256", pin)
                tls.put("allowInsecure", n.insecure && pin == null)
                s.put("security", "tls").put("tlsSettings", tls)
            }
        }
        return s
    }

    private fun String?.csv(): List<String> =
        this.orEmpty().split(',').map { it.trim() }.filter { it.isNotEmpty() }

    private fun String.isDomain(): Boolean =
        isNotBlank() && !contains(':') && !matches(Regex("^\\d{1,3}(\\.\\d{1,3}){3}$"))

    private fun JSONArray.objects(): List<JSONObject> =
        (0 until length()).mapNotNull { optJSONObject(it) }
}
