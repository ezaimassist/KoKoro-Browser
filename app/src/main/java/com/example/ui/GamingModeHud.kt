package com.example.ui

import androidx.compose.animation.AnimatedVisibility
import androidx.compose.animation.fadeIn
import androidx.compose.animation.fadeOut
import androidx.compose.animation.scaleIn
import androidx.compose.animation.scaleOut
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
import androidx.compose.material.icons.filled.Bolt
import androidx.compose.material.icons.filled.Close
import androidx.compose.material.icons.filled.Speed
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
import androidx.compose.ui.res.stringResource
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.unit.dp
import com.example.R
import com.example.engine.PerformanceTelemetry
import com.example.engine.ThermalStateLabel
import com.example.ui.theme.LocalTelemetryColors
import com.example.ui.theme.MonoMetricMedium
import java.util.Locale

@Composable
fun GamingModeHudOverlay(
    telemetry: PerformanceTelemetry,
    blockedCount: Int,
    frozenTabCount: Int,
    maxRefreshHz: Int,
    renderScalePercent: Int,
    hudExpanded: Boolean,
    onToggleHudExpanded: () -> Unit,
    onOpenGpuLab: () -> Unit,
    onExitGamingMode: () -> Unit,
    modifier: Modifier = Modifier
) {
    val tColors = LocalTelemetryColors.current
    val fpsColor = when {
        telemetry.fps >= 55 -> tColors.fpsOptimal
        telemetry.fps >= 38 -> tColors.thermalWarn
        else -> tColors.criticalPulse
    }
    val thermalColor = when (telemetry.thermalState) {
        ThermalStateLabel.NOMINAL -> tColors.fpsOptimal
        ThermalStateLabel.LIGHT -> tColors.gpuAccent
        ThermalStateLabel.MODERATE -> tColors.thermalWarn
        else -> tColors.criticalPulse
    }

    Box(modifier = modifier) {
        // Minimized floating FPS pill when HUD is collapsed
        AnimatedVisibility(
            visible = !hudExpanded,
            enter = fadeIn() + scaleIn(),
            exit = fadeOut() + scaleOut()
        ) {
            Surface(
                shape = RoundedCornerShape(999.dp),
                color = tColors.hudBackground,
                border = BorderStroke(1.dp, tColors.fpsOptimal.copy(alpha = 0.6f)),
                modifier = Modifier
                    .testTag("hud_collapsed_pill")
                    .sizeIn(minWidth = 48.dp, minHeight = 48.dp)
                    .clickable(onClick = onToggleHudExpanded)
            ) {
                Row(
                    modifier = Modifier.padding(horizontal = 12.dp, vertical = 10.dp),
                    verticalAlignment = Alignment.CenterVertically,
                    horizontalArrangement = Arrangement.spacedBy(6.dp)
                ) {
                    Box(
                        modifier = Modifier
                            .size(8.dp)
                            .clip(CircleShape)
                            .background(fpsColor)
                    )
                    Text(
                        text = "${telemetry.fps} FPS",
                        style = MonoMetricMedium,
                        color = fpsColor
                    )
                    Text(
                        text = "TURBO",
                        style = MaterialTheme.typography.labelSmall,
                        color = Color.White.copy(alpha = 0.72f)
                    )
                }
            }
        }

        // Expanded compact telemetry HUD
        AnimatedVisibility(
            visible = hudExpanded,
            enter = fadeIn() + scaleIn(),
            exit = fadeOut() + scaleOut()
        ) {
            Surface(
                shape = RoundedCornerShape(16.dp),
                color = tColors.hudBackground,
                border = BorderStroke(1.dp, tColors.fpsOptimal.copy(alpha = 0.5f)),
                modifier = Modifier
                    .width(268.dp)
                    .testTag("gaming_mode_hud_panel")
            ) {
                Column(modifier = Modifier.padding(12.dp)) {
                    Row(
                        modifier = Modifier.fillMaxWidth(),
                        verticalAlignment = Alignment.CenterVertically,
                        horizontalArrangement = Arrangement.SpaceBetween
                    ) {
                        Row(
                            verticalAlignment = Alignment.CenterVertically,
                            horizontalArrangement = Arrangement.spacedBy(6.dp)
                        ) {
                            Icon(
                                imageVector = Icons.Filled.Bolt,
                                contentDescription = null,
                                tint = tColors.fpsOptimal,
                                modifier = Modifier.size(16.dp)
                            )
                            Text(
                                text = "GAMING MODE • ${maxRefreshHz}Hz",
                                style = MaterialTheme.typography.labelSmall,
                                color = tColors.fpsOptimal
                            )
                        }
                        Row(horizontalArrangement = Arrangement.spacedBy(2.dp)) {
                            HudMiniAction(
                                icon = Icons.Filled.Speed,
                                contentDescription = stringResource(R.string.cd_gpu_lab),
                                tint = tColors.gpuAccent,
                                tag = "hud_gpu_lab_button",
                                onClick = onOpenGpuLab
                            )
                            HudMiniAction(
                                icon = Icons.Filled.VisibilityOff,
                                contentDescription = stringResource(R.string.cd_hud_toggle),
                                tint = Color.White.copy(alpha = 0.8f),
                                tag = "hud_minimize_button",
                                onClick = onToggleHudExpanded
                            )
                            HudMiniAction(
                                icon = Icons.Filled.Close,
                                contentDescription = stringResource(R.string.cd_exit_gaming),
                                tint = tColors.criticalPulse,
                                tag = "hud_exit_gaming_button",
                                onClick = onExitGamingMode
                            )
                        }
                    }

                    Spacer(modifier = Modifier.height(8.dp))

                    Row(
                        modifier = Modifier.fillMaxWidth(),
                        horizontalArrangement = Arrangement.SpaceBetween,
                        verticalAlignment = Alignment.Bottom
                    ) {
                        HudStatCell(
                            value = "${telemetry.fps}",
                            label = "FPS",
                            color = fpsColor
                        )
                        HudStatCell(
                            value = String.format(Locale.US, "%.1fms", telemetry.frameTimeMs),
                            label = "FRAME",
                            color = tColors.gpuAccent
                        )
                        HudStatCell(
                            value = String.format(Locale.US, "%.0f°C", telemetry.batteryTempC),
                            label = telemetry.thermalState.display.uppercase(),
                            color = thermalColor
                        )
                        HudStatCell(
                            value = "${telemetry.appHeapMb}M",
                            label = "HEAP",
                            color = Color.White
                        )
                    }

                    Spacer(modifier = Modifier.height(8.dp))

                    FrameTimeSparkline(
                        samples = telemetry.recentFrameTimesMs,
                        lineColor = fpsColor,
                        warningColor = tColors.thermalWarn,
                        modifier = Modifier
                            .fillMaxWidth()
                            .height(26.dp)
                    )

                    Spacer(modifier = Modifier.height(6.dp))

                    Row(
                        modifier = Modifier.fillMaxWidth(),
                        horizontalArrangement = Arrangement.SpaceBetween
                    ) {
                        Text(
                            text = "SCALE ${renderScalePercent}%",
                            style = MaterialTheme.typography.labelSmall,
                            color = Color.White.copy(alpha = 0.65f)
                        )
                        Text(
                            text = "FROZEN $frozenTabCount TABS",
                            style = MaterialTheme.typography.labelSmall,
                            color = tColors.gpuAccent.copy(alpha = 0.9f)
                        )
                        Text(
                            text = "SHIELD $blockedCount",
                            style = MaterialTheme.typography.labelSmall,
                            color = tColors.fpsOptimal.copy(alpha = 0.9f)
                        )
                    }
                }
            }
        }
    }
}

@Composable
private fun HudStatCell(
    value: String,
    label: String,
    color: Color
) {
    Column(horizontalAlignment = Alignment.Start) {
        Text(
            text = value,
            style = MonoMetricMedium,
            fontWeight = FontWeight.Bold,
            color = color
        )
        Text(
            text = label,
            style = MaterialTheme.typography.labelSmall,
            color = Color.White.copy(alpha = 0.6f)
        )
    }
}

@Composable
private fun HudMiniAction(
    icon: androidx.compose.ui.graphics.vector.ImageVector,
    contentDescription: String,
    tint: Color,
    tag: String,
    onClick: () -> Unit
) {
    Box(
        modifier = Modifier
            .testTag(tag)
            .size(34.dp)
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
fun FrameTimeSparkline(
    samples: List<Float>,
    lineColor: Color,
    warningColor: Color,
    modifier: Modifier = Modifier
) {
    Canvas(modifier = modifier) {
        val w = size.width
        val h = size.height
        if (w <= 0f || h <= 0f) return@Canvas

        // 16.6ms (60fps) baseline guide
        val targetY = h * (1f - (16.6f / 40f).coerceIn(0f, 1f))
        drawLine(
            color = Color.White.copy(alpha = 0.14f),
            start = Offset(0f, targetY),
            end = Offset(w, targetY),
            strokeWidth = 1f
        )

        if (samples.size < 2) return@Canvas

        val stepX = w / (samples.size - 1).coerceAtLeast(1)
        val path = Path()
        samples.forEachIndexed { idx, ms ->
            val clamped = ms.coerceIn(4f, 40f)
            val x = idx * stepX
            val y = h * (1f - (clamped / 40f))
            if (idx == 0) path.moveTo(x, y) else path.lineTo(x, y)
        }

        val maxSpike = samples.maxOrNull() ?: 16.6f
        drawPath(
            path = path,
            color = if (maxSpike > 26f) warningColor else lineColor,
            style = Stroke(width = 2.2f, cap = StrokeCap.Round)
        )
    }
}
