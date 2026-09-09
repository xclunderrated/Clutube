package com.example.ui.components

import androidx.compose.foundation.background
import androidx.compose.foundation.clickable
import androidx.compose.foundation.interaction.MutableInteractionSource
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.size
import androidx.compose.material3.CircularProgressIndicator
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.Text
import androidx.compose.material3.TextButton
import androidx.compose.runtime.Composable
import androidx.compose.runtime.remember
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.platform.testTag
import androidx.compose.ui.semantics.contentDescription
import androidx.compose.ui.semantics.liveRegion
import androidx.compose.ui.semantics.semantics
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import com.example.ui.theme.YouTubeRed

/** Covers VidLink's rough first frames with a quiet dark loading state. */
@Composable
fun VidLinkLoadingOverlay(
    modifier: Modifier = Modifier,
    message: String = "Loading stream…",
    onCancel: (() -> Unit)? = null,
    onRetry: (() -> Unit)? = null
) {
    Box(
        modifier = modifier
            .fillMaxSize()
            .background(MaterialTheme.colorScheme.background.copy(alpha = 0.96f))
            // Block touches from passing through to the player underneath.
            .clickable(
                interactionSource = remember { MutableInteractionSource() },
                indication = null,
                onClick = {}
            )
            .testTag("vidlink_loading_overlay")
            .semantics(mergeDescendants = true) {
                contentDescription = message
                liveRegion = androidx.compose.ui.semantics.LiveRegionMode.Polite
            }
    ) {
        Column(
            modifier = Modifier.align(Alignment.Center),
            horizontalAlignment = Alignment.CenterHorizontally
        ) {
            CircularProgressIndicator(
                color = YouTubeRed,
                strokeWidth = 2.5.dp,
                modifier = Modifier.size(32.dp)
            )
            Spacer(modifier = Modifier.height(12.dp))
            Text(
                text = message,
                fontSize = 13.sp,
                fontWeight = FontWeight.Medium,
                color = MaterialTheme.colorScheme.onBackground
            )
            if (onCancel != null || onRetry != null) {
                Spacer(modifier = Modifier.height(8.dp))
                androidx.compose.foundation.layout.Row {
                    if (onCancel != null) {
                        TextButton(onClick = onCancel) {
                            Text("Cancel", color = MaterialTheme.colorScheme.onSurfaceVariant)
                        }
                    }
                    if (onRetry != null) {
                        TextButton(
                            onClick = onRetry,
                            modifier = Modifier.padding(start = 4.dp)
                        ) {
                            Text("Retry", color = YouTubeRed, fontWeight = FontWeight.Bold)
                        }
                    }
                }
            }
        }
    }
}
