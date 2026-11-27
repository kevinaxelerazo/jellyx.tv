package dev.jdtech.jellyfin.player.local.presentation

import android.app.Application
import android.content.SharedPreferences
import androidx.lifecycle.SavedStateHandle
import androidx.media3.common.C
import androidx.media3.common.Format
import androidx.media3.common.MediaItem
import androidx.media3.common.Player
import androidx.media3.common.TrackGroup
import androidx.media3.common.Tracks
import dev.jdtech.jellyfin.models.JellyxSegment
import dev.jdtech.jellyfin.models.JellyxSegmentType
import dev.jdtech.jellyfin.player.core.domain.models.PlayerItem
import dev.jdtech.jellyfin.player.local.R
import dev.jdtech.jellyfin.player.local.cache.JellyxCacheManager
import dev.jdtech.jellyfin.player.local.domain.DeviceCapabilityInspector
import dev.jdtech.jellyfin.player.local.domain.PlaylistManager
import dev.jdtech.jellyfin.repository.JellyfinRepository
import dev.jdtech.jellyfin.session.SessionAlertEvent
import dev.jdtech.jellyfin.session.SessionEventManager
import dev.jdtech.jellyfin.settings.domain.AppPreferences
import io.mockk.coEvery
import io.mockk.coVerify
import io.mockk.every
import io.mockk.mockk
import io.mockk.verify
import io.mockk.verifyOrder
import java.util.UUID
import kotlinx.coroutines.CompletableDeferred
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.ExperimentalCoroutinesApi
import kotlinx.coroutines.flow.MutableSharedFlow
import kotlinx.coroutines.flow.first
import kotlinx.coroutines.test.UnconfinedTestDispatcher
import kotlinx.coroutines.test.resetMain
import kotlinx.coroutines.test.runTest
import kotlinx.coroutines.test.setMain
import org.jellyfin.sdk.model.api.BaseItemKind
import org.junit.After
import org.junit.Assert.assertEquals
import org.junit.Assert.assertFalse
import org.junit.Assert.assertNotNull
import org.junit.Assert.assertNull
import org.junit.Assert.assertTrue
import org.junit.Before
import org.junit.Test

@OptIn(ExperimentalCoroutinesApi::class)
class PlayerViewModelSessionTest {

    private lateinit var testDispatcher: kotlinx.coroutines.test.TestDispatcher
    private lateinit var mockPlayer: Player
    private lateinit var playlistManager: PlaylistManager
    private lateinit var repository: JellyfinRepository
    private lateinit var sharedPreferences: SharedPreferences
    private lateinit var appPreferences: AppPreferences
    private lateinit var capabilityInspector: DeviceCapabilityInspector
    private lateinit var sessionEventManager: SessionEventManager
    private lateinit var eventsFlow: MutableSharedFlow<SessionAlertEvent>
    private lateinit var viewModel: PlayerViewModel

    @Before
    fun setup() {
        testDispatcher = UnconfinedTestDispatcher()
        Dispatchers.setMain(testDispatcher)
        mockPlayer = mockk(relaxed = true)
        eventsFlow = MutableSharedFlow(extraBufferCapacity = 8)

        val app = mockk<Application>(relaxed = true)
        playlistManager = mockk(relaxed = true)
        repository = mockk(relaxed = true)
        sharedPreferences =
            mockk(relaxed = true) {
                every { getBoolean("pref_player_media_segments_skip_button", true) } returns false
                every {
                    getStringSet(
                        "pref_player_media_segments_skip_button_type",
                        setOf("INTRO", "OUTRO"),
                    )
                } returns emptySet()
                every { getLong("pref_player_media_segments_skip_button_duration", 5L) } returns 0L
                every { getBoolean("pref_player_media_segments_auto_skip", false) } returns false
                every {
                    getStringSet(
                        "pref_player_media_segments_auto_skip_type",
                        setOf("INTRO", "OUTRO"),
                    )
                } returns emptySet()
                every { getString("pref_player_media_segments_auto_skip_mode", "always") } returns
                    "always"
                every { getString("pref_player_backend", "exoplayer") } returns "exoplayer"
                every { getBoolean("pref_player_smart_mpv_live_tv", true) } returns true
                every { getBoolean("pref_player_smart_mpv_ass_subs", true) } returns true
            }
        appPreferences = AppPreferences(sharedPreferences)
        capabilityInspector = mockk(relaxed = true)
        sessionEventManager = mockk(relaxed = true)
        every { sessionEventManager.events } returns eventsFlow
        every { sessionEventManager.syncPlayEvents } returns MutableSharedFlow()

        viewModel = createViewModel(app)
    }

    private fun createViewModel(
        app: Application = mockk(relaxed = true),
        prefs: AppPreferences = appPreferences,
        cacheManager: JellyxCacheManager? = null,
    ): PlayerViewModel {
        return PlayerViewModel(
            application = app,
            playlistManager = playlistManager,
            repository = repository,
            appPreferences = prefs,
            capabilityInspector = capabilityInspector,
            sessionEventManager = sessionEventManager,
            savedStateHandle = SavedStateHandle(),
            testPlayer = mockPlayer,
            cacheManager = cacheManager,
        )
    }

    @After
    fun tearDown() {
        Dispatchers.resetMain()
    }

    @Test
    fun `ForceStopPlayback pauses, stops, clears items, blocks playback, and exits on dismiss`() =
        runTest {
            eventsFlow.emit(SessionAlertEvent.ForceStopPlayback)

            verify { mockPlayer.pause() }
            verify { mockPlayer.stop() }
            verify { mockPlayer.clearMediaItems() }
            val alert = viewModel.uiState.value.concurrentSessionAlert
            assertNotNull(alert)
            assertEquals(true, alert?.isConcurrentStreamBlock)
            assertEquals(true, viewModel.uiState.value.isPlaybackBlockedByLimit)

            viewModel.dismissConcurrentSessionAlert()
            assertNull(viewModel.uiState.value.concurrentSessionAlert)
            val emittedEvent = viewModel.eventsChannelFlow.first()
            assertEquals(PlayerEvents.NavigateBack, emittedEvent)
        }

    @Test
    fun `DisplayMessage with concurrent flag stops player and updates uiState`() = runTest {
        val msg =
            SessionAlertEvent.DisplayMessage(
                header = "Alerta",
                text = "Límite de pantalla",
                isConcurrentStreamBlock = true,
            )

        eventsFlow.emit(msg)

        verify { mockPlayer.pause() }
        verify { mockPlayer.stop() }
        verify { mockPlayer.clearMediaItems() }
        assertEquals(msg, viewModel.uiState.value.concurrentSessionAlert)
        assertEquals(true, viewModel.uiState.value.isPlaybackBlockedByLimit)
    }

    @Test
    fun `findMatchingTrackIndex matches language and label across episode or player switches`() {
        val g0 =
            mockk<Tracks.Group>(relaxed = true) {
                every { type } returns C.TRACK_TYPE_TEXT
                every { isSupported } returns true
                every { mediaTrackGroup } returns
                    TrackGroup(
                        Format.Builder().setLanguage("eng").setLabel("English").build()
                    )
            }
        val g1 =
            mockk<Tracks.Group>(relaxed = true) {
                every { type } returns C.TRACK_TYPE_TEXT
                every { isSupported } returns true
                every { mediaTrackGroup } returns
                    TrackGroup(
                        Format.Builder().setLanguage("spa").setLabel("Español (España)").build()
                    )
            }
        val g2 =
            mockk<Tracks.Group>(relaxed = true) {
                every { type } returns C.TRACK_TYPE_TEXT
                every { isSupported } returns true
                every { mediaTrackGroup } returns
                    TrackGroup(
                        Format.Builder().setLanguage("spa").setLabel("Español (Latino)").build()
                    )
            }
        val tracks = Tracks(listOf(g0, g1, g2))

        val exactPref =
            PlayerViewModel.SessionTrackPreference(
                disabled = false,
                language = g2.mediaTrackGroup.getFormat(0).language,
                label = "Español (Latino)",
                fallbackIndex = 0,
            )
        assertEquals(2, viewModel.findMatchingTrackIndex(tracks, C.TRACK_TYPE_TEXT, exactPref))

        val langFallbackPref =
            PlayerViewModel.SessionTrackPreference(
                disabled = false,
                language = g1.mediaTrackGroup.getFormat(0).language,
                label = "Latinoamérica",
                fallbackIndex = 0,
            )
        assertEquals(
            1,
            viewModel.findMatchingTrackIndex(tracks, C.TRACK_TYPE_TEXT, langFallbackPref),
        )

        val disabledPref = PlayerViewModel.SessionTrackPreference(disabled = true)
        assertNull(viewModel.findMatchingTrackIndex(tracks, C.TRACK_TYPE_TEXT, disabledPref))
    }

    @Test
    fun `switchToEpisode initializes exact episode item without adjacent preloading`() = runTest {
        val targetEpisodeId = UUID.randomUUID()
        val playerItem =
            PlayerItem(
                name = "Episode 4",
                itemId = targetEpisodeId,
                mediaSourceId = "src-4",
                playbackPosition = 42_000L,
                mediaSourceUri = "https://jellyx.test/ep4.mkv",
                parentIndexNumber = 1,
                indexNumber = 4,
            )
        coEvery {
            playlistManager.getInitialItem(
                itemId = targetEpisodeId,
                itemKind = BaseItemKind.EPISODE,
                mediaSourceIndex = null,
                startFromBeginning = false,
            )
        } returns playerItem

        viewModel.switchToEpisode(targetEpisodeId)

        coVerify(exactly = 1) {
            playlistManager.getInitialItem(
                itemId = targetEpisodeId,
                itemKind = BaseItemKind.EPISODE,
                mediaSourceIndex = null,
                startFromBeginning = false,
            )
        }
        verify { mockPlayer.setMediaItems(match { it.size == 1 && it[0].mediaId == targetEpisodeId.toString() }, 0, 42_000L) }
        verify { mockPlayer.prepare() }
        verify { mockPlayer.play() }
    }

    @Test
    fun `loadAdjacentEpisodesIfNeeded inserts nextItem before previousItem at index 0 and sets hasNextEpisode`() =
        runTest {
            val ep3Id = UUID.randomUUID()
            val ep4Id = UUID.randomUUID()
            val ep5Id = UUID.randomUUID()
            val ep3 =
                PlayerItem(
                    name = "Episode 3",
                    itemId = ep3Id,
                    mediaSourceId = "src-3",
                    playbackPosition = 0L,
                    mediaSourceUri = "https://jellyx.test/ep3.mkv",
                    parentIndexNumber = 1,
                    indexNumber = 3,
                )
            val ep4 =
                PlayerItem(
                    name = "Episode 4",
                    itemId = ep4Id,
                    mediaSourceId = "src-4",
                    playbackPosition = 0L,
                    mediaSourceUri = "https://jellyx.test/ep4.mkv",
                    parentIndexNumber = 1,
                    indexNumber = 4,
                )
            val ep5 =
                PlayerItem(
                    name = "Episode 5",
                    itemId = ep5Id,
                    mediaSourceId = "src-5",
                    playbackPosition = 0L,
                    mediaSourceUri = "https://jellyx.test/ep5.mkv",
                    parentIndexNumber = 1,
                    indexNumber = 5,
                )
            coEvery {
                playlistManager.getInitialItem(
                    itemId = ep4Id,
                    itemKind = BaseItemKind.EPISODE,
                    mediaSourceIndex = null,
                    startFromBeginning = false,
                )
            } returns ep4
            coEvery { playlistManager.getNextPlayerItem() } returns ep5
            coEvery { playlistManager.getPreviousPlayerItem() } returns ep3
            every { mockPlayer.currentMediaItem } returns
                MediaItem.Builder().setMediaId(ep4Id.toString()).build()
            every { mockPlayer.hasNextMediaItem() } returns false

            viewModel.switchToEpisode(ep4Id)
            viewModel.onPlaybackStateChanged(Player.STATE_READY)

            verifyOrder {
                mockPlayer.addMediaItem(1, match { it.mediaId == ep5Id.toString() })
                mockPlayer.addMediaItem(0, match { it.mediaId == ep3Id.toString() })
            }
            assertTrue(viewModel.uiState.value.hasNextEpisode)
        }

    @Test
    fun `onMediaItemTransition clears previous episode segments immediately before network load`() =
        runTest {
            val sharedPrefs =
                mockk<SharedPreferences>(relaxed = true) {
                    every { getBoolean("pref_player_media_segments_skip_button", true) } returns
                        true
                    every {
                        getStringSet(
                            "pref_player_media_segments_skip_button_type",
                            setOf("INTRO", "OUTRO"),
                        )
                    } returns setOf("INTRO", "OUTRO")
                    every { getLong("pref_player_media_segments_skip_button_duration", 5L) } returns
                        5L
                    every { getBoolean("pref_player_media_segments_auto_skip", false) } returns
                        false
                    every {
                        getStringSet(
                            "pref_player_media_segments_auto_skip_type",
                            setOf("INTRO", "OUTRO"),
                        )
                    } returns emptySet()
                    every {
                        getString("pref_player_media_segments_auto_skip_mode", "always")
                    } returns "always"
                    every {
                        getLong("pref_player_media_segments_next_episode_threshold", 5_000L)
                    } returns 5_000L
                    every { getString("pref_player_backend", any()) } returns "exoplayer"
                }
            val localPrefs = AppPreferences(sharedPrefs)

            val segmentVm = createViewModel(prefs = localPrefs)
            val ep1Id = UUID.randomUUID()
            val ep2Id = UUID.randomUUID()
            val ep1 =
                PlayerItem(
                    name = "Episode 1",
                    itemId = ep1Id,
                    mediaSourceId = "src-1",
                    playbackPosition = 0L,
                    mediaSourceUri = "https://jellyx.test/ep1.mkv",
                    parentIndexNumber = 1,
                    indexNumber = 1,
                )
            val ep2 =
                PlayerItem(
                    name = "Episode 2",
                    itemId = ep2Id,
                    mediaSourceId = "src-2",
                    playbackPosition = 0L,
                    mediaSourceUri = "https://jellyx.test/ep2.mkv",
                    parentIndexNumber = 1,
                    indexNumber = 2,
                )
            coEvery {
                playlistManager.getInitialItem(
                    itemId = ep1Id,
                    itemKind = BaseItemKind.EPISODE,
                    mediaSourceIndex = null,
                    startFromBeginning = false,
                )
            } returns ep1
            coEvery { playlistManager.getNextPlayerItem() } returns ep2
            coEvery { playlistManager.getPreviousPlayerItem() } returns null
            every { playlistManager.getCurrentEpisodeSeasonAndSeries() } returns null
            every { playlistManager.getSeasonEpisodes() } returns emptyList()

            val ep1Intro = JellyxSegment(JellyxSegmentType.INTRO, 10_000L, 60_000L)
            val ep2Deferred = CompletableDeferred<List<JellyxSegment>>()
            coEvery { repository.getSegments(ep1Id) } returns listOf(ep1Intro)
            coEvery { repository.getSegments(ep2Id) } coAnswers { ep2Deferred.await() }

            val ep1MediaItem = MediaItem.Builder().setMediaId(ep1Id.toString()).build()
            val ep2MediaItem = MediaItem.Builder().setMediaId(ep2Id.toString()).build()

            every { mockPlayer.currentMediaItem } returns ep1MediaItem
            every { mockPlayer.currentPosition } returns 20_000L
            segmentVm.switchToEpisode(ep1Id)
            segmentVm.onMediaItemTransition(ep1MediaItem, Player.MEDIA_ITEM_TRANSITION_REASON_AUTO)
            segmentVm.onPlaybackStateChanged(Player.STATE_READY)
            segmentVm.updateCurrentSegment()

            assertEquals(ep1Intro, segmentVm.uiState.value.currentSegment)
            assertTrue(segmentVm.uiState.value.hasNextEpisode)

            // Transition to ep2 while ep2 segments are still loading over network
            every { mockPlayer.currentMediaItem } returns ep2MediaItem
            every { mockPlayer.currentMediaItemIndex } returns 1
            segmentVm.onMediaItemTransition(ep2MediaItem, Player.MEDIA_ITEM_TRANSITION_REASON_AUTO)
            segmentVm.updateCurrentSegment()

            assertNull(segmentVm.uiState.value.currentSegment)
            assertFalse(segmentVm.uiState.value.hasNextEpisode)

            ep2Deferred.complete(emptyList())
        }

    @Test
    fun `selectSpeed and applyTemporarySpeed update player speed while preserving persistent speed`() {
        viewModel.selectSpeed(1.5f)
        assertEquals(1.5f, viewModel.playbackSpeed)
        verify { mockPlayer.setPlaybackSpeed(1.5f) }

        viewModel.applyTemporarySpeed(2.0f)
        assertEquals(1.5f, viewModel.playbackSpeed)
        verify { mockPlayer.setPlaybackSpeed(2.0f) }
    }

    @Test
    fun `updatePictureInPictureMode updates PiP state`() {
        assertFalse(viewModel.isInPictureInPictureMode)
        viewModel.updatePictureInPictureMode(true)
        assertTrue(viewModel.isInPictureInPictureMode)
        viewModel.updatePictureInPictureMode(false)
        assertFalse(viewModel.isInPictureInPictureMode)
    }

    @Test
    fun `initializePlayer prepares cache once with clearMpvCache matching engine decision`() =
        runTest {
            val cacheManager = mockk<JellyxCacheManager>(relaxed = true)
            val vm = createViewModel(cacheManager = cacheManager)

            val exoMovieId = UUID.randomUUID()
            val exoMovie =
                PlayerItem(
                    name = "Movie 1",
                    itemId = exoMovieId,
                    mediaSourceId = "src-1",
                    playbackPosition = 0L,
                    mediaSourceUri = "https://jellyx.test/movie1.mkv",
                    hasAssSubtitles = false,
                )
            coEvery {
                playlistManager.getInitialItem(
                    itemId = exoMovieId,
                    itemKind = BaseItemKind.MOVIE,
                    mediaSourceIndex = null,
                    startFromBeginning = false,
                )
            } returns exoMovie
            every { sharedPreferences.getString("pref_player_backend", "exoplayer") } returns
                "exoplayer"
            every { sharedPreferences.getBoolean("pref_player_smart_mpv_ass_subs", true) } returns
                true

            vm.initializePlayer(exoMovieId, BaseItemKind.MOVIE.serialName, false)

            coVerify(exactly = 1) {
                cacheManager.prepareForPlayback(itemId = exoMovieId, clearMpvCache = true)
            }

            val assAnimeId = UUID.randomUUID()
            val assAnime =
                PlayerItem(
                    name = "Anime Ep 1",
                    itemId = assAnimeId,
                    mediaSourceId = "src-2",
                    playbackPosition = 0L,
                    mediaSourceUri = "https://jellyx.test/anime1.mkv",
                    hasAssSubtitles = true,
                )
            coEvery {
                playlistManager.getInitialItem(
                    itemId = assAnimeId,
                    itemKind = BaseItemKind.EPISODE,
                    mediaSourceIndex = null,
                    startFromBeginning = false,
                )
            } returns assAnime

            vm.initializePlayer(assAnimeId, BaseItemKind.EPISODE.serialName, false)

            coVerify(exactly = 1) {
                cacheManager.prepareForPlayback(itemId = assAnimeId, clearMpvCache = false)
            }
        }
}
