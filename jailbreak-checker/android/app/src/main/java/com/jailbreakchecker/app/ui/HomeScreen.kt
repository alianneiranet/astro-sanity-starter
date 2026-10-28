package com.jailbreakchecker.app.ui

import androidx.compose.foundation.background
import androidx.compose.foundation.clickable
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.PaddingValues
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.width
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.lazy.grid.GridCells
import androidx.compose.foundation.lazy.grid.GridItemSpan
import androidx.compose.foundation.lazy.grid.LazyVerticalGrid
import androidx.compose.foundation.lazy.grid.items
import androidx.compose.foundation.lazy.LazyColumn
import androidx.compose.foundation.lazy.items
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.outlined.AutoAwesome
import androidx.compose.material.icons.outlined.Refresh
import androidx.compose.material.icons.outlined.Settings
import androidx.compose.material.icons.automirrored.outlined.KeyboardArrowRight
import androidx.compose.material.icons.automirrored.outlined.MenuBook
import androidx.compose.material.icons.outlined.Info
import androidx.compose.material.icons.outlined.Newspaper
import androidx.compose.material.icons.outlined.Search
import androidx.compose.material.icons.outlined.SportsEsports
import androidx.compose.material.icons.outlined.Close
import androidx.compose.material3.Card
import androidx.compose.material3.CircularProgressIndicator
import androidx.compose.material3.CardDefaults
import androidx.compose.material3.ExperimentalMaterial3Api
import androidx.compose.material3.Icon
import androidx.compose.material3.IconButton
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.NavigationBar
import androidx.compose.material3.NavigationBarItem
import androidx.compose.material3.OutlinedTextField
import androidx.compose.material3.Scaffold
import androidx.compose.material3.Text
import androidx.compose.material3.TopAppBar
import androidx.compose.runtime.Composable
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableIntStateOf
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.saveable.rememberSaveable
import androidx.compose.runtime.setValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clip
import androidx.compose.ui.graphics.Brush
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.graphics.lerp
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.text.style.TextOverflow
import androidx.compose.ui.unit.dp
import com.jailbreakchecker.app.data.Brand
import com.jailbreakchecker.app.data.ConsoleInfo
import com.jailbreakchecker.app.data.Guide
import com.jailbreakchecker.app.data.NewsItem
import com.jailbreakchecker.app.data.Status
import com.jailbreakchecker.app.data.UpdaterMeta
import java.text.Normalizer

@OptIn(ExperimentalMaterial3Api::class)
@Composable
fun HomeScreen(
    brands: List<Brand>,
    consoles: List<ConsoleInfo>,
    news: List<NewsItem>,
    meta: UpdaterMeta,
    guides: List<Guide>,
    hasApiKey: Boolean,
    searching: Boolean,
    onBrand: (String) -> Unit,
    onConsole: (String) -> Unit,
    onGuide: (String) -> Unit,
    onSearchNow: () -> Unit,
    onSettings: () -> Unit,
) {
    var tab by rememberSaveable { mutableIntStateOf(0) }
    Scaffold(
        topBar = {
            TopAppBar(
                title = {
                    Column {
                        Text("Revisor de Jailbreak", style = MaterialTheme.typography.titleLarge)
                        Text(
                            updateLine(meta, hasApiKey, searching),
                            style = MaterialTheme.typography.bodySmall,
                            color = MaterialTheme.colorScheme.onSurfaceVariant,
                        )
                    }
                },
                actions = {
                    if (searching) {
                        CircularProgressIndicator(Modifier.padding(12.dp).size(22.dp), strokeWidth = 2.dp)
                    } else {
                        IconButton(onClick = if (hasApiKey) onSearchNow else onSettings) {
                            Icon(Icons.Outlined.Refresh, "Buscar novedades ahora")
                        }
                    }
                    IconButton(onClick = onSettings) { Icon(Icons.Outlined.Settings, "Ajustes") }
                },
            )
        },
        bottomBar = {
            NavigationBar {
                NavigationBarItem(
                    selected = tab == 0,
                    onClick = { tab = 0 },
                    icon = { Icon(Icons.Outlined.SportsEsports, null) },
                    label = { Text("Consolas") },
                )
                NavigationBarItem(
                    selected = tab == 1,
                    onClick = { tab = 1 },
                    icon = { Icon(Icons.Outlined.Newspaper, null) },
                    label = { Text("Noticias") },
                )
                NavigationBarItem(
                    selected = tab == 2,
                    onClick = { tab = 2 },
                    icon = { Icon(Icons.AutoMirrored.Outlined.MenuBook, null) },
                    label = { Text("Guías") },
                )
            }
        },
    ) { padding ->
        Box(Modifier.padding(padding)) {
            when (tab) {
                0 -> BrandsTab(brands, consoles, hasApiKey, onBrand, onConsole, onSettings)
                1 -> NewsList(news, consoles, hasApiKey, onConsole, onSettings)
                else -> GuidesList(guides, consoles, onGuide)
            }
        }
    }
}

private fun updateLine(meta: UpdaterMeta, hasApiKey: Boolean, searching: Boolean): String {
    if (searching) return "La IA está buscando novedades…"
    if (!hasApiKey) return "Datos de octubre de 2026 · IA sin configurar"
    val last = meta.lastSuccessAt.takeIf { it.isNotBlank() } ?: return "La IA aún no ha buscado novedades"
    return "La IA revisó las noticias ${relativeTime(last)}"
}

@Composable
private fun BrandsTab(
    brands: List<Brand>,
    consoles: List<ConsoleInfo>,
    hasApiKey: Boolean,
    onBrand: (String) -> Unit,
    onConsole: (String) -> Unit,
    onSettings: () -> Unit,
) {
    var query by rememberSaveable { mutableStateOf("") }
    val results = remember(query, consoles) { searchConsoles(consoles, query) }
    val brandById = brands.associateBy { it.id }

    LazyVerticalGrid(
        columns = GridCells.Adaptive(minSize = 160.dp),
        contentPadding = PaddingValues(16.dp),
        horizontalArrangement = Arrangement.spacedBy(12.dp),
        verticalArrangement = Arrangement.spacedBy(12.dp),
        modifier = Modifier.fillMaxSize(),
    ) {
        if (!hasApiKey) {
            item(span = { GridItemSpan(maxLineSpan) }) { SetupBanner(onSettings) }
        }
        item(span = { GridItemSpan(maxLineSpan) }) {
            OutlinedTextField(
                value = query,
                onValueChange = { query = it },
                modifier = Modifier.fillMaxWidth(),
                singleLine = true,
                leadingIcon = { Icon(Icons.Outlined.Search, null) },
                trailingIcon = {
                    if (query.isNotEmpty()) {
                        IconButton(onClick = { query = "" }) { Icon(Icons.Outlined.Close, "Borrar") }
                    }
                },
                placeholder = { Text("Buscar consola (ej. PS4, Switch…)") },
                shape = RoundedCornerShape(16.dp),
            )
        }
        if (query.isNotBlank()) {
            if (results.isEmpty()) {
                item(span = { GridItemSpan(maxLineSpan) }) {
                    Text(
                        "No encontramos «$query».",
                        Modifier.padding(8.dp),
                        color = MaterialTheme.colorScheme.onSurfaceVariant,
                    )
                }
            }
            items(results, key = { it.id }, span = { GridItemSpan(maxLineSpan) }) { console ->
                ConsoleRow(console, brandById[console.brandId], onClick = { onConsole(console.id) })
            }
        } else {
            item(span = { GridItemSpan(maxLineSpan) }) {
                Text(
                    "Elige una compañía",
                    style = MaterialTheme.typography.titleMedium,
                    modifier = Modifier.padding(top = 4.dp),
                )
            }
            items(brands, key = { it.id }) { brand ->
                BrandCard(brand, consoles.filter { it.brandId == brand.id }, onClick = { onBrand(brand.id) })
            }
            item(span = { GridItemSpan(maxLineSpan) }) {
                Card(
                    colors = CardDefaults.cardColors(containerColor = MaterialTheme.colorScheme.surfaceContainer),
                    shape = RoundedCornerShape(20.dp),
                ) {
                    Column(Modifier.padding(16.dp)) {
                        Text("¿Qué significan los colores?", style = MaterialTheme.typography.titleMedium)
                        Spacer(Modifier.height(12.dp))
                        StatusLegend()
                    }
                }
            }
            item(span = { GridItemSpan(maxLineSpan) }) { Disclaimer() }
        }
    }
}

private fun normalize(s: String): String =
    Normalizer.normalize(s, Normalizer.Form.NFD).replace(Regex("\\p{M}+"), "")
        .lowercase().replace(Regex("[^a-z0-9]"), "")

fun searchConsoles(consoles: List<ConsoleInfo>, query: String): List<ConsoleInfo> {
    val q = normalize(query)
    if (q.isEmpty()) return emptyList()
    return consoles.filter { c ->
        listOf(c.name, c.shortName, c.id).any { normalize(it).contains(q) }
    }.sortedByDescending { it.year }
}

@Composable
private fun BrandCard(brand: Brand, consoles: List<ConsoleInfo>, onClick: () -> Unit) {
    val color = parseHexColor(brand.color)
    val newest = consoles.maxByOrNull { it.year }
    val counts = consoles.groupingBy { Status.parse(it.summary.status) }.eachCount()
    Card(
        onClick = onClick,
        shape = RoundedCornerShape(22.dp),
        colors = CardDefaults.cardColors(containerColor = Color.Transparent),
    ) {
        Column(
            Modifier
                .fillMaxWidth()
                .background(Brush.verticalGradient(listOf(color, lerp(color, Color.Black, 0.55f))))
                .padding(16.dp),
        ) {
            Row(verticalAlignment = Alignment.CenterVertically) {
                ConsoleLogo(brand.id, newest?.formFactor ?: "home", lerp(color, Color.White, 0.15f), size = 52.dp, width = 96.dp)
                Spacer(Modifier.weight(1f))
                Text(
                    "${consoles.size}",
                    color = Color.White,
                    style = MaterialTheme.typography.headlineSmall,
                )
            }
            Spacer(Modifier.height(14.dp))
            Text(
                brand.name,
                color = Color.White,
                style = MaterialTheme.typography.titleLarge,
                maxLines = 1,
                overflow = TextOverflow.Ellipsis,
            )
            Text(
                brand.company,
                color = Color.White.copy(alpha = 0.8f),
                style = MaterialTheme.typography.bodySmall,
            )
            Spacer(Modifier.height(10.dp))
            Row(horizontalArrangement = Arrangement.spacedBy(8.dp), verticalAlignment = Alignment.CenterVertically) {
                listOf(Status.GREEN, Status.ORANGE, Status.YELLOW, Status.RED).forEach { s ->
                    val n = counts[s] ?: 0
                    if (n > 0) {
                        Row(verticalAlignment = Alignment.CenterVertically) {
                            StatusDot(s, size = 10)
                            Spacer(Modifier.width(3.dp))
                            Text("$n", color = Color.White, style = MaterialTheme.typography.labelMedium)
                        }
                    }
                }
            }
        }
    }
}

@Composable
fun ConsoleRow(console: ConsoleInfo, brand: Brand?, onClick: () -> Unit) {
    val status = Status.parse(console.summary.status)
    Card(
        onClick = onClick,
        shape = RoundedCornerShape(18.dp),
        colors = CardDefaults.cardColors(containerColor = MaterialTheme.colorScheme.surfaceContainer),
    ) {
        Row(Modifier.padding(12.dp), verticalAlignment = Alignment.CenterVertically) {
            ConsoleLogo(console.id, console.formFactor, parseHexColor(brand?.color ?: "#455A64"), size = 56.dp, width = 84.dp)
            Spacer(Modifier.width(14.dp))
            Column(Modifier.weight(1f)) {
                Text(console.name, style = MaterialTheme.typography.titleMedium, maxLines = 2)
                Text(
                    buildString {
                        if (console.year > 0) append(console.year)
                        if (console.latestFirmware.isNotBlank()) append(" · FW ${console.latestFirmware}")
                    },
                    style = MaterialTheme.typography.bodySmall,
                    color = MaterialTheme.colorScheme.onSurfaceVariant,
                )
                Spacer(Modifier.height(6.dp))
                Text(
                    console.summary.text,
                    style = MaterialTheme.typography.bodySmall,
                    maxLines = 2,
                    overflow = TextOverflow.Ellipsis,
                )
            }
            Spacer(Modifier.width(10.dp))
            StatusDot(status, size = 18)
        }
    }
}

@Composable
fun SetupBanner(onSettings: () -> Unit) {
    Row(
        Modifier
            .fillMaxWidth()
            .clip(RoundedCornerShape(16.dp))
            .background(MaterialTheme.colorScheme.surfaceContainerHigh)
            .clickable(onClick = onSettings)
            .padding(14.dp),
        verticalAlignment = Alignment.CenterVertically,
    ) {
        Icon(Icons.Outlined.AutoAwesome, null, tint = MaterialTheme.colorScheme.primary)
        Spacer(Modifier.width(12.dp))
        Column(Modifier.weight(1f)) {
            Text("Activa la búsqueda con IA", style = MaterialTheme.typography.labelLarge)
            Text(
                "Pon tu API key de Anthropic y la app buscará las novedades ella misma cada hora.",
                style = MaterialTheme.typography.bodySmall,
            )
        }
        Icon(Icons.AutoMirrored.Outlined.KeyboardArrowRight, null)
    }
}

@Composable
fun Disclaimer(modifier: Modifier = Modifier) {
    Row(modifier.padding(horizontal = 4.dp, vertical = 8.dp)) {
        Icon(
            Icons.Outlined.Info,
            null,
            tint = MaterialTheme.colorScheme.onSurfaceVariant,
            modifier = Modifier.padding(top = 2.dp).width(16.dp),
        )
        Spacer(Modifier.width(8.dp))
        Text(
            "Solo informativo. Modificar tu consola puede anular la garantía o provocar baneos online. " +
                "Usa homebrew y copias de juegos que te pertenezcan y respeta las leyes de tu país.",
            style = MaterialTheme.typography.bodySmall,
            color = MaterialTheme.colorScheme.onSurfaceVariant,
            fontWeight = FontWeight.Normal,
        )
    }
}

@Composable
private fun GuidesList(guides: List<Guide>, consoles: List<ConsoleInfo>, onGuide: (String) -> Unit) {
    val names = consoles.associate { it.id to it.shortName }
    LazyColumn(
        contentPadding = PaddingValues(16.dp),
        verticalArrangement = Arrangement.spacedBy(10.dp),
        modifier = Modifier.fillMaxSize(),
    ) {
        item {
            Text(
                "Guías paso a paso de cada método. También las encuentras dentro de cada consola, junto al firmware que te corresponde.",
                style = MaterialTheme.typography.bodyMedium,
                color = MaterialTheme.colorScheme.onSurfaceVariant,
            )
        }
        items(guides, key = { it.id }) { guide ->
            Card(
                onClick = { onGuide(guide.id) },
                shape = RoundedCornerShape(16.dp),
                colors = CardDefaults.cardColors(containerColor = MaterialTheme.colorScheme.surfaceContainer),
            ) {
                Column(Modifier.padding(14.dp)) {
                    Text(guide.title, style = MaterialTheme.typography.titleMedium)
                    Text(
                        guide.consoleIds.mapNotNull { names[it] }.joinToString(" · ") +
                            if (guide.time.isNotBlank()) " · ${guide.time}" else "",
                        style = MaterialTheme.typography.bodySmall,
                        color = MaterialTheme.colorScheme.onSurfaceVariant,
                    )
                }
            }
        }
    }
}
