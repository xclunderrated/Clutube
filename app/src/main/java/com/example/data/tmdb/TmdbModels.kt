package com.example.data.tmdb

import androidx.compose.runtime.Immutable
import com.squareup.moshi.Json
import com.squareup.moshi.JsonClass

@JsonClass(generateAdapter = true)
data class TmdbPagedResponse<T>(
    @Json(name = "page") val page: Int = 1,
    @Json(name = "results") val results: List<T> = emptyList(),
    @Json(name = "total_pages") val totalPages: Int = 1,
    @Json(name = "total_results") val totalResults: Int = 0
)

@JsonClass(generateAdapter = true)
data class TmdbMediaItem(
    @Json(name = "id") val id: Int,
    @Json(name = "title") val title: String? = null,
    @Json(name = "name") val name: String? = null,
    @Json(name = "original_title") val originalTitle: String? = null,
    @Json(name = "original_name") val originalName: String? = null,
    @Json(name = "overview") val overview: String? = null,
    @Json(name = "poster_path") val posterPath: String? = null,
    @Json(name = "backdrop_path") val backdropPath: String? = null,
    @Json(name = "media_type") val mediaType: String? = null,
    @Json(name = "release_date") val releaseDate: String? = null,
    @Json(name = "first_air_date") val firstAirDate: String? = null,
    @Json(name = "vote_average") val voteAverage: Double? = null,
    @Json(name = "vote_count") val voteCount: Int? = null,
    @Json(name = "popularity") val popularity: Double? = null,
    @Json(name = "genre_ids") val genreIds: List<Int>? = null
)

@JsonClass(generateAdapter = true)
data class TmdbVideoItem(
    @Json(name = "id") val id: String,
    @Json(name = "name") val name: String? = null,
    @Json(name = "key") val key: String,
    @Json(name = "site") val site: String? = null,
    @Json(name = "type") val type: String? = null,
    @Json(name = "official") val official: Boolean? = null,
    @Json(name = "published_at") val publishedAt: String? = null
)

@JsonClass(generateAdapter = true)
data class TmdbVideoResponse(
    @Json(name = "id") val id: Int? = null,
    @Json(name = "results") val results: List<TmdbVideoItem> = emptyList()
)

@JsonClass(generateAdapter = true)
data class TmdbCastMember(
    @Json(name = "id") val id: Int,
    @Json(name = "name") val name: String,
    @Json(name = "character") val character: String? = null,
    @Json(name = "profile_path") val profilePath: String? = null,
    @Json(name = "order") val order: Int? = null
)

@JsonClass(generateAdapter = true)
data class TmdbCrewMember(
    @Json(name = "id") val id: Int,
    @Json(name = "name") val name: String,
    @Json(name = "job") val job: String? = null,
    @Json(name = "department") val department: String? = null,
    @Json(name = "profile_path") val profilePath: String? = null
)

@JsonClass(generateAdapter = true)
data class TmdbCredits(
    @Json(name = "cast") val cast: List<TmdbCastMember> = emptyList(),
    @Json(name = "crew") val crew: List<TmdbCrewMember> = emptyList()
)

@JsonClass(generateAdapter = true)
data class TmdbProductionCompany(
    @Json(name = "id") val id: Int,
    @Json(name = "name") val name: String,
    @Json(name = "logo_path") val logoPath: String? = null,
    @Json(name = "origin_country") val originCountry: String? = null
)

@JsonClass(generateAdapter = true)
data class TmdbNetwork(
    @Json(name = "id") val id: Int,
    @Json(name = "name") val name: String,
    @Json(name = "logo_path") val logoPath: String? = null,
    @Json(name = "origin_country") val originCountry: String? = null
)

@JsonClass(generateAdapter = true)
data class TmdbCreatedBy(
    @Json(name = "id") val id: Int,
    @Json(name = "name") val name: String,
    @Json(name = "profile_path") val profilePath: String? = null
)

@JsonClass(generateAdapter = true)
data class TmdbTvDetails(
    @Json(name = "id") val id: Int,
    @Json(name = "name") val name: String,
    @Json(name = "overview") val overview: String? = null,
    @Json(name = "poster_path") val posterPath: String? = null,
    @Json(name = "backdrop_path") val backdropPath: String? = null,
    @Json(name = "first_air_date") val firstAirDate: String? = null,
    @Json(name = "last_air_date") val lastAirDate: String? = null,
    @Json(name = "number_of_episodes") val numberOfEpisodes: Int? = null,
    @Json(name = "number_of_seasons") val numberOfSeasons: Int? = null,
    @Json(name = "status") val status: String? = null,
    @Json(name = "tagline") val tagline: String? = null,
    @Json(name = "seasons") val seasons: List<TmdbSeasonSummary>? = null,
    @Json(name = "vote_average") val voteAverage: Double? = null,
    @Json(name = "vote_count") val voteCount: Int? = null,
    @Json(name = "genres") val genres: List<TmdbGenre>? = null,
    @Json(name = "networks") val networks: List<TmdbNetwork>? = null,
    @Json(name = "production_companies") val productionCompanies: List<TmdbProductionCompany>? = null,
    @Json(name = "created_by") val createdBy: List<TmdbCreatedBy>? = null,
    @Json(name = "credits") val credits: TmdbCredits? = null
)

@JsonClass(generateAdapter = true)
data class TmdbSeasonSummary(
    @Json(name = "id") val id: Int,
    @Json(name = "season_number") val seasonNumber: Int,
    @Json(name = "name") val name: String,
    @Json(name = "episode_count") val episodeCount: Int? = null,
    @Json(name = "poster_path") val posterPath: String? = null,
    @Json(name = "overview") val overview: String? = null
)

@JsonClass(generateAdapter = true)
data class TmdbSeasonDetailResponse(
    @Json(name = "_id") val id: String? = null,
    @Json(name = "season_number") val seasonNumber: Int,
    @Json(name = "name") val name: String,
    @Json(name = "overview") val overview: String? = null,
    @Json(name = "poster_path") val posterPath: String? = null,
    @Json(name = "episodes") val episodes: List<TmdbEpisodeItem> = emptyList()
)

@Immutable
@JsonClass(generateAdapter = true)
data class TmdbEpisodeItem(
    @Json(name = "id") val id: Int,
    @Json(name = "episode_number") val episodeNumber: Int,
    @Json(name = "season_number") val seasonNumber: Int,
    @Json(name = "name") val name: String,
    @Json(name = "overview") val overview: String? = null,
    @Json(name = "still_path") val stillPath: String? = null,
    @Json(name = "vote_average") val voteAverage: Double? = null,
    @Json(name = "air_date") val airDate: String? = null,
    @Json(name = "runtime") val runtime: Int? = null
)

@JsonClass(generateAdapter = true)
data class TmdbMovieDetails(
    @Json(name = "id") val id: Int,
    @Json(name = "title") val title: String,
    @Json(name = "overview") val overview: String? = null,
    @Json(name = "poster_path") val posterPath: String? = null,
    @Json(name = "backdrop_path") val backdropPath: String? = null,
    @Json(name = "release_date") val releaseDate: String? = null,
    @Json(name = "runtime") val runtime: Int? = null,
    @Json(name = "vote_average") val voteAverage: Double? = null,
    @Json(name = "vote_count") val voteCount: Int? = null,
    @Json(name = "tagline") val tagline: String? = null,
    @Json(name = "imdb_id") val imdbId: String? = null,
    @Json(name = "status") val status: String? = null,
    @Json(name = "budget") val budget: Long? = null,
    @Json(name = "revenue") val revenue: Long? = null,
    @Json(name = "genres") val genres: List<TmdbGenre>? = null,
    @Json(name = "production_companies") val productionCompanies: List<TmdbProductionCompany>? = null,
    @Json(name = "credits") val credits: TmdbCredits? = null
)

@JsonClass(generateAdapter = true)
data class TmdbGenre(
    @Json(name = "id") val id: Int,
    @Json(name = "name") val name: String
)

@JsonClass(generateAdapter = true)
data class TmdbGenreResponse(
    @Json(name = "genres") val genres: List<TmdbGenre> = emptyList()
)

@JsonClass(generateAdapter = true)
data class TmdbCompanyDetails(
    @Json(name = "id") val id: Int,
    @Json(name = "name") val name: String,
    @Json(name = "logo_path") val logoPath: String? = null,
    @Json(name = "homepage") val homepage: String? = null
)

@JsonClass(generateAdapter = true)
data class TmdbNetworkDetails(
    @Json(name = "id") val id: Int,
    @Json(name = "name") val name: String,
    @Json(name = "logo_path") val logoPath: String? = null,
    @Json(name = "homepage") val homepage: String? = null
)
