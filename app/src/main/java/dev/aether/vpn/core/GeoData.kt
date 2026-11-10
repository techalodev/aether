package dev.aether.vpn.core

import android.content.Context
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.StateFlow
import kotlinx.coroutines.flow.asStateFlow
import kotlinx.coroutines.withContext
import java.io.BufferedInputStream
import java.io.EOFException
import java.io.File
import java.io.InputStream
import java.net.HttpURLConnection
import java.net.URL

/**
 * geoip.dat / geosite.dat, stored in filesDir (the Xray asset directory).
 * Downloaded only on user request — they are large. Sources are configurable.
 */
object GeoData {
    const val GEOIP = "geoip.dat"
    const val GEOSITE = "geosite.dat"
    const val APPROX_SIZE_MB = 100

    data class State(
        val installed: Boolean = false,
        val updatedAt: Long = 0,
        val sizeBytes: Long = 0,
        /** 0..1 while downloading, null otherwise. */
        val progress: Float? = null,
    )

    /** Category names present in the installed files (lower-case). */
    data class Categories(val geoip: Set<String> = emptySet(), val geosite: Set<String> = emptySet())

    private val _state = MutableStateFlow(State())
    val state: StateFlow<State> = _state.asStateFlow()

    @Volatile private var cached: Pair<Long, Categories>? = null

    fun refresh(context: Context) {
        val files = listOf(GEOIP, GEOSITE).map { File(context.filesDir, it) }
        val installed = files.all { it.exists() && it.length() > 0 }
        _state.value = _state.value.copy(
            installed = installed,
            updatedAt = if (installed) files.minOf { it.lastModified() } else 0,
            sizeBytes = files.filter { it.exists() }.sumOf { it.length() },
        )
    }

    /** Scans the .dat files (cheap: only entry headers are read) and caches the result. */
    fun categories(context: Context): Categories {
        val ip = File(context.filesDir, GEOIP)
        val site = File(context.filesDir, GEOSITE)
        val stamp = ip.lastModified() xor (site.lastModified() shl 1) xor ip.length() xor site.length()
        cached?.let { if (it.first == stamp) return it.second }
        val result = Categories(scan(ip), scan(site))
        cached = stamp to result
        return result
    }

    suspend fun download(context: Context, geoipUrl: String, geositeUrl: String) = withContext(Dispatchers.IO) {
        try {
            val items = listOf(geoipUrl to GEOIP, geositeUrl to GEOSITE)
            items.forEachIndexed { index, (url, name) ->
                fetch(url, File(context.filesDir, name)) { p ->
                    _state.value = _state.value.copy(progress = (index + p) / items.size)
                }
            }
        } finally {
            _state.value = _state.value.copy(progress = null)
            cached = null
            refresh(context)
        }
    }

    fun delete(context: Context) {
        listOf(GEOIP, GEOSITE).forEach { File(context.filesDir, it).delete() }
        cached = null
        refresh(context)
    }

    // ---- .dat scanning: repeated field 1 (GeoIP / GeoSite), each starting with field 1 = code ----

    private fun scan(file: File): Set<String> {
        if (!file.exists() || file.length() == 0L) return emptySet()
        return runCatching {
            val codes = HashSet<String>()
            BufferedInputStream(file.inputStream(), 64 * 1024).use { input ->
                while (true) {
                    val tag = input.read()
                    if (tag < 0) break
                    if (tag != 0x0A) break
                    val len = readVarint(input)
                    var consumed = 0L
                    if (input.read() == 0x0A) {
                        consumed++
                        val codeLen = readVarint(input).toInt()
                        consumed += varintSize(codeLen.toLong())
                        val bytes = ByteArray(codeLen)
                        readFully(input, bytes)
                        consumed += codeLen
                        codes += String(bytes).lowercase()
                    } else consumed++
                    skipFully(input, len - consumed)
                }
            }
            codes
        }.getOrDefault(emptySet())
    }

    private fun readVarint(input: InputStream): Long {
        var result = 0L
        var shift = 0
        while (true) {
            val b = input.read()
            if (b < 0) throw EOFException()
            result = result or ((b and 0x7F).toLong() shl shift)
            if (b and 0x80 == 0) return result
            shift += 7
        }
    }

    private fun varintSize(value: Long): Int {
        var v = value
        var n = 1
        while (v >= 0x80) { v = v shr 7; n++ }
        return n
    }

    private fun readFully(input: InputStream, buf: ByteArray) {
        var off = 0
        while (off < buf.size) {
            val n = input.read(buf, off, buf.size - off)
            if (n < 0) throw EOFException()
            off += n
        }
    }

    private fun skipFully(input: InputStream, count: Long) {
        var left = count
        while (left > 0) {
            val n = input.skip(left)
            if (n <= 0) {
                if (input.read() < 0) throw EOFException()
                left--
            } else left -= n
        }
    }

    private fun fetch(url: String, target: File, onProgress: (Float) -> Unit) {
        val conn = (URL(url).openConnection() as HttpURLConnection).apply {
            connectTimeout = 20_000
            readTimeout = 60_000
            instanceFollowRedirects = true
        }
        try {
            if (conn.responseCode !in 200..299) error("HTTP ${conn.responseCode}")
            val length = conn.contentLengthLong.takeIf { it > 0 }
            val tmp = File(target.parentFile, target.name + ".part")
            conn.inputStream.use { input ->
                tmp.outputStream().use { out ->
                    val buf = ByteArray(64 * 1024)
                    var read = 0L
                    while (true) {
                        val n = input.read(buf)
                        if (n < 0) break
                        out.write(buf, 0, n)
                        read += n
                        if (length != null) onProgress((read.toFloat() / length).coerceIn(0f, 1f))
                    }
                }
            }
            if (scan(tmp).isEmpty()) {
                tmp.delete()
                error("${target.name}: not a valid geo file")
            }
            if (!tmp.renameTo(target)) {
                target.delete()
                if (!tmp.renameTo(target)) error("Cannot save ${target.name}")
            }
        } finally {
            conn.disconnect()
        }
    }
}
