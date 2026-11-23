package com.example.ui

import android.net.Uri
import androidx.activity.compose.BackHandler
import androidx.activity.compose.rememberLauncherForActivityResult
import androidx.activity.result.contract.ActivityResultContracts
import androidx.compose.foundation.BorderStroke
import androidx.compose.foundation.background
import androidx.compose.foundation.layout.Arrangement
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
import androidx.compose.material.icons.filled.DeleteSweep
import androidx.compose.material.icons.filled.Download
import androidx.compose.material.icons.filled.Memory
import androidx.compose.material.icons.filled.Refresh
import androidx.compose.material3.Button
import androidx.compose.material3.Icon
import androidx.compose.material3.IconButton
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.OutlinedButton
import androidx.compose.material3.Surface
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
import androidx.compose.ui.platform.LocalContext
import androidx.compose.ui.platform.testTag
import androidx.compose.ui.res.stringResource
import androidx.compose.ui.unit.dp
import com.example.R
import com.example.engine.DeviceCapabilitiesReport
import com.example.engine.DiagnosticCategory
import com.example.engine.DiagnosticEvent
import com.example.engine.DiagnosticsState
import com.example.ui.theme.LocalPulseSemanticColors
import com.example.ui.theme.MonoLogStyle
import com.example.ui.theme.MonoMetricLarge
import java.text.SimpleDateFormat
import java.util.Date
import java.util.Locale

@Composable
fun PulseDiagnosticsScreen(
    capabilities: DeviceCapabilitiesReport,
    diagnostics: DiagnosticsState,
    activeUrl: String,
    onProbeActiveWebGl: () -> Unit,
    onClearLogs: () -> Unit,
    onBuildExportText: () -> String,
    onExportStatus: (String) -> Unit,
    onBack: () -> Unit,
    modifier: Modifier = Modifier
) {
    BackHandler(onBack = onBack)
    val context = LocalContext.current
    val semantic = LocalPulseSemanticColors.current
    var selectedTab by rememberSaveable { mutableIntStateOf(0) }
    val tabs = listOf("Engine & FPS", "Errors (${diagnostics.totalErrorsCount})", "Timings & Log")

    // Zero-permission SAF document export for downloading diagnostic reports directly on Android
    val exportLauncher = rememberLauncherForActivityResult(
        contract = ActivityResultContracts.CreateDocument("text/plain")
    ) { uri: Uri? ->
        if (uri != null) {
            val result = runCatching {
                val report = onBuildExportText()
                context.contentResolver.openOutputStream(uri)?.use { out ->
                    out.write(report.toByteArray(Charsets.UTF_8))
                }
            }
            if (result.isSuccess) {
                onExportStatus("Diagnostic report saved to device")
            } else {
                onExportStatus("Export failed: ${result.exceptionOrNull()?.message ?: "IO error"}")
            }
        }
    }

    Column(
        modifier = modifier
            .fillMaxSize()
            .background(MaterialTheme.colorScheme.background),
        horizontalAlignment = Alignment.CenterHorizontally
    ) {
        Row(
            modifier = Modifier
                .fillMaxWidth()
                .widthIn(max = 860.dp)
                .padding(horizontal = 8.dp, vertical = 6.dp),
            verticalAlignment = Alignment.CenterVertically,
            horizontalArrangement = Arrangement.SpaceBetween
        ) {
            Row(verticalAlignment = Alignment.CenterVertically) {
                IconButton(
                    onClick = onBack,
                    modifier = Modifier.testTag("diag_back_button")
                ) {
                    Icon(Icons.AutoMirrored.Filled.ArrowBack, contentDescription = stringResource(R.string.cd_back))
                }
                Column {
                    Text("Real-Time Diagnostics", style = MaterialTheme.typography.titleLarge)
                    Text(
                        text = "Native WebViewClient, WebChromeClient & FrameMetrics",
                        style = MaterialTheme.typography.labelSmall,
                        color = MaterialTheme.colorScheme.onSurfaceVariant
                    )
                }
            }
            Row {
                IconButton(
                    onClick = {
                        val stamp = SimpleDateFormat("yyyyMMdd-HHmmss", Locale.US).format(Date())
                        exportLauncher.launch("pulsebrowser-diagnostics-$stamp.txt")
                    },
                    modifier = Modifier.testTag("diag_export_button")
                ) {
                    Icon(
                        imageVector = Icons.Filled.Download,
                        contentDescription = stringResource(R.string.cd_export_report),
                        tint = MaterialTheme.colorScheme.primary
                    )
                }
                IconButton(
                    onClick = onClearLogs,
                    modifier = Modifier.testTag("diag_clear_button")
                ) {
                    Icon(Icons.Filled.DeleteSweep, contentDescription = "Clear diagnostic logs")
                }
            }
        }

        TabRow(
            selectedTabIndex = selectedTab,
            containerColor = MaterialTheme.colorScheme.surface,
            modifier = Modifier
                .fillMaxWidth()
                .widthIn(max = 860.dp)
        ) {
            tabs.forEachIndexed { idx, label ->
                Tab(
                    selected = selectedTab == idx,
                    onClick = { selectedTab = idx },
                    text = { Text(label, style = MaterialTheme.typography.labelLarge) },
                    modifier = Modifier.testTag("diag_tab_$idx")
                )
            }
        }

        LazyColumn(
            modifier = Modifier
                .fillMaxSize()
                .widthIn(max = 860.dp),
            contentPadding = PaddingValues(16.dp),
            verticalArrangement = Arrangement.spacedBy(14.dp)
        ) {
            when (selectedTab) {
                0 -> {
                    // Honest FPS, Display Hz & FrameMetrics Card
                    item {
                        val fm = diagnostics.frameMetrics
                        Surface(
                            shape = RoundedCornerShape(18.dp),
                            color = MaterialTheme.colorScheme.surface,
                            border = BorderStroke(1.dp, semantic.border),
                            modifier = Modifier.fillMaxWidth()
                        ) {
                            Column(
                                modifier = Modifier.padding(16.dp),
                                verticalArrangement = Arrangement.spacedBy(10.dp)
                            ) {
                                Text("Frame Pacing & Refresh Rate Telemetry", style = MaterialTheme.typography.titleMedium)
                                Text(
                                    text = fm.measurementMethod,
                                    style = MaterialTheme.typography.bodySmall,
                                    color = MaterialTheme.colorScheme.onSurfaceVariant
                                )
                                Row(
                                    modifier = Modifier.fillMaxWidth(),
                                    horizontalArrangement = Arrangement.SpaceBetween
                                ) {
                                    DiagMetric("REQ / PANEL", "${diagnostics.requestedDisplayHz.toInt()}/${diagnostics.actualDisplayHz.toInt()}Hz", MaterialTheme.colorScheme.primary)
                                    DiagMetric("HOST FPS", "${fm.hostCompositorFps}", semantic.okGreen)
                                    DiagMetric("P95 FRAME", String.format(Locale.US, "%.1fms", fm.frameMetricsP95Ms), semantic.warnAmber)
                                    DiagMetric("JANK", "${fm.jankFrameCount}", if (fm.jankFrameCount > 0) semantic.errorRose else semantic.okGreen)
                                }
                                FramePacingSparkline(
                                    samples = fm.recentFrameMs,
                                    lineColor = MaterialTheme.colorScheme.primary,
                                    warnColor = semantic.warnAmber,
                                    modifier = Modifier
                                        .fillMaxWidth()
                                        .height(52.dp)
                                        .clip(RoundedCornerShape(10.dp))
                                        .background(MaterialTheme.colorScheme.surfaceVariant)
                                        .padding(6.dp)
                                )
                                DiagSpecRow("Choreographer Avg", String.format(Locale.US, "%.2f ms", fm.choreographerAvgFrameMs))
                                DiagSpecRow("FrameMetrics Swap Avg", String.format(Locale.US, "%.2f ms", fm.frameMetricsSwapAvgMs))
                                DiagSpecRow("App Heap / Free RAM", "${fm.appHeapMb} MB / ${fm.availableRamMb} MB (lowMem=${fm.lowMemoryFlag})")
                                DiagSpecRow("Thermal State", fm.thermalStatusLabel)
                            }
                        }
                    }

                    // Active Page Read-Only WebGL2 / WebAssembly Probe
                    item {
                        val wp = diagnostics.webGlProbe
                        Surface(
                            shape = RoundedCornerShape(18.dp),
                            color = MaterialTheme.colorScheme.surface,
                            border = BorderStroke(1.dp, semantic.border),
                            modifier = Modifier.fillMaxWidth()
                        ) {
                            Column(
                                modifier = Modifier.padding(16.dp),
                                verticalArrangement = Arrangement.spacedBy(8.dp)
                            ) {
                                Row(
                                    modifier = Modifier.fillMaxWidth(),
                                    horizontalArrangement = Arrangement.SpaceBetween,
                                    verticalAlignment = Alignment.CenterVertically
                                ) {
                                    Text("Active Page WebGL2 & Wasm Probe", style = MaterialTheme.typography.titleMedium)
                                    OutlinedButton(
                                        onClick = onProbeActiveWebGl,
                                        shape = RoundedCornerShape(10.dp),
                                        contentPadding = PaddingValues(horizontal = 10.dp, vertical = 6.dp),
                                        modifier = Modifier.testTag("diag_probe_webgl_button")
                                    ) {
                                        Icon(Icons.Filled.Refresh, contentDescription = null, modifier = Modifier.size(16.dp))
                                        Spacer(Modifier.width(4.dp))
                                        Text("Probe Now")
                                    }
                                }
                                Text(
                                    text = "Uses a temporary detached 1×1 canvas (strictly read-only; does not alter active game WebGL contexts).",
                                    style = MaterialTheme.typography.bodySmall,
                                    color = MaterialTheme.colorScheme.onSurfaceVariant
                                )
                                DiagSpecRow("Active URL", activeUrl)
                                DiagSpecRow("WebGL 2.0", wp.webGl2Available?.toString() ?: "Tap 'Probe Now' on a loaded page")
                                DiagSpecRow("WebAssembly.instantiateStreaming", wp.webAssemblyAvailable?.toString() ?: "—")
                                DiagSpecRow("SharedArrayBuffer (COOP/COEP)", wp.sharedArrayBufferAvailable?.toString() ?: "—")
                                DiagSpecRow("WebGL Renderer", wp.renderer)
                                DiagSpecRow("WebGL Vendor", wp.vendor)
                                DiagSpecRow("WebGL Version / MaxTex", "${wp.version} | ${wp.maxTextureSize}")
                                DiagSpecRow("Effective DPR", wp.devicePixelRatio)
                                DiagSpecRow("Client Hints Brands", wp.clientHintsBrands)
                                DiagSpecRow("window.chrome Present", wp.hasWindowChrome?.toString() ?: "—")
                                DiagSpecRow("Effective User-Agent", wp.effectiveUserAgent)
                            }
                        }
                    }

                    // Chromium WebView & Hardware EGL/Vulkan Capabilities
                    item {
                        Surface(
                            shape = RoundedCornerShape(18.dp),
                            color = MaterialTheme.colorScheme.surface,
                            border = BorderStroke(1.dp, semantic.border),
                            modifier = Modifier.fillMaxWidth()
                        ) {
                            Column(
                                modifier = Modifier.padding(16.dp),
                                verticalArrangement = Arrangement.spacedBy(6.dp)
                            ) {
                                Row(
                                    verticalAlignment = Alignment.CenterVertically,
                                    horizontalArrangement = Arrangement.spacedBy(8.dp)
                                ) {
                                    Icon(Icons.Filled.Memory, contentDescription = null, tint = MaterialTheme.colorScheme.primary)
                                    Text("Chromium WebView & Device Hardware", style = MaterialTheme.typography.titleMedium)
                                }
                                DiagSpecRow("WebView Package", capabilities.webViewPackage)
                                DiagSpecRow("WebView Version", capabilities.webViewVersion)
                                DiagSpecRow("Device / OS", "${capabilities.deviceModel} • ${capabilities.androidVersion}")
                                DiagSpecRow("CPU / RAM", "${capabilities.cpuCores} cores (${capabilities.primaryAbi}) • ${capabilities.totalRamMb} MB")
                                DiagSpecRow("EGL Renderer", capabilities.eglRenderer)
                                DiagSpecRow("EGL / GLES", "${capabilities.eglVendor} • ${capabilities.glesConfigVersion}")
                                DiagSpecRow("Max Texture Size", "${capabilities.maxTextureSize}px")
                                DiagSpecRow("Vulkan Feature", capabilities.vulkanVersion)
                                DiagSpecRow("Supported Display Hz", capabilities.selectableRefreshRatesHz.joinToString(", ") { "${it} Hz" })
                                DiagSpecRow("Sustained Perf API", if (capabilities.sustainedPerformanceSupported) "Supported" else "Not reported")
                                Spacer(Modifier.height(6.dp))
                                Button(
                                    onClick = {
                                        val stamp = SimpleDateFormat("yyyyMMdd-HHmmss", Locale.US).format(Date())
                                        exportLauncher.launch("pulsebrowser-diagnostics-$stamp.txt")
                                    },
                                    shape = RoundedCornerShape(12.dp),
                                    modifier = Modifier
                                        .fillMaxWidth()
                                        .testTag("diag_export_full_button")
                                ) {
                                    Icon(Icons.Filled.Download, contentDescription = null)
                                    Spacer(Modifier.width(8.dp))
                                    Text("Save Full Diagnostic Report (.txt)")
                                }
                            }
                        }
                    }
                }

                1 -> {
                    val errorsOnly = diagnostics.events.filter {
                        it.category != DiagnosticCategory.CONSOLE_LOG
                    }
                    if (errorsOnly.isEmpty()) {
                        item {
                            EmptyDiagnosticCard(
                                title = "No Errors Recorded",
                                body = "WebViewClient HTTP/network errors, WebAssembly compile failures, MPQ asset errors, TLS blocks, renderer crashes, and Android memory-pressure trim events will appear here in real time."
                            )
                        }
                    } else {
                        items(errorsOnly, key = { it.id }) { ev ->
                            DiagnosticEventCard(ev = ev)
                        }
                    }
                }

                2 -> {
                    item {
                        Surface(
                            shape = RoundedCornerShape(16.dp),
                            color = MaterialTheme.colorScheme.surface,
                            border = BorderStroke(1.dp, semantic.border),
                            modifier = Modifier.fillMaxWidth()
                        ) {
                            Column(
                                modifier = Modifier.padding(14.dp),
                                verticalArrangement = Arrangement.spacedBy(6.dp)
                            ) {
                                Text("Page Load Timings (${diagnostics.timings.size})", style = MaterialTheme.typography.titleMedium)
                                if (diagnostics.timings.isEmpty()) {
                                    Text(
                                        "Load a page to measure real onPageStarted → onPageFinished timings.",
                                        style = MaterialTheme.typography.bodySmall,
                                        color = MaterialTheme.colorScheme.onSurfaceVariant
                                    )
                                } else {
                                    diagnostics.timings.take(10).forEach { timing ->
                                        DiagSpecRow("${timing.durationMs} ms", timing.url)
                                    }
                                }
                            }
                        }
                    }

                    if (diagnostics.events.isEmpty()) {
                        item {
                            EmptyDiagnosticCard(
                                title = "Console Stream Empty",
                                body = "Messages from WebChromeClient.onConsoleMessage (including LastHavens ?log=1 output) will stream here once a page runs."
                            )
                        }
                    } else {
                        items(diagnostics.events, key = { it.id }) { ev ->
                            DiagnosticEventCard(ev = ev)
                        }
                    }
                }
            }
        }
    }
}

@Composable
private fun DiagMetric(label: String, value: String, color: Color) {
    Column {
        Text(label, style = MaterialTheme.typography.labelSmall, color = MaterialTheme.colorScheme.onSurfaceVariant)
        Text(value, style = MonoMetricLarge, color = color)
    }
}

@Composable
private fun DiagSpecRow(label: String, value: String) {
    Row(
        modifier = Modifier
            .fillMaxWidth()
            .padding(vertical = 2.dp),
        horizontalArrangement = Arrangement.SpaceBetween
    ) {
        Text(
            text = label,
            style = MaterialTheme.typography.bodySmall,
            color = MaterialTheme.colorScheme.onSurfaceVariant,
            modifier = Modifier.weight(0.42f)
        )
        Text(
            text = value,
            style = MonoLogStyle,
            color = MaterialTheme.colorScheme.onSurface,
            modifier = Modifier.weight(0.58f)
        )
    }
}

@Composable
private fun DiagnosticEventCard(ev: DiagnosticEvent) {
    val semantic = LocalPulseSemanticColors.current
    val badgeColor = when (ev.category) {
        DiagnosticCategory.WASM_ERROR,
        DiagnosticCategory.RENDERER_CRASH,
        DiagnosticCategory.TLS_ERROR -> semantic.errorRose
        DiagnosticCategory.MPQ_OR_ASSET_ERROR,
        DiagnosticCategory.HTTP_ERROR,
        DiagnosticCategory.NETWORK_ERROR,
        DiagnosticCategory.MEMORY_PRESSURE,
        DiagnosticCategory.CONSOLE_ERROR -> semantic.warnAmber
        DiagnosticCategory.CONSOLE_WARN -> MaterialTheme.colorScheme.secondary
        DiagnosticCategory.CONSOLE_LOG -> MaterialTheme.colorScheme.primary
    }
    val timeText = SimpleDateFormat("HH:mm:ss.SSS", Locale.US).format(Date(ev.timestampMs))

    Surface(
        shape = RoundedCornerShape(14.dp),
        color = MaterialTheme.colorScheme.surface,
        border = BorderStroke(1.dp, badgeColor.copy(alpha = 0.45f)),
        modifier = Modifier.fillMaxWidth()
    ) {
        Column(
            modifier = Modifier.padding(12.dp),
            verticalArrangement = Arrangement.spacedBy(4.dp)
        ) {
            Row(
                modifier = Modifier.fillMaxWidth(),
                horizontalArrangement = Arrangement.SpaceBetween
            ) {
                Text(ev.category.name, style = MaterialTheme.typography.labelSmall, color = badgeColor)
                Text(timeText, style = MaterialTheme.typography.labelSmall, color = MaterialTheme.colorScheme.onSurfaceVariant)
            }
            Text(ev.summary, style = MaterialTheme.typography.titleSmall)
            if (ev.detail.isNotBlank()) {
                Text(ev.detail, style = MonoLogStyle, color = MaterialTheme.colorScheme.onSurfaceVariant)
            }
        }
    }
}

@Composable
private fun EmptyDiagnosticCard(title: String, body: String) {
    val semantic = LocalPulseSemanticColors.current
    Surface(
        shape = RoundedCornerShape(16.dp),
        color = MaterialTheme.colorScheme.surface,
        border = BorderStroke(1.dp, semantic.border),
        modifier = Modifier.fillMaxWidth()
    ) {
        Column(
            modifier = Modifier.padding(18.dp),
            verticalArrangement = Arrangement.spacedBy(6.dp)
        ) {
            Text(title, style = MaterialTheme.typography.titleMedium)
            Text(body, style = MaterialTheme.typography.bodySmall, color = MaterialTheme.colorScheme.onSurfaceVariant)
        }
    }
}
