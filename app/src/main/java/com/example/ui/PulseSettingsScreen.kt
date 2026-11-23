package com.example.ui

import androidx.activity.compose.BackHandler
import androidx.compose.foundation.BorderStroke
import androidx.compose.foundation.background
import androidx.compose.foundation.clickable
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.PaddingValues
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.widthIn
import androidx.compose.foundation.lazy.LazyColumn
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.automirrored.filled.ArrowBack
import androidx.compose.material3.Icon
import androidx.compose.material3.IconButton
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.OutlinedButton
import androidx.compose.material3.Surface
import androidx.compose.material3.Switch
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clip
import androidx.compose.ui.platform.testTag
import androidx.compose.ui.res.stringResource
import androidx.compose.ui.unit.dp
import com.example.R
import com.example.data.PulseSettings
import com.example.engine.DeviceCapabilitiesReport
import com.example.ui.theme.LocalPulseSemanticColors

@Composable
fun PulseSettingsScreen(
    settings: PulseSettings,
    capabilities: DeviceCapabilitiesReport,
    onUpdateSettings: ((PulseSettings) -> PulseSettings) -> Unit,
    onClearCache: () -> Unit,
    onClearCookiesAndStorage: () -> Unit,
    onClearHistory: () -> Unit,
    onBack: () -> Unit,
    modifier: Modifier = Modifier
) {
    BackHandler(onBack = onBack)
    val semantic = LocalPulseSemanticColors.current

    Column(
        modifier = modifier
            .fillMaxSize()
            .background(MaterialTheme.colorScheme.background),
        horizontalAlignment = Alignment.CenterHorizontally
    ) {
        Row(
            modifier = Modifier
                .fillMaxWidth()
                .widthIn(max = 860.dp)
                .padding(horizontal = 8.dp, vertical = 6.dp),
            verticalAlignment = Alignment.CenterVertically
        ) {
            IconButton(
                onClick = onBack,
                modifier = Modifier.testTag("settings_back_button")
            ) {
                Icon(Icons.AutoMirrored.Filled.ArrowBack, contentDescription = stringResource(R.string.cd_back))
            }
            Text("Settings — Privacy, Performance & Display", style = MaterialTheme.typography.titleLarge)
        }

        LazyColumn(
            modifier = Modifier
                .fillMaxSize()
                .widthIn(max = 860.dp),
            contentPadding = PaddingValues(16.dp),
            verticalArrangement = Arrangement.spacedBy(14.dp)
        ) {
            // Display & Refresh Rate Section
            item {
                SettingsCard(title = "Display & Refresh Rate") {
                    Text(
                        text = "Select a display refresh mode actually reported by your device (${capabilities.selectableRefreshRatesHz.joinToString(", ") { "${it}Hz" }}). Requesting a higher display mode does not force a game to render faster than its WebGL engine permits.",
                        style = MaterialTheme.typography.bodySmall,
                        color = MaterialTheme.colorScheme.onSurfaceVariant
                    )
                    Row(
                        modifier = Modifier.fillMaxWidth(),
                        horizontalArrangement = Arrangement.spacedBy(8.dp)
                    ) {
                        (listOf(0) + capabilities.selectableRefreshRatesHz).forEach { hz ->
                            val selected = settings.preferredRefreshHz == hz
                            Surface(
                                shape = RoundedCornerShape(10.dp),
                                color = if (selected) MaterialTheme.colorScheme.primary else MaterialTheme.colorScheme.surfaceVariant,
                                modifier = Modifier
                                    .clip(RoundedCornerShape(10.dp))
                                    .clickable { onUpdateSettings { it.copy(preferredRefreshHz = hz) } }
                                    .testTag("settings_hz_$hz")
                            ) {
                                Text(
                                    text = if (hz == 0) "Auto Max" else "${hz}Hz",
                                    style = MaterialTheme.typography.labelLarge,
                                    color = if (selected) MaterialTheme.colorScheme.onPrimary else MaterialTheme.colorScheme.onSurface,
                                    modifier = Modifier.padding(horizontal = 12.dp, vertical = 8.dp)
                                )
                            }
                        }
                    }

                    SettingsToggle(
                        title = "Dark Modern Theme",
                        subtitle = "Midnight slate theme optimized for OLED displays",
                        checked = settings.darkTheme,
                        tag = "settings_dark_theme",
                        onChange = { v -> onUpdateSettings { it.copy(darkTheme = v) } }
                    )
                    SettingsToggle(
                        title = "Bottom Address Bar",
                        subtitle = "Ergonomic thumb reach on large smartphones",
                        checked = settings.toolbarAtBottom,
                        tag = "settings_bottom_bar",
                        onChange = { v -> onUpdateSettings { it.copy(toolbarAtBottom = v) } }
                    )
                }
            }

            // Gaming Mode & Performance Section
            item {
                SettingsCard(title = "Gaming Mode & WebGL Performance") {
                    SettingsToggle(
                        title = "Hide Browser UI in Gaming Mode",
                        subtitle = "Collapses address bar to a tiny floating Gaming HUD pill",
                        checked = settings.hideUiInGamingMode,
                        tag = "settings_hide_ui_gaming",
                        onChange = { v -> onUpdateSettings { it.copy(hideUiInGamingMode = v) } }
                    )
                    SettingsToggle(
                        title = "Lock Landscape in Gaming Mode",
                        subtitle = "Uses sensor landscape without recreating the WebView session",
                        checked = settings.lockLandscapeInGamingMode,
                        tag = "settings_lock_landscape",
                        onChange = { v -> onUpdateSettings { it.copy(lockLandscapeInGamingMode = v) } }
                    )
                    SettingsToggle(
                        title = "Keep Screen On While Gaming",
                        subtitle = "Prevents screen timeout during gameplay & asset downloads",
                        checked = settings.keepScreenOnInGamingMode,
                        tag = "settings_keep_screen_on",
                        onChange = { v -> onUpdateSettings { it.copy(keepScreenOnInGamingMode = v) } }
                    )
                    SettingsToggle(
                        title = "Unbuffered Touch Input Dispatch",
                        subtitle = "Calls requestUnbufferedDispatch() for lower touch latency",
                        checked = settings.unbufferedTouchInput,
                        tag = "settings_unbuffered_touch",
                        onChange = { v -> onUpdateSettings { it.copy(unbufferedTouchInput = v) } }
                    )
                    SettingsToggle(
                        title = "Freeze Background Tabs in Gaming Mode",
                        subtitle = "Pauses inactive tabs to preserve RAM for large WebAssembly games",
                        checked = settings.freezeBackgroundTabsInGaming,
                        tag = "settings_freeze_bg",
                        onChange = { v -> onUpdateSettings { it.copy(freezeBackgroundTabsInGaming = v) } }
                    )
                    SettingsToggle(
                        title = "Auto-Apply Per-Website Gaming Profiles",
                        subtitle = "Automatically activates saved profile when opening profiled hosts",
                        checked = settings.autoApplySiteProfiles,
                        tag = "settings_auto_profiles",
                        onChange = { v -> onUpdateSettings { it.copy(autoApplySiteProfiles = v) } }
                    )

                    Text("Safe Viewport Scaling (Default 100% Native)", style = MaterialTheme.typography.titleSmall)
                    Text(
                        text = "Keep at 100% Native for LastHavens and WebGL2 games. Lower values only adjust safe WebView viewport scale and never monkey-patch WebGL contexts.",
                        style = MaterialTheme.typography.bodySmall,
                        color = MaterialTheme.colorScheme.onSurfaceVariant
                    )
                    Row(horizontalArrangement = Arrangement.spacedBy(8.dp)) {
                        listOf(100 to "100% Native", 90 to "90% Safe", 80 to "80% Safe").forEach { (pct, lbl) ->
                            val selected = settings.safeRenderScalePercent == pct
                            Surface(
                                shape = RoundedCornerShape(10.dp),
                                color = if (selected) MaterialTheme.colorScheme.primary else MaterialTheme.colorScheme.surfaceVariant,
                                modifier = Modifier
                                    .clip(RoundedCornerShape(10.dp))
                                    .clickable { onUpdateSettings { it.copy(safeRenderScalePercent = pct) } }
                                    .testTag("settings_scale_$pct")
                            ) {
                                Text(
                                    text = lbl,
                                    style = MaterialTheme.typography.labelLarge,
                                    color = if (selected) MaterialTheme.colorScheme.onPrimary else MaterialTheme.colorScheme.onSurface,
                                    modifier = Modifier.padding(horizontal = 12.dp, vertical = 8.dp)
                                )
                            }
                        }
                    }
                }
            }

            // Privacy, Network & Compatibility Section
            item {
                SettingsCard(title = "Privacy, Cookies & Compatibility") {
                    SettingsToggle(
                        title = "HTTPS-First Upgrade",
                        subtitle = "Upgrades typed http:// URLs to https:// while preserving strict TLS verification",
                        checked = settings.httpsUpgrade,
                        tag = "settings_https_upgrade",
                        onChange = { v -> onUpdateSettings { it.copy(httpsUpgrade = v) } }
                    )
                    SettingsToggle(
                        title = "Allow Third-Party Cookies (Game Auth/CDN)",
                        subtitle = "Recommended ON for LastHavens login & CDN MPQ asset sessions; disabled in Private tabs",
                        checked = settings.allowThirdPartyCookies,
                        tag = "settings_3p_cookies",
                        onChange = { v -> onUpdateSettings { it.copy(allowThirdPartyCookies = v) } }
                    )
                    SettingsToggle(
                        title = "Standalone Chrome Compatibility",
                        subtitle = "Removes Android WebView (wv, Version/4.0, Android WebView Client Hints, X-Requested-With) so LastHavens runs natively instead of redirecting to Chrome/Safari",
                        checked = settings.standaloneChromeCompat,
                        tag = "settings_standalone_chrome_compat",
                        onChange = { v -> onUpdateSettings { it.copy(standaloneChromeCompat = v) } }
                    )
                    SettingsToggle(
                        title = "Default Desktop User-Agent for New Tabs",
                        subtitle = "Can also be toggled per-tab inside the Tabs drawer",
                        checked = settings.defaultDesktopUserAgent,
                        tag = "settings_desktop_ua",
                        onChange = { v -> onUpdateSettings { it.copy(defaultDesktopUserAgent = v) } }
                    )

                    Text("Default Search Engine", style = MaterialTheme.typography.titleSmall)
                    Row(horizontalArrangement = Arrangement.spacedBy(8.dp)) {
                        listOf("duckduckgo" to "DuckDuckGo", "startpage" to "Startpage", "google" to "Google").forEach { (id, label) ->
                            val selected = settings.searchEngine == id
                            Surface(
                                shape = RoundedCornerShape(10.dp),
                                color = if (selected) MaterialTheme.colorScheme.primary else MaterialTheme.colorScheme.surfaceVariant,
                                modifier = Modifier
                                    .clip(RoundedCornerShape(10.dp))
                                    .clickable { onUpdateSettings { it.copy(searchEngine = id) } }
                                    .testTag("settings_search_$id")
                            ) {
                                Text(
                                    text = label,
                                    style = MaterialTheme.typography.labelLarge,
                                    color = if (selected) MaterialTheme.colorScheme.onPrimary else MaterialTheme.colorScheme.onSurface,
                                    modifier = Modifier.padding(horizontal = 12.dp, vertical = 8.dp)
                                )
                            }
                        }
                    }
                }
            }

            // Storage & Site Data Maintenance
            item {
                SettingsCard(title = "Storage & Data Maintenance") {
                    Text(
                        text = "Keeping HTTP cache and IndexedDB intact prevents re-downloading large LastHavens MPQ/Wasm assets on every launch. Only clear if troubleshooting corrupted assets.",
                        style = MaterialTheme.typography.bodySmall,
                        color = MaterialTheme.colorScheme.onSurfaceVariant
                    )
                    Row(
                        modifier = Modifier.fillMaxWidth(),
                        horizontalArrangement = Arrangement.spacedBy(8.dp)
                    ) {
                        OutlinedButton(
                            onClick = onClearCache,
                            shape = RoundedCornerShape(10.dp),
                            modifier = Modifier
                                .weight(1f)
                                .testTag("settings_clear_cache")
                        ) {
                            Text("Clear Cache")
                        }
                        OutlinedButton(
                            onClick = onClearHistory,
                            shape = RoundedCornerShape(10.dp),
                            modifier = Modifier
                                .weight(1f)
                                .testTag("settings_clear_history")
                        ) {
                            Text("Clear History")
                        }
                    }
                    OutlinedButton(
                        onClick = onClearCookiesAndStorage,
                        shape = RoundedCornerShape(10.dp),
                        border = BorderStroke(1.dp, semantic.errorRose.copy(alpha = 0.6f)),
                        modifier = Modifier
                            .fillMaxWidth()
                            .testTag("settings_clear_cookies_storage")
                    ) {
                        Text("Clear Cookies & IndexedDB Storage", color = semantic.errorRose)
                    }
                }
            }
        }
    }
}

@Composable
private fun SettingsCard(
    title: String,
    content: @Composable () -> Unit
) {
    val semantic = LocalPulseSemanticColors.current
    Surface(
        shape = RoundedCornerShape(18.dp),
        color = MaterialTheme.colorScheme.surface,
        border = BorderStroke(1.dp, semantic.border),
        modifier = Modifier.fillMaxWidth()
    ) {
        Column(
            modifier = Modifier.padding(16.dp),
            verticalArrangement = Arrangement.spacedBy(10.dp)
        ) {
            Text(title, style = MaterialTheme.typography.titleMedium)
            content()
        }
    }
}

@Composable
private fun SettingsToggle(
    title: String,
    subtitle: String,
    checked: Boolean,
    tag: String,
    onChange: (Boolean) -> Unit
) {
    Row(
        modifier = Modifier.fillMaxWidth(),
        verticalAlignment = Alignment.CenterVertically,
        horizontalArrangement = Arrangement.SpaceBetween
    ) {
        Column(modifier = Modifier.weight(1f).padding(end = 12.dp)) {
            Text(title, style = MaterialTheme.typography.titleSmall)
            Text(subtitle, style = MaterialTheme.typography.bodySmall, color = MaterialTheme.colorScheme.onSurfaceVariant)
        }
        Switch(
            checked = checked,
            onCheckedChange = onChange,
            modifier = Modifier.testTag(tag)
        )
    }
}
