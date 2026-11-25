package dev.jdtech.jellyfin.player.local.domain

import dev.jdtech.jellyfin.models.JellyxMediaStream
import org.jellyfin.sdk.model.api.MediaStreamType
import org.junit.Assert.assertEquals
import org.junit.Assert.assertFalse
import org.junit.Assert.assertTrue
import org.junit.Test

class PlayerEngineSelectorTest {
    private fun stream(
        type: MediaStreamType,
        codec: String,
        isExternal: Boolean = false,
        path: String? = null,
    ): JellyxMediaStream {
        return JellyxMediaStream(
            title = "Stream",
            displayTitle = null,
            language = "jpn",
            type = type,
            codec = codec,
            isExternal = isExternal,
            path = path,
            channelLayout = null,
            videoRangeType = null,
            height = null,
            width = null,
            videoDoViTitle = null,
        )
    }

    @Test
    fun `detects internal ASS and SSA subtitles`() {
        assertTrue(
            PlayerEngineSelector.hasAssOrSsaSubtitles(
                listOf(stream(MediaStreamType.SUBTITLE, "ass"))
            )
        )
        assertTrue(
            PlayerEngineSelector.hasAssOrSsaSubtitles(
                listOf(stream(MediaStreamType.SUBTITLE, "SSA"))
            )
        )
    }

    @Test
    fun `detects external ass and ssa paths including query string`() {
        assertTrue(
            PlayerEngineSelector.hasAssOrSsaSubtitles(
                listOf(
                    stream(
                        type = MediaStreamType.SUBTITLE,
                        codec = "",
                        isExternal = true,
                        path = "https://jellyx.test/Subs/ep1.ass?api_key=abc123",
                    )
                )
            )
        )
        assertTrue(
            PlayerEngineSelector.hasAssOrSsaSubtitles(
                listOf(
                    stream(
                        type = MediaStreamType.SUBTITLE,
                        codec = "",
                        isExternal = true,
                        path = "https://jellyx.test/Subs/ep1.SSA?token=xyz",
                    )
                )
            )
        )
    }

    @Test
    fun `returns false for SRT VTT PGS burned-in or non-subtitle streams`() {
        assertFalse(PlayerEngineSelector.hasAssOrSsaSubtitles(emptyList()))
        assertFalse(
            PlayerEngineSelector.hasAssOrSsaSubtitles(
                listOf(
                    stream(MediaStreamType.VIDEO, "ass"),
                    stream(MediaStreamType.SUBTITLE, "subrip", isExternal = true, path = "https://jellyx.test/sub.srt"),
                    stream(MediaStreamType.SUBTITLE, "webvtt", isExternal = true, path = "https://jellyx.test/sub.vtt"),
                    stream(MediaStreamType.SUBTITLE, "pgssub"),
                )
            )
        )
    }

    @Test
    fun `selectEngine prioritizes explicit mpv user preference`() {
        assertEquals(
            PlayerEngineDecision.MPV_USER_PREFERENCE,
            PlayerEngineSelector.selectEngine(
                configuredBackend = "mpv",
                smartMpvForLiveTv = true,
                smartMpvForAssSubtitles = true,
                isLiveTv = true,
                hasAssSubtitles = true,
            ),
        )
        assertEquals(
            PlayerEngineDecision.MPV_USER_PREFERENCE,
            PlayerEngineSelector.selectEngine(
                configuredBackend = "MPV",
                smartMpvForLiveTv = false,
                smartMpvForAssSubtitles = false,
                isLiveTv = false,
                hasAssSubtitles = false,
            ),
        )
    }

    @Test
    fun `selectEngine preselects libmpv for Live TV when enabled and falls back to ExoPlayer when disabled`() {
        assertEquals(
            PlayerEngineDecision.MPV_LIVE_TV,
            PlayerEngineSelector.selectEngine(
                configuredBackend = "exoplayer",
                smartMpvForLiveTv = true,
                smartMpvForAssSubtitles = true,
                isLiveTv = true,
                hasAssSubtitles = false,
            ),
        )
        assertEquals(
            PlayerEngineDecision.EXOPLAYER,
            PlayerEngineSelector.selectEngine(
                configuredBackend = "exoplayer",
                smartMpvForLiveTv = false,
                smartMpvForAssSubtitles = true,
                isLiveTv = true,
                hasAssSubtitles = false,
            ),
        )
    }

    @Test
    fun `selectEngine preselects libmpv for ASS SSA when enabled and keeps ExoPlayer when disabled or standard VOD`() {
        assertEquals(
            PlayerEngineDecision.MPV_ASS_SUBTITLES,
            PlayerEngineSelector.selectEngine(
                configuredBackend = "exoplayer",
                smartMpvForLiveTv = true,
                smartMpvForAssSubtitles = true,
                isLiveTv = false,
                hasAssSubtitles = true,
            ),
        )
        assertEquals(
            PlayerEngineDecision.EXOPLAYER,
            PlayerEngineSelector.selectEngine(
                configuredBackend = "exoplayer",
                smartMpvForLiveTv = true,
                smartMpvForAssSubtitles = false,
                isLiveTv = false,
                hasAssSubtitles = true,
            ),
        )
        assertEquals(
            PlayerEngineDecision.EXOPLAYER,
            PlayerEngineSelector.selectEngine(
                configuredBackend = "exoplayer",
                smartMpvForLiveTv = true,
                smartMpvForAssSubtitles = true,
                isLiveTv = false,
                hasAssSubtitles = false,
            ),
        )
    }
}
