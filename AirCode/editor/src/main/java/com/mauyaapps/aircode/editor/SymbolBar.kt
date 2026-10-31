package com.mauyaapps.aircode.editor

import androidx.compose.foundation.clickable
import androidx.compose.foundation.horizontalScroll
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.PaddingValues
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.heightIn
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.widthIn
import androidx.compose.foundation.rememberScrollState
import androidx.compose.foundation.shape.CircleShape
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.Surface
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.text.font.FontFamily
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import com.mauyaapps.aircode.theme.GlassSurface
import com.mauyaapps.aircode.theme.LocalGlassStyle

private val SYMBOLS = listOf("{", "}", "(", ")", "[", "]", ";", ":")

private const val TAB_LABEL = "Tab"

@Composable
fun SymbolBar(controller: EditorController, modifier: Modifier = Modifier) {
    val glass = LocalGlassStyle.current.enabled
    Surface(
        modifier = modifier.fillMaxWidth(),
        color = if (glass) Color.Transparent else MaterialTheme.colorScheme.surfaceVariant,
    ) {
        Row(
            modifier = Modifier
                .horizontalScroll(rememberScrollState())
                .padding(if (glass) PaddingValues(horizontal = 8.dp, vertical = 6.dp) else PaddingValues(0.dp)),
            horizontalArrangement = if (glass) Arrangement.spacedBy(8.dp) else Arrangement.Start,
        ) {
            SymbolKey(label = TAB_LABEL, onClick = controller::indent)
            SYMBOLS.forEach { symbol ->
                SymbolKey(label = symbol, onClick = { controller.insertText(symbol) })
            }
        }
    }
}

@Composable
private fun SymbolKey(label: String, onClick: () -> Unit) {
    GlassSurface(shape = CircleShape, fallbackColor = Color.Transparent) {
        Box(
            modifier = Modifier
                .widthIn(min = 48.dp)
                .heightIn(min = 44.dp)
                .clickable(onClick = onClick),
            contentAlignment = Alignment.Center,
        ) {
            Text(
                text = label,
                fontFamily = FontFamily.Monospace,
                fontSize = 18.sp,
                color = MaterialTheme.colorScheme.onSurfaceVariant,
            )
        }
    }
}
