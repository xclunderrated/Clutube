package com.example.ui.components

import androidx.compose.animation.AnimatedVisibility
import androidx.compose.animation.core.tween
import androidx.compose.animation.fadeIn
import androidx.compose.animation.fadeOut
import androidx.compose.foundation.background
import androidx.compose.foundation.clickable
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.width
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.filled.SkipNext
import androidx.compose.material3.Icon
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clip
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.platform.testTag
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import com.example.data.introdb.SkipSegment

/**
 * Netflix-style skip pill rendered over the provider embed surface.
 * Visibility is fully caller-driven (see `PlayerSurfaceWithUpNext`); this
 * composable only handles presentation and the tap affordance.
 */
@Composable
fun SkipSegmentButton(
    segment: SkipSegment?,
    visible: Boolean,
    onSkip: (SkipSegment) -> Unit,
    modifier: Modifier = Modifier
) {
    AnimatedVisibility(
        visible = visible && segment != null,
        // Tap must feel instant: quick fade-in, near-instant fade-out so the
        // pill is gone the moment it is touched (no linger + reappear flash).
        enter = fadeIn(animationSpec = tween(150)),
        exit = fadeOut(animationSpec = tween(80)),
        modifier = modifier
    ) {
        val current = segment ?: return@AnimatedVisibility
        Box(
            modifier = Modifier
                .clip(RoundedCornerShape(8.dp))
                .background(Color.Black.copy(alpha = 0.78f))
                .clickable { onSkip(current) }
                .testTag("skip_segment_button")
                .padding(horizontal = 14.dp, vertical = 10.dp),
            contentAlignment = Alignment.Center
        ) {
            Row(verticalAlignment = Alignment.CenterVertically) {
                Text(
                    text = current.label(),
                    color = Color.White,
                    fontSize = 14.sp,
                    fontWeight = FontWeight.SemiBold
                )
                if (current.isNextEpisodeStyle) {
                    Spacer(modifier = Modifier.width(6.dp))
                    Icon(
                        imageVector = Icons.Default.SkipNext,
                        contentDescription = null,
                        tint = Color.White
                    )
                }
            }
        }
    }
}
