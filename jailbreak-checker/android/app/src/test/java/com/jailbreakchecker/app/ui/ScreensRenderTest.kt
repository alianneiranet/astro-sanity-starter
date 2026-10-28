package com.jailbreakchecker.app.ui

import android.graphics.Bitmap
import android.graphics.Canvas
import androidx.activity.ComponentActivity
import androidx.compose.runtime.Composable
import androidx.compose.runtime.CompositionLocalProvider
import androidx.compose.ui.test.hasSetTextAction
import androidx.compose.ui.test.junit4.createAndroidComposeRule
import androidx.compose.ui.test.onNodeWithText
import androidx.compose.ui.test.performClick
import androidx.compose.ui.test.performTextInput
import com.jailbreakchecker.app.data.AppSettings
import com.jailbreakchecker.app.data.Catalog
import com.jailbreakchecker.app.data.GuideCatalog
import com.jailbreakchecker.app.data.Repository
import com.jailbreakchecker.app.data.UpdaterMeta
import com.jailbreakchecker.app.ui.theme.JailbreakTheme
import org.junit.Rule
import org.junit.Test
import org.junit.runner.RunWith
import org.robolectric.RobolectricTestRunner
import org.robolectric.annotation.Config
import org.robolectric.annotation.GraphicsMode
import java.io.File

/**
 * Renders the main screens with the bundled data. Catches crashes in the UI code and
 * writes PNG snapshots to app/build/screenshots for a quick visual check.
 */
@RunWith(RobolectricTestRunner::class)
@GraphicsMode(GraphicsMode.Mode.NATIVE)
@Config(sdk = [35], qualifiers = "w411dp-h891dp-night-xhdpi")
class ScreensRenderTest {

    @get:Rule
    val compose = createAndroidComposeRule<ComponentActivity>()

    private val catalog: Catalog by lazy {
        Repository.json.decodeFromString(Catalog.serializer(), File("../../data/consoles.json").readText())
    }
    private val guides by lazy {
        Repository.json.decodeFromString(GuideCatalog.serializer(), File("../../data/guides.json").readText()).guides.associateBy { it.id }
    }
    private val logoIds by lazy {
        File("../../data/logos").listFiles().orEmpty().map { it.name.removeSuffix(".svg") }.toSet()
    }

    private fun show(content: @Composable () -> Unit) = compose.setContent {
        JailbreakTheme(darkTheme = true) {
            CompositionLocalProvider(LocalLogoIds provides logoIds) { content() }
        }
    }

    private fun snapshot(name: String) {
        val dir = File("build/screenshots").apply { mkdirs() }
        compose.waitForIdle()
        val view = compose.activity.window.decorView
        val bitmap = Bitmap.createBitmap(view.width, view.height, Bitmap.Config.ARGB_8888)
        view.draw(Canvas(bitmap))
        File(dir, "$name.png").outputStream().use { bitmap.compress(Bitmap.CompressFormat.PNG, 100, it) }
    }

    private fun console(id: String) = catalog.consoles.first { it.id == id }
    private fun brand(id: String) = catalog.brands.first { it.id == id }

    @Test
    fun home() {
        show { HomeScreen(
                brands = catalog.brands, consoles = catalog.consoles, news = emptyList(), meta = UpdaterMeta(),
                guides = guides.values.toList(), updatingGuides = emptySet(), onUpdateAllGuides = {},
                hasApiKey = false, searching = false, onBrand = {}, onConsole = {}, onGuide = {}, onSearchNow = {}, onSettings = {},
            ) }
        compose.onNodeWithText("Activa la búsqueda con IA").assertExists()
        snapshot("home")
    }

    @Test
    fun brand() {
        show { BrandScreen(brand("nintendo"), catalog.consoles.filter { it.brandId == "nintendo" }, {}, {}) }
        compose.onNodeWithText("Nintendo Switch 2").assertExists()
        snapshot("brand-nintendo")
    }

    @Test
    fun ps5WithVersionSearch() {
        show { ConsoleScreen(console("ps5"), brand("sony"), guides, true, false, {}, {}, {}) }
        compose.onNode(hasSetTextAction()).performTextInput("9.60")
        compose.onNodeWithText("Tu versión 9.60").assertExists()
        snapshot("console-ps5")
    }

    @Test
    fun switchV2SaysModchipOnlyWithoutAskingFirmware() {
        show { ConsoleScreen(console("switch1"), brand("nintendo"), guides, false, false, {}, {}, {}) }
        compose.onNodeWithText("¿Qué modelo tienes?").assertExists()
        compose.onNodeWithText("V2, Lite u OLED").performClick()
        compose.onNodeWithText("Con este modelo no importa el firmware").assertExists()
        compose.onNodeWithText("Solo con modchip (hay que abrirla)").assertExists()
        compose.onNodeWithText("¿Qué firmware tienes?").assertDoesNotExist()
        snapshot("console-switch1-v2")
    }

    @Test
    fun switchPatchedV1AsksFirmware() {
        show { ConsoleScreen(console("switch1"), brand("nintendo"), guides, false, false, {}, {}, {}) }
        compose.onNodeWithText("V1 parcheada (finales de 2018–2019)").performClick()
        compose.onNodeWithText("¿Qué firmware tienes?").assertExists()
    }

    @Test
    fun ps1SkipsFirmwareQuestion() {
        show { ConsoleScreen(console("ps1"), brand("sony"), guides, false, false, {}, {}, {}) }
        compose.onNodeWithText("No hace falta elegir firmware").assertExists()
        snapshot("console-ps1")
    }

    @Test
    fun guide() {
        show { GuideScreen(guides["switch-rcm"], "2026-10-28", true, false, {}, {}, {}) }
        compose.onNodeWithText("Qué necesitas").assertExists()
        snapshot("guide-switch-rcm")
    }

    @Test
    fun newsWithFilters() {
        val now = java.time.Instant.parse("2026-10-29T10:00:00Z").toString()
        val news = listOf(
            com.jailbreakchecker.app.data.NewsItem(title = "Atmosphère 1.12.2", summary = "Soporte para 23.1.0.", consoleIds = listOf("switch1"), url = "https://github.com/Atmosphere-NX/Atmosphere/releases", source = "Atmosphère (GitHub)", createdAt = now, category = "herramientas", origin = "feed"),
            com.jailbreakchecker.app.data.NewsItem(title = "shadPS4 v0.12 mejora el rendimiento", summary = "Nueva versión del emulador de PS4.", consoleIds = listOf("ps4"), url = "https://github.com/shadps4-emu/shadPS4/releases", source = "shadPS4 (GitHub)", createdAt = now, category = "emuladores", origin = "feed"),
            com.jailbreakchecker.app.data.NewsItem(title = "Exploit de kernel para PS5 14.00", summary = "Demostración privada, sin publicar.", consoleIds = listOf("ps5"), status = "yellow", url = "https://example.com", source = "Wololo", createdAt = now, category = "jailbreak", origin = "ia"),
        )
        show { NewsList(news, catalog.consoles, false, {}, {}) }
        compose.onNodeWithText("Emuladores").performClick()
        compose.onNodeWithText("shadPS4 v0.12 mejora el rendimiento").assertExists()
        compose.onNodeWithText("Atmosphère 1.12.2").assertDoesNotExist()
        compose.onNodeWithText("Todo").performClick()
        snapshot("news")
    }

    @Test
    fun guideWithImportedImage() {
        val dir = File("build/tmp-images").apply { mkdirs() }
        val bmp = Bitmap.createBitmap(320, 160, Bitmap.Config.ARGB_8888).apply { eraseColor(android.graphics.Color.rgb(40, 120, 220)) }
        val file = File(dir, "paso.png").apply { outputStream().use { bmp.compress(Bitmap.CompressFormat.PNG, 100, it) } }
        val base = guides.getValue("switch-rcm")
        val guide = base.copy(
            origin = "importada",
            updatedAt = "2026-10-29T10:00:00Z",
            steps = listOf(base.steps[0].copy(image = file.toURI().toString(), imageCaption = "Menú de hekate")) + base.steps.drop(1),
        )
        show { GuideScreen(guide, "2026-10-28", false, false, {}, {}, {}) }
        compose.onNodeWithText("Menú de hekate").assertExists()
        snapshot("guide-with-image")
    }

    @Test
    fun consoleImportCard() {
        show { ConsoleScreen(console("ps1"), brand("sony"), guides, false, false, {}, {}, {}, { "prompt" }, {}) }
        compose.onNodeWithText("Actualizar con otra IA (gratis)").assertExists()
    }

    @Test
    fun settings() {
        show { SettingsScreen(AppSettings(), UpdaterMeta(), false, {}, { _, _ -> null }, {}, {}, {}) }
        compose.onNodeWithText("Guardar y comprobar").assertExists()
        snapshot("settings")
    }
}
