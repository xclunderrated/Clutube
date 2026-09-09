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
import androidx.compose.runtime.CompositionLocalProvider
import androidx.compose.runtime.State
import androidx.compose.runtime.compositionLocalOf
import androidx.compose.runtime.getValue
import androidx.compose.runtime.remember
import androidx.compose.ui.composed
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clip
import androidx.compose.ui.draw.drawBehind
import androidx.compose.ui.geometry.Offset
import androidx.compose.ui.graphics.Brush
import androidx.compose.ui.platform.testTag
import androidx.compose.ui.semantics.clearAndSetSemantics
import androidx.compose.ui.unit.Dp
import androidx.compose.ui.unit.dp

/**
 * YouTube-style animated shimmer effect modifier.
 * Uses drawBehind for zero-recomposition hardware-accelerated sweeping animation.
 * Colors derive from MaterialTheme so light/dark stay in sync with Color.kt.
 */
@Composable
fun rememberShimmerBrush(): Brush {
    val scheme = MaterialTheme.colorScheme
    val baseColor = scheme.surfaceVariant.copy(alpha = 0.9f)
    val highlightColor = scheme.surfaceVariant.copy(alpha = 0.45f)

    return remember(baseColor, highlightColor) {
        Brush.linearGradient(
            colors = listOf(baseColor, highlightColor, baseColor),
            start = Offset.Zero,
            end = Offset(400f, 400f)
        )
    }
}

/**
 * YouTube-style shimmer.
 *
 * Scroll perf design:
 * - ONE infiniteTransition per screen via [SharedShimmerHost]; all skeleton
 *   nodes read [LocalShimmerTranslate] in the draw phase (no recomposition).
 * - [PendingStudioPlaceholder] (rendered per feed card while enrichment
 *   resolves) is STATIC — dozens of concurrent sweeps during scroll was the
 *   worst scroll-jank source. It reserves layout size so names land w/o shift.
 * - Only initial-load skeletons (thumbnail) animate.
 */
val LocalShimmerTranslate = compositionLocalOf<Float?> { null }

@Composable
fun rememberSharedShimmerTranslate(): State<Float> {
    val transition = rememberInfiniteTransition(label = "shared_shimmer")
    return transition.animateFloat(
        initialValue = -400f,
        targetValue = 1400f,
        animationSpec = infiniteRepeatable(
            animation = tween(durationMillis = 1300, easing = LinearEasing),
            repeatMode = RepeatMode.Restart
        ),
        label = "shared_shimmer_anim"
    )
}

/**
 * Provide a single shared sweep for a scrolling screen. Place around the
 * LazyColumn/Grid content (e.g. Home feed) so every skeleton shares one clock.
 */
@Composable
fun SharedShimmerHost(content: @Composable () -> Unit) {
    val translate by rememberSharedShimmerTranslate()
    CompositionLocalProvider(LocalShimmerTranslate provides translate) {
        content()
    }
}

/** Static themed placeholder — zero animation, zero per-frame cost. */
fun Modifier.staticPlaceholder(): Modifier = composed {
    val scheme = MaterialTheme.colorScheme
    val baseColor = scheme.surfaceVariant.copy(alpha = 0.85f)
    this.then(Modifier.drawBehind { drawRect(color = baseColor) })
}

/**
 * Animated skeleton highlight that reads the animation state in the draw phase.
 * The layout and composition phases therefore stay untouched on every animation frame.
 * When inside [SharedShimmerHost], reuses the shared clock (1 anim per screen).
 */
@Composable
fun Modifier.shimmerPlaceholder(): Modifier {
    val scheme = MaterialTheme.colorScheme
    // Derive from theme instead of hard-coded greys to avoid drift.
    val baseColor = scheme.surfaceVariant.copy(alpha = 0.85f)
    val highlightColor = scheme.onSurfaceVariant.copy(alpha = 0.18f)
    val shimmerColors = remember(baseColor, highlightColor) {
        listOf(baseColor, highlightColor, baseColor)
    }
    val sharedTranslate = LocalShimmerTranslate.current
    if (sharedTranslate != null) {
        return this.then(
            Modifier.drawBehind {
                drawRect(color = baseColor)
                drawRect(
                    brush = Brush.linearGradient(
                        colors = shimmerColors,
                        start = Offset(sharedTranslate - 400f, sharedTranslate - 400f),
                        end = Offset(sharedTranslate, sharedTranslate)
                    )
                )
            }
        )
    }
    val transition = rememberInfiniteTransition(label = "shimmer")
    val translateAnim = transition.animateFloat(
        initialValue = -400f,
        targetValue = 1400f,
        animationSpec = infiniteRepeatable(
            animation = tween(durationMillis = 1300, easing = LinearEasing),
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
 * True while a catalog row still carries the placeholder studio label.
 * UI shows [PendingStudioPlaceholder] instead of the string until
 * visible-card enrichment resolves the real studio (YouTube-style:
 * nothing fake is ever rendered as the channel name).
 */
fun isStudioPending(channelName: String): Boolean =
    channelName == com.example.model.UNRESOLVED_STUDIO_NAME

/**
 * Fixed-size bar reserving the channel-name slot while the real
 * studio resolves. Fixed size avoids layout shift when the name lands.
 * STATIC by design: this renders per feed card during scroll, so animating
 * it would mean dozens of concurrent sweeps (the #1 scroll-jank source).
 */
@Composable
fun PendingStudioPlaceholder(
    modifier: Modifier = Modifier,
    width: Dp = 84.dp,
    height: Dp = 11.dp
) {
    Box(
        modifier = modifier
            .width(width)
            .height(height)
            .clip(RoundedCornerShape(3.dp))
            .staticPlaceholder()
            .clearAndSetSemantics { }
    )
}

/**
 * Skeleton placeholder for main feed VideoCard.
 * Only the 16:9 thumbnail sweeps; avatar + text bars are static so an
 * initial-load list of 6 skeletons costs 6 sweeps instead of ~24.
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
            .clearAndSetSemantics { }
    ) {
        // Thumbnail Shimmer (only animated node)
        Box(
            modifier = Modifier
                .fillMaxWidth()
                .aspectRatio(16f / 9f)
                .clip(RoundedCornerShape(12.dp))
                .shimmerPlaceholder()
        )

        Spacer(modifier = Modifier.height(10.dp))

        // Info Row: Avatar + Title & Meta lines (static — no per-frame cost)
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
                    .staticPlaceholder()
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
                        .staticPlaceholder()
                )

                Spacer(modifier = Modifier.height(6.dp))

                // Title Line 2 / Meta details
                Box(
                    modifier = Modifier
                        .fillMaxWidth(0.58f)
                        .height(12.dp)
                        .clip(RoundedCornerShape(4.dp))
                        .staticPlaceholder()
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
            .clearAndSetSemantics { }
    )
}

/**
 * Skeleton placeholder for YouTube Shorts shelf.
 * Only posters sweep; title bars static.
 */
@Composable
fun ShortsShelfSkeleton(
    modifier: Modifier = Modifier
) {
    Column(
        modifier = modifier
            .fillMaxWidth()
            .padding(vertical = 12.dp)
            .clearAndSetSemantics { }
    ) {
        // Shelf Title Skeleton (static)
        Box(
            modifier = Modifier
                .padding(horizontal = 14.dp, vertical = 6.dp)
                .size(width = 100.dp, height = 18.dp)
                .clip(RoundedCornerShape(4.dp))
                .staticPlaceholder()
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
                            .staticPlaceholder()
                    )
                }
            }
        }
    }
}

/**
 * Skeleton placeholder for TV Show Episode item in Watch Screen.
 * Thumbnail sweeps; text bars static.
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
            .padding(8.dp)
            .clearAndSetSemantics { },
        verticalAlignment = Alignment.CenterVertically
    ) {
        // Thumbnail skeleton (only animated node)
        Box(
            modifier = Modifier
                .width(115.dp)
                .aspectRatio(16f / 9f)
                .clip(RoundedCornerShape(6.dp))
                .shimmerEffect()
        )

        Spacer(modifier = Modifier.width(10.dp))

        // Episode info skeleton (static)
        Column(
            modifier = Modifier.weight(1f)
        ) {
            Box(
                modifier = Modifier
                    .fillMaxWidth(0.75f)
                    .height(14.dp)
                    .clip(RoundedCornerShape(4.dp))
                    .staticPlaceholder()
            )

            Spacer(modifier = Modifier.height(6.dp))

            Box(
                modifier = Modifier
                    .fillMaxWidth(0.4f)
                    .height(11.dp)
                    .clip(RoundedCornerShape(4.dp))
                    .staticPlaceholder()
            )

            Spacer(modifier = Modifier.height(6.dp))

            Box(
                modifier = Modifier
                    .fillMaxWidth(0.9f)
                    .height(10.dp)
                    .clip(RoundedCornerShape(4.dp))
                    .staticPlaceholder()
            )
        }
    }
}

/**
 * Skeleton placeholder for CompactRelatedVideoCard (Up next sidebar/list).
 * Thumbnail sweeps; text static.
 */
@Composable
fun CompactRelatedVideoCardSkeleton(
    modifier: Modifier = Modifier
) {
    Row(
        modifier = modifier
            .fillMaxWidth()
            .padding(horizontal = 8.dp, vertical = 6.dp)
            .clearAndSetSemantics { },
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
                    .staticPlaceholder()
            )
            Spacer(modifier = Modifier.height(6.dp))
            Box(
                modifier = Modifier
                    .fillMaxWidth(0.5f)
                    .height(11.dp)
                    .clip(RoundedCornerShape(4.dp))
                    .staticPlaceholder()
            )
        }
    }
}
