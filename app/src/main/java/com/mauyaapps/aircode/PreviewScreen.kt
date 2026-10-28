package com.mauyaapps.aircode

import androidx.activity.compose.BackHandler
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.padding
import androidx.compose.material3.ExperimentalMaterial3Api
import androidx.compose.material3.Scaffold
import androidx.compose.material3.Text
import androidx.compose.material3.TextButton
import androidx.compose.material3.TopAppBar
import androidx.compose.runtime.Composable
import androidx.compose.ui.Modifier
import androidx.compose.ui.res.stringResource
import androidx.compose.ui.text.style.TextOverflow
import com.mauyaapps.aircode.editor.PreviewKind
import com.mauyaapps.aircode.editor.PreviewView

data class PreviewState(val kind: PreviewKind, val fileName: String, val text: String)

@OptIn(ExperimentalMaterial3Api::class)
@Composable
fun PreviewScreen(
    preview: PreviewState,
    darkTheme: Boolean,
    onClose: () -> Unit,
) {
    BackHandler(onBack = onClose)
    Scaffold(
        modifier = Modifier.fillMaxSize(),
        topBar = {
            TopAppBar(
                title = {
                    Text(
                        text = stringResource(R.string.preview_title, preview.fileName),
                        maxLines = 1,
                        overflow = TextOverflow.Ellipsis,
                    )
                },
                navigationIcon = { TextButton(onClick = onClose) { Text(CLOSE_GLYPH) } },
            )
        },
    ) { padding ->
        PreviewView(
            kind = preview.kind,
            text = preview.text,
            darkTheme = darkTheme,
            modifier = Modifier
                .padding(padding)
                .fillMaxSize(),
        )
    }
}

private const val CLOSE_GLYPH = "×"
