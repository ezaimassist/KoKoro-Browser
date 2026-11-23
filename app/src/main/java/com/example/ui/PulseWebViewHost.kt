package com.example.ui

import android.annotation.SuppressLint
import android.content.Context
import android.graphics.Bitmap
import android.net.http.SslError
import android.os.Build
import android.view.MotionEvent
import android.view.View
import android.view.ViewGroup
import android.webkit.ConsoleMessage
import android.webkit.CookieManager
import android.webkit.RenderProcessGoneDetail
import android.webkit.SslErrorHandler
import android.webkit.WebChromeClient
import android.webkit.WebResourceError
import android.webkit.WebResourceRequest
import android.webkit.WebResourceResponse
import android.webkit.WebSettings
import android.webkit.WebView
import android.webkit.WebViewClient
import android.widget.FrameLayout
import androidx.compose.runtime.Composable
import androidx.compose.runtime.DisposableEffect
import androidx.compose.runtime.LaunchedEffect
import androidx.compose.runtime.remember
import androidx.compose.runtime.rememberUpdatedState
import androidx.compose.ui.Modifier
import androidx.compose.ui.platform.LocalContext
import androidx.compose.ui.viewinterop.AndroidView
import androidx.webkit.ScriptHandler
import com.example.data.PulseSettings
import com.example.engine.BrowserIdentityCompat
import com.example.engine.DiagnosticsCollector
import com.example.engine.UrlUtils
import com.example.viewmodel.PulseTab
import com.example.viewmodel.PulseWebCommand
import kotlinx.coroutines.flow.SharedFlow

/**
 * Compatibility-first Chromium WebView subclass with Android unbuffered touch dispatch
 * for low-latency gaming input without interfering with WebGL/WebAssembly rendering.
 */
class LowLatencyPulseWebView(context: Context) : WebView(context) {
    @Volatile
    var unbufferedTouchEnabled: Boolean = true

    var compatScriptHandler: ScriptHandler? = null

    override fun dispatchTouchEvent(event: MotionEvent): Boolean {
        if (unbufferedTouchEnabled && event.actionMasked == MotionEvent.ACTION_DOWN) {
            requestUnbufferedDispatch(event)
        }
        return super.dispatchTouchEvent(event)
    }
}

@Composable
fun PulseWebViewHost(
    tabs: List<PulseTab>,
    activeTabId: String,
    gamingModeActive: Boolean,
    effectiveRenderScale: Int,
    pulseSettings: PulseSettings,
    diagnosticsCollector: DiagnosticsCollector,
    commands: SharedFlow<PulseWebCommand>,
    onPageStarted: (tabId: String, url: String) -> Unit,
    onPageProgress: (tabId: String, progress: Int, canGoBack: Boolean, canGoForward: Boolean) -> Unit,
    onPageTitle: (tabId: String, title: String?) -> Unit,
    onPageFinished: (tabId: String, url: String, canGoBack: Boolean, canGoForward: Boolean) -> Unit,
    onRendererCrash: (tabId: String, didCrash: Boolean, priorityAtExit: Int) -> Unit,
    onDownloadRequested: (tabId: String, url: String, ua: String, disposition: String?, mime: String?, length: Long) -> Unit,
    onWebGlProbeResult: (String?) -> Unit,
    onEnterHtml5Fullscreen: (View, WebChromeClient.CustomViewCallback) -> Unit,
    onExitHtml5Fullscreen: () -> Unit,
    modifier: Modifier = Modifier
) {
    val context = LocalContext.current
    val pool = remember { LinkedHashMap<String, LowLatencyPulseWebView>() }
    val container = remember { FrameLayout(context) }

    val latestSettings = rememberUpdatedState(pulseSettings)
    val latestGaming = rememberUpdatedState(gamingModeActive)
    val latestScale = rememberUpdatedState(effectiveRenderScale)
    val latestTabs = rememberUpdatedState(tabs)
    val latestStarted = rememberUpdatedState(onPageStarted)
    val latestProgress = rememberUpdatedState(onPageProgress)
    val latestTitle = rememberUpdatedState(onPageTitle)
    val latestFinished = rememberUpdatedState(onPageFinished)
    val latestCrash = rememberUpdatedState(onRendererCrash)
    val latestDownload = rememberUpdatedState(onDownloadRequested)
    val latestProbeResult = rememberUpdatedState(onWebGlProbeResult)
    val latestEnterFs = rememberUpdatedState(onEnterHtml5Fullscreen)
    val latestExitFs = rememberUpdatedState(onExitHtml5Fullscreen)

    fun configureWebView(wv: LowLatencyPulseWebView, tabId: String) {
        val tabMeta = latestTabs.value.find { it.id == tabId }
        val isPrivate = tabMeta?.isPrivate == true
        val useDesktopUa = tabMeta?.isDesktopUa == true
        val isFrozen = tabMeta?.isFrozen == true && tabId != activeTabId
        val s = latestSettings.value

        wv.unbufferedTouchEnabled = s.unbufferedTouchInput || latestGaming.value

        // IMPORTANT: Keep LAYER_TYPE_NONE on a hardwareAccelerated="true" Activity window.
        // Chromium WebView is already GPU-accelerated directly; forcing LAYER_TYPE_HARDWARE
        // allocates a redundant offscreen FBO that can cause black screens/OOM on large WebGL2 games.
        if (wv.layerType != View.LAYER_TYPE_NONE) {
            wv.setLayerType(View.LAYER_TYPE_NONE, null)
        }

        val webSettings = wv.settings
        BrowserIdentityCompat.applySettings(
            context = context,
            webSettings = webSettings,
            desktopMode = useDesktopUa,
            standaloneChromeCompat = s.standaloneChromeCompat
        )

        if (s.standaloneChromeCompat) {
            if (wv.compatScriptHandler == null) {
                wv.compatScriptHandler = BrowserIdentityCompat.attachDocumentStartCompatScript(wv)
            }
        } else {
            wv.compatScriptHandler?.let { handler ->
                runCatching { handler.remove() }
                wv.compatScriptHandler = null
            }
        }

        // Private tabs avoid persistent disk cache and third-party cookies;
        // normal tabs use LOAD_DEFAULT so large MPQ/Wasm game assets cache cleanly across sessions.
        webSettings.cacheMode = if (isPrivate) WebSettings.LOAD_NO_CACHE else WebSettings.LOAD_DEFAULT

        val cookieMgr = CookieManager.getInstance()
        cookieMgr.setAcceptCookie(!isPrivate)
        cookieMgr.setAcceptThirdPartyCookies(wv, !isPrivate && s.allowThirdPartyCookies)

        // Safe optional scaling only when user explicitly opts below 100% (0 resets to native WebView default)
        val scale = if (tabId == activeTabId) latestScale.value else 100
        wv.setInitialScale(if (scale in 75..95) scale else 0)

        if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.O) {
            when {
                tabId == activeTabId -> {
                    wv.setRendererPriorityPolicy(WebView.RENDERER_PRIORITY_IMPORTANT, false)
                }
                isFrozen -> {
                    wv.setRendererPriorityPolicy(WebView.RENDERER_PRIORITY_WAIVED, true)
                }
                else -> {
                    wv.setRendererPriorityPolicy(WebView.RENDERER_PRIORITY_BOUND, true)
                }
            }
        }

        if (isFrozen) {
            wv.onPause()
        } else if (tabId == activeTabId) {
            wv.onResume()
        }
    }

    @SuppressLint("SetJavaScriptEnabled")
    fun createWebView(tabId: String): LowLatencyPulseWebView {
        return LowLatencyPulseWebView(context).apply {
            layoutParams = FrameLayout.LayoutParams(
                ViewGroup.LayoutParams.MATCH_PARENT,
                ViewGroup.LayoutParams.MATCH_PARENT
            )
            setBackgroundColor(android.graphics.Color.parseColor("#0B0F19"))
            isFocusable = true
            isFocusableInTouchMode = true

            this.settings.apply {
                javaScriptEnabled = true
                domStorageEnabled = true // Enables localStorage & IndexedDB for MPQ/Wasm caching
                @Suppress("DEPRECATION")
                databaseEnabled = true
                mediaPlaybackRequiresUserGesture = false // Smooth game audio playback
                useWideViewPort = true
                loadWithOverviewMode = true
                builtInZoomControls = true
                displayZoomControls = false
                javaScriptCanOpenWindowsAutomatically = false
                setSupportMultipleWindows(false)
                cacheMode = WebSettings.LOAD_DEFAULT

                // Preserve strict web security boundaries
                allowFileAccess = false
                allowContentAccess = false
                @Suppress("DEPRECATION")
                allowFileAccessFromFileURLs = false
                @Suppress("DEPRECATION")
                allowUniversalAccessFromFileURLs = false
                mixedContentMode = WebSettings.MIXED_CONTENT_NEVER_ALLOW
                if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.O) {
                    safeBrowsingEnabled = true
                }
            }

            // Apply standalone Chrome identity immediately on creation before any request fires
            val initialTab = latestTabs.value.find { it.id == tabId }
            val initialSettings = latestSettings.value
            BrowserIdentityCompat.applySettings(
                context = context,
                webSettings = this.settings,
                desktopMode = initialTab?.isDesktopUa == true,
                standaloneChromeCompat = initialSettings.standaloneChromeCompat
            )
            if (initialSettings.standaloneChromeCompat) {
                compatScriptHandler = BrowserIdentityCompat.attachDocumentStartCompatScript(this)
            }

            setDownloadListener { url, userAgent, contentDisposition, mimeType, contentLength ->
                latestDownload.value(tabId, url, userAgent, contentDisposition, mimeType, contentLength)
            }

            webViewClient = object : WebViewClient() {
                // Intentionally do NOT override shouldInterceptRequest to alter/block game traffic:
                // preserves HTTP Range requests, Content-Type: application/wasm, COOP/COEP, and MPQ streams.

                override fun shouldOverrideUrlLoading(
                    view: WebView?,
                    request: WebResourceRequest?
                ): Boolean {
                    val uri = request?.url ?: return true
                    return !UrlUtils.isAllowedNavigationUri(uri)
                }

                override fun onPageStarted(view: WebView?, url: String?, favicon: Bitmap?) {
                    if (!url.isNullOrBlank()) {
                        latestStarted.value(tabId, url)
                    }
                }

                override fun onPageFinished(view: WebView?, url: String?) {
                    if (!url.isNullOrBlank() && view != null) {
                        val isPrivate = latestTabs.value.find { it.id == tabId }?.isPrivate == true
                        if (!isPrivate) {
                            CookieManager.getInstance().flush()
                        }
                        latestFinished.value(tabId, url, view.canGoBack(), view.canGoForward())
                    }
                }

                override fun onReceivedHttpError(
                    view: WebView?,
                    request: WebResourceRequest?,
                    errorResponse: WebResourceResponse?
                ) {
                    val url = request?.url?.toString().orEmpty()
                    val code = errorResponse?.statusCode ?: 0
                    val reason = errorResponse?.reasonPhrase ?: "HTTP Error"
                    val mime = errorResponse?.mimeType.orEmpty()
                    diagnosticsCollector.recordHttpError(url, code, reason, mime)
                }

                override fun onReceivedError(
                    view: WebView?,
                    request: WebResourceRequest?,
                    error: WebResourceError?
                ) {
                    val url = request?.url?.toString().orEmpty()
                    val code = error?.errorCode ?: -1
                    val desc = error?.description?.toString() ?: "Network error"
                    diagnosticsCollector.recordNetworkError(url, code, desc)
                }

                override fun onReceivedSslError(
                    view: WebView?,
                    handler: SslErrorHandler?,
                    error: SslError?
                ) {
                    // Never bypass TLS certificate validation
                    handler?.cancel()
                    diagnosticsCollector.recordSslError(
                        url = error?.url.orEmpty(),
                        primaryError = error?.primaryError ?: -1
                    )
                }

                override fun onRenderProcessGone(
                    view: WebView?,
                    detail: RenderProcessGoneDetail?
                ): Boolean {
                    val didCrash = if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.O) {
                        detail?.didCrash() ?: false
                    } else {
                        false
                    }
                    val priority = if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.O) {
                        detail?.rendererPriorityAtExit() ?: -1
                    } else {
                        -1
                    }

                    // Detach and destroy the dead WebView cleanly so the app stays alive
                    pool.remove(tabId)?.let { dead ->
                        (dead.parent as? ViewGroup)?.removeView(dead)
                        dead.destroy()
                    }
                    latestCrash.value(tabId, didCrash, priority)
                    return true
                }
            }

            webChromeClient = object : WebChromeClient() {
                override fun onProgressChanged(view: WebView?, newProgress: Int) {
                    if (view != null) {
                        latestProgress.value(tabId, newProgress, view.canGoBack(), view.canGoForward())
                    }
                }

                override fun onReceivedTitle(view: WebView?, title: String?) {
                    latestTitle.value(tabId, title)
                }

                override fun onConsoleMessage(consoleMessage: ConsoleMessage?): Boolean {
                    if (consoleMessage != null) {
                        diagnosticsCollector.recordConsoleMessage(
                            level = consoleMessage.messageLevel().name,
                            message = consoleMessage.message().orEmpty(),
                            sourceId = consoleMessage.sourceId().orEmpty(),
                            lineNumber = consoleMessage.lineNumber()
                        )
                    }
                    return false
                }

                override fun onShowCustomView(view: View?, callback: CustomViewCallback?) {
                    if (view != null && callback != null) {
                        latestEnterFs.value(view, callback)
                    }
                }

                override fun onHideCustomView() {
                    latestExitFs.value()
                }
            }
        }
    }

    fun getOrCreate(tabId: String): LowLatencyPulseWebView {
        return pool.getOrPut(tabId) { createWebView(tabId) }
    }

    LaunchedEffect(commands) {
        commands.collect { cmd ->
            when (cmd) {
                is PulseWebCommand.LoadUrl -> {
                    val wv = getOrCreate(cmd.tabId)
                    configureWebView(wv, cmd.tabId)
                    wv.loadUrl(cmd.url)
                }
                is PulseWebCommand.GoBack -> pool[cmd.tabId]?.goBack()
                is PulseWebCommand.GoForward -> pool[cmd.tabId]?.goForward()
                is PulseWebCommand.Reload -> {
                    pool[cmd.tabId]?.let { wv ->
                        configureWebView(wv, cmd.tabId)
                        wv.reload()
                    }
                }
                is PulseWebCommand.StopLoading -> pool[cmd.tabId]?.stopLoading()
                is PulseWebCommand.RecreateAndReload -> {
                    pool.remove(cmd.tabId)?.let { old ->
                        (old.parent as? ViewGroup)?.removeView(old)
                        old.stopLoading()
                        old.destroy()
                    }
                    val fresh = getOrCreate(cmd.tabId)
                    configureWebView(fresh, cmd.tabId)
                    if (cmd.tabId == activeTabId) {
                        container.removeAllViews()
                        container.addView(fresh)
                    }
                    if (!UrlUtils.isInternal(cmd.url)) {
                        fresh.loadUrl(cmd.url)
                    }
                }
                is PulseWebCommand.ProbeActiveWebGl -> {
                    pool[cmd.tabId]?.evaluateJavascript(
                        DiagnosticsCollector.READ_ONLY_WEBGL_WASM_PROBE_JS
                    ) { json ->
                        latestProbeResult.value(json)
                    }
                }
                is PulseWebCommand.CloseTab -> {
                    pool.remove(cmd.tabId)?.let { old ->
                        (old.parent as? ViewGroup)?.removeView(old)
                        if (cmd.wasPrivate) {
                            old.clearCache(true)
                            old.clearFormData()
                            old.clearHistory()
                        }
                        old.stopLoading()
                        old.destroy()
                    }
                }
                is PulseWebCommand.ClearAllCaches -> {
                    pool.values.firstOrNull()?.clearCache(true)
                }
            }
        }
    }

    LaunchedEffect(activeTabId, gamingModeActive, effectiveRenderScale, pulseSettings, tabs) {
        pool.forEach { (id, wv) -> configureWebView(wv, id) }
    }

    DisposableEffect(Unit) {
        onDispose {
            CookieManager.getInstance().flush()
            pool.values.forEach { wv ->
                (wv.parent as? ViewGroup)?.removeView(wv)
                wv.stopLoading()
                wv.destroy()
            }
            pool.clear()
        }
    }

    AndroidView(
        factory = { container },
        update = { host ->
            val tabMeta = tabs.find { it.id == activeTabId }
            if (tabMeta?.rendererCrashed == true) {
                host.removeAllViews()
            } else {
                val activeWv = getOrCreate(activeTabId)
                configureWebView(activeWv, activeTabId)
                if (host.childCount != 1 || host.getChildAt(0) !== activeWv) {
                    host.removeAllViews()
                    (activeWv.parent as? ViewGroup)?.removeView(activeWv)
                    host.addView(activeWv)
                }
            }
        },
        modifier = modifier
    )
}
