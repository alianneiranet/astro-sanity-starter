package com.jailbreakchecker.app.data

import java.time.LocalDate

/**
 * Prompt the user pastes into any AI chat with web search (ChatGPT, Gemini, Claude, Copilot…).
 * The answer is a JSON in the [Importer.FORMAT] format that the app imports.
 */
object ImportPrompt {

    fun build(console: ConsoleInfo?, guides: List<Guide>, allConsoles: List<ConsoleInfo>, today: LocalDate = LocalDate.now()): String {
        val target = if (console != null) {
            "la consola **${console.name}** (consoleId \"${console.id}\")"
        } else {
            "la consola que te indique a continuación: ______________________ (escribe aquí el nombre de la consola)"
        }
        val consoleList = allConsoles.joinToString(", ") { "${it.id} = ${it.name}" }
        val current = console?.let {
            val variants = it.variants.joinToString("\n") { v -> "- ${v.id}: ${v.name} — ${v.description}" }.ifBlank { "- (sin modelos distintos)" }
            val currentJson = Repository.json.encodeToString(
                ConsoleInfo.serializer(),
                it.copy(lastCheckedAt = "", sources = it.sources.take(4)),
            )
            val related = guides.filter { g -> it.id in g.consoleIds }
            val guidesJson = related.joinToString(",\n") { g -> Repository.json.encodeToString(Guide.serializer(), g.copy(updatedAt = "", origin = "")) }
            """
## Datos actuales en la app (actualízalos)
Modelos (variantes) de esta consola, usa estos ids exactos:
$variants

Estado actual:
```json
$currentJson
```

Guías actuales de esta consola (si una sigue sirviendo, mantén su "id" y mejórala):
```json
[$guidesJson]
```
"""
        }.orEmpty()

        return """# Tarea: investigar el jailbreak / homebrew más moderno de $target y devolver un JSON

Hoy es ${today}. Eres un investigador experto en la escena del homebrew de consolas. Necesito el método MÁS ACTUAL y los pasos EXACTOS para que una persona sin conocimientos técnicos pueda hacer jailbreak o cargar homebrew en su propia consola, para una app llamada "Revisor de Jailbreak".

## Cómo investigar
1. Busca en internet AHORA (no uses solo tu memoria). Prioriza noticias de los últimos días y semanas.
2. Fuentes fiables, en este orden: repositorios oficiales de GitHub de cada herramienta (página de Releases), las guías canónicas (switch.hacks.guide, 3ds.hacks.guide, wiiu.hacks.guide, wii.hacks.guide, vita.hacks.guide, dsi.cfw.guide, ConsoleMods wiki), GBAtemp, Wololo, PSX-Place, los subreddits de la escena y prensa conocida.
3. Averigua: último firmware oficial, qué exploits existen y para qué rangos de firmware y modelos exactos, las versiones actuales de cada herramienta, y si algo está en desarrollo o no es público.
4. Escribe los pasos con rutas de menú reales, nombres de archivos y carpetas, versiones y enlaces de descarga oficiales. Incluye copias de seguridad (NAND, EEPROM) y riesgos de baneo.
5. NO inventes versiones, archivos ni enlaces. Si algo no se puede confirmar, dilo en la explicación y márcalo como "yellow". Nada de piratería: no enlaces ROMs/ISOs, sigpatches ni webs que venden "jailbreaks".

## Colores ("status")
- "green": hay un método público que funciona (aunque sea temporal y haya que repetirlo al reiniciar).
- "orange": se puede, pero con condiciones fuertes (abrir la consola y soldar un modchip, solo ciertos modelos, hardware raro). Si un modelo SOLO se puede con modchip, es "orange" para cualquier firmware.
- "yellow": en desarrollo, incompleto, no publicado o pendiente de que las herramientas soporten el firmware nuevo. Explica por qué.
- "red": no existe ningún método.

## Imágenes (opcional)
Cada paso puede llevar "image": un enlace https DIRECTO a una imagen (que termine en .png/.jpg/.webp y provenga de la guía oficial o del repositorio del proyecto) o una imagen incrustada como "data:image/png;base64,....". Si no tienes una imagen real y útil, deja "image": "". Nunca inventes enlaces de imágenes.

## Reglas del JSON
- Responde SOLO con el JSON, sin texto antes ni después (puede ir dentro de un bloque ```json).
- Textos en español neutro, frases cortas y claras.
- "consoleId" debe ser uno de estos: $consoleList. Si no estás seguro, pon el nombre en "consoleName".
- "firmwares": rangos de versión ordenados del más antiguo al más nuevo, sin solaparse, con "min"/"max" inclusivos (vacío = sin límite). Usa el formato real de versión de la consola.
- En "variants" usa solo los ids de modelo existentes. Pon "allVersions" en un modelo cuando el firmware no importa para ese modelo; si no, pon "allVersions": null y cubre ese modelo con rangos en "firmwares".
- Usa "mode": "all" solo si todas las versiones de todos los modelos tienen el mismo estado (entonces "firmwares": []).
- "guideId" de cada rango debe ser el "id" de una de las guías que devuelves (o de las actuales), o "".
- Cada guía: 4 a 14 pasos completos, "difficulty" = "facil" | "media" | "dificil", y enlaces solo https oficiales.
$current
## Formato exacto de la respuesta
```json
{
  "format": "${Importer.FORMAT}",
  "consoleId": "${console?.id ?: "id-de-la-consola"}",
  "consoleName": "${console?.name ?: "Nombre de la consola"}",
  "checkedAt": "$today",
  "console": {
    "summary": { "status": "green", "text": "Resumen de máx. 60 caracteres, p. ej. Jailbreak hasta la 13.60" },
    "latestFirmware": "último firmware oficial",
    "mode": "firmware",
    "allVersions": null,
    "variants": [
      { "id": "id-de-modelo-existente", "allVersions": { "status": "orange", "title": "Solo con modchip", "explanation": "…", "method": "…", "guideId": "id-guia" } }
    ],
    "firmwares": [
      { "id": "rango-1", "label": "1.00 – 9.00", "variant": "", "min": "1.00", "max": "9.00", "status": "green", "title": "Jailbreak disponible", "explanation": "2-4 frases claras", "method": "Herramientas", "guideId": "id-guia" }
    ],
    "sources": [ { "title": "Fuente oficial", "url": "https://…" } ]
  },
  "guides": [
    {
      "id": "id-guia",
      "title": "Título de la guía",
      "consoleIds": ["${console?.id ?: "id-de-la-consola"}"],
      "difficulty": "media",
      "time": "30 min",
      "summary": "Qué hace el método y sus límites",
      "requirements": ["Lo que hace falta: hardware, cables, PC, tamaño y formato de la SD, rango de firmware…"],
      "steps": [
        { "title": "Paso corto", "text": "Instrucciones exactas con menús, archivos y carpetas", "image": "", "imageCaption": "" }
      ],
      "warnings": ["Riesgos: baneos, bricks, actualizaciones que evitar, estafas"],
      "links": [ { "title": "Descarga oficial / guía completa", "url": "https://…" } ]
    }
  ]
}
```
"""
    }
}
