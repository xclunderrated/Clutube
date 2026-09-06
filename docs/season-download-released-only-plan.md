# Plan: Season download = released episodes only

## Problem
Watch page "Download season" queues the whole season, including episodes
that are still upcoming / unreleased. The button label counts all episodes
(e.g. "Download season 2 (8)") instead of only the released ones.

Traced flow (no behavior changed yet):

- `app/src/main/java/com/example/ui/components/TvShowEpisodeList.kt:104-108`
  builds `seasonEpisodes` = all episodes of the selected season.
- `TvShowEpisodeList.kt:277-288` — season row shows when
  `seasonEpisodes.isNotEmpty()` and calls
  `onDownloadSeason(safeSelectedSeason, seasonEpisodes)` with the FULL list.
- `TvShowEpisodeList.kt:304-310` — label uses `seasonEpisodes.size`
  (total, upcoming included).
- Per-episode rows are already correct: `TvShowEpisodeList.kt:363,377-379`
  hides the download button for `isUnreleased(episode.airDate)`.
- `app/src/main/java/com/example/viewmodel/YouTubeViewModel.kt`
  `requestDownloadSeason` (:2528), `startConfiguredDownload` Season branch
  (:2626-2638), `startAutoBestDownload` Season (:2686-2705),
  `startConfiguredTorrentDownload` Season (:2747-2764), `downloadSeason`
  (:2857) — all filter by season number only, no air-date check.
- `app/src/main/java/com/example/data/download/DownloadManager.kt:351-368`
  (`downloadSeason`) loops over the season-filtered list with no
  `isUnreleased` guard, so upcoming episodes burn torrent searches and land
  as FAILED rows ("No active download stream...", see `:290-312`).
- `app/src/main/java/com/example/ui/components/DownloadOptionsSheet.kt:134-156`
  derives `itemCount` / "N Episodes" / size estimates from
  `target.episodes`, so it inherits whatever the target contains.
- Release truth: `app/src/main/java/com/example/model/NotificationModels.kt:84-85`
  `isUnreleased(airDate)` (`null`/blank date = released).

## Agreed behavior (from user)
- Tapping "Download season" downloads the RELEASED episodes only.
- Button label shows both counts, e.g. "Download season 2 (6 of 8)".
- Zero-released case: nothing is queued (graceful feedback, no FAILED rows).

## Changes

### 1. UI — `ui/components/TvShowEpisodeList.kt` (~lines 104, 277-318)
- After `seasonEpisodes`, derive:
  ```kotlin
  val releasedSeasonEpisodes = seasonEpisodes.filterNot { isUnreleased(it.airDate) }
  val upcomingCount = seasonEpisodes.size - releasedSeasonEpisodes.size
  ```
- Keep the row visible when `seasonEpisodes.isNotEmpty()` so the
  "R of T" info is visible, but:
  - `onClick` passes `releasedSeasonEpisodes`, not `seasonEpisodes`.
  - If `releasedSeasonEpisodes.isEmpty()` (all upcoming): render the row
    non-clickable/disabled, e.g. "Season S - no released episodes yet".
    Never queue in this state.
  - Labels (R = released, T = total, D = downloaded):
    - none downloaded: "Download season S (R of T)"
    - partial: "Season S (D/R of T)" (keep short)
    - all released downloaded: "Season S downloaded (R of T)"
  - Base `downloadedCount` / `allSeasonDownloaded` on the RELEASED list so
    upcoming episodes never block the green check.
- Keep `testTag("download_season_btn")` unchanged.

### 2. ViewModel hardening — `viewmodel/YouTubeViewModel.kt`
- In all three Season branches (`startConfiguredDownload`, `startAutoBestDownload`,
  `startConfiguredTorrentDownload`), filter again:
  ```kotlin
  val releasable = target.episodes.filter {
      it.seasonNumber == target.seasonNumber && !isUnreleased(it.airDate)
  }
  if (releasable.isEmpty()) {
      showFeedback("No released episodes in Season X yet")
      return
  }
  ```
  then iterate and build the feedback count from `releasable`.
- Optionally filter in `requestDownloadSeason` itself so
  `DownloadTarget.Season` never holds unreleased episodes and the
  `DownloadOptionsSheet` counts/estimates auto-correct.
- Needs import: `com.example.model.isUnreleased`.

### 3. Last line of defense — `data/download/DownloadManager.kt:351-368`
- Add `&& !isUnreleased(episode.airDate)` to `downloadSeason`'s filter;
  early-return when empty. Prevents FAILED rows + wasted
  `resolveTvTorrents` calls even if an unfiltered list reaches the manager.
- Needs import: `com.example.model.isUnreleased`.

### 4. Verify only (no change expected)
- `ui/components/DownloadOptionsSheet.kt:137,155` — auto-corrects once the
  target is filtered; confirm estimate math uses filtered `itemCount`.
- `MainActivity.kt:951` — pure pass-through, no change.

## Edge cases
- All upcoming -> no queue, friendly feedback, no FAILED rows.
- `airDate` null/blank -> treated as released (existing `isUnreleased`
  contract, matches per-episode behavior).
- Partially-aired season -> queues only aired ones, label shows `R of T`.
- Episode airing while the sheet is open -> VM/manager re-filter at confirm
  time wins over the UI snapshot.

## Verification
- `./gradlew :app:assembleDebug`
- New unit test, e.g.
  `app/src/test/java/com/example/SeasonDownloadFilterTest.kt`, for the
  filter: mixed released/upcoming -> released only; all upcoming -> empty;
  null date -> released.
- Manual: open a currently-airing show -> row reads e.g.
  "Download season 2 (6 of 8)" -> confirm -> Downloads shows 6 rows, zero
  FAILED upcoming rows; all-upcoming season shows the disabled note.
