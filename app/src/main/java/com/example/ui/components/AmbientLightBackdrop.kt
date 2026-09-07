package com.example.ui.components

import androidx.compose.foundation.background
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.runtime.Composable
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.alpha
import androidx.compose.ui.draw.blur
import androidx.compose.ui.draw.scale
import androidx.compose.ui.graphics.Brush
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.layout.ContentScale
import androidx.compose.ui.platform.testTag
import androidx.compose.ui.unit.dp
import coil.compose.AsyncImage
import com.example.util.ImagePreset
import com.example.util.rememberOptimizedImageRequest

/** Artwork-based ambient mode that can extend outside the video surface. */
@Composable
fun AmbientLightBackdrop(
    artworkUrl: String,
    modifier: Modifier = Modifier
) {
    if (artworkUrl.isBlank()) {
        Box(
            modifier = modifier
                .background(Color.Black)
                .testTag("player_ambient_light")
        )
        return
    }
    val artworkRequest = rememberOptimizedImageRequest(
        data = artworkUrl,
        preset = ImagePreset.COMPACT_THUMBNAIL,
        crossfade = false
    )

    Box(
        modifier = modifier
            .background(Color.Black)
            .testTag("player_ambient_light")
    ) {
        AsyncImage(
            model = artworkRequest,
            contentDescription = null,
            contentScale = ContentScale.Crop,
            modifier = Modifier
                .fillMaxSize()
                .scale(1.18f)
                // 32dp blur is ~35% cheaper than 48dp and still fully ambient
                // once the dark scrim is applied. Only shown while paused.
                .blur(32.dp)
                .alpha(0.18f)
        )
        Box(
            modifier = Modifier
                .fillMaxSize()
                .background(
                    Brush.verticalGradient(
                        listOf(
                            Color.Black.copy(alpha = 0.48f),
                            Color.Black.copy(alpha = 0.72f),
                            Color.Black.copy(alpha = 0.96f)
                        )
                    )
                )
        )
    }
}
