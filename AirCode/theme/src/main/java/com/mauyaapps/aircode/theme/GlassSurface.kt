package com.mauyaapps.aircode.theme

import androidx.compose.foundation.background
import androidx.compose.foundation.border
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.material3.LocalContentColor
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.Surface
import androidx.compose.runtime.Composable
import androidx.compose.runtime.CompositionLocalProvider
import androidx.compose.runtime.Immutable
import androidx.compose.runtime.staticCompositionLocalOf
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clip
import androidx.compose.ui.graphics.Brush
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.graphics.RectangleShape
import androidx.compose.ui.graphics.Shape
import androidx.compose.ui.unit.dp

@Immutable
data class GlassStyle(val enabled: Boolean, val dark: Boolean)

val LocalGlassStyle = staticCompositionLocalOf { GlassStyle(enabled = false, dark = false) }

private fun rimBrush(dark: Boolean): Brush {
    val bright = Color.White.copy(alpha = if (dark) 0.70f else 0.95f)
    val faint = if (dark) Color.White.copy(alpha = 0.10f) else Color.Black.copy(alpha = 0.07f)
    return Brush.linearGradient(
        0f to bright,
        0.30f to faint,
        0.70f to faint,
        1f to bright.copy(alpha = bright.alpha * 0.85f),
    )
}

fun Modifier.glassFrame(shape: Shape, dark: Boolean): Modifier =
    this
        .clip(shape)
        .border(width = 1.2.dp, brush = rimBrush(dark), shape = shape)

fun Modifier.glass(shape: Shape, dark: Boolean, tint: Color = Color.Unspecified): Modifier {
    val top = Color.White.copy(alpha = if (dark) 0.18f else 0.60f)
    val bottom = Color.White.copy(alpha = if (dark) 0.06f else 0.28f)
    var result = this
        .clip(shape)
        .background(Brush.verticalGradient(listOf(top, bottom)))
    if (tint != Color.Unspecified) result = result.background(tint)
    return result.border(width = 1.2.dp, brush = rimBrush(dark), shape = shape)
}

@Composable
fun GlassSurface(
    modifier: Modifier = Modifier,
    shape: Shape = RoundedCornerShape(24.dp),
    fallbackShape: Shape = RectangleShape,
    fallbackColor: Color = MaterialTheme.colorScheme.surfaceVariant,
    tint: Color = Color.Unspecified,
    content: @Composable () -> Unit,
) {
    val glass = LocalGlassStyle.current
    if (glass.enabled) {
        CompositionLocalProvider(LocalContentColor provides MaterialTheme.colorScheme.onSurface) {
            Box(modifier = modifier.glass(shape, glass.dark, tint)) { content() }
        }
    } else {
        Surface(modifier = modifier, shape = fallbackShape, color = fallbackColor, content = content)
    }
}
