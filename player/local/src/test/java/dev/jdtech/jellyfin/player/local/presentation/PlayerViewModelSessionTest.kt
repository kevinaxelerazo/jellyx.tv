package dev.jdtech.jellyfin.player.local.presentation

import android.app.Application
import androidx.lifecycle.SavedStateHandle
import androidx.media3.common.C
import androidx.media3.common.Format
import androidx.media3.common.Player
import androidx.media3.common.TrackGroup
import androidx.media3.common.Tracks
import dev.jdtech.jellyfin.player.core.domain.models.PlayerItem
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
import java.util.UUID
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
import org.junit.Assert.assertNotNull
import org.junit.Assert.assertNull
import org.junit.Before
import org.junit.Test

@OptIn(ExperimentalCoroutinesApi::class)
class PlayerViewModelSessionTest {

    private lateinit var testDispatcher: kotlinx.coroutines.test.TestDispatcher
    private lateinit var mockPlayer: Player
    private lateinit var playlistManager: PlaylistManager
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
        val repository = mockk<JellyfinRepository>(relaxed = true)
        val appPreferences = mockk<AppPreferences>(relaxed = true)
        every { appPreferences.getValue(appPreferences.playerMediaSegmentsSkipButton) } returns
            false
        every { appPreferences.getValue(appPreferences.playerMediaSegmentsSkipButtonType) } returns
            emptySet()
        every {
            appPreferences.getValue(appPreferences.playerMediaSegmentsSkipButtonDuration)
        } returns 0L
        every { appPreferences.getValue(appPreferences.playerMediaSegmentsAutoSkip) } returns false
        every { appPreferences.getValue(appPreferences.playerMediaSegmentsAutoSkipType) } returns
            emptySet()
        every { appPreferences.getValue(appPreferences.playerMediaSegmentsAutoSkipMode) } returns
            "always"
        val capabilityInspector = mockk<DeviceCapabilityInspector>(relaxed = true)
        val sessionEventManager = mockk<SessionEventManager>(relaxed = true)
        every { sessionEventManager.events } returns eventsFlow
        every { sessionEventManager.syncPlayEvents } returns MutableSharedFlow()

        viewModel =
            PlayerViewModel(
                application = app,
                playlistManager = playlistManager,
                repository = repository,
                appPreferences = appPreferences,
                capabilityInspector = capabilityInspector,
                sessionEventManager = sessionEventManager,
                savedStateHandle = SavedStateHandle(),
                testPlayer = mockPlayer,
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
}
