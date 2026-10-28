package com.jailbreakchecker.app.data

import android.util.Base64
import com.jailbreakchecker.app.ai.Schemas
import kotlinx.serialization.json.JsonArray
import kotlinx.serialization.json.JsonElement
import kotlinx.serialization.json.JsonNull
import kotlinx.serialization.json.JsonObject
import kotlinx.serialization.json.JsonPrimitive
import kotlinx.serialization.json.contentOrNull
import java.io.File
import java.security.MessageDigest
import java.text.Normalizer
import java.time.Instant

/**
 * Imports the JSON an external AI (ChatGPT, Gemini, Claude…) produced with the prompt from
 * [ImportPrompt]. Format "revisor-jailbreak/1":
 *
 * {
 *   "format": "revisor-jailbreak/1",
 *   "consoleId": "switch1",              // optional: detected from consoleName if missing/unknown
 *   "consoleName": "Nintendo Switch",
 *   "checkedAt": "2026-10-29",
 *   "console": { summary, latestFirmware, mode, allVersions, variants[{id, allVersions}], firmwares[], sources[] },  // optional
 *   "guides": [ { id, title, difficulty, time, summary, requirements[], steps[{title, text, image, imageCaption}], warnings[], links[] } ]
 * }
 */
object Importer {

    const val FORMAT = "revisor-jailbreak/1"
    private const val MAX_IMAGE_BYTES = 4 * 1024 * 1024

    data class Preview(
        val console: ConsoleInfo?,
        /** How the console was recognised, shown to the user. */
        val detectedBy: String,
        val consoleUpdate: ConsoleInfo?,
        val guides: List<Guide>,
        val warnings: List<String>,
    ) {
        val isEmpty: Boolean get() = consoleUpdate == null && guides.isEmpty()
    }

    class ImportException(message: String) : Exception(message)

    /** Accepts raw JSON or a chat answer with ```json fences or extra text around it. */
    fun extractJson(text: String): JsonObject {
        val cleaned = text.replace("﻿", "").trim()
        val start = cleaned.indexOf('{')
        val end = cleaned.lastIndexOf('}')
        if (start < 0 || end <= start) throw ImportException("No encontré ningún JSON en el texto.")
        return runCatching { Repository.json.parseToJsonElement(cleaned.substring(start, end + 1)) as JsonObject }
            .getOrElse { throw ImportException("El JSON no es válido: ${it.message?.take(120)}") }
    }

    private fun normalize(s: String) =
        Normalizer.normalize(s, Normalizer.Form.NFD).replace(Regex("\\p{M}+"), "").lowercase().replace(Regex("[^a-z0-9]"), "")

    /** Finds the console by id, then by name/short name, then by keywords (e.g. "PlayStation 5" -> ps5). */
    fun detectConsole(id: String, name: String, consoles: List<ConsoleInfo>): Pair<ConsoleInfo?, String> {
        consoles.firstOrNull { it.id == id.trim() }?.let { return it to "id \"${it.id}\"" }
        val n = normalize(name)
        if (n.isNotEmpty()) {
            consoles.firstOrNull { normalize(it.name) == n || normalize(it.shortName) == n }?.let { return it to "nombre" }
        }
        val text = "$name $id"
        val keywordHits = FeedClassifier.consolesIn(text)
        keywordHits.firstNotNullOfOrNull { hit -> consoles.firstOrNull { it.id == hit } }?.let { return it to "palabras clave" }
        if (n.length >= 3) {
            consoles.firstOrNull { normalize(it.name).contains(n) || n.contains(normalize(it.shortName)) }?.let { return it to "nombre parecido" }
        }
        return null to ""
    }

    private fun JsonObject.str(key: String, max: Int = 1500): String =
        ((this[key] as? JsonPrimitive)?.contentOrNull ?: "").trim().take(max)

    private fun JsonObject.objects(key: String): List<JsonObject> =
        (this[key] as? JsonArray)?.mapNotNull { it as? JsonObject }.orEmpty()

    private fun JsonObject.strings(key: String, maxItems: Int): List<String> =
        (this[key] as? JsonArray).orEmpty().mapNotNull { (it as? JsonPrimitive)?.contentOrNull?.trim()?.take(500) }
            .filter { it.isNotBlank() }.take(maxItems)

    private fun slug(s: String) = normalize(s).take(40).ifBlank { "guia" }

    /** Validates the import against the current data. Images in base64 are written to [imagesDir]. */
    fun preview(
        text: String,
        consoles: List<ConsoleInfo>,
        guides: List<Guide>,
        imagesDir: File,
        now: Instant = Instant.now(),
    ): Preview {
        val root = extractJson(text)
        val warnings = mutableListOf<String>()
        val format = root.str("format", 40)
        if (format.isNotBlank() && format != FORMAT) warnings += "Formato \"$format\" desconocido; se intentó importar igualmente."

        val (console, detectedBy) = detectConsole(root.str("consoleId", 40), root.str("consoleName", 120), consoles)
        val stamp = now.toString()
        val guideIds = guides.map { it.id }.toSet()

        // 1) console status, validated with the same rules as the in-app AI
        val consoleUpdate = (root["console"] as? JsonObject)?.let { c ->
            if (console == null) {
                warnings += "No reconocí la consola: se ignoró la parte de estado."
                null
            } else {
                runCatching { Schemas.parseConsoleUpdate(toToolFormat(c, console), console, guideIds + importedGuideIds(root, console), stamp).console }
                    .onFailure { warnings += "Estado de la consola descartado: ${it.message}" }
                    .getOrNull()
            }
        }

        // 2) guides
        val parsedGuides = root.objects("guides").mapIndexedNotNull { index, g ->
            runCatching { parseGuide(g, console, guides, imagesDir, stamp) }
                .onFailure { warnings += "Guía ${index + 1} descartada: ${it.message}" }
                .getOrNull()
        }

        if (consoleUpdate == null && parsedGuides.isEmpty()) {
            throw ImportException(
                if (console == null) "No reconocí la consola y no había guías válidas." else "No había nada válido para importar.\n" + warnings.joinToString("\n"),
            )
        }
        return Preview(console, detectedBy, consoleUpdate, parsedGuides, warnings)
    }

    /** Ids the import will create, so the console data can already point at them. */
    private fun importedGuideIds(root: JsonObject, console: ConsoleInfo): Set<String> =
        root.objects("guides").map { g -> g.str("id", 60).ifBlank { "${console.id}-${slug(g.str("title", 80))}" } }.toSet()

    /** Maps the import's camelCase console object onto the strict tool format the validator expects. */
    private fun toToolFormat(c: JsonObject, current: ConsoleInfo): JsonObject {
        fun info(o: JsonObject?): JsonElement = o?.let {
            JsonObject(
                mapOf(
                    "status" to JsonPrimitive(it.str("status", 20)),
                    "title" to JsonPrimitive(it.str("title", 160)),
                    "explanation" to JsonPrimitive(it.str("explanation")),
                    "method" to JsonPrimitive(it.str("method", 200)),
                    "guide_id" to JsonPrimitive(it.str("guideId", 60)),
                ),
            )
        } ?: JsonNull
        val summary = c["summary"] as? JsonObject
        val variants = c.objects("variants").map { v ->
            val single = v["allVersions"] as? JsonObject
            JsonObject(mapOf("variant_id" to JsonPrimitive(v.str("id", 40)), "single_status" to JsonPrimitive(single != null), "status_info" to info(single)))
        }
        // Missing parts keep the current data, so an import can update just the summary or one model.
        val firmwareObjects = if (c["firmwares"] is JsonArray) {
            c.objects("firmwares")
        } else {
            current.firmwares.map { Repository.json.encodeToJsonElement(FirmwareRange.serializer(), it) as JsonObject }
        }
        val firmwares = firmwareObjects.map { f ->
            JsonObject(
                listOf("id", "label", "variant", "min", "max", "status", "title", "explanation", "method").associateWith { JsonPrimitive(f.str(it)) } +
                    ("guide_id" to JsonPrimitive(f.str("guideId", 60))),
            )
        }
        val allVersions = (c["allVersions"] as? JsonObject)
            ?: current.allVersions?.let { Repository.json.encodeToJsonElement(StatusInfo.serializer(), it) as JsonObject }
        val mode = c.str("mode", 20).ifBlank { current.mode }
        return JsonObject(
            mapOf(
                "changed" to JsonPrimitive(true),
                "change_summary" to JsonPrimitive("Importado"),
                "summary_status" to JsonPrimitive(summary?.str("status", 20) ?: current.summary.status),
                "summary_text" to JsonPrimitive(summary?.str("text", 120)?.ifBlank { null } ?: current.summary.text),
                "latest_firmware" to JsonPrimitive(c.str("latestFirmware", 40).ifBlank { current.latestFirmware }),
                "mode" to JsonPrimitive(mode),
                "all_versions" to info(allVersions),
                "variants" to JsonArray(variants),
                "firmwares" to JsonArray(firmwares),
                "sources" to (c["sources"] as? JsonArray ?: JsonArray(emptyList())),
            ),
        )
    }

    private fun parseGuide(g: JsonObject, console: ConsoleInfo?, existing: List<Guide>, imagesDir: File, stamp: String): Guide {
        val title = g.str("title", 160).ifBlank { throw ImportException("sin título") }
        val consoleIds = g.strings("consoleIds", 6).ifEmpty { listOfNotNull(console?.id) }
        if (consoleIds.isEmpty()) throw ImportException("no sé a qué consola pertenece")
        val requestedId = g.str("id", 60).replace(Regex("[^a-zA-Z0-9_-]"), "-")
        val id = requestedId.ifBlank { "${consoleIds.first()}-${slug(title)}" }
        val steps = g.objects("steps").mapNotNull { s ->
            val stepTitle = s.str("title", 160)
            val text = s.str("text", 2500)
            if (stepTitle.isBlank() || text.isBlank()) return@mapNotNull null
            GuideStep(stepTitle, text, saveImage(s.str("image", 8_000_000), imagesDir), s.str("imageCaption", 200))
        }.take(25)
        if (steps.size < 2) throw ImportException("\"$title\" tiene menos de 2 pasos")
        val links = g.objects("links").map { Source(it.str("title", 160), it.str("url", 500)) }
            .filter { Regex("^https://\\S+$").matches(it.url) }.take(10)
        val base = existing.firstOrNull { it.id == id }
        return Guide(
            id = id,
            title = title,
            consoleIds = consoleIds,
            difficulty = g.str("difficulty", 10).takeIf { it in listOf("facil", "media", "dificil") } ?: base?.difficulty ?: "media",
            time = g.str("time", 60),
            summary = g.str("summary", 1200),
            requirements = g.strings("requirements", 20),
            steps = steps,
            warnings = g.strings("warnings", 12),
            links = links,
            updatedAt = stamp,
            origin = "importada",
        )
    }

    /** https URLs are kept; data:image/...;base64 images are written to disk; anything else is dropped. */
    fun saveImage(value: String, dir: File): String {
        val v = value.trim()
        if (v.isEmpty()) return ""
        if (Regex("^https://\\S+$").matches(v)) return v
        val match = Regex("^data:image/(png|jpe?g|webp|gif);base64,(.+)$", RegexOption.DOT_MATCHES_ALL).find(v) ?: return ""
        val bytes = runCatching { Base64.decode(match.groupValues[2].replace(Regex("\\s"), ""), Base64.DEFAULT) }.getOrNull() ?: return ""
        if (bytes.isEmpty() || bytes.size > MAX_IMAGE_BYTES) return ""
        val ext = match.groupValues[1].replace("jpeg", "jpg")
        val name = MessageDigest.getInstance("SHA-1").digest(bytes).joinToString("") { "%02x".format(it) }.take(20) + ".$ext"
        dir.mkdirs()
        val file = File(dir, name)
        if (!file.exists()) file.writeBytes(bytes)
        return file.toURI().toString()
    }

    /** Stores the preview: console data and guides go to the local store like AI results. */
    suspend fun apply(store: LocalStore, preview: Preview, now: Instant = Instant.now()) {
        val stamp = now.toString()
        store.update { s ->
            var next = s
            if (preview.consoleUpdate != null) {
                val c = preview.consoleUpdate
                next = next.copy(
                    consoles = next.consoles + (c.id to c),
                    lastChecked = next.lastChecked + (c.id to stamp),
                    history = (listOf(ChangeRecord(c.id, stamp, "Datos importados desde JSON")) + next.history).take(50),
                )
            }
            next.copy(guides = next.guides + preview.guides.associateBy { it.id })
        }
    }
}
