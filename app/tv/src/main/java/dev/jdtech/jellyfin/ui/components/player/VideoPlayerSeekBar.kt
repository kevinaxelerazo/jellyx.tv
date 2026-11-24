package dev.jdtech.jellyfin.ui.components.player

import android.view.KeyEvent
import androidx.compose.animation.core.animateDpAsState
import androidx.compose.foundation.Canvas
import androidx.compose.foundation.focusable
import androidx.compose.foundation.interaction.MutableInteractionSource
import androidx.compose.foundation.interaction.collectIsFocusedAsState
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.padding
import androidx.compose.runtime.Composable
import androidx.compose.runtime.LaunchedEffect
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableFloatStateOf
import androidx.compose.runtime.mutableLongStateOf
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.rememberUpdatedState
import androidx.compose.runtime.setValue
import androidx.compose.ui.ExperimentalComposeUiApi
import androidx.compose.ui.Modifier
import androidx.compose.ui.geometry.Offset
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.graphics.StrokeCap
import androidx.compose.ui.input.key.KeyEventType
import androidx.compose.ui.input.key.onPreviewKeyEvent
import androidx.compose.ui.input.key.type
import androidx.compose.ui.tooling.preview.Preview
import androidx.compose.ui.unit.dp
import androidx.tv.material3.MaterialTheme
import dev.jdtech.jellyfin.presentation.theme.JellyxTheme
import dev.jdtech.jellyfin.utils.TvSeekKeyHelper
import kotlinx.coroutines.delay

@OptIn(ExperimentalComposeUiApi::class)
@Composable
fun VideoPlayerSeekBar(
    progress: Float,
    onSeek: (seekProgress: Float) -> Unit,
    state: VideoPlayerState,
    durationMs: Long = 0L,
    onPlayPauseToggle: () -> Unit = {},
    onScrubbingChanged: (isScrubbing: Boolean, previewProgress: Float?) -> Unit = { _, _ -> },
) {
    val interactionSource = remember { MutableInteractionSource() }
    val isFocused by interactionSource.collectIsFocusedAsState()
    var isScrubbing by remember { mutableStateOf(false) }
    var seekProgress by remember { mutableFloatStateOf(progress.coerceIn(0f, 1f)) }
    var scrubCommitTrigger by remember { mutableLongStateOf(0L) }
    val color by
        rememberUpdatedState(
            newValue =
                if (isFocused || isScrubbing) {
                    MaterialTheme.colorScheme.primary
                } else {
                    MaterialTheme.colorScheme.onSurface
                }
        )
    val animatedHeight by
        animateDpAsState(targetValue = 8.dp.times(if (isFocused || isScrubbing) 2f else 1f))

    LaunchedEffect(isFocused) {
        if (!isFocused && isScrubbing) {
            isScrubbing = false
            onScrubbingChanged(false, null)
        }
    }

    LaunchedEffect(isScrubbing, seekProgress) {
        onScrubbingChanged(isScrubbing, if (isScrubbing) seekProgress else null)
    }

    LaunchedEffect(scrubCommitTrigger) {
        if (scrubCommitTrigger > 0L && isScrubbing) {
            delay(650L)
            onSeek(seekProgress)
            isScrubbing = false
            onScrubbingChanged(false, null)
            state.showControls()
        }
    }

    Canvas(
        modifier =
            Modifier.fillMaxWidth()
                .height(animatedHeight)
                .padding(horizontal = 4.dp)
                .onPreviewKeyEvent { keyEvent ->
                    if (!isFocused) return@onPreviewKeyEvent false
                    val keyCode = keyEvent.nativeKeyEvent.keyCode
                    when (keyEvent.type) {
                        KeyEventType.KeyDown -> {
                            when (keyCode) {
                                KeyEvent.KEYCODE_DPAD_LEFT,
                                KeyEvent.KEYCODE_SYSTEM_NAVIGATION_LEFT,
                                KeyEvent.KEYCODE_MEDIA_REWIND -> {
                                    if (!isScrubbing) {
                                        seekProgress = progress.coerceIn(0f, 1f)
                                    }
                                    isScrubbing = true
                                    seekProgress =
                                        TvSeekKeyHelper.computeScrubProgress(
                                            currentProgress = seekProgress,
                                            direction = -1,
                                            durationMs = durationMs,
                                            repeatCount = keyEvent.nativeKeyEvent.repeatCount,
                                        )
                                    state.showControls(seconds = Int.MAX_VALUE)
                                    scrubCommitTrigger++
                                    true
                                }
                                KeyEvent.KEYCODE_DPAD_RIGHT,
                                KeyEvent.KEYCODE_SYSTEM_NAVIGATION_RIGHT,
                                KeyEvent.KEYCODE_MEDIA_FAST_FORWARD -> {
                                    if (!isScrubbing) {
                                        seekProgress = progress.coerceIn(0f, 1f)
                                    }
                                    isScrubbing = true
                                    seekProgress =
                                        TvSeekKeyHelper.computeScrubProgress(
                                            currentProgress = seekProgress,
                                            direction = 1,
                                            durationMs = durationMs,
                                            repeatCount = keyEvent.nativeKeyEvent.repeatCount,
                                        )
                                    state.showControls(seconds = Int.MAX_VALUE)
                                    scrubCommitTrigger++
                                    true
                                }
                                KeyEvent.KEYCODE_DPAD_CENTER,
                                KeyEvent.KEYCODE_ENTER,
                                KeyEvent.KEYCODE_NUMPAD_ENTER -> {
                                    if (isScrubbing) {
                                        onSeek(seekProgress)
                                        isScrubbing = false
                                        onScrubbingChanged(false, null)
                                        state.showControls()
                                    } else {
                                        onPlayPauseToggle()
                                        state.showControls()
                                    }
                                    true
                                }
                                KeyEvent.KEYCODE_BACK -> {
                                    if (isScrubbing) {
                                        isScrubbing = false
                                        onScrubbingChanged(false, null)
                                        state.showControls()
                                        true
                                    } else {
                                        false
                                    }
                                }
                                KeyEvent.KEYCODE_DPAD_UP -> {
                                    if (isScrubbing) {
                                        onSeek(seekProgress)
                                        isScrubbing = false
                                        onScrubbingChanged(false, null)
                                        state.showControls()
                                    }
                                    false
                                }
                                else -> false
                            }
                        }
                        KeyEventType.KeyUp -> {
                            when (keyCode) {
                                KeyEvent.KEYCODE_DPAD_LEFT,
                                KeyEvent.KEYCODE_SYSTEM_NAVIGATION_LEFT,
                                KeyEvent.KEYCODE_DPAD_RIGHT,
                                KeyEvent.KEYCODE_SYSTEM_NAVIGATION_RIGHT,
                                KeyEvent.KEYCODE_MEDIA_REWIND,
                                KeyEvent.KEYCODE_MEDIA_FAST_FORWARD,
                                KeyEvent.KEYCODE_DPAD_CENTER,
                                KeyEvent.KEYCODE_ENTER,
                                KeyEvent.KEYCODE_NUMPAD_ENTER -> true
                                else -> false
                            }
                        }
                        else -> false
                    }
                }
                .focusable(interactionSource = interactionSource)
    ) {
        val displayedProgress = (if (isScrubbing) seekProgress else progress).coerceIn(0f, 1f)
        val yOffset = size.height.div(2)
        drawLine(
            color = color.copy(alpha = 0.24f),
            start = Offset(x = 0f, y = yOffset),
            end = Offset(x = size.width, y = yOffset),
            strokeWidth = size.height.div(2),
            cap = StrokeCap.Round,
        )
        drawLine(
            color = color,
            start = Offset(x = 0f, y = yOffset),
            end = Offset(x = size.width.times(displayedProgress), y = yOffset),
            strokeWidth = size.height.div(2),
            cap = StrokeCap.Round,
        )
        drawCircle(
            color = Color.White,
            radius = size.height.div(if (isFocused) 1.5f else 2f),
            center = Offset(x = size.width.times(displayedProgress), y = yOffset),
        )
    }
}

@Preview
@Composable
fun VideoPlayerSeekBarPreview() {
    JellyxTheme {
        VideoPlayerSeekBar(progress = 0.4f, onSeek = {}, state = rememberVideoPlayerState())
    }
}
