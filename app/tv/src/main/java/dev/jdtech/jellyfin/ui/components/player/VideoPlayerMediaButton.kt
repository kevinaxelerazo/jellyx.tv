package dev.jdtech.jellyfin.ui.components.player

import androidx.compose.foundation.interaction.MutableInteractionSource
import androidx.compose.foundation.interaction.collectIsFocusedAsState
import androidx.compose.runtime.Composable
import androidx.compose.runtime.LaunchedEffect
import androidx.compose.runtime.getValue
import androidx.compose.runtime.remember
import androidx.compose.ui.Modifier
import androidx.compose.ui.graphics.painter.Painter
import androidx.compose.ui.input.key.Key
import androidx.compose.ui.input.key.KeyEventType
import androidx.compose.ui.input.key.key
import androidx.compose.ui.input.key.onPreviewKeyEvent
import androidx.compose.ui.input.key.type
import androidx.tv.material3.Icon
import androidx.tv.material3.IconButton

@Composable
fun VideoPlayerMediaButton(
    icon: Painter,
    state: VideoPlayerState,
    isPlaying: Boolean,
    modifier: Modifier = Modifier,
    onClick: () -> Unit = {},
) {
    val interactionSource = remember { MutableInteractionSource() }
    val isFocused by interactionSource.collectIsFocusedAsState()

    LaunchedEffect(isFocused, isPlaying) {
        if (isFocused && isPlaying && state.controlsVisible) {
            state.showControls()
        }
    }

    IconButton(
        onClick = onClick,
        modifier =
            modifier.onPreviewKeyEvent { keyEvent ->
                if (keyEvent.key == Key.DirectionUp) {
                    when (keyEvent.type) {
                        KeyEventType.KeyDown -> {
                            state.hideControls()
                            true
                        }
                        KeyEventType.KeyUp -> state.wasRecentlyHidden()
                        else -> false
                    }
                } else {
                    false
                }
            },
        interactionSource = interactionSource,
    ) {
        Icon(painter = icon, contentDescription = null)
    }
}
