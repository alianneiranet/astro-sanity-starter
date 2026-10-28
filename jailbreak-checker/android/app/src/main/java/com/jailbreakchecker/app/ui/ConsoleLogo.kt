package com.jailbreakchecker.app.ui

import androidx.compose.foundation.background
import androidx.compose.foundation.border
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.runtime.Composable
import androidx.compose.runtime.compositionLocalOf
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clip
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.layout.ContentScale
import androidx.compose.ui.unit.Dp
import androidx.compose.ui.unit.dp
import coil3.compose.SubcomposeAsyncImage

/** Ids (console or brand) that have an SVG logo in assets/logos. Provided once at the app root. */
val LocalLogoIds = compositionLocalOf { emptySet<String>() }

/**
 * Official logo (SVG from Wikimedia Commons) on a light tile so dark logos stay readable
 * in dark mode. Falls back to the drawn console icon when there is no logo.
 */
@Composable
fun ConsoleLogo(
    id: String,
    formFactor: String,
    brandColor: Color,
    modifier: Modifier = Modifier,
    size: Dp = 64.dp,
    width: Dp = size,
) {
    if (id !in LocalLogoIds.current) {
        ConsoleIcon(formFactor, brandColor, modifier, size)
        return
    }
    Box(
        modifier
            .size(width = width, height = size)
            .clip(RoundedCornerShape(size * 0.24f))
            .background(Color.White)
            .border(1.dp, Color(0x1A000000), RoundedCornerShape(size * 0.24f))
            .padding(size * 0.12f),
        contentAlignment = Alignment.Center,
    ) {
        SubcomposeAsyncImage(
            model = "file:///android_asset/logos/$id.svg",
            contentDescription = null,
            contentScale = ContentScale.Fit,
            modifier = Modifier.fillMaxSize(),
            error = { ConsoleIcon(formFactor, brandColor, Modifier.fillMaxSize(), size) },
        )
    }
}
