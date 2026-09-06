package com.example.ui.screens

import androidx.compose.foundation.background
import androidx.compose.foundation.clickable
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.layout.width
import androidx.compose.foundation.lazy.LazyColumn
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.filled.ArrowBack
import androidx.compose.material.icons.filled.DeleteSweep
import androidx.compose.material.icons.filled.Dns
import androidx.compose.material.icons.filled.Download
import androidx.compose.material.icons.filled.History
import androidx.compose.material.icons.filled.Info
import androidx.compose.material.icons.filled.NotificationsActive
import androidx.compose.material.icons.filled.PlayArrow
import androidx.compose.material.icons.filled.Settings
import androidx.compose.material.icons.filled.VisibilityOff
import androidx.compose.material3.AlertDialog
import androidx.compose.material3.Icon
import androidx.compose.material3.IconButton
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.RadioButton
import androidx.compose.material3.Surface
import androidx.compose.material3.Switch
import androidx.compose.material3.Text
import androidx.compose.material3.TextButton
import androidx.compose.runtime.Composable
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.setValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clip
import androidx.compose.ui.graphics.vector.ImageVector
import androidx.compose.ui.platform.testTag
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import com.example.BuildConfig
import com.example.model.DeviceLayoutMode
import com.example.model.PlaybackPreferences
import com.example.model.PlaybackQuality
import com.example.model.SubtitlePreference
import com.example.ui.components.PlaybackPreferencesDialog
import com.example.ui.components.ClearLocalDataDialog
import com.example.ui.components.RecommendationControlsDialog
import com.example.ui.components.playbackQualityLabel
import com.example.ui.components.subtitlePreferenceLabel

/**
 * Dedicated settings destination, opened from the You page gear button.
 * Library content (Watch Later, History, Downloads) stays on You; everything
 * configurable lives here, grouped into sections.
 */
@Composable
fun SettingsScreen(
    // Playback
    playbackPreferences: PlaybackPreferences = PlaybackPreferences(),
    onQualitySelected: (PlaybackQuality) -> Unit = {},
    onSubtitleSelected: (SubtitlePreference) -> Unit = {},
    isAutoNextEpisodeEnabled: Boolean = true,
    onToggleAutoNextEpisode: () -> Unit = {},
    showContinueWatchingOnHome: Boolean = true,
    onSetContinueWatchingOnHome: (Boolean) -> Unit = {},
    continueWatchFullscreen: Boolean = true,
    onSetContinueWatchFullscreen: (Boolean) -> Unit = {},
    // Appearance
    deviceLayoutMode: DeviceLayoutMode = DeviceLayoutMode.AUTO,
    onSelectDeviceLayoutMode: (DeviceLayoutMode) -> Unit = {},
    // Notifications
    releaseNotificationsEnabled: Boolean = true,
    onSetReleaseNotificationsEnabled: (Boolean) -> Unit = {},
    // Downloads & subtitles
    offlineSubtitleLanguage: String = "en",
    isSubtitleAutoDownload: Boolean = true,
    wyzieApiKey: String = "",
    subdlApiKey: String = "",
    onOfflineSubtitleLanguageSelected: (String) -> Unit = {},
    onSubtitleAutoDownloadChanged: (Boolean) -> Unit = {},
    onWyzieApiKeyChanged: (String) -> Unit = {},
    onSubdlApiKeyChanged: (String) -> Unit = {},
    onOpenDownloads: () -> Unit = {},
    onOpenServerDialog: () -> Unit = {},
    // Recommendations & privacy
    notInterestedCount: Int = 0,
    notRecommendedChannelCount: Int = 0,
    onClearRecommendationPreferences: () -> Unit = {},
    // Danger zone
    onClearLocalData: () -> Unit = {},
    onBack: () -> Unit = {},
    modifier: Modifier = Modifier
) {
    var isPlaybackPrefsOpen by remember { mutableStateOf(false) }
    var isRecommendationOpen by remember { mutableStateOf(false) }
    var isClearDataOpen by remember { mutableStateOf(false) }
    var isLayoutPickerOpen by remember { mutableStateOf(false) }

    LazyColumn(
        modifier = modifier
            .fillMaxSize()
            .background(MaterialTheme.colorScheme.background)
            .testTag("settings_screen"),
    ) {
        item(key = "settings_header", contentType = "header") {
            Row(
                modifier = Modifier
                    .fillMaxWidth()
                    .padding(start = 4.dp, end = 16.dp, top = 8.dp, bottom = 4.dp),
                verticalAlignment = Alignment.CenterVertically
            ) {
                IconButton(
                    onClick = onBack,
                    modifier = Modifier.testTag("settings_back_button")
                ) {
                    Icon(
                        imageVector = Icons.Default.ArrowBack,
                        contentDescription = "Back",
                        tint = MaterialTheme.colorScheme.onBackground
                    )
                }
                Text(
                    text = "Settings",
                    fontSize = 20.sp,
                    fontWeight = FontWeight.Bold,
                    color = MaterialTheme.colorScheme.onBackground
                )
            }
        }

        item(key = "section_playback", contentType = "section") {
            SettingsSectionHeader(title = "Playback")
        }
        item(key = "row_servers", contentType = "row") {
            SettingsRow(
                icon = Icons.Default.Dns,
                title = "Stream servers",
                subtitle = "Choose a playback provider",
                onClick = onOpenServerDialog
            )
        }
        item(key = "row_playback_prefs", contentType = "row") {
            SettingsRow(
                icon = Icons.Default.Settings,
                title = "Playback & subtitles",
                subtitle = "${playbackQualityLabel(playbackPreferences.quality)} quality · " +
                    "${subtitlePreferenceLabel(playbackPreferences.subtitles)} captions",
                onClick = { isPlaybackPrefsOpen = true },
                testTag = "playback_preferences_settings"
            )
        }
        item(key = "row_autonext", contentType = "row") {
            SettingsRow(
                icon = Icons.Default.PlayArrow,
                title = "Auto-play next episode",
                subtitle = if (isAutoNextEpisodeEnabled) "On · series continue automatically"
                else "Off · you choose what plays next",
                onClick = onToggleAutoNextEpisode,
                trailingContent = {
                    Switch(
                        checked = isAutoNextEpisodeEnabled,
                        onCheckedChange = { onToggleAutoNextEpisode() }
                    )
                }
            )
        }
        item(key = "row_continue_home", contentType = "row") {
            SettingsRow(
                icon = Icons.Default.History,
                title = "Continue watching on Home",
                subtitle = if (showContinueWatchingOnHome) "Resume shelf is shown on the Home feed"
                else "Off · recent titles appear as normal cards",
                onClick = { onSetContinueWatchingOnHome(!showContinueWatchingOnHome) },
                trailingContent = {
                    Switch(
                        checked = showContinueWatchingOnHome,
                        onCheckedChange = onSetContinueWatchingOnHome,
                        modifier = Modifier.testTag("continue_watching_home_toggle")
                    )
                }
            )
        }
        item(key = "row_continue_fs", contentType = "row") {
            SettingsRow(
                icon = Icons.Default.PlayArrow,
                title = "Start Continue Watching in fullscreen",
                subtitle = if (continueWatchFullscreen) "Movies and series resume straight in fullscreen"
                else "Off · resumes open the watch page first",
                onClick = { onSetContinueWatchFullscreen(!continueWatchFullscreen) },
                trailingContent = {
                    Switch(
                        checked = continueWatchFullscreen,
                        onCheckedChange = onSetContinueWatchFullscreen,
                        modifier = Modifier.testTag("continue_watch_fullscreen_toggle")
                    )
                }
            )
        }

        item(key = "section_appearance", contentType = "section") {
            SettingsSectionHeader(title = "Appearance")
        }
        item(key = "row_layout", contentType = "row") {
            SettingsRow(
                icon = Icons.Default.Settings,
                title = "Layout",
                subtitle = deviceLayoutMode.displayName,
                onClick = { isLayoutPickerOpen = true }
            )
        }

        item(key = "section_notifications", contentType = "section") {
            SettingsSectionHeader(title = "Notifications")
        }
        item(key = "row_release_notif", contentType = "row") {
            SettingsRow(
                icon = Icons.Default.NotificationsActive,
                title = "Release notifications",
                subtitle = if (releaseNotificationsEnabled) "Alerts are scheduled on this device"
                else "Off · alerts stay in the app inbox",
                onClick = { onSetReleaseNotificationsEnabled(!releaseNotificationsEnabled) },
                trailingContent = {
                    Switch(
                        checked = releaseNotificationsEnabled,
                        onCheckedChange = onSetReleaseNotificationsEnabled,
                        modifier = Modifier.testTag("release_notifications_toggle")
                    )
                }
            )
        }

        item(key = "section_downloads", contentType = "section") {
            SettingsSectionHeader(title = "Downloads")
        }
        item(key = "row_downloads", contentType = "row") {
            SettingsRow(
                icon = Icons.Default.Download,
                title = "Manage downloads",
                subtitle = "Offline movies, shows, and subtitles",
                onClick = onOpenDownloads
            )
        }

        item(key = "section_privacy", contentType = "section") {
            SettingsSectionHeader(title = "Recommendations & privacy")
        }
        item(key = "row_recs", contentType = "row") {
            SettingsRow(
                icon = Icons.Default.VisibilityOff,
                title = "Recommendation controls",
                subtitle = if (notInterestedCount == 0 && notRecommendedChannelCount == 0) {
                    "Personalize the Home feed"
                } else {
                    "$notInterestedCount hidden videos, $notRecommendedChannelCount blocked channels"
                },
                onClick = { isRecommendationOpen = true }
            )
        }
        item(key = "row_clear_data", contentType = "row") {
            SettingsRow(
                icon = Icons.Default.DeleteSweep,
                title = "Clear local data",
                subtitle = "Remove profile, history, saved videos, and queue",
                onClick = { isClearDataOpen = true }
            )
        }

        item(key = "section_about", contentType = "section") {
            SettingsSectionHeader(title = "About")
        }
        item(key = "row_about", contentType = "row") {
            SettingsRow(
                icon = Icons.Default.Info,
                title = "CluTube",
                subtitle = "Version ${BuildConfig.VERSION_NAME} · local-first, no account",
                onClick = null
            )
        }

        item(key = "settings_bottom_spacer", contentType = "spacer") {
            Spacer(modifier = Modifier.height(80.dp))
        }
    }

    if (isPlaybackPrefsOpen) {
        PlaybackPreferencesDialog(
            playbackPreferences = playbackPreferences,
            onQualitySelected = onQualitySelected,
            onSubtitleSelected = onSubtitleSelected,
            offlineSubtitleLanguage = offlineSubtitleLanguage,
            isSubtitleAutoDownload = isSubtitleAutoDownload,
            wyzieApiKey = wyzieApiKey,
            subdlApiKey = subdlApiKey,
            onOfflineSubtitleLanguageSelected = onOfflineSubtitleLanguageSelected,
            onSubtitleAutoDownloadChanged = onSubtitleAutoDownloadChanged,
            onWyzieApiKeyChanged = onWyzieApiKeyChanged,
            onSubdlApiKeyChanged = onSubdlApiKeyChanged,
            onDismiss = { isPlaybackPrefsOpen = false }
        )
    }
    if (isRecommendationOpen) {
        RecommendationControlsDialog(
            notInterestedCount = notInterestedCount,
            notRecommendedChannelCount = notRecommendedChannelCount,
            onClearRecommendationPreferences = onClearRecommendationPreferences,
            onDismiss = { isRecommendationOpen = false }
        )
    }
    if (isClearDataOpen) {
        ClearLocalDataDialog(
            onClearLocalData = onClearLocalData,
            onDismiss = { isClearDataOpen = false }
        )
    }
    if (isLayoutPickerOpen) {
        AlertDialog(
            onDismissRequest = { isLayoutPickerOpen = false },
            title = { Text("Layout") },
            text = {
                Column(verticalArrangement = Arrangement.spacedBy(4.dp)) {
                    DeviceLayoutMode.values().forEach { mode ->
                        Row(
                            modifier = Modifier
                                .fillMaxWidth()
                                .clip(RoundedCornerShape(10.dp))
                                .clickable {
                                    onSelectDeviceLayoutMode(mode)
                                    isLayoutPickerOpen = false
                                }
                                .padding(horizontal = 8.dp, vertical = 10.dp),
                            verticalAlignment = Alignment.CenterVertically
                        ) {
                            RadioButton(
                                selected = deviceLayoutMode == mode,
                                onClick = {
                                    onSelectDeviceLayoutMode(mode)
                                    isLayoutPickerOpen = false
                                }
                            )
                            Spacer(modifier = Modifier.width(8.dp))
                            Column(modifier = Modifier.weight(1f)) {
                                Text(
                                    text = mode.displayName,
                                    fontSize = 14.sp,
                                    fontWeight = FontWeight.Medium,
                                    color = MaterialTheme.colorScheme.onSurface
                                )
                                Text(
                                    text = mode.description,
                                    fontSize = 12.sp,
                                    color = MaterialTheme.colorScheme.onSurfaceVariant
                                )
                            }
                        }
                    }
                }
            },
            confirmButton = {
                TextButton(onClick = { isLayoutPickerOpen = false }) { Text("Done") }
            }
        )
    }
}

@Composable
private fun SettingsSectionHeader(title: String) {
    Text(
        text = title,
        fontSize = 13.sp,
        fontWeight = FontWeight.Bold,
        color = MaterialTheme.colorScheme.primary,
        modifier = Modifier.padding(start = 16.dp, end = 16.dp, top = 18.dp, bottom = 2.dp)
    )
}

@Composable
private fun SettingsRow(
    icon: ImageVector,
    title: String,
    subtitle: String? = null,
    onClick: (() -> Unit)? = null,
    testTag: String? = null,
    trailingContent: (@Composable () -> Unit)? = null
) {
    Surface(
        tonalElevation = 1.dp,
        shape = RoundedCornerShape(14.dp),
        modifier = Modifier
            .fillMaxWidth()
            .padding(horizontal = 16.dp, vertical = 4.dp)
            .then(
                if (onClick != null) {
                    Modifier
                        .clip(RoundedCornerShape(14.dp))
                        .clickable(onClick = onClick)
                } else Modifier
            )
            .then(testTag?.let { Modifier.testTag(it) } ?: Modifier)
    ) {
        Row(
            modifier = Modifier.padding(horizontal = 14.dp, vertical = 13.dp),
            verticalAlignment = Alignment.CenterVertically
        ) {
            Icon(
                imageVector = icon,
                contentDescription = title,
                tint = MaterialTheme.colorScheme.onBackground,
                modifier = Modifier.size(22.dp)
            )
            Spacer(modifier = Modifier.width(16.dp))
            Column(modifier = Modifier.weight(1f)) {
                Text(
                    text = title,
                    fontSize = 14.sp,
                    fontWeight = FontWeight.Medium,
                    color = MaterialTheme.colorScheme.onBackground
                )
                if (subtitle != null) {
                    Text(
                        text = subtitle,
                        fontSize = 11.sp,
                        color = MaterialTheme.colorScheme.onSurfaceVariant
                    )
                }
            }
            trailingContent?.invoke()
        }
    }
}
