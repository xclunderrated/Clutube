package com.example.model

import androidx.compose.runtime.Immutable

@Immutable
enum class MediaType {
    VIDEO,
    MOVIE,
    TV_SHOW,
    TRAILER,
    LIVESTREAM
}

@Immutable
data class CastMemberItem(
    val id: Int,
    val name: String,
    val character: String = "",
    val avatarUrl: String? = null
)

@Immutable
data class VideoItem(
    val id: String,
    val title: String,
    val description: String,
    val channelName: String,
    val channelHandle: String = "@${channelName.lowercase().replace(" ", "")}",
    val channelAvatarUrl: String,
    val channelSubscribers: String = "",
    /** Real view totals are not available from the catalog APIs used here. */
    val views: String = "",
    val publishedAt: String,
    val duration: String,
    val thumbnailUrl: String,
    val posterUrl: String? = null,
    val backdropUrl: String? = null,
    val episodeStillUrl: String? = null,
    val streamUrl: String = "",
    val embedStreamUrl: String = "",
    val mediaType: MediaType = MediaType.VIDEO,
    val likesCount: String = "",
    val commentsCount: String = "",
    val category: String = "All",
    val isVerified: Boolean = true,
    val isLiked: Boolean = false,
    val isDisliked: Boolean = false,
    val isSubscribed: Boolean = false,
    val isSaved: Boolean = false,
    val tags: List<String> = emptyList(),
    val tmdbId: String? = null,
    val imdbId: String? = null,
    val currentSeason: Int = 1,
    val currentEpisode: Int = 1,
    val totalSeasons: Int = 1,
    val totalEpisodes: Int = 1,
    val rating: Double? = null,
    val voteCount: Int? = null,
    val genres: List<String> = emptyList(),
    val director: String? = null,
    val writers: List<String> = emptyList(),
    val creators: List<String> = emptyList(),
    val cast: List<CastMemberItem> = emptyList(),
    val tagline: String? = null,
    val status: String? = null,
    val runtimeMinutes: Int? = null,
    /** Raw ISO date used for release-alert scheduling; UI uses the formatted field. */
    val releaseDateIso: String? = null,
    val releaseDateFormatted: String? = null,
    val budgetFormatted: String? = null,
    val revenueFormatted: String? = null,
    val productionCompanies: List<String> = emptyList(),
    val networks: List<String> = emptyList()
)

@Immutable
data class ShortItem(
    val id: String,
    val title: String,
    val channelName: String,
    val channelAvatarUrl: String,
    val likesCount: String,
    val commentsCount: String,
    val soundTrack: String,
    val videoStreamUrl: String,
    val thumbnailUrl: String,
    val isSubscribed: Boolean = false,
    val isLiked: Boolean = false,
    /** YouTube trailer key when this Short is a catalog trailer. */
    val trailerVideoId: String? = null,
    /** The catalog title opened by the Shorts "Watch Now" action. */
    val mediaItem: VideoItem? = null
)

@Immutable
data class CommentItem(
    val id: String,
    val author: String,
    val avatarUrl: String,
    val timeAgo: String,
    val text: String,
    val likes: String = "0",
    val isLiked: Boolean = false,
    val isHeartedByCreator: Boolean = false,
    val replyCount: Int = 0
)

@Immutable
data class ChannelItem(
    val id: String,
    val name: String,
    val handle: String,
    val avatarUrl: String,
    val bannerUrl: String? = null,
    val subscribers: String = "",
    val videosCount: String = "",
    val description: String = "",
    val hasNewStory: Boolean = false,
    val isSubscribed: Boolean = false,
    val joinedDate: String = "",
    /** Channel view totals are omitted until the backend can provide real values. */
    val totalViews: String = "",
    val location: String = ""
)

@Immutable
data class StreamServer(
    val id: String,
    val name: String,
    val provider: String,
    val quality: String,
    val urlTemplate: String,
    val isRecommended: Boolean = false
)
