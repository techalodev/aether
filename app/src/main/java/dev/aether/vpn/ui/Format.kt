package dev.aether.vpn.ui

import java.util.Locale

fun formatBytes(bytes: Long): String {
    if (bytes < 1024) return "$bytes B"
    val units = listOf("KB", "MB", "GB", "TB")
    var v = bytes.toDouble() / 1024
    var i = 0
    while (v >= 1024 && i < units.lastIndex) {
        v /= 1024; i++
    }
    return String.format(Locale.US, if (v >= 100) "%.0f %s" else "%.1f %s", v, units[i])
}

fun formatSpeed(bytesPerSecond: Long): String = formatBytes(bytesPerSecond) + "/s"

fun formatDuration(millis: Long): String {
    val s = millis / 1000
    val h = s / 3600
    val m = s % 3600 / 60
    val sec = s % 60
    return if (h > 0) String.format(Locale.US, "%d:%02d:%02d", h, m, sec)
    else String.format(Locale.US, "%02d:%02d", m, sec)
}
