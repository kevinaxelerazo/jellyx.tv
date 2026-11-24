package dev.jdtech.jellyfin.repository

import dev.jdtech.jellyfin.database.ServerDatabaseDao
import dev.jdtech.jellyfin.models.JellyxImages
import dev.jdtech.jellyfin.models.JellyxMovie
import dev.jdtech.jellyfin.models.JellyxRatingsDto
import dev.jdtech.jellyfin.models.RottenTomatoesStatus
import dev.jdtech.jellyfin.models.toNativeRatings
import io.mockk.coEvery
import io.mockk.coVerify
import io.mockk.every
import io.mockk.mockk
import io.mockk.mockkConstructor
import io.mockk.slot
import io.mockk.unmockkConstructor
import java.util.UUID
import kotlinx.coroutines.test.runTest
import okhttp3.Call
import okhttp3.MediaType.Companion.toMediaType
import okhttp3.OkHttpClient
import okhttp3.Protocol
import okhttp3.Request
import okhttp3.Response
import okhttp3.ResponseBody.Companion.toResponseBody
import org.json.JSONObject
import org.junit.Assert.assertEquals
import org.junit.Assert.assertNull
import org.junit.Assert.assertTrue
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

    @Test
    fun toNativeRatings_derivesTmdbPercentageOnlyWhenTmdbProviderIdAndCommunityRatingPresent() {
        val withTmdb =
            JellyxMovie(
                id = UUID.randomUUID(),
                name = "With TMDb",
                originalTitle = null,
                overview = "",
                sources = emptyList(),
                played = false,
                favorite = false,
                canPlay = true,
                canDownload = false,
                runtimeTicks = 0L,
                playbackPositionTicks = 0L,
                premiereDate = null,
                people = emptyList(),
                genres = emptyList(),
                communityRating = 7.84f,
                criticRating = 82f,
                providerIds = mapOf("tmdb" to "27205"),
                officialRating = null,
                status = "Released",
                productionYear = 2010,
                endDate = null,
                trailer = null,
                images = JellyxImages(),
                chapters = emptyList(),
                trickplayInfo = null,
            )
        val withoutTmdb = withTmdb.copy(providerIds = mapOf("Imdb" to "tt1375666"))

        val ratingsWithTmdb = withTmdb.toNativeRatings()
        val ratingsWithoutTmdb = withoutTmdb.toNativeRatings()

        assertEquals(78, ratingsWithTmdb.tmdbPercentage)
        assertEquals(7.84f, ratingsWithTmdb.imdbRating)
        assertEquals(82, ratingsWithTmdb.rtCriticPercentage)
        assertNull(ratingsWithoutTmdb.tmdbPercentage)
    }

    @Test
    fun getEnrichedRatings_whenIsSeriesTrue_queriesTmdbTvEndpoint() = runTest {
        val itemId = UUID.randomUUID()
        coEvery { database.getRatings(itemId) } returns null

        val requestSlot = slot<Request>()
        val call = mockk<Call>()
        every { httpClient.newCall(capture(requestSlot)) } returns call
        every { call.execute() } answers {
            val req = requestSlot.captured
            Response.Builder()
                .request(req)
                .protocol(Protocol.HTTP_1_1)
                .code(200)
                .message("OK")
                .body("""{"vote_average": 8.6}""".toResponseBody("application/json".toMediaType()))
                .build()
        }

        mockkConstructor(JSONObject::class)
        try {
            every { anyConstructed<JSONObject>().optDouble("vote_average", any()) } returns 8.6

            val repository =
                RatingsRepositoryImpl(
                    database = database,
                    omdbApiKey = "",
                    tmdbApiKey = "test-tmdb-key",
                    mdblistApiKey = "",
                    httpClient = httpClient,
                )

            val ratings =
                repository.getEnrichedRatings(
                    itemId = itemId,
                    communityRating = 8.0f,
                    criticRating = null,
                    providerIds = mapOf("Tmdb" to "1399"),
                    isSeries = true,
                )

            assertTrue(
                requestSlot.captured.url.toString().startsWith("https://api.themoviedb.org/3/tv/1399")
            )
            assertEquals(86, ratings.tmdbPercentage)
        } finally {
            unmockkConstructor(JSONObject::class)
        }
    }
}
