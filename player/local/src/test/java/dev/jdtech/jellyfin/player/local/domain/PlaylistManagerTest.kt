package dev.jdtech.jellyfin.player.local.domain

import android.net.Uri
import dev.jdtech.jellyfin.models.JellyxChannel
import dev.jdtech.jellyfin.models.JellyxEpisode
import dev.jdtech.jellyfin.models.JellyxImages
import dev.jdtech.jellyfin.models.JellyxMediaStream
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
import org.jellyfin.sdk.model.api.MediaStreamType
import org.junit.After
import org.junit.Assert.assertEquals
import org.junit.Assert.assertFalse
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

    private fun buildSubtitleStream(codec: String, isExternal: Boolean = false, path: String? = null): JellyxMediaStream {
        return JellyxMediaStream(
            title = "Sub",
            displayTitle = null,
            language = "spa",
            type = MediaStreamType.SUBTITLE,
            codec = codec,
            isExternal = isExternal,
            path = path,
            channelLayout = null,
            videoRangeType = null,
            height = null,
            width = null,
            videoDoViTitle = null,
        )
    }

    @Test
    fun `toPlayerItem sets hasAssSubtitles true for ASS or SSA streams and false for SRT-only or subtitle-less sources`() =
        runTest {
            val seriesId = UUID.randomUUID()
            val seasonId = UUID.randomUUID()
            val epAss = buildEpisode(UUID.randomUUID(), 1, seriesId, seasonId)
            val epSsaExt = buildEpisode(UUID.randomUUID(), 2, seriesId, seasonId)
            val epSrt = buildEpisode(UUID.randomUUID(), 3, seriesId, seasonId)
            val epNoSubs = buildEpisode(UUID.randomUUID(), 4, seriesId, seasonId)

            val assSource =
                buildLiveSource(epAss.id).copy(
                    path = "https://jellyx.test/ep1.mkv",
                    mediaStreams = listOf(buildSubtitleStream("ass")),
                )
            val ssaExtSource =
                buildLiveSource(epSsaExt.id).copy(
                    path = "https://jellyx.test/ep2.mkv",
                    mediaStreams = listOf(
                        buildSubtitleStream(
                            codec = "ssa",
                            isExternal = true,
                            path = "https://jellyx.test/ep2.ssa?api_key=123",
                        )
                    ),
                )
            val srtSource =
                buildLiveSource(epSrt.id).copy(
                    path = "https://jellyx.test/ep3.mkv",
                    mediaStreams = listOf(
                        buildSubtitleStream(
                            codec = "subrip",
                            isExternal = true,
                            path = "https://jellyx.test/ep3.srt",
                        )
                    ),
                )
            val noSubsSource =
                buildLiveSource(epNoSubs.id).copy(
                    path = "https://jellyx.test/ep4.mkv",
                    mediaStreams = emptyList(),
                )

            coEvery { repository.getEpisode(epAss.id) } returns epAss
            coEvery { repository.getEpisode(epSsaExt.id) } returns epSsaExt
            coEvery { repository.getEpisode(epSrt.id) } returns epSrt
            coEvery { repository.getEpisode(epNoSubs.id) } returns epNoSubs
            coEvery { repository.getEpisodes(seriesId = seriesId, seasonId = seasonId, fields = any()) } returns
                listOf(epAss, epSsaExt, epSrt, epNoSubs)

            coEvery { repository.getMediaSources(epAss.id, true) } returns listOf(assSource)
            coEvery { smartSourceSelector.selectSource(listOf(assSource), any()) } returns assSource
            coEvery { repository.getMediaSources(epSsaExt.id, true) } returns listOf(ssaExtSource)
            coEvery { smartSourceSelector.selectSource(listOf(ssaExtSource), any()) } returns ssaExtSource
            coEvery { repository.getMediaSources(epSrt.id, true) } returns listOf(srtSource)
            coEvery { smartSourceSelector.selectSource(listOf(srtSource), any()) } returns srtSource
            coEvery { repository.getMediaSources(epNoSubs.id, true) } returns listOf(noSubsSource)
            coEvery { smartSourceSelector.selectSource(listOf(noSubsSource), any()) } returns noSubsSource

            val assItem = playlistManager.getInitialItem(epAss.id, BaseItemKind.EPISODE)
            assertTrue(assItem?.hasAssSubtitles == true)

            val ssaItem = playlistManager.getInitialItem(epSsaExt.id, BaseItemKind.EPISODE)
            assertTrue(ssaItem?.hasAssSubtitles == true)

            val srtItem = playlistManager.getInitialItem(epSrt.id, BaseItemKind.EPISODE)
            assertFalse(srtItem?.hasAssSubtitles == true)

            val noSubsItem = playlistManager.getInitialItem(epNoSubs.id, BaseItemKind.EPISODE)
            assertFalse(noSubsItem?.hasAssSubtitles == true)
        }
}

