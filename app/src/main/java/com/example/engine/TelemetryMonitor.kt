package com.example.engine

import android.app.ActivityManager
import android.content.Context
import android.content.Intent
import android.content.IntentFilter
import android.os.BatteryManager
import android.os.Build
import android.os.Debug
import android.os.Handler
import android.os.Looper
import android.os.PowerManager
import android.view.Choreographer
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.StateFlow
import kotlinx.coroutines.flow.asStateFlow

enum class ThermalStateLabel(val display: String) {
    NOMINAL("Cool"),
    LIGHT("Warm"),
    MODERATE("Moderate"),
    SEVERE("Throttling"),
    CRITICAL("Critical")
}

data class PerformanceTelemetry(
    val fps: Int = 60,
    val frameTimeMs: Float = 16.6f,
    val worstFrameMs: Float = 16.6f,
    val jankPercent: Int = 0,
    val recentFrameTimesMs: List<Float> = emptyList(),
    val appHeapMb: Int = 0,
    val availableRamMb: Long = 0L,
    val thermalState: ThermalStateLabel = ThermalStateLabel.NOMINAL,
    val batteryTempC: Float = 30.0f
)

class TelemetryMonitor(private val context: Context) {

    private val mainHandler = Handler(Looper.getMainLooper())
    private val _telemetry = MutableStateFlow(PerformanceTelemetry())
    val telemetry: StateFlow<PerformanceTelemetry> = _telemetry.asStateFlow()

    @Volatile
    private var running = false

    private var lastFrameNanos = 0L
    private var windowStartNanos = 0L
    private var framesInWindow = 0
    private var jankFramesInWindow = 0
    private var worstFrameInWindowMs = 0f
    private var sumFrameMs = 0f
    private val frameHistory = ArrayDeque<Float>(36)

    private val frameCallback = object : Choreographer.FrameCallback {
        override fun doFrame(frameTimeNanos: Long) {
            if (!running) return

            if (lastFrameNanos != 0L) {
                val deltaMs = (frameTimeNanos - lastFrameNanos) / 1_000_000f
                if (deltaMs in 1f..500f) {
                    framesInWindow++
                    sumFrameMs += deltaMs
                    if (deltaMs > worstFrameInWindowMs) worstFrameInWindowMs = deltaMs
                    if (deltaMs > 24.0f) jankFramesInWindow++

                    frameHistory.addLast(deltaMs)
                    while (frameHistory.size > 32) frameHistory.removeFirst()
                }
            } else {
                windowStartNanos = frameTimeNanos
            }
            lastFrameNanos = frameTimeNanos

            val elapsedNanos = frameTimeNanos - windowStartNanos
            if (elapsedNanos >= 500_000_000L && framesInWindow > 0) {
                val elapsedSec = elapsedNanos / 1_000_000_000f
                val fps = (framesInWindow / elapsedSec).toInt().coerceIn(1, 240)
                val avgFrameMs = sumFrameMs / framesInWindow
                val jankPct = ((jankFramesInWindow * 100f) / framesInWindow).toInt().coerceIn(0, 100)
                val sysStats = sampleSystemMetrics()

                _telemetry.value = PerformanceTelemetry(
                    fps = fps,
                    frameTimeMs = avgFrameMs,
                    worstFrameMs = worstFrameInWindowMs,
                    jankPercent = jankPct,
                    recentFrameTimesMs = frameHistory.toList(),
                    appHeapMb = sysStats.appHeapMb,
                    availableRamMb = sysStats.availableRamMb,
                    thermalState = sysStats.thermalState,
                    batteryTempC = sysStats.batteryTempC
                )

                windowStartNanos = frameTimeNanos
                framesInWindow = 0
                jankFramesInWindow = 0
                worstFrameInWindowMs = 0f
                sumFrameMs = 0f
            }

            Choreographer.getInstance().postFrameCallback(this)
        }
    }

    fun start() {
        if (running) return
        running = true
        lastFrameNanos = 0L
        windowStartNanos = 0L
        framesInWindow = 0
        jankFramesInWindow = 0
        worstFrameInWindowMs = 0f
        sumFrameMs = 0f
        mainHandler.post {
            if (running) {
                Choreographer.getInstance().postFrameCallback(frameCallback)
            }
        }
    }

    fun stop() {
        running = false
        mainHandler.post {
            Choreographer.getInstance().removeFrameCallback(frameCallback)
        }
    }

    private data class SystemSample(
        val appHeapMb: Int,
        val availableRamMb: Long,
        val thermalState: ThermalStateLabel,
        val batteryTempC: Float
    )

    private fun sampleSystemMetrics(): SystemSample {
        val am = context.getSystemService(Context.ACTIVITY_SERVICE) as? ActivityManager
        val memInfo = ActivityManager.MemoryInfo()
        am?.getMemoryInfo(memInfo)

        val runtimeUsed = Runtime.getRuntime().totalMemory() - Runtime.getRuntime().freeMemory()
        val nativeHeap = Debug.getNativeHeapAllocatedSize()
        val appHeapMb = ((runtimeUsed + nativeHeap) / (1024L * 1024L)).toInt().coerceAtLeast(1)
        val availMb = memInfo.availMem / (1024L * 1024L)

        val thermal = if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.Q) {
            val pm = context.getSystemService(Context.POWER_SERVICE) as? PowerManager
            when (pm?.currentThermalStatus ?: PowerManager.THERMAL_STATUS_NONE) {
                PowerManager.THERMAL_STATUS_NONE -> ThermalStateLabel.NOMINAL
                PowerManager.THERMAL_STATUS_LIGHT -> ThermalStateLabel.LIGHT
                PowerManager.THERMAL_STATUS_MODERATE -> ThermalStateLabel.MODERATE
                PowerManager.THERMAL_STATUS_SEVERE -> ThermalStateLabel.SEVERE
                else -> ThermalStateLabel.CRITICAL
            }
        } else {
            ThermalStateLabel.NOMINAL
        }

        val batteryIntent = runCatching {
            context.registerReceiver(null, IntentFilter(Intent.ACTION_BATTERY_CHANGED))
        }.getOrNull()
        val rawTemp = batteryIntent?.getIntExtra(BatteryManager.EXTRA_TEMPERATURE, 300) ?: 300
        val tempC = rawTemp / 10f

        return SystemSample(
            appHeapMb = appHeapMb,
            availableRamMb = availMb,
            thermalState = thermal,
            batteryTempC = tempC
        )
    }
}
