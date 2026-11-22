package dev.jdtech.jellyfin.ui.components.player

import android.os.SystemClock
import androidx.annotation.IntRange
import androidx.compose.runtime.Composable
import androidx.compose.runtime.LaunchedEffect
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.setValue
import kotlinx.coroutines.FlowPreview
import kotlinx.coroutines.channels.Channel
import kotlinx.coroutines.channels.Channel.Factory.CONFLATED
import kotlinx.coroutines.flow.consumeAsFlow
import kotlinx.coroutines.flow.debounce

class VideoPlayerState
internal constructor(@param:IntRange(from = 0) private val hideSeconds: Int) {
    private var _controlsVisible by mutableStateOf(true)
    val controlsVisible
        get() = _controlsVisible

    private var lastHiddenUptimeMs: Long = 0L

    fun showControls(seconds: Int = hideSeconds) {
        _controlsVisible = true
        channel.trySend(seconds)
    }

    fun hideControls() {
        lastHiddenUptimeMs = SystemClock.uptimeMillis()
        _controlsVisible = false
    }

    fun wasRecentlyHidden(windowMs: Long = 450L): Boolean =
        SystemClock.uptimeMillis() - lastHiddenUptimeMs < windowMs

    private val channel = Channel<Int>(CONFLATED)

    @OptIn(FlowPreview::class)
    suspend fun observe() {
        channel.consumeAsFlow().debounce { it.toLong() * 1000 }.collect { _controlsVisible = false }
    }
}

@Composable
fun rememberVideoPlayerState(@IntRange(from = 0) hideSeconds: Int = 5) = remember {
    VideoPlayerState(hideSeconds = hideSeconds)
}
    .also { LaunchedEffect(it) { it.observe() } }
