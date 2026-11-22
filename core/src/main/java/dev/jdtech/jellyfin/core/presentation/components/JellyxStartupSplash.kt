package dev.jdtech.jellyfin.core.presentation.components

import android.media.AudioAttributes
import android.media.AudioFormat
import android.media.AudioTrack
import androidx.compose.animation.core.Animatable
import androidx.compose.animation.core.FastOutSlowInEasing
import androidx.compose.animation.core.tween
import androidx.compose.foundation.Image
import androidx.compose.foundation.background
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.size
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.runtime.LaunchedEffect
import androidx.compose.runtime.remember
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.alpha
import androidx.compose.ui.draw.scale
import androidx.compose.ui.graphics.Brush
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.res.painterResource
import androidx.compose.ui.text.SpanStyle
import androidx.compose.ui.text.buildAnnotatedString
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.text.withStyle
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import dev.jdtech.jellyfin.core.R as CoreR
import kotlin.math.PI
import kotlin.math.exp
import kotlin.math.sin
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.delay
import kotlinx.coroutines.launch
import kotlinx.coroutines.withContext

private val JellyxBrandRed = Color(0xFFE01F39)
private val JellyxBrandGrey = Color(0xFF8C96A3)

/**
 * Lightweight startup intro overlay featuring the JellyX antenna logo in Red & Grey, zoom-in
 * animation, and a synthesized dual-tone "ta-tán" chime played off the main thread via MODE_STREAM.
 */
@Composable
fun JellyxStartupSplash(
    accentColor: Color = JellyxBrandRed,
    onFinished: () -> Unit,
) {
    val logoScale = remember { Animatable(0.72f) }
    val logoAlpha = remember { Animatable(0f) }
    val containerAlpha = remember { Animatable(1f) }

    LaunchedEffect(Unit) {
        launch { playStartupChime() }
        launch {
            logoAlpha.animateTo(1f, animationSpec = tween(durationMillis = 480))
        }
        logoScale.animateTo(
            targetValue = 1.06f,
            animationSpec = tween(durationMillis = 920, easing = FastOutSlowInEasing),
        )
        delay(320)
        containerAlpha.animateTo(0f, animationSpec = tween(durationMillis = 280))
        onFinished()
    }

    Box(
        modifier =
            Modifier.fillMaxSize()
                .alpha(containerAlpha.value)
                .background(
                    Brush.radialGradient(
                        colors = listOf(JellyxBrandRed.copy(alpha = 0.16f), Color(0xFF04070B))
                    )
                ),
        contentAlignment = Alignment.Center,
    ) {
        Column(
            horizontalAlignment = Alignment.CenterHorizontally,
            modifier = Modifier.scale(logoScale.value).alpha(logoAlpha.value),
        ) {
            Image(
                painter = painterResource(id = CoreR.drawable.ic_logo),
                contentDescription = "JellyX",
                modifier = Modifier.size(116.dp),
            )
            Spacer(modifier = Modifier.height(18.dp))
            Text(
                text =
                    buildAnnotatedString {
                        withStyle(SpanStyle(color = Color.White)) { append("JELLY") }
                        withStyle(SpanStyle(color = JellyxBrandRed)) { append("X") }
                    },
                fontSize = 36.sp,
                fontWeight = FontWeight.ExtraBold,
                letterSpacing = 5.sp,
            )
            Text(
                text = "TV & STREAMING",
                color = JellyxBrandGrey,
                fontSize = 11.sp,
                fontWeight = FontWeight.SemiBold,
                letterSpacing = 3.sp,
            )
        }
    }
}

private suspend fun playStartupChime() =
    withContext(Dispatchers.Default) {
        runCatching {
            val sampleRate = 44100
            val durationMs = 760
            val numSamples = sampleRate * durationMs / 1000
            val pcm = ShortArray(numSamples)

            for (i in 0 until numSamples) {
                val t = i.toDouble() / sampleRate
                val firstEnv = if (t < 0.22) exp(-t * 18.0) else 0.0
                val firstTone = sin(2.0 * PI * 220.0 * t) * 0.45 + sin(2.0 * PI * 330.0 * t) * 0.25

                val secondT = (t - 0.15).coerceAtLeast(0.0)
                val secondEnv = if (t >= 0.15) exp(-secondT * 4.8) else 0.0
                val secondChord =
                    sin(2.0 * PI * 293.66 * secondT) * 0.38 +
                        sin(2.0 * PI * 440.0 * secondT) * 0.32 +
                        sin(2.0 * PI * 587.33 * secondT) * 0.22

                val sample = ((firstTone * firstEnv) + (secondChord * secondEnv)) * 0.42
                pcm[i] = (sample.coerceIn(-1.0, 1.0) * Short.MAX_VALUE).toInt().toShort()
            }

            val minBufBytes =
                AudioTrack.getMinBufferSize(
                    sampleRate,
                    AudioFormat.CHANNEL_OUT_MONO,
                    AudioFormat.ENCODING_PCM_16BIT,
                ).coerceAtLeast(4096)
            val bufferBytes = maxOf(minBufBytes, pcm.size * 2)

            val audioTrack =
                AudioTrack.Builder()
                    .setAudioAttributes(
                        AudioAttributes.Builder()
                            .setUsage(AudioAttributes.USAGE_MEDIA)
                            .setContentType(AudioAttributes.CONTENT_TYPE_MUSIC)
                            .build()
                    )
                    .setAudioFormat(
                        AudioFormat.Builder()
                            .setEncoding(AudioFormat.ENCODING_PCM_16BIT)
                            .setSampleRate(sampleRate)
                            .setChannelMask(AudioFormat.CHANNEL_OUT_MONO)
                            .build()
                    )
                    .setTransferMode(AudioTrack.MODE_STREAM)
                    .setBufferSizeInBytes(bufferBytes)
                    .build()

            audioTrack.play()
            var offset = 0
            while (offset < pcm.size) {
                val written = audioTrack.write(pcm, offset, pcm.size - offset)
                if (written <= 0) break
                offset += written
            }
            delay(durationMs.toLong() + 120L)
            audioTrack.stop()
            audioTrack.release()
        }
    }
