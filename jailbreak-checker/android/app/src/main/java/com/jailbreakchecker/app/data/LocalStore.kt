package com.jailbreakchecker.app.data

import android.content.Context
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.StateFlow
import kotlinx.coroutines.flow.asStateFlow
import kotlinx.coroutines.sync.Mutex
import kotlinx.coroutines.sync.withLock
import kotlinx.coroutines.withContext
import kotlinx.serialization.Serializable
import java.io.File

/** Everything the on-device AI has learned, persisted as one JSON file in app storage. */
@Serializable
data class LocalState(
    /** Consoles rewritten by the AI, by id. They replace the bundled entry while they are newer. */
    val consoles: Map<String, ConsoleInfo> = emptyMap(),
    /** When each console was last reviewed by the AI (ISO-8601), by id. */
    val lastChecked: Map<String, String> = emptyMap(),
    val news: List<NewsItem> = emptyList(),
    val history: List<ChangeRecord> = emptyList(),
    val meta: UpdaterMeta = UpdaterMeta(),
)

/**
 * Process-wide store shared by the UI and the background worker, so a finished AI check
 * shows up on screen immediately.
 */
class LocalStore private constructor(private val file: File) {

    private val mutex = Mutex()
    private val _state = MutableStateFlow(read())
    val state: StateFlow<LocalState> = _state.asStateFlow()

    private fun read(): LocalState =
        runCatching { Repository.json.decodeFromString(LocalState.serializer(), file.readText()) }
            .getOrDefault(LocalState())

    suspend fun update(transform: (LocalState) -> LocalState): LocalState = mutex.withLock {
        val next = transform(_state.value)
        withContext(Dispatchers.IO) {
            val tmp = File(file.parentFile, file.name + ".tmp")
            tmp.writeText(Repository.json.encodeToString(LocalState.serializer(), next))
            if (!tmp.renameTo(file)) {
                file.writeText(tmp.readText())
                tmp.delete()
            }
        }
        _state.value = next
        next
    }

    suspend fun clear() = update { LocalState() }

    companion object {
        @Volatile
        private var instance: LocalStore? = null

        fun get(context: Context): LocalStore =
            instance ?: synchronized(this) {
                instance ?: LocalStore(File(context.applicationContext.filesDir, "ai-state.json")).also { instance = it }
            }
    }
}
