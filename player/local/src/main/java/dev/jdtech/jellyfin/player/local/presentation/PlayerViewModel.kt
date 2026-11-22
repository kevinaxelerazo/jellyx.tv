package dev.jdtech.jellyfin.player.local.presentation

import android.app.Application
import android.graphics.Bitmap
import android.graphics.BitmapFactory
import android.widget.Toast
import androidx.lifecycle.SavedStateHandle
import androidx.lifecycle.ViewModel
import androidx.lifecycle.viewModelScope
import androidx.media3.common.AudioAttributes
import androidx.media3.common.C
import androidx.media3.common.MediaItem
import androidx.media3.common.MediaMetadata
import androidx.media3.common.PlaybackException
import androidx.media3.common.Player
import androidx.media3.common.TrackSelectionOverride
import androidx.media3.common.TrackSelectionParameters
import androidx.media3.common.Tracks
import androidx.media3.common.util.UnstableApi
import androidx.media3.datasource.DefaultDataSource
import androidx.media3.datasource.DefaultHttpDataSource
import androidx.media3.exoplayer.DefaultRenderersFactory
import androidx.media3.exoplayer.ExoPlayer
import androidx.media3.exoplayer.source.DefaultMediaSourceFactory
import androidx.media3.exoplayer.trackselection.DefaultTrackSelector
import dagger.hilt.android.lifecycle.HiltViewModel
import dev.jdtech.jellyfin.models.JellyxChannel
import dev.jdtech.jellyfin.models.JellyxEpisode
import dev.jdtech.jellyfin.models.JellyxItem
import dev.jdtech.jellyfin.models.JellyxSegment
import dev.jdtech.jellyfin.models.JellyxSegmentType
import dev.jdtech.jellyfin.player.core.domain.models.PlayerChapter
import dev.jdtech.jellyfin.player.core.domain.models.PlayerItem
import dev.jdtech.jellyfin.player.core.domain.models.Trickplay
import dev.jdtech.jellyfin.player.local.R
import dev.jdtech.jellyfin.player.local.cache.JellyxCacheManager
import dev.jdtech.jellyfin.player.local.domain.DeviceCapabilityInspector
import dev.jdtech.jellyfin.player.local.domain.PlaylistManager
import dev.jdtech.jellyfin.player.local.mpv.MPVPlayer
import dev.jdtech.jellyfin.repository.JellyfinRepository
import dev.jdtech.jellyfin.repository.SyncPlayGroupInfo
import dev.jdtech.jellyfin.session.SessionAlertEvent
import dev.jdtech.jellyfin.session.SessionEventManager
import dev.jdtech.jellyfin.session.SyncPlayEvent
import dev.jdtech.jellyfin.settings.domain.AppPreferences
import dev.jdtech.jellyfin.settings.domain.Constants
import java.util.UUID
import javax.inject.Inject
import kotlin.math.ceil
import kotlinx.coroutines.DelicateCoroutinesApi
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.GlobalScope
import kotlinx.coroutines.Job
import kotlinx.coroutines.channels.Channel
import kotlinx.coroutines.delay
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.asStateFlow
import kotlinx.coroutines.flow.receiveAsFlow
import kotlinx.coroutines.flow.update
import kotlinx.coroutines.launch
import kotlinx.coroutines.withContext
import org.jellyfin.sdk.model.api.BaseItemKind
import org.jellyfin.sdk.model.api.SendCommandType
import timber.log.Timber

@androidx.annotation.OptIn(UnstableApi::class)
@HiltViewModel
class PlayerViewModel
internal constructor(
    private val application: Application,
    private val playlistManager: PlaylistManager,
    private val repository: JellyfinRepository,
    private val appPreferences: AppPreferences,
    private val capabilityInspector: DeviceCapabilityInspector,
    private val sessionEventManager: SessionEventManager,
    private val savedStateHandle: SavedStateHandle,
    testPlayer: Player?,
    private val cacheManager: JellyxCacheManager? = null,
) : ViewModel(), Player.Listener {
    @Inject
    constructor(
        application: Application,
        playlistManager: PlaylistManager,
        repository: JellyfinRepository,
        appPreferences: AppPreferences,
        capabilityInspector: DeviceCapabilityInspector,
        sessionEventManager: SessionEventManager,
        cacheManager: JellyxCacheManager,
        savedStateHandle: SavedStateHandle,
    ) : this(
        application = application,
        playlistManager = playlistManager,
        repository = repository,
        appPreferences = appPreferences,
        capabilityInspector = capabilityInspector,
        sessionEventManager = sessionEventManager,
        savedStateHandle = savedStateHandle,
        testPlayer = null,
        cacheManager = cacheManager,
    )

    var player: Player
        private set

    private var hasFailedOverToMpv: Boolean = false
    private var isBlockedByConcurrentLimit: Boolean = false
    private var mpvFallbackReason: String? = null
    private var bufferingWatchdogJob: Job? = null
    private var transitionJob: Job? = null
    private var errorRecoveryJob: Job? = null
    private var neighborsLoadedForItemId: UUID? = null
    private val retriedSourceRefreshItemIds = mutableSetOf<UUID>()
    private var liveReconnectJob: Job? = null
    private var liveReconnectAttempts: Int = 0
    private var lastLiveEpgRefreshMs: Long = 0L
    private var isApplyingRemoteSyncCommand: Boolean = false
    private var sessionSubtitlePref: SessionTrackPreference? = null
    private var sessionAudioPref: SessionTrackPreference? = null
    private var appliedSessionTrackKey: String? = null

    internal data class SessionTrackPreference(
        val disabled: Boolean = false,
        val language: String? = null,
        val label: String? = null,
        val fallbackIndex: Int? = null,
    )

    private val _uiState =
        MutableStateFlow(
            UiState(
                currentItemTitle = "",
                currentSegment = null,
                currentSkipButtonStringRes = R.string.player_controls_skip_intro,
                currentTrickplay = null,
                currentChapters = emptyList(),
                fileLoaded = false,
            )
        )
    val uiState = _uiState.asStateFlow()

    private val eventsChannel = Channel<PlayerEvents>(Channel.BUFFERED)
    val eventsChannelFlow = eventsChannel.receiveAsFlow()

    data class UiState(
        val currentItemTitle: String,
        val currentSegment: JellyxSegment?,
        val currentSkipButtonStringRes: Int,
        val currentTrickplay: Trickplay?,
        val currentChapters: List<PlayerChapter>,
        val fileLoaded: Boolean,
        val concurrentSessionAlert: SessionAlertEvent.DisplayMessage? = null,
        val currentItemId: UUID? = null,
        val isCurrentItemFavorite: Boolean = false,
        val playbackEnded: Boolean = false,
        val similarItems: List<JellyxItem> = emptyList(),
        val syncPlayGroups: List<SyncPlayGroupInfo> = emptyList(),
        val activeSyncPlayGroupName: String? = null,
        val isLiveTv: Boolean = false,
        val liveChannels: List<JellyxChannel> = emptyList(),
        val currentChannel: JellyxChannel? = null,
        val isReconnectingLive: Boolean = false,
        val epgNowEpochSec: Long = System.currentTimeMillis() / 1000L,
        val currentSeasonId: UUID? = null,
        val currentSeriesId: UUID? = null,
        val seasonEpisodes: List<JellyxEpisode> = emptyList(),
        val isPlaybackBlockedByLimit: Boolean = false,
    )

    private var items: MutableList<PlayerItem> = mutableListOf()

    private val trackSelector by lazy { DefaultTrackSelector(application) }
    var playWhenReady = true
    private var currentMediaItemIndex = savedStateHandle["mediaItemIndex"] ?: 0
    private var playbackPosition: Long = savedStateHandle["position"] ?: 0
    private var currentMediaItemSegments: List<JellyxSegment> = emptyList()

    // Segments preferences
    var segmentsSkipButton: Boolean = false
    private var segmentsSkipButtonTypes: Set<String> = emptySet()
    var segmentsSkipButtonDuration: Long = 0L
    var segmentsAutoSkip: Boolean = false
    private var segmentsAutoSkipTypes: Set<String> = emptySet()
    private var segmentsAutoSkipMode: String = "always"

    var playbackSpeed: Float = 1f

    var isInPictureInPictureMode: Boolean = false

    init {
        segmentsSkipButton = appPreferences.getValue(appPreferences.playerMediaSegmentsSkipButton)
        segmentsSkipButtonTypes =
            appPreferences.getValue(appPreferences.playerMediaSegmentsSkipButtonType)
        segmentsSkipButtonDuration =
            appPreferences.getValue(appPreferences.playerMediaSegmentsSkipButtonDuration)
        segmentsAutoSkip = appPreferences.getValue(appPreferences.playerMediaSegmentsAutoSkip)
        segmentsAutoSkipTypes =
            appPreferences.getValue(appPreferences.playerMediaSegmentsAutoSkipType)
        segmentsAutoSkipMode =
            appPreferences.getValue(appPreferences.playerMediaSegmentsAutoSkipMode)

        if (testPlayer != null) {
            player = testPlayer
        } else {
            val audioAttributes =
                AudioAttributes.Builder()
                    .setContentType(C.AUDIO_CONTENT_TYPE_MOVIE)
                    .setUsage(C.USAGE_MEDIA)
                    .build()

            trackSelector.setParameters(
                trackSelector
                    .buildUponParameters()
                    .setTunnelingEnabled(true)
                    .setPreferredAudioLanguage(
                        appPreferences.getValue(appPreferences.preferredAudioLanguage)
                    )
                    .setPreferredTextLanguage(
                        appPreferences.getValue(appPreferences.preferredSubtitleLanguage)
                    )
            )

            val playerBackend = appPreferences.getValue(appPreferences.playerBackend)
            player =
                when (playerBackend) {
                    "exoplayer" -> {
                        val renderersFactory =
                            DefaultRenderersFactory(application)
                                .setExtensionRendererMode(
                                    DefaultRenderersFactory.EXTENSION_RENDERER_MODE_ON
                                )
                        val httpDataSourceFactory =
                            DefaultHttpDataSource.Factory()
                                .setUserAgent("VLC/3.0.20 LibVLC/3.0.20")
                                .setAllowCrossProtocolRedirects(true)
                                .setKeepPostFor302Redirects(true)
                                .setConnectTimeoutMs(15_000)
                                .setReadTimeoutMs(25_000)
                        val upstreamFactory =
                            DefaultDataSource.Factory(application, httpDataSourceFactory)
                        val mediaSourceFactory =
                            cacheManager?.let {
                                DefaultMediaSourceFactory(
                                    it.buildCacheDataSourceFactory(upstreamFactory)
                                )
                            } ?: DefaultMediaSourceFactory(upstreamFactory)
                        val capabilities = capabilityInspector.inspect()
                        val maxBufferMs = if (capabilities.isLowRamDevice) 60_000 else 120_000
                        val loadControl =
                            androidx.media3.exoplayer.DefaultLoadControl.Builder()
                                .setBufferDurationsMs(35_000, maxBufferMs, 3_500, 6_000)
                                .setBackBuffer(30_000, true)
                                .build()
                        ExoPlayer.Builder(application, renderersFactory)
                            .setLoadControl(loadControl)
                            .setMediaSourceFactory(mediaSourceFactory)
                            .setAudioAttributes(audioAttributes, true)
                            .setTrackSelector(trackSelector)
                            .setSeekBackIncrementMs(
                                appPreferences.getValue(appPreferences.playerSeekBackInc)
                            )
                            .setSeekForwardIncrementMs(
                                appPreferences.getValue(appPreferences.playerSeekForwardInc)
                            )
                            .setPauseAtEndOfMediaItems(true)
                            .build()
                    }
                    "mpv" -> buildMpvPlayer(audioAttributes)

                    else -> throw RuntimeException("$playerBackend is not a valid player backend")
                }
        }

        sessionEventManager.startListening()
        viewModelScope.launch { runCatching { repository.postCapabilities() } }
        viewModelScope.launch {
            sessionEventManager.events.collect { event -> handleSessionAlertEvent(event) }
        }
        viewModelScope.launch {
            sessionEventManager.syncPlayEvents.collect { event -> handleSyncPlayEvent(event) }
        }
    }

    private fun buildMpvPlayer(
        audioAttributes: AudioAttributes =
            AudioAttributes.Builder()
                .setContentType(C.AUDIO_CONTENT_TYPE_MOVIE)
                .setUsage(C.USAGE_MEDIA)
                .build()
    ): MPVPlayer {
        val capabilities = capabilityInspector.inspect()
        val mpvTrackParamsBuilder = trackSelector.parameters.buildUpon()
        sessionAudioPref?.let { pref ->
            if (pref.disabled) {
                mpvTrackParamsBuilder.setTrackTypeDisabled(C.TRACK_TYPE_AUDIO, true)
            } else {
                mpvTrackParamsBuilder.setTrackTypeDisabled(C.TRACK_TYPE_AUDIO, false)
                pref.language?.takeIf { it.isNotBlank() }?.let {
                    mpvTrackParamsBuilder.setPreferredAudioLanguage(it)
                }
            }
        }
        sessionSubtitlePref?.let { pref ->
            if (pref.disabled) {
                mpvTrackParamsBuilder.setTrackTypeDisabled(C.TRACK_TYPE_TEXT, true)
            } else {
                mpvTrackParamsBuilder.setTrackTypeDisabled(C.TRACK_TYPE_TEXT, false)
                pref.language?.takeIf { it.isNotBlank() }?.let {
                    mpvTrackParamsBuilder.setPreferredTextLanguage(it)
                }
            }
        }
        return MPVPlayer.Builder(application)
            .setAudioAttributes(audioAttributes, true)
            .setTrackSelectionParameters(mpvTrackParamsBuilder.build())
            .setSeekBackIncrementMs(appPreferences.getValue(appPreferences.playerSeekBackInc))
            .setSeekForwardIncrementMs(appPreferences.getValue(appPreferences.playerSeekForwardInc))
            .setPauseAtEndOfMediaItems(true)
            .setVideoOutput(appPreferences.getValue(appPreferences.playerMpvVo))
            .setAudioOutput(appPreferences.getValue(appPreferences.playerMpvAo))
            .setHwDec(appPreferences.getValue(appPreferences.playerMpvHwdec))
            .setDeviceMemoryProfile(
                isLowRamDevice = capabilities.isLowRamDevice,
                totalRamMb = capabilities.totalRamMb,
            )
            .build()
    }

    internal fun handleSessionAlertEvent(event: SessionAlertEvent) {
        when (event) {
            is SessionAlertEvent.ForceStopPlayback -> {
                enforceConcurrentBlock(
                    header = "Límite de pantallas alcanzado",
                    text =
                        "Tu cuenta permite 1 pantalla simultánea. La reproducción se detuvo por exceso de pantallas.",
                )
            }
            is SessionAlertEvent.DisplayMessage -> {
                if (event.isConcurrentStreamBlock) {
                    enforceConcurrentBlock(header = event.header, text = event.text)
                } else {
                    _uiState.update { it.copy(concurrentSessionAlert = event) }
                }
            }
            is SessionAlertEvent.SessionRevoked -> {
                enforceConcurrentBlock(
                    header = "Acceso restringido",
                    text = event.reason,
                )
            }
        }
    }

    private fun enforceConcurrentBlock(header: String, text: String) {
        isBlockedByConcurrentLimit = true
        bufferingWatchdogJob?.cancel()
        transitionJob?.cancel()
        errorRecoveryJob?.cancel()
        liveReconnectJob?.cancel()
        runCatching { player.pause() }
        runCatching { player.stop() }
        runCatching {
            if (player::class.java.name != "dev.jdtech.jellyfin.player.local.mpv.MPVPlayer") {
                player.clearMediaItems()
            }
        }
        _uiState.update { current ->
            current.copy(
                isPlaybackBlockedByLimit = true,
                concurrentSessionAlert =
                    current.concurrentSessionAlert?.takeIf { it.isConcurrentStreamBlock }
                        ?: SessionAlertEvent.DisplayMessage(
                            header = header,
                            text = text,
                            isConcurrentStreamBlock = true,
                        ),
            )
        }
    }

    fun dismissConcurrentSessionAlert() {
        val shouldExit =
            _uiState.value.concurrentSessionAlert?.isConcurrentStreamBlock == true ||
                isBlockedByConcurrentLimit
        _uiState.update { it.copy(concurrentSessionAlert = null) }
        if (shouldExit) {
            eventsChannel.trySend(PlayerEvents.NavigateBack)
        }
    }

    fun initializePlayer(itemId: UUID, itemKind: String, startFromBeginning: Boolean) {
        if (isBlockedByConcurrentLimit) {
            eventsChannel.trySend(PlayerEvents.NavigateBack)
            return
        }
        lastLiveEpgRefreshMs = 0L
        transitionJob?.cancel()
        bufferingWatchdogJob?.cancel()
        errorRecoveryJob?.cancel()
        neighborsLoadedForItemId = null
        retriedSourceRefreshItemIds.clear()
        player.removeListener(this)
        player.addListener(this)

        viewModelScope.launch {
            val startItem =
                try {
                    playlistManager.getInitialItem(
                        itemId = itemId,
                        itemKind = BaseItemKind.fromName(itemKind),
                        mediaSourceIndex = null,
                        startFromBeginning = startFromBeginning,
                    )
                } catch (e: Exception) {
                    Timber.e(e)
                    Toast.makeText(application, e.localizedMessage, Toast.LENGTH_LONG).show()
                    null
                }

            if (startItem == null) {
                Timber.e("No start item, stopping player initialization")
                return@launch
            }

            items = mutableListOf(startItem)
            currentMediaItemIndex = 0

            val mediaItems = mutableListOf<MediaItem>()
            try {
                for (item in items) {
                    mediaItems.add(item.toMediaItem())
                }
            } catch (e: Exception) {
                Timber.e(e)
            }

            val startPosition =
                if (playbackPosition == 0L) {
                    items.getOrNull(currentMediaItemIndex)?.playbackPosition ?: C.TIME_UNSET
                } else {
                    playbackPosition
                }
            playbackPosition = 0L

            cacheManager?.prepareForPlayback(if (startItem.isLiveTv) null else startItem.itemId)

            player.setMediaItems(mediaItems, 0, startPosition)
            player.prepare()
            player.play()
            startBufferingWatchdogIfNeeded()
        }
    }

    private fun startBufferingWatchdogIfNeeded() {
        bufferingWatchdogJob?.cancel()
        if (player !is ExoPlayer || hasFailedOverToMpv) return
        bufferingWatchdogJob =
            viewModelScope.launch {
                val isLive =
                    items.getOrNull(currentMediaItemIndex)?.isLiveTv == true ||
                        _uiState.value.isLiveTv
                val baseTimeoutMs = if (isLive) 10_000L else 18_000L
                val graceMs = 8_000L
                delay(baseTimeoutMs)
                if (player is ExoPlayer && !_uiState.value.fileLoaded && !hasFailedOverToMpv) {
                    val bufferedAheadMs =
                        (player.bufferedPosition - player.currentPosition.coerceAtLeast(0L))
                            .coerceAtLeast(0L)
                    if (bufferedAheadMs > 500L) {
                        delay(graceMs)
                        if (
                            player is ExoPlayer &&
                                !_uiState.value.fileLoaded &&
                                !hasFailedOverToMpv
                        ) {
                            val reason = if (isLive) "LIVE_TIMEOUT_18S" else "VOD_TIMEOUT_26S"
                            Timber.w("ExoPlayer startup timed out (%s); failing over to libmpv", reason)
                            failoverToMpv(reason)
                        }
                    } else {
                        val reason = if (isLive) "LIVE_TIMEOUT_10S" else "VOD_TIMEOUT_18S"
                        Timber.w("ExoPlayer startup stalled (%s); failing over to libmpv", reason)
                        failoverToMpv(reason)
                    }
                }
            }
    }

    private fun failoverToMpv(reason: String) {
        if (hasFailedOverToMpv || player !is ExoPlayer) return
        hasFailedOverToMpv = true
        mpvFallbackReason = reason
        bufferingWatchdogJob?.cancel()
        transitionJob?.cancel()

        val oldPlayer = player
        val currentMediaId = oldPlayer.currentMediaItem?.mediaId
        val targetItem =
            items.firstOrNull { it.itemId.toString() == currentMediaId }
                ?: items.getOrNull(oldPlayer.currentMediaItemIndex)
                ?: items.firstOrNull { it.itemId == _uiState.value.currentItemId }
                ?: items.firstOrNull()
        val rawPosition = oldPlayer.currentPosition
        val resumePos =
            when {
                rawPosition > 0L && rawPosition != C.TIME_UNSET -> rawPosition
                targetItem != null &&
                    targetItem.playbackPosition > 0L &&
                    targetItem.playbackPosition != C.TIME_UNSET -> targetItem.playbackPosition
                else -> 0L
            }

        try {
            oldPlayer.removeListener(this)
            oldPlayer.stop()
            oldPlayer.release()
        } catch (e: Exception) {
            Timber.w(e, "Error releasing ExoPlayer during MPV failover")
        }

        val mpvPlayer = buildMpvPlayer()
        player = mpvPlayer
        appliedSessionTrackKey = null
        neighborsLoadedForItemId = null
        player.addListener(this)

        if (targetItem != null) {
            items = mutableListOf(targetItem)
            currentMediaItemIndex = 0
            playlistManager.resetLoadedNeighbors(targetItem.itemId)
            player.setMediaItems(listOf(targetItem.toMediaItem()), 0, resumePos)
            player.prepare()
            player.play()
        }

        eventsChannel.trySend(PlayerEvents.PlayerSwitched(player))
        Toast.makeText(
                application,
                "Reproductor conmutado",
                Toast.LENGTH_SHORT,
            )
            .show()
    }

    private fun PlayerItem.toMediaItem(): MediaItem {
        val streamUrl = mediaSourceUri
        val mediaSubtitles = externalSubtitles.map { externalSubtitle ->
            MediaItem.SubtitleConfiguration.Builder(externalSubtitle.uri)
                .setLabel(
                    externalSubtitle.title.ifBlank { application.getString(R.string.external) }
                )
                .setMimeType(externalSubtitle.mimeType)
                .setLanguage(externalSubtitle.language)
                .build()
        }

        Timber.d("Stream url: $streamUrl")
        val isHlsLive = isLiveTv || streamUrl.contains(".m3u8", ignoreCase = true)
        val mediaItemBuilder =
            MediaItem.Builder()
                .setMediaId(itemId.toString())
                .setUri(streamUrl)
                .setMediaMetadata(MediaMetadata.Builder().setTitle(name).build())
                .setSubtitleConfigurations(mediaSubtitles)
        if (streamUrl.contains(".m3u8", ignoreCase = true)) {
            mediaItemBuilder.setMimeType(androidx.media3.common.MimeTypes.APPLICATION_M3U8)
        }
        if (isHlsLive) {
            mediaItemBuilder.setLiveConfiguration(
                MediaItem.LiveConfiguration.Builder()
                    .setTargetOffsetMs(30_000L)
                    .setMinOffsetMs(15_000L)
                    .setMaxOffsetMs(60_000L)
                    .setMinPlaybackSpeed(0.97f)
                    .setMaxPlaybackSpeed(1.03f)
                    .build()
            )
        }
        val mediaItem = mediaItemBuilder.build()

        return mediaItem
    }

    @OptIn(DelicateCoroutinesApi::class)
    private fun releasePlayer() {
        val mediaId = player.currentMediaItem?.mediaId
        val position = player.currentPosition
        val duration = player.duration
        val liveStreamIds = items.mapNotNull { it.liveStreamId }.distinct()
        GlobalScope.launch {
            delay(200L)
            try {
                liveStreamIds.forEach { repository.closeLiveStream(it) }
                if (mediaId != null && duration != C.TIME_UNSET) {
                    Timber.d("Sending playback stop")
                    repository.postPlaybackStop(
                        UUID.fromString(mediaId),
                        position.times(10000),
                        position.div(duration.toFloat()).times(100).toInt(),
                    )
                }
            } catch (e: Exception) {
                Timber.e(e)
            }
        }

        transitionJob?.cancel()
        bufferingWatchdogJob?.cancel()
        errorRecoveryJob?.cancel()
        liveReconnectJob?.cancel()
        _uiState.update { it.copy(currentTrickplay = null) }
        playWhenReady = false
        playbackPosition = 0L
        currentMediaItemIndex = 0
        player.removeListener(this)
        player.release()
    }

    fun updatePlaybackProgress() {
        Timber.d("Updating playback progress")
        viewModelScope.launch(Dispatchers.Main) {
            savedStateHandle["position"] = player.currentPosition
            if (_uiState.value.isLiveTv) {
                val nowMs = System.currentTimeMillis()
                _uiState.update { it.copy(epgNowEpochSec = nowMs / 1000L) }
                val state = _uiState.value
                val channel = state.currentChannel
                val channelId = channel?.id ?: state.currentItemId
                val nowDateTime = java.time.LocalDateTime.now()
                val needsEpgRefresh =
                    channelId != null &&
                        (nowMs - lastLiveEpgRefreshMs >= 60_000L) &&
                        (channel == null ||
                            channel.currentProgram?.endDate?.let { !nowDateTime.isBefore(it) } ==
                                true ||
                            (channel.currentProgram != null && channel.nextProgram == null))
                if (needsEpgRefresh) {
                    lastLiveEpgRefreshMs = nowMs
                    val refreshed =
                        runCatching { repository.getLiveTvChannel(channelId) }.getOrNull()
                    if (refreshed != null) {
                        _uiState.update { current ->
                            val updatedChannel =
                                if (current.currentChannel?.id == refreshed.id) {
                                    refreshed.copy(
                                        favorite =
                                            current.isCurrentItemFavorite || refreshed.favorite
                                    )
                                } else {
                                    refreshed
                                }
                            val isStillCurrentChannel =
                                current.currentChannel?.id == refreshed.id ||
                                    current.currentItemId == refreshed.id
                            current.copy(
                                currentChannel =
                                    if (isStillCurrentChannel) updatedChannel
                                    else current.currentChannel,
                                liveChannels =
                                    current.liveChannels.map { ch ->
                                        if (ch.id == refreshed.id) updatedChannel else ch
                                    },
                            )
                        }
                    }
                }
            }
            if (player.currentMediaItem != null && player.currentMediaItem!!.mediaId.isNotEmpty()) {
                val itemId = UUID.fromString(player.currentMediaItem!!.mediaId)
                try {
                    repository.postPlaybackProgress(
                        itemId,
                        player.currentPosition.times(10000),
                        !player.isPlaying,
                    )
                } catch (e: Exception) {
                    Timber.e(e)
                }
            }
        }
    }

    fun updateCurrentSegment() {
        Timber.d("Updating current segment")
        viewModelScope.launch(Dispatchers.Main) {
            if (currentMediaItemSegments.isEmpty()) {
                return@launch
            }

            val milliSeconds = player.currentPosition

            // Get current segment, - 100 milliseconds to avoid showing button after segment ends
            val currentSegment = currentMediaItemSegments.find { segment ->
                milliSeconds in segment.startTicks..<(segment.endTicks - 100L)
            }

            if (currentSegment == null) {
                // Remove button if not pressed and there is no current segment
                if (_uiState.value.currentSegment != null) {
                    _uiState.update { it.copy(currentSegment = null) }
                }
                return@launch
            }

            Timber.tag("SegmentInfo").d("currentSegment: %s", currentSegment)

            if (
                segmentsAutoSkip &&
                    segmentsAutoSkipTypes.contains(currentSegment.type.toString()) &&
                    (segmentsAutoSkipMode == Constants.PlayerMediaSegmentsAutoSkip.ALWAYS ||
                        (segmentsAutoSkipMode == Constants.PlayerMediaSegmentsAutoSkip.PIP &&
                            isInPictureInPictureMode))
            ) {
                // Auto Skip segment
                skipSegment(currentSegment)
            } else if (segmentsSkipButtonTypes.contains(currentSegment.type.toString())) {
                // Skip Button segment
                _uiState.update {
                    it.copy(
                        currentSegment = currentSegment,
                        currentSkipButtonStringRes = getSkipButtonTextStringId(currentSegment),
                    )
                }
            } else {
                _uiState.update { it.copy(currentSegment = null) }
            }
        }
    }

    override fun onMediaItemTransition(mediaItem: MediaItem?, reason: Int) {
        Timber.d("Playing MediaItem: ${mediaItem?.mediaId}")
        appliedSessionTrackKey = null
        neighborsLoadedForItemId = null
        savedStateHandle["mediaItemIndex"] = player.currentMediaItemIndex
        transitionJob?.cancel()
        transitionJob =
            viewModelScope.launch {
                try {
                    val targetMediaId = mediaItem?.mediaId ?: player.currentMediaItem?.mediaId
                    val item =
                        items.firstOrNull { it.itemId.toString() == targetMediaId }
                            ?: items.getOrNull(player.currentMediaItemIndex)
                            ?: return@launch

                    currentMediaItemIndex = items.indexOf(item).coerceAtLeast(0)
                    val itemTitle =
                        if (item.parentIndexNumber != null && item.indexNumber != null) {
                            if (item.indexNumberEnd == null) {
                                "S${item.parentIndexNumber}:E${item.indexNumber} - ${item.name}"
                            } else {
                                "S${item.parentIndexNumber}:E${item.indexNumber}-${item.indexNumberEnd} - ${item.name}"
                            }
                        } else {
                            item.name
                        }
                    playlistManager.setCurrentMediaItemIndex(item.itemId)
                    val activeChannel =
                        if (item.isLiveTv) playlistManager.getCurrentChannel() else null
                    val allLiveChannels =
                        if (item.isLiveTv) playlistManager.getLiveChannels() else emptyList()
                    val seasonAndSeries =
                        if (!item.isLiveTv) playlistManager.getCurrentEpisodeSeasonAndSeries()
                        else null
                    val seasonEpisodes =
                        if (!item.isLiveTv) playlistManager.getSeasonEpisodes() else emptyList()

                    _uiState.update {
                        it.copy(
                            currentItemTitle = itemTitle,
                            currentSegment = null,
                            currentChapters = item.chapters,
                            fileLoaded = false,
                            currentItemId = item.itemId,
                            playbackEnded = false,
                            similarItems = emptyList(),
                            isLiveTv = item.isLiveTv,
                            liveChannels = allLiveChannels,
                            currentChannel = activeChannel,
                            isCurrentItemFavorite = activeChannel?.favorite ?: false,
                            isReconnectingLive = false,
                            currentSeasonId = seasonAndSeries?.first,
                            currentSeriesId = seasonAndSeries?.second,
                            seasonEpisodes = seasonEpisodes,
                        )
                    }
                    startBufferingWatchdogIfNeeded()
                    cacheManager?.prepareForPlayback(if (item.isLiveTv) null else item.itemId)

                    if (!item.isLiveTv) {
                        launch {
                            val currentItem =
                                runCatching { repository.getItem(item.itemId) }.getOrNull()
                            val similar =
                                runCatching {
                                        repository.getSimilarItems(item.itemId, limit = 4)
                                    }
                                    .getOrDefault(emptyList())
                            _uiState.update {
                                it.copy(
                                    isCurrentItemFavorite = currentItem?.favorite ?: false,
                                    similarItems = similar,
                                )
                            }
                        }
                    }

                    repository.postPlaybackStart(item.itemId)

                    if (!item.isLiveTv && (segmentsSkipButton || segmentsAutoSkip)) {
                        getSegments(item.itemId)
                    }

                    if (!item.isLiveTv && appPreferences.getValue(appPreferences.playerTrickplay)) {
                        getTrickplay(item)
                    }

                    Timber.tag("PlayerItems").d(items.map { it.indexNumber }.toString())
                } catch (e: Exception) {
                    Timber.e(e)
                }
            }
    }

    private fun loadAdjacentEpisodesIfNeeded() {
        if (_uiState.value.isLiveTv) return
        val activeMediaId = player.currentMediaItem?.mediaId ?: return
        val activeItemId = runCatching { UUID.fromString(activeMediaId) }.getOrNull() ?: return
        if (neighborsLoadedForItemId == activeItemId) return
        neighborsLoadedForItemId = activeItemId

        viewModelScope.launch {
            val previousItem = playlistManager.getPreviousPlayerItem()
            if (
                previousItem != null &&
                    player.currentMediaItem?.mediaId == activeMediaId &&
                    items.none { it.itemId == previousItem.itemId }
            ) {
                val currentIdx = items.indexOfFirst { it.itemId == activeItemId }
                if (currentIdx >= 0) {
                    items.add(currentIdx, previousItem)
                    player.addMediaItem(currentIdx, previousItem.toMediaItem())
                    currentMediaItemIndex =
                        items.indexOfFirst { it.itemId == activeItemId }.coerceAtLeast(0)
                }
            }

            val nextItem = playlistManager.getNextPlayerItem()
            if (
                nextItem != null &&
                    player.currentMediaItem?.mediaId == activeMediaId &&
                    items.none { it.itemId == nextItem.itemId }
            ) {
                val currentIdx = items.indexOfFirst { it.itemId == activeItemId }
                if (currentIdx >= 0) {
                    items.add(currentIdx + 1, nextItem)
                    player.addMediaItem(currentIdx + 1, nextItem.toMediaItem())
                }
            }
        }
    }

    override fun onPlayWhenReadyChanged(playWhenReady: Boolean, reason: Int) {
        // Report playback stopped for current item and transition to the next one
        if (
            !playWhenReady &&
                reason == Player.PLAY_WHEN_READY_CHANGE_REASON_END_OF_MEDIA_ITEM &&
                player.playbackState == ExoPlayer.STATE_READY
        ) {
            viewModelScope.launch {
                val mediaId = player.currentMediaItem?.mediaId
                val position = player.currentPosition
                val duration = player.duration
                try {
                    repository.postPlaybackStop(
                        UUID.fromString(mediaId),
                        position.times(10000),
                        position.div(duration.toFloat()).times(100).toInt(),
                    )
                } catch (e: Exception) {
                    Timber.e(e)
                }
                player.seekToNextMediaItem()
                player.play()
            }
        }
    }

    override fun onPlaybackStateChanged(state: Int) {
        var stateString = "UNKNOWN_STATE             -"
        when (state) {
            ExoPlayer.STATE_IDLE -> {
                stateString = "ExoPlayer.STATE_IDLE      -"
            }
            ExoPlayer.STATE_BUFFERING -> {
                stateString = "ExoPlayer.STATE_BUFFERING -"
            }
            ExoPlayer.STATE_READY -> {
                stateString = "ExoPlayer.STATE_READY     -"
                bufferingWatchdogJob?.cancel()
                liveReconnectJob?.cancel()
                liveReconnectAttempts = 0
                _uiState.update { it.copy(fileLoaded = true, isReconnectingLive = false) }
                loadAdjacentEpisodesIfNeeded()
            }
            ExoPlayer.STATE_ENDED -> {
                stateString = "ExoPlayer.STATE_ENDED     -"
                if (_uiState.value.isLiveTv) {
                    Timber.w("Live TV stream reported STATE_ENDED; attempting auto-reconnect")
                    reconnectCurrentChannel()
                } else if (_uiState.value.similarItems.isNotEmpty()) {
                    _uiState.update { it.copy(playbackEnded = true) }
                } else {
                    eventsChannel.trySend(PlayerEvents.NavigateBack)
                }
            }
        }
        Timber.d("Changed player state to $stateString")
    }

    override fun onPlayerError(error: PlaybackException) {
        super.onPlayerError(error)
        Timber.e(error, "Playback error in backend (%s)", error.errorCodeName)
        if (_uiState.value.isLiveTv && liveReconnectAttempts < 1) {
            liveReconnectAttempts++
            Timber.w(
                "Live TV playback error (%s); auto-reconnect attempt %d/1",
                error.errorCodeName,
                liveReconnectAttempts,
            )
            reconnectCurrentChannel(delayMs = 800L)
            return
        }

        val currentMediaId =
            player.currentMediaItem?.mediaId?.let { runCatching { UUID.fromString(it) }.getOrNull() }
                ?: _uiState.value.currentItemId
                ?: items.getOrNull(currentMediaItemIndex)?.itemId

        val isRecoverableHttpOrNetworkError =
            error.errorCode == PlaybackException.ERROR_CODE_IO_BAD_HTTP_STATUS ||
                error.errorCode == PlaybackException.ERROR_CODE_IO_NETWORK_CONNECTION_TIMEOUT ||
                error.errorCode == PlaybackException.ERROR_CODE_IO_NETWORK_CONNECTION_FAILED ||
                error.errorCode == PlaybackException.ERROR_CODE_IO_UNSPECIFIED

        if (
            !_uiState.value.isLiveTv &&
                currentMediaId != null &&
                isRecoverableHttpOrNetworkError &&
                currentMediaId !in retriedSourceRefreshItemIds
        ) {
            retriedSourceRefreshItemIds.add(currentMediaId)
            val rawPosition = player.currentPosition
            val resumePos =
                rawPosition.takeIf { it > 0L && it != C.TIME_UNSET }
                    ?: items.firstOrNull { it.itemId == currentMediaId }?.playbackPosition
                    ?: 0L
            bufferingWatchdogJob?.cancel()
            errorRecoveryJob?.cancel()
            errorRecoveryJob =
                viewModelScope.launch {
                    val refreshedItem =
                        runCatching { playlistManager.refreshItemSource(currentMediaId, resumePos) }
                            .getOrNull()
                    if (refreshedItem != null) {
                        cacheManager?.evictItem(currentMediaId)
                        items = mutableListOf(refreshedItem)
                        currentMediaItemIndex = 0
                        neighborsLoadedForItemId = null
                        playlistManager.resetLoadedNeighbors(currentMediaId)
                        player.setMediaItems(listOf(refreshedItem.toMediaItem()), 0, resumePos)
                        player.prepare()
                        player.play()
                        startBufferingWatchdogIfNeeded()
                        return@launch
                    }
                    if (player is ExoPlayer && !hasFailedOverToMpv) {
                        failoverToMpv(error.errorCodeName)
                    }
                }
            return
        }

        if (player is ExoPlayer && !hasFailedOverToMpv) {
            failoverToMpv(error.errorCodeName)
        } else {
            Toast.makeText(
                    application,
                    error.localizedMessage ?: "Error de reproducción (${error.errorCodeName})",
                    Toast.LENGTH_LONG,
                )
                .show()
        }
    }

    fun toggleCurrentItemFavorite() {
        val itemId = _uiState.value.currentItemId ?: return
        val newFavorite = !_uiState.value.isCurrentItemFavorite
        val isLive = _uiState.value.isLiveTv
        if (isLive) {
            val updatedChannels = playlistManager.updateChannelFavorite(itemId, newFavorite)
            _uiState.update {
                it.copy(
                    isCurrentItemFavorite = newFavorite,
                    liveChannels = updatedChannels,
                    currentChannel =
                        updatedChannels.firstOrNull { ch -> ch.id == itemId } ?: it.currentChannel,
                )
            }
        } else {
            _uiState.update { it.copy(isCurrentItemFavorite = newFavorite) }
        }
        viewModelScope.launch {
            runCatching {
                    if (newFavorite) {
                        repository.markAsFavorite(itemId)
                    } else {
                        repository.unmarkAsFavorite(itemId)
                    }
                }
                .onFailure {
                    Timber.e(it, "Failed to toggle favorite for %s", itemId)
                    if (isLive) {
                        val revertedChannels =
                            playlistManager.updateChannelFavorite(itemId, !newFavorite)
                        _uiState.update { state ->
                            state.copy(
                                isCurrentItemFavorite = !newFavorite,
                                liveChannels = revertedChannels,
                                currentChannel =
                                    revertedChannels.firstOrNull { ch -> ch.id == itemId }
                                        ?: state.currentChannel,
                            )
                        }
                    } else {
                        _uiState.update { state -> state.copy(isCurrentItemFavorite = !newFavorite) }
                    }
                }
        }
    }

    fun toggleChannelFavorite(channelId: UUID) {
        val currentChannels = _uiState.value.liveChannels
        val target = currentChannels.firstOrNull { it.id == channelId } ?: return
        val newFavorite = !target.favorite
        val updatedChannels = playlistManager.updateChannelFavorite(channelId, newFavorite)
        _uiState.update { state ->
            state.copy(
                liveChannels = updatedChannels,
                currentChannel =
                    if (state.currentChannel?.id == channelId) {
                        updatedChannels.firstOrNull { it.id == channelId }
                    } else {
                        state.currentChannel
                    },
                isCurrentItemFavorite =
                    if (state.currentItemId == channelId) newFavorite else state.isCurrentItemFavorite,
            )
        }
        viewModelScope.launch {
            runCatching {
                    if (newFavorite) {
                        repository.markAsFavorite(channelId)
                    } else {
                        repository.unmarkAsFavorite(channelId)
                    }
                }
                .onFailure { error ->
                    Timber.e(error, "Failed to toggle favorite for channel %s", channelId)
                    val revertedChannels =
                        playlistManager.updateChannelFavorite(channelId, !newFavorite)
                    _uiState.update { state ->
                        state.copy(
                            liveChannels = revertedChannels,
                            currentChannel =
                                if (state.currentChannel?.id == channelId) {
                                    revertedChannels.firstOrNull { it.id == channelId }
                                } else {
                                    state.currentChannel
                                },
                            isCurrentItemFavorite =
                                if (state.currentItemId == channelId) !newFavorite
                                else state.isCurrentItemFavorite,
                        )
                    }
                }
        }
    }

    fun dismissPlaybackEnded() {
        _uiState.update { it.copy(playbackEnded = false) }
        eventsChannel.trySend(PlayerEvents.NavigateBack)
    }

    fun refreshSyncPlayGroups() {
        viewModelScope.launch {
            val groups = repository.getSyncPlayGroups()
            _uiState.update { it.copy(syncPlayGroups = groups) }
        }
    }

    fun createSyncPlayGroup(groupName: String) {
        val cleanName = groupName.trim().ifBlank { "Watch Party" }
        viewModelScope.launch {
            if (repository.createSyncPlayGroup(cleanName)) {
                _uiState.update { it.copy(activeSyncPlayGroupName = cleanName) }
                refreshSyncPlayGroups()
            }
        }
    }

    fun joinSyncPlayGroup(group: SyncPlayGroupInfo) {
        viewModelScope.launch {
            if (repository.joinSyncPlayGroup(group.groupId)) {
                _uiState.update { it.copy(activeSyncPlayGroupName = group.groupName) }
                refreshSyncPlayGroups()
            }
        }
    }

    fun leaveSyncPlayGroup() {
        viewModelScope.launch {
            repository.leaveSyncPlayGroup()
            _uiState.update { it.copy(activeSyncPlayGroupName = null) }
            refreshSyncPlayGroups()
        }
    }

    fun notifySyncPlayPlayPause(shouldPlay: Boolean) {
        if (_uiState.value.activeSyncPlayGroupName == null || isApplyingRemoteSyncCommand) return
        viewModelScope.launch {
            if (shouldPlay) {
                repository.sendSyncPlayUnpause()
            } else {
                repository.sendSyncPlayPause()
            }
        }
    }

    fun notifySyncPlaySeek(positionMs: Long) {
        if (_uiState.value.activeSyncPlayGroupName == null || isApplyingRemoteSyncCommand) return
        viewModelScope.launch { repository.sendSyncPlaySeek(positionMs) }
    }

    private suspend fun handleSyncPlayEvent(event: SyncPlayEvent) {
        if (_uiState.value.activeSyncPlayGroupName == null) return
        when (event) {
            is SyncPlayEvent.Command -> {
                withContext(Dispatchers.Main) {
                    isApplyingRemoteSyncCommand = true
                    try {
                        event.positionTicks?.let { ticks ->
                            val targetMs = (ticks / 10_000L).coerceAtLeast(0L)
                            if (kotlin.math.abs(player.currentPosition - targetMs) > 1_500L) {
                                player.seekTo(targetMs)
                            }
                        }
                        when (event.command) {
                            SendCommandType.UNPAUSE -> player.play()
                            SendCommandType.PAUSE -> player.pause()
                            SendCommandType.STOP -> dismissPlaybackEnded()
                            SendCommandType.SEEK -> Unit
                        }
                    } finally {
                        isApplyingRemoteSyncCommand = false
                    }
                }
            }
            is SyncPlayEvent.GroupUpdated -> refreshSyncPlayGroups()
        }
    }

    override fun onCleared() {
        super.onCleared()
        Timber.d("Clearing Player ViewModel")
        releasePlayer()
    }

    override fun onTracksChanged(tracks: Tracks) {
        super.onTracksChanged(tracks)
        if (tracks.groups.isEmpty()) return
        val mediaId = player.currentMediaItem?.mediaId ?: return
        val sessionKey = "${System.identityHashCode(player)}:$mediaId"
        if (appliedSessionTrackKey == sessionKey) return
        appliedSessionTrackKey = sessionKey

        sessionAudioPref?.let { applySessionTrackPreference(tracks, C.TRACK_TYPE_AUDIO, it) }
        sessionSubtitlePref?.let { applySessionTrackPreference(tracks, C.TRACK_TYPE_TEXT, it) }
    }

    fun switchToTrack(trackType: @C.TrackType Int, index: Int) {
        // Index -1 equals disable track
        if (index == -1) {
            saveSessionTrackPreference(trackType, SessionTrackPreference(disabled = true))
            applyTrackDisable(trackType)
        } else {
            val supportedGroups =
                player.currentTracks.groups.filter { it.type == trackType && it.isSupported }
            val targetGroup = supportedGroups.getOrNull(index) ?: return
            val format = targetGroup.mediaTrackGroup.getFormat(0)
            saveSessionTrackPreference(
                trackType,
                SessionTrackPreference(
                    disabled = false,
                    language = format.language?.takeIf { it.isNotBlank() },
                    label = format.label?.takeIf { it.isNotBlank() },
                    fallbackIndex = index,
                ),
            )
            applyTrackOverride(trackType, targetGroup.mediaTrackGroup, format.language)
        }
    }

    private fun saveSessionTrackPreference(
        trackType: @C.TrackType Int,
        pref: SessionTrackPreference,
    ) {
        when (trackType) {
            C.TRACK_TYPE_TEXT -> sessionSubtitlePref = pref
            C.TRACK_TYPE_AUDIO -> sessionAudioPref = pref
        }
    }

    private fun applySessionTrackPreference(
        tracks: Tracks,
        trackType: @C.TrackType Int,
        pref: SessionTrackPreference,
    ) {
        if (pref.disabled) {
            applyTrackDisable(trackType)
            return
        }
        val supportedGroups = tracks.groups.filter { it.type == trackType && it.isSupported }
        if (supportedGroups.isEmpty()) return
        val matchedIndex = findMatchingTrackIndex(tracks, trackType, pref) ?: return
        val targetGroup = supportedGroups.getOrNull(matchedIndex) ?: return
        if (!targetGroup.isSelected) {
            applyTrackOverride(
                trackType = trackType,
                mediaTrackGroup = targetGroup.mediaTrackGroup,
                language = targetGroup.mediaTrackGroup.getFormat(0).language,
            )
        }
    }

    private fun applyTrackDisable(trackType: @C.TrackType Int) {
        player.trackSelectionParameters =
            player.trackSelectionParameters
                .buildUpon()
                .clearOverridesOfType(trackType)
                .setTrackTypeDisabled(trackType, true)
                .build()
    }

    private fun applyTrackOverride(
        trackType: @C.TrackType Int,
        mediaTrackGroup: androidx.media3.common.TrackGroup,
        language: String?,
    ) {
        val builder =
            player.trackSelectionParameters
                .buildUpon()
                .setOverrideForType(TrackSelectionOverride(mediaTrackGroup, 0))
                .setTrackTypeDisabled(trackType, false)
        if (!language.isNullOrBlank()) {
            when (trackType) {
                C.TRACK_TYPE_TEXT -> builder.setPreferredTextLanguage(language)
                C.TRACK_TYPE_AUDIO -> builder.setPreferredAudioLanguage(language)
            }
        }
        player.trackSelectionParameters = builder.build()
    }

    internal fun findMatchingTrackIndex(
        tracks: Tracks,
        trackType: @C.TrackType Int,
        pref: SessionTrackPreference,
    ): Int? {
        if (pref.disabled) return null
        val supportedGroups = tracks.groups.filter { it.type == trackType && it.isSupported }
        if (supportedGroups.isEmpty()) return null

        val targetLang = pref.language?.trim()?.lowercase()
        val targetLabel = pref.label?.trim()?.lowercase()

        if (!targetLang.isNullOrBlank() && !targetLabel.isNullOrBlank()) {
            supportedGroups.indexOfFirst { group ->
                val fmt = group.mediaTrackGroup.getFormat(0)
                fmt.language?.trim()?.lowercase() == targetLang &&
                    fmt.label?.trim()?.lowercase() == targetLabel
            }.takeIf { it >= 0 }?.let { return it }
        }

        if (!targetLabel.isNullOrBlank()) {
            supportedGroups.indexOfFirst { group ->
                group.mediaTrackGroup.getFormat(0).label?.trim()?.lowercase() == targetLabel
            }.takeIf { it >= 0 }?.let { return it }
        }

        if (!targetLang.isNullOrBlank()) {
            supportedGroups.indexOfFirst { group ->
                group.mediaTrackGroup.getFormat(0).language?.trim()?.lowercase() == targetLang
            }.takeIf { it >= 0 }?.let { return it }
        }

        return pref.fallbackIndex?.takeIf { it in supportedGroups.indices }
    }

    fun selectSpeed(speed: Float) {
        player.setPlaybackSpeed(speed)
        playbackSpeed = speed
    }

    private suspend fun getSegments(itemId: UUID) {
        try {
            currentMediaItemSegments = repository.getSegments(itemId)
        } catch (e: Exception) {
            currentMediaItemSegments = emptyList()
            Timber.e(e)
        }
    }

    private suspend fun getTrickplay(item: PlayerItem) {
        val trickplayInfo = item.trickplayInfo ?: return
        Timber.d("Trickplay Resolution: ${trickplayInfo.width}")

        withContext(Dispatchers.Default) {
            val maxIndex =
                ceil(
                        trickplayInfo.thumbnailCount
                            .toDouble()
                            .div(trickplayInfo.tileWidth * trickplayInfo.tileHeight)
                    )
                    .toInt()
            val bitmaps = mutableListOf<Bitmap>()

            for (i in 0..maxIndex) {
                repository.getTrickplayData(item.itemId, trickplayInfo.width, i)?.let { byteArray ->
                    val fullBitmap = BitmapFactory.decodeByteArray(byteArray, 0, byteArray.size)
                    for (offsetY in
                        0..<trickplayInfo.height * trickplayInfo.tileHeight step
                            trickplayInfo.height) {
                        for (offsetX in
                            0..<trickplayInfo.width * trickplayInfo.tileWidth step
                                trickplayInfo.width) {
                            val bitmap =
                                Bitmap.createBitmap(
                                    fullBitmap,
                                    offsetX,
                                    offsetY,
                                    trickplayInfo.width,
                                    trickplayInfo.height,
                                )
                            bitmaps.add(bitmap)
                        }
                    }
                }
            }
            _uiState.update {
                it.copy(currentTrickplay = Trickplay(trickplayInfo.interval, bitmaps))
            }
        }
    }

    fun skipSegment(segment: JellyxSegment) {
        if (shouldSkipToNextEpisode(segment)) {
            player.seekToNextMediaItem()
        } else {
            player.seekTo(segment.endTicks)
        }
        _uiState.update { it.copy(currentSegment = null) }
    }

    // Check if the outro segment's end time is within n milliseconds of the player's total duration
    private fun shouldSkipToNextEpisode(segment: JellyxSegment): Boolean {
        return if (segment.type == JellyxSegmentType.OUTRO && player.hasNextMediaItem()) {
            val segmentEndTimeMillis = segment.endTicks
            val playerDurationMillis = player.duration
            val thresholdMillis =
                playerDurationMillis -
                    appPreferences.getValue(appPreferences.playerMediaSegmentsNextEpisodeThreshold)

            segmentEndTimeMillis > thresholdMillis
        } else {
            false
        }
    }

    private fun getSkipButtonTextStringId(segment: JellyxSegment): Int {
        return when (shouldSkipToNextEpisode(segment)) {
            true -> R.string.player_controls_next_episode
            false ->
                when (segment.type) {
                    JellyxSegmentType.INTRO -> R.string.player_controls_skip_intro
                    JellyxSegmentType.OUTRO -> R.string.player_controls_skip_outro
                    JellyxSegmentType.RECAP -> R.string.player_controls_skip_recap
                    JellyxSegmentType.COMMERCIAL -> R.string.player_controls_skip_commercial
                    JellyxSegmentType.PREVIEW -> R.string.player_controls_skip_preview
                    else -> R.string.player_controls_skip_unknown
                }
        }
    }

    /**
     * Get chapters of current item
     *
     * @return list of [PlayerChapter]
     */
    private fun getChapters(): List<PlayerChapter> {
        return uiState.value.currentChapters
    }

    /**
     * Get the index of the current chapter
     *
     * @return the index of the current chapter
     */
    private fun getCurrentChapterIndex(): Int? {
        val chapters = getChapters()

        for (i in chapters.indices.reversed()) {
            if (chapters[i].startPosition < player.currentPosition) {
                return i
            }
        }

        return null
    }

    /**
     * Get the index of the next chapter
     *
     * @return the index of the next chapter
     */
    private fun getNextChapterIndex(): Int? {
        val chapters = getChapters()
        val currentChapterIndex = getCurrentChapterIndex() ?: return null

        return minOf(chapters.size - 1, currentChapterIndex + 1)
    }

    /**
     * Get the index of the previous chapter. Only use this for seeking as it will return the
     * current chapter when player position is more than 5 seconds past the start of the chapter
     *
     * @return the index of the previous chapter
     */
    private fun getPreviousChapterIndex(): Int? {
        val chapters = getChapters()
        val currentChapterIndex = getCurrentChapterIndex() ?: return null

        // Return current chapter when more than 5 seconds past chapter start
        if (player.currentPosition > chapters[currentChapterIndex].startPosition + 5000L) {
            return currentChapterIndex
        }

        return maxOf(0, currentChapterIndex - 1)
    }

    fun isLastChapter(): Boolean =
        getChapters().let { chapters -> getCurrentChapterIndex() == chapters.size - 1 }

    /**
     * Seek to chapter
     *
     * @param [chapterIndex] the index of the chapter to seek to
     * @return the [PlayerChapter] which has been sought to
     */
    private fun seekToChapter(chapterIndex: Int): PlayerChapter? {
        return getChapters().getOrNull(chapterIndex)?.also { chapter ->
            player.seekTo(chapter.startPosition)
        }
    }

    /**
     * Seek to the next chapter
     *
     * @return the [PlayerChapter] which has been sought to
     */
    fun seekToNextChapter(): PlayerChapter? {
        return getNextChapterIndex()?.let { seekToChapter(it) }
    }

    /**
     * Seek to the previous chapter Will seek to start of current chapter if player position is more
     * than 5 seconds past start of chapter
     *
     * @return the [PlayerChapter] which has been sought to
     */
    fun seekToPreviousChapter(): PlayerChapter? {
        return getPreviousChapterIndex()?.let { seekToChapter(it) }
    }

    fun switchToEpisode(episodeId: UUID) {
        liveReconnectJob?.cancel()
        playbackPosition = 0L
        currentMediaItemIndex = 0
        savedStateHandle["position"] = 0L
        initializePlayer(
            itemId = episodeId,
            itemKind = BaseItemKind.EPISODE.serialName,
            startFromBeginning = false,
        )
    }

    fun switchChannel(delta: Int): Boolean {
        val nextChannelId = playlistManager.getChannelIdAtOffset(delta) ?: return false
        return switchToChannel(nextChannelId)
    }

    fun switchToChannel(channelId: UUID): Boolean {
        liveReconnectJob?.cancel()
        liveReconnectAttempts = 0
        playbackPosition = 0L
        currentMediaItemIndex = 0
        savedStateHandle["position"] = 0L
        val previousLiveStreamIds = items.mapNotNull { it.liveStreamId }.distinct()
        if (previousLiveStreamIds.isNotEmpty()) {
            viewModelScope.launch {
                previousLiveStreamIds.forEach { repository.closeLiveStream(it) }
            }
        }
        initializePlayer(
            itemId = channelId,
            itemKind = org.jellyfin.sdk.model.api.BaseItemKind.TV_CHANNEL.serialName,
            startFromBeginning = true,
        )
        return true
    }

    fun reconnectCurrentChannel(delayMs: Long = 800L) {
        val channelId = _uiState.value.currentChannel?.id ?: _uiState.value.currentItemId ?: return
        liveReconnectJob?.cancel()
        playbackPosition = 0L
        currentMediaItemIndex = 0
        savedStateHandle["position"] = 0L
        _uiState.update { it.copy(isReconnectingLive = true) }
        liveReconnectJob =
            viewModelScope.launch {
                if (delayMs > 0L) delay(delayMs)
                val previousLiveStreamIds = items.mapNotNull { it.liveStreamId }.distinct()
                previousLiveStreamIds.forEach { repository.closeLiveStream(it) }
                initializePlayer(
                    itemId = channelId,
                    itemKind = org.jellyfin.sdk.model.api.BaseItemKind.TV_CHANNEL.serialName,
                    startFromBeginning = true,
                )
            }
    }

    override fun onIsPlayingChanged(isPlaying: Boolean) {
        super.onIsPlayingChanged(isPlaying)
        if (isPlaying && isBlockedByConcurrentLimit) {
            runCatching {
                player.pause()
                player.stop()
            }
            return
        }
        eventsChannel.trySend(PlayerEvents.IsPlayingChanged(isPlaying))
    }

    fun getLiveBufferAheadSec(): Double {
        val mpv = player as? MPVPlayer
        if (mpv != null) {
            return mpv.getBufferedAheadSec()
        }
        val pos = player.currentPosition.coerceAtLeast(0L)
        return ((player.bufferedPosition - pos).coerceAtLeast(0L)) / 1000.0
    }

    fun getPlaybackStatsText(): String {
        val currentItem = items.getOrNull(player.currentMediaItemIndex)
        val exo = player as? ExoPlayer
        val mpv = player as? MPVPlayer
        val mpvStats = mpv?.getPlaybackStats()
        val vFormat = exo?.videoFormat
        val aFormat = exo?.audioFormat
        val vCounters = exo?.videoDecoderCounters

        val width =
            if (mpvStats != null) {
                mpvStats.width.takeIf { it > 0 } ?: player.videoSize.width.takeIf { it > 0 } ?: 0
            } else {
                vFormat?.width?.takeIf { it > 0 } ?: player.videoSize.width.takeIf { it > 0 } ?: 0
            }
        val height =
            if (mpvStats != null) {
                mpvStats.height.takeIf { it > 0 } ?: player.videoSize.height.takeIf { it > 0 } ?: 0
            } else {
                vFormat?.height?.takeIf { it > 0 } ?: player.videoSize.height.takeIf { it > 0 } ?: 0
            }
        val fps =
            if (mpvStats != null) {
                mpvStats.fps?.takeIf { it > 0.0 }?.let { String.format("%.2f fps", it) } ?: "N/A"
            } else {
                vFormat?.frameRate?.takeIf { it > 0f }?.let { String.format("%.2f fps", it) }
                    ?: "N/A"
            }
        val videoCodec =
            if (mpvStats != null) {
                mpvStats.videoCodec?.uppercase() ?: "Auto"
            } else {
                vFormat?.sampleMimeType?.substringAfter("/")?.uppercase()
                    ?: vFormat?.codecs
                    ?: "Auto"
            }
        val videoCodecProfile =
            if (mpvStats != null) {
                mpvStats.hwdec?.takeIf { it.isNotBlank() && it != "no" }?.let { " [HW: $it]" }
                    ?: " [SW]"
            } else {
                vFormat?.codecs?.let { " ($it)" } ?: ""
            }
        val rangeLabel =
            if (mpvStats != null) {
                when (mpvStats.gamma?.lowercase()) {
                    "pq" -> "HDR10 / PQ"
                    "hlg" -> "HLG"
                    else -> currentItem?.videoRangeLabel ?: "SDR"
                }
            } else {
                currentItem?.videoRangeLabel ?: "SDR"
            }

        val vBitrateBps: Long? =
            if (mpvStats != null) {
                mpvStats.videoBitrateBps?.takeIf { it > 0L }
                    ?: currentItem?.videoStreamBitrate?.toLong()
                    ?: currentItem?.sourceBitrate?.toLong()
            } else {
                vFormat?.bitrate?.takeIf { it > 0 }?.toLong()
                    ?: currentItem?.videoStreamBitrate?.toLong()
                    ?: currentItem?.sourceBitrate?.toLong()
            }
        val vBitrateStr =
            vBitrateBps?.let { String.format("%.2f Mbps", it / 1_000_000.0) } ?: "Variable"

        val audioCodec =
            if (mpvStats != null) {
                mpvStats.audioCodec?.uppercase() ?: "Auto"
            } else {
                aFormat?.sampleMimeType?.substringAfter("/")?.uppercase()
                    ?: aFormat?.codecs
                    ?: "Auto"
            }
        val rawAudioChannels =
            if (mpvStats != null) {
                mpvStats.audioChannels
            } else {
                aFormat?.channelCount?.takeIf { it > 0 }
            }
        val audioChannels =
            rawAudioChannels?.let { ch ->
                when (ch) {
                    1 -> "1.0 Mono"
                    2 -> "2.0 Stereo"
                    6 -> "5.1 Surround"
                    8 -> "7.1 Surround"
                    else -> "$ch ch"
                }
            } ?: "N/A"
        val audioSampleRate =
            if (mpvStats != null) {
                mpvStats.audioSampleRate?.let { "${it / 1000.0} kHz" } ?: ""
            } else {
                aFormat?.sampleRate?.takeIf { it > 0 }?.let { "${it / 1000.0} kHz" } ?: ""
            }
        val aBitrateBps =
            if (mpvStats != null) {
                mpvStats.audioBitrateBps?.toInt() ?: currentItem?.audioStreamBitrate
            } else {
                aFormat?.bitrate?.takeIf { it > 0 } ?: currentItem?.audioStreamBitrate
            }
        val aBitrateStr = aBitrateBps?.let { "${it / 1000} kbps" } ?: "Variable"
        val audioLang =
            if (mpvStats != null) {
                mpvStats.audioLang?.uppercase() ?: ""
            } else {
                aFormat?.language?.uppercase() ?: ""
            }

        val framesLine =
            if (mpvStats != null) {
                val avSyncSuffix =
                    mpvStats.avSyncMs?.let { " · A/V Sync: ${String.format("%.1f ms", it)}" } ?: ""
                "• Frames: ${mpvStats.droppedFrames + mpvStats.decoderDroppedFrames} dropped (dec: ${mpvStats.decoderDroppedFrames}, delayed: ${mpvStats.voDelayedFrames})$avSyncSuffix"
            } else {
                val droppedFrames = vCounters?.droppedBufferCount ?: 0
                val renderedFrames = vCounters?.renderedOutputBufferCount ?: 0
                val maxConsecutiveDropped = vCounters?.maxConsecutiveDroppedBufferCount ?: 0
                val totalFrames = droppedFrames + renderedFrames
                val dropPct =
                    if (totalFrames > 0) String.format("%.1f%%", (droppedFrames * 100.0) / totalFrames)
                    else "0.0%"
                "• Frames: $droppedFrames dropped / $renderedFrames rendered ($dropPct) [máx consec: $maxConsecutiveDropped]"
            }

        val bufferAheadSec =
            mpvStats?.bufferAheadSec?.takeIf { it > 0.0 } ?: getLiveBufferAheadSec()
        val backendName =
            when {
                exo != null -> "ExoPlayer (Media3)"
                mpvFallbackReason != null -> "libmpv (Auto-Fallback: $mpvFallbackReason)"
                else -> "libmpv (Native)"
            }
        val sourceLabel =
            currentItem?.sourceName?.takeIf { it.isNotBlank() } ?: uiState.value.currentItemTitle
        val sourcesCountInfo =
            currentItem?.totalSourcesCount?.let { count ->
                if (count > 1) " [Smart Auto · $count symlinks]" else " [Direct Play]"
            } ?: ""

        return buildString {
            appendLine("ⓘ Estadísticas de Reproducción (Jellyx.tv)")
            appendLine("──────────────────────────────────────")
            appendLine("• Fuente: $sourceLabel$sourcesCountInfo")
            appendLine("• Motor: $backendName")
            appendLine("• Video: ${width}x${height} @ $fps · $videoCodec$videoCodecProfile · $rangeLabel")
            appendLine("• Bitrate Video: $vBitrateStr")
            appendLine(
                "• Audio: $audioCodec · $audioChannels ${if (audioSampleRate.isNotEmpty()) "($audioSampleRate)" else ""} ${if (audioLang.isNotEmpty()) "[$audioLang]" else ""}"
            )
            appendLine("• Bitrate Audio: $aBitrateStr")
            appendLine(framesLine)
            append("• Buffer en RAM/Caché: ${String.format("%.1f s", bufferAheadSec)}")
        }
    }
}

sealed interface PlayerEvents {
    data object NavigateBack : PlayerEvents

    data class IsPlayingChanged(val isPlaying: Boolean) : PlayerEvents

    data class PlayerSwitched(val newPlayer: Player) : PlayerEvents
}
