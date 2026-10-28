package com.jailbreakchecker.app.ui

import androidx.compose.animation.AnimatedVisibility
import androidx.compose.animation.animateContentSize
import androidx.compose.foundation.BorderStroke
import androidx.compose.foundation.background
import androidx.compose.foundation.layout.Arrangement
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
import androidx.compose.foundation.lazy.LazyColumn
import androidx.compose.foundation.lazy.items
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.foundation.text.KeyboardOptions
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.automirrored.outlined.ArrowBack
import androidx.compose.material.icons.automirrored.outlined.OpenInNew
import androidx.compose.material.icons.outlined.AutoAwesome
import androidx.compose.material.icons.outlined.CheckCircle
import androidx.compose.material.icons.automirrored.outlined.MenuBook
import androidx.compose.material.icons.outlined.ExpandLess
import androidx.compose.material.icons.outlined.ExpandMore
import androidx.compose.material.icons.outlined.Search
import androidx.compose.material3.Button
import androidx.compose.material3.Card
import androidx.compose.material3.CircularProgressIndicator
import androidx.compose.material3.OutlinedButton
import androidx.compose.material3.CardDefaults
import androidx.compose.material3.ExperimentalMaterial3Api
import androidx.compose.material3.HorizontalDivider
import androidx.compose.material3.Icon
import androidx.compose.material3.IconButton
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.OutlinedTextField
import androidx.compose.material3.Scaffold
import androidx.compose.material3.Text
import androidx.compose.material3.TextButton
import androidx.compose.material3.TopAppBar
import androidx.compose.runtime.Composable
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.saveable.rememberSaveable
import androidx.compose.runtime.setValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clip
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.platform.LocalUriHandler
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.text.input.KeyboardType
import androidx.compose.ui.unit.dp
import com.jailbreakchecker.app.data.Brand
import com.jailbreakchecker.app.data.ConsoleInfo
import com.jailbreakchecker.app.data.FirmwareRange
import com.jailbreakchecker.app.data.Guide
import com.jailbreakchecker.app.data.Status
import com.jailbreakchecker.app.data.StatusInfo
import com.jailbreakchecker.app.data.Versions

@OptIn(ExperimentalMaterial3Api::class)
@Composable
fun ConsoleScreen(
    console: ConsoleInfo?,
    brand: Brand?,
    guides: Map<String, Guide>,
    hasApiKey: Boolean,
    reviewing: Boolean,
    onBack: () -> Unit,
    onGuide: (String) -> Unit,
    onReview: () -> Unit,
    prompt: () -> String = { "" },
    onImportText: (String) -> Unit = {},
) {
    Scaffold(
        topBar = {
            TopAppBar(
                title = { Text(console?.shortName ?: "") },
                navigationIcon = {
                    IconButton(onClick = onBack) { Icon(Icons.AutoMirrored.Outlined.ArrowBack, "Atrás") }
                },
            )
        },
    ) { padding ->
        if (console == null) {
            Text("Consola no encontrada", Modifier.padding(padding).padding(24.dp))
            return@Scaffold
        }
        // The variant question is only asked when the console has models that behave differently.
        var variantId by rememberSaveable(console.id) { mutableStateOf(console.variants.singleOrNull()?.id) }
        var query by rememberSaveable(console.id) { mutableStateOf("") }
        var expanded by rememberSaveable(console.id) { mutableStateOf<String?>(null) }

        val needsVariant = console.asksFirmware && console.variants.size > 1 && variantId == null
        val selectedVariant = console.variants.firstOrNull { it.id == variantId }
        val ranges = console.rangesFor(variantId)
        val guideFor: (String) -> Guide? = { id -> guides[id] }
        val match = if (query.isBlank()) null else Versions.match(query, ranges)

        LazyColumn(
            Modifier
                .padding(padding)
                .fillMaxSize(),
            contentPadding = PaddingValues(16.dp),
            verticalArrangement = Arrangement.spacedBy(12.dp),
        ) {
            item { Header(console, brand) }

            if (!console.asksFirmware) {
                item {
                    AllVersionsCard(
                        console.allVersions ?: StatusInfo(console.summary.status, console.summary.text),
                        "No hace falta elegir firmware",
                        guideFor,
                        onGuide,
                    )
                }
            } else {
                if (console.variants.size > 1) {
                    item { StepTitle(1, "¿Qué modelo tienes?") }
                    items(console.variants, key = { "v-" + it.id }) { variant ->
                        val selected = variant.id == variantId
                        Card(
                            onClick = {
                                variantId = variant.id
                                expanded = null
                            },
                            shape = RoundedCornerShape(16.dp),
                            border = if (selected) BorderStroke(2.dp, MaterialTheme.colorScheme.primary) else null,
                            colors = CardDefaults.cardColors(
                                containerColor = if (selected) {
                                    MaterialTheme.colorScheme.surfaceContainerHighest
                                } else {
                                    MaterialTheme.colorScheme.surfaceContainer
                                },
                            ),
                        ) {
                            Row(Modifier.padding(14.dp), verticalAlignment = Alignment.CenterVertically) {
                                Column(Modifier.weight(1f)) {
                                    Text(variant.name, style = MaterialTheme.typography.titleMedium)
                                    if (variant.description.isNotBlank()) {
                                        Text(
                                            variant.description,
                                            style = MaterialTheme.typography.bodySmall,
                                            color = MaterialTheme.colorScheme.onSurfaceVariant,
                                        )
                                    }
                                }
                                variant.allVersions?.let {
                                    Spacer(Modifier.width(8.dp))
                                    StatusPill(Status.parse(it.status))
                                }
                                if (selected) {
                                    Spacer(Modifier.width(6.dp))
                                    Icon(Icons.Outlined.CheckCircle, null, tint = MaterialTheme.colorScheme.primary)
                                }
                            }
                        }
                    }
                }

                if (selectedVariant?.allVersions != null) {
                    item {
                        AllVersionsCard(selectedVariant.allVersions, "Con este modelo no importa el firmware", guideFor, onGuide)
                    }
                } else if (!needsVariant) {
                    item {
                        StepTitle(if (console.variants.size > 1) 2 else 1, "¿Qué firmware tienes?")
                        if (console.versionHint.isNotBlank()) {
                            Text(
                                "Lo ves en: ${console.versionHint}",
                                style = MaterialTheme.typography.bodySmall,
                                color = MaterialTheme.colorScheme.onSurfaceVariant,
                                modifier = Modifier.padding(top = 4.dp),
                            )
                        }
                    }
                    item {
                        OutlinedTextField(
                            value = query,
                            onValueChange = { query = it.take(24) },
                            modifier = Modifier.fillMaxWidth(),
                            singleLine = true,
                            leadingIcon = { Icon(Icons.Outlined.Search, null) },
                            placeholder = {
                                Text("Escribe tu versión, ej. ${console.firmwares.firstOrNull()?.max?.ifBlank { null } ?: "1.00"}")
                            },
                            keyboardOptions = KeyboardOptions(keyboardType = KeyboardType.Decimal),
                            shape = RoundedCornerShape(16.dp),
                        )
                    }
                    if (query.isNotBlank()) {
                        item { MatchResult(query, match) }
                    }
                    item {
                        Text(
                            "O elige tu rango de la lista:",
                            style = MaterialTheme.typography.bodyMedium,
                            color = MaterialTheme.colorScheme.onSurfaceVariant,
                        )
                    }
                    items(ranges, key = { "f-" + it.id }) { range ->
                        FirmwareRow(
                            range = range,
                            expanded = expanded == range.id || match?.id == range.id,
                            highlighted = match?.id == range.id,
                            guide = guideFor(range.guideId),
                            onClick = { expanded = if (expanded == range.id) null else range.id },
                            onGuide = onGuide,
                        )
                    }
                }
            }

            val related = guides.values.filter { console.id in it.consoleIds }
            if (related.isNotEmpty()) {
                item { Text("Guías para esta consola", style = MaterialTheme.typography.titleMedium, modifier = Modifier.padding(top = 8.dp)) }
                items(related, key = { "g-" + it.id }) { g ->
                    Card(
                        onClick = { onGuide(g.id) },
                        shape = RoundedCornerShape(16.dp),
                        colors = CardDefaults.cardColors(containerColor = MaterialTheme.colorScheme.surfaceContainer),
                    ) {
                        Row(Modifier.padding(14.dp), verticalAlignment = Alignment.CenterVertically) {
                            Icon(Icons.AutoMirrored.Outlined.MenuBook, null, tint = MaterialTheme.colorScheme.primary)
                            Spacer(Modifier.width(12.dp))
                            Column(Modifier.weight(1f)) {
                                Text(g.title, style = MaterialTheme.typography.bodyLarge)
                                if (g.origin == "importada") {
                                    Text("Importada", style = MaterialTheme.typography.labelSmall, color = MaterialTheme.colorScheme.primary)
                                }
                            }
                        }
                    }
                }
            }
            if (console.sources.isNotEmpty()) {
                item { SourcesCard(console) }
            }
            item {
                ImportCard(
                    title = "Actualizar con otra IA (gratis)",
                    description = "Copia el prompt y pégalo en ChatGPT, Gemini o Claude con búsqueda web. Luego copia su respuesta (JSON) y pulsa Pegar, o guárdala como archivo .json. La app detecta la consola, valida los datos y actualiza estado y guías (con imágenes).",
                    prompt = prompt,
                    onImportText = onImportText,
                )
            }
            if (hasApiKey) {
                item {
                    OutlinedButton(onClick = onReview, enabled = !reviewing, modifier = Modifier.fillMaxWidth()) {
                        if (reviewing) {
                            CircularProgressIndicator(Modifier.size(18.dp), strokeWidth = 2.dp)
                            Spacer(Modifier.width(10.dp))
                            Text("La IA está revisando esta consola…")
                        } else {
                            Icon(Icons.Outlined.AutoAwesome, null)
                            Spacer(Modifier.width(8.dp))
                            Text("Revisar esta consola con IA ahora")
                        }
                    }
                }
            }
            item {
                val checked = console.lastCheckedAt.ifBlank { console.updatedAt }
                Text(
                    buildString {
                        append("Información actualizada ")
                        append(relativeTime(console.updatedAt).ifBlank { "—" })
                        if (checked.isNotBlank() && checked != console.updatedAt) {
                            append(" · revisada por IA ${relativeTime(checked)}")
                        }
                    },
                    style = MaterialTheme.typography.bodySmall,
                    color = MaterialTheme.colorScheme.onSurfaceVariant,
                )
            }
            item { Disclaimer() }
        }
    }
}

@Composable
private fun Header(console: ConsoleInfo, brand: Brand?) {
    val status = Status.parse(console.summary.status)
    Row(verticalAlignment = Alignment.CenterVertically) {
        ConsoleLogo(console.id, console.formFactor, parseHexColor(brand?.color ?: "#455A64"), size = 84.dp, width = 110.dp)
        Spacer(Modifier.width(16.dp))
        Column(Modifier.weight(1f)) {
            Text(console.name, style = MaterialTheme.typography.headlineSmall)
            Text(
                buildString {
                    append(brand?.company ?: "")
                    if (console.year > 0) append(" · ${console.year}")
                    if (console.latestFirmware.isNotBlank()) append(" · último FW ${console.latestFirmware}")
                },
                style = MaterialTheme.typography.bodySmall,
                color = MaterialTheme.colorScheme.onSurfaceVariant,
            )
            Spacer(Modifier.height(8.dp))
            Row(verticalAlignment = Alignment.CenterVertically) {
                StatusPill(status)
            }
            Spacer(Modifier.height(4.dp))
            Text(console.summary.text, style = MaterialTheme.typography.bodyMedium)
        }
    }
}

@Composable
private fun StepTitle(number: Int, text: String) {
    Row(verticalAlignment = Alignment.CenterVertically, modifier = Modifier.padding(top = 8.dp)) {
        Text(
            "$number",
            modifier = Modifier
                .clip(RoundedCornerShape(50))
                .background(MaterialTheme.colorScheme.primary)
                .padding(horizontal = 9.dp, vertical = 2.dp),
            color = MaterialTheme.colorScheme.onPrimary,
            fontWeight = FontWeight.Bold,
        )
        Spacer(Modifier.width(10.dp))
        Text(text, style = MaterialTheme.typography.titleMedium)
    }
}

@Composable
private fun AllVersionsCard(
    info: StatusInfo,
    heading: String,
    guideFor: (String) -> Guide?,
    onGuide: (String) -> Unit,
) {
    val status = Status.parse(info.status)
    Card(
        shape = RoundedCornerShape(20.dp),
        colors = CardDefaults.cardColors(containerColor = status.color.copy(alpha = 0.14f)),
        border = BorderStroke(1.5.dp, status.color),
    ) {
        Column(Modifier.padding(18.dp)) {
            Text(
                heading,
                style = MaterialTheme.typography.labelLarge,
                color = MaterialTheme.colorScheme.onSurfaceVariant,
            )
            Spacer(Modifier.height(8.dp))
            Row(verticalAlignment = Alignment.CenterVertically) {
                Text(
                    info.title.ifBlank { "Todas las versiones" },
                    style = MaterialTheme.typography.titleLarge,
                    modifier = Modifier.weight(1f),
                )
                StatusPill(status)
            }
            if (info.explanation.isNotBlank()) {
                Spacer(Modifier.height(8.dp))
                Text(info.explanation, style = MaterialTheme.typography.bodyMedium)
            }
            if (info.method.isNotBlank()) {
                Spacer(Modifier.height(10.dp))
                MethodLine(info.method)
            }
            guideFor(info.guideId)?.let { GuideButton(it, onGuide) }
        }
    }
}

@Composable
private fun MatchResult(query: String, match: FirmwareRange?) {
    if (!Versions.isValid(query)) {
        Text("Escribe un número de versión, por ejemplo 9.00 o 17.0.1.", color = MaterialTheme.colorScheme.error)
        return
    }
    val status = Status.parse(match?.status)
    Card(
        modifier = Modifier.fillMaxWidth(),
        shape = RoundedCornerShape(20.dp),
        colors = CardDefaults.cardColors(containerColor = status.color),
    ) {
        Column(Modifier.padding(16.dp)) {
            Text(
                "Tu versión $query",
                color = status.onColor.copy(alpha = 0.85f),
                style = MaterialTheme.typography.labelLarge,
            )
            Text(
                match?.title ?: "No está en la lista",
                color = status.onColor,
                style = MaterialTheme.typography.headlineSmall,
            )
            Text(
                match?.let { "Rango ${it.label}: ${status.label}" }
                    ?: "Puede ser una versión más nueva de la que tenemos registrada o un número mal escrito. Revisa la lista de abajo.",
                color = status.onColor,
                style = MaterialTheme.typography.bodyMedium,
            )
        }
    }
}

@Composable
private fun FirmwareRow(
    range: FirmwareRange,
    expanded: Boolean,
    highlighted: Boolean,
    guide: Guide?,
    onClick: () -> Unit,
    onGuide: (String) -> Unit,
) {
    val status = Status.parse(range.status)
    Card(
        onClick = onClick,
        shape = RoundedCornerShape(16.dp),
        border = if (highlighted) BorderStroke(2.dp, status.color) else null,
        colors = CardDefaults.cardColors(containerColor = MaterialTheme.colorScheme.surfaceContainer),
        modifier = Modifier.animateContentSize(),
    ) {
        Column(Modifier.padding(14.dp)) {
            Row(verticalAlignment = Alignment.CenterVertically) {
                Column(Modifier.weight(1f)) {
                    Text(range.label, style = MaterialTheme.typography.titleMedium)
                    Text(
                        range.title,
                        style = MaterialTheme.typography.bodySmall,
                        color = MaterialTheme.colorScheme.onSurfaceVariant,
                    )
                }
                Spacer(Modifier.width(8.dp))
                StatusPill(status)
                Icon(
                    if (expanded) Icons.Outlined.ExpandLess else Icons.Outlined.ExpandMore,
                    null,
                    tint = MaterialTheme.colorScheme.onSurfaceVariant,
                )
            }
            AnimatedVisibility(expanded) {
                Column {
                    Spacer(Modifier.height(10.dp))
                    HorizontalDivider(color = MaterialTheme.colorScheme.outlineVariant)
                    Spacer(Modifier.height(10.dp))
                    Text(range.explanation, style = MaterialTheme.typography.bodyMedium)
                    if (range.method.isNotBlank() && range.method != "—") {
                        Spacer(Modifier.height(10.dp))
                        MethodLine(range.method)
                    }
                    guide?.let { GuideButton(it, onGuide) }
                }
            }
        }
    }
}

@Composable
private fun MethodLine(method: String) {
    Row(
        Modifier
            .clip(RoundedCornerShape(10.dp))
            .background(MaterialTheme.colorScheme.surfaceContainerHighest)
            .padding(horizontal = 10.dp, vertical = 6.dp),
    ) {
        Text("Método: ", style = MaterialTheme.typography.labelMedium, color = MaterialTheme.colorScheme.onSurfaceVariant)
        Text(method, style = MaterialTheme.typography.labelMedium, fontWeight = FontWeight.Bold)
    }
}

@Composable
private fun SourcesCard(console: ConsoleInfo) {
    val uri = LocalUriHandler.current
    Card(
        shape = RoundedCornerShape(18.dp),
        colors = CardDefaults.cardColors(containerColor = MaterialTheme.colorScheme.surfaceContainer),
    ) {
        Column(Modifier.padding(vertical = 8.dp)) {
            Text(
                "Fuentes",
                style = MaterialTheme.typography.titleMedium,
                modifier = Modifier.padding(horizontal = 16.dp, vertical = 4.dp),
            )
            console.sources.filter { it.url.startsWith("http") }.forEach { source ->
                TextButton(
                    onClick = { runCatching { uri.openUri(source.url) } },
                    modifier = Modifier.fillMaxWidth(),
                    contentPadding = PaddingValues(horizontal = 16.dp),
                ) {
                    Text(source.title.ifBlank { source.url }, modifier = Modifier.weight(1f), color = MaterialTheme.colorScheme.primary)
                    Icon(Icons.AutoMirrored.Outlined.OpenInNew, null, tint = Color.Gray)
                }
            }
        }
    }
}

@Composable
private fun GuideButton(guide: Guide, onGuide: (String) -> Unit) {
    Button(onClick = { onGuide(guide.id) }, modifier = Modifier.fillMaxWidth().padding(top = 12.dp)) {
        Icon(Icons.AutoMirrored.Outlined.MenuBook, null)
        Spacer(Modifier.width(8.dp))
        Text("Ver guía: cómo hacerlo")
    }
}
