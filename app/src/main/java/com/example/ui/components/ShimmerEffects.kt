package com.example.ui.components

import androidx.compose.animation.core.LinearEasing
import androidx.compose.animation.core.RepeatMode
import androidx.compose.animation.core.animateFloat
import androidx.compose.animation.core.infiniteRepeatable
import androidx.compose.animation.core.rememberInfiniteTransition
import androidx.compose.animation.core.tween
import androidx.compose.foundation.background
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.PaddingValues
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.aspectRatio
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.layout.width
import androidx.compose.foundation.lazy.LazyRow
import androidx.compose.foundation.shape.CircleShape
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.material3.MaterialTheme
import androidx.compose.runtime.Composable
import androidx.compose.runtime.remember
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clip
import androidx.compose.ui.draw.drawBehind
import androidx.compose.ui.geometry.Offset
import androidx.compose.ui.graphics.Brush
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.platform.testTag
import androidx.compose.ui.unit.dp

/**
 * YouTube-style animated shimmer effect modifier.
 * Uses drawBehind for zero-recomposition hardware-accelerated sweeping animation.
 */
@Composable
fun rememberShimmerBrush(): Brush {
    val isDark = MaterialTheme.colorScheme.surface.let {
        (it.red * 0.299 + it.green * 0.587 + it.blue * 0.114) < 0.5
    }

    val baseColor = if (isDark) Color(0xFF242424) else Color(0xFFE2E2E2)
    val highlightColor = if (isDark) Color(0xFF333333) else Color(0xFFEEEEEE)

    return remember(baseColor, highlightColor) {
        Brush.linearGradient(
            colors = listOf(baseColor, highlightColor, baseColor),
            start = Offset.Zero,
            end = Offset(400f, 400f)
        )
    }
}

/**
 * Animated skeleton highlight that reads the animation state in the draw phase.
 * The layout and composition phases therefore stay untouched on every animation frame.
 */
@Composable
fun Modifier.shimmerPlaceholder(): Modifier {
    val isDark = MaterialTheme.colorScheme.surface.let {
        (it.red * 0.299 + it.green * 0.587 + it.blue * 0.114) < 0.5
    }
    val baseColor = if (isDark) Color(0xFF242424) else Color(0xFFE2E2E2)
    val highlightColor = if (isDark) Color(0xFF333333) else Color(0xFFEEEEEE)
    val shimmerColors = remember(baseColor, highlightColor) {
        listOf(baseColor, highlightColor, baseColor)
    }
    val transition = rememberInfiniteTransition(label = "shimmer")
    val translateAnim = transition.animateFloat(
        initialValue = -400f,
        targetValue = 1400f,
        animationSpec = infiniteRepeatable(
            animation = tween(durationMillis = 1100, easing = LinearEasing),
            repeatMode = RepeatMode.Restart
        ),
        label = "shimmer_anim"
    )

    return this.then(
        Modifier.drawBehind {
            drawRect(color = baseColor)
            val translation = translateAnim.value
            drawRect(
                brush = Brush.linearGradient(
                    colors = shimmerColors,
                    start = Offset(translation - 400f, translation - 400f),
                    end = Offset(translation, translation)
                )
            )
        }
    )
}

@Composable
fun Modifier.shimmerEffect(): Modifier = shimmerPlaceholder()

/**
 * Skeleton placeholder for main feed VideoCard.
 * Clean 16:9 thumbnail, time badge placeholder, channel avatar circle, and multi-line title shimmer bars.
 */
@Composable
fun VideoCardSkeleton(
    modifier: Modifier = Modifier
) {
    Column(
        modifier = modifier
            .fillMaxWidth()
            .padding(bottom = 16.dp)
            .testTag("video_card_skeleton")
    ) {
        // Thumbnail Shimmer
        Box(
            modifier = Modifier
                .fillMaxWidth()
                .aspectRatio(16f / 9f)
                .clip(RoundedCornerShape(12.dp))
                .shimmerPlaceholder()
        )

        Spacer(modifier = Modifier.height(10.dp))

        // Info Row: Avatar + Title & Meta lines
        Row(
            modifier = Modifier
                .fillMaxWidth()
                .padding(horizontal = 8.dp),
            verticalAlignment = Alignment.Top
        ) {
            // Channel Avatar Circle
            Box(
                modifier = Modifier
                    .size(38.dp)
                    .clip(CircleShape)
                    .shimmerPlaceholder()
            )

            Spacer(modifier = Modifier.width(12.dp))

            Column(
                modifier = Modifier.weight(1f)
            ) {
                // Title Line 1
                Box(
                    modifier = Modifier
                        .fillMaxWidth(0.92f)
                        .height(14.dp)
                        .clip(RoundedCornerShape(4.dp))
                        .shimmerPlaceholder()
                )

                Spacer(modifier = Modifier.height(6.dp))

                // Title Line 2 / Meta details
                Box(
                    modifier = Modifier
                        .fillMaxWidth(0.58f)
                        .height(12.dp)
                        .clip(RoundedCornerShape(4.dp))
                        .shimmerPlaceholder()
                )
            }
        }
    }
}

/**
 * Skeleton placeholder for Premiere Hero Cinema Card on Home screen.
 */
@Composable
fun PremiereHeroCardSkeleton(
    modifier: Modifier = Modifier
) {
    Box(
        modifier = modifier
            .fillMaxWidth()
            .padding(horizontal = 14.dp, vertical = 6.dp)
            .height(215.dp)
            .clip(RoundedCornerShape(12.dp))
            .shimmerPlaceholder()
            .testTag("premiere_hero_skeleton")
    )
}

/**
 * Skeleton placeholder for YouTube Shorts shelf.
 */
@Composable
fun ShortsShelfSkeleton(
    modifier: Modifier = Modifier
) {
    Column(
        modifier = modifier
            .fillMaxWidth()
            .padding(vertical = 12.dp)
    ) {
        // Shelf Title Skeleton
        Box(
            modifier = Modifier
                .padding(horizontal = 14.dp, vertical = 6.dp)
                .size(width = 100.dp, height = 18.dp)
                .clip(RoundedCornerShape(4.dp))
                .shimmerPlaceholder()
        )

        Spacer(modifier = Modifier.height(8.dp))

        // Horizontal row of vertical short skeletons
        LazyRow(
            contentPadding = PaddingValues(horizontal = 14.dp),
            horizontalArrangement = Arrangement.spacedBy(10.dp),
            userScrollEnabled = false
        ) {
            items(
                count = 4,
                key = { "short_skeleton_$it" },
                contentType = { "short_skeleton" }
            ) {
                Column(modifier = Modifier.width(135.dp)) {
                    Box(
                        modifier = Modifier
                        .fillMaxWidth()
                        .aspectRatio(9f / 16f)
                        .clip(RoundedCornerShape(12.dp))
                            .shimmerPlaceholder()
                    )
                    Spacer(modifier = Modifier.height(6.dp))
                    Box(
                        modifier = Modifier
                            .fillMaxWidth(0.9f)
                            .height(12.dp)
                            .clip(RoundedCornerShape(4.dp))
                            .shimmerPlaceholder()
                    )
                }
            }
        }
    }
}

/**
 * Skeleton placeholder for TV Show Episode item in Watch Screen.
 */
@Composable
fun EpisodeItemCardSkeleton(
    modifier: Modifier = Modifier
) {
    Row(
        modifier = modifier
            .fillMaxWidth()
            .padding(vertical = 4.dp)
            .clip(RoundedCornerShape(10.dp))
            .background(MaterialTheme.colorScheme.surfaceVariant.copy(alpha = 0.35f))
            .padding(8.dp),
        verticalAlignment = Alignment.CenterVertically
    ) {
        // Thumbnail skeleton
        Box(
            modifier = Modifier
                .width(115.dp)
                .aspectRatio(16f / 9f)
                .clip(RoundedCornerShape(6.dp))
                .shimmerEffect()
        )

        Spacer(modifier = Modifier.width(10.dp))

        // Episode info skeleton
        Column(
            modifier = Modifier.weight(1f)
        ) {
            Box(
                modifier = Modifier
                    .fillMaxWidth(0.75f)
                    .height(14.dp)
                    .clip(RoundedCornerShape(4.dp))
                    .shimmerEffect()
            )

            Spacer(modifier = Modifier.height(6.dp))

            Box(
                modifier = Modifier
                    .fillMaxWidth(0.4f)
                    .height(11.dp)
                    .clip(RoundedCornerShape(4.dp))
                    .shimmerEffect()
            )

            Spacer(modifier = Modifier.height(6.dp))

            Box(
                modifier = Modifier
                    .fillMaxWidth(0.9f)
                    .height(10.dp)
                    .clip(RoundedCornerShape(4.dp))
                    .shimmerEffect()
            )
        }
    }
}

/**
 * Skeleton placeholder for CompactRelatedVideoCard (Up next sidebar/list).
 */
@Composable
fun CompactRelatedVideoCardSkeleton(
    modifier: Modifier = Modifier
) {
    Row(
        modifier = modifier
            .fillMaxWidth()
            .padding(horizontal = 8.dp, vertical = 6.dp),
        verticalAlignment = Alignment.CenterVertically
    ) {
        Box(
            modifier = Modifier
                .width(115.dp)
                .aspectRatio(16f / 9f)
                .clip(RoundedCornerShape(6.dp))
                .shimmerEffect()
        )

        Spacer(modifier = Modifier.width(10.dp))

        Column(modifier = Modifier.weight(1f)) {
            Box(
                modifier = Modifier
                    .fillMaxWidth(0.85f)
                    .height(13.dp)
                    .clip(RoundedCornerShape(4.dp))
                    .shimmerEffect()
            )
            Spacer(modifier = Modifier.height(6.dp))
            Box(
                modifier = Modifier
                    .fillMaxWidth(0.5f)
                    .height(11.dp)
                    .clip(RoundedCornerShape(4.dp))
                    .shimmerEffect()
            )
        }
    }
}
