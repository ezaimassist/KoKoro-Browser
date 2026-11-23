package com.example.engine

data class EngineBottleneckFinding(
    val id: String,
    val subsystem: String,
    val severity: String,
    val title: String,
    val bottleneckAnalysis: String,
    val bareTurboOptimization: String,
    val chromiumFlags: String
)

object BareEngineAuditData {
    val findings: List<EngineBottleneckFinding> = listOf(
        EngineBottleneckFinding(
            id = "render_vsync_batch",
            subsystem = "Input & Compositor",
            severity = "CRITICAL",
            title = "VSync Input Batching & Double-Tap Gesture Delay",
            bottleneckAnalysis = "Upstream Chromium/Bare Browser coalesces Android MotionEvents on VSync boundaries and waits on touch-action heuristics, adding 16–40ms of input-to-photon latency in fast WebGL/HTML5 games.",
            bareTurboOptimization = "Enables View.requestUnbufferedDispatch() on the active WebView surface, injects touch-action: none on <canvas>, and requests desynchronized: true contexts to minimize compositor queueing.",
            chromiumFlags = "--enable-features=LowLatencyCanvas,AndroidSurfaceControl"
        ),
        EngineBottleneckFinding(
            id = "gpu_skia_vulkan",
            subsystem = "GPU Acceleration",
            severity = "HIGH",
            title = "OpenGL ES Driver Overhead vs. Vulkan / ANGLE & Skia Graphite",
            bottleneckAnalysis = "On Android devices where Bare Browser defaults to Skia Ganesh over legacy GLES, draw-call validation and shader compilation stalls cause micro-stutters. Fragment fill-rate saturates on 1440p+ mobile displays when MSAA is enabled.",
            bareTurboOptimization = "Enforces LAYER_TYPE_HARDWARE, Wide Color Gamut, WebGL powerPreference='high-performance', optional MSAA disable, and Dynamic Render Scale (85%/70% DPR) to cut GPU pixel load up to 51%.",
            chromiumFlags = "--enable-vulkan --use-angle=vulkan --enable-features=SkiaGraphite,ZeroCopyRasterizer"
        ),
        EngineBottleneckFinding(
            id = "renderer_priority",
            subsystem = "Process & Scheduler",
            severity = "HIGH",
            title = "Background Tab Contention & Renderer Priority Throttling",
            bottleneckAnalysis = "Multi-tab Chromium sessions keep background tab timers, ads, and extension service workers competing for big-core CPU cycles, L3 cache, and GPU command buffers—triggering thermal throttling.",
            bareTurboOptimization = "Gaming Mode elevates the active tab to RENDERER_PRIORITY_IMPORTANT (waivedWhenNotVisible=false), freezes all inactive tabs via onPause() + RENDERER_PRIORITY_WAIVED, and enables Sustained Performance Mode.",
            chromiumFlags = "--enable-features=IntensiveWakeUpThrottling,StopInBackground"
        ),
        EngineBottleneckFinding(
            id = "refresh_rate_cap",
            subsystem = "Display Pipeline",
            severity = "MEDIUM",
            title = "OEM 60Hz WebView Fallback on 90Hz/120Hz/144Hz Panels",
            bottleneckAnalysis = "Many Android OEMs dynamically drop browser windows to 60Hz when no native Android View scroll occurs, capping WebGL requestAnimationFrame even on 120Hz LTPO displays.",
            bareTurboOptimization = "Queries Display.supportedModes and locks WindowManager.LayoutParams.preferredDisplayModeId to the highest physical Hz mode while Gaming Mode or Max Refresh Lock is active.",
            chromiumFlags = "bare://flags/#enable-high-refresh-rate-raf"
        ),
        EngineBottleneckFinding(
            id = "network_telemetry",
            subsystem = "Network & Main Thread",
            severity = "MEDIUM",
            title = "Third-Party Ad/Telemetry Scripts Causing JS GC Spikes",
            bottleneckAnalysis = "Even on de-Googled browsers, third-party game portals inject ad auctions (Prebid), analytics beacons, and chat widgets that trigger V8 garbage-collection pauses mid-frame.",
            bareTurboOptimization = "NetworkShieldEngine intercepts and drops ad/telemetry requests in-flight via shouldInterceptRequest with zero-byte 204 responses, plus aggressive Gaming Mode filtering for chat & A/B scripts.",
            chromiumFlags = "GN: enable_reporting=false, google_api_key=\"\", enable_extensions=true"
        )
    )
}
