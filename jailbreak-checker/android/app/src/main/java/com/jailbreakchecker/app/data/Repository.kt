package com.jailbreakchecker.app.data

import android.content.Context
import kotlinx.coroutines.flow.Flow
import kotlinx.coroutines.flow.map
import kotlinx.serialization.json.Json
import kotlinx.serialization.json.JsonArray
import kotlinx.serialization.json.JsonElement
import kotlinx.serialization.json.JsonNull
import kotlinx.serialization.json.JsonObject
import kotlinx.serialization.json.JsonPrimitive

/**
 * Source of truth for the UI: the bundled catalog (assets/consoles.json + guides.json),
 * overlaid with whatever the on-device AI has updated since (see [LocalStore]).
 */
class Repository(private val context: Context) {

    private val store = LocalStore.get(context)

    val bundled: Catalog by lazy { loadCatalog(context) }

    private val guideCatalog: GuideCatalog by lazy {
        val text = context.assets.open("guides.json").bufferedReader().use { it.readText() }
        json.decodeFromString(GuideCatalog.serializer(), text)
    }

    /** Guides as shipped with the app (the AI may have newer versions, see [guides]). */
    val bundledGuides: List<Guide> get() = guideCatalog.guides

    /** When the bundled guides were last checked against the official sources (YYYY-MM-DD). */
    val guidesReviewedAt: String get() = guideCatalog.reviewedAt

    val feedSources: List<FeedSource> by lazy {
        val text = context.assets.open("sources.json").bufferedReader().use { it.readText() }
        json.decodeFromString(FeedSourceList.serializer(), text).sources
    }

    fun guides(): Flow<List<Guide>> = store.state.map { mergeGuides(bundledGuides, it, guidesReviewedAt) }

    /** Console ids that have an SVG logo in assets/logos. */
    val logoIds: Set<String> by lazy {
        context.assets.list("logos").orEmpty().filter { it.endsWith(".svg") }.map { it.removeSuffix(".svg") }.toSet()
    }

    fun consoles(): Flow<List<ConsoleInfo>> = store.state.map { mergeConsoles(bundled.consoles, it) }

    fun news(): Flow<List<NewsItem>> = store.state.map { it.news }

    fun meta(): Flow<UpdaterMeta> = store.state.map { it.meta }

    fun history(): Flow<List<ChangeRecord>> = store.state.map { it.history }

    companion object {
        val json = Json {
            ignoreUnknownKeys = true
            coerceInputValues = true
            explicitNulls = false
            encodeDefaults = true
        }

        fun loadCatalog(context: Context): Catalog {
            val text = context.assets.open("consoles.json").bufferedReader().use { it.readText() }
            return json.decodeFromString(Catalog.serializer(), text)
        }

        /** Converts plain Java/Kotlin values (maps, lists, strings, numbers...) into a JSON tree. */
        fun toJsonElement(value: Any?): JsonElement = when (value) {
            null -> JsonNull
            is JsonElement -> value
            is String -> JsonPrimitive(value)
            is Boolean -> JsonPrimitive(value)
            is Number -> JsonPrimitive(value)
            is Map<*, *> -> JsonObject(value.entries.associate { (k, v) -> k.toString() to toJsonElement(v) })
            is Iterable<*> -> JsonArray(value.map { toJsonElement(it) })
            else -> JsonPrimitive(value.toString())
        }

        /**
         * A rewritten guide wins only if it is newer than the review of the guides shipped with the app.
         * Imported guides with new ids are added at the end.
         */
        fun mergeGuides(bundled: List<Guide>, state: LocalState, bundledReviewedAt: String = ""): List<Guide> {
            val ids = bundled.map { it.id }.toSet()
            val merged = bundled.map { base -> state.guides[base.id]?.takeIf { it.updatedAt > bundledReviewedAt } ?: base }
            return merged + state.guides.values.filter { it.id !in ids }.sortedBy { it.title }
        }

        /**
         * An AI-updated console wins only while it is newer than the bundled one, so a new
         * app version with corrected data replaces older AI results.
         */
        fun mergeConsoles(bundled: List<ConsoleInfo>, state: LocalState): List<ConsoleInfo> =
            bundled.map { base ->
                val ai = state.consoles[base.id]
                val chosen = if (ai != null && ai.updatedAt > base.updatedAt) ai else base
                chosen.copy(lastCheckedAt = state.lastChecked[base.id] ?: chosen.lastCheckedAt)
            }
    }
}
