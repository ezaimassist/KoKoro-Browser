package com.example.ui

import androidx.activity.compose.BackHandler
import androidx.compose.foundation.BorderStroke
import androidx.compose.foundation.background
import androidx.compose.foundation.clickable
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.PaddingValues
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.layout.width
import androidx.compose.foundation.layout.widthIn
import androidx.compose.foundation.lazy.LazyColumn
import androidx.compose.foundation.lazy.items
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.automirrored.filled.ArrowBack
import androidx.compose.material.icons.filled.Bolt
import androidx.compose.material.icons.filled.Memory
import androidx.compose.material.icons.filled.PlayArrow
import androidx.compose.material.icons.filled.Refresh
import androidx.compose.material3.Button
import androidx.compose.material3.Icon
import androidx.compose.material3.IconButton
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.Surface
import androidx.compose.material3.Switch
import androidx.compose.material3.Tab
import androidx.compose.material3.TabRow
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableIntStateOf
import androidx.compose.runtime.saveable.rememberSaveable
import androidx.compose.runtime.setValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clip
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.platform.testTag
import androidx.compose.ui.res.stringResource
import androidx.compose.ui.unit.dp
import com.example.R
import com.example.data.BrowserSettings
import com.example.engine.BareEngineAuditData
import com.example.engine.EngineBottleneckFinding
import com.example.engine.HardwareGpuReport
import com.example.engine.PerformanceTelemetry
import com.example.ui.theme.LocalTelemetryColors
import com.example.ui.theme.MonoCodeStyle
import com.example.ui.theme.MonoMetricLarge
import com.example.ui.theme.MonoMetricMedium
import java.util.Locale

@Composable
fun GpuLabScreen(
    gpuReport: HardwareGpuReport,
    telemetry: PerformanceTelemetry,
    settings: BrowserSettings,
    gamingModeActive: Boolean,
    onUpdateSettings: ((BrowserSettings) -> BrowserSettings) -> Unit,
    onLaunchBenchmark: () -> Unit,
    onRefreshProbe: () -> Unit,
    onToggleGamingMode: () -> Unit,
    onBack: () -> Unit,
    modifier: Modifier = Modifier
) {
    BackHandler(onBack = onBack)
    val tColors = LocalTelemetryColors.current
    var selectedTab by rememberSaveable { mutableIntStateOf(0) }
    val tabs = listOf("Telemetry", "Engine Tuning", "Bare Audit")

    Column(
        modifier = modifier
            .fillMaxSize()
            .background(MaterialTheme.colorScheme.background),
        horizontalAlignment = Alignment.CenterHorizontally
    ) {
        // Header bar
        Row(
            modifier = Modifier
                .fillMaxWidth()
                .widthIn(max = 840.dp)
                .padding(horizontal = 8.dp, vertical = 6.dp),
            verticalAlignment = Alignment.CenterVertically,
            horizontalArrangement = Arrangement.SpaceBetween
        ) {
            Row(verticalAlignment = Alignment.CenterVertically) {
                IconButton(
                    onClick = onBack,
                    modifier = Modifier.testTag("gpu_lab_back_button")
                ) {
                    Icon(
                        imageVector = Icons.AutoMirrored.Filled.ArrowBack,
                        contentDescription = stringResource(R.string.cd_back)
                    )
                }
                Column {
                    Text(
                        text = "GPU & Rendering Lab",
                        style = MaterialTheme.typography.titleLarge
                    )
                    Text(
                        text = gpuReport.deviceModel,
                        style = MaterialTheme.typography.labelSmall,
                        color = MaterialTheme.colorScheme.onSurfaceVariant
                    )
                }
            }
            IconButton(
                onClick = onRefreshProbe,
                modifier = Modifier.testTag("gpu_lab_refresh_probe")
            ) {
                Icon(
                    imageVector = Icons.Filled.Refresh,
                    contentDescription = "Refresh hardware GPU probe"
                )
            }
        }

        TabRow(
            selectedTabIndex = selectedTab,
            containerColor = MaterialTheme.colorScheme.surface,
            modifier = Modifier
                .fillMaxWidth()
                .widthIn(max = 840.dp)
        ) {
            tabs.forEachIndexed { idx, label ->
                Tab(
                    selected = selectedTab == idx,
                    onClick = { selectedTab = idx },
                    text = { Text(label, style = MaterialTheme.typography.labelLarge) },
                    modifier = Modifier.testTag("gpu_lab_tab_$idx")
                )
            }
        }

        LazyColumn(
            modifier = Modifier
                .fillMaxSize()
                .widthIn(max = 840.dp),
            contentPadding = PaddingValues(16.dp),
            verticalArrangement = Arrangement.spacedBy(14.dp)
        ) {
            when (selectedTab) {
                0 -> {
                    // Live Choreographer Frame Telemetry Card
                    item {
                        Surface(
                            shape = RoundedCornerShape(18.dp),
                            color = MaterialTheme.colorScheme.surface,
                            border = BorderStroke(1.dp, tColors.cardBorder),
                            modifier = Modifier.fillMaxWidth()
                        ) {
                            Column(
                                modifier = Modifier.padding(16.dp),
                                verticalArrangement = Arrangement.spacedBy(12.dp)
                            ) {
                                Row(
                                    modifier = Modifier.fillMaxWidth(),
                                    horizontalArrangement = Arrangement.SpaceBetween,
                                    verticalAlignment = Alignment.CenterVertically
                                ) {
                                    Text(
                                        text = "Live Frame Pacing (Choreographer)",
                                        style = MaterialTheme.typography.titleMedium
                                    )
                                    Surface(
                                        shape = RoundedCornerShape(999.dp),
                                        color = if (gamingModeActive) {
                                            MaterialTheme.colorScheme.primaryContainer
                                        } else {
                                            MaterialTheme.colorScheme.surfaceVariant
                                        }
                                    ) {
                                        Text(
                                            text = if (gamingModeActive) "GAMING ON" else "STANDARD",
                                            style = MaterialTheme.typography.labelSmall,
                                            color = if (gamingModeActive) {
                                                MaterialTheme.colorScheme.onPrimaryContainer
                                            } else {
                                                MaterialTheme.colorScheme.onSurfaceVariant
                                            },
                                            modifier = Modifier.padding(horizontal = 8.dp, vertical = 4.dp)
                                        )
                                    }
                                }

                                Row(
                                    modifier = Modifier.fillMaxWidth(),
                                    horizontalArrangement = Arrangement.SpaceBetween
                                ) {
                                    MetricBlock("FPS", "${telemetry.fps}", tColors.fpsOptimal)
                                    MetricBlock(
                                        "AVG FRAME",
                                        String.format(Locale.US, "%.1f ms", telemetry.frameTimeMs),
                                        tColors.gpuAccent
                                    )
                                    MetricBlock(
                                        "JANK",
                                        "${telemetry.jankPercent}%",
                                        if (telemetry.jankPercent > 5) tColors.criticalPulse else tColors.fpsOptimal
                                    )
                                    MetricBlock(
                                        "THERMAL",
                                        String.format(Locale.US, "%.1f°C", telemetry.batteryTempC),
                                        tColors.thermalWarn
                                    )
                                }

                                FrameTimeSparkline(
                                    samples = telemetry.recentFrameTimesMs,
                                    lineColor = tColors.fpsOptimal,
                                    warningColor = tColors.thermalWarn,
                                    modifier = Modifier
                                        .fillMaxWidth()
                                        .height(54.dp)
                                        .clip(RoundedCornerShape(10.dp))
                                        .background(MaterialTheme.colorScheme.surfaceVariant)
                                        .padding(6.dp)
                                )

                                Row(horizontalArrangement = Arrangement.spacedBy(10.dp)) {
                                    Button(
                                        onClick = onLaunchBenchmark,
                                        shape = RoundedCornerShape(12.dp),
                                        modifier = Modifier
                                            .weight(1f)
                                            .testTag("gpu_lab_launch_benchmark")
                                    ) {
                                        Icon(Icons.Filled.PlayArrow, contentDescription = null)
                                        Spacer(Modifier.width(6.dp))
                                        Text("Run WebGL2 Stress Lab")
                                    }
                                    Surface(
                                        shape = RoundedCornerShape(12.dp),
                                        color = if (gamingModeActive) {
                                            MaterialTheme.colorScheme.primary
                                        } else {
                                            MaterialTheme.colorScheme.primaryContainer
                                        },
                                        modifier = Modifier
                                            .clip(RoundedCornerShape(12.dp))
                                            .clickable(onClick = onToggleGamingMode)
                                            .testTag("gpu_lab_gaming_toggle")
                                    ) {
                                        Box(
                                            modifier = Modifier.padding(horizontal = 14.dp, vertical = 12.dp),
                                            contentAlignment = Alignment.Center
                                        ) {
                                            Icon(
                                                imageVector = Icons.Filled.Bolt,
                                                contentDescription = stringResource(R.string.cd_gaming_toggle),
                                                tint = if (gamingModeActive) {
                                                    MaterialTheme.colorScheme.onPrimary
                                                } else {
                                                    MaterialTheme.colorScheme.onPrimaryContainer
                                                }
                                            )
                                        }
                                    }
                                }
                            }
                        }
                    }

                    // Hardware GPU & Vulkan Inspector Card
                    item {
                        Surface(
                            shape = RoundedCornerShape(18.dp),
                            color = MaterialTheme.colorScheme.surface,
                            border = BorderStroke(1.dp, tColors.cardBorder),
                            modifier = Modifier.fillMaxWidth()
                        ) {
                            Column(
                                modifier = Modifier.padding(16.dp),
                                verticalArrangement = Arrangement.spacedBy(8.dp)
                            ) {
                                Row(
                                    verticalAlignment = Alignment.CenterVertically,
                                    horizontalArrangement = Arrangement.spacedBy(8.dp)
                                ) {
                                    Icon(
                                        imageVector = Icons.Filled.Memory,
                                        contentDescription = null,
                                        tint = tColors.gpuAccent
                                    )
                                    Text(
                                        text = "Hardware GPU & Graphics Stack",
                                        style = MaterialTheme.typography.titleMedium
                                    )
                                }
                                SpecRow("GL Renderer", gpuReport.glRenderer)
                                SpecRow("GL Vendor", gpuReport.glVendor)
                                SpecRow("OpenGL ES", gpuReport.glesConfigVersion)
                                SpecRow("Vulkan API", "${gpuReport.vulkanVersionLabel} (Lvl ${gpuReport.vulkanHardwareLevel})")
                                SpecRow("Max Texture", "${gpuReport.maxTextureSize} × ${gpuReport.maxTextureSize}")
                                SpecRow(
                                    "GPU Extensions",
                                    buildList {
                                        if (gpuReport.supportsAstc) add("ASTC")
                                        if (gpuReport.supportsAnisotropic) add("Aniso")
                                        if (gpuReport.supportsFloatTexture) add("FP32 Tex")
                                    }.ifEmpty { listOf("Standard GLES") }.joinToString(" • ")
                                )
                                SpecRow("Display Hz", "${gpuReport.currentRefreshRateHz.toInt()} Hz active / ${gpuReport.maxRefreshRateHz.toInt()} Hz max")
                                SpecRow("HDR / Wide Color", "${if (gpuReport.hdrSupported) "HDR" else "SDR"} • ${if (gpuReport.wideColorGamutSupported) "Wide P3" else "sRGB"}")
                                SpecRow("Sustained Perf API", if (gpuReport.sustainedPerformanceSupported) "Hardware Supported" else "OEM Managed")
                                SpecRow("CPU / Memory", "${gpuReport.cpuCores} cores (${gpuReport.primaryAbi}) • ${gpuReport.totalRamMb} MB RAM")
                                SpecRow("Chromium WebView", "${gpuReport.webViewEngineVersion}")
                            }
                        }
                    }

                    // Supported Display Refresh Modes
                    if (gpuReport.supportedModes.isNotEmpty()) {
                        item {
                            Surface(
                                shape = RoundedCornerShape(18.dp),
                                color = MaterialTheme.colorScheme.surface,
                                border = BorderStroke(1.dp, tColors.cardBorder),
                                modifier = Modifier.fillMaxWidth()
                            ) {
                                Column(
                                    modifier = Modifier.padding(16.dp),
                                    verticalArrangement = Arrangement.spacedBy(6.dp)
                                ) {
                                    Text(
                                        text = "Display Refresh Modes (${gpuReport.supportedModes.size})",
                                        style = MaterialTheme.typography.titleMedium
                                    )
                                    gpuReport.supportedModes.take(6).forEach { mode ->
                                        SpecRow(
                                            label = "Mode #${mode.modeId}",
                                            value = "${mode.width}×${mode.height} @ ${String.format(Locale.US, "%.1f", mode.refreshRateHz)} Hz"
                                        )
                                    }
                                }
                            }
                        }
                    }
                }

                1 -> {
                    // Render Resolution Scale Selector
                    item {
                        Surface(
                            shape = RoundedCornerShape(18.dp),
                            color = MaterialTheme.colorScheme.surface,
                            border = BorderStroke(1.dp, tColors.cardBorder),
                            modifier = Modifier.fillMaxWidth()
                        ) {
                            Column(
                                modifier = Modifier.padding(16.dp),
                                verticalArrangement = Arrangement.spacedBy(10.dp)
                            ) {
                                Text(
                                    text = "Dynamic WebGL Render Scale (DPR)",
                                    style = MaterialTheme.typography.titleMedium
                                )
                                Text(
                                    text = "Reduces fragment shader pixel fill-rate on high-DPI displays to boost sustained WebGL FPS.",
                                    style = MaterialTheme.typography.bodySmall,
                                    color = MaterialTheme.colorScheme.onSurfaceVariant
                                )
                                Row(
                                    modifier = Modifier.fillMaxWidth(),
                                    horizontalArrangement = Arrangement.spacedBy(8.dp)
                                ) {
                                    listOf(100 to "Native", 85 to "Balanced", 75 to "Max FPS", 70 to "Ultra").forEach { (pct, label) ->
                                        val selected = settings.renderScalePercent == pct
                                        Surface(
                                            shape = RoundedCornerShape(12.dp),
                                            color = if (selected) {
                                                MaterialTheme.colorScheme.primary
                                            } else {
                                                MaterialTheme.colorScheme.surfaceVariant
                                            },
                                            modifier = Modifier
                                                .weight(1f)
                                                .clip(RoundedCornerShape(12.dp))
                                                .clickable {
                                                    onUpdateSettings { it.copy(renderScalePercent = pct) }
                                                }
                                                .testTag("render_scale_$pct")
                                        ) {
                                            Column(
                                                modifier = Modifier.padding(vertical = 10.dp, horizontal = 6.dp),
                                                horizontalAlignment = Alignment.CenterHorizontally
                                            ) {
                                                Text(
                                                    text = "$pct%",
                                                    style = MonoMetricMedium,
                                                    color = if (selected) {
                                                        MaterialTheme.colorScheme.onPrimary
                                                    } else {
                                                        MaterialTheme.colorScheme.onSurface
                                                    }
                                                )
                                                Text(
                                                    text = label,
                                                    style = MaterialTheme.typography.labelSmall,
                                                    color = if (selected) {
                                                        MaterialTheme.colorScheme.onPrimary
                                                    } else {
                                                        MaterialTheme.colorScheme.onSurfaceVariant
                                                    }
                                                )
                                            }
                                        }
                                    }
                                }
                            }
                        }
                    }

                    // Interactive Rendering & GPU Switches
                    item {
                        Surface(
                            shape = RoundedCornerShape(18.dp),
                            color = MaterialTheme.colorScheme.surface,
                            border = BorderStroke(1.dp, tColors.cardBorder),
                            modifier = Modifier.fillMaxWidth()
                        ) {
                            Column(modifier = Modifier.padding(16.dp), verticalArrangement = Arrangement.spacedBy(6.dp)) {
                                Text("GPU & Input Latency Pipeline", style = MaterialTheme.typography.titleMedium)
                                EngineToggleRow(
                                    title = "Hardware Compositing Layer",
                                    subtitle = "Enforces View.LAYER_TYPE_HARDWARE on active WebView",
                                    checked = settings.hardwareCompositing,
                                    tag = "toggle_hardware_compositing",
                                    onCheckedChange = { v -> onUpdateSettings { it.copy(hardwareCompositing = v) } }
                                )
                                EngineToggleRow(
                                    title = "WebGL High-Performance + Desync",
                                    subtitle = "Injects powerPreference:'high-performance' & desynchronized:true",
                                    checked = settings.webGlHighPerformance,
                                    tag = "toggle_webgl_high_perf",
                                    onCheckedChange = { v -> onUpdateSettings { it.copy(webGlHighPerformance = v) } }
                                )
                                EngineToggleRow(
                                    title = "Disable WebGL MSAA Antialiasing",
                                    subtitle = "Cuts tile-GPU bandwidth overhead for higher frame rates",
                                    checked = settings.webGlDisableAntialias,
                                    tag = "toggle_disable_aa",
                                    onCheckedChange = { v -> onUpdateSettings { it.copy(webGlDisableAntialias = v) } }
                                )
                                EngineToggleRow(
                                    title = "Unbuffered Touch Dispatch",
                                    subtitle = "Calls requestUnbufferedDispatch() to bypass VSync touch batching",
                                    checked = settings.unbufferedTouchInput,
                                    tag = "toggle_unbuffered_touch",
                                    onCheckedChange = { v -> onUpdateSettings { it.copy(unbufferedTouchInput = v) } }
                                )
                                EngineToggleRow(
                                    title = "Lock Max Display Refresh Rate",
                                    subtitle = "Locks preferredDisplayModeId to ${gpuReport.maxRefreshRateHz.toInt()}Hz panel mode",
                                    checked = settings.lockMaxRefreshRate,
                                    tag = "toggle_max_hz",
                                    onCheckedChange = { v -> onUpdateSettings { it.copy(lockMaxRefreshRate = v) } }
                                )
                                EngineToggleRow(
                                    title = "Sustained Performance Mode",
                                    subtitle = "Stabilizes SoC thermal envelope during long gaming sessions",
                                    checked = settings.sustainedPerformanceMode,
                                    tag = "toggle_sustained_perf",
                                    onCheckedChange = { v -> onUpdateSettings { it.copy(sustainedPerformanceMode = v) } }
                                )
                                EngineToggleRow(
                                    title = "Freeze Background Tabs in Gaming Mode",
                                    subtitle = "Suspends inactive tabs via onPause() & RENDERER_PRIORITY_WAIVED",
                                    checked = settings.freezeBackgroundTabs,
                                    tag = "toggle_freeze_tabs",
                                    onCheckedChange = { v -> onUpdateSettings { it.copy(freezeBackgroundTabs = v) } }
                                )
                            }
                        }
                    }

                    // Bare Browser UI & Privacy Preferences
                    item {
                        Surface(
                            shape = RoundedCornerShape(18.dp),
                            color = MaterialTheme.colorScheme.surface,
                            border = BorderStroke(1.dp, tColors.cardBorder),
                            modifier = Modifier.fillMaxWidth()
                        ) {
                            Column(modifier = Modifier.padding(16.dp), verticalArrangement = Arrangement.spacedBy(6.dp)) {
                                Text("Bare UI & Browser Controls", style = MaterialTheme.typography.titleMedium)
                                EngineToggleRow(
                                    title = "Bottom Ergonomic Toolbar",
                                    subtitle = "Bare Browser signature bottom omnibox positioning",
                                    checked = settings.toolbarAtBottom,
                                    tag = "toggle_bottom_toolbar",
                                    onCheckedChange = { v -> onUpdateSettings { it.copy(toolbarAtBottom = v) } }
                                )
                                EngineToggleRow(
                                    title = "Auto-Trigger Gaming Mode",
                                    subtitle = "Automatically enters Gaming Mode on profiled game domains",
                                    checked = settings.autoGamingMode,
                                    tag = "toggle_auto_gaming",
                                    onCheckedChange = { v -> onUpdateSettings { it.copy(autoGamingMode = v) } }
                                )
                                EngineToggleRow(
                                    title = "Show Floating Telemetry HUD",
                                    subtitle = "Display live FPS, ms & thermal sparkline in Gaming Mode",
                                    checked = settings.showHudInGamingMode,
                                    tag = "toggle_show_hud",
                                    onCheckedChange = { v -> onUpdateSettings { it.copy(showHudInGamingMode = v) } }
                                )
                                EngineToggleRow(
                                    title = "Desktop User-Agent",
                                    subtitle = "Unlocks desktop WebGL portals & keyboard/gamepad builds",
                                    checked = settings.desktopUserAgent,
                                    tag = "toggle_desktop_ua",
                                    onCheckedChange = { v -> onUpdateSettings { it.copy(desktopUserAgent = v) } }
                                )
                                EngineToggleRow(
                                    title = "Dark Tactical Theme",
                                    subtitle = "OLED-friendly carbon dark surface palette",
                                    checked = settings.darkTheme,
                                    tag = "toggle_dark_theme",
                                    onCheckedChange = { v -> onUpdateSettings { it.copy(darkTheme = v) } }
                                )
                            }
                        }
                    }
                }

                2 -> {
                    item {
                        Surface(
                            shape = RoundedCornerShape(18.dp),
                            color = MaterialTheme.colorScheme.secondaryContainer.copy(alpha = 0.45f),
                            border = BorderStroke(1.dp, tColors.gpuAccent.copy(alpha = 0.45f)),
                            modifier = Modifier.fillMaxWidth()
                        ) {
                            Column(
                                modifier = Modifier.padding(16.dp),
                                verticalArrangement = Arrangement.spacedBy(6.dp)
                            ) {
                                Text(
                                    text = "BareBrowser/bare-browser Rendering Audit",
                                    style = MaterialTheme.typography.titleMedium,
                                    color = MaterialTheme.colorScheme.secondary
                                )
                                Text(
                                    text = "Technical breakdown of Chromium/Bare Browser bottlenecks on Android and the exact runtime & GPU mitigations implemented in Bare Turbo.",
                                    style = MaterialTheme.typography.bodySmall,
                                    color = MaterialTheme.colorScheme.onSurface
                                )
                            }
                        }
                    }

                    items(BareEngineAuditData.findings, key = { it.id }) { finding ->
                        BottleneckAuditCard(finding = finding)
                    }
                }
            }
        }
    }
}

@Composable
private fun MetricBlock(label: String, value: String, color: Color) {
    Column {
        Text(
            text = label,
            style = MaterialTheme.typography.labelSmall,
            color = MaterialTheme.colorScheme.onSurfaceVariant
        )
        Text(
            text = value,
            style = MonoMetricLarge,
            color = color
        )
    }
}

@Composable
private fun SpecRow(label: String, value: String) {
    Row(
        modifier = Modifier
            .fillMaxWidth()
            .padding(vertical = 2.dp),
        horizontalArrangement = Arrangement.SpaceBetween,
        verticalAlignment = Alignment.Top
    ) {
        Text(
            text = label,
            style = MaterialTheme.typography.bodySmall,
            color = MaterialTheme.colorScheme.onSurfaceVariant,
            modifier = Modifier.weight(0.4f)
        )
        Text(
            text = value,
            style = MonoCodeStyle,
            color = MaterialTheme.colorScheme.onSurface,
            modifier = Modifier.weight(0.6f)
        )
    }
}

@Composable
private fun EngineToggleRow(
    title: String,
    subtitle: String,
    checked: Boolean,
    tag: String,
    onCheckedChange: (Boolean) -> Unit
) {
    Row(
        modifier = Modifier
            .fillMaxWidth()
            .padding(vertical = 6.dp),
        verticalAlignment = Alignment.CenterVertically,
        horizontalArrangement = Arrangement.SpaceBetween
    ) {
        Column(modifier = Modifier.weight(1f).padding(end = 12.dp)) {
            Text(text = title, style = MaterialTheme.typography.titleSmall)
            Text(
                text = subtitle,
                style = MaterialTheme.typography.bodySmall,
                color = MaterialTheme.colorScheme.onSurfaceVariant
            )
        }
        Switch(
            checked = checked,
            onCheckedChange = onCheckedChange,
            modifier = Modifier.testTag(tag)
        )
    }
}

@Composable
private fun BottleneckAuditCard(finding: EngineBottleneckFinding) {
    val tColors = LocalTelemetryColors.current
    val severityColor = when (finding.severity) {
        "CRITICAL" -> tColors.criticalPulse
        "HIGH" -> tColors.thermalWarn
        else -> tColors.gpuAccent
    }
    Surface(
        shape = RoundedCornerShape(16.dp),
        color = MaterialTheme.colorScheme.surface,
        border = BorderStroke(1.dp, tColors.cardBorder),
        modifier = Modifier.fillMaxWidth()
    ) {
        Column(
            modifier = Modifier.padding(15.dp),
            verticalArrangement = Arrangement.spacedBy(8.dp)
        ) {
            Row(
                modifier = Modifier.fillMaxWidth(),
                horizontalArrangement = Arrangement.SpaceBetween,
                verticalAlignment = Alignment.CenterVertically
            ) {
                Text(
                    text = finding.subsystem.uppercase(),
                    style = MaterialTheme.typography.labelSmall,
                    color = MaterialTheme.colorScheme.secondary
                )
                Surface(
                    shape = RoundedCornerShape(6.dp),
                    color = severityColor.copy(alpha = 0.16f)
                ) {
                    Text(
                        text = finding.severity,
                        style = MaterialTheme.typography.labelSmall,
                        color = severityColor,
                        modifier = Modifier.padding(horizontal = 8.dp, vertical = 2.dp)
                    )
                }
            }
            Text(text = finding.title, style = MaterialTheme.typography.titleMedium)
            Text(
                text = "Bottleneck: ${finding.bottleneckAnalysis}",
                style = MaterialTheme.typography.bodySmall,
                color = MaterialTheme.colorScheme.onSurfaceVariant
            )
            Text(
                text = "Mitigation: ${finding.bareTurboOptimization}",
                style = MaterialTheme.typography.bodySmall,
                color = MaterialTheme.colorScheme.onSurface
            )
            Surface(
                shape = RoundedCornerShape(8.dp),
                color = MaterialTheme.colorScheme.surfaceVariant,
                modifier = Modifier.fillMaxWidth()
            ) {
                Text(
                    text = finding.chromiumFlags,
                    style = MonoCodeStyle,
                    color = tColors.fpsOptimal,
                    modifier = Modifier.padding(10.dp)
                )
            }
        }
    }
}
