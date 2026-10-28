package com.jailbreakchecker.app.ui

import androidx.compose.foundation.background
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.shape.CircleShape
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clip
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.unit.dp
import com.jailbreakchecker.app.data.Status
import java.time.Duration
import java.time.Instant

val Status.color: Color
    get() = when (this) {
        Status.GREEN -> Color(0xFF2E9E46)
        Status.ORANGE -> Color(0xFFF57C00)
        Status.YELLOW -> Color(0xFFF2B705)
        Status.RED -> Color(0xFFD93636)
        Status.UNKNOWN -> Color(0xFF7A8594)
    }

/** Text drawn on top of [color]. */
val Status.onColor: Color
    get() = if (this == Status.YELLOW) Color(0xFF2B2200) else Color.White

val Status.label: String
    get() = when (this) {
        Status.GREEN -> "Jailbreak"
        Status.ORANGE -> "Con limitaciones"
        Status.YELLOW -> "En desarrollo"
        Status.RED -> "Sin jailbreak"
        Status.UNKNOWN -> "Desconocido"
    }

val Status.meaning: String
    get() = when (this) {
        Status.GREEN -> "Se puede hacer jailbreak o cargar homebrew sin complicaciones."
        Status.ORANGE -> "Se puede, pero con condiciones: abrir la consola, soldar un chip o solo ciertos modelos."
        Status.YELLOW -> "Hay avances, pero el exploit está en desarrollo, incompleto o no es público."
        Status.RED -> "No existe ningún método conocido para esta versión."
        Status.UNKNOWN -> "Todavía no tenemos datos."
    }

@Composable
fun StatusDot(status: Status, modifier: Modifier = Modifier, size: Int = 12) {
    Box(
        modifier
            .size(size.dp)
            .clip(CircleShape)
            .background(status.color),
    )
}

@Composable
fun StatusPill(status: Status, modifier: Modifier = Modifier, text: String = status.label) {
    Row(
        modifier
            .clip(RoundedCornerShape(50))
            .background(status.color)
            .padding(horizontal = 10.dp, vertical = 4.dp),
        verticalAlignment = Alignment.CenterVertically,
    ) {
        Text(
            text,
            color = status.onColor,
            style = MaterialTheme.typography.labelMedium,
            fontWeight = FontWeight.Bold,
            maxLines = 1,
        )
    }
}

@Composable
fun StatusLegend(modifier: Modifier = Modifier) {
    Column(modifier, verticalArrangement = Arrangement.spacedBy(10.dp)) {
        listOf(Status.GREEN, Status.ORANGE, Status.YELLOW, Status.RED).forEach { status ->
            Row(verticalAlignment = Alignment.Top, horizontalArrangement = Arrangement.spacedBy(10.dp)) {
                StatusDot(status, Modifier.padding(top = 4.dp))
                Column {
                    Text(status.label, style = MaterialTheme.typography.labelLarge)
                    Text(
                        status.meaning,
                        style = MaterialTheme.typography.bodySmall,
                        color = MaterialTheme.colorScheme.onSurfaceVariant,
                    )
                }
            }
        }
    }
}

fun parseHexColor(hex: String, fallback: Color = Color(0xFF455A64)): Color =
    runCatching { Color(android.graphics.Color.parseColor(hex)) }.getOrDefault(fallback)

/** "hace 5 min", "hace 3 h", "hace 2 días" — or empty if [iso] can't be parsed. */
fun relativeTime(iso: String, now: Instant = Instant.now()): String {
    val then = runCatching { Instant.parse(iso) }.getOrNull() ?: return ""
    val d = Duration.between(then, now)
    return when {
        d.isNegative || d.toMinutes() < 1 -> "hace un momento"
        d.toMinutes() < 60 -> "hace ${d.toMinutes()} min"
        d.toHours() < 24 -> "hace ${d.toHours()} h"
        d.toDays() == 1L -> "hace 1 día"
        else -> "hace ${d.toDays()} días"
    }
}
