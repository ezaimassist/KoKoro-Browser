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

data class DisplayModeSummary(
    val modeId: Int,
    val width: Int,
    val height: Int,
    val refreshRateHz: Float
)

data class HardwareGpuReport(
    val glRenderer: String = "Probing GPU…",
    val glVendor: String = "—",
    val glVersion: String = "—",
    val glesConfigVersion: String = "—",
    val maxTextureSize: Int = 0,
    val vulkanSupported: Boolean = false,
    val vulkanHardwareLevel: Int = -1,
    val vulkanVersionLabel: String = "Not Detected",
    val supportsAstc: Boolean = false,
    val supportsAnisotropic: Boolean = false,
    val supportsFloatTexture: Boolean = false,
    val currentRefreshRateHz: Float = 60f,
    val maxRefreshRateHz: Float = 60f,
    val supportedModes: List<DisplayModeSummary> = emptyList(),
    val wideColorGamutSupported: Boolean = false,
    val hdrSupported: Boolean = false,
    val sustainedPerformanceSupported: Boolean = false,
    val cpuCores: Int = Runtime.getRuntime().availableProcessors(),
    val primaryAbi: String = Build.SUPPORTED_ABIS.firstOrNull() ?: "unknown",
    val totalRamMb: Long = 0L,
    val webViewEnginePackage: String = "Android System WebView",
    val webViewEngineVersion: String = "Unknown",
    val deviceModel: String = "${Build.MANUFACTURER} ${Build.MODEL}"
)

object HardwareGpuInspector {

    suspend fun inspect(context: Context): HardwareGpuReport = withContext(Dispatchers.Default) {
        val eglInfo = probeOffscreenEgl()
        val pm = context.packageManager
        val am = context.getSystemService(Context.ACTIVITY_SERVICE) as? ActivityManager
        val dm = context.getSystemService(Context.DISPLAY_SERVICE) as? DisplayManager
        val powerManager = context.getSystemService(Context.POWER_SERVICE) as? PowerManager

        val glesVersion = am?.deviceConfigurationInfo?.glEsVersion ?: "3.0"
        val memInfo = ActivityManager.MemoryInfo()
        am?.getMemoryInfo(memInfo)
        val totalRamMb = memInfo.totalMem / (1024L * 1024L)

        // Vulkan hardware capabilities
        var vulkanSupported = false
        var vulkanLevel = -1
        var vulkanVersionLabel = "Unavailable"
        if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.N) {
            vulkanSupported = pm.hasSystemFeature(PackageManager.FEATURE_VULKAN_HARDWARE_LEVEL)
            if (vulkanSupported) {
                vulkanLevel = when {
                    pm.hasSystemFeature(PackageManager.FEATURE_VULKAN_HARDWARE_LEVEL, 1) -> 1
                    else -> 0
                }
                vulkanVersionLabel = when {
                    pm.hasSystemFeature(PackageManager.FEATURE_VULKAN_HARDWARE_VERSION, 0x00403000) -> "Vulkan 1.3"
                    pm.hasSystemFeature(PackageManager.FEATURE_VULKAN_HARDWARE_VERSION, 0x00402000) -> "Vulkan 1.2"
                    pm.hasSystemFeature(PackageManager.FEATURE_VULKAN_HARDWARE_VERSION, 0x00401000) -> "Vulkan 1.1"
                    else -> "Vulkan 1.0+"
                }
            }
        }

        // Display refresh rates & HDR
        val display = dm?.getDisplay(Display.DEFAULT_DISPLAY)
        val currentHz = display?.refreshRate ?: 60f
        val modes = display?.supportedModes?.map {
            DisplayModeSummary(
                modeId = it.modeId,
                width = it.physicalWidth,
                height = it.physicalHeight,
                refreshRateHz = it.refreshRate
            )
        }?.sortedByDescending { it.refreshRateHz } ?: emptyList()
        val maxHz = modes.maxOfOrNull { it.refreshRateHz } ?: currentHz

        val wideColor = if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.O) {
            context.resources.configuration.isScreenWideColorGamut
        } else {
            false
        }
        val hdr = if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.N) {
            (display?.hdrCapabilities?.supportedHdrTypes?.isNotEmpty() == true)
        } else {
            false
        }

        val sustainedPerf = if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.N) {
            runCatching { powerManager?.isSustainedPerformanceModeSupported == true }.getOrDefault(false)
        } else {
            false
        }

        val webViewPkg = if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.O) {
            runCatching { WebView.getCurrentWebViewPackage() }.getOrNull()
        } else {
            null
        }

        HardwareGpuReport(
            glRenderer = eglInfo.renderer,
            glVendor = eglInfo.vendor,
            glVersion = eglInfo.version,
            glesConfigVersion = "OpenGL ES $glesVersion",
            maxTextureSize = eglInfo.maxTextureSize,
            vulkanSupported = vulkanSupported,
            vulkanHardwareLevel = vulkanLevel,
            vulkanVersionLabel = vulkanVersionLabel,
            supportsAstc = eglInfo.extensions.contains("texture_compression_astc", ignoreCase = true),
            supportsAnisotropic = eglInfo.extensions.contains("texture_filter_anisotropic", ignoreCase = true),
            supportsFloatTexture = eglInfo.extensions.contains("texture_float", ignoreCase = true) ||
                eglInfo.extensions.contains("color_buffer_float", ignoreCase = true),
            currentRefreshRateHz = currentHz,
            maxRefreshRateHz = maxHz,
            supportedModes = modes,
            wideColorGamutSupported = wideColor,
            hdrSupported = hdr,
            sustainedPerformanceSupported = sustainedPerf,
            cpuCores = Runtime.getRuntime().availableProcessors(),
            primaryAbi = Build.SUPPORTED_ABIS.firstOrNull() ?: "arm64-v8a",
            totalRamMb = totalRamMb,
            webViewEnginePackage = webViewPkg?.packageName ?: "com.android.webview",
            webViewEngineVersion = webViewPkg?.versionName ?: "System Chromium",
            deviceModel = "${Build.MANUFACTURER.replaceFirstChar { it.uppercase() }} ${Build.MODEL}"
        )
    }

    private data class EglProbeResult(
        val renderer: String,
        val vendor: String,
        val version: String,
        val maxTextureSize: Int,
        val extensions: String
    )

    private fun probeOffscreenEgl(): EglProbeResult {
        return runCatching {
            val display = EGL14.eglGetDisplay(EGL14.EGL_DEFAULT_DISPLAY)
            if (display == EGL14.EGL_NO_DISPLAY) {
                return EglProbeResult("Hardware GPU (No EGL Display)", "Android", "GLES 3.0", 4096, "")
            }
            val version = IntArray(2)
            if (!EGL14.eglInitialize(display, version, 0, version, 1)) {
                return EglProbeResult("Hardware GPU", "Android", "GLES 3.0", 4096, "")
            }

            val configAttribs = intArrayOf(
                EGL14.EGL_RENDERABLE_TYPE, EGL14.EGL_OPENGL_ES2_BIT,
                EGL14.EGL_SURFACE_TYPE, EGL14.EGL_PBUFFER_BIT,
                EGL14.EGL_RED_SIZE, 8,
                EGL14.EGL_GREEN_SIZE, 8,
                EGL14.EGL_BLUE_SIZE, 8,
                EGL14.EGL_NONE
            )
            val configs = arrayOfNulls<EGLConfig>(1)
            val numConfigs = IntArray(1)
            EGL14.eglChooseConfig(display, configAttribs, 0, configs, 0, 1, numConfigs, 0)
            val config = configs[0] ?: run {
                EGL14.eglTerminate(display)
                return EglProbeResult("Hardware GPU", "Android", "GLES 2.0+", 4096, "")
            }

            val surfaceAttribs = intArrayOf(
                EGL14.EGL_WIDTH, 1,
                EGL14.EGL_HEIGHT, 1,
                EGL14.EGL_NONE
            )
            val surface = EGL14.eglCreatePbufferSurface(display, config, surfaceAttribs, 0)
            val contextAttribs = intArrayOf(EGL14.EGL_CONTEXT_CLIENT_VERSION, 2, EGL14.EGL_NONE)
            val eglContext = EGL14.eglCreateContext(display, config, EGL14.EGL_NO_CONTEXT, contextAttribs, 0)

            EGL14.eglMakeCurrent(display, surface, surface, eglContext)

            val renderer = GLES20.glGetString(GLES20.GL_RENDERER) ?: "Unknown GPU"
            val vendor = GLES20.glGetString(GLES20.GL_VENDOR) ?: "Unknown Vendor"
            val glVer = GLES20.glGetString(GLES20.GL_VERSION) ?: "OpenGL ES"
            val extensions = GLES20.glGetString(GLES20.GL_EXTENSIONS) ?: ""
            val maxTex = IntArray(1)
            GLES20.glGetIntegerv(GLES20.GL_MAX_TEXTURE_SIZE, maxTex, 0)

            EGL14.eglMakeCurrent(display, EGL14.EGL_NO_SURFACE, EGL14.EGL_NO_SURFACE, EGL14.EGL_NO_CONTEXT)
            EGL14.eglDestroySurface(display, surface)
            EGL14.eglDestroyContext(display, eglContext)
            EGL14.eglTerminate(display)

            EglProbeResult(
                renderer = renderer,
                vendor = vendor,
                version = glVer,
                maxTextureSize = maxTex[0].coerceAtLeast(2048),
                extensions = extensions
            )
        }.getOrElse {
            EglProbeResult("Android Hardware GPU", "System", "OpenGL ES", 4096, "")
        }
    }
}
