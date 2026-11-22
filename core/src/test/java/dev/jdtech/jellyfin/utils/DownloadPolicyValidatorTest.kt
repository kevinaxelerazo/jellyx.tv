package dev.jdtech.jellyfin.utils

import android.app.UiModeManager
import android.content.Context
import android.content.pm.PackageManager
import android.content.res.Configuration
import dev.jdtech.jellyfin.models.JellyxMediaStream
import dev.jdtech.jellyfin.models.JellyxMovie
import dev.jdtech.jellyfin.models.JellyxSource
import dev.jdtech.jellyfin.models.JellyxSourceType
import io.mockk.every
import io.mockk.mockk
import java.io.File
import org.jellyfin.sdk.model.api.MediaStreamType
import org.jellyfin.sdk.model.api.VideoRangeType
import org.junit.Assert.assertEquals
import org.junit.Assert.assertTrue
import org.junit.Test

class DownloadPolicyValidatorTest {

    @Test
    fun `rejects Leanback TV devices`() {
        val context = mockk<Context>(relaxed = true)
        val uiModeManager = mockk<UiModeManager>(relaxed = true)
        val packageManager = mockk<PackageManager>(relaxed = true)
        every { context.getSystemService(Context.UI_MODE_SERVICE) } returns uiModeManager
        every { uiModeManager.currentModeType } returns Configuration.UI_MODE_TYPE_TELEVISION
        every { context.packageManager } returns packageManager

        val result =
            DownloadPolicyValidator.validateDeviceAndStorage(
                context = context,
                storageDir = File("."),
                availableBytesOverride = 10L * 1024L * 1024L * 1024L,
            )

        assertTrue(result is DownloadPolicyResult.Denied)
    }

    @Test
    fun `rejects devices with less than 2GB free storage`() {
        val context = mockk<Context>(relaxed = true)
        val uiModeManager = mockk<UiModeManager>(relaxed = true)
        val packageManager = mockk<PackageManager>(relaxed = true)
        every { context.getSystemService(Context.UI_MODE_SERVICE) } returns uiModeManager
        every { uiModeManager.currentModeType } returns Configuration.UI_MODE_TYPE_NORMAL
        every { context.packageManager } returns packageManager
        every { packageManager.hasSystemFeature(PackageManager.FEATURE_LEANBACK) } returns false

        val result =
            DownloadPolicyValidator.validateDeviceAndStorage(
                context = context,
                storageDir = File("."),
                availableBytesOverride = 1L * 1024L * 1024L * 1024L,
            )

        assertTrue(result is DownloadPolicyResult.Denied)
    }

    @Test
    fun `allows JellyxMovie items when canDownload is true and rejects when false`() {
        val downloadableMovie = mockk<JellyxMovie>(relaxed = true)
        every { downloadableMovie.canDownload } returns true
        assertTrue(DownloadPolicyValidator.validateItem(downloadableMovie) is DownloadPolicyResult.Allowed)

        val restrictedMovie = mockk<JellyxMovie>(relaxed = true)
        every { restrictedMovie.canDownload } returns false
        assertTrue(DownloadPolicyValidator.validateItem(restrictedMovie) is DownloadPolicyResult.Denied)
    }

    @Test
    fun `select1080pSdrSourceForDownload prefers 1080p SDR over 4K Dolby Vision`() {
        val source4kDv =
            JellyxSource(
                id = "4k-dv",
                name = "4K DV",
                path = "https://example/4k.mkv",
                size = 20_000_000_000L,
                mediaStreams =
                    listOf(
                        JellyxMediaStream(
                            title = "HEVC",
                            displayTitle = "4K Dolby Vision",
                            language = "eng",
                            type = MediaStreamType.VIDEO,
                            codec = "hevc",
                            isExternal = false,
                            path = null,
                            channelLayout = null,
                            videoRangeType = VideoRangeType.DOVI,
                            height = 2160,
                            width = 3840,
                            videoDoViTitle = "Dolby Vision",
                        )
                    ),
                type = JellyxSourceType.REMOTE,
            )

        val source1080pSdr =
            JellyxSource(
                id = "1080p-sdr",
                name = "1080p SDR",
                path = "https://example/1080p.mkv",
                size = 4_000_000_000L,
                mediaStreams =
                    listOf(
                        JellyxMediaStream(
                            title = "H264",
                            displayTitle = "1080p SDR",
                            language = "eng",
                            type = MediaStreamType.VIDEO,
                            codec = "h264",
                            isExternal = false,
                            path = null,
                            channelLayout = null,
                            videoRangeType = VideoRangeType.SDR,
                            height = 1080,
                            width = 1920,
                            videoDoViTitle = null,
                        )
                    ),
                type = JellyxSourceType.REMOTE,
            )

        val selected =
            DownloadPolicyValidator.select1080pSdrSourceForDownload(
                listOf(source4kDv, source1080pSdr)
            )

        assertEquals("1080p-sdr", selected?.id)
    }
}
