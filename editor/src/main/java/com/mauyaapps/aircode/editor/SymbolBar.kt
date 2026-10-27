package com.mauyaapps.aircode.editor

import androidx.compose.foundation.clickable
import androidx.compose.foundation.horizontalScroll
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.heightIn
import androidx.compose.foundation.layout.widthIn
import androidx.compose.foundation.rememberScrollState
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.Surface
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.text.font.FontFamily
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp

/** Символы панели; клавиша Tab добавляется отдельно. */
private val SYMBOLS = listOf("{", "}", "(", ")", "[", "]", ";", ":")

private const val TAB_LABEL = "Tab"

/**
 * Панель символов над клавиатурой. Нажатия идут прямо в [controller],
 * фокус остаётся в редакторе, поэтому клавиатура не закрывается.
 */
@Composable
fun SymbolBar(controller: EditorController, modifier: Modifier = Modifier) {
    Surface(modifier = modifier.fillMaxWidth(), color = MaterialTheme.colorScheme.surfaceVariant) {
        Row(modifier = Modifier.horizontalScroll(rememberScrollState())) {
            SymbolKey(label = TAB_LABEL, onClick = controller::indent)
            SYMBOLS.forEach { symbol ->
                SymbolKey(label = symbol, onClick = { controller.insertText(symbol) })
            }
        }
    }
}

@Composable
private fun SymbolKey(label: String, onClick: () -> Unit) {
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
