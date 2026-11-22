package dev.jdtech.jellyfin.player.local.domain

import android.net.Uri
import dev.jdtech.jellyfin.models.JellyxChannel
import dev.jdtech.jellyfin.models.JellyxEpisode
import dev.jdtech.jellyfin.models.JellyxImages
import dev.jdtech.jellyfin.models.JellyxSource
import dev.jdtech.jellyfin.models.JellyxSourceType
import dev.jdtech.jellyfin.repository.JellyfinRepository
import io.mockk.coEvery
import io.mockk.coVerify
import io.mockk.every
import io.mockk.mockk
import io.mockk.mockkStatic
import io.mockk.unmockkStatic
import java.util.UUID
import kotlinx.coroutines.test.runTest
import org.jellyfin.sdk.model.api.BaseItemKind
import org.junit.After
import org.junit.Assert.assertEquals
import org.junit.Assert.assertNotNull
import org.junit.Assert.assertNull
import org.junit.Assert.assertTrue
import org.junit.Before
import org.junit.Test

class PlaylistManagerTest {
    private lateinit var repository: JellyfinRepository
    private lateinit var smartSourceSelector: SmartSourceSelector
    private lateinit var playlistManager: PlaylistManager

    @Before
    fun setup() {
        mockkStatic(Uri::class)
        every { Uri.parse(any()) } returns mockk(relaxed = true)
        repository = mockk(relaxed = true)
        smartSourceSelector = mockk()
        playlistManager = PlaylistManager(repository, smartSourceSelector)
    }

    @After
    fun tearDown() {
        unmockkStatic(Uri::class)
    }

    private fun buildChannel(id: UUID, name: String, number: String, favorite: Boolean): JellyxChannel {
        return JellyxChannel(
            id = id,
            name = name,
            originalTitle = null,
            overview = "",
            channelNumber = number,
            currentProgram = null,
            favorite = favorite,
        )
    }

    private fun buildLiveSource(channelId: UUID): JellyxSource {
        return JellyxSource(
            id = "src-$channelId",
            name = "Live HLS",
            type = JellyxSourceType.REMOTE,
            path = "https://jellyx.test/live/$channelId.m3u8",
            size = 0L,
            mediaStreams = emptyList(),
        )
    }

    @Test
    fun `TV channel zapping reuses cached channels and updateChannelFavorite sorts favorites first`() =
        runTest {
            val ch1Id = UUID.randomUUID()
            val ch2Id = UUID.randomUUID()
            val ch3Id = UUID.randomUUID()
            val channels =
                listOf(
                    buildChannel(ch1Id, "Canal 1", "1", favorite = false),
                    buildChannel(ch2Id, "Canal 2", "2", favorite = false),
                    buildChannel(ch3Id, "Canal 3", "3", favorite = true),
                )
            val source1 = buildLiveSource(ch1Id)
            val source2 = buildLiveSource(ch2Id)

            coEvery { repository.getLiveTvChannels() } returns channels
            coEvery { repository.getMediaSources(ch1Id, true) } returns listOf(source1)
            coEvery { repository.getMediaSources(ch2Id, true) } returns listOf(source2)
            coEvery { smartSourceSelector.selectSource(listOf(source1), any()) } returns source1
            coEvery { smartSourceSelector.selectSource(listOf(source2), any()) } returns source2

            val first =
                playlistManager.getInitialItem(
                    itemId = ch1Id,
                    itemKind = BaseItemKind.TV_CHANNEL,
                    startFromBeginning = true,
                )
            assertEquals(ch1Id, first?.itemId)

            val second =
                playlistManager.getInitialItem(
                    itemId = ch2Id,
                    itemKind = BaseItemKind.TV_CHANNEL,
                    startFromBeginning = true,
                )
            assertEquals(ch2Id, second?.itemId)
            coVerify(exactly = 1) { repository.getLiveTvChannels() }

            val updated = playlistManager.updateChannelFavorite(ch2Id, favorite = true)
            assertEquals(listOf(ch2Id, ch3Id, ch1Id), updated.map { it.id })
            assertTrue(playlistManager.getCurrentChannel()?.favorite == true)
            assertEquals(ch2Id, playlistManager.getCurrentChannel()?.id)
        }

    private fun buildEpisode(
        id: UUID,
        index: Int,
        seriesId: UUID,
        seasonId: UUID,
    ): JellyxEpisode {
        return JellyxEpisode(
            id = id,
            name = "Episode $index",
            originalTitle = null,
            overview = "",
            indexNumber = index,
            indexNumberEnd = null,
            parentIndexNumber = 1,
            sources = emptyList(),
            played = false,
            favorite = false,
            canPlay = true,
            canDownload = true,
            runtimeTicks = 0L,
            playbackPositionTicks = 0L,
            premiereDate = null,
            seriesId = seriesId,
            seriesName = "Vision Quest",
            seasonId = seasonId,
            seasonName = "Season 1",
            communityRating = null,
            people = emptyList(),
            images = JellyxImages(),
            chapters = emptyList(),
            trickplayInfo = null,
        )
    }

    @Test
    fun `resetLoadedNeighbors preserves target episode and allows reloading prev and next cleanly`() =
        runTest {
            val seriesId = UUID.randomUUID()
            val seasonId = UUID.randomUUID()
            val ep3 = buildEpisode(UUID.randomUUID(), 3, seriesId, seasonId)
            val ep4 = buildEpisode(UUID.randomUUID(), 4, seriesId, seasonId)
            val ep5 = buildEpisode(UUID.randomUUID(), 5, seriesId, seasonId)
            val episodes = listOf(ep3, ep4, ep5)

            coEvery { repository.getEpisode(ep4.id) } returns ep4
            coEvery { repository.getEpisodes(seriesId = seriesId, seasonId = seasonId, fields = any()) } returns episodes
            listOf(ep3, ep4, ep5).forEach { ep ->
                val src = buildLiveSource(ep.id).copy(name = "1080p", path = "https://jellyx.test/${ep.id}.mkv")
                coEvery { repository.getMediaSources(ep.id, true) } returns listOf(src)
                coEvery { smartSourceSelector.selectSource(listOf(src), any()) } returns src
            }

            val initial =
                playlistManager.getInitialItem(
                    itemId = ep4.id,
                    itemKind = BaseItemKind.EPISODE,
                    startFromBeginning = false,
                )
            assertEquals(ep4.id, initial?.itemId)
            assertEquals(3, playlistManager.getSeasonEpisodes().size)
            assertEquals(seasonId to seriesId, playlistManager.getCurrentEpisodeSeasonAndSeries())

            assertEquals(ep3.id, playlistManager.getPreviousPlayerItem()?.itemId)
            assertEquals(ep5.id, playlistManager.getNextPlayerItem()?.itemId)

            // Calling again before reset returns null (already loaded)
            assertNull(playlistManager.getPreviousPlayerItem())
            assertNull(playlistManager.getNextPlayerItem())

            // Resetting neighbors keeps only ep4 and allows prev/next to be loaded again
            playlistManager.resetLoadedNeighbors(ep4.id)
            assertEquals(ep3.id, playlistManager.getPreviousPlayerItem()?.itemId)
            assertEquals(ep5.id, playlistManager.getNextPlayerItem()?.itemId)

            val refreshed = playlistManager.refreshItemSource(ep4.id, 12_500L)
            assertNotNull(refreshed)
            assertEquals(ep4.id, refreshed?.itemId)
            assertEquals(12_500L, refreshed?.playbackPosition)
        }
}

