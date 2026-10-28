package com.jailbreakchecker.app.data

import android.util.Xml
import kotlinx.serialization.Serializable
import okhttp3.OkHttpClient
import okhttp3.Request
import org.xmlpull.v1.XmlPullParser
import java.io.StringReader
import java.time.Instant
import java.time.ZonedDateTime
import java.time.format.DateTimeFormatter
import java.util.Locale
import java.util.concurrent.TimeUnit

/** A free news source read directly by the app (RSS or Atom), see data/sources.json. */
@Serializable
data class FeedSource(
    val id: String,
    val name: String,
    val url: String,
    /** jailbreak | emuladores | herramientas */
    val category: String = "jailbreak",
    val consoleIds: List<String> = emptyList(),
    /** Broad feeds (whole forums, general news): keep only hacking/homebrew/emulation items. */
    val filter: Boolean = false,
)

@Serializable
data class FeedSourceList(val sources: List<FeedSource> = emptyList())

/** Raw entry parsed from a feed, before classification. */
data class FeedEntry(val title: String, val link: String, val published: Instant?, val summary: String)

object FeedParser {

    /** Parses RSS 2.0 `<item>` and Atom `<entry>` elements; unknown tags are ignored. */
    fun parse(xml: String): List<FeedEntry> {
        val parser = Xml.newPullParser().apply {
            setFeature(XmlPullParser.FEATURE_PROCESS_NAMESPACES, false)
            setInput(StringReader(xml))
        }
        val entries = mutableListOf<FeedEntry>()
        var inEntry = false
        var title = ""
        var link = ""
        var date = ""
        var summary = ""
        var event = parser.eventType
        while (event != XmlPullParser.END_DOCUMENT) {
            when (event) {
                XmlPullParser.START_TAG -> when (parser.name) {
                    "item", "entry" -> {
                        inEntry = true
                        title = ""; link = ""; date = ""; summary = ""
                    }
                    "title" -> if (inEntry) title = parser.nextTextSafe()
                    "link" -> if (inEntry) {
                        val href = parser.getAttributeValue(null, "href")
                        val rel = parser.getAttributeValue(null, "rel")
                        if (href != null) {
                            if (link.isEmpty() || rel == "alternate") link = href
                        } else {
                            link = parser.nextTextSafe()
                        }
                    }
                    "pubDate", "published", "updated", "dc:date" -> if (inEntry && date.isEmpty()) date = parser.nextTextSafe()
                    "description", "summary", "content", "content:encoded" -> if (inEntry && summary.isEmpty()) summary = parser.nextTextSafe()
                }
                XmlPullParser.END_TAG -> if (parser.name == "item" || parser.name == "entry") {
                    inEntry = false
                    if (title.isNotBlank()) entries += FeedEntry(clean(title, 200), link.trim(), parseDate(date), clean(summary, 280))
                }
            }
            event = parser.next()
        }
        return entries
    }

    private fun XmlPullParser.nextTextSafe(): String = runCatching { nextText() }.getOrDefault("")

    fun clean(html: String, max: Int): String {
        val text = html
            .replace(Regex("<[^>]+>"), " ")
            .replace("&nbsp;", " ").replace("&amp;", "&").replace("&quot;", "\"").replace("&#39;", "'")
            .replace("&lt;", "<").replace("&gt;", ">").replace("&#8217;", "’").replace("&#8211;", "–")
            .replace(Regex("\\s+"), " ")
            .trim()
        return if (text.length <= max) text else text.take(max - 1).trimEnd() + "…"
    }

    fun parseDate(value: String): Instant? {
        val v = value.trim()
        if (v.isEmpty()) return null
        return runCatching { Instant.parse(v) }.getOrNull()
            ?: runCatching { ZonedDateTime.parse(v, DateTimeFormatter.ISO_OFFSET_DATE_TIME).toInstant() }.getOrNull()
            ?: runCatching { ZonedDateTime.parse(v, DateTimeFormatter.RFC_1123_DATE_TIME).toInstant() }.getOrNull()
            ?: runCatching { ZonedDateTime.parse(v, DateTimeFormatter.ofPattern("EEE, dd MMM yyyy HH:mm:ss Z", Locale.ENGLISH)).toInstant() }.getOrNull()
    }
}

/** Turns feed entries into app news: picks the consoles they talk about and drops noise. */
object FeedClassifier {

    private val consoleKeywords: List<Pair<String, List<String>>> = listOf(
        "switch2" to listOf("ns2jb"), // plus SWITCH_2 below
        "ps5" to listOf("ps5", "playstation 5", "etahen", "byepervisor", "relapse"),
        "ps4" to listOf("ps4", "playstation 4", "goldhen", "pppwn", "shadps4", "lapse"),
        "ps3" to listOf("ps3", "playstation 3", "ps3hen", "evilnat", "rpcs3", "badwdsd"),
        "vita" to listOf("vita", "henkaku", "henlo", "vita3k"),
        "psp" to listOf("psp", "ppsspp", "ark-4", "ark-5"),
        "ps2" to listOf("ps2", "playstation 2", "pcsx2", "freemcboot", "opentuna", "open ps2 loader"),
        "ps1" to listOf("ps1", "psx", "duckstation", "freepsxboot"),
        "switch1" to listOf("switch", "atmosph", "hekate", "picofly", "ryujinx", "eden emu", "citron"),
        "n3ds" to listOf("3ds", "luma3ds", "azahar", "citra", "mset9"),
        "wiiu" to listOf("wii u", "wiiu", "aroma", "cemu", "tiramisu"),
        "wii" to listOf("wii", "letterbomb", "bluebomb", "homebrew channel"),
        "gamecube" to listOf("gamecube", "picoboot", "swiss"),
        "nds" to listOf("nintendo ds", "dsi", "melonds", "twilight menu"),
        "gba" to listOf("gba", "game boy advance", "mgba"),
        "x360" to listOf("xbox 360", "x360", "badupdate", "rgh", "xenia"),
        "xseries" to listOf("xbox series", "series x", "series s"),
        "xone" to listOf("xbox one", "collateral damage"),
        "xbox" to listOf("xemu", "original xbox"),
        "dreamcast" to listOf("dreamcast", "flycast", "gdemu"),
        "saturn" to listOf("saturn", "saroo"),
    )

    /** Words that make an item from a broad feed worth showing (hacking, homebrew, emulation, firmware). */
    private val topic = Regex(
        "(?<![a-z0-9])(jailbreak|exploit|hack|homebrew|cfw|custom firmware|kernel|payload|modchip|firmware|" +
            "system update|emulat|emulador|bypass|unsigned|piracy|ban wave|banned)",
    )

    /** "Switch 2" but not "Switch 23.1.0" (a Switch 1 firmware). */
    private const val SWITCH_2 = "switch ?2(?![0-9.])"

    private val consolePatterns = consoleKeywords.map { (id, words) ->
        val alternatives = words.map { Regex.escape(it) } + if (id == "switch2") listOf(SWITCH_2) else emptyList()
        id to Regex(alternatives.joinToString("|", prefix = "(?<![a-z0-9])(", postfix = ")"))
    }

    fun consolesIn(text: String): List<String> {
        val t = text.lowercase()
        val found = mutableListOf<String>()
        for ((id, pattern) in consolePatterns) {
            // "Switch 2" news shouldn't also be tagged as the original Switch
            if (id == "switch1" && "switch2" in found && Regex("switch(?! ?2(?![0-9.]))").find(t) == null) continue
            if (pattern.containsMatchIn(t)) found += id
        }
        return found
    }

    fun isRelevant(text: String): Boolean {
        val t = text.lowercase()
        return topic.containsMatchIn(t) && consolesIn(t).isNotEmpty()
    }

    fun toNews(source: FeedSource, entry: FeedEntry, now: Instant): NewsItem? {
        val text = entry.title + " " + entry.summary
        if (source.filter && !isRelevant(text)) return null
        // A console named in the title is more precise than the source's default consoles.
        val consoles = consolesIn(entry.title).ifEmpty { source.consoleIds }.take(4)
        if (source.category == "jailbreak" && consoles.isEmpty()) return null
        return NewsItem(
            title = entry.title,
            summary = entry.summary.ifBlank { "Nueva publicación en ${source.name}." },
            consoleIds = consoles,
            status = "unknown",
            url = entry.link.takeIf { it.startsWith("http") }.orEmpty(),
            source = source.name,
            publishedAt = (entry.published ?: now).toString().take(10),
            createdAt = (entry.published ?: now).toString(),
            category = source.category,
            origin = "feed",
        )
    }
}

/** Downloads every source (free, no API key) and returns the recent, relevant items. */
class FeedFetcher(
    private val client: OkHttpClient = OkHttpClient.Builder()
        .connectTimeout(15, TimeUnit.SECONDS)
        .readTimeout(20, TimeUnit.SECONDS)
        .build(),
) {
    data class Result(val news: List<NewsItem>, val failed: List<String>)

    fun fetchAll(sources: List<FeedSource>, now: Instant = Instant.now(), maxAgeDays: Long = 21, perSource: Int = 6): Result {
        val news = mutableListOf<NewsItem>()
        val failed = mutableListOf<String>()
        val oldest = now.minusSeconds(maxAgeDays * 86_400)
        for (source in sources) {
            val xml = runCatching {
                client.newCall(
                    Request.Builder()
                        .url(source.url)
                        .header("User-Agent", "Mozilla/5.0 (Android) RevisorJailbreak/3.0")
                        .build(),
                ).execute().use { r -> if (r.isSuccessful) r.body?.string() else null }
            }.getOrNull()
            if (xml == null) {
                failed += source.name
                continue
            }
            runCatching { FeedParser.parse(xml) }.getOrDefault(emptyList())
                .filter { (it.published ?: now) >= oldest }
                .mapNotNull { FeedClassifier.toNews(source, it, now) }
                .take(perSource)
                .let { news += it }
        }
        return Result(news, failed)
    }
}
