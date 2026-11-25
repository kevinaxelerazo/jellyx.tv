package dev.jdtech.jellyfin.core.presentation.components

import androidx.compose.animation.core.LinearEasing
import androidx.compose.animation.core.RepeatMode
import androidx.compose.animation.core.animateFloat
import androidx.compose.animation.core.infiniteRepeatable
import androidx.compose.animation.core.rememberInfiniteTransition
import androidx.compose.animation.core.tween
import androidx.compose.foundation.Image
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.size
import androidx.compose.material3.MaterialTheme
import androidx.compose.runtime.Composable
import androidx.compose.runtime.getValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.alpha
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.graphics.graphicsLayer
import androidx.compose.ui.res.painterResource
import androidx.compose.ui.unit.Dp
import androidx.compose.ui.unit.dp
import dev.jdtech.jellyfin.core.R as CoreR

/**
 * Lightweight, translucent, smoothly rotating JellyX antenna logo for loading and buffering
 * indicators across Phone, Tablet, and TV.
 */
@Composable
fun JellyxLoadingLogo(
    modifier: Modifier = Modifier,
    size: Dp = 76.dp,
    alpha: Float = 0.70f,
    @Suppress("UNUSED_PARAMETER") ringColor: Color = MaterialTheme.colorScheme.primary,
) {
    val transition = rememberInfiniteTransition(label = "jellyx_loading")
    val rotation by
        transition.animateFloat(
            initialValue = 0f,
            targetValue = 360f,
            animationSpec =
                infiniteRepeatable(
                    animation = tween(durationMillis = 1600, easing = LinearEasing),
                    repeatMode = RepeatMode.Restart,
                ),
            label = "jellyx_loading_rotation",
        )

    Box(modifier = modifier.size(size), contentAlignment = Alignment.Center) {
        Image(
            painter = painterResource(id = CoreR.drawable.ic_logo),
            contentDescription = "Cargando",
            modifier =
                Modifier.size(size * 0.90f)
                    .graphicsLayer { rotationZ = rotation }
                    .alpha(alpha),
        )
    }
}

