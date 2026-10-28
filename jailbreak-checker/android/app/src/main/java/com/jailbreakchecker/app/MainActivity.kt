package com.jailbreakchecker.app

import android.os.Bundle
import androidx.activity.ComponentActivity
import androidx.activity.compose.setContent
import androidx.activity.enableEdgeToEdge
import androidx.activity.viewModels
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.Surface
import androidx.compose.runtime.getValue
import androidx.core.splashscreen.SplashScreen.Companion.installSplashScreen
import androidx.lifecycle.compose.collectAsStateWithLifecycle
import androidx.navigation.compose.NavHost
import androidx.navigation.compose.composable
import androidx.navigation.compose.rememberNavController
import com.jailbreakchecker.app.ui.BrandScreen
import com.jailbreakchecker.app.ui.ConsoleScreen
import com.jailbreakchecker.app.ui.HomeScreen
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
                    AppNavigation(viewModel)
                }
            }
        }
    }
}

@androidx.compose.runtime.Composable
private fun AppNavigation(viewModel: MainViewModel) {
    val nav = rememberNavController()
    val brands by viewModel.brands.collectAsStateWithLifecycle()
    val consoles by viewModel.consoles.collectAsStateWithLifecycle()
    val news by viewModel.news.collectAsStateWithLifecycle()
    val meta by viewModel.meta.collectAsStateWithLifecycle()

    NavHost(navController = nav, startDestination = "home") {
        composable("home") {
            HomeScreen(
                brands = brands,
                consoles = consoles,
                news = news,
                meta = meta,
                onlineEnabled = viewModel.onlineEnabled,
                onBrand = { nav.navigate("brand/$it") },
                onConsole = { nav.navigate("console/$it") },
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
            val consoleId = entry.arguments?.getString("consoleId")
            val console = consoles.firstOrNull { it.id == consoleId }
            ConsoleScreen(
                console = console,
                brand = brands.firstOrNull { it.id == console?.brandId },
                onBack = { nav.popBackStack() },
            )
        }
    }
}
