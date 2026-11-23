package com.example.ui

import androidx.activity.compose.BackHandler
import androidx.compose.foundation.BorderStroke
import androidx.compose.foundation.background
import androidx.compose.foundation.clickable
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.PaddingValues
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.width
import androidx.compose.foundation.layout.widthIn
import androidx.compose.foundation.lazy.LazyColumn
import androidx.compose.foundation.lazy.items
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.automirrored.filled.ArrowBack
import androidx.compose.material.icons.filled.Add
import androidx.compose.material.icons.filled.Delete
import androidx.compose.material.icons.filled.Shield
import androidx.compose.material.icons.filled.SportsEsports
import androidx.compose.material3.Button
import androidx.compose.material3.Icon
import androidx.compose.material3.IconButton
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.OutlinedButton
import androidx.compose.material3.OutlinedTextField
import androidx.compose.material3.Surface
import androidx.compose.material3.Switch
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableIntStateOf
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.setValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clip
import androidx.compose.ui.platform.testTag
import androidx.compose.ui.res.stringResource
import androidx.compose.ui.text.style.TextOverflow
import androidx.compose.ui.unit.dp
import com.example.R
import com.example.data.BookmarkEntity
import com.example.data.BrowserSettings
import com.example.data.SiteGamingProfileEntity
import com.example.engine.ShieldStats
import com.example.engine.UrlSecurityHelper
import com.example.ui.theme.LocalTelemetryColors
import com.example.ui.theme.MonoCodeStyle
import com.example.ui.theme.MonoMetricLarge

@Composable
fun ShieldAndProfilesScreen(
    activeUrl: String,
    settings: BrowserSettings,
    shieldStats: ShieldStats,
    bookmarks: List<BookmarkEntity>,
    siteProfiles: List<SiteGamingProfileEntity>,
    onUpdateSettings: ((BrowserSettings) -> BrowserSettings) -> Unit,
    onResetShieldStats: () -> Unit,
    onOpenUrl: (String) -> Unit,
    onAddBookmark: (title: String, url: String, isGaming: Boolean) -> Unit,
    onDeleteBookmark: (Long) -> Unit,
    onSaveSiteProfile: (host: String, autoGaming: Boolean, renderScale: Int, disableAa: Boolean) -> Unit,
    onDeleteSiteProfile: (String) -> Unit,
    onBack: () -> Unit,
    modifier: Modifier = Modifier
) {
    BackHandler(onBack = onBack)
    val tColors = LocalTelemetryColors.current

    val activeHost = UrlSecurityHelper.extractHost(activeUrl).orEmpty()
    var profileHostInput by remember(activeHost) { mutableStateOf(if (activeHost == "home") "" else activeHost) }
    var profileScale by remember { mutableIntStateOf(85) }
    var profileDisableAa by remember { mutableStateOf(false) }

    var bookmarkTitleInput by remember { mutableStateOf("") }
    var bookmarkUrlInput by remember { mutableStateOf("") }

    Column(
        modifier = modifier
            .fillMaxSize()
            .background(MaterialTheme.colorScheme.background),
        horizontalAlignment = Alignment.CenterHorizontally
    ) {
        Row(
            modifier = Modifier
                .fillMaxWidth()
                .widthIn(max = 840.dp)
                .padding(horizontal = 8.dp, vertical = 6.dp),
            verticalAlignment = Alignment.CenterVertically
        ) {
            IconButton(
                onClick = onBack,
                modifier = Modifier.testTag("shield_screen_back_button")
            ) {
                Icon(
                    imageVector = Icons.AutoMirrored.Filled.ArrowBack,
                    contentDescription = stringResource(R.string.cd_back)
                )
            }
            Column {
                Text(
                    text = "Shield, Profiles & Bookmarks",
                    style = MaterialTheme.typography.titleLarge
                )
                Text(
                    text = "100% Local Room Storage • Zero Cloud Telemetry",
                    style = MaterialTheme.typography.labelSmall,
                    color = MaterialTheme.colorScheme.onSurfaceVariant
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
            // Network Shield Overview Card
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
                        Row(
                            modifier = Modifier.fillMaxWidth(),
                            horizontalArrangement = Arrangement.SpaceBetween,
                            verticalAlignment = Alignment.CenterVertically
                        ) {
                            Row(
                                verticalAlignment = Alignment.CenterVertically,
                                horizontalArrangement = Arrangement.spacedBy(8.dp)
                            ) {
                                Icon(
                                    imageVector = Icons.Filled.Shield,
                                    contentDescription = null,
                                    tint = tColors.fpsOptimal
                                )
                                Text("Bare Network & Tracker Shield", style = MaterialTheme.typography.titleMedium)
                            }
                            Switch(
                                checked = settings.shieldEnabled,
                                onCheckedChange = { enabled -> onUpdateSettings { it.copy(shieldEnabled = enabled) } },
                                modifier = Modifier.testTag("shield_master_switch")
                            )
                        }

                        Row(
                            modifier = Modifier.fillMaxWidth(),
                            horizontalArrangement = Arrangement.SpaceBetween
                        ) {
                            Column {
                                Text("TOTAL BLOCKED", style = MaterialTheme.typography.labelSmall, color = MaterialTheme.colorScheme.onSurfaceVariant)
                                Text("${shieldStats.totalBlocked}", style = MonoMetricLarge, color = tColors.fpsOptimal)
                            }
                            Column {
                                Text("IN GAMING MODE", style = MaterialTheme.typography.labelSmall, color = MaterialTheme.colorScheme.onSurfaceVariant)
                                Text("${shieldStats.gamingModeBlocked}", style = MonoMetricLarge, color = tColors.gpuAccent)
                            }
                            Column {
                                Text("SAVED DATA", style = MaterialTheme.typography.labelSmall, color = MaterialTheme.colorScheme.onSurfaceVariant)
                                Text("${shieldStats.estimatedSavedKb} KB", style = MonoMetricLarge, color = tColors.thermalWarn)
                            }
                        }

                        Row(
                            modifier = Modifier.fillMaxWidth(),
                            horizontalArrangement = Arrangement.SpaceBetween,
                            verticalAlignment = Alignment.CenterVertically
                        ) {
                            Text("HTTPS-First Strict Upgrade", style = MaterialTheme.typography.bodyMedium)
                            Switch(
                                checked = settings.httpsOnly,
                                onCheckedChange = { v -> onUpdateSettings { it.copy(httpsOnly = v) } },
                                modifier = Modifier.testTag("https_only_switch")
                            )
                        }

                        if (shieldStats.recentBlockedHosts.isNotEmpty()) {
                            Text(
                                text = "Recently Intercepted Trackers",
                                style = MaterialTheme.typography.labelMedium,
                                color = MaterialTheme.colorScheme.onSurfaceVariant
                            )
                            shieldStats.recentBlockedHosts.take(5).forEach { host ->
                                Text(
                                    text = "✕ $host",
                                    style = MonoCodeStyle,
                                    color = tColors.criticalPulse
                                )
                            }
                            OutlinedButton(
                                onClick = onResetShieldStats,
                                shape = RoundedCornerShape(10.dp),
                                modifier = Modifier.testTag("reset_shield_stats_button")
                            ) {
                                Text("Reset Session Counters")
                            }
                        } else {
                            Text(
                                text = "No third-party trackers intercepted yet in this session.",
                                style = MaterialTheme.typography.bodySmall,
                                color = MaterialTheme.colorScheme.onSurfaceVariant
                            )
                        }
                    }
                }
            }

            // Per-Site Gaming Profiles Section
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
                        Row(
                            verticalAlignment = Alignment.CenterVertically,
                            horizontalArrangement = Arrangement.spacedBy(8.dp)
                        ) {
                            Icon(
                                imageVector = Icons.Filled.SportsEsports,
                                contentDescription = null,
                                tint = tColors.gpuAccent
                            )
                            Text("Per-Site Gaming Mode Profiles", style = MaterialTheme.typography.titleMedium)
                        }
                        Text(
                            text = "Auto-triggers Gaming Mode and applies custom GPU render scaling when you visit a domain.",
                            style = MaterialTheme.typography.bodySmall,
                            color = MaterialTheme.colorScheme.onSurfaceVariant
                        )

                        OutlinedTextField(
                            value = profileHostInput,
                            onValueChange = { profileHostInput = it },
                            label = { Text("Domain (e.g. krunker.io)") },
                            singleLine = true,
                            modifier = Modifier
                                .fillMaxWidth()
                                .testTag("profile_host_input")
                        )

                        Row(
                            modifier = Modifier.fillMaxWidth(),
                            horizontalArrangement = Arrangement.spacedBy(8.dp)
                        ) {
                            listOf(100, 85, 75, 70).forEach { scale ->
                                val selected = profileScale == scale
                                Surface(
                                    shape = RoundedCornerShape(10.dp),
                                    color = if (selected) MaterialTheme.colorScheme.primary else MaterialTheme.colorScheme.surfaceVariant,
                                    modifier = Modifier
                                        .weight(1f)
                                        .clip(RoundedCornerShape(10.dp))
                                        .clickable { profileScale = scale }
                                        .testTag("profile_scale_$scale")
                                ) {
                                    Text(
                                        text = "$scale%",
                                        style = MaterialTheme.typography.labelMedium,
                                        color = if (selected) MaterialTheme.colorScheme.onPrimary else MaterialTheme.colorScheme.onSurface,
                                        modifier = Modifier.padding(vertical = 8.dp, horizontal = 8.dp)
                                    )
                                }
                            }
                        }

                        Row(
                            modifier = Modifier.fillMaxWidth(),
                            horizontalArrangement = Arrangement.SpaceBetween,
                            verticalAlignment = Alignment.CenterVertically
                        ) {
                            Text("Disable MSAA on this domain", style = MaterialTheme.typography.bodySmall)
                            Switch(
                                checked = profileDisableAa,
                                onCheckedChange = { profileDisableAa = it },
                                modifier = Modifier.testTag("profile_disable_aa_switch")
                            )
                        }

                        Button(
                            onClick = {
                                if (profileHostInput.isNotBlank()) {
                                    onSaveSiteProfile(profileHostInput, true, profileScale, profileDisableAa)
                                    profileHostInput = ""
                                }
                            },
                            shape = RoundedCornerShape(12.dp),
                            modifier = Modifier
                                .fillMaxWidth()
                                .testTag("save_site_profile_button")
                        ) {
                            Icon(Icons.Filled.Add, contentDescription = null)
                            Spacer(Modifier.width(6.dp))
                            Text("Save Site Gaming Profile")
                        }
                    }
                }
            }

            items(siteProfiles, key = { it.host }) { profile ->
                Surface(
                    shape = RoundedCornerShape(14.dp),
                    color = MaterialTheme.colorScheme.surfaceVariant,
                    border = BorderStroke(1.dp, tColors.cardBorder),
                    modifier = Modifier.fillMaxWidth()
                ) {
                    Row(
                        modifier = Modifier
                            .fillMaxWidth()
                            .clickable { onOpenUrl("https://${profile.host}") }
                            .padding(horizontal = 14.dp, vertical = 10.dp),
                        verticalAlignment = Alignment.CenterVertically,
                        horizontalArrangement = Arrangement.SpaceBetween
                    ) {
                        Column(modifier = Modifier.weight(1f)) {
                            Text(profile.host, style = MaterialTheme.typography.titleSmall)
                            Text(
                                text = "Scale ${profile.renderScalePercent}% • Auto Gaming: ${if (profile.autoGamingMode) "ON" else "OFF"} • AA: ${if (profile.disableAntialias) "OFF" else "AUTO"}",
                                style = MaterialTheme.typography.labelSmall,
                                color = MaterialTheme.colorScheme.onSurfaceVariant
                            )
                        }
                        IconButton(
                            onClick = { onDeleteSiteProfile(profile.host) },
                            modifier = Modifier.testTag("delete_profile_${profile.host}")
                        ) {
                            Icon(
                                imageVector = Icons.Filled.Delete,
                                contentDescription = "Delete profile for ${profile.host}",
                                tint = MaterialTheme.colorScheme.error
                            )
                        }
                    }
                }
            }

            // Local Bookmarks Section
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
                        Text("Local Bookmarks (${bookmarks.size})", style = MaterialTheme.typography.titleMedium)
                        OutlinedTextField(
                            value = bookmarkTitleInput,
                            onValueChange = { bookmarkTitleInput = it },
                            label = { Text("Title") },
                            singleLine = true,
                            modifier = Modifier
                                .fillMaxWidth()
                                .testTag("bookmark_title_input")
                        )
                        OutlinedTextField(
                            value = bookmarkUrlInput,
                            onValueChange = { bookmarkUrlInput = it },
                            label = { Text("URL (https://...)") },
                            singleLine = true,
                            modifier = Modifier
                                .fillMaxWidth()
                                .testTag("bookmark_url_input")
                        )
                        Button(
                            onClick = {
                                if (bookmarkUrlInput.isNotBlank()) {
                                    onAddBookmark(bookmarkTitleInput, bookmarkUrlInput, true)
                                    bookmarkTitleInput = ""
                                    bookmarkUrlInput = ""
                                }
                            },
                            shape = RoundedCornerShape(12.dp),
                            modifier = Modifier
                                .fillMaxWidth()
                                .testTag("add_bookmark_button")
                        ) {
                            Icon(Icons.Filled.Add, contentDescription = null)
                            Spacer(Modifier.width(6.dp))
                            Text("Save Local Bookmark")
                        }
                    }
                }
            }

            items(bookmarks, key = { it.id }) { bm ->
                Surface(
                    shape = RoundedCornerShape(14.dp),
                    color = MaterialTheme.colorScheme.surfaceVariant,
                    border = BorderStroke(1.dp, tColors.cardBorder),
                    modifier = Modifier.fillMaxWidth()
                ) {
                    Row(
                        modifier = Modifier
                            .fillMaxWidth()
                            .clickable { onOpenUrl(bm.url) }
                            .padding(horizontal = 14.dp, vertical = 10.dp),
                        verticalAlignment = Alignment.CenterVertically,
                        horizontalArrangement = Arrangement.SpaceBetween
                    ) {
                        Column(modifier = Modifier.weight(1f)) {
                            Text(
                                text = bm.title,
                                style = MaterialTheme.typography.titleSmall,
                                maxLines = 1,
                                overflow = TextOverflow.Ellipsis
                            )
                            Text(
                                text = bm.url,
                                style = MonoCodeStyle,
                                color = MaterialTheme.colorScheme.onSurfaceVariant,
                                maxLines = 1,
                                overflow = TextOverflow.Ellipsis
                            )
                        }
                        IconButton(
                            onClick = { onDeleteBookmark(bm.id) },
                            modifier = Modifier.testTag("delete_bookmark_${bm.id}")
                        ) {
                            Icon(
                                imageVector = Icons.Filled.Delete,
                                contentDescription = "Delete bookmark ${bm.title}",
                                tint = MaterialTheme.colorScheme.error
                            )
                        }
                    }
                }
            }

            item { Spacer(Modifier.height(16.dp)) }
        }
    }
}
