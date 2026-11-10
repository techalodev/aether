package dev.aether.vpn.core

import android.content.Context
import android.content.Intent
import android.os.Build
import android.provider.Settings
import androidx.core.content.FileProvider
import androidx.core.net.toUri
import dev.aether.vpn.BuildConfig
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.StateFlow
import kotlinx.coroutines.flow.asStateFlow
import kotlinx.coroutines.withContext
import org.json.JSONObject
import java.io.File
import java.net.HttpURLConnection
import java.net.URL

/** Self-updates from GitHub Releases of this repository. */
object Updater {
    const val REPO = "techalodev/aether"

    data class Release(
        val version: String,
        val notes: String,
        val apkUrl: String,
        val apkName: String,
        val size: Long,
        val pageUrl: String,
    )

    sealed interface State {
        data object Idle : State
        data object Checking : State
        data object UpToDate : State
        data class Available(val release: Release) : State
        data class Downloading(val release: Release, val progress: Float) : State
        data class Ready(val release: Release, val file: File) : State
        data class Failed(val message: String) : State
    }

    private val _state = MutableStateFlow<State>(State.Idle)
    val state: StateFlow<State> = _state.asStateFlow()

    /** Set when the user dismisses the dialog for a version, so auto-checks stay quiet. */
    @Volatile var dismissedVersion: String? = null

    suspend fun check(): State = withContext(Dispatchers.IO) {
        _state.value = State.Checking
        val result = try {
            val conn = (URL("https://api.github.com/repos/$REPO/releases/latest").openConnection() as HttpURLConnection).apply {
                connectTimeout = 15_000
                readTimeout = 15_000
                setRequestProperty("Accept", "application/vnd.github+json")
                setRequestProperty("User-Agent", "Aether/${BuildConfig.VERSION_NAME}")
            }
            try {
                when (conn.responseCode) {
                    200 -> parse(JSONObject(conn.inputStream.use { it.readBytes().decodeToString() }))
                    404 -> State.Failed("No releases found")
                    else -> State.Failed("GitHub: HTTP ${conn.responseCode}")
                }
            } finally {
                conn.disconnect()
            }
        } catch (e: Exception) {
            State.Failed(e.message ?: "Network error")
        }
        _state.value = result
        result
    }

    private fun parse(json: JSONObject): State {
        val version = json.optString("tag_name").removePrefix("v")
        if (!isNewer(version, BuildConfig.VERSION_NAME)) return State.UpToDate
        val assets = json.optJSONArray("assets") ?: return State.Failed("Release has no files")
        val apks = (0 until assets.length()).map { assets.getJSONObject(it) }
            .filter { it.optString("name").endsWith(".apk") }
        // Prefer the split for this device's primary ABI, fall back to the universal APK
        val abi = Build.SUPPORTED_ABIS.firstOrNull().orEmpty()
        val asset = apks.firstOrNull { it.optString("name").contains(abi) && abi.isNotEmpty() }
            ?: apks.firstOrNull { it.optString("name").contains("universal") }
            ?: apks.firstOrNull()
            ?: return State.Failed("No APK for this device")
        return State.Available(
            Release(
                version = version,
                notes = json.optString("body").trim(),
                apkUrl = asset.optString("browser_download_url"),
                apkName = asset.optString("name"),
                size = asset.optLong("size"),
                pageUrl = json.optString("html_url"),
            )
        )
    }

    fun isNewer(candidate: String, current: String): Boolean {
        fun parts(v: String) = v.split('.', '-').map { it.toIntOrNull() ?: 0 }
        val a = parts(candidate)
        val b = parts(current)
        for (i in 0 until maxOf(a.size, b.size)) {
            val x = a.getOrElse(i) { 0 }
            val y = b.getOrElse(i) { 0 }
            if (x != y) return x > y
        }
        return false
    }

    suspend fun download(context: Context, release: Release) = withContext(Dispatchers.IO) {
        val dir = File(context.cacheDir, "updates").apply { mkdirs() }
        dir.listFiles()?.forEach { it.delete() }
        val target = File(dir, "Aether-${release.version}.apk")
        try {
            _state.value = State.Downloading(release, 0f)
            val conn = (URL(release.apkUrl).openConnection() as HttpURLConnection).apply {
                connectTimeout = 20_000
                readTimeout = 60_000
                instanceFollowRedirects = true
                setRequestProperty("User-Agent", "Aether/${BuildConfig.VERSION_NAME}")
            }
            try {
                if (conn.responseCode !in 200..299) error("HTTP ${conn.responseCode}")
                val total = conn.contentLengthLong.takeIf { it > 0 } ?: release.size
                conn.inputStream.use { input ->
                    target.outputStream().use { out ->
                        val buf = ByteArray(64 * 1024)
                        var read = 0L
                        var lastEmit = 0L
                        while (true) {
                            val n = input.read(buf)
                            if (n < 0) break
                            out.write(buf, 0, n)
                            read += n
                            if (total > 0 && read - lastEmit > 256 * 1024) {
                                lastEmit = read
                                _state.value = State.Downloading(release, (read.toFloat() / total).coerceIn(0f, 1f))
                            }
                        }
                    }
                }
            } finally {
                conn.disconnect()
            }
            _state.value = State.Ready(release, target)
            install(context, target)
        } catch (e: Exception) {
            target.delete()
            _state.value = State.Failed(e.message ?: "Download failed")
        }
    }

    /** Hands the APK to the system installer (Android checks it is signed with the same key). */
    fun install(context: Context, file: File) {
        if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.O && !context.packageManager.canRequestPackageInstalls()) {
            context.startActivity(
                Intent(Settings.ACTION_MANAGE_UNKNOWN_APP_SOURCES, "package:${context.packageName}".toUri())
                    .addFlags(Intent.FLAG_ACTIVITY_NEW_TASK)
            )
            return
        }
        val uri = FileProvider.getUriForFile(context, "${context.packageName}.updates", file)
        context.startActivity(
            Intent(Intent.ACTION_VIEW)
                .setDataAndType(uri, "application/vnd.android.package-archive")
                .addFlags(Intent.FLAG_GRANT_READ_URI_PERMISSION or Intent.FLAG_ACTIVITY_NEW_TASK)
        )
    }

    fun reset() {
        _state.value = State.Idle
    }
}
