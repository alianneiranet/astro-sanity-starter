package com.jailbreakchecker.app.data

import android.graphics.Bitmap
import android.util.Base64
import androidx.test.core.app.ApplicationProvider
import kotlinx.coroutines.runBlocking
import org.junit.Assert.assertEquals
import org.junit.Assert.assertNull
import org.junit.Assert.assertThrows
import org.junit.Assert.assertTrue
import org.junit.Rule
import org.junit.Test
import org.junit.rules.TemporaryFolder
import org.junit.runner.RunWith
import org.robolectric.RobolectricTestRunner
import org.robolectric.annotation.Config
import java.io.ByteArrayOutputStream
import java.io.File
import java.time.Instant

@RunWith(RobolectricTestRunner::class)
@Config(sdk = [35])
class ImporterTest {

    @get:Rule
    val tmp = TemporaryFolder()

    private val catalog = Repository.json.decodeFromString(Catalog.serializer(), File("../../data/consoles.json").readText())
    private val guides = Repository.json.decodeFromString(GuideCatalog.serializer(), File("../../data/guides.json").readText()).guides
    private val now = Instant.parse("2026-10-29T12:00:00Z")

    private fun pngDataUri(): String {
        val bmp = Bitmap.createBitmap(4, 4, Bitmap.Config.ARGB_8888)
        val out = ByteArrayOutputStream()
        bmp.compress(Bitmap.CompressFormat.PNG, 100, out)
        return "data:image/png;base64," + Base64.encodeToString(out.toByteArray(), Base64.NO_WRAP)
    }

    private fun answer(body: String) = "¡Claro! Aquí tienes el JSON:\n```json\n$body\n```\nEspero que te sirva."

    @Test
    fun importsChatAnswerDetectingConsoleByName() {
        val json = """{
          "format": "revisor-jailbreak/1",
          "consoleName": "PlayStation 5",
          "console": {
            "summary": {"status": "green", "text": "Jailbreak hasta la 14.10"},
            "latestFirmware": "14.20",
            "mode": "firmware",
            "allVersions": null,
            "variants": [],
            "firmwares": [
              {"id": "ps5-a", "label": "1.00 – 14.10", "variant": "", "min": "1.00", "max": "14.10", "status": "green", "title": "Jailbreak", "explanation": "Funciona.", "method": "X", "guideId": "ps5-nueva"},
              {"id": "ps5-b", "label": "14.20 o superior", "variant": "", "min": "14.20", "max": "", "status": "red", "title": "Sin jailbreak", "explanation": "No.", "method": "—", "guideId": ""}
            ],
            "sources": [{"title": "GitHub", "url": "https://github.com/example/exploit"}]
          },
          "guides": [
            {"id": "ps5-nueva", "title": "PS5 14.10: nuevo método", "difficulty": "media", "time": "30 min", "summary": "Resumen.",
             "requirements": ["PS5 en 14.10"],
             "steps": [
               {"title": "Paso 1", "text": "Haz esto.", "image": "https://example.com/paso1.png", "imageCaption": "Menú"},
               {"title": "Paso 2", "text": "Haz lo otro.", "image": "${pngDataUri()}"},
               {"title": "Paso 3", "text": "Termina.", "image": "javascript:alert(1)"}
             ],
             "warnings": ["Riesgo de baneo."],
             "links": [{"title": "Oficial", "url": "https://github.com/example/exploit"}, {"title": "Mala", "url": "http://example.com"}]},
            {"title": "Guía rota", "steps": [{"title": "Solo uno", "text": "x"}]}
          ]
        }"""
        val dir = tmp.newFolder("img")
        val p = Importer.preview(answer(json), catalog.consoles, guides, dir, now)

        assertEquals("ps5", p.console!!.id)
        assertEquals("Jailbreak hasta la 14.10", p.consoleUpdate!!.summary.text)
        assertEquals("ps5-nueva", p.consoleUpdate!!.firmwares.first().guideId)
        val g = p.guides.single()
        assertEquals(listOf("ps5"), g.consoleIds)
        assertEquals("importada", g.origin)
        assertEquals("https://example.com/paso1.png", g.steps[0].image)
        assertTrue(g.steps[1].image.startsWith("file:"))
        assertTrue(File(java.net.URI(g.steps[1].image)).exists())
        assertEquals("", g.steps[2].image)
        assertEquals(listOf("https://github.com/example/exploit"), g.links.map { it.url })
        assertTrue(p.warnings.any { "Guía rota" in it || "Guía 2" in it })

        // apply: console overridden, new guide listed with the others
        val store = LocalStore.get(ApplicationProvider.getApplicationContext())
        runBlocking {
            store.clear()
            Importer.apply(store, p, now)
        }
        val merged = Repository.mergeConsoles(catalog.consoles, store.state.value).first { it.id == "ps5" }
        assertEquals("14.20", merged.latestFirmware)
        val allGuides = Repository.mergeGuides(guides, store.state.value, "2026-10-28")
        assertTrue(allGuides.any { it.id == "ps5-nueva" })
        assertEquals(guides.size + 1, allGuides.size)
    }

    @Test
    fun partialImportKeepsCurrentData() {
        val json = """{"consoleId": "switch1", "console": {"summary": {"status": "green", "text": "Atmosphère soporta la 23.1.0"}, "latestFirmware": "23.1.0"}}"""
        val p = Importer.preview(json, catalog.consoles, guides, tmp.newFolder(), now)
        val current = catalog.consoles.first { it.id == "switch1" }
        assertEquals("23.1.0", p.consoleUpdate!!.latestFirmware)
        assertEquals(current.firmwares.map { it.id }, p.consoleUpdate!!.firmwares.map { it.id })
        assertEquals("orange", p.consoleUpdate!!.variants.first { it.id == "mariko" }.allVersions!!.status)
        assertTrue(p.guides.isEmpty())
    }

    @Test
    fun detectsConsolesByKeywordsAndRejectsGarbage() {
        assertEquals("switch2", Importer.detectConsole("", "Nintendo Switch 2", catalog.consoles).first?.id)
        assertEquals("n3ds", Importer.detectConsole("", "New Nintendo 3DS XL", catalog.consoles).first?.id)
        assertEquals("x360", Importer.detectConsole("xbox360", "Xbox 360 Slim", catalog.consoles).first?.id)
        assertNull(Importer.detectConsole("", "Tostadora", catalog.consoles).first)

        assertThrows(Importer.ImportException::class.java) { Importer.preview("hola", catalog.consoles, guides, tmp.newFolder(), now) }
        assertThrows(Importer.ImportException::class.java) {
            Importer.preview("""{"consoleName": "Tostadora", "console": {"summary": {"status": "green", "text": "x"}}}""", catalog.consoles, guides, tmp.newFolder(), now)
        }
    }

    @Test
    fun promptContainsConsoleDataAndFormat() {
        val ps5 = catalog.consoles.first { it.id == "ps5" }
        val prompt = ImportPrompt.build(ps5, guides, catalog.consoles)
        assertTrue(Importer.FORMAT in prompt)
        assertTrue("\"consoleId\": \"ps5\"" in prompt)
        assertTrue("ps5-jailbreak" in prompt)
        assertTrue("Relapse" in prompt)
        val generic = ImportPrompt.build(null, guides, catalog.consoles)
        assertTrue("switch2 = Nintendo Switch 2" in generic)
    }

    @Test
    fun bundledExampleImports() {
        val p = Importer.preview(File("../../docs/ejemplo-importacion.json").readText(), catalog.consoles, guides, tmp.newFolder(), now)
        assertEquals("switch1", p.console!!.id)
        assertTrue(p.warnings.isEmpty())
        assertEquals(1, p.guides.size)
        assertEquals("orange", p.consoleUpdate!!.variants.first { it.id == "mariko" }.allVersions!!.status)
    }
}
