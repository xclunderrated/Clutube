package com.example.data.tmdb

import android.util.Log
import com.example.data.StreamService
import com.example.model.CastMemberItem
import com.example.model.ChannelItem
import com.example.model.CommentItem
import com.example.model.MediaType
import com.example.model.ShortItem
import com.example.model.VideoItem
import com.example.data.youtube.YouTubeChannelArtworkExtractor
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.async
import kotlinx.coroutines.awaitAll
import kotlinx.coroutines.coroutineScope
import kotlinx.coroutines.withContext
import java.util.Locale
import java.util.concurrent.ConcurrentHashMap

object TmdbRepository {

    private val api = TmdbClient.apiService

    private val logoCache = ConcurrentHashMap<Int, String>()
    private val channelArtworkCache = ConcurrentHashMap<String, YouTubeChannelArtworkExtractor.Artwork>()

    fun getCachedLogo(id: Int): String? = logoCache[id]

    suspend fun warmLogoCache() = withContext(Dispatchers.IO) {
        STUDIO_CHANNELS.forEach { studio ->
            studio.tmdbCompanyId?.let { id ->
                runCatching {
                    val details = api.getCompanyDetails(id)
                    details.logoPath?.let { logoCache[id] = "${TmdbClient.IMAGE_BASE_W500}$it" }
                }
            }
            studio.tmdbNetworkId?.let { id ->
                runCatching {
                    val details = api.getNetworkDetails(id)
                    details.logoPath?.let { logoCache[id] = "${TmdbClient.IMAGE_BASE_W500}$it" }
                }
            }
        }
        // TMDB identifies the studio behind a title, but it does not own the
        // YouTube channel artwork. Resolve the channel's current avatar and
        // banner through NewPipe Extractor, then keep the catalog fallbacks.
        coroutineScope {
            STUDIO_CHANNELS.map { studio ->
                async {
                    val url = studio.youtubeChannelUrl ?: return@async
                    YouTubeChannelArtworkExtractor.fetch(url)
                        .onSuccess { artwork ->
                            if (artwork.avatarUrl != null || artwork.bannerUrl != null) {
                                channelArtworkCache[studio.name] = artwork
                            }
                        }
                        .onFailure { error ->
                            Log.w("TmdbRepository", "Studio artwork refresh failed for ${studio.name}: ${error.message}")
                        }
                }
            }.awaitAll()
        }
    }

    private fun logoUrlForIds(companyId: Int?, networkId: Int?): String? {
        val studio = STUDIO_CHANNELS.firstOrNull {
            it.tmdbCompanyId == companyId || it.tmdbNetworkId == networkId
        }
        studio?.let { channelArtworkCache[it.name]?.avatarUrl?.let { url -> return url } }
        if (networkId != null) logoCache[networkId]?.let { return it }
        if (companyId != null) logoCache[companyId]?.let { return it }
        return studio?.avatar
    }

    private fun channelAvatarFor(studio: StudioInfo): String {
        return channelArtworkCache[studio.name]?.avatarUrl
            ?: logoUrlForIds(studio.tmdbCompanyId, studio.tmdbNetworkId)
            ?: studio.avatar
    }

    fun applyCachedChannelArtwork(video: VideoItem): VideoItem {
        val studio = STUDIO_CHANNELS.firstOrNull {
            it.name.equals(video.channelName, ignoreCase = true) ||
                video.channelName.contains(it.searchQuery, ignoreCase = true) ||
                it.name.contains(video.channelName, ignoreCase = true)
        } ?: return video
        val artwork = channelArtworkCache[studio.name] ?: return video
        return video.copy(
            channelAvatarUrl = artwork.avatarUrl ?: video.channelAvatarUrl
        )
    }

    fun applyCachedChannelArtwork(short: ShortItem): ShortItem {
        val studio = STUDIO_CHANNELS.firstOrNull {
            it.name.equals(short.channelName, ignoreCase = true) ||
                short.channelName.contains(it.searchQuery, ignoreCase = true) ||
                it.name.contains(short.channelName, ignoreCase = true)
        } ?: return short
        val artwork = channelArtworkCache[studio.name] ?: return short
        return short.copy(
            channelAvatarUrl = artwork.avatarUrl ?: short.channelAvatarUrl
        )
    }

    val GENRE_MAP = mapOf(
        28 to "Action",
        12 to "Adventure",
        16 to "Animation",
        35 to "Comedy",
        80 to "Crime",
        99 to "Documentary",
        18 to "Drama",
        10751 to "Family",
        14 to "Fantasy",
        27 to "Horror",
        36 to "History",
        10402 to "Music",
        9648 to "Mystery",
        10749 to "Romance",
        878 to "Sci-Fi",
        10765 to "Sci-Fi & Fantasy",
        10759 to "Action & Adventure",
        53 to "Thriller",
        10752 to "War",
        37 to "Western"
    )

    val GENRE_NAME_TO_ID = mapOf(
        "Action" to 28,
        "Animation" to 16,
        "Comedy" to 35,
        "Drama" to 18,
        "Horror" to 27,
        "Sci-Fi" to 878,
        "Music" to 10402,
        "Documentary" to 99,
        "Crime" to 80,
        "Family" to 10751
    )

    data class StudioInfo(
        val name: String,
        val avatar: String,
        val subs: String,
        val banner: String,
        val description: String,
        val searchQuery: String,
        val tmdbCompanyId: Int? = null,
        val tmdbNetworkId: Int? = null,
        val youtubeChannelUrl: String? = null
    )

    private val STUDIO_CHANNELS = listOf(
        StudioInfo(
            name = "Warner Bros. Pictures",
            avatar = "https://yt3.googleusercontent.com/yVXKYrUI8hckCQdyUuOWf5ZJk2keT8WO3TV2b8RYk3RKgjz5Rh8v1UsH7Yz2j_hbDQRk32rZ_rM=s900-c-k-c0x00ffffff-no-rj",
            subs = "11.4M subscribers",
            banner = "https://images.unsplash.com/photo-1536440136628-849c177e76a1?w=1200&auto=format&fit=crop&q=80",
            description = "Official YouTube channel for Warner Bros. Pictures. Discover cinematic blockbusters, premier trailers, and exclusive behind-the-scenes content.",
            searchQuery = "Warner Bros",
            tmdbCompanyId = 174,
            youtubeChannelUrl = "https://www.youtube.com/@WarnerBrosPictures"
        ),
        StudioInfo(
            name = "Netflix",
            avatar = "https://yt3.googleusercontent.com/3b73AYEMMfa3SX5KJMeygio9smTPvrPrpicuQZbfQ_2DN7dV_ApiRM4CdYjSprEy1YYvt_9b=s900-c-k-c0x00ffffff-no-rj",
            subs = "28.5M subscribers",
            banner = "https://images.unsplash.com/photo-1574375927938-d5a98e8ffe85?w=1200&auto=format&fit=crop&q=80",
            description = "Welcome to the official Netflix channel. Watch full original series, movies, award-winning documentaries, and specials in 4K HDR.",
            searchQuery = "Netflix",
            tmdbNetworkId = 213,
            youtubeChannelUrl = "https://www.youtube.com/@Netflix"
        ),
        StudioInfo(
            name = "HBO",
            avatar = "https://yt3.googleusercontent.com/xrXPx6zj9lXDumnGmxo1BMS3NhyOjHaO3io8FXK1yqbojj3eMJpbJ-b92ODieH2hsY8KYuXs_w=s900-c-k-c0x00ffffff-no-rj",
            subs = "8.9M subscribers",
            banner = "https://images.unsplash.com/photo-1489599849927-2ee91cede3ba?w=1200&auto=format&fit=crop&q=80",
            description = "The home of HBO Originals, iconic dramas, groundbreaking series, and landmark cinema productions.",
            searchQuery = "HBO",
            tmdbCompanyId = 3268,
            tmdbNetworkId = 49,
            youtubeChannelUrl = "https://www.youtube.com/@HBO"
        ),
        StudioInfo(
            name = "Marvel Studios",
            avatar = "https://yt3.googleusercontent.com/k7BhK-hm9_MbJbaKznHPhir6e4pWXbm1ppAHoseLIzRgoAPBMmH1IIhYKlXbGono25RD1OQwHQ=s900-c-k-c0x00ffffff-no-rj",
            subs = "20.1M subscribers",
            banner = "https://images.unsplash.com/photo-1607604276583-eef5d076aa5f?w=1200&auto=format&fit=crop&q=80",
            description = "The official channel for Marvel Studios. Experience the Marvel Cinematic Universe with movies, series, and superhero legends.",
            searchQuery = "Marvel",
            tmdbCompanyId = 420,
            youtubeChannelUrl = "https://www.youtube.com/@Marvel"
        ),
        StudioInfo(
            name = "A24",
            avatar = "https://yt3.googleusercontent.com/37jAKnh2Yt05eT_ebynqYHNAQXUedv98drEAnFstVbHoi2c9wL2dFdeDgQDnX7R6bdBXsEXaRw=s900-c-k-c0x00ffffff-no-rj",
            subs = "3.6M subscribers",
            banner = "https://images.unsplash.com/photo-1534447677768-be436bb09401?w=1200&auto=format&fit=crop&q=80",
            description = "Independent film and television production studio known for visionary storytelling, indie masterpieces, and arthouse cinema.",
            searchQuery = "A24",
            tmdbCompanyId = 41077,
            youtubeChannelUrl = "https://www.youtube.com/@A24"
        ),
        StudioInfo(
            name = "Universal Pictures",
            avatar = "https://yt3.googleusercontent.com/PzIajPyy9_HJ-LR5S6q86JV9o2GjabXF54QJnr3PeHpOiGOXtroatGM3mB2QxQa_vkF5yuyu9hs=s900-c-k-c0x00ffffff-no-rj",
            subs = "9.2M subscribers",
            banner = "https://images.unsplash.com/photo-1518676590629-3dcbd9c5a5c9?w=1200&auto=format&fit=crop&q=80",
            description = "Official home of Universal Pictures. Watch action-packed thrillers, classic franchises, and world-class theatrical films.",
            searchQuery = "Universal",
            tmdbCompanyId = 33,
            youtubeChannelUrl = "https://www.youtube.com/@UniversalPictures"
        ),
        StudioInfo(
            name = "Paramount+",
            avatar = "https://yt3.googleusercontent.com/sb8Ha1xXC5Z2MmsAnucyBCKpJ6gjiHgqyKlk1X8Up7_aKD8LIBM3gNQ2D4bVrt3MG8wMe0y_sA=s900-c-k-c0x00ffffff-no-rj",
            subs = "5.4M subscribers",
            banner = "https://images.unsplash.com/photo-1485846234645-a62644f84728?w=1200&auto=format&fit=crop&q=80",
            description = "Stream iconic series, blockbuster movies, live sports, and original series on the official Paramount+ channel.",
            searchQuery = "Paramount",
            tmdbCompanyId = 4,
            tmdbNetworkId = 4330,
            youtubeChannelUrl = "https://www.youtube.com/@ParamountPlus"
        ),
        StudioInfo(
            name = "Apple TV",
            avatar = "https://yt3.googleusercontent.com/j80BliGp7lHWs89o2pAkm0Kv0R98sVASljijox5AsjYRZgovOFeyUekozb4_T8da1th7EI4Pyg=s900-c-k-c0x00ffffff-no-rj",
            subs = "7.1M subscribers",
            banner = "https://images.unsplash.com/photo-1534528741775-53994a69daeb?w=1200&auto=format&fit=crop&q=80",
            description = "Apple Original films and series from today's top storytellers. Watch premium dramas, comedies, and documentaries.",
            searchQuery = "Apple TV",
            tmdbNetworkId = 2552,
            youtubeChannelUrl = "https://www.youtube.com/@AppleTV"
        ),
        StudioInfo(
            name = "Disney+",
            avatar = "https://yt3.googleusercontent.com/UPhpnK8upp-dSOTsTLHh-oi4vybASrC5eppw1ud4NgMAvGEjFXaN46bcj-mu2IahKeW5nb2Ywg=s900-c-k-c0x00ffffff-no-rj",
            subs = "14.8M subscribers",
            banner = "https://images.unsplash.com/photo-1507003211169-0a1dd7228f2d?w=1200&auto=format&fit=crop&q=80",
            description = "The dedicated streaming home for movies and shows from Disney, Pixar, Marvel, Star Wars, National Geographic, and more.",
            searchQuery = "Disney",
            tmdbNetworkId = 2739,
            youtubeChannelUrl = "https://www.youtube.com/@Disney"
        )
    )

    suspend fun fetchFullMediaDetails(video: VideoItem): Result<VideoItem> = withContext(Dispatchers.IO) {
        runCatching {
            val tmdbIdInt = video.tmdbId?.toIntOrNull() ?: return@runCatching video
            val isTv = video.mediaType == MediaType.TV_SHOW

            if (isTv) {
                val details = api.getTvDetails(tmdbIdInt, appendToResponse = "credits")
                val primaryNetwork = details.networks?.firstOrNull { it.logoPath != null }
                    ?: details.networks?.firstOrNull()
                val primaryCompany = details.productionCompanies?.firstOrNull { it.logoPath != null }
                    ?: details.productionCompanies?.firstOrNull()
                val realNetworkName = primaryNetwork?.name
                    ?: primaryCompany?.name
                    ?: video.channelName

                val castList = details.credits?.cast?.take(20)?.map {
                    CastMemberItem(
                        id = it.id,
                        name = it.name,
                        character = it.character ?: "",
                        avatarUrl = if (it.profilePath != null) "${TmdbClient.IMAGE_BASE_W185}${it.profilePath}" else null
                    )
                } ?: emptyList()

                val creatorsList = details.createdBy?.map { it.name } ?: emptyList()
                val genresList = details.genres?.map { it.name } ?: emptyList()
                val prodCompanies = details.productionCompanies?.map { it.name } ?: emptyList()
                val networksList = details.networks?.map { it.name } ?: emptyList()

                details.networks?.forEach { net ->
                    net.logoPath?.let { logoCache[net.id] = "${TmdbClient.IMAGE_BASE_W500}$it" }
                }
                details.productionCompanies?.forEach { co ->
                    co.logoPath?.let { logoCache[co.id] = "${TmdbClient.IMAGE_BASE_W500}$it" }
                }

                val configuredStudioAvatar = logoUrlForIds(
                    companyId = primaryCompany?.id,
                    networkId = primaryNetwork?.id
                )
                val realChannelAvatarUrl = configuredStudioAvatar
                    ?: primaryNetwork?.logoPath
                    ?.let { "${TmdbClient.IMAGE_BASE_W500}$it" }
                    ?: primaryCompany?.logoPath
                        ?.let { "${TmdbClient.IMAGE_BASE_W500}$it" }
                    ?: video.channelAvatarUrl

                val officialPoster = details.posterPath?.takeIf { it.isNotBlank() }?.let { "${TmdbClient.IMAGE_BASE_W500}$it" }
                val sameTitleBackdrop = details.backdropPath?.takeIf { it.isNotBlank() }?.let { "${TmdbClient.IMAGE_BASE_W780}$it" }
                val updatedThumbnail = officialPoster
                    ?: video.thumbnailUrl.takeIf { it.isNotBlank() && !it.contains("images.unsplash.com") }
                    ?: sameTitleBackdrop
                    ?: video.thumbnailUrl
                val updatedBackdrop = sameTitleBackdrop
                    ?: video.backdropUrl
                    ?: officialPoster

                video.copy(
                    channelName = realNetworkName,
                    channelHandle = "@${realNetworkName.lowercase().replace(" ", "").replace("/", "").replace("+", "plus")}",
                    channelAvatarUrl = realChannelAvatarUrl,
                    thumbnailUrl = updatedThumbnail,
                    backdropUrl = updatedBackdrop,
                    rating = details.voteAverage ?: video.rating,
                    voteCount = details.voteCount ?: video.voteCount,
                    genres = if (genresList.isNotEmpty()) genresList else video.genres,
                    creators = creatorsList,
                    cast = castList,
                    tagline = details.tagline,
                    status = details.status,
                    totalSeasons = details.numberOfSeasons ?: video.totalSeasons,
                    totalEpisodes = details.numberOfEpisodes ?: video.totalEpisodes,
                    releaseDateIso = details.firstAirDate ?: video.releaseDateIso,
                    releaseDateFormatted = details.firstAirDate ?: video.publishedAt,
                    productionCompanies = prodCompanies,
                    networks = networksList,
                    description = details.overview?.takeIf { it.isNotBlank() } ?: video.description
                )
            } else {
                val details = api.getMovieDetails(tmdbIdInt, appendToResponse = "credits")
                val primaryStudio = details.productionCompanies?.firstOrNull { it.logoPath != null }
                    ?: details.productionCompanies?.firstOrNull()
                val realStudioName = primaryStudio?.name
                    ?: video.channelName

                val castList = details.credits?.cast?.take(20)?.map {
                    CastMemberItem(
                        id = it.id,
                        name = it.name,
                        character = it.character ?: "",
                        avatarUrl = if (it.profilePath != null) "${TmdbClient.IMAGE_BASE_W185}${it.profilePath}" else null
                    )
                } ?: emptyList()

                val directorName = details.credits?.crew?.find { it.job == "Director" }?.name
                val writersList = details.credits?.crew
                    ?.filter { it.job in listOf("Screenplay", "Writer", "Story") }
                    ?.map { it.name }
                    ?.distinct() ?: emptyList()

                val genresList = details.genres?.map { it.name } ?: emptyList()
                val prodCompanies = details.productionCompanies?.map { it.name } ?: emptyList()

                details.productionCompanies?.forEach { co ->
                    co.logoPath?.let { logoCache[co.id] = "${TmdbClient.IMAGE_BASE_W500}$it" }
                }

                val configuredStudioAvatar = logoUrlForIds(
                    companyId = primaryStudio?.id,
                    networkId = null
                )
                val realChannelAvatarUrl = configuredStudioAvatar
                    ?: primaryStudio?.logoPath
                    ?.let { "${TmdbClient.IMAGE_BASE_W500}$it" }
                    ?: video.channelAvatarUrl

                val budgetText = details.budget?.takeIf { it > 0 }?.let { String.format(Locale.US, "$%,d", it) }
                val revenueText = details.revenue?.takeIf { it > 0 }?.let { String.format(Locale.US, "$%,d", it) }

                val durationText = details.runtime?.takeIf { it > 0 }?.let { mins ->
                    val hrs = mins / 60
                    val remain = mins % 60
                    if (hrs > 0) "${hrs}h ${remain}m" else "${mins}m"
                } ?: video.duration

                val officialPoster = details.posterPath?.takeIf { it.isNotBlank() }?.let { "${TmdbClient.IMAGE_BASE_W500}$it" }
                val sameTitleBackdrop = details.backdropPath?.takeIf { it.isNotBlank() }?.let { "${TmdbClient.IMAGE_BASE_W780}$it" }
                val updatedThumbnail = officialPoster
                    ?: video.thumbnailUrl.takeIf { it.isNotBlank() && !it.contains("images.unsplash.com") }
                    ?: sameTitleBackdrop
                    ?: video.thumbnailUrl
                val updatedBackdrop = sameTitleBackdrop
                    ?: video.backdropUrl
                    ?: officialPoster

                video.copy(
                    channelName = realStudioName,
                    channelHandle = "@${realStudioName.lowercase().replace(" ", "").replace("/", "").replace("+", "plus")}",
                    channelAvatarUrl = realChannelAvatarUrl,
                    thumbnailUrl = updatedThumbnail,
                    backdropUrl = updatedBackdrop,
                    rating = details.voteAverage ?: video.rating,
                    voteCount = details.voteCount ?: video.voteCount,
                    genres = if (genresList.isNotEmpty()) genresList else video.genres,
                    director = directorName,
                    writers = writersList,
                    cast = castList,
                    tagline = details.tagline,
                    status = details.status,
                    runtimeMinutes = details.runtime,
                    duration = durationText,
                    releaseDateIso = details.releaseDate ?: video.releaseDateIso,
                    releaseDateFormatted = details.releaseDate ?: video.publishedAt,
                    budgetFormatted = budgetText,
                    revenueFormatted = revenueText,
                    productionCompanies = prodCompanies,
                    description = details.overview?.takeIf { it.isNotBlank() } ?: video.description
                )
            }
        }
    }

    /**
     * Feed cards only need the lightweight search/list response. Full credits,
     * production data, and season metadata are fetched when a title is opened;
     * avoiding one details request per card makes startup and scrolling much
     * less sensitive to TMDB latency and rate limits.
     */
    private suspend fun enrichVideoList(videos: List<VideoItem>): List<VideoItem> =
        videos.map(::applyCachedChannelArtwork)

    suspend fun getTrendingFeed(page: Int = 1): Result<List<VideoItem>> = withContext(Dispatchers.IO) {
        runCatching {
            val response = api.getTrendingAllWeek(page)
            enrichVideoList(response.results.map { mapToVideoItem(it) })
        }
    }

    /**
     * Builds the Shorts tab from real TMDB movie/series trailers. TMDB stores
     * the catalog metadata while YouTube hosts the playable trailer, so each
     * result keeps both pieces together for the UI and Watch Now action.
     */
    suspend fun getTrailerShorts(page: Int = 1): Result<List<ShortItem>> = withContext(Dispatchers.IO) {
        runCatching {
            val candidates = api.getTrendingAllWeek(page).results
                .filter { item ->
                    item.mediaType == "movie" || item.mediaType == "tv" ||
                        (item.title != null && item.name == null) ||
                        (item.name != null && item.title == null)
                }
                .take(24)

            coroutineScope {
                candidates.map { item ->
                    async {
                        val isTv = item.mediaType == "tv" ||
                            (item.title == null && item.name != null)
                        val mediaType = if (isTv) MediaType.TV_SHOW else MediaType.MOVIE
                        val videos = runCatching {
                            if (isTv) api.getTvVideos(item.id).results
                            else api.getMovieVideos(item.id).results
                        }.getOrDefault(emptyList())
                        val trailer = selectTrailerVideo(videos) ?: return@async null
                        val media = fetchFullMediaDetails(
                            mapToVideoItem(item, forcedType = mediaType)
                        ).getOrElse {
                            mapToVideoItem(item, forcedType = mediaType)
                        }
                        ShortItem(
                            id = "trailer_${mediaType.name.lowercase(Locale.US)}_${item.id}",
                            title = media.title,
                            channelName = media.channelName,
                            channelAvatarUrl = media.channelAvatarUrl,
                            likesCount = media.likesCount,
                            commentsCount = media.commentsCount,
                            soundTrack = trailer.name?.takeIf { it.isNotBlank() } ?: "Official Trailer",
                            videoStreamUrl = "",
                            thumbnailUrl = "https://i.ytimg.com/vi/${trailer.key}/hqdefault.jpg",
                            mediaItem = media,
                            trailerVideoId = trailer.key
                        )
                    }
                }.awaitAll().filterNotNull()
            }
        }

    }

    /** Selects a playable official YouTube trailer before teasers/clips. */
    fun selectTrailerVideo(videos: List<TmdbVideoItem>): TmdbVideoItem? {
        val typePriority = mapOf(
            "trailer" to 0,
            "teaser" to 1,
            "clip" to 2,
            "featurette" to 3
        )
        return videos
            .asSequence()
            .filter { it.key.isNotBlank() && it.site.equals("YouTube", ignoreCase = true) }
            .sortedWith(
                compareBy<TmdbVideoItem>(
                    { if (it.official == true) 0 else 1 },
                    { typePriority[it.type?.lowercase(Locale.US)] ?: 9 },
                    { if (it.name?.contains("official", ignoreCase = true) == true) 0 else 1 }
                )
            )
            .firstOrNull()
    }

    suspend fun getMoviesFeed(categoryFilter: String = "Popular", page: Int = 1): Result<List<VideoItem>> = withContext(Dispatchers.IO) {
        runCatching {
            val response = when (categoryFilter) {
                "Top Rated" -> api.getTopRatedMovies(page)
                "Now Playing" -> api.getNowPlayingMovies(page)
                else -> api.getPopularMovies(page)
            }
            enrichVideoList(response.results.map { mapToVideoItem(it, forcedType = MediaType.MOVIE) })
        }
    }

    suspend fun getTvShowsFeed(categoryFilter: String = "Popular", page: Int = 1): Result<List<VideoItem>> = withContext(Dispatchers.IO) {
        runCatching {
            val response = when (categoryFilter) {
                "Top Rated" -> api.getTopRatedTvShows(page)
                else -> api.getPopularTvShows(page)
            }
            enrichVideoList(response.results.map { mapToVideoItem(it, forcedType = MediaType.TV_SHOW) })
        }
    }

    suspend fun getUpcomingFeed(page: Int = 1): Result<List<VideoItem>> = withContext(Dispatchers.IO) {
        runCatching {
            val (movies, tv) = coroutineScope {
                val movieRequest = async { api.getUpcomingMovies(page).results }
                val tvRequest = async { api.getTvOnTheAir(page).results }
                movieRequest.await() to tvRequest.await()
            }
            (movies + tv)
                .map { item ->
                    val type = if (item.mediaType == "tv" || item.name != null && item.title == null) {
                        MediaType.TV_SHOW
                    } else {
                        MediaType.MOVIE
                    }
                    mapToVideoItem(item, forcedType = type, forcedCategory = "Coming soon")
                }
                .filter { it.releaseDateIso != null || it.releaseDateFormatted != null }
                .sortedBy { it.releaseDateIso ?: "9999-12-31" }
                .map(::applyCachedChannelArtwork)
        }
    }

    suspend fun getByGenre(genreName: String, page: Int = 1): Result<List<VideoItem>> = withContext(Dispatchers.IO) {
        runCatching {
            val genreId = GENRE_NAME_TO_ID[genreName] ?: 28
            val movieResp = api.discoverMoviesByGenre(genreId, page)
            val tvResp = api.discoverTvByGenre(genreId, page)

            val combined = mutableListOf<VideoItem>()
            val maxLen = maxOf(movieResp.results.size, tvResp.results.size)
            for (i in 0 until maxLen) {
                if (i < movieResp.results.size) {
                    combined.add(mapToVideoItem(movieResp.results[i], forcedType = MediaType.MOVIE, forcedCategory = genreName))
                }
                if (i < tvResp.results.size) {
                    combined.add(mapToVideoItem(tvResp.results[i], forcedType = MediaType.TV_SHOW, forcedCategory = genreName))
                }
            }
            enrichVideoList(combined)
        }
    }

    suspend fun searchTmdb(query: String, page: Int = 1): Result<List<VideoItem>> = withContext(Dispatchers.IO) {
        runCatching {
            if (query.isBlank()) return@runCatching emptyList()
            val response = api.searchMulti(query = query, page = page)
            val videos = response.results
                .filter { it.posterPath != null || it.backdropPath != null }
                .map { mapToVideoItem(it) }
            enrichVideoList(videos)
        }
    }

    suspend fun getTvEpisodes(tvId: Int, seasonNumber: Int): Result<List<TmdbEpisodeItem>> = withContext(Dispatchers.IO) {
        runCatching {
            val response = api.getTvSeasonDetails(tvId, seasonNumber)
            response.episodes
        }
    }

    suspend fun getRecommendations(id: Int, isTv: Boolean): Result<List<VideoItem>> = withContext(Dispatchers.IO) {
        runCatching {
            val response = if (isTv) api.getTvRecommendations(id) else api.getMovieRecommendations(id)
            enrichVideoList(response.results.map { mapToVideoItem(it) })
        }
    }

    suspend fun getTvTotalSeasons(tvId: Int): Int = withContext(Dispatchers.IO) {
        runCatching {
            val details = api.getTvDetails(tvId)
            details.numberOfSeasons ?: 1
        }.getOrDefault(1)
    }

    fun mapToVideoItem(
        item: TmdbMediaItem,
        forcedType: MediaType? = null,
        forcedCategory: String? = null
    ): VideoItem {
        val isTv = forcedType == MediaType.TV_SHOW ||
                item.mediaType == "tv" ||
                (forcedType == null && item.title == null && item.name != null)

        val mediaType = if (isTv) MediaType.TV_SHOW else MediaType.MOVIE
        val titleText = item.title ?: item.name ?: item.originalTitle ?: item.originalName ?: "Untitled"
        val releaseYear = (item.releaseDate ?: item.firstAirDate ?: "").take(4)
        val yearSuffix = if (releaseYear.isNotEmpty()) " ($releaseYear)" else ""
        val formattedTitle = "$titleText$yearSuffix"

        // Preferred: Official poster artwork for the exact movie or TV show.
        // Fallback 1: Current thumbnail implementation (backdrop scene from the same title).
        // Fallback 2: Existing empty-state placeholder only if neither exists.
        val officialPoster = item.posterPath?.takeIf { it.isNotBlank() }?.let {
            "${TmdbClient.IMAGE_BASE_W500}$it"
        }
        val sameTitleBackdrop = item.backdropPath?.takeIf { it.isNotBlank() }?.let {
            "${TmdbClient.IMAGE_BASE_W780}$it"
        }
        val emptyStateFallback = "https://images.unsplash.com/photo-1489599849927-2ee91cede3ba?w=800&auto=format&fit=crop&q=80"

        val selectedThumbnail = sameTitleBackdrop ?: officialPoster ?: emptyStateFallback
        val selectedPoster = officialPoster ?: sameTitleBackdrop ?: emptyStateFallback
        val selectedBackdrop = sameTitleBackdrop ?: officialPoster

        val studioIndex = kotlin.math.abs(item.id) % STUDIO_CHANNELS.size
        val studio = STUDIO_CHANNELS[studioIndex]

        val voteCount = item.voteCount ?: 0
        val voteAvg = item.voteAverage ?: 0.0
        val likesCalculated = when {
            voteCount >= 1000 -> "${voteCount / 1000}K"
            else -> "$voteCount"
        }

        val primaryGenreName = item.genreIds?.firstOrNull()?.let { GENRE_MAP[it] } ?: if (isTv) "TV Series" else "Cinema"
        val category = forcedCategory ?: when {
            isTv -> "Series / TV"
            else -> "Movies"
        }

        val durationBadge = if (isTv) {
            "TV SERIES"
        } else {
            ""
        }

        val embedUrl = if (isTv) {
            StreamService.buildEmbedUrl(MediaType.TV_SHOW, item.id.toString(), season = 1, episode = 1)
        } else {
            StreamService.buildEmbedUrl(MediaType.MOVIE, item.id.toString())
        }

        val publishedTime = if (releaseYear.isNotEmpty()) releaseYear else "Recent"

        val tags = mutableListOf("#$primaryGenreName", if (isTv) "#TVSeries" else "#MovieStream", "#4KStream", "#CinemaHub")
        if (voteAvg >= 8.0) tags.add("#TopRated")

        val fullDescription = buildString {
            append(item.overview ?: "Enjoy high definition streaming of $formattedTitle on YouTube.")
            append("\n\n")
            append("• Release: ${item.releaseDate ?: item.firstAirDate ?: "N/A"}\n")
            append("• Format: 4K UHD Dolby Atmos\n")
            append("• Streaming: VidSrc embed player with VidLink Pro fallback\n")
            if (isTv) {
                append("• Series Mode: Multi-Season & Multi-Episode Enabled")
            }
        }

        val genresList = item.genreIds?.mapNotNull { GENRE_MAP[it] } ?: emptyList()

        return VideoItem(
            id = "tmdb_${item.id}",
            title = formattedTitle,
            description = fullDescription,
            channelName = studio.name,
            channelHandle = "@${studio.name.lowercase().replace(" ", "").replace("/", "").replace("+", "plus")}",
            channelAvatarUrl = channelAvatarFor(studio),
            channelSubscribers = studio.subs,
            // TMDB does not expose a YouTube-style view count. Keep this empty
            // instead of turning popularity or votes into a misleading number.
            views = "",
            publishedAt = publishedTime,
            duration = durationBadge,
            thumbnailUrl = selectedThumbnail,
            posterUrl = selectedPoster,
            backdropUrl = selectedBackdrop,
            streamUrl = "",
            embedStreamUrl = embedUrl,
            mediaType = mediaType,
            likesCount = if (voteCount > 0) likesCalculated else "",
            commentsCount = "",
            category = category,
            tags = tags,
            tmdbId = item.id.toString(),
            currentSeason = 1,
            currentEpisode = 1,
            totalSeasons = if (isTv) 0 else 1,
            totalEpisodes = if (isTv) 0 else 1,
            rating = item.voteAverage,
            voteCount = item.voteCount,
            genres = genresList,
            releaseDateIso = item.releaseDate ?: item.firstAirDate,
            releaseDateFormatted = item.releaseDate ?: item.firstAirDate ?: releaseYear
        )
    }

    fun getSampleShorts(): List<ShortItem> {
        return listOf(
            ShortItem(
                id = "short_dune",
                title = "Dune 2 Desert Scene was Filmed in Real 50°C Dunes! #Dune #Cinema",
                channelName = "Warner Bros. Pictures",
                channelAvatarUrl = "https://yt3.googleusercontent.com/yVXKYrUI8hckCQdyUuOWf5ZJk2keT8WO3TV2b8RYk3RKgjz5Rh8v1UsH7Yz2j_hbDQRk32rZ_rM=s900-c-k-c0x00ffffff-no-rj",
                likesCount = "1.2M",
                commentsCount = "8.4K",
                soundTrack = "Hans Zimmer - Paul's Dream (Official Audio)",
                videoStreamUrl = "",
                thumbnailUrl = "https://images.unsplash.com/photo-1534447677768-be436bb09401?w=600&auto=format&fit=crop&q=80"
            ),
            ShortItem(
                id = "short_stranger",
                title = "Running Up That Hill - Season 4 Climax Scene! 📻✨",
                channelName = "Netflix",
                channelAvatarUrl = "https://yt3.googleusercontent.com/3b73AYEMMfa3SX5KJMeygio9smTPvrPrpicuQZbfQ_2DN7dV_ApiRM4CdYjSprEy1YYvt_9b=s900-c-k-c0x00ffffff-no-rj",
                likesCount = "890K",
                commentsCount = "12.1K",
                soundTrack = "Kate Bush - Running Up That Hill",
                videoStreamUrl = "",
                thumbnailUrl = "https://images.unsplash.com/photo-1518709268805-4e9042af9f23?w=600&auto=format&fit=crop&q=80"
            ),
            ShortItem(
                id = "short_oppenheimer",
                title = "Christopher Nolan explaining the Trinity Test practical effect 💥",
                channelName = "Universal Pictures",
                channelAvatarUrl = "https://yt3.googleusercontent.com/PzIajPyy9_HJ-LR5S6q86JV9o2GjabXF54QJnr3PeHpOiGOXtroatGM3mB2QxQa_vkF5yuyu9hs=s900-c-k-c0x00ffffff-no-rj",
                likesCount = "640K",
                commentsCount = "3.2K",
                soundTrack = "Ludwig Göransson - Can You Hear The Music",
                videoStreamUrl = "",
                thumbnailUrl = "https://images.unsplash.com/photo-1478760329108-5c3ed9d495a0?w=600&auto=format&fit=crop&q=80"
            ),
            ShortItem(
                id = "short_spiderman",
                title = "Across the Spider-Verse Animation Secret Breakdown! 🎨🕷️",
                channelName = "Marvel Studios",
                channelAvatarUrl = "https://yt3.googleusercontent.com/k7BhK-hm9_MbJbaKznHPhir6e4pWXbm1ppAHoseLIzRgoAPBMmH1IIhYKlXbGono25RD1OQwHQ=s900-c-k-c0x00ffffff-no-rj",
                likesCount = "1.5M",
                commentsCount = "19.5K",
                soundTrack = "Metro Boomin - Am I Dreaming",
                videoStreamUrl = "",
                thumbnailUrl = "https://images.unsplash.com/photo-1635805737707-575885ab0820?w=600&auto=format&fit=crop&q=80"
            )
        )
    }

    fun getStudioChannels(): List<ChannelItem> {
        return STUDIO_CHANNELS.mapIndexed { index, studio ->
            val logo = logoUrlForIds(studio.tmdbCompanyId, studio.tmdbNetworkId)
            val artwork = channelArtworkCache[studio.name]
            ChannelItem(
                id = "studio_$index",
                name = studio.name,
                handle = "@${studio.name.lowercase().replace(" ", "").replace("/", "").replace("+", "plus")}",
                avatarUrl = artwork?.avatarUrl ?: logo ?: studio.avatar,
                bannerUrl = artwork?.bannerUrl ?: studio.banner,
                subscribers = studio.subs,
                description = studio.description,
                videosCount = "${35 + (index * 7)} videos",
                hasNewStory = index % 2 == 0,
                isSubscribed = true
            )
        }
    }

    fun getStudioChannelByName(
        name: String,
        fallbackAvatar: String? = null,
        fallbackHandle: String? = null
    ): ChannelItem {
        val matched = STUDIO_CHANNELS.find {
            it.name.equals(name, ignoreCase = true) ||
            name.contains(it.searchQuery, ignoreCase = true) ||
            it.name.contains(name, ignoreCase = true)
        }

        if (matched != null) {
            val logo = logoUrlForIds(matched.tmdbCompanyId, matched.tmdbNetworkId)
            val artwork = channelArtworkCache[matched.name]
            return ChannelItem(
                id = "studio_${matched.name.hashCode()}",
                name = matched.name,
                handle = "@${matched.name.lowercase().replace(" ", "").replace("/", "").replace("+", "plus")}",
                avatarUrl = artwork?.avatarUrl ?: logo ?: matched.avatar,
                bannerUrl = artwork?.bannerUrl ?: matched.banner,
                subscribers = matched.subs,
                description = matched.description,
                videosCount = "48 videos",
                isSubscribed = true
            )
        }

        val cleanHandle = fallbackHandle ?: "@${name.lowercase().replace(" ", "").replace("/", "").replace("+", "plus")}"
        val cleanAvatar = fallbackAvatar ?: "https://images.unsplash.com/photo-1489599849927-2ee91cede3ba?w=150&auto=format&fit=crop&q=80"
        val banner = "https://images.unsplash.com/photo-1536440136628-849c177e76a1?w=1200&auto=format&fit=crop&q=80"

        return ChannelItem(
            id = "studio_${name.hashCode()}",
            name = name,
            handle = cleanHandle,
            avatarUrl = cleanAvatar,
            bannerUrl = banner,
            subscribers = "4.5M subscribers",
            description = "Official channel for $name. Watch full movie premieres, original series episodes, and exclusive cinema releases in 4K UHD.",
            videosCount = "36 videos",
            isSubscribed = false
        )
    }

    private fun normalizedChannelSearchValue(value: String): String =
        value.lowercase(Locale.US).filter(Char::isLetterOrDigit)

    private fun isChannelSearchMatch(candidate: String, query: String): Boolean {
        val normalizedCandidate = normalizedChannelSearchValue(candidate)
        val normalizedQuery = normalizedChannelSearchValue(query)
        return normalizedCandidate == normalizedQuery ||
            normalizedCandidate.contains(normalizedQuery) ||
            normalizedQuery.contains(normalizedCandidate)
    }

    private suspend fun findCompanyId(query: String): Int? = runCatching {
        val results = api.searchCompanies(query).results
        results.firstOrNull { isChannelSearchMatch(it.name, query) }?.id
            ?: results.firstOrNull()?.id
    }.getOrNull()

    suspend fun getChannelMedia(channelName: String): Result<List<VideoItem>> = withContext(Dispatchers.IO) {
        runCatching {
            val studioInfo = STUDIO_CHANNELS.find {
                it.name.equals(channelName, ignoreCase = true) ||
                channelName.contains(it.searchQuery, ignoreCase = true) ||
                it.name.contains(channelName, ignoreCase = true)
            }

            val query = studioInfo?.searchQuery ?: run {
                val words = channelName.split(" ").filter {
                    !it.equals("Pictures", ignoreCase = true) &&
                    !it.equals("Studios", ignoreCase = true) &&
                    !it.equals("Originals", ignoreCase = true) &&
                    !it.equals("Cinema", ignoreCase = true) &&
                    !it.equals("Premiere", ignoreCase = true) &&
                    !it.equals("Entertainment", ignoreCase = true)
                }
                if (words.isNotEmpty()) words.joinToString(" ") else channelName
            }

            val channelDisplayName = studioInfo?.name ?: channelName
            val channelHandle = "@${channelDisplayName.lowercase(Locale.US)
                .replace(" ", "")
                .replace("/", "")
                .replace("+", "plus")}"
            val channelAvatar = studioInfo?.let(::channelAvatarFor)
                ?: "https://images.unsplash.com/photo-1489599849927-2ee91cede3ba?w=150&auto=format&fit=crop&q=80"
            val subscriberText = studioInfo?.subs ?: "4.5M subscribers"

            // Search by the real TMDB company/network first. A text search for
            // "Netflix" or "Warner Bros" returns titles that merely mention
            // those words, not the studio's actual catalogue.
            val companyId = studioInfo?.tmdbCompanyId ?: findCompanyId(query)
            val networkId = studioInfo?.tmdbNetworkId
            val discoveredItems = mutableListOf<Pair<com.example.data.tmdb.TmdbMediaItem, MediaType>>()
            val discoveredIds = mutableSetOf<String>()

            fun addDiscovered(items: List<TmdbMediaItem>, type: MediaType) {
                items.forEach { item ->
                    if (item.posterPath == null && item.backdropPath == null) return@forEach
                    val key = "${type.name}:${item.id}"
                    if (discoveredIds.add(key)) discoveredItems += item to type
                }
            }

            if (companyId != null) {
                runCatching { addDiscovered(api.discoverMoviesByCompany(companyId).results, MediaType.MOVIE) }
                runCatching { addDiscovered(api.discoverTvByCompany(companyId).results, MediaType.TV_SHOW) }
            }
            if (networkId != null) {
                runCatching { addDiscovered(api.discoverMoviesByNetwork(networkId).results, MediaType.MOVIE) }
                runCatching { addDiscovered(api.discoverTvByNetwork(networkId).results, MediaType.TV_SHOW) }
            }

            // Keep an API/search fallback for a custom channel name or a
            // provider that has not indexed its company/network yet. Do not
            // fill the channel with unrelated trending titles.
            if (discoveredItems.isEmpty()) {
                runCatching { api.searchMulti(query = query, page = 1) }
                    .getOrNull()
                    ?.results
                    ?.filter { it.posterPath != null || it.backdropPath != null }
                    ?.forEach { item ->
                        val type = if (item.mediaType == "tv" ||
                            (item.title == null && item.name != null)
                        ) MediaType.TV_SHOW else MediaType.MOVIE
                        addDiscovered(listOf(item), type)
                    }
            }

            discoveredItems.map { (item, type) ->
                mapToVideoItem(item, forcedType = type).copy(
                    channelName = channelDisplayName,
                    channelHandle = channelHandle,
                    channelAvatarUrl = channelAvatar,
                    channelSubscribers = subscriberText,
                    isVerified = true
                )
            }.map(::applyCachedChannelArtwork)
        }
    }
}
