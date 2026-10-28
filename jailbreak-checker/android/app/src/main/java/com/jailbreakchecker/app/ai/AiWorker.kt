package com.jailbreakchecker.app.ai

import android.Manifest
import android.app.NotificationChannel
import android.app.NotificationManager
import android.app.PendingIntent
import android.content.Context
import android.content.Intent
import android.content.pm.PackageManager
import android.util.Log
import androidx.core.app.NotificationCompat
import androidx.core.app.NotificationManagerCompat
import androidx.core.content.ContextCompat
import androidx.work.BackoffPolicy
import androidx.work.Constraints
import androidx.work.CoroutineWorker
import androidx.work.ExistingPeriodicWorkPolicy
import androidx.work.ExistingWorkPolicy
import androidx.work.NetworkType
import androidx.work.OneTimeWorkRequestBuilder
import androidx.work.PeriodicWorkRequestBuilder
import androidx.work.WorkManager
import androidx.work.WorkerParameters
import androidx.work.workDataOf
import com.anthropic.errors.AnthropicServiceException
import com.jailbreakchecker.app.MainActivity
import com.jailbreakchecker.app.R
import com.jailbreakchecker.app.data.FeedFetcher
import com.jailbreakchecker.app.data.LocalStore
import com.jailbreakchecker.app.data.Repository
import com.jailbreakchecker.app.data.SettingsStore
import java.io.IOException
import java.time.Instant
import java.util.concurrent.TimeUnit

/** Background job: either the news scan (which then queues reviews) or one console review. */
class AiWorker(context: Context, params: WorkerParameters) : CoroutineWorker(context, params) {

    override suspend fun doWork(): Result {
        val settings = SettingsStore.get(applicationContext).settings.value
        val store = LocalStore.get(applicationContext)
        val repo = Repository(applicationContext)
        val runner = if (settings.hasApiKey) ClaudeRunner(settings.apiKey, settings.model) else null
        val updater = Updater(store, repo.bundled.consoles, repo.bundledGuides, runner, repo.guidesReviewedAt)
        val consoleId = inputData.getString(KEY_CONSOLE)
        val guideId = inputData.getString(KEY_GUIDE)

        return try {
            when {
                guideId != null -> {
                    if (runner == null) return Result.success()
                    val change = updater.updateGuide(guideId)
                    if (change != null && settings.notifications && inputData.getBoolean(KEY_NOTIFY_GUIDE, false)) {
                        notify(applicationContext, guideId.hashCode(), "Guía actualizada", change)
                    }
                }
                consoleId != null -> {
                    if (runner == null) return Result.success()
                    val change = updater.review(consoleId, inputData.getString(KEY_REASON).orEmpty())
                    if (change != null && settings.notifications) {
                        val name = repo.bundled.consoles.firstOrNull { it.id == consoleId }?.name ?: consoleId
                        notify(applicationContext, consoleId.hashCode(), "$name: estado actualizado", change)
                    }
                }
                else -> {
                    // 1) free sources, always (no API key, no cost)
                    val fromFeeds = runCatching { updater.fetchFeeds(FeedFetcher(), repo.feedSources) }.getOrDefault(emptyList())
                    // 2) the AI scan, only with an API key; a failure here doesn't lose the free news
                    var fromAi = emptyList<com.jailbreakchecker.app.data.NewsItem>()
                    if (runner != null) {
                        try {
                            val outcome = updater.scan()
                            fromAi = outcome.newNews
                            enqueueReviews(applicationContext, outcome.toReview)
                        } catch (e: Exception) {
                            Log.w(TAG, "AI scan failed", e)
                            fail(store, ClaudeRunner.explain(e))
                        }
                    }
                    val fresh = fromAi + fromFeeds
                    if (fresh.isNotEmpty() && settings.notifications) {
                        notify(
                            applicationContext,
                            NOTIFY_NEWS,
                            if (fresh.size == 1) "Nueva noticia" else "${fresh.size} noticias nuevas",
                            fresh.first().title,
                        )
                    }
                }
            }
            Result.success()
        } catch (e: Schemas.InvalidUpdate) {
            // Keep the old data; it is retried on a later run.
            Log.w(TAG, "Rejected AI update", e)
            Result.success()
        } catch (e: AnthropicServiceException) {
            fail(store, ClaudeRunner.explain(e))
            if (e.statusCode() == 429 || e.statusCode() >= 500) Result.retry() else Result.failure()
        } catch (e: IOException) {
            fail(store, ClaudeRunner.explain(e))
            Result.retry()
        } catch (e: Exception) {
            Log.e(TAG, "AI task failed", e)
            fail(store, ClaudeRunner.explain(e))
            Result.failure()
        }
    }

    private suspend fun fail(store: LocalStore, message: String) {
        val now = Instant.now().toString()
        store.update { it.copy(meta = it.meta.copy(lastRunAt = now, ok = false, message = message)) }
    }

    companion object {
        private const val TAG = "AiWorker"
        const val TAG_AI = "jb-ai"
        private const val KEY_CONSOLE = "consoleId"
        private const val KEY_REASON = "reason"
        private const val KEY_GUIDE = "guideId"
        private const val KEY_NOTIFY_GUIDE = "notifyGuide"
        private const val GUIDES = "jb-guides"
        private const val PERIODIC = "jb-periodic"
        private const val MANUAL = "jb-manual"
        private const val REVIEWS = "jb-reviews"
        private const val CHANNEL = "updates"
        private const val NOTIFY_NEWS = 1

        private val constraints = Constraints.Builder().setRequiredNetworkType(NetworkType.CONNECTED).build()

        /** Applies the user's interval: (re)schedules the periodic scan or cancels it. */
        fun schedule(context: Context) {
            val settings = SettingsStore.get(context).settings.value
            val wm = runCatching { WorkManager.getInstance(context) }.getOrNull() ?: return
            // Free sources work without an API key, so the periodic check only depends on the interval.
            if (settings.intervalHours <= 0) {
                wm.cancelUniqueWork(PERIODIC)
                return
            }
            val request = PeriodicWorkRequestBuilder<AiWorker>(settings.intervalHours.toLong(), TimeUnit.HOURS)
                .setConstraints(constraints)
                .setBackoffCriteria(BackoffPolicy.EXPONENTIAL, 10, TimeUnit.MINUTES)
                .addTag(TAG_AI)
                .build()
            wm.enqueueUniquePeriodicWork(PERIODIC, ExistingPeriodicWorkPolicy.UPDATE, request)
        }

        /** "Buscar novedades ahora". */
        fun runNow(context: Context) {
            val request = OneTimeWorkRequestBuilder<AiWorker>()
                .setConstraints(constraints)
                .addTag(TAG_AI)
                .addTag("manual")
                .build()
            WorkManager.getInstance(context).enqueueUniqueWork(MANUAL, ExistingWorkPolicy.KEEP, request)
        }

        /** "Actualizar guías": one background job per guide, run one after another. */
        fun updateGuides(context: Context, guideIds: List<String>) {
            if (guideIds.isEmpty()) return
            val requests = guideIds.map { id ->
                OneTimeWorkRequestBuilder<AiWorker>()
                    .setConstraints(constraints)
                    .setInputData(workDataOf(KEY_GUIDE to id, KEY_NOTIFY_GUIDE to (guideIds.size == 1)))
                    .addTag(TAG_AI)
                    .addTag("guide-$id")
                    .build()
            }
            WorkManager.getInstance(context).beginUniqueWork(GUIDES, ExistingWorkPolicy.APPEND_OR_REPLACE, requests.first())
                .let { requests.drop(1).fold(it) { chain, r -> chain.then(r) } }
                .enqueue()
        }

        /** "Revisar esta consola ahora" from the console screen. */
        fun reviewNow(context: Context, consoleId: String) =
            enqueueReviews(context, listOf(consoleId to "El usuario pidió revisar esta consola ahora."))

        private fun enqueueReviews(context: Context, items: List<Pair<String, String>>) {
            if (items.isEmpty()) return
            val requests = items.map { (id, reason) ->
                OneTimeWorkRequestBuilder<AiWorker>()
                    .setConstraints(constraints)
                    .setInputData(workDataOf(KEY_CONSOLE to id, KEY_REASON to reason))
                    .addTag(TAG_AI)
                    .addTag("review-$id")
                    .build()
            }
            WorkManager.getInstance(context).beginUniqueWork(REVIEWS, ExistingWorkPolicy.APPEND_OR_REPLACE, requests.first())
                .let { requests.drop(1).fold(it) { chain, r -> chain.then(r) } }
                .enqueue()
        }

        fun createChannel(context: Context) {
            val channel = NotificationChannel(CHANNEL, "Novedades de jailbreak", NotificationManager.IMPORTANCE_DEFAULT)
            context.getSystemService(NotificationManager::class.java).createNotificationChannel(channel)
        }

        private fun notify(context: Context, id: Int, title: String, text: String) {
            if (ContextCompat.checkSelfPermission(context, Manifest.permission.POST_NOTIFICATIONS) != PackageManager.PERMISSION_GRANTED &&
                android.os.Build.VERSION.SDK_INT >= 33
            ) {
                return
            }
            val open = PendingIntent.getActivity(
                context,
                0,
                Intent(context, MainActivity::class.java),
                PendingIntent.FLAG_IMMUTABLE or PendingIntent.FLAG_UPDATE_CURRENT,
            )
            val notification = NotificationCompat.Builder(context, CHANNEL)
                .setSmallIcon(R.drawable.ic_notification)
                .setContentTitle(title)
                .setContentText(text)
                .setStyle(NotificationCompat.BigTextStyle().bigText(text))
                .setContentIntent(open)
                .setAutoCancel(true)
                .build()
            runCatching { NotificationManagerCompat.from(context).notify(id, notification) }
        }
    }
}
