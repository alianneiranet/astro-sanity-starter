package com.jailbreakchecker.app.ai

import androidx.test.core.app.ApplicationProvider
import com.jailbreakchecker.app.data.Catalog
import com.jailbreakchecker.app.data.ConsoleInfo
import com.jailbreakchecker.app.data.GuideCatalog
import com.jailbreakchecker.app.data.LocalState
import com.jailbreakchecker.app.data.LocalStore
import com.jailbreakchecker.app.data.Repository
import kotlinx.coroutines.runBlocking
import kotlinx.serialization.json.Json
import kotlinx.serialization.json.JsonObject
import kotlinx.serialization.json.jsonArray
import kotlinx.serialization.json.jsonObject
import kotlinx.serialization.json.jsonPrimitive
import okhttp3.mockwebserver.MockResponse
import okhttp3.mockwebserver.MockWebServer
import org.junit.After
import org.junit.Assert.assertEquals
import org.junit.Assert.assertNull
import org.junit.Assert.assertThrows
import org.junit.Assert.assertTrue
import org.junit.Before
import org.junit.Test
import org.junit.runner.RunWith
import org.robolectric.RobolectricTestRunner
import org.robolectric.annotation.Config
import java.io.File
import java.time.Instant

/**
 * Runs the on-device research loop with the real Anthropic SDK against a fake Messages API,
 * checking both what the app sends and what it stores.
 */
@RunWith(RobolectricTestRunner::class)
@Config(sdk = [35])
class UpdaterTest {

    private val catalog = Repository.json.decodeFromString(Catalog.serializer(), File("../../data/consoles.json").readText())
    private val guides = Repository.json.decodeFromString(GuideCatalog.serializer(), File("../../data/guides.json").readText()).guides
    private lateinit var server: MockWebServer
    private lateinit var store: LocalStore

    @Before
    fun setUp() {
        server = MockWebServer().apply { start() }
        store = LocalStore.get(ApplicationProvider.getApplicationContext())
        runBlocking { store.clear() }
    }

    @After
    fun tearDown() {
        server.shutdown()
    }

    private fun message(stopReason: String, content: String) = MockResponse()
        .setHeader("content-type", "application/json")
        .setBody(
            """{"id":"msg_1","type":"message","role":"assistant","model":"claude-opus-5-5","content":$content,
            "stop_reason":"$stopReason","stop_sequence":null,
            "usage":{"input_tokens":1200,"output_tokens":300,"server_tool_use":{"web_search_requests":2}}}""",
        )

    private fun toolUse(name: String, input: String) =
        message("tool_use", """[{"type":"tool_use","id":"toolu_1","name":"$name","input":$input}]""")

    private fun updater() = Updater(store, catalog.consoles, guides, ClaudeRunner("test-key", "claude-opus-5-5", server.url("/").toString()))

    private val switchUpdate = """{
      "changed": true, "change_summary": "Atmosphère soporta ya la 23.1.0",
      "summary_status": "green", "summary_text": "V1 sin parche: sí. V2, Lite y OLED: solo con modchip",
      "latest_firmware": "23.1.0", "mode": "firmware",
      "all_versions": {"status":"green","title":"","explanation":"","method":"","guide_id":""},
      "variants": [
        {"variant_id":"unpatched","single_status":true,"status_info":{"status":"green","title":"Cualquier firmware","explanation":"RCM.","method":"RCM","guide_id":"switch-rcm"}},
        {"variant_id":"patched_v1","single_status":false,"status_info":{"status":"orange","title":"","explanation":"","method":"","guide_id":""}},
        {"variant_id":"mariko","single_status":true,"status_info":{"status":"orange","title":"Solo con modchip","explanation":"Hay que abrirla.","method":"Picofly","guide_id":"switch-modchip"}}
      ],
      "firmwares": [
        {"id":"sw1-patched-low","label":"1.0.0 – 4.1.0","variant":"patched_v1","min":"1.0.0","max":"4.1.0","status":"green","title":"Software","explanation":"Caffeine.","method":"Caffeine","guide_id":""},
        {"id":"sw1-patched-high","label":"5.0.0 o superior","variant":"patched_v1","min":"5.0.0","max":"","status":"orange","title":"Modchip","explanation":"Chip.","method":"Picofly","guide_id":"switch-modchip"}
      ],
      "sources": [{"title":"Atmosphère","url":"https://github.com/Atmosphere-NX/Atmosphere/releases"}]
    }"""

    @Test
    fun scanThenReviewUpdatesTheLocalStore(): Unit = runBlocking {
        // 1) the scan pauses mid-search once, then reports one news item and one console to review
        server.enqueue(message("pause_turn", """[{"type":"text","text":"Buscando..."}]"""))
        server.enqueue(
            toolUse(
                Schemas.REPORT_SCAN,
                """{"news":[{"title":"Atmosphère 1.12.2 soporta la 23.1.0","summary":"Ya se puede actualizar.","console_ids":["switch1","nope"],
                "status":"green","url":"https://github.com/Atmosphere-NX/Atmosphere/releases","source":"GitHub","published_date":"2026-10-29"}],
                "consoles_to_review":[{"console_id":"switch1","reason":"Nuevo firmware soportado"}]}""",
            ),
        )
        val now = Instant.parse("2026-10-29T10:00:00Z")
        val outcome = updater().scan(now)
        assertEquals(1, outcome.newNews.size)
        assertEquals(listOf("switch1"), outcome.newNews.first().consoleIds)
        assertEquals("switch1", outcome.toReview.first().first)
        assertEquals(Updater.MAX_REVIEWS_PER_RUN, outcome.toReview.size)

        // what the app sent
        val first = server.takeRequest()
        assertTrue(first.getHeader("anthropic-beta")!!.contains("server-side-fallback-2026-07-01"))
        // the user's key is sent (SDK 3.x uses a bearer header; older versions used x-api-key)
        assertTrue(first.getHeader("Authorization") == "Bearer test-key" || first.getHeader("X-Api-Key") == "test-key")
        val body = Json.parseToJsonElement(first.body.readUtf8()).jsonObject
        assertEquals("default", body["fallbacks"]!!.jsonPrimitive.content)
        assertEquals("adaptive", body["thinking"]!!.jsonObject["type"]!!.jsonPrimitive.content)
        assertEquals("medium", body["output_config"]!!.jsonObject["effort"]!!.jsonPrimitive.content)
        val tools = body["tools"]!!.jsonArray
        assertEquals("web_search_20260209", tools[0].jsonObject["type"]!!.jsonPrimitive.content)
        assertEquals("true", tools[1].jsonObject["strict"]!!.jsonPrimitive.content)
        // the paused turn is sent back so the server can resume it
        val second = Json.parseToJsonElement(server.takeRequest().body.readUtf8()).jsonObject
        assertEquals("assistant", second["messages"]!!.jsonArray.last().jsonObject["role"]!!.jsonPrimitive.content)

        // 2) the review returns corrected data for the Switch
        server.enqueue(toolUse(Schemas.SUBMIT_CONSOLE, switchUpdate))
        val change = updater().review("switch1", "Nuevo firmware soportado", now)
        assertEquals("Atmosphère soporta ya la 23.1.0", change)

        val state: LocalState = store.state.value
        val merged: ConsoleInfo = Repository.mergeConsoles(catalog.consoles, state).first { it.id == "switch1" }
        assertEquals("23.1.0", merged.latestFirmware)
        assertEquals("orange", merged.variants.first { it.id == "mariko" }.allVersions!!.status)
        assertEquals("switch-modchip", merged.variants.first { it.id == "mariko" }.allVersions!!.guideId)
        assertNull(merged.variants.first { it.id == "patched_v1" }.allVersions)
        assertEquals(1, state.history.size)
        assertEquals(1, state.news.size)
        assertTrue(state.meta.ok)

        // the review request listed the guides as an enum on the strict tool
        val reviewBody = Json.parseToJsonElement(server.takeRequest().body.readUtf8()).jsonObject
        val reviewTool = reviewBody["tools"]!!.jsonArray[1].jsonObject
        assertEquals(Schemas.SUBMIT_CONSOLE, reviewTool["name"]!!.jsonPrimitive.content)
        assertEquals("high", reviewBody["output_config"]!!.jsonObject["effort"]!!.jsonPrimitive.content)
    }

    @Test
    fun invalidUpdatesAreRejected() {
        val switch = catalog.consoles.first { it.id == "switch1" }
        val ids = guides.map { it.id }.toSet()
        val base = Json.parseToJsonElement(switchUpdate).jsonObject

        // patched_v1 needs firmware ranges when it has no single status
        val noRanges = JsonObject(base + ("firmwares" to Json.parseToJsonElement("[]")))
        assertThrows(Schemas.InvalidUpdate::class.java) { Schemas.parseConsoleUpdate(noRanges, switch, ids, "2026-10-29T00:00:00Z") }

        // inverted range
        val inverted = JsonObject(
            base + ("firmwares" to Json.parseToJsonElement(
                """[{"id":"x","label":"x","variant":"patched_v1","min":"9.0.0","max":"5.0.0","status":"green","title":"","explanation":"","method":"","guide_id":""}]""",
            )),
        )
        assertThrows(Schemas.InvalidUpdate::class.java) { Schemas.parseConsoleUpdate(inverted, switch, ids, "2026-10-29T00:00:00Z") }

        // unknown guide ids are dropped instead of breaking the app
        val madeUpGuide = Json.parseToJsonElement(switchUpdate.replace("\"guide_id\":\"switch-rcm\"", "\"guide_id\":\"made-up\""))
        val parsed = Schemas.parseConsoleUpdate(madeUpGuide, switch, ids, "2026-10-29T00:00:00Z")
        assertEquals("", parsed.console.variants.first { it.id == "unpatched" }.allVersions!!.guideId)
        assertEquals("switch-modchip", parsed.console.variants.first { it.id == "mariko" }.allVersions!!.guideId)
    }

    @Test
    fun bundledDataWinsOverOlderAiData() {
        val ps5 = catalog.consoles.first { it.id == "ps5" }
        val oldAi = ps5.copy(summary = ps5.summary.copy(text = "viejo"), updatedAt = "2026-01-01T00:00:00Z")
        val newAi = ps5.copy(summary = ps5.summary.copy(text = "nuevo"), updatedAt = "2026-12-01T00:00:00Z")
        val pick = { ai: ConsoleInfo ->
            Repository.mergeConsoles(catalog.consoles, LocalState(consoles = mapOf("ps5" to ai))).first { it.id == "ps5" }.summary.text
        }
        assertEquals(ps5.summary.text, pick(oldAi))
        assertEquals("nuevo", pick(newAi))
    }

    @Test
    fun dueConsolesPreferActiveConsoles() {
        val now = Instant.parse("2026-10-29T12:00:00Z")
        val state = LocalState(lastChecked = mapOf("ps5" to "2026-10-29T11:00:00Z"))
        val due = Updater.dueConsoles(catalog.consoles, state, now).map { it.id }
        assertTrue("ps5" !in due)
        // never-checked consoles with a 24 h interval come before retro ones checked monthly
        assertTrue(due.indexOf("ps4") < due.indexOf("nes"))
    }
}
