package dev.aether.vpn.service

import android.content.Context
import android.content.Intent
import androidx.core.content.ContextCompat
import dev.aether.vpn.core.Country
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.StateFlow
import kotlinx.coroutines.flow.asStateFlow

sealed interface VpnState {
    data object Idle : VpnState
    data object Connecting : VpnState
    data class Connected(val since: Long) : VpnState
    data object Stopping : VpnState
    data class Error(val message: String) : VpnState
}

/** Bytes per second through the proxy outbound. */
data class Traffic(val up: Long = 0, val down: Long = 0)

object VpnController {
    private val _state = MutableStateFlow<VpnState>(VpnState.Idle)
    val state: StateFlow<VpnState> = _state.asStateFlow()

    private val _traffic = MutableStateFlow(Traffic())
    val traffic: StateFlow<Traffic> = _traffic.asStateFlow()

    internal fun set(state: VpnState) {
        _state.value = state
        if (state !is VpnState.Connected) {
            _traffic.value = Traffic()
            _country.value = null
        }
    }

    internal fun setTraffic(t: Traffic) {
        _traffic.value = t
    }

    /** Exit country of the running tunnel, once detected. */
    private val _country = MutableStateFlow<Country?>(null)
    val country: StateFlow<Country?> = _country.asStateFlow()
    internal fun setCountry(c: Country?) {
        _country.value = c
    }

    /** System "Always-on VPN" / "Block connections without VPN" for this app (null = unknown). */
    data class Lockdown(val alwaysOn: Boolean, val blocking: Boolean)
    private val _lockdown = MutableStateFlow<Lockdown?>(null)
    val lockdown: StateFlow<Lockdown?> = _lockdown.asStateFlow()
    internal fun setLockdown(l: Lockdown?) {
        _lockdown.value = l
    }

    val isActive: Boolean
        get() = _state.value.let { it is VpnState.Connected || it is VpnState.Connecting }

    /** Caller must have obtained VPN consent via VpnService.prepare first. */
    fun start(context: Context) {
        val intent = Intent(context, AetherVpnService::class.java).setAction(AetherVpnService.ACTION_START)
        ContextCompat.startForegroundService(context, intent)
    }

    fun stop(context: Context) {
        if (_state.value is VpnState.Idle) return
        val intent = Intent(context, AetherVpnService::class.java).setAction(AetherVpnService.ACTION_STOP)
        context.startService(intent)
    }
}
