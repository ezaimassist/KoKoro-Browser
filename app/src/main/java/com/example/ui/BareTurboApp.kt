package com.example.ui

import android.app.Activity
import androidx.activity.compose.BackHandler
import androidx.compose.animation.AnimatedVisibility
import androidx.compose.animation.fadeIn
import androidx.compose.animation.fadeOut
import androidx.compose.foundation.BorderStroke
import androidx.compose.foundation.background
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.BoxWithConstraints
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.imePadding
import androidx.compose.foundation.layout.navigationBarsPadding
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.statusBarsPadding
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.filled.Bolt
import androidx.compose.material.icons.filled.Memory
import androidx.compose.material.icons.filled.Public
import androidx.compose.material.icons.filled.Shield
import androidx.compose.material3.Icon
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.NavigationRail
import androidx.compose.material3.NavigationRailItem
import androidx.compose.material3.Surface
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.runtime.LaunchedEffect
import androidx.compose.runtime.getValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.platform.LocalContext
import androidx.compose.ui.platform.testTag
import androidx.compose.ui.unit.dp
import androidx.lifecycle.compose.collectAsStateWithLifecycle
import com.example.engine.UrlSecurityHelper
import com.example.engine.WindowPerformanceController
import com.example.ui.theme.LocalTelemetryColors
import com.example.viewmodel.AppDestination
import com.example.viewmodel.BrowserViewModel

@Composable
fun BareTurboApp(
    viewModel: BrowserViewModel
) {
    val state by viewModel.uiState.collectAsStateWithLifecycle()
    val context = LocalContext.current
    val activity = context as? Activity
    val tColors = LocalTelemetryColors.current

    // Synchronize real Android Window performance modes (Max Hz lock, Sustained Perf, Immersive Gaming Mode)
    LaunchedEffect(
        state.gamingModeActive,
        state.settings.lockMaxRefreshRate,
        state.settings.sustainedPerformanceMode
    ) {
        if (activity != null) {
            WindowPerformanceController.applyPerformanceProfile(
                activity = activity,
                gamingModeActive = state.gamingModeActive,
                lockMaxRefreshRate = state.settings.lockMaxRefreshRate,
                sustainedPerformanceMode = state.settings.sustainedPerformanceMode
            )
        }
    }

    // Back gesture handling when inside WebView or Gaming Mode
    BackHandler(
        enabled = state.destination == AppDestination.BROWSER &&
            (state.gamingModeActive || state.activeTab.canGoBack || state.activeTab.url != UrlSecurityHelper.INTERNAL_HOME)
    ) {
        when {
            state.activeTab.canGoBack -> viewModel.goBack()
            state.gamingModeActive -> viewModel.setGamingMode(false, "Exited Gaming Mode")
            state.activeTab.url != UrlSecurityHelper.INTERNAL_HOME -> viewModel.navigateActiveTabToUrl(UrlSecurityHelper.INTERNAL_HOME)
        }
    }

    BoxWithConstraints(
        modifier = Modifier
            .fillMaxSize()
            .background(MaterialTheme.colorScheme.background)
    ) {
        val isExpandedWidth = maxWidth >= 700.dp

        Row(modifier = Modifier.fillMaxSize()) {
            // Adaptive NavigationRail for tablets / foldables when not in fullscreen Gaming Mode
            if (isExpandedWidth && !state.gamingModeActive) {
                NavigationRail(
                    containerColor = MaterialTheme.colorScheme.surface,
                    modifier = Modifier
                        .statusBarsPadding()
                        .navigationBarsPadding()
                ) {
                    NavigationRailItem(
                        selected = state.destination == AppDestination.BROWSER,
                        onClick = { viewModel.navigateToDestination(AppDestination.BROWSER) },
                        icon = { Icon(Icons.Filled.Public, contentDescription = "Browser") },
                        label = { Text("Browse") },
                        modifier = Modifier.testTag("rail_browser")
                    )
                    NavigationRailItem(
                        selected = state.destination == AppDestination.GPU_LAB,
                        onClick = { viewModel.navigateToDestination(AppDestination.GPU_LAB) },
                        icon = { Icon(Icons.Filled.Memory, contentDescription = "GPU Lab") },
                        label = { Text("GPU Lab") },
                        modifier = Modifier.testTag("rail_gpu_lab")
                    )
                    NavigationRailItem(
                        selected = state.destination == AppDestination.SHIELD_PROFILES,
                        onClick = { viewModel.navigateToDestination(AppDestination.SHIELD_PROFILES) },
                        icon = { Icon(Icons.Filled.Shield, contentDescription = "Shield") },
                        label = { Text("Shield") },
                        modifier = Modifier.testTag("rail_shield")
                    )
                    NavigationRailItem(
                        selected = state.gamingModeActive,
                        onClick = { viewModel.toggleGamingMode() },
                        icon = { Icon(Icons.Filled.Bolt, contentDescription = "Gaming Mode") },
                        label = { Text("Turbo") },
                        modifier = Modifier.testTag("rail_gaming_mode")
                    )
                }
            }

            Box(
                modifier = Modifier
                    .weight(1f)
                    .fillMaxSize()
            ) {
                when (state.destination) {
                    AppDestination.BROWSER -> {
                        val isHome = state.activeTab.url == UrlSecurityHelper.INTERNAL_HOME
                        val bottomBar = state.settings.toolbarAtBottom

                        Column(
                            modifier = Modifier
                                .fillMaxSize()
                                .then(
                                    if (state.gamingModeActive) {
                                        Modifier
                                    } else {
                                        Modifier
                                            .statusBarsPadding()
                                            .navigationBarsPadding()
                                            .imePadding()
                                    }
                                )
                        ) {
                            // Top Toolbar (when user disables bottom toolbar and not in Gaming Mode)
                            if (!bottomBar && !state.gamingModeActive) {
                                BrowserChromeBar(
                                    activeTab = state.activeTab,
                                    tabCount = state.tabs.size,
                                    gamingModeActive = state.gamingModeActive,
                                    shieldEnabled = state.settings.shieldEnabled,
                                    isBookmarked = state.isCurrentUrlBookmarked,
                                    fps = state.telemetry.fps,
                                    onSubmitAddress = viewModel::navigateAddress,
                                    onBack = viewModel::goBack,
                                    onForward = viewModel::goForward,
                                    onReloadOrStop = viewModel::reloadOrStop,
                                    onHome = { viewModel.navigateActiveTabToUrl(UrlSecurityHelper.INTERNAL_HOME) },
                                    onToggleGamingMode = viewModel::toggleGamingMode,
                                    onToggleBookmark = viewModel::toggleBookmarkForActiveTab,
                                    onOpenTabs = { viewModel.openTabsDrawer(true) },
                                    onOpenGpuLab = { viewModel.navigateToDestination(AppDestination.GPU_LAB) },
                                    onOpenShield = { viewModel.navigateToDestination(AppDestination.SHIELD_PROFILES) }
                                )
                            }

                            // Main Viewport: Real WebView kept alive + StartPage overlay when on bare://home
                            Box(
                                modifier = Modifier
                                    .weight(1f)
                                    .fillMaxWidth()
                            ) {
                                BrowserWebViewHost(
                                    tabs = state.tabs,
                                    activeTabId = state.activeTab.id,
                                    gamingModeActive = state.gamingModeActive,
                                    settings = state.settings,
                                    shieldEngine = viewModel.shieldEngine,
                                    commands = viewModel.webViewCommands,
                                    onPageStarted = viewModel::onWebPageStarted,
                                    onPageProgress = viewModel::onWebPageProgress,
                                    onPageTitle = viewModel::onWebPageTitle,
                                    onPageFinished = viewModel::onWebPageFinished,
                                    modifier = Modifier.fillMaxSize()
                                )

                                if (isHome) {
                                    StartPageScreen(
                                        gpuReport = state.gpuReport,
                                        telemetry = state.telemetry,
                                        shieldStats = state.shieldStats,
                                        bookmarks = state.bookmarks,
                                        activePreset = state.activePreset,
                                        gamingModeActive = state.gamingModeActive,
                                        renderScalePercent = state.settings.renderScalePercent,
                                        onSelectPreset = viewModel::applyGamingPreset,
                                        onOpenUrl = viewModel::navigateActiveTabToUrl,
                                        onToggleGamingMode = viewModel::toggleGamingMode,
                                        onOpenGpuLab = { viewModel.navigateToDestination(AppDestination.GPU_LAB) },
                                        onOpenShield = { viewModel.navigateToDestination(AppDestination.SHIELD_PROFILES) },
                                        modifier = Modifier
                                            .fillMaxSize()
                                            .background(MaterialTheme.colorScheme.background)
                                    )
                                }

                                // Floating Real-Time Gaming Mode HUD Overlay
                                if (state.gamingModeActive) {
                                    GamingModeHudOverlay(
                                        telemetry = state.telemetry,
                                        blockedCount = state.shieldStats.totalBlocked,
                                        frozenTabCount = state.frozenTabCount,
                                        maxRefreshHz = state.gpuReport.maxRefreshRateHz.toInt(),
                                        renderScalePercent = state.activeSiteProfile?.renderScalePercent
                                            ?: state.settings.renderScalePercent,
                                        hudExpanded = state.hudVisible,
                                        onToggleHudExpanded = viewModel::toggleHudVisibility,
                                        onOpenGpuLab = { viewModel.navigateToDestination(AppDestination.GPU_LAB) },
                                        onExitGamingMode = { viewModel.setGamingMode(false, "Exited Gaming Mode") },
                                        modifier = Modifier
                                            .align(Alignment.TopStart)
                                            .statusBarsPadding()
                                            .padding(12.dp)
                                    )
                                }
                            }

                            // Bottom Toolbar (Bare Browser default ergonomic position)
                            if (bottomBar && !state.gamingModeActive) {
                                BrowserChromeBar(
                                    activeTab = state.activeTab,
                                    tabCount = state.tabs.size,
                                    gamingModeActive = state.gamingModeActive,
                                    shieldEnabled = state.settings.shieldEnabled,
                                    isBookmarked = state.isCurrentUrlBookmarked,
                                    fps = state.telemetry.fps,
                                    onSubmitAddress = viewModel::navigateAddress,
                                    onBack = viewModel::goBack,
                                    onForward = viewModel::goForward,
                                    onReloadOrStop = viewModel::reloadOrStop,
                                    onHome = { viewModel.navigateActiveTabToUrl(UrlSecurityHelper.INTERNAL_HOME) },
                                    onToggleGamingMode = viewModel::toggleGamingMode,
                                    onToggleBookmark = viewModel::toggleBookmarkForActiveTab,
                                    onOpenTabs = { viewModel.openTabsDrawer(true) },
                                    onOpenGpuLab = { viewModel.navigateToDestination(AppDestination.GPU_LAB) },
                                    onOpenShield = { viewModel.navigateToDestination(AppDestination.SHIELD_PROFILES) }
                                )
                            }
                        }
                    }

                    AppDestination.GPU_LAB -> {
                        GpuLabScreen(
                            gpuReport = state.gpuReport,
                            telemetry = state.telemetry,
                            settings = state.settings,
                            gamingModeActive = state.gamingModeActive,
                            onUpdateSettings = viewModel::updateSettings,
                            onLaunchBenchmark = {
                                viewModel.navigateActiveTabToUrl(UrlSecurityHelper.INTERNAL_BENCHMARK)
                            },
                            onRefreshProbe = viewModel::refreshHardwareProbe,
                            onToggleGamingMode = viewModel::toggleGamingMode,
                            onBack = { viewModel.navigateToDestination(AppDestination.BROWSER) },
                            modifier = Modifier
                                .statusBarsPadding()
                                .navigationBarsPadding()
                        )
                    }

                    AppDestination.SHIELD_PROFILES -> {
                        ShieldAndProfilesScreen(
                            activeUrl = state.activeTab.url,
                            settings = state.settings,
                            shieldStats = state.shieldStats,
                            bookmarks = state.bookmarks,
                            siteProfiles = state.siteProfiles,
                            onUpdateSettings = viewModel::updateSettings,
                            onResetShieldStats = viewModel::resetShieldCounters,
                            onOpenUrl = viewModel::navigateActiveTabToUrl,
                            onAddBookmark = viewModel::addManualBookmark,
                            onDeleteBookmark = viewModel::deleteBookmark,
                            onSaveSiteProfile = viewModel::saveSiteProfileForHost,
                            onDeleteSiteProfile = viewModel::deleteSiteProfile,
                            onBack = { viewModel.navigateToDestination(AppDestination.BROWSER) },
                            modifier = Modifier
                                .statusBarsPadding()
                                .navigationBarsPadding()
                        )
                    }
                }

                // Bare Browser Side Drawer Tab Manager
                if (state.isTabsDrawerOpen) {
                    TabsSideDrawer(
                        tabs = state.tabs,
                        activeTabIndex = state.activeTabIndex,
                        isGridView = state.isTabGridView,
                        appHeapMb = state.telemetry.appHeapMb,
                        onToggleGridView = viewModel::toggleTabGridView,
                        onSelectTab = viewModel::selectTab,
                        onCloseTab = viewModel::closeTab,
                        onNewTab = { viewModel.openNewTab() },
                        onDismiss = { viewModel.openTabsDrawer(false) }
                    )
                }

                // Ephemeral Status Banner Toast
                androidx.compose.animation.AnimatedVisibility(
                    visible = state.statusBannerMessage != null,
                    enter = fadeIn(),
                    exit = fadeOut(),
                    modifier = Modifier
                        .align(Alignment.TopCenter)
                        .statusBarsPadding()
                        .padding(top = 12.dp, start = 20.dp, end = 20.dp)
                ) {
                    Surface(
                        shape = RoundedCornerShape(999.dp),
                        color = tColors.hudBackground,
                        border = BorderStroke(1.dp, tColors.fpsOptimal.copy(alpha = 0.65f))
                    ) {
                        Text(
                            text = state.statusBannerMessage.orEmpty(),
                            style = MaterialTheme.typography.labelMedium,
                            color = tColors.fpsOptimal,
                            modifier = Modifier.padding(horizontal = 16.dp, vertical = 9.dp)
                        )
                    }
                }
            }
        }
    }
}
