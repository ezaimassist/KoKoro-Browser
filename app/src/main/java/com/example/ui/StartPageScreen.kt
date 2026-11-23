package com.example.ui

import androidx.compose.foundation.BorderStroke
import androidx.compose.foundation.Image
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
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.filled.Bolt
import androidx.compose.material.icons.filled.Memory
import androidx.compose.material.icons.filled.PlayArrow
import androidx.compose.material.icons.filled.Public
import androidx.compose.material.icons.filled.Shield
import androidx.compose.material.icons.filled.Speed
import androidx.compose.material.icons.filled.SportsEsports
import androidx.compose.material3.Button
import androidx.compose.material3.ButtonDefaults
import androidx.compose.material3.Icon
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.OutlinedButton
import androidx.compose.material3.Surface
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clip
import androidx.compose.ui.graphics.Brush
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.layout.ContentScale
import androidx.compose.ui.platform.testTag
import androidx.compose.ui.res.painterResource
import androidx.compose.ui.res.stringResource
import androidx.compose.ui.text.style.TextOverflow
import androidx.compose.ui.unit.dp
import com.example.R
import com.example.data.BookmarkEntity
import com.example.engine.HardwareGpuReport
import com.example.engine.PerformanceTelemetry
import com.example.engine.ShieldStats
import com.example.engine.UrlSecurityHelper
import com.example.ui.theme.LocalTelemetryColors
import com.example.ui.theme.MonoMetricMedium
import com.example.viewmodel.GamingPreset

@Composable
fun StartPageScreen(
    gpuReport: HardwareGpuReport,
    telemetry: PerformanceTelemetry,
    shieldStats: ShieldStats,
    bookmarks: List<BookmarkEntity>,
    activePreset: GamingPreset,
    gamingModeActive: Boolean,
    renderScalePercent: Int,
    onSelectPreset: (GamingPreset) -> Unit,
    onOpenUrl: (String) -> Unit,
    onToggleGamingMode: () -> Unit,
    onOpenGpuLab: () -> Unit,
    onOpenShield: () -> Unit,
    modifier: Modifier = Modifier
) {
    val tColors = LocalTelemetryColors.current

    Box(
        modifier = modifier.fillMaxSize(),
        contentAlignment = Alignment.TopCenter
    ) {
        LazyColumn(
            modifier = Modifier
                .fillMaxSize()
                .widthIn(max = 840.dp),
            contentPadding = PaddingValues(horizontal = 16.dp, vertical = 14.dp),
            verticalArrangement = Arrangement.spacedBy(16.dp)
        ) {
            // Hero Banner Card with generated tactical circuit artwork
            item {
                Surface(
                    shape = RoundedCornerShape(22.dp),
                    border = BorderStroke(1.dp, tColors.cardBorder),
                    color = MaterialTheme.colorScheme.surface,
                    modifier = Modifier
                        .fillMaxWidth()
                        .height(198.dp)
                ) {
                    Box(modifier = Modifier.fillMaxSize()) {
                        Image(
                            painter = painterResource(id = R.drawable.img_gaming_hero_1795386899459),
                            contentDescription = stringResource(R.string.cd_hero_banner),
                            contentScale = ContentScale.Crop,
                            modifier = Modifier.fillMaxSize()
                        )
                        Box(
                            modifier = Modifier
                                .fillMaxSize()
                                .background(
                                    Brush.horizontalGradient(
                                        colors = listOf(
                                            Color(0xF00A0C10),
                                            Color(0xC20A0C10),
                                            Color(0x400A0C10)
                                        )
                                    )
                                )
                        )
                        Column(
                            modifier = Modifier
                                .fillMaxSize()
                                .padding(18.dp),
                            verticalArrangement = Arrangement.SpaceBetween
                        ) {
                            Row(
                                verticalAlignment = Alignment.CenterVertically,
                                horizontalArrangement = Arrangement.spacedBy(8.dp)
                            ) {
                                Surface(
                                    shape = RoundedCornerShape(999.dp),
                                    color = tColors.fpsOptimal.copy(alpha = 0.16f),
                                    border = BorderStroke(1.dp, tColors.fpsOptimal.copy(alpha = 0.55f))
                                ) {
                                    Text(
                                        text = "BARE ENGINE • DE-GOOGLED",
                                        style = MaterialTheme.typography.labelSmall,
                                        color = tColors.fpsOptimal,
                                        modifier = Modifier.padding(horizontal = 10.dp, vertical = 4.dp)
                                    )
                                }
                                Surface(
                                    shape = RoundedCornerShape(999.dp),
                                    color = tColors.gpuAccent.copy(alpha = 0.16f),
                                    border = BorderStroke(1.dp, tColors.gpuAccent.copy(alpha = 0.5f))
                                ) {
                                    Text(
                                        text = "${gpuReport.maxRefreshRateHz.toInt()}Hz MAX",
                                        style = MaterialTheme.typography.labelSmall,
                                        color = tColors.gpuAccent,
                                        modifier = Modifier.padding(horizontal = 8.dp, vertical = 4.dp)
                                    )
                                }
                            }

                            Column {
                                Text(
                                    text = stringResource(R.string.app_name),
                                    style = MaterialTheme.typography.displaySmall,
                                    color = Color.White
                                )
                                Text(
                                    text = "${gpuReport.glRenderer} • ${gpuReport.vulkanVersionLabel}",
                                    style = MaterialTheme.typography.bodySmall,
                                    color = Color(0xFFCBD5E1),
                                    maxLines = 1,
                                    overflow = TextOverflow.Ellipsis
                                )
                            }

                            Row(horizontalArrangement = Arrangement.spacedBy(10.dp)) {
                                Button(
                                    onClick = { onOpenUrl(UrlSecurityHelper.INTERNAL_BENCHMARK) },
                                    shape = RoundedCornerShape(12.dp),
                                    colors = ButtonDefaults.buttonColors(
                                        containerColor = tColors.fpsOptimal,
                                        contentColor = Color(0xFF0A1400)
                                    ),
                                    contentPadding = PaddingValues(horizontal = 14.dp, vertical = 8.dp),
                                    modifier = Modifier.testTag("start_launch_benchmark_button")
                                ) {
                                    Icon(
                                        imageVector = Icons.Filled.PlayArrow,
                                        contentDescription = null,
                                        modifier = Modifier.size(18.dp)
                                    )
                                    Spacer(modifier = Modifier.width(6.dp))
                                    Text(
                                        text = "WebGL2 Lab",
                                        style = MaterialTheme.typography.labelLarge
                                    )
                                }

                                OutlinedButton(
                                    onClick = onToggleGamingMode,
                                    shape = RoundedCornerShape(12.dp),
                                    border = BorderStroke(
                                        1.dp,
                                        if (gamingModeActive) tColors.fpsOptimal else Color.White.copy(alpha = 0.35f)
                                    ),
                                    colors = ButtonDefaults.outlinedButtonColors(
                                        contentColor = if (gamingModeActive) tColors.fpsOptimal else Color.White
                                    ),
                                    contentPadding = PaddingValues(horizontal = 14.dp, vertical = 8.dp),
                                    modifier = Modifier.testTag("start_gaming_mode_button")
                                ) {
                                    Icon(
                                        imageVector = Icons.Filled.Bolt,
                                        contentDescription = null,
                                        modifier = Modifier.size(16.dp)
                                    )
                                    Spacer(modifier = Modifier.width(4.dp))
                                    Text(
                                        text = if (gamingModeActive) "Gaming ON" else "Gaming Mode",
                                        style = MaterialTheme.typography.labelLarge
                                    )
                                }
                            }
                        }
                    }
                }
            }

            // Live Telemetry Strip (Real Hardware Readings)
            item {
                Row(
                    modifier = Modifier.fillMaxWidth(),
                    horizontalArrangement = Arrangement.spacedBy(10.dp)
                ) {
                    StartMetricTile(
                        title = "LIVE FPS",
                        value = "${telemetry.fps}",
                        sub = "${String.format(java.util.Locale.US, "%.1f", telemetry.frameTimeMs)} ms",
                        accent = tColors.fpsOptimal,
                        onClick = onOpenGpuLab,
                        tag = "start_metric_fps",
                        modifier = Modifier.weight(1f)
                    )
                    StartMetricTile(
                        title = "GPU SCALE",
                        value = "${renderScalePercent}%",
                        sub = if (gpuReport.vulkanSupported) "Vulkan Ready" else "GLES 3",
                        accent = tColors.gpuAccent,
                        onClick = onOpenGpuLab,
                        tag = "start_metric_gpu",
                        modifier = Modifier.weight(1f)
                    )
                    StartMetricTile(
                        title = "SHIELD",
                        value = "${shieldStats.totalBlocked}",
                        sub = "${shieldStats.estimatedSavedKb} KB saved",
                        accent = tColors.thermalWarn,
                        onClick = onOpenShield,
                        tag = "start_metric_shield",
                        modifier = Modifier.weight(1f)
                    )
                }
            }

            // Gaming Performance Presets
            item {
                Column(verticalArrangement = Arrangement.spacedBy(8.dp)) {
                    Row(
                        modifier = Modifier.fillMaxWidth(),
                        horizontalArrangement = Arrangement.SpaceBetween,
                        verticalAlignment = Alignment.CenterVertically
                    ) {
                        Text(
                            text = "Engine Performance Profile",
                            style = MaterialTheme.typography.titleMedium
                        )
                        Text(
                            text = "GPU Lab →",
                            style = MaterialTheme.typography.labelMedium,
                            color = MaterialTheme.colorScheme.secondary,
                            modifier = Modifier
                                .clip(RoundedCornerShape(8.dp))
                                .clickable(onClick = onOpenGpuLab)
                                .padding(horizontal = 6.dp, vertical = 4.dp)
                                .testTag("start_open_gpu_lab_link")
                        )
                    }

                    Row(
                        modifier = Modifier.fillMaxWidth(),
                        horizontalArrangement = Arrangement.spacedBy(8.dp)
                    ) {
                        GamingPreset.entries.forEach { preset ->
                            val selected = preset == activePreset
                            Surface(
                                shape = RoundedCornerShape(14.dp),
                                color = if (selected) {
                                    MaterialTheme.colorScheme.primaryContainer
                                } else {
                                    MaterialTheme.colorScheme.surfaceVariant
                                },
                                border = BorderStroke(
                                    width = if (selected) 1.5.dp else 1.dp,
                                    color = if (selected) MaterialTheme.colorScheme.primary else tColors.cardBorder
                                ),
                                modifier = Modifier
                                    .weight(1f)
                                    .clip(RoundedCornerShape(14.dp))
                                    .clickable { onSelectPreset(preset) }
                                    .testTag("preset_${preset.name.lowercase()}")
                            ) {
                                Column(
                                    modifier = Modifier.padding(10.dp),
                                    verticalArrangement = Arrangement.spacedBy(4.dp)
                                ) {
                                    Text(
                                        text = preset.label,
                                        style = MaterialTheme.typography.labelLarge,
                                        color = if (selected) {
                                            MaterialTheme.colorScheme.onPrimaryContainer
                                        } else {
                                            MaterialTheme.colorScheme.onSurface
                                        }
                                    )
                                    Text(
                                        text = preset.subtitle,
                                        style = MaterialTheme.typography.bodySmall,
                                        color = MaterialTheme.colorScheme.onSurfaceVariant,
                                        maxLines = 2,
                                        overflow = TextOverflow.Ellipsis
                                    )
                                }
                            }
                        }
                    }
                }
            }

            // Quick-Launch Speed Dial (Bookmarks & Gaming Portals)
            item {
                Row(
                    modifier = Modifier.fillMaxWidth(),
                    horizontalArrangement = Arrangement.SpaceBetween,
                    verticalAlignment = Alignment.CenterVertically
                ) {
                    Text(
                        text = "Gaming Speed Dial & Bookmarks",
                        style = MaterialTheme.typography.titleMedium
                    )
                    Text(
                        text = "Manage",
                        style = MaterialTheme.typography.labelMedium,
                        color = MaterialTheme.colorScheme.primary,
                        modifier = Modifier
                            .clip(RoundedCornerShape(8.dp))
                            .clickable(onClick = onOpenShield)
                            .padding(horizontal = 6.dp, vertical = 4.dp)
                            .testTag("start_manage_bookmarks_link")
                    )
                }
            }

            item {
                val rows = bookmarks.chunked(2)
                Column(verticalArrangement = Arrangement.spacedBy(10.dp)) {
                    rows.forEach { pair ->
                        Row(
                            modifier = Modifier.fillMaxWidth(),
                            horizontalArrangement = Arrangement.spacedBy(10.dp)
                        ) {
                            pair.forEach { bookmark ->
                                SpeedDialTile(
                                    bookmark = bookmark,
                                    onClick = { onOpenUrl(bookmark.url) },
                                    modifier = Modifier.weight(1f)
                                )
                            }
                            if (pair.size == 1) {
                                Spacer(modifier = Modifier.weight(1f))
                            }
                        }
                    }
                }
            }

            // Quick Tools Row
            item {
                Row(
                    modifier = Modifier.fillMaxWidth(),
                    horizontalArrangement = Arrangement.spacedBy(10.dp)
                ) {
                    QuickActionCard(
                        icon = Icons.Filled.Memory,
                        title = "GPU & Bottleneck Audit",
                        subtitle = "EGL, Vulkan, Skia & flags",
                        accent = tColors.gpuAccent,
                        onClick = onOpenGpuLab,
                        tag = "start_card_gpu_audit",
                        modifier = Modifier.weight(1f)
                    )
                    QuickActionCard(
                        icon = Icons.Filled.Shield,
                        title = "Shield & Site Profiles",
                        subtitle = "Blockers & per-site Hz/DPR",
                        accent = tColors.fpsOptimal,
                        onClick = onOpenShield,
                        tag = "start_card_shield_profiles",
                        modifier = Modifier.weight(1f)
                    )
                }
            }
        }
    }
}

@Composable
private fun StartMetricTile(
    title: String,
    value: String,
    sub: String,
    accent: Color,
    onClick: () -> Unit,
    tag: String,
    modifier: Modifier = Modifier
) {
    val tColors = LocalTelemetryColors.current
    Surface(
        shape = RoundedCornerShape(14.dp),
        color = MaterialTheme.colorScheme.surface,
        border = BorderStroke(1.dp, tColors.cardBorder),
        modifier = modifier
            .clip(RoundedCornerShape(14.dp))
            .clickable(onClick = onClick)
            .testTag(tag)
    ) {
        Column(modifier = Modifier.padding(12.dp)) {
            Text(
                text = title,
                style = MaterialTheme.typography.labelSmall,
                color = MaterialTheme.colorScheme.onSurfaceVariant
            )
            Spacer(modifier = Modifier.height(4.dp))
            Text(
                text = value,
                style = MonoMetricMedium,
                color = accent
            )
            Text(
                text = sub,
                style = MaterialTheme.typography.bodySmall,
                color = MaterialTheme.colorScheme.onSurfaceVariant,
                maxLines = 1,
                overflow = TextOverflow.Ellipsis
            )
        }
    }
}

@Composable
private fun SpeedDialTile(
    bookmark: BookmarkEntity,
    onClick: () -> Unit,
    modifier: Modifier = Modifier
) {
    val tColors = LocalTelemetryColors.current
    Surface(
        shape = RoundedCornerShape(14.dp),
        color = MaterialTheme.colorScheme.surfaceVariant,
        border = BorderStroke(1.dp, tColors.cardBorder),
        modifier = modifier
            .clip(RoundedCornerShape(14.dp))
            .clickable(onClick = onClick)
            .testTag("speed_dial_${bookmark.id}")
    ) {
        Row(
            modifier = Modifier.padding(12.dp),
            verticalAlignment = Alignment.CenterVertically,
            horizontalArrangement = Arrangement.spacedBy(10.dp)
        ) {
            Box(
                modifier = Modifier
                    .size(38.dp)
                    .clip(RoundedCornerShape(10.dp))
                    .background(
                        if (bookmark.isGamingPortal) {
                            MaterialTheme.colorScheme.primaryContainer
                        } else {
                            MaterialTheme.colorScheme.secondaryContainer
                        }
                    ),
                contentAlignment = Alignment.Center
            ) {
                Icon(
                    imageVector = if (bookmark.url == UrlSecurityHelper.INTERNAL_BENCHMARK) {
                        Icons.Filled.Speed
                    } else if (bookmark.isGamingPortal) {
                        Icons.Filled.SportsEsports
                    } else {
                        Icons.Filled.Public
                    },
                    contentDescription = null,
                    tint = if (bookmark.isGamingPortal) {
                        MaterialTheme.colorScheme.primary
                    } else {
                        MaterialTheme.colorScheme.secondary
                    },
                    modifier = Modifier.size(20.dp)
                )
            }
            Column(modifier = Modifier.weight(1f)) {
                Text(
                    text = bookmark.title,
                    style = MaterialTheme.typography.titleSmall,
                    maxLines = 1,
                    overflow = TextOverflow.Ellipsis
                )
                Text(
                    text = bookmark.category.uppercase(),
                    style = MaterialTheme.typography.labelSmall,
                    color = MaterialTheme.colorScheme.onSurfaceVariant
                )
            }
        }
    }
}

@Composable
private fun QuickActionCard(
    icon: androidx.compose.ui.graphics.vector.ImageVector,
    title: String,
    subtitle: String,
    accent: Color,
    onClick: () -> Unit,
    tag: String,
    modifier: Modifier = Modifier
) {
    val tColors = LocalTelemetryColors.current
    Surface(
        shape = RoundedCornerShape(16.dp),
        color = MaterialTheme.colorScheme.surface,
        border = BorderStroke(1.dp, tColors.cardBorder),
        modifier = modifier
            .clip(RoundedCornerShape(16.dp))
            .clickable(onClick = onClick)
            .testTag(tag)
    ) {
        Column(
            modifier = Modifier.padding(14.dp),
            verticalArrangement = Arrangement.spacedBy(6.dp)
        ) {
            Icon(
                imageVector = icon,
                contentDescription = null,
                tint = accent,
                modifier = Modifier.size(22.dp)
            )
            Text(text = title, style = MaterialTheme.typography.titleSmall)
            Text(
                text = subtitle,
                style = MaterialTheme.typography.bodySmall,
                color = MaterialTheme.colorScheme.onSurfaceVariant
            )
        }
    }
}
