package dev.jdtech.jellyfin.models

import java.time.LocalDateTime
import java.util.UUID
import org.junit.Assert.assertEquals
import org.junit.Test

class LatestReleasesRankerTest {

    private val now = LocalDateTime.of(2026, 6, 15, 12, 0)

    private fun candidate(
        name: String,
        premiereDate: LocalDateTime? = null,
        productionYear: Int? = null,
        communityRating: Float? = null,
        id: UUID = UUID.randomUUID(),
    ): ReleaseCandidate<String> =
        ReleaseCandidate(
            id = id,
            item = name,
            premiereDate = premiereDate,
            productionYear = productionYear,
            communityRating = communityRating,
            name = name,
        )

    @Test
    fun rankMovies_recentReleasesOutrankBackfilledClassics() {
        val backfilled1999 =
            candidate(
                name = "Classic 1999",
                premiereDate = LocalDateTime.of(1999, 5, 10, 0, 0),
                productionYear = 1999,
                communityRating = 9.0f,
            )
        val backfilled2012 =
            candidate(
                name = "Classic 2012",
                premiereDate = LocalDateTime.of(2012, 11, 2, 0, 0),
                productionYear = 2012,
                communityRating = 8.4f,
            )
        val streaming2025 =
            candidate(
                name = "Streaming 2025",
                premiereDate = LocalDateTime.of(2025, 12, 19, 0, 0),
                productionYear = 2025,
                communityRating = 7.1f,
            )
        val theatrical2026 =
            candidate(
                name = "Theatrical 2026",
                premiereDate = LocalDateTime.of(2026, 6, 5, 0, 0),
                productionYear = 2026,
                communityRating = 7.8f,
            )

        val ranked =
            LatestReleasesRanker.rankMovies(
                candidates = listOf(backfilled1999, backfilled2012, streaming2025, theatrical2026),
                now = now,
            )

        assertEquals(
            listOf("Theatrical 2026", "Streaming 2025", "Classic 2012", "Classic 1999"),
            ranked,
        )
    }

    @Test
    fun rankMovies_excludesFarFutureReleases() {
        val validNearFuture =
            candidate(
                name = "Near Future",
                premiereDate = now.plusDays(7),
                productionYear = 2026,
            )
        val farFuturePremiere =
            candidate(
                name = "Far Future Premiere",
                premiereDate = now.plusYears(2),
                productionYear = 2028,
            )
        val farFutureYearOnly =
            candidate(
                name = "Far Future Year Only",
                premiereDate = null,
                productionYear = 2028,
            )

        val ranked =
            LatestReleasesRanker.rankMovies(
                candidates = listOf(farFuturePremiere, farFutureYearOnly, validNearFuture),
                now = now,
            )

        assertEquals(listOf("Near Future"), ranked)
    }

    @Test
    fun rankMovies_fallsBackToProductionYearWhenPremiereDateIsNull() {
        val year2026NoDate =
            candidate(
                name = "Year 2026",
                premiereDate = null,
                productionYear = 2026,
            )
        val year2025WithDate =
            candidate(
                name = "Date 2025",
                premiereDate = LocalDateTime.of(2025, 8, 20, 0, 0),
                productionYear = 2025,
            )
        val year2024NoDate =
            candidate(
                name = "Year 2024",
                premiereDate = null,
                productionYear = 2024,
            )

        val ranked =
            LatestReleasesRanker.rankMovies(
                candidates = listOf(year2024NoDate, year2025WithDate, year2026NoDate),
                now = now,
            )

        assertEquals(listOf("Year 2026", "Date 2025", "Year 2024"), ranked)
    }

    @Test
    fun rankSeries_ongoingSeriesWithNewEpisodeOutranksOlderAndBackfilledSeries() {
        val ongoing2020 =
            candidate(
                name = "Ongoing 2020",
                premiereDate = LocalDateTime.of(2020, 3, 1, 0, 0),
                productionYear = 2020,
            )
        val dormant2024 =
            candidate(
                name = "Dormant 2024",
                premiereDate = LocalDateTime.of(2024, 10, 15, 0, 0),
                productionYear = 2024,
            )
        val backfilled2008 =
            candidate(
                name = "Backfilled 2008",
                premiereDate = LocalDateTime.of(2008, 1, 20, 0, 0),
                productionYear = 2008,
            )

        val episodeDates =
            mapOf(ongoing2020.id to LocalDateTime.of(2026, 6, 12, 0, 0))

        val ranked =
            LatestReleasesRanker.rankSeries(
                seriesCandidates = listOf(backfilled2008, dormant2024, ongoing2020),
                latestEpisodeDateBySeriesId = episodeDates,
                now = now,
            )

        assertEquals(listOf("Ongoing 2020", "Dormant 2024", "Backfilled 2008"), ranked)
    }

    @Test
    fun rankSeries_new2026SeriesRanksAtTopAlongsideOngoing2026Episodes() {
        val new2026Series =
            candidate(
                name = "New 2026 Series",
                premiereDate = LocalDateTime.of(2026, 6, 14, 0, 0),
                productionYear = 2026,
            )
        val ongoingSeries =
            candidate(
                name = "Ongoing With Episode",
                premiereDate = LocalDateTime.of(2021, 9, 1, 0, 0),
                productionYear = 2021,
            )
        val older2025Series =
            candidate(
                name = "Older 2025 Series",
                premiereDate = LocalDateTime.of(2025, 11, 1, 0, 0),
                productionYear = 2025,
            )

        val episodeDates =
            mapOf(ongoingSeries.id to LocalDateTime.of(2026, 6, 10, 0, 0))

        val ranked =
            LatestReleasesRanker.rankSeries(
                seriesCandidates = listOf(older2025Series, ongoingSeries, new2026Series),
                latestEpisodeDateBySeriesId = episodeDates,
                now = now,
            )

        assertEquals(
            listOf("New 2026 Series", "Ongoing With Episode", "Older 2025 Series"),
            ranked,
        )
    }

    @Test
    fun rankSeries_ignoresFarFutureEpisodes() {
        val seriesWithFarFutureEpisode =
            candidate(
                name = "Series With Far Future Episode",
                premiereDate = LocalDateTime.of(2023, 4, 1, 0, 0),
                productionYear = 2023,
            )
        val series2025 =
            candidate(
                name = "Series 2025",
                premiereDate = LocalDateTime.of(2025, 5, 1, 0, 0),
                productionYear = 2025,
            )

        val episodeDates =
            mapOf(seriesWithFarFutureEpisode.id to now.plusDays(30))

        val ranked =
            LatestReleasesRanker.rankSeries(
                seriesCandidates = listOf(seriesWithFarFutureEpisode, series2025),
                latestEpisodeDateBySeriesId = episodeDates,
                now = now,
            )

        assertEquals(listOf("Series 2025", "Series With Far Future Episode"), ranked)
    }
}
