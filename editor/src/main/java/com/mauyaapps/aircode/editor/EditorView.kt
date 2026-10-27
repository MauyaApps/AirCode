package com.mauyaapps.aircode.editor

import android.annotation.SuppressLint
import android.content.Context
import android.graphics.Color
import android.webkit.WebResourceRequest
import android.webkit.WebView
import android.webkit.WebViewClient
import androidx.compose.runtime.Composable
import androidx.compose.runtime.LaunchedEffect
import androidx.compose.runtime.SideEffect
import androidx.compose.ui.Modifier
import androidx.compose.ui.viewinterop.AndroidView

private const val EDITOR_URL = "file:///android_asset/editor/index.html"
private const val BRIDGE_NAME = "AirCodeBridge"

/**
 * Редактор кода на CodeMirror 6 внутри WebView. Страница загружается из assets,
 * сети у неё нет: загрузки из интернета заблокированы, переходы по ссылкам запрещены.
 */
@Composable
fun EditorView(
    controller: EditorController,
    documents: List<EditorDocument>,
    activeId: Long?,
    darkTheme: Boolean,
    autocompleteEnabled: Boolean,
    onChanged: () -> Unit,
    modifier: Modifier = Modifier,
) {
    SideEffect { controller.onChanged = onChanged }
    LaunchedEffect(darkTheme) { controller.setDarkTheme(darkTheme) }
    LaunchedEffect(autocompleteEnabled) { controller.setAutocomplete(autocompleteEnabled) }
    LaunchedEffect(documents.map { it.id }, activeId) { controller.sync(documents, activeId) }

    AndroidView(
        modifier = modifier,
        factory = { context -> createWebView(context, controller) },
        onRelease = { view ->
            controller.detach()
            view.destroy()
        },
    )
}

@SuppressLint("SetJavaScriptEnabled")
private fun createWebView(context: Context, controller: EditorController): WebView {
    val webView = WebView(context)
    webView.settings.apply {
        javaScriptEnabled = true
        allowContentAccess = false
        blockNetworkLoads = true
        domStorageEnabled = false
        setSupportZoom(false)
    }
    webView.setBackgroundColor(Color.TRANSPARENT)
    webView.webViewClient = object : WebViewClient() {
        override fun shouldOverrideUrlLoading(view: WebView, request: WebResourceRequest): Boolean =
            true
    }
    webView.addJavascriptInterface(
        EditorBridge { json ->
            // Мост вызывается из фонового потока, а контроллер работает в главном
            webView.post { EditorMessageParser.parse(json)?.let(controller::handle) }
        },
        BRIDGE_NAME,
    )
    controller.attach(webView)
    webView.loadUrl(EDITOR_URL)
    return webView
}
