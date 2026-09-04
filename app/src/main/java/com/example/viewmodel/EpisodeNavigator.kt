package com.example.viewmodel

import com.example.data.tmdb.TmdbEpisodeItem

data class NextEpisode(
    val season: Int,
    val episode: Int
)

/**
 * Pure episode navigation. The list supplied by TMDB is the source of truth
 * for the next episode in the current season; when that metadata is absent we
 * do not invent an episode number. A known next season may still begin at E1.
 */
object EpisodeNavigator {
    fun nextEpisode(
        currentSeason: Int,
        currentEpisode: Int,
        episodes: List<TmdbEpisodeItem>,
        totalSeasons: Int
    ): NextEpisode? {
        val safeSeason = currentSeason.coerceAtLeast(1)
        val safeEpisode = currentEpisode.coerceAtLeast(1)
        val currentSeasonEpisodes = episodes
            .asSequence()
            .filter { it.seasonNumber == safeSeason }
            .toList()

        // Without metadata for the current season we cannot distinguish the
        // final episode from a temporarily incomplete/network-failed response.
        if (currentSeasonEpisodes.isEmpty()) return null

        val nextInCurrentSeason = currentSeasonEpisodes
            .asSequence()
            .filter { it.episodeNumber > safeEpisode }
            .sortedBy { it.episodeNumber }
            .firstOrNull()

        if (nextInCurrentSeason != null) {
            return NextEpisode(safeSeason, nextInCurrentSeason.episodeNumber)
        }

        val safeTotalSeasons = totalSeasons.coerceAtLeast(0)
        return if (safeSeason < safeTotalSeasons) {
            NextEpisode(safeSeason + 1, 1)
        } else {
            null
        }
    }
}
