package com.jailbreakchecker.app.ai

import com.anthropic.models.beta.messages.BetaOutputConfig
import com.jailbreakchecker.app.data.ChangeRecord
import com.jailbreakchecker.app.data.ConsoleInfo
import com.jailbreakchecker.app.data.FeedFetcher
import com.jailbreakchecker.app.data.FeedSource
import com.jailbreakchecker.app.data.Guide
import com.jailbreakchecker.app.data.LocalState
import com.jailbreakchecker.app.data.LocalStore
import com.jailbreakchecker.app.data.NewsItem
import com.jailbreakchecker.app.data.Repository
import java.time.Instant

/**
 * The research loop, run on the phone:
 *  1. scan: one Claude call with web search over the whole catalog -> news + consoles to review
 *  2. review: one Claude call per console that needs it -> validated, complete console data
 * Each step is a separate background job (see [AiWorker]) so none hits WorkManager's time limit.
 */
class Updater(
    private val store: LocalStore,
    private val bundled: List<ConsoleInfo>,
    private val bundledGuides: List<Guide>,
    private val runner: Researcher?,
    private val guidesReviewedAt: String = "",
) {
    private val guides: List<Guide> get() = Repository.mergeGuides(bundledGuides, store.state.value, guidesReviewedAt)

    private fun requireRunner(): Researcher = runner ?: throw IllegalStateException("Sin API key")

    /** Free sources (RSS/Atom), no AI. Returns the items that were not stored yet. */
    suspend fun fetchFeeds(fetcher: FeedFetcher, sources: List<FeedSource>, now: Instant = Instant.now()): List<NewsItem> {
        val result = fetcher.fetchAll(sources, now)
        var added = emptyList<NewsItem>()
        store.update { s ->
            val known = s.news.map { key(it) }.toSet()
            added = result.news.filter { key(it) !in known }.distinctBy { key(it) }
            s.copy(
                news = (added + s.news).sortedByDescending { it.createdAt }.take(MAX_NEWS),
                meta = s.meta.copy(feedsCheckedAt = now.toString(), feedsFailed = result.failed),
            )
        }
        return added
    }
    data class ScanOutcome(val newNews: List<NewsItem>, val toReview: List<Pair<String, String>>)

    private fun consoles(state: LocalState) = Repository.mergeConsoles(bundled, state)

    suspend fun scan(now: Instant = Instant.now()): ScanOutcome {
        val state = store.state.value
        val consoles = consoles(state)
        val result = requireRunner().run(
            system = Prompts.scanSystem(now.toString().take(10)),
            prompt = Prompts.scanPrompt(consoles, state.news.take(40).map { it.title }),
            tool = Schemas.reportScanTool,
            effort = BetaOutputConfig.Effort.MEDIUM,
            maxSearches = 5,
        )
        val stamp = now.toString()
        val input = result.input
        if (input == null) {
            store.update { s -> s.copy(meta = s.meta.copy(lastRunAt = stamp, ok = false, message = "La búsqueda no devolvió resultados.")) }
            return ScanOutcome(emptyList(), emptyList())
        }
        val parsed = Schemas.parseScan(input, consoles.map { it.id }.toSet(), stamp)
        var added = emptyList<NewsItem>()
        store.update { s ->
            val known = s.news.map { key(it) }.toSet()
            added = parsed.news.filter { key(it) !in known }
            s.copy(
                news = (added + s.news).sortedByDescending { it.createdAt }.take(MAX_NEWS),
                meta = s.meta.copy(
                    lastRunAt = stamp,
                    lastSuccessAt = stamp,
                    ok = true,
                    message = "",
                    lastInputTokens = result.inputTokens,
                    lastOutputTokens = result.outputTokens,
                    lastSearches = result.searches,
                ),
            )
        }
        val queue = parsed.review.toMutableList()
        for (c in dueConsoles(consoles, state, now)) {
            if (queue.none { it.first == c.id }) queue += c.id to "Revisión periódica programada (sin noticias concretas)."
        }
        return ScanOutcome(added, queue.take(MAX_REVIEWS_PER_RUN))
    }

    /** Returns the change summary when the console changed, null otherwise. Throws on invalid data. */
    suspend fun review(consoleId: String, reason: String, now: Instant = Instant.now()): String? {
        val current = consoles(store.state.value).firstOrNull { it.id == consoleId } ?: return null
        val result = requireRunner().run(
            system = Prompts.reviewSystem(now.toString().take(10)),
            prompt = Prompts.reviewPrompt(current, reason, guides),
            tool = Schemas.submitConsoleTool(guides.map { it.id }),
            effort = BetaOutputConfig.Effort.HIGH,
            maxSearches = 5,
        )
        val stamp = now.toString()
        val input = result.input ?: throw Schemas.InvalidUpdate("sin respuesta para $consoleId")
        val update = Schemas.parseConsoleUpdate(input, current, guides.map { it.id }.toSet(), stamp)
        store.update { s ->
            val checked = s.lastChecked + (consoleId to stamp)
            if (!update.changed) {
                s.copy(lastChecked = checked)
            } else {
                s.copy(
                    lastChecked = checked,
                    consoles = s.consoles + (consoleId to update.console),
                    history = (listOf(ChangeRecord(consoleId, stamp, update.changeSummary)) + s.history).take(MAX_HISTORY),
                )
            }
        }
        return update.changeSummary.ifBlank { "Información actualizada" }.takeIf { update.changed }
    }

    /** Rewrites one guide with current official information. Returns the change summary, or null. */
    suspend fun updateGuide(guideId: String, now: Instant = Instant.now()): String? {
        val current = guides.firstOrNull { it.id == guideId } ?: return null
        val result = requireRunner().run(
            system = Prompts.guideSystem(now.toString().take(10)),
            prompt = Prompts.guidePrompt(current),
            tool = Schemas.submitGuideTool,
            effort = BetaOutputConfig.Effort.HIGH,
            maxSearches = 8,
        )
        val input = result.input ?: throw Schemas.InvalidUpdate("sin respuesta para la guía $guideId")
        val update = Schemas.parseGuideUpdate(input, current, now.toString())
        // Even "no changes" refreshes the date, so the user sees the guide was checked.
        store.update { s -> s.copy(guides = s.guides + (guideId to update.guide)) }
        return update.changeSummary.ifBlank { "Guía revisada" }.takeIf { update.changed }
    }

    companion object {
        const val MAX_REVIEWS_PER_RUN = 2
        private const val MAX_NEWS = 150
        private const val MAX_HISTORY = 50

        private fun key(n: NewsItem) = n.url.ifBlank { n.title }.lowercase()

        /**
         * Consoles whose last review is older than their checkEveryHours, most overdue first
         * (relative to their interval, so active consoles beat retro ones checked monthly).
         */
        fun dueConsoles(consoles: List<ConsoleInfo>, state: LocalState, now: Instant): List<ConsoleInfo> {
            fun overdue(c: ConsoleInfo): Double {
                val last = state.lastChecked[c.id]?.let { runCatching { Instant.parse(it) }.getOrNull() } ?: Instant.EPOCH
                return (now.toEpochMilli() - last.toEpochMilli()).toDouble() / (c.checkEveryHours.coerceAtLeast(1) * 3_600_000.0)
            }
            return consoles.filter { overdue(it) >= 1.0 }.sortedByDescending { overdue(it) }
        }
    }
}
