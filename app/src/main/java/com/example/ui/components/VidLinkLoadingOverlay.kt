package com.example.ui.components

import androidx.compose.foundation.background
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.size
import androidx.compose.material3.CircularProgressIndicator
import androidx.compose.runtime.Composable
import androidx.compose.ui.Modifier
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.platform.testTag
import androidx.compose.ui.unit.dp
import com.example.ui.theme.YouTubeRed

/** Covers VidLink's rough first frames with a quiet dark loading state. */
@Composable
fun VidLinkLoadingOverlay(
    modifier: Modifier = Modifier
) {
    Box(
        modifier = modifier
            .fillMaxSize()
            .background(Color(0xFF090909))
            .testTag("vidlink_loading_overlay")
    ) {
        CircularProgressIndicator(
            color = YouTubeRed,
            strokeWidth = 2.5.dp,
            modifier = Modifier
                .size(32.dp)
                .align(androidx.compose.ui.Alignment.Center)
        )
    }
}
