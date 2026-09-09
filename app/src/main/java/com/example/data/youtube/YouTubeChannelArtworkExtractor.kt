package com.example.data.youtube

import com.example.model.ChannelItem
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.withContext
import org.schabi.newpipe.extractor.ServiceList
import org.schabi.newpipe.extractor.channel.ChannelExtractor

/**
 * Resolves the artwork actually published by a YouTube channel. TMDB remains
 * the catalog source, while NewPipe Extractor supplies channel-owned avatars
 * and banners when YouTube exposes them.
 *
 * HTTP + NewPipe bootstrap lives in [NewPipeClient], shared with the trailer
 * view-count extractor so both surfaces never double-init.
 */
object YouTubeChannelArtworkExtractor {

    data class Artwork(
        val avatarUrl: String? = null,
        val bannerUrl: String? = null
    )

    suspend fun fetch(channelUrl: String): Result<Artwork> = withContext(Dispatchers.IO) {
        runCatching {
            NewPipeClient.ensureInitialized()
            val linkHandler = ServiceList.YouTube.getChannelLHFactory().fromUrl(channelUrl)
            val extractor: ChannelExtractor = ServiceList.YouTube.getChannelExtractor(linkHandler)
            extractor.fetchPage()

            Artwork(
                // Prefer YouTube's circular-avatar CDN variant when it is
                // available. This is the channel-owned artwork, not a TMDB
                // company logo or a generated fallback.
                avatarUrl = bestAvatarUrl(extractor.avatars),
                bannerUrl = extractor.banners
                    .maxByOrNull(::imageScore)
                    ?.url
                    ?.takeIf(String::isNotBlank)
            )
        }
    }

    fun bestImageUrl(images: List<org.schabi.newpipe.extractor.Image>): String? =
        images.maxByOrNull(::imageScore)?.url?.takeIf(String::isNotBlank)

    private fun bestAvatarUrl(images: List<org.schabi.newpipe.extractor.Image>): String? {
        val youtubeImages = images.filter { image ->
            image.url.contains("yt3.googleusercontent.com", ignoreCase = true) ||
                image.url.contains("yt3.ggpht.com", ignoreCase = true)
        }
        val candidates = youtubeImages.ifEmpty { images }
        return candidates
            .maxWithOrNull(
                compareBy<org.schabi.newpipe.extractor.Image> {
                    if (isCircularAvatarUrl(it.url)) 1 else 0
                }.thenBy(::imageScore)
            )
            ?.url
            ?.takeIf(String::isNotBlank)
            ?.let(::requestCircularAvatar)
    }

    private fun isCircularAvatarUrl(url: String): Boolean =
        Regex("=s\\d+-c(?:[-?&#]|$)", RegexOption.IGNORE_CASE).containsMatchIn(url)

    /** Ask the YouTube CDN for its current high-resolution circular crop. */
    private fun requestCircularAvatar(url: String): String {
        if (!url.contains("yt3.", ignoreCase = true)) return url
        val sizeMarker = Regex("=s\\d+(?:-[^?&#]*)?", RegexOption.IGNORE_CASE)
        return if (sizeMarker.containsMatchIn(url)) {
            sizeMarker.replace(url, "=s900-c-k-c0x00ffffff-no-rj")
        } else {
            url
        }
    }

    private fun imageScore(image: org.schabi.newpipe.extractor.Image): Long {
        val width = image.width.takeIf { it > 0 } ?: 0
        val height = image.height.takeIf { it > 0 } ?: 0
        return width.toLong() * height.toLong()
    }
}
