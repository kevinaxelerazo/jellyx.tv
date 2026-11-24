package dev.jdtech.jellyfin.utils

import android.view.KeyEvent
import org.junit.Assert.assertEquals
import org.junit.Test

class TvSeekKeyHelperTest {

    @Test
    fun `classifyKeyCode maps media seek, play-pause, navigation, and unknown keys`() {
        assertEquals(
            TvMediaKeyAction.SEEK_FORWARD,
            TvSeekKeyHelper.classifyKeyCode(KeyEvent.KEYCODE_MEDIA_FAST_FORWARD),
        )
        assertEquals(
            TvMediaKeyAction.SEEK_BACKWARD,
            TvSeekKeyHelper.classifyKeyCode(KeyEvent.KEYCODE_MEDIA_REWIND),
        )
        assertEquals(
            TvMediaKeyAction.SEEK_FORWARD,
            TvSeekKeyHelper.classifyKeyCode(KeyEvent.KEYCODE_MEDIA_SKIP_FORWARD),
        )
        assertEquals(
            TvMediaKeyAction.SEEK_BACKWARD,
            TvSeekKeyHelper.classifyKeyCode(KeyEvent.KEYCODE_MEDIA_SKIP_BACKWARD),
        )
        assertEquals(
            TvMediaKeyAction.SEEK_FORWARD,
            TvSeekKeyHelper.classifyKeyCode(KeyEvent.KEYCODE_MEDIA_STEP_FORWARD),
        )
        assertEquals(
            TvMediaKeyAction.SEEK_BACKWARD,
            TvSeekKeyHelper.classifyKeyCode(KeyEvent.KEYCODE_MEDIA_STEP_BACKWARD),
        )
        assertEquals(
            TvMediaKeyAction.PLAY_PAUSE,
            TvSeekKeyHelper.classifyKeyCode(KeyEvent.KEYCODE_MEDIA_PLAY_PAUSE),
        )
        assertEquals(
            TvMediaKeyAction.DPAD_LEFT,
            TvSeekKeyHelper.classifyKeyCode(KeyEvent.KEYCODE_SYSTEM_NAVIGATION_LEFT),
        )
        assertEquals(
            TvMediaKeyAction.DPAD_RIGHT,
            TvSeekKeyHelper.classifyKeyCode(KeyEvent.KEYCODE_SYSTEM_NAVIGATION_RIGHT),
        )
        assertEquals(
            TvMediaKeyAction.DPAD_LEFT,
            TvSeekKeyHelper.classifyKeyCode(KeyEvent.KEYCODE_DPAD_LEFT),
        )
        assertEquals(
            TvMediaKeyAction.DPAD_RIGHT,
            TvSeekKeyHelper.classifyKeyCode(KeyEvent.KEYCODE_DPAD_RIGHT),
        )
        assertEquals(
            TvMediaKeyAction.NONE,
            TvSeekKeyHelper.classifyKeyCode(KeyEvent.KEYCODE_VOLUME_UP),
        )
    }

    @Test
    fun `computeSeekStepMs scales with repeatCount`() {
        assertEquals(10_000L, TvSeekKeyHelper.computeSeekStepMs(0))
        assertEquals(10_000L, TvSeekKeyHelper.computeSeekStepMs(4))
        assertEquals(30_000L, TvSeekKeyHelper.computeSeekStepMs(5))
        assertEquals(30_000L, TvSeekKeyHelper.computeSeekStepMs(11))
        assertEquals(60_000L, TvSeekKeyHelper.computeSeekStepMs(12))
    }

    @Test
    fun `computeNextSeekPosition clamps at 0 and durationMs and handles unknown duration`() {
        assertEquals(0L, TvSeekKeyHelper.computeNextSeekPosition(5_000L, -10_000L, 120_000L))
        assertEquals(
            120_000L,
            TvSeekKeyHelper.computeNextSeekPosition(115_000L, 10_000L, 120_000L),
        )
        assertEquals(45_000L, TvSeekKeyHelper.computeNextSeekPosition(35_000L, 10_000L, 120_000L))
        assertEquals(50_000L, TvSeekKeyHelper.computeNextSeekPosition(40_000L, 10_000L, 0L))
        assertEquals(50_000L, TvSeekKeyHelper.computeNextSeekPosition(40_000L, 10_000L, -1L))
        assertEquals(0L, TvSeekKeyHelper.computeNextSeekPosition(-5_000L, -10_000L, -1L))
    }

    @Test
    fun `computeScrubProgress clamps between 0f and 1f with valid and zero duration`() {
        assertEquals(
            0.5f,
            TvSeekKeyHelper.computeScrubProgress(
                currentProgress = 0.4f,
                direction = 1,
                durationMs = 100_000L,
                repeatCount = 0,
            ),
            0.0001f,
        )
        assertEquals(
            0f,
            TvSeekKeyHelper.computeScrubProgress(
                currentProgress = 0.05f,
                direction = -1,
                durationMs = 100_000L,
                repeatCount = 0,
            ),
            0.0001f,
        )
        assertEquals(
            1f,
            TvSeekKeyHelper.computeScrubProgress(
                currentProgress = 0.95f,
                direction = 1,
                durationMs = 100_000L,
                repeatCount = 0,
            ),
            0.0001f,
        )
        assertEquals(
            0.52f,
            TvSeekKeyHelper.computeScrubProgress(
                currentProgress = 0.50f,
                direction = 1,
                durationMs = 0L,
                repeatCount = 0,
            ),
            0.0001f,
        )
        assertEquals(
            0.45f,
            TvSeekKeyHelper.computeScrubProgress(
                currentProgress = 0.50f,
                direction = -1,
                durationMs = 0L,
                repeatCount = 5,
            ),
            0.0001f,
        )
    }

    @Test
    fun `formatTimestampMs formats mm ss and h mm ss`() {
        assertEquals("00:00", TvSeekKeyHelper.formatTimestampMs(0L))
        assertEquals("07:51", TvSeekKeyHelper.formatTimestampMs(471_000L))
        assertEquals("1:02:03", TvSeekKeyHelper.formatTimestampMs(3_723_000L))
    }
}
