package com.example.ui

import androidx.compose.animation.AnimatedVisibility
import androidx.compose.animation.fadeIn
import androidx.compose.animation.fadeOut
import androidx.compose.foundation.BorderStroke
import androidx.compose.foundation.Canvas
import androidx.compose.foundation.background
import androidx.compose.foundation.clickable
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.layout.sizeIn
import androidx.compose.foundation.layout.width
import androidx.compose.foundation.shape.CircleShape
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.filled.BugReport
import androidx.compose.material.icons.filled.Close
import androidx.compose.material.icons.filled.DisplaySettings
import androidx.compose.material.icons.filled.SportsEsports
import androidx.compose.material.icons.filled.Visibility
import androidx.compose.material.icons.filled.VisibilityOff
import androidx.compose.material3.Icon
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.Surface
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clip
import androidx.compose.ui.geometry.Offset
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.graphics.Path
import androidx.compose.ui.graphics.StrokeCap
import androidx.compose.ui.graphics.drawscope.Stroke
import androidx.compose.ui.platform.testTag
import androidx.compose.ui.unit.dp
import com.example.engine.DiagnosticsState
import com.example.ui.theme.LocalPulseSemanticColors
import com.example.ui.theme.MonoMetricSmall
import java.util.Locale

@Composable
fun PulseGamingHud(
    diagnostics: DiagnosticsState,
    hudExpanded: Boolean,
    browserUiHidden: Boolean,
    onToggleExpanded: () -> Unit,
    onToggleBrowserUiHidden: () -> Unit,
    onCycleRefreshHz: () -> Unit,
    onOpenDiagnostics: () -> Unit,
    onExitGamingMode: () -> Unit,
    modifier: Modifier = Modifier
) {
    val semantic = LocalPulseSemanticColors.current
    val fm = diagnostics.frameMetrics

    Box(modifier = modifier) {
        // Minimal translucent pill when collapsed so game viewport & controls remain unobstructed
        AnimatedVisibility(
            visible = !hudExpanded,
            enter = fadeIn(),
            exit = fadeOut()
        ) {
            Surface(
                shape = RoundedCornerShape(999.dp),
                color = semantic.hudOverlay,
                border = BorderStroke(
                    1.dp,
                    if (diagnostics.totalErrorsCount > 0) semantic.errorRose else MaterialTheme.colorScheme.primary.copy(alpha = 0.65f)
                ),
                modifier = Modifier
                    .testTag("gaming_hud_pill")
                    .sizeIn(minWidth = 48.dp, minHeight = 44.dp)
                    .clickable(onClick = onToggleExpanded)
            ) {
                Row(
                    modifier = Modifier.padding(horizontal = 10.dp, vertical = 8.dp),
                    verticalAlignment = Alignment.CenterVertically,
                    horizontalArrangement = Arrangement.spacedBy(6.dp)
                ) {
                    Icon(
                        imageVector = Icons.Filled.SportsEsports,
                        contentDescription = "Expand Gaming Mode controls",
                        tint = MaterialTheme.colorScheme.primary,
                        modifier = Modifier.size(16.dp)
                    )
                    Text(
                        text = "${diagnostics.actualDisplayHz.toInt()}Hz",
                        style = MonoMetricSmall,
                        color = Color.White
                    )
                    Text(
                        text = "• ${fm.hostCompositorFps}c",
                        style = MaterialTheme.typography.labelSmall,
                        color = semantic.okGreen
                    )
                    if (diagnostics.totalErrorsCount > 0) {
                        Box(
                            modifier = Modifier
                                .size(7.dp)
                                .clip(CircleShape)
                                .background(semantic.errorRose)
                        )
                    }
                }
            }
        }

        // Expanded compact diagnostic & gaming control card
        AnimatedVisibility(
            visible = hudExpanded,
            enter = fadeIn(),
            exit = fadeOut()
        ) {
            Surface(
                shape = RoundedCornerShape(16.dp),
                color = semantic.hudOverlay,
                border = BorderStroke(1.dp, MaterialTheme.colorScheme.primary.copy(alpha = 0.55f)),
                modifier = Modifier
                    .width(292.dp)
                    .testTag("gaming_hud_expanded")
            ) {
                Column(modifier = Modifier.padding(12.dp)) {
                    Row(
                        modifier = Modifier.fillMaxWidth(),
                        horizontalArrangement = Arrangement.SpaceBetween,
                        verticalAlignment = Alignment.CenterVertically
                    ) {
                        Text(
                            text = "GAMING MODE HUD",
                            style = MaterialTheme.typography.labelMedium,
                            color = MaterialTheme.colorScheme.primary
                        )
                        Row(horizontalArrangement = Arrangement.spacedBy(4.dp)) {
                            HudTinyIcon(
                                icon = Icons.Filled.DisplaySettings,
                                contentDescription = "Cycle display refresh rate",
                                tint = MaterialTheme.colorScheme.primary,
                                tag = "hud_cycle_hz_button",
                                onClick = onCycleRefreshHz
                            )
                            HudTinyIcon(
                                icon = if (browserUiHidden) Icons.Filled.Visibility else Icons.Filled.VisibilityOff,
                                contentDescription = "Show or hide browser UI",
                                tint = Color.White,
                                tag = "hud_toggle_chrome_button",
                                onClick = onToggleBrowserUiHidden
                            )
                            HudTinyIcon(
                                icon = Icons.Filled.BugReport,
                                contentDescription = "Open diagnostics",
                                tint = if (diagnostics.totalErrorsCount > 0) semantic.errorRose else semantic.okGreen,
                                tag = "hud_diagnostics_button",
                                onClick = onOpenDiagnostics
                            )
                            HudTinyIcon(
                                icon = Icons.Filled.Close,
                                contentDescription = "Collapse HUD",
                                tint = Color.White.copy(alpha = 0.8f),
                                tag = "hud_collapse_button",
                                onClick = onToggleExpanded
                            )
                        }
                    }

                    Spacer(Modifier.height(8.dp))

                    // Clearly distinguish Requested/Active Display Hz from Host Compositor FPS
                    Row(
                        modifier = Modifier.fillMaxWidth(),
                        horizontalArrangement = Arrangement.SpaceBetween
                    ) {
                        HudMetric("DISPLAY HZ", "${diagnostics.requestedDisplayHz.toInt()}/${diagnostics.actualDisplayHz.toInt()}", MaterialTheme.colorScheme.primary)
                        HudMetric("HOST FPS", "${fm.hostCompositorFps}", semantic.okGreen)
                        HudMetric("P95 MS", String.format(Locale.US, "%.1f", fm.frameMetricsP95Ms), semantic.warnAmber)
                        HudMetric("FREE RAM", "${fm.availableRamMb}M", Color.White)
                    }

                    Spacer(Modifier.height(6.dp))

                    FramePacingSparkline(
                        samples = fm.recentFrameMs,
                        lineColor = MaterialTheme.colorScheme.primary,
                        warnColor = semantic.warnAmber,
                        modifier = Modifier
                            .fillMaxWidth()
                            .height(24.dp)
                    )

                    Spacer(Modifier.height(6.dp))

                    Text(
                        text = "Method: Choreographer/FrameMetrics (host compositor). Check game ?stats=1 for WebGL FPS.",
                        style = MaterialTheme.typography.labelSmall,
                        color = Color.White.copy(alpha = 0.62f)
                    )

                    Spacer(Modifier.height(8.dp))

                    Row(
                        modifier = Modifier.fillMaxWidth(),
                        horizontalArrangement = Arrangement.SpaceBetween,
                        verticalAlignment = Alignment.CenterVertically
                    ) {
                        Text(
                            text = "Errors: ${diagnostics.totalErrorsCount} • Wasm: ${diagnostics.wasmErrorsCount}",
                            style = MaterialTheme.typography.labelSmall,
                            color = if (diagnostics.totalErrorsCount > 0) semantic.errorRose else Color.White.copy(alpha = 0.72f)
                        )
                        Surface(
                            shape = RoundedCornerShape(8.dp),
                            color = semantic.errorRose.copy(alpha = 0.22f),
                            modifier = Modifier
                                .clip(RoundedCornerShape(8.dp))
                                .clickable(onClick = onExitGamingMode)
                                .testTag("hud_exit_gaming_button")
                        ) {
                            Text(
                                text = "Exit Gaming",
                                style = MaterialTheme.typography.labelSmall,
                                color = semantic.errorRose,
                                modifier = Modifier.padding(horizontal = 8.dp, vertical = 5.dp)
                            )
                        }
                    }
                }
            }
        }
    }
}

@Composable
private fun HudMetric(label: String, value: String, color: Color) {
    Column {
        Text(
            text = value,
            style = MonoMetricSmall,
            color = color
        )
        Text(
            text = label,
            style = MaterialTheme.typography.labelSmall,
            color = Color.White.copy(alpha = 0.58f)
        )
    }
}

@Composable
private fun HudTinyIcon(
    icon: androidx.compose.ui.graphics.vector.ImageVector,
    contentDescription: String,
    tint: Color,
    tag: String,
    onClick: () -> Unit
) {
    Box(
        modifier = Modifier
            .testTag(tag)
            .size(32.dp)
            .clip(CircleShape)
            .clickable(onClick = onClick),
        contentAlignment = Alignment.Center
    ) {
        Icon(
            imageVector = icon,
            contentDescription = contentDescription,
            tint = tint,
            modifier = Modifier.size(16.dp)
        )
    }
}

@Composable
fun FramePacingSparkline(
    samples: List<Float>,
    lineColor: Color,
    warnColor: Color,
    modifier: Modifier = Modifier
) {
    Canvas(modifier = modifier) {
        val w = size.width
        val h = size.height
        if (w <= 0f || h <= 0f) return@Canvas
        val guideY = h * (1f - (16.6f / 40f))
        drawLine(
            color = Color.White.copy(alpha = 0.16f),
            start = Offset(0f, guideY),
            end = Offset(w, guideY),
            strokeWidth = 1f
        )
        if (samples.size < 2) return@Canvas
        val step = w / (samples.size - 1)
        val path = Path()
        samples.forEachIndexed { idx, ms ->
            val clamped = ms.coerceIn(4f, 40f)
            val x = idx * step
            val y = h * (1f - clamped / 40f)
            if (idx == 0) path.moveTo(x, y) else path.lineTo(x, y)
        }
        val spike = samples.maxOrNull() ?: 16.6f
        drawPath(
            path = path,
            color = if (spike > 26f) warnColor else lineColor,
            style = Stroke(width = 2.2f, cap = StrokeCap.Round)
        )
    }
}
