package dev.aether.vpn.core

import dev.aether.vpn.data.Profile
import dev.aether.vpn.data.Settings
import org.json.JSONObject
import org.junit.Assert.assertEquals
import org.junit.Assert.assertFalse
import org.junit.Assert.assertTrue
import org.junit.Assume.assumeTrue
import org.junit.Test
import java.io.File

class ClashAndGeoTest {

    private val yaml = """
        mixed-port: 7890
        proxies:
          - &ref_0
            name: ' 🇩🇪 Germany'
            type: vless
            server: de.example.com
            port: 443
            network: tcp
            uuid: 11111111-2222-3333-4444-555555555555
            tls: true
            servername: sni.example.com
            reality-opts:
              public-key: PUBKEY
              short-id: ab12
            client-fingerprint: firefox
          - name: Hy
            type: hysteria2
            server: hy.example.com
            port: 8444
            password: secret
            sni: hy.example.com
        proxy-groups:
          - name: auto
            type: url-test
            proxies:
              - *ref_0
    """.trimIndent()

    @Test
    fun parsesClashProxies() {
        val profiles = SubscriptionFetcher.parseBody(yaml, "sub")
        assertEquals(2, profiles.size)
        val de = profiles[0]
        assertEquals("🇩🇪 Germany", de.name)
        assertEquals("VLESS", de.protocol)
        assertEquals("REALITY", de.security)
        val node = LinkParser.parse(de.link)!!
        assertEquals("PUBKEY", node.publicKey)
        assertEquals("sni.example.com", node.sni)
        assertEquals("Hysteria2", profiles[1].protocol)
    }

    @Test
    fun parsesRealClashFile() {
        val path = System.getenv("AETHER_CLASH_FILE")
        assumeTrue(path != null && File(path).exists())
        val profiles = SubscriptionFetcher.parseBody(File(path!!).readText(), "sub")
        println("clash profiles=${profiles.size}")
        assertTrue(profiles.size > 90)
    }

    @Test
    fun jsonConfigDropsMissingGeoCategories() {
        val json = """
            {"remarks":"x","meta":{"serverDescription":"10 Gbit/s"},
             "outbounds":[{"tag":"proxy","protocol":"vless","settings":{"address":"a.example","port":443,"id":"u"}}],
             "routing":{"rules":[
               {"domain":["geosite:x5","geosite:category-ru"],"outboundTag":"direct"},
               {"domain":["geosite:twitch"],"outboundTag":"proxy"},
               {"ip":["geoip:ru"],"outboundTag":"direct"}
             ]},
             "dns":{"servers":[{"address":"77.88.8.8","domains":["geosite:category-ru"]},"1.1.1.1"]}}
        """.trimIndent()
        val profile = SubscriptionFetcher.parseBody(json, "s").single()
        assertEquals("10 Gbit/s", profile.description)

        val none = JSONObject(XrayConfig.forTun(profile, Settings(), GeoData.Categories())).toString()
        assertFalse(none.contains("geosite:"))
        assertFalse(none.contains("geoip:"))

        val some = JSONObject(XrayConfig.forTun(profile, Settings(), GeoData.Categories(setOf("ru"), setOf("category-ru")))).toString()
        assertTrue(some.contains("geosite:category-ru"))
        assertTrue(some.contains("geoip:ru"))
        assertFalse(some.contains("geosite:x5"))
        assertFalse(some.contains("geosite:twitch"))
    }

    @Test
    fun countryFlag() {
        assertEquals("🇩🇪", Country("DE", "Germany").flag)
    }

    @Suppress("unused")
    private fun profile(link: String) = Profile("id", null, "n", link, "VLESS", "a", 1)
}
