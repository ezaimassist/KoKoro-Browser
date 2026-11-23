package com.example.data

import android.content.Context
import androidx.datastore.preferences.core.Preferences
import androidx.datastore.preferences.core.booleanPreferencesKey
import androidx.datastore.preferences.core.edit
import androidx.datastore.preferences.core.intPreferencesKey
import androidx.datastore.preferences.preferencesDataStore
import kotlinx.coroutines.flow.Flow
import kotlinx.coroutines.flow.map

private val Context.barePrefsDataStore by preferencesDataStore(name = "bare_turbo_settings")

data class BrowserSettings(
    val toolbarAtBottom: Boolean = true,
    val shieldEnabled: Boolean = true,
    val autoGamingMode: Boolean = true,
    val showHudInGamingMode: Boolean = true,
    val lockMaxRefreshRate: Boolean = true,
    val unbufferedTouchInput: Boolean = true,
    val sustainedPerformanceMode: Boolean = true,
    val freezeBackgroundTabs: Boolean = true,
    val hardwareCompositing: Boolean = true,
    val webGlHighPerformance: Boolean = true,
    val webGlDisableAntialias: Boolean = false,
    val renderScalePercent: Int = 100,
    val desktopUserAgent: Boolean = false,
    val httpsOnly: Boolean = true,
    val darkTheme: Boolean = true
)

class BrowserPreferences(private val context: Context) {

    private object Keys {
        val TOOLBAR_AT_BOTTOM = booleanPreferencesKey("toolbar_at_bottom")
        val SHIELD_ENABLED = booleanPreferencesKey("shield_enabled")
        val AUTO_GAMING_MODE = booleanPreferencesKey("auto_gaming_mode")
        val SHOW_HUD = booleanPreferencesKey("show_hud")
        val LOCK_MAX_REFRESH = booleanPreferencesKey("lock_max_refresh")
        val UNBUFFERED_TOUCH = booleanPreferencesKey("unbuffered_touch")
        val SUSTAINED_PERF = booleanPreferencesKey("sustained_perf")
        val FREEZE_BG_TABS = booleanPreferencesKey("freeze_bg_tabs")
        val HARDWARE_COMPOSITING = booleanPreferencesKey("hardware_compositing")
        val WEBGL_HIGH_PERF = booleanPreferencesKey("webgl_high_perf")
        val WEBGL_DISABLE_AA = booleanPreferencesKey("webgl_disable_aa")
        val RENDER_SCALE = intPreferencesKey("render_scale")
        val DESKTOP_UA = booleanPreferencesKey("desktop_ua")
        val HTTPS_ONLY = booleanPreferencesKey("https_only")
        val DARK_THEME = booleanPreferencesKey("dark_theme")
    }

    val settingsFlow: Flow<BrowserSettings> = context.barePrefsDataStore.data.map { prefs: Preferences ->
        BrowserSettings(
            toolbarAtBottom = prefs[Keys.TOOLBAR_AT_BOTTOM] ?: true,
            shieldEnabled = prefs[Keys.SHIELD_ENABLED] ?: true,
            autoGamingMode = prefs[Keys.AUTO_GAMING_MODE] ?: true,
            showHudInGamingMode = prefs[Keys.SHOW_HUD] ?: true,
            lockMaxRefreshRate = prefs[Keys.LOCK_MAX_REFRESH] ?: true,
            unbufferedTouchInput = prefs[Keys.UNBUFFERED_TOUCH] ?: true,
            sustainedPerformanceMode = prefs[Keys.SUSTAINED_PERF] ?: true,
            freezeBackgroundTabs = prefs[Keys.FREEZE_BG_TABS] ?: true,
            hardwareCompositing = prefs[Keys.HARDWARE_COMPOSITING] ?: true,
            webGlHighPerformance = prefs[Keys.WEBGL_HIGH_PERF] ?: true,
            webGlDisableAntialias = prefs[Keys.WEBGL_DISABLE_AA] ?: false,
            renderScalePercent = (prefs[Keys.RENDER_SCALE] ?: 100).coerceIn(70, 100),
            desktopUserAgent = prefs[Keys.DESKTOP_UA] ?: false,
            httpsOnly = prefs[Keys.HTTPS_ONLY] ?: true,
            darkTheme = prefs[Keys.DARK_THEME] ?: true
        )
    }

    suspend fun update(transform: (BrowserSettings) -> BrowserSettings) {
        context.barePrefsDataStore.edit { prefs ->
            val current = BrowserSettings(
                toolbarAtBottom = prefs[Keys.TOOLBAR_AT_BOTTOM] ?: true,
                shieldEnabled = prefs[Keys.SHIELD_ENABLED] ?: true,
                autoGamingMode = prefs[Keys.AUTO_GAMING_MODE] ?: true,
                showHudInGamingMode = prefs[Keys.SHOW_HUD] ?: true,
                lockMaxRefreshRate = prefs[Keys.LOCK_MAX_REFRESH] ?: true,
                unbufferedTouchInput = prefs[Keys.UNBUFFERED_TOUCH] ?: true,
                sustainedPerformanceMode = prefs[Keys.SUSTAINED_PERF] ?: true,
                freezeBackgroundTabs = prefs[Keys.FREEZE_BG_TABS] ?: true,
                hardwareCompositing = prefs[Keys.HARDWARE_COMPOSITING] ?: true,
                webGlHighPerformance = prefs[Keys.WEBGL_HIGH_PERF] ?: true,
                webGlDisableAntialias = prefs[Keys.WEBGL_DISABLE_AA] ?: false,
                renderScalePercent = (prefs[Keys.RENDER_SCALE] ?: 100).coerceIn(70, 100),
                desktopUserAgent = prefs[Keys.DESKTOP_UA] ?: false,
                httpsOnly = prefs[Keys.HTTPS_ONLY] ?: true,
                darkTheme = prefs[Keys.DARK_THEME] ?: true
            )
            val next = transform(current)
            prefs[Keys.TOOLBAR_AT_BOTTOM] = next.toolbarAtBottom
            prefs[Keys.SHIELD_ENABLED] = next.shieldEnabled
            prefs[Keys.AUTO_GAMING_MODE] = next.autoGamingMode
            prefs[Keys.SHOW_HUD] = next.showHudInGamingMode
            prefs[Keys.LOCK_MAX_REFRESH] = next.lockMaxRefreshRate
            prefs[Keys.UNBUFFERED_TOUCH] = next.unbufferedTouchInput
            prefs[Keys.SUSTAINED_PERF] = next.sustainedPerformanceMode
            prefs[Keys.FREEZE_BG_TABS] = next.freezeBackgroundTabs
            prefs[Keys.HARDWARE_COMPOSITING] = next.hardwareCompositing
            prefs[Keys.WEBGL_HIGH_PERF] = next.webGlHighPerformance
            prefs[Keys.WEBGL_DISABLE_AA] = next.webGlDisableAntialias
            prefs[Keys.RENDER_SCALE] = next.renderScalePercent.coerceIn(70, 100)
            prefs[Keys.DESKTOP_UA] = next.desktopUserAgent
            prefs[Keys.HTTPS_ONLY] = next.httpsOnly
            prefs[Keys.DARK_THEME] = next.darkTheme
        }
    }
}
