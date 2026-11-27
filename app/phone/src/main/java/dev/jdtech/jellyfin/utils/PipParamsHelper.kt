package dev.jdtech.jellyfin.utils

import kotlin.math.roundToInt

/**
 * Pure JVM geometry result for Android Picture-in-Picture parameters.
 *
 * Keeps Android framework types out of the calculation so PiP clamping and
 * source-rect centering are directly unit-testable on the JVM.
 */
data class PipGeometry(
    val aspectNumerator: Int,
    val aspectDenominator: Int,
    val sourceLeft: Int,
    val sourceTop: Int,
    val sourceRight: Int,
    val sourceBottom: Int,
) {
    val hasValidSourceRect: Boolean
        get() = sourceRight > sourceLeft && sourceBottom > sourceTop

    val aspectRatio: Float
        get() = aspectNumerator.toFloat() / aspectDenominator.toFloat()
}

/**
 * Computes safe aspect ratios and centered source rects for [android.app.PictureInPictureParams].
 */
object PipParamsHelper {
    private const val DEFAULT_ASPECT_NUMERATOR = 16
    private const val DEFAULT_ASPECT_DENOMINATOR = 9
    private const val ASPECT_SCALE = 1000
    const val MIN_ASPECT_RATIO = 0.42f
    const val MAX_ASPECT_RATIO = 2.38f

    fun computeSafePipGeometry(
        videoWidth: Int,
        videoHeight: Int,
        pixelWidthHeightRatio: Float,
        viewWidth: Int,
        viewHeight: Int,
    ): PipGeometry {
        val (aspectNumerator, aspectDenominator) =
            computeSafeAspectRatio(videoWidth, videoHeight, pixelWidthHeightRatio)

        if (viewWidth <= 0 || viewHeight <= 0) {
            return PipGeometry(
                aspectNumerator = aspectNumerator,
                aspectDenominator = aspectDenominator,
                sourceLeft = 0,
                sourceTop = 0,
                sourceRight = 0,
                sourceBottom = 0,
            )
        }

        val targetRatio = aspectNumerator.toFloat() / aspectDenominator.toFloat()
        val viewRatio = viewWidth.toFloat() / viewHeight.toFloat()

        val sourceLeft: Int
        val sourceTop: Int
        val sourceRight: Int
        val sourceBottom: Int

        if (viewRatio < targetRatio) {
            val rectHeight = (viewWidth.toFloat() / targetRatio).roundToInt().coerceIn(1, viewHeight)
            sourceLeft = 0
            sourceTop = (viewHeight - rectHeight) / 2
            sourceRight = viewWidth
            sourceBottom = sourceTop + rectHeight
        } else {
            val rectWidth = (viewHeight.toFloat() * targetRatio).roundToInt().coerceIn(1, viewWidth)
            sourceLeft = (viewWidth - rectWidth) / 2
            sourceTop = 0
            sourceRight = sourceLeft + rectWidth
            sourceBottom = viewHeight
        }

        return PipGeometry(
            aspectNumerator = aspectNumerator,
            aspectDenominator = aspectDenominator,
            sourceLeft = sourceLeft,
            sourceTop = sourceTop,
            sourceRight = sourceRight,
            sourceBottom = sourceBottom,
        )
    }

    private fun computeSafeAspectRatio(
        videoWidth: Int,
        videoHeight: Int,
        pixelWidthHeightRatio: Float,
    ): Pair<Int, Int> {
        if (videoWidth <= 0 || videoHeight <= 0 || !pixelWidthHeightRatio.isFinite()) {
            return DEFAULT_ASPECT_NUMERATOR to DEFAULT_ASPECT_DENOMINATOR
        }

        val effectiveWidth =
            if (pixelWidthHeightRatio > 0f) {
                videoWidth.toFloat() * pixelWidthHeightRatio
            } else {
                videoWidth.toFloat()
            }
        val effectiveHeight = videoHeight.toFloat()
        val rawRatio = effectiveWidth / effectiveHeight

        if (!rawRatio.isFinite() || rawRatio <= 0f) {
            return DEFAULT_ASPECT_NUMERATOR to DEFAULT_ASPECT_DENOMINATOR
        }

        val clampedRatio = rawRatio.coerceIn(MIN_ASPECT_RATIO, MAX_ASPECT_RATIO)
        val numerator = (clampedRatio * ASPECT_SCALE).roundToInt().coerceIn(420, 2380)
        return numerator to ASPECT_SCALE
    }
}
