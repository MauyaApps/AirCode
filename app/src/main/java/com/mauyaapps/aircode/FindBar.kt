package com.mauyaapps.aircode

import androidx.compose.foundation.layout.PaddingValues
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.text.KeyboardActions
import androidx.compose.foundation.text.KeyboardOptions
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.Text
import androidx.compose.material3.TextButton
import androidx.compose.material3.TextField
import androidx.compose.material3.TextFieldDefaults
import androidx.compose.runtime.Composable
import androidx.compose.runtime.LaunchedEffect
import androidx.compose.runtime.remember
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.focus.FocusRequester
import androidx.compose.ui.focus.focusRequester
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.res.stringResource
import androidx.compose.ui.text.input.ImeAction
import androidx.compose.ui.unit.dp
import com.mauyaapps.aircode.editor.SearchResult
import com.mauyaapps.aircode.theme.GlassSurface
import com.mauyaapps.aircode.theme.LocalGlassStyle

@Composable
fun FindBar(
    query: String,
    onQueryChange: (String) -> Unit,
    caseSensitive: Boolean,
    onCaseSensitiveChange: (Boolean) -> Unit,
    result: () -> SearchResult,
    onNext: () -> Unit,
    onPrevious: () -> Unit,
    onClose: () -> Unit,
    modifier: Modifier = Modifier,
) {
    val focusRequester = remember { FocusRequester() }
    LaunchedEffect(Unit) { focusRequester.requestFocus() }
    val searchResult = result()

    val glass = LocalGlassStyle.current.enabled
    GlassSurface(
        modifier = modifier
            .fillMaxWidth()
            .padding(if (glass) PaddingValues(horizontal = 8.dp, vertical = 4.dp) else PaddingValues(0.dp)),
    ) {
        Row(
            modifier = Modifier.padding(start = 8.dp),
            verticalAlignment = Alignment.CenterVertically,
        ) {
            TextField(
                value = query,
                onValueChange = onQueryChange,
                singleLine = true,
                placeholder = { Text(stringResource(R.string.search_hint)) },
                keyboardOptions = KeyboardOptions(imeAction = ImeAction.Search),
                keyboardActions = KeyboardActions(onSearch = { onNext() }),
                colors = if (glass) {
                    TextFieldDefaults.colors(
                        focusedContainerColor = Color.Transparent,
                        unfocusedContainerColor = Color.Transparent,
                        focusedIndicatorColor = Color.Transparent,
                        unfocusedIndicatorColor = Color.Transparent,
                    )
                } else {
                    TextFieldDefaults.colors()
                },
                modifier = Modifier
                    .weight(1f)
                    .focusRequester(focusRequester),
            )
            if (query.isNotEmpty()) {
                Text(
                    text = stringResource(R.string.search_count, searchResult.current, searchResult.total),
                    style = MaterialTheme.typography.labelMedium,
                    modifier = Modifier.padding(horizontal = 6.dp),
                )
            }
            TextButton(onClick = { onCaseSensitiveChange(!caseSensitive) }) {
                Text(
                    text = stringResource(R.string.search_case),
                    color = if (caseSensitive) {
                        MaterialTheme.colorScheme.primary
                    } else {
                        MaterialTheme.colorScheme.onSurfaceVariant
                    },
                )
            }
            TextButton(onClick = onPrevious, enabled = searchResult.total > 0) { Text(UP_GLYPH) }
            TextButton(onClick = onNext, enabled = searchResult.total > 0) { Text(DOWN_GLYPH) }
            TextButton(onClick = onClose) { Text(CLOSE_GLYPH) }
        }
    }
}

private const val UP_GLYPH = "↑"
private const val DOWN_GLYPH = "↓"
private const val CLOSE_GLYPH = "×"
