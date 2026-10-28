package com.jailbreakchecker.app.ui

import androidx.compose.foundation.background
import androidx.compose.foundation.layout.ExperimentalLayoutApi
import androidx.compose.foundation.layout.FlowRow
import androidx.compose.material3.Button
import androidx.compose.material3.FilterChip
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.saveable.rememberSaveable
import androidx.compose.runtime.setValue
import androidx.compose.ui.text.style.TextOverflow
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
import androidx.compose.foundation.lazy.LazyColumn
import androidx.compose.foundation.lazy.items
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.automirrored.outlined.ArrowBack
import androidx.compose.material.icons.automirrored.outlined.OpenInNew
import androidx.compose.material.icons.outlined.Newspaper
import androidx.compose.material3.AssistChip
import androidx.compose.material3.Card
import androidx.compose.material3.CardDefaults
import androidx.compose.material3.CircularProgressIndicator
import androidx.compose.material3.ExperimentalMaterial3Api
import androidx.compose.material3.Icon
import androidx.compose.material3.IconButton
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.Scaffold
import androidx.compose.material3.Text
import androidx.compose.material3.TopAppBar
import androidx.compose.material3.TopAppBarDefaults
import androidx.compose.runtime.Composable
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.graphics.Brush
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.graphics.lerp
import androidx.compose.ui.platform.LocalUriHandler
import androidx.compose.ui.text.style.TextAlign
import androidx.compose.ui.unit.dp
import com.jailbreakchecker.app.data.Brand
import com.jailbreakchecker.app.data.ConsoleInfo
import com.jailbreakchecker.app.data.NewsItem
import com.jailbreakchecker.app.data.Status

@OptIn(ExperimentalMaterial3Api::class)
@Composable
fun BrandScreen(
    brand: Brand?,
    consoles: List<ConsoleInfo>,
    onBack: () -> Unit,
    onConsole: (String) -> Unit,
) {
    val color = parseHexColor(brand?.color ?: "#455A64")
    val current = consoles.filter { !it.legacy }.sortedByDescending { it.year }
    val classic = consoles.filter { it.legacy }.sortedByDescending { it.year }
    Scaffold(
        topBar = {
            TopAppBar(
                title = { Text(brand?.name ?: "") },
                navigationIcon = {
                    IconButton(onClick = onBack) { Icon(Icons.AutoMirrored.Outlined.ArrowBack, "Atrás") }
                },
                colors = TopAppBarDefaults.topAppBarColors(
                    containerColor = color,
                    titleContentColor = Color.White,
                    navigationIconContentColor = Color.White,
                ),
            )
        },
    ) { padding ->
        LazyColumn(
            Modifier
                .padding(padding)
                .fillMaxSize(),
            contentPadding = PaddingValues(bottom = 24.dp),
            verticalArrangement = Arrangement.spacedBy(10.dp),
        ) {
            item {
                Column(
                    Modifier
                        .fillMaxWidth()
                        .background(Brush.verticalGradient(listOf(color, lerp(color, Color.Black, 0.6f))))
                        .padding(horizontal = 20.dp, vertical = 18.dp),
                ) {
                    Text(brand?.company ?: "", color = Color.White.copy(alpha = 0.8f))
                    Text(
                        brand?.description ?: "",
                        color = Color.White,
                        style = MaterialTheme.typography.bodyLarge,
                    )
                    Spacer(Modifier.height(6.dp))
                    Text(
                        "Toca una consola para ver su estado.",
                        color = Color.White.copy(alpha = 0.8f),
                        style = MaterialTheme.typography.bodySmall,
                    )
                }
            }
            if (current.isNotEmpty()) {
                item { SectionTitle("Actuales") }
                items(current, key = { it.id }) {
                    Box(Modifier.padding(horizontal = 16.dp)) { ConsoleRow(it, brand) { onConsole(it.id) } }
                }
            }
            if (classic.isNotEmpty()) {
                item { SectionTitle("Clásicas / antiguas") }
                items(classic, key = { it.id }) {
                    Box(Modifier.padding(horizontal = 16.dp)) { ConsoleRow(it, brand) { onConsole(it.id) } }
                }
            }
        }
    }
}

@Composable
fun SectionTitle(text: String) {
    Text(
        text,
        style = MaterialTheme.typography.titleMedium,
        modifier = Modifier.padding(start = 20.dp, end = 20.dp, top = 12.dp, bottom = 2.dp),
    )
}

private val NEWS_FILTERS = listOf("todo" to "Todo", "jailbreak" to "Jailbreak", "emuladores" to "Emuladores", "herramientas" to "Herramientas")

private fun categoryLabel(category: String) = when (category) {
    "emuladores" -> "Emulador"
    "herramientas" -> "Herramienta"
    else -> "Jailbreak"
}

@OptIn(ExperimentalLayoutApi::class)
@Composable
fun NewsList(
    news: List<NewsItem>,
    consoles: List<ConsoleInfo>,
    searching: Boolean,
    onConsole: (String) -> Unit,
    onSearchNow: () -> Unit,
) {
    var filter by rememberSaveable { mutableStateOf("todo") }
    val names = consoles.associate { it.id to it.shortName }
    val uri = LocalUriHandler.current
    val shown = if (filter == "todo") news else news.filter { it.category == filter }

    LazyColumn(
        contentPadding = PaddingValues(16.dp),
        verticalArrangement = Arrangement.spacedBy(12.dp),
        modifier = Modifier.fillMaxSize(),
    ) {
        item {
            FlowRow(horizontalArrangement = Arrangement.spacedBy(8.dp)) {
                NEWS_FILTERS.forEach { (id, label) ->
                    FilterChip(selected = filter == id, onClick = { filter = id }, label = { Text(label) })
                }
            }
        }
        if (shown.isEmpty()) {
            item {
                EmptyNews(
                    if (searching) {
                        "Buscando novedades…"
                    } else {
                        "Todavía no hay noticias aquí. Pulsa \"Buscar novedades\": la app lee gratis las webs de la escena (Wololo, GBAtemp, Reddit) y las versiones nuevas de herramientas y emuladores en GitHub."
                    },
                )
                if (!searching) {
                    Button(onClick = onSearchNow, modifier = Modifier.fillMaxWidth()) { Text("Buscar novedades") }
                }
            }
        }
        items(shown, key = { it.url.ifBlank { it.title } + it.createdAt }) { item ->
            val status = Status.parse(item.status)
            Card(
                onClick = { if (item.url.isNotBlank()) runCatching { uri.openUri(item.url) } },
                shape = RoundedCornerShape(18.dp),
                colors = CardDefaults.cardColors(containerColor = MaterialTheme.colorScheme.surfaceContainer),
            ) {
                Row(Modifier.padding(14.dp)) {
                    if (status != Status.UNKNOWN) {
                        StatusDot(status, Modifier.padding(top = 5.dp), size = 12)
                        Spacer(Modifier.width(12.dp))
                    }
                    Column(Modifier.weight(1f)) {
                        Text(
                            categoryLabel(item.category).uppercase() + if (item.origin == "ia") " · IA" else "",
                            style = MaterialTheme.typography.labelSmall,
                            color = MaterialTheme.colorScheme.primary,
                        )
                        Text(item.title, style = MaterialTheme.typography.titleMedium)
                        if (item.summary.isNotBlank()) {
                            Spacer(Modifier.height(4.dp))
                            Text(item.summary, style = MaterialTheme.typography.bodyMedium, maxLines = 4, overflow = TextOverflow.Ellipsis)
                        }
                        if (item.consoleIds.isNotEmpty()) {
                            Spacer(Modifier.height(6.dp))
                            Row(horizontalArrangement = Arrangement.spacedBy(6.dp)) {
                                item.consoleIds.take(3).forEach { id ->
                                    AssistChip(onClick = { onConsole(id) }, label = { Text(names[id] ?: id) })
                                }
                            }
                        }
                        Row(verticalAlignment = Alignment.CenterVertically) {
                            Text(
                                listOf(item.source, relativeTime(item.createdAt))
                                    .filter { it.isNotBlank() }
                                    .joinToString(" · "),
                                style = MaterialTheme.typography.bodySmall,
                                color = MaterialTheme.colorScheme.onSurfaceVariant,
                                modifier = Modifier.weight(1f),
                            )
                            if (item.url.isNotBlank()) {
                                Icon(Icons.AutoMirrored.Outlined.OpenInNew, "Abrir", tint = MaterialTheme.colorScheme.onSurfaceVariant)
                            }
                        }
                    }
                }
            }
        }
    }
}

@Composable
private fun EmptyNews(text: String) {
    Column(
        Modifier
            .fillMaxWidth()
            .padding(24.dp),
        horizontalAlignment = Alignment.CenterHorizontally,
        verticalArrangement = Arrangement.Center,
    ) {
        Icon(Icons.Outlined.Newspaper, null, tint = MaterialTheme.colorScheme.onSurfaceVariant)
        Spacer(Modifier.height(12.dp))
        Text(text, textAlign = TextAlign.Center, color = MaterialTheme.colorScheme.onSurfaceVariant)
    }
}
