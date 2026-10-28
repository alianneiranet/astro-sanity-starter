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
import com.anthropic.errors.UnauthorizedException
import com.jailbreakchecker.app.MainActivity
import com.jailbreakchecker.app.R
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
        if (!settings.hasApiKey) return Result.success()
        val store = LocalStore.get(applicationContext)
        val repo = Repository(applicationContext)
        val updater = Updater(store, repo.bundled.consoles, repo.guides, ClaudeRunner(settings.apiKey, settings.model))

        return try {
            when (val consoleId = inputData.getString(KEY_CONSOLE)) {
                null -> {
                    val outcome = updater.scan()
                    if (outcome.newNews.isNotEmpty() && settings.notifications) {
                        notify(
                            applicationContext,
                            NOTIFY_NEWS,
                            if (outcome.newNews.size == 1) "Nueva noticia de jailbreak" else "${outcome.newNews.size} noticias nuevas de jailbreak",
                            outcome.newNews.first().title,
                        )
                    }
                    enqueueReviews(applicationContext, outcome.toReview)
                }
                else -> {
                    val change = updater.review(consoleId, inputData.getString(KEY_REASON).orEmpty())
                    if (change != null && settings.notifications) {
                        val name = repo.bundled.consoles.firstOrNull { it.id == consoleId }?.name ?: consoleId
                        notify(applicationContext, consoleId.hashCode(), "$name: estado actualizado", change)
                    }
                }
            }
            Result.success()
        } catch (e: UnauthorizedException) {
            fail(store, "La API key no es válida. Revísala en Ajustes.")
            Result.failure()
        } catch (e: AnthropicServiceException) {
            fail(store, "Error de la API (${e.statusCode()}): ${e.message?.take(160)}")
            if (e.statusCode() == 429 || e.statusCode() >= 500) Result.retry() else Result.failure()
        } catch (e: IOException) {
            fail(store, "Sin conexión: se intentará de nuevo.")
            Result.retry()
        } catch (e: Schemas.InvalidUpdate) {
            // Keep the old data; the console stays due and is retried on a later run.
            Log.w(TAG, "Rejected AI update", e)
            Result.success()
        } catch (e: Exception) {
            Log.e(TAG, "AI check failed", e)
            fail(store, "Error: ${e.message?.take(160)}")
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
            if (!settings.hasApiKey || settings.intervalHours <= 0) {
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
