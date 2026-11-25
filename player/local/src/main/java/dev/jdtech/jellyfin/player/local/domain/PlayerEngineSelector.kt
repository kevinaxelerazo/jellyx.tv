package dev.jdtech.jellyfin.player.local.domain

import dev.jdtech.jellyfin.models.JellyxMediaStream
import org.jellyfin.sdk.model.api.MediaStreamType

enum class PlayerEngineDecision(val directLabel: String?) {
    EXOPLAYER(null),
    MPV_USER_PREFERENCE(null),
    MPV_LIVE_TV("Live TV"),
    MPV_ASS_SUBTITLES("ASS/SSA"),
}

object PlayerEngineSelector {
    private val ASS_CODECS = setOf("ass", "ssa")

    fun hasAssOrSsaSubtitles(streams: List<JellyxMediaStream>): Boolean {
        return streams.any { stream ->
            if (stream.type != MediaStreamType.SUBTITLE) return@any false
            val codec = stream.codec.trim().lowercase()
            if (codec in ASS_CODECS) return@any true
            val cleanPath = stream.path?.substringBefore('?')?.lowercase().orEmpty()
            cleanPath.endsWith(".ass") || cleanPath.endsWith(".ssa")
        }
    }

    fun selectEngine(
        configuredBackend: String,
        smartMpvForLiveTv: Boolean,
        smartMpvForAssSubtitles: Boolean,
        isLiveTv: Boolean,
        hasAssSubtitles: Boolean,
    ): PlayerEngineDecision {
        if (configuredBackend.equals("mpv", ignoreCase = true)) {
            return PlayerEngineDecision.MPV_USER_PREFERENCE
        }
        if (isLiveTv && smartMpvForLiveTv) {
            return PlayerEngineDecision.MPV_LIVE_TV
        }
        if (hasAssSubtitles && smartMpvForAssSubtitles) {
            return PlayerEngineDecision.MPV_ASS_SUBTITLES
        }
        return PlayerEngineDecision.EXOPLAYER
    }
}
