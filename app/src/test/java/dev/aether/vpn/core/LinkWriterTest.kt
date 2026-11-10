package dev.aether.vpn.core

import dev.aether.vpn.data.Profile
import dev.aether.vpn.data.RoutingRule
import dev.aether.vpn.data.RuleAction
import dev.aether.vpn.data.Settings
import org.json.JSONObject
import org.junit.Assert.assertEquals
import org.junit.Assert.assertFalse
import org.junit.Assert.assertTrue
import org.junit.Assume.assumeTrue
import org.junit.Test
import java.io.File

class LinkWriterTest {

    private val samples = listOf(
        "vless://uuid@example.com:443?encryption=none&flow=xtls-rprx-vision&type=tcp&security=reality&sni=sni.example&fp=firefox&pbk=KEY&sid=ab#Reality",
        "vless://uuid@example.com:443?encryption=none&type=ws&host=h.example&path=%2Fws&security=tls&sni=h.example#WS",
        "trojan://secret@example.com:443?type=grpc&serviceName=svc&security=tls#Trojan",
        "hysteria2://pass@host.example:8444/?sni=host.example&fm=%7B%22quicParams%22%3A%7B%22congestion%22%3A%22bbr%22%7D%7D#Hy",
        "ss://YWVzLTI1Ni1nY206cGFzcw@1.2.3.4:8388#SS",
    )

    @Test
    fun roundTrip() {
        samples.forEach { link ->
            val node = LinkParser.parse(link)!!
            assertEquals(link, node, LinkParser.parse(LinkWriter.write(node)))
        }
    }

    @Test
    fun roundTripSubscriptionFile() {
        val path = System.getenv("AETHER_SUB_FILE")
        assumeTrue(path != null && File(path).exists() && !File(path).readText().trimStart().startsWith("["))
        LinkParser.splitLinks(File(path!!).readText()).forEach { link ->
            val node = LinkParser.parse(link)!!
            assertEquals(node, LinkParser.parse(LinkWriter.write(node)))
        }
    }

    private fun profile(link: String) = Profile("id", null, "n", link, "VLESS", "example.com", 443)

    @Test
    fun ruPresetSkipsGeoRulesWithoutFiles() {
        val cfg = JSONObject(XrayConfig.forTun(profile(samples[0]), Settings(ruDirect = true), GeoData.Categories()))
        val rules = cfg.getJSONObject("routing").getJSONArray("rules").toString()
        assertTrue(rules.contains("domain:xn--p1ai"))
        assertFalse(rules.contains("geosite:"))
        assertFalse(rules.contains("geoip:"))
    }

    @Test
    fun ruPresetWithGeoFilesAndCustomRules() {
        val settings = Settings(
            ruDirect = true,
            rules = listOf(RoutingRule("r", "example.рф, 10.0.0.0/8\ngeosite:google", RuleAction.Block)),
        )
        val cfg = JSONObject(XrayConfig.forTun(profile(samples[0]), settings, GeoData.Categories(geoip = setOf("ru"), geosite = setOf("category-ru", "google"))))
        val rules = cfg.getJSONObject("routing").getJSONArray("rules")
        val text = rules.toString()
        assertTrue(text.contains("geosite:category-ru"))
        assertTrue(text.contains("geoip:ru"))
        assertTrue(text.contains("domain:example.xn--p1ai"))
        // custom rule comes before the preset
        val blockIndex = (0 until rules.length()).first { rules.getJSONObject(it).optString("outboundTag") == "block" }
        val presetIndex = (0 until rules.length()).first { rules.getJSONObject(it).toString().contains("category-ru") }
        assertTrue(blockIndex < presetIndex)
    }
}
