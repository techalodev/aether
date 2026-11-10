package dev.aether.vpn.core

import org.junit.Assert.assertEquals
import org.junit.Assert.assertNotNull
import org.junit.Assume.assumeTrue
import org.junit.Test
import java.io.File

class SubscriptionParseTest {

    @Test
    fun parsesRealityLink() {
        val node = LinkParser.parse(
            "vless://uuid@example.com:443?encryption=none&type=tcp&security=reality&sni=sni.example&fp=firefox&pbk=KEY&sid=ab#%F0%9F%87%A9%F0%9F%87%AA%20Germany"
        )
        assertNotNull(node)
        assertEquals("example.com", node!!.address)
        assertEquals("reality", node.security)
        assertEquals("🇩🇪 Germany", node.name)
    }

    @Test
    fun parsesHysteria2WithTrailingSlash() {
        val node = LinkParser.parse("hysteria2://pass@host.example:8444/?sni=host.example#Hy")
        assertNotNull(node)
        assertEquals(8444, node!!.port)
        assertEquals("pass", node.secret)
    }

    /** Optional: run against a real subscription body, e.g. AETHER_SUB_FILE=/path/body.txt */
    @Test
    fun parsesWholeSubscriptionFile() {
        val path = System.getenv("AETHER_SUB_FILE")
        assumeTrue(path != null && File(path).exists())
        val body = File(path!!).readText()
        val links = if (body.trimStart().startsWith("[")) emptyList() else LinkParser.splitLinks(body)
        val failed = links.filter { LinkParser.parse(it) == null }
        val profiles = SubscriptionFetcher.parseBody(body, "sub")
        println("links=${links.size} failed=${failed.size} profiles=${profiles.size}")
        failed.forEach { println("FAILED: " + it.substringBefore("://") + " …" + it.substringAfter('#', "")) }
        assertEquals(0, failed.size)
    }
}
