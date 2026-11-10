package dev.aether.vpn.service

import android.app.PendingIntent
import android.content.Intent
import android.net.VpnService
import android.os.Build
import android.service.quicksettings.Tile
import android.service.quicksettings.TileService
import dev.aether.vpn.MainActivity
import dev.aether.vpn.data.Repository
import kotlinx.coroutines.CoroutineScope
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.Job
import kotlinx.coroutines.launch

class QuickTileService : TileService() {
    private var job: Job? = null

    override fun onStartListening() {
        job = CoroutineScope(Dispatchers.Main).launch {
            VpnController.state.collect { render(it) }
        }
    }

    override fun onStopListening() {
        job?.cancel()
        job = null
    }

    override fun onClick() {
        if (VpnController.isActive) {
            VpnController.stop(this)
            return
        }
        Repository.init(applicationContext)
        if (VpnService.prepare(this) != null || Repository.data.value.selected == null) {
            openApp()
        } else {
            VpnController.start(this)
        }
    }

    private fun openApp() {
        val intent = Intent(this, MainActivity::class.java).addFlags(Intent.FLAG_ACTIVITY_NEW_TASK)
        if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.UPSIDE_DOWN_CAKE) {
            startActivityAndCollapse(
                PendingIntent.getActivity(this, 0, intent, PendingIntent.FLAG_IMMUTABLE)
            )
        } else {
            @Suppress("DEPRECATION", "StartActivityAndCollapseDeprecated")
            startActivityAndCollapse(intent)
        }
    }

    private fun render(state: VpnState) {
        val tile = qsTile ?: return
        tile.state = when (state) {
            is VpnState.Connected, VpnState.Connecting -> Tile.STATE_ACTIVE
            else -> Tile.STATE_INACTIVE
        }
        if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.Q) {
            tile.subtitle = Repository.data.value.selected?.name
        }
        tile.updateTile()
    }
}
