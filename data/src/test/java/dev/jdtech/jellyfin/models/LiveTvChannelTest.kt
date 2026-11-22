package dev.jdtech.jellyfin.models

import android.content.Context
import android.net.Uri
import dev.jdtech.jellyfin.api.JellyfinApi
import dev.jdtech.jellyfin.database.ServerDatabaseDao
import dev.jdtech.jellyfin.repository.JellyfinRepository
import dev.jdtech.jellyfin.repository.JellyfinRepositoryImpl
import dev.jdtech.jellyfin.settings.domain.AppPreferences
import io.mockk.coEvery
import io.mockk.coVerify
import io.mockk.every
import io.mockk.mockk
import io.mockk.mockkStatic
import io.mockk.unmockkStatic
import java.time.LocalDateTime
import java.util.UUID
import kotlinx.coroutines.test.runTest
import org.jellyfin.sdk.api.client.Response
import org.jellyfin.sdk.api.operations.LiveTvApi
import org.jellyfin.sdk.model.api.BaseItemDto
import org.jellyfin.sdk.model.api.BaseItemDtoQueryResult
import org.jellyfin.sdk.model.api.BaseItemKind
import org.jellyfin.sdk.model.api.UserItemDataDto
import org.junit.After
import org.junit.Assert.assertEquals
import org.junit.Assert.assertFalse
import org.junit.Assert.assertNotNull
import org.junit.Assert.assertTrue
import org.junit.Before
import org.junit.Test

class LiveTvChannelTest {

    @Before
    fun setup() {
        mockkStatic(Uri::class)
        every { Uri.parse(any()) } returns mockk(relaxed = true)
    }

    @After
    fun tearDown() {
        unmockkStatic(Uri::class)
    }

    @Test
    fun `CollectionType supported includes LiveTv and parses livetv`() {
        assertTrue(CollectionType.supported.contains(CollectionType.LiveTv))
        assertEquals(CollectionType.LiveTv, CollectionType.fromString("livetv"))
    }

    @Test
    fun `BaseItemDto toJellyxChannel maps channelNumber and currentProgram with canDownload false`() =
        runTest {
            val repo = mockk<JellyfinRepository>(relaxed = true)
            every { repo.getBaseUrl() } returns "https://demo.jellyx.tv"
            val channelId = UUID.randomUUID()
            val programId = UUID.randomUUID()

            val programDto =
                mockk<BaseItemDto>(relaxed = true) {
                    every { id } returns programId
                    every { name } returns "Noticiero Nocturno"
                    every { overview } returns "Resumen del día"
                    every { startDate } returns LocalDateTime.of(2026, 11, 13, 20, 0)
                    every { endDate } returns LocalDateTime.of(2026, 11, 13, 21, 0)
                    every { this@mockk.channelId } returns channelId
                }
            val channelDto =
                mockk<BaseItemDto>(relaxed = true) {
                    every { id } returns channelId
                    every { name } returns "Canal 5 HD"
                    every { channelNumber } returns "5.1"
                    every { currentProgram } returns programDto
                }

            val channel = channelDto.toJellyxChannel(repo)

            assertEquals(channelId, channel.id)
            assertEquals("Canal 5 HD", channel.name)
            assertEquals("5.1", channel.channelNumber)
            assertNotNull(channel.currentProgram)
            assertEquals("Noticiero Nocturno", channel.currentProgram?.name)
            assertTrue(channel.canPlay)
            assertFalse(channel.canDownload)
        }

    @Test
    fun `BaseItemDto toJellyxItem supports TV_CHANNEL`() = runTest {
        val repo = mockk<JellyfinRepository>(relaxed = true)
        every { repo.getBaseUrl() } returns "https://demo.jellyx.tv"
        val channelId = UUID.randomUUID()
        val dto =
            mockk<BaseItemDto>(relaxed = true) {
                every { id } returns channelId
                every { type } returns BaseItemKind.TV_CHANNEL
                every { name } returns "ESPN"
                every { channelNumber } returns "102"
                every { currentProgram } returns null
            }

        val item = dto.toJellyxItem(repo)

        assertTrue(item is JellyxChannel)
        assertEquals("102", (item as JellyxChannel).channelNumber)
    }

    @Test
    fun `getLiveTvChannels sorts favorite channels first then by number and name`() = runTest {
        val context = mockk<Context>(relaxed = true)
        val jellyfinApi = mockk<JellyfinApi>(relaxed = true)
        val liveTvApi = mockk<LiveTvApi>()
        val database = mockk<ServerDatabaseDao>(relaxed = true)
        val appPreferences = mockk<AppPreferences>(relaxed = true)
        val userId = UUID.randomUUID()

        every { jellyfinApi.userId } returns userId
        every { jellyfinApi.liveTvApi } returns liveTvApi
        every { jellyfinApi.api.baseUrl } returns "https://demo.jellyx.tv"

        val ch1Id = UUID.randomUUID()
        val ch2Id = UUID.randomUUID()
        val ch3Id = UUID.randomUUID()

        val ch1 =
            mockk<BaseItemDto>(relaxed = true) {
                every { id } returns ch1Id
                every { name } returns "Canal Uno"
                every { channelNumber } returns "1"
                every { userData } returns mockk<UserItemDataDto>(relaxed = true) {
                    every { isFavorite } returns false
                }
            }
        val ch2 =
            mockk<BaseItemDto>(relaxed = true) {
                every { id } returns ch2Id
                every { name } returns "Canal Dos"
                every { channelNumber } returns "20"
                every { userData } returns mockk<UserItemDataDto>(relaxed = true) {
                    every { isFavorite } returns true
                }
            }
        val ch3 =
            mockk<BaseItemDto>(relaxed = true) {
                every { id } returns ch3Id
                every { name } returns "Canal Tres"
                every { channelNumber } returns "5"
                every { userData } returns mockk<UserItemDataDto>(relaxed = true) {
                    every { isFavorite } returns true
                }
            }

        coEvery {
            liveTvApi.getLiveTvChannels(
                userId = userId,
                addCurrentProgram = true,
                enableUserData = true,
                enableImages = true,
                fields = any(),
            )
        } returns
            Response(
                content =
                    BaseItemDtoQueryResult(
                        items = listOf(ch1, ch2, ch3),
                        totalRecordCount = 3,
                        startIndex = 0,
                    ),
                status = 200,
                headers = emptyMap(),
            )

        coEvery {
            liveTvApi.getLiveTvPrograms(
                channelIds = any(),
                userId = any(),
                hasAired = any(),
                minEndDate = any(),
                maxStartDate = any(),
                enableImages = any(),
                enableUserData = any(),
                fields = any(),
            )
        } returns
            Response(
                content =
                    BaseItemDtoQueryResult(
                        items = emptyList(),
                        totalRecordCount = 0,
                        startIndex = 0,
                    ),
                status = 200,
                headers = emptyMap(),
            )

        val repo = JellyfinRepositoryImpl(context, jellyfinApi, database, appPreferences)
        val sorted = repo.getLiveTvChannels()

        assertEquals(listOf(ch3Id, ch2Id, ch1Id), sorted.map { it.id })
    }

    @Test
    fun `getLiveTvChannels resolves currentProgram and nextProgram from getLiveTvPrograms chunks`() =
        runTest {
            val context = mockk<Context>(relaxed = true)
            val jellyfinApi = mockk<JellyfinApi>(relaxed = true)
            val liveTvApi = mockk<LiveTvApi>()
            val database = mockk<ServerDatabaseDao>(relaxed = true)
            val appPreferences = mockk<AppPreferences>(relaxed = true)
            val userId = UUID.randomUUID()

            every { jellyfinApi.userId } returns userId
            every { jellyfinApi.liveTvApi } returns liveTvApi
            every { jellyfinApi.api.baseUrl } returns "https://demo.jellyx.tv"

            val targetChannelId = UUID.randomUUID()
            val extraChannels =
                (1..40).map { index ->
                    val cid = UUID.randomUUID()
                    mockk<BaseItemDto>(relaxed = true) {
                        every { id } returns cid
                        every { name } returns "Canal Extra $index"
                        every { channelNumber } returns "${index + 1}"
                        every { currentProgram } returns null
                    }
                }
            val targetChannelDto =
                mockk<BaseItemDto>(relaxed = true) {
                    every { id } returns targetChannelId
                    every { name } returns "Canal Principal"
                    every { channelNumber } returns "1"
                    every { currentProgram } returns null
                }

            val now = LocalDateTime.now()
            val currentProgDto =
                mockk<BaseItemDto>(relaxed = true) {
                    every { id } returns UUID.randomUUID()
                    every { name } returns "Programa Actual"
                    every { startDate } returns now.minusMinutes(15)
                    every { endDate } returns now.plusMinutes(45)
                    every { channelId } returns targetChannelId
                }
            val nextProgDto =
                mockk<BaseItemDto>(relaxed = true) {
                    every { id } returns UUID.randomUUID()
                    every { name } returns "Programa Siguiente"
                    every { startDate } returns now.plusMinutes(45)
                    every { endDate } returns now.plusMinutes(105)
                    every { channelId } returns targetChannelId
                }

            coEvery {
                liveTvApi.getLiveTvChannels(
                    userId = userId,
                    addCurrentProgram = true,
                    enableUserData = true,
                    enableImages = true,
                    fields = any(),
                )
            } returns
                Response(
                    content =
                        BaseItemDtoQueryResult(
                            items = extraChannels + targetChannelDto,
                            totalRecordCount = 41,
                            startIndex = 0,
                        ),
                    status = 200,
                    headers = emptyMap(),
                )

            coEvery {
                liveTvApi.getLiveTvPrograms(
                    channelIds = match { it.size == 40 },
                    userId = userId,
                    hasAired = false,
                    minEndDate = any(),
                    maxStartDate = any(),
                    enableImages = false,
                    enableUserData = false,
                    fields = any(),
                )
            } returns
                Response(
                    content =
                        BaseItemDtoQueryResult(
                            items = emptyList(),
                            totalRecordCount = 0,
                            startIndex = 0,
                        ),
                    status = 200,
                    headers = emptyMap(),
                )

            coEvery {
                liveTvApi.getLiveTvPrograms(
                    channelIds = listOf(targetChannelId),
                    userId = userId,
                    hasAired = false,
                    minEndDate = any(),
                    maxStartDate = any(),
                    enableImages = false,
                    enableUserData = false,
                    fields = any(),
                )
            } returns
                Response(
                    content =
                        BaseItemDtoQueryResult(
                            items = listOf(nextProgDto, currentProgDto),
                            totalRecordCount = 2,
                            startIndex = 0,
                        ),
                    status = 200,
                    headers = emptyMap(),
                )

            val repo = JellyfinRepositoryImpl(context, jellyfinApi, database, appPreferences)
            val channels = repo.getLiveTvChannels()
            val target = channels.first { it.id == targetChannelId }

            assertEquals("Programa Actual", target.currentProgram?.name)
            assertEquals("Programa Siguiente", target.nextProgram?.name)
            coVerify(exactly = 2) {
                liveTvApi.getLiveTvPrograms(
                    channelIds = any(),
                    userId = userId,
                    hasAired = false,
                    minEndDate = any(),
                    maxStartDate = any(),
                    enableImages = false,
                    enableUserData = false,
                    fields = any(),
                )
            }
        }

    @Test
    fun `getLiveTvChannel resolves nextProgram`() = runTest {
        val context = mockk<Context>(relaxed = true)
        val jellyfinApi = mockk<JellyfinApi>(relaxed = true)
        val liveTvApi = mockk<LiveTvApi>()
        val database = mockk<ServerDatabaseDao>(relaxed = true)
        val appPreferences = mockk<AppPreferences>(relaxed = true)
        val userId = UUID.randomUUID()
        val channelId = UUID.randomUUID()

        every { jellyfinApi.userId } returns userId
        every { jellyfinApi.liveTvApi } returns liveTvApi
        every { jellyfinApi.api.baseUrl } returns "https://demo.jellyx.tv"

        val now = LocalDateTime.now()
        val currentProgDto =
            mockk<BaseItemDto>(relaxed = true) {
                every { id } returns UUID.randomUUID()
                every { name } returns "Noticiero"
                every { startDate } returns now.minusMinutes(10)
                every { endDate } returns now.plusMinutes(20)
                every { this@mockk.channelId } returns channelId
            }
        val nextProgDto =
            mockk<BaseItemDto>(relaxed = true) {
                every { id } returns UUID.randomUUID()
                every { name } returns "Cine Nocturno"
                every { startDate } returns now.plusMinutes(20)
                every { endDate } returns now.plusMinutes(110)
                every { this@mockk.channelId } returns channelId
            }
        val channelDto =
            mockk<BaseItemDto>(relaxed = true) {
                every { id } returns channelId
                every { name } returns "Canal 7"
                every { channelNumber } returns "7"
                every { currentProgram } returns currentProgDto
            }

        coEvery { liveTvApi.getChannel(channelId = channelId, userId = userId) } returns
            Response(content = channelDto, status = 200, headers = emptyMap())

        coEvery {
            liveTvApi.getLiveTvPrograms(
                channelIds = listOf(channelId),
                userId = userId,
                hasAired = false,
                minEndDate = any(),
                maxStartDate = any(),
                limit = 6,
                enableImages = false,
                enableUserData = false,
                fields = any(),
            )
        } returns
            Response(
                content =
                    BaseItemDtoQueryResult(
                        items = listOf(currentProgDto, nextProgDto),
                        totalRecordCount = 2,
                        startIndex = 0,
                    ),
                status = 200,
                headers = emptyMap(),
            )

        val repo = JellyfinRepositoryImpl(context, jellyfinApi, database, appPreferences)
        val channel = repo.getLiveTvChannel(channelId)

        assertEquals("Noticiero", channel.currentProgram?.name)
        assertEquals("Cine Nocturno", channel.nextProgram?.name)
    }
}
