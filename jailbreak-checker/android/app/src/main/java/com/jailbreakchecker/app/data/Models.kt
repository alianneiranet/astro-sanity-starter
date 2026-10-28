package com.jailbreakchecker.app.data

import kotlinx.serialization.Serializable

/** Jailbreak traffic light. Stored lowercase in JSON / Firestore ("green", "orange"...). */
enum class Status {
    GREEN, ORANGE, YELLOW, RED, UNKNOWN;

    companion object {
        fun parse(value: String?): Status =
            entries.firstOrNull { it.name.equals(value?.trim(), ignoreCase = true) } ?: UNKNOWN
    }
}

@Serializable
data class Catalog(
    val seedVersion: Int = 0,
    val generatedAt: String = "",
    val brands: List<Brand> = emptyList(),
    val consoles: List<ConsoleInfo> = emptyList(),
)

@Serializable
data class Brand(
    val id: String,
    val name: String,
    val company: String = "",
    val color: String = "#455A64",
    val order: Int = 99,
    val description: String = "",
)

@Serializable
data class Summary(
    val status: String = "unknown",
    val text: String = "",
)

@Serializable
data class StatusInfo(
    val status: String = "unknown",
    val title: String = "",
    val explanation: String = "",
    val method: String = "",
    /** Id of a [Guide] explaining how to do it; empty if there is none. */
    val guideId: String = "",
)

@Serializable
data class Variant(
    val id: String,
    val name: String,
    val description: String = "",
    /** When set, this model has one status for every firmware and the firmware question is skipped. */
    val allVersions: StatusInfo? = null,
)

@Serializable
data class FirmwareRange(
    val id: String,
    val label: String,
    /** Variant id this range applies to; empty = applies to every model. */
    val variant: String = "",
    /** Inclusive lower bound; empty = no lower bound. */
    val min: String = "",
    /** Inclusive upper bound; empty = no upper bound. */
    val max: String = "",
    val status: String = "unknown",
    val title: String = "",
    val explanation: String = "",
    val method: String = "",
    val guideId: String = "",
) {
    val isCatchAll: Boolean get() = min.isBlank() && max.isBlank()
}

@Serializable
data class Source(
    val title: String = "",
    val url: String = "",
)

@Serializable
data class ConsoleInfo(
    val id: String,
    val brandId: String,
    val name: String,
    val shortName: String = name,
    val year: Int = 0,
    /** home | handheld | hybrid | clamshell | retro — picks the drawn icon. */
    val formFactor: String = "home",
    val legacy: Boolean = false,
    /** "all" = every version shares one status (no firmware question); "firmware" = ask. */
    val mode: String = "firmware",
    val latestFirmware: String = "",
    val checkEveryHours: Int = 24,
    val summary: Summary = Summary(),
    val versionHint: String = "",
    val allVersions: StatusInfo? = null,
    val variants: List<Variant> = emptyList(),
    val firmwares: List<FirmwareRange> = emptyList(),
    val sources: List<Source> = emptyList(),
    val updatedAt: String = "",
    val lastCheckedAt: String = "",
) {
    val asksFirmware: Boolean get() = mode != "all" && firmwares.isNotEmpty()

    fun rangesFor(variantId: String?): List<FirmwareRange> =
        if (variants.isEmpty() || variantId == null) firmwares
        else firmwares.filter { it.variant.isBlank() || it.variant == variantId }
}

@Serializable
data class NewsItem(
    val id: String = "",
    val title: String = "",
    val summary: String = "",
    val consoleIds: List<String> = emptyList(),
    val status: String = "unknown",
    val url: String = "",
    val source: String = "",
    val publishedAt: String = "",
    val createdAt: String = "",
    /** jailbreak | emuladores | herramientas */
    val category: String = "jailbreak",
    /** "ia" (found by the AI) or "feed" (free RSS/Atom source) */
    val origin: String = "ia",
)

@Serializable
data class UpdaterMeta(
    val lastRunAt: String = "",
    val lastSuccessAt: String = "",
    val ok: Boolean = true,
    val message: String = "",
    val lastInputTokens: Long = 0,
    val lastOutputTokens: Long = 0,
    val lastSearches: Long = 0,
    /** Free RSS/Atom sources: last time they were read and which ones failed. */
    val feedsCheckedAt: String = "",
    val feedsFailed: List<String> = emptyList(),
)

/** One AI change to a console, kept for the "historial" list. */
@Serializable
data class ChangeRecord(
    val consoleId: String,
    val at: String,
    val summary: String,
)

@Serializable
data class GuideStep(
    val title: String,
    val text: String,
)

@Serializable
data class Guide(
    val id: String,
    val title: String,
    val consoleIds: List<String> = emptyList(),
    /** facil | media | dificil */
    val difficulty: String = "media",
    val time: String = "",
    val summary: String = "",
    val requirements: List<String> = emptyList(),
    val steps: List<GuideStep> = emptyList(),
    val warnings: List<String> = emptyList(),
    val links: List<Source> = emptyList(),
    /** Set when the AI rewrote the guide (ISO-8601); empty for the bundled version. */
    val updatedAt: String = "",
)

@Serializable
data class GuideCatalog(
    /** Date (YYYY-MM-DD) the bundled guides were checked against the official sources. */
    val reviewedAt: String = "",
    val guides: List<Guide> = emptyList(),
)
