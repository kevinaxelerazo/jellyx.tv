package dev.jdtech.jellyfin

import android.app.AppOpsManager
import android.app.PictureInPictureParams
import android.content.Intent
import android.content.pm.ActivityInfo
import android.content.pm.PackageManager
import android.content.res.Configuration
import android.graphics.Color
import android.graphics.Rect
import android.media.AudioManager
import android.os.Build
import android.os.Bundle
import android.os.Handler
import android.os.Looper
import android.os.Process
import android.util.Rational
import android.view.SurfaceView
import android.view.View
import android.view.ViewGroup
import android.view.WindowManager
import android.widget.Button
import android.widget.FrameLayout
import android.widget.ImageButton
import android.widget.ImageView
import android.widget.ProgressBar
import android.widget.Space
import android.widget.TextView
import androidx.activity.OnBackPressedCallback
import androidx.activity.viewModels
import androidx.compose.animation.AnimatedVisibility
import androidx.compose.animation.fadeIn
import androidx.compose.animation.fadeOut
import androidx.compose.animation.slideInHorizontally
import androidx.compose.animation.slideOutHorizontally
import androidx.compose.foundation.background
import androidx.compose.foundation.border
import androidx.compose.foundation.clickable
import androidx.compose.foundation.interaction.MutableInteractionSource
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
import androidx.compose.foundation.layout.widthIn
import androidx.compose.foundation.lazy.LazyColumn
import androidx.compose.foundation.lazy.LazyRow
import androidx.compose.foundation.lazy.items
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.material3.Button as ComposeButton
import androidx.compose.material3.Icon as ComposeIcon
import androidx.compose.material3.IconButton
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.OutlinedButton
import androidx.compose.material3.Text as ComposeText
import androidx.compose.runtime.Composable
import androidx.compose.runtime.LaunchedEffect
import androidx.compose.runtime.collectAsState
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.setValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clip
import androidx.compose.ui.graphics.Color as ComposeColor
import androidx.compose.ui.layout.ContentScale
import androidx.compose.ui.platform.ComposeView
import androidx.compose.ui.res.painterResource
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.text.style.TextOverflow
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import androidx.core.view.isVisible
import androidx.lifecycle.Lifecycle
import androidx.lifecycle.lifecycleScope
import androidx.lifecycle.repeatOnLifecycle
import androidx.media3.common.C
import androidx.media3.common.Player
import androidx.media3.ui.DefaultTimeBar
import androidx.media3.ui.PlayerControlView
import androidx.media3.ui.PlayerView
import coil3.compose.AsyncImage
import dagger.hilt.android.AndroidEntryPoint
import dev.jdtech.jellyfin.core.R as CoreR
import dev.jdtech.jellyfin.core.presentation.components.ConcurrentScreenAlertDialog
import dev.jdtech.jellyfin.core.presentation.components.JellyxLoadingLogo
import dev.jdtech.jellyfin.databinding.ActivityPlayerBinding
import dev.jdtech.jellyfin.film.presentation.livetv.computeProgramProgress
import dev.jdtech.jellyfin.film.presentation.livetv.formatLiveEpgSummary
import dev.jdtech.jellyfin.film.presentation.livetv.formatNextProgramLabel
import dev.jdtech.jellyfin.models.JellyxChannel
import dev.jdtech.jellyfin.models.JellyxEpisode
import dev.jdtech.jellyfin.models.JellyxItem
import dev.jdtech.jellyfin.models.JellyxShow
import dev.jdtech.jellyfin.player.local.presentation.PlayerEvents
import dev.jdtech.jellyfin.player.local.presentation.PlayerViewModel
import dev.jdtech.jellyfin.presentation.player.SpeedSelectionDialogFragment
import dev.jdtech.jellyfin.presentation.player.TrackSelectionDialogFragment
import dev.jdtech.jellyfin.presentation.theme.JellyxTheme
import dev.jdtech.jellyfin.settings.domain.AppPreferences
import dev.jdtech.jellyfin.utils.PlayerGestureHelper
import dev.jdtech.jellyfin.utils.PreviewScrubListener
import org.jellyfin.sdk.model.api.BaseItemKind
import java.time.Instant
import java.time.LocalDateTime
import java.time.ZoneId
import java.time.format.DateTimeFormatter
import java.util.UUID
import javax.inject.Inject
import kotlinx.coroutines.delay
import kotlinx.coroutines.launch
import timber.log.Timber

var isControlsLocked: Boolean = false

@AndroidEntryPoint
class PlayerActivity : BasePlayerActivity() {

    @Inject lateinit var appPreferences: AppPreferences

    lateinit var binding: ActivityPlayerBinding
    private var playerGestureHelper: PlayerGestureHelper? = null
    override val viewModel: PlayerViewModel by viewModels()
    private var previewScrubListener: PreviewScrubListener? = null
    private var wasZoom: Boolean = false
    private var skipButtonTimeoutExpired: Boolean = true
    private val isControllerVisibleState = mutableStateOf(false)
    private val showLiveChannelDrawerState = mutableStateOf(false)
    private val showEpisodesDrawerState = mutableStateOf(false)
    private val isInPipState = mutableStateOf(false)
    private val isBufferingState = mutableStateOf(true)
    private val bufferingListener =
        object : Player.Listener {
            override fun onPlaybackStateChanged(playbackState: Int) {
                isBufferingState.value = playbackState == Player.STATE_BUFFERING
            }
        }

    private lateinit var skipSegmentButton: Button

    private val isPipSupported by lazy {
        // Check if device has PiP feature
        if (!packageManager.hasSystemFeature(PackageManager.FEATURE_PICTURE_IN_PICTURE)) {
            return@lazy false
        }

        // Check if PiP is enabled for the app
        val appOps = getSystemService(APP_OPS_SERVICE) as AppOpsManager?
        appOps?.checkOpNoThrow(
            AppOpsManager.OPSTR_PICTURE_IN_PICTURE,
            Process.myUid(),
            packageName,
        ) == AppOpsManager.MODE_ALLOWED
    }

    private val handler = Handler(Looper.getMainLooper())
    private val skipButtonTimeout = Runnable {
        if (!binding.playerView.isControllerFullyVisible) {
            skipSegmentButton.isVisible = false
            skipButtonTimeoutExpired = true
        }
    }

    override fun onCreate(savedInstanceState: Bundle?) {
        super.onCreate(savedInstanceState)

        val itemId = UUID.fromString(intent.extras!!.getString("itemId"))
        val itemKind = intent.extras!!.getString("itemKind")
        val startFromBeginning = intent.extras!!.getBoolean("startFromBeginning")

        binding = ActivityPlayerBinding.inflate(layoutInflater)
        setContentView(binding.root)
        window.addFlags(WindowManager.LayoutParams.FLAG_KEEP_SCREEN_ON)

        onBackPressedDispatcher.addCallback(
            this,
            object : OnBackPressedCallback(true) {
                override fun handleOnBackPressed() {
                    val activeAlert = viewModel.uiState.value.concurrentSessionAlert
                    if (
                        activeAlert?.isConcurrentStreamBlock == true ||
                            viewModel.uiState.value.isPlaybackBlockedByLimit
                    ) {
                        return
                    }
                    if (showEpisodesDrawerState.value) {
                        showEpisodesDrawerState.value = false
                    } else if (showLiveChannelDrawerState.value) {
                        showLiveChannelDrawerState.value = false
                    } else {
                        isEnabled = false
                        onBackPressedDispatcher.onBackPressed()
                        isEnabled = true
                    }
                }
            },
        )

        val liveTvOverlayComposeView =
            ComposeView(this).apply {
                setContent {
                    JellyxTheme {
                        val state by viewModel.uiState.collectAsState()
                        val inPip = isInPipState.value || viewModel.isInPictureInPictureMode

                        LaunchedEffect(state.isLiveTv, state.currentChannel?.id) {
                            if (
                                state.isLiveTv &&
                                    state.currentChannel != null &&
                                    !showLiveChannelDrawerState.value &&
                                    !inPip
                            ) {
                                binding.playerView.showController()
                            }
                        }

                        val timeFormatter = remember { DateTimeFormatter.ofPattern("HH:mm") }

                        Box(modifier = Modifier.fillMaxSize()) {
                            if (isBufferingState.value && !state.playbackEnded && !inPip) {
                                JellyxLoadingLogo(modifier = Modifier.align(Alignment.Center))
                            }

                            // 2. Left Edge Handle
                            AnimatedVisibility(
                                visible =
                                    state.isLiveTv &&
                                        isControllerVisibleState.value &&
                                        !showLiveChannelDrawerState.value &&
                                        !inPip,
                                modifier = Modifier.align(Alignment.CenterStart),
                                enter = fadeIn(),
                                exit = fadeOut(),
                            ) {
                                Row(
                                    modifier =
                                        Modifier.clip(
                                                RoundedCornerShape(
                                                    topEnd = 12.dp,
                                                    bottomEnd = 12.dp,
                                                )
                                            )
                                            .background(ComposeColor(0xB80B111E))
                                            .border(
                                                1.dp,
                                                ComposeColor.White.copy(alpha = 0.16f),
                                                RoundedCornerShape(
                                                    topEnd = 12.dp,
                                                    bottomEnd = 12.dp,
                                                ),
                                            )
                                            .clickable {
                                                binding.playerView.hideController()
                                                showLiveChannelDrawerState.value = true
                                            }
                                            .padding(horizontal = 12.dp, vertical = 10.dp),
                                    verticalAlignment = Alignment.CenterVertically,
                                    horizontalArrangement = Arrangement.spacedBy(6.dp),
                                ) {
                                    ComposeIcon(
                                        painter = painterResource(CoreR.drawable.ic_tv),
                                        contentDescription = "Canales",
                                        tint = ComposeColor.White,
                                        modifier = Modifier.size(18.dp),
                                    )
                                    ComposeText(
                                        text = "Canales",
                                        color = ComposeColor.White,
                                        fontSize = 13.sp,
                                        fontWeight = FontWeight.SemiBold,
                                    )
                                }
                            }

                            // 3. Reconnecting Badge
                            AnimatedVisibility(
                                visible =
                                    state.isLiveTv && state.isReconnectingLive && !inPip,
                                modifier =
                                    Modifier.align(Alignment.TopEnd)
                                        .padding(top = 64.dp, end = 16.dp),
                                enter = fadeIn(),
                                exit = fadeOut(),
                            ) {
                                Row(
                                    modifier =
                                        Modifier.clip(RoundedCornerShape(20.dp))
                                            .background(ComposeColor(0xCC0B111E))
                                            .border(
                                                1.dp,
                                                ComposeColor.White.copy(alpha = 0.2f),
                                                RoundedCornerShape(20.dp),
                                            )
                                            .padding(horizontal = 12.dp, vertical = 6.dp),
                                    verticalAlignment = Alignment.CenterVertically,
                                    horizontalArrangement = Arrangement.spacedBy(8.dp),
                                ) {
                                    JellyxLoadingLogo(size = 18.dp)
                                    ComposeText(
                                        text = "Reconectando...",
                                        color = ComposeColor.White,
                                        fontSize = 12.sp,
                                        fontWeight = FontWeight.Medium,
                                    )
                                }
                            }

                            // 4. Hideable Translucent Left Channel Drawer
                            AnimatedVisibility(
                                visible =
                                    state.isLiveTv && showLiveChannelDrawerState.value && !inPip,
                                enter = slideInHorizontally { -it } + fadeIn(),
                                exit = slideOutHorizontally { -it } + fadeOut(),
                            ) {
                                LiveTvChannelDrawer(
                                    channels = state.liveChannels,
                                    currentChannelId = state.currentChannel?.id,
                                    timeFormatter = timeFormatter,
                                    onSelectChannel = { channelId ->
                                        viewModel.switchToChannel(channelId)
                                        showLiveChannelDrawerState.value = false
                                    },
                                    onToggleFavorite = { channelId ->
                                        viewModel.toggleChannelFavorite(channelId)
                                    },
                                    onDismiss = { showLiveChannelDrawerState.value = false },
                                )
                            }

                            // 5. Hideable VOD Episodes Drawer
                            AnimatedVisibility(
                                visible =
                                    !state.isLiveTv &&
                                        showEpisodesDrawerState.value &&
                                        !inPip &&
                                        state.seasonEpisodes.isNotEmpty(),
                                enter = slideInHorizontally { -it } + fadeIn(),
                                exit = slideOutHorizontally { -it } + fadeOut(),
                            ) {
                                VodEpisodesDrawer(
                                    episodes = state.seasonEpisodes,
                                    currentEpisodeId = state.currentItemId,
                                    onSelectEpisode = { episodeId ->
                                        showEpisodesDrawerState.value = false
                                        viewModel.switchToEpisode(episodeId)
                                    },
                                    onDismiss = { showEpisodesDrawerState.value = false },
                                )
                            }

                            // 6. Amber Concurrent Screen / Session Block Warning
                            state.concurrentSessionAlert?.let { alert ->
                                ConcurrentScreenAlertDialog(
                                    header = alert.header,
                                    message = alert.text,
                                    isBlocking =
                                        alert.isConcurrentStreamBlock ||
                                            state.isPlaybackBlockedByLimit,
                                    onDismiss = { viewModel.dismissConcurrentSessionAlert() },
                                )
                            }
                        }
                    }
                }
            }
        (binding.root as? ViewGroup)?.addView(
            liveTvOverlayComposeView,
            ViewGroup.LayoutParams(
                ViewGroup.LayoutParams.MATCH_PARENT,
                ViewGroup.LayoutParams.MATCH_PARENT,
            ),
        )

        val playbackEndedComposeView =
            ComposeView(this).apply {
                isVisible = false
                setContent {
                    JellyxTheme {
                        val state by viewModel.uiState.collectAsState()
                        if (state.playbackEnded && state.similarItems.isNotEmpty()) {
                            MobilePlaybackEndedOverlay(
                                currentTitle = state.currentItemTitle,
                                isFavorite = state.isCurrentItemFavorite,
                                recommendations = state.similarItems.take(4),
                                onToggleFavorite = { viewModel.toggleCurrentItemFavorite() },
                                onExit = { viewModel.dismissPlaybackEnded() },
                                onSelectItem = { item ->
                                    val kind =
                                        when (item) {
                                            is JellyxShow -> BaseItemKind.SERIES
                                            is JellyxEpisode -> BaseItemKind.EPISODE
                                            else -> BaseItemKind.MOVIE
                                        }
                                    val nextIntent =
                                        Intent(this@PlayerActivity, PlayerActivity::class.java).apply {
                                            putExtra("itemId", item.id.toString())
                                            putExtra("itemKind", kind.serialName)
                                            putExtra("startFromBeginning", false)
                                        }
                                    startActivity(nextIntent)
                                    finishPlayback()
                                },
                            )
                        }
                    }
                }
            }
        (binding.root as? ViewGroup)?.addView(
            playbackEndedComposeView,
            ViewGroup.LayoutParams(
                ViewGroup.LayoutParams.MATCH_PARENT,
                ViewGroup.LayoutParams.MATCH_PARENT,
            ),
        )

        binding.playerView.player = viewModel.player
        viewModel.player.addListener(bufferingListener)
        isBufferingState.value =
            viewModel.player.playbackState == Player.STATE_BUFFERING ||
                !viewModel.uiState.value.fileLoaded
        binding.playerView.subtitleView?.apply {
            setApplyEmbeddedStyles(false)
            setApplyEmbeddedFontSizes(false)
            setStyle(
                androidx.media3.ui.CaptionStyleCompat(
                    android.graphics.Color.WHITE,
                    android.graphics.Color.TRANSPARENT,
                    android.graphics.Color.TRANSPARENT,
                    androidx.media3.ui.CaptionStyleCompat.EDGE_TYPE_OUTLINE,
                    android.graphics.Color.BLACK,
                    null,
                )
            )
            setBottomPaddingFraction(0.03f)
            setFractionalTextSize(
                androidx.media3.ui.SubtitleView.DEFAULT_TEXT_SIZE_FRACTION * 0.85f
            )
        }
        binding.playerView.setControllerVisibilityListener(
            PlayerView.ControllerVisibilityListener { visibility ->
                isControllerVisibleState.value = (visibility == View.VISIBLE)
                if (visibility == View.GONE) {
                    hideSystemUI()
                }
                if (skipButtonTimeoutExpired && viewModel.uiState.value.currentSegment != null) {
                    skipSegmentButton.visibility = visibility
                }
            }
        )

        val playerControls = binding.playerView.findViewById<View>(R.id.player_controls)
        val lockedControls = binding.playerView.findViewById<View>(R.id.locked_player_view)

        isControlsLocked = false

        configureInsets(playerControls)
        configureInsets(lockedControls)

        if (appPreferences.getValue(appPreferences.playerGestures)) {
            playerGestureHelper =
                PlayerGestureHelper(
                    appPreferences,
                    this,
                    binding.playerView,
                    getSystemService(AUDIO_SERVICE) as AudioManager,
                )
        }

        binding.playerView.findViewById<View>(R.id.back_button).setOnClickListener {
            finishPlayback()
        }

        val videoNameTextView = binding.playerView.findViewById<TextView>(R.id.video_name)

        val playerControlView =
            binding.playerView.findViewById<PlayerControlView>(R.id.exo_controller)
        val exoPrev = binding.playerView.findViewById<ImageButton>(R.id.exo_prev)
        val exoRew = binding.playerView.findViewById<ImageButton>(R.id.exo_rew)
        val exoFfwd = binding.playerView.findViewById<ImageButton>(R.id.exo_ffwd)
        val exoNext = binding.playerView.findViewById<ImageButton>(R.id.exo_next)

        val audioButton = binding.playerView.findViewById<ImageButton>(R.id.btn_audio_track)
        val subtitleButton = binding.playerView.findViewById<ImageButton>(R.id.btn_subtitle)
        val speedButton = binding.playerView.findViewById<ImageButton>(R.id.btn_speed)
        val spaceSpeed = binding.playerView.findViewById<Space>(R.id.space_speed)
        val infoButton = binding.playerView.findViewById<ImageButton>(R.id.btn_info)
        val statsOverlay = binding.playbackStatsOverlay
        skipSegmentButton = binding.playerView.findViewById(R.id.btn_skip_segment)
        val pipButton = binding.playerView.findViewById<ImageButton>(R.id.btn_pip)
        val lockButton = binding.playerView.findViewById<ImageButton>(R.id.btn_lockview)
        val unlockButton = binding.playerView.findViewById<ImageButton>(R.id.btn_unlock)

        val vodTimeRow = binding.playerView.findViewById<View>(R.id.vod_time_row)
        val timeBar = binding.playerView.findViewById<DefaultTimeBar>(R.id.exo_progress)
        val liveCacheBarContainer =
            binding.playerView.findViewById<View>(R.id.live_cache_bar_container)
        val liveCacheProgressBar =
            binding.playerView.findViewById<ProgressBar>(R.id.live_cache_progress_bar)
        val liveCacheStatusText =
            binding.playerView.findViewById<TextView>(R.id.live_cache_status_text)
        val liveEpgChannelName =
            binding.playerView.findViewById<TextView>(R.id.live_epg_channel_name)
        val liveEpgProgramTitle =
            binding.playerView.findViewById<TextView>(R.id.live_epg_program_title)
        val liveEpgTimelineRow =
            binding.playerView.findViewById<View>(R.id.live_epg_timeline_row)
        val liveEpgStartTime =
            binding.playerView.findViewById<TextView>(R.id.live_epg_start_time)
        val liveEpgEndTime =
            binding.playerView.findViewById<TextView>(R.id.live_epg_end_time)
        val liveEpgNextContainer =
            binding.playerView.findViewById<View>(R.id.live_epg_next_container)
        val liveEpgNextProgram =
            binding.playerView.findViewById<TextView>(R.id.live_epg_next_program)
        val liveEpgTimeText =
            binding.playerView.findViewById<TextView>(R.id.live_epg_time_text)
        val liveEpgProgressBar =
            binding.playerView.findViewById<ProgressBar>(R.id.live_epg_progress_bar)
        val epgTimeFormatter = DateTimeFormatter.ofPattern("HH:mm")

        fun updateUnifiedLiveEpgHud(channel: JellyxChannel?, epgNowEpochSec: Long) {
            liveEpgChannelName.text =
                if (channel != null) {
                    val numberPrefix =
                        if (!channel.channelNumber.isNullOrBlank()) {
                            "CH ${channel.channelNumber} · "
                        } else {
                            ""
                        }
                    "$numberPrefix${channel.displayName}"
                } else {
                    ""
                }

            val currentProgram = channel?.currentProgram
            liveEpgProgramTitle.text =
                currentProgram?.name?.takeIf { it.isNotBlank() } ?: "Transmisión en directo"

            val nowDateTime =
                Instant.ofEpochSecond(epgNowEpochSec)
                    .atZone(ZoneId.systemDefault())
                    .toLocalDateTime()
            val start = currentProgram?.startDate
            val end = currentProgram?.endDate
            val epgProgress = computeProgramProgress(start = start, end = end, now = nowDateTime)
            if (epgProgress != null && start != null && end != null) {
                liveEpgTimelineRow.isVisible = true
                liveEpgStartTime.text = start.format(epgTimeFormatter)
                liveEpgEndTime.text = end.format(epgTimeFormatter)
                liveEpgProgressBar.progress = (epgProgress * 1000f).toInt().coerceIn(20, 1000)
            } else {
                liveEpgTimelineRow.isVisible = false
            }

            liveEpgTimeText.isVisible = true
            liveEpgTimeText.text =
                formatLiveEpgSummary(
                    start = start,
                    end = end,
                    now = nowDateTime,
                    timeFormatter = epgTimeFormatter,
                ) ?: "Sin programación EPG disponible"

            val nextLabel =
                formatNextProgramLabel(
                    nextProgram = channel?.nextProgram,
                    timeFormatter = epgTimeFormatter,
                )
            if (nextLabel != null) {
                liveEpgNextContainer.isVisible = true
                liveEpgNextProgram.text = nextLabel
            } else {
                liveEpgNextContainer.isVisible = false
            }
        }
        val liveChannelsButton =
            binding.playerView.findViewById<ImageButton>(R.id.btn_live_channels)
        val spaceLiveChannels = binding.playerView.findViewById<Space>(R.id.space_live_channels)
        val liveFavoriteButton =
            binding.playerView.findViewById<ImageButton>(R.id.btn_live_favorite)
        val spaceLiveFavorite = binding.playerView.findViewById<Space>(R.id.space_live_favorite)
        val liveReconnectButton =
            binding.playerView.findViewById<ImageButton>(R.id.btn_live_reconnect)
        val spaceLiveReconnect = binding.playerView.findViewById<Space>(R.id.space_live_reconnect)
        val episodesButton = binding.playerView.findViewById<ImageButton>(R.id.btn_episodes)
        val spaceEpisodes = binding.playerView.findViewById<Space>(R.id.space_episodes)

        liveChannelsButton.setOnClickListener {
            binding.playerView.hideController()
            showLiveChannelDrawerState.value = true
        }
        liveFavoriteButton.setOnClickListener { viewModel.toggleCurrentItemFavorite() }
        liveReconnectButton.setOnClickListener { viewModel.reconnectCurrentChannel(delayMs = 0L) }
        episodesButton.setOnClickListener {
            binding.playerView.hideController()
            showEpisodesDrawerState.value = true
        }

        infoButton.setOnClickListener {
            statsOverlay.isVisible = !statsOverlay.isVisible
            if (statsOverlay.isVisible) {
                statsOverlay.text = viewModel.getPlaybackStatsText()
            }
        }
        statsOverlay.setOnClickListener { statsOverlay.isVisible = false }

        lifecycleScope.launch {
            repeatOnLifecycle(Lifecycle.State.STARTED) {
                launch {
                    viewModel.uiState.collect { uiState ->
                        Timber.d("$uiState")
                        uiState.apply {
                            val showEndOverlay =
                                playbackEnded && similarItems.isNotEmpty() && !isInPictureInPictureMode
                            playbackEndedComposeView.isVisible = showEndOverlay
                            if (showEndOverlay) {
                                binding.playerView.hideController()
                            }

                            // Title
                            videoNameTextView.text = currentItemTitle

                            // Live TV controls & gestures
                            playerGestureHelper?.isLiveTv = isLiveTv
                            playerControlView.setShowPreviousButton(!isLiveTv)
                            playerControlView.setShowNextButton(!isLiveTv)
                            playerControlView.setShowRewindButton(!isLiveTv)
                            playerControlView.setShowFastForwardButton(!isLiveTv)

                            exoPrev.isVisible = !isLiveTv
                            exoRew.isVisible = !isLiveTv
                            exoFfwd.isVisible = !isLiveTv
                            exoNext.isVisible = !isLiveTv
                            speedButton.isVisible = !isLiveTv
                            spaceSpeed.isVisible = !isLiveTv
                            vodTimeRow.isVisible = !isLiveTv
                            timeBar.isVisible = !isLiveTv
                            liveCacheBarContainer.isVisible = isLiveTv
                            if (isLiveTv) {
                                updateUnifiedLiveEpgHud(
                                    channel = currentChannel,
                                    epgNowEpochSec = epgNowEpochSec,
                                )
                            }

                            liveChannelsButton.isVisible = isLiveTv
                            spaceLiveChannels.isVisible = isLiveTv
                            liveFavoriteButton.isVisible = isLiveTv
                            spaceLiveFavorite.isVisible = isLiveTv
                            liveReconnectButton.isVisible = isLiveTv
                            spaceLiveReconnect.isVisible = isLiveTv
                            val showEpisodesButton = !isLiveTv && seasonEpisodes.isNotEmpty()
                            episodesButton.isVisible = showEpisodesButton
                            spaceEpisodes.isVisible = showEpisodesButton
                            liveFavoriteButton.setImageResource(
                                if (isCurrentItemFavorite) CoreR.drawable.ic_heart_filled
                                else CoreR.drawable.ic_heart
                            )

                            // Media segment
                            currentSegment?.let { segment ->
                                // Skip Button - text
                                skipSegmentButton.text = getString(currentSkipButtonStringRes)
                                // Skip Button - visibility
                                skipSegmentButton.isVisible = !isInPictureInPictureMode
                                if (skipSegmentButton.isVisible) {
                                    skipButtonTimeoutExpired = false
                                    handler.removeCallbacks(skipButtonTimeout)
                                    handler.postDelayed(
                                        skipButtonTimeout,
                                        viewModel.segmentsSkipButtonDuration * 1000,
                                    )
                                }
                                // Skip Button - onClick
                                skipSegmentButton.setOnClickListener {
                                    viewModel.skipSegment(segment)
                                    skipSegmentButton.isVisible = false
                                }
                            } ?: run { skipSegmentButton.isVisible = false }

                            // Trickplay
                            previewScrubListener?.let { it.currentTrickplay = currentTrickplay }

                            playerGestureHelper?.let { it.currentTrickplay = currentTrickplay }

                            // Chapters
                            if (currentChapters.isNotEmpty()) {
                                val numOfChapters = currentChapters.size
                                playerControlView.setExtraAdGroupMarkers(
                                    LongArray(numOfChapters) { index ->
                                        currentChapters[index].startPosition
                                    },
                                    BooleanArray(numOfChapters) { false },
                                )
                            } else {
                                playerControlView.setExtraAdGroupMarkers(null, null)
                            }

                            // File Loaded
                            if (fileLoaded) {
                                audioButton.isEnabled = true
                                audioButton.imageAlpha = 255
                                lockButton.isEnabled = true
                                lockButton.imageAlpha = 255
                                subtitleButton.isEnabled = true
                                subtitleButton.imageAlpha = 255
                                speedButton.isEnabled = true
                                speedButton.imageAlpha = 255
                                pipButton.isEnabled = true
                                pipButton.imageAlpha = 255
                            }
                        }
                    }
                }

                launch {
                    viewModel.eventsChannelFlow.collect { event ->
                        when (event) {
                            is PlayerEvents.NavigateBack -> finishPlayback()
                            is PlayerEvents.PlayerSwitched -> {
                                binding.playerView.player?.removeListener(bufferingListener)
                                binding.playerView.player = event.newPlayer
                                event.newPlayer.addListener(bufferingListener)
                                isBufferingState.value =
                                    event.newPlayer.playbackState == Player.STATE_BUFFERING
                            }
                            is PlayerEvents.IsPlayingChanged -> {
                                if (event.isPlaying) {
                                    window.addFlags(WindowManager.LayoutParams.FLAG_KEEP_SCREEN_ON)
                                } else {
                                    window.clearFlags(
                                        WindowManager.LayoutParams.FLAG_KEEP_SCREEN_ON
                                    )
                                }

                                if (appPreferences.getValue(appPreferences.playerPipGesture)) {
                                    try {
                                        setPictureInPictureParams(pipParams(event.isPlaying))
                                    } catch (_: IllegalArgumentException) {}
                                }
                            }
                        }
                    }
                }

                launch {
                    while (true) {
                        if (statsOverlay.isVisible) {
                            statsOverlay.text = viewModel.getPlaybackStatsText()
                        }
                        delay(1000L)
                    }
                }

                launch {
                    while (true) {
                        if (viewModel.uiState.value.isLiveTv) {
                            val bufferSec = viewModel.getLiveBufferAheadSec()
                            liveCacheProgressBar.progress =
                                (bufferSec * 10).toInt().coerceIn(0, 300)
                            liveCacheStatusText.text =
                                String.format("Precarga: %.1fs / 30s", bufferSec)
                            updateUnifiedLiveEpgHud(
                                channel = viewModel.uiState.value.currentChannel,
                                epgNowEpochSec = viewModel.uiState.value.epgNowEpochSec,
                            )
                        }
                        delay(500L)
                    }
                }

                launch {
                    while (true) {
                        viewModel.updatePlaybackProgress()
                        delay(5000L)
                    }
                }

                if (
                    appPreferences.getValue(appPreferences.playerMediaSegmentsSkipButton) ||
                        appPreferences.getValue(appPreferences.playerMediaSegmentsAutoSkip)
                ) {
                    launch {
                        while (true) {
                            viewModel.updateCurrentSegment()
                            delay(1000L)
                        }
                    }
                }
            }
        }

        audioButton.isEnabled = false
        audioButton.imageAlpha = 75

        lockButton.isEnabled = false
        lockButton.imageAlpha = 75

        subtitleButton.isEnabled = false
        subtitleButton.imageAlpha = 75

        speedButton.isEnabled = false
        speedButton.imageAlpha = 75

        if (isPipSupported) {
            pipButton.isEnabled = false
            pipButton.imageAlpha = 75
        } else {
            val pipSpace = binding.playerView.findViewById<Space>(R.id.space_pip)
            pipButton.isVisible = false
            pipSpace.isVisible = false
        }

        audioButton.setOnClickListener {
            TrackSelectionDialogFragment(C.TRACK_TYPE_AUDIO, viewModel)
                .show(supportFragmentManager, "trackselectiondialog")
        }

        val exoPlayerControlView = findViewById<FrameLayout>(R.id.player_controls)
        val lockedLayout = findViewById<FrameLayout>(R.id.locked_player_view)

        lockButton.setOnClickListener {
            exoPlayerControlView.visibility = View.GONE
            lockedLayout.visibility = View.VISIBLE
            requestedOrientation = ActivityInfo.SCREEN_ORIENTATION_LOCKED
            isControlsLocked = true
        }

        unlockButton.setOnClickListener {
            exoPlayerControlView.visibility = View.VISIBLE
            lockedLayout.visibility = View.GONE
            requestedOrientation = ActivityInfo.SCREEN_ORIENTATION_SENSOR_LANDSCAPE
            isControlsLocked = false
        }

        subtitleButton.setOnClickListener {
            TrackSelectionDialogFragment(C.TRACK_TYPE_TEXT, viewModel)
                .show(supportFragmentManager, "trackselectiondialog")
        }

        speedButton.setOnClickListener {
            SpeedSelectionDialogFragment(viewModel)
                .show(supportFragmentManager, "speedselectiondialog")
        }

        pipButton.setOnClickListener { pictureInPicture() }

        // Set marker color
        timeBar.setAdMarkerColor(Color.WHITE)

        if (appPreferences.getValue(appPreferences.playerTrickplay)) {
            val imagePreview = binding.playerView.findViewById<ImageView>(R.id.image_preview)
            previewScrubListener = PreviewScrubListener(imagePreview, timeBar, viewModel.player)

            timeBar.addListener(previewScrubListener!!)
        }

        viewModel.initializePlayer(
            itemId = itemId,
            itemKind = itemKind ?: "",
            startFromBeginning = startFromBeginning,
        )
        hideSystemUI()
    }

    override fun onNewIntent(intent: Intent) {
        super.onNewIntent(intent)
        setIntent(intent)
        showLiveChannelDrawerState.value = false
        showEpisodesDrawerState.value = false

        val itemId = UUID.fromString(intent.extras!!.getString("itemId"))
        val itemKind = intent.extras!!.getString("itemKind")
        val startFromBeginning = intent.extras!!.getBoolean("startFromBeginning")

        viewModel.initializePlayer(
            itemId = itemId,
            itemKind = itemKind ?: "",
            startFromBeginning = startFromBeginning,
        )
    }

    override fun onUserLeaveHint() {
        super.onUserLeaveHint()
        if (
            Build.VERSION.SDK_INT < Build.VERSION_CODES.S &&
                appPreferences.getValue(appPreferences.playerPipGesture) &&
                viewModel.player.isPlaying &&
                !isControlsLocked
        ) {
            pictureInPicture()
        }
    }

    override fun onDestroy() {
        if (::binding.isInitialized) {
            binding.playerView.player?.removeListener(bufferingListener)
        }
        super.onDestroy()
    }

    private fun finishPlayback() {
        try {
            viewModel.player.clearVideoSurfaceView(
                binding.playerView.videoSurfaceView as SurfaceView
            )
        } catch (e: Exception) {
            Timber.e(e)
        }
        handler.removeCallbacks(skipButtonTimeout)
        finish()
    }

    private fun pipParams(
        enableAutoEnter: Boolean = viewModel.player.isPlaying
    ): PictureInPictureParams {
        val displayAspectRatio = Rational(binding.playerView.width, binding.playerView.height)

        val aspectRatio =
            binding.playerView.player?.videoSize?.let {
                Rational(
                    it.width.coerceAtMost((it.height * 2.39f).toInt()),
                    it.height.coerceAtMost((it.width * 2.39f).toInt()),
                )
            }

        val sourceRectHint =
            if (displayAspectRatio < aspectRatio!!) {
                val space =
                    ((binding.playerView.height -
                            (binding.playerView.width.toFloat() / aspectRatio.toFloat())) / 2)
                        .toInt()
                Rect(
                    0,
                    space,
                    binding.playerView.width,
                    (binding.playerView.width.toFloat() / aspectRatio.toFloat()).toInt() + space,
                )
            } else {
                val space =
                    ((binding.playerView.width -
                            (binding.playerView.height.toFloat() * aspectRatio.toFloat())) / 2)
                        .toInt()
                Rect(
                    space,
                    0,
                    (binding.playerView.height.toFloat() * aspectRatio.toFloat()).toInt() + space,
                    binding.playerView.height,
                )
            }

        val builder =
            PictureInPictureParams.Builder()
                .setAspectRatio(aspectRatio)
                .setSourceRectHint(sourceRectHint)

        if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.S) {
            builder.setAutoEnterEnabled(enableAutoEnter)
        }

        return builder.build()
    }

    private fun pictureInPicture() {
        if (!isPipSupported) {
            return
        }

        try {
            enterPictureInPictureMode(pipParams())
        } catch (_: IllegalArgumentException) {}
    }

    override fun onPictureInPictureModeChanged(
        isInPictureInPictureMode: Boolean,
        newConfig: Configuration,
    ) {
        super.onPictureInPictureModeChanged(isInPictureInPictureMode, newConfig)
        isInPipState.value = isInPictureInPictureMode
        viewModel.isInPictureInPictureMode = isInPictureInPictureMode
        when (isInPictureInPictureMode) {
            true -> {
                binding.playerView.useController = false
                skipSegmentButton.isVisible = false

                wasZoom = playerGestureHelper?.isZoomEnabled == true
                playerGestureHelper?.updateZoomMode(false)

                // Brightness mode Auto
                window.attributes =
                    window.attributes.apply {
                        screenBrightness = WindowManager.LayoutParams.BRIGHTNESS_OVERRIDE_NONE
                    }
            }

            false -> {
                binding.playerView.useController = true
                playerGestureHelper?.updateZoomMode(wasZoom)

                // Override auto brightness
                if (
                    appPreferences.getValue(appPreferences.playerGesturesVB) &&
                        appPreferences.getValue(appPreferences.playerGesturesBrightnessRemember)
                ) {
                    window.attributes =
                        window.attributes.apply {
                            screenBrightness =
                                appPreferences.getValue(appPreferences.playerBrightness)
                        }
                }
            }
        }
    }
}

@Composable
private fun MobilePlaybackEndedOverlay(
    currentTitle: String,
    isFavorite: Boolean,
    recommendations: List<JellyxItem>,
    onToggleFavorite: () -> Unit,
    onExit: () -> Unit,
    onSelectItem: (JellyxItem) -> Unit,
) {
    Box(
        modifier =
            Modifier.fillMaxSize()
                .background(ComposeColor.Black.copy(alpha = 0.9f))
                .padding(horizontal = 24.dp, vertical = 20.dp)
    ) {
        Column(
            modifier = Modifier.fillMaxSize(),
            verticalArrangement = Arrangement.SpaceBetween,
        ) {
            Column(verticalArrangement = Arrangement.spacedBy(4.dp)) {
                ComposeText(
                    text = "Recomendados para ti",
                    color = ComposeColor.White,
                    fontSize = 22.sp,
                    fontWeight = FontWeight.Bold,
                )
                if (currentTitle.isNotBlank()) {
                    ComposeText(
                        text = "Porque viste $currentTitle",
                        color = ComposeColor.White.copy(alpha = 0.72f),
                        fontSize = 13.sp,
                        maxLines = 1,
                        overflow = TextOverflow.Ellipsis,
                    )
                }
            }

            LazyRow(
                horizontalArrangement = Arrangement.spacedBy(12.dp),
                modifier = Modifier.fillMaxWidth(),
            ) {
                items(recommendations, key = { it.id }) { item ->
                    Column(
                        modifier =
                            Modifier.width(160.dp)
                                .clip(RoundedCornerShape(10.dp))
                                .background(ComposeColor.White.copy(alpha = 0.08f))
                                .clickable { onSelectItem(item) }
                                .padding(8.dp),
                        verticalArrangement = Arrangement.spacedBy(6.dp),
                    ) {
                        AsyncImage(
                            model = item.images.backdrop ?: item.images.primary,
                            contentDescription = item.name,
                            contentScale = ContentScale.Crop,
                            modifier =
                                Modifier.fillMaxWidth()
                                    .height(92.dp)
                                    .clip(RoundedCornerShape(8.dp))
                                    .background(ComposeColor.DarkGray),
                        )
                        ComposeText(
                            text = item.name,
                            color = ComposeColor.White,
                            fontSize = 13.sp,
                            fontWeight = FontWeight.SemiBold,
                            maxLines = 1,
                            overflow = TextOverflow.Ellipsis,
                        )
                    }
                }
            }

            Row(horizontalArrangement = Arrangement.spacedBy(12.dp)) {
                ComposeButton(onClick = onToggleFavorite) {
                    ComposeIcon(
                        painter =
                            painterResource(
                                id =
                                    if (isFavorite) CoreR.drawable.ic_heart_filled
                                    else CoreR.drawable.ic_heart
                            ),
                        contentDescription = null,
                        modifier = Modifier.size(18.dp),
                    )
                    Spacer(Modifier.width(8.dp))
                    ComposeText(text = if (isFavorite) "En Mi lista" else "Agregar a Mi lista")
                }
                OutlinedButton(onClick = onExit) {
                    ComposeText(text = "Salir", color = ComposeColor.White)
                }
            }
        }
    }
}

@Composable
private fun LiveTvChannelDrawer(
    channels: List<JellyxChannel>,
    currentChannelId: UUID?,
    timeFormatter: DateTimeFormatter,
    onSelectChannel: (UUID) -> Unit,
    onToggleFavorite: (UUID) -> Unit,
    onDismiss: () -> Unit,
) {
    Row(modifier = Modifier.fillMaxSize()) {
        Column(
            modifier =
                Modifier.width(300.dp)
                    .fillMaxHeight()
                    .background(ComposeColor(0xCC0A0E17))
                    .border(1.dp, ComposeColor.White.copy(alpha = 0.12f))
                    .clickable(
                        indication = null,
                        interactionSource = remember { MutableInteractionSource() },
                    ) {}
                    .padding(12.dp),
            verticalArrangement = Arrangement.spacedBy(8.dp),
        ) {
            Row(
                modifier = Modifier.fillMaxWidth(),
                verticalAlignment = Alignment.CenterVertically,
                horizontalArrangement = Arrangement.SpaceBetween,
            ) {
                ComposeText(
                    text = "Canales (${channels.size})",
                    color = ComposeColor.White,
                    fontSize = 16.sp,
                    fontWeight = FontWeight.Bold,
                )
                IconButton(onClick = onDismiss) {
                    ComposeIcon(
                        painter = painterResource(CoreR.drawable.ic_x),
                        contentDescription = "Cerrar",
                        tint = ComposeColor.White,
                    )
                }
            }

            LazyColumn(
                modifier = Modifier.fillMaxSize(),
                verticalArrangement = Arrangement.spacedBy(6.dp),
            ) {
                items(channels, key = { it.id }) { channel ->
                    val isSelected = channel.id == currentChannelId
                    val itemShape = RoundedCornerShape(10.dp)
                    Row(
                        modifier =
                            Modifier.fillMaxWidth()
                                .clip(itemShape)
                                .background(
                                    if (isSelected) ComposeColor.White.copy(alpha = 0.14f)
                                    else ComposeColor.White.copy(alpha = 0.05f)
                                )
                                .then(
                                    if (isSelected) {
                                        Modifier.border(
                                            1.dp,
                                            MaterialTheme.colorScheme.primary.copy(alpha = 0.7f),
                                            itemShape,
                                        )
                                    } else {
                                        Modifier
                                    }
                                )
                                .clickable { onSelectChannel(channel.id) }
                                .padding(horizontal = 8.dp, vertical = 8.dp),
                        verticalAlignment = Alignment.CenterVertically,
                        horizontalArrangement = Arrangement.spacedBy(8.dp),
                    ) {
                        if (!channel.channelNumber.isNullOrBlank()) {
                            ComposeText(
                                text = channel.channelNumber.orEmpty(),
                                color = ComposeColor.White.copy(alpha = 0.65f),
                                fontSize = 12.sp,
                                fontWeight = FontWeight.SemiBold,
                                modifier = Modifier.widthIn(min = 24.dp),
                            )
                        }

                        if (channel.images.primary != null) {
                            AsyncImage(
                                model = channel.images.primary,
                                contentDescription = channel.displayName,
                                contentScale = ContentScale.Fit,
                                error = painterResource(CoreR.drawable.ic_tv),
                                placeholder = painterResource(CoreR.drawable.ic_tv),
                                modifier =
                                    Modifier.size(34.dp)
                                        .clip(RoundedCornerShape(6.dp))
                                        .background(ComposeColor.White.copy(alpha = 0.08f))
                                        .padding(2.dp),
                            )
                        } else {
                            Box(
                                modifier =
                                    Modifier.size(34.dp)
                                        .clip(RoundedCornerShape(6.dp))
                                        .background(ComposeColor.White.copy(alpha = 0.08f)),
                                contentAlignment = Alignment.Center,
                            ) {
                                ComposeIcon(
                                    painter = painterResource(CoreR.drawable.ic_tv),
                                    contentDescription = channel.displayName,
                                    tint = ComposeColor.White.copy(alpha = 0.75f),
                                    modifier = Modifier.size(18.dp),
                                )
                            }
                        }

                        Column(
                            modifier = Modifier.weight(1f),
                            verticalArrangement = Arrangement.spacedBy(2.dp),
                        ) {
                            ComposeText(
                                text = channel.displayName,
                                color = ComposeColor.White,
                                fontSize = 13.sp,
                                fontWeight = FontWeight.SemiBold,
                                maxLines = 1,
                                overflow = TextOverflow.Ellipsis,
                            )
                            val program = channel.currentProgram
                            val programLabel =
                                if (program != null && program.name.isNotBlank()) {
                                    val endSuffix =
                                        program.endDate?.let { " · ${it.format(timeFormatter)}" }
                                            ?: ""
                                    "${program.name}$endSuffix"
                                } else {
                                    "En vivo"
                                }
                            ComposeText(
                                text = programLabel,
                                color = ComposeColor.White.copy(alpha = 0.65f),
                                fontSize = 11.sp,
                                maxLines = 1,
                                overflow = TextOverflow.Ellipsis,
                            )
                        }

                        IconButton(
                            onClick = { onToggleFavorite(channel.id) },
                            modifier = Modifier.size(32.dp),
                        ) {
                            ComposeIcon(
                                painter =
                                    painterResource(
                                        id =
                                            if (channel.favorite) CoreR.drawable.ic_heart_filled
                                            else CoreR.drawable.ic_heart
                                    ),
                                contentDescription = "Favorito",
                                tint =
                                    if (channel.favorite) ComposeColor(0xFFEF4444)
                                    else ComposeColor.White.copy(alpha = 0.7f),
                                modifier = Modifier.size(18.dp),
                            )
                        }
                    }
                }
            }
        }

        Box(
            modifier =
                Modifier.weight(1f)
                    .fillMaxHeight()
                    .clickable(
                        indication = null,
                        interactionSource = remember { MutableInteractionSource() },
                    ) {
                        onDismiss()
                    }
        )
    }
}

@Composable
private fun VodEpisodesDrawer(
    episodes: List<JellyxEpisode>,
    currentEpisodeId: UUID?,
    onSelectEpisode: (UUID) -> Unit,
    onDismiss: () -> Unit,
) {
    Row(modifier = Modifier.fillMaxSize()) {
        Column(
            modifier =
                Modifier.width(320.dp)
                    .fillMaxHeight()
                    .background(ComposeColor(0xD60A0E17))
                    .border(1.dp, ComposeColor.White.copy(alpha = 0.14f))
                    .clickable(
                        indication = null,
                        interactionSource = remember { MutableInteractionSource() },
                    ) {}
                    .padding(12.dp),
            verticalArrangement = Arrangement.spacedBy(8.dp),
        ) {
            Row(
                modifier = Modifier.fillMaxWidth(),
                verticalAlignment = Alignment.CenterVertically,
                horizontalArrangement = Arrangement.SpaceBetween,
            ) {
                ComposeText(
                    text = "Episodios (${episodes.size})",
                    color = ComposeColor.White,
                    fontSize = 16.sp,
                    fontWeight = FontWeight.Bold,
                )
                IconButton(onClick = onDismiss) {
                    ComposeIcon(
                        painter = painterResource(CoreR.drawable.ic_x),
                        contentDescription = "Cerrar",
                        tint = ComposeColor.White,
                    )
                }
            }

            LazyColumn(
                modifier = Modifier.fillMaxSize(),
                verticalArrangement = Arrangement.spacedBy(6.dp),
            ) {
                items(episodes, key = { it.id }) { episode ->
                    val isSelected = episode.id == currentEpisodeId
                    val itemShape = RoundedCornerShape(10.dp)
                    Row(
                        modifier =
                            Modifier.fillMaxWidth()
                                .clip(itemShape)
                                .background(
                                    if (isSelected) ComposeColor.White.copy(alpha = 0.14f)
                                    else ComposeColor.White.copy(alpha = 0.05f)
                                )
                                .then(
                                    if (isSelected) {
                                        Modifier.border(
                                            1.dp,
                                            MaterialTheme.colorScheme.primary.copy(alpha = 0.7f),
                                            itemShape,
                                        )
                                    } else {
                                        Modifier
                                    }
                                )
                                .clickable { onSelectEpisode(episode.id) }
                                .padding(horizontal = 8.dp, vertical = 8.dp),
                        verticalAlignment = Alignment.CenterVertically,
                        horizontalArrangement = Arrangement.spacedBy(8.dp),
                    ) {
                        val thumb = episode.images.primary ?: episode.images.backdrop
                        if (thumb != null) {
                            AsyncImage(
                                model = thumb,
                                contentDescription = episode.name,
                                contentScale = ContentScale.Crop,
                                modifier =
                                    Modifier.size(width = 72.dp, height = 42.dp)
                                        .clip(RoundedCornerShape(6.dp))
                                        .background(ComposeColor.White.copy(alpha = 0.08f)),
                            )
                        } else {
                            Box(
                                modifier =
                                    Modifier.size(width = 72.dp, height = 42.dp)
                                        .clip(RoundedCornerShape(6.dp))
                                        .background(ComposeColor.White.copy(alpha = 0.08f)),
                                contentAlignment = Alignment.Center,
                            ) {
                                ComposeIcon(
                                    painter = painterResource(CoreR.drawable.ic_play),
                                    contentDescription = episode.name,
                                    tint = ComposeColor.White.copy(alpha = 0.75f),
                                    modifier = Modifier.size(16.dp),
                                )
                            }
                        }

                        Column(
                            modifier = Modifier.weight(1f),
                            verticalArrangement = Arrangement.spacedBy(2.dp),
                        ) {
                            ComposeText(
                                text =
                                    "S${episode.parentIndexNumber}:E${episode.indexNumber} · ${episode.name}",
                                color = ComposeColor.White,
                                fontSize = 13.sp,
                                fontWeight = FontWeight.SemiBold,
                                maxLines = 1,
                                overflow = TextOverflow.Ellipsis,
                            )
                            if (episode.overview.isNotBlank()) {
                                ComposeText(
                                    text = episode.overview,
                                    color = ComposeColor.White.copy(alpha = 0.65f),
                                    fontSize = 11.sp,
                                    maxLines = 2,
                                    overflow = TextOverflow.Ellipsis,
                                )
                            }
                        }

                        if (episode.played) {
                            ComposeIcon(
                                painter = painterResource(CoreR.drawable.ic_check),
                                contentDescription = "Visto",
                                tint = MaterialTheme.colorScheme.primary,
                                modifier = Modifier.size(16.dp),
                            )
                        }
                    }
                }
            }
        }

        Box(
            modifier =
                Modifier.weight(1f)
                    .fillMaxHeight()
                    .clickable(
                        indication = null,
                        interactionSource = remember { MutableInteractionSource() },
                    ) {
                        onDismiss()
                    }
        )
    }
}
