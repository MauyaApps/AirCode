package com.mauyaapps.aircode.theme

import androidx.compose.foundation.background
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.BoxScope
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.offset
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.shape.CircleShape
import androidx.compose.runtime.Composable
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.BlurredEdgeTreatment
import androidx.compose.ui.draw.blur
import androidx.compose.ui.graphics.Brush
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.graphics.compositeOver
import androidx.compose.ui.graphics.toArgb
import androidx.compose.ui.unit.dp

@Composable
fun GlassBackground(accent: Color, dark: Boolean, content: @Composable () -> Unit) {
    val base = if (dark) Color(0xFF0B0F14) else Color(0xFFEFF3F8)
    val top = accent.copy(alpha = if (dark) 0.30f else 0.22f).compositeOver(base)
    val second = Color(rotateHue(accent.toArgb(), 70f))
    val third = Color(rotateHue(accent.toArgb(), -60f))

    Box(
        modifier = Modifier
            .fillMaxSize()
            .background(Brush.verticalGradient(listOf(top, base))),
    ) {
        Blob(accent.copy(alpha = 0.55f), 320, Alignment.TopStart, -90, -40)
        Blob(second.copy(alpha = 0.40f), 360, Alignment.CenterEnd, 120, 40)
        Blob(third.copy(alpha = 0.40f), 300, Alignment.BottomStart, -100, 60)
        content()
    }
}

@Composable
private fun BoxScope.Blob(
    color: Color,
    sizeDp: Int,
    alignment: Alignment,
    offsetX: Int,
    offsetY: Int,
) {
    Box(
        modifier = Modifier
            .align(alignment)
            .offset(x = offsetX.dp, y = offsetY.dp)
            .size(sizeDp.dp)
            .blur(80.dp, BlurredEdgeTreatment.Unbounded)
            .background(color, CircleShape),
    )
}
