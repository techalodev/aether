package dev.aether.vpn.data

import android.content.Context
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.StateFlow
import kotlinx.coroutines.flow.asStateFlow
import kotlinx.coroutines.sync.Mutex
import kotlinx.coroutines.sync.withLock
import kotlinx.coroutines.withContext
import kotlinx.serialization.json.Json
import java.io.File

/** Single source of truth for profiles, subscriptions and settings, persisted as JSON. */
object Repository {
    private val json = Json {
        ignoreUnknownKeys = true
        encodeDefaults = true
    }
    private val mutex = Mutex()
    private lateinit var file: File

    private val _data = MutableStateFlow(AppData())
    val data: StateFlow<AppData> = _data.asStateFlow()

    fun init(context: Context) {
        if (::file.isInitialized) return
        file = File(context.filesDir, "state.json")
        _data.value = runCatching { json.decodeFromString<AppData>(file.readText()) }
            .getOrDefault(AppData())
    }

    suspend fun update(transform: (AppData) -> AppData) = mutex.withLock {
        val next = transform(_data.value)
        if (next == _data.value) return@withLock
        _data.value = next
        withContext(Dispatchers.IO) {
            val tmp = File(file.parentFile, file.name + ".tmp")
            tmp.writeText(json.encodeToString(AppData.serializer(), next))
            if (!tmp.renameTo(file)) {
                file.delete()
                tmp.renameTo(file)
            }
        }
    }

    suspend fun updateSettings(transform: (Settings) -> Settings) =
        update { it.copy(settings = transform(it.settings)) }
}
