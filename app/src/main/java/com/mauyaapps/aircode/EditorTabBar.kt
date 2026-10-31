package com.mauyaapps.aircode

import androidx.compose.foundation.clickable
import androidx.compose.foundation.horizontalScroll
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.PaddingValues
import androidx.compose.foundation.layout.Row
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
import androidx.compose.ui.text.style.TextOverflow
import androidx.compose.ui.unit.dp
import com.mauyaapps.aircode.theme.GlassSurface
import com.mauyaapps.aircode.theme.LocalGlassStyle

@Composable
fun EditorTabBar(
    tabs: List<EditorTab>,
    activeId: Long?,
    onSelect: (Long) -> Unit,
    onClose: (Long) -> Unit,
    modifier: Modifier = Modifier,
) {
    val glass = LocalGlassStyle.current.enabled
    Surface(
        modifier = modifier,
        color = if (glass) Color.Transparent else MaterialTheme.colorScheme.surfaceVariant,
    ) {
        Row(
            modifier = Modifier
                .horizontalScroll(rememberScrollState())
                .padding(if (glass) PaddingValues(horizontal = 8.dp, vertical = 6.dp) else PaddingValues(0.dp)),
            horizontalArrangement = if (glass) Arrangement.spacedBy(8.dp) else Arrangement.Start,
        ) {
            tabs.forEach { tab ->
                val selected = tab.id == activeId
                GlassSurface(
                    shape = CircleShape,
                    tint = if (selected) MaterialTheme.colorScheme.primary.copy(alpha = 0.30f) else Color.Unspecified,
                    fallbackColor = if (selected) {
                        MaterialTheme.colorScheme.surface
                    } else {
                        MaterialTheme.colorScheme.surfaceVariant
                    },
                ) {
                    Row(
                        modifier = Modifier
                            .heightIn(min = 44.dp)
                            .clickable { onSelect(tab.id) }
                            .padding(start = 12.dp),
                        verticalAlignment = Alignment.CenterVertically,
                    ) {
                        Text(
                            text = if (tab.isDirty) "${tab.name} •" else tab.name,
                            style = MaterialTheme.typography.labelLarge,
                            maxLines = 1,
                            overflow = TextOverflow.Ellipsis,
                            modifier = Modifier.widthIn(max = 160.dp),
                        )
                        Text(
                            text = CLOSE_GLYPH,
                            style = MaterialTheme.typography.titleMedium,
                            modifier = Modifier
                                .clickable { onClose(tab.id) }
                                .heightIn(min = 44.dp)
                                .widthIn(min = 40.dp)
                                .padding(top = 8.dp)
                                .padding(horizontal = 12.dp),
                        )
                    }
                }
            }
        }
    }
}

private const val CLOSE_GLYPH = "×"
