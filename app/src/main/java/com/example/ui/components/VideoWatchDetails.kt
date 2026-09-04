package com.example.ui.components

import android.content.Intent
import android.net.Uri
import androidx.compose.animation.AnimatedVisibility
import androidx.compose.animation.animateContentSize
import androidx.compose.foundation.background
import androidx.compose.foundation.border
import androidx.compose.foundation.clickable
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.ExperimentalLayoutApi
import androidx.compose.foundation.layout.FlowRow
import androidx.compose.foundation.layout.PaddingValues
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.layout.width
import androidx.compose.foundation.lazy.LazyRow
import androidx.compose.foundation.lazy.items
import androidx.compose.foundation.rememberScrollState
import androidx.compose.foundation.shape.CircleShape
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.filled.AccountCircle
import androidx.compose.material.icons.filled.AutoAwesome
import androidx.compose.material.icons.filled.Bookmark
import androidx.compose.material.icons.filled.BookmarkBorder
import androidx.compose.material.icons.filled.Check
import androidx.compose.material.icons.filled.CheckCircle
import androidx.compose.material.icons.filled.Download
import androidx.compose.material.icons.filled.KeyboardArrowDown
import androidx.compose.material.icons.filled.KeyboardArrowUp
import androidx.compose.material.icons.filled.Movie
import androidx.compose.material.icons.filled.NotificationsActive
import androidx.compose.material.icons.filled.NotificationsNone
import androidx.compose.material.icons.filled.Person
import androidx.compose.material.icons.filled.QueueMusic
import androidx.compose.material.icons.filled.Share
import androidx.compose.material.icons.filled.SkipNext
import androidx.compose.material.icons.filled.ThumbDown
import androidx.compose.material.icons.filled.ThumbUp
import androidx.compose.material.icons.filled.Tv
import androidx.compose.material.icons.outlined.ThumbDown
import androidx.compose.material.icons.outlined.ThumbUp
import androidx.compose.material3.ButtonDefaults
import androidx.compose.material3.ElevatedButton
import androidx.compose.material3.HorizontalDivider
import androidx.compose.material3.Icon
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.setValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clip
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.graphics.vector.ImageVector
import androidx.compose.ui.layout.ContentScale
import androidx.compose.ui.platform.LocalContext
import androidx.compose.ui.platform.testTag
import androidx.compose.ui.text.font.FontStyle
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.text.style.TextAlign
import androidx.compose.ui.text.style.TextOverflow
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import coil.compose.AsyncImage
import com.example.model.CastMemberItem
import com.example.model.CommentItem
import com.example.model.MediaType
import com.example.model.VideoItem
import com.example.model.isUnreleased
import com.example.ui.theme.YTBlueVerified
import com.example.ui.theme.YouTubeRed
import com.example.util.ImagePreset
import com.example.util.rememberOptimizedImageRequest
import java.util.Locale

@OptIn(ExperimentalLayoutApi::class)
@Composable
fun VideoWatchDetails(
    video: VideoItem,
    isLiked: Boolean,
    isDisliked: Boolean,
    isSubscribed: Boolean,
    isSaved: Boolean,
    topComment: CommentItem?,
    onToggleLike: () -> Unit,
    onToggleDislike: () -> Unit,
    onToggleSubscribe: () -> Unit,
    onToggleSave: () -> Unit,
    onOpenComments: () -> Unit,
    onOpenServerDialog: () -> Unit,
    onOpenQueue: () -> Unit = {},
    onOpenChannel: ((String) -> Unit)? = null,
    onPlayNextEpisode: (() -> Unit)? = null,
    isReleaseAlertActive: Boolean = false,
    onToggleReleaseAlert: () -> Unit = {},
    isDownloaded: Boolean = false,
    downloadProgress: Int? = null,
    onDownloadClick: (() -> Unit)? = null,
    modifier: Modifier = Modifier
) {
    val context = LocalContext.current
    var isDescriptionExpanded by remember { mutableStateOf(false) }
    val commentAvatarRequest = rememberOptimizedImageRequest(
        data = topComment?.avatarUrl,
        preset = ImagePreset.AVATAR
    )

    val ratingValue = video.rating
    val formattedRating = ratingValue
        ?.takeIf { it > 0 }
        ?.let { String.format(Locale.US, "%.1f", it) }
    val isUpcoming = isUnreleased(video.releaseDateIso ?: video.releaseDateFormatted)

    Column(
        modifier = modifier
            .fillMaxWidth()
            .padding(horizontal = 14.dp, vertical = 10.dp)
    ) {
        // Keep the Watch page compact; the title is the description toggle.
        Row(
            modifier = Modifier
                .fillMaxWidth()
                .clip(RoundedCornerShape(8.dp))
                .clickable { isDescriptionExpanded = !isDescriptionExpanded }
                .padding(vertical = 2.dp)
                .testTag("video_title_toggle"),
            verticalAlignment = Alignment.CenterVertically
        ) {
            Text(
                text = video.title,
                modifier = Modifier.weight(1f),
                fontSize = 18.sp,
                fontWeight = FontWeight.SemiBold,
                color = MaterialTheme.colorScheme.onBackground,
                lineHeight = 24.sp,
                letterSpacing = (-0.2).sp,
                maxLines = 2,
                overflow = TextOverflow.Ellipsis
            )
            if (!formattedRating.isNullOrBlank()) {
                Spacer(modifier = Modifier.width(6.dp))
                ImdbRatingBadge(formattedRating)
            }
            Spacer(modifier = Modifier.width(6.dp))
            Icon(
                imageVector = if (isDescriptionExpanded) Icons.Default.KeyboardArrowUp else Icons.Default.KeyboardArrowDown,
                contentDescription = if (isDescriptionExpanded) "Collapse description" else "Open description",
                tint = MaterialTheme.colorScheme.onSurfaceVariant,
                modifier = Modifier.size(22.dp)
            )
        }

        Spacer(modifier = Modifier.height(8.dp))

        // Rich information is rendered only after the title is tapped.
        if (isDescriptionExpanded) {
            Box(
                modifier = Modifier
                    .fillMaxWidth()
                    .clip(RoundedCornerShape(12.dp))
                    .background(MaterialTheme.colorScheme.surfaceContainerLow.copy(alpha = 0.9f))
                    .border(
                        width = 1.dp,
                        color = MaterialTheme.colorScheme.outline.copy(alpha = 0.12f),
                        shape = RoundedCornerShape(12.dp)
                    )
                    .padding(12.dp)
                    .animateContentSize()
                    .testTag("video_description_card")
            ) {
            Column {
                // Header Bar with Quick Stats & Ratings
                Row(
                    verticalAlignment = Alignment.CenterVertically,
                    horizontalArrangement = Arrangement.spacedBy(8.dp),
                    modifier = Modifier.fillMaxWidth()
                ) {
                    if (video.views.isNotBlank()) {
                        Text(
                            text = video.views,
                            fontSize = 12.sp,
                            fontWeight = FontWeight.Bold,
                            color = MaterialTheme.colorScheme.onBackground
                        )
                    }

                    if (video.publishedAt.isNotBlank()) {
                        Text(
                            text = video.publishedAt,
                            fontSize = 12.sp,
                            fontWeight = FontWeight.Bold,
                            color = MaterialTheme.colorScheme.onBackground
                        )
                    }

                    if (video.mediaType == MediaType.TV_SHOW) {
                        Box(
                            modifier = Modifier
                                .clip(RoundedCornerShape(4.dp))
                                .background(YouTubeRed.copy(alpha = 0.08f))
                                .padding(horizontal = 5.dp, vertical = 1.dp)
                        ) {
                            Text(
                                text = "TV SERIES",
                                fontSize = 10.sp,
                                fontWeight = FontWeight.Bold,
                                color = YouTubeRed.copy(alpha = 0.62f)
                            )
                        }
                    }

                    Spacer(modifier = Modifier.weight(1f))

                    Icon(
                        imageVector = if (isDescriptionExpanded) Icons.Default.KeyboardArrowUp else Icons.Default.KeyboardArrowDown,
                        contentDescription = if (isDescriptionExpanded) "Collapse" else "Expand",
                        tint = MaterialTheme.colorScheme.onSurfaceVariant,
                        modifier = Modifier.size(20.dp)
                    )
                }

                Spacer(modifier = Modifier.height(6.dp))

                // Short summary preview or full rich details
                if (!isDescriptionExpanded) {
                    Text(
                        text = video.description,
                        fontSize = 12.sp,
                        color = MaterialTheme.colorScheme.onSurfaceVariant,
                        maxLines = 2,
                        overflow = TextOverflow.Ellipsis,
                        lineHeight = 17.sp
                    )

                    Row(
                        modifier = Modifier.padding(top = 4.dp),
                        verticalAlignment = Alignment.CenterVertically
                    ) {
                        Text(
                            text = "...more info & cast",
                            fontSize = 12.sp,
                            fontWeight = FontWeight.Bold,
                            color = MaterialTheme.colorScheme.primary
                        )
                    }
                } else {
                    // Full Expanded Clean Cinema Info Sheet
                    Column(modifier = Modifier.fillMaxWidth()) {
                        // Tagline if present
                        if (!video.tagline.isNullOrBlank()) {
                            Text(
                                text = "\"${video.tagline}\"",
                                fontSize = 13.sp,
                                fontStyle = FontStyle.Italic,
                                fontWeight = FontWeight.Medium,
                                color = MaterialTheme.colorScheme.onSurfaceVariant.copy(alpha = 0.9f),
                                modifier = Modifier.padding(vertical = 4.dp)
                            )
                        }

                        // Full Synopsis
                        Text(
                            text = video.description,
                            fontSize = 13.sp,
                            color = MaterialTheme.colorScheme.onSurface,
                            lineHeight = 18.sp,
                            modifier = Modifier.padding(vertical = 4.dp)
                        )

                        Spacer(modifier = Modifier.height(10.dp))
                        HorizontalDivider(color = MaterialTheme.colorScheme.outline.copy(alpha = 0.15f))
                        Spacer(modifier = Modifier.height(10.dp))

                        // Movie & Series Metadata Details Section
                        Text(
                            text = "Production & Details",
                            fontSize = 13.sp,
                            fontWeight = FontWeight.Bold,
                            color = MaterialTheme.colorScheme.onBackground
                        )
                        Spacer(modifier = Modifier.height(6.dp))

                        // Info rows
                        if (!video.director.isNullOrBlank()) {
                            MetadataRow(label = "Director", value = video.director)
                        }
                        if (video.creators.isNotEmpty()) {
                            MetadataRow(label = "Created By", value = video.creators.joinToString(", "))
                        }
                        if (video.writers.isNotEmpty()) {
                            MetadataRow(label = "Writers", value = video.writers.joinToString(", "))
                        }
                        if (video.genres.isNotEmpty()) {
                            MetadataRow(label = "Genres", value = video.genres.joinToString(" • "))
                        }
                        if (!video.releaseDateFormatted.isNullOrBlank()) {
                            MetadataRow(label = "Release Date", value = video.releaseDateFormatted)
                        }
                        if (video.mediaType == MediaType.TV_SHOW &&
                            (video.totalSeasons > 0 || video.totalEpisodes > 0)
                        ) {
                            MetadataRow(
                                label = "Seasons / Episodes",
                                value = "${video.totalSeasons} Seasons • ${video.totalEpisodes} Episodes"
                            )
                        } else if (video.runtimeMinutes != null && video.runtimeMinutes > 0) {
                            val hrs = video.runtimeMinutes / 60
                            val mins = video.runtimeMinutes % 60
                            val runtimeStr = if (hrs > 0) "${hrs}h ${mins}m (${video.runtimeMinutes} min)" else "${mins} min"
                            MetadataRow(label = "Runtime", value = runtimeStr)
                        }
                        if (video.networks.isNotEmpty()) {
                            MetadataRow(label = "Network", value = video.networks.joinToString(", "))
                        }
                        if (video.productionCompanies.isNotEmpty()) {
                            MetadataRow(label = "Studios", value = video.productionCompanies.joinToString(", "))
                        }
                        if (!video.status.isNullOrBlank()) {
                            MetadataRow(label = "Status", value = video.status)
                        }
                        if (!video.budgetFormatted.isNullOrBlank()) {
                            MetadataRow(label = "Budget", value = video.budgetFormatted)
                        }
                        if (!video.revenueFormatted.isNullOrBlank()) {
                            MetadataRow(label = "Box Office", value = video.revenueFormatted)
                        }
                        // Cast & Actors Carousel
                        if (video.cast.isNotEmpty()) {
                            Spacer(modifier = Modifier.height(12.dp))
                            HorizontalDivider(color = MaterialTheme.colorScheme.outline.copy(alpha = 0.15f))
                            Spacer(modifier = Modifier.height(10.dp))

                            Row(
                                verticalAlignment = Alignment.CenterVertically,
                                horizontalArrangement = Arrangement.SpaceBetween,
                                modifier = Modifier.fillMaxWidth()
                            ) {
                                Text(
                                    text = "Cast & Actors",
                                    fontSize = 13.sp,
                                    fontWeight = FontWeight.Bold,
                                    color = MaterialTheme.colorScheme.onBackground
                                )
                                Text(
                                    text = "${video.cast.size} actors",
                                    fontSize = 11.sp,
                                    color = MaterialTheme.colorScheme.onSurfaceVariant
                                )
                            }

                            Spacer(modifier = Modifier.height(8.dp))

                            LazyRow(
                                modifier = Modifier.fillMaxWidth(),
                                horizontalArrangement = Arrangement.spacedBy(10.dp)
                            ) {
                                items(
                                    items = video.cast,
                                    key = { it.name + "_" + (it.character ?: "") },
                                    contentType = { "actor_card" }
                                ) { castMember ->
                                    ActorCard(castMember = castMember)
                                }
                            }
                        }

                        // Genres Tag Chips
                        if (video.genres.isNotEmpty()) {
                            Spacer(modifier = Modifier.height(10.dp))
                            FlowRow(
                                horizontalArrangement = Arrangement.spacedBy(6.dp),
                                verticalArrangement = Arrangement.spacedBy(6.dp)
                            ) {
                                video.genres.forEach { genre ->
                                    Box(
                                        modifier = Modifier
                                            .clip(RoundedCornerShape(12.dp))
                                            .background(MaterialTheme.colorScheme.surfaceVariant)
                                            .border(
                                                width = 1.dp,
                                                color = MaterialTheme.colorScheme.outline.copy(alpha = 0.2f),
                                                shape = RoundedCornerShape(12.dp)
                                            )
                                            .padding(horizontal = 8.dp, vertical = 4.dp)
                                    ) {
                                        Text(
                                            text = genre,
                                            fontSize = 11.sp,
                                            fontWeight = FontWeight.Medium,
                                            color = MaterialTheme.colorScheme.onSurfaceVariant
                                        )
                                    }
                                }
                            }
                        }

                        Spacer(modifier = Modifier.height(10.dp))

                        // Show Less Button
                        Row(
                            modifier = Modifier
                                .fillMaxWidth()
                                .padding(top = 4.dp),
                            horizontalArrangement = Arrangement.Center
                        ) {
                            Text(
                                text = "Show less ▲",
                                fontSize = 12.sp,
                                fontWeight = FontWeight.Bold,
                                color = MaterialTheme.colorScheme.primary
                            )
                        }
                    }
                }
            }
        }
        }

        Spacer(modifier = Modifier.height(12.dp))

        // Channel / Studio Profile Row
        Row(
            modifier = Modifier.fillMaxWidth(),
            verticalAlignment = Alignment.CenterVertically,
            horizontalArrangement = Arrangement.SpaceBetween
        ) {
            Row(
                verticalAlignment = Alignment.CenterVertically,
                modifier = Modifier
                    .weight(1f)
                    .clip(RoundedCornerShape(8.dp))
                    .clickable(enabled = onOpenChannel != null) {
                        onOpenChannel?.invoke(video.channelName)
                    }
                    .padding(vertical = 4.dp, horizontal = 2.dp)
                    .testTag("channel_info_row")
            ) {
                StudioLogoAvatar(
                    logoUrl = video.channelAvatarUrl,
                    contentDescription = video.channelName,
                    modifier = Modifier
                        .size(38.dp)
                        .border(
                            width = 1.dp,
                            color = MaterialTheme.colorScheme.outline.copy(alpha = 0.2f),
                            shape = CircleShape
                        )
                )

                Spacer(modifier = Modifier.width(10.dp))

                Column {
                    Row(verticalAlignment = Alignment.CenterVertically) {
                        Text(
                            text = video.channelName,
                            fontSize = 14.sp,
                            fontWeight = FontWeight.Medium,
                            color = MaterialTheme.colorScheme.onBackground,
                            maxLines = 1,
                            overflow = TextOverflow.Ellipsis
                        )
                        if (video.isVerified) {
                            Spacer(modifier = Modifier.width(3.dp))
                            Icon(
                                imageVector = Icons.Default.CheckCircle,
                                contentDescription = "Verified Studio",
                                tint = YTBlueVerified,
                                modifier = Modifier.size(13.dp)
                            )
                        }
                    }
                    Text(
                        text = if (video.mediaType == MediaType.TV_SHOW) "Official Series Network" else "Official Studio",
                        fontSize = 12.sp,
                        color = MaterialTheme.colorScheme.onSurfaceVariant
                    )
                }
            }

            // Subscribe Button
            ElevatedButton(
                onClick = onToggleSubscribe,
                colors = ButtonDefaults.elevatedButtonColors(
                    containerColor = if (isSubscribed) MaterialTheme.colorScheme.surfaceVariant else MaterialTheme.colorScheme.onBackground,
                    contentColor = if (isSubscribed) MaterialTheme.colorScheme.onBackground else MaterialTheme.colorScheme.background
                ),
                shape = RoundedCornerShape(20.dp),
                modifier = Modifier
                    .height(36.dp)
                    .testTag("subscribe_button")
            ) {
                if (isSubscribed) {
                    Icon(
                        imageVector = Icons.Default.NotificationsActive,
                        contentDescription = "Subscribed",
                        modifier = Modifier.size(15.dp)
                    )
                    Spacer(modifier = Modifier.width(4.dp))
                    Text(
                        text = "Subscribed",
                        fontSize = 13.sp,
                        fontWeight = FontWeight.Medium
                    )
                } else {
                    Text(
                        text = "Subscribe",
                        fontSize = 13.sp,
                        fontWeight = FontWeight.Medium
                    )
                }
            }
        }

        Spacer(modifier = Modifier.height(14.dp))

        // Horizontal Action Pills LazyRow
        LazyRow(
            modifier = Modifier.fillMaxWidth(),
            horizontalArrangement = Arrangement.spacedBy(8.dp),
            verticalAlignment = Alignment.CenterVertically
        ) {
            item(key = "action_segmented_like_dislike") {
                // Segmented Like / Dislike Pill
                Row(
                    modifier = Modifier
                        .clip(RoundedCornerShape(20.dp))
                        .background(MaterialTheme.colorScheme.surfaceContainerLow)
                        .padding(horizontal = 4.dp, vertical = 2.dp),
                    verticalAlignment = Alignment.CenterVertically
                ) {
                    // Like Button
                    Row(
                        modifier = Modifier
                            .clip(RoundedCornerShape(20.dp))
                            .clickable(onClick = onToggleLike)
                            .padding(horizontal = 10.dp, vertical = 6.dp)
                            .testTag("action_like"),
                        verticalAlignment = Alignment.CenterVertically
                    ) {
                        Icon(
                            imageVector = if (isLiked) Icons.Filled.ThumbUp else Icons.Outlined.ThumbUp,
                            contentDescription = "Like",
                            tint = if (isLiked) YouTubeRed else MaterialTheme.colorScheme.onSurfaceVariant,
                            modifier = Modifier.size(16.dp)
                        )
                        if (video.likesCount.isNotBlank()) {
                            Spacer(modifier = Modifier.width(6.dp))
                            Text(
                                text = video.likesCount,
                                fontSize = 12.sp,
                                fontWeight = FontWeight.Medium,
                                color = MaterialTheme.colorScheme.onSurfaceVariant
                            )
                        }
                    }

                    // Vertical Divider
                    Box(
                        modifier = Modifier
                            .width(1.dp)
                            .height(18.dp)
                            .background(MaterialTheme.colorScheme.outline.copy(alpha = 0.4f))
                    )

                    // Dislike Button
                    Box(
                        modifier = Modifier
                            .clip(RoundedCornerShape(20.dp))
                            .clickable(onClick = onToggleDislike)
                            .padding(horizontal = 10.dp, vertical = 6.dp)
                            .testTag("action_dislike"),
                        contentAlignment = Alignment.Center
                    ) {
                        Icon(
                            imageVector = if (isDisliked) Icons.Filled.ThumbDown else Icons.Outlined.ThumbDown,
                            contentDescription = "Dislike",
                            tint = if (isDisliked) YouTubeRed else MaterialTheme.colorScheme.onSurfaceVariant,
                            modifier = Modifier.size(16.dp)
                        )
                    }
                }
            }

            item(key = "action_share_pill") {
                ActionPill(
                    label = "Share",
                    icon = Icons.Default.Share,
                    onClick = {
                        val appLink = Uri.Builder()
                            .scheme("clutube")
                            .authority("watch")
                            .appendPath(video.id)
                            .appendQueryParameter("title", video.title)
                            .build()
                        val sendIntent = Intent().apply {
                            action = Intent.ACTION_SEND
                            putExtra(Intent.EXTRA_TEXT, "Watch '${video.title}' in CluTube: $appLink")
                            type = "text/plain"
                        }
                        val shareIntent = Intent.createChooser(sendIntent, null)
                        context.startActivity(shareIntent)
                    },
                    testTag = "action_share"
                )
            }

            item(key = "action_servers_pill") {
                ActionPill(
                    label = "Servers",
                    icon = Icons.Default.AutoAwesome,
                    iconTint = YouTubeRed,
                    onClick = onOpenServerDialog,
                    testTag = "action_servers"
                )
            }

            if (isUpcoming) {
                item(key = "action_release_alert_pill") {
                    ActionPill(
                        label = if (isReleaseAlertActive) "Notifying" else "Notify me",
                        icon = if (isReleaseAlertActive) {
                            Icons.Default.NotificationsActive
                        } else {
                            Icons.Default.NotificationsNone
                        },
                        iconTint = if (isReleaseAlertActive) YouTubeRed else null,
                        onClick = onToggleReleaseAlert,
                        testTag = "release_alert_toggle"
                    )
                }
            }

            item(key = "action_queue_pill") {
                ActionPill(
                    label = "Queue",
                    icon = Icons.Default.QueueMusic,
                    onClick = onOpenQueue,
                    testTag = "action_queue"
                )
            }

            item(key = "action_save_pill") {
                ActionPill(
                    label = if (isSaved) "Saved" else "Save",
                    icon = if (isSaved) Icons.Filled.Bookmark else Icons.Filled.BookmarkBorder,
                    iconTint = if (isSaved) YTBlueVerified else null,
                    onClick = onToggleSave,
                    testTag = "action_save"
                )
            }

            if (onDownloadClick != null) {
                item(key = "action_download_pill") {
                    val downloadLabel = when {
                        isDownloaded -> "Downloaded"
                        downloadProgress != null -> "$downloadProgress%"
                        else -> "Download"
                    }
                    val downloadIcon = when {
                        isDownloaded -> Icons.Default.Check
                        else -> Icons.Default.Download
                    }
                    ActionPill(
                        label = downloadLabel,
                        icon = downloadIcon,
                        iconTint = if (isDownloaded) Color(0xFF4CAF50) else null,
                        onClick = onDownloadClick,
                        testTag = "action_download"
                    )
                }
            }

            // Keep Next EP beside the other actions, but make it the final
            // action so Save remains easy to find and the row reads naturally.
            if (video.mediaType == MediaType.TV_SHOW && onPlayNextEpisode != null) {
                item(key = "action_next_ep_pill") {
                    Box(
                        modifier = Modifier
                            .clip(RoundedCornerShape(20.dp))
                            .background(YouTubeRed)
                            .clickable { onPlayNextEpisode() }
                            .padding(horizontal = 12.dp, vertical = 6.dp)
                            .testTag("action_next_ep"),
                        contentAlignment = Alignment.Center
                    ) {
                        Row(verticalAlignment = Alignment.CenterVertically) {
                            Icon(
                                imageVector = Icons.Default.SkipNext,
                                contentDescription = "Next Episode",
                                tint = Color.White,
                                modifier = Modifier.size(16.dp)
                            )
                            Spacer(modifier = Modifier.width(4.dp))
                            Text(
                                text = "Next EP",
                                color = Color.White,
                                fontSize = 12.sp,
                                fontWeight = FontWeight.Bold
                            )
                        }
                    }
                }
            }

        }

        Spacer(modifier = Modifier.height(14.dp))

        // Comments Preview Card
        Box(
            modifier = Modifier
                .fillMaxWidth()
                .clip(RoundedCornerShape(12.dp))
                .background(MaterialTheme.colorScheme.surfaceContainerLow)
                .clickable { onOpenComments() }
                .padding(12.dp)
                .testTag("comments_preview_card")
        ) {
            Column {
                Row(
                    modifier = Modifier.fillMaxWidth(),
                    horizontalArrangement = Arrangement.SpaceBetween,
                    verticalAlignment = Alignment.CenterVertically
                ) {
                    Row(verticalAlignment = Alignment.CenterVertically) {
                        Text(
                            text = "Comments",
                            fontSize = 13.sp,
                            fontWeight = FontWeight.Bold,
                            color = MaterialTheme.colorScheme.onSurfaceVariant
                        )
                        if (video.commentsCount.isNotBlank()) {
                            Spacer(modifier = Modifier.width(6.dp))
                            Text(
                                text = video.commentsCount,
                                fontSize = 12.sp,
                                color = MaterialTheme.colorScheme.onSurfaceVariant
                            )
                        }
                    }
                    Text(
                        text = "View all",
                        fontSize = 11.sp,
                        color = YTBlueVerified,
                        fontWeight = FontWeight.SemiBold
                    )
                }

                if (topComment != null) {
                    Spacer(modifier = Modifier.height(8.dp))
                    Row(verticalAlignment = Alignment.Top) {
                        AsyncImage(
                            model = commentAvatarRequest,
                            contentDescription = topComment.author,
                            contentScale = ContentScale.Crop,
                            modifier = Modifier
                                .size(24.dp)
                                .clip(CircleShape)
                        )
                        Spacer(modifier = Modifier.width(8.dp))
                        Text(
                            text = topComment.text,
                            fontSize = 12.sp,
                            color = MaterialTheme.colorScheme.onSurfaceVariant,
                            maxLines = 2,
                            overflow = TextOverflow.Ellipsis,
                            lineHeight = 16.sp
                        )
                    }
                }
            }
        }
    }
}

@Composable
private fun MetadataRow(label: String, value: String) {
    Row(
        modifier = Modifier
            .fillMaxWidth()
            .padding(vertical = 2.dp),
        verticalAlignment = Alignment.Top
    ) {
        Text(
            text = "$label: ",
            fontSize = 12.sp,
            fontWeight = FontWeight.Bold,
            color = MaterialTheme.colorScheme.onSurfaceVariant,
            modifier = Modifier.width(110.dp)
        )
        Text(
            text = value,
            fontSize = 12.sp,
            color = MaterialTheme.colorScheme.onSurface,
            modifier = Modifier.weight(1f)
        )
    }
}

@Composable
private fun ActorCard(castMember: CastMemberItem) {
    val avatarRequest = rememberOptimizedImageRequest(
        data = castMember.avatarUrl,
        preset = ImagePreset.AVATAR
    )

    Column(
        modifier = Modifier
            .width(76.dp)
            .clip(RoundedCornerShape(8.dp))
            .background(MaterialTheme.colorScheme.surface.copy(alpha = 0.6f))
            .padding(6.dp),
        horizontalAlignment = Alignment.CenterHorizontally
    ) {
        if (castMember.avatarUrl != null) {
            AsyncImage(
                model = avatarRequest,
                contentDescription = castMember.name,
                contentScale = ContentScale.Crop,
                modifier = Modifier
                    .size(54.dp)
                    .clip(CircleShape)
                    .border(
                        width = 1.dp,
                        color = MaterialTheme.colorScheme.outline.copy(alpha = 0.2f),
                        shape = CircleShape
                    )
            )
        } else {
            Box(
                modifier = Modifier
                    .size(54.dp)
                    .clip(CircleShape)
                    .background(MaterialTheme.colorScheme.surfaceVariant),
                contentAlignment = Alignment.Center
            ) {
                Icon(
                    imageVector = Icons.Default.Person,
                    contentDescription = castMember.name,
                    tint = MaterialTheme.colorScheme.onSurfaceVariant,
                    modifier = Modifier.size(30.dp)
                )
            }
        }

        Spacer(modifier = Modifier.height(4.dp))

        Text(
            text = castMember.name,
            fontSize = 11.sp,
            fontWeight = FontWeight.Bold,
            color = MaterialTheme.colorScheme.onBackground,
            maxLines = 2,
            overflow = TextOverflow.Ellipsis,
            textAlign = TextAlign.Center,
            lineHeight = 13.sp
        )

        if (castMember.character.isNotBlank()) {
            Text(
                text = castMember.character,
                fontSize = 9.sp,
                color = MaterialTheme.colorScheme.onSurfaceVariant,
                maxLines = 2,
                overflow = TextOverflow.Ellipsis,
                textAlign = TextAlign.Center,
                lineHeight = 11.sp
            )
        }
    }
}

@Composable
private fun ActionPill(
    label: String,
    icon: ImageVector,
    iconTint: Color? = null,
    onClick: () -> Unit,
    testTag: String
) {
    Row(
        modifier = Modifier
            .clip(RoundedCornerShape(20.dp))
            .background(MaterialTheme.colorScheme.surfaceContainerLow)
            .clickable { onClick() }
            .padding(horizontal = 12.dp, vertical = 7.dp)
            .testTag(testTag),
        verticalAlignment = Alignment.CenterVertically
    ) {
        Icon(
            imageVector = icon,
            contentDescription = label,
            tint = iconTint ?: MaterialTheme.colorScheme.onSurfaceVariant,
            modifier = Modifier.size(16.dp)
        )
        Spacer(modifier = Modifier.width(6.dp))
        Text(
            text = label,
            fontSize = 12.sp,
            fontWeight = FontWeight.Medium,
            color = MaterialTheme.colorScheme.onSurfaceVariant
        )
    }
}

/* @Composable
private fun PlaybackPreferencesMenu(
    preferences: PlaybackPreferences,
    onQualitySelected: (PlaybackQuality) -> Unit,
    onSubtitleSelected: (SubtitlePreference) -> Unit
) {
    var expanded by remember { mutableStateOf(false) }
    Box {
        ActionPill(
            label = "${preferences.quality.wireValue} · ${preferences.subtitles.wireValue}",
            icon = Icons.Default.Settings,
            onClick = { expanded = true },
            testTag = "playback_preferences_button"
        )
        DropdownMenu(
            expanded = expanded,
            onDismissRequest = { expanded = false }
        ) {
            Text(
                text = "Quality",
                modifier = Modifier.padding(horizontal = 16.dp, vertical = 6.dp),
                fontSize = 12.sp,
                fontWeight = FontWeight.Bold,
                color = MaterialTheme.colorScheme.onSurfaceVariant
            )
            PlaybackQuality.values().forEach { quality ->
                DropdownMenuItem(
                    text = { Text(quality.wireValue) },
                    onClick = {
                        expanded = false
                        onQualitySelected(quality)
                    },
                    trailingIcon = if (quality == preferences.quality) {
                        { Text("✓", color = YouTubeRed) }
                    } else null
                )
            }
            Text(
                text = "Subtitles",
                modifier = Modifier.padding(horizontal = 16.dp, vertical = 6.dp),
                fontSize = 12.sp,
                fontWeight = FontWeight.Bold,
                color = MaterialTheme.colorScheme.onSurfaceVariant
            )
            SubtitlePreference.values().forEach { subtitles ->
                DropdownMenuItem(
                    text = {
                        Text(
                            when (subtitles) {
                                SubtitlePreference.OFF -> "Off"
                                SubtitlePreference.AUTO -> "Auto"
                                SubtitlePreference.ENGLISH -> "English"
                                SubtitlePreference.SPANISH -> "Spanish"
                            }
                        )
                    },
                    onClick = {
                        expanded = false
                        onSubtitleSelected(subtitles)
                    },
                    trailingIcon = if (subtitles == preferences.subtitles) {
                        { Text("✓", color = YouTubeRed) }
                    } else null
                )
            }
        }
    }
} */
