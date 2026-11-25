package dev.jdtech.jellyfin.models

import java.time.LocalDateTime
import java.util.Locale
import java.util.UUID

data class ReleaseCandidate<T>(
    val id: UUID,
    val item: T,
    val premiereDate: LocalDateTime?,
    val productionYear: Int?,
    val communityRating: Float?,
    val name: String,
)

object LatestReleasesRanker {
    private const val MIN_MOVIE_YEAR = 1888
    private const val MIN_SERIES_YEAR = 1920

    private data class RankedEntry<T>(
        val candidate: ReleaseCandidate<T>,
        val effectiveDate: LocalDateTime?,
    )

    private fun <T> releaseComparator(): Comparator<RankedEntry<T>> =
        compareBy<RankedEntry<T>, LocalDateTime?>(nullsLast(reverseOrder())) { it.effectiveDate }
            .thenBy(nullsLast(reverseOrder())) { it.candidate.productionYear }
            .thenBy(nullsLast(reverseOrder())) { it.candidate.communityRating }
            .thenBy { it.candidate.name.lowercase(Locale.ROOT) }

    fun <T> rankMovies(
        candidates: List<ReleaseCandidate<T>>,
        now: LocalDateTime = LocalDateTime.now(),
        maxFutureDays: Long = 14L,
        limit: Int = 16,
    ): List<T> {
        val maxAllowedDate = now.plusDays(maxFutureDays)
        return candidates
            .distinctBy { it.id }
            .mapNotNull { candidate ->
                val premiereDate = candidate.premiereDate
                val productionYear = candidate.productionYear
                val isFuturePremiere = premiereDate != null && premiereDate.isAfter(maxAllowedDate)
                val isFutureYearOnly =
                    premiereDate == null && productionYear != null && productionYear > now.year
                if (isFuturePremiere || isFutureYearOnly) {
                    return@mapNotNull null
                }

                val effectiveDate =
                    premiereDate
                        ?: productionYear
                            ?.takeIf { it in MIN_MOVIE_YEAR..now.year }
                            ?.let { LocalDateTime.of(it, 1, 1, 0, 0) }
                RankedEntry(candidate = candidate, effectiveDate = effectiveDate)
            }
            .sortedWith(releaseComparator())
            .take(limit.coerceAtLeast(0))
            .map { it.candidate.item }
    }

    fun <T> rankSeries(
        seriesCandidates: List<ReleaseCandidate<T>>,
        latestEpisodeDateBySeriesId: Map<UUID, LocalDateTime>,
        now: LocalDateTime = LocalDateTime.now(),
        maxFutureDays: Long = 7L,
        limit: Int = 16,
    ): List<T> {
        val maxAllowedDate = now.plusDays(maxFutureDays)
        return seriesCandidates
            .distinctBy { it.id }
            .mapNotNull { candidate ->
                val validSeriesDate =
                    candidate.premiereDate?.takeIf { !it.isAfter(maxAllowedDate) }
                val validEpisodeDate =
                    latestEpisodeDateBySeriesId[candidate.id]?.takeIf { !it.isAfter(maxAllowedDate) }
                if (
                    candidate.premiereDate != null &&
                        candidate.premiereDate.isAfter(maxAllowedDate) &&
                        validEpisodeDate == null
                ) {
                    return@mapNotNull null
                }

                val fallbackYearDate =
                    candidate.productionYear
                        ?.takeIf { it in MIN_SERIES_YEAR..now.year }
                        ?.let { LocalDateTime.of(it, 1, 1, 0, 0) }
                val effectiveDate =
                    listOfNotNull(validSeriesDate, validEpisodeDate).maxOrNull() ?: fallbackYearDate
                RankedEntry(candidate = candidate, effectiveDate = effectiveDate)
            }
            .sortedWith(releaseComparator())
            .take(limit.coerceAtLeast(0))
            .map { it.candidate.item }
    }
}
