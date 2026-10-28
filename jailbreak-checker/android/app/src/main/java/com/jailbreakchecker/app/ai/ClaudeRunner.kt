package com.jailbreakchecker.app.ai

import android.util.Log
import com.anthropic.client.AnthropicClient
import com.anthropic.client.okhttp.AnthropicOkHttpClient
import com.anthropic.core.RequestOptions
import com.anthropic.models.beta.messages.BetaMessageParam
import com.anthropic.models.beta.messages.BetaOutputConfig
import com.anthropic.models.beta.messages.BetaStopReason
import com.anthropic.models.beta.messages.BetaThinkingConfigAdaptive
import com.anthropic.models.beta.messages.BetaTool
import com.anthropic.models.beta.messages.BetaWebSearchTool20250305
import com.anthropic.models.beta.messages.BetaWebSearchTool20260209
import com.anthropic.models.beta.messages.MessageCreateParams
import com.jailbreakchecker.app.data.Repository
import kotlinx.serialization.json.JsonElement
import java.time.Duration

/** One research task: web search plus a single "submit" tool whose input is the answer. */
fun interface Researcher {
    fun run(system: String, prompt: String, tool: BetaTool, effort: BetaOutputConfig.Effort, maxSearches: Long): ClaudeRunner.Result
}

/** [Researcher] backed by the Claude API, called directly from the phone with the user's key. */
class ClaudeRunner(apiKey: String, private val model: String, baseUrl: String? = null) : Researcher {

    data class Result(
        val input: JsonElement?,
        val inputTokens: Long,
        val outputTokens: Long,
        val searches: Long,
    )

    private val client: AnthropicClient = AnthropicOkHttpClient.builder()
        .apiKey(apiKey)
        .maxRetries(3)
        .apply { if (baseUrl != null) baseUrl(baseUrl) }
        .build()

    override fun run(
        system: String,
        prompt: String,
        tool: BetaTool,
        effort: BetaOutputConfig.Effort,
        maxSearches: Long,
    ): Result {
        val messages = mutableListOf<BetaMessageParam>(
            BetaMessageParam.ofUser(prompt),
        )
        var inputTokens = 0L
        var outputTokens = 0L
        var searches = 0L

        repeat(MAX_TURNS) { turn ->
            val params = MessageCreateParams.builder()
                .model(model)
                .maxTokens(16000L)
                .thinking(BetaThinkingConfigAdaptive.builder().build())
                .outputConfig(BetaOutputConfig.builder().effort(effort).build())
                .system(system)
                .apply {
                    if (isHaiku) {
                        // Haiku: basic web search, and no server-side fallback exists for it.
                        addTool(BetaWebSearchTool20250305.builder().maxUses(maxSearches).build())
                    } else {
                        // Jailbreak research can trip safety classifiers: let the API retry on its recommended model.
                        addBeta("server-side-fallback-2026-07-01")
                        fallbacksDefault()
                        addTool(BetaWebSearchTool20260209.builder().maxUses(maxSearches).build())
                    }
                }
                .addTool(tool)
                .messages(messages)
                .build()
            val response = client.beta().messages().create(
                params,
                RequestOptions.builder().timeout(Duration.ofMinutes(6)).build(),
            )

            inputTokens += response.usage().inputTokens()
            outputTokens += response.usage().outputTokens()
            searches += response.usage().serverToolUse().map { it.webSearchRequests() }.orElse(0L)

            val stop = response.stopReason().orElse(null)
            if (stop == BetaStopReason.REFUSAL) {
                Log.w(TAG, "Request declined: ${response.stopDetails()}")
                return Result(null, inputTokens, outputTokens, searches)
            }

            val call = response.content().firstNotNullOfOrNull { block ->
                block.toolUse().orElse(null)?.takeIf { it.name() == tool.name() }
            }
            if (call != null) {
                return Result(toJson(call._input().convert(Any::class.java)), inputTokens, outputTokens, searches)
            }

            when {
                // The server-side search loop paused: send the turn back so it resumes.
                stop == BetaStopReason.PAUSE_TURN -> messages += response.toParam()
                stop == BetaStopReason.END_TURN && turn < MAX_TURNS - 1 -> {
                    messages += response.toParam()
                    messages += BetaMessageParam.ofUser("Now call the ${tool.name()} tool with your findings. Do not search again.")
                }
                else -> {
                    Log.w(TAG, "Stopped without a result: $stop")
                    return Result(null, inputTokens, outputTokens, searches)
                }
            }
        }
        return Result(null, inputTokens, outputTokens, searches)
    }

    private val isHaiku = model.startsWith("claude-haiku")

    /** Checks that the key works (and can use the chosen model) without spending tokens. */
    fun verify() {
        client.models().retrieve(model)
    }

    companion object {
        private const val TAG = "ClaudeRunner"
        private const val MAX_TURNS = 5

        fun toJson(value: Any?): JsonElement = Repository.toJsonElement(value)

        /** Readable Spanish message for API errors the user can fix. */
        fun explain(e: Throwable): String {
            val text = e.message.orEmpty()
            return when {
                "credit balance" in text -> "Tu cuenta de Anthropic no tiene saldo. Añade crédito en console.anthropic.com → Billing (mínimo 5 US$). Mientras tanto la app sigue usando las fuentes gratuitas."
                e is com.anthropic.errors.UnauthorizedException -> "La API key no es válida. Revísala en Ajustes."
                e is com.anthropic.errors.PermissionDeniedException -> "Tu API key no tiene permiso para usar este modelo."
                e is com.anthropic.errors.NotFoundException -> "Tu cuenta no tiene acceso a este modelo; prueba con otro en Ajustes."
                e is com.anthropic.errors.RateLimitException -> "Has llegado al límite de uso de tu cuenta; se reintentará más tarde."
                e is java.io.IOException -> "Sin conexión a internet: se intentará de nuevo."
                else -> text.take(160).ifBlank { e.javaClass.simpleName }
            }
        }
    }
}
