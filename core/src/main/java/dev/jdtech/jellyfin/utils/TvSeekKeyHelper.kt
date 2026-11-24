package dev.jdtech.jellyfin.utils

import android.view.KeyEvent

enum class TvMediaKeyAction {
    SEEK_BACKWARD,
    SEEK_FORWARD,
    PLAY_PAUSE,
    PLAY,
    PAUSE,
    CHANNEL_UP,
    CHANNEL_DOWN,
    DPAD_LEFT,
    DPAD_RIGHT,
    NONE,
}

object TvSeekKeyHelper {
    fun classifyKeyCode(keyCode: Int): TvMediaKeyAction =
        when (keyCode) {
            KeyEvent.KEYCODE_MEDIA_REWIND,
            KeyEvent.KEYCODE_MEDIA_SKIP_BACKWARD,
            KeyEvent.KEYCODE_MEDIA_STEP_BACKWARD -> TvMediaKeyAction.SEEK_BACKWARD
            KeyEvent.KEYCODE_MEDIA_FAST_FORWARD,
            KeyEvent.KEYCODE_MEDIA_SKIP_FORWARD,
            KeyEvent.KEYCODE_MEDIA_STEP_FORWARD -> TvMediaKeyAction.SEEK_FORWARD
            KeyEvent.KEYCODE_MEDIA_PLAY_PAUSE -> TvMediaKeyAction.PLAY_PAUSE
            KeyEvent.KEYCODE_MEDIA_PLAY -> TvMediaKeyAction.PLAY
            KeyEvent.KEYCODE_MEDIA_PAUSE -> TvMediaKeyAction.PAUSE
            KeyEvent.KEYCODE_CHANNEL_UP -> TvMediaKeyAction.CHANNEL_UP
            KeyEvent.KEYCODE_CHANNEL_DOWN -> TvMediaKeyAction.CHANNEL_DOWN
            KeyEvent.KEYCODE_DPAD_LEFT,
            KeyEvent.KEYCODE_SYSTEM_NAVIGATION_LEFT -> TvMediaKeyAction.DPAD_LEFT
            KeyEvent.KEYCODE_DPAD_RIGHT,
            KeyEvent.KEYCODE_SYSTEM_NAVIGATION_RIGHT -> TvMediaKeyAction.DPAD_RIGHT
            else -> TvMediaKeyAction.NONE
        }

    fun computeSeekStepMs(repeatCount: Int, baseStepMs: Long = 10_000L): Long =
        when {
            repeatCount >= 12 -> baseStepMs * 6
            repeatCount >= 5 -> baseStepMs * 3
            else -> baseStepMs
        }

    fun computeNextSeekPosition(basePositionMs: Long, deltaMs: Long, durationMs: Long): Long {
        val raw = (basePositionMs.coerceAtLeast(0L) + deltaMs).coerceAtLeast(0L)
        return if (durationMs > 0L) raw.coerceAtMost(durationMs) else raw
    }

    fun computeScrubProgress(
        currentProgress: Float,
        direction: Int,
        durationMs: Long,
        repeatCount: Int,
    ): Float {
        val stepFraction =
            if (durationMs > 0L) {
                computeSeekStepMs(repeatCount).toFloat() / durationMs.toFloat()
            } else {
                if (repeatCount >= 5) 0.05f else 0.02f
            }
        return (currentProgress + direction * stepFraction).coerceIn(0f, 1f)
    }

    fun formatTimestampMs(positionMs: Long): String {
        val totalSeconds = positionMs.coerceAtLeast(0L) / 1000L
        val hours = totalSeconds / 3600L
        val minutes = (totalSeconds % 3600L) / 60L
        val seconds = totalSeconds % 60L
        val mm = minutes.toString().padStart(2, '0')
        val ss = seconds.toString().padStart(2, '0')
        return if (hours > 0L) "$hours:$mm:$ss" else "$mm:$ss"
    }
}
