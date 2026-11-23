package com.example.viewmodel

import android.app.Application
import android.webkit.CookieManager
import android.webkit.WebStorage
import androidx.lifecycle.AndroidViewModel
import androidx.lifecycle.viewModelScope
import com.example.data.PulseDatabase
import com.example.data.PulsePreferences
import com.example.data.PulseRepository
import com.example.data.PulseSettings
import com.example.data.SiteGamingProfileEntity
import com.example.engine.ActiveWebGlProbeResult
import com.example.engine.DeviceCapabilitiesProbe
import com.example.engine.DiagnosticsCollector
import com.example.engine.PulseDownloadHelper
import com.example.engine.UrlUtils
import kotlinx.coroutines.delay
import kotlinx.coroutines.flow.MutableSharedFlow
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.SharedFlow
import kotlinx.coroutines.flow.StateFlow
import kotlinx.coroutines.flow.asSharedFlow
import kotlinx.coroutines.flow.asStateFlow
import kotlinx.coroutines.flow.update
import kotlinx.coroutines.launch
import org.json.JSONObject

class PulseViewModel(application: Application) : AndroidViewModel(application) {

    private val database = PulseDatabase.getInstance(application)
    private val preferences = PulsePreferences(application)
    private val repository = PulseRepository(
        bookmarkDao = database.bookmarkDao(),
        historyDao = database.historyDao(),
        downloadDao = database.downloadDao(),
        siteProfileDao = database.siteProfileDao(),
        preferences = preferences
    )

    val diagnosticsCollector = DiagnosticsCollector(application)

    private val _uiState = MutableStateFlow(PulseUiState())
    val uiState: StateFlow<PulseUiState> = _uiState.asStateFlow()

    private val _webCommands = MutableSharedFlow<PulseWebCommand>(extraBufferCapacity = 32)
    val webCommands: SharedFlow<PulseWebCommand> = _webCommands.asSharedFlow()

    init {
        diagnosticsCollector.startMonitoring()

        viewModelScope.launch {
            repository.ensureDefaults()
        }

        viewModelScope.launch {
            val report = DeviceCapabilitiesProbe.probe(application)
            _uiState.update { it.copy(deviceCapabilities = report) }
        }

        viewModelScope.launch {
            diagnosticsCollector.state.collect { diag ->
                _uiState.update { it.copy(diagnostics = diag) }
            }
        }

        viewModelScope.launch {
            repository.settingsFlow.collect { settings ->
                _uiState.update { state ->
                    state.copy(
                        settings = settings,
                        tabs = updateFreezeFlags(
                            state.tabs,
                            state.activeTabIndex,
                            state.gamingModeActive && settings.freezeBackgroundTabsInGaming
                        )
                    )
                }
            }
        }

        viewModelScope.launch {
            repository.bookmarksFlow.collect { list ->
                _uiState.update { it.copy(bookmarks = list) }
            }
        }

        viewModelScope.launch {
            repository.historyFlow.collect { list ->
                _uiState.update { it.copy(history = list) }
            }
        }

        viewModelScope.launch {
            repository.downloadsFlow.collect { list ->
                _uiState.update { it.copy(downloads = list) }
            }
        }

        viewModelScope.launch {
            repository.siteProfilesFlow.collect { list ->
                _uiState.update { state ->
                    val host = UrlUtils.extractHost(state.activeTab.url)
                    val matched = list.find { it.host.equals(host, ignoreCase = true) }
                    state.copy(siteProfiles = list, activeSiteProfile = matched)
                }
            }
        }
    }

    override fun onCleared() {
        diagnosticsCollector.stopMonitoring()
        super.onCleared()
    }

    fun navigateTo(destination: PulseDestination) {
        _uiState.update { it.copy(destination = destination, isTabsDrawerOpen = false) }
    }

    fun setTabsDrawerOpen(open: Boolean) {
        _uiState.update { it.copy(isTabsDrawerOpen = open) }
    }

    fun submitAddress(rawInput: String) {
        val settings = _uiState.value.settings
        val normalized = UrlUtils.normalizeUserInput(
            rawInput = rawInput,
            httpsUpgrade = settings.httpsUpgrade,
            searchEngine = settings.searchEngine
        )
        loadUrlInActiveTab(normalized)
    }

    fun launchLastHavens() {
        loadUrlInActiveTab(PulseRepository.LASTHAVENS_PLAY_URL)
    }

    fun loadUrlInActiveTab(url: String) {
        if (UrlUtils.isInternal(url)) {
            val tabId = _uiState.value.activeTab.id
            updateTab(tabId) {
                it.copy(
                    url = UrlUtils.INTERNAL_HOME,
                    title = "PulseBrowser Start",
                    isLoading = false,
                    progress = 100,
                    rendererCrashed = false,
                    crashSummary = null
                )
            }
            _uiState.update { it.copy(destination = PulseDestination.BROWSER, activeSiteProfile = null) }
            return
        }

        val tabId = _uiState.value.activeTab.id
        val now = System.currentTimeMillis()
        updateTab(tabId) {
            it.copy(
                url = url,
                title = UrlUtils.extractHost(url) ?: url,
                isLoading = true,
                progress = 10,
                rendererCrashed = false,
                crashSummary = null,
                loadStartMs = now
            )
        }
        _uiState.update { it.copy(destination = PulseDestination.BROWSER) }
        _webCommands.tryEmit(PulseWebCommand.LoadUrl(tabId, url))
        checkAndApplySiteProfile(url, tabId)
    }

    private fun checkAndApplySiteProfile(url: String, tabId: String) {
        val host = UrlUtils.extractHost(url) ?: return
        viewModelScope.launch {
            val profile = repository.findSiteProfile(host)
            _uiState.update { it.copy(activeSiteProfile = profile) }
            val state = _uiState.value
            if (profile != null && state.settings.autoApplySiteProfiles) {
                if (profile.useDesktopUserAgent && !state.activeTab.isDesktopUa) {
                    updateTab(tabId) { it.copy(isDesktopUa = true) }
                }
                if (profile.autoGamingMode && !state.gamingModeActive) {
                    setGamingMode(
                        enabled = true,
                        hideUi = profile.hideBrowserUi,
                        reason = "Applied Gaming Profile for $host"
                    )
                }
            }
        }
    }

    fun openNewTab(isPrivate: Boolean = false, initialUrl: String = UrlUtils.INTERNAL_HOME) {
        val tab = PulseTab(
            url = initialUrl,
            title = if (isPrivate) "Private Tab" else "PulseBrowser Start",
            isPrivate = isPrivate,
            isDesktopUa = _uiState.value.settings.defaultDesktopUserAgent
        )
        _uiState.update { state ->
            val updated = state.tabs + tab
            val nextIdx = updated.lastIndex
            state.copy(
                tabs = updateFreezeFlags(
                    updated,
                    nextIdx,
                    state.gamingModeActive && state.settings.freezeBackgroundTabsInGaming
                ),
                activeTabIndex = nextIdx,
                isTabsDrawerOpen = false,
                destination = PulseDestination.BROWSER
            )
        }
        if (initialUrl != UrlUtils.INTERNAL_HOME) {
            loadUrlInActiveTab(initialUrl)
        }
    }

    fun selectTab(index: Int) {
        _uiState.update { state ->
            val clamped = index.coerceIn(0, state.tabs.lastIndex)
            val updatedTabs = updateFreezeFlags(
                state.tabs,
                clamped,
                state.gamingModeActive && state.settings.freezeBackgroundTabsInGaming
            )
            val host = UrlUtils.extractHost(updatedTabs[clamped].url)
            val profile = state.siteProfiles.find { it.host.equals(host, ignoreCase = true) }
            state.copy(
                tabs = updatedTabs,
                activeTabIndex = clamped,
                isTabsDrawerOpen = false,
                destination = PulseDestination.BROWSER,
                activeSiteProfile = profile
            )
        }
    }

    fun closeTab(tabId: String) {
        val target = _uiState.value.tabs.find { it.id == tabId }
        _webCommands.tryEmit(PulseWebCommand.CloseTab(tabId, wasPrivate = target?.isPrivate == true))
        _uiState.update { state ->
            val remaining = state.tabs.filterNot { it.id == tabId }
            if (remaining.isEmpty()) {
                val fresh = PulseTab(isDesktopUa = state.settings.defaultDesktopUserAgent)
                state.copy(tabs = listOf(fresh), activeTabIndex = 0, activeSiteProfile = null)
            } else {
                val nextIdx = state.activeTabIndex.coerceAtMost(remaining.lastIndex)
                state.copy(
                    tabs = updateFreezeFlags(
                        remaining,
                        nextIdx,
                        state.gamingModeActive && state.settings.freezeBackgroundTabsInGaming
                    ),
                    activeTabIndex = nextIdx
                )
            }
        }
    }

    fun toggleDesktopUserAgentForActiveTab() {
        val tab = _uiState.value.activeTab
        val nextUa = !tab.isDesktopUa
        updateTab(tab.id) { it.copy(isDesktopUa = nextUa) }
        if (!UrlUtils.isInternal(tab.url)) {
            _webCommands.tryEmit(PulseWebCommand.Reload(tab.id))
        }
        showToast(if (nextUa) "Desktop User-Agent enabled for tab" else "Mobile User-Agent restored")
    }

    fun toggleGamingMode() {
        val next = !_uiState.value.gamingModeActive
        setGamingMode(
            enabled = next,
            hideUi = _uiState.value.settings.hideUiInGamingMode,
            reason = if (next) "Gaming Mode ON" else "Gaming Mode OFF"
        )
    }

    fun setGamingMode(enabled: Boolean, hideUi: Boolean = _uiState.value.settings.hideUiInGamingMode, reason: String? = null) {
        _uiState.update { state ->
            state.copy(
                gamingModeActive = enabled,
                gamingUiHidden = enabled && hideUi,
                gamingHudExpanded = false,
                tabs = updateFreezeFlags(
                    state.tabs,
                    state.activeTabIndex,
                    enabled && state.settings.freezeBackgroundTabsInGaming
                )
            )
        }
        if (reason != null) showToast(reason)
    }

    fun toggleGamingHudExpanded() {
        _uiState.update { it.copy(gamingHudExpanded = !it.gamingHudExpanded) }
    }

    fun toggleGamingUiHidden() {
        _uiState.update { it.copy(gamingUiHidden = !it.gamingUiHidden) }
    }

    fun setHtml5FullscreenActive(active: Boolean) {
        _uiState.update { it.copy(html5FullscreenActive = active) }
    }

    fun cyclePreferredRefreshRate() {
        val supported = _uiState.value.deviceCapabilities.selectableRefreshRatesHz
        val options = listOf(0) + supported
        val current = _uiState.value.settings.preferredRefreshHz
        val nextIdx = (options.indexOf(current) + 1) % options.size
        val nextHz = options[nextIdx]
        updateSettings { it.copy(preferredRefreshHz = nextHz) }
        showToast(if (nextHz == 0) "Display Mode: Auto Max Hz" else "Requested Display Mode: ${nextHz}Hz")
    }

    fun goBack() {
        val tab = _uiState.value.activeTab
        if (UrlUtils.isInternal(tab.url)) return
        if (tab.canGoBack) {
            _webCommands.tryEmit(PulseWebCommand.GoBack(tab.id))
        } else {
            loadUrlInActiveTab(UrlUtils.INTERNAL_HOME)
        }
    }

    fun goForward() {
        val tab = _uiState.value.activeTab
        if (tab.canGoForward) _webCommands.tryEmit(PulseWebCommand.GoForward(tab.id))
    }

    fun reloadOrStop() {
        val tab = _uiState.value.activeTab
        if (UrlUtils.isInternal(tab.url)) return
        if (tab.rendererCrashed) {
            recoverCrashedTab(tab.id)
            return
        }
        if (tab.isLoading) {
            _webCommands.tryEmit(PulseWebCommand.StopLoading(tab.id))
        } else {
            updateTab(tab.id) { it.copy(loadStartMs = System.currentTimeMillis(), isLoading = true) }
            _webCommands.tryEmit(PulseWebCommand.Reload(tab.id))
        }
    }

    fun recoverCrashedTab(tabId: String) {
        val tab = _uiState.value.tabs.find { it.id == tabId } ?: return
        val targetUrl = tab.url.ifBlank { UrlUtils.INTERNAL_HOME }
        updateTab(tabId) {
            it.copy(
                rendererCrashed = false,
                crashSummary = null,
                isLoading = true,
                progress = 10,
                loadStartMs = System.currentTimeMillis()
            )
        }
        _webCommands.tryEmit(PulseWebCommand.RecreateAndReload(tabId, targetUrl))
        showToast("Recreating Chromium renderer process…")
    }

    fun triggerActiveWebGlProbe() {
        val tab = _uiState.value.activeTab
        if (UrlUtils.isInternal(tab.url)) {
            showToast("Open a webpage first to probe live WebGL2/Wasm")
            return
        }
        _webCommands.tryEmit(PulseWebCommand.ProbeActiveWebGl(tab.id))
    }

    fun onWebGlProbeJsonResult(rawJson: String?) {
        if (rawJson.isNullOrBlank() || rawJson == "null") return
        runCatching {
            val cleaned = if (rawJson.startsWith("\"") && rawJson.endsWith("\"")) {
                JSONObject("{\"v\":$rawJson}").getString("v")
            } else {
                rawJson
            }
            val obj = JSONObject(cleaned)
            diagnosticsCollector.updateWebGlProbe(
                ActiveWebGlProbeResult(
                    probedAtMs = System.currentTimeMillis(),
                    webGl2Available = obj.optBoolean("webgl2", false),
                    webAssemblyAvailable = obj.optBoolean("wasm", false),
                    sharedArrayBufferAvailable = obj.optBoolean("sab", false),
                    renderer = obj.optString("renderer", "Unknown"),
                    vendor = obj.optString("vendor", "Unknown"),
                    version = obj.optString("version", "Unknown"),
                    maxTextureSize = obj.optString("maxTex", "—"),
                    devicePixelRatio = obj.optString("dpr", "—"),
                    effectiveUserAgent = obj.optString("ua", "—"),
                    clientHintsBrands = obj.optString("brands", "—"),
                    hasWindowChrome = if (obj.has("hasChrome")) obj.optBoolean("hasChrome", false) else null
                )
            )
        }
    }

    fun onWebPageStarted(tabId: String, url: String) {
        val now = System.currentTimeMillis()
        updateTab(tabId) {
            it.copy(
                url = url,
                isLoading = true,
                progress = 15,
                rendererCrashed = false,
                crashSummary = null,
                loadStartMs = if (it.loadStartMs == 0L) now else it.loadStartMs
            )
        }
        if (tabId == _uiState.value.activeTab.id) {
            checkAndApplySiteProfile(url, tabId)
        }
    }

    fun onWebPageProgress(tabId: String, progress: Int, canGoBack: Boolean, canGoForward: Boolean) {
        updateTab(tabId) {
            it.copy(
                progress = progress,
                isLoading = progress < 100,
                canGoBack = canGoBack,
                canGoForward = canGoForward
            )
        }
    }

    fun onWebPageTitle(tabId: String, title: String?) {
        if (title.isNullOrBlank()) return
        updateTab(tabId) { it.copy(title = title.take(80)) }
    }

    fun onWebPageFinished(tabId: String, url: String, canGoBack: Boolean, canGoForward: Boolean) {
        val tab = _uiState.value.tabs.find { it.id == tabId }
        val started = tab?.loadStartMs?.takeIf { it > 0L } ?: System.currentTimeMillis()
        val duration = (System.currentTimeMillis() - started).coerceAtLeast(0L)
        diagnosticsCollector.recordPageLoadTiming(url, started, duration, completed = true)

        updateTab(tabId) {
            it.copy(
                url = url,
                isLoading = false,
                progress = 100,
                canGoBack = canGoBack,
                canGoForward = canGoForward,
                loadStartMs = 0L,
                lastLoadDurationMs = duration
            )
        }

        // Record history strictly for non-private tabs
        if (tab != null && !tab.isPrivate && !UrlUtils.isInternal(url)) {
            viewModelScope.launch {
                repository.recordHistory(tab.title, url)
            }
        }
    }

    fun onRendererProcessGone(tabId: String, didCrash: Boolean, priorityAtExit: Int) {
        val tab = _uiState.value.tabs.find { it.id == tabId }
        val title = tab?.title ?: "Tab"
        val url = tab?.url ?: ""
        diagnosticsCollector.recordRendererCrash(title, url, didCrash, priorityAtExit)
        val summary = if (didCrash) {
            "Chromium renderer crashed. Tap Reload to recover safely."
        } else {
            "Chromium renderer was terminated by Android (low memory / OOM). Tap Reload to recover."
        }
        updateTab(tabId) {
            it.copy(
                isLoading = false,
                rendererCrashed = true,
                crashSummary = summary
            )
        }
        showToast(if (didCrash) "Renderer crashed — logged in Diagnostics" else "Renderer OOM killed — logged in Diagnostics")
    }

    fun onMemoryTrim(levelName: String, critical: Boolean) {
        diagnosticsCollector.recordMemoryPressure(levelName)
        if (critical) {
            // Proactively freeze background tabs to protect active game tab
            _uiState.update { state ->
                state.copy(
                    tabs = updateFreezeFlags(state.tabs, state.activeTabIndex, shouldFreeze = true)
                )
            }
        }
    }

    fun handleDownloadRequest(
        tabId: String,
        url: String,
        userAgent: String,
        contentDisposition: String?,
        mimeType: String?,
        contentLength: Long
    ) {
        val isPrivate = _uiState.value.tabs.find { it.id == tabId }?.isPrivate == true
        val result = PulseDownloadHelper.enqueueDownload(
            context = getApplication(),
            url = url,
            userAgent = userAgent,
            contentDisposition = contentDisposition,
            mimeType = mimeType,
            isPrivateTab = isPrivate
        )
        viewModelScope.launch {
            repository.recordDownload(
                systemDownloadId = result.systemDownloadId,
                fileName = result.fileName,
                url = url,
                mimeType = mimeType ?: "application/octet-stream",
                contentLength = contentLength,
                status = result.status
            )
        }
        showToast("Download: ${result.fileName} (${result.status})")
    }

    fun toggleBookmarkForActiveTab() {
        val tab = _uiState.value.activeTab
        if (UrlUtils.isInternal(tab.url)) return
        viewModelScope.launch {
            val saved = repository.toggleBookmark(tab.title, tab.url, _uiState.value.gamingModeActive)
            showToast(if (saved) "Bookmarked locally" else "Bookmark removed")
        }
    }

    fun addManualBookmark(title: String, url: String, isGaming: Boolean) {
        viewModelScope.launch {
            val ok = repository.addBookmark(title, url, isGaming)
            showToast(if (ok) "Bookmark saved" else "Enter a valid http/https URL")
        }
    }

    fun deleteBookmark(id: Long) {
        viewModelScope.launch { repository.deleteBookmark(id) }
    }

    fun deleteHistoryItem(id: Long) {
        viewModelScope.launch { repository.deleteHistoryItem(id) }
    }

    fun clearHistory() {
        viewModelScope.launch {
            repository.clearHistory()
            showToast("Browsing history cleared")
        }
    }

    fun deleteDownload(id: Long) {
        viewModelScope.launch { repository.deleteDownload(id) }
    }

    fun clearDownloadsList() {
        viewModelScope.launch {
            repository.clearDownloads()
            showToast("Download records cleared")
        }
    }

    fun saveSiteProfile(profile: SiteGamingProfileEntity) {
        val cleanHost = profile.host.trim().lowercase()
            .removePrefix("https://").removePrefix("http://").substringBefore("/")
        if (cleanHost.isEmpty()) return
        viewModelScope.launch {
            repository.saveSiteProfile(profile.copy(host = cleanHost))
            showToast("Saved gaming profile for $cleanHost")
        }
    }

    fun deleteSiteProfile(host: String) {
        viewModelScope.launch { repository.deleteSiteProfile(host) }
    }

    fun updateSettings(transform: (PulseSettings) -> PulseSettings) {
        viewModelScope.launch { repository.updateSettings(transform) }
    }

    fun clearBrowserCache() {
        _webCommands.tryEmit(PulseWebCommand.ClearAllCaches)
        showToast("WebView HTTP cache cleared")
    }

    fun clearCookiesAndStorage() {
        CookieManager.getInstance().removeAllCookies(null)
        CookieManager.getInstance().flush()
        WebStorage.getInstance().deleteAllData()
        showToast("Cookies & IndexedDB/DOM storage cleared")
    }

    fun buildDiagnosticReportText(): String {
        val s = _uiState.value
        return diagnosticsCollector.buildExportableReportText(
            device = s.deviceCapabilities,
            activeUrl = s.activeTab.url,
            gamingModeActive = s.gamingModeActive
        )
    }

    fun showToast(message: String) {
        _uiState.update { it.copy(toastMessage = message) }
        viewModelScope.launch {
            delay(2800)
            _uiState.update { cur -> if (cur.toastMessage == message) cur.copy(toastMessage = null) else cur }
        }
    }

    private fun updateTab(tabId: String, transform: (PulseTab) -> PulseTab) {
        _uiState.update { state ->
            state.copy(tabs = state.tabs.map { if (it.id == tabId) transform(it) else it })
        }
    }

    private fun updateFreezeFlags(
        tabs: List<PulseTab>,
        activeIndex: Int,
        shouldFreeze: Boolean
    ): List<PulseTab> = tabs.mapIndexed { idx, tab ->
        tab.copy(isFrozen = shouldFreeze && idx != activeIndex && !UrlUtils.isInternal(tab.url))
    }
}
