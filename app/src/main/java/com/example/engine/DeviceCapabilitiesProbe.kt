package com.example.engine

import android.app.ActivityManager
import android.content.Context
import android.content.pm.PackageManager
import android.hardware.display.DisplayManager
import android.opengl.EGL14
import android.opengl.EGLConfig
import android.opengl.GLES20
import android.os.Build
import android.os.PowerManager
import android.view.Display
import android.webkit.WebView
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.withContext
import kotlin.math.roundToInt

data class DisplayModeInfo(
    val modeId: Int,
    val width: Int,
    val height: Int,
    val refreshRateHz: Float
)

data class DeviceCapabilitiesReport(
    val deviceModel: String = "${Build.MANUFACTURER} ${Build.MODEL}",
    val androidVersion: String = "Android ${Build.VERSION.RELEASE} (API ${Build.VERSION.SDK_INT})",
    val primaryAbi: String = Build.SUPPORTED_ABIS.firstOrNull() ?: "unknown",
    val cpuCores: Int = Runtime.getRuntime().availableProcessors(),
    val totalRamMb: Long = 0L,
    val webViewPackage: String = "Probing…",
    val webViewVersion: String = "Probing…",
    val eglRenderer: String = "Probing…",
    val eglVendor: String = "—",
    val eglVersion: String = "—",
    val glesConfigVersion: String = "—",
    val maxTextureSize: Int = 0,
    val vulkanVersion: String = "Unavailable",
    val supportsAstc: Boolean = false,
    val supportsFloatTexture: Boolean = false,
    val activeDisplayHz: Float = 60f,
    val maxDisplayHz: Float = 60f,
    val supportedModes: List<DisplayModeInfo> = emptyList(),
    // Distinct rounded refresh rate options actually supported by this device (e.g. [60, 90, 120])
    val selectableRefreshRatesHz: List<Int> = listOf(60),
    val sustainedPerformanceSupported: Boolean = false,
    val wideColorGamutSupported: Boolean = false
)

object DeviceCapabilitiesProbe {

    suspend fun probe(context: Context): DeviceCapabilitiesReport = withContext(Dispatchers.Default) {
        val egl = probeOffscreenEgl()
        val pm = context.packageManager
        val am = context.getSystemService(Context.ACTIVITY_SERVICE) as? ActivityManager
        val dm = context.getSystemService(Context.DISPLAY_SERVICE) as? DisplayManager
        val powerManager = context.getSystemService(Context.POWER_SERVICE) as? PowerManager

        val memInfo = ActivityManager.MemoryInfo()
        am?.getMemoryInfo(memInfo)
        val totalRamMb = memInfo.totalMem / (1024L * 1024L)
        val glesVer = am?.deviceConfigurationInfo?.glEsVersion ?: "3.0"

        var vulkanLabel = "Not Supported"
        if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.N &&
            pm.hasSystemFeature(PackageManager.FEATURE_VULKAN_HARDWARE_LEVEL)
        ) {
            vulkanLabel = when {
                pm.hasSystemFeature(PackageManager.FEATURE_VULKAN_HARDWARE_VERSION, 0x00403000) -> "Vulkan 1.3"
                pm.hasSystemFeature(PackageManager.FEATURE_VULKAN_HARDWARE_VERSION, 0x00402000) -> "Vulkan 1.2"
                pm.hasSystemFeature(PackageManager.FEATURE_VULKAN_HARDWARE_VERSION, 0x00401000) -> "Vulkan 1.1"
                else -> "Vulkan 1.0+"
            }
        }

        val display = dm?.getDisplay(Display.DEFAULT_DISPLAY)
        val activeHz = display?.refreshRate ?: 60f
        val modes = display?.supportedModes?.map {
            DisplayModeInfo(
                modeId = it.modeId,
                width = it.physicalWidth,
                height = it.physicalHeight,
                refreshRateHz = it.refreshRate
            )
        }?.sortedByDescending { it.refreshRateHz } ?: emptyList()

        val distinctHz = modes
            .map { it.refreshRateHz.roundToInt() }
            .filter { it >= 50 }
            .distinct()
            .sorted()
            .ifEmpty { listOf(activeHz.roundToInt().coerceAtLeast(60)) }

        val maxHz = modes.maxOfOrNull { it.refreshRateHz } ?: activeHz

        val webViewPkg = if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.O) {
            runCatching { WebView.getCurrentWebViewPackage() }.getOrNull()
        } else {
            null
        }

        val sustainedPerf = if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.N) {
            runCatching { powerManager?.isSustainedPerformanceModeSupported == true }.getOrDefault(false)
        } else {
            false
        }

        val wideColor = if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.O) {
            context.resources.configuration.isScreenWideColorGamut
        } else {
            false
        }

        DeviceCapabilitiesReport(
            deviceModel = "${Build.MANUFACTURER.replaceFirstChar { it.uppercase() }} ${Build.MODEL}",
            androidVersion = "Android ${Build.VERSION.RELEASE} (API ${Build.VERSION.SDK_INT})",
            primaryAbi = Build.SUPPORTED_ABIS.firstOrNull() ?: "unknown",
            cpuCores = Runtime.getRuntime().availableProcessors(),
            totalRamMb = totalRamMb,
            webViewPackage = webViewPkg?.packageName ?: "com.android.webview",
            webViewVersion = webViewPkg?.versionName ?: "System Chromium WebView",
            eglRenderer = egl.renderer,
            eglVendor = egl.vendor,
            eglVersion = egl.version,
            glesConfigVersion = "OpenGL ES $glesVer",
            maxTextureSize = egl.maxTextureSize,
            vulkanVersion = vulkanLabel,
            supportsAstc = egl.extensions.contains("texture_compression_astc", ignoreCase = true),
            supportsFloatTexture = egl.extensions.contains("texture_float", ignoreCase = true) ||
                egl.extensions.contains("color_buffer_float", ignoreCase = true),
            activeDisplayHz = activeHz,
            maxDisplayHz = maxHz,
            supportedModes = modes,
            selectableRefreshRatesHz = distinctHz,
            sustainedPerformanceSupported = sustainedPerf,
            wideColorGamutSupported = wideColor
        )
    }

    private data class EglResult(
        val renderer: String,
        val vendor: String,
        val version: String,
        val maxTextureSize: Int,
        val extensions: String
    )

    private fun probeOffscreenEgl(): EglResult = runCatching {
        val display = EGL14.eglGetDisplay(EGL14.EGL_DEFAULT_DISPLAY)
        if (display == EGL14.EGL_NO_DISPLAY) {
            return EglResult("Unavailable (No EGL Display)", "Android", "—", 0, "")
        }
        val ver = IntArray(2)
        if (!EGL14.eglInitialize(display, ver, 0, ver, 1)) {
            return EglResult("EGL Init Failed", "Android", "—", 0, "")
        }
        val cfgAttribs = intArrayOf(
            EGL14.EGL_RENDERABLE_TYPE, EGL14.EGL_OPENGL_ES2_BIT,
            EGL14.EGL_SURFACE_TYPE, EGL14.EGL_PBUFFER_BIT,
            EGL14.EGL_RED_SIZE, 8,
            EGL14.EGL_GREEN_SIZE, 8,
            EGL14.EGL_BLUE_SIZE, 8,
            EGL14.EGL_NONE
        )
        val configs = arrayOfNulls<EGLConfig>(1)
        val numCfg = IntArray(1)
        EGL14.eglChooseConfig(display, cfgAttribs, 0, configs, 0, 1, numCfg, 0)
        val cfg = configs[0] ?: run {
            EGL14.eglTerminate(display)
            return EglResult("No EGL Config", "Android", "—", 0, "")
        }
        val surface = EGL14.eglCreatePbufferSurface(
            display, cfg, intArrayOf(EGL14.EGL_WIDTH, 1, EGL14.EGL_HEIGHT, 1, EGL14.EGL_NONE), 0
        )
        val ctx = EGL14.eglCreateContext(
            display, cfg, EGL14.EGL_NO_CONTEXT,
            intArrayOf(EGL14.EGL_CONTEXT_CLIENT_VERSION, 2, EGL14.EGL_NONE), 0
        )
        EGL14.eglMakeCurrent(display, surface, surface, ctx)

        val renderer = GLES20.glGetString(GLES20.GL_RENDERER) ?: "Unknown"
        val vendor = GLES20.glGetString(GLES20.GL_VENDOR) ?: "Unknown"
        val version = GLES20.glGetString(GLES20.GL_VERSION) ?: "Unknown"
        val ext = GLES20.glGetString(GLES20.GL_EXTENSIONS) ?: ""
        val maxTex = IntArray(1)
        GLES20.glGetIntegerv(GLES20.GL_MAX_TEXTURE_SIZE, maxTex, 0)

        EGL14.eglMakeCurrent(display, EGL14.EGL_NO_SURFACE, EGL14.EGL_NO_SURFACE, EGL14.EGL_NO_CONTEXT)
        EGL14.eglDestroySurface(display, surface)
        EGL14.eglDestroyContext(display, ctx)
        EGL14.eglTerminate(display)

        EglResult(renderer, vendor, version, maxTex[0], ext)
    }.getOrElse {
        EglResult("Probe error: ${it.javaClass.simpleName}", "Android", "—", 0, "")
    }
}
