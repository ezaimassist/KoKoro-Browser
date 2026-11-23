package com.example

import android.content.Context
import android.net.Uri
import androidx.test.core.app.ApplicationProvider
import com.example.data.PulseRepository
import com.example.engine.DiagnosticCategory
import com.example.engine.DiagnosticsCollector
import com.example.engine.UrlUtils
import org.junit.Assert.assertEquals
import org.junit.Assert.assertFalse
import org.junit.Assert.assertTrue
import org.junit.Test
import org.junit.runner.RunWith
import org.robolectric.RobolectricTestRunner
import org.robolectric.annotation.Config

@RunWith(RobolectricTestRunner::class)
@Config(sdk = [36])
class ExampleRobolectricTest {

    @Test
    fun appNameMatchesPulseBrowser2() {
        val context = ApplicationProvider.getApplicationContext<Context>()
        assertEquals("PulseBrowser 2.0", context.getString(R.string.app_name))
    }

    @Test
    fun lastHavensUrlPreservesQueryParametersAndAllowedScheme() {
        val url = UrlUtils.normalizeUserInput(
            PulseRepository.LASTHAVENS_PLAY_URL,
            httpsUpgrade = true,
            searchEngine = "duckduckgo"
        )
        assertEquals("https://alt.lasthavens.com/play?stats=1&log=1", url)
        assertTrue(UrlUtils.isAllowedNavigationUri(Uri.parse(url)))
        assertEquals("alt.lasthavens.com", UrlUtils.extractHost(url))
    }

    @Test
    fun dangerousSchemesAreBlocked() {
        assertFalse(UrlUtils.isAllowedNavigationUri(Uri.parse("javascript:alert(1)")))
        assertFalse(UrlUtils.isAllowedNavigationUri(Uri.parse("file:///etc/hosts")))
        assertFalse(UrlUtils.isAllowedNavigationUri(Uri.parse("content://settings/secure")))
        val normalized = UrlUtils.normalizeUserInput("javascript:alert(1)", true, "duckduckgo")
        assertTrue(normalized.startsWith("https://duckduckgo.com/?q="))
    }

    @Test
    fun diagnosticsClassifiesWasmAndMpqFailuresAccurately() {
        val context = ApplicationProvider.getApplicationContext<Context>()
        val collector = DiagnosticsCollector(context)

        collector.recordConsoleMessage(
            level = "ERROR",
            message = "CompileError: WebAssembly.instantiateStreaming() failed: expected magic word",
            sourceId = "https://alt.lasthavens.com/play",
            lineNumber = 42
        )
        collector.recordHttpError(
            url = "https://alt.lasthavens.com/assets/patch-3.mpq",
            statusCode = 404,
            reason = "Not Found",
            mimeType = "application/octet-stream"
        )
        collector.recordRendererCrash(
            tabTitle = "LastHavens",
            url = PulseRepository.LASTHAVENS_PLAY_URL,
            didCrash = false,
            priorityAtExit = 2
        )

        val state = collector.state.value
        assertEquals(1, state.wasmErrorsCount)
        assertEquals(1, state.httpErrorsCount)
        assertEquals(1, state.crashOrMemoryCount)
        assertTrue(state.events.any { it.category == DiagnosticCategory.WASM_ERROR })
        assertTrue(state.events.any { it.category == DiagnosticCategory.MPQ_OR_ASSET_ERROR })
        assertTrue(state.events.any { it.category == DiagnosticCategory.RENDERER_CRASH })
    }
}
