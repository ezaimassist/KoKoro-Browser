package com.example

import com.example.data.PulseRepository
import com.example.engine.BrowserIdentityCompat
import com.example.engine.UrlUtils
import org.junit.Assert.assertEquals
import org.junit.Assert.assertFalse
import org.junit.Assert.assertTrue
import org.junit.Test

class ExampleUnitTest {

    @Test
    fun lastHavensPlayUrlIsPreservedExactly() {
        val normalized = UrlUtils.normalizeUserInput(
            rawInput = PulseRepository.LASTHAVENS_PLAY_URL,
            httpsUpgrade = true,
            searchEngine = "duckduckgo"
        )
        assertEquals("https://alt.lasthavens.com/play?stats=1&log=1", normalized)
        assertTrue(UrlUtils.isSecureUrl(normalized))
    }

    @Test
    fun httpUrlUpgradesToHttpsWhenEnabled() {
        val normalized = UrlUtils.normalizeUserInput(
            rawInput = "http://alt.lasthavens.com/play?stats=1&log=1",
            httpsUpgrade = true,
            searchEngine = "duckduckgo"
        )
        assertEquals("https://alt.lasthavens.com/play?stats=1&log=1", normalized)
    }

    @Test
    fun blockedSchemesRouteSafelyToSearch() {
        val jsResult = UrlUtils.normalizeUserInput("javascript:alert(1)", true, "duckduckgo")
        val fileResult = UrlUtils.normalizeUserInput("file:///etc/hosts", true, "duckduckgo")
        assertTrue(jsResult.startsWith("https://duckduckgo.com/?q="))
        assertTrue(fileResult.startsWith("https://duckduckgo.com/?q="))
        assertFalse(jsResult.startsWith("javascript:"))
        assertFalse(fileResult.startsWith("file:"))
    }

    @Test
    fun standaloneChromeUserAgentRemovesWebViewTokensAndKeepsChromiumMajor() {
        val rawWebViewUa =
            "Mozilla/5.0 (Linux; Android 14; Pixel 8 Build/AP2A.240805.005; wv) " +
                "AppleWebKit/537.36 (KHTML, like Gecko) Version/4.0 Chrome/134.0.6998.108 Mobile Safari/537.36"

        val mobileUa = BrowserIdentityCompat.buildUserAgent(
            rawDefaultUa = rawWebViewUa,
            desktopMode = false,
            standaloneChromeCompat = true
        )
        assertFalse(mobileUa.contains("; wv"))
        assertFalse(mobileUa.contains("Version/4.0"))
        assertTrue(mobileUa.contains("Chrome/134.0.0.0"))
        assertTrue(mobileUa.contains("Mobile Safari/537.36"))

        val desktopUa = BrowserIdentityCompat.buildUserAgent(
            rawDefaultUa = rawWebViewUa,
            desktopMode = true,
            standaloneChromeCompat = true
        )
        assertFalse(desktopUa.contains("; wv"))
        assertFalse(desktopUa.contains("Version/4.0"))
        assertFalse(desktopUa.contains("Mobile"))
        assertTrue(desktopUa.contains("Chrome/134.0.0.0"))
    }
}
