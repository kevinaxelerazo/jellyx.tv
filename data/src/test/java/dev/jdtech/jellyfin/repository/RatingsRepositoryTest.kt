package dev.jdtech.jellyfin.repository

import dev.jdtech.jellyfin.database.ServerDatabaseDao
import dev.jdtech.jellyfin.models.JellyxRatingsDto
import dev.jdtech.jellyfin.models.RottenTomatoesStatus
import io.mockk.coEvery
import io.mockk.coVerify
import io.mockk.every
import io.mockk.mockk
import java.util.UUID
import kotlinx.coroutines.test.runTest
import okhttp3.OkHttpClient
import org.junit.Assert.assertEquals
import org.junit.Assert.assertNull
import org.junit.Before
import org.junit.Test

class RatingsRepositoryTest {
    private lateinit var database: ServerDatabaseDao
    private lateinit var httpClient: OkHttpClient

    @Before
    fun setUp() {
        database = mockk(relaxed = true)
        httpClient = mockk(relaxed = true)
    }

    @Test
    fun extractNativeRatings_whenCriticRatingIs75AndCommunityIs8_returnsFreshTomatoAndImdbImmediately() {
        val repository = RatingsRepositoryImpl(database = database, httpClient = httpClient)

        val ratings = repository.extractNativeRatings(communityRating = 8.1f, criticRating = 75f)

        assertEquals(8.1f, ratings.imdbRating)
        assertEquals(75, ratings.rtCriticPercentage)
        assertEquals(RottenTomatoesStatus.FRESH, ratings.rtCriticStatus)
    }

    @Test
    fun extractNativeRatings_whenCriticRatingIs59_returnsRottenStatus() {
        val repository = RatingsRepositoryImpl(database = database, httpClient = httpClient)

        val rotten = repository.extractNativeRatings(communityRating = 5.5f, criticRating = 59f)
        val freshBoundary =
            repository.extractNativeRatings(communityRating = 6.0f, criticRating = 60f)

        assertEquals(59, rotten.rtCriticPercentage)
        assertEquals(RottenTomatoesStatus.ROTTEN, rotten.rtCriticStatus)
        assertEquals(60, freshBoundary.rtCriticPercentage)
        assertEquals(RottenTomatoesStatus.FRESH, freshBoundary.rtCriticStatus)
    }

    @Test
    fun getEnrichedRatings_whenRoomCacheIsFresh_doesNotCallRemoteApi() = runTest {
        val itemId = UUID.randomUUID()
        val nowMs = 1_700_000_000_000L
        coEvery { database.getRatings(itemId) } returns
            JellyxRatingsDto(
                itemId = itemId,
                imdbRating = 8.7f,
                tmdbPercentage = 85,
                rtCriticPercentage = 94,
                rtAudiencePercentage = 91,
                updatedAtEpochMs = nowMs - 60_000L,
            )

        val repository =
            RatingsRepositoryImpl(
                database = database,
                omdbApiKey = "test-omdb",
                tmdbApiKey = "test-tmdb",
                mdblistApiKey = "test-mdblist",
                httpClient = httpClient,
                clockMs = { nowMs },
            )

        val ratings =
            repository.getEnrichedRatings(
                itemId = itemId,
                communityRating = 8.0f,
                criticRating = 90f,
                providerIds = mapOf("Imdb" to "tt1375666", "Tmdb" to "27205"),
            )

        assertEquals(8.7f, ratings.imdbRating)
        assertEquals(85, ratings.tmdbPercentage)
        assertEquals(94, ratings.rtCriticPercentage)
        assertEquals(91, ratings.rtAudiencePercentage)
        assertEquals(RottenTomatoesStatus.FRESH, ratings.rtCriticStatus)
        coVerify(exactly = 0) { httpClient.newCall(any()) }
    }

    @Test
    fun getEnrichedRatings_whenApiKeysAreBlank_returnsNativeRatingsGracefully() = runTest {
        val itemId = UUID.randomUUID()
        coEvery { database.getRatings(itemId) } returns null
        every { httpClient.newCall(any()) } throws AssertionError("HTTP should not be called")

        val repository =
            RatingsRepositoryImpl(
                database = database,
                omdbApiKey = "",
                tmdbApiKey = "",
                mdblistApiKey = "",
                httpClient = httpClient,
            )

        val ratings =
            repository.getEnrichedRatings(
                itemId = itemId,
                communityRating = 7.9f,
                criticRating = 88f,
                providerIds = mapOf("Imdb" to "tt0133093"),
            )

        assertEquals(7.9f, ratings.imdbRating)
        assertEquals(88, ratings.rtCriticPercentage)
        assertEquals(RottenTomatoesStatus.FRESH, ratings.rtCriticStatus)
        assertNull(ratings.tmdbPercentage)
        assertNull(ratings.rtAudiencePercentage)
    }
}
