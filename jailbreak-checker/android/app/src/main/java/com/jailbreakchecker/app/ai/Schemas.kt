package com.jailbreakchecker.app.ai

import com.anthropic.core.JsonValue
import com.anthropic.models.beta.messages.BetaTool
import com.jailbreakchecker.app.data.ConsoleInfo
import com.jailbreakchecker.app.data.FirmwareRange
import com.jailbreakchecker.app.data.NewsItem
import com.jailbreakchecker.app.data.Source
import com.jailbreakchecker.app.data.StatusInfo
import com.jailbreakchecker.app.data.Summary
import com.jailbreakchecker.app.data.Versions
import kotlinx.serialization.json.JsonArray
import kotlinx.serialization.json.JsonElement
import kotlinx.serialization.json.JsonObject
import kotlinx.serialization.json.JsonPrimitive
import kotlinx.serialization.json.booleanOrNull
import kotlinx.serialization.json.contentOrNull

/**
 * The tools Claude fills in (strict JSON schemas) and the validation that turns their input
 * into app data. Strict mode guarantees the shape; the parsers enforce the rules a schema
 * can't express (known ids, version order, every model covered...).
 */
object Schemas {
    val STATUSES = listOf("green", "orange", "yellow", "red")

    private fun str(description: String) = mapOf("type" to "string", "description" to description)
    private fun statusEnum(description: String) = mapOf("type" to "string", "enum" to STATUSES, "description" to description)
    private fun obj(required: List<String>, properties: Map<String, Any>, description: String? = null) = buildMap {
        put("type", "object")
        put("additionalProperties", false)
        put("required", required)
        put("properties", properties)
        if (description != null) put("description", description)
    }
    private fun array(items: Any, description: String) = mapOf("type" to "array", "items" to items, "description" to description)

    private fun tool(name: String, description: String, schema: Map<String, Any>): BetaTool {
        @Suppress("UNCHECKED_CAST")
        val props = schema["properties"] as Map<String, Any>
        @Suppress("UNCHECKED_CAST")
        val required = schema["required"] as List<String>
        val properties = BetaTool.InputSchema.Properties.builder().apply {
            props.forEach { (key, value) -> putAdditionalProperty(key, JsonValue.from(value)) }
        }.build()
        return BetaTool.builder()
            .name(name)
            .description(description)
            .strict(true)
            .inputSchema(
                BetaTool.InputSchema.builder()
                    .properties(properties)
                    .required(required)
                    .putAdditionalProperty("additionalProperties", JsonValue.from(false))
                    .build(),
            )
            .build()
    }

    const val REPORT_SCAN = "report_scan"
    const val SUBMIT_CONSOLE = "submit_console_update"

    val reportScanTool: BetaTool = tool(
        REPORT_SCAN,
        "Report the result of the news scan. Call exactly once, after searching, even if nothing new was found (use empty arrays).",
        obj(
            listOf("news", "consoles_to_review"),
            mapOf(
                "news" to array(
                    obj(
                        listOf("title", "summary", "console_ids", "status", "url", "source", "published_date"),
                        mapOf(
                            "title" to str("Headline in Spanish, max ~90 characters"),
                            "summary" to str("1-2 sentences in Spanish: what changed and for which firmware/models"),
                            "console_ids" to mapOf("type" to "array", "items" to mapOf("type" to "string"), "description" to "Catalog console ids affected"),
                            "status" to statusEnum("Status this news implies for the affected firmware"),
                            "url" to str("URL of the best source (original announcement if possible)"),
                            "source" to str("Site name, e.g. 'GBAtemp' or 'GitHub'"),
                            "published_date" to str("YYYY-MM-DD"),
                        ),
                    ),
                    "New, relevant, verified news not already published. Max 8.",
                ),
                "consoles_to_review" to array(
                    obj(listOf("console_id", "reason"), mapOf("console_id" to str("Catalog console id"), "reason" to str("Why the stored data looks outdated"))),
                    "Consoles whose stored status is now outdated or wrong.",
                ),
            ),
        ),
    )

    fun submitConsoleTool(guideIds: List<String>): BetaTool {
        val guideId = mapOf("type" to "string", "enum" to listOf("") + guideIds, "description" to "Guide explaining the method, or empty")
        val statusInfo = obj(
            listOf("status", "title", "explanation", "method", "guide_id"),
            mapOf(
                "status" to statusEnum("Traffic light"),
                "title" to str("Short Spanish title"),
                "explanation" to str("2-4 plain Spanish sentences for a non-technical user"),
                "method" to str("Exploit / tool names, e.g. 'GoldHEN + PPPwn'"),
                "guide_id" to guideId,
            ),
        )
        return tool(
            SUBMIT_CONSOLE,
            "Submit the complete, corrected jailbreak status of the console under review. Call exactly once. " +
                "If nothing needs to change, set changed=false and resend the current data.",
            obj(
                listOf("changed", "change_summary", "summary_status", "summary_text", "latest_firmware", "mode", "all_versions", "variants", "firmwares", "sources"),
                mapOf(
                    "changed" to mapOf("type" to "boolean", "description" to "true only for a material change"),
                    "change_summary" to str("Spanish, one sentence describing what changed (empty if nothing)"),
                    "summary_status" to statusEnum("Best realistic status a user can get today on this console"),
                    "summary_text" to str("Spanish, max ~60 characters"),
                    "latest_firmware" to str("Latest official firmware, or empty if not applicable"),
                    "mode" to mapOf("type" to "string", "enum" to listOf("all", "firmware"), "description" to "'all' only when every version of every model shares one status"),
                    "all_versions" to statusInfo + ("description" to "Used when mode='all'; otherwise repeat the summary"),
                    "variants" to array(
                        obj(
                            listOf("variant_id", "single_status", "status_info"),
                            mapOf(
                                "variant_id" to str("Existing variant id"),
                                "single_status" to mapOf("type" to "boolean", "description" to "true when firmware doesn't matter for this model"),
                                "status_info" to statusInfo,
                            ),
                        ),
                        "One entry per existing hardware model (empty if the console has no models).",
                    ),
                    "firmwares" to array(
                        obj(
                            listOf("id", "label", "variant", "min", "max", "status", "title", "explanation", "method", "guide_id"),
                            mapOf(
                                "id" to str("Stable id; keep existing ids"),
                                "label" to str("What the user sees, e.g. '9.03 – 11.00' or '14.20 o superior'"),
                                "variant" to str("Existing variant id, or empty for all models"),
                                "min" to str("Inclusive minimum version, or empty"),
                                "max" to str("Inclusive maximum version, or empty"),
                                "status" to statusEnum("Status for this range"),
                                "title" to str("Short Spanish title"),
                                "explanation" to str("2-4 plain Spanish sentences"),
                                "method" to str("Exploit / tool names, or '—'"),
                                "guide_id" to guideId,
                            ),
                        ),
                        "Ordered firmware ranges (oldest first). Empty when mode='all'.",
                    ),
                    "sources" to array(obj(listOf("title", "url"), mapOf("title" to str("Source title"), "url" to str("https URL"))), "1-4 reputable sources"),
                ),
            ),
        )
    }

    // ---- parsing ---------------------------------------------------------------------------

    data class ScanResult(val news: List<NewsItem>, val review: List<Pair<String, String>>)

    data class ConsoleUpdate(val changed: Boolean, val changeSummary: String, val console: ConsoleInfo)

    class InvalidUpdate(message: String) : Exception(message)

    private fun JsonObject.text(key: String, max: Int = 600): String =
        ((this[key] as? JsonPrimitive)?.contentOrNull ?: "").trim().take(max)

    private fun JsonObject.array(key: String): List<JsonObject> =
        (this[key] as? JsonArray)?.mapNotNull { it as? JsonObject }.orEmpty()

    private fun isHttpUrl(url: String) = Regex("^https?://\\S+$", RegexOption.IGNORE_CASE).matches(url)

    private fun status(value: String): String? = value.takeIf { it in STATUSES }

    fun parseScan(input: JsonElement, knownIds: Set<String>, now: String): ScanResult {
        val root = input as? JsonObject ?: return ScanResult(emptyList(), emptyList())
        val news = root.array("news").take(8).mapNotNull { n ->
            val ids = (n["console_ids"] as? JsonArray).orEmpty()
                .mapNotNull { (it as? JsonPrimitive)?.contentOrNull?.trim() }
                .filter { it in knownIds }
            val item = NewsItem(
                title = n.text("title", 160),
                summary = n.text("summary", 500),
                consoleIds = ids,
                status = status(n.text("status")) ?: "unknown",
                url = n.text("url", 500).takeIf(::isHttpUrl).orEmpty(),
                source = n.text("source", 60),
                publishedAt = n.text("published_date", 10).takeIf { Regex("\\d{4}-\\d{2}-\\d{2}").matches(it) }.orEmpty(),
                createdAt = now,
            )
            item.takeIf { it.title.isNotBlank() && it.summary.isNotBlank() && it.consoleIds.isNotEmpty() }
        }
        val review = root.array("consoles_to_review")
            .map { it.text("console_id", 40) to it.text("reason", 300) }
            .filter { it.first in knownIds }
            .distinctBy { it.first }
        return ScanResult(news, review)
    }

    private fun parseStatusInfo(o: JsonObject?, guideIds: Set<String>): StatusInfo? {
        o ?: return null
        val s = status(o.text("status")) ?: return null
        return StatusInfo(
            status = s,
            title = o.text("title", 120),
            explanation = o.text("explanation", 900),
            method = o.text("method", 160),
            guideId = o.text("guide_id", 60).takeIf { it in guideIds }.orEmpty(),
        )
    }

    /** Validates Claude's submission and merges it into [current]; throws [InvalidUpdate] if unusable. */
    fun parseConsoleUpdate(input: JsonElement, current: ConsoleInfo, guideIds: Set<String>, now: String): ConsoleUpdate {
        val o = input as? JsonObject ?: throw InvalidUpdate("input is not an object")
        val mode = if (o.text("mode") == "all") "all" else "firmware"
        val summaryStatus = status(o.text("summary_status")) ?: throw InvalidUpdate("summary_status missing")
        val summaryText = o.text("summary_text", 120).ifBlank { throw InvalidUpdate("summary_text missing") }

        val allVersions = if (mode == "all") {
            parseStatusInfo(o["all_versions"] as? JsonObject, guideIds)
                ?.takeIf { it.explanation.isNotBlank() }
                ?: throw InvalidUpdate("all_versions missing")
        } else {
            null
        }

        // Variants: names are fixed, only their single status may change.
        val submitted = o.array("variants").associateBy { it.text("variant_id", 40) }
        val variants = current.variants.map { v ->
            val s = submitted[v.id] ?: return@map v
            val single = (s["single_status"] as? JsonPrimitive)?.booleanOrNull == true
            v.copy(allVersions = if (single) parseStatusInfo(s["status_info"] as? JsonObject, guideIds) ?: v.allVersions else null)
        }
        val variantIds = variants.map { it.id }.toSet()

        val usedIds = mutableSetOf<String>()
        val firmwares = if (mode == "all") emptyList() else o.array("firmwares").mapIndexed { index, f ->
            val label = f.text("label", 80).ifBlank { throw InvalidUpdate("firmware #$index has no label") }
            val min = f.text("min", 40)
            val max = f.text("max", 40)
            val variant = f.text("variant", 40)
            val st = status(f.text("status")) ?: throw InvalidUpdate("firmware $label has an invalid status")
            if (variant.isNotBlank() && variant !in variantIds) throw InvalidUpdate("firmware $label uses unknown variant $variant")
            if (min.isNotBlank() && max.isNotBlank() && Versions.compare(min, max) > 0) throw InvalidUpdate("firmware $label: min > max")
            var id = f.text("id", 60).replace(Regex("[^a-zA-Z0-9_-]"), "-").ifBlank { "${current.id}-$index" }
            while (!usedIds.add(id)) id += "x"
            FirmwareRange(
                id = id,
                label = label,
                variant = variant,
                min = min,
                max = max,
                status = st,
                title = f.text("title", 120),
                explanation = f.text("explanation", 900),
                method = f.text("method", 160).ifBlank { "—" },
                guideId = f.text("guide_id", 60).takeIf { it in guideIds }.orEmpty(),
            )
        }

        if (mode == "firmware") {
            val needRanges = if (variants.isEmpty()) listOf("") else variants.filter { it.allVersions == null }.map { it.id }
            for (v in needRanges) {
                if (firmwares.none { it.variant.isBlank() || it.variant == v }) {
                    throw InvalidUpdate(if (v.isBlank()) "mode=firmware but no firmware ranges" else "no firmware range covers model $v")
                }
            }
        }

        val sources = o.array("sources")
            .map { Source(it.text("title", 120), it.text("url", 500)) }
            .filter { isHttpUrl(it.url) }
            .take(4)

        return ConsoleUpdate(
            changed = (o["changed"] as? JsonPrimitive)?.booleanOrNull == true,
            changeSummary = o.text("change_summary", 300),
            console = current.copy(
                mode = mode,
                latestFirmware = o.text("latest_firmware", 40),
                summary = Summary(summaryStatus, summaryText),
                allVersions = allVersions,
                variants = variants,
                firmwares = firmwares,
                sources = sources.ifEmpty { current.sources },
                updatedAt = now,
            ),
        )
    }
}
