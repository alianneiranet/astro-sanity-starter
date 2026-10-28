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
                // Jailbreak research can trip safety classifiers: let the API retry on its recommended model.
                .addBeta("server-side-fallback-2026-07-01")
                .fallbacksDefault()
                .system(system)
                .addTool(BetaWebSearchTool20260209.builder().maxUses(maxSearches).build())
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

    /** Checks that the key works (and can use the chosen model) without spending tokens. */
    fun verify() {
        client.models().retrieve(model)
    }

    companion object {
        private const val TAG = "ClaudeRunner"
        private const val MAX_TURNS = 5

        fun toJson(value: Any?): JsonElement = Repository.toJsonElement(value)
    }
}
