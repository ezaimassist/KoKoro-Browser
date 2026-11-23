package com.example.ui

import androidx.compose.foundation.BorderStroke
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
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.foundation.text.BasicTextField
import androidx.compose.foundation.text.KeyboardActions
import androidx.compose.foundation.text.KeyboardOptions
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.automirrored.filled.ArrowBack
import androidx.compose.material.icons.automirrored.filled.ArrowForward
import androidx.compose.material.icons.filled.Bookmark
import androidx.compose.material.icons.filled.BugReport
import androidx.compose.material.icons.filled.Close
import androidx.compose.material.icons.filled.CollectionsBookmark
import androidx.compose.material.icons.filled.Home
import androidx.compose.material.icons.filled.Lock
import androidx.compose.material.icons.filled.Refresh
import androidx.compose.material.icons.filled.Settings
import androidx.compose.material.icons.filled.SportsEsports
import androidx.compose.material.icons.filled.VisibilityOff
import androidx.compose.material.icons.outlined.BookmarkBorder
import androidx.compose.material3.Icon
import androidx.compose.material3.IconButton
import androidx.compose.material3.LinearProgressIndicator
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.Surface
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.runtime.LaunchedEffect
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.setValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clip
import androidx.compose.ui.focus.onFocusChanged
import androidx.compose.ui.graphics.SolidColor
import androidx.compose.ui.platform.LocalFocusManager
import androidx.compose.ui.platform.testTag
import androidx.compose.ui.res.stringResource
import androidx.compose.ui.text.input.ImeAction
import androidx.compose.ui.text.input.KeyboardType
import androidx.compose.ui.text.style.TextOverflow
import androidx.compose.ui.unit.dp
import com.example.R
import com.example.engine.UrlUtils
import com.example.ui.theme.JetBrainsMonoFontFamily
import com.example.ui.theme.LocalPulseSemanticColors
import com.example.viewmodel.PulseTab

@Composable
fun PulseBrowserChrome(
    activeTab: PulseTab,
    tabCount: Int,
    gamingModeActive: Boolean,
    isBookmarked: Boolean,
    diagnosticErrorsCount: Int,
    onSubmitAddress: (String) -> Unit,
    onBack: () -> Unit,
    onForward: () -> Unit,
    onReloadOrStop: () -> Unit,
    onHome: () -> Unit,
    onToggleGamingMode: () -> Unit,
    onToggleBookmark: () -> Unit,
    onOpenDiagnostics: () -> Unit,
    onOpenLibrary: () -> Unit,
    onOpenSettings: () -> Unit,
    onOpenTabs: () -> Unit,
    modifier: Modifier = Modifier
) {
    val semantic = LocalPulseSemanticColors.current
    val focusManager = LocalFocusManager.current
    var addressInput by remember { mutableStateOf("") }
    var focused by remember { mutableStateOf(false) }

    LaunchedEffect(activeTab.url, focused) {
        if (!focused) {
            addressInput = if (UrlUtils.isInternal(activeTab.url)) "" else activeTab.url
        }
    }

    Surface(
        color = if (activeTab.isPrivate) semantic.privateContainer else MaterialTheme.colorScheme.surface,
        tonalElevation = 4.dp,
        border = BorderStroke(
            1.dp,
            if (activeTab.isPrivate) semantic.privateViolet.copy(alpha = 0.5f) else semantic.border
        ),
        modifier = modifier.fillMaxWidth()
    ) {
        Column {
            if (activeTab.isLoading && activeTab.progress in 1..99) {
                LinearProgressIndicator(
                    progress = { activeTab.progress / 100f },
                    modifier = Modifier
                        .fillMaxWidth()
                        .height(2.5.dp),
                    color = if (activeTab.isPrivate) semantic.privateViolet else MaterialTheme.colorScheme.primary,
                    trackColor = MaterialTheme.colorScheme.surfaceVariant
                )
            }

            // Address Bar Row
            Row(
                modifier = Modifier
                    .fillMaxWidth()
                    .padding(horizontal = 10.dp, vertical = 8.dp),
                verticalAlignment = Alignment.CenterVertically,
                horizontalArrangement = Arrangement.spacedBy(8.dp)
            ) {
                // Diagnostics Quick Button with Live Error Badge
                Surface(
                    shape = RoundedCornerShape(12.dp),
                    color = if (diagnosticErrorsCount > 0) {
                        semantic.errorRose.copy(alpha = 0.2f)
                    } else {
                        MaterialTheme.colorScheme.surfaceVariant
                    },
                    border = BorderStroke(
                        1.dp,
                        if (diagnosticErrorsCount > 0) semantic.errorRose else semantic.border
                    ),
                    modifier = Modifier
                        .testTag("chrome_diagnostics_button")
                        .sizeIn(minWidth = 48.dp, minHeight = 44.dp)
                        .clip(RoundedCornerShape(12.dp))
                        .clickable(onClick = onOpenDiagnostics)
                ) {
                    Row(
                        modifier = Modifier.padding(horizontal = 8.dp, vertical = 8.dp),
                        verticalAlignment = Alignment.CenterVertically,
                        horizontalArrangement = Arrangement.spacedBy(4.dp)
                    ) {
                        Icon(
                            imageVector = Icons.Filled.BugReport,
                            contentDescription = stringResource(R.string.cd_diagnostics),
                            tint = if (diagnosticErrorsCount > 0) semantic.errorRose else MaterialTheme.colorScheme.primary,
                            modifier = Modifier.size(18.dp)
                        )
                        if (diagnosticErrorsCount > 0) {
                            Text(
                                text = "$diagnosticErrorsCount",
                                style = MaterialTheme.typography.labelSmall,
                                color = semantic.errorRose
                            )
                        }
                    }
                }

                // URL Omnibox
                Surface(
                    shape = RoundedCornerShape(14.dp),
                    color = MaterialTheme.colorScheme.surfaceVariant,
                    border = BorderStroke(
                        1.dp,
                        when {
                            focused -> MaterialTheme.colorScheme.primary
                            activeTab.isPrivate -> semantic.privateViolet
                            else -> semantic.border
                        }
                    ),
                    modifier = Modifier
                        .weight(1f)
                        .height(44.dp)
                ) {
                    Row(
                        modifier = Modifier
                            .fillMaxWidth()
                            .padding(horizontal = 10.dp),
                        verticalAlignment = Alignment.CenterVertically
                    ) {
                        if (activeTab.isPrivate) {
                            Icon(
                                imageVector = Icons.Filled.VisibilityOff,
                                contentDescription = "Private tab",
                                tint = semantic.privateViolet,
                                modifier = Modifier.size(15.dp)
                            )
                            Spacer(Modifier.width(6.dp))
                        } else if (UrlUtils.isSecureUrl(activeTab.url) && !focused) {
                            Icon(
                                imageVector = Icons.Filled.Lock,
                                contentDescription = null,
                                tint = semantic.okGreen,
                                modifier = Modifier.size(14.dp)
                            )
                            Spacer(Modifier.width(6.dp))
                        }

                        Box(modifier = Modifier.weight(1f)) {
                            if (addressInput.isEmpty()) {
                                Text(
                                    text = stringResource(R.string.address_placeholder),
                                    style = MaterialTheme.typography.bodyMedium,
                                    color = MaterialTheme.colorScheme.onSurfaceVariant,
                                    maxLines = 1,
                                    overflow = TextOverflow.Ellipsis
                                )
                            }
                            BasicTextField(
                                value = addressInput,
                                onValueChange = { addressInput = it },
                                singleLine = true,
                                textStyle = MaterialTheme.typography.bodyMedium.copy(
                                    color = MaterialTheme.colorScheme.onSurface,
                                    fontFamily = JetBrainsMonoFontFamily
                                ),
                                cursorBrush = SolidColor(MaterialTheme.colorScheme.primary),
                                keyboardOptions = KeyboardOptions(
                                    keyboardType = KeyboardType.Uri,
                                    imeAction = ImeAction.Go
                                ),
                                keyboardActions = KeyboardActions(
                                    onGo = {
                                        focusManager.clearFocus()
                                        onSubmitAddress(addressInput)
                                    }
                                ),
                                modifier = Modifier
                                    .fillMaxWidth()
                                    .testTag("address_bar_input")
                                    .onFocusChanged { focused = it.isFocused }
                            )
                        }

                        if (!UrlUtils.isInternal(activeTab.url)) {
                            IconButton(
                                onClick = onReloadOrStop,
                                modifier = Modifier
                                    .size(36.dp)
                                    .testTag("chrome_reload_button")
                            ) {
                                Icon(
                                    imageVector = if (activeTab.isLoading) Icons.Filled.Close else Icons.Filled.Refresh,
                                    contentDescription = stringResource(
                                        if (activeTab.isLoading) R.string.cd_stop else R.string.cd_reload
                                    ),
                                    modifier = Modifier.size(18.dp)
                                )
                            }
                        }
                    }
                }

                // Gaming Mode Trigger
                Surface(
                    shape = RoundedCornerShape(12.dp),
                    color = if (gamingModeActive) {
                        MaterialTheme.colorScheme.primary
                    } else {
                        MaterialTheme.colorScheme.primaryContainer
                    },
                    modifier = Modifier
                        .testTag("chrome_gaming_mode_button")
                        .sizeIn(minWidth = 48.dp, minHeight = 44.dp)
                        .clip(RoundedCornerShape(12.dp))
                        .clickable(onClick = onToggleGamingMode)
                ) {
                    Box(
                        modifier = Modifier.padding(horizontal = 10.dp),
                        contentAlignment = Alignment.Center
                    ) {
                        Icon(
                            imageVector = Icons.Filled.SportsEsports,
                            contentDescription = stringResource(R.string.cd_gaming_mode),
                            tint = if (gamingModeActive) {
                                MaterialTheme.colorScheme.onPrimary
                            } else {
                                MaterialTheme.colorScheme.onPrimaryContainer
                            },
                            modifier = Modifier.size(20.dp)
                        )
                    }
                }
            }

            // Navigation & Browser Tools Row
            Row(
                modifier = Modifier
                    .fillMaxWidth()
                    .padding(horizontal = 4.dp, vertical = 2.dp),
                horizontalArrangement = Arrangement.SpaceBetween,
                verticalAlignment = Alignment.CenterVertically
            ) {
                IconButton(
                    onClick = onBack,
                    enabled = activeTab.canGoBack || !UrlUtils.isInternal(activeTab.url),
                    modifier = Modifier.testTag("nav_back_button")
                ) {
                    Icon(Icons.AutoMirrored.Filled.ArrowBack, contentDescription = stringResource(R.string.cd_back))
                }
                IconButton(
                    onClick = onForward,
                    enabled = activeTab.canGoForward,
                    modifier = Modifier.testTag("nav_forward_button")
                ) {
                    Icon(Icons.AutoMirrored.Filled.ArrowForward, contentDescription = stringResource(R.string.cd_forward))
                }
                IconButton(
                    onClick = onHome,
                    modifier = Modifier.testTag("nav_home_button")
                ) {
                    Icon(Icons.Filled.Home, contentDescription = stringResource(R.string.cd_home))
                }
                IconButton(
                    onClick = onToggleBookmark,
                    enabled = !UrlUtils.isInternal(activeTab.url),
                    modifier = Modifier.testTag("nav_bookmark_button")
                ) {
                    Icon(
                        imageVector = if (isBookmarked) Icons.Filled.Bookmark else Icons.Outlined.BookmarkBorder,
                        contentDescription = stringResource(R.string.cd_bookmark),
                        tint = if (isBookmarked) MaterialTheme.colorScheme.primary else MaterialTheme.colorScheme.onSurface
                    )
                }
                IconButton(
                    onClick = onOpenLibrary,
                    modifier = Modifier.testTag("nav_library_button")
                ) {
                    Icon(Icons.Filled.CollectionsBookmark, contentDescription = stringResource(R.string.cd_library))
                }
                IconButton(
                    onClick = onOpenSettings,
                    modifier = Modifier.testTag("nav_settings_button")
                ) {
                    Icon(Icons.Filled.Settings, contentDescription = stringResource(R.string.cd_settings))
                }

                // Tab Counter Badge
                Box(
                    modifier = Modifier
                        .testTag("nav_tabs_button")
                        .size(48.dp)
                        .clip(RoundedCornerShape(12.dp))
                        .clickable(onClick = onOpenTabs),
                    contentAlignment = Alignment.Center
                ) {
                    Box(
                        modifier = Modifier
                            .size(26.dp)
                            .clip(RoundedCornerShape(7.dp))
                            .background(MaterialTheme.colorScheme.surfaceVariant),
                        contentAlignment = Alignment.Center
                    ) {
                        Text(
                            text = if (tabCount > 99) "99+" else "$tabCount",
                            style = MaterialTheme.typography.labelMedium,
                            color = MaterialTheme.colorScheme.onSurface
                        )
                    }
                }
            }
        }
    }
}
