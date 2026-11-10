package dev.aether.vpn.data

import android.content.Context
import dev.aether.vpn.core.SubscriptionFetcher
import java.net.URI

/** What changed in one subscription update. */
data class RefreshReport(
    val subName: String,
    val added: List<String> = emptyList(),
    val removed: List<String> = emptyList(),
    val updated: List<String> = emptyList(),
    val error: String? = null,
) {
    val hasChanges get() = added.isNotEmpty() || removed.isNotEmpty() || updated.isNotEmpty()
}

object SubscriptionManager {

    fun isDue(sub: Subscription, now: Long = System.currentTimeMillis()): Boolean =
        sub.autoUpdateHours > 0 && now - sub.updatedAt >= sub.autoUpdateHours * 3_600_000L

    fun effectiveUserAgent(sub: Subscription, settings: Settings) =
        sub.userAgent?.takeIf { it.isNotBlank() } ?: settings.userAgent

    fun effectiveSendHwid(sub: Subscription, settings: Settings) = sub.sendHwid ?: settings.sendHwid

    fun hostOf(url: String) = runCatching { URI(url).host }.getOrNull() ?: "Subscription"

    /** Fetches and merges a subscription. Local edits are replaced by the server version. */
    suspend fun refresh(context: Context, id: String): RefreshReport {
        val data = Repository.data.value
        val sub = data.subscriptions.firstOrNull { it.id == id } ?: return RefreshReport("", error = "Not found")
        val displayName = sub.name.ifBlank { hostOf(sub.url) }
        return try {
            val settings = data.settings
            val headers = if (effectiveSendHwid(sub, settings)) Hwid.headers(context) else emptyMap()
            val result = SubscriptionFetcher.fetch(sub.url, sub.id, effectiveUserAgent(sub, settings), headers)
            var report = RefreshReport(displayName)

            Repository.update { d ->
                val old = d.profiles.filter { it.subId == id }
                val used = HashSet<String>()
                // Keep ids (and therefore the selection) stable across updates
                val merged = result.profiles.map { p ->
                    val match = old.firstOrNull { it.id !in used && it.link == p.link }
                        ?: old.firstOrNull { it.id !in used && it.name == p.name }
                    if (match != null) {
                        used += match.id
                        p.copy(id = match.id)
                    } else p
                }
                val oldById = old.associateBy { it.id }
                report = report.copy(
                    added = merged.filter { it.id !in oldById }.map { it.name },
                    removed = old.filter { it.id !in used }.map { it.name },
                    updated = merged.filter { m -> oldById[m.id]?.let { it.link != m.link || it.name != m.name } == true }
                        .map { it.name },
                )

                val others = d.profiles.filterNot { it.subId == id }
                val index = d.profiles.indexOfFirst { it.subId == id }.takeIf { it >= 0 } ?: others.size
                val profiles = others.take(index) + merged + others.drop(index)
                val first = sub.updatedAt == 0L
                d.copy(
                    profiles = profiles,
                    selectedId = d.selectedId.takeIf { sel -> profiles.any { it.id == sel } }
                        ?: profiles.firstOrNull()?.id,
                    subscriptions = d.subscriptions.map {
                        if (it.id != id) it else it.copy(
                            name = it.name.ifBlank { result.title ?: hostOf(it.url) },
                            updatedAt = System.currentTimeMillis(),
                            upload = result.upload,
                            download = result.download,
                            total = result.total,
                            expire = result.expire,
                            supportUrl = result.supportUrl,
                            description = result.description,
                            // Adopt the provider's suggested interval on the first fetch only
                            autoUpdateHours = if (first && result.updateIntervalHours != null)
                                result.updateIntervalHours.coerceIn(1, 168) else it.autoUpdateHours,
                        )
                    },
                )
            }
            report.copy(subName = Repository.data.value.subscriptions.firstOrNull { it.id == id }?.name ?: displayName)
        } catch (e: Throwable) {
            Repository.update { d ->
                d.copy(subscriptions = d.subscriptions.map {
                    if (it.id == id && it.name.isBlank()) it.copy(name = hostOf(it.url)) else it
                })
            }
            RefreshReport(displayName, error = e.message ?: "Update failed")
        }
    }

    suspend fun refreshDue(context: Context) {
        Repository.data.value.subscriptions.filter { isDue(it) }.forEach { refresh(context, it.id) }
    }
}
