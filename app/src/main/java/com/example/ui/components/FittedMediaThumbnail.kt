package com.example.ui.components

import androidx.compose.animation.core.animateFloatAsState
import androidx.compose.foundation.background
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.BoxScope
import androidx.compose.foundation.layout.aspectRatio
import androidx.compose.foundation.layout.fillMaxHeight
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
import androidx.compose.ui.platform.LocalContext
import androidx.compose.ui.semantics.clearAndSetSemantics
import androidx.compose.ui.unit.dp
import coil.compose.AsyncImage
import com.example.util.ImagePreset
import com.example.util.buildOptimizedImageRequest
import com.example.util.rememberThumbnailRequestWithFallback

/**
 * Universal media thumbnail container.
 *
 * TMDB research:
 * - poster_path = 2:3 (0.667) vertical art WITH title text baked in (w342/w500).
 * - backdrop_path = 16:9 (1.778) scene WITHOUT title (99% iso_639_1=null, w780/w1280).
 *
 * To keep the YouTube 16:9 look while showing the poster WITH the name
 * (no crop of the poster, no black bars/pillars, no extra /images API calls
 * per feed item for infinite scroll), [preferPoster] renders the full 2:3
 * poster centered (Fit) over a filled 16:9 backdrop background.
 */
@Composable
fun FittedMediaThumbnail(
    thumbnailUrl: String?,
    backdropUrl: String?,
    contentDescription: String?,
    modifier: Modifier = Modifier,
    posterUrl: String? = null,
    isPosterRatio: Boolean = false,
    preferPoster: Boolean = false,
    imagePreset: ImagePreset = ImagePreset.THUMBNAIL,
    isWatched: Boolean = false,
    dimAlpha: Float? = null,
    shape: Shape = RoundedCornerShape(12.dp),
    overlayContent: @Composable (BoxScope.() -> Unit)? = null
) {
    // For 16:9 widescreen thumbnails, backdrop is the native 16:9 frame.
    // For 2:3 portrait posters, posterUrl is the native 2:3 frame.
    // When preferPoster is set (movie/TV catalog), posterUrl carries the
    // official art WITH the title and is shown uncropped (see below).
    val primaryArtwork = if (isPosterRatio) {
        posterUrl?.takeIf { it.isNotBlank() } ?: thumbnailUrl ?: backdropUrl
    } else if (preferPoster && !posterUrl.isNullOrBlank()) {
        // Background layer still uses the 16:9 scene so the frame is filled.
        backdropUrl?.takeIf { it.isNotBlank() } ?: thumbnailUrl ?: posterUrl
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
        preset = effectivePreset,
        crossfade = true
    )
    // Foreground poster WITH title: uses existing list-level poster_path
    // (w500) so infinite scroll pays zero extra /images calls.
    // Note: intentionally not excluding posterUrl == primaryArtwork — when
    // only the poster exists, the same art backs the frame (dimmed Crop)
    // while the foreground stays uncropped (Fit), so we never crop the title.
    val showPosterForeground = !isPosterRatio &&
        preferPoster &&
        !posterUrl.isNullOrBlank()
    val appContext = LocalContext.current
    val posterForegroundRequest = remember(appContext, posterUrl, showPosterForeground) {
        if (showPosterForeground) {
            buildOptimizedImageRequest(appContext, posterUrl, ImagePreset.POSTER_CARD, true)
        } else null
    }
    var loadFailed by remember(primaryArtwork, fallbackArtwork) { mutableStateOf(false) }
    var errorCount by remember(primaryArtwork, fallbackArtwork) { mutableStateOf(0) }
    var posterFailed by remember(posterUrl) { mutableStateOf(false) }
    val targetAlpha = dimAlpha ?: if (isWatched) 0.52f else 1f
    val animatedAlpha by animateFloatAsState(targetValue = targetAlpha, label = "thumb_dim")

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
            // Background fills 100% of the container with zero letterboxing,
            // side pillars, or black bars.
            AsyncImage(
                model = thumbnailRequest,
                contentDescription = if (showPosterForeground) null else contentDescription,
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
                modifier = Modifier
                    .fillMaxSize()
                    .graphicsLayer {
                        // Dim the scene slightly when the poster sits on top
                        // so the poster title stays legible.
                        alpha = if (showPosterForeground) animatedAlpha * 0.55f else animatedAlpha
                    }
            )
            // Foreground: full 2:3 poster WITH name, uncropped (Fit) and
            // centered. Side areas show the dimmed scene, never black bars.
            if (showPosterForeground && !posterFailed && posterForegroundRequest != null) {
                AsyncImage(
                    model = posterForegroundRequest,
                    contentDescription = contentDescription,
                    contentScale = ContentScale.Fit,
                    onError = { posterFailed = true },
                    modifier = Modifier
                        .fillMaxHeight()
                        .aspectRatio(2f / 3f)
                        .graphicsLayer {
                            alpha = animatedAlpha
                        }
                )
            }
        }

        // Overlays (duration badge, watched pill, progress bar, etc.)
        if (overlayContent != null) {
            overlayContent()
        }
    }
}
