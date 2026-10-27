package com.mauyaapps.aircode.editor

import android.webkit.JavascriptInterface

/**
 * Объект, доступный из JS как `AirCodeBridge`. Вызывается из фонового потока WebView,
 * поэтому [onMessage] сам отвечает за переход на главный поток.
 */
internal class EditorBridge(private val onMessage: (String) -> Unit) {

    @JavascriptInterface
    fun postMessage(json: String) {
        onMessage(json)
    }
}
