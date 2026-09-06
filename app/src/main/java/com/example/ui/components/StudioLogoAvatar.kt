package com.example.ui.components

import androidx.compose.foundation.background
import androidx.compose.foundation.border
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.shape.CircleShape
import androidx.compose.runtime.Composable
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clip
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.layout.ContentScale
import androidx.compose.ui.unit.Dp
import androidx.compose.ui.unit.dp
import coil.compose.AsyncImage
import com.example.util.ImagePreset
import com.example.util.rememberOptimizedImageRequest

/**
 * YouTube-style channel avatar for studio/network marks.
 *
 * Studio logos are often wide transparent PNGs rather than square portraits.
 * Containing the mark inside a brand-colored circular tile keeps the full logo
 * visible at every avatar size instead of cropping its sides or leaving a
 * distracting white halo around dark studio marks.
 */
@Composable
fun StudioLogoAvatar(
    logoUrl: String?,
    contentDescription: String?,
    modifier: Modifier = Modifier,
    innerPadding: Dp = 3.dp,
    borderWidth: Dp = 0.dp,
    borderColor: Color = Color.Transparent
) {
    val isStudioLogo = logoUrl?.let {
        it.contains("image.tmdb.org/t/p/", ignoreCase = true) ||
            it.contains("yt3.googleusercontent.com/", ignoreCase = true) ||
            it.contains("yt3.ggpht.com/", ignoreCase = true)
    } == true
    val isYouTubeAvatar = logoUrl?.let {
        it.contains("yt3.googleusercontent.com/", ignoreCase = true) ||
            it.contains("yt3.ggpht.com/", ignoreCase = true)
    } == true
    val useLogoTile = isStudioLogo && !isYouTubeAvatar
    val studioBackground = if (useLogoTile) {
        studioBrandBackground(contentDescription, logoUrl)
    } else {
        Color.Transparent
    }
    val logoRequest = rememberOptimizedImageRequest(
        data = logoUrl,
        preset = ImagePreset.LARGE_AVATAR
    )

    Box(
        modifier = modifier
            .clip(CircleShape)
            .background(if (useLogoTile) studioBackground else Color.Transparent)
            .then(
                if (borderWidth > 0.dp) {
                    Modifier.border(borderWidth, borderColor, CircleShape)
                } else {
                    Modifier
                }
            ),
        contentAlignment = Alignment.Center
    ) {
        if (!logoUrl.isNullOrBlank()) {
            AsyncImage(
                model = logoRequest,
                contentDescription = contentDescription,
                // NewPipe supplies the real YouTube avatar, already prepared
                // by YouTube for circular use. Fill the avatar boundary and
                // let the parent clip it instead of adding a second tile.
                contentScale = if (useLogoTile) ContentScale.Fit else ContentScale.Crop,
                modifier = Modifier
                    .fillMaxSize()
                    .padding(if (useLogoTile) innerPadding else 0.dp)
            )
        }
    }
}

private fun studioBrandBackground(name: String?, logoUrl: String?): Color {
    val normalized = name.orEmpty().lowercase()
    val isTransparentCatalogLogo = logoUrl?.contains("image.tmdb.org/t/p/", ignoreCase = true) == true

    // TMDB company marks are frequently transparent black PNGs. A light tile
    // keeps those marks readable while preserving the branded treatment for
    // the current channel artwork.
    if (isTransparentCatalogLogo) {
        return Color(0xFFF1F1F1)
    }

    return when {
        // These marks also commonly arrive as black variants, so use a light
        // tile instead of allowing a dark brand tile to hide the artwork.
        "netflix" in normalized || "hbo" in normalized || "apple" in normalized -> Color(0xFFF1F1F1)
        "marvel" in normalized -> Color(0xFFE62429)
        "warner" in normalized -> Color(0xFF0B1D3A)
        "universal" in normalized -> Color(0xFF111827)
        "paramount" in normalized -> Color(0xFF0B2E52)
        "disney" in normalized -> Color(0xFF09245C)
        "a24" in normalized -> Color(0xFFE7E7E7)
        else -> Color(0xFFF1F1F1)
    }
}
