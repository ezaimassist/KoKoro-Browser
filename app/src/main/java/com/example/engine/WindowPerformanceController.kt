package com.example.engine

import android.app.Activity
import android.content.pm.ActivityInfo
import android.os.Build
import android.os.PowerManager
import android.view.WindowManager
import androidx.core.view.WindowCompat
import androidx.core.view.WindowInsetsCompat
import androidx.core.view.WindowInsetsControllerCompat

object WindowPerformanceController {

    fun applyPerformanceProfile(
        activity: Activity,
        gamingModeActive: Boolean,
        lockMaxRefreshRate: Boolean,
        sustainedPerformanceMode: Boolean
    ) {
        val window = activity.window ?: return
        val params = window.attributes

        // 1. Keep screen awake during Gaming Mode so gameplay never dims/locks
        if (gamingModeActive) {
            window.addFlags(WindowManager.LayoutParams.FLAG_KEEP_SCREEN_ON)
        } else {
            window.clearFlags(WindowManager.LayoutParams.FLAG_KEEP_SCREEN_ON)
        }

        // 2. Lock highest supported display refresh rate (90Hz / 120Hz / 144Hz)
        if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.M) {
            val display = if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.R) {
                activity.display
            } else {
                @Suppress("DEPRECATION")
                activity.windowManager.defaultDisplay
            }
            if (display != null) {
                val bestMode = display.supportedModes.maxByOrNull { it.refreshRate }
                if ((gamingModeActive || lockMaxRefreshRate) && bestMode != null) {
                    params.preferredDisplayModeId = bestMode.modeId
                    params.preferredRefreshRate = bestMode.refreshRate
                } else {
                    params.preferredDisplayModeId = 0
                    params.preferredRefreshRate = 0f
                }
            }
        }

        // 3. Wide Color Gamut for HDR/WebGL fidelity on supported displays
        if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.O) {
            params.colorMode = ActivityInfo.COLOR_MODE_WIDE_COLOR_GAMUT
        }
        window.attributes = params

        // 4. Sustained Performance Mode (reduces CPU/GPU thermal oscillation during long sessions)
        if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.N) {
            val pm = activity.getSystemService(PowerManager::class.java)
            if (pm != null && pm.isSustainedPerformanceModeSupported) {
                runCatching {
                    window.setSustainedPerformanceMode(gamingModeActive && sustainedPerformanceMode)
                }
            }
        }

        // 5. Immersive distraction-free fullscreen bars during Gaming Mode
        val insetsController = WindowCompat.getInsetsController(window, window.decorView)
        insetsController.systemBarsBehavior =
            WindowInsetsControllerCompat.BEHAVIOR_SHOW_TRANSIENT_BARS_BY_SWIPE
        if (gamingModeActive) {
            insetsController.hide(WindowInsetsCompat.Type.systemBars())
        } else {
            insetsController.show(WindowInsetsCompat.Type.systemBars())
        }
    }
}
