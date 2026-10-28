package com.jailbreakchecker.app

import android.app.Application
import androidx.lifecycle.AndroidViewModel
import androidx.lifecycle.viewModelScope
import androidx.work.WorkInfo
import androidx.work.WorkManager
import com.jailbreakchecker.app.ai.AiWorker
import com.jailbreakchecker.app.ai.ClaudeRunner
import com.jailbreakchecker.app.data.AppSettings
import com.jailbreakchecker.app.data.ChangeRecord
import com.jailbreakchecker.app.data.ConsoleInfo
import com.jailbreakchecker.app.data.Guide
import com.jailbreakchecker.app.data.LocalStore
import com.jailbreakchecker.app.data.NewsItem
import com.jailbreakchecker.app.data.Repository
import com.jailbreakchecker.app.data.SettingsStore
import com.jailbreakchecker.app.data.UpdaterMeta
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.flow.Flow
import kotlinx.coroutines.flow.SharingStarted
import kotlinx.coroutines.flow.StateFlow
import kotlinx.coroutines.flow.flowOf
import kotlinx.coroutines.flow.map
import kotlinx.coroutines.flow.stateIn
import kotlinx.coroutines.launch
import kotlinx.coroutines.withContext

class MainViewModel(application: Application) : AndroidViewModel(application) {

    private val repository = Repository(application)
    private val settingsStore = SettingsStore.get(application)

    val brands = repository.bundled.brands.sortedBy { it.order }
    val guidesReviewedAt: String = repository.guidesReviewedAt

    val guides: StateFlow<List<Guide>> = repository.guides()
        .stateIn(viewModelScope, SharingStarted.WhileSubscribed(5_000), repository.bundledGuides)
    val logoIds: Set<String> = repository.logoIds

    val settings: StateFlow<AppSettings> = settingsStore.settings

    val consoles: StateFlow<List<ConsoleInfo>> = repository.consoles()
        .stateIn(viewModelScope, SharingStarted.WhileSubscribed(5_000), repository.bundled.consoles)

    val news: StateFlow<List<NewsItem>> = repository.news()
        .stateIn(viewModelScope, SharingStarted.WhileSubscribed(5_000), emptyList())

    val meta: StateFlow<UpdaterMeta> = repository.meta()
        .stateIn(viewModelScope, SharingStarted.WhileSubscribed(5_000), UpdaterMeta())

    val history: StateFlow<List<ChangeRecord>> = repository.history()
        .stateIn(viewModelScope, SharingStarted.WhileSubscribed(5_000), emptyList())

    /**
     * What is being worked on right now: "scan" for the news search, a console id for a review,
     * or "guide:<id>" for a guide update (queued or running).
     */
    val running: StateFlow<Set<String>> = workInfos()
        .map { infos ->
            infos.filter {
                it.state == WorkInfo.State.RUNNING ||
                    (it.state == WorkInfo.State.ENQUEUED && it.tags.any { t -> t.startsWith("review-") || t.startsWith("guide-") || t == "manual" })
            }
                .map { info ->
                    info.tags.firstOrNull { it.startsWith("review-") }?.removePrefix("review-")
                        ?: info.tags.firstOrNull { it.startsWith("guide-") }?.let { "guide:" + it.removePrefix("guide-") }
                        ?: "scan"
                }
                .toSet()
        }
        .stateIn(viewModelScope, SharingStarted.WhileSubscribed(5_000), emptySet())

    private fun workInfos(): Flow<List<WorkInfo>> =
        runCatching { WorkManager.getInstance(getApplication()).getWorkInfosByTagFlow(AiWorker.TAG_AI) }
            .getOrElse { flowOf(emptyList()) }

    fun runNow() = AiWorker.runNow(getApplication())

    fun reviewNow(consoleId: String) = AiWorker.reviewNow(getApplication(), consoleId)

    fun updateGuides(ids: List<String>) = AiWorker.updateGuides(getApplication(), ids)

    fun updateAllGuides() = updateGuides(guides.value.map { it.id })

    fun saveSettings(settings: AppSettings) {
        settingsStore.save(settings)
        AiWorker.schedule(getApplication())
    }

    /** Checks the key against the API; returns null when it works, or a readable error. */
    suspend fun verifyKey(apiKey: String, model: String): String? = withContext(Dispatchers.IO) {
        runCatching { ClaudeRunner(apiKey.trim(), model).verify() }
            .exceptionOrNull()
            ?.let { e ->
                when (e) {
                    is com.anthropic.errors.UnauthorizedException -> "La API key no es válida."
                    is com.anthropic.errors.NotFoundException -> "Tu cuenta no tiene acceso a ese modelo."
                    is com.anthropic.errors.PermissionDeniedException -> "La API key no tiene permiso para usar ese modelo."
                    is java.io.IOException -> "Sin conexión a internet."
                    else -> e.message ?: e.javaClass.simpleName
                }
            }
    }

    fun clearAiData() = viewModelScope.launch { LocalStore.get(getApplication()).clear() }
}
