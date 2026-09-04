package com.example.ui.components

import androidx.compose.foundation.background
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.shape.CircleShape
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clip
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.layout.ContentScale
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.unit.TextUnit
import androidx.compose.ui.unit.sp
import coil.compose.AsyncImage
import com.example.ui.theme.YouTubeRed
import com.example.util.ImagePreset
import com.example.util.rememberOptimizedImageRequest

/** Renders the chosen local photo, falling back to intentional initials. */
@Composable
fun LocalProfileAvatar(
    value: String,
    modifier: Modifier = Modifier,
    imagePreset: ImagePreset = ImagePreset.AVATAR,
    textSize: TextUnit = 16.sp,
    contentDescription: String? = "Local profile"
) {
    val imageUri = value.trim().takeIf(::isLocalProfileImageReference)
    val imageRequest = imageUri?.let {
        rememberOptimizedImageRequest(data = it, preset = imagePreset)
    }
    val initials = value.trim()
        .takeIf { it.isNotBlank() && !isLocalProfileImageReference(it) }
        ?.take(2)
        ?.uppercase()
        .takeUnless { it.isNullOrBlank() }
        ?: "C"

    Box(
        modifier = modifier
            .clip(CircleShape)
            .background(YouTubeRed.copy(alpha = 0.18f)),
        contentAlignment = Alignment.Center
    ) {
        if (imageRequest != null) {
            AsyncImage(
                model = imageRequest,
                contentDescription = contentDescription,
                contentScale = ContentScale.Crop,
                modifier = Modifier.matchParentSize()
            )
        } else {
            Text(
                text = initials,
                fontSize = textSize,
                fontWeight = FontWeight.Bold,
                color = YouTubeRed
            )
        }
    }
}

fun isLocalProfileImageReference(value: String): Boolean {
    val normalized = value.trim().lowercase()
    return normalized.startsWith("content://") ||
        normalized.startsWith("file://") ||
        normalized.startsWith("android.resource://") ||
        normalized.startsWith("data:image/")
}
