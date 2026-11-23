package com.example.viewmodel

import android.app.Application
import androidx.lifecycle.AndroidViewModel
import androidx.lifecycle.viewModelScope
import com.example.data.BareDatabase
import com.example.data.BrowserPreferences
import com.example.data.BrowserRepository
import com.example.data.BrowserSettings
import com.example.data.SiteGamingProfileEntity
import com.example.engine.BareBenchmarkHtml
import com.example.engine.GamingOptimizationScripts
import com.example.engine.HardwareGpuInspector
import com.example.engine.NetworkShieldEngine
import com.example.engine.TelemetryMonitor
import com.example.engine.UrlSecurityHelper
import kotlinx.coroutines.delay
import kotlinx.coroutines.flow.MutableSharedFlow
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.SharedFlow
import kotlinx.coroutines.flow.StateFlow
import kotlinx.coroutines.flow.asSharedFlow
import kotlinx.coroutines.flow.asStateFlow
import kotlinx.coroutines.flow.update
import kotlinx.coroutines.launch

class BrowserViewModel(application: Application) : AndroidViewModel(application) {

    private val database = BareDatabase.getInstance(application)
    private val preferences = BrowserPreferences(application)
    private val repository = BrowserRepository(
        bookmarkDao = database.bookmarkDao(),
        siteProfileDao = database.siteProfileDao(),
        preferences = preferences
    )

    val shieldEngine = NetworkShieldEngine()
    private val telemetryMonitor = TelemetryMonitor(application)

    private val _uiState = MutableStateFlow(BrowserUiState())
    val uiState: StateFlow<BrowserUiState> = _uiState.asStateFlow()

    private val _webViewCommands = MutableSharedFlow<WebViewCommand>(extraBufferCapacity = 32)
    val webViewCommands: SharedFlow<WebViewCommand> = _webViewCommands.asSharedFlow()

    init {
        telemetryMonitor.start()

        viewModelScope.launch {
            repository.ensureDefaultData()
        }

        viewModelScope.launch {
            val report = HardwareGpuInspector.inspect(application)
            _uiState.update { it.copy(gpuReport = report) }
        }

        viewModelScope.launch {
            telemetryMonitor.telemetry.collect { sample ->
                _uiState.update { it.copy(telemetry = sample) }
            }
        }

        viewModelScope.launch {
            shieldEngine.stats.collect { shield ->
                _uiState.update { state ->
                    val updatedTabs = state.tabs.map { tab ->
                        tab.copy(blockedCount = shieldEngine.getTabBlockedCount(tab.id))
                    }
                    state.copy(shieldStats = shield, tabs = updatedTabs)
                }
            }
        }

        viewModelScope.launch {
            repository.settingsFlow.collect { settings ->
                _uiState.update { state ->
                    val nextTabs = applyBackgroundFreezeState(
                        tabs = state.tabs,
                        activeIndex = state.activeTabIndex,
                        shouldFreeze = state.gamingModeActive && settings.freezeBackgroundTabs
                    )
                    state.copy(
                        settings = settings,
                        hudVisible = if (state.gamingModeActive) settings.showHudInGamingMode else state.hudVisible,
                        tabs = nextTabs
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
            repository.siteProfilesFlow.collect { profiles ->
                _uiState.update { state ->
                    val currentHost = UrlSecurityHelper.extractHost(state.activeTab.url)
                    val match = profiles.find { it.host.equals(currentHost, ignoreCase = true) }
                    state.copy(siteProfiles = profiles, activeSiteProfile = match)
                }
            }
        }
    }

    override fun onCleared() {
        telemetryMonitor.stop()
        super.onCleared()
    }

    fun navigateToDestination(destination: AppDestination) {
        _uiState.update { it.copy(destination = destination, isTabsDrawerOpen = false) }
    }

    fun openTabsDrawer(open: Boolean) {
        _uiState.update { it.copy(isTabsDrawerOpen = open) }
    }

    fun toggleTabGridView() {
        _uiState.update { it.copy(isTabGridView = !it.isTabGridView) }
    }

    fun navigateAddress(rawInput: String) {
        val httpsOnly = _uiState.value.settings.httpsOnly
        val targetUrl = UrlSecurityHelper.normalizeUserInput(rawInput, httpsOnly)
        navigateActiveTabToUrl(targetUrl)
    }

    fun navigateActiveTabToUrl(url: String) {
        when (url.lowercase()) {
            UrlSecurityHelper.INTERNAL_GPU, UrlSecurityHelper.INTERNAL_FLAGS -> {
                _uiState.update { it.copy(destination = AppDestination.GPU_LAB, isTabsDrawerOpen = false) }
                return
            }
            UrlSecurityHelper.INTERNAL_HOME -> {
                val tabId = _uiState.value.activeTab.id
                updateTab(tabId) {
                    it.copy(
                        url = UrlSecurityHelper.INTERNAL_HOME,
                        title = "Bare Turbo Start",
                        isLoading = false,
                        progress = 100
                    )
                }
                _uiState.update { it.copy(destination = AppDestination.BROWSER, activeSiteProfile = null) }
                return
            }
            UrlSecurityHelper.INTERNAL_BENCHMARK -> {
                val tabId = _uiState.value.activeTab.id
                updateTab(tabId) {
                    it.copy(
                        url = UrlSecurityHelper.INTERNAL_BENCHMARK,
                        title = "WebGL2 & Latency Lab",
                        isLoading = true,
                        progress = 20
                    )
                }
                _uiState.update { it.copy(destination = AppDestination.BROWSER) }
                _webViewCommands.tryEmit(
                    WebViewCommand.LoadHtml(
                        tabId = tabId,
                        baseUrl = "https://bare.local/benchmark",
                        html = BareBenchmarkHtml.buildBenchmarkPage()
                    )
                )
                if (_uiState.value.settings.autoGamingMode && !_uiState.value.gamingModeActive) {
                    setGamingMode(true, "Auto-enabled Gaming Mode for WebGL2 Lab")
                }
                return
            }
        }

        val tabId = _uiState.value.activeTab.id
        updateTab(tabId) {
            it.copy(
                url = url,
                title = UrlSecurityHelper.extractHost(url) ?: url,
                isLoading = true,
                progress = 15
            )
        }
        _uiState.update { it.copy(destination = AppDestination.BROWSER) }
        _webViewCommands.tryEmit(WebViewCommand.LoadUrl(tabId, url))

        // Check if this host has a saved per-site Gaming Mode profile
        checkAndApplySiteProfile(url)
    }

    private fun checkAndApplySiteProfile(url: String) {
        val host = UrlSecurityHelper.extractHost(url) ?: return
        viewModelScope.launch {
            val profile = repository.getSiteProfile(host)
            _uiState.update { it.copy(activeSiteProfile = profile) }
            val state = _uiState.value
            if (profile != null && profile.autoGamingMode && state.settings.autoGamingMode && !state.gamingModeActive) {
                setGamingMode(true, "Gaming Profile applied for $host (${profile.renderScalePercent}% scale)")
            }
        }
    }

    fun openNewTab(initialUrl: String = UrlSecurityHelper.INTERNAL_HOME) {
        val newTab = BrowserTab(
            url = initialUrl,
            title = if (initialUrl == UrlSecurityHelper.INTERNAL_HOME) "Bare Turbo Start" else initialUrl
        )
        _uiState.update { state ->
            val updated = state.tabs + newTab
            val newIndex = updated.lastIndex
            state.copy(
                tabs = applyBackgroundFreezeState(
                    tabs = updated,
                    activeIndex = newIndex,
                    shouldFreeze = state.gamingModeActive && state.settings.freezeBackgroundTabs
                ),
                activeTabIndex = newIndex,
                isTabsDrawerOpen = false,
                destination = AppDestination.BROWSER
            )
        }
        if (initialUrl != UrlSecurityHelper.INTERNAL_HOME) {
            navigateActiveTabToUrl(initialUrl)
        }
    }

    fun selectTab(index: Int) {
        _uiState.update { state ->
            val clamped = index.coerceIn(0, state.tabs.lastIndex)
            val nextTabs = applyBackgroundFreezeState(
                tabs = state.tabs,
                activeIndex = clamped,
                shouldFreeze = state.gamingModeActive && state.settings.freezeBackgroundTabs
            )
            val selectedUrl = nextTabs[clamped].url
            val host = UrlSecurityHelper.extractHost(selectedUrl)
            val profile = state.siteProfiles.find { it.host.equals(host, ignoreCase = true) }
            state.copy(
                tabs = nextTabs,
                activeTabIndex = clamped,
                isTabsDrawerOpen = false,
                destination = AppDestination.BROWSER,
                activeSiteProfile = profile
            )
        }
    }

    fun closeTab(tabId: String) {
        shieldEngine.clearTabStats(tabId)
        _webViewCommands.tryEmit(WebViewCommand.CloseTab(tabId))
        _uiState.update { state ->
            val remaining = state.tabs.filterNot { it.id == tabId }
            if (remaining.isEmpty()) {
                val fresh = BrowserTab()
                state.copy(tabs = listOf(fresh), activeTabIndex = 0, activeSiteProfile = null)
            } else {
                val nextIndex = state.activeTabIndex.coerceAtMost(remaining.lastIndex)
                val nextTabs = applyBackgroundFreezeState(
                    tabs = remaining,
                    activeIndex = nextIndex,
                    shouldFreeze = state.gamingModeActive && state.settings.freezeBackgroundTabs
                )
                state.copy(tabs = nextTabs, activeTabIndex = nextIndex)
            }
        }
    }

    fun toggleGamingMode() {
        val next = !_uiState.value.gamingModeActive
        setGamingMode(
            enabled = next,
            reason = if (next) "Gaming Mode ON • Max Hz, Unbuffered Touch & Tab Freeze" else "Gaming Mode OFF"
        )
    }

    fun setGamingMode(enabled: Boolean, reason: String? = null) {
        _uiState.update { state ->
            val nextTabs = applyBackgroundFreezeState(
                tabs = state.tabs,
                activeIndex = state.activeTabIndex,
                shouldFreeze = enabled && state.settings.freezeBackgroundTabs
            )
            state.copy(
                gamingModeActive = enabled,
                hudVisible = if (enabled) state.settings.showHudInGamingMode else false,
                tabs = nextTabs
            )
        }
        injectCurrentOptimizerIntoActiveTab()
        if (reason != null) showStatusMessage(reason)
    }

    fun toggleHudVisibility() {
        _uiState.update { it.copy(hudVisible = !it.hudVisible) }
    }

    fun applyGamingPreset(preset: GamingPreset) {
        _uiState.update { it.copy(activePreset = preset) }
        viewModelScope.launch {
            repository.updateSettings { s ->
                when (preset) {
                    GamingPreset.BALANCED -> s.copy(
                        renderScalePercent = 100,
                        webGlHighPerformance = true,
                        webGlDisableAntialias = false,
                        lockMaxRefreshRate = true,
                        unbufferedTouchInput = true,
                        sustainedPerformanceMode = false,
                        freezeBackgroundTabs = true,
                        shieldEnabled = true
                    )
                    GamingPreset.COMPETITIVE -> s.copy(
                        renderScalePercent = 75,
                        webGlHighPerformance = true,
                        webGlDisableAntialias = true,
                        lockMaxRefreshRate = true,
                        unbufferedTouchInput = true,
                        sustainedPerformanceMode = false,
                        freezeBackgroundTabs = true,
                        shieldEnabled = true
                    )
                    GamingPreset.ENDURANCE -> s.copy(
                        renderScalePercent = 85,
                        webGlHighPerformance = true,
                        webGlDisableAntialias = false,
                        lockMaxRefreshRate = true,
                        unbufferedTouchInput = true,
                        sustainedPerformanceMode = true,
                        freezeBackgroundTabs = true,
                        shieldEnabled = true
                    )
                }
            }
            injectCurrentOptimizerIntoActiveTab()
            showStatusMessage("Preset applied: ${preset.label}")
        }
    }

    fun updateSettings(transform: (BrowserSettings) -> BrowserSettings) {
        viewModelScope.launch {
            repository.updateSettings(transform)
            injectCurrentOptimizerIntoActiveTab()
        }
    }

    fun goBack() {
        val active = _uiState.value.activeTab
        if (active.url == UrlSecurityHelper.INTERNAL_HOME) return
        if (active.canGoBack) {
            _webViewCommands.tryEmit(WebViewCommand.GoBack(active.id))
        } else {
            navigateActiveTabToUrl(UrlSecurityHelper.INTERNAL_HOME)
        }
    }

    fun goForward() {
        val active = _uiState.value.activeTab
        if (active.canGoForward) {
            _webViewCommands.tryEmit(WebViewCommand.GoForward(active.id))
        }
    }

    fun reloadOrStop() {
        val active = _uiState.value.activeTab
        if (active.url == UrlSecurityHelper.INTERNAL_HOME) return
        if (active.url == UrlSecurityHelper.INTERNAL_BENCHMARK) {
            navigateActiveTabToUrl(UrlSecurityHelper.INTERNAL_BENCHMARK)
            return
        }
        if (active.isLoading) {
            _webViewCommands.tryEmit(WebViewCommand.StopLoading(active.id))
        } else {
            _webViewCommands.tryEmit(WebViewCommand.Reload(active.id))
        }
    }

    fun onWebPageStarted(tabId: String, url: String) {
        val displayUrl = if (url == "https://bare.local/benchmark") UrlSecurityHelper.INTERNAL_BENCHMARK else url
        updateTab(tabId) {
            it.copy(url = displayUrl, isLoading = true, progress = 15)
        }
        if (tabId == _uiState.value.activeTab.id) {
            checkAndApplySiteProfile(displayUrl)
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
        updateTab(tabId) { it.copy(title = title.take(72)) }
    }

    fun onWebPageFinished(tabId: String, url: String, canGoBack: Boolean, canGoForward: Boolean) {
        val displayUrl = if (url == "https://bare.local/benchmark") UrlSecurityHelper.INTERNAL_BENCHMARK else url
        updateTab(tabId) {
            it.copy(
                url = displayUrl,
                isLoading = false,
                progress = 100,
                canGoBack = canGoBack,
                canGoForward = canGoForward
            )
        }
        val script = buildEffectiveOptimizerScript()
        _webViewCommands.tryEmit(WebViewCommand.InjectOptimization(tabId, script))
    }

    fun toggleBookmarkForActiveTab() {
        val tab = _uiState.value.activeTab
        if (tab.url == UrlSecurityHelper.INTERNAL_HOME) return
        viewModelScope.launch {
            val added = repository.toggleBookmark(
                title = tab.title,
                url = tab.url,
                isGamingPortal = _uiState.value.gamingModeActive
            )
            showStatusMessage(if (added) "Saved to local bookmarks" else "Removed bookmark")
        }
    }

    fun addManualBookmark(title: String, url: String, isGamingPortal: Boolean) {
        viewModelScope.launch {
            repository.addBookmark(title, url, isGamingPortal)
            showStatusMessage("Bookmark added")
        }
    }

    fun deleteBookmark(id: Long) {
        viewModelScope.launch { repository.deleteBookmark(id) }
    }

    fun saveSiteProfileForHost(
        host: String,
        autoGamingMode: Boolean,
        renderScalePercent: Int,
        disableAntialias: Boolean
    ) {
        val cleanHost = host.trim().lowercase().removePrefix("https://").removePrefix("http://").substringBefore("/")
        if (cleanHost.isEmpty()) return
        viewModelScope.launch {
            repository.saveSiteProfile(
                SiteGamingProfileEntity(
                    host = cleanHost,
                    autoGamingMode = autoGamingMode,
                    renderScalePercent = renderScalePercent.coerceIn(70, 100),
                    forceWebGlHighPerf = true,
                    disableAntialias = disableAntialias,
                    aggressiveShield = true
                )
            )
            showStatusMessage("Saved gaming profile for $cleanHost")
        }
    }

    fun deleteSiteProfile(host: String) {
        viewModelScope.launch { repository.deleteSiteProfile(host) }
    }

    fun resetShieldCounters() {
        shieldEngine.resetSessionStats()
        showStatusMessage("Shield session counters reset")
    }

    fun refreshHardwareProbe() {
        viewModelScope.launch {
            val report = HardwareGpuInspector.inspect(getApplication())
            _uiState.update { it.copy(gpuReport = report) }
            showStatusMessage("Hardware GPU & display probe updated")
        }
    }

    fun buildEffectiveOptimizerScript(): String {
        val state = _uiState.value
        val profile = state.activeSiteProfile
        val effectiveScale = if (state.gamingModeActive && profile != null) {
            profile.renderScalePercent
        } else {
            state.settings.renderScalePercent
        }
        val effectiveDisableAa = if (state.gamingModeActive && profile != null) {
            profile.disableAntialias || state.settings.webGlDisableAntialias
        } else {
            state.settings.webGlDisableAntialias
        }
        return GamingOptimizationScripts.buildEngineOptimizerScript(
            gamingModeActive = state.gamingModeActive,
            webGlHighPerformance = state.settings.webGlHighPerformance,
            disableAntialias = effectiveDisableAa,
            renderScalePercent = effectiveScale
        )
    }

    private fun injectCurrentOptimizerIntoActiveTab() {
        val tab = _uiState.value.activeTab
        if (tab.url != UrlSecurityHelper.INTERNAL_HOME) {
            _webViewCommands.tryEmit(
                WebViewCommand.InjectOptimization(tab.id, buildEffectiveOptimizerScript())
            )
        }
    }

    private fun updateTab(tabId: String, transform: (BrowserTab) -> BrowserTab) {
        _uiState.update { state ->
            state.copy(tabs = state.tabs.map { if (it.id == tabId) transform(it) else it })
        }
    }

    private fun applyBackgroundFreezeState(
        tabs: List<BrowserTab>,
        activeIndex: Int,
        shouldFreeze: Boolean
    ): List<BrowserTab> {
        return tabs.mapIndexed { idx, tab ->
            tab.copy(isFrozen = shouldFreeze && idx != activeIndex && tab.url != UrlSecurityHelper.INTERNAL_HOME)
        }
    }

    private fun showStatusMessage(message: String) {
        _uiState.update { it.copy(statusBannerMessage = message) }
        viewModelScope.launch {
            delay(2800)
            _uiState.update { current ->
                if (current.statusBannerMessage == message) current.copy(statusBannerMessage = null) else current
            }
        }
    }
}
