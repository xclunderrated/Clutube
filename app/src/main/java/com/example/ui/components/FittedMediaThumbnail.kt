package com.example.ui.components

import androidx.compose.foundation.background
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.BoxScope
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.material3.MaterialTheme
import androidx.compose.runtime.Composable
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clip
import androidx.compose.ui.graphics.Shape
import androidx.compose.ui.graphics.graphicsLayer
import androidx.compose.ui.layout.ContentScale
import androidx.compose.ui.unit.dp
import coil.compose.AsyncImage
import com.example.util.ImagePreset
import com.example.util.rememberThumbnailRequestWithFallback

/**
 * Universal media thumbnail container that renders widescreen poster / backdrop art
 * perfectly filling the 16:9 thumbnail frame (ContentScale.Crop) with zero letterboxing
 * or black bars.
 */
@Composable
fun FittedMediaThumbnail(
    thumbnailUrl: String?,
    backdropUrl: String?,
    contentDescription: String?,
    modifier: Modifier = Modifier,
    posterUrl: String? = null,
    isPosterRatio: Boolean = false,
    imagePreset: ImagePreset = ImagePreset.THUMBNAIL,
    isWatched: Boolean = false,
    shape: Shape = RoundedCornerShape(12.dp),
    overlayContent: @Composable (BoxScope.() -> Unit)? = null
) {
    // For 16:9 widescreen thumbnails, backdrop is the native 16:9 frame.
    // For 2:3 portrait posters, posterUrl is the native 2:3 frame.
    val primaryArtwork = if (isPosterRatio) {
        posterUrl?.takeIf { it.isNotBlank() } ?: thumbnailUrl ?: backdropUrl
    } else {
        backdropUrl?.takeIf { it.isNotBlank() } ?: thumbnailUrl ?: posterUrl
    }
    val fallbackArtwork = if (isPosterRatio) {
        thumbnailUrl?.takeIf { it.isNotBlank() } ?: backdropUrl ?: posterUrl
    } else {
        thumbnailUrl?.takeIf { it.isNotBlank() } ?: posterUrl ?: backdropUrl
    }

    val effectivePreset = if (isPosterRatio && imagePreset == ImagePreset.THUMBNAIL) {
        ImagePreset.POSTER_CARD
    } else {
        imagePreset
    }

    val (thumbnailRequest, onThumbnailError) = rememberThumbnailRequestWithFallback(
        primaryUrl = primaryArtwork,
        fallbackUrl = fallbackArtwork,
        preset = effectivePreset
    )

    Box(
        modifier = modifier
            .clip(shape)
            .background(MaterialTheme.colorScheme.surfaceVariant.copy(alpha = 0.5f)),
        contentAlignment = Alignment.Center
    ) {
        // Image fills 100% of the container with zero letterboxing, side pillars, or black bars.
        AsyncImage(
            model = thumbnailRequest,
            contentDescription = contentDescription,
            contentScale = ContentScale.Crop,
            onError = { onThumbnailError() },
            modifier = Modifier
                .fillMaxSize()
                .graphicsLayer {
                    alpha = if (isWatched) 0.52f else 1f
                }
        )

        // Overlays (duration badge, watched pill, progress bar, etc.)
        if (overlayContent != null) {
            overlayContent()
        }
    }
}
