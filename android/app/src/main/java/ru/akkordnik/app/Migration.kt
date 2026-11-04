package ru.akkordnik.app

import android.annotation.SuppressLint
import android.app.Activity
import android.os.Handler
import android.os.Looper
import android.webkit.JavascriptInterface
import android.webkit.WebResourceRequest
import android.webkit.WebResourceResponse
import android.webkit.WebView
import android.webkit.WebViewClient

/**
 * Перенос песен из прошлой версии (она хранила их в localStorage WebView с адресом https://localhost).
 * Открываем невидимый WebView на том же адресе, читаем localStorage и сразу закрываем.
 */
object Migration {
    class Bridge(private val cb: (String?) -> Unit) {
        @JavascriptInterface fun done(s: String?) = cb(s ?: "")
    }

    @SuppressLint("SetJavaScriptEnabled")
    fun run(activity: Activity, onResult: (raw: String?, ok: Boolean) -> Unit) {
        val main = Handler(Looper.getMainLooper())
        var finished = false
        var web: WebView? = null
        fun finish(v: String?) = main.post {
            if (finished) return@post
            finished = true
            onResult(v, v != null)
            web?.let { w -> w.stopLoading(); w.destroy() }
            web = null
        }
        try {
            val w = WebView(activity)
            web = w
            w.settings.javaScriptEnabled = true
            w.settings.domStorageEnabled = true
            w.addJavascriptInterface(Bridge { finish(it) }, "AkkMig")
            w.webViewClient = object : WebViewClient() {
                override fun shouldInterceptRequest(view: WebView?, request: WebResourceRequest?): WebResourceResponse {
                    val html = "<!doctype html><meta charset=utf-8><script>" +
                        "var v=null;try{v=localStorage.getItem('akkordnik.v2')}catch(e){}" +
                        "AkkMig.done(v);</script>"
                    return WebResourceResponse("text/html", "utf-8", html.byteInputStream())
                }
            }
            w.loadUrl("https://localhost/migrate.html")
            main.postDelayed({ finish(null) }, 6000)
        } catch (_: Throwable) {
            finish(null)
        }
    }
}
