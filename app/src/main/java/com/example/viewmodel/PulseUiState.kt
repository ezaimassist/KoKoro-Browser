package com.example.viewmodel

import com.example.data.BookmarkEntity
import com.example.data.DownloadEntity
import com.example.data.HistoryEntity
import com.example.data.PulseSettings
import com.example.data.SiteGamingProfileEntity
import com.example.engine.DeviceCapabilitiesReport
import com.example.engine.DiagnosticsState
import com.example.engine.UrlUtils
import java.util.UUID

enum class PulseDestination {
    BROWSER,
    DIAGNOSTICS,
    LIBRARY,
    SETTINGS
}

data class PulseTab(
    val id: String = UUID.randomUUID().toString(),
    val title: String = "PulseBrowser Start",
    val url: String = UrlUtils.INTERNAL_HOME,
    val isPrivate: Boolean = false,
    val isDesktopUa: Boolean = false,
    val progress: Int = 100,
    val isLoading: Boolean = false,
    val canGoBack: Boolean = false,
    val canGoForward: Boolean = false,
    val isFrozen: Boolean = false,
    val rendererCrashed: Boolean = false,
    val crashSummary: String? = null,
    val loadStartMs: Long = 0L,
    val lastLoadDurationMs: Long = 0L
)

sealed interface PulseWebCommand {
    data class LoadUrl(val tabId: String, val url: String) : PulseWebCommand
    data class GoBack(val tabId: String) : PulseWebCommand
    data class GoForward(val tabId: String) : PulseWebCommand
    data class Reload(val tabId: String) : PulseWebCommand
    data class StopLoading(val tabId: String) : PulseWebCommand
    data class RecreateAndReload(val tabId: String, val url: String) : PulseWebCommand
    data class ProbeActiveWebGl(val tabId: String) : PulseWebCommand
    data class CloseTab(val tabId: String, val wasPrivate: Boolean) : PulseWebCommand
    data object ClearAllCaches : PulseWebCommand
}

data class PulseUiState(
    val destination: PulseDestination = PulseDestination.BROWSER,
    val tabs: List<PulseTab> = listOf(PulseTab()),
    val activeTabIndex: Int = 0,
    val isTabsDrawerOpen: Boolean = false,
    val gamingModeActive: Boolean = false,
    val gamingHudExpanded: Boolean = false,
    val gamingUiHidden: Boolean = false,
    val html5FullscreenActive: Boolean = false,
    val settings: PulseSettings = PulseSettings(),
    val deviceCapabilities: DeviceCapabilitiesReport = DeviceCapabilitiesReport(),
    val diagnostics: DiagnosticsState = DiagnosticsState(),
    val bookmarks: List<BookmarkEntity> = emptyList(),
    val history: List<HistoryEntity> = emptyList(),
    val downloads: List<DownloadEntity> = emptyList(),
    val siteProfiles: List<SiteGamingProfileEntity> = emptyList(),
    val activeSiteProfile: SiteGamingProfileEntity? = null,
    val toastMessage: String? = null
) {
    val activeTab: PulseTab
        get() = tabs.getOrElse(activeTabIndex) { tabs.first() }

    val isActiveUrlBookmarked: Boolean
        get() = bookmarks.any { it.url.equals(activeTab.url, ignoreCase = true) }

    val effectiveRefreshHzPreference: Int
        get() = if (gamingModeActive && activeSiteProfile != null) {
            activeSiteProfile.preferredRefreshHz
        } else {
            settings.preferredRefreshHz
        }

    val effectiveKeepScreenOn: Boolean
        get() = if (gamingModeActive && activeSiteProfile != null) {
            activeSiteProfile.keepScreenOn
        } else {
            settings.keepScreenOnInGamingMode
        }

    val effectiveLockLandscape: Boolean
        get() = if (gamingModeActive && activeSiteProfile != null) {
            activeSiteProfile.lockLandscape
        } else {
            settings.lockLandscapeInGamingMode
        }

    val effectiveSafeRenderScale: Int
        get() = if (gamingModeActive && activeSiteProfile != null) {
            activeSiteProfile.safeRenderScalePercent
        } else {
            settings.safeRenderScalePercent
        }
}
