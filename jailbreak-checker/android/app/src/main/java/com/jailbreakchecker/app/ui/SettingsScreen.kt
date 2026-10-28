package com.jailbreakchecker.app.ui

import android.Manifest
import android.os.Build
import androidx.activity.compose.rememberLauncherForActivityResult
import androidx.activity.result.contract.ActivityResultContracts
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.ExperimentalLayoutApi
import androidx.compose.foundation.layout.FlowRow
import androidx.compose.foundation.layout.PaddingValues
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.width
import androidx.compose.foundation.lazy.LazyColumn
import androidx.compose.foundation.selection.selectable
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.foundation.text.KeyboardOptions
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.automirrored.outlined.ArrowBack
import androidx.compose.material.icons.outlined.Visibility
import androidx.compose.material.icons.outlined.VisibilityOff
import androidx.compose.material3.Button
import androidx.compose.material3.Card
import androidx.compose.material3.CardDefaults
import androidx.compose.material3.CircularProgressIndicator
import androidx.compose.material3.ExperimentalMaterial3Api
import androidx.compose.material3.FilterChip
import androidx.compose.material3.Icon
import androidx.compose.material3.IconButton
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.OutlinedButton
import androidx.compose.material3.OutlinedTextField
import androidx.compose.material3.RadioButton
import androidx.compose.material3.Scaffold
import androidx.compose.material3.Switch
import androidx.compose.material3.Text
import androidx.compose.material3.TextButton
import androidx.compose.material3.TopAppBar
import androidx.compose.runtime.Composable
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.rememberCoroutineScope
import androidx.compose.runtime.saveable.rememberSaveable
import androidx.compose.runtime.setValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.platform.LocalUriHandler
import androidx.compose.ui.text.input.KeyboardType
import androidx.compose.ui.text.input.PasswordVisualTransformation
import androidx.compose.ui.text.input.VisualTransformation
import androidx.compose.ui.unit.dp
import com.jailbreakchecker.app.data.AppSettings
import com.jailbreakchecker.app.data.Status
import com.jailbreakchecker.app.data.UpdaterMeta
import kotlinx.coroutines.launch
import java.util.Locale

/** Rough cost of one AI check in US$ (≈30k input + 3k output tokens + 5 web searches). */
private fun costPerCheck(model: String): Double {
    val (input, output) = when (model) {
        "claude-sonnet-5-5" -> 2.0 to 10.0
        "claude-haiku-5-5" -> 0.10 to 0.50
        else -> 4.0 to 20.0
    }
    return 30_000 * input / 1e6 + 3_000 * output / 1e6 + 5 * 0.01
}

private fun money(v: Double) = "%.2f US$".format(Locale.US, v)

@OptIn(ExperimentalMaterial3Api::class, ExperimentalLayoutApi::class)
@Composable
fun SettingsScreen(
    settings: AppSettings,
    meta: UpdaterMeta,
    running: Boolean,
    onSave: (AppSettings) -> Unit,
    onVerify: suspend (String, String) -> String?,
    onRunNow: () -> Unit,
    onClearAi: () -> Unit,
    onBack: () -> Unit,
) {
    var key by rememberSaveable { mutableStateOf(settings.apiKey) }
    var showKey by remember { mutableStateOf(false) }
    var checking by remember { mutableStateOf(false) }
    var keyResult by remember { mutableStateOf<String?>(null) }
    var keyOk by remember { mutableStateOf(false) }
    val scope = rememberCoroutineScope()
    val uri = LocalUriHandler.current
    val notificationPermission = rememberLauncherForActivityResult(ActivityResultContracts.RequestPermission()) { granted ->
        onSave(settings.copy(notifications = granted))
    }

    Scaffold(
        topBar = {
            TopAppBar(
                title = { Text("Ajustes de la IA") },
                navigationIcon = { IconButton(onClick = onBack) { Icon(Icons.AutoMirrored.Outlined.ArrowBack, "Atrás") } },
            )
        },
    ) { padding ->
        LazyColumn(
            Modifier
                .padding(padding)
                .fillMaxSize(),
            contentPadding = PaddingValues(16.dp),
            verticalArrangement = Arrangement.spacedBy(14.dp),
        ) {
            item {
                Text(
                    "Las noticias, emuladores y herramientas nuevas se leen GRATIS de las webs de la escena y de GitHub, sin API key. " +
                        "La IA (Claude, de Anthropic) es opcional: actualiza el estado de cada consola y las guías. Necesita tu propia API key con saldo " +
                        "(se recarga desde 5 US$ en console.anthropic.com → Billing). Se guarda cifrada en este teléfono y solo se envía a api.anthropic.com.",
                    style = MaterialTheme.typography.bodyMedium,
                )
                TextButton(onClick = { runCatching { uri.openUri("https://console.anthropic.com/settings/keys") } }, contentPadding = PaddingValues(0.dp)) {
                    Text("Conseguir una API key en console.anthropic.com")
                }
            }

            item {
                SettingsCard("1. API key") {
                    OutlinedTextField(
                        value = key,
                        onValueChange = {
                            key = it.trim()
                            keyResult = null
                        },
                        modifier = Modifier.fillMaxWidth(),
                        singleLine = true,
                        placeholder = { Text("sk-ant-…") },
                        visualTransformation = if (showKey) VisualTransformation.None else PasswordVisualTransformation(),
                        keyboardOptions = KeyboardOptions(keyboardType = KeyboardType.Password),
                        trailingIcon = {
                            IconButton(onClick = { showKey = !showKey }) {
                                Icon(if (showKey) Icons.Outlined.VisibilityOff else Icons.Outlined.Visibility, "Mostrar")
                            }
                        },
                        shape = RoundedCornerShape(14.dp),
                    )
                    Spacer(Modifier.height(10.dp))
                    Row(verticalAlignment = Alignment.CenterVertically) {
                        Button(
                            enabled = key.isNotBlank() && !checking,
                            onClick = {
                                checking = true
                                scope.launch {
                                    val error = onVerify(key, settings.model)
                                    checking = false
                                    keyOk = error == null
                                    keyResult = error ?: "Funciona. Guardada."
                                    if (error == null) {
                                        onSave(settings.copy(apiKey = key))
                                        if (Build.VERSION.SDK_INT >= 33 && settings.notifications) {
                                            notificationPermission.launch(Manifest.permission.POST_NOTIFICATIONS)
                                        }
                                    }
                                }
                            },
                        ) { Text("Guardar y comprobar") }
                        if (checking) {
                            Spacer(Modifier.width(12.dp))
                            CircularProgressIndicator(Modifier.height(24.dp).width(24.dp), strokeWidth = 2.dp)
                        }
                        if (settings.hasApiKey) {
                            Spacer(Modifier.weight(1f))
                            TextButton(onClick = {
                                key = ""
                                keyResult = null
                                onSave(settings.copy(apiKey = ""))
                            }) { Text("Borrar") }
                        }
                    }
                    keyResult?.let {
                        Text(it, color = if (keyOk) Status.GREEN.color else MaterialTheme.colorScheme.error, modifier = Modifier.padding(top = 6.dp))
                    }
                }
            }

            item {
                SettingsCard("2. Modelo de IA") {
                    AppSettings.MODELS.forEach { (id, label) ->
                        Row(
                            Modifier
                                .fillMaxWidth()
                                .selectable(selected = settings.model == id, onClick = { onSave(settings.copy(model = id)) })
                                .padding(vertical = 2.dp),
                            verticalAlignment = Alignment.CenterVertically,
                        ) {
                            RadioButton(selected = settings.model == id, onClick = { onSave(settings.copy(model = id)) })
                            Column {
                                Text(label, style = MaterialTheme.typography.bodyMedium)
                                Text(
                                    "~${money(costPerCheck(id))} por búsqueda",
                                    style = MaterialTheme.typography.bodySmall,
                                    color = MaterialTheme.colorScheme.onSurfaceVariant,
                                )
                            }
                        }
                    }
                }
            }

            item {
                SettingsCard("3. Revisión automática") {
                    FlowRow(horizontalArrangement = Arrangement.spacedBy(8.dp)) {
                        AppSettings.INTERVALS.forEach { h ->
                            FilterChip(
                                selected = settings.intervalHours == h,
                                onClick = { onSave(settings.copy(intervalHours = h)) },
                                label = { Text(if (h == 0) "Apagada" else if (h == 1) "Cada hora" else "Cada $h h") },
                            )
                        }
                    }
                    val perDay = if (settings.intervalHours > 0) 24.0 / settings.intervalHours else 0.0
                    val daily = perDay * costPerCheck(settings.model) + if (perDay > 0) 8 * costPerCheck(settings.model) * 1.3 else 0.0
                    Text(
                        when {
                            perDay == 0.0 -> "Sin revisión automática: solo busca cuando pulses \"Buscar novedades ahora\"."
                            !settings.hasApiKey -> "Sin API key solo se leen las fuentes gratuitas: no cuesta nada."
                            else -> "Fuentes gratuitas: 0 US$. IA: ~${money(costPerCheck(settings.model))} por búsqueda, unos ${money(daily)} al día con esta frecuencia (estimación; revisa el consumo real en console.anthropic.com)."
                        },
                        style = MaterialTheme.typography.bodySmall,
                        color = MaterialTheme.colorScheme.onSurfaceVariant,
                        modifier = Modifier.padding(top = 8.dp),
                    )
                    Row(verticalAlignment = Alignment.CenterVertically, modifier = Modifier.padding(top = 8.dp)) {
                        Text("Avisarme con una notificación", modifier = Modifier.weight(1f))
                        Switch(
                            checked = settings.notifications,
                            onCheckedChange = { on ->
                                if (on && Build.VERSION.SDK_INT >= 33) {
                                    notificationPermission.launch(Manifest.permission.POST_NOTIFICATIONS)
                                } else {
                                    onSave(settings.copy(notifications = on))
                                }
                            },
                        )
                    }
                }
            }

            item {
                SettingsCard("Estado") {
                    val feeds = meta.feedsCheckedAt.takeIf { it.isNotBlank() }
                    Text(
                        when {
                            running -> "Buscando novedades ahora…"
                            feeds == null -> "Fuentes gratuitas: todavía no se han leído."
                            meta.feedsFailed.isEmpty() -> "Fuentes gratuitas: leídas ${relativeTime(feeds)}."
                            else -> "Fuentes gratuitas: leídas ${relativeTime(feeds)} (sin respuesta: ${meta.feedsFailed.take(4).joinToString()})."
                        },
                    )
                    val last = meta.lastRunAt.takeIf { it.isNotBlank() && settings.hasApiKey }
                    if (last != null && !running) {
                        Text(
                            if (meta.ok) "IA: última búsqueda ${relativeTime(last)}, correcta." else "IA: ${meta.message}",
                            color = if (meta.ok) MaterialTheme.colorScheme.onSurface else MaterialTheme.colorScheme.error,
                            modifier = Modifier.padding(top = 4.dp),
                        )
                    }
                    if (meta.lastInputTokens > 0) {
                        Text(
                            "Uso de la última búsqueda: ${meta.lastInputTokens} tokens de entrada, ${meta.lastOutputTokens} de salida, ${meta.lastSearches} búsquedas web.",
                            style = MaterialTheme.typography.bodySmall,
                            color = MaterialTheme.colorScheme.onSurfaceVariant,
                            modifier = Modifier.padding(top = 4.dp),
                        )
                    }
                    Spacer(Modifier.height(10.dp))
                    Button(onClick = onRunNow, enabled = !running, modifier = Modifier.fillMaxWidth()) {
                        Text(if (running) "Buscando…" else "Buscar novedades ahora")
                    }
                    OutlinedButton(onClick = onClearAi, modifier = Modifier.fillMaxWidth().padding(top = 6.dp)) {
                        Text("Borrar lo aprendido por la IA")
                    }
                }
            }
        }
    }
}

@Composable
private fun SettingsCard(title: String, content: @Composable () -> Unit) {
    Card(
        shape = RoundedCornerShape(18.dp),
        colors = CardDefaults.cardColors(containerColor = MaterialTheme.colorScheme.surfaceContainer),
    ) {
        Column(Modifier.padding(16.dp)) {
            Text(title, style = MaterialTheme.typography.titleMedium)
            Spacer(Modifier.height(10.dp))
            content()
        }
    }
}
