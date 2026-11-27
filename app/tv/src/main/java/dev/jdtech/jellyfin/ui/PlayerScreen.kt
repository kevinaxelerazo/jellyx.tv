package dev.jdtech.jellyfin.ui

import androidx.activity.compose.BackHandler
import androidx.compose.foundation.BorderStroke
import androidx.compose.foundation.background
import androidx.compose.foundation.border
import androidx.compose.foundation.focusable
import androidx.compose.foundation.interaction.MutableInteractionSource
import androidx.compose.foundation.interaction.collectIsFocusedAsState
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.fillMaxHeight
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.layout.width
import androidx.compose.foundation.lazy.LazyColumn
import androidx.compose.foundation.lazy.LazyRow
import androidx.compose.foundation.lazy.itemsIndexed
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.runtime.DisposableEffect
import androidx.compose.runtime.LaunchedEffect
import androidx.compose.runtime.collectAsState
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableDoubleStateOf
import androidx.compose.runtime.mutableLongStateOf
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.setValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clip
import androidx.compose.ui.focus.FocusRequester
import androidx.compose.ui.focus.focusRequester
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.input.key.KeyEventType
import androidx.compose.ui.input.key.nativeKeyCode
import androidx.compose.ui.input.key.onPreviewKeyEvent
import androidx.compose.ui.input.key.type
import androidx.compose.ui.layout.ContentScale
import androidx.compose.ui.platform.LocalContext
import androidx.compose.ui.platform.LocalView
import androidx.compose.ui.res.painterResource
import androidx.compose.ui.res.stringResource
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.text.style.TextOverflow
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import androidx.compose.ui.viewinterop.AndroidView
import androidx.compose.ui.window.Dialog
import androidx.compose.ui.zIndex
import androidx.hilt.lifecycle.viewmodel.compose.hiltViewModel
import androidx.lifecycle.Lifecycle
import androidx.lifecycle.LifecycleEventObserver
import androidx.lifecycle.compose.LocalLifecycleOwner
import androidx.media3.common.C
import androidx.media3.common.Player
import androidx.media3.common.util.UnstableApi
import androidx.media3.session.MediaSession
import androidx.media3.ui.CaptionStyleCompat
import androidx.media3.ui.PlayerView
import androidx.media3.ui.SubtitleView
import androidx.tv.material3.Border
import androidx.tv.material3.Button
import androidx.tv.material3.ButtonDefaults
import androidx.tv.material3.ClickableSurfaceDefaults
import androidx.tv.material3.Glow
import androidx.tv.material3.Icon
import androidx.tv.material3.IconButton
import androidx.tv.material3.MaterialTheme
import androidx.tv.material3.Surface
import coil3.compose.AsyncImage
import dev.jdtech.jellyfin.core.R
import dev.jdtech.jellyfin.core.presentation.components.ConcurrentScreenAlertDialog
import dev.jdtech.jellyfin.core.presentation.components.JellyxLoadingLogo
import dev.jdtech.jellyfin.film.presentation.livetv.computeProgramProgress
import dev.jdtech.jellyfin.film.presentation.livetv.formatLiveEpgSummary
import dev.jdtech.jellyfin.film.presentation.livetv.formatNextProgramLabel
import dev.jdtech.jellyfin.models.JellyxChannel
import dev.jdtech.jellyfin.models.JellyxEpisode
import dev.jdtech.jellyfin.models.JellyxItem
import dev.jdtech.jellyfin.models.JellyxShow
import dev.jdtech.jellyfin.player.core.domain.models.Track
import org.jellyfin.sdk.model.api.BaseItemKind
import dev.jdtech.jellyfin.player.local.R as PlayerLocalR
import dev.jdtech.jellyfin.player.local.presentation.PlayerEvents
import dev.jdtech.jellyfin.player.local.presentation.PlayerViewModel
import dev.jdtech.jellyfin.presentation.theme.spacings
import dev.jdtech.jellyfin.ui.components.player.VideoPlayerControlsLayout
import dev.jdtech.jellyfin.ui.components.player.VideoPlayerMediaButton
import dev.jdtech.jellyfin.ui.components.player.VideoPlayerMediaTitle
import dev.jdtech.jellyfin.ui.components.player.VideoPlayerOverlay
import dev.jdtech.jellyfin.ui.components.player.VideoPlayerSeeker
import dev.jdtech.jellyfin.ui.components.player.VideoPlayerState
import dev.jdtech.jellyfin.ui.components.player.rememberVideoPlayerState
import dev.jdtech.jellyfin.ui.dialogs.VideoPlayerTrackSelectorDialog
import dev.jdtech.jellyfin.utils.TvMediaKeyAction
import dev.jdtech.jellyfin.utils.TvSeekKeyHelper
import dev.jdtech.jellyfin.utils.handleDPadKeyEvents
import java.time.Instant
import java.time.ZoneId
import java.time.format.DateTimeFormatter
import java.util.Locale
import java.util.UUID
import kotlin.time.Duration.Companion.milliseconds
import kotlinx.coroutines.delay

@Composable
fun PlayerScreen(
    itemId: UUID,
    itemKind: String,
    startFromBeginning: Boolean,
    onNavigateBack: () -> Unit = {},
    onNavigateToSeason: (UUID) -> Unit = {},
    onOpenRecommendedItem: (UUID, BaseItemKind) -> Unit = { _, _ -> },
) {
    val viewModel = hiltViewModel<PlayerViewModel>()

    val uiState by viewModel.uiState.collectAsState()

    val context = LocalContext.current
    val currentView = LocalView.current

    // Keep the screen on while player is show
    DisposableEffect(Unit) {
        currentView.keepScreenOn = true
        onDispose { currentView.keepScreenOn = false }
    }

    var lifecycle by remember { mutableStateOf(Lifecycle.Event.ON_CREATE) }
    var mediaSession by remember { mutableStateOf<MediaSession?>(null) }
    var activePlayer by remember { mutableStateOf(viewModel.player) }
    val lifecycleOwner = LocalLifecycleOwner.current
    DisposableEffect(lifecycleOwner, activePlayer) {
        val observer = LifecycleEventObserver { _, event ->
            lifecycle = event

            // Handle creation and release of media session
            when (event) {
                Lifecycle.Event.ON_STOP -> {
                    runCatching { mediaSession?.release() }
                    mediaSession = null
                }

                Lifecycle.Event.ON_START -> {
                    runCatching { mediaSession?.release() }
                    mediaSession = MediaSession.Builder(context, activePlayer).build()
                }

                else -> {}
            }
        }
        lifecycleOwner.lifecycle.addObserver(observer)
        if (
            lifecycleOwner.lifecycle.currentState.isAtLeast(Lifecycle.State.STARTED) &&
                mediaSession == null
        ) {
            runCatching { mediaSession?.release() }
            mediaSession = MediaSession.Builder(context, activePlayer).build()
        }

        onDispose {
            lifecycleOwner.lifecycle.removeObserver(observer)
            runCatching { mediaSession?.release() }
            mediaSession = null
        }
    }

    val videoPlayerState = rememberVideoPlayerState()
    var activeTrackSelectorType by remember { mutableStateOf<Int?>(null) }
    var showSyncPlayDialog by remember { mutableStateOf(false) }
    var showLiveChannelDrawer by remember { mutableStateOf(false) }
    var showEpisodesDrawer by remember { mutableStateOf(false) }
    var showStatsOverlay by remember { mutableStateOf(false) }
    var statsText by remember { mutableStateOf("") }
    var seekFeedbackDeltaSeconds by remember { mutableStateOf<Int?>(null) }
    var seekFeedbackTrigger by remember { mutableLongStateOf(0L) }
    var pendingSeekTargetMs by remember { mutableStateOf<Long?>(null) }
    var pendingSeekCommitTrigger by remember { mutableLongStateOf(0L) }
    var hasUncommittedSeek by remember { mutableStateOf(false) }

    BackHandler(enabled = showLiveChannelDrawer || showEpisodesDrawer) {
        showLiveChannelDrawer = false
        showEpisodesDrawer = false
    }

    LaunchedEffect(seekFeedbackTrigger) {
        if (seekFeedbackTrigger > 0L) {
            delay(1200)
            seekFeedbackDeltaSeconds = null
            pendingSeekTargetMs = null
            hasUncommittedSeek = false
        }
    }

    LaunchedEffect(pendingSeekCommitTrigger) {
        if (pendingSeekCommitTrigger > 0L) {
            delay(180)
            if (hasUncommittedSeek) {
                pendingSeekTargetMs?.let { target ->
                    activePlayer.seekTo(target)
                    viewModel.notifySyncPlaySeek(target)
                }
                hasUncommittedSeek = false
            }
        }
    }

    LaunchedEffect(Unit) {
        viewModel.eventsChannelFlow.collect { event ->
            when (event) {
                is PlayerEvents.PlayerSwitched -> activePlayer = event.newPlayer
                is PlayerEvents.NavigateBack -> onNavigateBack()
                else -> Unit
            }
        }
    }

    var currentPosition by remember { mutableLongStateOf(0L) }
    var isPlaying by remember { mutableStateOf(activePlayer.isPlaying) }
    var isBuffering by remember { mutableStateOf(false) }
    var bufferAheadSec by remember { mutableDoubleStateOf(0.0) }
    val playerBoxFocusRequester = remember { FocusRequester() }

    LaunchedEffect(uiState.isLiveTv, uiState.currentChannel?.id) {
        if (uiState.isLiveTv && uiState.currentChannel != null && !showLiveChannelDrawer) {
            videoPlayerState.showControls()
        }
    }

    LaunchedEffect(activePlayer) {
        while (true) {
            delay(300)
            if (pendingSeekTargetMs == null) {
                currentPosition = activePlayer.currentPosition
            }
            isPlaying = activePlayer.isPlaying
            isBuffering = activePlayer.playbackState == Player.STATE_BUFFERING
            bufferAheadSec = viewModel.getLiveBufferAheadSec()
            if (uiState.fileLoaded && !uiState.isLiveTv) {
                viewModel.updateCurrentSegment()
            }
            if (showStatsOverlay) {
                statsText = viewModel.getPlaybackStatsText()
            }
        }
    }

    val segment = uiState.currentSegment
    val durationMs = activePlayer.duration
    val remainingMs = if (durationMs > 0L) (durationMs - currentPosition).coerceAtLeast(0L) else 0L
    val showFallbackNextEpisode =
        segment == null &&
            !uiState.isLiveTv &&
            !uiState.playbackEnded &&
            (activePlayer.hasNextMediaItem() || uiState.hasNextEpisode) &&
            durationMs > 0L &&
            remainingMs in 1L..30_000L

    LaunchedEffect(
        videoPlayerState.controlsVisible,
        activeTrackSelectorType,
        showSyncPlayDialog,
        showLiveChannelDrawer,
        showEpisodesDrawer,
        uiState.currentSegment,
        showFallbackNextEpisode,
        uiState.playbackEnded,
    ) {
        if (showLiveChannelDrawer || showEpisodesDrawer) {
            videoPlayerState.hideControls()
        } else if (activeTrackSelectorType != null || showSyncPlayDialog) {
            videoPlayerState.showControls(seconds = Int.MAX_VALUE)
        } else if (videoPlayerState.controlsVisible && isPlaying) {
            videoPlayerState.showControls()
        } else if (
            !videoPlayerState.controlsVisible &&
                !showLiveChannelDrawer &&
                !showEpisodesDrawer &&
                activeTrackSelectorType == null &&
                !showSyncPlayDialog &&
                !uiState.playbackEnded &&
                uiState.currentSegment == null &&
                !showFallbackNextEpisode
        ) {
            runCatching { playerBoxFocusRequester.requestFocus() }
            delay(350L)
            runCatching { playerBoxFocusRequester.requestFocus() }
        }
    }

    fun performIncrementalSeek(direction: Int, repeatCount: Int) {
        val stepMs = TvSeekKeyHelper.computeSeekStepMs(repeatCount)
        val basePos = pendingSeekTargetMs ?: activePlayer.currentPosition.coerceAtLeast(0L)
        val targetMs =
            TvSeekKeyHelper.computeNextSeekPosition(
                basePositionMs = basePos,
                deltaMs = direction * stepMs,
                durationMs = activePlayer.duration,
            )
        pendingSeekTargetMs = targetMs
        currentPosition = targetMs
        val deltaSec = (direction * (stepMs / 1000L)).toInt()
        seekFeedbackDeltaSeconds =
            (seekFeedbackDeltaSeconds ?: 0).let { prev ->
                if ((prev < 0 && deltaSec < 0) || (prev > 0 && deltaSec > 0)) {
                    prev + deltaSec
                } else {
                    deltaSec
                }
            }
        seekFeedbackTrigger++
        if (repeatCount == 0) {
            activePlayer.seekTo(targetMs)
            viewModel.notifySyncPlaySeek(targetMs)
            hasUncommittedSeek = false
        } else {
            hasUncommittedSeek = true
            pendingSeekCommitTrigger++
        }
    }

    // TODO: implement the track selection dialogs
    /*
    resultRecipient.onNavResult { result ->
        when (result) {
            is NavResult.Canceled -> Unit
            is NavResult.Value -> {
                val trackType = result.value.trackType
                val index = result.value.index

                if (index == -1) {
                    viewModel.player.trackSelectionParameters = viewModel.player.trackSelectionParameters
                        .buildUpon()
                        .clearOverridesOfType(trackType)
                        .setTrackTypeDisabled(trackType, true)
                        .build()
                } else {
                    viewModel.player.trackSelectionParameters = viewModel.player.trackSelectionParameters
                        .buildUpon()
                        .setOverrideForType(
                            TrackSelectionOverride(viewModel.player.currentTracks.groups[index].mediaTrackGroup, 0),
                        )
                        .setTrackTypeDisabled(trackType, false)
                        .build()
                }
            }
        }
    }
     */

    Box(
        modifier =
            Modifier.focusRequester(playerBoxFocusRequester)
                .onPreviewKeyEvent { keyEvent ->
                    val keyCode = keyEvent.nativeKeyEvent.keyCode
                    val repeatCount = keyEvent.nativeKeyEvent.repeatCount
                    val action = TvSeekKeyHelper.classifyKeyCode(keyCode)
                    val isModalOrDrawerOpen =
                        showLiveChannelDrawer ||
                            showEpisodesDrawer ||
                            activeTrackSelectorType != null ||
                            showSyncPlayDialog ||
                            uiState.playbackEnded

                    if (isModalOrDrawerOpen) {
                        return@onPreviewKeyEvent false
                    }

                    when (keyEvent.type) {
                        KeyEventType.KeyDown -> {
                            when (action) {
                                TvMediaKeyAction.CHANNEL_UP -> {
                                    if (repeatCount == 0) {
                                        viewModel.switchChannel(1)
                                    }
                                    true
                                }
                                TvMediaKeyAction.CHANNEL_DOWN -> {
                                    if (repeatCount == 0) {
                                        viewModel.switchChannel(-1)
                                    }
                                    true
                                }
                                TvMediaKeyAction.SEEK_BACKWARD -> {
                                    if (!uiState.isLiveTv) {
                                        performIncrementalSeek(-1, repeatCount)
                                        if (videoPlayerState.controlsVisible) {
                                            videoPlayerState.showControls()
                                        }
                                        true
                                    } else {
                                        false
                                    }
                                }
                                TvMediaKeyAction.SEEK_FORWARD -> {
                                    if (!uiState.isLiveTv) {
                                        performIncrementalSeek(1, repeatCount)
                                        if (videoPlayerState.controlsVisible) {
                                            videoPlayerState.showControls()
                                        }
                                        true
                                    } else {
                                        false
                                    }
                                }
                                TvMediaKeyAction.PLAY_PAUSE -> {
                                    if (repeatCount == 0) {
                                        val shouldPlay = !activePlayer.isPlaying
                                        if (shouldPlay) {
                                            activePlayer.play()
                                        } else {
                                            activePlayer.pause()
                                        }
                                        viewModel.notifySyncPlayPlayPause(shouldPlay)
                                        videoPlayerState.showControls()
                                    }
                                    true
                                }
                                TvMediaKeyAction.PLAY -> {
                                    if (repeatCount == 0) {
                                        activePlayer.play()
                                        viewModel.notifySyncPlayPlayPause(true)
                                        videoPlayerState.showControls()
                                    }
                                    true
                                }
                                TvMediaKeyAction.PAUSE -> {
                                    if (repeatCount == 0) {
                                        activePlayer.pause()
                                        viewModel.notifySyncPlayPlayPause(false)
                                        videoPlayerState.showControls(Int.MAX_VALUE)
                                    }
                                    true
                                }
                                TvMediaKeyAction.DPAD_LEFT -> {
                                    if (!videoPlayerState.controlsVisible) {
                                        if (uiState.isLiveTv) {
                                            if (repeatCount == 0) {
                                                showLiveChannelDrawer = true
                                            }
                                        } else {
                                            performIncrementalSeek(-1, repeatCount)
                                        }
                                        true
                                    } else {
                                        false
                                    }
                                }
                                TvMediaKeyAction.DPAD_RIGHT -> {
                                    if (!videoPlayerState.controlsVisible && !uiState.isLiveTv) {
                                        performIncrementalSeek(1, repeatCount)
                                        true
                                    } else {
                                        false
                                    }
                                }
                                else -> false
                            }
                        }
                        KeyEventType.KeyUp -> {
                            when (action) {
                                TvMediaKeyAction.SEEK_BACKWARD,
                                TvMediaKeyAction.SEEK_FORWARD -> {
                                    if (!uiState.isLiveTv) {
                                        if (hasUncommittedSeek) {
                                            pendingSeekTargetMs?.let { target ->
                                                activePlayer.seekTo(target)
                                                viewModel.notifySyncPlaySeek(target)
                                            }
                                            hasUncommittedSeek = false
                                        }
                                        true
                                    } else {
                                        false
                                    }
                                }
                                TvMediaKeyAction.DPAD_LEFT -> {
                                    if (!videoPlayerState.controlsVisible) {
                                        if (!uiState.isLiveTv && hasUncommittedSeek) {
                                            pendingSeekTargetMs?.let { target ->
                                                activePlayer.seekTo(target)
                                                viewModel.notifySyncPlaySeek(target)
                                            }
                                            hasUncommittedSeek = false
                                        }
                                        true
                                    } else {
                                        false
                                    }
                                }
                                TvMediaKeyAction.DPAD_RIGHT -> {
                                    if (!videoPlayerState.controlsVisible && !uiState.isLiveTv) {
                                        if (hasUncommittedSeek) {
                                            pendingSeekTargetMs?.let { target ->
                                                activePlayer.seekTo(target)
                                                viewModel.notifySyncPlaySeek(target)
                                            }
                                            hasUncommittedSeek = false
                                        }
                                        true
                                    } else {
                                        false
                                    }
                                }
                                TvMediaKeyAction.PLAY_PAUSE,
                                TvMediaKeyAction.PLAY,
                                TvMediaKeyAction.PAUSE,
                                TvMediaKeyAction.CHANNEL_UP,
                                TvMediaKeyAction.CHANNEL_DOWN -> true
                                else ->
                                    !videoPlayerState.controlsVisible &&
                                        keyCode == android.view.KeyEvent.KEYCODE_DPAD_UP &&
                                        videoPlayerState.wasRecentlyHidden()
                            }
                        }
                        else -> false
                    }
                }
                .dPadEvents(
                    exoPlayer = activePlayer,
                    videoPlayerState = videoPlayerState,
                    isDrawerOpen = showLiveChannelDrawer || showEpisodesDrawer,
                )
                .focusable()
    ) {
        AndroidView(
            factory = { context ->
                PlayerView(context).also { playerView ->
                    playerView.player = activePlayer
                    playerView.useController = false
                    playerView.setShowBuffering(PlayerView.SHOW_BUFFERING_NEVER)
                    playerView.isFocusable = false
                    playerView.isFocusableInTouchMode = false
                    playerView.descendantFocusability = android.view.ViewGroup.FOCUS_BLOCK_DESCENDANTS
                    playerView.subtitleView?.apply {
                        setApplyEmbeddedStyles(false)
                        setApplyEmbeddedFontSizes(false)
                        setStyle(
                            CaptionStyleCompat(
                                android.graphics.Color.WHITE,
                                android.graphics.Color.TRANSPARENT,
                                android.graphics.Color.TRANSPARENT,
                                CaptionStyleCompat.EDGE_TYPE_OUTLINE,
                                android.graphics.Color.BLACK,
                                null,
                            )
                        )
                        setBottomPaddingFraction(0.03f)
                        setFractionalTextSize(SubtitleView.DEFAULT_TEXT_SIZE_FRACTION * 0.85f)
                    }
                    viewModel.initializePlayer(
                        itemId = itemId,
                        itemKind = itemKind,
                        startFromBeginning = startFromBeginning,
                    )
                    playerView.setBackgroundColor(
                        context.resources.getColor(android.R.color.black, context.theme)
                    )
                }
            },
            update = {
                if (it.player !== activePlayer) {
                    it.player = activePlayer
                }
                when (lifecycle) {
                    Lifecycle.Event.ON_PAUSE -> {
                        it.onPause()
                        it.player?.pause()
                    }

                    Lifecycle.Event.ON_RESUME -> {
                        it.onResume()
                    }

                    else -> Unit
                }
            },
            modifier = Modifier.fillMaxSize(),
        )
        if (isBuffering && !uiState.playbackEnded) {
            JellyxLoadingLogo(modifier = Modifier.align(Alignment.Center))
        }
        val focusRequester = remember { FocusRequester() }
        VideoPlayerOverlay(
            modifier = Modifier.align(Alignment.BottomCenter),
            focusRequester = focusRequester,
            state = videoPlayerState,
            isPlaying = isPlaying,
            controls = {
                VideoPlayerControls(
                    title = uiState.currentItemTitle,
                    isPlaying = isPlaying,
                    contentCurrentPosition = currentPosition,
                    player = activePlayer,
                    state = videoPlayerState,
                    focusRequester = focusRequester,
                    onToggleStats = {
                        showStatsOverlay = !showStatsOverlay
                        if (showStatsOverlay) {
                            statsText = viewModel.getPlaybackStatsText()
                        }
                    },
                    onSelectAudioTrack = { activeTrackSelectorType = C.TRACK_TYPE_AUDIO },
                    onSelectSubtitleTrack = { activeTrackSelectorType = C.TRACK_TYPE_TEXT },
                    isFavorite = uiState.isCurrentItemFavorite,
                    onToggleFavorite = { viewModel.toggleCurrentItemFavorite() },
                    onOpenSyncPlay = {
                        viewModel.refreshSyncPlayGroups()
                        showSyncPlayDialog = true
                    },
                    onPlayPauseSync = { shouldPlay -> viewModel.notifySyncPlayPlayPause(shouldPlay) },
                    onSeekSync = { targetMs -> viewModel.notifySyncPlaySeek(targetMs) },
                    isLiveTv = uiState.isLiveTv,
                    currentChannel = uiState.currentChannel,
                    epgNowEpochSec = uiState.epgNowEpochSec,
                    bufferAheadSec = bufferAheadSec,
                    onOpenChannelDrawer = {
                        videoPlayerState.hideControls()
                        showLiveChannelDrawer = true
                    },
                    onReconnectLive = { viewModel.reconnectCurrentChannel(delayMs = 0L) },
                    hasSeasonNavigation = uiState.currentSeasonId != null,
                    hasEpisodesDrawer = uiState.seasonEpisodes.isNotEmpty(),
                    onGoToSeason = {
                        uiState.currentSeasonId?.let { seasonId ->
                            videoPlayerState.hideControls()
                            onNavigateToSeason(seasonId)
                        }
                    },
                    onOpenEpisodesDrawer = {
                        videoPlayerState.hideControls()
                        showEpisodesDrawer = true
                    },
                )
            },
        )
        if (uiState.isReconnectingLive) {
            Box(
                modifier =
                    Modifier.align(Alignment.TopEnd)
                        .padding(24.dp)
                        .background(Color.Black.copy(alpha = 0.82f), RoundedCornerShape(10.dp))
                        .border(1.dp, Color.White.copy(alpha = 0.16f), RoundedCornerShape(10.dp))
                        .padding(horizontal = 16.dp, vertical = 10.dp)
            ) {
                Row(
                    verticalAlignment = Alignment.CenterVertically,
                    horizontalArrangement = Arrangement.spacedBy(8.dp),
                ) {
                    JellyxLoadingLogo(size = 18.dp)
                    Text(
                        text = "Reconectando señal en vivo...",
                        color = Color.White,
                        fontSize = 13.sp,
                        fontWeight = FontWeight.SemiBold,
                    )
                }
            }
        }
        if (showLiveChannelDrawer && uiState.isLiveTv) {
            LiveChannelDrawerOverlay(
                channels = uiState.liveChannels,
                currentChannelId = uiState.currentChannel?.id ?: uiState.currentItemId,
                epgNowEpochSec = uiState.epgNowEpochSec,
                onSelectChannel = { channel ->
                    showLiveChannelDrawer = false
                    viewModel.switchToChannel(channel.id)
                },
                onToggleFavorite = { channel -> viewModel.toggleChannelFavorite(channel.id) },
                onClose = { showLiveChannelDrawer = false },
            )
        }
        if (showEpisodesDrawer && !uiState.isLiveTv && uiState.seasonEpisodes.isNotEmpty()) {
            EpisodeSelectorDrawerOverlay(
                episodes = uiState.seasonEpisodes,
                currentEpisodeId = uiState.currentItemId,
                hasSeasonNavigation = uiState.currentSeasonId != null,
                onSelectEpisode = { episode ->
                    showEpisodesDrawer = false
                    viewModel.switchToEpisode(episode.id)
                },
                onGoToSeason = {
                    showEpisodesDrawer = false
                    uiState.currentSeasonId?.let { onNavigateToSeason(it) }
                },
                onClose = { showEpisodesDrawer = false },
            )
        }
        if (showStatsOverlay) {
            Box(
                modifier =
                    Modifier.align(Alignment.TopStart)
                        .padding(24.dp)
                        .background(Color.Black.copy(alpha = 0.78f), RoundedCornerShape(8.dp))
                        .padding(12.dp)
            ) {
                Text(text = statsText, color = Color.White)
            }
        }
        seekFeedbackDeltaSeconds?.let { delta ->
            if (!videoPlayerState.controlsVisible) {
                val baseLabel = if (delta >= 0) "⏩ +${delta}s" else "⏪ ${delta}s"
                val label =
                    pendingSeekTargetMs?.let { targetMs ->
                        "$baseLabel  •  ${TvSeekKeyHelper.formatTimestampMs(targetMs)}"
                    } ?: baseLabel
                Box(
                    modifier =
                        Modifier.align(Alignment.BottomCenter)
                            .padding(bottom = 48.dp)
                            .background(Color.Black.copy(alpha = 0.75f), RoundedCornerShape(20.dp))
                            .padding(horizontal = 20.dp, vertical = 10.dp)
                ) {
                    Text(text = label, color = Color.White)
                }
            }
        }

        // Media Segments (Skip Intro / Skip Outro / Next Episode) + fallback Next Episode near end
        if (
            (segment != null || showFallbackNextEpisode) &&
                activeTrackSelectorType == null &&
                !showLiveChannelDrawer &&
                !showEpisodesDrawer &&
                !uiState.playbackEnded
        ) {
            val skipButtonFocusRequester = remember { FocusRequester() }
            val isNextEpisode =
                showFallbackNextEpisode ||
                    uiState.currentSkipButtonStringRes ==
                        PlayerLocalR.string.player_controls_next_episode
            val remainingSeconds =
                if (isNextEpisode && durationMs > 0L) {
                    (remainingMs / 1000L).toInt()
                } else {
                    null
                }
            val skipLabelRes =
                if (showFallbackNextEpisode) {
                    PlayerLocalR.string.player_controls_next_episode
                } else {
                    uiState.currentSkipButtonStringRes
                }

            Box(
                modifier =
                    Modifier.fillMaxSize()
                        .padding(
                            bottom = if (videoPlayerState.controlsVisible) 136.dp else 0.dp
                        )
            ) {
                SkipButton(
                    stringRes = skipLabelRes,
                    countdownSeconds = remainingSeconds,
                    onClick = {
                        if (segment != null) {
                            viewModel.skipSegment(segment)
                        } else {
                            activePlayer.seekToNextMediaItem()
                        }
                    },
                    skipButtonFocusRequester = skipButtonFocusRequester,
                )
            }

            LaunchedEffect(segment, showFallbackNextEpisode, videoPlayerState.controlsVisible) {
                if (!videoPlayerState.controlsVisible && activeTrackSelectorType == null) {
                    runCatching { skipButtonFocusRequester.requestFocus() }
                }
            }
        }

        if (uiState.playbackEnded && uiState.similarItems.isNotEmpty()) {
            PlaybackEndedOverlay(
                currentTitle = uiState.currentItemTitle,
                isFavorite = uiState.isCurrentItemFavorite,
                recommendations = uiState.similarItems.take(4),
                onToggleFavorite = { viewModel.toggleCurrentItemFavorite() },
                onExit = { viewModel.dismissPlaybackEnded() },
                onSelectItem = { item ->
                    val kind =
                        when (item) {
                            is JellyxShow -> BaseItemKind.SERIES
                            is JellyxEpisode -> BaseItemKind.EPISODE
                            else -> BaseItemKind.MOVIE
                        }
                    onOpenRecommendedItem(item.id, kind)
                },
            )
        }
    }

    activeTrackSelectorType?.let { trackType ->
        val tracks = remember(trackType, activePlayer) { getTracks(activePlayer, trackType) }
        Dialog(onDismissRequest = { activeTrackSelectorType = null }) {
            VideoPlayerTrackSelectorDialog(
                trackType = trackType,
                tracks = tracks,
                onTrackSelected = { trackId ->
                    viewModel.switchToTrack(trackType, trackId)
                    activeTrackSelectorType = null
                },
            )
        }
    }

    if (showSyncPlayDialog) {
        Dialog(onDismissRequest = { showSyncPlayDialog = false }) {
            Column(
                modifier =
                    Modifier.width(420.dp)
                        .clip(RoundedCornerShape(16.dp))
                        .background(Color(0xFF1A1A1A))
                        .padding(24.dp),
                verticalArrangement = Arrangement.spacedBy(12.dp),
            ) {
                Text(
                    text = "Watch Party (SyncPlay)",
                    color = Color.White,
                    fontSize = 20.sp,
                    fontWeight = FontWeight.Bold,
                )
                uiState.activeSyncPlayGroupName?.let { groupName ->
                    Text(
                        text = "Conectado a: $groupName",
                        color = Color(0xFF81C784),
                        fontSize = 14.sp,
                    )
                    Button(
                        onClick = {
                            viewModel.leaveSyncPlayGroup()
                            showSyncPlayDialog = false
                        }
                    ) {
                        Text(text = "Salir de Watch Party", color = Color.Black)
                    }
                } ?: run {
                    Button(
                        onClick = {
                            viewModel.createSyncPlayGroup(
                                "Watch Party - ${uiState.currentItemTitle.ifBlank { "JellyX" }}"
                            )
                            showSyncPlayDialog = false
                        }
                    ) {
                        Text(text = "Crear nueva Watch Party", color = Color.Black)
                    }

                    if (uiState.syncPlayGroups.isEmpty()) {
                        Text(
                            text = "No hay salas activas en este momento.",
                            color = Color.White.copy(alpha = 0.7f),
                            fontSize = 13.sp,
                        )
                    } else {
                        uiState.syncPlayGroups.forEach { group ->
                            Button(
                                onClick = {
                                    viewModel.joinSyncPlayGroup(group)
                                    showSyncPlayDialog = false
                                }
                            ) {
                                Text(
                                    text =
                                        "Unirse: ${group.groupName} (${group.participants.size})",
                                    color = Color.Black,
                                )
                            }
                        }
                    }
                }
            }
        }
    }

    uiState.concurrentSessionAlert?.let { alert ->
        ConcurrentScreenAlertDialog(
            header = alert.header,
            message = alert.text,
            isBlocking = alert.isConcurrentStreamBlock || uiState.isPlaybackBlockedByLimit,
            onDismiss = { viewModel.dismissConcurrentSessionAlert() },
        )
    }
}

@androidx.annotation.OptIn(UnstableApi::class)
@Composable
fun VideoPlayerControls(
    title: String,
    isPlaying: Boolean,
    contentCurrentPosition: Long,
    player: Player,
    state: VideoPlayerState,
    focusRequester: FocusRequester,
    onToggleStats: () -> Unit,
    onSelectAudioTrack: () -> Unit,
    onSelectSubtitleTrack: () -> Unit,
    isFavorite: Boolean,
    onToggleFavorite: () -> Unit,
    onOpenSyncPlay: () -> Unit,
    onPlayPauseSync: (Boolean) -> Unit,
    onSeekSync: (Long) -> Unit,
    isLiveTv: Boolean = false,
    currentChannel: JellyxChannel? = null,
    epgNowEpochSec: Long = 0L,
    bufferAheadSec: Double = 0.0,
    onOpenChannelDrawer: () -> Unit = {},
    onReconnectLive: () -> Unit = {},
    hasSeasonNavigation: Boolean = false,
    hasEpisodesDrawer: Boolean = false,
    onGoToSeason: () -> Unit = {},
    onOpenEpisodesDrawer: () -> Unit = {},
) {
    val onPlayPauseToggle = { shouldPlay: Boolean ->
        if (shouldPlay) {
            player.play()
        } else {
            player.pause()
        }
        onPlayPauseSync(shouldPlay)
    }

    val seekerFocusRequester = remember { FocusRequester() }
    val subtitleText =
        if (isLiveTv) {
            currentChannel?.group?.takeIf { it.isNotBlank() }
        } else {
            null
        }

    VideoPlayerControlsLayout(
        mediaTitle = { VideoPlayerMediaTitle(title = title, subtitle = subtitleText) },
        seeker = {
            if (isLiveTv) {
                LivePlayerBufferBar(
                    focusRequester = seekerFocusRequester,
                    state = state,
                    isPlaying = isPlaying,
                    onPlayPauseToggle = onPlayPauseToggle,
                    bufferAheadSec = bufferAheadSec,
                    currentChannel = currentChannel,
                    epgNowEpochSec = epgNowEpochSec,
                )
            } else {
                VideoPlayerSeeker(
                    focusRequester = seekerFocusRequester,
                    state = state,
                    isPlaying = isPlaying,
                    onPlayPauseToggle = onPlayPauseToggle,
                    onSeek = { fraction ->
                        val durationMs = player.duration
                        if (durationMs > 0L) {
                            val targetMs =
                                (durationMs * fraction).toLong().coerceIn(0L, durationMs)
                            player.seekTo(targetMs)
                            onSeekSync(targetMs)
                        }
                    },
                    contentProgress = contentCurrentPosition.milliseconds,
                    contentDuration = player.duration.milliseconds,
                )
            }
        },
        mediaActions = {
            Row(horizontalArrangement = Arrangement.spacedBy(MaterialTheme.spacings.medium)) {
                VideoPlayerMediaButton(
                    icon = painterResource(id = R.drawable.ic_info),
                    state = state,
                    isPlaying = isPlaying,
                    modifier = Modifier.focusRequester(focusRequester),
                    onClick = onToggleStats,
                )
                if (isLiveTv) {
                    VideoPlayerMediaButton(
                        icon = painterResource(id = R.drawable.ic_tv),
                        state = state,
                        isPlaying = isPlaying,
                        onClick = onOpenChannelDrawer,
                    )
                    VideoPlayerMediaButton(
                        icon = painterResource(id = R.drawable.ic_rotate_ccw),
                        state = state,
                        isPlaying = isPlaying,
                        onClick = onReconnectLive,
                    )
                }
                VideoPlayerMediaButton(
                    icon = painterResource(id = R.drawable.ic_speaker),
                    state = state,
                    isPlaying = isPlaying,
                    onClick = onSelectAudioTrack,
                )
                VideoPlayerMediaButton(
                    icon = painterResource(id = R.drawable.ic_closed_caption),
                    state = state,
                    isPlaying = isPlaying,
                    onClick = onSelectSubtitleTrack,
                )
                VideoPlayerMediaButton(
                    icon =
                        painterResource(
                            id = if (isFavorite) R.drawable.ic_heart_filled else R.drawable.ic_heart
                        ),
                    state = state,
                    isPlaying = isPlaying,
                    onClick = onToggleFavorite,
                )
                if (!isLiveTv) {
                    if (hasSeasonNavigation) {
                        VideoPlayerMediaButton(
                            icon = painterResource(id = R.drawable.ic_arrow_left),
                            state = state,
                            isPlaying = isPlaying,
                            onClick = onGoToSeason,
                        )
                    }
                    if (hasEpisodesDrawer) {
                        VideoPlayerMediaButton(
                            icon = painterResource(id = R.drawable.ic_library),
                            state = state,
                            isPlaying = isPlaying,
                            onClick = onOpenEpisodesDrawer,
                        )
                    }
                    VideoPlayerMediaButton(
                        icon = painterResource(id = R.drawable.ic_watch_party),
                        state = state,
                        isPlaying = isPlaying,
                        onClick = onOpenSyncPlay,
                    )
                }
            }
        },
    )
}

@Composable
private fun LivePlayerBufferBar(
    focusRequester: FocusRequester,
    state: VideoPlayerState,
    isPlaying: Boolean,
    onPlayPauseToggle: (Boolean) -> Unit,
    bufferAheadSec: Double,
    currentChannel: JellyxChannel?,
    epgNowEpochSec: Long,
) {
    val interactionSource = remember { MutableInteractionSource() }
    val isFocused by interactionSource.collectIsFocusedAsState()

    LaunchedEffect(isFocused && isPlaying) {
        if (isFocused && isPlaying) {
            state.showControls()
        }
    }

    val timeFormatter = remember { DateTimeFormatter.ofPattern("HH:mm") }
    val nowDateTime =
        remember(epgNowEpochSec) {
            Instant.ofEpochSecond(epgNowEpochSec)
                .atZone(ZoneId.systemDefault())
                .toLocalDateTime()
        }
    val currentProgram = currentChannel?.currentProgram
    val startDate = currentProgram?.startDate
    val endDate = currentProgram?.endDate
    val epgProgress =
        computeProgramProgress(
            start = startDate,
            end = endDate,
            now = nowDateTime,
        )
    val epgSummary =
        formatLiveEpgSummary(
            start = startDate,
            end = endDate,
            now = nowDateTime,
            timeFormatter = timeFormatter,
        )
    val nextLabel = formatNextProgramLabel(currentChannel?.nextProgram, timeFormatter)

    Column(
        modifier =
            Modifier.fillMaxWidth()
                .clip(RoundedCornerShape(16.dp))
                .background(Color(0xCC0B111E))
                .border(1.dp, Color.White.copy(alpha = 0.16f), RoundedCornerShape(16.dp))
                .padding(horizontal = 20.dp, vertical = 16.dp),
        verticalArrangement = Arrangement.spacedBy(10.dp),
    ) {
        // Row 1 — Channel identity (left) + preload/cache health (right)
        Row(
            modifier = Modifier.fillMaxWidth(),
            verticalAlignment = Alignment.CenterVertically,
            horizontalArrangement = Arrangement.spacedBy(10.dp),
        ) {
            Row(
                modifier = Modifier.weight(1f),
                verticalAlignment = Alignment.CenterVertically,
                horizontalArrangement = Arrangement.spacedBy(8.dp),
            ) {
                if (currentChannel != null) {
                    val logoUri = currentChannel.images.primary ?: currentChannel.images.backdrop
                    if (logoUri != null) {
                        Box(
                            modifier =
                                Modifier.size(width = 38.dp, height = 26.dp)
                                    .clip(RoundedCornerShape(4.dp))
                                    .background(Color(0xFF10141C)),
                            contentAlignment = Alignment.Center,
                        ) {
                            AsyncImage(
                                model = logoUri,
                                contentDescription = currentChannel.displayName,
                                contentScale = ContentScale.Fit,
                                modifier = Modifier.fillMaxSize().padding(2.dp),
                            )
                        }
                    }
                }
                Box(
                    modifier =
                        Modifier.clip(RoundedCornerShape(6.dp))
                            .background(Color(0xFFEF4444))
                            .padding(horizontal = 8.dp, vertical = 2.dp)
                ) {
                    Text(
                        text = "● EN VIVO",
                        color = Color.White,
                        fontSize = 11.sp,
                        fontWeight = FontWeight.Bold,
                    )
                }
                val channelTitle =
                    if (currentChannel != null) {
                        "${if (!currentChannel.channelNumber.isNullOrBlank()) "CH ${currentChannel.channelNumber} · " else ""}${currentChannel.displayName}"
                    } else {
                        "Live TV"
                    }
                Text(
                    text = channelTitle,
                    color = Color.White.copy(alpha = 0.92f),
                    fontSize = 14.sp,
                    fontWeight = FontWeight.SemiBold,
                    maxLines = 1,
                    overflow = TextOverflow.Ellipsis,
                    modifier = Modifier.weight(1f),
                )
            }

            Column(
                horizontalAlignment = Alignment.End,
                verticalArrangement = Arrangement.spacedBy(3.dp),
            ) {
                Text(
                    text =
                        String.format(
                            Locale.getDefault(),
                            "Precarga: %.1fs / 30s",
                            bufferAheadSec,
                        ),
                    color = Color.White.copy(alpha = 0.78f),
                    fontSize = 11.sp,
                )
                val cacheFraction = (bufferAheadSec / 30.0).toFloat().coerceIn(0.03f, 1f)
                val cacheColor =
                    if (bufferAheadSec >= 8.0) Color(0xFF38BDF8) else Color(0xFFF59E0B)
                Box(
                    modifier =
                        Modifier.width(116.dp)
                            .height(4.5.dp)
                            .clip(RoundedCornerShape(3.dp))
                            .background(Color.White.copy(alpha = 0.22f))
                ) {
                    Box(
                        modifier =
                            Modifier.fillMaxWidth(cacheFraction)
                                .fillMaxHeight()
                                .clip(RoundedCornerShape(3.dp))
                                .background(cacheColor)
                    )
                }
            }
        }

        // Row 2 — Hero current program title
        Text(
            text =
                currentProgram?.name?.takeIf { it.isNotBlank() } ?: "Transmisión en directo",
            color = Color.White,
            fontSize = 19.sp,
            fontWeight = FontWeight.Bold,
            maxLines = 1,
            overflow = TextOverflow.Ellipsis,
        )

        // Row 3 — Play/Pause + Hero EPG progress timeline
        Row(verticalAlignment = Alignment.CenterVertically, modifier = Modifier.fillMaxWidth()) {
            IconButton(
                onClick = { onPlayPauseToggle(!isPlaying) },
                modifier = Modifier.focusRequester(focusRequester),
                interactionSource = interactionSource,
            ) {
                Icon(
                    painter =
                        painterResource(
                            id = if (!isPlaying) R.drawable.ic_play else R.drawable.ic_pause
                        ),
                    contentDescription = null,
                )
            }
            Spacer(modifier = Modifier.width(MaterialTheme.spacings.medium))
            if (epgProgress != null && startDate != null && endDate != null) {
                Text(
                    text = startDate.format(timeFormatter),
                    color = Color.White.copy(alpha = 0.85f),
                    fontSize = 13.sp,
                    fontWeight = FontWeight.Medium,
                )
                Spacer(modifier = Modifier.width(10.dp))
                Box(
                    modifier =
                        Modifier.weight(1f)
                            .height(7.dp)
                            .clip(RoundedCornerShape(4.dp))
                            .background(Color.White.copy(alpha = 0.22f))
                ) {
                    Box(
                        modifier =
                            Modifier.fillMaxWidth(epgProgress.coerceIn(0.02f, 1f))
                                .fillMaxHeight()
                                .clip(RoundedCornerShape(4.dp))
                                .background(MaterialTheme.colorScheme.primary)
                    )
                }
                Spacer(modifier = Modifier.width(10.dp))
                Text(
                    text = endDate.format(timeFormatter),
                    color = Color.White.copy(alpha = 0.85f),
                    fontSize = 13.sp,
                    fontWeight = FontWeight.Medium,
                )
            } else {
                val fillFraction = (bufferAheadSec / 30.0).toFloat().coerceIn(0.02f, 1f)
                Box(
                    modifier =
                        Modifier.weight(1f)
                            .height(7.dp)
                            .clip(RoundedCornerShape(4.dp))
                            .background(Color.White.copy(alpha = 0.22f))
                ) {
                    Box(
                        modifier =
                            Modifier.fillMaxWidth(fillFraction)
                                .fillMaxHeight()
                                .clip(RoundedCornerShape(4.dp))
                                .background(MaterialTheme.colorScheme.primary)
                    )
                }
            }
        }

        // Row 4 — Duration + estimated end time + remaining time
        Text(
            text = epgSummary ?: "Sin programación EPG disponible",
            color = Color.White.copy(alpha = 0.80f),
            fontSize = 13.sp,
            maxLines = 1,
            overflow = TextOverflow.Ellipsis,
        )

        // Row 5 — Next program
        if (nextLabel != null) {
            Box(
                modifier =
                    Modifier.fillMaxWidth().height(1.dp).background(Color.White.copy(alpha = 0.12f))
            )
            Row(
                modifier = Modifier.fillMaxWidth(),
                verticalAlignment = Alignment.CenterVertically,
                horizontalArrangement = Arrangement.spacedBy(8.dp),
            ) {
                Box(
                    modifier =
                        Modifier.clip(RoundedCornerShape(6.dp))
                            .background(Color(0x2E38BDF8))
                            .padding(horizontal = 8.dp, vertical = 2.dp)
                ) {
                    Text(
                        text = "A continuación",
                        color = Color(0xFF7DD3FC),
                        fontSize = 11.sp,
                        fontWeight = FontWeight.Bold,
                    )
                }
                Text(
                    text = nextLabel,
                    color = Color.White.copy(alpha = 0.90f),
                    fontSize = 13.sp,
                    fontWeight = FontWeight.Medium,
                    maxLines = 1,
                    overflow = TextOverflow.Ellipsis,
                    modifier = Modifier.weight(1f),
                )
            }
        }
    }
}

@Composable
private fun LiveChannelDrawerOverlay(
    channels: List<JellyxChannel>,
    currentChannelId: UUID?,
    epgNowEpochSec: Long,
    onSelectChannel: (JellyxChannel) -> Unit,
    onToggleFavorite: (JellyxChannel) -> Unit,
    onClose: () -> Unit,
) {
    val firstFocusRequester = remember { FocusRequester() }
    val timeFormatter = remember { DateTimeFormatter.ofPattern("HH:mm") }
    val nowDateTime =
        remember(epgNowEpochSec) {
            Instant.ofEpochSecond(epgNowEpochSec).atZone(ZoneId.systemDefault()).toLocalDateTime()
        }

    LaunchedEffect(channels) {
        runCatching { firstFocusRequester.requestFocus() }
    }

    Box(modifier = Modifier.fillMaxSize().zIndex(3f)) {
        Column(
            modifier =
                Modifier.align(Alignment.CenterStart)
                    .width(380.dp)
                    .fillMaxHeight()
                    .background(Color(0xB80A0E17))
                    .border(1.dp, Color.White.copy(alpha = 0.12f))
                    .padding(16.dp),
            verticalArrangement = Arrangement.spacedBy(10.dp),
        ) {
            Row(
                modifier = Modifier.fillMaxWidth(),
                horizontalArrangement = Arrangement.SpaceBetween,
                verticalAlignment = Alignment.CenterVertically,
            ) {
                Text(
                    text = "📺 Canales en Vivo (${channels.size})",
                    color = Color.White,
                    fontSize = 17.sp,
                    fontWeight = FontWeight.Bold,
                )
                Button(
                    onClick = onClose,
                    colors =
                        ButtonDefaults.colors(
                            containerColor = Color(0xFF1F2736),
                            contentColor = Color.White,
                        ),
                ) {
                    Text(text = "Cerrar", fontSize = 12.sp)
                }
            }

            LazyColumn(
                verticalArrangement = Arrangement.spacedBy(6.dp),
                modifier = Modifier.weight(1f).fillMaxWidth(),
            ) {
                itemsIndexed(channels, key = { _, ch -> ch.id }) { index, channel ->
                    val isCurrent = channel.id == currentChannelId
                    val program = channel.currentProgram
                    val progress =
                        computeProgramProgress(
                            start = program?.startDate,
                            end = program?.endDate,
                            now = nowDateTime,
                        )
                    val shouldRequestFocus =
                        isCurrent || (currentChannelId == null && index == 0)

                    Row(
                        verticalAlignment = Alignment.CenterVertically,
                        horizontalArrangement = Arrangement.spacedBy(6.dp),
                        modifier = Modifier.fillMaxWidth(),
                    ) {
                        Surface(
                            onClick = { onSelectChannel(channel) },
                            colors =
                                ClickableSurfaceDefaults.colors(
                                    containerColor =
                                        if (isCurrent) Color(0xFF23324D) else Color(0xFF161C28),
                                    focusedContainerColor = MaterialTheme.colorScheme.inverseSurface,
                                    contentColor = Color.White,
                                    focusedContentColor = MaterialTheme.colorScheme.inverseOnSurface,
                                ),
                            border =
                                ClickableSurfaceDefaults.border(
                                    border =
                                        if (isCurrent) {
                                            Border(
                                                BorderStroke(1.dp, MaterialTheme.colorScheme.primary),
                                                shape = RoundedCornerShape(8.dp),
                                            )
                                        } else {
                                            Border.None
                                        },
                                    focusedBorder =
                                        Border(
                                            BorderStroke(2.dp, MaterialTheme.colorScheme.primary),
                                            shape = RoundedCornerShape(8.dp),
                                        ),
                                ),
                            shape = ClickableSurfaceDefaults.shape(RoundedCornerShape(8.dp)),
                            modifier =
                                if (shouldRequestFocus) {
                                    Modifier.weight(1f).focusRequester(firstFocusRequester)
                                } else {
                                    Modifier.weight(1f)
                                },
                        ) {
                            Row(
                                verticalAlignment = Alignment.CenterVertically,
                                modifier = Modifier.fillMaxWidth().padding(8.dp),
                            ) {
                                Box(
                                    modifier =
                                        Modifier.size(width = 38.dp, height = 28.dp)
                                            .clip(RoundedCornerShape(5.dp))
                                            .background(Color(0xFF10141C)),
                                    contentAlignment = Alignment.Center,
                                ) {
                                    val imageUri = channel.images.primary ?: channel.images.backdrop
                                    if (imageUri != null) {
                                        AsyncImage(
                                            model = imageUri,
                                            contentDescription = channel.displayName,
                                            contentScale = ContentScale.Fit,
                                            modifier = Modifier.fillMaxSize().padding(2.dp),
                                        )
                                    } else {
                                        Icon(
                                            painter = painterResource(id = R.drawable.ic_tv),
                                            contentDescription = channel.displayName,
                                            modifier = Modifier.size(16.dp),
                                        )
                                    }
                                }
                                Spacer(modifier = Modifier.width(10.dp))
                                Column(modifier = Modifier.weight(1f)) {
                                    val favPrefix = if (channel.favorite) "⭐ " else ""
                                    val numberPrefix =
                                        channel.channelNumber?.takeIf { it.isNotBlank() }?.let {
                                            "$it · "
                                        } ?: ""
                                    Text(
                                        text = "$favPrefix$numberPrefix${channel.displayName}",
                                        fontSize = 13.sp,
                                        fontWeight = FontWeight.SemiBold,
                                        maxLines = 1,
                                        overflow = TextOverflow.Ellipsis,
                                    )
                                    val programLine =
                                        if (program != null) {
                                            val s = program.startDate?.format(timeFormatter)
                                            val e = program.endDate?.format(timeFormatter)
                                            if (s != null && e != null) "$s-$e · ${program.name}"
                                            else if (e != null) "Hasta $e · ${program.name}"
                                            else program.name
                                        } else {
                                            "En vivo"
                                        }
                                    Text(
                                        text = programLine,
                                        fontSize = 11.sp,
                                        maxLines = 1,
                                        overflow = TextOverflow.Ellipsis,
                                    )
                                    if (progress != null) {
                                        Spacer(modifier = Modifier.height(3.dp))
                                        Box(
                                            modifier =
                                                Modifier.fillMaxWidth()
                                                    .height(2.5.dp)
                                                    .clip(RoundedCornerShape(2.dp))
                                                    .background(Color.Gray.copy(alpha = 0.35f))
                                        ) {
                                            Box(
                                                modifier =
                                                    Modifier.fillMaxWidth(progress.coerceAtLeast(0.03f))
                                                        .fillMaxHeight()
                                                        .background(MaterialTheme.colorScheme.primary)
                                            )
                                        }
                                    }
                                }
                            }
                        }
                        IconButton(onClick = { onToggleFavorite(channel) }) {
                            Icon(
                                painter =
                                    painterResource(
                                        id =
                                            if (channel.favorite) R.drawable.ic_heart_filled
                                            else R.drawable.ic_heart
                                    ),
                                contentDescription = null,
                                tint =
                                    if (channel.favorite) MaterialTheme.colorScheme.primary
                                    else Color.White.copy(alpha = 0.7f),
                            )
                        }
                    }
                }
            }
        }
    }
}

@Composable
private fun EpisodeSelectorDrawerOverlay(
    episodes: List<JellyxEpisode>,
    currentEpisodeId: UUID?,
    hasSeasonNavigation: Boolean,
    onSelectEpisode: (JellyxEpisode) -> Unit,
    onGoToSeason: () -> Unit,
    onClose: () -> Unit,
) {
    val firstFocusRequester = remember { FocusRequester() }

    LaunchedEffect(episodes, currentEpisodeId) {
        runCatching { firstFocusRequester.requestFocus() }
    }

    Box(modifier = Modifier.fillMaxSize().zIndex(3f)) {
        Column(
            modifier =
                Modifier.align(Alignment.CenterStart)
                    .width(420.dp)
                    .fillMaxHeight()
                    .background(Color(0xD60A0E17))
                    .border(1.dp, Color.White.copy(alpha = 0.14f))
                    .padding(16.dp),
            verticalArrangement = Arrangement.spacedBy(10.dp),
        ) {
            Row(
                modifier = Modifier.fillMaxWidth(),
                horizontalArrangement = Arrangement.SpaceBetween,
                verticalAlignment = Alignment.CenterVertically,
            ) {
                Text(
                    text = "Episodios (${episodes.size})",
                    color = Color.White,
                    fontSize = 18.sp,
                    fontWeight = FontWeight.Bold,
                )
                Row(horizontalArrangement = Arrangement.spacedBy(8.dp)) {
                    if (hasSeasonNavigation) {
                        Button(
                            onClick = onGoToSeason,
                            colors =
                                ButtonDefaults.colors(
                                    containerColor = Color(0xFF23324D),
                                    contentColor = Color.White,
                                ),
                        ) {
                            Text(text = "Temporada", fontSize = 12.sp)
                        }
                    }
                    Button(
                        onClick = onClose,
                        colors =
                            ButtonDefaults.colors(
                                containerColor = Color(0xFF1F2736),
                                contentColor = Color.White,
                            ),
                    ) {
                        Text(text = "Cerrar", fontSize = 12.sp)
                    }
                }
            }

            LazyColumn(
                verticalArrangement = Arrangement.spacedBy(8.dp),
                modifier = Modifier.weight(1f).fillMaxWidth(),
            ) {
                itemsIndexed(episodes, key = { _, ep -> ep.id }) { index, episode ->
                    val isCurrent = episode.id == currentEpisodeId
                    val shouldRequestFocus =
                        isCurrent || (currentEpisodeId == null && index == 0)

                    Surface(
                        onClick = { onSelectEpisode(episode) },
                        colors =
                            ClickableSurfaceDefaults.colors(
                                containerColor =
                                    if (isCurrent) Color(0xFF23324D) else Color(0xFF161C28),
                                focusedContainerColor = MaterialTheme.colorScheme.inverseSurface,
                                contentColor = Color.White,
                                focusedContentColor = MaterialTheme.colorScheme.inverseOnSurface,
                            ),
                        border =
                            ClickableSurfaceDefaults.border(
                                border =
                                    if (isCurrent) {
                                        Border(
                                            BorderStroke(1.dp, MaterialTheme.colorScheme.primary),
                                            shape = RoundedCornerShape(8.dp),
                                        )
                                    } else {
                                        Border.None
                                    },
                                focusedBorder =
                                    Border(
                                        BorderStroke(2.dp, MaterialTheme.colorScheme.primary),
                                        shape = RoundedCornerShape(8.dp),
                                    ),
                            ),
                        shape = ClickableSurfaceDefaults.shape(RoundedCornerShape(8.dp)),
                        modifier =
                            if (shouldRequestFocus) {
                                Modifier.fillMaxWidth().focusRequester(firstFocusRequester)
                            } else {
                                Modifier.fillMaxWidth()
                            },
                    ) {
                        Row(
                            verticalAlignment = Alignment.CenterVertically,
                            modifier = Modifier.fillMaxWidth().padding(8.dp),
                            horizontalArrangement = Arrangement.spacedBy(10.dp),
                        ) {
                            Box(
                                modifier =
                                    Modifier.size(width = 96.dp, height = 54.dp)
                                        .clip(RoundedCornerShape(6.dp))
                                        .background(Color(0xFF10141C)),
                                contentAlignment = Alignment.Center,
                            ) {
                                val thumb = episode.images.primary ?: episode.images.backdrop
                                if (thumb != null) {
                                    AsyncImage(
                                        model = thumb,
                                        contentDescription = episode.name,
                                        contentScale = ContentScale.Crop,
                                        modifier = Modifier.fillMaxSize(),
                                    )
                                } else {
                                    Icon(
                                        painter = painterResource(id = R.drawable.ic_play),
                                        contentDescription = null,
                                        modifier = Modifier.size(18.dp),
                                    )
                                }
                            }
                            Column(
                                modifier = Modifier.weight(1f),
                                verticalArrangement = Arrangement.spacedBy(2.dp),
                            ) {
                                Text(
                                    text = "S${episode.parentIndexNumber}:E${episode.indexNumber} · ${episode.name}",
                                    fontSize = 13.sp,
                                    fontWeight = FontWeight.SemiBold,
                                    maxLines = 1,
                                    overflow = TextOverflow.Ellipsis,
                                )
                                if (episode.overview.isNotBlank()) {
                                    Text(
                                        text = episode.overview,
                                        fontSize = 11.sp,
                                        maxLines = 2,
                                        overflow = TextOverflow.Ellipsis,
                                    )
                                }
                            }
                            if (episode.played) {
                                Icon(
                                    painter = painterResource(id = R.drawable.ic_check),
                                    contentDescription = null,
                                    modifier = Modifier.size(16.dp),
                                )
                            }
                        }
                    }
                }
            }
        }
    }
}

@Composable
private fun PlaybackEndedOverlay(
    currentTitle: String,
    isFavorite: Boolean,
    recommendations: List<JellyxItem>,
    onToggleFavorite: () -> Unit,
    onExit: () -> Unit,
    onSelectItem: (JellyxItem) -> Unit,
) {
    val firstCardFocusRequester = remember { FocusRequester() }

    LaunchedEffect(recommendations) {
        runCatching { firstCardFocusRequester.requestFocus() }
    }

    Box(
        modifier =
            Modifier.fillMaxSize()
                .background(Color.Black.copy(alpha = 0.88f))
                .zIndex(2f)
                .padding(horizontal = 48.dp, vertical = 36.dp)
    ) {
        Column(
            modifier = Modifier.fillMaxSize(),
            verticalArrangement = Arrangement.SpaceBetween,
        ) {
            Column(verticalArrangement = Arrangement.spacedBy(8.dp)) {
                Text(
                    text = "Recomendados para ti",
                    color = Color.White,
                    fontSize = 28.sp,
                    fontWeight = FontWeight.Bold,
                )
                if (currentTitle.isNotBlank()) {
                    Text(
                        text = "Porque viste $currentTitle",
                        color = Color.White.copy(alpha = 0.72f),
                        fontSize = 15.sp,
                    )
                }
            }

            LazyRow(
                horizontalArrangement = Arrangement.spacedBy(20.dp),
                modifier = Modifier.fillMaxWidth(),
            ) {
                itemsIndexed(recommendations, key = { _, item -> item.id }) { index, item ->
                    val cardModifier =
                        if (index == 0) {
                            Modifier.width(200.dp).focusRequester(firstCardFocusRequester)
                        } else {
                            Modifier.width(200.dp)
                        }
                    Surface(
                        onClick = { onSelectItem(item) },
                        modifier = cardModifier,
                    ) {
                        Column(
                            modifier = Modifier.padding(8.dp),
                            verticalArrangement = Arrangement.spacedBy(8.dp),
                        ) {
                            AsyncImage(
                                model = item.images.backdrop ?: item.images.primary,
                                contentDescription = item.name,
                                contentScale = ContentScale.Crop,
                                modifier =
                                    Modifier.fillMaxWidth()
                                        .height(115.dp)
                                        .clip(RoundedCornerShape(10.dp))
                                        .background(Color.DarkGray),
                            )
                            Text(
                                text = item.name,
                                color = Color.White,
                                fontSize = 14.sp,
                                fontWeight = FontWeight.SemiBold,
                                maxLines = 1,
                                overflow = TextOverflow.Ellipsis,
                            )
                        }
                    }
                }
            }

            Row(horizontalArrangement = Arrangement.spacedBy(16.dp)) {
                Button(onClick = onToggleFavorite) {
                    Icon(
                        painter =
                            painterResource(
                                id =
                                    if (isFavorite) R.drawable.ic_heart_filled
                                    else R.drawable.ic_heart
                            ),
                        contentDescription = null,
                    )
                    Spacer(Modifier.size(ButtonDefaults.IconSpacing))
                    Text(
                        text = if (isFavorite) "En Mi lista" else "Agregar a Mi lista",
                        color = Color.Black,
                    )
                }
                Button(onClick = onExit) {
                    Text(text = "Salir", color = Color.Black)
                }
            }
        }
    }
}

@Composable
private fun SkipButton(
    stringRes: Int,
    countdownSeconds: Int? = null,
    onClick: () -> Unit,
    skipButtonFocusRequester: FocusRequester,
) {
    Box(
        modifier = Modifier.fillMaxSize().padding(MaterialTheme.spacings.large).zIndex(1f),
        contentAlignment = Alignment.BottomEnd,
    ) {
        Button(
            onClick = onClick,
            modifier = Modifier.focusRequester(skipButtonFocusRequester),
            glow =
                ButtonDefaults.glow(
                    focusedGlow = Glow(elevationColor = Color.Gray, elevation = 20.dp)
                ),
        ) {
            Icon(
                painter = painterResource(id = R.drawable.ic_skip_forward),
                contentDescription = null,
            )
            Spacer(Modifier.size(ButtonDefaults.IconSpacing))
            val label =
                if (countdownSeconds != null) {
                    "${stringResource(stringRes)} (${countdownSeconds}s)"
                } else {
                    stringResource(stringRes)
                }
            Text(text = label, color = Color.Black)
        }
    }
}

private fun Modifier.dPadEvents(
    exoPlayer: Player,
    videoPlayerState: VideoPlayerState,
    isDrawerOpen: Boolean = false,
): Modifier =
    this.handleDPadKeyEvents(
        onUp = {
            if (
                !videoPlayerState.controlsVisible &&
                    !isDrawerOpen &&
                    !videoPlayerState.wasRecentlyHidden()
            ) {
                videoPlayerState.showControls()
            }
        },
        onDown = {
            if (!videoPlayerState.controlsVisible && !isDrawerOpen) {
                videoPlayerState.showControls()
            }
        },
        onEnter = {
            if (!videoPlayerState.controlsVisible && !isDrawerOpen) {
                exoPlayer.pause()
                videoPlayerState.showControls()
            }
        },
    )

@androidx.annotation.OptIn(UnstableApi::class)
private fun getTracks(player: Player, type: Int): Array<Track> {
    val tracks = arrayListOf<Track>()
    val supportedGroups = player.currentTracks.groups.filter { it.type == type && it.isSupported }
    for ((filteredIndex, group) in supportedGroups.withIndex()) {
        val format = group.mediaTrackGroup.getFormat(0)
        val track =
            Track(
                id = filteredIndex,
                label = format.label,
                language = format.language?.let { Locale(it).displayLanguage },
                codec = format.codecs,
                selected = group.isSelected,
                supported = group.isSupported,
            )
        tracks.add(track)
    }

    val noneTrack =
        Track(
            id = -1,
            label = null,
            language = null,
            codec = null,
            selected = !tracks.any { it.selected },
            supported = true,
        )
    return arrayOf(noneTrack) + tracks
}
