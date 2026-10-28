package com.jailbreakchecker.app.ui

import androidx.compose.foundation.Canvas
import androidx.compose.foundation.background
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.runtime.Composable
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clip
import androidx.compose.ui.geometry.CornerRadius
import androidx.compose.ui.geometry.Offset
import androidx.compose.ui.geometry.Size
import androidx.compose.ui.graphics.Brush
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.graphics.drawscope.DrawScope
import androidx.compose.ui.graphics.lerp
import androidx.compose.ui.unit.Dp
import androidx.compose.ui.unit.dp

/**
 * A drawn console illustration (no photos needed), tinted with the brand color.
 * [formFactor]: home | handheld | hybrid | clamshell | retro.
 */
@Composable
fun ConsoleIcon(formFactor: String, brandColor: Color, modifier: Modifier = Modifier, size: Dp = 64.dp) {
    Box(
        modifier
            .size(size)
            .clip(RoundedCornerShape(size * 0.24f))
            .background(
                Brush.linearGradient(
                    listOf(lerp(brandColor, Color.White, 0.18f), lerp(brandColor, Color.Black, 0.35f)),
                ),
            ),
    ) {
        Canvas(
            Modifier
                .fillMaxSize()
                .padding(size * 0.14f),
        ) {
            when (formFactor) {
                "handheld" -> drawHandheld()
                "hybrid" -> drawHybrid(brandColor)
                "clamshell" -> drawClamshell()
                "retro" -> drawCartridge()
                else -> drawHomeConsole()
            }
        }
    }
}

private val Ink = Color.White
private val Shade = Color(0x55000000)
private val Screen = Color(0xFF14202E)

private fun DrawScope.r(f: Float) = CornerRadius(size.minDimension * f)

private fun DrawScope.drawHomeConsole() {
    val w = size.width
    val h = size.height
    // console box
    drawRoundRect(Ink, Offset(w * 0.08f, h * 0.06f), Size(w * 0.84f, h * 0.36f), r(0.08f))
    drawRoundRect(Shade, Offset(w * 0.16f, h * 0.20f), Size(w * 0.42f, h * 0.06f), r(0.03f))
    drawCircle(Shade, w * 0.045f, Offset(w * 0.78f, h * 0.23f))
    // controller
    val top = h * 0.52f
    drawRoundRect(Ink, Offset(w * 0.06f, top), Size(w * 0.88f, h * 0.30f), r(0.15f))
    drawRoundRect(Ink, Offset(w * 0.08f, top + h * 0.12f), Size(w * 0.22f, h * 0.34f), r(0.11f))
    drawRoundRect(Ink, Offset(w * 0.70f, top + h * 0.12f), Size(w * 0.22f, h * 0.34f), r(0.11f))
    // d-pad
    val cx = w * 0.27f
    val cy = top + h * 0.15f
    val a = w * 0.035f
    drawRect(Shade, Offset(cx - a * 3, cy - a), Size(a * 6, a * 2))
    drawRect(Shade, Offset(cx - a, cy - a * 3), Size(a * 2, a * 6))
    // buttons
    listOf(Offset(0.73f, -0.06f), Offset(0.66f, 0.0f), Offset(0.80f, 0.0f), Offset(0.73f, 0.06f)).forEach {
        drawCircle(Shade, w * 0.032f, Offset(w * it.x, cy + h * it.y))
    }
}

private fun DrawScope.drawHandheld() {
    val w = size.width
    val h = size.height
    drawRoundRect(Ink, Offset(0f, h * 0.22f), Size(w, h * 0.56f), r(0.16f))
    drawRoundRect(Screen, Offset(w * 0.26f, h * 0.30f), Size(w * 0.48f, h * 0.40f), r(0.03f))
    val cy = h * 0.50f
    val a = w * 0.028f
    val cx = w * 0.13f
    drawRect(Shade, Offset(cx - a * 3, cy - a), Size(a * 6, a * 2))
    drawRect(Shade, Offset(cx - a, cy - a * 3), Size(a * 2, a * 6))
    drawCircle(Shade, w * 0.035f, Offset(w * 0.84f, cy - h * 0.05f))
    drawCircle(Shade, w * 0.035f, Offset(w * 0.91f, cy + h * 0.04f))
}

private fun DrawScope.drawHybrid(brand: Color) {
    val w = size.width
    val h = size.height
    val left = lerp(brand, Color(0xFF00C3E3), 0.65f)
    val right = lerp(brand, Color(0xFFFF4554), 0.65f)
    drawRoundRect(left, Offset(0f, h * 0.20f), Size(w * 0.22f, h * 0.60f), r(0.12f))
    drawRoundRect(right, Offset(w * 0.78f, h * 0.20f), Size(w * 0.22f, h * 0.60f), r(0.12f))
    drawRect(Ink, Offset(w * 0.20f, h * 0.20f), Size(w * 0.60f, h * 0.60f))
    drawRoundRect(Screen, Offset(w * 0.25f, h * 0.26f), Size(w * 0.50f, h * 0.48f), r(0.02f))
    drawCircle(Ink, w * 0.045f, Offset(w * 0.11f, h * 0.36f))
    drawCircle(Ink, w * 0.045f, Offset(w * 0.89f, h * 0.56f))
}

private fun DrawScope.drawClamshell() {
    val w = size.width
    val h = size.height
    drawRoundRect(Ink, Offset(w * 0.12f, 0f), Size(w * 0.76f, h * 0.47f), r(0.08f))
    drawRoundRect(Screen, Offset(w * 0.22f, h * 0.07f), Size(w * 0.56f, h * 0.33f), r(0.02f))
    drawRoundRect(Ink, Offset(w * 0.12f, h * 0.53f), Size(w * 0.76f, h * 0.47f), r(0.08f))
    drawRoundRect(Screen, Offset(w * 0.30f, h * 0.60f), Size(w * 0.40f, h * 0.30f), r(0.02f))
    drawCircle(Shade, w * 0.03f, Offset(w * 0.21f, h * 0.70f))
    drawCircle(Shade, w * 0.03f, Offset(w * 0.79f, h * 0.70f))
}

private fun DrawScope.drawCartridge() {
    val w = size.width
    val h = size.height
    drawRoundRect(Ink, Offset(w * 0.12f, h * 0.02f), Size(w * 0.76f, h * 0.96f), r(0.07f))
    // label
    drawRoundRect(Screen, Offset(w * 0.22f, h * 0.14f), Size(w * 0.56f, h * 0.44f), r(0.04f))
    drawCircle(Color(0xFF2E9E46), w * 0.07f, Offset(w * 0.40f, h * 0.36f))
    drawCircle(Color(0xFFF2B705), w * 0.05f, Offset(w * 0.58f, h * 0.30f))
    // grip ridges
    for (i in 0 until 4) {
        drawRect(Shade, Offset(w * 0.24f, h * (0.68f + i * 0.07f)), Size(w * 0.52f, h * 0.03f))
    }
}
