package dev.aether.vpn.data

import android.annotation.SuppressLint
import android.content.Context
import android.os.Build
import android.provider.Settings as AndroidSettings

/** Device HWID — always the real device identifier; it can be turned off but not spoofed. */
object Hwid {
    @SuppressLint("HardwareIds")
    fun deviceId(context: Context): String =
        AndroidSettings.Secure.getString(context.contentResolver, AndroidSettings.Secure.ANDROID_ID)
            ?: "unknown"

    /** Headers understood by Remnawave / Marzban-style panels. */
    fun headers(context: Context): Map<String, String> = mapOf(
        "x-hwid" to deviceId(context),
        "x-device-os" to "Android",
        "x-ver-os" to Build.VERSION.RELEASE,
        "x-device-model" to Build.MODEL,
    )
}
