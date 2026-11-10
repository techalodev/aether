package dev.aether.vpn.ui

import android.app.Application
import androidx.lifecycle.AndroidViewModel
import androidx.lifecycle.viewModelScope
import dev.aether.vpn.core.GeoData
import dev.aether.vpn.core.ProxyProbe
import dev.aether.vpn.core.SubscriptionFetcher
import dev.aether.vpn.core.Updater
import dev.aether.vpn.data.AppData
import dev.aether.vpn.data.Hwid
import dev.aether.vpn.data.Profile
import dev.aether.vpn.data.RefreshReport
import dev.aether.vpn.data.Repository
import dev.aether.vpn.data.Settings
import dev.aether.vpn.data.Subscription
import dev.aether.vpn.data.SubscriptionManager
import dev.aether.vpn.service.VpnController
import dev.aether.vpn.service.VpnState
import kotlinx.coroutines.async
import kotlinx.coroutines.awaitAll
import kotlinx.coroutines.channels.Channel
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.StateFlow
import kotlinx.coroutines.flow.asStateFlow
import kotlinx.coroutines.flow.receiveAsFlow
import kotlinx.coroutines.flow.update
import kotlinx.coroutines.launch
import kotlinx.coroutines.sync.Semaphore
import kotlinx.coroutines.sync.withPermit

class MainViewModel(app: Application) : AndroidViewModel(app) {

    val data: StateFlow<AppData> = Repository.data
    val vpnState = VpnController.state
    val traffic = VpnController.traffic
    val geo = GeoData.state
    val country = VpnController.country
    val lockdown = VpnController.lockdown

    /** profileId → delay in ms (-1 = failed, 0 = testing). */
    private val _pings = MutableStateFlow<Map<String, Int>>(emptyMap())
    val pings = _pings.asStateFlow()

    private val _refreshing = MutableStateFlow<Set<String>>(emptySet())
    val refreshing = _refreshing.asStateFlow()

    private val _messages = Channel<String>(Channel.BUFFERED)
    val messages = _messages.receiveAsFlow()

    /** Results of manual subscription updates, shown as a popup on the Subscriptions tab. */
    private val _reports = Channel<List<RefreshReport>>(Channel.BUFFERED)
    val reports = _reports.receiveAsFlow()

    private val context get() = getApplication<Application>()

    val update = Updater.state

    /** True when the update dialog should pop up on its own (auto-check found something new). */
    private val _updatePrompt = MutableStateFlow(false)
    val updatePrompt = _updatePrompt.asStateFlow()

    init {
        viewModelScope.launch {
            data.value.subscriptions.filter { SubscriptionManager.isDue(it) }.forEach { refresh(it.id) }
        }
        if (data.value.settings.checkUpdates && Updater.state.value is Updater.State.Idle) {
            viewModelScope.launch {
                val result = Updater.check()
                if (result is Updater.State.Available && result.release.version != Updater.dismissedVersion) {
                    _updatePrompt.value = true
                }
            }
        }
    }

    // ---- updates --------------------------------------------------------------------------------

    fun checkForUpdates() = viewModelScope.launch {
        if (Updater.check() is Updater.State.Available) _updatePrompt.value = true
    }

    fun downloadUpdate(release: Updater.Release) = viewModelScope.launch { Updater.download(context, release) }

    fun installUpdate(file: java.io.File) = Updater.install(context, file)

    fun dismissUpdate() {
        (Updater.state.value as? Updater.State.Available)?.let { Updater.dismissedVersion = it.release.version }
        _updatePrompt.value = false
    }

    fun showUpdate() {
        _updatePrompt.value = true
    }

    private fun restartIfConnected() {
        if (vpnState.value is VpnState.Connected) VpnController.start(context)
    }

    // ---- profiles -------------------------------------------------------------------------------

    fun select(profile: Profile) {
        if (profile.id == data.value.selectedId) return
        viewModelScope.launch {
            Repository.update { it.copy(selectedId = profile.id) }
            restartIfConnected()
        }
    }

    fun saveProfile(profile: Profile) = viewModelScope.launch {
        Repository.update { d -> d.copy(profiles = d.profiles.map { if (it.id == profile.id) profile else it }) }
        if (profile.id == data.value.selectedId) restartIfConnected()
    }

    fun deleteProfile(profile: Profile) = viewModelScope.launch {
        Repository.update { d ->
            d.copy(
                profiles = d.profiles.filterNot { it.id == profile.id },
                selectedId = d.selectedId.takeIf { it != profile.id },
            )
        }
    }

    /** Up to 16 Proxy GET tests run at once; results appear as each one finishes. */
    private val pingGate = Semaphore(16)

    fun pingAll() = ping(data.value.profiles)

    fun ping(profiles: List<Profile>) {
        if (profiles.isEmpty()) return
        val url = data.value.settings.testUrl
        _pings.update { it + profiles.associate { p -> p.id to 0 } }
        profiles.forEach { profile ->
            viewModelScope.launch {
                val ms = pingGate.withPermit { ProxyProbe.ping(context, profile, url) }
                _pings.update { it + (profile.id to ms) }
            }
        }
    }

    fun finishBatteryPrompt() = viewModelScope.launch {
        Repository.update { it.copy(batteryPromptDone = true) }
    }

    /** Imports clipboard / shared text: a subscription URL or one or more share links. */
    fun import(text: String?) {
        val t = text?.trim().orEmpty()
        when {
            t.isEmpty() -> say("Clipboard is empty")
            t.startsWith("http://") || t.startsWith("https://") -> addSubscription("", t.lineSequence().first())
            else -> viewModelScope.launch {
                val added = SubscriptionFetcher.parseBody(t, null)
                if (added.isEmpty()) {
                    say("Nothing to import")
                    return@launch
                }
                Repository.update { d ->
                    d.copy(profiles = d.profiles + added, selectedId = d.selectedId ?: added.first().id)
                }
                say(if (added.size == 1) "Added ${added.first().name}" else "Added ${added.size} configs")
            }
        }
    }

    // ---- subscriptions --------------------------------------------------------------------------

    fun addSubscription(name: String, url: String) {
        val sub = Subscription(id = SubscriptionFetcher.newId(), name = name.trim(), url = url.trim())
        viewModelScope.launch {
            Repository.update { it.copy(subscriptions = it.subscriptions + sub) }
            refresh(sub.id, manual = true)
        }
    }

    /** Saves edits from the subscription sheet; refetches if anything affecting the request changed. */
    fun saveSubscription(updated: Subscription) = viewModelScope.launch {
        val old = data.value.subscriptions.firstOrNull { it.id == updated.id } ?: return@launch
        Repository.update { d -> d.copy(subscriptions = d.subscriptions.map { if (it.id == updated.id) updated else it }) }
        if (old.url != updated.url || old.userAgent != updated.userAgent || old.sendHwid != updated.sendHwid) {
            refresh(updated.id, manual = true)
        }
    }

    fun updateSubscription(id: String, transform: (Subscription) -> Subscription) = viewModelScope.launch {
        Repository.update { d -> d.copy(subscriptions = d.subscriptions.map { if (it.id == id) transform(it) else it }) }
    }

    fun moveSubscription(from: Int, to: Int) = viewModelScope.launch {
        Repository.update { d ->
            if (from !in d.subscriptions.indices || to !in d.subscriptions.indices) return@update d
            val list = d.subscriptions.toMutableList()
            list.add(to, list.removeAt(from))
            d.copy(subscriptions = list)
        }
    }

    fun toggleLocalCollapsed() = viewModelScope.launch {
        Repository.update { it.copy(localCollapsed = !it.localCollapsed) }
    }

    fun deleteSubscription(id: String) = viewModelScope.launch {
        Repository.update { d ->
            val remaining = d.profiles.filterNot { it.subId == id }
            d.copy(
                subscriptions = d.subscriptions.filterNot { it.id == id },
                profiles = remaining,
                selectedId = d.selectedId.takeIf { sel -> remaining.any { it.id == sel } },
            )
        }
    }

    fun refreshAll() = viewModelScope.launch {
        val ids = data.value.subscriptions.map { it.id }.filter { it !in _refreshing.value }
        if (ids.isEmpty()) return@launch
        val before = data.value.selected?.link
        _refreshing.update { it + ids }
        val reports = ids.map { id ->
            async { SubscriptionManager.refresh(context, id).also { _refreshing.update { s -> s - id } } }
        }.awaitAll()
        _reports.trySend(reports)
        reconnectIfSelectedChanged(before)
    }

    /** [manual] updates report their result in a popup; background ones stay silent. */
    fun refresh(id: String, manual: Boolean = false) {
        if (id in _refreshing.value) return
        val before = data.value.selected?.link
        _refreshing.update { it + id }
        viewModelScope.launch {
            val report = try {
                SubscriptionManager.refresh(context, id)
            } finally {
                _refreshing.update { it - id }
            }
            if (manual) _reports.trySend(listOf(report))
            reconnectIfSelectedChanged(before)
        }
    }

    /** If the running profile changed during an update, reconnect with the new version. */
    private fun reconnectIfSelectedChanged(linkBefore: String?) {
        if (data.value.selected?.link != linkBefore) restartIfConnected()
    }

    // ---- geodata --------------------------------------------------------------------------------

    fun downloadGeo() = viewModelScope.launch {
        val s = data.value.settings
        runCatching { GeoData.download(context, s.geoipUrl, s.geositeUrl) }
            .onSuccess { say("Geo files downloaded"); restartIfConnected() }
            .onFailure { say("Download failed: ${it.message}") }
    }

    fun deleteGeo() {
        GeoData.delete(context)
        restartIfConnected()
    }

    // ---- settings -------------------------------------------------------------------------------

    fun updateSettings(transform: (Settings) -> Settings) = viewModelScope.launch {
        Repository.updateSettings(transform)
    }

    /** Settings that change the tunnel take effect by reconnecting. */
    fun updateTunnelSettings(transform: (Settings) -> Settings) = viewModelScope.launch {
        Repository.updateSettings(transform)
        restartIfConnected()
    }

    fun deviceHwid(): String = Hwid.deviceId(context)

    fun say(message: String) {
        _messages.trySend(message)
    }
}
