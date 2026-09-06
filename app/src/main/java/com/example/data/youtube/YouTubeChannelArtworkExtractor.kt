package com.example.data.youtube

import com.example.model.ChannelItem
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.withContext
import okhttp3.OkHttpClient
import okhttp3.Request as OkHttpRequest
import org.schabi.newpipe.extractor.NewPipe
import org.schabi.newpipe.extractor.ServiceList
import org.schabi.newpipe.extractor.channel.ChannelExtractor
import org.schabi.newpipe.extractor.downloader.Downloader
import org.schabi.newpipe.extractor.downloader.Request as ExtractorRequest
import org.schabi.newpipe.extractor.downloader.Response as ExtractorResponse
import org.schabi.newpipe.extractor.exceptions.ReCaptchaException
import java.io.IOException
import java.util.concurrent.TimeUnit

/**
 * Resolves the artwork actually published by a YouTube channel. TMDB remains
 * the catalog source, while NewPipe Extractor supplies channel-owned avatars
 * and banners when YouTube exposes them.
 */
object YouTubeChannelArtworkExtractor {

    data class Artwork(
        val avatarUrl: String? = null,
        val bannerUrl: String? = null
    )

    private val httpClient = OkHttpClient.Builder()
        .followRedirects(true)
        .followSslRedirects(true)
        .connectTimeout(15, TimeUnit.SECONDS)
        .readTimeout(20, TimeUnit.SECONDS)
        .callTimeout(30, TimeUnit.SECONDS)
        .build()

    @Volatile
    private var isInitialized = false

    private fun ensureInitialized() {
        if (isInitialized) return
        synchronized(this) {
            if (!isInitialized) {
                NewPipe.init(OkHttpDownloader(httpClient))
                isInitialized = true
            }
        }
    }

    suspend fun fetch(channelUrl: String): Result<Artwork> = withContext(Dispatchers.IO) {
        runCatching {
            ensureInitialized()
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

    private class OkHttpDownloader(
        private val client: OkHttpClient
    ) : Downloader() {
        override fun execute(request: ExtractorRequest): ExtractorResponse {
            val requestHeaders = request.headers()
            val builder = OkHttpRequest.Builder()
                .url(request.url())
                .apply {
                    if (requestHeaders.keys.none { it.equals("User-Agent", ignoreCase = true) }) {
                        header(
                            "User-Agent",
                            "Mozilla/5.0 (Linux; Android 13) AppleWebKit/537.36 " +
                                "(KHTML, like Gecko) Chrome/124.0 Mobile Safari/537.36"
                        )
                    }
                    if (requestHeaders.keys.none { it.equals("Accept-Language", ignoreCase = true) }) {
                        header("Accept-Language", "en-US,en;q=0.9")
                    }
                }
            requestHeaders.forEach { (name, values) ->
                values.forEach { value -> builder.addHeader(name, value) }
            }

            val body = request.dataToSend()?.let { bytes ->
                okhttp3.RequestBody.create(null, bytes)
            }
            val httpRequest = when (request.httpMethod().uppercase()) {
                "POST" -> builder.post(body ?: okhttp3.RequestBody.create(null, ByteArray(0))).build()
                "HEAD" -> builder.head().build()
                else -> builder.get().build()
            }

            return client.newCall(httpRequest).execute().use { response ->
                ExtractorResponse(
                    response.code,
                    response.message,
                    response.headers.toMultimap(),
                    response.body?.string().orEmpty(),
                    response.request.url.toString()
                )
            }
        }
    }
}
