package com.example.util

import android.content.Context
import android.graphics.Bitmap
import com.example.BuildConfig
import androidx.compose.runtime.Composable
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.setValue
import androidx.compose.ui.platform.LocalContext
import coil.request.CachePolicy
import coil.request.ImageRequest
import coil.size.Precision
import coil.size.Scale

/** Target sizes used by the feed so Coil never decodes a full-resolution source into a small view. */
enum class ImagePreset(val width: Int, val height: Int) {
    // Keep enough source detail for large tablet cards while remaining bounded
    // on phones and in scrolling feeds.
    THUMBNAIL(640, 360),
    COMPACT_THUMBNAIL(480, 270),
    EPISODE_THUMBNAIL(480, 270),
    POSTER_CARD(342, 513),
    AVATAR(64, 64),
    LARGE_AVATAR(96, 96),
    SHORT_CARD(240, 380),
    SHORT_BACKGROUND(720, 1280),
    BANNER(1280, 360)
}

/**
 * Builds a cache-friendly request for a known UI target.
 * Hardware bitmaps keep decoded pixels out of the managed heap; RGB_565 is used for opaque artwork.
 */
fun buildOptimizedImageRequest(
    context: Context,
    data: Any?,
    preset: ImagePreset = ImagePreset.THUMBNAIL,
    crossfade: Boolean = false
): ImageRequest = buildOptimizedImageRequest(
    context = context,
    data = data,
    width = preset.width,
    height = preset.height,
    crossfade = crossfade
)

fun buildOptimizedImageRequest(
    context: Context,
    data: Any?,
    width: Int,
    height: Int,
    crossfade: Boolean = false
): ImageRequest {
    val targetWidth = width.coerceAtLeast(1)
    val targetHeight = height.coerceAtLeast(1)

    return ImageRequest.Builder(context)
        // Ask the image host for a target-sized source whenever it supports it.
        // Coil's size() still protects unknown hosts from full-resolution decoding.
        .data(optimizeImageUrl(data, targetWidth, targetHeight))
        .size(targetWidth, targetHeight)
        .scale(Scale.FILL)
        .precision(Precision.INEXACT)
        .allowHardware(true)
        .bitmapConfig(Bitmap.Config.RGB_565)
        .memoryCachePolicy(CachePolicy.ENABLED)
        .diskCachePolicy(CachePolicy.ENABLED)
        .networkCachePolicy(CachePolicy.ENABLED)
        .crossfade(crossfade)
        .build()
}

/**
 * Reduces network transfer as well as decode work for the two image hosts used
 * throughout the feed. Unknown URLs are left untouched so provider-specific
 * query parameters cannot be damaged.
 */
private fun optimizeImageUrl(data: Any?, width: Int, height: Int): Any? {
    val url = data as? String ?: return data
    if (url.isBlank()) return data

    return when {
        "image.tmdb.org/t/p/" in url -> {
            Regex("(/t/p/)w\\d+", RegexOption.IGNORE_CASE)
                .replace(url, "$1${tmdbWidthFor(width, height)}")
        }

        "images.unsplash.com/" in url -> {
            url
                .replaceQueryParameter("w", width)
                .replaceQueryParameter("q", 72)
        }

        // YouTube can keep the same channel CDN URL while replacing the
        // underlying avatar/banner. Tie those URLs to the app release so a
        // fresh release always asks for the current artwork.
        "yt3.googleusercontent.com/" in url || "yt3.ggpht.com/" in url -> {
            url.withArtworkCacheVersion()
        }

        else -> data
    }
}

private fun tmdbWidthFor(width: Int, height: Int): String {
    val longestSide = maxOf(width, height)
    return when {
        longestSide <= 200 -> "w185"
        longestSide <= 400 -> "w342"
        longestSide <= 800 -> "w780"
        else -> "w1280"
    }
}

private fun String.replaceQueryParameter(name: String, value: Int): String {
    val parameter = Regex("([?&])$name=\\d+", RegexOption.IGNORE_CASE)
    if (parameter.containsMatchIn(this)) {
        return parameter.replace(this, "$1$name=$value")
    }
    val separator = if ('?' in this) '&' else '?'
    return "$this$separator$name=$value"
}

private fun String.withArtworkCacheVersion(): String {
    val separator = if ('?' in this) '&' else '?'
    return "$this${separator}clutube_artwork_v=${BuildConfig.VERSION_CODE}"
}

@Composable
fun rememberOptimizedImageRequest(
    data: Any?,
    preset: ImagePreset = ImagePreset.THUMBNAIL,
    crossfade: Boolean = false
): ImageRequest {
    val context = LocalContext.current
    return remember(context, data, preset, crossfade) {
        buildOptimizedImageRequest(context, data, preset, crossfade)
    }
}

/**
 * Stateful helper that loads [primaryUrl] (official movie/TV poster), and if it fails to load,
 * automatically falls back to [fallbackUrl] (image/backdrop from that same movie or show).
 */
@Composable
fun rememberThumbnailRequestWithFallback(
    primaryUrl: String?,
    fallbackUrl: String?,
    preset: ImagePreset = ImagePreset.THUMBNAIL,
    crossfade: Boolean = false,
    onFallbackTriggered: (() -> Unit)? = null
): Pair<ImageRequest, () -> Unit> {
    val context = LocalContext.current
    var useFallback by remember(primaryUrl, fallbackUrl) {
        mutableStateOf(false)
    }
    val effectiveUrl = if (useFallback && !fallbackUrl.isNullOrBlank() && fallbackUrl != primaryUrl) {
        fallbackUrl
    } else {
        primaryUrl
    }
    val request = remember(context, effectiveUrl, preset, crossfade) {
        buildOptimizedImageRequest(context, effectiveUrl, preset, crossfade)
    }
    val onError: () -> Unit = {
        if (!useFallback && !fallbackUrl.isNullOrBlank() && fallbackUrl != primaryUrl) {
            useFallback = true
            onFallbackTriggered?.invoke()
        }
    }
    return Pair(request, onError)
}

enum class ArtworkRole {
    POSTER,
    BACKDROP,
    EPISODE_STILL,
    THUMBNAIL
}

fun resolveArtwork(
    video: com.example.model.VideoItem,
    role: ArtworkRole = ArtworkRole.THUMBNAIL
): Pair<String?, String?> {
    return when (role) {
        ArtworkRole.POSTER -> {
            val primary = video.posterUrl?.takeIf { it.isNotBlank() }
                ?: video.thumbnailUrl.takeIf { it.isNotBlank() }
            val fallback = video.backdropUrl?.takeIf { it.isNotBlank() } ?: primary
            primary to fallback
        }
        ArtworkRole.BACKDROP -> {
            val primary = video.backdropUrl?.takeIf { it.isNotBlank() }
                ?: video.thumbnailUrl.takeIf { it.isNotBlank() }
            val fallback = video.posterUrl?.takeIf { it.isNotBlank() } ?: primary
            primary to fallback
        }
        ArtworkRole.EPISODE_STILL -> {
            val primary = video.episodeStillUrl?.takeIf { it.isNotBlank() }
                ?: video.backdropUrl?.takeIf { it.isNotBlank() }
            val fallback = video.thumbnailUrl.takeIf { it.isNotBlank() } ?: video.posterUrl
            primary to fallback
        }
        ArtworkRole.THUMBNAIL -> {
            val primary = video.thumbnailUrl.takeIf { it.isNotBlank() }
                ?: video.posterUrl
                ?: video.backdropUrl
            val fallback = video.backdropUrl?.takeIf { it.isNotBlank() } ?: video.posterUrl
            primary to fallback
        }
    }
}


