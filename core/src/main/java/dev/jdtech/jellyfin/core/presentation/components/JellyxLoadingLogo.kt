package dev.jdtech.jellyfin.core.presentation.components

import androidx.compose.animation.core.LinearEasing
import androidx.compose.animation.core.RepeatMode
import androidx.compose.animation.core.animateFloat
import androidx.compose.animation.core.infiniteRepeatable
import androidx.compose.animation.core.rememberInfiniteTransition
import androidx.compose.animation.core.tween
import androidx.compose.foundation.Canvas
import androidx.compose.foundation.Image
import androidx.compose.foundation.background
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.shape.CircleShape
import androidx.compose.material3.MaterialTheme
import androidx.compose.runtime.Composable
import androidx.compose.runtime.getValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.alpha
import androidx.compose.ui.draw.clip
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.graphics.StrokeCap
import androidx.compose.ui.graphics.drawscope.Stroke
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
    ringColor: Color = MaterialTheme.colorScheme.primary,
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

    if (size < 48.dp) {
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
    } else {
        Box(
            modifier =
                modifier
                    .size(size)
                    .clip(CircleShape)
                    .background(Color.Black.copy(alpha = 0.22f))
                    .padding(6.dp),
            contentAlignment = Alignment.Center,
        ) {
            Canvas(modifier = Modifier.matchParentSize()) {
                val strokeWidth = 2.6.dp.toPx()
                drawArc(
                    color = ringColor.copy(alpha = 0.16f),
                    startAngle = 0f,
                    sweepAngle = 360f,
                    useCenter = false,
                    style = Stroke(width = strokeWidth, cap = StrokeCap.Round),
                )
            }
            Image(
                painter = painterResource(id = CoreR.drawable.ic_logo),
                contentDescription = "Cargando",
                modifier =
                    Modifier.size(size * 0.74f)
                        .graphicsLayer { rotationZ = rotation }
                        .alpha(alpha),
            )
        }
    }
}

