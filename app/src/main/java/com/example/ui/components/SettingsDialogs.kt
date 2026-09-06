package com.example.ui.components

import android.content.Intent
import androidx.activity.compose.rememberLauncherForActivityResult
import androidx.activity.result.contract.ActivityResultContracts
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.layout.width
import androidx.compose.material3.AlertDialog
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.OutlinedTextField
import androidx.compose.material3.Text
import androidx.compose.material3.TextButton
import androidx.compose.runtime.Composable
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.setValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.platform.LocalContext
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import com.example.util.ImagePreset

@Composable
fun EditProfileDialog(
    localProfileName: String,
    localProfileAvatar: String,
    onSaveProfile: (String, String) -> Unit,
    onDismiss: () -> Unit
) {
    var nameDraft by remember(localProfileName) { mutableStateOf(localProfileName) }
    var avatarDraft by remember(localProfileAvatar) { mutableStateOf(localProfileAvatar) }
    val context = LocalContext.current
    val profileImagePicker = rememberLauncherForActivityResult(
        contract = ActivityResultContracts.OpenDocument()
    ) { uri ->
        uri ?: return@rememberLauncherForActivityResult
        runCatching {
            context.contentResolver.takePersistableUriPermission(
                uri,
                Intent.FLAG_GRANT_READ_URI_PERMISSION
            )
        }
        avatarDraft = uri.toString()
    }
    AlertDialog(
        onDismissRequest = onDismiss,
        title = { Text("Edit local profile") },
        text = {
            Column(verticalArrangement = Arrangement.spacedBy(10.dp)) {
                OutlinedTextField(
                    value = nameDraft,
                    onValueChange = { nameDraft = it },
                    label = { Text("Profile name") },
                    singleLine = true
                )
                if (isLocalProfileImageReference(avatarDraft)) {
                    Row(verticalAlignment = Alignment.CenterVertically) {
                        LocalProfileAvatar(
                            value = avatarDraft,
                            imagePreset = ImagePreset.LARGE_AVATAR,
                            modifier = Modifier.size(52.dp)
                        )
                        Spacer(modifier = Modifier.width(10.dp))
                        Column {
                            Text(
                                text = "Profile photo selected",
                                fontSize = 13.sp,
                                fontWeight = FontWeight.Medium
                            )
                            Text(
                                text = "Stored locally on this device",
                                fontSize = 11.sp,
                                color = MaterialTheme.colorScheme.onSurfaceVariant
                            )
                        }
                    }
                } else {
                    OutlinedTextField(
                        value = avatarDraft,
                        onValueChange = { avatarDraft = it.take(2) },
                        label = { Text("Avatar letters") },
                        singleLine = true
                    )
                }
                Row(horizontalArrangement = Arrangement.spacedBy(4.dp)) {
                    TextButton(onClick = { profileImagePicker.launch(arrayOf("image/*")) }) {
                        Text("Choose photo")
                    }
                    if (isLocalProfileImageReference(avatarDraft)) {
                        TextButton(onClick = {
                            avatarDraft = nameDraft.trim().take(2).ifBlank { "C" }
                        }) {
                            Text("Use initials")
                        }
                    }
                }
            }
        },
        confirmButton = {
            TextButton(onClick = {
                onSaveProfile(nameDraft, avatarDraft)
                onDismiss()
            }) { Text("Save") }
        },
        dismissButton = {
            TextButton(onClick = onDismiss) { Text("Cancel") }
        }
    )
}

@Composable
fun ClearLocalDataDialog(
    onClearLocalData: () -> Unit,
    onDismiss: () -> Unit
) {
    AlertDialog(
        onDismissRequest = onDismiss,
        title = { Text("Clear local data?") },
        text = { Text("This removes your local profile, history, saved videos, likes, and queue from this device.") },
        confirmButton = {
            TextButton(onClick = {
                onClearLocalData()
                onDismiss()
            }) { Text("Clear data") }
        },
        dismissButton = {
            TextButton(onClick = onDismiss) { Text("Cancel") }
        }
    )
}

@Composable
fun RecommendationControlsDialog(
    notInterestedCount: Int,
    notRecommendedChannelCount: Int,
    onClearRecommendationPreferences: () -> Unit,
    onDismiss: () -> Unit
) {
    AlertDialog(
        onDismissRequest = onDismiss,
        title = { Text("Recommendation controls") },
        text = {
            Column(verticalArrangement = Arrangement.spacedBy(8.dp)) {
                Text(
                    text = "Your Home feed respects choices made from a video's menu.",
                    color = MaterialTheme.colorScheme.onSurfaceVariant,
                    fontSize = 13.sp
                )
                Text(
                    text = "$notInterestedCount videos hidden\n$notRecommendedChannelCount channels blocked",
                    color = MaterialTheme.colorScheme.onSurface,
                    fontSize = 14.sp,
                    fontWeight = FontWeight.Medium
                )
            }
        },
        confirmButton = {
            TextButton(onClick = {
                onClearRecommendationPreferences()
                onDismiss()
            }) {
                Text("Clear choices")
            }
        },
        dismissButton = {
            TextButton(onClick = onDismiss) {
                Text("Done")
            }
        }
    )
}
