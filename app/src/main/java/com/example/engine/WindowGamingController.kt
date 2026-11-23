package com.example.engine

import android.app.Activity
import android.content.pm.ActivityInfo
import android.os.Build
import android.os.PowerManager
import android.view.Display
import android.view.WindowManager
import androidx.core.view.WindowCompat
import androidx.core.view.WindowInsetsCompat
import androidx.core.view.WindowInsetsControllerCompat
import kotlin.math.abs

data class AppliedDisplayModeResult(
    val requestedHz: Float,
    val actualActiveHz: Float
)

object WindowGamingController {

    fun applyWindowConfiguration(
        activity: Activity,
        gamingModeActive: Boolean,
        html5FullscreenActive: Boolean,
        keepScreenOn: Boolean,
        lockLandscape: Boolean,
        preferredRefreshHz: Int // 0 = Auto highest supported, or 60 / 90 / 120
    ): AppliedDisplayModeResult {
        val window = activity.window ?: return AppliedDisplayModeResult(60f, 60f)

        // 1. Keep screen awake while gaming or in fullscreen media/canvas
        if ((gamingModeActive && keepScreenOn) || html5FullscreenActive) {
            window.addFlags(WindowManager.LayoutParams.FLAG_KEEP_SCREEN_ON)
        } else {
            window.clearFlags(WindowManager.LayoutParams.FLAG_KEEP_SCREEN_ON)
        }

        // 2. Orientation management (landscape lock for Gaming Mode or HTML5 fullscreen)
        val targetOrientation = when {
            html5FullscreenActive -> ActivityInfo.SCREEN_ORIENTATION_SENSOR_LANDSCAPE
            gamingModeActive && lockLandscape -> ActivityInfo.SCREEN_ORIENTATION_SENSOR_LANDSCAPE
            else -> ActivityInfo.SCREEN_ORIENTATION_UNSPECIFIED
        }
        if (activity.requestedOrientation != targetOrientation) {
            activity.requestedOrientation = targetOrientation
        }

        // 3. Display refresh rate selection strictly from actual supported Display.Modes
        var requestedHz = 60f
        var actualHz = 60f
        val attrs = window.attributes
        val display: Display? = if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.R) {
            activity.display
        } else {
            @Suppress("DEPRECATION")
            activity.windowManager.defaultDisplay
        }

        if (display != null) {
            actualHz = display.refreshRate
            val modes = display.supportedModes
            val chosenMode = when {
                preferredRefreshHz > 0 -> {
                    modes.minByOrNull { abs(it.refreshRate - preferredRefreshHz.toFloat()) }
                }
                gamingModeActive -> {
                    modes.maxByOrNull { it.refreshRate }
                }
                else -> null
            }

            if (chosenMode != null) {
                attrs.preferredDisplayModeId = chosenMode.modeId
                attrs.preferredRefreshRate = chosenMode.refreshRate
                requestedHz = chosenMode.refreshRate
            } else {
                attrs.preferredDisplayModeId = 0
                attrs.preferredRefreshRate = 0f
                requestedHz = actualHz
            }
        }

        if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.O) {
            attrs.colorMode = ActivityInfo.COLOR_MODE_WIDE_COLOR_GAMUT
        }
        window.attributes = attrs

        // 4. Sustained Performance Mode on supported Android devices
        if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.N) {
            val pm = activity.getSystemService(PowerManager::class.java)
            if (pm != null && pm.isSustainedPerformanceModeSupported) {
                runCatching { window.setSustainedPerformanceMode(gamingModeActive) }
            }
        }

        // 5. Immersive sticky fullscreen bars
        val insets = WindowCompat.getInsetsController(window, window.decorView)
        insets.systemBarsBehavior = WindowInsetsControllerCompat.BEHAVIOR_SHOW_TRANSIENT_BARS_BY_SWIPE
        if (gamingModeActive || html5FullscreenActive) {
            insets.hide(WindowInsetsCompat.Type.systemBars())
        } else {
            insets.show(WindowInsetsCompat.Type.systemBars())
        }

        return AppliedDisplayModeResult(requestedHz = requestedHz, actualActiveHz = actualHz)
    }
}
