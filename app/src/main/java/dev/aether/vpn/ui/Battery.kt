package dev.aether.vpn.ui

import android.annotation.SuppressLint
import android.content.Context
import android.content.Intent
import android.os.PowerManager
import android.provider.Settings
import androidx.compose.runtime.Composable
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.setValue
import androidx.compose.ui.platform.LocalContext
import androidx.core.net.toUri
import androidx.lifecycle.compose.LifecycleResumeEffect

fun Context.isBackgroundUnrestricted(): Boolean =
    getSystemService(PowerManager::class.java)?.isIgnoringBatteryOptimizations(packageName) == true

/** Opens the system dialog that exempts Aether from battery optimisation. */
@SuppressLint("BatteryLife")
fun Context.requestBackgroundUnrestricted() {
    val direct = Intent(Settings.ACTION_REQUEST_IGNORE_BATTERY_OPTIMIZATIONS, "package:$packageName".toUri())
    runCatching { startActivity(direct) }
        .onFailure { runCatching { startActivity(Intent(Settings.ACTION_IGNORE_BATTERY_OPTIMIZATION_SETTINGS)) } }
}

/** Re-checked every time the screen resumes, so the UI updates after the system dialog. */
@Composable
fun rememberBackgroundUnrestricted(): Boolean {
    val context = LocalContext.current
    var granted by remember { mutableStateOf(context.isBackgroundUnrestricted()) }
    LifecycleResumeEffect(Unit) {
        granted = context.isBackgroundUnrestricted()
        onPauseOrDispose { }
    }
    return granted
}
