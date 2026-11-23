package com.example.data

import android.content.Context
import androidx.datastore.preferences.core.Preferences
import androidx.datastore.preferences.core.booleanPreferencesKey
import androidx.datastore.preferences.core.edit
import androidx.datastore.preferences.core.intPreferencesKey
import androidx.datastore.preferences.core.stringPreferencesKey
import androidx.datastore.preferences.preferencesDataStore
import kotlinx.coroutines.flow.Flow
import kotlinx.coroutines.flow.map

private val Context.pulsePrefsStore by preferencesDataStore(name = "pulsebrowser_v2_prefs")

data class PulseSettings(
    val darkTheme: Boolean = true,
    val toolbarAtBottom: Boolean = true,
    val searchEngine: String = "duckduckgo",
    val httpsUpgrade: Boolean = true,
    // Keep third-party cookies enabled by default so LastHavens login & CDN MPQ asset auth do not fail;
    // user can restrict in Privacy Settings anytime.
    val allowThirdPartyCookies: Boolean = true,
    // Removes Android WebView in-app browser markers (; wv, Version/4.0, "Android WebView" Client Hints,
    // X-Requested-With) so browser games like LastHavens run natively in PulseBrowser 2.0.
    val standaloneChromeCompat: Boolean = true,
    val defaultDesktopUserAgent: Boolean = false,
    val hideUiInGamingMode: Boolean = true,
    val keepScreenOnInGamingMode: Boolean = true,
    val lockLandscapeInGamingMode: Boolean = true,
    val unbufferedTouchInput: Boolean = true,
    val freezeBackgroundTabsInGaming: Boolean = true,
    val autoApplySiteProfiles: Boolean = true,
    val showDiagnosticHudInGaming: Boolean = true,
    // 0 = Auto highest supported display mode, or explicit 60 / 90 / 120 Hz if supported by panel
    val preferredRefreshHz: Int = 0,
    // 100 = Native untouched (recommended default for WebGL2 / LastHavens stability)
    val safeRenderScalePercent: Int = 100
)

class PulsePreferences(private val context: Context) {

    private object Keys {
        val DARK_THEME = booleanPreferencesKey("dark_theme")
        val TOOLBAR_BOTTOM = booleanPreferencesKey("toolbar_bottom")
        val SEARCH_ENGINE = stringPreferencesKey("search_engine")
        val HTTPS_UPGRADE = booleanPreferencesKey("https_upgrade")
        val THIRD_PARTY_COOKIES = booleanPreferencesKey("third_party_cookies")
        val STANDALONE_CHROME_COMPAT = booleanPreferencesKey("standalone_chrome_compat")
        val DESKTOP_UA = booleanPreferencesKey("desktop_ua")
        val HIDE_UI_GAMING = booleanPreferencesKey("hide_ui_gaming")
        val KEEP_SCREEN_ON = booleanPreferencesKey("keep_screen_on")
        val LOCK_LANDSCAPE = booleanPreferencesKey("lock_landscape")
        val UNBUFFERED_TOUCH = booleanPreferencesKey("unbuffered_touch")
        val FREEZE_BG_TABS = booleanPreferencesKey("freeze_bg_tabs")
        val AUTO_SITE_PROFILES = booleanPreferencesKey("auto_site_profiles")
        val SHOW_HUD_GAMING = booleanPreferencesKey("show_hud_gaming")
        val PREFERRED_HZ = intPreferencesKey("preferred_hz")
        val SAFE_SCALE = intPreferencesKey("safe_scale")
    }

    val settingsFlow: Flow<PulseSettings> = context.pulsePrefsStore.data.map(::fromPrefs)

    suspend fun update(transform: (PulseSettings) -> PulseSettings) {
        context.pulsePrefsStore.edit { prefs ->
            val next = transform(fromPrefs(prefs))
            prefs[Keys.DARK_THEME] = next.darkTheme
            prefs[Keys.TOOLBAR_BOTTOM] = next.toolbarAtBottom
            prefs[Keys.SEARCH_ENGINE] = next.searchEngine
            prefs[Keys.HTTPS_UPGRADE] = next.httpsUpgrade
            prefs[Keys.THIRD_PARTY_COOKIES] = next.allowThirdPartyCookies
            prefs[Keys.STANDALONE_CHROME_COMPAT] = next.standaloneChromeCompat
            prefs[Keys.DESKTOP_UA] = next.defaultDesktopUserAgent
            prefs[Keys.HIDE_UI_GAMING] = next.hideUiInGamingMode
            prefs[Keys.KEEP_SCREEN_ON] = next.keepScreenOnInGamingMode
            prefs[Keys.LOCK_LANDSCAPE] = next.lockLandscapeInGamingMode
            prefs[Keys.UNBUFFERED_TOUCH] = next.unbufferedTouchInput
            prefs[Keys.FREEZE_BG_TABS] = next.freezeBackgroundTabsInGaming
            prefs[Keys.AUTO_SITE_PROFILES] = next.autoApplySiteProfiles
            prefs[Keys.SHOW_HUD_GAMING] = next.showDiagnosticHudInGaming
            prefs[Keys.PREFERRED_HZ] = next.preferredRefreshHz
            prefs[Keys.SAFE_SCALE] = next.safeRenderScalePercent.coerceIn(75, 100)
        }
    }

    private fun fromPrefs(prefs: Preferences): PulseSettings = PulseSettings(
        darkTheme = prefs[Keys.DARK_THEME] ?: true,
        toolbarAtBottom = prefs[Keys.TOOLBAR_BOTTOM] ?: true,
        searchEngine = prefs[Keys.SEARCH_ENGINE] ?: "duckduckgo",
        httpsUpgrade = prefs[Keys.HTTPS_UPGRADE] ?: true,
        allowThirdPartyCookies = prefs[Keys.THIRD_PARTY_COOKIES] ?: true,
        standaloneChromeCompat = prefs[Keys.STANDALONE_CHROME_COMPAT] ?: true,
        defaultDesktopUserAgent = prefs[Keys.DESKTOP_UA] ?: false,
        hideUiInGamingMode = prefs[Keys.HIDE_UI_GAMING] ?: true,
        keepScreenOnInGamingMode = prefs[Keys.KEEP_SCREEN_ON] ?: true,
        lockLandscapeInGamingMode = prefs[Keys.LOCK_LANDSCAPE] ?: true,
        unbufferedTouchInput = prefs[Keys.UNBUFFERED_TOUCH] ?: true,
        freezeBackgroundTabsInGaming = prefs[Keys.FREEZE_BG_TABS] ?: true,
        autoApplySiteProfiles = prefs[Keys.AUTO_SITE_PROFILES] ?: true,
        showDiagnosticHudInGaming = prefs[Keys.SHOW_HUD_GAMING] ?: true,
        preferredRefreshHz = prefs[Keys.PREFERRED_HZ] ?: 0,
        safeRenderScalePercent = (prefs[Keys.SAFE_SCALE] ?: 100).coerceIn(75, 100)
    )
}
