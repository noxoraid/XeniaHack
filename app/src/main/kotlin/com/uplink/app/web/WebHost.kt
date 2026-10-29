package com.uplink.app

import android.annotation.SuppressLint
import android.content.Context
import android.content.Intent
import android.content.MutableContextWrapper
import android.graphics.Bitmap
import android.os.Handler
import android.os.Looper
import android.webkit.CookieManager
import android.webkit.WebChromeClient
import android.webkit.WebResourceRequest
import android.webkit.WebResourceResponse
import android.webkit.WebSettings
import android.webkit.WebStorage
import android.webkit.WebView
import android.webkit.WebViewClient
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableIntStateOf
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.setValue
import org.json.JSONObject
import org.json.JSONTokener
import java.io.ByteArrayInputStream
import java.util.concurrent.CountDownLatch
import java.util.concurrent.TimeUnit

object WebHost {
    const val HOME = "https://www.google.com"
    private const val DESKTOP_AGENT =
        "Mozilla/5.0 (X11; Linux x86_64) AppleWebKit/537.36 (KHTML, like Gecko) Chrome/124.0.0.0 Safari/537.36"
    private const val DESKTOP_VIEWPORT =
        "(function(){var m=document.querySelector('meta[name=viewport]');if(!m){m=document.createElement('meta');" +
            "m.name='viewport';document.head.appendChild(m)}m.content='width=1280'})()"

    var url by mutableStateOf(HOME)
    var title by mutableStateOf("")
    var progress by mutableIntStateOf(100)
    var canBack by mutableStateOf(false)
    var canForward by mutableStateOf(false)
    var desktop by mutableStateOf(false)
    var adblock by mutableStateOf(true)
    var devtools by mutableStateOf(false)
    var adsBuffer by mutableStateOf(false)
    var mediaBuffer by mutableStateOf(false)
    var unlocked by mutableStateOf(false)
    var held by mutableIntStateOf(0)
    private var pageUrl = ""

    var failure by mutableStateOf<String?>(null)

    private var view: WebView? = null

    val opened: Boolean get() = view != null
    private var shell: MutableContextWrapper? = null
    private var phoneAgent = ""

    fun prime(app: Context) {
        adblock = Cfg.adblock(app)
        desktop = Cfg.desktop(app)
        adsBuffer = Cfg.adsBuffer(app)
        mediaBuffer = Cfg.mediaBuffer(app)
    }

    fun obtain(host: Context): WebView? {
        view?.let {
            shell?.baseContext = host
            return it
        }
        return try {
            val wrap = MutableContextWrapper(host)
            val web = WebView(wrap)
            phoneAgent = web.settings.userAgentString
                .replace("; wv)", ")")
                .replace("Version/4.0 ", "")
            configure(web)
            shell = wrap
            view = web
            web.loadUrl(HOME)
            web
        } catch (t: Throwable) {
            failure = t.message ?: t.toString()
            null
        }
    }

    fun detach(web: WebView, app: Context) {
        web.onPause()
        shell?.baseContext = app.applicationContext
    }

    @SuppressLint("SetJavaScriptEnabled")
    private fun configure(web: WebView) {
        val s = web.settings
        s.javaScriptEnabled = true
        s.domStorageEnabled = true
        s.useWideViewPort = true
        s.loadWithOverviewMode = true
        s.setSupportZoom(true)
        s.builtInZoomControls = true
        s.displayZoomControls = false
        s.mixedContentMode = WebSettings.MIXED_CONTENT_COMPATIBILITY_MODE
        s.allowFileAccess = false
        s.javaScriptCanOpenWindowsAutomatically = false
        s.setSupportMultipleWindows(false)
        applyAgent(web)
        CookieManager.getInstance().setAcceptCookie(true)
        CookieManager.getInstance().setAcceptThirdPartyCookies(web, true)
        try { WebView.setWebContentsDebuggingEnabled(true) } catch (_: Throwable) { }
        web.webViewClient = FlowClient()
        web.webChromeClient = FlowChrome()
    }

    private fun applyAgent(web: WebView) {
        web.settings.userAgentString = if (desktop) DESKTOP_AGENT else phoneAgent
    }

    fun resolve(raw: String): String {
        val text = raw.trim()
        if (text.startsWith("http://") || text.startsWith("https://")) return text
        val spaced = text.any { it.isWhitespace() }
        if (!spaced && (text.contains('.') || text.startsWith("localhost"))) return "https://$text"
        return "https://www.google.com/search?q=" + java.net.URLEncoder.encode(text, "UTF-8")
    }

    fun load(raw: String) {
        view?.loadUrl(resolve(raw))
    }

    fun open(raw: String, stopped: () -> Boolean): String {
        val web = view ?: throw Exception(tr("Open the Browser screen once first"))
        val address = resolve(raw)
        Handler(Looper.getMainLooper()).post { web.loadUrl(address) }
        Thread.sleep(400)
        var waited = 0
        while (progress < 100 && waited < 15_000) {
            if (stopped()) throw TaskStopped()
            Thread.sleep(200)
            waited += 200
        }
        return title + "\n" + url
    }

    fun script(code: String): String {
        val web = view ?: throw Exception(tr("Open the Browser screen once first"))
        val gate = CountDownLatch(1)
        val box = arrayOfNulls<String>(1)
        Handler(Looper.getMainLooper()).post {
            web.evaluateJavascript(code) {
                box[0] = it
                gate.countDown()
            }
        }
        if (!gate.await(8, TimeUnit.SECONDS)) throw Exception(tr("The page did not answer"))
        val raw = box[0] ?: return "null"
        val value = try {
            JSONTokener(raw).nextValue()
        } catch (_: Exception) {
            raw
        }
        return if (value == JSONObject.NULL) "null" else value.toString()
    }

    fun setDesktop(ctx: Context, on: Boolean) {
        desktop = on
        Cfg.setDesktop(ctx, on)
        view?.let {
            applyAgent(it)
            it.reload()
        }
    }

    fun setAdblock(ctx: Context, on: Boolean) {
        adblock = on
        Cfg.setAdblock(ctx, on)
        view?.reload()
    }

    fun setAdsBuffer(ctx: Context, on: Boolean) {
        adsBuffer = on
        Cfg.setAdsBuffer(ctx, on)
        view?.reload()
    }

    fun setMediaBuffer(ctx: Context, on: Boolean) {
        mediaBuffer = on
        Cfg.setMediaBuffer(ctx, on)
        view?.reload()
    }

    fun unlock() {
        unlocked = true
        held = 0
        view?.reload()
    }

    private fun empty(): WebResourceResponse = WebResourceResponse("text/plain", "utf-8", ByteArrayInputStream(ByteArray(0)))

    fun wipe() {
        view?.let {
            it.clearCache(true)
            it.clearHistory()
            it.clearFormData()
        }
        CookieManager.getInstance().removeAllCookies(null)
        WebStorage.getInstance().deleteAllData()
        canBack = false
        canForward = false
    }

    private class FlowClient : WebViewClient() {
        override fun shouldInterceptRequest(view: WebView, request: WebResourceRequest): WebResourceResponse? {
            return try {
                val ad = WebBlock.blocks(request.url.host)
                val media = !ad && WebBlock.isMedia(request.url, request.requestHeaders)
                when {
                    ad && WebHost.adblock -> WebHost.empty()
                    ad && WebHost.adsBuffer && !WebHost.unlocked -> hold(view)
                    media && WebHost.mediaBuffer && !WebHost.unlocked -> hold(view)
                    else -> null
                }
            } catch (_: Throwable) {
                null
            }
        }

        private fun hold(view: WebView): WebResourceResponse {
            view.post { WebHost.held++ }
            return WebHost.empty()
        }

        override fun shouldOverrideUrlLoading(view: WebView, request: WebResourceRequest): Boolean {
            val scheme = request.url.scheme ?: return false
            if (scheme == "http" || scheme == "https") return false
            if (scheme == "mailto" || scheme == "tel") {
                try {
                    view.context.startActivity(Intent(Intent.ACTION_VIEW, request.url).addFlags(Intent.FLAG_ACTIVITY_NEW_TASK))
                } catch (_: Throwable) {
                }
            }
            return true
        }

        override fun onPageStarted(view: WebView, url: String?, favicon: Bitmap?) {
            if (url != null) WebHost.url = url
            if (url != null && url != WebHost.pageUrl) {
                WebHost.pageUrl = url
                WebHost.unlocked = false
                WebHost.held = 0
            }
        }

        override fun doUpdateVisitedHistory(view: WebView, url: String?, isReload: Boolean) {
            if (url != null) WebHost.url = url
            WebHost.canBack = view.canGoBack()
            WebHost.canForward = view.canGoForward()
        }

        override fun onPageFinished(view: WebView, url: String?) {
            try {
                if (url != null && (url.startsWith("http://") || url.startsWith("https://"))) {
                    WebHistory.add(url, view.title ?: url)
                }
                if (WebHost.adblock) view.evaluateJavascript(WebBlock.cosmetic, null)
                if (WebHost.desktop) view.evaluateJavascript(DESKTOP_VIEWPORT, null)
                if (WebHost.devtools) DevTools.attach(view)
            } catch (_: Throwable) {
            }
        }
    }

    private class FlowChrome : WebChromeClient() {
        override fun onProgressChanged(view: WebView, newProgress: Int) {
            WebHost.progress = newProgress
        }

        override fun onReceivedTitle(view: WebView, title: String?) {
            WebHost.title = title ?: ""
        }
    }
}
