package com.example.viewmodel

import com.example.data.BookmarkEntity
import com.example.data.BrowserSettings
import com.example.data.SiteGamingProfileEntity
import com.example.engine.HardwareGpuReport
import com.example.engine.PerformanceTelemetry
import com.example.engine.ShieldStats
import com.example.engine.UrlSecurityHelper
import java.util.UUID

enum class AppDestination {
    BROWSER,
    GPU_LAB,
    SHIELD_PROFILES
}

enum class GamingPreset(val label: String, val subtitle: String) {
    BALANCED("Balanced", "100% DPR • High-Perf WebGL • Shield ON"),
    COMPETITIVE("Max FPS", "75% DPR • No MSAA • 120Hz Lock • Freeze Tabs"),
    ENDURANCE("Cool Sustain", "85% DPR • Sustained Perf • Thermal Guard")
}

data class BrowserTab(
    val id: String = UUID.randomUUID().toString(),
    val title: String = "Bare Turbo Start",
    val url: String = UrlSecurityHelper.INTERNAL_HOME,
    val progress: Int = 100,
    val isLoading: Boolean = false,
    val canGoBack: Boolean = false,
    val canGoForward: Boolean = false,
    val isFrozen: Boolean = false,
    val blockedCount: Int = 0
)

sealed interface WebViewCommand {
    data class LoadUrl(val tabId: String, val url: String) : WebViewCommand
    data class LoadHtml(val tabId: String, val baseUrl: String, val html: String) : WebViewCommand
    data class GoBack(val tabId: String) : WebViewCommand
    data class GoForward(val tabId: String) : WebViewCommand
    data class Reload(val tabId: String) : WebViewCommand
    data class StopLoading(val tabId: String) : WebViewCommand
    data class InjectOptimization(val tabId: String, val script: String) : WebViewCommand
    data class CloseTab(val tabId: String) : WebViewCommand
}

data class BrowserUiState(
    val destination: AppDestination = AppDestination.BROWSER,
    val tabs: List<BrowserTab> = listOf(BrowserTab()),
    val activeTabIndex: Int = 0,
    val gamingModeActive: Boolean = false,
    val hudVisible: Boolean = true,
    val activePreset: GamingPreset = GamingPreset.BALANCED,
    val isTabsDrawerOpen: Boolean = false,
    val isTabGridView: Boolean = false,
    val settings: BrowserSettings = BrowserSettings(),
    val telemetry: PerformanceTelemetry = PerformanceTelemetry(),
    val gpuReport: HardwareGpuReport = HardwareGpuReport(),
    val shieldStats: ShieldStats = ShieldStats(),
    val bookmarks: List<BookmarkEntity> = emptyList(),
    val siteProfiles: List<SiteGamingProfileEntity> = emptyList(),
    val activeSiteProfile: SiteGamingProfileEntity? = null,
    val statusBannerMessage: String? = null
) {
    val activeTab: BrowserTab
        get() = tabs.getOrElse(activeTabIndex) { tabs.first() }

    val isCurrentUrlBookmarked: Boolean
        get() = bookmarks.any { it.url.equals(activeTab.url, ignoreCase = true) }

    val frozenTabCount: Int
        get() = tabs.count { it.isFrozen }
}
