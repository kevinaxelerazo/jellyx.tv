package dev.jdtech.jellyfin.player.local.domain

import dev.jdtech.jellyfin.models.JellyxSource
import dev.jdtech.jellyfin.models.JellyxSourceType
import javax.inject.Inject
import org.jellyfin.sdk.model.api.MediaStreamType
import org.jellyfin.sdk.model.api.VideoRangeType

class SmartSourceSelector
@Inject
constructor(
    private val capabilityInspector: DeviceCapabilityInspector,
    private val bandwidthEstimator: NetworkBandwidthEstimator,
) {
    suspend fun selectSource(
        sources: List<JellyxSource>,
        manualSourceIndex: Int? = null,
    ): JellyxSource {
        require(sources.isNotEmpty()) { "Media sources list must not be empty" }

        if (manualSourceIndex != null && manualSourceIndex in sources.indices) {
            return sources[manualSourceIndex]
        }

        sources
            .firstOrNull {
                it.type == JellyxSourceType.LOCAL &&
                    !it.path.endsWith(".download") &&
                    it.size > 0L
            }
            ?.let {
                return it
            }

        val playableSources =
            sources
                .filterNot {
                    it.type == JellyxSourceType.LOCAL &&
                        (it.path.endsWith(".download") || it.size <= 0L)
                }
                .ifEmpty { sources }

        if (playableSources.size == 1) {
            return playableSources.first()
        }

        val capabilities = capabilityInspector.inspect()
        val classified = playableSources.map { source -> classifySource(source) }

        val sdrCandidates = classified.filter { !it.is4kOrHdr }
        val fallbackSource =
            sdrCandidates.minByOrNull { it.requiredBitrateBps }?.source
                ?: classified.minByOrNull { it.requiredBitrateBps }!!.source

        if (!capabilities.isHighEndDevice) {
            return fallbackSource
        }

        val compatibleHighEndCandidates =
            classified
                .filter { it.is4kOrHdr && isHdrCompatible(it.videoRangeType, capabilities) }
                .sortedByDescending { it.requiredBitrateBps }

        if (compatibleHighEndCandidates.isEmpty()) {
            return fallbackSource
        }

        val primaryCandidate = compatibleHighEndCandidates.first()
        val estimatedBandwidthBps =
            bandwidthEstimator.estimateBandwidthBps(primaryCandidate.source.path)

        return compatibleHighEndCandidates
            .firstOrNull { candidate ->
                estimatedBandwidthBps >=
                    (candidate.requiredBitrateBps * BANDWIDTH_SAFETY_FACTOR).toLong()
            }
            ?.source ?: fallbackSource
    }

    private fun classifySource(source: JellyxSource): ClassifiedSource {
        val videoStream = source.mediaStreams.firstOrNull { it.type == MediaStreamType.VIDEO }
        val width = videoStream?.width ?: 0
        val videoRangeType = videoStream?.videoRangeType ?: VideoRangeType.SDR
        val is4kOrHdr =
            width >= UHD_MIN_WIDTH ||
                videoRangeType in
                    setOf(
                        VideoRangeType.DOVI,
                        VideoRangeType.DOVI_WITH_HDR10,
                        VideoRangeType.DOVI_WITH_HDR10_PLUS,
                        VideoRangeType.HDR10,
                        VideoRangeType.HDR10_PLUS,
                    )
        val requiredBitrateBps =
            (source.bitrate ?: videoStream?.bitRate ?: DEFAULT_ASSUMED_BITRATE_BPS).toLong()
        return ClassifiedSource(
            source = source,
            is4kOrHdr = is4kOrHdr,
            videoRangeType = videoRangeType,
            requiredBitrateBps = requiredBitrateBps,
        )
    }

    private fun isHdrCompatible(
        videoRangeType: VideoRangeType,
        capabilities: DeviceCapabilities,
    ): Boolean {
        return when (videoRangeType) {
            // Pure Dolby Vision Profile 5 without HDR10 fallback requires native Dolby Vision
            // support
            VideoRangeType.DOVI -> capabilities.supportsDolbyVision
            VideoRangeType.DOVI_WITH_HDR10,
            VideoRangeType.DOVI_WITH_HDR10_PLUS,
            VideoRangeType.HDR10_PLUS,
            VideoRangeType.HDR10 ->
                capabilities.supportsDolbyVision ||
                    capabilities.supportsHdr10Plus ||
                    capabilities.supportsHdr10
            else -> true
        }
    }

    private data class ClassifiedSource(
        val source: JellyxSource,
        val is4kOrHdr: Boolean,
        val videoRangeType: VideoRangeType,
        val requiredBitrateBps: Long,
    )

    companion object {
        private const val UHD_MIN_WIDTH = 3800
        private const val DEFAULT_ASSUMED_BITRATE_BPS = 25_000_000
        private const val BANDWIDTH_SAFETY_FACTOR = 1.25
    }
}
