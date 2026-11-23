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
import androidx.compose.material.icons.filled.BugReport
import androidx.compose.material.icons.filled.CollectionsBookmark
import androidx.compose.material.icons.filled.PlayArrow
import androidx.compose.material.icons.filled.Public
import androidx.compose.material.icons.filled.Settings
import androidx.compose.material.icons.filled.SportsEsports
import androidx.compose.material.icons.filled.VisibilityOff
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
import com.example.data.PulseRepository
import com.example.engine.DeviceCapabilitiesReport
import com.example.engine.DiagnosticsState
import com.example.ui.theme.LocalPulseSemanticColors
import com.example.ui.theme.MonoMetricSmall

@Composable
fun PulseStartPage(
    isPrivateTab: Boolean,
    capabilities: DeviceCapabilitiesReport,
    diagnostics: DiagnosticsState,
    bookmarks: List<BookmarkEntity>,
    onLaunchLastHavens: () -> Unit,
    onOpenUrl: (String) -> Unit,
    onOpenPrivateTab: () -> Unit,
    onOpenDiagnostics: () -> Unit,
    onOpenLibrary: () -> Unit,
    onOpenSettings: () -> Unit,
    modifier: Modifier = Modifier
) {
    val semantic = LocalPulseSemanticColors.current

    Box(
        modifier = modifier.fillMaxSize(),
        contentAlignment = Alignment.TopCenter
    ) {
        LazyColumn(
            modifier = Modifier
                .fillMaxSize()
                .widthIn(max = 860.dp),
            contentPadding = PaddingValues(16.dp),
            verticalArrangement = Arrangement.spacedBy(14.dp)
        ) {
            if (isPrivateTab) {
                item {
                    Surface(
                        shape = RoundedCornerShape(16.dp),
                        color = semantic.privateContainer,
                        border = BorderStroke(1.dp, semantic.privateViolet.copy(alpha = 0.65f)),
                        modifier = Modifier.fillMaxWidth()
                    ) {
                        Row(
                            modifier = Modifier.padding(14.dp),
                            verticalAlignment = Alignment.CenterVertically,
                            horizontalArrangement = Arrangement.spacedBy(10.dp)
                        ) {
                            Icon(
                                imageVector = Icons.Filled.VisibilityOff,
                                contentDescription = null,
                                tint = semantic.privateViolet
                            )
                            Column {
                                Text("Private Browsing Tab", style = MaterialTheme.typography.titleSmall, color = Color.White)
                                Text(
                                    "History is not saved, cookies are disabled for this session, and cache clears on close.",
                                    style = MaterialTheme.typography.bodySmall,
                                    color = Color(0xFFE9D5FF)
                                )
                            }
                        }
                    }
                }
            }

            // Primary Compatibility Target Hero Card — LastHavens
            item {
                Surface(
                    shape = RoundedCornerShape(22.dp),
                    border = BorderStroke(1.dp, semantic.border),
                    color = MaterialTheme.colorScheme.surface,
                    modifier = Modifier
                        .fillMaxWidth()
                        .height(216.dp)
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
                                        listOf(Color(0xF20B0F19), Color(0xC80B0F19), Color(0x520B0F19))
                                    )
                                )
                        )
                        Column(
                            modifier = Modifier
                                .fillMaxSize()
                                .padding(18.dp),
                            verticalArrangement = Arrangement.SpaceBetween
                        ) {
                            Row(horizontalArrangement = Arrangement.spacedBy(8.dp)) {
                                StatusPill(
                                    text = "PULSEBROWSER 2.0",
                                    color = MaterialTheme.colorScheme.primary
                                )
                                StatusPill(
                                    text = "WEBVIEW ${capabilities.webViewVersion.take(14)}",
                                    color = semantic.okGreen
                                )
                            }

                            Column {
                                Text(
                                    text = "LastHavens Direct Launch",
                                    style = MaterialTheme.typography.headlineSmall,
                                    color = Color.White
                                )
                                Text(
                                    text = PulseRepository.LASTHAVENS_PLAY_URL,
                                    style = MaterialTheme.typography.labelMedium,
                                    color = Color(0xFFCBD5E1),
                                    maxLines = 1,
                                    overflow = TextOverflow.Ellipsis
                                )
                                Text(
                                    text = "Zero response interception • Native WebGL2/Wasm • IndexedDB & HTTP cache ON",
                                    style = MaterialTheme.typography.bodySmall,
                                    color = Color(0xFF94A3B8),
                                    maxLines = 1,
                                    overflow = TextOverflow.Ellipsis
                                )
                            }

                            Row(horizontalArrangement = Arrangement.spacedBy(10.dp)) {
                                Button(
                                    onClick = onLaunchLastHavens,
                                    shape = RoundedCornerShape(12.dp),
                                    colors = ButtonDefaults.buttonColors(
                                        containerColor = MaterialTheme.colorScheme.primary,
                                        contentColor = MaterialTheme.colorScheme.onPrimary
                                    ),
                                    modifier = Modifier.testTag("start_launch_lasthavens_button")
                                ) {
                                    Icon(Icons.Filled.PlayArrow, contentDescription = null, modifier = Modifier.size(18.dp))
                                    Spacer(Modifier.width(6.dp))
                                    Text("Play LastHavens")
                                }

                                OutlinedButton(
                                    onClick = onOpenDiagnostics,
                                    shape = RoundedCornerShape(12.dp),
                                    border = BorderStroke(1.dp, Color.White.copy(alpha = 0.35f)),
                                    colors = ButtonDefaults.outlinedButtonColors(contentColor = Color.White),
                                    modifier = Modifier.testTag("start_open_diagnostics_button")
                                ) {
                                    Icon(Icons.Filled.BugReport, contentDescription = null, modifier = Modifier.size(16.dp))
                                    Spacer(Modifier.width(6.dp))
                                    Text("Diagnostics")
                                }
                            }
                        }
                    }
                }
            }

            // Live Device Engine Readiness Row
            item {
                Row(
                    modifier = Modifier.fillMaxWidth(),
                    horizontalArrangement = Arrangement.spacedBy(10.dp)
                ) {
                    ReadinessTile(
                        label = "DISPLAY MODES",
                        value = capabilities.selectableRefreshRatesHz.joinToString("/") { "${it}Hz" },
                        sub = "Active ${capabilities.activeDisplayHz.toInt()} Hz",
                        accent = MaterialTheme.colorScheme.primary,
                        onClick = onOpenSettings,
                        modifier = Modifier.weight(1f)
                    )
                    ReadinessTile(
                        label = "FREE RAM",
                        value = "${diagnostics.frameMetrics.availableRamMb} MB",
                        sub = "${capabilities.primaryAbi} • ${capabilities.cpuCores}c",
                        accent = semantic.okGreen,
                        onClick = onOpenDiagnostics,
                        modifier = Modifier.weight(1f)
                    )
                    ReadinessTile(
                        label = "DIAG ERRORS",
                        value = "${diagnostics.totalErrorsCount}",
                        sub = "Wasm ${diagnostics.wasmErrorsCount} • HTTP ${diagnostics.httpErrorsCount}",
                        accent = if (diagnostics.totalErrorsCount > 0) semantic.errorRose else semantic.okGreen,
                        onClick = onOpenDiagnostics,
                        modifier = Modifier.weight(1f)
                    )
                }
            }

            // Quick Tools Row
            item {
                Row(
                    modifier = Modifier.fillMaxWidth(),
                    horizontalArrangement = Arrangement.spacedBy(10.dp)
                ) {
                    QuickNavChip(
                        icon = Icons.Filled.VisibilityOff,
                        label = "Private Tab",
                        onClick = onOpenPrivateTab,
                        tag = "start_new_private_tab",
                        modifier = Modifier.weight(1f)
                    )
                    QuickNavChip(
                        icon = Icons.Filled.CollectionsBookmark,
                        label = "Library",
                        onClick = onOpenLibrary,
                        tag = "start_open_library",
                        modifier = Modifier.weight(1f)
                    )
                    QuickNavChip(
                        icon = Icons.Filled.Settings,
                        label = "Settings",
                        onClick = onOpenSettings,
                        tag = "start_open_settings",
                        modifier = Modifier.weight(1f)
                    )
                }
            }

            // Saved Bookmarks Speed Dial
            item {
                Text("Bookmarks & Gaming Portals", style = MaterialTheme.typography.titleMedium)
            }

            item {
                val pairs = bookmarks.chunked(2)
                Column(verticalArrangement = Arrangement.spacedBy(10.dp)) {
                    pairs.forEach { rowItems ->
                        Row(
                            modifier = Modifier.fillMaxWidth(),
                            horizontalArrangement = Arrangement.spacedBy(10.dp)
                        ) {
                            rowItems.forEach { bm ->
                                Surface(
                                    shape = RoundedCornerShape(14.dp),
                                    color = MaterialTheme.colorScheme.surfaceVariant,
                                    border = BorderStroke(1.dp, semantic.border),
                                    modifier = Modifier
                                        .weight(1f)
                                        .clip(RoundedCornerShape(14.dp))
                                        .clickable { onOpenUrl(bm.url) }
                                        .testTag("start_bookmark_${bm.id}")
                                ) {
                                    Row(
                                        modifier = Modifier.padding(12.dp),
                                        verticalAlignment = Alignment.CenterVertically,
                                        horizontalArrangement = Arrangement.spacedBy(10.dp)
                                    ) {
                                        Icon(
                                            imageVector = if (bm.isGamingSite) Icons.Filled.SportsEsports else Icons.Filled.Public,
                                            contentDescription = null,
                                            tint = if (bm.isGamingSite) MaterialTheme.colorScheme.primary else MaterialTheme.colorScheme.secondary,
                                            modifier = Modifier.size(20.dp)
                                        )
                                        Column(modifier = Modifier.weight(1f)) {
                                            Text(
                                                text = bm.title,
                                                style = MaterialTheme.typography.titleSmall,
                                                maxLines = 1,
                                                overflow = TextOverflow.Ellipsis
                                            )
                                            Text(
                                                text = bm.url,
                                                style = MaterialTheme.typography.labelSmall,
                                                color = MaterialTheme.colorScheme.onSurfaceVariant,
                                                maxLines = 1,
                                                overflow = TextOverflow.Ellipsis
                                            )
                                        }
                                    }
                                }
                            }
                            if (rowItems.size == 1) Spacer(Modifier.weight(1f))
                        }
                    }
                }
            }
        }
    }
}

@Composable
private fun StatusPill(text: String, color: Color) {
    Surface(
        shape = RoundedCornerShape(999.dp),
        color = color.copy(alpha = 0.16f),
        border = BorderStroke(1.dp, color.copy(alpha = 0.55f))
    ) {
        Text(
            text = text,
            style = MaterialTheme.typography.labelSmall,
            color = color,
            modifier = Modifier.padding(horizontal = 9.dp, vertical = 4.dp)
        )
    }
}

@Composable
private fun ReadinessTile(
    label: String,
    value: String,
    sub: String,
    accent: Color,
    onClick: () -> Unit,
    modifier: Modifier = Modifier
) {
    val semantic = LocalPulseSemanticColors.current
    Surface(
        shape = RoundedCornerShape(14.dp),
        color = MaterialTheme.colorScheme.surface,
        border = BorderStroke(1.dp, semantic.border),
        modifier = modifier
            .clip(RoundedCornerShape(14.dp))
            .clickable(onClick = onClick)
    ) {
        Column(modifier = Modifier.padding(11.dp)) {
            Text(label, style = MaterialTheme.typography.labelSmall, color = MaterialTheme.colorScheme.onSurfaceVariant)
            Spacer(Modifier.height(4.dp))
            Text(value, style = MonoMetricSmall, color = accent, maxLines = 1, overflow = TextOverflow.Ellipsis)
            Text(sub, style = MaterialTheme.typography.labelSmall, color = MaterialTheme.colorScheme.onSurfaceVariant, maxLines = 1, overflow = TextOverflow.Ellipsis)
        }
    }
}

@Composable
private fun QuickNavChip(
    icon: androidx.compose.ui.graphics.vector.ImageVector,
    label: String,
    onClick: () -> Unit,
    tag: String,
    modifier: Modifier = Modifier
) {
    val semantic = LocalPulseSemanticColors.current
    Surface(
        shape = RoundedCornerShape(12.dp),
        color = MaterialTheme.colorScheme.surface,
        border = BorderStroke(1.dp, semantic.border),
        modifier = modifier
            .clip(RoundedCornerShape(12.dp))
            .clickable(onClick = onClick)
            .testTag(tag)
    ) {
        Row(
            modifier = Modifier.padding(horizontal = 10.dp, vertical = 11.dp),
            horizontalArrangement = Arrangement.Center,
            verticalAlignment = Alignment.CenterVertically
        ) {
            Icon(icon, contentDescription = null, modifier = Modifier.size(16.dp), tint = MaterialTheme.colorScheme.primary)
            Spacer(Modifier.width(6.dp))
            Text(label, style = MaterialTheme.typography.labelLarge, maxLines = 1)
        }
    }
}
