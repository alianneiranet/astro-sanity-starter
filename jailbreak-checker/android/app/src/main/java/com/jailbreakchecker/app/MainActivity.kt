package com.jailbreakchecker.app

import android.os.Bundle
import androidx.activity.ComponentActivity
import androidx.activity.compose.setContent
import androidx.activity.enableEdgeToEdge
import androidx.activity.viewModels
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.Surface
import androidx.compose.runtime.Composable
import androidx.compose.runtime.CompositionLocalProvider
import androidx.compose.runtime.getValue
import androidx.core.splashscreen.SplashScreen.Companion.installSplashScreen
import androidx.lifecycle.compose.collectAsStateWithLifecycle
import androidx.navigation.compose.NavHost
import androidx.navigation.compose.composable
import androidx.navigation.compose.rememberNavController
import com.jailbreakchecker.app.ui.BrandScreen
import com.jailbreakchecker.app.ui.ConsoleScreen
import com.jailbreakchecker.app.ui.GuideScreen
import com.jailbreakchecker.app.ui.HomeScreen
import com.jailbreakchecker.app.ui.LocalLogoIds
import com.jailbreakchecker.app.ui.SettingsScreen
import com.jailbreakchecker.app.ui.theme.JailbreakTheme

class MainActivity : ComponentActivity() {

    private val viewModel: MainViewModel by viewModels()

    override fun onCreate(savedInstanceState: Bundle?) {
        installSplashScreen()
        super.onCreate(savedInstanceState)
        enableEdgeToEdge()
        setContent {
            JailbreakTheme {
                Surface(color = MaterialTheme.colorScheme.background) {
                    CompositionLocalProvider(LocalLogoIds provides viewModel.logoIds) {
                        AppNavigation(viewModel)
                    }
                }
            }
        }
    }
}

@Composable
private fun AppNavigation(viewModel: MainViewModel) {
    val nav = rememberNavController()
    val consoles by viewModel.consoles.collectAsStateWithLifecycle()
    val news by viewModel.news.collectAsStateWithLifecycle()
    val meta by viewModel.meta.collectAsStateWithLifecycle()
    val settings by viewModel.settings.collectAsStateWithLifecycle()
    val running by viewModel.running.collectAsStateWithLifecycle()
    val brands = viewModel.brands
    val guideList by viewModel.guides.collectAsStateWithLifecycle()
    val guides = guideList.associateBy { it.id }
    val updatingGuides = running.filter { it.startsWith("guide:") }.map { it.removePrefix("guide:") }.toSet()

    NavHost(navController = nav, startDestination = "home") {
        composable("home") {
            HomeScreen(
                brands = brands,
                consoles = consoles,
                news = news,
                meta = meta,
                guides = guideList,
                updatingGuides = updatingGuides,
                onUpdateAllGuides = viewModel::updateAllGuides,
                hasApiKey = settings.hasApiKey,
                searching = "scan" in running,
                onBrand = { nav.navigate("brand/$it") },
                onConsole = { nav.navigate("console/$it") },
                onGuide = { nav.navigate("guide/$it") },
                onSearchNow = viewModel::runNow,
                onSettings = { nav.navigate("settings") },
            )
        }
        composable("brand/{brandId}") { entry ->
            val brandId = entry.arguments?.getString("brandId")
            BrandScreen(
                brand = brands.firstOrNull { it.id == brandId },
                consoles = consoles.filter { it.brandId == brandId },
                onBack = { nav.popBackStack() },
                onConsole = { nav.navigate("console/$it") },
            )
        }
        composable("console/{consoleId}") { entry ->
            val consoleId = entry.arguments?.getString("consoleId").orEmpty()
            val console = consoles.firstOrNull { it.id == consoleId }
            ConsoleScreen(
                console = console,
                brand = brands.firstOrNull { it.id == console?.brandId },
                guides = guides,
                hasApiKey = settings.hasApiKey,
                reviewing = consoleId in running,
                onBack = { nav.popBackStack() },
                onGuide = { nav.navigate("guide/$it") },
                onReview = { viewModel.reviewNow(consoleId) },
            )
        }
        composable("guide/{guideId}") { entry ->
            val guideId = entry.arguments?.getString("guideId").orEmpty()
            GuideScreen(
                guide = guides[guideId],
                reviewedAt = viewModel.guidesReviewedAt,
                hasApiKey = settings.hasApiKey,
                updating = guideId in updatingGuides,
                onUpdate = { viewModel.updateGuides(listOf(guideId)) },
                onSettings = { nav.navigate("settings") },
                onBack = { nav.popBackStack() },
            )
        }
        composable("settings") {
            SettingsScreen(
                settings = settings,
                meta = meta,
                running = running.isNotEmpty(),
                onSave = viewModel::saveSettings,
                onVerify = viewModel::verifyKey,
                onRunNow = viewModel::runNow,
                onClearAi = { viewModel.clearAiData() },
                onBack = { nav.popBackStack() },
            )
        }
    }
}
