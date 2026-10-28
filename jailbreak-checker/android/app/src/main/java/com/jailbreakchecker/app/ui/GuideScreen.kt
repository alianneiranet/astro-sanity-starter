package com.jailbreakchecker.app.ui

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
import androidx.compose.foundation.lazy.itemsIndexed
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.automirrored.outlined.ArrowBack
import androidx.compose.material.icons.automirrored.outlined.OpenInNew
import androidx.compose.material.icons.outlined.Refresh
import androidx.compose.material.icons.outlined.Schedule
import androidx.compose.material.icons.outlined.WarningAmber
import androidx.compose.material3.Card
import androidx.compose.material3.CircularProgressIndicator
import androidx.compose.material3.OutlinedButton
import androidx.compose.material3.CardDefaults
import androidx.compose.material3.ExperimentalMaterial3Api
import androidx.compose.material3.Icon
import androidx.compose.material3.IconButton
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.Scaffold
import androidx.compose.material3.Text
import androidx.compose.material3.TextButton
import androidx.compose.material3.TopAppBar
import androidx.compose.runtime.Composable
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clip
import androidx.compose.ui.platform.LocalUriHandler
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.unit.dp
import com.jailbreakchecker.app.data.Guide
import com.jailbreakchecker.app.data.Status

private val Guide.difficultyStatus: Status
    get() = when (difficulty) {
        "facil" -> Status.GREEN
        "dificil" -> Status.RED
        else -> Status.ORANGE
    }

private val Guide.difficultyLabel: String
    get() = when (difficulty) {
        "facil" -> "Fácil"
        "dificil" -> "Difícil"
        else -> "Media"
    }

@OptIn(ExperimentalMaterial3Api::class)
@Composable
fun GuideScreen(
    guide: Guide?,
    hasApiKey: Boolean,
    updating: Boolean,
    onUpdate: () -> Unit,
    onSettings: () -> Unit,
    onBack: () -> Unit,
) {
    Scaffold(
        topBar = {
            TopAppBar(
                title = { Text("Guía paso a paso") },
                navigationIcon = {
                    IconButton(onClick = onBack) { Icon(Icons.AutoMirrored.Outlined.ArrowBack, "Atrás") }
                },
            )
        },
    ) { padding ->
        if (guide == null) {
            Text("Guía no encontrada", Modifier.padding(padding).padding(24.dp))
            return@Scaffold
        }
        val uri = LocalUriHandler.current
        LazyColumn(
            Modifier
                .padding(padding)
                .fillMaxSize(),
            contentPadding = PaddingValues(16.dp),
            verticalArrangement = Arrangement.spacedBy(12.dp),
        ) {
            item {
                Text(guide.title, style = MaterialTheme.typography.headlineSmall)
                Spacer(Modifier.height(8.dp))
                Row(verticalAlignment = Alignment.CenterVertically, horizontalArrangement = Arrangement.spacedBy(8.dp)) {
                    StatusPill(guide.difficultyStatus, text = "Dificultad: ${guide.difficultyLabel}")
                    if (guide.time.isNotBlank()) {
                        Icon(Icons.Outlined.Schedule, null, tint = MaterialTheme.colorScheme.onSurfaceVariant)
                        Text(guide.time, style = MaterialTheme.typography.bodySmall, color = MaterialTheme.colorScheme.onSurfaceVariant)
                    }
                }
                Spacer(Modifier.height(10.dp))
                Text(guide.summary, style = MaterialTheme.typography.bodyLarge)
                Spacer(Modifier.height(10.dp))
                Text(
                    if (guide.updatedAt.isNotBlank()) "Revisada por IA ${relativeTime(guide.updatedAt)} en las fuentes oficiales." else "Versión incluida en la app (octubre de 2026).",
                    style = MaterialTheme.typography.bodySmall,
                    color = MaterialTheme.colorScheme.onSurfaceVariant,
                )
                OutlinedButton(
                    onClick = if (hasApiKey) onUpdate else onSettings,
                    enabled = !updating,
                    modifier = Modifier.fillMaxWidth().padding(top = 6.dp),
                ) {
                    if (updating) {
                        CircularProgressIndicator(Modifier.size(18.dp), strokeWidth = 2.dp)
                        Spacer(Modifier.width(10.dp))
                        Text("Actualizando con IA… (unos minutos)")
                    } else {
                        Icon(Icons.Outlined.Refresh, null)
                        Spacer(Modifier.width(8.dp))
                        Text(if (hasApiKey) "Actualizar esta guía con IA" else "Configura la IA para actualizar")
                    }
                }
            }
            if (guide.requirements.isNotEmpty()) {
                item {
                    SectionCard("Qué necesitas") {
                        guide.requirements.forEach { req ->
                            Row(Modifier.padding(vertical = 3.dp)) {
                                Text("•  ", fontWeight = FontWeight.Bold)
                                Text(req, style = MaterialTheme.typography.bodyMedium)
                            }
                        }
                    }
                }
            }
            item { Text("Pasos", style = MaterialTheme.typography.titleMedium, modifier = Modifier.padding(top = 4.dp)) }
            itemsIndexed(guide.steps) { index, step ->
                Card(
                    shape = RoundedCornerShape(16.dp),
                    colors = CardDefaults.cardColors(containerColor = MaterialTheme.colorScheme.surfaceContainer),
                ) {
                    Row(Modifier.padding(14.dp)) {
                        Text(
                            "${index + 1}",
                            modifier = Modifier
                                .clip(RoundedCornerShape(50))
                                .background(MaterialTheme.colorScheme.primary)
                                .padding(horizontal = 9.dp, vertical = 2.dp),
                            color = MaterialTheme.colorScheme.onPrimary,
                            fontWeight = FontWeight.Bold,
                        )
                        Spacer(Modifier.width(12.dp))
                        Column {
                            Text(step.title, style = MaterialTheme.typography.titleMedium)
                            Spacer(Modifier.height(4.dp))
                            Text(step.text, style = MaterialTheme.typography.bodyMedium)
                        }
                    }
                }
            }
            if (guide.warnings.isNotEmpty()) {
                item {
                    Card(
                        shape = RoundedCornerShape(16.dp),
                        colors = CardDefaults.cardColors(containerColor = Status.ORANGE.color.copy(alpha = 0.15f)),
                    ) {
                        Column(Modifier.padding(14.dp)) {
                            Row(verticalAlignment = Alignment.CenterVertically) {
                                Icon(Icons.Outlined.WarningAmber, null, tint = Status.ORANGE.color)
                                Spacer(Modifier.width(8.dp))
                                Text("Importante", style = MaterialTheme.typography.titleMedium)
                            }
                            guide.warnings.forEach {
                                Text("•  $it", style = MaterialTheme.typography.bodyMedium, modifier = Modifier.padding(top = 6.dp))
                            }
                        }
                    }
                }
            }
            if (guide.links.isNotEmpty()) {
                item {
                    SectionCard("Guías completas y descargas oficiales") {
                        guide.links.forEach { link ->
                            TextButton(
                                onClick = { runCatching { uri.openUri(link.url) } },
                                contentPadding = PaddingValues(horizontal = 0.dp),
                                modifier = Modifier.fillMaxWidth(),
                            ) {
                                Text(link.title, modifier = Modifier.weight(1f), color = MaterialTheme.colorScheme.primary)
                                Icon(Icons.AutoMirrored.Outlined.OpenInNew, null, tint = MaterialTheme.colorScheme.onSurfaceVariant)
                            }
                        }
                    }
                }
            }
            item {
                Text(
                    "Esta guía es un resumen. Las herramientas cambian a menudo: descárgalas solo de sus páginas oficiales y sigue su documentación.",
                    style = MaterialTheme.typography.bodySmall,
                    color = MaterialTheme.colorScheme.onSurfaceVariant,
                )
            }
            item { Disclaimer() }
        }
    }
}

@Composable
private fun SectionCard(title: String, content: @Composable () -> Unit) {
    Card(
        shape = RoundedCornerShape(16.dp),
        colors = CardDefaults.cardColors(containerColor = MaterialTheme.colorScheme.surfaceContainer),
    ) {
        Column(Modifier.padding(14.dp)) {
            Text(title, style = MaterialTheme.typography.titleMedium)
            Spacer(Modifier.height(8.dp))
            content()
        }
    }
}
