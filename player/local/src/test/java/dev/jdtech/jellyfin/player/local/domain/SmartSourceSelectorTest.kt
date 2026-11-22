package dev.jdtech.jellyfin.player.local.domain

import dev.jdtech.jellyfin.models.JellyxMediaStream
import dev.jdtech.jellyfin.models.JellyxSource
import dev.jdtech.jellyfin.models.JellyxSourceType
import io.mockk.coEvery
import io.mockk.coVerify
import io.mockk.every
import io.mockk.mockk
import kotlinx.coroutines.test.runTest
import org.jellyfin.sdk.model.api.MediaStreamType
import org.jellyfin.sdk.model.api.VideoRangeType
import org.junit.Assert.assertEquals
import org.junit.Before
import org.junit.Test

class SmartSourceSelectorTest {
    private lateinit var capabilityInspector: DeviceCapabilityInspector
    private lateinit var bandwidthEstimator: NetworkBandwidthEstimator
    private lateinit var selector: SmartSourceSelector

    @Before
    fun setUp() {
        capabilityInspector = mockk()
        bandwidthEstimator = mockk()
        selector = SmartSourceSelector(capabilityInspector, bandwidthEstimator)
    }

    private fun buildSource(
        id: String,
        type: JellyxSourceType = JellyxSourceType.REMOTE,
        width: Int = 1920,
        videoRangeType: VideoRangeType = VideoRangeType.SDR,
        bitrateBps: Int = 8_000_000,
    ): JellyxSource {
        return JellyxSource(
            id = id,
            name = id,
            type = type,
            path =
                if (type == JellyxSourceType.LOCAL) "/data/local/$id.mkv"
                else "https://jellyx.test/$id",
            size = 1_000_000L,
            bitrate = bitrateBps,
            mediaStreams =
                listOf(
                    JellyxMediaStream(
                        title = "Video",
                        displayTitle = "Video",
                        language = "und",
                        type = MediaStreamType.VIDEO,
                        codec = if (width >= 3800) "hevc" else "h264",
                        isExternal = false,
                        path = null,
                        channelLayout = null,
                        videoRangeType = videoRangeType,
                        height = if (width >= 3800) 2160 else 1080,
                        width = width,
                        videoDoViTitle = null,
                        bitRate = bitrateBps,
                    )
                ),
        )
    }

    @Test
    fun selectSource_whenHighEndDvDeviceAndFastNetwork_selects4kDoViRemux() = runTest {
        every { capabilityInspector.inspect() } returns
            DeviceCapabilities(
                supportsDolbyVision = true,
                supportsHdr10Plus = true,
                supportsHdr10 = true,
                supports4kHevcHw = true,
                isLowRamDevice = false,
                totalRamMb = 6144L,
            )
        coEvery { bandwidthEstimator.estimateBandwidthBps(any()) } returns 140_000_000L

        val remux4k =
            buildSource(
                "4k-dv8",
                width = 3840,
                videoRangeType = VideoRangeType.DOVI_WITH_HDR10,
                bitrateBps = 90_000_000,
            )
        val sdr1080p =
            buildSource(
                "1080p-sdr",
                width = 1920,
                videoRangeType = VideoRangeType.SDR,
                bitrateBps = 8_000_000,
            )

        val selected = selector.selectSource(listOf(remux4k, sdr1080p))
        assertEquals("4k-dv8", selected.id)
    }

    @Test
    fun selectSource_whenHighEndDeviceButSlowNetwork_fallsBackTo1080pSdr() = runTest {
        every { capabilityInspector.inspect() } returns
            DeviceCapabilities(
                supportsDolbyVision = true,
                supportsHdr10Plus = true,
                supportsHdr10 = true,
                supports4kHevcHw = true,
                isLowRamDevice = false,
                totalRamMb = 6144L,
            )
        // 90 Mbps * 1.25 = 112.5 Mbps required, 95 Mbps is insufficient
        coEvery { bandwidthEstimator.estimateBandwidthBps(any()) } returns 95_000_000L

        val remux4k =
            buildSource(
                "4k-dv8",
                width = 3840,
                videoRangeType = VideoRangeType.DOVI_WITH_HDR10,
                bitrateBps = 90_000_000,
            )
        val sdr1080p =
            buildSource(
                "1080p-sdr",
                width = 1920,
                videoRangeType = VideoRangeType.SDR,
                bitrateBps = 8_000_000,
            )

        val selected = selector.selectSource(listOf(remux4k, sdr1080p))
        assertEquals("1080p-sdr", selected.id)
    }

    @Test
    fun selectSource_whenLowRamOrSdrDevice_alwaysSelects1080pSdrWithoutProbing() = runTest {
        every { capabilityInspector.inspect() } returns
            DeviceCapabilities(
                supportsDolbyVision = false,
                supportsHdr10Plus = false,
                supportsHdr10 = false,
                supports4kHevcHw = true,
                isLowRamDevice = true,
                totalRamMb = 2048L,
            )

        val remux4k =
            buildSource(
                "4k-dv8",
                width = 3840,
                videoRangeType = VideoRangeType.DOVI_WITH_HDR10,
                bitrateBps = 90_000_000,
            )
        val sdr1080p =
            buildSource(
                "1080p-sdr",
                width = 1920,
                videoRangeType = VideoRangeType.SDR,
                bitrateBps = 8_000_000,
            )

        val selected = selector.selectSource(listOf(remux4k, sdr1080p))
        assertEquals("1080p-sdr", selected.id)
        coVerify(exactly = 0) { bandwidthEstimator.estimateBandwidthBps(any()) }
    }

    @Test
    fun selectSource_whenDeviceLacksDolbyVisionAndSourceIsDoViProfile5_selects1080pSdr() = runTest {
        every { capabilityInspector.inspect() } returns
            DeviceCapabilities(
                supportsDolbyVision = false,
                supportsHdr10Plus = true,
                supportsHdr10 = true,
                supports4kHevcHw = true,
                isLowRamDevice = false,
                totalRamMb = 6144L,
            )

        val doviP5 =
            buildSource(
                "4k-dovi-p5",
                width = 3840,
                videoRangeType = VideoRangeType.DOVI,
                bitrateBps = 30_000_000,
            )
        val sdr1080p =
            buildSource(
                "1080p-sdr",
                width = 1920,
                videoRangeType = VideoRangeType.SDR,
                bitrateBps = 8_000_000,
            )

        val selected = selector.selectSource(listOf(doviP5, sdr1080p))
        assertEquals("1080p-sdr", selected.id)
        coVerify(exactly = 0) { bandwidthEstimator.estimateBandwidthBps(any()) }
    }

    @Test
    fun selectSource_whenDeviceLacksDolbyVisionButSourceIsDoViWithHdr10Fallback_selects4kHdr() =
        runTest {
            every { capabilityInspector.inspect() } returns
                DeviceCapabilities(
                    supportsDolbyVision = false,
                    supportsHdr10Plus = true,
                    supportsHdr10 = true,
                    supports4kHevcHw = true,
                    isLowRamDevice = false,
                    totalRamMb = 6144L,
                )
            coEvery { bandwidthEstimator.estimateBandwidthBps(any()) } returns 150_000_000L

            val doviP8 =
                buildSource(
                    "4k-dv8-hdr10",
                    width = 3840,
                    videoRangeType = VideoRangeType.DOVI_WITH_HDR10,
                    bitrateBps = 90_000_000,
                )
            val sdr1080p =
                buildSource(
                    "1080p-sdr",
                    width = 1920,
                    videoRangeType = VideoRangeType.SDR,
                    bitrateBps = 8_000_000,
                )

            val selected = selector.selectSource(listOf(doviP8, sdr1080p))
            assertEquals("4k-dv8-hdr10", selected.id)
        }

    @Test
    fun selectSource_whenManualSourceIndexProvided_overridesAutomaticSelection() = runTest {
        val remux4k =
            buildSource(
                "4k-dv8",
                width = 3840,
                videoRangeType = VideoRangeType.DOVI_WITH_HDR10,
                bitrateBps = 90_000_000,
            )
        val sdr1080p =
            buildSource(
                "1080p-sdr",
                width = 1920,
                videoRangeType = VideoRangeType.SDR,
                bitrateBps = 8_000_000,
            )

        val selected = selector.selectSource(listOf(remux4k, sdr1080p), manualSourceIndex = 0)
        assertEquals("4k-dv8", selected.id)
    }

    @Test
    fun selectSource_whenLocalSourceExists_alwaysPrioritizesLocalSource() = runTest {
        val remux4k =
            buildSource(
                "4k-dv8",
                width = 3840,
                videoRangeType = VideoRangeType.DOVI_WITH_HDR10,
                bitrateBps = 90_000_000,
            )
        val local1080p =
            buildSource(
                "local-1080p",
                type = JellyxSourceType.LOCAL,
                width = 1920,
                videoRangeType = VideoRangeType.SDR,
                bitrateBps = 6_000_000,
            )

        val selected = selector.selectSource(listOf(remux4k, local1080p))
        assertEquals("local-1080p", selected.id)
    }

    @Test
    fun selectSource_whenLocalSourceIsIncompleteDotDownloadOrEmpty_ignoresLocalAndSelectsRemote() =
        runTest {
            every { capabilityInspector.inspect() } returns
                DeviceCapabilities(
                    supportsDolbyVision = false,
                    supportsHdr10Plus = false,
                    supportsHdr10 = false,
                    supports4kHevcHw = true,
                    isLowRamDevice = false,
                    totalRamMb = 4096L,
                )

            val remote1080p =
                buildSource(
                    "remote-1080p",
                    type = JellyxSourceType.REMOTE,
                    width = 1920,
                    videoRangeType = VideoRangeType.SDR,
                    bitrateBps = 8_000_000,
                )
            val incompleteLocal =
                buildSource(
                    "local-downloading",
                    type = JellyxSourceType.LOCAL,
                    width = 1920,
                    videoRangeType = VideoRangeType.SDR,
                    bitrateBps = 6_000_000,
                ).copy(path = "/data/local/ep.download")
            val emptyLocal =
                buildSource(
                    "local-empty",
                    type = JellyxSourceType.LOCAL,
                    width = 1920,
                    videoRangeType = VideoRangeType.SDR,
                    bitrateBps = 6_000_000,
                ).copy(size = 0L)

            val selected =
                selector.selectSource(listOf(incompleteLocal, emptyLocal, remote1080p))
            assertEquals("remote-1080p", selected.id)
        }
}
