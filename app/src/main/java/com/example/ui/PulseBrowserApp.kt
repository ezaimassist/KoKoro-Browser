package com.example.ui

import android.app.Activity
import android.view.View
import android.view.ViewGroup
import android.webkit.WebChromeClient
import android.widget.FrameLayout
import androidx.activity.compose.BackHandler
import androidx.compose.animation.fadeIn
import androidx.compose.animation.fadeOut
import androidx.compose.foundation.BorderStroke
import androidx.compose.foundation.background
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.BoxWithConstraints
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.imePadding
import androidx.compose.foundation.layout.navigationBarsPadding
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.statusBarsPadding
import androidx.compose.foundation.layout.width
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.filled.BugReport
import androidx.compose.material.icons.filled.CollectionsBookmark
import androidx.compose.material.icons.filled.Public
import androidx.compose.material.icons.filled.Refresh
import androidx.compose.material.icons.filled.Settings
import androidx.compose.material.icons.filled.Warning
import androidx.compose.material3.Button
import androidx.compose.material3.Icon
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.NavigationRail
import androidx.compose.material3.NavigationRailItem
import androidx.compose.material3.OutlinedButton
import androidx.compose.material3.Surface
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.runtime.DisposableEffect
import androidx.compose.runtime.LaunchedEffect
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.setValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.platform.LocalContext
import androidx.compose.ui.platform.testTag
import androidx.compose.ui.unit.dp
import androidx.compose.ui.viewinterop.AndroidView
import androidx.lifecycle.compose.collectAsStateWithLifecycle
import com.example.engine.UrlUtils
import com.example.engine.WindowGamingController
import com.example.ui.theme.LocalPulseSemanticColors
import com.example.viewmodel.PulseDestination
import com.example.viewmodel.PulseViewModel

@Composable
fun PulseBrowserApp(
    viewModel: PulseViewModel
) {
    val state by viewModel.uiState.collectAsStateWithLifecycle()
    val context = LocalContext.current
    val activity = context as? Activity
    val semantic = LocalPulseSemanticColors.current

    // Native HTML5 Fullscreen Custom View state (canvas.requestFullscreen() / video fullscreen)
    var customFullscreenView by remember { mutableStateOf<View?>(null) }
    var customFullscreenCallback by remember { mutableStateOf<WebChromeClient.CustomViewCallback?>(null) }

    fun exitHtml5Fullscreen() {
        customFullscreenCallback?.onCustomViewHidden()
        customFullscreenCallback = null
        customFullscreenView = null
        viewModel.setHtml5FullscreenActive(false)
    }

    // Attach real Window.OnFrameMetricsAvailableListener for honest frame timing telemetry
    DisposableEffect(activity) {
        val window = activity?.window
        if (window != null) {
            viewModel.diagnosticsCollector.attachFrameMetrics(window)
        }
        onDispose {
            viewModel.diagnosticsCollector.detachFrameMetrics()
        }
    }

    // Synchronize real Android Window refresh mode, landscape lock, keep-screen-on, and immersive fullscreen
    LaunchedEffect(
        state.gamingModeActive,
        state.html5FullscreenActive,
        state.effectiveKeepScreenOn,
        state.effectiveLockLandscape,
        state.effectiveRefreshHzPreference
    ) {
        if (activity != null) {
            val applied = WindowGamingController.applyWindowConfiguration(
                activity = activity,
                gamingModeActive = state.gamingModeActive,
                html5FullscreenActive = state.html5FullscreenActive,
                keepScreenOn = state.effectiveKeepScreenOn,
                lockLandscape = state.effectiveLockLandscape,
                preferredRefreshHz = state.effectiveRefreshHzPreference
            )
            viewModel.diagnosticsCollector.updateDisplayRefreshTelemetry(
                requestedHz = applied.requestedHz,
                actualHz = applied.actualActiveHz
            )
        }
    }

    // Exit HTML5 fullscreen cleanly on system Back
    BackHandler(enabled = customFullscreenView != null) {
        exitHtml5Fullscreen()
    }

    // WebView Back / Exit Gaming Mode handling when on main browser screen
    BackHandler(
        enabled = customFullscreenView == null &&
            state.destination == PulseDestination.BROWSER &&
            (state.activeTab.canGoBack || state.gamingModeActive || !UrlUtils.isInternal(state.activeTab.url))
    ) {
        when {
            state.activeTab.canGoBack -> viewModel.goBack()
            state.gamingModeActive -> viewModel.setGamingMode(false, reason = "Exited Gaming Mode")
            !UrlUtils.isInternal(state.activeTab.url) -> viewModel.loadUrlInActiveTab(UrlUtils.INTERNAL_HOME)
        }
    }

    BoxWithConstraints(
        modifier = Modifier
            .fillMaxSize()
            .background(MaterialTheme.colorScheme.background)
    ) {
        val isWideScreen = maxWidth >= 700.dp

        Row(modifier = Modifier.fillMaxSize()) {
            if (isWideScreen && !state.gamingModeActive && customFullscreenView == null) {
                NavigationRail(
                    containerColor = MaterialTheme.colorScheme.surface,
                    modifier = Modifier
                        .statusBarsPadding()
                        .navigationBarsPadding()
                ) {
                    NavigationRailItem(
                        selected = state.destination == PulseDestination.BROWSER,
                        onClick = { viewModel.navigateTo(PulseDestination.BROWSER) },
                        icon = { Icon(Icons.Filled.Public, contentDescription = "Browser") },
                        label = { Text("Browse") }
                    )
                    NavigationRailItem(
                        selected = state.destination == PulseDestination.DIAGNOSTICS,
                        onClick = { viewModel.navigateTo(PulseDestination.DIAGNOSTICS) },
                        icon = { Icon(Icons.Filled.BugReport, contentDescription = "Diagnostics") },
                        label = { Text("Diag") }
                    )
                    NavigationRailItem(
                        selected = state.destination == PulseDestination.LIBRARY,
                        onClick = { viewModel.navigateTo(PulseDestination.LIBRARY) },
                        icon = { Icon(Icons.Filled.CollectionsBookmark, contentDescription = "Library") },
                        label = { Text("Library") }
                    )
                    NavigationRailItem(
                        selected = state.destination == PulseDestination.SETTINGS,
                        onClick = { viewModel.navigateTo(PulseDestination.SETTINGS) },
                        icon = { Icon(Icons.Filled.Settings, contentDescription = "Settings") },
                        label = { Text("Settings") }
                    )
                }
            }

            Box(
                modifier = Modifier
                    .weight(1f)
                    .fillMaxSize()
            ) {
                when (state.destination) {
                    PulseDestination.BROWSER -> {
                        val isHome = UrlUtils.isInternal(state.activeTab.url)
                        val showChrome = !(state.gamingModeActive && state.gamingUiHidden)
                        val bottomChrome = state.settings.toolbarAtBottom

                        Column(
                            modifier = Modifier
                                .fillMaxSize()
                                .then(
                                    if (state.gamingModeActive && state.gamingUiHidden) {
                                        Modifier
                                    } else {
                                        Modifier
                                            .statusBarsPadding()
                                            .navigationBarsPadding()
                                            .imePadding()
                                    }
                                )
                        ) {
                            if (showChrome && !bottomChrome) {
                                PulseBrowserChrome(
                                    activeTab = state.activeTab,
                                    tabCount = state.tabs.size,
                                    gamingModeActive = state.gamingModeActive,
                                    isBookmarked = state.isActiveUrlBookmarked,
                                    diagnosticErrorsCount = state.diagnostics.totalErrorsCount,
                                    onSubmitAddress = viewModel::submitAddress,
                                    onBack = viewModel::goBack,
                                    onForward = viewModel::goForward,
                                    onReloadOrStop = viewModel::reloadOrStop,
                                    onHome = { viewModel.loadUrlInActiveTab(UrlUtils.INTERNAL_HOME) },
                                    onToggleGamingMode = viewModel::toggleGamingMode,
                                    onToggleBookmark = viewModel::toggleBookmarkForActiveTab,
                                    onOpenDiagnostics = { viewModel.navigateTo(PulseDestination.DIAGNOSTICS) },
                                    onOpenLibrary = { viewModel.navigateTo(PulseDestination.LIBRARY) },
                                    onOpenSettings = { viewModel.navigateTo(PulseDestination.SETTINGS) },
                                    onOpenTabs = { viewModel.setTabsDrawerOpen(true) }
                                )
                            }

                            Box(
                                modifier = Modifier
                                    .weight(1f)
                                    .fillMaxWidth()
                            ) {
                                PulseWebViewHost(
                                    tabs = state.tabs,
                                    activeTabId = state.activeTab.id,
                                    gamingModeActive = state.gamingModeActive,
                                    effectiveRenderScale = state.effectiveSafeRenderScale,
                                    pulseSettings = state.settings,
                                    diagnosticsCollector = viewModel.diagnosticsCollector,
                                    commands = viewModel.webCommands,
                                    onPageStarted = viewModel::onWebPageStarted,
                                    onPageProgress = viewModel::onWebPageProgress,
                                    onPageTitle = viewModel::onWebPageTitle,
                                    onPageFinished = viewModel::onWebPageFinished,
                                    onRendererCrash = viewModel::onRendererProcessGone,
                                    onDownloadRequested = viewModel::handleDownloadRequest,
                                    onWebGlProbeResult = viewModel::onWebGlProbeJsonResult,
                                    onEnterHtml5Fullscreen = { view, callback ->
                                        customFullscreenView = view
                                        customFullscreenCallback = callback
                                        viewModel.setHtml5FullscreenActive(true)
                                    },
                                    onExitHtml5Fullscreen = { exitHtml5Fullscreen() },
                                    modifier = Modifier.fillMaxSize()
                                )

                                if (isHome) {
                                    PulseStartPage(
                                        isPrivateTab = state.activeTab.isPrivate,
                                        capabilities = state.deviceCapabilities,
                                        diagnostics = state.diagnostics,
                                        bookmarks = state.bookmarks,
                                        onLaunchLastHavens = viewModel::launchLastHavens,
                                        onOpenUrl = viewModel::loadUrlInActiveTab,
                                        onOpenPrivateTab = { viewModel.openNewTab(isPrivate = true) },
                                        onOpenDiagnostics = { viewModel.navigateTo(PulseDestination.DIAGNOSTICS) },
                                        onOpenLibrary = { viewModel.navigateTo(PulseDestination.LIBRARY) },
                                        onOpenSettings = { viewModel.navigateTo(PulseDestination.SETTINGS) },
                                        modifier = Modifier
                                            .fillMaxSize()
                                            .background(MaterialTheme.colorScheme.background)
                                    )
                                }

                                // Renderer Crash / OOM Safe Recovery Banner
                                if (state.activeTab.rendererCrashed) {
                                    Box(
                                        modifier = Modifier
                                            .fillMaxSize()
                                            .background(MaterialTheme.colorScheme.background.copy(alpha = 0.94f))
                                            .padding(24.dp),
                                        contentAlignment = Alignment.Center
                                    ) {
                                        Surface(
                                            shape = RoundedCornerShape(20.dp),
                                            color = MaterialTheme.colorScheme.surface,
                                            border = BorderStroke(1.dp, semantic.errorRose),
                                            modifier = Modifier.fillMaxWidth()
                                        ) {
                                            Column(
                                                modifier = Modifier.padding(20.dp),
                                                verticalArrangement = Arrangement.spacedBy(12.dp)
                                            ) {
                                                Row(
                                                    verticalAlignment = Alignment.CenterVertically,
                                                    horizontalArrangement = Arrangement.spacedBy(10.dp)
                                                ) {
                                                    Icon(Icons.Filled.Warning, contentDescription = null, tint = semantic.errorRose)
                                                    Text("Chromium Renderer Interrupted", style = MaterialTheme.typography.titleMedium)
                                                }
                                                Text(
                                                    text = state.activeTab.crashSummary ?: "The renderer process stopped unexpectedly.",
                                                    style = MaterialTheme.typography.bodyMedium,
                                                    color = MaterialTheme.colorScheme.onSurfaceVariant
                                                )
                                                Row(horizontalArrangement = Arrangement.spacedBy(10.dp)) {
                                                    Button(
                                                        onClick = { viewModel.recoverCrashedTab(state.activeTab.id) },
                                                        shape = RoundedCornerShape(12.dp),
                                                        modifier = Modifier.testTag("crash_recover_button")
                                                    ) {
                                                        Icon(Icons.Filled.Refresh, contentDescription = null)
                                                        Spacer(Modifier.width(6.dp))
                                                        Text("Reload Safely")
                                                    }
                                                    OutlinedButton(
                                                        onClick = { viewModel.navigateTo(PulseDestination.DIAGNOSTICS) },
                                                        shape = RoundedCornerShape(12.dp),
                                                        modifier = Modifier.testTag("crash_open_diagnostics_button")
                                                    ) {
                                                        Text("View Diagnostics")
                                                    }
                                                }
                                            }
                                        }
                                    }
                                }

                                // Floating Gaming Mode Pill / HUD
                                if (state.gamingModeActive && state.settings.showDiagnosticHudInGaming) {
                                    PulseGamingHud(
                                        diagnostics = state.diagnostics,
                                        hudExpanded = state.gamingHudExpanded,
                                        browserUiHidden = state.gamingUiHidden,
                                        onToggleExpanded = viewModel::toggleGamingHudExpanded,
                                        onToggleBrowserUiHidden = viewModel::toggleGamingUiHidden,
                                        onCycleRefreshHz = viewModel::cyclePreferredRefreshRate,
                                        onOpenDiagnostics = { viewModel.navigateTo(PulseDestination.DIAGNOSTICS) },
                                        onExitGamingMode = { viewModel.setGamingMode(false, reason = "Exited Gaming Mode") },
                                        modifier = Modifier
                                            .align(Alignment.TopStart)
                                            .statusBarsPadding()
                                            .padding(10.dp)
                                    )
                                }
                            }

                            if (showChrome && bottomChrome) {
                                PulseBrowserChrome(
                                    activeTab = state.activeTab,
                                    tabCount = state.tabs.size,
                                    gamingModeActive = state.gamingModeActive,
                                    isBookmarked = state.isActiveUrlBookmarked,
                                    diagnosticErrorsCount = state.diagnostics.totalErrorsCount,
                                    onSubmitAddress = viewModel::submitAddress,
                                    onBack = viewModel::goBack,
                                    onForward = viewModel::goForward,
                                    onReloadOrStop = viewModel::reloadOrStop,
                                    onHome = { viewModel.loadUrlInActiveTab(UrlUtils.INTERNAL_HOME) },
                                    onToggleGamingMode = viewModel::toggleGamingMode,
                                    onToggleBookmark = viewModel::toggleBookmarkForActiveTab,
                                    onOpenDiagnostics = { viewModel.navigateTo(PulseDestination.DIAGNOSTICS) },
                                    onOpenLibrary = { viewModel.navigateTo(PulseDestination.LIBRARY) },
                                    onOpenSettings = { viewModel.navigateTo(PulseDestination.SETTINGS) },
                                    onOpenTabs = { viewModel.setTabsDrawerOpen(true) }
                                )
                            }
                        }
                    }

                    PulseDestination.DIAGNOSTICS -> {
                        PulseDiagnosticsScreen(
                            capabilities = state.deviceCapabilities,
                            diagnostics = state.diagnostics,
                            activeUrl = state.activeTab.url,
                            onProbeActiveWebGl = viewModel::triggerActiveWebGlProbe,
                            onClearLogs = { viewModel.diagnosticsCollector.clearLogs() },
                            onBuildExportText = viewModel::buildDiagnosticReportText,
                            onExportStatus = viewModel::showToast,
                            onBack = { viewModel.navigateTo(PulseDestination.BROWSER) },
                            modifier = Modifier
                                .statusBarsPadding()
                                .navigationBarsPadding()
                        )
                    }

                    PulseDestination.LIBRARY -> {
                        PulseLibraryScreen(
                            activeUrl = state.activeTab.url,
                            bookmarks = state.bookmarks,
                            history = state.history,
                            downloads = state.downloads,
                            siteProfiles = state.siteProfiles,
                            supportedRefreshRates = state.deviceCapabilities.selectableRefreshRatesHz,
                            onOpenUrl = viewModel::loadUrlInActiveTab,
                            onAddBookmark = viewModel::addManualBookmark,
                            onDeleteBookmark = viewModel::deleteBookmark,
                            onDeleteHistory = viewModel::deleteHistoryItem,
                            onClearHistory = viewModel::clearHistory,
                            onDeleteDownload = viewModel::deleteDownload,
                            onClearDownloads = viewModel::clearDownloadsList,
                            onSaveProfile = viewModel::saveSiteProfile,
                            onDeleteProfile = viewModel::deleteSiteProfile,
                            onBack = { viewModel.navigateTo(PulseDestination.BROWSER) },
                            modifier = Modifier
                                .statusBarsPadding()
                                .navigationBarsPadding()
                        )
                    }

                    PulseDestination.SETTINGS -> {
                        PulseSettingsScreen(
                            settings = state.settings,
                            capabilities = state.deviceCapabilities,
                            onUpdateSettings = viewModel::updateSettings,
                            onClearCache = viewModel::clearBrowserCache,
                            onClearCookiesAndStorage = viewModel::clearCookiesAndStorage,
                            onClearHistory = viewModel::clearHistory,
                            onBack = { viewModel.navigateTo(PulseDestination.BROWSER) },
                            modifier = Modifier
                                .statusBarsPadding()
                                .navigationBarsPadding()
                        )
                    }
                }

                if (state.isTabsDrawerOpen) {
                    PulseTabsDrawer(
                        tabs = state.tabs,
                        activeTabIndex = state.activeTabIndex,
                        onSelectTab = viewModel::selectTab,
                        onCloseTab = viewModel::closeTab,
                        onNewTab = { viewModel.openNewTab(isPrivate = false) },
                        onNewPrivateTab = { viewModel.openNewTab(isPrivate = true) },
                        onToggleDesktopUa = viewModel::toggleDesktopUserAgentForActiveTab,
                        onDismiss = { viewModel.setTabsDrawerOpen(false) }
                    )
                }

                // Native HTML5 Fullscreen Container (for canvas.requestFullscreen() / HTML5 video)
                val fsView = customFullscreenView
                if (fsView != null) {
                    Box(
                        modifier = Modifier
                            .fillMaxSize()
                            .background(Color.Black)
                    ) {
                        AndroidView(
                            factory = { ctx ->
                                FrameLayout(ctx).apply {
                                    (fsView.parent as? ViewGroup)?.removeView(fsView)
                                    addView(
                                        fsView,
                                        FrameLayout.LayoutParams(
                                            ViewGroup.LayoutParams.MATCH_PARENT,
                                            ViewGroup.LayoutParams.MATCH_PARENT
                                        )
                                    )
                                }
                            },
                            modifier = Modifier.fillMaxSize()
                        )
                    }
                }

                // Ephemeral Status Toast
                androidx.compose.animation.AnimatedVisibility(
                    visible = state.toastMessage != null,
                    enter = fadeIn(),
                    exit = fadeOut(),
                    modifier = Modifier
                        .align(Alignment.TopCenter)
                        .statusBarsPadding()
                        .padding(top = 12.dp, start = 20.dp, end = 20.dp)
                ) {
                    Surface(
                        shape = RoundedCornerShape(999.dp),
                        color = semantic.hudOverlay,
                        border = BorderStroke(1.dp, MaterialTheme.colorScheme.primary.copy(alpha = 0.65f))
                    ) {
                        Text(
                            text = state.toastMessage.orEmpty(),
                            style = MaterialTheme.typography.labelMedium,
                            color = MaterialTheme.colorScheme.primary,
                            modifier = Modifier.padding(horizontal = 16.dp, vertical = 9.dp)
                        )
                    }
                }
            }
        }
    }
}
