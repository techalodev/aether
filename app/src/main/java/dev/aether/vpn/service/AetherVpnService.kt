package dev.aether.vpn.service

import android.app.Notification
import android.app.NotificationChannel
import android.app.NotificationManager
import android.app.PendingIntent
import android.content.Intent
import android.content.pm.ServiceInfo
import android.net.IpPrefix
import android.net.VpnService
import android.os.Build
import android.os.ParcelFileDescriptor
import androidx.core.app.NotificationCompat
import androidx.core.app.ServiceCompat
import dev.aether.vpn.MainActivity
import dev.aether.vpn.R
import dev.aether.vpn.core.GeoData
import dev.aether.vpn.core.ProxyProbe
import dev.aether.vpn.core.XrayConfig
import dev.aether.vpn.core.startCore
import dev.aether.vpn.data.Repository
import dev.aether.vpn.ui.AppIcon
import dev.aether.vpn.data.Settings
import dev.aether.vpn.data.SplitMode
import dev.aether.vpn.ui.formatSpeed
import kotlinx.coroutines.CoroutineScope
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.Job
import kotlinx.coroutines.SupervisorJob
import kotlinx.coroutines.cancel
import kotlinx.coroutines.delay
import kotlinx.coroutines.isActive
import kotlinx.coroutines.launch
import kotlinx.coroutines.sync.Mutex
import kotlinx.coroutines.sync.withLock
import libv2ray.CoreCallbackHandler
import libv2ray.CoreController
import libv2ray.Libv2ray
import java.net.InetAddress

class AetherVpnService : VpnService() {

    companion object {
        const val ACTION_START = "dev.aether.vpn.START"
        const val ACTION_STOP = "dev.aether.vpn.STOP"
        private const val CHANNEL = "vpn"
        private const val NOTIFICATION_ID = 1
    }

    private val scope = CoroutineScope(SupervisorJob() + Dispatchers.IO)
    private val lock = Mutex()
    private var tun: ParcelFileDescriptor? = null
    private var statsJob: Job? = null
    private var countryJob: Job? = null
    private var profileName = ""
    private var lastTitle = ""
    private var lastText = ""
    private val iconListener: (AppIcon) -> Unit = {
        if (VpnController.isActive) notify(buildNotification(lastTitle, lastText))
    }

    override fun onCreate() {
        super.onCreate()
        AppIcon.listeners += iconListener
    }

    private val core: CoreController by lazy {
        Libv2ray.newCoreController(object : CoreCallbackHandler {
            override fun startup(): Long = 0
            override fun shutdown(): Long = 0
            override fun onEmitStatus(p0: Long, p1: String?): Long = 0
        })
    }

    override fun onStartCommand(intent: Intent?, flags: Int, startId: Int): Int {
        if (intent?.action == ACTION_STOP) {
            scope.launch { shutdown() }
            return START_NOT_STICKY
        }
        // ACTION_START, or a system (always-on VPN) start
        startForegroundCompat(buildNotification(getString(R.string.app_name), "Connecting…"))
        scope.launch { connect() }
        return START_STICKY
    }

    override fun onRevoke() {
        scope.launch { shutdown() }
    }

    override fun onDestroy() {
        AppIcon.listeners -= iconListener
        stopCore()
        VpnController.set(VpnState.Idle)
        scope.cancel()
        super.onDestroy()
    }

    // ---- lifecycle ------------------------------------------------------------------------------

    private suspend fun connect() = lock.withLock {
        VpnController.set(VpnState.Connecting)
        stopCore()
        try {
            val data = Repository.data.value
            val profile = data.selected ?: error("No configuration selected")
            val config = XrayConfig.forTun(profile, data.settings, GeoData.categories(this))
            profileName = profile.name

            val pfd = buildTun(data.settings, profile.name)
                ?: error("VPN permission not granted")
            tun = pfd
            startCore(core, config, pfd.fd)
            if (!core.isRunning) error("Core failed to start")

            VpnController.set(VpnState.Connected(System.currentTimeMillis()))
            notify(buildNotification(profileName, "Connected"))
            startStats()
            if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.Q) {
                VpnController.setLockdown(VpnController.Lockdown(isAlwaysOn, isLockdownEnabled))
            }
            countryJob?.cancel()
            countryJob = scope.launch {
                VpnController.setCountry(ProxyProbe.country(this@AetherVpnService, profile))
            }
        } catch (e: Throwable) {
            stopCore()
            VpnController.set(VpnState.Error(e.message ?: e.javaClass.simpleName))
            ServiceCompat.stopForeground(this, ServiceCompat.STOP_FOREGROUND_REMOVE)
            stopSelf()
        }
    }

    private suspend fun shutdown() = lock.withLock {
        VpnController.set(VpnState.Stopping)
        stopCore()
        VpnController.set(VpnState.Idle)
        ServiceCompat.stopForeground(this, ServiceCompat.STOP_FOREGROUND_REMOVE)
        stopSelf()
    }

    private fun stopCore() {
        statsJob?.cancel()
        statsJob = null
        countryJob?.cancel()
        countryJob = null
        runCatching { if (core.isRunning) core.stopLoop() }
        runCatching { tun?.close() }
        tun = null
    }

    private fun buildTun(settings: Settings, session: String): ParcelFileDescriptor? {
        val dns = settings.remoteDns
        val bypassLan = settings.bypassLan
        val builder = Builder()
            .setSession(session)
            .setMtu(XrayConfig.MTU)
            .addAddress("10.18.0.1", 30)
            .addAddress("fd18:ae7e::1", 126)
            .addRoute("0.0.0.0", 0)
            .addRoute("::", 0)

        val dnsServers = dns.split(',', ' ').map { it.trim() }
            .filter { it.isNotEmpty() && it.all { c -> c.isDigit() || c == '.' || c == ':' } }
        dnsServers.ifEmpty { listOf("1.1.1.1") }.forEach { runCatching { builder.addDnsServer(it) } }

        if (bypassLan && Build.VERSION.SDK_INT >= Build.VERSION_CODES.TIRAMISU) {
            XrayConfig.PRIVATE_CIDRS.forEach { cidr ->
                runCatching {
                    val (ip, len) = cidr.split('/')
                    builder.excludeRoute(IpPrefix(InetAddress.getByName(ip), len.toInt()))
                }
            }
        }
        // Our own traffic (subscription updates, core dialing) must always bypass the tunnel
        val apps = settings.splitApps - packageName
        when {
            settings.splitMode == SplitMode.Include && apps.isNotEmpty() ->
                apps.forEach { runCatching { builder.addAllowedApplication(it) } }
            settings.splitMode == SplitMode.Exclude -> {
                builder.addDisallowedApplication(packageName)
                apps.forEach { runCatching { builder.addDisallowedApplication(it) } }
            }
            else -> builder.addDisallowedApplication(packageName)
        }
        if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.Q) builder.setMetered(false)
        return builder.establish()
    }

    // ---- stats ----------------------------------------------------------------------------------

    private fun startStats() {
        statsJob = scope.launch {
            var tick = 0
            while (isActive) {
                delay(1000)
                val raw = runCatching { core.queryAllOutboundTrafficStats() }.getOrNull().orEmpty()
                var up = 0L
                var down = 0L
                raw.split(';').forEach { entry ->
                    val parts = entry.split(',')
                    if (parts.size == 3 && parts[0] !in setOf("direct", "block", "dns-out")) {
                        val v = parts[2].toLongOrNull() ?: 0
                        if (parts[1] == "uplink") up += v else down += v
                    }
                }
                VpnController.setTraffic(Traffic(up, down))
                if (++tick % 2 == 0) {
                    notify(buildNotification(profileName, "↑ ${formatSpeed(up)}   ↓ ${formatSpeed(down)}"))
                }
            }
        }
    }

    // ---- notification ---------------------------------------------------------------------------

    private fun startForegroundCompat(notification: Notification) {
        val type = if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.UPSIDE_DOWN_CAKE) {
            ServiceInfo.FOREGROUND_SERVICE_TYPE_SPECIAL_USE
        } else 0
        ServiceCompat.startForeground(this, NOTIFICATION_ID, notification, type)
    }

    private fun notify(notification: Notification) {
        getSystemService(NotificationManager::class.java).notify(NOTIFICATION_ID, notification)
    }

    private fun buildNotification(title: String, text: String): Notification {
        val nm = getSystemService(NotificationManager::class.java)
        if (nm.getNotificationChannel(CHANNEL) == null) {
            nm.createNotificationChannel(
                NotificationChannel(CHANNEL, "VPN status", NotificationManager.IMPORTANCE_LOW).apply {
                    setShowBadge(false)
                }
            )
        }
        val open = PendingIntent.getActivity(
            this, 0, Intent(this, MainActivity::class.java),
            PendingIntent.FLAG_IMMUTABLE or PendingIntent.FLAG_UPDATE_CURRENT,
        )
        val stop = PendingIntent.getService(
            this, 1, Intent(this, AetherVpnService::class.java).setAction(ACTION_STOP),
            PendingIntent.FLAG_IMMUTABLE or PendingIntent.FLAG_UPDATE_CURRENT,
        )
        lastTitle = title
        lastText = text
        // One icon only: the Aether mark, tinted in the colour of the picked launcher icon
        return NotificationCompat.Builder(this, CHANNEL)
            .setSmallIcon(R.drawable.ic_stat_aether)
            .setColor(AppIcon.current(this).accent)
            .setContentTitle(title)
            .setContentText(text)
            .setContentIntent(open)
            .setOngoing(true)
            .setOnlyAlertOnce(true)
            .setSilent(true)
            .setShowWhen(false)
            .setCategory(NotificationCompat.CATEGORY_SERVICE)
            .addAction(0, "Disconnect", stop)
            .build()
    }
}
