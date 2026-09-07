package com.example.ui.components

import androidx.compose.foundation.background
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.BoxScope
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.filled.BrokenImage
import androidx.compose.material3.Icon
import androidx.compose.material3.MaterialTheme
import androidx.compose.runtime.Composable
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.setValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clip
import androidx.compose.ui.graphics.Shape
import androidx.compose.ui.graphics.graphicsLayer
import androidx.compose.ui.layout.ContentScale
import androidx.compose.ui.semantics.clearAndSetSemantics
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
    dimAlpha: Float? = null,
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

    // No crossfade in scrolling lists: per-item fade-ins during fling cost
    // frames. Detail/hero surfaces can opt in explicitly if needed.
    val (thumbnailRequest, onThumbnailError) = rememberThumbnailRequestWithFallback(
        primaryUrl = primaryArtwork,
        fallbackUrl = fallbackArtwork,
        preset = effectivePreset,
        crossfade = false
    )
    var loadFailed by remember(primaryArtwork, fallbackArtwork) { mutableStateOf(false) }
    var errorCount by remember(primaryArtwork, fallbackArtwork) { mutableStateOf(0) }
    // Static dim: animating alpha per card on every bind was 2 anims/card.
    // Watched state changes are rare; a snap is cheaper and invisible here.
    val targetAlpha = dimAlpha ?: if (isWatched) 0.52f else 1f
    val needsDimLayer = targetAlpha < 0.999f

    val hasArtwork = !primaryArtwork.isNullOrBlank() || !fallbackArtwork.isNullOrBlank()

    Box(
        modifier = modifier
            .clip(shape)
            .background(MaterialTheme.colorScheme.surfaceVariant.copy(alpha = 0.5f)),
        contentAlignment = Alignment.Center
    ) {
        if (!hasArtwork || loadFailed) {
            // Themed empty/error state instead of a permanent grey box.
            Icon(
                imageVector = Icons.Default.BrokenImage,
                contentDescription = null,
                tint = MaterialTheme.colorScheme.onSurfaceVariant.copy(alpha = 0.5f),
                modifier = Modifier
                    .size(28.dp)
                    .clearAndSetSemantics { }
            )
        } else {
            // Image fills 100% of the container with zero letterboxing, side pillars, or black bars.
            AsyncImage(
                model = thumbnailRequest,
                contentDescription = contentDescription,
                contentScale = ContentScale.Crop,
                onError = {
                    errorCount += 1
                    // First failure tries the fallback; second failure shows broken-image.
                    onThumbnailError()
                    if (errorCount >= 2 || fallbackArtwork.isNullOrBlank() || fallbackArtwork == primaryArtwork) {
                        // If there is no usable fallback, fail immediately, otherwise
                        // fail only after the fallback also errors.
                        if (fallbackArtwork.isNullOrBlank() || fallbackArtwork == primaryArtwork || errorCount >= 2) {
                            loadFailed = true
                        }
                    }
                },
                onSuccess = { loadFailed = false },
                modifier = if (needsDimLayer) {
                    Modifier
                        .fillMaxSize()
                        .graphicsLayer { alpha = targetAlpha }
                } else {
                    Modifier.fillMaxSize()
                }
            )
        }

        // Overlays (duration badge, watched pill, progress bar, etc.)
        if (overlayContent != null) {
            overlayContent()
        }
    }
}
