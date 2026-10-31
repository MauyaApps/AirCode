package com.mauyaapps.aircode

import androidx.activity.compose.BackHandler
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.safeDrawingPadding
import androidx.compose.material3.Surface
import androidx.compose.runtime.Composable
import androidx.compose.ui.Modifier
import com.mauyaapps.aircode.terminal.TerminalManager
import com.mauyaapps.aircode.terminal.TerminalPanel

@Composable
fun TerminalScreen(manager: TerminalManager, onClose: () -> Unit) {
    BackHandler(onBack = onClose)
    Surface(modifier = Modifier.fillMaxSize()) {
        TerminalPanel(
            manager = manager,
            expanded = true,
            onToggleExpanded = {},
            onHide = onClose,
            onInputFocusChange = {},
            showExpandToggle = false,
            modifier = Modifier.fillMaxSize().safeDrawingPadding(),
        )
    }
}
