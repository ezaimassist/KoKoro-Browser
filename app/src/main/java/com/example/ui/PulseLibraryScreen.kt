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
import androidx.compose.material3.Button
import androidx.compose.material3.Icon
import androidx.compose.material3.IconButton
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.OutlinedButton
import androidx.compose.material3.OutlinedTextField
import androidx.compose.material3.ScrollableTabRow
import androidx.compose.material3.Surface
import androidx.compose.material3.Switch
import androidx.compose.material3.Tab
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableIntStateOf
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.saveable.rememberSaveable
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
import com.example.data.DownloadEntity
import com.example.data.HistoryEntity
import com.example.data.SiteGamingProfileEntity
import com.example.engine.UrlUtils
import com.example.ui.theme.LocalPulseSemanticColors
import com.example.ui.theme.MonoLogStyle
import java.text.SimpleDateFormat
import java.util.Date
import java.util.Locale

@Composable
fun PulseLibraryScreen(
    activeUrl: String,
    bookmarks: List<BookmarkEntity>,
    history: List<HistoryEntity>,
    downloads: List<DownloadEntity>,
    siteProfiles: List<SiteGamingProfileEntity>,
    supportedRefreshRates: List<Int>,
    onOpenUrl: (String) -> Unit,
    onAddBookmark: (String, String, Boolean) -> Unit,
    onDeleteBookmark: (Long) -> Unit,
    onDeleteHistory: (Long) -> Unit,
    onClearHistory: () -> Unit,
    onDeleteDownload: (Long) -> Unit,
    onClearDownloads: () -> Unit,
    onSaveProfile: (SiteGamingProfileEntity) -> Unit,
    onDeleteProfile: (String) -> Unit,
    onBack: () -> Unit,
    modifier: Modifier = Modifier
) {
    BackHandler(onBack = onBack)
    val semantic = LocalPulseSemanticColors.current
    var selectedTab by rememberSaveable { mutableIntStateOf(0) }
    val tabs = listOf(
        "Bookmarks (${bookmarks.size})",
        "History (${history.size})",
        "Downloads (${downloads.size})",
        "Site Profiles (${siteProfiles.size})"
    )

    var bmTitle by remember { mutableStateOf("") }
    var bmUrl by remember { mutableStateOf("") }

    val activeHost = UrlUtils.extractHost(activeUrl).orEmpty()
    var profileHost by remember(activeHost) { mutableStateOf(activeHost) }
    var profileAutoGaming by remember { mutableStateOf(true) }
    var profileLandscape by remember { mutableStateOf(true) }
    var profileHideUi by remember { mutableStateOf(true) }
    var profileDesktopUa by remember { mutableStateOf(false) }
    var profileHz by remember { mutableIntStateOf(0) }
    var profileScale by remember { mutableIntStateOf(100) }

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
            verticalAlignment = Alignment.CenterVertically
        ) {
            IconButton(
                onClick = onBack,
                modifier = Modifier.testTag("library_back_button")
            ) {
                Icon(Icons.AutoMirrored.Filled.ArrowBack, contentDescription = stringResource(R.string.cd_back))
            }
            Text("Library & Gaming Profiles", style = MaterialTheme.typography.titleLarge)
        }

        ScrollableTabRow(
            selectedTabIndex = selectedTab,
            edgePadding = 12.dp,
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
                    modifier = Modifier.testTag("library_tab_$idx")
                )
            }
        }

        LazyColumn(
            modifier = Modifier
                .fillMaxSize()
                .widthIn(max = 860.dp),
            contentPadding = PaddingValues(16.dp),
            verticalArrangement = Arrangement.spacedBy(10.dp)
        ) {
            when (selectedTab) {
                0 -> {
                    item {
                        Surface(
                            shape = RoundedCornerShape(16.dp),
                            color = MaterialTheme.colorScheme.surface,
                            border = BorderStroke(1.dp, semantic.border),
                            modifier = Modifier.fillMaxWidth()
                        ) {
                            Column(
                                modifier = Modifier.padding(14.dp),
                                verticalArrangement = Arrangement.spacedBy(8.dp)
                            ) {
                                Text("Add Bookmark", style = MaterialTheme.typography.titleSmall)
                                OutlinedTextField(
                                    value = bmTitle,
                                    onValueChange = { bmTitle = it },
                                    label = { Text("Title") },
                                    singleLine = true,
                                    modifier = Modifier
                                        .fillMaxWidth()
                                        .testTag("lib_bm_title")
                                )
                                OutlinedTextField(
                                    value = bmUrl,
                                    onValueChange = { bmUrl = it },
                                    label = { Text("URL (https://...)") },
                                    singleLine = true,
                                    modifier = Modifier
                                        .fillMaxWidth()
                                        .testTag("lib_bm_url")
                                )
                                Button(
                                    onClick = {
                                        if (bmUrl.isNotBlank()) {
                                            onAddBookmark(bmTitle, bmUrl, true)
                                            bmTitle = ""
                                            bmUrl = ""
                                        }
                                    },
                                    shape = RoundedCornerShape(12.dp),
                                    modifier = Modifier
                                        .fillMaxWidth()
                                        .testTag("lib_bm_add_button")
                                ) {
                                    Icon(Icons.Filled.Add, contentDescription = null)
                                    Spacer(Modifier.width(6.dp))
                                    Text("Save Bookmark")
                                }
                            }
                        }
                    }

                    items(bookmarks, key = { it.id }) { bm ->
                        LibraryRowCard(
                            title = bm.title,
                            subtitle = bm.url,
                            onClick = { onOpenUrl(bm.url) },
                            onDelete = { onDeleteBookmark(bm.id) }
                        )
                    }
                }

                1 -> {
                    item {
                        Row(
                            modifier = Modifier.fillMaxWidth(),
                            horizontalArrangement = Arrangement.SpaceBetween,
                            verticalAlignment = Alignment.CenterVertically
                        ) {
                            Text(
                                "Private tabs are never logged in History.",
                                style = MaterialTheme.typography.bodySmall,
                                color = MaterialTheme.colorScheme.onSurfaceVariant
                            )
                            OutlinedButton(
                                onClick = onClearHistory,
                                shape = RoundedCornerShape(10.dp),
                                modifier = Modifier.testTag("lib_clear_history_button")
                            ) {
                                Text("Clear All")
                            }
                        }
                    }

                    if (history.isEmpty()) {
                        item {
                            Text(
                                "No browsing history yet.",
                                style = MaterialTheme.typography.bodyMedium,
                                color = MaterialTheme.colorScheme.onSurfaceVariant
                            )
                        }
                    } else {
                        items(history, key = { it.id }) { item ->
                            val ts = SimpleDateFormat("MMM d, HH:mm", Locale.US).format(Date(item.visitedAt))
                            LibraryRowCard(
                                title = item.title,
                                subtitle = "$ts • ${item.url}",
                                onClick = { onOpenUrl(item.url) },
                                onDelete = { onDeleteHistory(item.id) }
                            )
                        }
                    }
                }

                2 -> {
                    item {
                        Row(
                            modifier = Modifier.fillMaxWidth(),
                            horizontalArrangement = Arrangement.SpaceBetween,
                            verticalAlignment = Alignment.CenterVertically
                        ) {
                            Text(
                                "Managed via Android DownloadManager",
                                style = MaterialTheme.typography.bodySmall,
                                color = MaterialTheme.colorScheme.onSurfaceVariant
                            )
                            OutlinedButton(
                                onClick = onClearDownloads,
                                shape = RoundedCornerShape(10.dp),
                                modifier = Modifier.testTag("lib_clear_downloads_button")
                            ) {
                                Text("Clear List")
                            }
                        }
                    }

                    if (downloads.isEmpty()) {
                        item {
                            Text(
                                "No downloads yet. File links clicked in the browser queue automatically in Android Downloads.",
                                style = MaterialTheme.typography.bodyMedium,
                                color = MaterialTheme.colorScheme.onSurfaceVariant
                            )
                        }
                    } else {
                        items(downloads, key = { it.id }) { dl ->
                            val sizeLabel = if (dl.contentLength > 0) "${dl.contentLength / 1024} KB" else "Unknown size"
                            LibraryRowCard(
                                title = dl.fileName,
                                subtitle = "${dl.status} • $sizeLabel • ${dl.url}",
                                onClick = { onOpenUrl(dl.url) },
                                onDelete = { onDeleteDownload(dl.id) }
                            )
                        }
                    }
                }

                3 -> {
                    item {
                        Surface(
                            shape = RoundedCornerShape(16.dp),
                            color = MaterialTheme.colorScheme.surface,
                            border = BorderStroke(1.dp, semantic.border),
                            modifier = Modifier.fillMaxWidth()
                        ) {
                            Column(
                                modifier = Modifier.padding(14.dp),
                                verticalArrangement = Arrangement.spacedBy(8.dp)
                            ) {
                                Text("Configure Per-Website Gaming Profile", style = MaterialTheme.typography.titleMedium)
                                OutlinedTextField(
                                    value = profileHost,
                                    onValueChange = { profileHost = it },
                                    label = { Text("Host (e.g. alt.lasthavens.com)") },
                                    singleLine = true,
                                    modifier = Modifier
                                        .fillMaxWidth()
                                        .testTag("lib_profile_host")
                                )
                                ProfileSwitch("Auto-Enable Gaming Mode", profileAutoGaming) { profileAutoGaming = it }
                                ProfileSwitch("Lock Landscape Orientation", profileLandscape) { profileLandscape = it }
                                ProfileSwitch("Hide Browser UI in Gaming Mode", profileHideUi) { profileHideUi = it }
                                ProfileSwitch("Use Desktop User-Agent", profileDesktopUa) { profileDesktopUa = it }

                                Text("Requested Display Hz", style = MaterialTheme.typography.labelMedium)
                                Row(horizontalArrangement = Arrangement.spacedBy(6.dp)) {
                                    (listOf(0) + supportedRefreshRates).forEach { hz ->
                                        val selected = profileHz == hz
                                        Surface(
                                            shape = RoundedCornerShape(8.dp),
                                            color = if (selected) MaterialTheme.colorScheme.primary else MaterialTheme.colorScheme.surfaceVariant,
                                            modifier = Modifier
                                                .clip(RoundedCornerShape(8.dp))
                                                .clickable { profileHz = hz }
                                        ) {
                                            Text(
                                                text = if (hz == 0) "Auto Max" else "${hz}Hz",
                                                style = MaterialTheme.typography.labelMedium,
                                                color = if (selected) MaterialTheme.colorScheme.onPrimary else MaterialTheme.colorScheme.onSurface,
                                                modifier = Modifier.padding(horizontal = 10.dp, vertical = 6.dp)
                                            )
                                        }
                                    }
                                }

                                Text("Safe Viewport Scale (100% Recommended for WebGL2)", style = MaterialTheme.typography.labelMedium)
                                Row(horizontalArrangement = Arrangement.spacedBy(6.dp)) {
                                    listOf(100, 90, 80).forEach { scale ->
                                        val selected = profileScale == scale
                                        Surface(
                                            shape = RoundedCornerShape(8.dp),
                                            color = if (selected) MaterialTheme.colorScheme.primary else MaterialTheme.colorScheme.surfaceVariant,
                                            modifier = Modifier
                                                .clip(RoundedCornerShape(8.dp))
                                                .clickable { profileScale = scale }
                                        ) {
                                            Text(
                                                text = if (scale == 100) "100% Native" else "$scale%",
                                                style = MaterialTheme.typography.labelMedium,
                                                color = if (selected) MaterialTheme.colorScheme.onPrimary else MaterialTheme.colorScheme.onSurface,
                                                modifier = Modifier.padding(horizontal = 10.dp, vertical = 6.dp)
                                            )
                                        }
                                    }
                                }

                                Button(
                                    onClick = {
                                        if (profileHost.isNotBlank()) {
                                            onSaveProfile(
                                                SiteGamingProfileEntity(
                                                    host = profileHost,
                                                    autoGamingMode = profileAutoGaming,
                                                    lockLandscape = profileLandscape,
                                                    hideBrowserUi = profileHideUi,
                                                    keepScreenOn = true,
                                                    preferredRefreshHz = profileHz,
                                                    safeRenderScalePercent = profileScale,
                                                    useDesktopUserAgent = profileDesktopUa
                                                )
                                            )
                                            profileHost = ""
                                        }
                                    },
                                    shape = RoundedCornerShape(12.dp),
                                    modifier = Modifier
                                        .fillMaxWidth()
                                        .testTag("lib_save_profile_button")
                                ) {
                                    Text("Save Website Gaming Profile")
                                }
                            }
                        }
                    }

                    items(siteProfiles, key = { it.host }) { prof ->
                        val hzStr = if (prof.preferredRefreshHz == 0) "Auto Hz" else "${prof.preferredRefreshHz}Hz"
                        LibraryRowCard(
                            title = prof.host,
                            subtitle = "Gaming=${if (prof.autoGamingMode) "Auto" else "Manual"} • $hzStr • Scale=${prof.safeRenderScalePercent}% • Landscape=${prof.lockLandscape} • DesktopUA=${prof.useDesktopUserAgent}",
                            onClick = { onOpenUrl("https://${prof.host}") },
                            onDelete = { onDeleteProfile(prof.host) }
                        )
                    }
                }
            }
        }
    }
}

@Composable
private fun ProfileSwitch(label: String, checked: Boolean, onChange: (Boolean) -> Unit) {
    Row(
        modifier = Modifier.fillMaxWidth(),
        horizontalArrangement = Arrangement.SpaceBetween,
        verticalAlignment = Alignment.CenterVertically
    ) {
        Text(label, style = MaterialTheme.typography.bodyMedium)
        Switch(checked = checked, onCheckedChange = onChange)
    }
}

@Composable
private fun LibraryRowCard(
    title: String,
    subtitle: String,
    onClick: () -> Unit,
    onDelete: () -> Unit
) {
    val semantic = LocalPulseSemanticColors.current
    Surface(
        shape = RoundedCornerShape(14.dp),
        color = MaterialTheme.colorScheme.surfaceVariant,
        border = BorderStroke(1.dp, semantic.border),
        modifier = Modifier.fillMaxWidth()
    ) {
        Row(
            modifier = Modifier
                .fillMaxWidth()
                .clickable(onClick = onClick)
                .padding(horizontal = 14.dp, vertical = 10.dp),
            verticalAlignment = Alignment.CenterVertically,
            horizontalArrangement = Arrangement.SpaceBetween
        ) {
            Column(modifier = Modifier.weight(1f)) {
                Text(title, style = MaterialTheme.typography.titleSmall, maxLines = 1, overflow = TextOverflow.Ellipsis)
                Text(
                    subtitle,
                    style = MonoLogStyle,
                    color = MaterialTheme.colorScheme.onSurfaceVariant,
                    maxLines = 2,
                    overflow = TextOverflow.Ellipsis
                )
            }
            IconButton(onClick = onDelete) {
                Icon(Icons.Filled.Delete, contentDescription = "Delete $title", tint = MaterialTheme.colorScheme.error)
            }
        }
    }
}
