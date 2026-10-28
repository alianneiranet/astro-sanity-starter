package com.jailbreakchecker.app.ui

import android.graphics.Bitmap
import androidx.compose.ui.test.junit4.createAndroidComposeRule
import androidx.activity.ComponentActivity
import android.graphics.Canvas
import androidx.compose.ui.test.onNodeWithText
import androidx.compose.ui.test.performClick
import androidx.compose.ui.test.performTextInput
import androidx.compose.ui.test.hasSetTextAction
import com.jailbreakchecker.app.data.Catalog
import com.jailbreakchecker.app.data.Repository
import com.jailbreakchecker.app.ui.theme.JailbreakTheme
import org.junit.Rule
import org.junit.Test
import org.junit.runner.RunWith
import org.robolectric.RobolectricTestRunner
import org.robolectric.annotation.Config
import org.robolectric.annotation.GraphicsMode
import java.io.File

/**
 * Renders the main screens with the bundled catalog. Catches crashes in the UI code and
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

    private fun snapshot(name: String) {
        val dir = File("build/screenshots").apply { mkdirs() }
        compose.waitForIdle()
        val view = compose.activity.window.decorView
        val bitmap = Bitmap.createBitmap(view.width, view.height, Bitmap.Config.ARGB_8888)
        view.draw(Canvas(bitmap))
        File(dir, "$name.png").outputStream().use { bitmap.compress(Bitmap.CompressFormat.PNG, 100, it) }
    }

    @Test
    fun home() {
        compose.setContent {
            JailbreakTheme(darkTheme = true) {
                HomeScreen(catalog.brands, catalog.consoles, null, null, false, {}, {})
            }
        }
        compose.onNodeWithText("Nintendo").assertExists()
        snapshot("home")
    }

    @Test
    fun brand() {
        compose.setContent {
            JailbreakTheme(darkTheme = true) {
                BrandScreen(catalog.brands.first { it.id == "nintendo" }, catalog.consoles.filter { it.brandId == "nintendo" }, {}, {})
            }
        }
        compose.onNodeWithText("Nintendo Switch 2").assertExists()
        snapshot("brand-nintendo")
    }

    @Test
    fun ps5WithVersionSearch() {
        val ps5 = catalog.consoles.first { it.id == "ps5" }
        compose.setContent {
            JailbreakTheme(darkTheme = true) { ConsoleScreen(ps5, catalog.brands.first { it.id == "sony" }, {}) }
        }
        compose.onNode(hasSetTextAction()).performTextInput("9.60")
        compose.onNodeWithText("Tu versión 9.60").assertExists()
        snapshot("console-ps5")
    }

    @Test
    fun switchAsksModelFirst() {
        val sw = catalog.consoles.first { it.id == "switch1" }
        compose.setContent {
            JailbreakTheme(darkTheme = true) { ConsoleScreen(sw, catalog.brands.first { it.id == "nintendo" }, {}) }
        }
        compose.onNodeWithText("¿Qué modelo tienes?").assertExists()
        compose.onNodeWithText("V1 parcheada, V2, Lite u OLED").performClick()
        compose.onNodeWithText("¿Qué firmware tienes?").assertExists()
        snapshot("console-switch1")
    }

    @Test
    fun ps1SkipsFirmwareQuestion() {
        val ps1 = catalog.consoles.first { it.id == "ps1" }
        compose.setContent {
            JailbreakTheme(darkTheme = true) { ConsoleScreen(ps1, catalog.brands.first { it.id == "sony" }, {}) }
        }
        compose.onNodeWithText("No hace falta elegir firmware").assertExists()
        snapshot("console-ps1")
    }
}
