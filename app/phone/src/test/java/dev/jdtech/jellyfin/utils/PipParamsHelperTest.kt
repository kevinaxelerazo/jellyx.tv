package dev.jdtech.jellyfin.utils

import org.junit.Assert.assertEquals
import org.junit.Assert.assertFalse
import org.junit.Assert.assertTrue
import org.junit.Test

class PipParamsHelperTest {

    @Test
    fun `unknown 0x0 video size defaults to 16_9 with valid positive ratio`() {
        val geometry =
            PipParamsHelper.computeSafePipGeometry(
                videoWidth = 0,
                videoHeight = 0,
                pixelWidthHeightRatio = 1f,
                viewWidth = 1920,
                viewHeight = 1080,
            )

        assertEquals(16, geometry.aspectNumerator)
        assertEquals(9, geometry.aspectDenominator)
        assertTrue(geometry.aspectRatio > 0f)
        assertTrue(geometry.hasValidSourceRect)
        assertEquals(0, geometry.sourceLeft)
        assertEquals(0, geometry.sourceTop)
        assertEquals(1920, geometry.sourceRight)
        assertEquals(1080, geometry.sourceBottom)
    }

    @Test
    fun `non-finite pixel ratio falls back to 16_9`() {
        val geometry =
            PipParamsHelper.computeSafePipGeometry(
                videoWidth = 1920,
                videoHeight = 1080,
                pixelWidthHeightRatio = Float.NaN,
                viewWidth = 1920,
                viewHeight = 1080,
            )

        assertEquals(16, geometry.aspectNumerator)
        assertEquals(9, geometry.aspectDenominator)
    }

    @Test
    fun `standard 16_9 1920x1080 preserves aspect ratio`() {
        val geometry =
            PipParamsHelper.computeSafePipGeometry(
                videoWidth = 1920,
                videoHeight = 1080,
                pixelWidthHeightRatio = 1f,
                viewWidth = 2400,
                viewHeight = 1080,
            )

        assertEquals(1778, geometry.aspectNumerator)
        assertEquals(1000, geometry.aspectDenominator)
        assertEquals(1.778f, geometry.aspectRatio, 0.001f)
        assertTrue(geometry.hasValidSourceRect)
        // Centered horizontally inside 2400x1080
        assertTrue(geometry.sourceLeft > 0)
        assertTrue(geometry.sourceRight < 2400)
        assertEquals(0, geometry.sourceTop)
        assertEquals(1080, geometry.sourceBottom)
    }

    @Test
    fun `ultrawide 3_1 clamps below 2_38`() {
        val geometry =
            PipParamsHelper.computeSafePipGeometry(
                videoWidth = 3000,
                videoHeight = 1000,
                pixelWidthHeightRatio = 1f,
                viewWidth = 1920,
                viewHeight = 1080,
            )

        assertTrue(geometry.aspectRatio <= 2.38f)
        assertEquals(2380, geometry.aspectNumerator)
        assertEquals(1000, geometry.aspectDenominator)
        assertTrue(geometry.hasValidSourceRect)
        // Letterboxed vertically inside 1920x1080
        assertEquals(0, geometry.sourceLeft)
        assertEquals(1920, geometry.sourceRight)
        assertTrue(geometry.sourceTop > 0)
        assertTrue(geometry.sourceBottom < 1080)
    }

    @Test
    fun `portrait 9_21 stays at or above 0_42 and extreme portrait clamps to 0_42`() {
        val portrait9x21 =
            PipParamsHelper.computeSafePipGeometry(
                videoWidth = 900,
                videoHeight = 2100,
                pixelWidthHeightRatio = 1f,
                viewWidth = 1080,
                viewHeight = 1920,
            )
        assertTrue(portrait9x21.aspectRatio >= 0.42f)

        val extremePortrait =
            PipParamsHelper.computeSafePipGeometry(
                videoWidth = 100,
                videoHeight = 500,
                pixelWidthHeightRatio = 1f,
                viewWidth = 1080,
                viewHeight = 1920,
            )
        assertTrue(extremePortrait.aspectRatio >= 0.42f)
        assertEquals(420, extremePortrait.aspectNumerator)
        assertEquals(1000, extremePortrait.aspectDenominator)
    }

    @Test
    fun `anamorphic 1440x1080 with PAR 1_3333 resolves to approximately 16_9`() {
        val geometry =
            PipParamsHelper.computeSafePipGeometry(
                videoWidth = 1440,
                videoHeight = 1080,
                pixelWidthHeightRatio = 1.3333f,
                viewWidth = 1920,
                viewHeight = 1080,
            )

        assertEquals(16f / 9f, geometry.aspectRatio, 0.01f)
        assertEquals(1778, geometry.aspectNumerator)
        assertEquals(1000, geometry.aspectDenominator)
    }

    @Test
    fun `zero view dimensions omit sourceRectHint and positive view produces valid bounds`() {
        val noView =
            PipParamsHelper.computeSafePipGeometry(
                videoWidth = 1920,
                videoHeight = 1080,
                pixelWidthHeightRatio = 1f,
                viewWidth = 0,
                viewHeight = 0,
            )
        assertFalse(noView.hasValidSourceRect)

        val validView =
            PipParamsHelper.computeSafePipGeometry(
                videoWidth = 1920,
                videoHeight = 1080,
                pixelWidthHeightRatio = 1f,
                viewWidth = 1920,
                viewHeight = 1080,
            )
        assertTrue(validView.hasValidSourceRect)
        assertTrue(validView.sourceLeft < validView.sourceRight)
        assertTrue(validView.sourceTop < validView.sourceBottom)
    }
}
