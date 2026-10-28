package com.jailbreakchecker.app.data

import org.junit.Assert.assertEquals
import org.junit.Assert.assertNull
import org.junit.Assert.assertTrue
import org.junit.Test
import org.junit.runner.RunWith
import org.robolectric.RobolectricTestRunner
import org.robolectric.annotation.Config
import java.io.File
import java.time.Instant

@RunWith(RobolectricTestRunner::class)
@Config(sdk = [35])
class FeedsTest {

    private val rss = """<?xml version="1.0" encoding="UTF-8"?>
<rss version="2.0" xmlns:content="http://purl.org/rss/1.0/modules/content/"><channel><title>Wololo</title>
<item><title>PS5: etaHEN 2.3 released with 13.60 support</title><link>https://wololo.net/2026/10/27/etahen-2-3/</link>
<pubDate>Tue, 27 Oct 2026 10:00:00 +0000</pubDate><description><![CDATA[<p>The popular <b>homebrew enabler</b> gets an update&nbsp;today.</p>]]></description></item>
<item><title>Best gaming chairs of 2026</title><link>https://wololo.net/chairs</link><pubDate>Mon, 26 Oct 2026 10:00:00 +0000</pubDate><description>Ads</description></item>
</channel></rss>"""

    private val atom = """<?xml version="1.0" encoding="UTF-8"?>
<feed xmlns="http://www.w3.org/2005/Atom"><title>Release notes from Atmosphere</title>
<entry><id>tag:github.com,2008:Repository/1/1.12.2</id><updated>2026-10-28T09:00:00Z</updated>
<link rel="alternate" type="text/html" href="https://github.com/Atmosphere-NX/Atmosphere/releases/tag/1.12.2"/>
<title>Atmosphère 1.12.2</title><content type="html">&lt;p&gt;Adds support for 23.1.0&lt;/p&gt;</content></entry>
</feed>"""

    @Test
    fun parsesRssAndAtom() {
        val items = FeedParser.parse(rss)
        assertEquals(2, items.size)
        assertEquals("https://wololo.net/2026/10/27/etahen-2-3/", items[0].link)
        assertEquals("The popular homebrew enabler gets an update today.", items[0].summary)
        assertEquals(Instant.parse("2026-10-27T10:00:00Z"), items[0].published)

        val entries = FeedParser.parse(atom)
        assertEquals("Atmosphère 1.12.2", entries.single().title)
        assertEquals("https://github.com/Atmosphere-NX/Atmosphere/releases/tag/1.12.2", entries.single().link)
        assertEquals("Adds support for 23.1.0", entries.single().summary)
    }

    @Test
    fun classifiesConsolesAndDropsNoise() {
        assertEquals(listOf("ps5"), FeedClassifier.consolesIn("etaHEN for PS5 13.60"))
        assertEquals(listOf("switch2"), FeedClassifier.consolesIn("Switch 2 exploit ns2jb updated"))
        assertTrue("switch1" in FeedClassifier.consolesIn("Atmosphère 1.12.2 adds Switch 23.1.0"))
        assertEquals(emptyList<String>(), FeedClassifier.consolesIn("A total collapse of sales"))

        val now = Instant.parse("2026-10-29T00:00:00Z")
        val gbatemp = FeedSource("gbatemp", "GBAtemp", "https://gbatemp.net", "jailbreak", emptyList(), filter = true)
        val entries = FeedParser.parse(rss)
        assertEquals(listOf("ps5"), FeedClassifier.toNews(gbatemp, entries[0], now)!!.consoleIds)
        assertNull(FeedClassifier.toNews(gbatemp, entries[1], now))

        val github = FeedSource("gh", "Atmosphère (GitHub)", "https://github.com", "herramientas", listOf("switch1"))
        val news = FeedClassifier.toNews(github, FeedParser.parse(atom).single(), now)!!
        assertEquals("herramientas", news.category)
        assertEquals("feed", news.origin)
        assertEquals(listOf("switch1"), news.consoleIds)
    }

    @Test
    fun bundledSourcesAreValid() {
        val sources = Repository.json.decodeFromString(FeedSourceList.serializer(), File("../../data/sources.json").readText()).sources
        assertTrue(sources.size >= 20)
        val consoleIds = Repository.json.decodeFromString(Catalog.serializer(), File("../../data/consoles.json").readText()).consoles.map { it.id }.toSet()
        for (s in sources) {
            assertTrue(s.url, s.url.startsWith("https://"))
            assertTrue(s.id, s.category in listOf("jailbreak", "emuladores", "herramientas"))
            assertTrue(s.id, s.consoleIds.all { it in consoleIds })
        }
        assertEquals(sources.size, sources.map { it.id }.toSet().size)
    }

    /** Optional: parses real feeds saved in REAL_FEEDS_DIR (not committed) to catch format surprises. */
    @Test
    fun realFeedSamples() {
        val dir = System.getenv("REAL_FEEDS_DIR")?.let(::File) ?: return
        for (f in dir.listFiles().orEmpty().filter { it.extension == "xml" }) {
            val items = FeedParser.parse(f.readText())
            println("${f.name}: ${items.size} items, first=${items.firstOrNull()?.title} | ${items.firstOrNull()?.published} | ${items.firstOrNull()?.link}")
            assertTrue(f.name, items.isNotEmpty())
            assertTrue(f.name, items.all { it.link.startsWith("http") })
        }
    }
}
