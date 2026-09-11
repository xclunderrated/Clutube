package com.example.ui.components

import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.height
import androidx.compose.material3.AlertDialog
import androidx.compose.material3.DropdownMenu
import androidx.compose.material3.DropdownMenuItem
import androidx.compose.material3.HorizontalDivider
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.OutlinedTextField
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
import androidx.compose.ui.platform.testTag
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import com.example.model.PlaybackPreferences
import com.example.model.PlaybackQuality
import com.example.model.SubtitlePreference
import com.example.ui.theme.YouTubeRed

fun playbackQualityLabel(quality: PlaybackQuality): String = when (quality) {
    PlaybackQuality.AUTO -> "Auto"
    PlaybackQuality.P1080 -> "1080p"
    PlaybackQuality.P720 -> "720p"
    PlaybackQuality.P480 -> "480p"
    PlaybackQuality.P360 -> "360p"
}

fun subtitlePreferenceLabel(preference: SubtitlePreference): String = when (preference) {
    SubtitlePreference.OFF -> "Off"
    SubtitlePreference.AUTO -> "Auto"
    SubtitlePreference.ENGLISH -> "English"
    SubtitlePreference.SPANISH -> "Spanish"
}

@Composable
fun PlaybackPreferencesDialog(
    playbackPreferences: PlaybackPreferences,
    onQualitySelected: (PlaybackQuality) -> Unit,
    onSubtitleSelected: (SubtitlePreference) -> Unit,
    offlineSubtitleLanguage: String,
    isSubtitleAutoDownload: Boolean,
    wyzieApiKey: String,
    subdlApiKey: String,
    onOfflineSubtitleLanguageSelected: (String) -> Unit,
    onSubtitleAutoDownloadChanged: (Boolean) -> Unit,
    onWyzieApiKeyChanged: (String) -> Unit,
    onSubdlApiKeyChanged: (String) -> Unit,
    onDismiss: () -> Unit
) {
    var qualityExpanded by remember { mutableStateOf(false) }
    var subtitlesExpanded by remember { mutableStateOf(false) }
    var offlineLangExpanded by remember { mutableStateOf(false) }
    var wyzieDraft by remember(wyzieApiKey) { mutableStateOf(wyzieApiKey) }
    var subdlDraft by remember(subdlApiKey) { mutableStateOf(subdlApiKey) }
    val saveKeysAndDismiss = {
        if (wyzieDraft.trim() != wyzieApiKey) onWyzieApiKeyChanged(wyzieDraft)
        if (subdlDraft.trim() != subdlApiKey) onSubdlApiKeyChanged(subdlDraft)
        onDismiss()
    }

    AlertDialog(
        onDismissRequest = saveKeysAndDismiss,
        title = { Text("Playback preferences") },
        text = {
            Column(
                modifier = Modifier.fillMaxWidth(),
                verticalArrangement = Arrangement.spacedBy(8.dp)
            ) {
                Text(
                    text = "Saved to your account and used by every stream server.",
                    fontSize = 13.sp,
                    color = MaterialTheme.colorScheme.onSurfaceVariant
                )

                Box(modifier = Modifier.fillMaxWidth()) {
                    TextButton(
                        onClick = { qualityExpanded = true },
                        modifier = Modifier
                            .fillMaxWidth()
                            .testTag("quality_preference_picker")
                    ) {
                        Row(
                            modifier = Modifier.fillMaxWidth(),
                            horizontalArrangement = Arrangement.SpaceBetween,
                            verticalAlignment = Alignment.CenterVertically
                        ) {
                            Text("Playback quality")
                            Text(
                                playbackQualityLabel(playbackPreferences.quality),
                                color = YouTubeRed,
                                fontWeight = FontWeight.SemiBold
                            )
                        }
                    }
                    DropdownMenu(
                        expanded = qualityExpanded,
                        onDismissRequest = { qualityExpanded = false }
                    ) {
                        PlaybackQuality.values().forEach { quality ->
                            DropdownMenuItem(
                                text = { Text(playbackQualityLabel(quality)) },
                                onClick = {
                                    qualityExpanded = false
                                    onQualitySelected(quality)
                                }
                            )
                        }
                    }
                }

                Box(modifier = Modifier.fillMaxWidth()) {
                    TextButton(
                        onClick = { subtitlesExpanded = true },
                        modifier = Modifier
                            .fillMaxWidth()
                            .testTag("subtitle_preference_picker")
                    ) {
                        Row(
                            modifier = Modifier.fillMaxWidth(),
                            horizontalArrangement = Arrangement.SpaceBetween,
                            verticalAlignment = Alignment.CenterVertically
                        ) {
                            Text("Closed captions")
                            Text(
                                subtitlePreferenceLabel(playbackPreferences.subtitles),
                                color = YouTubeRed,
                                fontWeight = FontWeight.SemiBold
                            )
                        }
                    }
                    DropdownMenu(
                        expanded = subtitlesExpanded,
                        onDismissRequest = { subtitlesExpanded = false }
                    ) {
                        SubtitlePreference.values().forEach { subtitles ->
                            DropdownMenuItem(
                                text = { Text(subtitlePreferenceLabel(subtitles)) },
                                onClick = {
                                    subtitlesExpanded = false
                                    onSubtitleSelected(subtitles)
                                }
                            )
                        }
                    }
                }

                HorizontalDivider(color = MaterialTheme.colorScheme.outline.copy(alpha = 0.2f))

                Text(
                    text = "Offline subtitles",
                    fontSize = 13.sp,
                    fontWeight = FontWeight.SemiBold,
                    color = MaterialTheme.colorScheme.onSurface
                )

                // Default language for downloaded sidecars.
                Box(modifier = Modifier.fillMaxWidth()) {
                    TextButton(
                        onClick = { offlineLangExpanded = true },
                        modifier = Modifier
                            .fillMaxWidth()
                            .testTag("offline_subtitle_lang_picker")
                    ) {
                        Row(
                            modifier = Modifier.fillMaxWidth(),
                            horizontalArrangement = Arrangement.SpaceBetween,
                            verticalAlignment = Alignment.CenterVertically
                        ) {
                            Text("Download language")
                            Text(
                                text = when (offlineSubtitleLanguage) {
                                    "es" -> "Spanish"
                                    "off" -> "Off"
                                    else -> "English"
                                },
                                color = YouTubeRed,
                                fontWeight = FontWeight.SemiBold
                            )
                        }
                    }
                    DropdownMenu(
                        expanded = offlineLangExpanded,
                        onDismissRequest = { offlineLangExpanded = false }
                    ) {
                        listOf("en" to "English", "es" to "Spanish", "off" to "Off").forEach { (code, label) ->
                            DropdownMenuItem(
                                text = { Text(label) },
                                onClick = {
                                    offlineLangExpanded = false
                                    onOfflineSubtitleLanguageSelected(code)
                                }
                            )
                        }
                    }
                }

                // Auto-fetch sidecars when downloads finish.
                Row(
                    modifier = Modifier.fillMaxWidth(),
                    horizontalArrangement = Arrangement.SpaceBetween,
                    verticalAlignment = Alignment.CenterVertically
                ) {
                    Text(
                        text = "Auto-download subtitles",
                        fontSize = 14.sp,
                        modifier = Modifier.weight(1f)
                    )
                    Switch(
                        checked = isSubtitleAutoDownload,
                        onCheckedChange = onSubtitleAutoDownloadChanged,
                        modifier = Modifier.testTag("offline_subtitle_auto_switch")
                    )
                }

                // Optional BYOK keys. Blank = embedded key, then keyless
                // sources. Saved on dismiss.
                OutlinedTextField(
                    value = wyzieDraft,
                    onValueChange = { wyzieDraft = it },
                    label = { Text("Wyzie API key (optional)", fontSize = 12.sp) },
                    placeholder = { Text("store.wyzie.io/redeem", fontSize = 12.sp) },
                    singleLine = true,
                    modifier = Modifier
                        .fillMaxWidth()
                        .testTag("wyzie_api_key_field"),
                    textStyle = androidx.compose.ui.text.TextStyle(fontSize = 13.sp)
                )
                Spacer(modifier = Modifier.height(4.dp))
                OutlinedTextField(
                    value = subdlDraft,
                    onValueChange = { subdlDraft = it },
                    label = { Text("SubDL API key (optional)", fontSize = 12.sp) },
                    placeholder = { Text("subdl.com/panel/api", fontSize = 12.sp) },
                    singleLine = true,
                    modifier = Modifier
                        .fillMaxWidth()
                        .testTag("subdl_api_key_field"),
                    textStyle = androidx.compose.ui.text.TextStyle(fontSize = 13.sp)
                )
                Text(
                    text = "Keys stay on this device and unlock higher subtitle quotas.",
                    fontSize = 11.sp,
                    color = MaterialTheme.colorScheme.onSurfaceVariant
                )
            }
        },
        confirmButton = {
            TextButton(onClick = saveKeysAndDismiss) {
                Text("Done")
            }
        }
    )
}
