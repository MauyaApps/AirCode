package com.mauyaapps.aircode.editor

import android.annotation.SuppressLint
import android.graphics.Color
import android.webkit.WebResourceRequest
import android.webkit.WebView
import android.webkit.WebViewClient
import androidx.compose.runtime.Composable
import androidx.compose.ui.Modifier
import androidx.compose.ui.viewinterop.AndroidView

@SuppressLint("SetJavaScriptEnabled")
@Composable
fun PreviewView(
    kind: PreviewKind,
    text: String,
    darkTheme: Boolean,
    modifier: Modifier = Modifier,
) {
    AndroidView(
        modifier = modifier,
        factory = { context ->
            WebView(context).apply {
                disableForcedDarkening()
                settings.apply {
                    javaScriptEnabled = kind == PreviewKind.HTML
                    allowFileAccess = false
                    allowContentAccess = false
                    blockNetworkLoads = true
                    domStorageEnabled = false
                }
                setBackgroundColor(if (kind == PreviewKind.HTML) Color.WHITE else Color.TRANSPARENT)
                webViewClient = object : WebViewClient() {
                    override fun shouldOverrideUrlLoading(
                        view: WebView,
                        request: WebResourceRequest,
                    ): Boolean = true
                }
                val page = when (kind) {
                    PreviewKind.MARKDOWN -> MarkdownRenderer.renderPage(text, darkTheme)
                    PreviewKind.HTML -> text
                }
                loadDataWithBaseURL(null, page, "text/html", "utf-8", null)
            }
        },
        onRelease = { view -> view.destroy() },
    )
}
