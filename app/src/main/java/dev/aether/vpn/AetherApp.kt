package dev.aether.vpn

import android.app.Application
import dev.aether.vpn.core.GeoData
import dev.aether.vpn.data.Repository
import androidx.lifecycle.DefaultLifecycleObserver
import androidx.lifecycle.LifecycleOwner
import androidx.lifecycle.ProcessLifecycleOwner
import dev.aether.vpn.service.AutoUpdateWorker
import dev.aether.vpn.ui.AppIcon
import kotlinx.coroutines.MainScope
import kotlinx.coroutines.launch
import libv2ray.Libv2ray
import java.security.SecureRandom
import java.util.Base64

class AetherApp : Application() {
    override fun onCreate() {
        super.onCreate()
        Repository.init(this)
        GeoData.refresh(this)

        val key = Repository.data.value.xudpKey ?: ByteArray(32)
            .also { SecureRandom().nextBytes(it) }
            .let { Base64.getUrlEncoder().withoutPadding().encodeToString(it) }
            .also { k -> MainScope().launch { Repository.update { it.copy(xudpKey = k) } } }

        // filesDir is also where geoip.dat / geosite.dat live
        Libv2ray.initCoreEnv(filesDir.absolutePath, key)
        AutoUpdateWorker.schedule(this)

        // Swap the launcher icon only while the app is in the background (see AppIcon.select)
        AppIcon.applyPending(this)
        ProcessLifecycleOwner.get().lifecycle.addObserver(object : DefaultLifecycleObserver {
            override fun onStop(owner: LifecycleOwner) = AppIcon.applyPending(this@AetherApp)
        })
    }
}
