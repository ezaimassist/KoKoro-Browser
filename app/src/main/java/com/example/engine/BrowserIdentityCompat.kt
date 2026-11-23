package com.example.engine

import android.content.Context
import android.os.Build
import android.webkit.WebSettings
import android.webkit.WebView
import androidx.webkit.ScriptHandler
import androidx.webkit.UserAgentMetadata
import androidx.webkit.WebSettingsCompat
import androidx.webkit.WebViewCompat
import androidx.webkit.WebViewFeature

/**
 * Configures Android System WebView (Chromium) so PulseBrowser 2.0 identifies as a
 * standalone Chromium/Chrome browser rather than a restricted social-app in-app WebView.
 *
 * Specifically addresses the 4 signals websites (including LastHavens) check to show
 * "This app's built-in browser can't run the game. Open in Safari or Chrome":
 *  1. User-Agent string tokens (`; wv)` and `Version/4.0`).
 *  2. User-Agent Client Hints (`Sec-CH-UA` HTTP header & `navigator.userAgentData.brands`
 *     reporting `"Android WebView"`).
 *  3. `X-Requested-With` package header sent by Android WebView.
 *  4. Missing `window.chrome` object or residual `"Android WebView"` brand at `document_start`.
 *
 * Never intercepts network responses or alters WebGL, WebAssembly, SharedArrayBuffer, or canvas APIs.
 */
object BrowserIdentityCompat {

    data class ChromiumVersionInfo(
        val major: String,
        val full: String
    )

    private val CHROME_VERSION_REGEX = Regex("""Chrome/(\d+)(?:\.(\d+\.\d+\.\d+))?""")

    fun extractChromiumVersion(rawDefaultUa: String?): ChromiumVersionInfo {
        val match = rawDefaultUa?.let { CHROME_VERSION_REGEX.find(it) }
        val major = match?.groupValues?.getOrNull(1)?.takeIf { it.isNotBlank() } ?: "131"
        val rest = match?.groupValues?.getOrNull(2)?.takeIf { it.isNotBlank() }
        val full = if (rest != null) "$major.$rest" else "$major.0.0.0"
        return ChromiumVersionInfo(major = major, full = full)
    }

    /**
     * Builds a clean Chrome-compatible User-Agent string derived from the device's real
     * installed Chromium engine version, stripping `; wv)` and `Version/4.0`.
     */
    fun buildUserAgent(
        rawDefaultUa: String?,
        desktopMode: Boolean,
        standaloneChromeCompat: Boolean
    ): String {
        val ver = extractChromiumVersion(rawDefaultUa)
        if (desktopMode) {
            return "Mozilla/5.0 (X11; Linux x86_64) AppleWebKit/537.36 (KHTML, like Gecko) Chrome/${ver.major}.0.0.0 Safari/537.36"
        }
        if (!standaloneChromeCompat) {
            return rawDefaultUa?.takeIf { it.isNotBlank() }
                ?: "Mozilla/5.0 (Linux; Android 10; K) AppleWebKit/537.36 (KHTML, like Gecko) Chrome/${ver.major}.0.0.0 Mobile Safari/537.36"
        }
        // Standard modern Chrome on Android User-Agent (Chrome 110+ reduced format):
        // zero `; wv)`, zero `Version/4.0`, preserves actual Chromium major version.
        return "Mozilla/5.0 (Linux; Android 10; K) AppleWebKit/537.36 (KHTML, like Gecko) Chrome/${ver.major}.0.0.0 Mobile Safari/537.36"
    }

    /**
     * Applies User-Agent string, User-Agent Client Hints (`Sec-CH-UA` / `navigator.userAgentData`),
     * and `X-Requested-With` header suppression directly on Chromium WebSettings.
     */
    fun applySettings(
        context: Context,
        webSettings: WebSettings,
        desktopMode: Boolean,
        standaloneChromeCompat: Boolean
    ) {
        val rawDefaultUa = runCatching { WebSettings.getDefaultUserAgent(context) }.getOrNull()
        val version = extractChromiumVersion(rawDefaultUa)
        val desiredUa = buildUserAgent(
            rawDefaultUa = rawDefaultUa,
            desktopMode = desktopMode,
            standaloneChromeCompat = standaloneChromeCompat
        )
        if (webSettings.userAgentString != desiredUa) {
            webSettings.userAgentString = desiredUa
        }

        // 1. Suppress `X-Requested-With: <packageName>` header so servers do not classify
        //    PulseBrowser 2.0 as an embedded third-party app WebView.
        if (standaloneChromeCompat &&
            WebViewFeature.isFeatureSupported(WebViewFeature.REQUESTED_WITH_HEADER_ALLOW_LIST)
        ) {
            runCatching {
                WebSettingsCompat.setRequestedWithHeaderOriginAllowList(webSettings, emptySet())
            }
        }

        // 2. Replace `"Android WebView"` brand in Sec-CH-UA & navigator.userAgentData.brands
        //    with standard `"Google Chrome"` + `"Chromium"` using the real installed Chromium version.
        if (standaloneChromeCompat &&
            WebViewFeature.isFeatureSupported(WebViewFeature.USER_AGENT_METADATA)
        ) {
            runCatching {
                val brands = listOf(
                    UserAgentMetadata.BrandVersion.Builder()
                        .setBrand("Not)A;Brand")
                        .setMajorVersion("99")
                        .setFullVersion("99.0.0.0")
                        .build(),
                    UserAgentMetadata.BrandVersion.Builder()
                        .setBrand("Chromium")
                        .setMajorVersion(version.major)
                        .setFullVersion(version.full)
                        .build(),
                    UserAgentMetadata.BrandVersion.Builder()
                        .setBrand("Google Chrome")
                        .setMajorVersion(version.major)
                        .setFullVersion(version.full)
                        .build()
                )
                val builder = UserAgentMetadata.Builder()
                    .setBrandVersionList(brands)
                    .setFullVersion(version.full)
                    .setMobile(!desktopMode)

                if (desktopMode) {
                    builder
                        .setPlatform("Linux")
                        .setPlatformVersion("6.6.0")
                        .setArchitecture("x86")
                        .setBitness(64)
                        .setModel("")
                } else {
                    builder
                        .setPlatform("Android")
                        .setPlatformVersion(Build.VERSION.RELEASE ?: "14")
                        .setModel("")
                }

                WebSettingsCompat.setUserAgentMetadata(webSettings, builder.build())
            }
        }
    }

    /**
     * Installs a minimal `document_start` script (runs before any page JS executes) that:
     *  - Ensures `window.chrome` exists if undefined (matching standalone Chrome on Android).
     *  - Sanitizes `navigator.userAgentData.brands` / `getHighEntropyValues()` ONLY if
     *    `"Android WebView"` is still exposed on older WebView builds.
     *
     * Strictly avoids modifying WebGL, WebAssembly, canvas, SharedArrayBuffer, Workers, or fetch.
     */
    fun attachDocumentStartCompatScript(webView: WebView): ScriptHandler? {
        if (!WebViewFeature.isFeatureSupported(WebViewFeature.DOCUMENT_START_SCRIPT)) {
            return null
        }
        return runCatching {
            WebViewCompat.addDocumentStartJavaScript(
                webView,
                DOCUMENT_START_CHROME_COMPAT_JS,
                setOf("https://*", "http://*")
            )
        }.getOrNull()
    }

    val DOCUMENT_START_CHROME_COMPAT_JS: String = """
        (function() {
          try {
            if (typeof window.chrome === 'undefined') {
              Object.defineProperty(window, 'chrome', {
                value: {
                  loadTimes: function() { return {}; },
                  csi: function() { return {}; }
                },
                writable: true,
                enumerable: true,
                configurable: true
              });
            }
            var uad = navigator.userAgentData;
            if (uad && Array.isArray(uad.brands)) {
              var hasWvBrand = uad.brands.some(function(b) {
                return b && typeof b.brand === 'string' && /webview/i.test(b.brand);
              });
              if (hasWvBrand) {
                var cleanBrands = uad.brands.map(function(b) {
                  if (b && typeof b.brand === 'string' && /webview/i.test(b.brand)) {
                    return { brand: 'Google Chrome', version: b.version };
                  }
                  return b;
                });
                var wrappedUad = {
                  brands: cleanBrands,
                  mobile: uad.mobile,
                  platform: uad.platform,
                  toJSON: function() {
                    return { brands: cleanBrands, mobile: uad.mobile, platform: uad.platform };
                  },
                  getHighEntropyValues: function(hints) {
                    return uad.getHighEntropyValues(hints).then(function(vals) {
                      var copy = Object.assign({}, vals);
                      copy.brands = cleanBrands;
                      if (Array.isArray(copy.fullVersionList)) {
                        copy.fullVersionList = copy.fullVersionList.map(function(b) {
                          if (b && typeof b.brand === 'string' && /webview/i.test(b.brand)) {
                            return { brand: 'Google Chrome', version: b.version };
                          }
                          return b;
                        });
                      }
                      return copy;
                    });
                  }
                };
                Object.defineProperty(Navigator.prototype, 'userAgentData', {
                  get: function() { return wrappedUad; },
                  configurable: true
                });
              }
            }
          } catch (e) {}
        })();
    """.trimIndent()
}
