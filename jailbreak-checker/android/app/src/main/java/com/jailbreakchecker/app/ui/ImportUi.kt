package com.jailbreakchecker.app.ui

import android.content.ClipData
import android.content.ClipboardManager
import android.content.Context
import android.widget.Toast
import androidx.activity.compose.rememberLauncherForActivityResult
import androidx.activity.result.contract.ActivityResultContracts
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.heightIn
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.width
import androidx.compose.foundation.rememberScrollState
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.foundation.verticalScroll
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.outlined.ContentCopy
import androidx.compose.material.icons.outlined.ContentPaste
import androidx.compose.material.icons.outlined.FileOpen
import androidx.compose.material3.AlertDialog
import androidx.compose.material3.Button
import androidx.compose.material3.Card
import androidx.compose.material3.CardDefaults
import androidx.compose.material3.Icon
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.OutlinedButton
import androidx.compose.material3.Text
import androidx.compose.material3.TextButton
import androidx.compose.runtime.Composable
import androidx.compose.ui.Modifier
import androidx.compose.ui.platform.LocalContext
import androidx.compose.ui.unit.dp
import com.jailbreakchecker.app.data.Importer
import com.jailbreakchecker.app.data.Status

/** What the import dialog shows. */
sealed interface ImportState {
    data object Idle : ImportState
    data class Ready(val preview: Importer.Preview) : ImportState
    data class Failed(val message: String) : ImportState
    data class Done(val message: String) : ImportState
}

fun copyToClipboard(context: Context, label: String, text: String) {
    context.getSystemService(ClipboardManager::class.java)?.setPrimaryClip(ClipData.newPlainText(label, text))
    Toast.makeText(context, "Prompt copiado. Pégalo en ChatGPT, Gemini o Claude.", Toast.LENGTH_LONG).show()
}

fun readClipboard(context: Context): String =
    context.getSystemService(ClipboardManager::class.java)?.primaryClip
        ?.takeIf { it.itemCount > 0 }
        ?.getItemAt(0)?.coerceToText(context)?.toString().orEmpty()

/**
 * "Actualizar con otra IA (gratis)": copy the prompt, then import the JSON answer from a file
 * or from the clipboard.
 */
@Composable
fun ImportCard(
    title: String,
    description: String,
    prompt: () -> String,
    onImportText: (String) -> Unit,
) {
    val context = LocalContext.current
    val picker = rememberLauncherForActivityResult(ActivityResultContracts.OpenDocument()) { uri ->
        if (uri != null) {
            val text = runCatching {
                context.contentResolver.openInputStream(uri)?.use { it.readBytes().toString(Charsets.UTF_8) }
            }.getOrNull()
            onImportText(text.orEmpty())
        }
    }
    Card(
        shape = RoundedCornerShape(18.dp),
        colors = CardDefaults.cardColors(containerColor = MaterialTheme.colorScheme.surfaceContainerHigh),
    ) {
        Column(Modifier.padding(16.dp), verticalArrangement = Arrangement.spacedBy(8.dp)) {
            Text(title, style = MaterialTheme.typography.titleMedium)
            Text(description, style = MaterialTheme.typography.bodySmall, color = MaterialTheme.colorScheme.onSurfaceVariant)
            Button(onClick = { copyToClipboard(context, "Prompt Revisor de Jailbreak", prompt()) }, modifier = Modifier.fillMaxWidth()) {
                Icon(Icons.Outlined.ContentCopy, null)
                Spacer(Modifier.width(8.dp))
                Text("1. Copiar prompt para la IA")
            }
            Row(horizontalArrangement = Arrangement.spacedBy(8.dp)) {
                OutlinedButton(onClick = { onImportText(readClipboard(context)) }, modifier = Modifier.weight(1f)) {
                    Icon(Icons.Outlined.ContentPaste, null)
                    Spacer(Modifier.width(6.dp))
                    Text("2. Pegar")
                }
                OutlinedButton(
                    onClick = { picker.launch(arrayOf("application/json", "text/*", "application/octet-stream")) },
                    modifier = Modifier.weight(1f),
                ) {
                    Icon(Icons.Outlined.FileOpen, null)
                    Spacer(Modifier.width(6.dp))
                    Text("2. Archivo .json")
                }
            }
        }
    }
}

@Composable
fun ImportDialog(state: ImportState, onConfirm: () -> Unit, onDismiss: () -> Unit) {
    when (state) {
        ImportState.Idle -> Unit
        is ImportState.Failed -> AlertDialog(
            onDismissRequest = onDismiss,
            confirmButton = { TextButton(onClick = onDismiss) { Text("Entendido") } },
            title = { Text("No se pudo importar") },
            text = { Text(state.message) },
        )
        is ImportState.Done -> AlertDialog(
            onDismissRequest = onDismiss,
            confirmButton = { TextButton(onClick = onDismiss) { Text("Listo") } },
            title = { Text("Importado") },
            text = { Text(state.message) },
        )
        is ImportState.Ready -> {
            val p = state.preview
            AlertDialog(
                onDismissRequest = onDismiss,
                confirmButton = { Button(onClick = onConfirm) { Text("Importar") } },
                dismissButton = { TextButton(onClick = onDismiss) { Text("Cancelar") } },
                title = { Text("Revisa antes de importar") },
                text = {
                    Column(
                        Modifier
                            .heightIn(max = 420.dp)
                            .verticalScroll(rememberScrollState()),
                        verticalArrangement = Arrangement.spacedBy(8.dp),
                    ) {
                        Text(
                            p.console?.let { "Consola detectada: ${it.name} (por ${p.detectedBy})" } ?: "Consola: no reconocida",
                            style = MaterialTheme.typography.titleSmall,
                        )
                        p.consoleUpdate?.let { c ->
                            Row {
                                StatusPill(Status.parse(c.summary.status))
                                Spacer(Modifier.width(8.dp))
                                Text(c.summary.text, style = MaterialTheme.typography.bodyMedium)
                            }
                            Text(
                                "Se actualizará el estado: ${c.firmwares.size} rangos de firmware" +
                                    (if (c.latestFirmware.isNotBlank()) ", último firmware ${c.latestFirmware}" else "") + ".",
                                style = MaterialTheme.typography.bodySmall,
                            )
                        }
                        if (p.guides.isNotEmpty()) {
                            Text("Guías (${p.guides.size}):", style = MaterialTheme.typography.titleSmall)
                            p.guides.forEach { g ->
                                val images = g.steps.count { it.image.isNotBlank() }
                                Text(
                                    "• ${g.title} — ${g.steps.size} pasos" + if (images > 0) ", $images imágenes" else "",
                                    style = MaterialTheme.typography.bodySmall,
                                )
                            }
                        }
                        if (p.warnings.isNotEmpty()) {
                            Spacer(Modifier.height(4.dp))
                            Text("Avisos:", style = MaterialTheme.typography.titleSmall, color = Status.ORANGE.color)
                            p.warnings.forEach { Text("• $it", style = MaterialTheme.typography.bodySmall) }
                        }
                    }
                },
            )
        }
    }
}
