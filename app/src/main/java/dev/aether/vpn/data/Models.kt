package dev.aether.vpn.data

import kotlinx.serialization.Serializable

@Serializable
data class Profile(
    val id: String,
    /** Owning subscription, or null for manually imported configs. */
    val subId: String? = null,
    val name: String,
    /** Share link (vless://, vmess://, …) or a full Xray JSON config. */
    val link: String,
    val protocol: String,
    val address: String,
    val port: Int,
    /** e.g. "TCP", "WS", "gRPC". */
    val transport: String? = null,
    /** e.g. "REALITY", "TLS". */
    val security: String? = null,
    /** Per-server caption from the provider (Remnawave `serverDescription`). */
    val description: String? = null,
)

@Serializable
data class Subscription(
    val id: String,
    val name: String,
    val url: String,
    val updatedAt: Long = 0,
    val upload: Long = 0,
    val download: Long = 0,
    val total: Long = 0,
    /** Unix seconds, 0 = never expires. */
    val expire: Long = 0,
    val supportUrl: String? = null,
    /** Provider announcement (`announce` header). */
    val description: String? = null,
    /** 0 = auto-update off. */
    val autoUpdateHours: Int = 12,
    /** Per-subscription overrides; null = use global setting. */
    val userAgent: String? = null,
    val sendHwid: Boolean? = null,
    val showOnHome: Boolean = true,
    val collapsed: Boolean = false,
)

@Serializable
enum class ThemeMode { System, Light, Dark }

@Serializable
enum class SplitMode { Off, Include, Exclude }

@Serializable
enum class RuleAction { Proxy, Direct, Block }

@Serializable
enum class CountryPart { Flag, Code, Name }

@Serializable
data class RoutingRule(
    val id: String,
    /** Domains / IPs / geosite: / geoip: entries, separated by commas or new lines. */
    val values: String,
    val action: RuleAction,
    val enabled: Boolean = true,
)

@Serializable
data class Settings(
    val userAgent: String = DEFAULT_USER_AGENT,
    val sendHwid: Boolean = true,
    val remoteDns: String = "1.1.1.1",
    val bypassLan: Boolean = true,
    val testUrl: String = "https://www.gstatic.com/generate_204",
    val theme: ThemeMode = ThemeMode.System,
    val dynamicColor: Boolean = true,
    val splitMode: SplitMode = SplitMode.Off,
    val splitApps: Set<String> = emptySet(),
    val ruDirect: Boolean = false,
    val rules: List<RoutingRule> = emptyList(),
    val geoipUrl: String = DEFAULT_GEOIP_URL,
    val geositeUrl: String = DEFAULT_GEOSITE_URL,
    /** Popup with the result of a manual subscription update. */
    val updatePopup: Boolean = true,
    val updatePopupSeconds: Int = 5,
    /** What to show for the exit country; empty = hidden. */
    val countryParts: Set<CountryPart> = setOf(CountryPart.Flag),
    /** Look for a new GitHub release on launch. */
    val checkUpdates: Boolean = true,
    /** Floating rounded navigation bar with an animated indicator instead of the standard one. */
    val floatingNavBar: Boolean = false,
    /** Floating bar: the selected tab widens to show its label. */
    val navBarLabels: Boolean = false,
) {
    companion object {
        const val DEFAULT_USER_AGENT = "Aether/1.2"
        const val DEFAULT_GEOIP_URL = "https://github.com/runetfreedom/russia-v2ray-rules-dat/releases/latest/download/geoip.dat"
        const val DEFAULT_GEOSITE_URL = "https://github.com/runetfreedom/russia-v2ray-rules-dat/releases/latest/download/geosite.dat"

        /** Current releases as of November 2026. */
        val USER_AGENT_PRESETS = listOf(
            "Aether" to DEFAULT_USER_AGENT,
            "Happ" to "Happ/4.10.1",
            "v2rayNG" to "v2rayNG/2.3.15",
            "FlClash" to "FlClash/v0.9.3 clash-verge Platform/android",
        )
    }
}

@Serializable
data class AppData(
    val profiles: List<Profile> = emptyList(),
    val subscriptions: List<Subscription> = emptyList(),
    val selectedId: String? = null,
    val settings: Settings = Settings(),
    /** Random XUDP base key, generated once. */
    val xudpKey: String? = null,
    val localCollapsed: Boolean = false,
    /** The background-restriction prompt was answered or skipped. */
    val batteryPromptDone: Boolean = false,
) {
    val selected: Profile? get() = profiles.firstOrNull { it.id == selectedId }
}
