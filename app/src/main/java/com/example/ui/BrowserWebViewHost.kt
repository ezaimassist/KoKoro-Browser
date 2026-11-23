package com.example.ui

import android.annotation.SuppressLint
import android.content.Context
import android.graphics.Bitmap
import android.os.Build
import android.view.MotionEvent
import android.view.View
import android.view.ViewGroup
import android.webkit.CookieManager
import android.webkit.WebChromeClient
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
import com.example.data.BrowserSettings
import com.example.engine.NetworkShieldEngine
import com.example.engine.UrlSecurityHelper
import com.example.viewmodel.BrowserTab
import com.example.viewmodel.WebViewCommand
import kotlinx.coroutines.flow.SharedFlow

/**
 * Custom WebView subclass enabling Android's unbuffered touch dispatch
 * to bypass VSync input coalescing and minimize touch-to-photon latency for games.
 */
class LowLatencyWebView(context: Context) : WebView(context) {
    @Volatile
    var unbufferedTouchEnabled: Boolean = true

    override fun dispatchTouchEvent(event: MotionEvent): Boolean {
        if (unbufferedTouchEnabled && event.actionMasked == MotionEvent.ACTION_DOWN) {
            requestUnbufferedDispatch(event)
        }
        return super.dispatchTouchEvent(event)
    }
}

@Composable
fun BrowserWebViewHost(
    tabs: List<BrowserTab>,
    activeTabId: String,
    gamingModeActive: Boolean,
    settings: BrowserSettings,
    shieldEngine: NetworkShieldEngine,
    commands: SharedFlow<WebViewCommand>,
    onPageStarted: (tabId: String, url: String) -> Unit,
    onPageProgress: (tabId: String, progress: Int, canGoBack: Boolean, canGoForward: Boolean) -> Unit,
    onPageTitle: (tabId: String, title: String?) -> Unit,
    onPageFinished: (tabId: String, url: String, canGoBack: Boolean, canGoForward: Boolean) -> Unit,
    modifier: Modifier = Modifier
) {
    val context = LocalContext.current
    val webViewPool = remember { LinkedHashMap<String, LowLatencyWebView>() }
    val container = remember { FrameLayout(context) }

    val latestGamingMode = rememberUpdatedState(gamingModeActive)
    val latestSettings = rememberUpdatedState(settings)
    val latestOnStarted = rememberUpdatedState(onPageStarted)
    val latestOnProgress = rememberUpdatedState(onPageProgress)
    val latestOnTitle = rememberUpdatedState(onPageTitle)
    val latestOnFinished = rememberUpdatedState(onPageFinished)

    fun configureWebView(wv: LowLatencyWebView, tabId: String) {
        val currentSettings = latestSettings.value
        wv.unbufferedTouchEnabled = currentSettings.unbufferedTouchInput || latestGamingMode.value
        wv.setLayerType(
            if (currentSettings.hardwareCompositing) View.LAYER_TYPE_HARDWARE else View.LAYER_TYPE_NONE,
            null
        )

        val ws = wv.settings
        val targetUa = if (currentSettings.desktopUserAgent) {
            "Mozilla/5.0 (X11; Linux x86_64) AppleWebKit/537.36 (KHTML, like Gecko) Chrome/131.0.0.0 Safari/537.36"
        } else {
            WebSettings.getDefaultUserAgent(context)
        }
        if (ws.userAgentString != targetUa) {
            ws.userAgentString = targetUa
        }

        // Apply active vs frozen background renderer priority (API 26+)
        val tabMeta = tabs.find { it.id == tabId }
        val isFrozen = tabMeta?.isFrozen == true && tabId != activeTabId
        if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.O) {
            if (tabId == activeTabId) {
                wv.setRendererPriorityPolicy(WebView.RENDERER_PRIORITY_IMPORTANT, false)
            } else if (isFrozen) {
                wv.setRendererPriorityPolicy(WebView.RENDERER_PRIORITY_WAIVED, true)
            } else {
                wv.setRendererPriorityPolicy(WebView.RENDERER_PRIORITY_BOUND, true)
            }
        }
        if (isFrozen) {
            wv.onPause()
        } else if (tabId == activeTabId) {
            wv.onResume()
        }
    }

    @SuppressLint("SetJavaScriptEnabled")
    fun getOrCreateWebView(tabId: String): LowLatencyWebView {
        return webViewPool.getOrPut(tabId) {
            LowLatencyWebView(context).apply {
                layoutParams = FrameLayout.LayoutParams(
                    ViewGroup.LayoutParams.MATCH_PARENT,
                    ViewGroup.LayoutParams.MATCH_PARENT
                )
                setBackgroundColor(android.graphics.Color.parseColor("#0A0C10"))
                isFocusable = true
                isFocusableInTouchMode = true

                this.settings.apply {
                    javaScriptEnabled = true
                    domStorageEnabled = true
                    mediaPlaybackRequiresUserGesture = false
                    useWideViewPort = true
                    loadWithOverviewMode = true
                    builtInZoomControls = true
                    displayZoomControls = false
                    cacheMode = WebSettings.LOAD_DEFAULT

                    // Hardened WebView security boundaries
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

                CookieManager.getInstance().setAcceptCookie(true)
                CookieManager.getInstance().setAcceptThirdPartyCookies(this, false)

                webViewClient = object : WebViewClient() {
                    override fun shouldOverrideUrlLoading(
                        view: WebView?,
                        request: WebResourceRequest?
                    ): Boolean {
                        val uri = request?.url ?: return true
                        // Allow only safe http/https navigations inside WebView
                        return !UrlSecurityHelper.isAllowedNavigationUri(uri)
                    }

                    override fun shouldInterceptRequest(
                        view: WebView?,
                        request: WebResourceRequest?
                    ): WebResourceResponse? {
                        val s = latestSettings.value
                        if (!s.shieldEnabled) return null
                        val uri = request?.url ?: return null
                        val inGaming = latestGamingMode.value
                        if (shieldEngine.shouldBlock(uri, aggressiveGamingFilter = inGaming)) {
                            val host = uri.host ?: "tracker"
                            shieldEngine.recordBlocked(tabId, host, inGaming)
                            return shieldEngine.createEmptyBlockedResponse()
                        }
                        return null
                    }

                    override fun onPageStarted(view: WebView?, url: String?, favicon: Bitmap?) {
                        if (!url.isNullOrBlank()) {
                            latestOnStarted.value(tabId, url)
                        }
                    }

                    override fun onPageFinished(view: WebView?, url: String?) {
                        if (!url.isNullOrBlank() && view != null) {
                            latestOnFinished.value(tabId, url, view.canGoBack(), view.canGoForward())
                        }
                    }
                }

                webChromeClient = object : WebChromeClient() {
                    override fun onProgressChanged(view: WebView?, newProgress: Int) {
                        if (view != null) {
                            latestOnProgress.value(tabId, newProgress, view.canGoBack(), view.canGoForward())
                        }
                    }

                    override fun onReceivedTitle(view: WebView?, title: String?) {
                        latestOnTitle.value(tabId, title)
                    }
                }
            }
        }
    }

    // Listen for navigation, HTML benchmark, JS optimization, and tab close commands
    LaunchedEffect(commands) {
        commands.collect { cmd ->
            when (cmd) {
                is WebViewCommand.LoadUrl -> {
                    val wv = getOrCreateWebView(cmd.tabId)
                    configureWebView(wv, cmd.tabId)
                    wv.loadUrl(cmd.url)
                }
                is WebViewCommand.LoadHtml -> {
                    val wv = getOrCreateWebView(cmd.tabId)
                    configureWebView(wv, cmd.tabId)
                    wv.loadDataWithBaseURL(cmd.baseUrl, cmd.html, "text/html", "utf-8", null)
                }
                is WebViewCommand.GoBack -> webViewPool[cmd.tabId]?.goBack()
                is WebViewCommand.GoForward -> webViewPool[cmd.tabId]?.goForward()
                is WebViewCommand.Reload -> webViewPool[cmd.tabId]?.reload()
                is WebViewCommand.StopLoading -> webViewPool[cmd.tabId]?.stopLoading()
                is WebViewCommand.InjectOptimization -> {
                    webViewPool[cmd.tabId]?.evaluateJavascript(cmd.script, null)
                }
                is WebViewCommand.CloseTab -> {
                    webViewPool.remove(cmd.tabId)?.let { old ->
                        container.removeView(old)
                        old.stopLoading()
                        old.destroy()
                    }
                }
            }
        }
    }

    // Re-apply hardware / freeze / input settings whenever active tab, Gaming Mode, or settings change
    LaunchedEffect(activeTabId, gamingModeActive, settings, tabs) {
        webViewPool.forEach { (id, wv) ->
            configureWebView(wv, id)
        }
    }

    DisposableEffect(Unit) {
        onDispose {
            webViewPool.values.forEach { wv ->
                (wv.parent as? ViewGroup)?.removeView(wv)
                wv.stopLoading()
                wv.destroy()
            }
            webViewPool.clear()
        }
    }

    AndroidView(
        factory = { container },
        update = { host ->
            val activeWv = getOrCreateWebView(activeTabId)
            configureWebView(activeWv, activeTabId)
            if (host.childCount != 1 || host.getChildAt(0) !== activeWv) {
                host.removeAllViews()
                (activeWv.parent as? ViewGroup)?.removeView(activeWv)
                host.addView(activeWv)
            }
        },
        modifier = modifier
    )
}
