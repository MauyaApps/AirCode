package com.mauyaapps.aircode.editor

import android.webkit.JavascriptInterface

internal class EditorBridge(private val onMessage: (String) -> Unit) {

    @JavascriptInterface
    fun postMessage(json: String) {
        onMessage(json)
    }
}
