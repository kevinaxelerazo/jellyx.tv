package dev.jdtech.jellyfin.repository

import dev.jdtech.jellyfin.api.JellyfinApi
import dev.jdtech.jellyfin.database.ServerDatabaseDao
import dev.jdtech.jellyfin.models.JellyxChapter
import dev.jdtech.jellyfin.models.JellyxSegmentType
import io.mockk.coEvery
import io.mockk.every
import io.mockk.mockk
import java.util.UUID
import kotlinx.coroutines.test.runTest
import okhttp3.OkHttpClient
import org.jellyfin.sdk.api.client.Response
import org.jellyfin.sdk.api.operations.MediaSegmentApi
import org.jellyfin.sdk.model.api.MediaSegmentDto
import org.jellyfin.sdk.model.api.MediaSegmentDtoQueryResult
import org.jellyfin.sdk.model.api.MediaSegmentType
import org.junit.Assert.assertEquals
import org.junit.Before
import org.junit.Test

class SegmentResolverTest {
    private lateinit var jellyfinApi: JellyfinApi
    private lateinit var database: ServerDatabaseDao
    private lateinit var mediaSegmentApi: MediaSegmentApi
    private lateinit var httpClient: OkHttpClient

    @Before
    fun setUp() {
        jellyfinApi = mockk(relaxed = true)
        database = mockk(relaxed = true)
        mediaSegmentApi = mockk()
        httpClient = mockk(relaxed = true)

        every { jellyfinApi.mediaSegmentApi } returns mediaSegmentApi
    }

    @Test
    fun resolveSegments_whenOfficialMediaSegmentApiReturnsData_usesOfficialSegments() = runTest {
        val itemId = UUID.randomUUID()
        coEvery { database.getSegments(itemId) } returns emptyList()
        val dto =
            MediaSegmentDto(
                id = UUID.randomUUID(),
                itemId = itemId,
                type = MediaSegmentType.INTRO,
                startTicks = 150_000_000L, // 15_000 ms
                endTicks = 1_050_000_000L, // 105_000 ms
            )
        coEvery { mediaSegmentApi.getItemSegments(itemId) } returns
            Response(
                content =
                    MediaSegmentDtoQueryResult(
                        items = listOf(dto),
                        totalRecordCount = 1,
                        startIndex = 0,
                    ),
                status = 200,
                headers = emptyMap(),
            )

        val resolver = SegmentResolver(jellyfinApi, database, httpClient)
        val segments = resolver.resolveSegments(itemId)

        assertEquals(1, segments.size)
        assertEquals(JellyxSegmentType.INTRO, segments[0].type)
        assertEquals(15_000L, segments[0].startTicks)
        assertEquals(105_000L, segments[0].endTicks)
    }

    @Test
    fun resolveSegments_whenOfficialApiEmpty_parsesIntroSkipperPluginJsonCorrectly() {
        val resolver = SegmentResolver(jellyfinApi, database, httpClient)
        val json =
            """{"Introduction":{"IntroStart":42.5,"IntroEnd":132.0},"Credits":{"IntroStart":1320.0,"IntroEnd":1410.0}}"""

        val segments = resolver.parseIntroSkipperJson(json)

        assertEquals(2, segments.size)
        assertEquals(JellyxSegmentType.INTRO, segments[0].type)
        assertEquals(42_500L, segments[0].startTicks)
        assertEquals(132_000L, segments[0].endTicks)
        assertEquals(JellyxSegmentType.OUTRO, segments[1].type)
        assertEquals(1_320_000L, segments[1].startTicks)
        assertEquals(1_410_000L, segments[1].endTicks)
    }

    @Test
    fun resolveSegments_whenBothApisEmpty_extractsIntroAndOutroFromNamedChapters() = runTest {
        val itemId = UUID.randomUUID()
        coEvery { database.getSegments(itemId) } returns emptyList()
        coEvery { mediaSegmentApi.getItemSegments(itemId) } returns
            Response(
                content =
                    MediaSegmentDtoQueryResult(
                        items = emptyList(),
                        totalRecordCount = 0,
                        startIndex = 0,
                    ),
                status = 200,
                headers = emptyMap(),
            )
        every { jellyfinApi.api.baseUrl } returns ""

        val chapters =
            listOf(
                JellyxChapter(startPosition = 0L, name = "Cold Open"),
                JellyxChapter(startPosition = 65_000L, name = "Opening"),
                JellyxChapter(startPosition = 155_000L, name = "Episode"),
                JellyxChapter(startPosition = 1_350_000L, name = "Ending"),
            )

        val resolver = SegmentResolver(jellyfinApi, database, httpClient)
        val segments =
            resolver.resolveSegments(itemId, chapters = chapters, durationMs = 1_440_000L)

        assertEquals(2, segments.size)
        assertEquals(JellyxSegmentType.INTRO, segments[0].type)
        assertEquals(65_000L, segments[0].startTicks)
        assertEquals(155_000L, segments[0].endTicks)
        assertEquals(JellyxSegmentType.OUTRO, segments[1].type)
        assertEquals(1_350_000L, segments[1].startTicks)
        assertEquals(1_440_000L, segments[1].endTicks)
    }
}
