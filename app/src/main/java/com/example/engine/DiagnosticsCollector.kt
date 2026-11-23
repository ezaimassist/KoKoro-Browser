package com.example.engine

import android.app.ActivityManager
import android.content.Context
import android.os.Build
import android.os.Debug
import android.os.Handler
import android.os.Looper
import android.os.PowerManager
import android.view.Choreographer
import android.view.FrameMetrics
import android.view.Window
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.StateFlow
import kotlinx.coroutines.flow.asStateFlow
import java.text.SimpleDateFormat
import java.util.Date
import java.util.Locale

enum class DiagnosticCategory {
    CONSOLE_ERROR,
    CONSOLE_WARN,
    CONSOLE_LOG,
    WASM_ERROR,
    MPQ_OR_ASSET_ERROR,
    HTTP_ERROR,
    NETWORK_ERROR,
    TLS_ERROR,
    RENDERER_CRASH,
    MEMORY_PRESSURE
}

data class DiagnosticEvent(
    val id: Long,
    val timestampMs: Long,
    val category: DiagnosticCategory,
    val summary: String,
    val detail: String,
    val sourceUrl: String = ""
)

data class PageLoadTiming(
    val url: String,
    val startedAtMs: Long,
    val durationMs: Long,
    val completed: Boolean
)

data class ActiveWebGlProbeResult(
    val probedAtMs: Long = 0L,
    val webGl2Available: Boolean? = null,
    val webAssemblyAvailable: Boolean? = null,
    val sharedArrayBufferAvailable: Boolean? = null,
    val renderer: String = "Not probed yet",
    val vendor: String = "—",
    val version: String = "—",
    val maxTextureSize: String = "—",
    val devicePixelRatio: String = "—",
    val effectiveUserAgent: String = "—",
    val clientHintsBrands: String = "—",
    val hasWindowChrome: Boolean? = null
)

data class FrameMetricsSnapshot(
    val hostCompositorFps: Int = 0,
    val choreographerAvgFrameMs: Float = 0f,
    val frameMetricsP95Ms: Float = 0f,
    val frameMetricsSwapAvgMs: Float = 0f,
    val jankFrameCount: Int = 0,
    val recentFrameMs: List<Float> = emptyList(),
    val measurementMethod: String = "Android Choreographer + Window.OnFrameMetricsAvailableListener (Host Compositor — use game ?stats=1 HUD for internal WebGL FPS)",
    val appHeapMb: Int = 0,
    val availableRamMb: Long = 0L,
    val lowMemoryFlag: Boolean = false,
    val thermalStatusLabel: String = "Nominal"
)

data class DiagnosticsState(
    val events: List<DiagnosticEvent> = emptyList(),
    val timings: List<PageLoadTiming> = emptyList(),
    val webGlProbe: ActiveWebGlProbeResult = ActiveWebGlProbeResult(),
    val frameMetrics: FrameMetricsSnapshot = FrameMetricsSnapshot(),
    val requestedDisplayHz: Float = 60f,
    val actualDisplayHz: Float = 60f,
    val totalErrorsCount: Int = 0,
    val wasmErrorsCount: Int = 0,
    val httpErrorsCount: Int = 0,
    val crashOrMemoryCount: Int = 0
)

class DiagnosticsCollector(private val context: Context) {

    private val mainHandler = Handler(Looper.getMainLooper())
    private val lock = Any()
    private var nextEventId = 1L

    private val eventsBuffer = ArrayDeque<DiagnosticEvent>(200)
    private val timingsBuffer = ArrayDeque<PageLoadTiming>(40)

    private val _state = MutableStateFlow(DiagnosticsState())
    val state: StateFlow<DiagnosticsState> = _state.asStateFlow()

    @Volatile
    private var choreographerRunning = false
    private var lastFrameNanos = 0L
    private var windowStartNanos = 0L
    private var framesInWindow = 0
    private var sumChoreoMs = 0f
    private val choreoHistory = ArrayDeque<Float>(36)

    // Real Window.OnFrameMetricsAvailableListener metrics (API 24+)
    private val fmTotalRing = ArrayDeque<Float>(60)
    private val fmSwapRing = ArrayDeque<Float>(60)
    private var fmJankCount = 0
    private var attachedWindow: Window? = null
    private var frameMetricsListener: Window.OnFrameMetricsAvailableListener? = null

    private val choreographerCallback = object : Choreographer.FrameCallback {
        override fun doFrame(frameTimeNanos: Long) {
            if (!choreographerRunning) return
            if (lastFrameNanos != 0L) {
                val deltaMs = (frameTimeNanos - lastFrameNanos) / 1_000_000f
                if (deltaMs in 1f..500f) {
                    framesInWindow++
                    sumChoreoMs += deltaMs
                    choreoHistory.addLast(deltaMs)
                    while (choreoHistory.size > 32) choreoHistory.removeFirst()
                }
            } else {
                windowStartNanos = frameTimeNanos
            }
            lastFrameNanos = frameTimeNanos

            val elapsedNs = frameTimeNanos - windowStartNanos
            if (elapsedNs >= 500_000_000L && framesInWindow > 0) {
                val fps = (framesInWindow / (elapsedNs / 1_000_000_000f)).toInt().coerceIn(1, 240)
                val avgMs = sumChoreoMs / framesInWindow
                publishFrameMetricsSnapshot(fps, avgMs)
                windowStartNanos = frameTimeNanos
                framesInWindow = 0
                sumChoreoMs = 0f
            }
            Choreographer.getInstance().postFrameCallback(this)
        }
    }

    fun startMonitoring() {
        if (choreographerRunning) return
        choreographerRunning = true
        lastFrameNanos = 0L
        windowStartNanos = 0L
        framesInWindow = 0
        sumChoreoMs = 0f
        mainHandler.post {
            if (choreographerRunning) {
                Choreographer.getInstance().postFrameCallback(choreographerCallback)
            }
        }
    }

    fun stopMonitoring() {
        choreographerRunning = false
        mainHandler.post {
            Choreographer.getInstance().removeFrameCallback(choreographerCallback)
        }
        detachFrameMetrics()
    }

    fun attachFrameMetrics(window: Window) {
        if (Build.VERSION.SDK_INT < Build.VERSION_CODES.N) return
        if (attachedWindow === window && frameMetricsListener != null) return
        detachFrameMetrics()
        val listener = Window.OnFrameMetricsAvailableListener { _, metrics, dropCount ->
            val totalMs = metrics.getMetric(FrameMetrics.TOTAL_DURATION) / 1_000_000f
            val swapMs = metrics.getMetric(FrameMetrics.SWAP_BUFFERS_DURATION) / 1_000_000f
            synchronized(lock) {
                if (totalMs in 0.1f..1000f) {
                    fmTotalRing.addLast(totalMs)
                    while (fmTotalRing.size > 60) fmTotalRing.removeFirst()
                }
                if (swapMs in 0f..500f) {
                    fmSwapRing.addLast(swapMs)
                    while (fmSwapRing.size > 60) fmSwapRing.removeFirst()
                }
                if (dropCount > 0 || totalMs > 25f) {
                    fmJankCount += dropCount.coerceAtLeast(1)
                }
            }
        }
        runCatching {
            window.addOnFrameMetricsAvailableListener(listener, mainHandler)
            attachedWindow = window
            frameMetricsListener = listener
        }
    }

    fun detachFrameMetrics() {
        val w = attachedWindow
        val l = frameMetricsListener
        if (w != null && l != null && Build.VERSION.SDK_INT >= Build.VERSION_CODES.N) {
            runCatching { w.removeOnFrameMetricsAvailableListener(l) }
        }
        attachedWindow = null
        frameMetricsListener = null
    }

    fun updateDisplayRefreshTelemetry(requestedHz: Float, actualHz: Float) {
        _state.value = _state.value.copy(
            requestedDisplayHz = requestedHz,
            actualDisplayHz = actualHz
        )
    }

    fun recordConsoleMessage(level: String, message: String, sourceId: String, lineNumber: Int) {
        val lower = message.lowercase()
        val category = when {
            lower.contains("webassembly") ||
                lower.contains("wasm") ||
                lower.contains("compileerror") ||
                lower.contains("instantiatestreaming") ||
                lower.contains("sharedarraybuffer") ||
                lower.contains("linkerror") -> DiagnosticCategory.WASM_ERROR

            lower.contains(".mpq") ||
                lower.contains("mpq") ||
                lower.contains("context_lost") ||
                lower.contains("webgl") && level.equals("ERROR", true) -> DiagnosticCategory.MPQ_OR_ASSET_ERROR

            level.equals("ERROR", true) -> DiagnosticCategory.CONSOLE_ERROR
            level.equals("WARNING", true) -> DiagnosticCategory.CONSOLE_WARN
            else -> DiagnosticCategory.CONSOLE_LOG
        }
        // Keep verbose console.log entries bounded so errors/warnings aren't drowned out
        addEvent(
            category = category,
            summary = "[$level] ${message.take(180)}",
            detail = "$message\nSource: $sourceId:$lineNumber",
            sourceUrl = sourceId
        )
    }

    fun recordHttpError(url: String, statusCode: Int, reason: String, mimeType: String) {
        val lowerUrl = url.lowercase()
        val cat = when {
            lowerUrl.contains(".wasm") -> DiagnosticCategory.WASM_ERROR
            lowerUrl.contains(".mpq") -> DiagnosticCategory.MPQ_OR_ASSET_ERROR
            else -> DiagnosticCategory.HTTP_ERROR
        }
        addEvent(
            category = cat,
            summary = "HTTP $statusCode $reason",
            detail = "URL: $url\nMIME: ${mimeType.ifBlank { "unknown" }}",
            sourceUrl = url
        )
    }

    fun recordNetworkError(url: String, errorCode: Int, description: String) {
        val lowerUrl = url.lowercase()
        val cat = when {
            lowerUrl.contains(".wasm") -> DiagnosticCategory.WASM_ERROR
            lowerUrl.contains(".mpq") -> DiagnosticCategory.MPQ_OR_ASSET_ERROR
            else -> DiagnosticCategory.NETWORK_ERROR
        }
        addEvent(
            category = cat,
            summary = "Network Error ($errorCode): $description",
            detail = "URL: $url",
            sourceUrl = url
        )
    }

    fun recordSslError(url: String, primaryError: Int) {
        addEvent(
            category = DiagnosticCategory.TLS_ERROR,
            summary = "TLS Certificate Blocked (code $primaryError)",
            detail = "Connection cancelled to preserve TLS integrity.\nURL: $url",
            sourceUrl = url
        )
    }

    fun recordRendererCrash(tabTitle: String, url: String, didCrash: Boolean, priorityAtExit: Int) {
        val cause = if (didCrash) "Renderer Process Crashed" else "Renderer Killed by System (Likely OOM / LMK)"
        addEvent(
            category = DiagnosticCategory.RENDERER_CRASH,
            summary = "$cause ($tabTitle)",
            detail = "didCrash=$didCrash, rendererPriorityAtExit=$priorityAtExit\nURL: $url",
            sourceUrl = url
        )
    }

    fun recordMemoryPressure(levelName: String) {
        val am = context.getSystemService(Context.ACTIVITY_SERVICE) as? ActivityManager
        val info = ActivityManager.MemoryInfo()
        am?.getMemoryInfo(info)
        val availMb = info.availMem / (1024L * 1024L)
        addEvent(
            category = DiagnosticCategory.MEMORY_PRESSURE,
            summary = "Android Memory Pressure: $levelName",
            detail = "Available System RAM: ${availMb} MB | lowMemory=${info.lowMemory}"
        )
    }

    fun recordPageLoadTiming(url: String, startedAtMs: Long, durationMs: Long, completed: Boolean) {
        synchronized(lock) {
            timingsBuffer.addFirst(PageLoadTiming(url, startedAtMs, durationMs.coerceAtLeast(0L), completed))
            while (timingsBuffer.size > 30) timingsBuffer.removeLast()
            _state.value = _state.value.copy(timings = timingsBuffer.toList())
        }
    }

    fun updateWebGlProbe(result: ActiveWebGlProbeResult) {
        _state.value = _state.value.copy(webGlProbe = result)
    }

    fun clearLogs() {
        synchronized(lock) {
            eventsBuffer.clear()
            timingsBuffer.clear()
            fmJankCount = 0
            _state.value = _state.value.copy(
                events = emptyList(),
                timings = emptyList(),
                totalErrorsCount = 0,
                wasmErrorsCount = 0,
                httpErrorsCount = 0,
                crashOrMemoryCount = 0
            )
        }
    }

    fun buildExportableReportText(
        device: DeviceCapabilitiesReport,
        activeUrl: String,
        gamingModeActive: Boolean
    ): String {
        val s = _state.value
        val fmt = SimpleDateFormat("yyyy-MM-dd HH:mm:ss z", Locale.US)
        val sb = StringBuilder()
        sb.appendLine("=== PulseBrowser 2.0 Diagnostic Report ===")
        sb.appendLine("Generated: ${fmt.format(Date())}")
        sb.appendLine("Active URL: $activeUrl")
        sb.appendLine("Gaming Mode Active: $gamingModeActive")
        sb.appendLine()
        sb.appendLine("--- Device & Chromium WebView Engine ---")
        sb.appendLine("Device: ${device.deviceModel} (${device.androidVersion})")
        sb.appendLine("CPU / ABI: ${device.cpuCores} cores (${device.primaryAbi}) | Total RAM: ${device.totalRamMb} MB")
        sb.appendLine("WebView Package: ${device.webViewPackage}")
        sb.appendLine("WebView Version: ${device.webViewVersion}")
        sb.appendLine("EGL Renderer: ${device.eglRenderer} (${device.eglVendor})")
        sb.appendLine("EGL / GLES: ${device.eglVersion} | ${device.glesConfigVersion} | MaxTex: ${device.maxTextureSize}")
        sb.appendLine("Vulkan Support: ${device.vulkanVersion}")
        sb.appendLine("Display Modes Supported: ${device.selectableRefreshRatesHz.joinToString(", ") { "${it}Hz" }}")
        sb.appendLine("Requested Display Hz: ${String.format(Locale.US, "%.1f", s.requestedDisplayHz)} Hz | Active Panel Hz: ${String.format(Locale.US, "%.1f", s.actualDisplayHz)} Hz")
        sb.appendLine()
        sb.appendLine("--- Active Page WebGL / WebAssembly Probe (Read-Only Detached Canvas) ---")
        val wp = s.webGlProbe
        sb.appendLine("WebGL2 Supported: ${wp.webGl2Available ?: "Not probed"}")
        sb.appendLine("WebAssembly Supported: ${wp.webAssemblyAvailable ?: "Not probed"}")
        sb.appendLine("SharedArrayBuffer Available (COOP/COEP): ${wp.sharedArrayBufferAvailable ?: "Not probed"}")
        sb.appendLine("WebGL Renderer: ${wp.renderer} (${wp.vendor})")
        sb.appendLine("WebGL Version: ${wp.version} | MaxTex: ${wp.maxTextureSize} | DPR: ${wp.devicePixelRatio}")
        sb.appendLine("Effective navigator.userAgent: ${wp.effectiveUserAgent}")
        sb.appendLine("Client Hints Brands: ${wp.clientHintsBrands} | window.chrome: ${wp.hasWindowChrome ?: "Not probed"}")
        sb.appendLine()
        sb.appendLine("--- Frame Pacing & Memory Telemetry ---")
        val fm = s.frameMetrics
        sb.appendLine("Measurement Method: ${fm.measurementMethod}")
        sb.appendLine("Host Compositor FPS: ${fm.hostCompositorFps} fps | Choreographer Avg: ${String.format(Locale.US, "%.2f", fm.choreographerAvgFrameMs)} ms")
        sb.appendLine("Window FrameMetrics p95: ${String.format(Locale.US, "%.2f", fm.frameMetricsP95Ms)} ms | Swap Avg: ${String.format(Locale.US, "%.2f", fm.frameMetricsSwapAvgMs)} ms | Jank Frames: ${fm.jankFrameCount}")
        sb.appendLine("App Heap: ${fm.appHeapMb} MB | Available System RAM: ${fm.availableRamMb} MB | LowMem: ${fm.lowMemoryFlag} | Thermal: ${fm.thermalStatusLabel}")
        sb.appendLine()
        sb.appendLine("--- Page Load Timings (${s.timings.size}) ---")
        s.timings.forEach { t ->
            sb.appendLine("[${fmt.format(Date(t.startedAtMs))}] ${t.durationMs}ms (completed=${t.completed}) -> ${t.url}")
        }
        sb.appendLine()
        sb.appendLine("--- Diagnostic Events (${s.events.size}) | Wasm=${s.wasmErrorsCount} | HTTP=${s.httpErrorsCount} | Crash/Mem=${s.crashOrMemoryCount} ---")
        s.events.forEach { ev ->
            sb.appendLine("[${fmt.format(Date(ev.timestampMs))}] [${ev.category}] ${ev.summary}")
            if (ev.detail.isNotBlank()) {
                sb.appendLine("  ${ev.detail.replace("\n", "\n  ")}")
            }
        }
        return sb.toString()
    }

    private fun addEvent(
        category: DiagnosticCategory,
        summary: String,
        detail: String,
        sourceUrl: String = ""
    ) {
        synchronized(lock) {
            val ev = DiagnosticEvent(
                id = nextEventId++,
                timestampMs = System.currentTimeMillis(),
                category = category,
                summary = summary,
                detail = detail,
                sourceUrl = sourceUrl
            )
            eventsBuffer.addFirst(ev)
            while (eventsBuffer.size > 200) eventsBuffer.removeLast()

            val list = eventsBuffer.toList()
            val wasmCnt = list.count { it.category == DiagnosticCategory.WASM_ERROR }
            val httpCnt = list.count {
                it.category == DiagnosticCategory.HTTP_ERROR ||
                    it.category == DiagnosticCategory.NETWORK_ERROR ||
                    it.category == DiagnosticCategory.MPQ_OR_ASSET_ERROR
            }
            val crashMemCnt = list.count {
                it.category == DiagnosticCategory.RENDERER_CRASH ||
                    it.category == DiagnosticCategory.MEMORY_PRESSURE
            }
            val errCnt = list.count {
                it.category != DiagnosticCategory.CONSOLE_LOG &&
                    it.category != DiagnosticCategory.CONSOLE_WARN
            }

            _state.value = _state.value.copy(
                events = list,
                totalErrorsCount = errCnt,
                wasmErrorsCount = wasmCnt,
                httpErrorsCount = httpCnt,
                crashOrMemoryCount = crashMemCnt
            )
        }
    }

    private fun publishFrameMetricsSnapshot(choreoFps: Int, choreoAvgMs: Float) {
        val am = context.getSystemService(Context.ACTIVITY_SERVICE) as? ActivityManager
        val memInfo = ActivityManager.MemoryInfo()
        am?.getMemoryInfo(memInfo)
        val usedHeap = (Runtime.getRuntime().totalMemory() - Runtime.getRuntime().freeMemory()) +
            Debug.getNativeHeapAllocatedSize()
        val heapMb = (usedHeap / (1024L * 1024L)).toInt().coerceAtLeast(1)
        val availMb = memInfo.availMem / (1024L * 1024L)

        val thermal = if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.Q) {
            val pm = context.getSystemService(Context.POWER_SERVICE) as? PowerManager
            when (pm?.currentThermalStatus ?: PowerManager.THERMAL_STATUS_NONE) {
                PowerManager.THERMAL_STATUS_NONE -> "Nominal"
                PowerManager.THERMAL_STATUS_LIGHT -> "Light"
                PowerManager.THERMAL_STATUS_MODERATE -> "Moderate"
                PowerManager.THERMAL_STATUS_SEVERE -> "Severe"
                else -> "Critical"
            }
        } else {
            "Nominal"
        }

        val p95: Float
        val swapAvg: Float
        val janks: Int
        synchronized(lock) {
            p95 = if (fmTotalRing.isEmpty()) choreoAvgMs else {
                val sorted = fmTotalRing.sorted()
                sorted[((sorted.size * 0.95).toInt()).coerceIn(0, sorted.lastIndex)]
            }
            swapAvg = if (fmSwapRing.isEmpty()) 0f else fmSwapRing.average().toFloat()
            janks = fmJankCount
        }

        _state.value = _state.value.copy(
            frameMetrics = FrameMetricsSnapshot(
                hostCompositorFps = choreoFps,
                choreographerAvgFrameMs = choreoAvgMs,
                frameMetricsP95Ms = p95,
                frameMetricsSwapAvgMs = swapAvg,
                jankFrameCount = janks,
                recentFrameMs = choreoHistory.toList(),
                appHeapMb = heapMb,
                availableRamMb = availMb,
                lowMemoryFlag = memInfo.lowMemory,
                thermalStatusLabel = thermal
            )
        )
    }

    companion object {
        /**
         * Strictly READ-ONLY diagnostic probe using a temporary detached 1x1 canvas.
         * Never touches, alters, or monkey-patches the page's active game canvas or WebGL context.
         */
        val READ_ONLY_WEBGL_WASM_PROBE_JS = """
            (function() {
              try {
                var c = document.createElement('canvas');
                c.width = 1; c.height = 1;
                var gl = c.getContext('webgl2') || c.getContext('webgl');
                var webgl2 = !!(typeof WebGL2RenderingContext !== 'undefined' && gl instanceof WebGL2RenderingContext);
                var renderer = 'Unavailable';
                var vendor = 'Unavailable';
                var version = 'Unavailable';
                var maxTex = '0';
                if (gl) {
                  var dbg = gl.getExtension('WEBGL_debug_renderer_info');
                  renderer = dbg ? String(gl.getParameter(dbg.UNMASKED_RENDERER_WEBGL)) : String(gl.getParameter(gl.RENDERER));
                  vendor = dbg ? String(gl.getParameter(dbg.UNMASKED_VENDOR_WEBGL)) : String(gl.getParameter(gl.VENDOR));
                  version = String(gl.getParameter(gl.VERSION));
                  maxTex = String(gl.getParameter(gl.MAX_TEXTURE_SIZE));
                  var lose = gl.getExtension('WEBGL_lose_context');
                  if (lose) lose.loseContext();
                }
                var brandsStr = 'Unavailable';
                if (navigator.userAgentData && Array.isArray(navigator.userAgentData.brands)) {
                  brandsStr = navigator.userAgentData.brands.map(function(b) {
                    return b.brand + '/' + b.version;
                  }).join(', ');
                }
                return JSON.stringify({
                  webgl2: webgl2,
                  wasm: typeof WebAssembly === 'object' && typeof WebAssembly.instantiateStreaming === 'function',
                  sab: typeof SharedArrayBuffer !== 'undefined',
                  renderer: renderer,
                  vendor: vendor,
                  version: version,
                  maxTex: maxTex,
                  dpr: String(window.devicePixelRatio || 1),
                  ua: String(navigator.userAgent || ''),
                  brands: brandsStr,
                  hasChrome: typeof window.chrome !== 'undefined'
                });
              } catch (e) {
                return JSON.stringify({ error: String(e) });
              }
            })();
        """.trimIndent()
    }
}
