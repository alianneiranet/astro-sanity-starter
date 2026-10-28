// Calls Claude with server-side web search and one "submit" tool, and returns that tool's input.

import Anthropic from "@anthropic-ai/sdk";
import { logger } from "firebase-functions";

export interface ToolTaskOptions {
  client: Anthropic;
  model: string;
  effort: "low" | "medium" | "high" | "xhigh" | "max";
  maxSearches: number;
  system: string;
  prompt: string;
  tool: Anthropic.Beta.BetaTool;
  label: string;
}

export interface ToolTaskResult {
  input: unknown | null;
  inputTokens: number;
  outputTokens: number;
  searches: number;
}

const MAX_CONTINUATIONS = 4;

export async function runToolTask(opts: ToolTaskOptions): Promise<ToolTaskResult> {
  const messages: Anthropic.Beta.BetaMessageParam[] = [{ role: "user", content: opts.prompt }];
  const result: ToolTaskResult = { input: null, inputTokens: 0, outputTokens: 0, searches: 0 };

  for (let turn = 0; turn <= MAX_CONTINUATIONS; turn++) {
    const response = await opts.client.beta.messages.create(
      {
        model: opts.model,
        max_tokens: 16000,
        thinking: { type: "adaptive" },
        output_config: { effort: opts.effort },
        // Jailbreak research can trip safety classifiers; let the API retry on its recommended model.
        betas: ["server-side-fallback-2026-07-01"],
        fallbacks: "default",
        system: opts.system,
        tools: [{ type: "web_search_20260209", name: "web_search", max_uses: opts.maxSearches }, opts.tool],
        messages,
      },
      { timeout: 8 * 60 * 1000 },
    );

    result.inputTokens += response.usage.input_tokens + (response.usage.cache_read_input_tokens ?? 0);
    result.outputTokens += response.usage.output_tokens;
    result.searches += response.usage.server_tool_use?.web_search_requests ?? 0;

    if (response.stop_reason === "refusal") {
      logger.warn(`[${opts.label}] request declined`, { details: response.stop_details });
      return result;
    }

    const call = response.content.find(
      (b): b is Anthropic.Beta.BetaToolUseBlock => b.type === "tool_use" && b.name === opts.tool.name,
    );
    if (call) {
      result.input = call.input;
      return result;
    }

    if (response.stop_reason === "pause_turn") {
      // The server-side search loop paused; send the turn back so it resumes where it stopped.
      messages.push({ role: "assistant", content: response.content });
      continue;
    }

    if (response.stop_reason === "end_turn" && turn < MAX_CONTINUATIONS) {
      // Finished without calling the tool: ask once more for the structured result.
      messages.push({ role: "assistant", content: response.content });
      messages.push({
        role: "user",
        content: `Now call the ${opts.tool.name} tool with your findings. Do not search again.`,
      });
      continue;
    }

    logger.warn(`[${opts.label}] stopped without a result`, { stopReason: response.stop_reason });
    return result;
  }
  return result;
}
