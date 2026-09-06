package com.example.data.tmdb

import retrofit2.http.GET
import retrofit2.http.Path
import retrofit2.http.Query

interface TmdbApiService {

    @GET("trending/all/day")
    suspend fun getTrendingAllDay(
        @Query("page") page: Int = 1
    ): TmdbPagedResponse<TmdbMediaItem>

    @GET("trending/all/week")
    suspend fun getTrendingAllWeek(
        @Query("page") page: Int = 1
    ): TmdbPagedResponse<TmdbMediaItem>

    @GET("trending/movie/week")
    suspend fun getTrendingMovies(
        @Query("page") page: Int = 1
    ): TmdbPagedResponse<TmdbMediaItem>

    @GET("movie/popular")
    suspend fun getPopularMovies(
        @Query("page") page: Int = 1
    ): TmdbPagedResponse<TmdbMediaItem>

    @GET("movie/top_rated")
    suspend fun getTopRatedMovies(
        @Query("page") page: Int = 1
    ): TmdbPagedResponse<TmdbMediaItem>

    @GET("movie/now_playing")
    suspend fun getNowPlayingMovies(
        @Query("page") page: Int = 1
    ): TmdbPagedResponse<TmdbMediaItem>

    @GET("movie/upcoming")
    suspend fun getUpcomingMovies(
        @Query("page") page: Int = 1
    ): TmdbPagedResponse<TmdbMediaItem>

    @GET("trending/tv/week")
    suspend fun getTrendingTvShows(
        @Query("page") page: Int = 1
    ): TmdbPagedResponse<TmdbMediaItem>

    @GET("tv/popular")
    suspend fun getPopularTvShows(
        @Query("page") page: Int = 1
    ): TmdbPagedResponse<TmdbMediaItem>

    @GET("tv/top_rated")
    suspend fun getTopRatedTvShows(
        @Query("page") page: Int = 1
    ): TmdbPagedResponse<TmdbMediaItem>

    @GET("tv/on_the_air")
    suspend fun getTvOnTheAir(
        @Query("page") page: Int = 1
    ): TmdbPagedResponse<TmdbMediaItem>

    @GET("tv/airing_today")
    suspend fun getTvAiringToday(
        @Query("page") page: Int = 1
    ): TmdbPagedResponse<TmdbMediaItem>

    @GET("search/multi")
    suspend fun searchMulti(
        @Query("query") query: String,
        @Query("page") page: Int = 1,
        @Query("include_adult") includeAdult: Boolean = false
    ): TmdbPagedResponse<TmdbMediaItem>

    @GET("search/company")
    suspend fun searchCompanies(
        @Query("query") query: String,
        @Query("page") page: Int = 1
    ): TmdbPagedResponse<TmdbProductionCompany>

    @GET("discover/movie")
    suspend fun discoverMoviesByGenre(
        @Query("with_genres") genreId: Int,
        @Query("page") page: Int = 1,
        @Query("sort_by") sortBy: String = "popularity.desc"
    ): TmdbPagedResponse<TmdbMediaItem>

    @GET("discover/movie")
    suspend fun discoverMoviesByCompany(
        @Query("with_companies") companyId: Int,
        @Query("page") page: Int = 1,
        @Query("sort_by") sortBy: String = "popularity.desc"
    ): TmdbPagedResponse<TmdbMediaItem>

    @GET("discover/movie")
    suspend fun discoverMoviesByNetwork(
        @Query("with_networks") networkId: Int,
        @Query("page") page: Int = 1,
        @Query("sort_by") sortBy: String = "popularity.desc"
    ): TmdbPagedResponse<TmdbMediaItem>

    @GET("discover/tv")
    suspend fun discoverTvByGenre(
        @Query("with_genres") genreId: Int,
        @Query("page") page: Int = 1,
        @Query("sort_by") sortBy: String = "popularity.desc"
    ): TmdbPagedResponse<TmdbMediaItem>

    @GET("discover/tv")
    suspend fun discoverTvByCompany(
        @Query("with_companies") companyId: Int,
        @Query("page") page: Int = 1,
        @Query("sort_by") sortBy: String = "popularity.desc"
    ): TmdbPagedResponse<TmdbMediaItem>

    @GET("discover/tv")
    suspend fun discoverTvByNetwork(
        @Query("with_networks") networkId: Int,
        @Query("page") page: Int = 1,
        @Query("sort_by") sortBy: String = "popularity.desc"
    ): TmdbPagedResponse<TmdbMediaItem>

    @GET("movie/{movie_id}")
    suspend fun getMovieDetails(
        @Path("movie_id") movieId: Int,
        @Query("append_to_response") appendToResponse: String = "credits"
    ): TmdbMovieDetails

    @GET("movie/{movie_id}/videos")
    suspend fun getMovieVideos(
        @Path("movie_id") movieId: Int
    ): TmdbVideoResponse

    @GET("tv/{tv_id}")
    suspend fun getTvDetails(
        @Path("tv_id") tvId: Int,
        @Query("append_to_response") appendToResponse: String = "credits"
    ): TmdbTvDetails

    @GET("tv/{tv_id}/videos")
    suspend fun getTvVideos(
        @Path("tv_id") tvId: Int
    ): TmdbVideoResponse

    @GET("tv/{tv_id}/season/{season_number}")
    suspend fun getTvSeasonDetails(
        @Path("tv_id") tvId: Int,
        @Path("season_number") seasonNumber: Int
    ): TmdbSeasonDetailResponse

    @GET("movie/{movie_id}/recommendations")
    suspend fun getMovieRecommendations(
        @Path("movie_id") movieId: Int,
        @Query("page") page: Int = 1
    ): TmdbPagedResponse<TmdbMediaItem>

    @GET("tv/{tv_id}/recommendations")
    suspend fun getTvRecommendations(
        @Path("tv_id") tvId: Int,
        @Query("page") page: Int = 1
    ): TmdbPagedResponse<TmdbMediaItem>

    @GET("company/{company_id}")
    suspend fun getCompanyDetails(
        @Path("company_id") companyId: Int
    ): TmdbCompanyDetails

    @GET("network/{network_id}")
    suspend fun getNetworkDetails(
        @Path("network_id") networkId: Int
    ): TmdbNetworkDetails
}
